package io.joswlv.jirabranch.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.VcsException
import git4idea.GitUtil
import io.joswlv.jirabranch.JiraBranchBundle
import io.joswlv.jirabranch.dialog.CommitMessageDialog
import io.joswlv.jirabranch.services.GitService
import io.joswlv.jirabranch.services.JiraService

/**
 * JIRA 이슈 키를 포함한 커밋을 생성하는 액션
 */
class CreateCommitAction : AnAction(), DumbAware {
    private val LOG = Logger.getInstance(CreateCommitAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val gitService = GitService.getInstance(project)
        val jiraService = JiraService.getInstance(project)

        LOG.info("JIRA 이슈 키를 포함한 커밋 생성 액션 실행")

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

        // 변경사항이 있는지 확인
        if (!gitService.hasChanges()) {
            Messages.showInfoMessage(
                project,
                JiraBranchBundle.message("info.git.no.changes"),
                JiraBranchBundle.message("notification.info")
            )
            return
        }

        // JIRA 서비스 초기화 확인
        if (!jiraService.ensureInitialized()) {
            LOG.warn("JiraService가 초기화되지 않았습니다.")
            showNotification(
                project,
                JiraBranchBundle.message("notification.error"),
                JiraBranchBundle.message("error.jira.not.configured"),
                NotificationType.ERROR
            )
            return
        }

        // 현재 브랜치에서 JIRA 이슈 키 추출
        val currentBranch = gitService.getCurrentBranch()
        val issueKey = gitService.extractIssueKeyFromBranch(currentBranch)

        if (issueKey == null) {
            Messages.showErrorDialog(
                project,
                JiraBranchBundle.message("error.no.issue.key", currentBranch ?: ""),
                JiraBranchBundle.message("notification.error")
            )
            return
        }

        // 커밋 메시지 입력 다이얼로그 표시
        val commitDialog = CommitMessageDialog(project, issueKey)
        if (!commitDialog.showAndGet()) {
            return // 사용자가 취소함
        }

        // 커밋 메시지 가져오기
        val commitMessage = commitDialog.getCommitMessage()

        // 백그라운드에서 커밋 실행
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            JiraBranchBundle.message("task.committing.changes"),
            true
        ) {
            private var success = false
            private var errorMessage: String? = null

            override fun run(indicator: com.intellij.openapi.progress.ProgressIndicator) {
                try {
                    indicator.isIndeterminate = false
                    indicator.text = JiraBranchBundle.message("task.adding.files")
                    indicator.fraction = 0.3

                    // 첫 번째 단계: 파일 추가
                    indicator.text = JiraBranchBundle.message("task.committing")
                    indicator.fraction = 0.7

                    // 두 번째 단계: 커밋
                    success = gitService.commitChanges(commitMessage)
                } catch (e: VcsException) {
                    LOG.error("커밋 실패: ${e.message}", e)
                    errorMessage = e.message
                }
            }

            override fun onSuccess() {
                if (errorMessage != null) {
                    showNotification(
                        project,
                        JiraBranchBundle.message("notification.error"),
                        JiraBranchBundle.message("error.git.commit", errorMessage ?: ""),
                        NotificationType.ERROR
                    )
                }
            }
        })
    }

    /**
     * 이슈 키의 숫자 부분을 4자리로 패딩 처리
     */
    private fun formatIssueKey(issueKey: String): String {
        val keyParts = issueKey.split("-", limit = 2)

        if (keyParts.size == 2) {
            val projectCode = keyParts[0]
            val issueNumber = keyParts[1]

            try {
                // 숫자 부분을 정수로 변환하여 처리
                val numericPart = issueNumber.toInt()

                // 4자리 미만인 경우 0으로 패딩, 4자리 이상이면 그대로 유지
                val formattedNumber = if (numericPart < 10000) {
                    String.format("%04d", numericPart)
                } else {
                    numericPart.toString()
                }

                // 새 이슈 키 형식 생성
                return "$projectCode-$formattedNumber"
            } catch (e: NumberFormatException) {
                // 숫자 변환 실패 시 원본 이슈 키 사용
                LOG.warn("이슈 번호 형식 변환 실패: $issueNumber - 원본 키 사용")
            }
        }

        // 분리 실패 시 원본 이슈 키 사용
        return issueKey
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