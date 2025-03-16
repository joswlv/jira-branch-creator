package io.joswlv.jirabranch.toolwindow

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import io.joswlv.jirabranch.JiraBranchBundle
import io.joswlv.jirabranch.model.JiraIssue
import io.joswlv.jirabranch.services.GitBranchChangeListener
import io.joswlv.jirabranch.services.GitService
import io.joswlv.jirabranch.services.JiraService
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel

/**
 * JIRA 이슈를 표시하는 도구 창 팩토리
 */
class JiraBranchToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val jiraToolWindowContent = JiraBranchToolWindowContent(project, toolWindow)
        val content = ContentFactory.getInstance().createContent(
            jiraToolWindowContent.getContent(),
            JiraBranchBundle.message("toolwindow.title"),
            false
        )
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true

    inner class JiraBranchToolWindowContent(private val project: Project, private val toolWindow: ToolWindow) {
        private val jiraService = project.service<JiraService>()
        private val gitService = project.service<GitService>()

        private val issueList = JBList<JiraIssue>()
        private val listModel = DefaultListModel<JiraIssue>()
        private val issueFilter = JBLabel()

        fun getContent(): JPanel {
            val panel = JPanel(BorderLayout())
            panel.border = JBUI.Borders.empty(8)
            panel.preferredSize = Dimension(600, 400)

            // 헤더
            val headerPanel = JPanel(BorderLayout())
            headerPanel.border = JBUI.Borders.emptyBottom(8)
            headerPanel.add(JBLabel(JiraBranchBundle.message("toolwindow.my.issues")), BorderLayout.WEST)

            val refreshButton = JButton(JiraBranchBundle.message("toolwindow.refresh"))
            refreshButton.addActionListener {
                loadMyIssues()
            }
            headerPanel.add(refreshButton, BorderLayout.EAST)

            // 이슈 목록 설정
            issueList.model = listModel
            issueList.cellRenderer = JiraIssueListCellRenderer()
            issueList.selectionMode = ListSelectionModel.SINGLE_SELECTION
            issueList.emptyText.text = JiraBranchBundle.message("toolwindow.no.issues")

            // 액션 패널
            val actionPanel = JPanel(BorderLayout(10, 0))
            actionPanel.border = JBUI.Borders.emptyTop(8)

            val createBranchButton = JButton(JiraBranchBundle.message("toolwindow.create.branch"))
            createBranchButton.addActionListener {
                val selectedIssue = issueList.selectedValue ?: run {
                    Messages.showErrorDialog(
                        project,
                        JiraBranchBundle.message("error.no.selection"),
                        JiraBranchBundle.message("notification.error")
                    )
                    return@addActionListener
                }
                createBranchFromIssue(selectedIssue)
            }

            val openIssueButton = JButton(JiraBranchBundle.message("toolwindow.open.issue"))
            openIssueButton.addActionListener {
                val selectedIssue = issueList.selectedValue ?: return@addActionListener
                openIssueInBrowser(selectedIssue)
            }

            actionPanel.add(createBranchButton, BorderLayout.CENTER)
            actionPanel.add(openIssueButton, BorderLayout.EAST)

            // 패널 구성
            panel.add(headerPanel, BorderLayout.NORTH)
            panel.add(JBScrollPane(issueList), BorderLayout.CENTER)
            panel.add(actionPanel, BorderLayout.SOUTH)

            // 초기 데이터 로드
            loadMyIssues()

            return panel
        }

        private fun loadMyIssues() {
            listModel.clear()
            val loadingItem = JiraIssue(
                key = "",
                summary = JiraBranchBundle.message("toolwindow.loading"),
                description = null,
                status = "",
                assignee = null,
                issueType = ""
            )
            listModel.addElement(loadingItem)

            com.intellij.openapi.progress.ProgressManager.getInstance().run(
                object : com.intellij.openapi.progress.Task.Backgroundable(
                    project,
                    JiraBranchBundle.message("task.loading.issues"),
                    false
                ) {
                    override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                        try {
                            val issues = jiraService.getMyIssues()
                            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                                listModel.clear()
                                if (issues.isEmpty()) {
                                    issueList.emptyText.text = JiraBranchBundle.message("toolwindow.no.issues.found")
                                } else {
                                    issues.forEach { listModel.addElement(it) }
                                    issueList.selectedIndex = 0
                                }
                            }
                        } catch (e: Exception) {
                            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                                listModel.clear()
                                issueList.emptyText.text = JiraBranchBundle.message("error.jira.search.details")
                                showNotification(
                                    JiraBranchBundle.message("error.jira.search"),
                                    e.message ?: JiraBranchBundle.message("error.jira.search.details"),
                                    NotificationType.ERROR
                                )
                            }
                        }
                    }
                }
            )
        }

        private fun createBranchFromIssue(issue: JiraIssue) {
            val branchName = gitService.createBranchName(issue)

            val confirmBranchName = Messages.showInputDialog(
                project,
                JiraBranchBundle.message("dialog.branch.confirm"),
                JiraBranchBundle.message("dialog.branch.title"),
                Messages.getQuestionIcon(),
                branchName,
                object : com.intellij.openapi.ui.InputValidator {
                    override fun checkInput(inputString: String): Boolean = inputString.isNotBlank()
                    override fun canClose(inputString: String): Boolean = inputString.isNotBlank()
                }
            ) ?: return

            com.intellij.openapi.progress.ProgressManager.getInstance().run(
                object : com.intellij.openapi.progress.Task.Backgroundable(
                    project,
                    JiraBranchBundle.message("task.creating.branch"),
                    true
                ) {
                    override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                        try {
                            indicator.isIndeterminate = false
                            indicator.fraction = 0.5

                            val repository = gitService.getRepository() ?: return
                            val brancher = git4idea.branch.GitBrancher.getInstance(project)
                            brancher.checkoutNewBranch(confirmBranchName, listOf(repository))

                            // 브랜치 변경 이벤트 발생
                            project.messageBus.syncPublisher(GitBranchChangeListener.TOPIC).branchChanged()
                        } catch (e: Exception) {
                            com.intellij.openapi.application.ApplicationManager.getApplication().invokeLater {
                                Messages.showErrorDialog(
                                    project,
                                    JiraBranchBundle.message("error.git.branch") + ": " + e.message,
                                    JiraBranchBundle.message("notification.error")
                                )
                            }
                        }
                    }

                    override fun onSuccess() {
                        showNotification(
                            JiraBranchBundle.message("notification.branch.created"),
                            JiraBranchBundle.message("notification.branch.created.msg", confirmBranchName),
                            NotificationType.INFORMATION
                        )
                    }
                }
            )
        }

        private fun openIssueInBrowser(issue: JiraIssue) {
            issue.url?.let { com.intellij.ide.BrowserUtil.browse(it) }
        }

        private fun showNotification(title: String, content: String, type: NotificationType) {
            NotificationGroupManager.getInstance().getNotificationGroup("Jira Branch Creator")
                .createNotification(title, content, type)
                .notify(project)
        }

        /**
         * JIRA 이슈 셀 렌더러
         */
        private inner class JiraIssueListCellRenderer : com.intellij.ui.ColoredListCellRenderer<JiraIssue>() {
            override fun customizeCellRenderer(
                list: JList<out JiraIssue>,
                value: JiraIssue,
                index: Int,
                selected: Boolean,
                hasFocus: Boolean
            ) {
                // 이슈 타입에 따른 아이콘 설정
                when (value.issueType!!.lowercase()) {
                    "bug" -> setIcon(com.intellij.icons.AllIcons.General.BalloonError)
                    "task" -> setIcon(com.intellij.icons.AllIcons.General.TodoDefault)
                    "story" -> setIcon(com.intellij.icons.AllIcons.General.User)
                    else -> setIcon(com.intellij.icons.AllIcons.General.TodoQuestion)
                }

                append("${value.key}: ${value.summary}")

                // 이슈가 해결되었는지에 따라 다른 스타일 적용
                val statusStyle = if (value.isResolved()) {
                    com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES
                } else {
                    com.intellij.ui.SimpleTextAttributes.REGULAR_ATTRIBUTES
                }
                append(" [${value.status}]", statusStyle)

                // 우선순위가 있으면 표시
                value.priority?.let {
                    append(" ($it)", com.intellij.ui.SimpleTextAttributes.GRAY_ATTRIBUTES)
                }
            }
        }
    }
}