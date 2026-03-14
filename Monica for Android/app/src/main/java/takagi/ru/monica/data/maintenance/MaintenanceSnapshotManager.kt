package takagi.ru.monica.data.maintenance

import android.content.ContentValues
import android.database.Cursor
import android.util.Base64
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import takagi.ru.monica.data.PasswordDatabase
import takagi.ru.monica.data.model.TimelineMaintenanceSnapshotPayload
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * 专用快照管理器：负责维护合并前的完整行级快照和事务恢复。
 */
class MaintenanceSnapshotManager(
    private val database: PasswordDatabase
) {
    data class RestoreStats(
        val success: Boolean,
        val deletedPasswords: Int = 0,
        val deletedSecureItems: Int = 0,
        val upsertedPasswords: Int = 0,
        val upsertedSecureItems: Int = 0,
        val usedLegacyMode: Boolean = false
    )

    suspend fun createPayload(): TimelineMaintenanceSnapshotPayload = withContext(Dispatchers.IO) {
        val writableDb = database.openHelper.writableDatabase
        val passwordRows = dumpRowsAsJson(
            db = writableDb,
            table = PASSWORD_TABLE,
            whereClause = "isDeleted = 0 AND isArchived = 0"
        )
        val secureRows = dumpRowsAsJson(
            db = writableDb,
            table = SECURE_TABLE,
            whereClause = "isDeleted = 0"
        )

        val passwordIds = passwordRows.mapNotNull { parseId(it) }
        val secureIds = secureRows.mapNotNull { parseId(it) }

        TimelineMaintenanceSnapshotPayload(
            schemaVersion = ROW_SNAPSHOT_SCHEMA_VERSION,
            passwordIds = passwordIds,
            secureItemIds = secureIds,
            compression = COMPRESSION_GZIP_BASE64,
            passwordRowsCompressedChunks = compressRowsToChunks(passwordRows),
            secureItemRowsCompressedChunks = compressRowsToChunks(secureRows)
        )
    }

    suspend fun restorePayload(payload: TimelineMaintenanceSnapshotPayload): RestoreStats = withContext(Dispatchers.IO) {
        runCatching {
            val writableDb = database.openHelper.writableDatabase
            val passwordRows = resolveRows(payload.passwordRows, payload.passwordRowsCompressedChunks, payload.compression)
            val secureRows = resolveRows(payload.secureItemRows, payload.secureItemRowsCompressedChunks, payload.compression)
            if (passwordRows.isNotEmpty() || secureRows.isNotEmpty()) {
                restoreFromRowSnapshots(writableDb, passwordRows, secureRows)
            } else {
                restoreFromLegacyIds(writableDb, payload)
            }
        }.getOrElse {
            RestoreStats(success = false)
        }
    }

    private fun resolveRows(
        inlineRows: List<String>,
        compressedChunks: List<String>,
        compression: String?
    ): List<String> {
        if (inlineRows.isNotEmpty()) return inlineRows
        if (compressedChunks.isEmpty()) return emptyList()
        if (compression != COMPRESSION_GZIP_BASE64) return emptyList()
        return decodeRowsFromChunks(compressedChunks)
    }

    private fun restoreFromRowSnapshots(
        db: SupportSQLiteDatabase,
        passwordRows: List<String>,
        secureRows: List<String>
    ): RestoreStats {
        db.beginTransaction()
        try {
            val passwordStats = restoreTableRows(
                db = db,
                table = PASSWORD_TABLE,
                activeWhereClause = "isDeleted = 0 AND isArchived = 0",
                rows = passwordRows
            )
            val secureStats = restoreTableRows(
                db = db,
                table = SECURE_TABLE,
                activeWhereClause = "isDeleted = 0",
                rows = secureRows
            )
            db.setTransactionSuccessful()
            return RestoreStats(
                success = true,
                deletedPasswords = passwordStats.first,
                deletedSecureItems = secureStats.first,
                upsertedPasswords = passwordStats.second,
                upsertedSecureItems = secureStats.second,
                usedLegacyMode = false
            )
        } finally {
            db.endTransaction()
        }
    }

    private fun restoreFromLegacyIds(
        db: SupportSQLiteDatabase,
        payload: TimelineMaintenanceSnapshotPayload
    ): RestoreStats {
        db.beginTransaction()
        try {
            val passwordIdSet = payload.passwordIds.toSet()
            val secureIdSet = payload.secureItemIds.toSet()

            val currentPasswordIds = queryActiveIds(
                db,
                "SELECT id FROM $PASSWORD_TABLE WHERE isDeleted = 0 AND isArchived = 0"
            )
            val currentSecureIds = queryActiveIds(
                db,
                "SELECT id FROM $SECURE_TABLE WHERE isDeleted = 0"
            )

            var deletedPasswords = 0
            var deletedSecureItems = 0

            currentPasswordIds.filterNot { it in passwordIdSet }.forEach { id ->
                db.execSQL("DELETE FROM $PASSWORD_TABLE WHERE id = ?", arrayOf(id))
                deletedPasswords += 1
            }
            currentSecureIds.filterNot { it in secureIdSet }.forEach { id ->
                db.execSQL("DELETE FROM $SECURE_TABLE WHERE id = ?", arrayOf(id))
                deletedSecureItems += 1
            }

            db.setTransactionSuccessful()
            return RestoreStats(
                success = true,
                deletedPasswords = deletedPasswords,
                deletedSecureItems = deletedSecureItems,
                upsertedPasswords = 0,
                upsertedSecureItems = 0,
                usedLegacyMode = true
            )
        } finally {
            db.endTransaction()
        }
    }

    private fun restoreTableRows(
        db: SupportSQLiteDatabase,
        table: String,
        activeWhereClause: String,
        rows: List<String>
    ): Pair<Int, Int> {
        val rowObjects = rows.map { JSONObject(it) }
        val snapshotIds = rowObjects.mapNotNull { obj -> obj.optLong("id").takeIf { it > 0L } }.toSet()
        val currentIds = queryActiveIds(db, "SELECT id FROM $table WHERE $activeWhereClause")

        var deleted = 0
        currentIds.filterNot { it in snapshotIds }.forEach { id ->
            db.execSQL("DELETE FROM $table WHERE id = ?", arrayOf(id))
            deleted += 1
        }

        var upserted = 0
        rowObjects.forEach { obj ->
            val values = jsonObjectToContentValues(obj)
            db.insert(table, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, values)
            upserted += 1
        }
        return deleted to upserted
    }

    private fun dumpRowsAsJson(
        db: SupportSQLiteDatabase,
        table: String,
        whereClause: String
    ): List<String> {
        val rows = mutableListOf<String>()
        db.query("SELECT * FROM $table WHERE $whereClause").use { cursor ->
            val names = cursor.columnNames
            while (cursor.moveToNext()) {
                val obj = JSONObject()
                names.forEachIndexed { index, columnName ->
                    obj.put(columnName, readCursorValue(cursor, index))
                }
                rows += obj.toString()
            }
        }
        return rows
    }

    private fun readCursorValue(cursor: Cursor, index: Int): Any? {
        return when (cursor.getType(index)) {
            Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
            Cursor.FIELD_TYPE_INTEGER -> cursor.getLong(index)
            Cursor.FIELD_TYPE_FLOAT -> cursor.getDouble(index)
            Cursor.FIELD_TYPE_STRING -> cursor.getString(index)
            Cursor.FIELD_TYPE_BLOB -> {
                val blob = cursor.getBlob(index)
                JSONObject()
                    .put(BLOB_MARKER_KEY, Base64.encodeToString(blob, Base64.NO_WRAP))
            }
            else -> JSONObject.NULL
        }
    }

    private fun jsonObjectToContentValues(obj: JSONObject): ContentValues {
        val values = ContentValues()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.opt(key)
            when {
                value == null || value == JSONObject.NULL -> values.putNull(key)
                value is Boolean -> values.put(key, if (value) 1 else 0)
                value is Int -> values.put(key, value)
                value is Long -> values.put(key, value)
                value is Double -> values.put(key, value)
                value is String -> values.put(key, value)
                value is JSONObject && value.has(BLOB_MARKER_KEY) -> {
                    val raw = value.optString(BLOB_MARKER_KEY, "")
                    values.put(key, Base64.decode(raw, Base64.NO_WRAP))
                }
                else -> values.put(key, value.toString())
            }
        }
        return values
    }

    private fun queryActiveIds(db: SupportSQLiteDatabase, sql: String): List<Long> {
        return buildList {
            db.query(sql).use { cursor ->
                val index = cursor.getColumnIndex("id")
                while (cursor.moveToNext()) {
                    add(cursor.getLong(index))
                }
            }
        }
    }

    private fun parseId(rowJson: String): Long? {
        return runCatching {
            val obj = JSONObject(rowJson)
            obj.optLong("id").takeIf { it > 0L }
        }.getOrNull()
    }

    private fun compressRowsToChunks(rows: List<String>): List<String> {
        if (rows.isEmpty()) return emptyList()
        val payloadJson = JSONObject().put("rows", rows).toString()
        val output = ByteArrayOutputStream()
        GZIPOutputStream(output).bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(payloadJson)
        }
        val encoded = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        return encoded.chunked(COMPRESSED_CHUNK_SIZE)
    }

    private fun decodeRowsFromChunks(chunks: List<String>): List<String> {
        return runCatching {
            val encoded = chunks.joinToString(separator = "")
            val compressed = Base64.decode(encoded, Base64.NO_WRAP)
            val text = GZIPInputStream(ByteArrayInputStream(compressed)).bufferedReader(Charsets.UTF_8).use {
                it.readText()
            }
            val root = JSONObject(text)
            val array = root.optJSONArray("rows") ?: return emptyList()
            buildList {
                for (i in 0 until array.length()) {
                    val row = array.optString(i, "")
                    if (row.isNotEmpty()) add(row)
                }
            }
        }.getOrElse { emptyList() }
    }

    private companion object {
        private const val PASSWORD_TABLE = "password_entries"
        private const val SECURE_TABLE = "secure_items"
        private const val BLOB_MARKER_KEY = "__blob_base64"
        private const val ROW_SNAPSHOT_SCHEMA_VERSION = 2
        private const val COMPRESSION_GZIP_BASE64 = "gzip-base64"
        private const val COMPRESSED_CHUNK_SIZE = 240_000
    }
}
