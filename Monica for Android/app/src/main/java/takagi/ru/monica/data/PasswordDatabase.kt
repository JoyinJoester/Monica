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
        SecureItem::class,
        Category::class,
        OperationLog::class,
        LocalKeePassDatabase::class,
        CustomField::class  // 自定义字段表
    ],
    version = 27,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PasswordDatabase : RoomDatabase() {
    
    abstract fun passwordEntryDao(): PasswordEntryDao
    abstract fun secureItemDao(): SecureItemDao
    abstract fun categoryDao(): CategoryDao
    abstract fun operationLogDao(): OperationLogDao
    abstract fun localKeePassDatabaseDao(): LocalKeePassDatabaseDao
    abstract fun customFieldDao(): CustomFieldDao  // 自定义字段 DAO
    
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

        // Migration 15 → 16 - 空迁移(版本号占位)
        private val MIGRATION_15_16 = object : androidx.room.migration.Migration(15, 16) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 此版本暂无数据库结构变更
            }
        }

        // Migration 16 → 17 - 添加分类功能
        private val MIGRATION_16_17 = object : androidx.room.migration.Migration(16, 17) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Create categories table
                database.execSQL("CREATE TABLE IF NOT EXISTS `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `sortOrder` INTEGER NOT NULL DEFAULT 0)")
                
                // Add categoryId to password_entries
                database.execSQL("ALTER TABLE `password_entries` ADD COLUMN `categoryId` INTEGER DEFAULT NULL")
            }
        }

        // Migration 17 → 18 - 添加authenticatorKey字段
        private val MIGRATION_17_18 = object : androidx.room.migration.Migration(17, 18) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // Add authenticatorKey to password_entries
                database.execSQL("ALTER TABLE `password_entries` ADD COLUMN `authenticatorKey` TEXT NOT NULL DEFAULT ''")
            }
        }

        // Migration 18 → 19 - 添加操作日志表
        private val MIGRATION_18_19 = object : androidx.room.migration.Migration(18, 19) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS operation_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        itemType TEXT NOT NULL,
                        itemId INTEGER NOT NULL,
                        itemTitle TEXT NOT NULL,
                        operationType TEXT NOT NULL,
                        changesJson TEXT NOT NULL DEFAULT '',
                        deviceId TEXT NOT NULL DEFAULT '',
                        deviceName TEXT NOT NULL DEFAULT '',
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_operation_logs_timestamp ON operation_logs(timestamp)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_operation_logs_itemType ON operation_logs(itemType)")
            }
        }

        // Migration 19 → 20 - 添加 isReverted 字段
        private val MIGRATION_19_20 = object : androidx.room.migration.Migration(19, 20) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE operation_logs ADD COLUMN isReverted INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Migration 20 → 21 - 添加回收站功能（软删除字段）
        private val MIGRATION_20_21 = object : androidx.room.migration.Migration(20, 21) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 为 password_entries 表添加软删除字段
                database.execSQL("ALTER TABLE password_entries ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE password_entries ADD COLUMN deletedAt INTEGER DEFAULT NULL")
                
                // 为 secure_items 表添加软删除字段
                database.execSQL("ALTER TABLE secure_items ADD COLUMN isDeleted INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE secure_items ADD COLUMN deletedAt INTEGER DEFAULT NULL")
                
                // 创建索引以优化查询
                database.execSQL("CREATE INDEX IF NOT EXISTS index_password_entries_isDeleted ON password_entries(isDeleted)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_secure_items_isDeleted ON secure_items(isDeleted)")
            }
        }

        // Migration 21 → 22 - 添加第三方登录(SSO)字段
        private val MIGRATION_21_22 = object : androidx.room.migration.Migration(21, 22) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 添加登录类型字段
                database.execSQL("ALTER TABLE password_entries ADD COLUMN loginType TEXT NOT NULL DEFAULT 'PASSWORD'")
                // 添加SSO提供商字段
                database.execSQL("ALTER TABLE password_entries ADD COLUMN ssoProvider TEXT NOT NULL DEFAULT ''")
                // 添加关联账号条目ID字段
                database.execSQL("ALTER TABLE password_entries ADD COLUMN ssoRefEntryId INTEGER DEFAULT NULL")
            }
        }
        
        // Migration 22 → 23 - 添加本地 KeePass 数据库管理表
        private val MIGRATION_22_23 = object : androidx.room.migration.Migration(22, 23) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS local_keepass_databases (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        name TEXT NOT NULL,
                        filePath TEXT NOT NULL,
                        keyFileUri TEXT,
                        storage_location TEXT NOT NULL,
                        encrypted_password TEXT,
                        description TEXT,
                        created_at INTEGER NOT NULL,
                        last_accessed_at INTEGER NOT NULL,
                        last_synced_at INTEGER,
                        is_default INTEGER NOT NULL,
                        entry_count INTEGER NOT NULL,
                        sort_order INTEGER NOT NULL
                    )
                """.trimIndent())
                database.execSQL("CREATE INDEX IF NOT EXISTS index_local_keepass_databases_storage_location ON local_keepass_databases(storage_location)")
            }
        }
        
        // Migration 23 → 24 - 为密码条目添加 KeePass 数据库归属字段
        private val MIGRATION_23_24 = object : androidx.room.migration.Migration(23, 24) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE password_entries ADD COLUMN keepassDatabaseId INTEGER DEFAULT NULL")
            }
        }
        
        // Migration 24 → 25 - 修复 local_keepass_databases 表结构（保留数据）
        private val MIGRATION_24_25 = object : androidx.room.migration.Migration(24, 25) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 检查表是否存在并需要修复
                try {
                    // 1. 重命名旧表
                    database.execSQL("ALTER TABLE local_keepass_databases RENAME TO local_keepass_databases_backup")
                    
                    // 2. 创建正确结构的新表（统一不包含 keyFileUri，留给 25->26 添加）
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS local_keepass_databases (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            filePath TEXT NOT NULL,
                            storage_location TEXT NOT NULL,
                            encrypted_password TEXT,
                            description TEXT,
                            created_at INTEGER NOT NULL,
                            last_accessed_at INTEGER NOT NULL,
                            last_synced_at INTEGER,
                            is_default INTEGER NOT NULL,
                            entry_count INTEGER NOT NULL,
                            sort_order INTEGER NOT NULL
                        )
                    """.trimIndent())
                    
                    // 3. 复制数据
                    database.execSQL("""
                        INSERT INTO local_keepass_databases 
                        SELECT id, name, filePath, storage_location, encrypted_password, description,
                               created_at, last_accessed_at, last_synced_at, is_default, entry_count, sort_order
                        FROM local_keepass_databases_backup
                    """.trimIndent())
                    
                    // 4. 删除备份表
                    database.execSQL("DROP TABLE IF EXISTS local_keepass_databases_backup")
                    
                    // 5. 重建索引
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_local_keepass_databases_storage_location ON local_keepass_databases(storage_location)")
                } catch (e: Exception) {
                    // 如果出错（例如旧表不存在），确保新建一个干净的表
                    database.execSQL("DROP TABLE IF EXISTS local_keepass_databases_backup")
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS local_keepass_databases (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            name TEXT NOT NULL,
                            filePath TEXT NOT NULL,
                            storage_location TEXT NOT NULL,
                            encrypted_password TEXT,
                            description TEXT,
                            created_at INTEGER NOT NULL,
                            last_accessed_at INTEGER NOT NULL,
                            last_synced_at INTEGER,
                            is_default INTEGER NOT NULL,
                            entry_count INTEGER NOT NULL,
                            sort_order INTEGER NOT NULL
                        )
                    """.trimIndent())
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_local_keepass_databases_storage_location ON local_keepass_databases(storage_location)")
                }
            }
        }
        
        // Migration 25 → 26 - 为本地 KeePass 数据库添加密钥文件字段
        // 修复版：增加容错检查，防止重复添加字段导致崩溃
        private val MIGRATION_25_26 = object : androidx.room.migration.Migration(25, 26) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                val cursor = database.query("PRAGMA table_info(local_keepass_databases)")
                var hasColumn = false
                try {
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIndex != -1 && cursor.getString(nameIndex) == "keyFileUri") {
                            hasColumn = true
                            break
                        }
                    }
                } finally {
                    cursor.close()
                }

                if (!hasColumn) {
                    try {
                        database.execSQL("ALTER TABLE local_keepass_databases ADD COLUMN keyFileUri TEXT")
                    } catch (e: Exception) {
                        // 忽略错误，例如字段已存在（虽然我们检查了，但为了双重保险）
                        android.util.Log.e("PasswordDatabase", "Failed to add column keyFileUri: ${e.message}")
                    }
                }
            }
        }
        
        // Migration 26 → 27 - 添加自定义字段表 (custom_fields)
        // 支持每个密码条目拥有无限个自定义键值对
        private val MIGRATION_26_27 = object : androidx.room.migration.Migration(26, 27) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 迁移前检查：表是否已存在
                val cursor = database.query("SELECT name FROM sqlite_master WHERE type='table' AND name='custom_fields'")
                val tableExists = cursor.count > 0
                cursor.close()
                
                if (tableExists) {
                    android.util.Log.w("PasswordDatabase", "custom_fields table already exists, skipping creation")
                    return
                }
                
                try {
                    // 创建自定义字段表
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS custom_fields (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            entry_id INTEGER NOT NULL,
                            title TEXT NOT NULL,
                            value TEXT NOT NULL,
                            is_protected INTEGER NOT NULL DEFAULT 0,
                            sort_order INTEGER NOT NULL DEFAULT 0,
                            FOREIGN KEY(entry_id) REFERENCES password_entries(id) ON DELETE CASCADE
                        )
                    """.trimIndent())
                    
                    // 创建索引以提升查询性能
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_custom_fields_entry_id ON custom_fields(entry_id)")
                    
                    android.util.Log.i("PasswordDatabase", "Successfully created custom_fields table")
                } catch (e: Exception) {
                    android.util.Log.e("PasswordDatabase", "Failed to create custom_fields table: ${e.message}")
                    // 不抛出异常，让迁移继续，避免应用崩溃
                    // Room 会在后续操作中处理不一致性
                }
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
                        MIGRATION_14_15,  // 扩展OTP支持
                        MIGRATION_15_16,  // 版本占位
                        MIGRATION_16_17,  // 添加分类功能
                        MIGRATION_17_18,  // 添加authenticatorKey字段
                        MIGRATION_18_19,  // 添加操作日志表
                        MIGRATION_19_20,  // 添加 isReverted 字段
                        MIGRATION_20_21,  // 添加回收站功能（软删除字段）
                        MIGRATION_21_22,  // 添加第三方登录(SSO)字段
                        MIGRATION_22_23,  // 添加本地 KeePass 数据库管理表
                        MIGRATION_23_24,  // 为密码条目添加 KeePass 数据库归属字段
                        MIGRATION_24_25,  // 修复 local_keepass_databases 表结构
                        MIGRATION_25_26,  // 添加 KeePass 密钥文件字段
                        MIGRATION_26_27   // 添加自定义字段表
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}



