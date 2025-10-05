package takagi.ru.monica.repository

import takagi.ru.monica.data.PasswordEntry
import takagi.ru.monica.data.PasswordEntryDao
import kotlinx.coroutines.flow.Flow

/**
 * Repository for password entries
 */
class PasswordRepository(
    private val passwordEntryDao: PasswordEntryDao
) {
    
    fun getAllPasswordEntries(): Flow<List<PasswordEntry>> {
        return passwordEntryDao.getAllPasswordEntries()
    }
    
    fun searchPasswordEntries(query: String): Flow<List<PasswordEntry>> {
        return passwordEntryDao.searchPasswordEntries(query)
    }
    
    suspend fun getPasswordEntryById(id: Long): PasswordEntry? {
        return passwordEntryDao.getPasswordEntryById(id)
    }
    
    suspend fun insertPasswordEntry(entry: PasswordEntry): Long {
        return passwordEntryDao.insertPasswordEntry(entry)
    }
    
    suspend fun updatePasswordEntry(entry: PasswordEntry) {
        passwordEntryDao.updatePasswordEntry(entry)
    }
    
    suspend fun deletePasswordEntry(entry: PasswordEntry) {
        passwordEntryDao.deletePasswordEntry(entry)
    }
    
    suspend fun deletePasswordEntryById(id: Long) {
        passwordEntryDao.deletePasswordEntryById(id)
    }
    
    suspend fun getPasswordEntriesCount(): Int {
        return passwordEntryDao.getPasswordEntriesCount()
    }
    
    suspend fun deleteAllPasswordEntries() {
        passwordEntryDao.deleteAllPasswordEntries()
    }
}