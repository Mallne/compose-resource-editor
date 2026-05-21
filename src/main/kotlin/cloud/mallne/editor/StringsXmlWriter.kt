package cloud.mallne.editor

import cloud.mallne.editor.model.ResourceValue
import cloud.mallne.editor.model.TranslationEntry
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.vfs.VfsUtil
import org.jdom.Element

object StringsXmlWriter {

    fun save(
        root: ComposeResourcesRoot,
        entries: List<TranslationEntry>,
        modifiedLocale: String? = null
    ) {
        ApplicationManager.getApplication().runWriteAction {
            for (localeDir in root.localeDirs) {
                val locale = localeDir.locale
                if (modifiedLocale != null) {
                    if (modifiedLocale == "Base" && locale != null) continue
                    if (modifiedLocale != "Base" && locale != modifiedLocale) continue
                }

                val dir = localeDir.dir
                val existing = dir.children.find { !it.isDirectory && it.name == "strings.xml" }
                val file = existing ?: dir.createChildData(this, "strings.xml")

                val xml = buildXml(entries, locale)
                VfsUtil.saveText(file, xml)
            }
        }
    }

    private fun buildXml(entries: List<TranslationEntry>, locale: String?): String {
        val root = Element("resources")
        for (entry in entries.sortedBy { it.key }) {
            val value = if (locale == null) entry.baseValue
            else entry.translations[locale] ?: continue

            if (locale != null && value.isEmptyValue()) continue

            val element: Element = when (value) {
                is ResourceValue.Simple -> {
                    val el = Element("string").setAttribute("name", entry.key)
                    if (!entry.translatable) el.setAttribute("translatable", "false")
                    el.text = value.text
                    el
                }
                is ResourceValue.Array -> {
                    val el = Element("string-array").setAttribute("name", entry.key)
                    if (!entry.translatable) el.setAttribute("translatable", "false")
                    for (item in value.items) {
                        el.addContent(Element("item").setText(item))
                    }
                    el
                }
                is ResourceValue.Plurals -> {
                    val el = Element("plurals").setAttribute("name", entry.key)
                    if (!entry.translatable) el.setAttribute("translatable", "false")
                    for (item in value.items) {
                        el.addContent(Element("item").setAttribute("quantity", item.quantity).setText(item.value))
                    }
                    el
                }
            }
            root.addContent(element)
        }

        val xml = JDOMUtil.write(root, System.lineSeparator()).trimEnd()
        return xml.replace(Regex("(?<!&amp;)&quot;"), "\"")
    }
}
