# Compose Multiplatform Resource Editor — Translation Table Plan

## Overview

Build an IntelliJ plugin tool window that reads `strings.xml` files from Compose Multiplatform `composeResources/` directories and displays translations in a dynamic N-column table:

| Key | Type | Base (values/) | de | fr | ... |
|-----|------|----------------|----|----|-----|
| app_name | string | My App | Meine App | Mon App | ... |
| str_arr | string-array | [3 items] | [3 items] | ... |
| new_message | plurals | one:... other:... | one:... other:... | ... |

---

## 0. IntelliJ Plugin vs Kotlin Notebook — Research & Decision

### Kotlin Notebook
- **What it is**: An interactive REPL-style document within IntelliJ IDEA where code cells, markdown, and visualizations are mixed.
- **Relevant capabilities**: Can access IntelliJ Platform APIs via the `intellij-platform` integration library. Can read/write files, display tables, parse XML.
- **Suitable for**: Data exploration, prototyping, one-off analyses, quick experiments, learning APIs.
- **Limitations for this task**:
  - **Ephemeral** — no persistent UI; must re-run cells every session.
  - **No tool window / editor integration** — cannot add a permanent panel to the IDE sidebar.
  - **No file-system watching** — cannot react to file changes in real-time.
  - **No VCS/undo integration** — file writes bypass the IntelliJ PSI layer.
  - **No plugin distribution** — cannot be packaged and shared as a reusable tool.
  - **Swing UI is impractical** — notebooks are code-cell driven, not suited for complex interactive Swing tables.

### IntelliJ Plugin
- **What it is**: A compiled extension that integrates deeply into the IDE with custom tool windows, editors, actions, and listeners.
- **Relevant capabilities**: Full access to `JBTable`, `ToolWindowFactory`, `WriteCommandAction`, XML PSI, `VirtualFileListener`, `ProjectFileIndex`, `ToolbarDecorator`.
- **Suitable for**: Production editing tools, persistent IDE integrations, reusable/distributable plugins.
- **Advantages for this task**:
  - **Persistent tool window** — always available, survives IDE restarts.
  - **VFS watching** — auto-refresh on file changes.
  - **PSI-based writes** — undo/redo, VCS integration, index consistency.
  - **Professional Swing UI** — `JBTable` with inline editing, custom renderers, toolbar actions.
  - **Distributable** — can be published to JetBrains Marketplace.

### Verdict
**IntelliJ Plugin** is the correct choice. A Kotlin Notebook would be viable only for a throwaway script to *merge or inspect* resource files, but not for a daily-driver editing tool. This project is building a persistent, integrated editor — exactly what the IntelliJ Platform was designed for.

---

## 1. Directory & Resource Discovery

### 1.1 Find `composeResources` roots
- Scan the open project for directories named `composeResources` using `com.intellij.openapi.roots.ProjectFileIndex` and/or `com.intellij.openapi.vfs.VirtualFile`.
- A typical CMP project layout:
  ```
  composeResources/
    values/
      strings.xml          ← base / default locale
    values-de/
      strings.xml          ← German
    values-fr/
      strings.xml          ← French
    drawable/
      ...
  ```

### 1.2 Parse locale from folder name
- `values/` → base locale (displayed as "Base").
- `values-{locale}/` → extract `{locale}` (e.g., `de`, `fr`, `zh-rCN`).
- Sort columns: Key, Type, Base, then locales alphabetically.

### 1.3 Watch for file changes
- Attach a `VirtualFileListener` to reload the table when `strings.xml` files are created, modified, or deleted.

---

## 2. XML Parsing (`strings.xml`)

### 2.1 Three resource types — all required

#### Simple strings
```xml
<string name="app_name">My App</string>
```

#### String arrays
```xml
<string-array name="str_arr">
    <item>item ★</item>
    <item>item ⌘</item>
    <item>item ½</item>
</string-array>
```
Generated accessor: `Res.array.str_arr` → `List<String>`

#### Plurals
```xml
<plurals name="new_message">
    <item quantity="one">%1$d new message</item>
    <item quantity="other">%1$d new messages</item>
</plurals>
```
Generated accessor: `Res.plurals.new_message` → quantity-sensitive `String`
Supported quantities: `zero`, `one`, `two`, `few`, `many`, `other`

### 2.2 Using IntelliJ XML PSI (preferred)
- Leverage the built-in XML PSI layer (`com.intellij.psi.xml.*`).
- Parse each `strings.xml` into typed entries.
- Handle:
  - Quoted values, CDATA sections, escaped characters like `\'`, `\"`, `\n`, `\uXXXX`.
  - XML comments (must be preserved when writing back, stored alongside the entry).
  - The `name` attribute on all three element types.

### 2.3 Data model
```kotlin
enum class ResourceType { STRING, STRING_ARRAY, PLURALS }

data class PluralItem(val quantity: String, val value: String)

sealed class ResourceValue {
    data class Simple(val text: String) : ResourceValue()
    data class Array(val items: List<String>) : ResourceValue()
    data class Plurals(val items: List<PluralItem>) : ResourceValue()
}

data class TranslationEntry(
    val key: String,
    val type: ResourceType,
    val values: MutableMap<String, ResourceValue>,  // locale -> value
    val baseValue: ResourceValue,                   // from values/strings.xml
    val comment: String? = null
)

data class ResourceTableModel(
    val baseLocale: String = "Base",
    val locales: List<String>,
    val entries: List<TranslationEntry>
)
```

### 2.4 Key union across all locales
- A key present in *any* locale file must appear in the table.
- If a key is missing in a locale, show an empty/editable cell.
- The type (`string`, `string-array`, `plurals`) must be consistent across locales — flag mismatches.

### 2.5 Table row representation
- **Simple strings**: value shown directly in the cell, editable inline.
- **String arrays**: cell shows `"[N items]"` summary; double-click opens a dialog to edit the item list.
- **Plurals**: cell shows `"one:... other:..."` summary; double-click opens a dialog to edit each quantity variant.

---

## 3. Table UI

### 3.1 Swing Component (`JBTable`)
- Extend `com.intellij.ui.table.JBTable` with a custom `TableModel`.
- Columns: `[Key, Type, Base, locale1, locale2, ...]`.
- Use `com.intellij.util.ui.ColumnInfo` for column definitions.

### 3.2 Table Model
- `ResourceTableModel` extends `AbstractTableModel`.
- Columns: `List<ColumnInfo<TranslationEntry, *>>`.
- Row count → number of unique keys across all locales.
- The `Type` column is read-only and shows an icon/label: `S`, `Arr`, `Pl`.

### 3.3 Cell Rendering
- **Simple strings**: editable with `DefaultCellEditor` (text field).
- **String arrays**: render as `"[3 items]"` (blue, underlined), non-editable inline — open dialog on click.
- **Plurals**: render as `"one:... other:..."` (truncated), non-editable inline — open dialog on click.
- Highlight empty/missing translations (red border / background via custom `TableCellRenderer`).
- Read-only key and type columns.
- Striped rows using IntelliJ's `JBTable` default striping.

### 3.4 Editing behavior
- **Simple strings**: double-click → inline text field → save on focus loss or Enter.
- **String arrays**: double-click → `StringArrayEditorDialog` (list of items with add/remove/reorder).
- **Plurals**: double-click → `PluralsEditorDialog` (one field per quantity variant present in base locale).
- Changes are written to in-memory model immediately.
- Debounced save to disk (e.g., 1 second after last edit) to avoid thrashing.

---

## 4. Writing Changes Back

### 4.1 Save strategy
- On edit → collect all entries for that locale → rewrite the associated `strings.xml`.
- XML must be written preserving:
  - `<?xml version="1.0" encoding="utf-8"?>` declaration.
  - `<resources>` root element.
  - All elements in a stable order (key order, or preserve original order).
  - XML comments associated with entries.
  - Proper escaping of special characters.

### 4.2 IntelliJ PSI write (safe approach)
- Use `WriteCommandAction.runWriteCommandAction(project) { ... }` to modify XML files via PSI.
- This ensures VCS integration, undo support, and index updates.

### 4.3 Simpler fallback
- Use `VirtualFile.setBinaryContent()` with raw XML string built via `XmlSerializer`.
- Downside: loses undo history and may not trigger PSI indexing properly.

---

## 5. Tool Window Integration

### 5.1 Tool window content
```
┌──────────────────────────────────────────────────────────┐
│ [composeResources path selector]                         │
│ ┌──────────────────────────────────────────────────────┐ │
│ │ Key    │ T │ Base       │ de           │ fr      │.. │ │
│ ├────────┼───┼────────────┼──────────────┼─────────┤   │ │
│ │app_name│ S │ My App     │ Meine App    │ Mon App │   │ │
│ │str_arr │Arr│ [3 items]  │ [3 items]    │ [3]     │   │ │
│ │new_msgs│ Pl│ one:1 msg  │ one:1 Nachr  │         │   │ │
│ │        │   │ other:N msgs│ other:N Nachr│         │   │ │
│ └────────┴───┴────────────┴──────────────┴─────────┴───┘ │
│ [+ Add Key]  [Remove Key]  [Filter...]  [Add Locale]     │
└──────────────────────────────────────────────────────────┘
```

### 5.2 Toolbar actions
- **Add Key**: dialog to choose type (string / string-array / plurals), enter key + base value(s).
- **Remove Key**: removes selected row from all locale files.
- **Filter**: text field to filter rows by key name.
- **Add Locale**: scans for new locale folders, adds column.
- **Module selector** (if multiple `composeResources` roots exist).

---

## 6. Implementation Phases

### Phase 1 — Foundation (read-only table, all three types)
1. Set up project structure, remove template code.
2. Implement `ComposeResourcesScanner` — finds `composeResources` roots and locale folders.
3. Implement `StringsXmlParser` — reads XML, extracts `<string>`, `<string-array>`, and `<plurals>`.
4. Build `ResourceTableModel` and `JBTable` with Key + Type + all locale columns.
5. Display read-only table in the tool window.

### Phase 2 — Editing & Saving
1. Inline editing for simple strings.
2. `StringArrayEditorDialog` for string arrays.
3. `PluralsEditorDialog` for plurals.
4. Implement `StringsXmlWriter` — write all three types back to XML.
5. Debounced save on edit.
6. Handle new keys and key deletion across all locale files.

### Phase 3 — Polish
1. Cell rendering: highlight missing translations, type icons.
2. Filter support.
3. Add Key / Remove Key / Add Locale toolbar buttons.
4. Auto-refresh on file changes (VFS listener).
5. Undo/redo via `WriteCommandAction`.
6. Comments preservation.
7. Module selector for multi-module projects.

### Phase 4 — Advanced
1. Bulk operations (copy base to all locales, clear locale).
2. Search across values (not just keys).
3. Validation (missing keys, type mismatches across locales, duplicate keys).
4. Inline add of new locale (create folder + `strings.xml`).

---

## 7. Key IntelliJ APIs to Use

| Concern | API |
|---------|-----|
| File scanning | `ProjectFileIndex`, `VirtualFileManager`, `FilenameIndex` |
| XML PSI | `XmlFile`, `XmlTag`, `XmlAttribute` |
| Table | `JBTable`, `TableModel`, `ColumnInfo`, `DefaultCellEditor` |
| Dialogs | `DialogWrapper` (for array/plural editors) |
| Tool window | `ToolWindowFactory`, `ContentFactory` |
| Write-safe | `WriteCommandAction.runWriteCommandAction` |
| File watching | `VirtualFileListener`, `VirtualFileManager.addVirtualFileListener` |
| UI helpers | `ToolbarDecorator`, `AnAction`, `JBList`, `JBTextField` |

---

## 8. Files to Create / Modify

| File | Purpose |
|------|---------|
| `src/main/kotlin/cloud/mallne/editor/ComposeResourcesScanner.kt` | Scans project for `composeResources` and locale folders |
| `src/main/kotlin/cloud/mallne/editor/StringsXmlParser.kt` | Parses `strings.xml` (all 3 types) into model |
| `src/main/kotlin/cloud/mallne/editor/StringsXmlWriter.kt` | Writes model back to `strings.xml` (all 3 types) |
| `src/main/kotlin/cloud/mallne/editor/model/TranslationEntry.kt` | Data classes: `ResourceType`, `ResourceValue`, `TranslationEntry`, `ResourceTableModel` |
| `src/main/kotlin/cloud/mallne/editor/model/ResourceTableModel.kt` | Table model |
| `src/main/kotlin/cloud/mallne/editor/ui/TranslationTablePanel.kt` | Main panel with JBTable + toolbar |
| `src/main/kotlin/cloud/mallne/editor/ui/TranslationTableCellRenderer.kt` | Custom cell rendering |
| `src/main/kotlin/cloud/mallne/editor/ui/StringArrayEditorDialog.kt` | Dialog to edit string-array items |
| `src/main/kotlin/cloud/mallne/editor/ui/PluralsEditorDialog.kt` | Dialog to edit plural quantities |
| `src/main/kotlin/cloud/mallne/editor/toolwindow/TranslationToolWindowFactory.kt` | Tool window factory (replaces `MyToolWindowFactory`) |
| `src/main/resources/META-INF/plugin.xml` | Update plugin config |
| `plans/resource-editor-table-plan.md` | This plan |

---

## 9. Package Structure (final)

```
cloud.mallne.editor
  ├── model
  │   ├── ResourceType.kt
  │   ├── ResourceValue.kt
  │   ├── TranslationEntry.kt
  │   └── ResourceTableModel.kt
  ├── ui
  │   ├── TranslationTablePanel.kt
  │   ├── TranslationTableCellRenderer.kt
  │   ├── StringArrayEditorDialog.kt
  │   └── PluralsEditorDialog.kt
  ├── toolwindow
  │   └── TranslationToolWindowFactory.kt
  ├── ComposeResourcesScanner.kt
  ├── StringsXmlParser.kt
  └── StringsXmlWriter.kt
```

---

## 10. Risks & Notes

- **Large files**: For projects with hundreds of keys, the table should remain smooth — use `JBTable` with lazy rendering.
- **Encoding**: Always use UTF-8 for XML files.
- **Comments**: XML comments are tricky to preserve with simple DOM serialization — prefer PSI-based writes.
- **IntelliJ Platform version**: Currently targeting 2025.3.5. Some APIs may change.
- **Multiple `composeResources` roots**: A project may have multiple modules each with their own `composeResources`. Let the user choose which to edit, or show a selector.
- **Type consistency**: If a key is a `<string>` in the base locale but `<string-array>` in another, the tool must flag this as an error.
- **Plurals quantities vary by locale**: English only needs `one`/`other`, but Polish needs `one`/`few`/`many`/`other`. The dialog should show only the quantities that exist in the base locale (users can add more if needed).
