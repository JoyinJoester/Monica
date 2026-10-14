package takagi.ru.monica.autofill_ng

/** Per-request ownership. A form id is meaningful only inside its original web scope. */
internal class AutofillHtmlFormScopes<T> {
    private data class Owner(val webScope: Int, val ancestor: Int?, val explicitId: String?, val detached: Int)
    private val forms = mutableMapOf<Pair<Int, String>, MutableList<Int>>()
    private val owners = mutableMapOf<T, Owner>()

    fun registerForm(webScope: Int, identity: Int, htmlId: String?) {
        if (!htmlId.isNullOrEmpty()) forms.getOrPut(webScope to htmlId) { mutableListOf() }.add(identity)
    }

    fun registerField(field: T, webScope: Int, ancestor: Int?, explicitId: String?, traversal: Int) {
        owners[field] = Owner(webScope, ancestor, explicitId, -traversal - 1)
    }

    fun owner(field: T?): Int? {
        val value = owners[field] ?: return null
        // Resolve after traversal, so form="..." may point to a later form node.
        // Missing/duplicate ids must never merge unrelated forms or unowned inputs.
        return if (value.explicitId != null) forms[value.webScope to value.explicitId]?.singleOrNull() ?: value.detached
        else value.ancestor
    }
}
