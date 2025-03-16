package io.joswlv.jirabranch.services

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.diagnostic.LoadingState
import com.intellij.ide.BrowserUtil
import io.joswlv.jirabranch.JiraBranchBundle
import io.joswlv.jirabranch.config.AppSettingsState
import io.joswlv.jirabranch.dialog.TokenSetupDialog
import io.joswlv.jirabranch.model.JiraIssue
import io.joswlv.jirabranch.settings.AppSettingsComponent
import io.joswlv.jirabranch.utils.UrlUtils
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * HTTP 클라이언트를 사용하여 JIRA API와 상호작용하는 서비스 클래스
 */
@Service(Service.Level.PROJECT)
class JiraService(private val project: Project) {
    private val LOG = Logger.getInstance(JiraService::class.java)
    private var initialized = false
    private val executor: ExecutorService = Executors.newCachedThreadPool()

    private val issueCache = ConcurrentHashMap<String, Pair<JiraIssue, Long>>()
    private val CACHE_EXPIRY_MS = 5 * 60 * 1000 // 5분

    /**
     * 서비스가 안전하게 초기화되었는지 확인
     * @return 초기화가 준비되었으면 true, 아니면 false
     */
    @Synchronized
    fun ensureInitialized(): Boolean {
        if (initialized) return true

        // 애플리케이션이 적절한 상태인지 확인
        val app = ApplicationManager.getApplication()
        if (!app.isUnitTestMode && !LoadingState.COMPONENTS_LOADED.isOccurred) {
            LOG.warn("COMPONENTS_LOADED 상태 전에 JiraService 초기화 시도. 나중에 다시 시도합니다.")
            return false
        }

        initialized = true
        return true
    }

    /**
     * 시작 시 호출되는 사전 초기화 메서드
     */
    fun preInitialize() {
        LOG.info("JiraService 사전 초기화 중...")

        // 설정만 확인하고 실제 초기화는 나중에 수행
        val settings = AppSettingsState.getInstance()
        if (settings.isJiraConfigured()) {
            LOG.info("JIRA 설정이 구성되어 있습니다.")
        }
    }

    /**
     * JIRA 설정 확인 및 필요시 설정 다이얼로그 표시
     * @return 설정이 완료되었으면 true, 아니면 false
     */
    fun checkJiraSettings(): Boolean {
        // 먼저 초기화 상태 확인
        if (!ensureInitialized()) {
            LOG.warn("서비스가 아직 초기화되지 않았습니다.")
            return false
        }

        val settings = AppSettingsState.getInstance()

        LOG.info("JIRA 설정 확인 중... 구성됨: ${settings.isJiraConfigured()}")

        if (!settings.isJiraConfigured()) {
            LOG.info("JIRA가 구성되지 않았습니다. 대화 상자 표시 중...")

            // MessageDialogBuilder를 사용하여 대화 상자 표시 - 두 개의 버튼만 표시
            val result = MessageDialogBuilder.yesNo(
                JiraBranchBundle.message("notification.warning"),
                JiraBranchBundle.message("error.jira.not.configured")
            )
                .yesText(JiraBranchBundle.message("settings.title"))
                .noText(Messages.getCancelButton())
                .icon(Messages.getQuestionIcon())
                .ask(project)

            if (result) {
                // 설정 화면으로 이동
                ApplicationManager.getApplication().invokeAndWait {
                    ShowSettingsUtil.getInstance().showSettingsDialog(
                        project,
                        AppSettingsComponent::class.java
                    )
                }
                return settings.isJiraConfigured()
            } else {
                return false
            }
        }

        return true
    }

    /**
     * 현재 사용자에게 할당된 미해결 이슈 검색
     */
    fun getMyIssues(): List<JiraIssue> {
        // 서비스가 초기화되었는지 확인
        if (!ensureInitialized()) {
            LOG.warn("서비스가 아직 초기화되지 않았습니다. 이슈를 가져올 수 없습니다.")
            return emptyList()
        }

        if (!checkJiraSettings()) {
            return emptyList()
        }

        try {
            // 단순하고 안전한 JQL 쿼리 사용
            val jql = "assignee=currentUser() AND resolution=Unresolved ORDER BY updated DESC"
            LOG.info("JQL 쿼리 실행 중: $jql")
            val issues = searchIssues(jql)
            LOG.info("${issues.size}개의 이슈를 찾았습니다")
            return issues
        } catch (e: Exception) {
            LOG.error("이슈 가져오기 오류: ${e.message}", e)
            showNotification(
                JiraBranchBundle.message("error.jira.search"),
                e.message ?: JiraBranchBundle.message("error.jira.search.details"),
                NotificationType.ERROR
            )
            return emptyList()
        }
    }

    /**
     * 키워드로 이슈 검색 - 단순화된 쿼리 사용
     */
    fun searchIssuesByKeyword(keyword: String): List<JiraIssue> {
        // 서비스가 초기화되었는지 확인
        if (!ensureInitialized()) {
            LOG.warn("서비스가 아직 초기화되지 않았습니다. 이슈를 검색할 수 없습니다.")
            return emptyList()
        }

        if (!checkJiraSettings()) {
            return emptyList()
        }

        try {
            val sanitizedKeyword = keyword.replace("\"", "\\\"")

            // 한글을 포함한 복잡한 쿼리 대신 더 단순화된 쿼리 사용
            val jql = if (sanitizedKeyword.isNotEmpty()) {
                if (sanitizedKeyword.matches(Regex("[A-Z]+-\\d+"))) {
                    // 이슈 키 패턴인 경우 정확한 검색
                    "key = \"$sanitizedKeyword\" OR key ~ \"$sanitizedKeyword*\""
                } else {
                    // 그 외에는 요약(제목) 필드 검색 - 단순 검색 방식 사용
                    "summary ~ \"$sanitizedKeyword\""
                }
            } else {
                // 키워드가 없을 경우 할당된 이슈 검색
                "assignee = currentUser()"
            }

            // 상태 필터 추가 (필요시)
            val finalJql = if (jql.isNotEmpty()) {
                "($jql) AND resolution = Unresolved ORDER BY updated DESC"
            } else {
                "resolution = Unresolved ORDER BY updated DESC"
            }

            LOG.info("JQL 쿼리 실행 중: $finalJql")
            val issues = searchIssues(finalJql)
            LOG.info("'$keyword' 키워드와 일치하는 ${issues.size}개의 이슈를 찾았습니다")
            return issues
        } catch (e: Exception) {
            LOG.error("이슈 검색 오류: ${e.message}", e)

            // 오류 발생 시 재시도 로직 - 최대한 단순한 쿼리로 시도
            try {
                LOG.info("검색 실패 - 기본 쿼리로 재시도합니다")
                // 가장 기본적인 쿼리로 재시도
                val simpleJql = "resolution = Unresolved ORDER BY updated DESC"
                LOG.info("단순 JQL 쿼리 실행 중: $simpleJql")
                val issues = searchIssues(simpleJql)
                LOG.info("단순 쿼리로 ${issues.size}개의 이슈를 찾았습니다")
                return issues
            } catch (retryException: Exception) {
                LOG.error("단순 쿼리로도 실패: ${retryException.message}", retryException)
                showNotification(
                    JiraBranchBundle.message("error.jira.search"),
                    "검색 실패: ${retryException.message}",
                    NotificationType.ERROR
                )
                return emptyList()
            }
        }
    }

    /**
     * JQL로 이슈 검색 - Jira REST API v2 문서 기반으로 수정됨
     */
    private fun searchIssues(jql: String): List<JiraIssue> {
        try {
            val settings = AppSettingsState.getInstance()
            val jiraUrl = UrlUtils.sanitizeUrl(settings.jiraUrl)

            // URL 인코딩을 사용하여 JQL 쿼리 파라미터 준비
            val encodedJql = java.net.URLEncoder.encode(jql, "UTF-8")
            val endpoint = "$jiraUrl/rest/api/2/search?jql=$encodedJql&startAt=0&maxResults=50&fields=summary,description,status,assignee,issuetype,priority"

            LOG.debug("JIRA 검색 요청: GET $endpoint")
            val response = executeGetRequest(endpoint)

            if (response.first !in 200..299) {
                LOG.error("JQL 검색 실행 오류: $jql, HTTP ${response.first}, 응답: ${response.second}")

                // 오류 응답 본문에서 자세한 오류 메시지 추출 시도
                var errorDetails = "API 요청 실패: HTTP ${response.first}"
                try {
                    val errorJson = JsonParser.parseString(response.second).asJsonObject
                    if (errorJson.has("errorMessages") && errorJson.getAsJsonArray("errorMessages").size() > 0) {
                        errorDetails = errorJson.getAsJsonArray("errorMessages").first().asString
                    } else if (errorJson.has("errors") && !errorJson.getAsJsonObject("errors").entrySet().isEmpty()) {
                        errorDetails = errorJson.getAsJsonObject("errors").entrySet().first().value.asString
                    }
                } catch (e: Exception) {
                    // JSON 파싱 실패 시 기본 메시지 사용
                    LOG.warn("오류 응답 파싱 실패: ${e.message}")
                }

                throw Exception(errorDetails)
            }

            // 응답이 성공적으로 왔지만 내용이 비어있는지 확인
            if (response.second.isBlank()) {
                LOG.warn("JIRA API 응답이 비어 있습니다.")
                return emptyList()
            }

            // 응답 디버그 로깅 - 처음 100자만 표시
            val previewLength = minOf(100, response.second.length)
            LOG.debug("JIRA API 응답 미리보기 (처음 ${previewLength}자): ${response.second.substring(0, previewLength)}...")

            val issues = parseSearchResult(response.second)
            LOG.info("파싱된 이슈 수: ${issues.size}")
            return issues
        } catch (e: Exception) {
            LOG.error("JQL 검색 실행 오류: $jql", e)
            throw e
        }
    }

    /**
     * 검색 결과 JSON 파싱 - Jira Cloud REST API v2에 맞게 수정
     */
    private fun parseSearchResult(jsonString: String): List<JiraIssue> {
        try {
            val jsonObject = JsonParser.parseString(jsonString).asJsonObject

            // 디버그 로깅 - 전체 응답 구조 확인
            LOG.debug("JSON 응답 파싱 시작: ${jsonObject.keySet()}")
            LOG.debug("총 이슈 수: ${jsonObject.get("total")?.asInt}")

            val issues = jsonObject.getAsJsonArray("issues") ?: return emptyList()
            LOG.debug("이슈 배열 크기: ${issues.size()}")

            if (issues.size() == 0) {
                LOG.warn("검색 결과에 이슈가 없습니다.")
                return emptyList()
            }

            return issues.map { issueJson ->
                try {
                    val issue = issueJson.asJsonObject
                    val key = issue.get("key").asString
                    val fields = issue.getAsJsonObject("fields")

                    LOG.debug("이슈 키 파싱: $key, 필드 키셋: ${fields.keySet()}")

                    val summary = fields.get("summary")?.asString

                    // description이 null일 경우 처리
                    val description = if (fields.has("description") && !fields.get("description").isJsonNull) {
                        fields.get("description").asString
                    } else null

                    val status = if (fields.has("status") && !fields.get("status").isJsonNull) {
                        fields.getAsJsonObject("status").get("name").asString
                    } else null

                    val assignee = if (fields.has("assignee") && !fields.get("assignee").isJsonNull) {
                        fields.getAsJsonObject("assignee").get("displayName").asString
                    } else null

                    val issueType = if (fields.has("issuetype") && !fields.get("issuetype").isJsonNull) {
                        fields.getAsJsonObject("issuetype").get("name").asString
                    } else null

                    val priority = if (fields.has("priority") && !fields.get("priority").isJsonNull) {
                        fields.getAsJsonObject("priority").get("name").asString
                    } else null

                    val settings = AppSettingsState.getInstance()
                    val url = "${UrlUtils.sanitizeUrl(settings.jiraUrl)}/browse/$key"

                    LOG.debug("이슈 파싱 완료: $key - $summary")

                    JiraIssue(
                        key = key,
                        summary = summary,
                        description = description,
                        status = status,
                        assignee = assignee,
                        issueType = issueType,
                        priority = priority,
                        url = url
                    )
                } catch (e: Exception) {
                    LOG.error("개별 이슈 파싱 중 오류: ${e.message}", e)
                    // 개별 이슈 파싱 오류시에도 전체 프로세스가 중단되지 않도록 null 반환
                    null
                }
            }.filterNotNull() // null 항목 제거
        } catch (e: Exception) {
            LOG.error("JSON 파싱 오류: ${e.message}", e)
            throw e
        }
    }

    /**
     * 이슈 JSON 파싱
     */
    private fun parseIssueJson(jsonString: String): JiraIssue? {
        try {
            val issue = JsonParser.parseString(jsonString).asJsonObject
            val key = issue.get("key").asString
            val fields = issue.getAsJsonObject("fields")

            val summary = fields.get("summary")?.asString
            val description = fields.get("description")?.asString

            val status = if (fields.has("status") && !fields.get("status").isJsonNull) {
                fields.getAsJsonObject("status").get("name").asString
            } else null

            val assignee = if (fields.has("assignee") && !fields.get("assignee").isJsonNull) {
                fields.getAsJsonObject("assignee").get("displayName").asString
            } else null

            val issueType = if (fields.has("issuetype") && !fields.get("issuetype").isJsonNull) {
                fields.getAsJsonObject("issuetype").get("name").asString
            } else null

            val priority = if (fields.has("priority") && !fields.get("priority").isJsonNull) {
                fields.getAsJsonObject("priority").get("name").asString
            } else null

            val settings = AppSettingsState.getInstance()
            val url = "${UrlUtils.sanitizeUrl(settings.jiraUrl)}/browse/$key"

            return JiraIssue(
                key = key,
                summary = summary,
                description = description,
                status = status,
                assignee = assignee,
                issueType = issueType,
                priority = priority,
                url = url
            )
        } catch (e: Exception) {
            LOG.error("JSON 파싱 오류: ${e.message}", e)
            return null
        }
    }

    /**
     * HTTP GET 요청 실행 - BasicAuth 인증 사용
     */
    private fun executeGetRequest(url: String): Pair<Int, String> {
        val settings = AppSettingsState.getInstance()
        val connection = URL(url).openConnection() as HttpURLConnection

        try {
            // 기본 설정
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000 // 타임아웃 증가
            connection.readTimeout = 15000    // 타임아웃 증가
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")

            // BasicAuth 인증 헤더 설정
            val authString = "${settings.jiraUsername}:${settings.jiraApiToken}"
            val encodedAuth = Base64.getEncoder().encodeToString(authString.toByteArray())
            connection.setRequestProperty("Authorization", "Basic $encodedAuth")

            // X-Atlassian-Token 헤더 추가(CSRF 보호)
            connection.setRequestProperty("X-Atlassian-Token", "no-check")

            // 디버그 로깅
            LOG.debug("JIRA API 요청 URL: $url")
            LOG.debug("JIRA API 요청 메소드: ${connection.requestMethod}")

            // 모든 요청 헤더 로깅 (보안 정보 제외)
            LOG.debug("JIRA API 요청 헤더: Content-Type: ${connection.getRequestProperty("Content-Type")}")
            LOG.debug("JIRA API 요청 헤더: Accept: ${connection.getRequestProperty("Accept")}")
            LOG.debug("JIRA API 요청 헤더: X-Atlassian-Token: ${connection.getRequestProperty("X-Atlassian-Token")}")
            LOG.debug("JIRA API 요청 헤더: Authorization: Basic [인증 정보 숨김]")

            // 응답 읽기
            val statusCode = connection.responseCode
            val responseMessage = connection.responseMessage
            LOG.debug("JIRA API 응답 코드: $statusCode ($responseMessage)")

            val inputStream = if (statusCode >= 400) connection.errorStream else connection.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val response = reader.use { it.readText() }

            // 오류 응답 로깅
            if (statusCode >= 400) {
                LOG.debug("JIRA API 오류 응답: $response")
            }

            return Pair(statusCode, response)
        } catch (e: Exception) {
            LOG.error("HTTP GET 요청 실패: ${e.message}", e)
            return Pair(500, """{"errorMessages":["${e.message?.replace("\"", "\\\"") ?: "알 수 없는 오류"}"]}""")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * HTTP POST 요청 실행 - BasicAuth 인증 사용
     */
    private fun executePostRequest(url: String, jsonPayload: String): Pair<Int, String> {
        val settings = AppSettingsState.getInstance()
        val connection = URL(url).openConnection() as HttpURLConnection

        try {
            // 기본 설정
            connection.requestMethod = "POST"
            connection.connectTimeout = 15000 // 타임아웃 증가
            connection.readTimeout = 15000    // 타임아웃 증가
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            connection.setRequestProperty("Accept", "application/json")

            // BasicAuth 인증 헤더 설정
            val authString = "${settings.jiraUsername}:${settings.jiraApiToken}"
            val encodedAuth = Base64.getEncoder().encodeToString(authString.toByteArray())
            connection.setRequestProperty("Authorization", "Basic $encodedAuth")

            // X-Atlassian-Token 헤더 추가(CSRF 보호)
            connection.setRequestProperty("X-Atlassian-Token", "no-check")

            // 디버그 로깅
            LOG.debug("JIRA API 요청 URL: $url")
            LOG.debug("JIRA API 요청 메소드: ${connection.requestMethod}")
            LOG.debug("JIRA API 요청 본문: $jsonPayload")

            // 모든 요청 헤더 로깅 (보안 정보 제외)
            LOG.debug("JIRA API 요청 헤더: Content-Type: ${connection.getRequestProperty("Content-Type")}")
            LOG.debug("JIRA API 요청 헤더: Accept: ${connection.getRequestProperty("Accept")}")
            LOG.debug("JIRA API 요청 헤더: X-Atlassian-Token: ${connection.getRequestProperty("X-Atlassian-Token")}")
            LOG.debug("JIRA API 요청 헤더: Authorization: Basic [인증 정보 숨김]")

            // 요청 본문 작성
            OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                writer.write(jsonPayload)
                writer.flush()
            }

            // 응답 읽기
            val statusCode = connection.responseCode
            val responseMessage = connection.responseMessage
            LOG.debug("JIRA API 응답 코드: $statusCode ($responseMessage)")

            val inputStream = if (statusCode >= 400) connection.errorStream else connection.inputStream
            val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
            val response = reader.use { it.readText() }

            // 오류 응답 로깅
            if (statusCode >= 400) {
                LOG.debug("JIRA API 오류 응답: $response")
            }

            return Pair(statusCode, response)
        } catch (e: Exception) {
            LOG.error("HTTP POST 요청 실패: ${e.message}", e)
            return Pair(500, """{"errorMessages":["${e.message?.replace("\"", "\\\"") ?: "알 수 없는 오류"}"]}""")
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 알림 표시
     */
    private fun showNotification(title: String, content: String, type: NotificationType) {
        try {
            ApplicationManager.getApplication().invokeLater {
                val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("Jira Branch Creator")
                notificationGroup.createNotification(title, content, type).notify(project)
            }
        } catch (e: Exception) {
            LOG.error("알림 표시 실패: $title - $content", e)
        }
    }

    /**
     * 서비스 종료 시 자원 해제
     */
    fun dispose() {
        executor.shutdown()
    }
}