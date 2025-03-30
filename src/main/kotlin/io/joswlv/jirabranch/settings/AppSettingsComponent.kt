package io.joswlv.jirabranch.settings

import com.intellij.ide.BrowserUtil
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.RowLayout
import com.intellij.ui.dsl.builder.panel
import io.joswlv.jirabranch.JiraBranchBundle
import io.joswlv.jirabranch.config.AppSettingsState
import io.joswlv.jirabranch.dialog.TokenSetupDialog
import java.net.HttpURLConnection
import java.net.URI
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
        // 제외 상태 목록은 동적으로 수정되므로 여기서 확인할 필요 없음
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
        val jiraUrl = jiraUrlField?.text.orEmpty()
        if (jiraUrl.isBlank()) {
            showNotification(
                "JIRA Connection Test",
                "JIRA URL is empty. Please provide a valid URL.",
                NotificationType.ERROR
            )
            return
        }

        try {
            // URI를 기반으로 URL 생성 (Deprecated URL(String) 생성자 제거)
            val uri = URI(jiraUrl)
            val url = uri.toURL()

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                showNotification(
                    "JIRA Connection Test",
                    "Successfully connected to JIRA at: $jiraUrl",
                    NotificationType.INFORMATION
                )
            } else {
                showNotification(
                    "JIRA Connection Test",
                    "Failed to connect to JIRA. Response Code: $responseCode",
                    NotificationType.WARNING
                )
            }
        } catch (e: Exception) {
            LOG.error("Error testing JIRA connection for URL: $jiraUrl", e)
            showNotification(
                "JIRA Connection Test",
                "Failed to connect to JIRA. Error: ${e.message}",
                NotificationType.ERROR
            )
        }
    }


    /**
     * 알림 표시
     */
    private fun showNotification(title: String, content: String, type: NotificationType) {
        val notificationGroup = NotificationGroupManager.getInstance().getNotificationGroup("Jira Branch Creator")
        notificationGroup.createNotification(title, content, type).notify(ProjectManager.getInstance().defaultProject)
    }
}