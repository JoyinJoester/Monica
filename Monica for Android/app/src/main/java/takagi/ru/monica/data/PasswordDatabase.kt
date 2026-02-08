package takagi.ru.monica.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import takagi.ru.monica.data.bitwarden.*

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
        KeepassGroupSyncConfig::class,
        CustomField::class,  // 自定义字段表
        PasskeyEntry::class,  // Passkey 通行密钥表
        // Bitwarden 集成表
        BitwardenVault::class,
        BitwardenFolder::class,
        BitwardenSend::class,
        BitwardenConflictBackup::class,
        BitwardenPendingOperation::class
    ],
    version = 38,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PasswordDatabase : RoomDatabase() {
    
    abstract fun passwordEntryDao(): PasswordEntryDao
    abstract fun secureItemDao(): SecureItemDao
    abstract fun categoryDao(): CategoryDao
    abstract fun operationLogDao(): OperationLogDao
    abstract fun localKeePassDatabaseDao(): LocalKeePassDatabaseDao
    abstract fun keepassGroupSyncConfigDao(): KeepassGroupSyncConfigDao
    abstract fun customFieldDao(): CustomFieldDao  // 自定义字段 DAO
    abstract fun passkeyDao(): PasskeyDao  // Passkey DAO
    
    // Bitwarden DAOs
    abstract fun bitwardenVaultDao(): BitwardenVaultDao
    abstract fun bitwardenFolderDao(): BitwardenFolderDao
    abstract fun bitwardenSendDao(): BitwardenSendDao
    abstract fun bitwardenConflictBackupDao(): BitwardenConflictBackupDao
    abstract fun bitwardenPendingOperationDao(): BitwardenPendingOperationDao
    
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
        
        // Migration 27 → 28 - 添加 Passkey 通行密钥表
        // 支持 FIDO2/WebAuthn 标准的 Passkey 存储
        private val MIGRATION_27_28 = object : androidx.room.migration.Migration(27, 28) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 迁移前检查：表是否已存在
                val cursor = database.query("SELECT name FROM sqlite_master WHERE type='table' AND name='passkeys'")
                val tableExists = cursor.count > 0
                cursor.close()
                
                if (tableExists) {
                    android.util.Log.w("PasswordDatabase", "passkeys table already exists, skipping creation")
                    return
                }
                
                try {
                    // 创建 Passkey 表
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS passkeys (
                            credential_id TEXT PRIMARY KEY NOT NULL,
                            rp_id TEXT NOT NULL,
                            rp_name TEXT NOT NULL,
                            user_id TEXT NOT NULL,
                            user_name TEXT NOT NULL,
                            user_display_name TEXT NOT NULL,
                            public_key_algorithm INTEGER NOT NULL DEFAULT -7,
                            public_key TEXT NOT NULL,
                            private_key_alias TEXT NOT NULL,
                            created_at INTEGER NOT NULL,
                            last_used_at INTEGER NOT NULL,
                            use_count INTEGER NOT NULL DEFAULT 0,
                            icon_url TEXT,
                            is_discoverable INTEGER NOT NULL DEFAULT 1,
                            is_user_verification_required INTEGER NOT NULL DEFAULT 1,
                            transports TEXT NOT NULL DEFAULT 'internal',
                            aaguid TEXT NOT NULL DEFAULT '',
                            sign_count INTEGER NOT NULL DEFAULT 0,
                            is_backed_up INTEGER NOT NULL DEFAULT 0,
                            notes TEXT NOT NULL DEFAULT ''
                        )
                    """.trimIndent())
                    
                    // 创建索引以提升查询性能
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_passkeys_rp_id ON passkeys(rp_id)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_passkeys_user_name ON passkeys(user_name)")
                    
                    android.util.Log.i("PasswordDatabase", "Successfully created passkeys table")
                } catch (e: Exception) {
                    android.util.Log.e("PasswordDatabase", "Failed to create passkeys table: ${e.message}")
                }
            }
        }
        
        // Migration 28 → 29 - 为 secure_items 添加 categoryId 字段（验证器分类功能）
        private val MIGRATION_28_29 = object : androidx.room.migration.Migration(28, 29) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                // 检查字段是否已存在
                val cursor = database.query("PRAGMA table_info(secure_items)")
                var hasColumn = false
                try {
                    val nameIndex = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIndex != -1 && cursor.getString(nameIndex) == "categoryId") {
                            hasColumn = true
                            break
                        }
                    }
                } finally {
                    cursor.close()
                }
                
                if (!hasColumn) {
                    try {
                        database.execSQL("ALTER TABLE secure_items ADD COLUMN categoryId INTEGER DEFAULT NULL")
                        android.util.Log.i("PasswordDatabase", "Successfully added categoryId column to secure_items")
                    } catch (e: Exception) {
                        android.util.Log.e("PasswordDatabase", "Failed to add categoryId column: ${e.message}")
                    }
                }
            }
        }
        
        // Migration 29 → 30 - Bitwarden 集成
        // 添加 Bitwarden 相关的表和字段
        private val MIGRATION_29_30 = object : androidx.room.migration.Migration(29, 30) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                android.util.Log.i("PasswordDatabase", "Starting Migration 29→30: Bitwarden Integration")
                
                try {
                    // 1. 创建 bitwarden_vaults 表
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS bitwarden_vaults (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            email TEXT NOT NULL,
                            user_id TEXT,
                            display_name TEXT,
                            server_url TEXT NOT NULL DEFAULT 'https://vault.bitwarden.com',
                            identity_url TEXT NOT NULL DEFAULT 'https://identity.bitwarden.com',
                            api_url TEXT NOT NULL DEFAULT 'https://api.bitwarden.com',
                            events_url TEXT,
                            encrypted_access_token TEXT,
                            encrypted_refresh_token TEXT,
                            access_token_expires_at INTEGER,
                            encrypted_master_key TEXT,
                            encrypted_enc_key TEXT,
                            encrypted_mac_key TEXT,
                            kdf_type INTEGER NOT NULL DEFAULT 0,
                            kdf_iterations INTEGER NOT NULL DEFAULT 600000,
                            kdf_memory INTEGER,
                            kdf_parallelism INTEGER,
                            last_sync_at INTEGER,
                            last_full_sync_at INTEGER,
                            revision_date TEXT,
                            is_default INTEGER NOT NULL DEFAULT 0,
                            is_locked INTEGER NOT NULL DEFAULT 1,
                            is_connected INTEGER NOT NULL DEFAULT 0,
                            sync_enabled INTEGER NOT NULL DEFAULT 1,
                            created_at INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL
                        )
                    """.trimIndent())
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bitwarden_vaults_email ON bitwarden_vaults(email)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_bitwarden_vaults_isDefault ON bitwarden_vaults(is_default)")
                    
                    // 2. 创建 bitwarden_folders 表
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS bitwarden_folders (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            vault_id INTEGER NOT NULL,
                            bitwarden_folder_id TEXT NOT NULL,
                            name TEXT NOT NULL,
                            encrypted_name TEXT,
                            revision_date TEXT NOT NULL,
                            last_synced_at INTEGER NOT NULL,
                            is_local_modified INTEGER NOT NULL DEFAULT 0,
                            local_monica_category_id INTEGER,
                            sort_order INTEGER NOT NULL DEFAULT 0,
                            FOREIGN KEY(vault_id) REFERENCES bitwarden_vaults(id) ON DELETE NO ACTION ON UPDATE NO ACTION
                        )
                    """.trimIndent())
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_bitwarden_folders_vault_id ON bitwarden_folders(vault_id)")
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bitwarden_folders_bitwarden_folder_id ON bitwarden_folders(bitwarden_folder_id)")
                    
                    // 3. 创建 bitwarden_conflict_backups 表
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS bitwarden_conflict_backups (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            vault_id INTEGER NOT NULL,
                            entry_id INTEGER,
                            bitwarden_cipher_id TEXT,
                            conflict_type TEXT NOT NULL,
                            local_data_json TEXT NOT NULL,
                            server_data_json TEXT,
                            local_revision_date TEXT,
                            server_revision_date TEXT,
                            entry_title TEXT NOT NULL,
                            description TEXT,
                            is_resolved INTEGER NOT NULL DEFAULT 0,
                            resolution TEXT,
                            resolved_at INTEGER,
                            created_at INTEGER NOT NULL,
                            FOREIGN KEY(vault_id) REFERENCES bitwarden_vaults(id) ON DELETE NO ACTION ON UPDATE NO ACTION
                        )
                    """.trimIndent())
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_bitwarden_conflict_backups_vault_id ON bitwarden_conflict_backups(vault_id)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_bitwarden_conflict_backups_entry_id ON bitwarden_conflict_backups(entry_id)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_bitwarden_conflict_backups_conflict_type ON bitwarden_conflict_backups(conflict_type)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_bitwarden_conflict_backups_created_at ON bitwarden_conflict_backups(created_at)")
                    
                    // 4. 创建 bitwarden_pending_operations 表
                    database.execSQL("""
                        CREATE TABLE IF NOT EXISTS bitwarden_pending_operations (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            vault_id INTEGER NOT NULL,
                            entry_id INTEGER,
                            bitwarden_cipher_id TEXT,
                            operation_type TEXT NOT NULL,
                            target_type TEXT NOT NULL,
                            payload_json TEXT NOT NULL,
                            status TEXT NOT NULL DEFAULT 'PENDING',
                            retry_count INTEGER NOT NULL DEFAULT 0,
                            max_retries INTEGER NOT NULL DEFAULT 3,
                            last_error TEXT,
                            last_attempt_at INTEGER,
                            created_at INTEGER NOT NULL,
                            completed_at INTEGER,
                            FOREIGN KEY(vault_id) REFERENCES bitwarden_vaults(id) ON DELETE NO ACTION ON UPDATE NO ACTION
                        )
                    """.trimIndent())
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_bitwarden_pending_operations_vault_id ON bitwarden_pending_operations(vault_id)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_bitwarden_pending_operations_status ON bitwarden_pending_operations(status)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_bitwarden_pending_operations_created_at ON bitwarden_pending_operations(created_at)")
                    
                    // 5. 为 password_entries 添加 Bitwarden 字段
                    database.execSQL("ALTER TABLE password_entries ADD COLUMN bitwarden_vault_id INTEGER DEFAULT NULL")
                    database.execSQL("ALTER TABLE password_entries ADD COLUMN bitwarden_cipher_id TEXT DEFAULT NULL")
                    database.execSQL("ALTER TABLE password_entries ADD COLUMN bitwarden_folder_id TEXT DEFAULT NULL")
                    database.execSQL("ALTER TABLE password_entries ADD COLUMN bitwarden_revision_date TEXT DEFAULT NULL")
                    database.execSQL("ALTER TABLE password_entries ADD COLUMN bitwarden_cipher_type INTEGER NOT NULL DEFAULT 1")
                    database.execSQL("ALTER TABLE password_entries ADD COLUMN bitwarden_local_modified INTEGER NOT NULL DEFAULT 0")
                    
                    android.util.Log.i("PasswordDatabase", "Migration 29→30 completed successfully")
                } catch (e: Exception) {
                    android.util.Log.e("PasswordDatabase", "Migration 29→30 failed: ${e.message}")
                    // 不抛出异常，让应用继续运行
                    // Room 会在后续操作中处理不一致性
                }
            }
        }
        
        /**
         * Migration 30 -> 31: 扩展 Bitwarden 同步支持多数据类型
         * 
         * 添加内容:
         * 1. 为 bitwarden_pending_operations 添加 item_type 字段
         * 2. 为 secure_items 添加 Bitwarden 关联字段
         * 3. 为 passkeys 添加 Bitwarden 关联字段
         * 4. 为 categories 添加 Bitwarden 文件夹关联字段
         */
        private val MIGRATION_30_31 = object : androidx.room.migration.Migration(30, 31) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    android.util.Log.i("PasswordDatabase", "Starting migration 30→31: Bitwarden multi-type sync support")
                    
                    // 1. 为 bitwarden_pending_operations 添加 item_type 字段
                    database.execSQL(
                        "ALTER TABLE bitwarden_pending_operations ADD COLUMN item_type TEXT NOT NULL DEFAULT 'PASSWORD'"
                    )
                    database.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_bitwarden_pending_operations_item_type ON bitwarden_pending_operations(item_type)"
                    )
                    
                    // 2. 为 secure_items 添加 Bitwarden 关联字段
                    database.execSQL(
                        "ALTER TABLE secure_items ADD COLUMN bitwarden_vault_id INTEGER DEFAULT NULL"
                    )
                    database.execSQL(
                        "ALTER TABLE secure_items ADD COLUMN bitwarden_cipher_id TEXT DEFAULT NULL"
                    )
                    database.execSQL(
                        "ALTER TABLE secure_items ADD COLUMN bitwarden_folder_id TEXT DEFAULT NULL"
                    )
                    database.execSQL(
                        "ALTER TABLE secure_items ADD COLUMN bitwarden_revision_date TEXT DEFAULT NULL"
                    )
                    database.execSQL(
                        "ALTER TABLE secure_items ADD COLUMN bitwarden_local_modified INTEGER NOT NULL DEFAULT 0"
                    )
                    database.execSQL(
                        "ALTER TABLE secure_items ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'NONE'"
                    )
                    
                    // 3. 为 passkeys 添加 Bitwarden 关联字段
                    database.execSQL(
                        "ALTER TABLE passkeys ADD COLUMN bitwarden_vault_id INTEGER DEFAULT NULL"
                    )
                    database.execSQL(
                        "ALTER TABLE passkeys ADD COLUMN bitwarden_cipher_id TEXT DEFAULT NULL"
                    )
                    database.execSQL(
                        "ALTER TABLE passkeys ADD COLUMN sync_status TEXT NOT NULL DEFAULT 'NONE'"
                    )
                    
                    // 4. 为 categories 添加 Bitwarden 文件夹关联字段
                    database.execSQL(
                        "ALTER TABLE categories ADD COLUMN bitwarden_vault_id INTEGER DEFAULT NULL"
                    )
                    database.execSQL(
                        "ALTER TABLE categories ADD COLUMN bitwarden_folder_id TEXT DEFAULT NULL"
                    )
                    database.execSQL(
                        "ALTER TABLE categories ADD COLUMN sync_item_types TEXT DEFAULT NULL"
                    )
                    
                    android.util.Log.i("PasswordDatabase", "Migration 30→31 completed successfully")
                } catch (e: Exception) {
                    android.util.Log.e("PasswordDatabase", "Migration 30→31 failed: ${e.message}")
                }
            }
        }

        /**
         * Migration 31 -> 32: 为 passkeys 添加绑定密码字段
         */
        private val MIGRATION_31_32 = object : androidx.room.migration.Migration(31, 32) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    android.util.Log.i("PasswordDatabase", "Starting migration 31→32: passkey bound password")
                    database.execSQL(
                        "ALTER TABLE passkeys ADD COLUMN bound_password_id INTEGER DEFAULT NULL"
                    )
                    android.util.Log.i("PasswordDatabase", "Migration 31→32 completed successfully")
                } catch (e: Exception) {
                    android.util.Log.e("PasswordDatabase", "Migration 31→32 failed: ${e.message}")
                }
            }
        }

        /**
         * Migration 32 -> 33: 为 password_entries 添加通行密钥绑定字段
         */
        private val MIGRATION_32_33 = object : androidx.room.migration.Migration(32, 33) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    android.util.Log.i("PasswordDatabase", "Starting migration 32→33: password passkey bindings")
                    database.execSQL(
                        "ALTER TABLE password_entries ADD COLUMN passkey_bindings TEXT NOT NULL DEFAULT ''"
                    )
                    android.util.Log.i("PasswordDatabase", "Migration 32→33 completed successfully")
                } catch (e: Exception) {
                    android.util.Log.e("PasswordDatabase", "Migration 32→33 failed: ${e.message}")
                }
            }
        }

        /**
         * Migration 33 -> 34: 添加 KeePass 组同步映射表
         */
        private val MIGRATION_33_34 = object : androidx.room.migration.Migration(33, 34) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    android.util.Log.i("PasswordDatabase", "Starting migration 33→34: keepass group sync configs")
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS keepass_group_sync_configs (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            keepassDatabaseId INTEGER NOT NULL,
                            groupPath TEXT NOT NULL,
                            groupUuid TEXT,
                            bitwarden_vault_id INTEGER DEFAULT NULL,
                            bitwarden_folder_id TEXT DEFAULT NULL,
                            sync_item_types TEXT DEFAULT NULL,
                            updated_at INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    database.execSQL(
                        "CREATE UNIQUE INDEX IF NOT EXISTS index_keepass_group_sync_configs_keepassDatabaseId_groupPath ON keepass_group_sync_configs(keepassDatabaseId, groupPath)"
                    )
                    android.util.Log.i("PasswordDatabase", "Migration 33→34 completed successfully")
                } catch (e: Exception) {
                    android.util.Log.e("PasswordDatabase", "Migration 33→34 failed: ${e.message}")
                }
            }
        }

        /**
         * Migration 34 -> 35: 添加 Bitwarden Send 本地缓存表
         */
        private val MIGRATION_34_35 = object : androidx.room.migration.Migration(34, 35) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    android.util.Log.i("PasswordDatabase", "Starting migration 34→35: bitwarden sends cache")
                    database.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS bitwarden_sends (
                            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            vault_id INTEGER NOT NULL,
                            bitwarden_send_id TEXT NOT NULL,
                            access_id TEXT NOT NULL,
                            key_base64 TEXT,
                            type INTEGER NOT NULL DEFAULT 0,
                            name TEXT NOT NULL,
                            notes TEXT NOT NULL DEFAULT '',
                            text_content TEXT,
                            is_text_hidden INTEGER NOT NULL DEFAULT 0,
                            file_name TEXT,
                            file_size TEXT,
                            access_count INTEGER NOT NULL DEFAULT 0,
                            max_access_count INTEGER,
                            has_password INTEGER NOT NULL DEFAULT 0,
                            disabled INTEGER NOT NULL DEFAULT 0,
                            hide_email INTEGER NOT NULL DEFAULT 0,
                            revision_date TEXT NOT NULL DEFAULT '',
                            expiration_date TEXT,
                            deletion_date TEXT,
                            share_url TEXT NOT NULL DEFAULT '',
                            last_synced_at INTEGER NOT NULL,
                            created_at INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL,
                            FOREIGN KEY(vault_id) REFERENCES bitwarden_vaults(id) ON DELETE NO ACTION ON UPDATE NO ACTION
                        )
                        """.trimIndent()
                    )
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_bitwarden_sends_vault_id ON bitwarden_sends(vault_id)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_bitwarden_sends_bitwarden_send_id ON bitwarden_sends(bitwarden_send_id)")
                    database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bitwarden_sends_vault_id_bitwarden_send_id ON bitwarden_sends(vault_id, bitwarden_send_id)")
                    database.execSQL("CREATE INDEX IF NOT EXISTS index_bitwarden_sends_updated_at ON bitwarden_sends(updated_at)")
                    android.util.Log.i("PasswordDatabase", "Migration 34→35 completed successfully")
                } catch (e: Exception) {
                    android.util.Log.e("PasswordDatabase", "Migration 34→35 failed: ${e.message}")
                }
            }
        }

        /**
         * Migration 35 -> 36: 为 passkeys 添加 category_id 字段，接入统一文件夹体系
         */
        private val MIGRATION_35_36 = object : androidx.room.migration.Migration(35, 36) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    android.util.Log.i("PasswordDatabase", "Starting migration 35→36: passkeys category_id")
                    database.execSQL(
                        "ALTER TABLE passkeys ADD COLUMN category_id INTEGER DEFAULT NULL"
                    )
                    android.util.Log.i("PasswordDatabase", "Migration 35→36 completed successfully")
                } catch (e: Exception) {
                    android.util.Log.e("PasswordDatabase", "Migration 35→36 failed: ${e.message}")
                }
            }
        }

        /**
         * Migration 36 -> 37:
         * 1. 为 secure_items 添加 keepass_database_id（统一目标存储）
         * 2. 为 passkeys 添加 keepass_database_id（通行密钥目标存储）
         */
        private val MIGRATION_36_37 = object : androidx.room.migration.Migration(36, 37) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    android.util.Log.i("PasswordDatabase", "Starting migration 36→37: keepass_database_id for secure_items/passkeys")
                    database.execSQL(
                        "ALTER TABLE secure_items ADD COLUMN keepass_database_id INTEGER DEFAULT NULL"
                    )
                    database.execSQL(
                        "ALTER TABLE passkeys ADD COLUMN keepass_database_id INTEGER DEFAULT NULL"
                    )
                    android.util.Log.i("PasswordDatabase", "Migration 36→37 completed successfully")
                } catch (e: Exception) {
                    android.util.Log.e("PasswordDatabase", "Migration 36→37 failed: ${e.message}")
                }
            }
        }

        /**
         * Migration 37 -> 38:
         * 1. 为 password_entries 添加 keepassGroupPath（支持 KeePass 分组精确过滤）
         * 2. 为 secure_items 添加 keepass_group_path（支持笔记/验证器分组精确过滤）
         */
        private val MIGRATION_37_38 = object : androidx.room.migration.Migration(37, 38) {
            override fun migrate(database: androidx.sqlite.db.SupportSQLiteDatabase) {
                try {
                    android.util.Log.i("PasswordDatabase", "Starting migration 37→38: keepass group path")
                    database.execSQL(
                        "ALTER TABLE password_entries ADD COLUMN keepassGroupPath TEXT DEFAULT NULL"
                    )
                    database.execSQL(
                        "ALTER TABLE secure_items ADD COLUMN keepass_group_path TEXT DEFAULT NULL"
                    )
                    android.util.Log.i("PasswordDatabase", "Migration 37→38 completed successfully")
                } catch (e: Exception) {
                    android.util.Log.e("PasswordDatabase", "Migration 37→38 failed: ${e.message}")
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
                        MIGRATION_26_27,  // 添加自定义字段表
                        MIGRATION_27_28,  // 添加 Passkey 通行密钥表
                        MIGRATION_28_29,  // 为 secure_items 添加 categoryId 字段
                        MIGRATION_29_30,  // Bitwarden 集成
                        MIGRATION_30_31,  // Bitwarden 多类型同步支持
                        MIGRATION_31_32,  // Passkey 绑定密码
                        MIGRATION_32_33,  // Password 绑定通行密钥元数据
                        MIGRATION_33_34,  // KeePass 组同步配置
                        MIGRATION_34_35,  // Bitwarden Send 本地缓存
                        MIGRATION_35_36,  // Passkey 分类字段（统一文件夹）
                        MIGRATION_36_37,  // secure_items/passkeys KeePass 归属字段
                        MIGRATION_37_38   // keepass 分组路径字段
                    )
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}



