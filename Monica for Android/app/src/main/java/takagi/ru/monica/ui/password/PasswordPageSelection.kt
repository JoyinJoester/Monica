package takagi.ru.monica.ui.password

private const val PASSWORD_SELECTION_PREFIX = "password:"

internal fun passwordSelectionKey(id: Long): String = "$PASSWORD_SELECTION_PREFIX$id"

internal fun selectionKeysForPasswords(ids: Iterable<Long>): Set<String> {
    return ids.mapTo(linkedSetOf(), ::passwordSelectionKey)
}

internal fun selectedPasswordIds(keys: Set<String>): Set<Long> {
    return keys.mapNotNullTo(linkedSetOf(), ::passwordIdFromSelectionKey)
}

internal fun passwordIdFromSelectionKey(key: String): Long? {
    return key.removePrefix(PASSWORD_SELECTION_PREFIX)
        .takeIf { it.length != key.length }
        ?.toLongOrNull()
}
