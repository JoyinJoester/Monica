package takagi.ru.monica.domain.provider

import takagi.ru.monica.data.PasswordEntry
import java.util.Date

class PasswordCommandStateFactory {
    fun createQueuedDeleteTombstone(
        entry: PasswordEntry,
        now: Date,
        commandPolicy: PasswordCommandPolicy
    ): PasswordEntry {
        return createSoftDeletedEntry(
            entry = entry,
            now = now,
            commandPolicy = commandPolicy
        ).copy(bitwardenLocalModified = true)
    }

    fun createSoftDeletedEntry(
        entry: PasswordEntry,
        now: Date,
        commandPolicy: PasswordCommandPolicy
    ): PasswordEntry {
        return entry.copy(
            isDeleted = true,
            deletedAt = now,
            isArchived = false,
            archivedAt = null,
            updatedAt = now,
            bitwardenLocalModified = resolvePendingRemoteMutation(
                entry = entry,
                commandPolicy = commandPolicy
            )
        )
    }

    fun createArchivedEntry(
        entry: PasswordEntry,
        now: Date,
        commandPolicy: PasswordCommandPolicy
    ): PasswordEntry {
        return entry.copy(
            isArchived = true,
            archivedAt = entry.archivedAt ?: now,
            updatedAt = now,
            bitwardenLocalModified = resolvePendingRemoteMutation(
                entry = entry,
                commandPolicy = commandPolicy
            )
        )
    }

    fun createUnarchivedEntry(
        entry: PasswordEntry,
        now: Date,
        commandPolicy: PasswordCommandPolicy
    ): PasswordEntry {
        return entry.copy(
            isArchived = false,
            archivedAt = null,
            updatedAt = now,
            bitwardenLocalModified = resolvePendingRemoteMutation(
                entry = entry,
                commandPolicy = commandPolicy
            )
        )
    }

    fun createTrashRevertedEntry(entry: PasswordEntry, now: Date): PasswordEntry {
        return entry.copy(updatedAt = now)
    }

    private fun resolvePendingRemoteMutation(
        entry: PasswordEntry,
        commandPolicy: PasswordCommandPolicy
    ): Boolean {
        return if (commandPolicy.shouldMarkPendingRemoteMutation) {
            true
        } else {
            entry.bitwardenLocalModified
        }
    }
}
