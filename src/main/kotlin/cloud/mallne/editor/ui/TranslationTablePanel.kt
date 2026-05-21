package cloud.mallne.editor.ui

import cloud.mallne.editor.ComposeResourcesScanner
import cloud.mallne.editor.StringsXmlParser
import cloud.mallne.editor.ComposeResourcesRoot
import cloud.mallne.editor.StringsXmlWriter
import cloud.mallne.editor.model.ResourceTableModel
import cloud.mallne.editor.model.ResourceType
import cloud.mallne.editor.model.ResourceValue
import cloud.mallne.editor.model.TranslationEntry
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.OSProcessHandler
import com.intellij.execution.process.ProcessTerminatedListener
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.AbstractCellEditor
import javax.swing.JCheckBox
import javax.swing.JTable
import javax.swing.JTextField
import javax.swing.ListSelectionModel
import javax.swing.Timer
import javax.swing.table.TableCellEditor

class TranslationTablePanel(private val project: Project) : SimpleToolWindowPanel(true) {

    private val tableModel = ResourceTableModel()
    private val table = JBTable(tableModel)
    private var currentRoot: ComposeResourcesRoot? = null

    private var debounceTimer: Timer? = null
    private var pendingSaveLocale: String? = null

    init {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
        table.setShowGrid(false)
        table.setRowHeight(28)
        table.autoCreateRowSorter = true
        table.putClientProperty("terminateEditOnFocusLost", true)

        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) handleDoubleClick(e)
            }
        })

        val decorator = ToolbarDecorator.createDecorator(table)
            .setAddAction { addKey() }
            .setRemoveAction { removeKey() }
            .addExtraAction(object : AnAction(AllIcons.Actions.Refresh) {
                override fun actionPerformed(e: AnActionEvent) { refresh() }
            })
            .addExtraAction(object : AnAction(AllIcons.Actions.MenuSaveall) {
                override fun actionPerformed(e: AnActionEvent) { saveNow() }
            })
            .addExtraAction(object : AnAction("Generate Accessors", "Run generateResourceAccessorsForCommonMain", null) {
                override fun actionPerformed(e: AnActionEvent) { generateAccessors() }
            })

        toolbar = decorator.createPanel()
        add(JBScrollPane(table), BorderLayout.CENTER)

        refresh()
    }

    private fun configureColumnWidths() {
        table.columnModel.getColumn(ResourceTableModel.COL_CHECKBOX).apply {
            minWidth = 24
            maxWidth = 30
            preferredWidth = 26
        }
        table.columnModel.getColumn(ResourceTableModel.COL_WARNING).apply {
            minWidth = 24
            maxWidth = 30
            preferredWidth = 26
        }
        table.columnModel.getColumn(ResourceTableModel.COL_TYPE).apply {
            minWidth = 40
            maxWidth = 55
            preferredWidth = 45
        }
        table.columnModel.getColumn(ResourceTableModel.COL_KEY).apply {
            minWidth = 120
            preferredWidth = 200
        }
        table.columnModel.getColumn(ResourceTableModel.COL_BASE).apply {
            preferredWidth = 250
        }
    }

    fun refresh() {
        val roots = ComposeResourcesScanner.scan(project)
        val root = roots.firstOrNull()
        currentRoot = root

        if (root == null) {
            tableModel.updateEntries(emptyList())
            tableModel.updateLocales(emptyList())
            return
        }

        val locales = root.localeDirs.mapNotNull { it.locale }
        val entries = StringsXmlParser.parse(project, root)

        tableModel.baseLocaleLabel = "Base"
        tableModel.updateLocales(locales)
        tableModel.updateEntries(entries)
        installColumnConfig()
    }

    private fun installColumnConfig() {
        val renderer = TranslationTableCellRenderer()
        for (i in 0 until tableModel.columnCount) {
            table.columnModel.getColumn(i).cellRenderer = renderer
        }
        configureColumnWidths()

        val checkBoxEditor = object : AbstractCellEditor(), TableCellEditor {
            private val checkBox = JCheckBox()

            override fun getCellEditorValue(): Any = checkBox.isSelected

            override fun getTableCellEditorComponent(
                table: JTable,
                value: Any?,
                isSelected: Boolean,
                row: Int,
                column: Int
            ): Component {
                val modelRow = table.convertRowIndexToModel(row)
                checkBox.isSelected = tableModel.getEntry(modelRow).translatable
                return checkBox
            }

            override fun stopCellEditing(): Boolean {
                val row = table.editingRow
                val col = table.editingColumn
                val locale = if (col >= 0) tableModel.getLocale(col) else null
                val result = super.stopCellEditing()
                if (result && locale != null) {
                    scheduleSave(locale)
                }
                return result
            }
        }
        table.columnModel.getColumn(ResourceTableModel.COL_CHECKBOX).cellEditor = checkBoxEditor

        val simpleStringEditor = object : AbstractCellEditor(), TableCellEditor {
            private val field = JTextField()

            override fun getCellEditorValue(): Any = field.text

            override fun getTableCellEditorComponent(
                table: JTable,
                value: Any?,
                isSelected: Boolean,
                row: Int,
                column: Int
            ): Component {
                val text = when (value) {
                    is ResourceValue.Simple -> value.text
                    else -> ""
                }
                field.text = text
                return field
            }

            override fun stopCellEditing(): Boolean {
                val row = table.editingRow
                val col = table.editingColumn
                val locale = if (row >= 0 && col >= 0) tableModel.getLocale(col) else null
                val result = super.stopCellEditing()
                if (result && locale != null) {
                    scheduleSave(locale)
                }
                return result
            }
        }

        for (i in ResourceTableModel.COL_BASE until tableModel.columnCount) {
            table.columnModel.getColumn(i).cellEditor = simpleStringEditor
        }
    }

    private fun handleDoubleClick(e: MouseEvent) {
        val row = table.rowAtPoint(e.point)
        val col = table.columnAtPoint(e.point)
        if (row < 0 || col < 0) return

        val modelRow = table.convertRowIndexToModel(row)
        val entry = tableModel.getEntry(modelRow)
        if (entry.type == ResourceType.STRING) return

        val locale = tableModel.getLocale(col) ?: return
        val isBase = locale == tableModel.baseLocaleLabel

        val currentValue = if (isBase) entry.baseValue
        else entry.translations[locale]

        when (entry.type) {
            ResourceType.STRING_ARRAY -> {
                val items = (currentValue as? ResourceValue.Array)?.items
                    ?: (entry.baseValue as? ResourceValue.Array)?.items
                    ?: emptyList()
                val dialog = StringArrayEditorDialog(entry.key, items)
                if (dialog.showAndGet()) {
                    val newValue = ResourceValue.Array(dialog.getItems())
                    updateEntryValue(entry.key, newValue, locale, isBase)
                }
            }
            ResourceType.PLURALS -> {
                val items = (currentValue as? ResourceValue.Plurals)?.items
                    ?: (entry.baseValue as? ResourceValue.Plurals)?.items
                    ?: emptyList()
                val dialog = PluralsEditorDialog(entry.key, items)
                if (dialog.showAndGet()) {
                    val newValue = ResourceValue.Plurals(dialog.getItems())
                    updateEntryValue(entry.key, newValue, locale, isBase)
                }
            }
            else -> {}
        }
    }

    private fun updateEntryValue(key: String, newValue: ResourceValue, locale: String, isBase: Boolean) {
        val idx = tableModel.entries.indexOfFirst { it.key == key }
        if (idx < 0) return
        val entry = tableModel.entries[idx]

        val mutable = tableModel.entries.toMutableList()
        if (isBase) {
            mutable[idx] = entry.copy(baseValue = newValue)
        } else {
            entry.translations[locale] = newValue
            mutable[idx] = entry
        }
        tableModel.updateEntries(mutable)
        scheduleSave(locale)
    }

    private fun scheduleSave(locale: String?) {
        pendingSaveLocale = locale
        debounceTimer?.stop()
        debounceTimer = Timer(800) {
            saveNow()
        }.apply {
            isRepeats = false
            start()
        }
    }

    fun saveNow() {
        val root = currentRoot ?: return
        StringsXmlWriter.save(root, tableModel.entries, pendingSaveLocale)
        pendingSaveLocale = null
    }

    fun generateAccessors() {
        val root = currentRoot ?: return
        val module = ModuleUtilCore.findModuleForFile(root.root, project) ?: return
        val projectPath = ExternalSystemApiUtil.getExternalProjectPath(module) ?: return
        val gradlew = java.io.File(projectPath, if (SystemInfo.isWindows) "gradlew.bat" else "gradlew")
        val command = GeneralCommandLine(gradlew.absolutePath, "generateResourceAccessorsForCommonMain")
            .withWorkDirectory(projectPath)
        val handler = OSProcessHandler(command)
        ProcessTerminatedListener.attach(handler, project)
        handler.startNotify()
    }

    private fun addKey() {
        val dialog = AddKeyDialog(project)
        if (dialog.showAndGet()) {
            val entry = TranslationEntry(
                key = dialog.key,
                type = dialog.type,
                baseValue = dialog.baseValue
            )
            val mutable = tableModel.entries.toMutableList()
            mutable.add(entry)
            tableModel.updateEntries(mutable.sortedBy { it.key })
            scheduleSave("Base")
        }
    }

    private fun removeKey() {
        val selectedRow = table.selectedRow
        if (selectedRow < 0) return
        val modelRow = table.convertRowIndexToModel(selectedRow)
        val mutable = tableModel.entries.toMutableList()
        mutable.removeAt(modelRow)
        tableModel.updateEntries(mutable)
        scheduleSave(null)
    }
}
