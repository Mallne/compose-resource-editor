package cloud.mallne.editor.ui

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

class ResourceEditorNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        if (file.name != "strings.xml") return null
        if (!isInComposeResources(file)) return null

        return Function { _: FileEditor ->
            val panel = EditorNotificationPanel()
            panel.text = "Edit translations for this resource file in a table view"
            panel.createActionLabel("Open in Resource Editor") {
                ToolWindowManager.getInstance(project)
                    .getToolWindow("Resource Editor")
                    ?.activate(null)
            }
            panel
        }
    }

    private fun isInComposeResources(file: VirtualFile): Boolean {
        var parent = file.parent
        while (parent != null) {
            if (parent.name == "composeResources") return true
            parent = parent.parent
        }
        return false
    }
}
