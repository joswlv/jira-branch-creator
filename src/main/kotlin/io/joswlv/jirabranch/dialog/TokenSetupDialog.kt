package io.joswlv.jirabranch.dialog

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.dsl.builder.panel
import io.joswlv.jirabranch.JiraBranchBundle
import javax.swing.JComponent
import javax.swing.JLabel

/**
 * JIRA API 토큰을 입력받는 다이얼로그
 */
class TokenSetupDialog : DialogWrapper(true) {
    private val tokenField = JBPasswordField()

    init {
        title = JiraBranchBundle.message("dialog.token.title")
        init()
    }

    override fun createCenterPanel(): JComponent {
        return panel {
            row {
                label(JiraBranchBundle.message("dialog.token.label"))
            }
            row {
                cell(tokenField)
                    .resizableColumn()
                    .focused()
                    .comment(JiraBranchBundle.message("dialog.token.help"))
            }
            row {
                val saveMessage = JiraBranchBundle.message("dialog.token.save").replace("\n", "<br>")
                cell(JLabel("<html>$saveMessage</html>"))
                    .resizableColumn()
            }
        }
    }

    /**
     * 사용자가 입력한 토큰 반환
     */
    fun getToken(): String {
        return String(tokenField.password)
    }

    override fun getPreferredFocusedComponent() = tokenField
}