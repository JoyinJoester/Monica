package takagi.ru.monica.passkey

import android.content.Context
import kotlinx.coroutines.CancellationException
import takagi.ru.monica.data.PasskeyEntry

internal enum class PasskeyPortability {
    PORTABLE, DEVICE_ONLY, KEY_UNAVAILABLE, BACKUP_RESTRICTED, COUNTER_HISTORY, ALGORITHM_RESTRICTED, INVALID_FLAGS, UNKNOWN;

    companion object {
        fun classify(entry: PasskeyEntry, usable: Boolean, exportable: Boolean): PasskeyPortability = classify(
            entry.backupEligible, entry.backupState, entry.signCount, entry.publicKeyAlgorithm, usable, exportable)

        fun classify(backupEligible: Boolean?, backupState: Boolean?, signCount: Long, algorithm: Int,
            usable: Boolean, exportable: Boolean): PasskeyPortability = when {
            !usable -> KEY_UNAVAILABLE
            !exportable -> DEVICE_ONLY
            backupEligible == false && backupState == true -> INVALID_FLAGS
            backupEligible == false -> BACKUP_RESTRICTED
            signCount > 0 -> COUNTER_HISTORY
            algorithm != PasskeyEntry.ALGORITHM_ES256 -> ALGORITHM_RESTRICTED
            else -> PORTABLE
        }

        fun inspect(context: Context, entry: PasskeyEntry): PasskeyPortability = try {
            val resolved = PasskeyPrivateKeyStore.resolve(context, entry.privateKeyAlias)
            classify(entry, PasskeyPrivateKeySupport.hasUsablePrivateKey(resolved),
                PasskeyPrivateKeySupport.normalizeForBitwardenUpload(resolved) != null)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            UNKNOWN
        }
    }
}
