package takagi.ru.monica.wear.data

import androidx.room.TypeConverter
import java.util.Date

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
}
