package io.joswlv.jirabranch.startup

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import io.joswlv.jirabranch.services.JiraService


class JiraBranchPostStartupActivity : StartupActivity.DumbAware {
    private val LOG = Logger.getInstance(JiraBranchPostStartupActivity::class.java)

    override fun runActivity(project: Project) {
        LOG.info("JiraBranch 플러그인 시작 후 활동 실행 중...")

        val jiraService = project.getService(JiraService::class.java)

        jiraService.preInitialize()

        LOG.info("JiraBranch 시작 후 초기화 완료")
    }
}