package takagi.ru.monica.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * Room database for storing password entries and secure items
 */
@Database(
    entities = [
        PasswordEntry::class,
        SecureItem::class
    ],
    version = 15,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PasswordDatabase : RoomDatabase() {
    
    abstract fun passwordEntryDao(): PasswordEntryDao
    abstract fun secureItemDao(): SecureItemDao
    
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

        private val MIGRATION_7_8 = object : androidx.room.migration.Migration(7, 8) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 创建assets表
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS assets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        assetType TEXT NOT NULL,
                        balanceInCents INTEGER NOT NULL DEFAULT 0,
                        currencyCode TEXT NOT NULL DEFAULT 'CNY',
                        iconKey TEXT NOT NULL DEFAULT 'wallet',
                        colorHex TEXT NOT NULL DEFAULT '#4CAF50',
                        linkedBankCardId INTEGER,
                        isActive INTEGER NOT NULL DEFAULT 1,
                        sortOrder INTEGER NOT NULL DEFAULT 0,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_assets_assetType ON assets(assetType)")
            }
        }

        private val MIGRATION_8_9 = object : androidx.room.migration.Migration(8, 9) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 为linkedBankCardId添加唯一索引
                // 1. 先删除重复的银行卡资产(保留最早创建的)
                database.execSQL("""
                    DELETE FROM assets 
                    WHERE id NOT IN (
                        SELECT MIN(id) 
                        FROM assets 
                        WHERE linkedBankCardId IS NOT NULL 
                        GROUP BY linkedBankCardId
                    ) AND linkedBankCardId IS NOT NULL
                """.trimIndent())
                
                // 2. 创建唯一索引
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_assets_linkedBankCardId ON assets(linkedBankCardId)")
            }
        }

        private val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 为password_entries表添加应用包名和应用名称字段（用于自动填充）
                database.execSQL("ALTER TABLE password_entries ADD COLUMN appPackageName TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE password_entries ADD COLUMN appName TEXT NOT NULL DEFAULT ''")
            }
        }

        // Phase 7: Migration 10 → 11 - 扩展数据模型
        private val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 添加个人信息字段
                database.execSQL("ALTER TABLE password_entries ADD COLUMN email TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE password_entries ADD COLUMN phone TEXT NOT NULL DEFAULT ''")
                
                // 添加地址信息字段
                database.execSQL("ALTER TABLE password_entries ADD COLUMN addressLine TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE password_entries ADD COLUMN city TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE password_entries ADD COLUMN state TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE password_entries ADD COLUMN zipCode TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE password_entries ADD COLUMN country TEXT NOT NULL DEFAULT ''")
                
                // 添加支付信息字段 (加密存储)
                database.execSQL("ALTER TABLE password_entries ADD COLUMN creditCardNumber TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE password_entries ADD COLUMN creditCardHolder TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE password_entries ADD COLUMN creditCardExpiry TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE password_entries ADD COLUMN creditCardCVV TEXT NOT NULL DEFAULT ''")
            }
        }

        // Migration 11 → 12 - 删除记账功能相关表
        private val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 删除所有记账相关的表
                database.execSQL("DROP TABLE IF EXISTS ledger_entries")
                database.execSQL("DROP TABLE IF EXISTS ledger_categories")
                database.execSQL("DROP TABLE IF EXISTS ledger_tags")
                database.execSQL("DROP TABLE IF EXISTS ledger_entry_tag_cross_ref")
                database.execSQL("DROP TABLE IF EXISTS assets")
            }
        }
        // Migration 12 → 13 - 预留版本 (Passkey 功能开发)
        private val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 此版本暂无数据库结构变更
            }
        }
        
        // Migration 13 → 14 - 删除 Passkey 功能
        private val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 删除 passkeys 表 (如果存在)
                database.execSQL("DROP TABLE IF EXISTS passkeys")
            }
        }
        
        // Migration 14 → 15 - 扩展OTP支持 (HOTP/Steam/Yandex/mOTP)
        private val MIGRATION_14_15 = object : androidx.room.migration.Migration(14, 15) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 由于TotpData使用JSON存储在itemData字段中,
                // 新增的otpType、counter、pin字段通过Kotlin序列化的默认值机制自动处理
                // otpType默认为TOTP,确保向后兼容
                // 不需要修改数据库结构
                // 现有TOTP记录在反序列化时自动获得默认值: otpType=TOTP, counter=0, pin=""
            }
        }


        fun getDatabase(context: Context): PasswordDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PasswordDatabase::class.java,
                    "password_database"
                )
                    .addMigrations(
                        MIGRATION_1_2, 
                        MIGRATION_2_3, 
                        MIGRATION_3_4, 
                        MIGRATION_4_5, 
                        MIGRATION_5_6, 
                        MIGRATION_6_7, 
                        MIGRATION_7_8, 
                        MIGRATION_8_9, 
                        MIGRATION_9_10,
                        MIGRATION_10_11,
                        MIGRATION_11_12,  // 删除记账功能
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15   // 扩展OTP支持
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}



