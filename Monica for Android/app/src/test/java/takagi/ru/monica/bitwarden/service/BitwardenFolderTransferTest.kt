package takagi.ru.monica.bitwarden.service

import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class BitwardenFolderTransferTest {
    private val folders = mapOf("work" to "Work", "child" to "Work/Child", "other" to "Workplace", "target" to "Personal")

    @Test fun movePlansWholeTreeWithoutSimilarlyNamedSiblings() {
        assertEquals(mapOf("work" to "Personal/Work", "child" to "Personal/Work/Child"),
            planBitwardenFolderTransfer(folders, "work", "target", false, "Copy"))
    }
    @Test fun copyAtSameLocationAllocatesIndependentFolderNames() {
        val existing = folders + ("copy" to "Work (Copy)")
        assertEquals(mapOf("work" to "Work (Copy 2)", "child" to "Work (Copy 2)/Child"),
            planBitwardenFolderTransfer(existing, "work", null, true, "Copy"))
    }
    @Test fun staleCyclicAndUnchangedMoveTargetsAreRejected() {
        for (parent in listOf("work", "child", "missing")) for (copy in listOf(false, true)) {
            assertTrue(runCatching { planBitwardenFolderTransfer(folders, "work", parent, copy, "Copy") }.isFailure)
        }
        assertTrue(runCatching { planBitwardenFolderTransfer(folders, "work", null, false, "Copy") }.isFailure)
    }
    @Test fun encryptedCopyRetainsUnknownFieldsAndItemKeyWithNewFolderIdentity() {
        val source = Json.parseToJsonElement("""{"Id":"source","FolderId":"work","Key":"encrypted-key","Name":"encrypted-name","Login":{"Username":"ciphertext","Uris":[{"Uri":"encrypted-url","Match":3}]},"FutureEncryptedField":{"Blob":"opaque"},"RevisionDate":"old","Attachments":[]}""") as JsonObject
        val request = folderCopyDocument(source, "new-folder")
        assertTrue(folderCopyDocumentIsSafe(source, "source", "work"))
        assertFalse(folderCopyDocumentIsSafe(source, "source", "different-folder"))
        assertFalse(folderCopyDocumentIsSafe(source, "different-entry", "work"))
        assertEquals(JsonPrimitive("new-folder"), request["folderId"])
        assertEquals(source["Key"], request["key"])
        assertEquals(source["FutureEncryptedField"], request["futureEncryptedField"])
        assertFalse(request.containsKey("Id")); assertFalse(request.containsKey("id"))
        assertFalse(request.containsKey("revisionDate")); assertFalse(request.containsKey("attachments"))
        assertTrue(folderCopyPreserved(request, JsonObject(request + ("id" to JsonPrimitive("new")))))
        assertFalse(folderCopyPreserved(request, JsonObject(request - "futureEncryptedField")))
    }
    @Test fun attachmentsPasskeysAndOrganizationKeysCannotBeSilentlyCopied() {
        for (json in listOf("""{"Attachments":[{"Id":"attachment"}]}""",
            """{"Login":{"Fido2Credentials":[{"keyValue":"encrypted"}]}}""",
            """{"organizationId":"org"}""", """{"deletedDate":"yesterday"}""")) {
            assertFalse(folderCopyDocumentIsSafe(Json.parseToJsonElement(json) as JsonObject))
        }
    }
}
