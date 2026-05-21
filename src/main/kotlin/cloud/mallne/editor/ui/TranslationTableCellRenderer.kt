package cloud.mallne.editor.ui

import cloud.mallne.editor.model.ResourceTableModel
import cloud.mallne.editor.model.ResourceType
import cloud.mallne.editor.model.ResourceValue
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Component
import java.awt.Font
import javax.swing.JCheckBox
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

class TranslationTableCellRenderer : DefaultTableCellRenderer() {

    companion object {
        private val MISSING_BG = JBColor(Color(255, 240, 240), Color(70, 50, 50))
        private val NOT_TRANSLATABLE_FG = JBColor(Color(140, 140, 140), Color(130, 130, 130))
        private val WARNING_FG = JBColor(Color(200, 140, 0), Color(255, 200, 80))

        private val TYPE_COLORS = mapOf(
            ResourceType.STRING to JBColor(Color(50, 130, 50), Color(100, 200, 100)),
            ResourceType.STRING_ARRAY to JBColor(Color(30, 80, 180), Color(80, 150, 230)),
            ResourceType.PLURALS to JBColor(Color(170, 90, 30), Color(220, 150, 80))
        )
    }

    private val checkBox = JCheckBox().apply {
        isOpaque = true
        horizontalAlignment = CENTER
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        val model = table.model as? ResourceTableModel ?: return this
        if (row >= model.entries.size) return this

        val entry = model.entries[row]
        val baseBG = UIUtil.getTableBackground()

        if (column == ResourceTableModel.COL_CHECKBOX) {
            checkBox.isSelected = entry.translatable
            checkBox.toolTipText = if (entry.translatable) "Translatable" else "Not translatable"
            checkBox.background = baseBG
            return checkBox
        }

        if (column == ResourceTableModel.COL_WARNING) {
            val hasIssues = value as? String
            text = if (hasIssues != null) "\u26A0" else ""
            foreground = if (hasIssues != null) WARNING_FG else table.foreground
            toolTipText = if (hasIssues != null) "Missing translations" else null
            background = baseBG
            font = Font("Dialog", Font.BOLD, 14)
            horizontalAlignment = CENTER
            return this
        }

        val comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        if (isSelected) return comp

        horizontalAlignment = LEADING
        toolTipText = null
        background = baseBG
        foreground = table.foreground
        font = table.font

        when (column) {
            ResourceTableModel.COL_KEY -> {
                text = entry.key
                if (!entry.translatable) {
                    foreground = NOT_TRANSLATABLE_FG
                    font = table.font.deriveFont(Font.ITALIC)
                }
            }
            ResourceTableModel.COL_TYPE -> {
                text = when (entry.type) {
                    ResourceType.STRING -> "S"
                    ResourceType.STRING_ARRAY -> "Arr"
                    ResourceType.PLURALS -> "Pl"
                }
                foreground = TYPE_COLORS[entry.type] ?: table.foreground
            }
            ResourceTableModel.COL_BASE -> {
                text = formatValue(entry.baseValue)
            }
            else -> {
                val localeIdx = column - ResourceTableModel.COL_LOCALE_FIRST
                if (localeIdx in model.locales.indices) {
                    val locale = model.locales[localeIdx]
                    val localeValue = entry.translations[locale]
                    text = formatValue(localeValue)
                    if (entry.translatable && (localeValue == null || localeValue.isEmptyValue())) {
                        background = MISSING_BG
                        toolTipText = "Missing translation for $locale"
                    }
                } else {
                    text = formatValue(value as? ResourceValue)
                }
            }
        }

        return comp
    }

    private fun formatValue(value: ResourceValue?): String = when (value) {
        is ResourceValue.Simple -> if (value.text.isEmpty()) "" else value.text
        is ResourceValue.Array -> "[${value.items.size} items]"
        is ResourceValue.Plurals -> value.items.joinToString(" | ") { "${it.quantity}: ${it.value.take(30)}" }
        null -> ""
    }
}
