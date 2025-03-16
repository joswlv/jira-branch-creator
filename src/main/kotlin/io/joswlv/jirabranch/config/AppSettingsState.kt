package io.joswlv.jirabranch.config

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * 플러그인 설정 상태를 관리하는 서비스
 */
@Service(Service.Level.APP)
@State(
    name = "io.joswlv.jirabranch.config.AppSettingsState",
    storages = [Storage("JiraBranchCreatorSettings.xml")]
)
class AppSettingsState : PersistentStateComponent<AppSettingsState> {
    // JIRA 설정
    var jiraUrl: String = ""
    var jiraUsername: String = ""
    var jiraApiToken: String = ""

    // GitHub 설정
    var githubToken: String = ""
    var defaultBaseBranch: String = "main"

    // 브랜치 형식 설정
    var branchFormat: String = "feat"

    // 제외할 이슈 상태 목록
    var excludedStatuses: MutableList<String> = mutableListOf("Done", "Resolved", "Closed")

    /**
     * JIRA가 설정되어 있는지 확인
     */
    fun isJiraConfigured(): Boolean {
        return jiraUrl.isNotEmpty() && jiraUsername.isNotEmpty() && jiraApiToken.isNotEmpty()
    }

    /**
     * GitHub가 설정되어 있는지 확인
     */
    fun isGithubConfigured(): Boolean {
        return githubToken.isNotEmpty()
    }

    /**
     * 브랜치 이름에서 이슈 키 추출
     */
    fun extractIssueKeyFromBranch(branchName: String): String? {
        // JIRA 이슈 키 패턴 (예: ABC-123)
        val pattern = java.util.regex.Pattern.compile("[A-Z]+-\\d+")
        val matcher = pattern.matcher(branchName)

        return if (matcher.find()) {
            matcher.group()
        } else {
            null
        }
    }

    override fun getState(): AppSettingsState = this

    override fun loadState(state: AppSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        /**
         * 싱글톤 인스턴스 반환 (안전하게 처리)
         */
        @JvmStatic
        fun getInstance(): AppSettingsState {
            try {
                return ApplicationManager.getApplication().getService(AppSettingsState::class.java)
                    ?: throw IllegalStateException("AppSettingsState service is not registered")
            } catch (e: Exception) {
                // 서비스를 가져올 수 없는 경우 임시 인스턴스 생성 (기본값 포함)
                println("Could not get AppSettingsState service: ${e.message}. Creating temporary instance.")
                return AppSettingsState()
            }
        }
    }
}