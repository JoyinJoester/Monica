package takagi.ru.monica.repository

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MdbxAndroidIntegrationGuardTest {

    @Test
    fun vaultStoreKeepsMdbxOneOfficialMetadataAndLegacyReadableFormats() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxVaultStore.kt"
        ).readText()

        assertTrue(
            "Android-created vaults must advertise the official MDBX 1.0 release without changing the low-level schema token expected by older readers.",
            source.contains("private const val MDBX_SCHEMA_FORMAT_VERSION = \"MDBX-1\"") &&
                source.contains("private const val MDBX_OFFICIAL_RELEASE_LABEL = \"MDBX-1.0\"") &&
                source.contains("release_label, capability_flags") &&
                source.contains("MDBX_ANDROID_CAPABILITY_FLAGS")
        )
        assertTrue(
            "Old test-version vaults must remain readable so MDBX 1.0 is compatible 4ever.",
            source.contains("private const val MDBX_LEGACY_DRAFT_FORMAT_VERSION = \"MDBX-1-DRAFT\"") &&
                source.contains("formatVersion == MDBX_SCHEMA_FORMAT_VERSION ||") &&
                source.contains("formatVersion == MDBX_LEGACY_DRAFT_FORMAT_VERSION") &&
                source.contains("missingMinimumReadableTables")
        )
        assertTrue(
            "Preparing an old vault for MDBX 1.0 must be additive and must not force a new user prompt.",
            source.contains("suspend fun prepareVaultForOfficialMdbx1(") &&
                source.contains("openExistingWritableVault(file, allowSchemaPreparation = true)") &&
                source.contains("private fun queryVaultMetaBlob(") &&
                source.contains("installOfficialCredentialMaterial(db, credential, tigaMode, now)") &&
                source.contains("WHEN release_label IS NULL OR release_label = ''")
        )
    }

    @Test
    fun instrumentedCompatibilityTestLocksRealSqliteLegacyPreparation() {
        val source = projectFile(
            "app/src/androidTest/java/takagi/ru/monica/repository/MdbxVaultStoreInstrumentedCompatibilityTest.kt"
        ).readText()

        assertTrue(
            "Device/emulator tests must cover a real SQLite legacy draft vault with only the minimum readable tables.",
            source.contains("fun legacyDraftVaultPreparationUpgradesAdditively()") &&
                source.contains("CREATE TABLE vault_meta") &&
                source.contains("'MDBX-1-DRAFT'") &&
                source.contains("store.prepareVaultForOfficialMdbx1(file, credential, MdbxTigaMode.SKY)") &&
                source.contains("SELECT wrapped_epoch_key_ct FROM key_epochs WHERE status = 'active' LIMIT 1")
        )
        assertTrue(
            "Device/emulator tests must cover new Sky vaults staying portable without a key-file requirement.",
            source.contains("fun newSkyVaultStartsAsOfficialMdbxOneWithoutKeyFileRequirement()") &&
                source.contains("tigaMode = MdbxTigaMode.SKY.name") &&
                source.contains("SELECT key_file_name FROM vault_meta LIMIT 1") &&
                source.contains("SELECT key_file_fingerprint FROM vault_meta LIMIT 1")
        )
    }

    @Test
    fun oldVaultOpenPathsPrepareFlushAndImportThroughTheVaultFacade() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/MdbxViewModel.kt"
        ).readText()

        assertTrue(
            "Local, WebDAV, and OneDrive old-vault open paths should all validate credentials, prepare MDBX 1.0 metadata, flush the working copy, and import entries.",
            source.countOccurrences("vaultStore.validateVaultCredentialFile(") >= 3 &&
                source.countOccurrences("vaultStore.prepareVaultForOfficialMdbx1(") >= 3 &&
                source.countOccurrences("vaultStore.flushWorkingCopy(databaseId)") >= 3 &&
                source.countOccurrences("importEntriesFromVault(databaseId)") >= 3
        )
    }

    @Test
    fun tagsSearchAndSyncStayInsideMdbxVaultStoreWithOldBundleCompatibility() {
        val source = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxVaultStore.kt"
        ).readText()
        val facadeSource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxRepository.kt"
        ).readText()

        assertTrue(
            "Project tags and project search must be a repository/facade capability, not scattered through UI code.",
            source.contains("data class MdbxProjectTagSummary(") &&
                source.contains("data class MdbxProjectSearchResult(") &&
                source.contains("CREATE TABLE IF NOT EXISTS project_tags") &&
                source.contains("suspend fun setProjectTags(") &&
                source.contains("suspend fun searchProjects(")
        )
        assertTrue(
            "Sync bundles must carry complete project tag sets when present.",
            source.contains("\"project_tags\"") &&
                source.contains("SELECT p.project_id, pt.tag") &&
                source.contains("private fun importBundleProjectTags(")
        )
        assertTrue(
            "Old sync bundles without project_tags must preserve local tags by doing nothing.",
            source.contains("val tagsArray = payload.optJSONArray(\"project_tags\") ?: return 0")
        )
        assertTrue(
            "The app-facing MDBX facade must declare advanced 1.0 capabilities so callers do not depend on MdbxVaultStore internals.",
            facadeSource.contains("suspend fun setProjectTags(") &&
                facadeSource.contains("suspend fun searchProjects(") &&
                facadeSource.contains("suspend fun exportSyncBundle(") &&
                facadeSource.contains("suspend fun importSyncBundle(") &&
                facadeSource.contains("suspend fun listConflicts(") &&
                facadeSource.contains("suspend fun createSnapshot(") &&
                facadeSource.contains("suspend fun upsertAttachment(")
        )
        assertTrue(
            "MdbxVaultStore must explicitly implement the advanced facade methods so the boundary is compiler-enforced.",
            source.contains("override suspend fun setProjectTags(") &&
                source.contains("override suspend fun searchProjects(") &&
                source.contains("override suspend fun exportSyncBundle(") &&
                source.contains("override suspend fun importSyncBundle(") &&
                source.contains("override suspend fun listConflicts(") &&
                source.contains("override suspend fun createSnapshot(") &&
                source.contains("override suspend fun upsertAttachment(")
        )
    }

    @Test
    fun passkeyBatchMdbxBindingPreservesFolderId() {
        val daoSource = projectFile(
            "app/src/main/java/takagi/ru/monica/data/PasskeyDao.kt"
        ).readText()
        val repositorySource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/PasskeyRepository.kt"
        ).readText()

        assertTrue(
            "The DAO batch MDBX binding must write mdbx_folder_id as well as mdbx_database_id.",
            daoSource.contains("mdbx_folder_id = :folderId") &&
                daoSource.contains("category_id = NULL") &&
                daoSource.contains("updateMdbxDatabaseForPasskeys(recordIds: List<Long>, databaseId: Long?, folderId: String?)")
        )
        assertTrue(
            "The repository batch MDBX binding must mirror the same folder target into the MDBX file and Room row.",
            repositorySource.contains("folderId: String? = null") &&
                repositorySource.contains("categoryId = null") &&
                repositorySource.contains("mdbxFolderId = folderId") &&
                repositorySource.contains("passkeyDao.updateMdbxDatabaseForPasskeys(recordIds, databaseId, folderId.takeIf { databaseId != null })")
        )
    }

    @Test
    fun passkeyCreateAndMoveUseMdbxAwarePersistencePaths() {
        val createSource = projectFile(
            "app/src/main/java/takagi/ru/monica/passkey/PasskeyCreateActivity.kt"
        ).readText()
        val listSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/PasskeyListScreen.kt"
        ).readText()
        val viewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/PasskeyViewModel.kt"
        ).readText()

        assertTrue(
            "Passkey creation must use PasskeyRepository so selected MDBX targets are written to the MDBX vault, not only Room.",
            createSource.contains("insertPasskey = repository::savePasskey") &&
                createSource.contains("rollbackPasskey = ::rollbackPasskeyByCredentialId") &&
                createSource.contains("mdbxDatabaseId = initialMdbxDatabaseId") &&
                createSource.contains("mdbxFolderId = if (initialMdbxDatabaseId != null) initialMdbxFolderId else null")
        )
        assertTrue(
            "Passkey ViewModel must expose the repository MDBX batch binding so UI moves can keep Room and MDBX files aligned.",
            viewModelSource.contains("suspend fun updateMdbxDatabaseForPasskeys(") &&
                viewModelSource.contains("repository.updateMdbxDatabaseForPasskeys(recordIds, databaseId, folderId)")
        )
        assertTrue(
            "Passkey list moves to MDBX must call the MDBX-aware persistence path instead of only mutating an in-memory copy.",
            listSource.contains("suspend fun persistStorageTarget(") &&
                listSource.contains("is UnifiedMoveCategoryTarget.MdbxDatabaseTarget -> target.databaseId to null") &&
                listSource.contains("is UnifiedMoveCategoryTarget.MdbxFolderTarget -> target.databaseId to target.folderId") &&
                listSource.contains("viewModel.updateMdbxDatabaseForPasskeys(")
        )
    }

    @Test
    fun passkeyMdbxFolderFilteringUsesFolderIdAndRoundTripsSavedState() {
        val listSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/PasskeyListScreen.kt"
        ).readText()

        assertTrue(
            "MDBX folder filtering must match both database id and folder id.",
            listSource.contains("val effectiveMdbxFolderId: (PasskeyEntry) -> String?") &&
                listSource.contains("effectiveMdbxId == filter.databaseId && effectiveMdbxFolder == filter.folderId")
        )
        assertTrue(
            "Saved Passkey category filters must preserve MDBX folder selections across recomposition and app restart.",
            listSource.contains("SavedCategoryFilterState(type = \"mdbx_folder\", primaryId = filter.databaseId, text = filter.folderId)") &&
                listSource.contains("\"mdbx_database\" -> state.primaryId") &&
                listSource.contains("\"mdbx_folder\" ->") &&
                listSource.contains("UnifiedCategoryFilterSelection.MdbxFolderFilter(databaseId, folderId)")
        )
    }

    @Test
    fun vaultV2MdbxFilteringKeepsPasskeyOwnershipAndFolderSemantics() {
        val vaultV2Source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/vaultv2/VaultV2Pane.kt"
        ).readText()

        assertTrue(
            "VaultV2 MDBX database filters must include passkeys owned by that MDBX database.",
            vaultV2Source.contains("VaultV2ItemType.PASSKEY -> passkeyEntry?.mdbxDatabaseId") &&
                !vaultV2Source.contains("VaultV2ItemType.PASSKEY -> null")
        )
        assertTrue(
            "VaultV2 MDBX folder filters must use folder/root semantics for passkeys as well as other vault items.",
            vaultV2Source.contains("private fun VaultV2Item.mdbxFolderId(): String?") &&
                vaultV2Source.contains("VaultV2ItemType.PASSKEY -> passkeyEntry?.mdbxFolderId") &&
                vaultV2Source.contains("private fun VaultV2Item.matchesMdbxFolder(databaseId: Long, folderId: String): Boolean") &&
                vaultV2Source.contains("matchesMdbxFolder(selection.databaseId, selection.folderId)")
        )
    }

    @Test
    fun vaultV2MoveSheetPersistsPasskeysThroughRepositoryAwareUpdate() {
        val vaultV2Source = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/vaultv2/VaultV2Pane.kt"
        ).readText()

        assertTrue(
            "VaultV2 move sheet must collect selected passkeys instead of silently ignoring them.",
            vaultV2Source.contains("val passkeyEntries = selectedItems") &&
                vaultV2Source.contains(".filter { it.type == VaultV2ItemType.PASSKEY }") &&
                vaultV2Source.contains(".mapNotNull { it.passkeyEntry }")
        )
        assertTrue(
            "VaultV2 passkey moves must use PasskeyViewModel.updatePasskey so MDBX/KeePass/Bitwarden persistence stays aligned.",
            vaultV2Source.contains("applyPasswordPagePasskeyStorageTarget(") &&
                vaultV2Source.contains("passkeyViewModel.updatePasskey(updateResult.getOrThrow())")
        )
    }

    @Test
    fun movePickerDatabaseChipSelectsMdbxRootTargetDirectly() {
        val moveSheetSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/components/UnifiedMoveToCategoryBottomSheet.kt"
        ).readText()

        assertTrue(
            "Selecting an MDBX database chip must stage the MDBX root target directly; otherwise Passkey creation appears to fall back to local storage.",
            moveSheetSource.contains("fun stageRootTargetForSource(source: MovePickerSource)") &&
                moveSheetSource.contains("is MovePickerSource.MdbxDatabase -> stageTarget(") &&
                moveSheetSource.contains("UnifiedMoveCategoryTarget.MdbxDatabaseTarget(source.database.id)") &&
                moveSheetSource.contains("stageRootTargetForSource(source)")
        )
    }

    @Test
    fun passkeyCategoryChipMenuReceivesMdbxDatabases() {
        val passkeyListSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/PasskeyListScreen.kt"
        ).readText()
        val chipMenuCall = passkeyListSource
            .substringAfter("UnifiedCategoryFilterChipMenu(")
            .substringBefore("trailingContent =")

        assertTrue(
            "Passkey database filter menu must receive MDBX databases and folder loader; otherwise MDBX vaults are hidden from the Passkey page.",
            chipMenuCall.contains("mdbxDatabases = mdbxDatabases") &&
                chipMenuCall.contains("getMdbxFolders = { databaseId ->") &&
                chipMenuCall.contains("passwordViewModel?.getMdbxFolders(databaseId) ?: flowOf(emptyList())")
        )
    }

    @Test
    fun mdbxDatabaseChipsUseOneStorageIconAcrossMenus() {
        val passwordDatabaseFiltersSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/password/PasswordDatabaseFiltersSection.kt"
        ).readText()
        val chipMenuSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/components/UnifiedCategoryFilterChipMenu.kt"
        ).readText()
        val bottomSheetSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/components/UnifiedCategoryFilterBottomSheet.kt"
        ).readText()
        val moveSheetSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/components/UnifiedMoveToCategoryBottomSheet.kt"
        ).readText()
        val storagePickerSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/components/MultiStorageTargetPickerBottomSheet.kt"
        ).readText()

        val passwordMdbxBlock = passwordDatabaseFiltersSource
            .substringAfter("params.mdbxDatabases.forEach")
            .substringBefore("params.bitwardenVaults.forEach")
        val chipMenuMdbxBlock = chipMenuSource
            .substringAfter("mdbxDatabases.forEach")
            .substringBefore("bitwardenVaults.forEach")
        val bottomSheetMdbxBlock = bottomSheetSource
            .substringAfter("if (mdbxDatabases.isNotEmpty())")
            .substringBefore("if (bitwardenVaults.isNotEmpty())")

        assertTrue(
            "MDBX database chips should use the same storage icon everywhere, not KeePass key or old test-feature flask icons.",
            passwordMdbxBlock.contains("leadingIcon = Icons.Default.Storage") &&
                !passwordMdbxBlock.contains("Icons.Default.Science") &&
                chipMenuMdbxBlock.contains("leadingIcon = Icons.Default.Storage") &&
                bottomSheetMdbxBlock.contains("icon = Icons.Default.Storage") &&
                moveSheetSource.contains("override val icon: ImageVector = Icons.Default.Storage") &&
                storagePickerSource.contains("override val icon: ImageVector = Icons.Default.Storage")
        )
    }

    @Test
    fun passwordAndSecureItemMdbxFolderMovesPreserveFolderId() {
        val passwordDaoSource = projectFile(
            "app/src/main/java/takagi/ru/monica/data/PasswordEntryDao.kt"
        ).readText()
        val passwordRepositorySource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/PasswordRepository.kt"
        ).readText()
        val secureItemRepositorySource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/SecureItemRepository.kt"
        ).readText()
        val noteViewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/NoteViewModel.kt"
        ).readText()
        val documentViewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/DocumentViewModel.kt"
        ).readText()
        val bankCardViewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/BankCardViewModel.kt"
        ).readText()
        val totpViewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/TotpViewModel.kt"
        ).readText()

        assertTrue(
            "Password batch MDBX binding must write mdbx_folder_id into Room and mirror the same folder into the MDBX file.",
            passwordDaoSource.contains("mdbx_folder_id = :folderId") &&
                passwordDaoSource.contains("updateMdbxDatabaseForPasswords(") &&
                passwordRepositorySource.contains("folderId: String? = null") &&
                passwordRepositorySource.contains("mdbxFolderId = folderId") &&
                passwordRepositorySource.contains("passwordEntryDao.updatePasswordEntries(entriesForMdbx)")
        )
        assertTrue(
            "SecureItem repository must carry the MDBX folder id when creating or updating the MDBX-backed copy.",
            secureItemRepositorySource.contains("mdbxFolderId: String? = source.mdbxFolderId") &&
                secureItemRepositorySource.countOccurrences("mdbxFolderId = mdbxFolderId") >= 2 &&
                secureItemRepositorySource.contains("mdbxRepository?.upsertSecureItem(normalizedItem)")
        )
        assertTrue(
            "Note moves must normalize a folder target only when an MDBX database is selected and then write it to the updated item.",
            noteViewModelSource.contains("val targetMdbxFolderId = if (mdbxDatabaseId != null) mdbxFolderId else null") &&
                noteViewModelSource.contains("StorageTarget.Mdbx(mdbxDatabaseId, targetMdbxFolderId)") &&
                noteViewModelSource.contains("mdbxFolderId = targetMdbxFolderId")
        )
        assertTrue(
            "Document moves must preserve MDBX folder targets instead of collapsing everything to the vault root.",
            documentViewModelSource.contains("val targetMdbxFolderId = if (mdbxDatabaseId != null) mdbxFolderId else null") &&
                documentViewModelSource.contains("StorageTarget.Mdbx(mdbxDatabaseId, targetMdbxFolderId)") &&
                documentViewModelSource.contains("mdbxFolderId = targetMdbxFolderId")
        )
        assertTrue(
            "Card moves must preserve MDBX folder targets instead of collapsing everything to the vault root.",
            bankCardViewModelSource.contains("val targetMdbxFolderId = if (mdbxDatabaseId != null) mdbxFolderId else null") &&
                bankCardViewModelSource.contains("StorageTarget.Mdbx(mdbxDatabaseId, targetMdbxFolderId)") &&
                bankCardViewModelSource.contains("mdbxFolderId = targetMdbxFolderId")
        )
        assertTrue(
            "TOTP writes must preserve explicit MDBX folder targets and inherit the bound password folder when following a password into MDBX.",
            totpViewModelSource.contains("mdbxFolderId = resolvedMdbxFolderId") &&
                totpViewModelSource.contains("mdbxFolderId = boundPassword.mdbxFolderId") &&
                totpViewModelSource.contains("mdbxFolderId = boundPassword?.mdbxFolderId")
        )
    }

    @Test
    fun mdbxFolderIdRoundTripsThroughPayloadProjectIndexAndRoomImport() {
        val vaultStoreSource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxVaultStore.kt"
        ).readText()
        val viewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/MdbxViewModel.kt"
        ).readText()

        assertTrue(
            "MDBX item payloads must persist the folder id for every item family before encryption.",
            vaultStoreSource.contains(".put(\"mdbx_folder_id\", entry.mdbxFolderId)") &&
                vaultStoreSource.contains(".put(\"mdbx_folder_id\", item.mdbxFolderId)") &&
                vaultStoreSource.contains(".put(\"mdbx_folder_id\", passkey.mdbxFolderId)")
        )

        val writeEntryMutationBody = vaultStoreSource.substringAfter("private fun writeEntryMutation(")
            .substringBefore("private fun writeEntryDeleteMutation(")
        assertTrue(
            "MDBX writes must derive project folder placement from the encrypted payload and store it in project/object_index metadata.",
            writeEntryMutationBody.contains("val folderId = folderIdFromPayload(mutation.payloadJson)") &&
                writeEntryMutationBody.contains("ensureFolder(db, folderId, epochKey)") &&
                writeEntryMutationBody.contains("arrayOf(mutation.projectId, titleCt, folderId") &&
                writeEntryMutationBody.contains("arrayOf(titleCt, folderId, commitId") &&
                writeEntryMutationBody.contains("parentFolderId = folderId")
        )

        val folderIdFromPayloadBody = vaultStoreSource.substringAfter("private fun folderIdFromPayload(")
            .substringBefore("private fun buildDeviceId()")
        assertTrue(
            "Folder parsing must treat root/blank as root but preserve explicit non-root MDBX folders.",
            folderIdFromPayloadBody.contains("payload.optString(\"mdbx_folder_id\")") &&
                folderIdFromPayloadBody.contains("!it.equals(\"root\", ignoreCase = true)") &&
                folderIdFromPayloadBody.contains("if (explicitFolderId != null) return explicitFolderId")
        )

        val importPasswordBody = viewModelSource.substringAfter("private suspend fun importPasswordEntry(")
            .substringBefore("private suspend fun restoreCustomFields(")
        val importSecureItemBody = viewModelSource.substringAfter("private suspend fun importSecureItem(")
            .substringBefore("private fun remapImportedTotpBinding(")
        val importPasskeyBody = viewModelSource.substringAfter("private suspend fun importPasskey(")
            .substringBefore("private fun JSONObject.optMdbxFolderId(")
        assertTrue(
            "MDBX import must restore the folder id into Room for passwords, secure items, and passkeys.",
            importPasswordBody.contains("mdbxFolderId = payload.optMdbxFolderId()") &&
                importSecureItemBody.contains("mdbxFolderId = payload.optMdbxFolderId()") &&
                importPasskeyBody.contains("mdbxFolderId = payload.optMdbxFolderId()")
        )

        val optFolderBody = viewModelSource.substringAfter("private fun JSONObject.optMdbxFolderId(): String?")
            .substringBefore("private suspend fun importAttachmentsFromVault(")
        assertTrue(
            "Room import must not store root as a concrete folder id.",
            optFolderBody.contains("optString(\"mdbx_folder_id\")") &&
                optFolderBody.contains("!it.equals(\"root\", ignoreCase = true)")
        )
    }

    @Test
    fun passwordAndTotpEntryMutationsSerializeVaultWritesAndFlushes() {
        val vaultStoreSource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxVaultStore.kt"
        ).readText()
        val mutateEntriesByVaultBody = vaultStoreSource
            .substringAfter("private suspend fun <T : Any> mutateEntriesByVault(")
            .substringBefore("private fun mutationDatabaseId(")

        assertTrue(
            "Password/TOTP/passkey MDBX entry mutations must hold the per-vault write lock across SQLite writes and working-copy flushes.",
            mutateEntriesByVaultBody.contains("withVaultWriteLock(file)") &&
                mutateEntriesByVaultBody.contains("openExistingWritableVault(file).use") &&
                mutateEntriesByVaultBody.contains("markWorkingCopyDirtyAndFlushLocked(dbInfo, file)")
        )
        assertFalse(
            "Entry mutation writes must not release the vault lock before flushing the working copy.",
            mutateEntriesByVaultBody.contains("markWorkingCopyDirtyAndFlush(dbInfo, file)")
        )
    }

    @Test
    fun incomingVaultApplySerializesWithLocalMdbxWrites() {
        val vaultStoreSource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxVaultStore.kt"
        ).readText()
        val applyIncomingBody = vaultStoreSource
            .substringAfter("suspend fun applyIncomingVaultFile(")
            .substringBefore("override suspend fun upsertPassword")

        assertTrue(
            "MDBX incoming sync must use the same per-vault write lock as autofill, restore, and normal item writes.",
            applyIncomingBody.contains("withVaultWriteLock(localFile)") &&
                applyIncomingBody.contains("openExistingWritableVault(localFile).use") &&
                applyIncomingBody.contains("flushWorkingCopyToSourceIfNeeded(dbInfo, localFile)")
        )
        assertFalse(
            "Incoming sync must not release the vault lock before flushing the merged working copy.",
            applyIncomingBody.contains("}.also {\n            flushWorkingCopyToSourceIfNeeded(dbInfo, localFile)")
        )
    }

    @Test
    fun directMdbxFlushesSerializeWithVaultWrites() {
        val vaultStoreSource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxVaultStore.kt"
        ).readText()
        val flushPendingBody = vaultStoreSource
            .substringAfter("override suspend fun flushPendingWorkingCopy(")
            .substringBefore("override suspend fun flushWorkingCopy(")
        val flushBody = vaultStoreSource
            .substringAfter("override suspend fun flushWorkingCopy(")
            .substringBefore("override suspend fun listConflicts(")

        assertTrue(
            "Pending MDBX flush must hold the same per-vault write lock as item writes.",
            flushPendingBody.contains("withVaultWriteLock(file)") &&
                flushPendingBody.contains("flushWorkingCopyToSourceIfNeeded(dbInfo, file)")
        )
        assertTrue(
            "Explicit MDBX flush must hold the same per-vault write lock as item writes.",
            flushBody.contains("withVaultWriteLock(file)") &&
                flushBody.contains("flushWorkingCopyToSourceIfNeeded(dbInfo, file)")
        )
    }

    @Test
    fun appFacingWordingTreatsMdbxAsOnePointZeroNotTestFeature() {
        val managerSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/MdbxManagerScreen.kt"
        ).readText()
        val syncBackupSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/screens/SyncBackupScreen.kt"
        ).readText()
        val zhStrings = projectFile("app/src/main/res/values-zh/strings.xml").readText()
        val enStrings = projectFile("app/src/main/res/values/strings.xml").readText()

        assertTrue(managerSource.contains("MDBX 1.0"))
        assertTrue(syncBackupSource.contains("MDBX 1.0 数据库管理"))
        assertTrue(zhStrings.contains("MDBX 1.0 格式"))
        assertTrue(enStrings.contains("MDBX 1.0 Format"))
        assertFalse(managerSource.contains("MDBX（测试）"))
        assertFalse(syncBackupSource.contains("MDBX（测试）"))
        assertFalse(syncBackupSource.contains("MDBX 格式管理"))
    }

    @Test
    fun oneDriveMdbxUsesSourceTypeForRemoteSemanticsAndMoveUiLabels() {
        val databaseSource = projectFile(
            "app/src/main/java/takagi/ru/monica/data/LocalMdbxDatabase.kt"
        ).readText()
        val moveSheetSource = projectFile(
            "app/src/main/java/takagi/ru/monica/ui/components/UnifiedMoveToCategoryBottomSheet.kt"
        ).readText()

        assertTrue(
            "OneDrive MDBX vaults are remote sources too; do not treat only WebDAV as remote.",
            databaseSource.contains("fun LocalMdbxDatabase.isRemoteSource(): Boolean = when (sourceTypeEnum)") &&
                databaseSource.contains("MdbxSourceType.REMOTE_WEBDAV,") &&
                databaseSource.contains("MdbxSourceType.REMOTE_ONEDRIVE -> true")
        )
        assertTrue(
            "Move/copy UI must describe MDBX sources from sourceTypeEnum so OneDrive does not appear as remote webdav.",
            moveSheetSource.contains("is MovePickerSource.MdbxDatabase -> when (source.database.sourceTypeEnum)") &&
                moveSheetSource.contains("MdbxSourceType.REMOTE_WEBDAV -> \"WebDAV\"") &&
                moveSheetSource.contains("MdbxSourceType.REMOTE_ONEDRIVE -> \"OneDrive\"")
        )
    }

    @Test
    fun billingAddressesUseFirstClassMdbxSecureItemType() {
        val secureItemRepositorySource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/SecureItemRepository.kt"
        ).readText()
        val vaultStoreSource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxVaultStore.kt"
        ).readText()
        val viewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/MdbxViewModel.kt"
        ).readText()
        val billingAddressViewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/BillingAddressViewModel.kt"
        ).readText()

        assertTrue(
            "Billing addresses must map to an explicit MDBX entry type and use the existing secure-item MDBX persistence path.",
            secureItemRepositorySource.contains("ItemType.BILLING_ADDRESS -> \"billing-address\"") &&
                secureItemRepositorySource.contains("mdbxRepository?.upsertSecureItem(persistedItem)") &&
                secureItemRepositorySource.contains("mdbxRepository?.upsertSecureItem(normalizedItem)")
        )
        assertTrue(
            "MDBX vault storage must preserve billing addresses as first-class secure items instead of silently dropping them.",
            vaultStoreSource.contains("ItemType.BILLING_ADDRESS -> \"billing-address\"") &&
                vaultStoreSource.contains("\"billing-address\" -> \"账单地址\"")
        )
        assertTrue(
            "MDBX import must include active billing-address entries and restore their Room item type.",
            viewModelSource.contains("private val mdbxSecureItemEntryTypes = setOf(") &&
                viewModelSource.contains("\"billing-address\"") &&
                viewModelSource.contains("entryType in mdbxSecureItemEntryTypes") &&
                viewModelSource.contains("\"billing-address\" -> ItemType.BILLING_ADDRESS") &&
                viewModelSource.contains("ItemType.BILLING_ADDRESS -> \"billing-address\"")
        )
        assertTrue(
            "Billing address ViewModel operations must route through SecureItemRepository so MDBX-backed items write the vault file, not only Room.",
            billingAddressViewModelSource.contains("mdbxDatabaseId: Long? = null") &&
                billingAddressViewModelSource.contains("itemType = ItemType.BILLING_ADDRESS") &&
                billingAddressViewModelSource.contains("mdbxDatabaseId = mdbxDatabaseId") &&
                billingAddressViewModelSource.contains("repository.insertItem(copy)") &&
                billingAddressViewModelSource.contains("repository.updateItem(updatedItem)")
        )
    }

    @Test
    fun paymentAccountsUseExplicitRemoteCompatibilityBoundaries() {
        val secureItemRepositorySource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/SecureItemRepository.kt"
        ).readText()
        val vaultStoreSource = projectFile(
            "app/src/main/java/takagi/ru/monica/repository/MdbxVaultStore.kt"
        ).readText()
        val viewModelSource = projectFile(
            "app/src/main/java/takagi/ru/monica/viewmodel/MdbxViewModel.kt"
        ).readText()
        val mapperFactorySource = projectFile(
            "app/src/main/java/takagi/ru/monica/bitwarden/mapper/MapperFactory.kt"
        ).readText()

        assertTrue(
            "Payment accounts must use an explicit MDBX entry type when a storage path later writes them to MDBX.",
            secureItemRepositorySource.contains("ItemType.PAYMENT_ACCOUNT -> \"payment-account\"") &&
                vaultStoreSource.contains("ItemType.PAYMENT_ACCOUNT -> \"payment-account\"") &&
                vaultStoreSource.contains("\"payment-account\" -> \"支付方式\"")
        )
        assertTrue(
            "MDBX import must recognize payment-account entries instead of treating them as orphans.",
            viewModelSource.contains("private val mdbxSecureItemEntryTypes = setOf(\"note\", \"totp\", \"card\", \"document-ref\", \"billing-address\", \"payment-account\")") &&
                viewModelSource.contains("\"note\", \"totp\", \"card\", \"document-ref\", \"billing-address\", \"payment-account\" ->") &&
                viewModelSource.contains("\"payment-account\" -> ItemType.PAYMENT_ACCOUNT") &&
                viewModelSource.contains("ItemType.PAYMENT_ACCOUNT -> \"payment-account\"")
        )
        assertTrue(
            "Bitwarden has no first-class payment-account cipher in Monica, so the mapper boundary must stay explicit.",
            mapperFactorySource.contains("ItemType.PAYMENT_ACCOUNT -> null")
        )
    }

    private fun String.countOccurrences(needle: String): Int {
        if (needle.isEmpty()) return 0
        var count = 0
        var index = indexOf(needle)
        while (index >= 0) {
            count++
            index = indexOf(needle, startIndex = index + needle.length)
        }
        return count
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
