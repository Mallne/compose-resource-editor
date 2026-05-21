package cloud.mallne.editor.ui

import cloud.mallne.editor.model.ResourceType
import cloud.mallne.editor.model.ResourceValue
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField
import java.awt.GridBagConstraints
import java.awt.GridBagLayout

class AddKeyDialog(project: Project) : DialogWrapper(project, false) {

    private val keyField = JTextField()
    private val typeCombo = JComboBox(arrayOf(ResourceType.STRING, ResourceType.STRING_ARRAY, ResourceType.PLURALS))
    private val baseValueField = JTextField()

    val key: String get() = keyField.text.trim()
    val type: ResourceType get() = typeCombo.selectedItem as ResourceType
    val baseValue: ResourceValue
        get() = when (type) {
            ResourceType.STRING -> ResourceValue.Simple(baseValueField.text)
            ResourceType.STRING_ARRAY -> ResourceValue.Array(emptyList())
            ResourceType.PLURALS -> ResourceValue.Plurals(emptyList())
        }

    init {
        title = "Add Translation Key"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val panel = JPanel(GridBagLayout())
        val c = GridBagConstraints()

        c.fill = GridBagConstraints.HORIZONTAL
        c.gridx = 0; c.gridy = 0; c.weightx = 0.0
        panel.add(javax.swing.JLabel("Key:"), c)

        c.gridx = 1; c.gridy = 0; c.weightx = 1.0; c.insets = java.awt.Insets(0, 8, 0, 0)
        keyField.columns = 30
        panel.add(keyField, c)

        c.gridx = 0; c.gridy = 1; c.weightx = 0.0; c.insets = java.awt.Insets(8, 0, 0, 0)
        panel.add(javax.swing.JLabel("Type:"), c)

        c.gridx = 1; c.gridy = 1; c.weightx = 1.0; c.insets = java.awt.Insets(8, 8, 0, 0)
        panel.add(typeCombo, c)

        c.gridx = 0; c.gridy = 2; c.weightx = 0.0; c.insets = java.awt.Insets(8, 0, 0, 0)
        panel.add(javax.swing.JLabel("Base Value:"), c)

        c.gridx = 1; c.gridy = 2; c.weightx = 1.0; c.insets = java.awt.Insets(8, 8, 0, 0)
        baseValueField.columns = 30
        panel.add(baseValueField, c)

        return panel
    }

    override fun doValidate(): ValidationInfo? {
        if (keyField.text.isBlank()) return ValidationInfo("Key must not be empty", keyField)
        return null
    }
}
