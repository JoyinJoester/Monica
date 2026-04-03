package takagi.ru.monica.security

object MasterPasswordPolicy {
    const val MIN_LENGTH = 3

    data class InputTransformResult(
        val sanitized: String,
        val filteredUnsupportedCharacters: Boolean
    )

    fun transformInput(raw: String): InputTransformResult {
        val sanitized = raw.filterNot { it.isISOControl() }
        return InputTransformResult(
            sanitized = sanitized,
            filteredUnsupportedCharacters = sanitized != raw
        )
    }

    fun isEmpty(password: String): Boolean = password.isEmpty()

    fun meetsMinLength(password: String): Boolean = password.length >= MIN_LENGTH
}
