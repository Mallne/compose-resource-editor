package cloud.mallne.editor.ui

import cloud.mallne.editor.model.PluralItem
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

class PluralsEditorDialog(
    private val key: String,
    private val initialItems: List<PluralItem>
) : DialogWrapper(true) {

    private var items = initialItems.toMutableList()
    private val tableModel = object : AbstractTableModel() {
        override fun getRowCount() = items.size
        override fun getColumnCount() = 2
        override fun getColumnName(column: Int) = if (column == 0) "Quantity" else "Value"
        override fun getValueAt(row: Int, column: Int) =
            if (column == 0) items[row].quantity else items[row].value
        override fun setValueAt(value: Any?, row: Int, column: Int) {
            val text = value?.toString() ?: ""
            items[row] = if (column == 0) items[row].copy(quantity = text) else items[row].copy(value = text)
            fireTableRowsUpdated(row, row)
        }
        override fun isCellEditable(row: Int, column: Int) = true
    }

    private val table = JBTable(tableModel)

    init {
        title = "Edit Plurals: $key"
        init()
    }

    override fun createCenterPanel(): JComponent {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.setShowGrid(false)
        table.columnModel.getColumn(0).preferredWidth = 100
        table.columnModel.getColumn(1).preferredWidth = 300

        val decorator = ToolbarDecorator.createDecorator(table)
            .setAddAction { addItem() }
            .setRemoveAction { removeItem() }

        val panel = JPanel(BorderLayout())
        panel.add(decorator.createPanel(), BorderLayout.CENTER)
        return panel
    }

    private fun addItem() {
        items.add(PluralItem("", ""))
        tableModel.fireTableRowsInserted(items.size - 1, items.size - 1)
    }

    private fun removeItem() {
        val row = table.selectedRow
        if (row < 0) return
        items.removeAt(row)
        tableModel.fireTableRowsDeleted(row, row)
    }

    fun getItems(): List<PluralItem> = items.toList()
}
