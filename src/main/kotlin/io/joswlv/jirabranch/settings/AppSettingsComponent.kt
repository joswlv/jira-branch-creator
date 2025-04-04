package io.joswlv.jirabranch.settings

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import io.joswlv.jirabranch.JiraBranchBundle
import io.joswlv.jirabranch.config.AppSettingsState
import io.joswlv.jirabranch.dialog.TokenSetupDialog
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.util.Base64
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * 설정 화면 UI를 제공하는 클래스
 */
class AppSettingsComponent : Configurable {
    private val LOG = Logger.getInstance(AppSettingsComponent::class.java)

    private var mainPanel: JPanel? = null
    private var jiraUrlField: JBTextField? = null
    private var jiraUsernameField: JBTextField? = null
    private var jiraApiTokenField: JBPasswordField? = null
    private var githubTokenField: JBPasswordField? = null
    private var defaultBaseBranchField: JBTextField? = null
    private var branchFormatField: JBTextField? = null

    override fun getDisplayName(): String = JiraBranchBundle.message("settings.title")

    override fun createComponent(): JComponent {
        val settings = AppSettingsState.getInstance()

        // UI 컴포넌트 초기화
        jiraUrlField = JBTextField(settings.jiraUrl)
        jiraUsernameField = JBTextField(settings.jiraUsername)
        jiraApiTokenField = JBPasswordField()
        jiraApiTokenField?.text = settings.jiraApiToken

        githubTokenField = JBPasswordField()
        githubTokenField?.text = settings.githubToken
        defaultBaseBranchField = JBTextField(settings.defaultBaseBranch)

        branchFormatField = JBTextField(settings.branchFormat)

        // 토큰 발급 버튼
        val getTokenButton = JButton(JiraBranchBundle.message("settings.jira.get.token")).apply {
            addActionListener { openTokenPage() }
        }

        // 로그아웃 버튼
        val logoutButton = JButton(JiraBranchBundle.message("settings.jira.logout")).apply {
            addActionListener { logoutFromJira() }
        }

        // 연결 테스트 버튼
        val testConnectionButton = JButton("Test Connection").apply {
            addActionListener { testJiraConnection() }
        }

        // UI 패널 생성
        mainPanel = panel {
            group(JiraBranchBundle.message("settings.jira.section")) {
                row {
                    label(JiraBranchBundle.message("settings.jira.url"))
                    comment(JiraBranchBundle.message("settings.jira.url.comment"))
                }.layout(RowLayout.INDEPENDENT)

                row {
                    cell(jiraUrlField!!)
                        .align(AlignX.FILL)
                        .resizableColumn()
                }.layout(RowLayout.INDEPENDENT)

                row {
                    label(JiraBranchBundle.message("settings.jira.username"))
                    comment(JiraBranchBundle.message("settings.jira.username.comment"))

                }.layout(RowLayout.INDEPENDENT)

                row {
                    cell(jiraUsernameField!!)
                        .align(AlignX.FILL)
                }.layout(RowLayout.INDEPENDENT)

                row {
                    label(JiraBranchBundle.message("settings.jira.token"))
                    cell(jiraApiTokenField!!)
                        .align(AlignX.FILL)
                }.layout(RowLayout.INDEPENDENT)

                row {
                    cell(getTokenButton)
                    cell(logoutButton)
                    cell(testConnectionButton)
                }
            }

            group(JiraBranchBundle.message("settings.github.section")) {
                row {
                    label(JiraBranchBundle.message("settings.github.token"))
                    cell(githubTokenField!!)
                        .align(AlignX.FILL)
                }.layout(RowLayout.INDEPENDENT)

                row {
                    label(JiraBranchBundle.message("settings.github.base.branch"))
                    comment(JiraBranchBundle.message("settings.github.base.branch.comment"))

                }.layout(RowLayout.INDEPENDENT)

                row {
                    cell(defaultBaseBranchField!!)
                        .align(AlignX.FILL)
                }.layout(RowLayout.INDEPENDENT)
            }

            group(JiraBranchBundle.message("settings.branch.section")) {
                row {
                    label(JiraBranchBundle.message("settings.branch.format"))
                    comment(JiraBranchBundle.message("settings.branch.format.help"))
                }

                row {
                    cell(branchFormatField!!)
                        .align(AlignX.FILL)
                }
            }.layout(RowLayout.INDEPENDENT)
        }

        return mainPanel!!
    }

    override fun isModified(): Boolean {
        val settings = AppSettingsState.getInstance()
        return jiraUrlField?.text != settings.jiraUrl ||
                jiraUsernameField?.text != settings.jiraUsername ||
                String(jiraApiTokenField?.password ?: CharArray(0)) != settings.jiraApiToken ||
                String(githubTokenField?.password ?: CharArray(0)) != settings.githubToken ||
                defaultBaseBranchField?.text != settings.defaultBaseBranch ||
                branchFormatField?.text != settings.branchFormat
    }

    override fun apply() {
        val settings = AppSettingsState.getInstance()
        settings.jiraUrl = jiraUrlField?.text ?: ""
        settings.jiraUsername = jiraUsernameField?.text ?: ""
        settings.jiraApiToken = String(jiraApiTokenField?.password ?: CharArray(0))
        settings.githubToken = String(githubTokenField?.password ?: CharArray(0))
        settings.defaultBaseBranch = defaultBaseBranchField?.text ?: "main"
        settings.branchFormat = branchFormatField?.text ?: "feat"

        LOG.info("설정이 저장되었습니다. 제외 상태: ${settings.excludedStatuses.joinToString(", ")}")
    }

    override fun reset() {
        val settings = AppSettingsState.getInstance()
        jiraUrlField?.text = settings.jiraUrl
        jiraUsernameField?.text = settings.jiraUsername
        jiraApiTokenField?.text = settings.jiraApiToken
        githubTokenField?.text = settings.githubToken
        defaultBaseBranchField?.text = settings.defaultBaseBranch
        branchFormatField?.text = settings.branchFormat
    }

    private fun openTokenPage() {
        // JIRA API 토큰 발급 페이지 열기
        BrowserUtil.browse("https://id.atlassian.com/manage-profile/security/api-tokens")

        // 토큰 입력 다이얼로그 표시
        val dialog = TokenSetupDialog()
        if (dialog.showAndGet()) {
            val token = dialog.getToken()
            if (token.isNotEmpty()) {
                jiraApiTokenField?.text = token
            }
        }
    }

    private fun logoutFromJira() {
        // JIRA 인증 정보 초기화
        jiraApiTokenField?.text = ""
    }

    /**
     * JIRA 연결 테스트 - 간단한 HTTP 요청으로 구현
     */
    private fun testJiraConnection() {
        // 현재 입력된 값 가져오기
        val url = jiraUrlField?.text ?: ""
        val username = jiraUsernameField?.text ?: ""
        val token = String(jiraApiTokenField?.password ?: CharArray(0))

        if (url.isEmpty() || username.isEmpty() || token.isEmpty()) {
            showMessageDialog(
                "Connection Test Failed",
                "Please fill in all JIRA connection fields (URL, Username, and API Token)",
            )
            return
        }

        // 백그라운드에서 테스트 실행
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            ProjectManager.getInstance().defaultProject,
            "Testing JIRA Connection",
            false
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = true
                indicator.text = "Connecting to JIRA..."

                try {
                    // REST API 엔드포인트 구성 (서버 정보 가져오기)
                    val apiUrl = if (url.endsWith("/")) url.dropLast(1) else url
                    val serverInfoUrl = "$apiUrl/rest/api/2/serverInfo"

                    // HTTP 연결 설정
                    val uri = URI(serverInfoUrl)
                    val connection = uri.toURL().openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 10000
                    connection.readTimeout = 10000

                    // 기본 인증 헤더 설정
                    val auth = "$username:$token"
                    val encodedAuth = Base64.getEncoder().encodeToString(auth.toByteArray())
                    connection.setRequestProperty("Authorization", "Basic $encodedAuth")
                    connection.setRequestProperty("Accept", "application/json")

                    // 응답 확인
                    val responseCode = connection.responseCode
                    if (responseCode in 200..299) {
                        // 성공
                        val reader = BufferedReader(InputStreamReader(connection.inputStream))
                        val response = reader.readText()
                        reader.close()

                        showMessageDialog(
                            "Connection Successful",
                            "Successfully connected to JIRA server!",
                        )
                    } else {
                        // 실패
                        val errorMessage = when (responseCode) {
                            401 -> "Authentication failed. Please check your username and API token."
                            403 -> "Permission denied. Your account may not have the necessary permissions."
                            404 -> "JIRA server not found. Please check the URL."
                            else -> "Failed with HTTP error code: $responseCode"
                        }

                        showMessageDialog(
                            "Connection Failed",
                            errorMessage
                        )
                    }
                } catch (e: Exception) {
                    showMessageDialog(
                        "Connection Failed",
                        "Could not connect to JIRA server: ${e.message}",
                    )
                }
            }
        })
    }

    /**
     * 알림 표시 (다이얼로그로 변경)
     */
    private fun showMessageDialog(title: String, content: String) {
        ApplicationManager.getApplication().invokeLater {
            Messages.showInfoMessage(content, title)
        }
    }
}