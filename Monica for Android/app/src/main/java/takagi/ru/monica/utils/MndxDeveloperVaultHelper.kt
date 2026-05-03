package takagi.ru.monica.utils

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class MndxDeveloperVaultResult(
    val vaultName: String,
    val remotePath: String,
    val fileCount: Int
)

class MndxDeveloperVaultHelper(context: Context) {
    private val appContext = context.applicationContext
    private val oneDriveBackupHelper = OneDriveBackupHelper(appContext)

    suspend fun createDemoVaultInOneDrive(): Result<MndxDeveloperVaultResult> = withContext(Dispatchers.IO) {
        runCatching {
            val config = oneDriveBackupHelper.getConfig()
                ?: throw IllegalStateException("OneDrive backup folder is not configured")
            val source = OneDriveKeePassFileSource(
                context = appContext,
                accountIdentifier = config.accountId
            )
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val vaultName = "MonicaDemo_$timestamp.mndx"
            val root = source.createDirectory(config.folderPath, vaultName)

            DEMO_DIRECTORIES.forEach { relativePath ->
                ensureDirectoryPath(source, root.path, relativePath)
            }

            var fileCount = 0
            DEMO_FILES.forEach { file ->
                source.createFileInDirectory(
                    parentPath = buildRemotePath(root.path, file.directory),
                    name = file.name,
                    bytes = file.content.trimIndent().encodeToByteArray()
                )
                fileCount++
            }

            MndxDeveloperVaultResult(
                vaultName = vaultName,
                remotePath = root.path,
                fileCount = fileCount
            )
        }
    }

    private suspend fun ensureDirectoryPath(
        source: OneDriveKeePassFileSource,
        rootPath: String,
        relativePath: String
    ) {
        var current = rootPath
        relativePath.split("/")
            .filter { it.isNotBlank() }
            .forEach { segment ->
                val existing = runCatching {
                    source.listDirectory(current).firstOrNull { entry ->
                        entry.isDirectory && entry.name == segment
                    }
                }.getOrNull()
                current = existing?.path ?: source.createDirectory(current, segment).path
            }
    }

    private data class DemoFile(
        val directory: String,
        val name: String,
        val content: String
    )

    companion object {
        private val DEMO_DIRECTORIES = listOf(
            "refs/devices",
            "objects/entries",
            "objects/bindings",
            "objects/groups",
            "objects/totp",
            "objects/passkeys",
            "objects/secure-notes",
            "objects/cards",
            "objects/text",
            "objects/tombstones",
            "oplog/android/object_ops",
            "oplog/android/text_ops",
            "snapshots",
            "conflicts",
            "recovery",
            "tmp"
        )

        private val DEMO_FILES = listOf(
            DemoFile(
                directory = "",
                name = "mndx.json",
                content = """
                    {
                      "format": "mndx",
                      "formatVersion": "1-draft",
                      "fixture": true,
                      "encrypted": false,
                      "vaultId": "01JZMONICAANDROIDDEMO00000",
                      "createdAt": "2026-05-03T10:00:00Z",
                      "criticalExtensions": [],
                      "recommendedExtensions": ["monica-android-reference", "binding-objects", "history-dag"],
                      "privacy": {
                        "hashedObjectNames": false,
                        "encryptedManifest": false
                      },
                      "warning": "Developer fixture only. Do not store real secrets here."
                    }
                """
            ),
            DemoFile(
                directory = "objects/groups",
                name = "group_work.json",
                content = """
                    {
                      "objectId": "group_work",
                      "objectType": "group",
                      "schemaVersion": 1,
                      "parentId": null,
                      "fields": {
                        "group.name": {
                          "value": "Work",
                          "mergeClass": "scalar",
                          "clock": {"android": 1}
                        }
                      },
                      "objectClock": {"android": 1},
                      "createdAt": "2026-05-03T10:00:00Z",
                      "createdBy": "android",
                      "deleted": false
                    }
                """
            ),
            DemoFile(
                directory = "objects/entries",
                name = "entry_github.json",
                content = """
                    {
                      "objectId": "entry_github",
                      "objectType": "entry",
                      "schemaVersion": 1,
                      "parentId": "group_work",
                      "fields": {
                        "login.title": {"value": "GitHub", "mergeClass": "scalar", "clock": {"android": 2}},
                        "login.username": {"value": "alice@example.com", "mergeClass": "scalar", "clock": {"android": 2}},
                        "login.password": {"value": {"kind": "secret-ref", "secretId": "secret_github_password"}, "mergeClass": "scalar", "clock": {"android": 2}},
                        "login.url": {"value": "https://github.com", "mergeClass": "scalar", "clock": {"android": 2}},
                        "login.notes": {"value": {"kind": "crdt-text-ref", "textObjectId": "text_github_notes"}, "mergeClass": "crdt-text", "clock": {"android": 2}}
                      },
                      "objectClock": {"android": 2},
                      "createdAt": "2026-05-03T10:01:00Z",
                      "createdBy": "android",
                      "deleted": false
                    }
                """
            ),
            DemoFile(
                directory = "objects/totp",
                name = "totp_github.json",
                content = """
                    {
                      "objectId": "totp_github",
                      "objectType": "totp",
                      "schemaVersion": 1,
                      "parentId": "group_work",
                      "fields": {
                        "totp.issuer": {"value": "GitHub", "mergeClass": "scalar", "clock": {"android": 3}},
                        "totp.accountName": {"value": "alice@example.com", "mergeClass": "scalar", "clock": {"android": 3}},
                        "totp.secret": {"value": {"kind": "secret-ref", "secretId": "secret_github_totp"}, "mergeClass": "scalar", "clock": {"android": 3}}
                      },
                      "objectClock": {"android": 3},
                      "createdAt": "2026-05-03T10:02:00Z",
                      "createdBy": "android",
                      "deleted": false
                    }
                """
            ),
            DemoFile(
                directory = "objects/passkeys",
                name = "passkey_github.json",
                content = """
                    {
                      "objectId": "passkey_github",
                      "objectType": "passkey",
                      "schemaVersion": 1,
                      "parentId": "group_work",
                      "fields": {
                        "passkey.credentialId": {"value": "MDEyMzQ1Njc4OWFiY2RlZg", "mergeClass": "scalar", "clock": {"android": 4}},
                        "passkey.rpId": {"value": "github.com", "mergeClass": "scalar", "clock": {"android": 4}},
                        "passkey.userName": {"value": "alice@example.com", "mergeClass": "scalar", "clock": {"android": 4}},
                        "passkey.compatibilityMode": {"value": "bitwarden-compatible", "mergeClass": "scalar", "clock": {"android": 4}}
                      },
                      "objectClock": {"android": 4},
                      "createdAt": "2026-05-03T10:03:00Z",
                      "createdBy": "android",
                      "deleted": false
                    }
                """
            ),
            DemoFile(
                directory = "objects/bindings",
                name = "binding_github.json",
                content = """
                    {
                      "objectId": "binding_github",
                      "objectType": "binding",
                      "schemaVersion": 1,
                      "parentId": "group_work",
                      "fields": {
                        "binding.name": {"value": "GitHub", "mergeClass": "scalar", "clock": {"android": 5}},
                        "binding.primaryObjectId": {"value": "entry_github", "mergeClass": "scalar", "clock": {"android": 5}},
                        "binding.links": {
                          "value": [
                            {"role": "login", "objectId": "entry_github"},
                            {"role": "totp", "objectId": "totp_github"},
                            {"role": "passkey", "objectId": "passkey_github"}
                          ],
                          "mergeClass": "set",
                          "clock": {"android": 5}
                        }
                      },
                      "objectClock": {"android": 5},
                      "createdAt": "2026-05-03T10:04:00Z",
                      "createdBy": "android",
                      "deleted": false
                    }
                """
            ),
            DemoFile(
                directory = "objects/secure-notes",
                name = "note_github_recovery.json",
                content = """
                    {
                      "objectId": "note_github_recovery",
                      "objectType": "secure-note",
                      "schemaVersion": 1,
                      "parentId": "group_work",
                      "fields": {
                        "note.title": {"value": "GitHub recovery notes", "mergeClass": "scalar", "clock": {"android": 6}},
                        "note.body": {"value": {"kind": "crdt-text-ref", "textObjectId": "text_github_recovery"}, "mergeClass": "crdt-text", "clock": {"android": 6}}
                      },
                      "objectClock": {"android": 6},
                      "createdAt": "2026-05-03T10:05:00Z",
                      "createdBy": "android",
                      "deleted": false
                    }
                """
            ),
            DemoFile(
                directory = "objects/cards",
                name = "card_work.json",
                content = """
                    {
                      "objectId": "card_work",
                      "objectType": "card",
                      "schemaVersion": 1,
                      "parentId": "group_work",
                      "fields": {
                        "card.title": {"value": "Work card", "mergeClass": "scalar", "clock": {"android": 7}},
                        "card.number": {"value": {"kind": "secret-ref", "secretId": "secret_work_card_number"}, "mergeClass": "scalar", "clock": {"android": 7}}
                      },
                      "objectClock": {"android": 7},
                      "createdAt": "2026-05-03T10:06:00Z",
                      "createdBy": "android",
                      "deleted": false
                    }
                """
            ),
            DemoFile(
                directory = "objects/text",
                name = "text_github_notes.json",
                content = """
                    {
                      "objectId": "text_github_notes",
                      "objectType": "crdt-text",
                      "schemaVersion": 1,
                      "fields": {},
                      "objectClock": {"android": 8},
                      "createdAt": "2026-05-03T10:07:00Z",
                      "createdBy": "android",
                      "deleted": false,
                      "engine": "loro-text",
                      "engineVersion": "1",
                      "ownerObjectId": "entry_github",
                      "ownerField": "login.notes",
                      "stateRef": "text_state_github_notes"
                    }
                """
            ),
            DemoFile(
                directory = "oplog/android/object_ops",
                name = "000000000001.mop.json",
                content = """
                    {
                      "opId": "android:1",
                      "deviceId": "android",
                      "seq": 1,
                      "parents": [],
                      "type": "import_batch",
                      "message": "Create developer MNDX demo vault",
                      "actor": {"deviceId": "android"},
                      "baseClock": {},
                      "payload": {
                        "source": "developer-settings",
                        "createdObjects": ["group_work", "entry_github", "totp_github", "passkey_github", "binding_github"]
                      },
                      "timestamp": "2026-05-03T10:08:00Z",
                      "prevOpHash": "",
                      "recordHash": "sha256:developer-demo-op-1"
                    }
                """
            ),
            DemoFile(
                directory = "refs/devices",
                name = "android.mref.json",
                content = """
                    {
                      "vaultId": "01JZMONICAANDROIDDEMO00000",
                      "deviceId": "android",
                      "deviceName": "Monica Android Developer Demo",
                      "head": "android:1",
                      "knownHeads": {"android": 1},
                      "snapshot": null,
                      "updatedAt": "2026-05-03T10:08:00Z"
                    }
                """
            )
        )

        private fun buildRemotePath(parentPath: String, childPath: String): String {
            val parent = parentPath.trim('/')
            val child = childPath.trim('/')
            return when {
                parent.isBlank() -> child
                child.isBlank() -> parent
                else -> "$parent/$child"
            }
        }
    }
}
