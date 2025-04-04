package io.joswlv.jirabranch.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import io.joswlv.jirabranch.JiraBranchBundle
import io.joswlv.jirabranch.model.JiraIssue
import io.joswlv.jirabranch.services.JiraService
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.event.DocumentEvent

/**
 * JIRA 이슈 검색 다이얼로그
 */
class JiraIssueSearchDialog(private val project: Project) : DialogWrapper(project) {
    private val jiraService = JiraService.getInstance(project)
    private val searchField = SearchTextField()
    private val issueList = JBList<JiraIssue>()
    private val listModel = CollectionListModel<JiraIssue>()
    private val searchAlarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, disposable)
    private val SEARCH_DELAY_MS = 300
    private var isLoading = false

    var selectedIssue: JiraIssue? = null
        private set

    init {
        title = JiraBranchBundle.message("dialog.search.title")
        init()
    }

    override fun createCenterPanel(): JComponent {
        // 이슈 목록 설정
        issueList.model = listModel
        issueList.cellRenderer = JiraIssueCellRenderer()
        issueList.selectionMode = ListSelectionModel.SINGLE_SELECTION
        issueList.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                selectedIssue = issueList.selectedValue
            }
        }
        issueList.emptyText.text = JiraBranchBundle.message("dialog.search.no.results")

        // 검색 필드 설정 - 타이핑 지연시간 추가로 성능 개선
        searchField.addDocumentListener(object : DocumentAdapter() {
            override fun textChanged(e: DocumentEvent) {
                searchAlarm.cancelAllRequests()
                searchAlarm.addRequest({ performSearch(searchField.text) }, SEARCH_DELAY_MS)
            }
        })

        // UI 패널 구성
        val panel = JPanel(BorderLayout())
        panel.preferredSize = Dimension(600, 400)
        panel.border = JBUI.Borders.empty(10)

        val searchPanel = JPanel(BorderLayout()).apply {
            add(JLabel(JiraBranchBundle.message("dialog.search.label")), BorderLayout.WEST)
            add(searchField, BorderLayout.CENTER)
            border = JBUI.Borders.emptyBottom(10)
        }

        val scrollPane = JBScrollPane(issueList)

        panel.add(searchPanel, BorderLayout.NORTH)
        panel.add(scrollPane, BorderLayout.CENTER)

        // 초기 이슈 목록 로드
        loadInitialIssues()

        return panel
    }

    override fun dispose() {
        Disposer.dispose(searchAlarm)
        super.dispose()
    }

    override fun getPreferredFocusedComponent() = searchField

    /**
     * 로딩 상태로 설정
     */
    private fun setLoading(loading: Boolean, message: String = "로딩 중...") {
        isLoading = loading
        if (loading) {
            listModel.removeAll()
            issueList.emptyText.text = message
        }
    }

    /**
     * 키워드로 이슈 검색 또는 초기 이슈 목록 로드
     * @param keyword 검색 키워드 (빈 문자열인 경우 초기 목록 로드)
     */
    private fun searchIssues(keyword: String = "") {
        val isInitialLoad = keyword.isBlank()

        val loadingMessage = if (isInitialLoad) "이슈 불러오는 중..." else "'${keyword}' 검색 중..."
        setLoading(true, loadingMessage)

        val issues = if (isInitialLoad) jiraService.getMyIssues()
        else jiraService.searchIssuesByKeyword(keyword)

        try {
            if (!isDisposed) {
                setLoading(false)

                if (issues.isEmpty()) {
                    listModel.removeAll()
                    issueList.emptyText.text = if (isInitialLoad)
                        "할당된 이슈가 없습니다"
                    else
                        "'${keyword}' 검색 결과가 없습니다"
                } else {
                    updateIssueList(issues)
                }
            }
        } catch (e: Exception) {
            if (!isDisposed) {
                setLoading(false)
                listModel.removeAll()
                issueList.emptyText.text = if (isInitialLoad)
                    "오류 발생: ${e.message}"
                else
                    "검색 오류: ${e.message}"
            }
        }
    }

    /**
     * 초기 이슈 목록 로드
     */
    private fun loadInitialIssues() {
        searchIssues()
    }

    /**
     * 키워드로 이슈 검색
     */
    private fun performSearch(keyword: String) {
        searchIssues(keyword)
    }

    /**
     * 이슈 목록 업데이트
     */
    private fun updateIssueList(issues: List<JiraIssue>) {
        listModel.removeAll()
        if (issues.isEmpty()) {
            // 검색 결과가 없을 때는 그냥 빈 목록 표시 (listModel을 비워둠)
            // emptyText가 표시됨
        } else {
            listModel.add(issues)
            issueList.selectedIndex = 0
        }
    }

    /**
     * 셀 렌더러
     */
    private inner class JiraIssueCellRenderer : com.intellij.ui.ColoredListCellRenderer<JiraIssue>() {
        override fun customizeCellRenderer(
            list: JList<out JiraIssue>,
            value: JiraIssue,
            index: Int,
            selected: Boolean,
            hasFocus: Boolean
        ) {
            // 로딩 항목은 아이콘 없이 표시
            if (value.key.isEmpty()) {
                append(value.summary ?: "")
                return
            }

            // 이슈 타입에 따른 아이콘 설정 - null 안전 처리
            when (value.issueType?.lowercase()) {
                "bug" -> icon = com.intellij.icons.AllIcons.General.BalloonError
                "task" -> icon = com.intellij.icons.AllIcons.General.TodoDefault
                "story" -> icon = com.intellij.icons.AllIcons.General.User
                else -> icon = com.intellij.icons.AllIcons.General.TodoQuestion
            }

            append("${value.key}: ${value.summary ?: ""}")
            append(" [${value.status ?: "Unknown"}]", com.intellij.ui.SimpleTextAttributes.GRAYED_ATTRIBUTES)
        }
    }
}