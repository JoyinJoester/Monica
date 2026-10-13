package takagi.ru.monica.util

import java.io.BufferedReader
import java.net.URLDecoder
import java.util.Locale
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import takagi.ru.monica.data.ItemType

/** CSV from the standalone Bitwarden Authenticator, not the password manager. */
internal object BitwardenAuthenticatorCsv {
    private val base32 = Regex("[A-Z2-7]+={0,6}")

    fun read(reader: BufferedReader): DataExportImportManager.CsvImportResult {
        val header = CsvRecords.read(reader)?.removePrefix("\uFEFF")
            ?: throw IllegalArgumentException("Empty authenticator CSV")
        val columns = CsvRecords.fields(header).map { it.trim().lowercase(Locale.ROOT) }
        require(columns.size == columns.distinct().size &&
            columns.containsAll(listOf("name", "login_totp")) &&
            "login_password" !in columns && "login_username" !in columns) {
            "Not a Bitwarden Authenticator CSV"
        }
        val legacyColumns = columns == listOf("folder", "favorite", "type", "name", "login_uri", "login_totp")
        val items = mutableListOf<DataExportImportManager.ExportItem>()
        var rejected = 0
        var records = 0
        val now = System.currentTimeMillis()
        while (true) {
            val record = CsvRecords.read(reader) ?: break
            if (record.isBlank()) continue
            require(++records <= 50_000) { "Too many authenticator CSV records" }
            val item = runCatching {
                val fields = CsvRecords.fields(record)
                // Android exports have a six-column header but append three redundant values.
                // The OTP URI is authoritative; never mistake trailing values for credentials.
                require(fields.size == columns.size || (legacyColumns && fields.size == 9))
                fun field(name: String) = fields.getOrNull(columns.indexOf(name)).orEmpty()
                require(field("type").trim().lowercase(Locale.ROOT) in setOf("", "1", "login"))
                val raw = field("login_totp").trim()
                require(raw.startsWith("otpauth://totp/", true) || raw.startsWith("otpauth://hotp/", true))
                validateParameters(raw)
                val data = requireNotNull(TotpDataResolver.fromAuthenticatorKey(raw,
                    fallbackIssuer = field("name")))
                require(data.secret.matches(base32))
                DataExportImportManager.ExportItem(
                    id = records.toLong(), itemType = ItemType.TOTP.name,
                    title = suggestedTotpTitle(data, field("name")), itemData = Json.encodeToString(data),
                    notes = field("notes"), isFavorite = field("favorite").trim().lowercase(Locale.ROOT) in setOf("1", "true"),
                    imagePaths = "", createdAt = now, updatedAt = now,
                )
            }.getOrNull()
            if (item == null) rejected++ else items += item
        }
        return DataExportImportManager.CsvImportResult(items, rejected)
    }

    private fun validateParameters(uri: String) {
        val parameters = uri.substringAfter('?', "").split('&').filter { it.isNotBlank() }.map {
            URLDecoder.decode(it.substringBefore('='), "UTF-8") to
                URLDecoder.decode(it.substringAfter('=', ""), "UTF-8")
        }
        require(parameters.map { it.first }.distinct().size == parameters.size)
        val values = parameters.toMap()
        require(!values["secret"].isNullOrBlank())
        values["algorithm"]?.let { require(it.uppercase(Locale.ROOT) in setOf("SHA1", "SHA256", "SHA512")) }
        values["digits"]?.let { require(it.toIntOrNull() in 4..10) }
        values["period"]?.let { require((it.toIntOrNull() ?: 0) > 0) }
        values["counter"]?.let { require((it.toLongOrNull() ?: -1) >= 0) }
        if (uri.startsWith("otpauth://hotp/", true)) require("counter" in values)
    }
}
