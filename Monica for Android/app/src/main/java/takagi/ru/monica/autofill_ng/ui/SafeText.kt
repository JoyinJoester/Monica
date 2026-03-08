package takagi.ru.monica.autofill_ng.ui

/**
 * Sanitizes user-controlled text before Compose rendering.
 * - Preserves valid surrogate pairs (emoji, rare scripts)
 * - Replaces isolated surrogate chars with U+FFFD
 * - Drops non-printable control chars except tab/newline
 */
internal fun String.toSafeComposeText(): String {
    if (isEmpty()) return this

    val output = StringBuilder(length)
    var index = 0
    while (index < length) {
        val ch = this[index]
        when {
            ch.isHighSurrogate() -> {
                val next = this.getOrNull(index + 1)
                if (next != null && next.isLowSurrogate()) {
                    output.append(ch)
                    output.append(next)
                    index += 2
                    continue
                }
                output.append('\uFFFD')
            }
            ch.isLowSurrogate() -> {
                output.append('\uFFFD')
            }
            (ch.code in 0x00..0x1F && ch != '\n' && ch != '\t') || ch.code == 0x7F -> {
                // Drop control chars that may break text layout.
            }
            else -> output.append(ch)
        }
        index++
    }

    return output.toString()
}

