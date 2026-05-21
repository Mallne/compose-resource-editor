package cloud.mallne.editor.model

data class TranslationEntry(
    val key: String,
    val type: ResourceType,
    val baseValue: ResourceValue,
    val translations: MutableMap<String, ResourceValue> = mutableMapOf(),
    val translatable: Boolean = true,
    val comment: String? = null
) {
    fun displayValue(locale: String): ResourceValue? =
        translations[locale]

    fun allLocales(): Set<String> = translations.keys

    fun hasMissingTranslations(requiredLocales: List<String>): Boolean {
        if (!translatable || requiredLocales.isEmpty()) return false
        return requiredLocales.any { locale ->
            val value = translations[locale]
            value == null || value.isEmptyValue()
        }
    }
}
