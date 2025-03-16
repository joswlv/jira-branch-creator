package io.joswlv.jirabranch.dialog

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.dsl.builder.panel
import io.joswlv.jirabranch.JiraBranchBundle
import java.awt.Dimension
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import javax.swing.JComponent

/**
 * 커밋 메시지 입력 다이얼로그
 */
class CommitMessageDialog(
    private val project: Project,
    private val issueKey: String
) : DialogWrapper(project) {
    private val messageArea = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        columns = 50
        rows = 10
    }

    init {
        title = JiraBranchBundle.message("dialog.commit.title")

        // Ctrl+Enter 단축키 지원
        messageArea.addKeyListener(object : KeyListener {
            override fun keyTyped(e: KeyEvent) {}
            override fun keyReleased(e: KeyEvent) {}
            override fun keyPressed(e: KeyEvent) {
                if (e.isControlDown && e.keyCode == KeyEvent.VK_ENTER) {
                    if (doValidate() == null) {
                        doOKAction()
                    }
                }
            }
        })

        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row {
                label(JiraBranchBundle.message("dialog.commit.label"))
            }
            row {
                cell(messageArea)
                    .resizableColumn()
                    .focused()
                    .comment(JiraBranchBundle.message("dialog.commit.prefix", issueKey))
            }.resizableRow()

            preferredSize.size = Dimension(500, 300)
        }
    }

    /**
     * 최종 커밋 메시지 반환 (이슈 키 프리픽스 포함)
     */
    fun getCommitMessage(): String {
        val userMessage = messageArea.text.trim()
        return "[$issueKey] $userMessage"
    }

    override fun doValidate(): ValidationInfo? {
        return if (messageArea.text.trim().isEmpty()) {
            ValidationInfo(JiraBranchBundle.message("error.empty.commit"), messageArea)
        } else {
            null
        }
    }

    override fun getPreferredFocusedComponent() = messageArea
}