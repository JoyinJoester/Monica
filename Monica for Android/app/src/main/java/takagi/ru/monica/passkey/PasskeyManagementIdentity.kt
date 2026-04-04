package takagi.ru.monica.passkey

import takagi.ru.monica.data.PasskeyEntry

fun PasskeyEntry.managementRecordIdOrNull(): Long? = id.takeIf { it > 0L }

fun PasskeyEntry.managementKey(): String {
    return managementRecordIdOrNull()?.let { recordId ->
        "passkey:$recordId"
    } ?: buildString {
        append("passkey_ref:")
        append(boundPasswordId ?: "none")
        append(':')
        append(credentialId)
        append(':')
        append(rpId)
        append(':')
        append(userName)
        append(':')
        append(userDisplayName)
        append(':')
        append(bitwardenCipherId.orEmpty())
    }
}
