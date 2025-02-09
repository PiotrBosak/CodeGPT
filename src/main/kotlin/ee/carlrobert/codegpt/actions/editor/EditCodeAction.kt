package ee.carlrobert.codegpt.actions.editor

import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import ee.carlrobert.codegpt.Icons
import ee.carlrobert.codegpt.ui.EditCodePopover
import ee.carlrobert.codegpt.ui.EditCodePopoverForDiff
import javax.swing.Icon

open class EditCodeAction(icon: Icon) : BaseEditorAction(icon) {
    override fun actionPerformed(project: Project, editor: Editor, selectedText: String) {
        runInEdt {
            EditCodePopoverForDiff(editor).show()
        }
    }
}

class EditCodeFloatingMenuAction : EditCodeAction(Icons.DefaultSmall)

class EditCodeContextMenuAction : EditCodeAction(Icons.Sparkle)
