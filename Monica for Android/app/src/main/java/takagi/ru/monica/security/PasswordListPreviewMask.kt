package takagi.ru.monica.security

private const val PREVIEW_MASK = "••••••"

// Legacy imports can put card numbers in the username/cardholder subtitle.
// Match an entire digit run, including space/dash formatted card numbers.
private val CARD_NUMBER_IN_PREVIEW = Regex("(?<![0-9])[0-9](?:[\\p{Zs}\\t-]*[0-9]){11,}(?![0-9])")

fun maskCardNumbersInPasswordPreview(value: String): String =
    CARD_NUMBER_IN_PREVIEW.replace(value) { match ->
        "•••• ${match.value.filter(Char::isDigit).takeLast(4)}"
    }

/** Passive previews only: retain small identifying hints, never an entire short note. */
fun maskPasswordNotePreview(value: String): String {
    val line = value.lineSequence().firstOrNull(String::isNotBlank)?.trim() ?: return ""
    val length = line.codePointCount(0, line.length)
    if (length <= 4) return PREVIEW_MASK
    val prefix = line.substring(0, line.offsetByCodePoints(0, 2))
    val suffix = line.substring(line.offsetByCodePoints(0, length - 2))
    return "$prefix$PREVIEW_MASK$suffix"
}
