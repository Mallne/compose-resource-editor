package cloud.mallne.editor.model

data class PluralItem(val quantity: String, val value: String)

sealed interface ResourceValue {
    data class Simple(val text: String) : ResourceValue
    data class Array(val items: List<String>) : ResourceValue
    data class Plurals(val items: List<PluralItem>) : ResourceValue

    fun isEmptyValue(): Boolean = when (this) {
        is Simple -> text.isEmpty()
        is Array -> items.isEmpty()
        is Plurals -> items.isEmpty()
    }
}
