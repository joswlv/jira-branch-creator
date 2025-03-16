package io.joswlv.jirabranch.model

/**
 * JIRA 이슈 정보를 담는 모델 클래스
 */
data class JiraIssue(
    val key: String,
    val summary: String?,
    val description: String?,
    val status: String?,
    val assignee: String?,
    val issueType: String?,
    val priority: String? = null,
    val url: String? = null
) {
    /**
     * 이슈가 해결되었는지 확인
     */
    fun isResolved(): Boolean {
        return status?.lowercase()?.contains("resolved") == true ||
                status?.lowercase()?.contains("closed") == true ||
                status?.lowercase()?.contains("done") == true
    }

    /**
     * 이슈의 표시 텍스트 반환
     */
    override fun toString(): String {
        return "$key: ${summary ?: "제목 없음"} (${status ?: "상태 없음"})"
    }
}