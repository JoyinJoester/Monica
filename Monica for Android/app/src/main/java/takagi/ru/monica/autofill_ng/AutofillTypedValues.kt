package takagi.ru.monica.autofill_ng

import android.app.assist.AssistStructure
import android.content.Intent
import android.os.Build
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import java.text.DateFormatSymbols
import java.util.Locale

/** Convert only the already authorized targets; the structure never adds a new fill target. */
internal object AutofillTypedValues {
    fun fromIntent(intent: Intent): Map<AutofillId, AssistStructure.ViewNode> {
        val structure = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(AutofillManager.EXTRA_ASSIST_STRUCTURE, AssistStructure::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra<AssistStructure>(AutofillManager.EXTRA_ASSIST_STRUCTURE)
        } ?: return emptyMap()
        val nodes = linkedMapOf<AutofillId, AssistStructure.ViewNode>()
        val pending = ArrayDeque<AssistStructure.ViewNode>()
        repeat(structure.windowNodeCount) { pending.add(structure.getWindowNodeAt(it).rootViewNode) }
        var visited = 0
        while (pending.isNotEmpty() && visited++ < 10000) {
            val node = pending.removeFirst()
            node.autofillId?.let { nodes[it] = node }
            repeat(node.childCount) { pending.add(node.getChildAt(it)) }
        }
        return nodes
    }

    fun resolve(node: AssistStructure.ViewNode?, hint: String?, value: String): AutofillValue? {
        if (value.isBlank()) return null
        return when (node?.autofillType) {
            View.AUTOFILL_TYPE_LIST -> optionIndex(hint, value, node.autofillOptions?.map { it.toString() }.orEmpty())
                ?.let(AutofillValue::forList)
            View.AUTOFILL_TYPE_DATE, View.AUTOFILL_TYPE_TOGGLE, View.AUTOFILL_TYPE_NONE -> null
            else -> {
                // Chromium also labels textarea nodes "input", with type="textarea".
                // Only single-line HTML inputs strip line breaks without inserting spaces.
                val html = node?.htmlInfo
                val multiline = html?.tag.equals("textarea", ignoreCase = true) ||
                    html?.attributes?.any { it.first.equals("type", true) && it.second.equals("textarea", true) } == true ||
                    ((node?.inputType ?: 0) and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
                val text = if (AutofillProfileValues.canonical(hint) == "street-address" &&
                    html?.tag.equals("input", ignoreCase = true) && !multiline) value.replace("\n", ", ") else value
                AutofillValue.forText(text)
            }
        }
    }

    fun optionIndex(rawHint: String?, value: String, options: List<String>): Int? {
        val hint = AutofillProfileValues.canonical(rawHint)
        fun key(text: String): String = text.trim().lowercase(Locale.ROOT)
        fun month(text: String): Int? {
            text.trim().toIntOrNull()?.takeIf { it in 1..12 }?.let { return it }
            for (locale in listOf(Locale.getDefault(), Locale.ENGLISH)) {
                val symbols = DateFormatSymbols(locale)
                for (index in 0..11) {
                    if (key(text).trimEnd('.') in listOf(key(symbols.months[index]), key(symbols.shortMonths[index]).trimEnd('.'))) return index + 1
                }
            }
            // Common option labels such as "08 - August".
            return Regex("^(\\d{1,2})\\s*[-/].+").matchEntire(text.trim())?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it in 1..12 }
        }
        val matches = options.indices.filter { index ->
            val option = options[index]
            key(option) == key(value) || when (hint) {
                "country", "country-name" -> AutofillProfileValues.countryCode(value)?.let { it == AutofillProfileValues.countryCode(option) } == true
                "address-level1" -> regionName(value)?.let { key(it) == key(option) } == true || regionName(option)?.let { key(it) == key(value) } == true
                "cc-exp-month" -> month(value)?.let { it == month(option) } == true
                "cc-exp-year" -> value.trim().toIntOrNull()?.let { year ->
                    option.trim().toIntOrNull()?.let { other ->
                        year == other || (year in 2000..2099 && other in 0..99 && year % 100 == other)
                    }
                } == true
                else -> false
            }
        }
        // Duplicate/unknown options are ambiguous. Leave only that field untouched.
        return matches.singleOrNull()
    }
    private fun regionName(value: String): String? = regions[value.trim().uppercase(Locale.ROOT)]
    private val regions = """AL=Alabama|AK=Alaska|AZ=Arizona|AR=Arkansas|CA=California|CO=Colorado|CT=Connecticut|DE=Delaware|DC=Washington D.C.|FL=Florida|GA=Georgia|HI=Hawaii|ID=Idaho|IL=Illinois|IN=Indiana|IA=Iowa|KS=Kansas|KY=Kentucky|LA=Louisiana|ME=Maine|MD=Maryland|MA=Massachusetts|MI=Michigan|MN=Minnesota|MS=Mississippi|MO=Missouri|MT=Montana|NE=Nebraska|NV=Nevada|NH=New Hampshire|NJ=New Jersey|NM=New Mexico|NY=New York|NC=North Carolina|ND=North Dakota|OH=Ohio|OK=Oklahoma|OR=Oregon|PA=Pennsylvania|RI=Rhode Island|SC=South Carolina|SD=South Dakota|TN=Tennessee|TX=Texas|UT=Utah|VT=Vermont|VA=Virginia|WA=Washington|WV=West Virginia|WI=Wisconsin|WY=Wyoming|AB=Alberta|BC=British Columbia|MB=Manitoba|NB=New Brunswick|NL=Newfoundland and Labrador|NS=Nova Scotia|NT=Northwest Territories|NU=Nunavut|ON=Ontario|PE=Prince Edward Island|QC=Quebec|SK=Saskatchewan|YT=Yukon"""
        .split('|').associate { it.substringBefore('=') to it.substringAfter('=') }

}
