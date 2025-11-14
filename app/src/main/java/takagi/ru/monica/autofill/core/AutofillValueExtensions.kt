package takagi.ru.monica.autofill.core

import android.view.autofill.AutofillValue

private const val DEFAULT_AUTOFILL_VALUE_TAG = "AutofillValue"

/**
 * Safely converts an [AutofillValue] to text when possible, avoiding crashes on non-text types.
 */
fun AutofillValue?.safeTextOrNull(
    tag: String = DEFAULT_AUTOFILL_VALUE_TAG,
    fieldDescription: String? = null
): String? {
    if (this == null) return null
    if (isText) return textValue?.toString()

    AutofillLogger.w(
        tag,
        buildString {
            append("忽略非文本类型的自动填充值")
            fieldDescription?.let { append(" (" + it + ")") }
            append(", type=")
            append(describeType(this@safeTextOrNull))
        }
    )
    return null
}

private fun describeType(value: AutofillValue): String = when {
    value.isText -> "text"
    value.isList -> "list"
    value.isToggle -> "toggle"
    value.isDate -> "date"
    else -> "unknown"
}
