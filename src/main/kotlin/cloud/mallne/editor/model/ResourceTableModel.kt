package cloud.mallne.editor.model

import javax.swing.table.AbstractTableModel

class ResourceTableModel(
    var baseLocaleLabel: String = "Base",
    var locales: List<String> = emptyList(),
    var entries: List<TranslationEntry> = emptyList()
) : AbstractTableModel() {

    companion object {
        const val COL_CHECKBOX = 0
        const val COL_WARNING = 1
        const val COL_KEY = 2
        const val COL_TYPE = 3
        const val COL_BASE = 4
        const val COL_LOCALE_FIRST = 5
    }

    override fun getRowCount(): Int = entries.size

    override fun getColumnCount(): Int = COL_LOCALE_FIRST + locales.size

    override fun getColumnName(column: Int): String = when (column) {
        COL_CHECKBOX -> ""
        COL_WARNING -> "!"
        COL_KEY -> "Key"
        COL_TYPE -> "Type"
        COL_BASE -> baseLocaleLabel
        else -> locales[column - COL_LOCALE_FIRST]
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any? {
        val entry = entries[rowIndex]
        return when (columnIndex) {
            COL_CHECKBOX -> entry.translatable
            COL_WARNING -> if (entry.translatable && entry.hasMissingTranslations(locales)) "\u26A0" else null
            COL_KEY -> entry.key
            COL_TYPE -> entry.type
            COL_BASE -> entry.baseValue
            else -> {
                val locale = locales[columnIndex - COL_LOCALE_FIRST]
                entry.translations[locale]
            }
        }
    }

    override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean =
        columnIndex == COL_CHECKBOX || (columnIndex != COL_KEY && columnIndex != COL_TYPE && columnIndex != COL_WARNING)

    override fun setValueAt(value: Any?, rowIndex: Int, columnIndex: Int) {
        val entry = entries[rowIndex]
        if (columnIndex == COL_CHECKBOX) {
            val checked = value == true
            val updated = entry.copy(translatable = checked)
            val mutable = entries.toMutableList()
            mutable[rowIndex] = updated
            entries = mutable
            fireTableRowsUpdated(rowIndex, rowIndex)
            return
        }
        if (columnIndex <= COL_TYPE) return
        if (entry.type != ResourceType.STRING) return
        val text = value?.toString() ?: ""
        val newValue = ResourceValue.Simple(text)
        if (columnIndex == COL_BASE) {
            val updated = entry.copy(baseValue = newValue)
            val mutable = entries.toMutableList()
            mutable[rowIndex] = updated
            entries = mutable
        } else {
            val localeIdx = columnIndex - COL_LOCALE_FIRST
            if (localeIdx in locales.indices) {
                entry.translations[locales[localeIdx]] = newValue
            }
        }
        fireTableRowsUpdated(rowIndex, rowIndex)
    }

    fun getEntry(rowIndex: Int): TranslationEntry = entries[rowIndex]

    fun getLocale(columnIndex: Int): String? = when {
        columnIndex < COL_BASE -> null
        columnIndex == COL_BASE -> baseLocaleLabel
        columnIndex >= COL_LOCALE_FIRST -> locales[columnIndex - COL_LOCALE_FIRST]
        else -> null
    }

    fun updateEntries(newEntries: List<TranslationEntry>) {
        entries = newEntries
        fireTableDataChanged()
    }

    fun updateLocales(newLocales: List<String>) {
        locales = newLocales
        fireTableStructureChanged()
    }
}
