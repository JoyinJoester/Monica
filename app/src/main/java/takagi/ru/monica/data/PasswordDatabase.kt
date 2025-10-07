package takagi.ru.monica.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import takagi.ru.monica.data.ledger.LedgerCategory
import takagi.ru.monica.data.ledger.LedgerDao
import takagi.ru.monica.data.ledger.LedgerEntry
import takagi.ru.monica.data.ledger.LedgerEntryTagCrossRef
import takagi.ru.monica.data.ledger.LedgerTag

/**
 * Room database for storing password entries and secure items
 */
@Database(
    entities = [
        PasswordEntry::class,
        SecureItem::class,
        LedgerEntry::class,
        LedgerCategory::class,
        LedgerTag::class,
        LedgerEntryTagCrossRef::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PasswordDatabase : RoomDatabase() {
    
    abstract fun passwordEntryDao(): PasswordEntryDao
    abstract fun secureItemDao(): SecureItemDao
    abstract fun ledgerDao(): LedgerDao
    
    companion object {
        @Volatile
        private var INSTANCE: PasswordDatabase? = null
        
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 创建secure_items表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS secure_items (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        itemType TEXT NOT NULL,
                        title TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        isFavorite INTEGER NOT NULL,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL,
                        itemData TEXT NOT NULL,
                        imagePaths TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }
        
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 为secure_items表添加sortOrder字段
                database.execSQL("ALTER TABLE secure_items ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 为password_entries表添加sortOrder字段
                database.execSQL("ALTER TABLE password_entries ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 为password_entries表添加isGroupCover字段
                database.execSQL("ALTER TABLE password_entries ADD COLUMN isGroupCover INTEGER NOT NULL DEFAULT 0")
            }
        }
        
        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create ledger categories table
                database.execSQL(
                    """
                        CREATE TABLE IF NOT EXISTS ledger_categories (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            type TEXT,
                            iconKey TEXT NOT NULL,
                            colorHex TEXT NOT NULL,
                            sortOrder INTEGER NOT NULL DEFAULT 0,
                            parentId INTEGER,
                            FOREIGN KEY(parentId) REFERENCES ledger_categories(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED
                        )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ledger_categories_parentId ON ledger_categories(parentId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ledger_categories_type ON ledger_categories(type)")

                // Create ledger tags table
                database.execSQL(
                    """
                        CREATE TABLE IF NOT EXISTS ledger_tags (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            colorHex TEXT NOT NULL
                        )
                    """.trimIndent()
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_ledger_tags_name ON ledger_tags(name)")

                // Create ledger entries table
                database.execSQL(
                    """
                        CREATE TABLE IF NOT EXISTS ledger_entries (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            title TEXT NOT NULL,
                            amountInCents INTEGER NOT NULL DEFAULT 0,
                            currencyCode TEXT NOT NULL,
                            type TEXT NOT NULL,
                            categoryId INTEGER,
                            linkedItemId INTEGER,
                            occurredAt INTEGER NOT NULL,
                            note TEXT NOT NULL DEFAULT '',
                            createdAt INTEGER NOT NULL DEFAULT 0,
                            updatedAt INTEGER NOT NULL DEFAULT 0,
                            FOREIGN KEY(categoryId) REFERENCES ledger_categories(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
                            FOREIGN KEY(linkedItemId) REFERENCES secure_items(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED
                        )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ledger_entries_categoryId ON ledger_entries(categoryId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ledger_entries_linkedItemId ON ledger_entries(linkedItemId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ledger_entries_occurredAt ON ledger_entries(occurredAt)")

                // Create ledger entry-tag cross reference table
                database.execSQL(
                    """
                        CREATE TABLE IF NOT EXISTS ledger_entry_tag_cross_ref (
                            entryId INTEGER NOT NULL,
                            tagId INTEGER NOT NULL,
                            PRIMARY KEY(entryId, tagId),
                            FOREIGN KEY(entryId) REFERENCES ledger_entries(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,
                            FOREIGN KEY(tagId) REFERENCES ledger_tags(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED
                        )
                    """.trimIndent()
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_ledger_entry_tag_cross_ref_tagId ON ledger_entry_tag_cross_ref(tagId)")
            }
        }

        private val MIGRATION_6_7 = object : androidx.room.migration.Migration(6, 7) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 为ledger_entries表添加paymentMethod字段
                database.execSQL("ALTER TABLE ledger_entries ADD COLUMN paymentMethod TEXT NOT NULL DEFAULT ''")
            }
        }

        fun getDatabase(context: Context): PasswordDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PasswordDatabase::class.java,
                    "password_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}