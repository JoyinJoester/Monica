package takagi.ru.monica.data.model

enum class CardBrand(val displayName: String) {
    VISA("Visa"),
    MASTERCARD("Mastercard"),
    AMERICAN_EXPRESS("American Express"),
    DINERS_CLUB("Diners Club"),
    DISCOVER("Discover"),
    JCB("JCB"),
    UNIONPAY("UnionPay"),
    MAESTRO("Maestro"),
    MIR("Mir"),
    RUPAY("RuPay"),
    ELO("Elo"),
    DANKORT("Dankort"),
    MADA("Mada"),
    MEEZA("Meeza"),
    TROY("Troy"),
    UATP("UATP"),
    FORBRUGSFORENINGEN("Forbrugsforeningen"),
    UNKNOWN("Card")
}

object CardBrandDetector {
    private data class Rule(
        val brand: CardBrand,
        val pattern: Regex,
        val eagerPattern: Regex
    )

    // Patterns are based on the MIT-licensed creditcards-types rules used by Keyguard.
    private val rules = listOf(
        Rule(
            CardBrand.UNIONPAY,
            "^62[0-5]\\d{13,16}$".toRegex(),
            "^62".toRegex()
        ),
        Rule(
            CardBrand.ELO,
            "^(4[035]|5[0]|6[235])(6[7263]|9[90]|1[2416]|7[736]|8[9]|0[04579]|5[0])([0-9])([0-9])\\d{10}$".toRegex(),
            "^(4[035]|5[0]|6[235])(6[7263]|9[90]|1[2416]|7[736]|8[9]|0[04579]|5[0])([0-9])([0-9])".toRegex()
        ),
        Rule(
            CardBrand.MADA,
            "^(4(0(0861|1757|3024|6136|6996|7(197|395)|9201)|1(2565|0621|0685|7633|9593)|2(0132|1141|281(7|8|9)|689700|8(331|67(1|2|3)))|3(1361|2328|4107|9954)|4(0(533|647|795)|5564|6(393|404|672))|5(5(036|708)|7865|7997|8456)|6(2220|854(0|1|2|3))|7(4491)|8(301(0|1)|4783|609(4|5|6)|931(7|8|9))|93428)|5(0(4300|6968|8160)|13213|2(0058|1076|4(130|514)|9(415|741))|3(0(060|906)|1(095|196)|2013|5(825|989)|6023|7767|9931)|4(3(085|357)|9760)|5(4180|7606|8563|8848)|8(5265|8(8(4(5|6|7|8|9)|5(0|1))|98(2|3))|9(005|206)))|6(0(4906|5141)|36120)|9682(0(1|2|3|4|5|6|7|8|9)|1(0|1)))\\d{10}$".toRegex(),
            "^(4(0(0861|1757|3024|6136|6996|7(197|395)|9201)|1(2565|0621|0685|7633|9593)|2(0132|1141|281(7|8|9)|689700|8(331|67(1|2|3)))|3(1361|2328|4107|9954)|4(0(533|647|795)|5564|6(393|404|672))|5(5(036|708)|7865|7997|8456)|6(2220|854(0|1|2|3))|7(4491)|8(301(0|1)|4783|609(4|5|6)|931(7|8|9))|93428)|5(0(4300|6968|8160)|13213|2(0058|1076|4(130|514)|9(415|741))|3(0(060|906)|1(095|196)|2013|5(825|989)|6023|7767|9931)|4(3(085|357)|9760)|5(4180|7606|8563|8848)|8(5265|8(8(4(5|6|7|8|9)|5(0|1))|98(2|3))|9(005|206)))|6(0(4906|5141)|36120)|9682(0(1|2|3|4|5|6|7|8|9)|1(0|1)))".toRegex()
        ),
        Rule(
            CardBrand.MEEZA,
            "^5078(03|08|09|10)\\d{10}$".toRegex(),
            "^5078(03|08|09|10)".toRegex()
        ),
        Rule(
            CardBrand.MIR,
            "^220[0-4]\\d{12}$".toRegex(),
            "^220[0-4]".toRegex()
        ),
        Rule(
            CardBrand.TROY,
            "^9792\\d{12}$".toRegex(),
            "^9792".toRegex()
        ),
        Rule(
            CardBrand.DANKORT,
            "^5019\\d{12}$".toRegex(),
            "^5019".toRegex()
        ),
        Rule(
            CardBrand.FORBRUGSFORENINGEN,
            "^600722\\d{10}$".toRegex(),
            "^600".toRegex()
        ),
        Rule(
            CardBrand.UATP,
            "^1\\d{14}$".toRegex(),
            "^1".toRegex()
        ),
        Rule(
            CardBrand.AMERICAN_EXPRESS,
            "^3[47]\\d{13}$".toRegex(),
            "^3[47]".toRegex()
        ),
        Rule(
            CardBrand.DINERS_CLUB,
            "^3(0[0-5]|[68]\\d)\\d{11,16}$".toRegex(),
            "^3(0|[68])".toRegex()
        ),
        Rule(
            CardBrand.DISCOVER,
            "^(6011\\d{12}|65\\d{14}|64[4-9]\\d{13}|622(12[6-9]|1[3-9]\\d|[2-8]\\d{2}|9[01]\\d|92[0-5])\\d{10})$".toRegex(),
            "^(6011|65|64[4-9]|622(12[6-9]|1[3-9]|[2-8]|9[01]|92[0-5]))".toRegex()
        ),
        Rule(
            CardBrand.JCB,
            "^35\\d{14}$".toRegex(),
            "^35".toRegex()
        ),
        Rule(
            CardBrand.MAESTRO,
            "^(5018|5020|5038|5893|6304|6759|6761|6762|6763)\\d{8,15}$".toRegex(),
            "^(5(018|0[23]|[68])|6[37]|60111|60115|60117([56]|7[56])|60118[0-5]|64[0-3]|66)".toRegex()
        ),
        Rule(
            CardBrand.RUPAY,
            "^(60\\d|65\\d|81\\d|82\\d|508|353|356)\\d{13}$".toRegex(),
            "^(60|65|81|82|508|353|356)".toRegex()
        ),
        Rule(
            CardBrand.MASTERCARD,
            "^(5[1-5][0-9]{2}|222[1-9]|22[3-9][0-9]|2[3-6][0-9]{2}|27[01][0-9]|2720)\\d{12}$".toRegex(),
            "^(2[3-7]|22[2-9]|5[1-5])".toRegex()
        ),
        Rule(
            CardBrand.VISA,
            "^4\\d{12}(\\d{3}|\\d{6})?$".toRegex(),
            "^4".toRegex()
        )
    )

    fun detect(number: String, storedBrand: String = ""): CardBrand =
        detectInternal(number = number, storedBrand = storedBrand, allowPartial = true)

    fun detectStoredCard(number: String, storedBrand: String = ""): CardBrand =
        detectInternal(number = number, storedBrand = storedBrand, allowPartial = false)

    private fun detectInternal(
        number: String,
        storedBrand: String,
        allowPartial: Boolean
    ): CardBrand {
        val brandFromName = fromName(storedBrand)
        if (brandFromName != CardBrand.UNKNOWN) {
            return brandFromName
        }

        val digits = number.filter(Char::isDigit)
        if (digits.isBlank()) {
            return CardBrand.UNKNOWN
        }

        val exactBrand = rules.firstOrNull { it.pattern.matches(digits) }?.brand
        if (exactBrand != null || !allowPartial) {
            return exactBrand ?: CardBrand.UNKNOWN
        }

        return rules.firstOrNull { it.eagerPattern.containsMatchIn(digits) }?.brand
            ?: CardBrand.UNKNOWN
    }

    fun fromName(name: String): CardBrand {
        val normalized = name
            .trim()
            .lowercase()
            .replace(" ", "")
            .replace("-", "")
            .replace("_", "")
        if (normalized.isBlank()) {
            return CardBrand.UNKNOWN
        }

        return when {
            normalized.contains("unionpay") ||
                normalized.contains("chinaunionpay") ||
                normalized.contains("银联") -> CardBrand.UNIONPAY
            normalized.contains("americanexpress") ||
                normalized.contains("amex") ||
                normalized.contains("美国运通") -> CardBrand.AMERICAN_EXPRESS
            normalized.contains("dinersclub") ||
                normalized.contains("diners") -> CardBrand.DINERS_CLUB
            normalized.contains("mastercard") ||
                normalized == "master" ||
                normalized.contains("万事达") -> CardBrand.MASTERCARD
            normalized.contains("visa") -> CardBrand.VISA
            normalized.contains("discover") -> CardBrand.DISCOVER
            normalized.contains("maestro") -> CardBrand.MAESTRO
            normalized.contains("mir") -> CardBrand.MIR
            normalized.contains("rupay") -> CardBrand.RUPAY
            normalized.contains("jcb") -> CardBrand.JCB
            normalized.contains("elo") -> CardBrand.ELO
            normalized.contains("dankort") -> CardBrand.DANKORT
            normalized.contains("mada") -> CardBrand.MADA
            normalized.contains("meeza") -> CardBrand.MEEZA
            normalized.contains("troy") -> CardBrand.TROY
            normalized.contains("uatp") -> CardBrand.UATP
            normalized.contains("forbrugsforeningen") -> CardBrand.FORBRUGSFORENINGEN
            else -> when (normalized) {
            "amex", "americanexpress" -> CardBrand.AMERICAN_EXPRESS
            "diners", "dinersclub" -> CardBrand.DINERS_CLUB
            "mastercard", "master" -> CardBrand.MASTERCARD
            "unionpay", "union" -> CardBrand.UNIONPAY
            "rupay" -> CardBrand.RUPAY
            "forbrugsforeningen" -> CardBrand.FORBRUGSFORENINGEN
            else -> CardBrand.values().firstOrNull {
                it.name.lowercase().replace("_", "") == normalized ||
                    it.displayName.lowercase().replace(" ", "") == normalized
            } ?: CardBrand.UNKNOWN
            }
        }
    }
}
