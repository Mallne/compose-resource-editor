package cloud.mallne.editor.ui

import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ListSelectionModel
import javax.swing.table.AbstractTableModel

class StringArrayEditorDialog(
    private val key: String,
    private val initialItems: List<String>,
    project: com.intellij.openapi.project.Project? = null
) : DialogWrapper(project, true) {

    private var items = initialItems.toMutableList()
    private val tableModel = object : AbstractTableModel() {
        override fun getRowCount() = items.size
        override fun getColumnCount() = 1
        override fun getColumnName(column: Int) = "Item"
        override fun getValueAt(row: Int, column: Int) = items[row]
        override fun setValueAt(value: Any?, row: Int, column: Int) {
            items[row] = value?.toString() ?: ""
            fireTableRowsUpdated(row, row)
        }
        override fun isCellEditable(row: Int, column: Int) = true
    }

    private val table = JBTable(tableModel)

    init {
        title = "Edit String Array: $key"
        init()
    }

    override fun createCenterPanel(): JComponent {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.setShowGrid(false)
        table.columnModel.getColumn(0).preferredWidth = 400

        val addButton = JButton("+").also {
            it.addActionListener { addItem() }
        }
        val removeButton = JButton("-").also {
            it.addActionListener { removeItem() }
        }
        val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0)).also {
            it.add(addButton)
            it.add(removeButton)
        }

        val panel = JPanel(BorderLayout())
        panel.add(toolbar, BorderLayout.NORTH)
        panel.add(JBScrollPane(table), BorderLayout.CENTER)
        return panel
    }

    private fun addItem() {
        items.add("")
        tableModel.fireTableRowsInserted(items.size - 1, items.size - 1)
    }

    private fun removeItem() {
        val row = table.selectedRow
        if (row < 0) return
        items.removeAt(row)
        tableModel.fireTableRowsDeleted(row, row)
    }

    fun getItems(): List<String> = items.toList()
}
