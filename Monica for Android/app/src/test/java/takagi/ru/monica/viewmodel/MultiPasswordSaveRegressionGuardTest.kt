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
    fun mdbxLocalMutationsDoNotFlushWholeVaultToRemoteSynchronously() {
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
                "Local MDBX mutations should mark the working copy dirty instead of waiting for a full source upload.",
                mutationBody.contains("markWorkingCopyDirty(dbInfo)")
            )
            assertFalse(
                "Local MDBX mutations must not synchronously flush the whole vault to SAF/WebDAV.",
                mutationBody.contains("flushWorkingCopyToSourceIfNeeded")
            )
            assertFalse(
                "Local MDBX mutations must not force a full checkpoint before returning to the UI.",
                mutationBody.contains("checkpointForFlush(db, dbInfo)")
            )
        }

        val flushBody = source.substringAfter("private suspend fun flushWorkingCopyToSourceIfNeeded(")
            .substringBefore("private suspend fun markWorkingCopyDirty(")
        assertTrue(
            "Explicit sync must checkpoint the working copy before copying or uploading the MDBX file.",
            flushBody.contains("checkpointWorkingCopyForFlush(database, workingCopy)")
        )
        assertTrue(
            "Dirty external/WebDAV MDBX vaults must be visible as pending upload.",
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
            "MDBX repository methods must report success only after local working-copy commit.",
            repositorySource.contains("must only return after the local .mdbx working copy has") &&
                repositorySource.contains("External SAF/WebDAV propagation is a later sync")
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
            "Batched MDBX mutations must still commit a local SQLite transaction before returning.",
            batchedMutationBody.contains("openExistingWritableVault(file).use") &&
                batchedMutationBody.contains("db.beginTransaction()") &&
                batchedMutationBody.contains("vaultMutations.forEach") &&
                batchedMutationBody.contains("db.setTransactionSuccessful()") &&
                batchedMutationBody.contains("markWorkingCopyDirty(dbInfo)")
        )
        assertFalse(
            "Batched local MDBX mutations must not synchronously upload the whole vault.",
            batchedMutationBody.contains("flushWorkingCopyToSourceIfNeeded")
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
            "MDBX should keep WAL enabled for small incremental writes.",
            storeSource.contains("PRAGMA journal_mode=WAL")
        )
        assertTrue(
            "MDBX WAL should use synchronous=NORMAL to avoid full fsync cost on every write.",
            storeSource.contains("PRAGMA synchronous=NORMAL")
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
    fun mdbxManagerUsesKeepassStyleListAndDetailSheet() {
        val managerSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MdbxManagerScreen.kt"
        ).readText()

        assertTrue(
            "MDBX manager should stay list-first like KeePass management.",
            managerSource.contains("LazyColumn(") &&
                managerSource.contains("MdbxSectionHeader(") &&
                managerSource.contains("MdbxVaultSmallCard(")
        )
        assertTrue(
            "MDBX vault actions should live in a detail bottom sheet, not a full page mode.",
            managerSource.contains("private fun MdbxVaultDetailBottomSheet(") &&
                managerSource.contains("ModalBottomSheet(") &&
                managerSource.contains("历史 / 快照") &&
                managerSource.contains("冲突管理")
        )
        assertTrue(
            "MDBX manager should group databases by local internal, local external, and WebDAV source.",
            managerSource.contains("val internalDatabases = remember(databases)") &&
                managerSource.contains("val externalDatabases = remember(databases)") &&
                managerSource.contains("val remoteDatabases = remember(databases)")
        )
        assertFalse(
            "MDBX manager should not switch the whole screen into a selected-vault detail page.",
            managerSource.contains("selectedVaultId")
        )
        assertFalse(
            "MDBX manager should not need BackHandler for an in-page selected-vault detail mode.",
            managerSource.contains("BackHandler(")
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
            "MDBX detail sheet must expose the advanced tools entry.",
            managerSource.contains("onShowAdvanced") &&
                managerSource.contains("高级工具")
        )
        assertTrue(
            "Android manager must provide controls for bundle export/import, upload flush, chunk status, and benchmark.",
            managerSource.contains("MdbxAdvancedToolsDialog(") &&
                managerSource.contains("onExportBundle") &&
                managerSource.contains("onImportBundle") &&
                managerSource.contains("onFlushPendingUpload") &&
                managerSource.contains("onRunBenchmark") &&
                managerSource.contains("Chunk 校验") &&
                managerSource.contains("external-hash-ref") &&
                managerSource.contains("benchmark")
        )
        assertTrue(
            "Exported sync bundles should be copyable from the Android UI.",
            managerSource.contains("ClipboardUtils.copyToClipboard") &&
                managerSource.contains("MDBX sync bundle")
        )
    }

    @Test
    fun vaultV2TopActionsCanRefreshSelectedMdbxVault() {
        val vaultV2Source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/vaultv2/VaultV2Pane.kt"
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
            "VaultV2 top-right menu must expose MDBX refresh and call syncVault for the selected vault.",
            vaultV2Source.contains("MdbxRefreshTopActionsMenuItem(") &&
                vaultV2Source.contains("mdbxViewModel.syncVault(selectedMdbxDatabaseId)")
        )
        assertTrue(
            "All VaultV2 hosts must pass through the shared MdbxViewModel.",
            simpleMainSource.contains("mdbxViewModel = mdbxViewModel") &&
                compactTabsSource.contains("mdbxViewModel = mdbxViewModel")
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
