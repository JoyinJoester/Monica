package takagi.ru.monica.wear.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database for Monica-wear (仅包含TOTP验证器)
 */
@Database(
    entities = [SecureItem::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PasswordDatabase : RoomDatabase() {
    
    abstract fun secureItemDao(): SecureItemDao
    
    companion object {
        @Volatile
        private var INSTANCE: PasswordDatabase? = null
        
        fun getDatabase(context: Context): PasswordDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PasswordDatabase::class.java,
                    "monica_wear_database"
                )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
