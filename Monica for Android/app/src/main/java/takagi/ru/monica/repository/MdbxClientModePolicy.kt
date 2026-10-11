package takagi.ru.monica.repository

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File
import takagi.ru.monica.R
import takagi.ru.monica.data.LocalMdbxDatabase
import takagi.ru.monica.data.MdbxTigaMode
import takagi.ru.monica.data.isUnsupportedGlitter

/** Android support is narrower than the MDBX format. Never downgrade an unsupported vault. */
internal object MdbxClientModePolicy {
    fun isEnvelope(value: String?): Boolean = value?.startsWith("glitter-hw:") == true

    private fun unsupported(context: Context): Nothing = throw Mdbx2OperationException(
        Mdbx2FailureKind.UNSUPPORTED_SOURCE,
        context.getString(R.string.mdbx_client_mode_unsupported),
    )

    fun requireSupported(context: Context, mode: MdbxTigaMode) {
        if (mode == MdbxTigaMode.GLITTER) unsupported(context)
    }

    fun requireSupported(context: Context, database: LocalMdbxDatabase) {
        if (database.isUnsupportedGlitter) unsupported(context)
    }

    /** Read only the public format marker before native open, migration or password derivation. */
    fun requireSupportedFile(context: Context, file: File) {
        SQLiteDatabase.openDatabase(file.absolutePath, null,
            SQLiteDatabase.OPEN_READONLY or SQLiteDatabase.NO_LOCALIZED_COLLATORS,
            // Android's default corruption handler deletes files even for read-only opens.
            // A format probe must preserve the user's original database for recovery.
            { _ -> }).use { db ->
            db.rawQuery("SELECT default_tiga_mode FROM vault_meta LIMIT 1", null).use { cursor ->
                if (cursor.moveToFirst() && cursor.getString(0).equals("glitter", ignoreCase = true)) {
                    unsupported(context)
                }
            }
        }
    }
}
