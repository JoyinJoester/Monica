using Microsoft.Data.Sqlite;

namespace Monica.Data;

public interface IDatabaseMigrator
{
    Task MigrateAsync(CancellationToken cancellationToken = default);
}

public sealed class DatabaseMigrator(ISqliteConnectionFactory connectionFactory) : IDatabaseMigrator
{
    public const int CurrentSchemaVersion = 68;

    public async Task MigrateAsync(CancellationToken cancellationToken = default)
    {
        await using var connection = connectionFactory.CreateConnection();
        await connection.OpenAsync(cancellationToken);

        await ExecuteAsync(connection, "PRAGMA journal_mode=WAL;", cancellationToken);
        await ExecuteAsync(connection, "PRAGMA foreign_keys=ON;", cancellationToken);

        var version = await GetUserVersionAsync(connection, cancellationToken);
        if (version > CurrentSchemaVersion)
        {
            throw new InvalidOperationException($"Database schema {version} is newer than this Monica build ({CurrentSchemaVersion}).");
        }

        await CreateCurrentSchemaAsync(connection, cancellationToken);
        await ExecuteAsync(connection, $"PRAGMA user_version={CurrentSchemaVersion};", cancellationToken);
    }

    private static async Task<int> GetUserVersionAsync(SqliteConnection connection, CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.CommandText = "PRAGMA user_version;";
        var result = await command.ExecuteScalarAsync(cancellationToken);
        return Convert.ToInt32(result);
    }

    private static async Task CreateCurrentSchemaAsync(SqliteConnection connection, CancellationToken cancellationToken)
    {
        foreach (var sql in SchemaStatements)
        {
            await ExecuteAsync(connection, sql, cancellationToken);
        }
    }

    private static async Task ExecuteAsync(SqliteConnection connection, string sql, CancellationToken cancellationToken)
    {
        await using var command = connection.CreateCommand();
        command.CommandText = sql;
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static readonly string[] SchemaStatements =
    [
        """
        CREATE TABLE IF NOT EXISTS categories (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            name TEXT NOT NULL,
            sort_order INTEGER NOT NULL DEFAULT 0,
            mdbx_database_id INTEGER DEFAULT NULL
        );
        """,
        """
        CREATE TABLE IF NOT EXISTS password_entries (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            title TEXT NOT NULL,
            website TEXT NOT NULL DEFAULT '',
            username TEXT NOT NULL DEFAULT '',
            password TEXT NOT NULL DEFAULT '',
            notes TEXT NOT NULL DEFAULT '',
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            is_favorite INTEGER NOT NULL DEFAULT 0,
            sort_order INTEGER NOT NULL DEFAULT 0,
            is_group_cover INTEGER NOT NULL DEFAULT 0,
            app_package_name TEXT NOT NULL DEFAULT '',
            app_name TEXT NOT NULL DEFAULT '',
            email TEXT NOT NULL DEFAULT '',
            phone TEXT NOT NULL DEFAULT '',
            address_line TEXT NOT NULL DEFAULT '',
            city TEXT NOT NULL DEFAULT '',
            state TEXT NOT NULL DEFAULT '',
            zip_code TEXT NOT NULL DEFAULT '',
            country TEXT NOT NULL DEFAULT '',
            credit_card_number TEXT NOT NULL DEFAULT '',
            credit_card_holder TEXT NOT NULL DEFAULT '',
            credit_card_expiry TEXT NOT NULL DEFAULT '',
            credit_card_cvv TEXT NOT NULL DEFAULT '',
            category_id INTEGER DEFAULT NULL,
            bound_note_id INTEGER DEFAULT NULL,
            keepass_database_id INTEGER DEFAULT NULL,
            keepass_group_path TEXT DEFAULT NULL,
            keepass_entry_uuid TEXT DEFAULT NULL,
            keepass_group_uuid TEXT DEFAULT NULL,
            mdbx_database_id INTEGER DEFAULT NULL,
            mdbx_folder_id TEXT DEFAULT NULL,
            authenticator_key TEXT NOT NULL DEFAULT '',
            passkey_bindings TEXT NOT NULL DEFAULT '',
            ssh_key_data TEXT NOT NULL DEFAULT '',
            login_type TEXT NOT NULL DEFAULT 'PASSWORD',
            sso_provider TEXT NOT NULL DEFAULT '',
            sso_ref_entry_id INTEGER DEFAULT NULL,
            wifi_metadata TEXT NOT NULL DEFAULT '',
            custom_icon_type TEXT NOT NULL DEFAULT 'NONE',
            custom_icon_value TEXT DEFAULT NULL,
            custom_icon_updated_at INTEGER NOT NULL DEFAULT 0,
            is_deleted INTEGER NOT NULL DEFAULT 0,
            deleted_at INTEGER DEFAULT NULL,
            is_archived INTEGER NOT NULL DEFAULT 0,
            archived_at INTEGER DEFAULT NULL,
            replica_group_id TEXT DEFAULT NULL,
            bitwarden_vault_id INTEGER DEFAULT NULL,
            bitwarden_cipher_id TEXT DEFAULT NULL,
            bitwarden_folder_id TEXT DEFAULT NULL,
            bitwarden_revision_date TEXT DEFAULT NULL,
            bitwarden_cipher_type INTEGER NOT NULL DEFAULT 1,
            bitwarden_local_modified INTEGER NOT NULL DEFAULT 0,
            FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE SET NULL
        );
        """,
        "CREATE INDEX IF NOT EXISTS index_password_entries_is_deleted ON password_entries(is_deleted);",
        "CREATE INDEX IF NOT EXISTS index_password_entries_is_archived ON password_entries(is_archived);",
        "CREATE INDEX IF NOT EXISTS index_password_entries_replica_group_id ON password_entries(replica_group_id);",
        "CREATE INDEX IF NOT EXISTS index_password_entries_keepass_entry_uuid ON password_entries(keepass_entry_uuid);",
        "CREATE INDEX IF NOT EXISTS index_password_entries_mdbx_database_id ON password_entries(mdbx_database_id);",
        "CREATE INDEX IF NOT EXISTS index_password_entries_mdbx_database_folder ON password_entries(mdbx_database_id, mdbx_folder_id);",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_password_entries_bitwarden_vault_cipher_unique ON password_entries(bitwarden_vault_id, bitwarden_cipher_id) WHERE bitwarden_vault_id IS NOT NULL AND bitwarden_cipher_id IS NOT NULL;",
        """
        CREATE TABLE IF NOT EXISTS secure_items (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            item_type TEXT NOT NULL,
            title TEXT NOT NULL,
            notes TEXT NOT NULL DEFAULT '',
            is_favorite INTEGER NOT NULL DEFAULT 0,
            sort_order INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL,
            item_data TEXT NOT NULL DEFAULT '{}',
            image_paths TEXT NOT NULL DEFAULT '[]',
            category_id INTEGER DEFAULT NULL,
            keepass_database_id INTEGER DEFAULT NULL,
            keepass_group_path TEXT DEFAULT NULL,
            keepass_entry_uuid TEXT DEFAULT NULL,
            keepass_group_uuid TEXT DEFAULT NULL,
            mdbx_database_id INTEGER DEFAULT NULL,
            mdbx_folder_id TEXT DEFAULT NULL,
            is_deleted INTEGER NOT NULL DEFAULT 0,
            deleted_at INTEGER DEFAULT NULL,
            replica_group_id TEXT DEFAULT NULL,
            bitwarden_vault_id INTEGER DEFAULT NULL,
            bitwarden_cipher_id TEXT DEFAULT NULL,
            bitwarden_folder_id TEXT DEFAULT NULL,
            bitwarden_revision_date TEXT DEFAULT NULL,
            bitwarden_local_modified INTEGER NOT NULL DEFAULT 0,
            sync_status TEXT NOT NULL DEFAULT 'NONE',
            FOREIGN KEY(category_id) REFERENCES categories(id) ON DELETE SET NULL
        );
        """,
        "CREATE INDEX IF NOT EXISTS index_secure_items_is_deleted ON secure_items(is_deleted);",
        "CREATE INDEX IF NOT EXISTS index_secure_items_replica_group_id ON secure_items(replica_group_id);",
        "CREATE INDEX IF NOT EXISTS index_secure_items_keepass_entry_uuid ON secure_items(keepass_entry_uuid);",
        "CREATE INDEX IF NOT EXISTS index_secure_items_mdbx_database_id ON secure_items(mdbx_database_id);",
        "CREATE INDEX IF NOT EXISTS index_secure_items_mdbx_database_folder ON secure_items(mdbx_database_id, mdbx_folder_id);",
        "CREATE UNIQUE INDEX IF NOT EXISTS index_secure_items_bitwarden_vault_cipher_unique ON secure_items(bitwarden_vault_id, bitwarden_cipher_id) WHERE bitwarden_vault_id IS NOT NULL AND bitwarden_cipher_id IS NOT NULL;",
        """
        CREATE TABLE IF NOT EXISTS operation_logs (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            item_type TEXT NOT NULL,
            item_id INTEGER NOT NULL,
            item_title TEXT NOT NULL,
            operation_type TEXT NOT NULL,
            changes_json TEXT NOT NULL DEFAULT '',
            device_id TEXT NOT NULL DEFAULT '',
            device_name TEXT NOT NULL DEFAULT '',
            timestamp INTEGER NOT NULL,
            is_reverted INTEGER NOT NULL DEFAULT 0
        );
        """,
        "CREATE INDEX IF NOT EXISTS index_operation_logs_timestamp ON operation_logs(timestamp);",
        "CREATE INDEX IF NOT EXISTS index_operation_logs_item_type ON operation_logs(item_type);",
        """
        CREATE TABLE IF NOT EXISTS passkeys (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            credential_id TEXT NOT NULL,
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
            icon_url TEXT DEFAULT NULL,
            is_discoverable INTEGER NOT NULL DEFAULT 1,
            is_user_verification_required INTEGER NOT NULL DEFAULT 1,
            transports TEXT NOT NULL DEFAULT 'internal',
            aaguid TEXT NOT NULL DEFAULT '',
            sign_count INTEGER NOT NULL DEFAULT 0,
            is_backed_up INTEGER NOT NULL DEFAULT 0,
            notes TEXT NOT NULL DEFAULT '',
            bound_password_id INTEGER DEFAULT NULL,
            category_id INTEGER DEFAULT NULL,
            keepass_database_id INTEGER DEFAULT NULL,
            keepass_group_path TEXT DEFAULT NULL,
            mdbx_database_id INTEGER DEFAULT NULL,
            mdbx_folder_id TEXT DEFAULT NULL,
            bitwarden_vault_id INTEGER DEFAULT NULL,
            bitwarden_folder_id TEXT DEFAULT NULL,
            bitwarden_cipher_id TEXT DEFAULT NULL,
            sync_status TEXT NOT NULL DEFAULT 'NONE',
            passkey_mode TEXT NOT NULL DEFAULT 'LEGACY'
        );
        """,
        "CREATE INDEX IF NOT EXISTS index_passkeys_credential_id ON passkeys(credential_id);",
        "CREATE INDEX IF NOT EXISTS index_passkeys_rp_id ON passkeys(rp_id);",
        "CREATE INDEX IF NOT EXISTS index_passkeys_user_name ON passkeys(user_name);",
        "CREATE INDEX IF NOT EXISTS index_passkeys_mdbx_database_id ON passkeys(mdbx_database_id);",
        "CREATE INDEX IF NOT EXISTS index_passkeys_mdbx_database_folder ON passkeys(mdbx_database_id, mdbx_folder_id);",
        """
        CREATE TABLE IF NOT EXISTS local_mdbx_databases (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            name TEXT NOT NULL,
            file_path TEXT NOT NULL,
            storage_location TEXT NOT NULL,
            source_type TEXT NOT NULL,
            source_id INTEGER DEFAULT NULL,
            tiga_mode TEXT NOT NULL DEFAULT 'MULTI',
            encrypted_password TEXT DEFAULT NULL,
            unlock_method TEXT NOT NULL DEFAULT 'password',
            kdf_profile TEXT NOT NULL DEFAULT 'argon2id',
            key_file_name TEXT DEFAULT NULL,
            key_file_uri TEXT DEFAULT NULL,
            key_file_fingerprint TEXT DEFAULT NULL,
            description TEXT DEFAULT NULL,
            created_at INTEGER NOT NULL,
            last_accessed_at INTEGER NOT NULL,
            last_synced_at INTEGER DEFAULT NULL,
            is_default INTEGER NOT NULL DEFAULT 0,
            project_count INTEGER NOT NULL DEFAULT 0,
            sort_order INTEGER NOT NULL DEFAULT 0,
            working_copy_path TEXT DEFAULT NULL,
            cache_copy_path TEXT DEFAULT NULL,
            is_offline_available INTEGER NOT NULL DEFAULT 0,
            last_sync_status TEXT NOT NULL DEFAULT 'LOCAL_ONLY',
            last_sync_error TEXT DEFAULT NULL
        );
        """,
        "CREATE INDEX IF NOT EXISTS index_local_mdbx_databases_storage_location ON local_mdbx_databases(storage_location);",
        "CREATE INDEX IF NOT EXISTS index_local_mdbx_databases_source_type ON local_mdbx_databases(source_type);",
        "CREATE INDEX IF NOT EXISTS index_local_mdbx_databases_source_id ON local_mdbx_databases(source_id);",
        """
        CREATE TABLE IF NOT EXISTS mdbx_remote_sources (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            display_name TEXT NOT NULL,
            remote_path TEXT NOT NULL,
            remote_parent_path TEXT DEFAULT NULL,
            base_url TEXT DEFAULT NULL,
            username_encrypted TEXT DEFAULT NULL,
            password_encrypted TEXT DEFAULT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        );
        """,
        "CREATE INDEX IF NOT EXISTS index_mdbx_remote_sources_display_name ON mdbx_remote_sources(display_name);",
        """
        CREATE TABLE IF NOT EXISTS bitwarden_vaults (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            email TEXT NOT NULL,
            canonical_email TEXT NOT NULL DEFAULT '',
            user_id TEXT DEFAULT NULL,
            account_key TEXT NOT NULL DEFAULT '',
            display_name TEXT DEFAULT NULL,
            server_url TEXT NOT NULL DEFAULT 'https://vault.bitwarden.com',
            identity_url TEXT NOT NULL DEFAULT 'https://identity.bitwarden.com',
            api_url TEXT NOT NULL DEFAULT 'https://api.bitwarden.com',
            encrypted_access_token TEXT DEFAULT NULL,
            encrypted_refresh_token TEXT DEFAULT NULL,
            access_token_expires_at INTEGER DEFAULT NULL,
            encrypted_master_key TEXT DEFAULT NULL,
            encrypted_enc_key TEXT DEFAULT NULL,
            encrypted_mac_key TEXT DEFAULT NULL,
            kdf_type INTEGER NOT NULL DEFAULT 0,
            kdf_iterations INTEGER NOT NULL DEFAULT 600000,
            kdf_memory INTEGER DEFAULT NULL,
            kdf_parallelism INTEGER DEFAULT NULL,
            last_sync_at INTEGER DEFAULT NULL,
            revision_date TEXT DEFAULT NULL,
            is_default INTEGER NOT NULL DEFAULT 0,
            is_locked INTEGER NOT NULL DEFAULT 1,
            is_connected INTEGER NOT NULL DEFAULT 0,
            sync_enabled INTEGER NOT NULL DEFAULT 1,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        );
        """,
        "CREATE UNIQUE INDEX IF NOT EXISTS index_bitwarden_vaults_account_key ON bitwarden_vaults(account_key);",
        "CREATE INDEX IF NOT EXISTS index_bitwarden_vaults_canonical_email ON bitwarden_vaults(canonical_email);",
        """
        CREATE TABLE IF NOT EXISTS attachments (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            owner_type TEXT NOT NULL,
            owner_id INTEGER NOT NULL,
            file_name TEXT NOT NULL,
            content_type TEXT NOT NULL DEFAULT '',
            storage_path TEXT NOT NULL,
            size_bytes INTEGER NOT NULL DEFAULT 0,
            created_at INTEGER NOT NULL,
            bitwarden_vault_id INTEGER DEFAULT NULL,
            keepass_binary_ref TEXT DEFAULT NULL
        );
        """,
        "CREATE INDEX IF NOT EXISTS index_attachments_owner ON attachments(owner_type, owner_id);",
        "CREATE INDEX IF NOT EXISTS index_attachments_bw_id ON attachments(bitwarden_vault_id);",
        "CREATE INDEX IF NOT EXISTS index_attachments_kp_ref ON attachments(keepass_binary_ref);",
        """
        CREATE TABLE IF NOT EXISTS vault_credentials (
            id INTEGER PRIMARY KEY CHECK (id = 1),
            password_hash TEXT NOT NULL,
            salt TEXT NOT NULL,
            kdf TEXT NOT NULL,
            iterations INTEGER NOT NULL,
            memory_kib INTEGER NOT NULL,
            parallelism INTEGER NOT NULL,
            created_at INTEGER NOT NULL,
            updated_at INTEGER NOT NULL
        );
        """
    ];
}
