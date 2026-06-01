package takagi.ru.monica.attachments.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortableAttachmentBackupTest {

    @Test
    fun decodeManifest_keepsUnknownFieldsAndValidEntries() {
        val manifest = """
            {
              "version": 99,
              "future": "ignored",
              "entries": [
                {
                  "parentPasswordId": 42,
                  "fileName": "invoice.pdf",
                  "mimeType": "application/pdf",
                  "sizeBytes": 12,
                  "sha256Hex": "abc",
                  "payloadPath": "attachments_portable/blob.bin",
                  "createdAt": 1000,
                  "updatedAt": 2000
                }
              ]
            }
        """.trimIndent()

        val decoded = PortableAttachmentBackup.decodeManifest(manifest)

        assertEquals(99, decoded.version)
        assertEquals(1, decoded.entries.size)
        assertTrue(decoded.entries.single().isValid())
        assertEquals("attachments_portable/blob.bin", decoded.entries.single().payloadPath)
    }

    @Test
    fun decodeManifest_returnsEmptyManifestForBrokenInput() {
        val decoded = PortableAttachmentBackup.decodeManifest("{not-json")

        assertEquals(1, decoded.version)
        assertTrue(decoded.entries.isEmpty())
    }

    @Test
    fun entryValidationRejectsMissingParentFileNameOrPayloadPath() {
        val valid = PortableAttachmentBackup.Entry(
            parentPasswordId = 1,
            fileName = "a.txt",
            mimeType = "text/plain",
            sizeBytes = 1,
            sha256Hex = null,
            payloadPath = "attachments_portable/a.bin",
            createdAt = 1,
            updatedAt = 1
        )

        assertTrue(valid.isValid())
        assertFalse(valid.copy(parentPasswordId = 0).isValid())
        assertFalse(valid.copy(fileName = "").isValid())
        assertFalse(valid.copy(payloadPath = "").isValid())
    }
}
