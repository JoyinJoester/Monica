package takagi.ru.monica.viewmodel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiPasswordSaveRegressionGuardTest {

    @Test
    fun saveAcrossTargetsDoesNotDeleteSameTargetMultiPasswordRowsAsDuplicateReplicas() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/PasswordViewModel.kt"
        ).readText()
        val saveAcrossTargetsBody = source.substringAfter("fun savePasswordsAcrossTargets(")
            .substringBefore("private suspend fun canWriteKeePassTargets")

        assertTrue(
            "Existing entries must be grouped by target so all password rows for that target are passed back into saveGroupedPasswordsInternal.",
            saveAcrossTargetsBody.contains(".groupBy { it.toStorageTarget().stableKey }")
        )
        assertFalse(
            "Same-target entries can be valid multi-password rows; do not delete them as duplicate replicas.",
            saveAcrossTargetsBody.contains("duplicateReplicaIds")
        )
        assertFalse(
            "Same-target entries can be valid multi-password rows; cleanup must only remove deselected targets.",
            saveAcrossTargetsBody.contains("sameTargetEntries.filterNot")
        )
    }

    @Test
    fun detailScreenUsesResolvedGroupMembersEvenWhenReplicaGroupIdExists() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/PasswordDetailScreen.kt"
        ).readText()

        assertFalse(
            "Detail screen must not collapse replicaGroupId entries to only the current entry; multi-password rows can share the same replica group.",
            source.contains("if (!entry.replicaGroupId.isNullOrBlank()) {\n            listOf(entry)")
        )
        assertFalse(
            "Detail screen must not collapse replicaGroupId entries to only the current entry; multi-password rows can share the same replica group.",
            source.contains("if (!entry.replicaGroupId.isNullOrBlank()) {\r\n            listOf(entry)")
        )
        assertTrue(
            "Detail screen should use resolved group members for the password card.",
            source.contains("val detailPasswords = resolvedGroupPasswords.ifEmpty { listOf(entry) }")
        )
        assertTrue(
            "Detail screen should use groupPasswords when rendering the password card.",
            source.contains("groupPasswords.ifEmpty { listOf(entry) }")
        )
    }

    @Test
    fun addPasswordScreenLoadsMdbxDatabasesWithoutRouteInjectedViewModel() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/AddEditPasswordScreen.kt"
        ).readText()

        assertTrue(
            "Inline add-password surfaces must still show concrete MDBX vaults when they do not pass MdbxViewModel.",
            source.contains("?: database.localMdbxDatabaseDao().getAllDatabases()")
        )
        assertFalse(
            "Falling back to only the constructor list leaves FAB inline creation unable to choose a concrete MDBX vault.",
            source.contains("?: kotlinx.coroutines.flow.flowOf(mdbxDatabasesFallback)")
        )
    }

    @Test
    fun customDirectoryMdbxVaultsAreRegisteredAsExternalSources() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/MdbxViewModel.kt"
        ).readText()

        assertTrue(
            "Custom-directory vault creation must keep the selected SAF URI as the vault source path.",
            source.contains("val filePath = customDirVault?.externalUri?.toString() ?: localVaultFile.absolutePath")
        )
        assertTrue(
            "Custom-directory vault creation must be registered as external storage, not app-internal storage.",
            source.contains("MdbxStorageLocation.EXTERNAL")
        )
        assertTrue(
            "Custom-directory vault creation must keep a writable local copy for MDBX operations.",
            source.contains("workingCopyPath = localVaultFile.absolutePath")
        )
    }

    @Test
    fun mdbxIncomingNewObjectsAreCopiedBeforeEncryptedFieldsAreDecoded() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxVaultStore.kt"
        ).readText()

        val applyIncomingEntryBody = source.substringAfter("private fun applyIncomingEntry(")
            .substringBefore("private fun applyIncomingAttachment(")
        assertTrue(
            "A password created on another client is a new local entry; it must be copied before decoding incoming encrypted fields.",
            applyIncomingEntryBody.indexOf("if (localState == null)") <
                applyIncomingEntryBody.indexOf("val incomingPayload = normalizeVaultBytes")
        )

        val applyIncomingProjectBody = source.substringAfter("private fun applyIncomingProject(")
            .substringBefore("private fun applyIncomingEntry(")
        assertTrue(
            "A project created on another client is a new local project; it must be copied before decoding incoming encrypted fields.",
            applyIncomingProjectBody.indexOf("if (localState == null)") <
                applyIncomingProjectBody.indexOf("val incomingTitle = normalizeVaultBytes")
        )

        assertTrue(
            "When incoming credential lookup fails but the incoming file is the same vault and active epoch, merge should reuse the local epoch key.",
            source.contains("canReuseLocalEpochKeyForIncoming(local, incoming)")
        )
        assertTrue(
            "Project existence checks must not decode encrypted project titles with a null epoch key.",
            source.contains("SELECT 1 FROM projects WHERE project_id = ? LIMIT 1")
        )
    }

    @Test
    fun mdbxLocalMutationsPublishWorkingCopyToSourceAfterCommit() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxVaultStore.kt"
        ).readText()

        listOf(
            source.substringAfter("suspend fun resolveConflict(")
                .substringBefore("suspend fun applyIncomingVaultFile("),
            source.substringAfter("suspend fun upsertAttachment(")
                .substringBefore("suspend fun deleteAttachment("),
            source.substringAfter("suspend fun deleteAttachment(")
                .substringBefore("private suspend fun upsertEntryMutations("),
            source.substringAfter("private suspend fun <T : Any> mutateEntriesByVault(")
                .substringBefore("suspend fun readStoredEntries(")
        ).forEach { mutationBody ->
            assertTrue(
                "User-visible MDBX mutations should publish the working copy to SAF/WebDAV before reporting success.",
                mutationBody.contains("markWorkingCopyDirtyAndFlush(dbInfo, file)")
            )
        }

        val dirtyAndFlushBody = source.substringAfter("private suspend fun markWorkingCopyDirtyAndFlush(")
            .substringBefore("private fun checkpointWorkingCopyForFlush(")
        assertTrue(
            "MDBX publish helper must first mark pending upload so a failed upload is visible.",
            dirtyAndFlushBody.contains("markWorkingCopyDirty(database)") &&
                dirtyAndFlushBody.contains("flushWorkingCopyToSourceIfNeeded(database, workingCopy)")
        )

        val flushBody = source.substringAfter("private suspend fun flushWorkingCopyToSourceIfNeeded(")
            .substringBefore("private suspend fun markWorkingCopyDirty(")
        assertTrue(
            "MDBX source publishing must checkpoint the working copy before copying or uploading the MDBX file.",
            flushBody.contains("checkpointWorkingCopyForFlush(database, workingCopy)")
        )
        assertTrue(
            "Failed external/WebDAV publishes must leave sync status visible instead of silently dropping the local commit.",
            source.contains("MdbxSyncStatus.PENDING_UPLOAD")
        )
    }

    @Test
    fun mdbxBatchMutationsKeepDirectLocalWriteContract() {
        val repositorySource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxRepository.kt"
        ).readText()
        val storeSource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxVaultStore.kt"
        ).readText()
        val passwordRepositorySource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/PasswordRepository.kt"
        ).readText()
        val passkeyRepositorySource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/PasskeyRepository.kt"
        ).readText()

        assertTrue(
            "MDBX repository methods must report success only after local commit and source publish.",
            repositorySource.contains("commit the local .mdbx working copy") &&
                repositorySource.contains("publish") &&
                repositorySource.contains("next manual sync")
        )
        assertTrue(
            "MDBX repository should expose batch operations so bulk moves avoid opening the same vault repeatedly.",
            repositorySource.contains("suspend fun upsertPasswords(entries: List<PasswordEntry>)") &&
                repositorySource.contains("suspend fun deletePasswords(entries: List<PasswordEntry>)") &&
                repositorySource.contains("suspend fun upsertPasskeys(passkeys: List<PasskeyEntry>)") &&
                repositorySource.contains("suspend fun deletePasskeys(passkeys: List<PasskeyEntry>)")
        )

        val batchedMutationBody = storeSource.substringAfter("private suspend fun <T : Any> mutateEntriesByVault(")
            .substringBefore("private fun mutationDatabaseId(")
        assertTrue(
            "Batched MDBX mutations must commit a local SQLite transaction and publish once per vault before returning.",
            batchedMutationBody.contains("openExistingWritableVault(file).use") &&
                batchedMutationBody.contains("db.beginTransaction()") &&
                batchedMutationBody.contains("vaultMutations.forEach") &&
                batchedMutationBody.contains("db.setTransactionSuccessful()") &&
                batchedMutationBody.contains("markWorkingCopyDirtyAndFlush(dbInfo, file)")
        )

        assertTrue(
            "Password MDBX bulk moves should use the batch repository path.",
            passwordRepositorySource.contains("mdbxRepository?.upsertPasswords(entriesForMdbx)") &&
                passwordRepositorySource.contains("mdbxRepository?.deletePasswords(")
        )
        assertTrue(
            "Passkey MDBX bulk writes should use the batch repository path.",
            passkeyRepositorySource.contains("mdbxRepository?.upsertPasskeys(passkeysForMdbx)") &&
                passkeyRepositorySource.contains("mdbxRepository?.deletePasskeys(")
        )
    }

    @Test
    fun mdbxGitHistoryHasObjectVersionsDiffAndRevert() {
        val storeSource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxVaultStore.kt"
        ).readText()
        val viewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/MdbxViewModel.kt"
        ).readText()
        val managerSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MdbxManagerScreen.kt"
        ).readText()

        assertTrue(
            "MDBX history must store per-object versions so Diff/Revert are real, not just commit metadata.",
            storeSource.contains("CREATE TABLE IF NOT EXISTS object_versions")
        )
        assertTrue(
            "Entry writes must record object version snapshots in the same transaction as the commit.",
            storeSource.substringAfter("private fun writeEntryMutation(")
                .substringBefore("private fun writeEntryDeleteMutation(")
                .contains("recordEntryVersion(")
        )
        assertTrue(
            "Entry deletes must also record a deleted version snapshot so deletes can be reverted.",
            storeSource.substringAfter("private fun writeEntryDeleteMutation(")
                .substringBefore("suspend fun readStoredEntries(")
                .contains("recordEntryVersion(")
        )
        assertTrue(
            "Incoming sync must copy object version history between clients.",
            storeSource.substringAfter("private fun copyIncomingHistory(")
                .substringBefore("private fun copyIncomingFolders(")
                .contains("FROM object_versions")
        )
        assertTrue(
            "Store must expose commit diff.",
            storeSource.contains("suspend fun listCommitDiff(")
        )
        assertTrue(
            "Store must expose revert as a new commit operation.",
            storeSource.contains("suspend fun revertCommit(") &&
                storeSource.contains("commitKind = \"revert\"")
        )
        assertTrue(
            "ViewModel must expose commit diff to the history UI.",
            viewModelSource.contains("fun showCommitDiff(")
        )
        assertTrue(
            "ViewModel must expose revert and re-import MDBX entries after reverting.",
            viewModelSource.contains("fun revertCommit(") &&
                viewModelSource.substringAfter("fun revertCommit(")
                    .substringBefore("fun resolveConflict(")
                    .contains("importEntriesFromVault(databaseId)")
        )
        assertTrue(
            "History UI must expose Diff and Revert controls.",
            managerSource.contains("onShowDiff") &&
                managerSource.contains("onRevert") &&
                managerSource.contains("Text(\"Diff\")") &&
                managerSource.contains("Text(\"Revert\")")
        )
    }

    @Test
    fun mdbxEntryConflictResolutionWritesBackThroughHistory() {
        val storeSource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxVaultStore.kt"
        ).readText()

        val resolveBody = storeSource.substringAfter("suspend fun resolveConflict(")
            .substringBefore("suspend fun applyIncomingVaultFile(")
        assertTrue(
            "Entry conflict buttons must use the dedicated writeback path, not just update conflict metadata.",
            resolveBody.contains("resolveEntryConflict(")
        )

        val entryResolveBody = storeSource.substringAfter("private fun resolveEntryConflict(")
            .substringBefore("private fun readEntryVersionForCommit(")
        assertTrue(
            "Local-wins entry conflict resolution must advance the entry head through a merge commit.",
            entryResolveBody.contains("MdbxConflictResolution.LOCAL_WINS") &&
                entryResolveBody.contains("commitKind = \"merge\"") &&
                entryResolveBody.contains("updateEntryHeadForResolvedConflict(")
        )
        assertTrue(
            "Incoming-wins entry conflict resolution must apply the incoming object_versions snapshot.",
            entryResolveBody.contains("MdbxConflictResolution.INCOMING_WINS") &&
                entryResolveBody.contains("readEntryVersionForCommit(") &&
                entryResolveBody.contains("applyResolvedEntryVersion(")
        )
        assertTrue(
            "Custom and mark-resolved must not silently discard unresolved entry conflicts.",
            entryResolveBody.contains("Custom MDBX conflict merge needs a merged payload") &&
                entryResolveBody.contains("Entry conflicts must choose local or incoming")
        )
        assertTrue(
            "Android conflict resolution strings must match the Rust MDBX schema values.",
            storeSource.contains("LOCAL_WINS(\"local-wins\")") &&
                storeSource.contains("INCOMING_WINS(\"incoming-wins\")") &&
                storeSource.contains("CUSTOM_MERGE(\"custom\")")
        )
    }

    @Test
    fun mdbxUnlockAndWalAvoidKnownWriteSlowdowns() {
        val storeSource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxVaultStore.kt"
        ).readText()
        val cryptoSource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxVaultCrypto.kt"
        ).readText()

        assertTrue(
            "MDBX should request WAL in SQLite open flags before Android can mutate journal mode on an already-open vault.",
            storeSource.contains("SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING") &&
                storeSource.contains("SQLiteDatabase.openDatabase(file.absolutePath, null, flags)")
        )
        assertFalse(
            "MDBX writable open must not use openOrCreateDatabase because Android may try to switch an existing WAL vault back to TRUNCATE before our PRAGMA runs.",
            storeSource.contains("openOrCreateDatabase(file")
        )
        assertTrue(
            "MDBX WAL should use synchronous=NORMAL to avoid full fsync cost on every write.",
            storeSource.contains("PRAGMA synchronous=NORMAL")
        )
        assertTrue(
            "MDBX user-visible writes and publish/checkpoint work should be serialized per vault file.",
            storeSource.contains("private val vaultWriteLocks") &&
                storeSource.contains("withVaultWriteLock(file)") &&
                storeSource.contains("markWorkingCopyDirtyAndFlushLocked(dbInfo, file)")
        )
        assertTrue(
            "Unlock should derive the credential key once, verify it, and unwrap the epoch key in one pass.",
            cryptoSource.contains("fun unlockEpochKey(")
        )
        val readEpochKeyBody = storeSource.substringAfter("private fun readEpochKeyOrNull(")
            .substringBefore("private fun readStoredCredential(")
        assertTrue(
            "Store should use the one-pass unlock path instead of verifyCredential + unwrapEpochKey.",
            readEpochKeyBody.contains("MdbxVaultCrypto.unlockEpochKey(")
        )
        assertFalse(
            "Store readEpochKeyOrNull must not run the KDF twice by separately calling verifyCredential.",
            readEpochKeyBody.contains("MdbxVaultCrypto.verifyCredential(")
        )
        assertFalse(
            "Store readEpochKeyOrNull must not run the KDF twice by separately calling unwrapEpochKey.",
            readEpochKeyBody.contains("MdbxVaultCrypto.unwrapEpochKey(")
        )
    }

    @Test
    fun mdbxArchitectureCompletionExposesOplogBundlesExternalRefsAndBenchmarks() {
        val storeSource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxVaultStore.kt"
        ).readText()
        val viewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/MdbxViewModel.kt"
        ).readText()

        listOf(
            "CREATE TABLE IF NOT EXISTS oplog",
            "CREATE TABLE IF NOT EXISTS sync_bundles",
            "CREATE TABLE IF NOT EXISTS crypto_contexts",
            "CREATE TABLE IF NOT EXISTS mdbx_benchmarks"
        ).forEach { schema ->
            assertTrue("MDBX schema must include $schema.", storeSource.contains(schema))
        }

        val appendCommitBody = storeSource.substringAfter("private fun appendCommit(")
            .substringBefore("private fun insertOplog(")
        assertTrue(
            "Every MDBX commit must also append an oplog row.",
            appendCommitBody.contains("insertOplog(")
        )

        assertTrue(
            "MDBX store must export serialized sync bundles.",
            storeSource.contains("suspend fun exportSyncBundle(") &&
                storeSource.contains("\"monica-mdbx-sync-bundle-v1\"") &&
                storeSource.contains("payloadHash = sha256Hex")
        )
        assertTrue(
            "MDBX store must import sync bundles and apply entry object versions.",
            storeSource.contains("suspend fun importSyncBundle(") &&
                storeSource.contains("latestBundleEntryVersions(payload)") &&
                storeSource.contains("applyBundleEntryVersion(")
        )
        assertTrue(
            "MDBX sync bundle import must preserve local divergent edits as explicit conflicts.",
            storeSource.contains("private fun applyBundleEntryVersion(") &&
                storeSource.contains("isAncestor(db, localState.headCommitId, version.commitId)") &&
                storeSource.contains("isAncestor(db, version.commitId, localState.headCommitId)") &&
                storeSource.contains("insertIncomingConflict(") &&
                storeSource.contains("conflictCount = conflicts")
        )
        assertTrue(
            "External attachment refs must be first-class and content-hash bound.",
            storeSource.contains("suspend fun upsertExternalAttachmentRef(") &&
                storeSource.contains("'external-hash-ref'") &&
                storeSource.contains("external_uri_ct") &&
                storeSource.contains("External MDBX attachment requires a content hash")
        )
        assertTrue(
            "Crypto context metadata must record field AAD and key purpose.",
            storeSource.contains("private fun recordCryptoContext(") &&
                storeSource.contains("mdbx:v1:\$objectType:\$objectId:\$fieldName") &&
                storeSource.contains("key_purpose")
        )
        assertTrue(
            "MDBX benchmark harness must record latency and file delta.",
            storeSource.contains("suspend fun runBenchmark(") &&
                storeSource.contains("fileDeltaBytes") &&
                storeSource.contains("INSERT OR REPLACE INTO mdbx_benchmarks")
        )
        assertTrue(
            "Dirty external/WebDAV vault uploads need a batchable background entrypoint.",
            storeSource.contains("suspend fun flushPendingWorkingCopy(") &&
                viewModelSource.contains("fun flushPendingVaultUploads(") &&
                viewModelSource.contains("MdbxSyncStatus.PENDING_UPLOAD.name") &&
                viewModelSource.contains("vaultStore.flushPendingWorkingCopy(database.id)")
            )
    }

    @Test
    fun mdbxSnapshotsExposeSingleFileBackupHistoryAndRollback() {
        val storeSource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxVaultStore.kt"
        ).readText()
        val viewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/MdbxViewModel.kt"
        ).readText()
        val managerSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MdbxManagerScreen.kt"
        ).readText()

        assertTrue(
            "MDBX snapshots need a summary model for the UI and diagnostics.",
            storeSource.contains("data class MdbxSnapshotSummary")
        )
        listOf(
            "ensureColumn(db, \"snapshots\", \"name\"",
            "ensureColumn(db, \"snapshots\", \"snapshot_type\"",
            "ensureColumn(db, \"snapshots\", \"is_full\"",
            "ensureColumn(db, \"snapshots\", \"auto_prune\"",
            "ensureColumn(db, \"snapshots\", \"payload_bytes\""
        ).forEach { schemaGuard ->
            assertTrue("MDBX snapshot schema must include $schemaGuard.", storeSource.contains(schemaGuard))
        }
        listOf(
            "suspend fun listSnapshots(",
            "suspend fun createSnapshot(",
            "suspend fun deleteSnapshot(",
            "suspend fun revertToSnapshot(",
            "suspend fun pruneAutomaticSnapshots("
        ).forEach { api ->
            assertTrue("MDBX store must expose snapshot API $api.", storeSource.contains(api))
        }

        val appendCommitBody = storeSource.substringAfter("private fun appendCommit(")
            .substringBefore("private fun insertOplog(")
        assertTrue(
            "Every local commit should create a cheap automatic delta snapshot anchor.",
            appendCommitBody.contains("createSnapshotLocked(") &&
                appendCommitBody.contains("fullSnapshot = false") &&
                appendCommitBody.contains("autoPrune = true")
        )
        assertTrue(
            "Automatic snapshots must be pruned by retention policy.",
            storeSource.contains("private fun pruneAutomaticSnapshotsLocked(") &&
                storeSource.contains("private fun automaticSnapshotRetention(")
        )
        assertTrue(
            "Delta snapshot rollback should reconstruct entry state from object_versions at the base commit.",
            storeSource.contains("private fun revertToCommitState(") &&
                storeSource.contains("readLatestEntryVersionsAtCommit(db, baseCommitId)")
        )

        listOf(
            "fun createSnapshot(",
            "fun deleteSnapshot(",
            "fun revertToSnapshot(",
            "fun pruneAutomaticSnapshots("
        ).forEach { api ->
            assertTrue("ViewModel must expose snapshot action $api.", viewModelSource.contains(api))
        }
        assertTrue(
            "Delta dialog state must carry snapshot rows and loading state.",
            viewModelSource.contains("val snapshots: List<MdbxSnapshotSummary>") &&
                viewModelSource.contains("val isSnapshotLoading: Boolean")
        )
        assertTrue(
            "Snapshot rollback must re-import MDBX entries into the local UI tables.",
            viewModelSource.substringAfter("fun revertToSnapshot(")
                .substringBefore("fun pruneAutomaticSnapshots(")
                .contains("importEntriesFromVault(databaseId)")
        )

        assertTrue(
            "MDBX manager must expose a snapshot management panel.",
            managerSource.contains("SnapshotManagerPanel(") &&
                managerSource.contains("SnapshotRow(") &&
                managerSource.contains("onCreateSnapshot") &&
                managerSource.contains("onRevertSnapshot") &&
                managerSource.contains("onPruneAutomaticSnapshots")
        )
        assertTrue(
            "Snapshot UI must let the user choose delta versus full snapshot.",
            managerSource.contains("fullSnapshot") &&
                managerSource.contains("Delta 快照") &&
                managerSource.contains("完整快照")
        )
    }

    @Test
    fun mdbxManagerLivesUnderDatabaseBackupAndUsesStandalonePages() {
        val managerSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MdbxManagerScreen.kt"
        ).readText()
        val syncBackupSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/SyncBackupScreen.kt"
        ).readText()
        val mainActivitySource = projectFile(
            "app/src/main/java/takagi/ru/monica/MainActivity.kt"
        ).readText()

        assertTrue(
            "Database and backup settings must expose MDBX as its own testing category.",
            syncBackupSource.contains("onNavigateToMdbx") &&
                syncBackupSource.contains("MDBX（测试）") &&
                syncBackupSource.contains("MDBX 格式管理") &&
                mainActivitySource.contains("onNavigateToMdbx = {") &&
                mainActivitySource.contains("navController.navigate(Screen.MdbxManager.route)")
        )
        assertTrue(
            "MDBX manager should open to a hub and then branch into local, WebDAV, and OneDrive management pages.",
            managerSource.contains("MdbxManagerHubPage(") &&
                managerSource.contains("本地 MDBX 管理") &&
                managerSource.contains("WebDAV MDBX 管理") &&
                managerSource.contains("OneDrive MDBX 管理") &&
                managerSource.contains("MdbxManagerSource.LOCAL") &&
                managerSource.contains("MdbxManagerSource.WEBDAV") &&
                managerSource.contains("MdbxManagerSource.ONEDRIVE")
        )
        assertTrue(
            "MDBX source pages should stay list-first like KeePass management and open databases as standalone detail pages.",
            managerSource.contains("MdbxSourceManagementPage(") &&
                managerSource.contains("MdbxVaultSmallCard(") &&
                managerSource.contains("MdbxVaultDetailPage(") &&
                managerSource.contains("page = MdbxManagerPage.Detail")
        )
        assertTrue(
            "MDBX conflict, history/snapshot, and advanced tools must be standalone manager subpages instead of transient sheets.",
            managerSource.contains("MdbxConflictPage(") &&
                managerSource.contains("MdbxDeltaPage(") &&
                managerSource.contains("MdbxAdvancedToolsPage(") &&
                managerSource.contains("MdbxMaintenancePage(") &&
                managerSource.contains("BackHandler(") &&
                managerSource.contains("MdbxManagerPage.Conflict") &&
                managerSource.contains("MdbxManagerPage.History") &&
                managerSource.contains("MdbxManagerPage.Advanced") &&
                managerSource.contains("MdbxManagerPage.Maintenance")
        )
        assertTrue(
            "MDBX detail page must expose a diagnostics and maintenance page for format upgrade troubleshooting.",
            managerSource.contains("诊断 / 维护") &&
                managerSource.contains("onShowMaintenance") &&
                managerSource.contains("onRefreshDiagnostics") &&
                managerSource.contains("onFlushPendingUpload") &&
                managerSource.contains("schema、commit 图、设备 head、快照、附件 chunk 和同步状态")
        )
    }

    @Test
    fun mdbxAdvancedControlsAreReachableFromAndroidManager() {
        val storeSource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxVaultStore.kt"
        ).readText()
        val viewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/MdbxViewModel.kt"
        ).readText()
        val managerSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MdbxManagerScreen.kt"
        ).readText()

        listOf(
            "suspend fun exportSyncBundle(",
            "suspend fun importSyncBundle(",
            "suspend fun flushPendingWorkingCopy(",
            "suspend fun runBenchmark(",
            "CREATE TABLE IF NOT EXISTS attachment_chunks",
            "suspend fun upsertExternalAttachmentRef("
        ).forEach { api ->
            assertTrue("MDBX store must keep advanced capability $api.", storeSource.contains(api))
        }

        listOf(
            "val advancedDialogState",
            "fun showAdvancedTools(",
            "fun dismissAdvancedTools(",
            "fun exportSyncBundle(",
            "fun importSyncBundleFromJson(",
            "fun flushPendingVaultUpload(",
            "fun runBenchmark(",
            "syncBundleToExportJson(",
            "parseSyncBundleExportJson("
        ).forEach { api ->
            assertTrue("MdbxViewModel must expose Android advanced control $api.", viewModelSource.contains(api))
        }
        assertTrue(
            "Bundle import should refresh local UI tables and diagnostics after applying oplog payloads.",
            viewModelSource.substringAfter("fun importSyncBundleFromJson(")
                .substringBefore("fun flushPendingVaultUpload(")
                .contains("importEntriesFromVault(databaseId)") &&
                viewModelSource.substringAfter("fun importSyncBundleFromJson(")
                    .substringBefore("fun flushPendingVaultUpload(")
                    .contains("getVaultDiagnostics(databaseId)")
        )

        assertTrue(
            "MDBX detail page must expose the advanced tools entry.",
            managerSource.contains("onShowAdvanced") &&
                managerSource.contains("高级工具")
        )
        assertTrue(
            "Android manager must provide controls for bundle export/import, upload flush, chunk status, and benchmark.",
            managerSource.contains("MdbxAdvancedToolsPage(") &&
                managerSource.contains("onExportBundle") &&
                managerSource.contains("onImportBundle") &&
                managerSource.contains("onFlushPendingUpload") &&
                managerSource.contains("onRunBenchmark") &&
                managerSource.contains("Chunk 校验") &&
                managerSource.contains("external-hash-ref") &&
                managerSource.contains("benchmark")
        )
        assertTrue(
            "Android manager must expose later MDBX diagnostics in a standalone maintenance page.",
            managerSource.contains("MdbxMaintenancePage(") &&
                managerSource.contains("字段级 AAD") &&
                managerSource.contains("crypto_contexts") &&
                managerSource.contains("dangling parents") &&
                managerSource.contains("dangling branch heads") &&
                managerSource.contains("dangling device heads") &&
                managerSource.contains("Chunk mismatch") &&
                managerSource.contains("external-hash-ref") &&
                managerSource.contains("上传待处理写入")
        )
        assertTrue(
            "Exported sync bundles should be copyable from the Android UI.",
            managerSource.contains("ClipboardUtils.copyToClipboard") &&
                managerSource.contains("MDBX sync bundle")
        )
    }

    @Test
    fun mdbxDatabaseViewsExposePathNavigationAndSyncAction() {
        val vaultV2Source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/vaultv2/VaultV2Pane.kt"
        ).readText()
        val passwordListContentSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/password/PasswordListContent.kt"
        ).readText()
        val quickFolderSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/password/PasswordQuickFolderSupport.kt"
        ).readText()
        val quickFolderSectionsSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/password/PasswordQuickFolderSections.kt"
        ).readText()
        val passwordListMainPaneSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/password/PasswordListMainPane.kt"
        ).readText()
        val topActionsSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/password/PasswordTopActionsMenu.kt"
        ).readText()
        val passwordListTopSectionSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/password/PasswordListTopSection.kt"
        ).readText()
        val mdbxStoreSource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxVaultStore.kt"
        ).readText()
        val mdbxViewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/MdbxViewModel.kt"
        ).readText()
        val simpleMainSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/SimpleMainScreen.kt"
        ).readText()
        val compactTabsSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/CompactDraggableTabContent.kt"
        ).readText()

        assertTrue(
            "VaultV2 must receive MdbxViewModel so the password-list menu can run the same sync path as the MDBX manager.",
            vaultV2Source.contains("mdbxViewModel: MdbxViewModel? = null")
        )
        assertTrue(
            "VaultV2 must resolve the selected MDBX database from the storage filter.",
            vaultV2Source.contains("val selectedMdbxDatabaseId = remember(storageSelection)") &&
                vaultV2Source.contains("is UnifiedCategoryFilterSelection.MdbxDatabaseFilter -> storageSelection.databaseId")
        )
        assertTrue(
            "VaultV2 top-right menu must expose MDBX sync and call syncVault for the selected vault.",
            vaultV2Source.contains("MdbxSyncTopActionsMenuItem(") &&
                vaultV2Source.contains("mdbxViewModel.syncVault(selectedMdbxDatabaseId)")
        )
        assertTrue(
            "MDBX selected database views should auto-sync on entry so another client's published writes are pulled without visiting settings.",
            mdbxViewModelSource.contains("fun autoSyncVisibleVault(") &&
                mdbxViewModelSource.contains("database.lastSyncStatus != MdbxSyncStatus.PENDING_UPLOAD.name") &&
                passwordListContentSource.contains("autoSyncVisibleVault(selectedId)") &&
                vaultV2Source.contains("autoSyncVisibleVault(databaseId)")
        )
        assertTrue(
            "MDBX database pages must participate in the same path breadcrumb builder as KeePass and Bitwarden pages.",
            quickFolderSource.contains("is CategoryFilter.MdbxDatabase -> true") &&
                quickFolderSource.contains("root_mdbx_") &&
                quickFolderSource.contains("mdbxDatabases.find { it.id == filter.databaseId }?.name")
        )
        assertTrue(
            "Password list and VaultV2 must pass MDBX databases into breadcrumb building so the current path shows the vault name.",
            passwordListContentSource.contains("mdbxDatabases = mdbxDatabases") &&
                vaultV2Source.contains("mdbxDatabases = mdbxDatabases")
        )
        assertTrue(
            "The user-facing MDBX menu action should say sync, not refresh.",
            topActionsSource.contains("MdbxSyncTopActionsMenuItem") &&
                topActionsSource.contains("同步 MDBX 数据库") &&
                !topActionsSource.contains("\"${'$'}{stringResource(R.string.refresh)} MDBX\"")
        )
        assertTrue(
            "MDBX database pages must show a path-level sync affordance beside the breadcrumb path.",
            quickFolderSectionsSource.contains("data class MdbxPathSyncState") &&
                quickFolderSectionsSource.contains("fun MdbxPathSyncActions") &&
                quickFolderSectionsSource.contains("mdbxPathPendingSyncCount") &&
                quickFolderSectionsSource.contains("AnimatedVisibility") &&
                quickFolderSectionsSource.contains("expandHorizontally") &&
                quickFolderSectionsSource.contains("shrinkHorizontally") &&
                quickFolderSectionsSource.contains("未同步${'$'}{state.pendingCount}条")
        )
        assertTrue(
            "The MDBX unsynced chip must use store diagnostics instead of a hard-coded status-only count.",
            mdbxStoreSource.contains("val pendingSyncCount: Int") &&
                mdbxStoreSource.contains("calculatePendingSyncCount(") &&
                mdbxStoreSource.contains("queryPendingLocalOperationCount(") &&
                mdbxViewModelSource.contains("val pendingSyncCounts") &&
                passwordListContentSource.contains("pendingSyncCounts")
        )
        assertTrue(
            "The path-level MDBX sync action must stay a circular icon-only button that is always available on MDBX pages.",
            quickFolderSectionsSource.contains("shape = CircleShape") &&
                quickFolderSectionsSource.contains("IconButton(") &&
                quickFolderSectionsSource.contains("imageVector = Icons.Default.Sync") &&
                quickFolderSectionsSource.contains("contentDescription = \"同步 MDBX 数据库\"") &&
                !quickFolderSectionsSource.contains("TextButton")
        )
        assertTrue(
            "The MDBX path sync UI must squeeze the breadcrumb path when the pending status appears.",
            quickFolderSectionsSource.contains(".weight(1f)") &&
                quickFolderSectionsSource.contains(".height(36.dp)") &&
                quickFolderSectionsSource.contains(".width(104.dp)") &&
                quickFolderSectionsSource.contains("animateContentSize") &&
                quickFolderSectionsSource.contains("expandFrom = Alignment.End")
        )
        assertTrue(
            "Password list must pass the MDBX path sync state into its breadcrumb banner.",
            passwordListContentSource.contains("mdbxPathSyncState = mdbxPathSyncState") &&
                passwordListMainPaneSource.contains("mdbxSyncState = mdbxPathSyncState")
        )
        assertTrue(
            "The MDBX path sync button must flush pending uploads first and otherwise run the normal vault sync.",
            passwordListContentSource.contains("flushPendingVaultUpload(database.id)") &&
                passwordListContentSource.contains("syncVault(database.id)") &&
                vaultV2Source.contains("flushPendingVaultUpload(selectedMdbxDatabaseId)") &&
                vaultV2Source.contains("syncVault(selectedMdbxDatabaseId)")
        )
        assertTrue(
            "The top-right MDBX sync menu must share the same pending-upload-first behavior as the path sync button.",
            passwordListTopSectionSource.contains("selectedMdbxDatabase?.mdbxPathShouldFlushPendingUpload() == true") &&
                passwordListTopSectionSource.contains("flushPendingVaultUpload(selectedMdbxDatabaseId)") &&
                vaultV2Source.contains("selectedMdbxDatabase?.mdbxPathShouldFlushPendingUpload() == true") &&
                vaultV2Source.contains("flushPendingVaultUpload(selectedMdbxDatabaseId)")
        )
        assertTrue(
            "All VaultV2 hosts must pass through the shared MdbxViewModel.",
            simpleMainSource.contains("mdbxViewModel = mdbxViewModel") &&
                compactTabsSource.contains("mdbxViewModel = mdbxViewModel")
        )
    }

    @Test
    fun mdbxCreatedFoldersAreLoadedIntoPasswordCategoryMenus() {
        val viewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/PasswordViewModel.kt"
        ).readText()
        val quickFolderSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/password/PasswordQuickFolderSupport.kt"
        ).readText()
        val listContentSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/password/PasswordListContent.kt"
        ).readText()
        val bottomSheetSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/components/UnifiedCategoryFilterBottomSheet.kt"
        ).readText()
        val chipMenuSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/components/UnifiedCategoryFilterChipMenu.kt"
        ).readText()
        val topSectionSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/password/PasswordListTopSection.kt"
        ).readText()

        assertTrue(
            "Password filtering must represent a concrete MDBX folder, not only the MDBX database root.",
            viewModelSource.contains("data class MdbxFolderFilter(val databaseId: Long, val folderId: String)") &&
                bottomSheetSource.contains("data class MdbxFolderFilter(val databaseId: Long, val folderId: String)")
        )
        assertTrue(
            "Creating an MDBX folder must refresh the cached folder list so menus show the new folder immediately.",
            viewModelSource.contains("private val _mdbxFoldersByDatabase") &&
                viewModelSource.contains("fun getMdbxFolders(databaseId: Long)") &&
                viewModelSource.contains("fun refreshMdbxFolders(databaseId: Long)") &&
                viewModelSource.substringAfter("fun createMdbxFolder(")
                    .substringBefore("fun updateCategory(")
                    .contains("refreshMdbxFolders(databaseId)")
        )
        assertTrue(
            "Password list must load MDBX folders for the selected MDBX database or folder filter.",
            listContentSource.contains("is CategoryFilter.MdbxFolderFilter -> filter.databaseId") &&
                listContentSource.contains("selectedMdbxDatabaseId?.let(viewModel::getMdbxFolders)") &&
                listContentSource.contains("viewModel.refreshMdbxFolders(selectedId)")
        )
        assertTrue(
            "Quick-folder builders must receive MDBX folders and navigate to MdbxFolderFilter targets.",
            quickFolderSource.contains("selectedMdbxFolders: List<MdbxStoredFolderEntry>") &&
                quickFolderSource.contains("buildMdbxFolderQuickFolderShortcuts") &&
                quickFolderSource.contains("targetFilter = CategoryFilter.MdbxFolderFilter(databaseId, folder.folderId)") &&
                quickFolderSource.contains("is CategoryFilter.MdbxFolderFilter -> true")
        )
        assertTrue(
            "Both category menu surfaces must read MDBX folders from the shared folder flow.",
            bottomSheetSource.contains("getMdbxFolders: (Long) -> Flow<List<MdbxStoredFolderEntry>>") &&
                bottomSheetSource.contains("val folders by getMdbxFolders(database.id).collectAsState") &&
                chipMenuSource.contains("getMdbxFolders: (Long) -> Flow<List<MdbxStoredFolderEntry>>") &&
                chipMenuSource.contains("selectedMdbxDatabaseId?.let(getMdbxFolders)")
        )
        assertTrue(
            "Top-level password controls must pass MDBX folder loading into the chip menu and keep folder labels visible.",
            topSectionSource.contains("selectedMdbxFolders: List<MdbxStoredFolderEntry>") &&
                topSectionSource.contains("getMdbxFolders = viewModel::getMdbxFolders") &&
                topSectionSource.contains("UnifiedCategoryFilterSelection.MdbxFolderFilter(filter.databaseId, filter.folderId)")
        )
    }

    @Test
    fun mdbxFolderCreationUsesAndroidSafePragmasAndPersistentFailureLogs() {
        val storeSource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxVaultStore.kt"
        ).readText()

        val openBody = storeSource.substringAfter("private fun open(file: File): SQLiteDatabase")
            .substringBefore("private fun checkpoint(db: SQLiteDatabase)")
        assertFalse(
            "Android API 36 rejects query-like PRAGMA statements through execSQL; connection PRAGMA must use rawQuery.",
            openBody.contains("execSQL(\"PRAGMA")
        )
        assertTrue(
            "MDBX open should still apply the SQLite connection PRAGMAs required by the Rust storage contract.",
            openBody.contains("applyConnectionPragma(\"PRAGMA synchronous=NORMAL\")") &&
                openBody.contains("applyConnectionPragma(\"PRAGMA busy_timeout=5000\")") &&
                openBody.contains("applyConnectionPragma(\"PRAGMA secure_delete=ON\")")
        )
        assertTrue(
            "Connection PRAGMA helper must consume the returned cursor with rawQuery instead of execSQL.",
            storeSource.substringAfter("private fun SQLiteDatabase.applyConnectionPragma(sql: String)")
                .substringBefore("private fun checkpoint(db: SQLiteDatabase)")
                .contains("rawQuery(sql, emptyArray()).use")
        )

        val createFolderBody = storeSource.substringAfter("override suspend fun createFolder(")
            .substringBefore("override suspend fun listFolders(")
        assertTrue(
            "MDBX folder creation failures must be persisted to the MDBX diagnostic log so exported logs are actionable.",
            createFolderBody.contains("MdbxDiagLogger.append(") &&
                createFolderBody.contains("\"createFolder failed databaseId=") &&
                createFolderBody.contains("error=\${error.javaClass.simpleName}") &&
                createFolderBody.contains("throw error")
        )
    }

    private fun projectFile(relativePath: String): File {
        val candidates = mutableListOf<File>()
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            candidates += File(dir, relativePath)
            dir = dir.parentFile
        }

        return candidates.firstOrNull { it.isFile }
            ?: error("Unable to find project file: $relativePath from ${System.getProperty("user.dir")}")
    }
}
