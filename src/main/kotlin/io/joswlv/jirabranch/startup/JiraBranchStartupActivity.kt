package io.joswlv.jirabranch.startup

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import io.joswlv.jirabranch.services.JiraService

class JiraBranchStartupActivity : StartupActivity {
    private val LOG = Logger.getInstance(JiraBranchStartupActivity::class.java)

    override fun runActivity(project: Project) {
        LOG.info("프로젝트 초기화 완료: ${project.name}")

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                project.service<JiraService>()
            } catch (e: Exception) {
                LOG.error("JiraService 초기화 중 오류 발생", e)
            }
        }
    }
}