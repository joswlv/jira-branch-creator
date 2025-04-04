package io.joswlv.jirabranch.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import git4idea.GitUtil
import io.joswlv.jirabranch.JiraBranchBundle
import io.joswlv.jirabranch.config.AppSettingsState
import io.joswlv.jirabranch.dialog.JiraIssueSearchDialog
import io.joswlv.jirabranch.services.GitService
import io.joswlv.jirabranch.settings.AppSettingsComponent

/**
 * JIRA 이슈로부터 브랜치를 생성하는 액션
 */
class CreateBranchFromJiraAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = AppSettingsState.getInstance()

        // Git 저장소 확인
        if (GitUtil.getRepositories(project).isEmpty()) {
            showNotification(
                project,
                JiraBranchBundle.message("notification.error"),
                JiraBranchBundle.message("error.git.repo"),
                NotificationType.ERROR
            )
            return
        }

        // JIRA 설정 확인
        if (!settings.isJiraConfigured()) {
            handleUnconfiguredJira(project).let {
                if (it == DialogWrapper.CANCEL_EXIT_CODE) return
            }
        }

        showIssueSearchDialog(project)
    }

    private fun handleUnconfiguredJira(project: Project): Int {
        // MessageDialogBuilder를 사용하여 대화 상자 표시 - 두 개의 버튼만 표시
        val result = MessageDialogBuilder.yesNo(
            JiraBranchBundle.message("dialog.search.title"),
            JiraBranchBundle.message("error.jira.not.configured")
        )
            .yesText(JiraBranchBundle.message("settings.title"))
            .noText(Messages.getCancelButton())
            .icon(Messages.getQuestionIcon())
            .ask(project)

        return if (result) {
            // 설정 화면으로 이동
            ShowSettingsUtil.getInstance().showSettingsDialog(
                project,
                AppSettingsComponent::class.java
            )
            DialogWrapper.CANCEL_EXIT_CODE
        } else {
            DialogWrapper.CANCEL_EXIT_CODE
        }
    }

    private fun showIssueSearchDialog(project: Project) {
        val searchDialog = JiraIssueSearchDialog(project)
        if (!searchDialog.showAndGet()) {
            return // 사용자가 취소함
        }

        val selectedIssue = searchDialog.selectedIssue
        if (selectedIssue == null) {
            Messages.showErrorDialog(
                project,
                JiraBranchBundle.message("error.no.selection"),
                JiraBranchBundle.message("notification.error")
            )
            return
        }

        // Git 서비스를 통해 브랜치 이름 생성
        val gitService = GitService.getInstance(project)
        val branchName = gitService.createBranchName(selectedIssue)

        // 브랜치 이름 확인 다이얼로그
        val branchDialog = Messages.showInputDialog(
            project,
            JiraBranchBundle.message("dialog.branch.confirm"),
            JiraBranchBundle.message("dialog.branch.title"),
            Messages.getQuestionIcon(),
            branchName,
            object : com.intellij.openapi.ui.InputValidator {
                override fun checkInput(inputString: String): Boolean = inputString.isNotBlank()
                override fun canClose(inputString: String): Boolean = inputString.isNotBlank()
            }
        )

        if (branchDialog.isNullOrBlank()) {
            return
        }

        // 백그라운드에서 브랜치 생성
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            JiraBranchBundle.message("task.creating.branch"),
            true
        ) {
            private var success = false
            private var errorMessage: String? = null

            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                try {
                    indicator.isIndeterminate = false
                    indicator.fraction = 0.5
                    gitService.createAndCheckoutBranch(branchDialog)
                    success = true
                } catch (e: Exception) {
                    errorMessage = e.message
                }
            }

            override fun onSuccess() {
                if (errorMessage != null) {
                    showNotification(
                        project,
                        JiraBranchBundle.message("notification.error"),
                        errorMessage ?: JiraBranchBundle.message("error.git.branch"),
                        NotificationType.ERROR
                    )
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        // Git이 존재하는 프로젝트에서만 액션 활성화
        val project = e.project
        e.presentation.isEnabledAndVisible = project != null && !GitUtil.getRepositories(project).isEmpty()
    }

    private fun showNotification(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance().getNotificationGroup("Jira Branch Creator")
            .createNotification(title, content, type)
            .notify(project)
    }
}