package takagi.ru.monica.data

import androidx.room.TypeConverter
import java.util.Date
import takagi.ru.monica.data.ledger.LedgerEntryType

/**
 * Type converters for Room database
 */
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Date? {
        return value?.let { Date(it) }
    }

    @TypeConverter
    fun dateToTimestamp(date: Date?): Long? {
        return date?.time
    }
    
    @TypeConverter
    fun fromItemType(value: ItemType?): String? {
        return value?.name
    }
    
    @TypeConverter
    fun toItemType(value: String?): ItemType? {
        return value?.let { ItemType.valueOf(it) }
    }

    @TypeConverter
    fun fromLedgerEntryType(value: LedgerEntryType?): String? {
        return value?.name
    }

    @TypeConverter
    fun toLedgerEntryType(value: String?): LedgerEntryType? {
        return value?.let { LedgerEntryType.valueOf(it) }
    }
}