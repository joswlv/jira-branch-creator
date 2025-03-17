package io.joswlv.jirabranch.startup

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManagerListener
import io.joswlv.jirabranch.services.JiraService

/**
 * 프로젝트 열림 이벤트를 리스닝하여 서비스 초기화를 안전하게 처리합니다
 */
class JiraBranchProjectListener : ProjectManagerListener {
    private val LOG = Logger.getInstance(JiraBranchProjectListener::class.java)

    override fun projectOpened(project: Project) {
        LOG.info("프로젝트 열림: ${project.name}")

        // 안전하게 서비스를 가져와 초기화
        ApplicationManager.getApplication().invokeLater {
            try {
                val jiraService = project.service<JiraService>()
            } catch (e: Exception) {
                LOG.error("JiraService 초기화 중 오류 발생", e)
            }
        }
    }
}