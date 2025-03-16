package io.joswlv.jirabranch.services

import com.intellij.util.messages.Topic

/**
 * Git 브랜치 변경 이벤트를 처리하는 리스너 인터페이스
 */
interface GitBranchChangeListener {
    /**
     * 브랜치가 변경되었을 때 호출
     */
    fun branchChanged()

    companion object {
        val TOPIC = Topic.create("Git Branch Changed", GitBranchChangeListener::class.java)
    }
}