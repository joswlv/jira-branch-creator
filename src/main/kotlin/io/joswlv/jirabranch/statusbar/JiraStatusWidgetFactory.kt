package io.joswlv.jirabranch.statusbar

import com.intellij.ide.BrowserUtil
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.util.Consumer
import io.joswlv.jirabranch.JiraBranchBundle
import io.joswlv.jirabranch.config.AppSettingsState
import io.joswlv.jirabranch.services.GitBranchChangeListener
import io.joswlv.jirabranch.services.GitService
import io.joswlv.jirabranch.services.JiraService
import java.awt.event.MouseEvent
import javax.swing.Icon

/**
 * 상태 바에 JIRA 상태 위젯을 표시하는 팩토리 클래스
 */
class JiraStatusWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "Jira.Branch.Widget"

    override fun getDisplayName(): String = JiraBranchBundle.message("name")

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = JiraStatusWidget(project)

    override fun disposeWidget(widget: StatusBarWidget) {}

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true

    /**
     * JIRA 상태 위젯 구현
     */
    private class JiraStatusWidget(private val project: Project) : StatusBarWidget, StatusBarWidget.IconPresentation {
        private val LOG = Logger.getInstance(this.javaClass)
        private val gitService = project.service<GitService>()
        private val jiraService = project.service<JiraService>()
        private var statusBar: StatusBar? = null
        private var currentIssueKey: String? = null

        override fun ID(): String = "Jira.Branch.Widget"

        override fun getTooltipText(): String {
            val issueKey = getIssueKeyFromCurrentBranch()
            return if (issueKey != null) {
                JiraBranchBundle.message("widget.tooltip", issueKey)
            } else {
                JiraBranchBundle.message("widget.no.issue")
            }
        }

        override fun getIcon(): Icon {
            return try {
                IconLoader.getIcon("/icons/jira.svg", javaClass)
            } catch (e: Exception) {
                // 아이콘 로드 실패 시 대체 아이콘 사용
                LOG.warn("Failed to load jira icon: ${e.message}")
                com.intellij.icons.AllIcons.General.Information
            }
        }

        override fun getPresentation(): StatusBarWidget.WidgetPresentation {
            // 여기서 반드시 this를 반환해야 함 (null 반환 금지)
            return this
        }

        override fun getClickConsumer(): Consumer<MouseEvent>? = Consumer { event ->
            val issueKey = getIssueKeyFromCurrentBranch() ?: return@Consumer
            try {
                val settings = AppSettingsState.getInstance()
                val issueUrl = "${settings.jiraUrl}/browse/$issueKey"
                BrowserUtil.browse(issueUrl)
            } catch (e: Exception) {
                LOG.warn("Failed to open issue in browser: ${e.message}")
            }
        }

        override fun install(statusBar: StatusBar) {
            this.statusBar = statusBar

            // Git 브랜치 변경 이벤트 구독
            try {
                project.messageBus.connect().subscribe(
                    GitBranchChangeListener.TOPIC,
                    object : GitBranchChangeListener {
                        override fun branchChanged() {
                            updateWidget()
                        }
                    }
                )
            } catch (e: Exception) {
                LOG.warn("Failed to subscribe to branch change events: ${e.message}")
            }

            // 초기 상태 업데이트
            updateWidget()
        }

        override fun dispose() {
            statusBar = null
        }

        /**
         * 위젯 상태 업데이트
         */
        private fun updateWidget() {
            val newIssueKey = getIssueKeyFromCurrentBranch()
            if (newIssueKey != currentIssueKey) {
                currentIssueKey = newIssueKey
                statusBar?.updateWidget(ID())
            }
        }

        /**
         * 현재 브랜치에서 JIRA 이슈 키 추출
         */
        private fun getIssueKeyFromCurrentBranch(): String? {
            try {
                val currentBranch = gitService.getCurrentBranch() ?: return null
                return gitService.extractIssueKeyFromBranch(currentBranch)
            } catch (e: Exception) {
                LOG.warn("Failed to get issue key: ${e.message}")
                return null
            }
        }
    }
}