package cloud.mallne.editor

import cloud.mallne.editor.model.PluralItem
import cloud.mallne.editor.model.ResourceType
import cloud.mallne.editor.model.ResourceValue
import cloud.mallne.editor.model.TranslationEntry
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.xml.XmlFile
import com.intellij.psi.xml.XmlTag

object StringsXmlParser {

    fun parse(project: Project, root: ComposeResourcesRoot): List<TranslationEntry> {
        val allEntries = mutableMapOf<String, TranslationEntry>()

        for (localeDir in root.localeDirs) {
            val xmlFile = localeDir.stringsXml ?: continue
            val psiFile = PsiManager.getInstance(project).findFile(xmlFile) as? XmlFile ?: continue
            val rootTag = psiFile.rootTag ?: continue

            for (child in rootTag.subTags) {
                val name = child.getAttributeValue("name") ?: continue
                val entry = allEntries.getOrPut(name) {
                    TranslationEntry(
                        key = name,
                        type = detectType(child),
                        baseValue = ResourceValue.Simple("")
                    )
                }

                val value = parseTagValue(child)
                val notTranslatable = child.getAttributeValue("translatable") == "false"
                        || child.getAttributeValue("not-translatable") == "true"
                if (localeDir.locale == null) {
                    val updated = entry.copy(
                        type = detectType(child),
                        baseValue = value,
                        translatable = !notTranslatable
                    )
                    allEntries[name] = updated
                } else {
                    entry.translations[localeDir.locale] = value
                }
            }
        }

        return allEntries.values.toList().sortedBy { it.key }
    }

    private fun detectType(tag: XmlTag): ResourceType = when (tag.name) {
        "string-array" -> ResourceType.STRING_ARRAY
        "plurals" -> ResourceType.PLURALS
        else -> ResourceType.STRING
    }

    private fun parseTagValue(tag: XmlTag): ResourceValue = when (tag.name) {
        "string" -> ResourceValue.Simple(getUnescapedText(tag))
        "string-array" -> {
            val items = tag.subTags
                .filter { it.name == "item" }
                .map { getUnescapedText(it) }
            ResourceValue.Array(items)
        }
        "plurals" -> {
            val items = tag.subTags
                .filter { it.name == "item" }
                .mapNotNull { item ->
                    val quantity = item.getAttributeValue("quantity") ?: return@mapNotNull null
                    PluralItem(quantity, getUnescapedText(item))
                }
            ResourceValue.Plurals(items)
        }
        else -> ResourceValue.Simple("")
    }

    private fun getUnescapedText(tag: XmlTag): String {
        val texts = tag.value.textElements
        if (texts.isEmpty()) return ""
        return texts.joinToString("") { it.value }
    }
}
