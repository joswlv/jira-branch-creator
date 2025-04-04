package io.joswlv.jirabranch.services

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsException
import git4idea.GitUtil
import git4idea.branch.GitBrancher
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import io.joswlv.jirabranch.JiraBranchBundle
import io.joswlv.jirabranch.config.AppSettingsState
import io.joswlv.jirabranch.model.JiraIssue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


/**
 * Git 작업을 처리하는 서비스 클래스
 */
@Service(Service.Level.PROJECT)
class GitService(private val project: Project) {
    private val LOG = Logger.getInstance(GitService::class.java)

    companion object {
        @JvmStatic
        fun getInstance(project: Project): GitService {
            return project.getService(GitService::class.java)
        }
    }

    /**
     * JIRA 이슈 기반으로 브랜치 이름 생성
     * 이슈 번호의 숫자 부분을 4자리로 패딩 처리
     */
    fun createBranchName(issue: JiraIssue): String {
        val settings = AppSettingsState.getInstance()

        val issueKey = issue.key
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
                val formattedIssueKey = "$projectCode-$formattedNumber"

                // 브랜치 형식에 적용
                return "${settings.branchFormat}/${formattedIssueKey}"
            } catch (e: NumberFormatException) {
                // 숫자 변환 실패 시 원본 이슈 키 사용
                LOG.warn("이슈 번호 형식 변환 실패: $issueNumber - 원본 키 사용")
                return "${settings.branchFormat}/${issueKey}"
            }
        }

        // 분리 실패 시 원본 이슈 키 사용
        return "${settings.branchFormat}/${issueKey}"
    }

    fun commitChanges(commitMessage: String): Boolean {
        val application = ApplicationManager.getApplication()

        if (application.isDispatchThread) {
            var result = false
            val latch = CountDownLatch(1)

            application.executeOnPooledThread {
                try {
                    result = doCommitChanges(commitMessage)
                } finally {
                    latch.countDown()
                }
            }

            try {
                if (!latch.await(30, TimeUnit.SECONDS)) {
                    LOG.warn("커밋 작업 시간 초과")
                    return false
                }
            } catch (e: InterruptedException) {
                LOG.warn("커밋 작업이 중단됨", e)
                return false
            }

            return result
        } else {
            return doCommitChanges(commitMessage)
        }
    }

    private fun doCommitChanges(commitMessage: String): Boolean {
        try {
            val repositories = GitUtil.getRepositories(project) as List<GitRepository>
            if (repositories.isEmpty()) {
                showNotification(
                    JiraBranchBundle.message("notification.error"),
                    JiraBranchBundle.message("error.git.repo"),
                    NotificationType.ERROR
                )
                return false
            }

            val repository = repositories[0]

            // 변경사항이 있는지 확인
            if (!hasChanges()) {
                LOG.info("커밋할 변경사항이 없습니다.")
                showNotification(
                    JiraBranchBundle.message("notification.info"),
                    JiraBranchBundle.message("info.git.no.changes"),
                    NotificationType.INFORMATION
                )
                return false
            }

            // git add .
            val addHandler = GitLineHandler(project, repository.root, GitCommand.ADD)
            addHandler.addParameters(".")
            val addResult = Git.getInstance().runCommand(addHandler)

            if (!addResult.success()) {
                LOG.error("Git add 실패: ${addResult.errorOutputAsJoinedString}")
                showNotification(
                    JiraBranchBundle.message("notification.error"),
                    JiraBranchBundle.message("error.git.add", addResult.errorOutputAsJoinedString),
                    NotificationType.ERROR
                )
                return false
            }

            // git commit -m "message"
            val commitHandler = GitLineHandler(project, repository.root, GitCommand.COMMIT)
            commitHandler.addParameters("-m", commitMessage)
            val commitResult = Git.getInstance().runCommand(commitHandler)

            if (!commitResult.success()) {
                LOG.error("Git commit 실패: ${commitResult.errorOutputAsJoinedString}")
                showNotification(
                    JiraBranchBundle.message("notification.error"),
                    JiraBranchBundle.message("error.git.commit", commitResult.errorOutputAsJoinedString),
                    NotificationType.ERROR
                )
                return false
            }

            showNotification(
                JiraBranchBundle.message("notification.commit.created"),
                JiraBranchBundle.message("notification.commit.created.msg", commitMessage),
                NotificationType.INFORMATION
            )
            return true
        } catch (e: VcsException) {
            LOG.error("Git 커밋 실패: ${e.message}", e)
            showNotification(
                JiraBranchBundle.message("notification.error"),
                JiraBranchBundle.message("error.git.commit", e.message),
                NotificationType.ERROR
            )
            return false
        }
    }

    /**
     * Git 저장소에 변경사항이 있는지 확인
     * @return 변경사항이 있으면 true, 없으면 false
     */
    fun hasChanges(): Boolean {
        // EDT에서 실행되지 않도록 처리
        val application = ApplicationManager.getApplication()

        if (application.isDispatchThread) {
            var result = false
            val latch = CountDownLatch(1)

            application.executeOnPooledThread {
                try {
                    result = doHasChanges()
                } finally {
                    latch.countDown()
                }
            }

            try {
                if (!latch.await(10, TimeUnit.SECONDS)) {
                    LOG.warn("변경사항 확인 시간 초과")
                    return false
                }
            } catch (e: InterruptedException) {
                LOG.warn("변경사항 확인이 중단됨", e)
                return false
            }

            return result
        } else {
            return doHasChanges()
        }
    }

    private fun doHasChanges(): Boolean {
        try {
            val repository = getRepository() ?: return false

            // git status --porcelain 명령 실행
            val statusHandler = GitLineHandler(project, repository.root, GitCommand.STATUS)
            statusHandler.addParameters("--porcelain")
            val statusResult = Git.getInstance().runCommand(statusHandler)

            if (!statusResult.success()) {
                LOG.error("Git status 확인 실패: ${statusResult.errorOutputAsJoinedString}")
                showNotification(
                    JiraBranchBundle.message("notification.error"),
                    JiraBranchBundle.message("error.git.status", statusResult.errorOutputAsJoinedString),
                    NotificationType.ERROR
                )
                return false
            }

            // 변경사항이 있는지 확인 (출력이 비어있지 않으면 변경사항이 있음)
            return statusResult.output.isNotEmpty()
        } catch (e: Exception) {
            LOG.error("변경사항 확인 실패: ${e.message}", e)
            return false
        }
    }

    /**
     * 현재 브랜치 이름 가져오기
     */
    @Suppress("UNCHECKED_CAST")
    fun getCurrentBranch(): String? {
        val application = ApplicationManager.getApplication()

        if (application.isDispatchThread) {
            var result: String? = null
            val latch = CountDownLatch(1)

            application.executeOnPooledThread {
                try {
                    result = doGetCurrentBranch()
                } finally {
                    latch.countDown()
                }
            }

            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    LOG.warn("브랜치 조회 시간 초과")
                    return null
                }
            } catch (e: InterruptedException) {
                LOG.warn("브랜치 조회가 중단됨", e)
                return null
            }

            return result
        } else {
            return doGetCurrentBranch()
        }
    }

    private fun doGetCurrentBranch(): String? {
        try {
            val repositories = GitUtil.getRepositories(project) as List<GitRepository>
            if (repositories.isEmpty()) {
                return null
            }

            val repository = repositories[0]
            return repository.currentBranch?.name
        } catch (e: Exception) {
            LOG.error("현재 브랜치 가져오기 실패: ${e.message}", e)
            return null
        }
    }

    /**
     * 브랜치 이름에서 JIRA 이슈 키 추출
     */
    fun extractIssueKeyFromBranch(branchName: String?): String? {
        if (branchName == null) return null

        val settings = AppSettingsState.getInstance()
        return settings.extractIssueKeyFromBranch(branchName)
    }

    /**
     * 저장소 가져오기
     */
    @Suppress("UNCHECKED_CAST")
    fun getRepository(): GitRepository? {
        val application = ApplicationManager.getApplication()

        if (application.isDispatchThread) {
            var result: GitRepository? = null
            val latch = CountDownLatch(1)

            application.executeOnPooledThread {
                try {
                    result = doGetRepository()
                } finally {
                    latch.countDown()
                }
            }

            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    LOG.warn("저장소 조회 시간 초과")
                    return null
                }
            } catch (e: InterruptedException) {
                LOG.warn("저장소 조회가 중단됨", e)
                return null
            }

            return result
        } else {
            return doGetRepository()
        }
    }

    private fun doGetRepository(): GitRepository? {
        try {
            val repositories = GitUtil.getRepositories(project) as List<GitRepository>
            if (repositories.isEmpty()) {
                showNotification(
                    JiraBranchBundle.message("notification.error"),
                    JiraBranchBundle.message("error.git.repo"),
                    NotificationType.ERROR
                )
                return null
            }
            return repositories[0]
        } catch (e: Exception) {
            LOG.error("Git 저장소 가져오기 실패: ${e.message}", e)
            return null
        }
    }

    /**
     * 브랜치가 존재하는지 확인
     */
    fun branchExists(branchName: String, repository: GitRepository): Boolean {
        // EDT에서 실행되지 않도록 처리
        val application = ApplicationManager.getApplication()

        if (application.isDispatchThread) {
            var result = false
            val latch = CountDownLatch(1)

            application.executeOnPooledThread {
                try {
                    result = doBranchExists(branchName, repository)
                } finally {
                    latch.countDown()
                }
            }

            try {
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    LOG.warn("브랜치 존재 확인 시간 초과")
                    return false
                }
            } catch (e: InterruptedException) {
                LOG.warn("브랜치 존재 확인이 중단됨", e)
                return false
            }

            return result
        } else {
            return doBranchExists(branchName, repository)
        }
    }

    private fun doBranchExists(branchName: String, repository: GitRepository): Boolean {
        return repository.branches.localBranches.any { it.name == branchName } ||
                repository.branches.remoteBranches.any { it.nameForLocalOperations == branchName }
    }

    /**
     * 브랜치 생성 및 체크아웃 (브랜치가 이미 존재하면 체크아웃만 수행)
     * 새 브랜치 생성 시 defaultBaseBranch로 먼저 체크아웃한 후 새 브랜치 생성
     */
    fun createAndCheckoutBranch(branchName: String) {
        ApplicationManager.getApplication().executeOnPooledThread {
            doCreateAndCheckoutBranch(branchName)
        }
    }

    private fun doCreateAndCheckoutBranch(branchName: String) {
        try {
            val repository = getRepository() ?: return
            val brancher = GitBrancher.getInstance(project)
            val settings = AppSettingsState.getInstance()

            // 브랜치가 이미 존재하는지 확인
            if (branchExists(branchName, repository)) {
                LOG.info("브랜치 '$branchName'가 이미 존재합니다. 체크아웃만 수행합니다.")

                // 이미 있는 브랜치로 체크아웃
                brancher.checkout(branchName, false, listOf(repository)) {
                    // 체크아웃 성공 후 이벤트 발생
                    ApplicationManager.getApplication().invokeLater {
                        project.messageBus.syncPublisher(GitBranchChangeListener.TOPIC).branchChanged()

                        showNotification(
                            JiraBranchBundle.message("notification.branch.checkout"),
                            JiraBranchBundle.message("notification.branch.checkout.existing.msg", branchName),
                            NotificationType.INFORMATION
                        )
                    }
                }
            } else {
                // 기준 브랜치 가져오기
                val baseBranch = settings.defaultBaseBranch
                LOG.info("'$baseBranch' 브랜치를 기준으로 '$branchName' 브랜치를 생성합니다.")

                // 1. 먼저 기준 브랜치로 체크아웃
                brancher.checkout(baseBranch, false, listOf(repository)) {
                    // 2. 기준 브랜치에서 새 브랜치 생성
                    brancher.checkoutNewBranch(branchName, listOf(repository))

                    // 브랜치 변경 이벤트 발생
                    ApplicationManager.getApplication().invokeLater {
                        project.messageBus.syncPublisher(GitBranchChangeListener.TOPIC).branchChanged()

                        showNotification(
                            JiraBranchBundle.message("notification.branch.created"),
                            JiraBranchBundle.message("notification.branch.created.msg", branchName),
                            NotificationType.INFORMATION
                        )
                    }
                }
            }
        } catch (e: Exception) {
            LOG.error("브랜치 생성/체크아웃 실패: ${e.message}", e)
            showNotification(
                JiraBranchBundle.message("notification.error"),
                JiraBranchBundle.message("error.git.branch", e.message ?: ""),
                NotificationType.ERROR
            )
        }
    }

    /**
     * 알림 표시
     */
    private fun showNotification(title: String, content: String, type: NotificationType) {
        try {
            ApplicationManager.getApplication().invokeLater {
                val notificationGroup =
                    NotificationGroupManager.getInstance().getNotificationGroup("Jira Branch Creator")
                notificationGroup.createNotification(title, content, type).notify(project)
            }
        } catch (e: Exception) {
            LOG.error("알림 표시 실패: $title - $content", e)
        }
    }
}