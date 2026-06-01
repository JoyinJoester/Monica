using Dapper;
using Monica.Core.Models;

namespace Monica.Data.Repositories;

public interface IMonicaRepository
{
    Task<IReadOnlyList<PasswordEntry>> GetPasswordsAsync(bool includeDeleted = false, CancellationToken cancellationToken = default);
    Task<long> SavePasswordAsync(PasswordEntry entry, CancellationToken cancellationToken = default);
    Task SoftDeletePasswordAsync(long id, CancellationToken cancellationToken = default);
    Task RestorePasswordAsync(long id, CancellationToken cancellationToken = default);
    Task DeletePasswordPermanentlyAsync(long id, CancellationToken cancellationToken = default);
    Task<IReadOnlyList<CustomField>> GetCustomFieldsAsync(long entryId, CancellationToken cancellationToken = default);
    Task<IReadOnlyDictionary<long, IReadOnlyList<CustomField>>> GetCustomFieldsByEntryIdsAsync(IReadOnlyList<long> entryIds, CancellationToken cancellationToken = default);
    Task ReplaceCustomFieldsAsync(long entryId, IReadOnlyList<CustomField> fields, CancellationToken cancellationToken = default);
    Task<IReadOnlyList<long>> SearchEntryIdsByCustomFieldContentAsync(string query, CancellationToken cancellationToken = default);
    Task<IReadOnlyList<SecureItem>> GetSecureItemsAsync(VaultItemType? itemType = null, bool includeDeleted = false, CancellationToken cancellationToken = default);
    Task<IReadOnlyList<SecureItem>> GetSecureItemsByBoundPasswordIdAsync(long passwordId, bool includeDeleted = false, CancellationToken cancellationToken = default);
    Task<long> SaveSecureItemAsync(SecureItem item, CancellationToken cancellationToken = default);
    Task SoftDeleteSecureItemAsync(long id, CancellationToken cancellationToken = default);
    Task<IReadOnlyList<Category>> GetCategoriesAsync(CancellationToken cancellationToken = default);
    Task<long> SaveCategoryAsync(Category category, CancellationToken cancellationToken = default);
    Task<IReadOnlyList<LocalMdbxDatabase>> GetMdbxDatabasesAsync(CancellationToken cancellationToken = default);
    Task<long> SaveMdbxDatabaseAsync(LocalMdbxDatabase database, CancellationToken cancellationToken = default);
    Task LogAsync(OperationLog log, CancellationToken cancellationToken = default);
}

public sealed class MonicaRepository(ISqliteConnectionFactory connectionFactory, IDatabaseMigrator migrator) : IMonicaRepository
{
    public async Task<IReadOnlyList<PasswordEntry>> GetPasswordsAsync(bool includeDeleted = false, CancellationToken cancellationToken = default)
    {
        await migrator.MigrateAsync(cancellationToken);
        await using var connection = connectionFactory.CreateConnection();
        var rows = await connection.QueryAsync<PasswordEntryRow>(
            """
            SELECT * FROM password_entries
            WHERE (@IncludeDeleted = 1 OR is_deleted = 0)
            ORDER BY is_favorite DESC, sort_order ASC, updated_at DESC
            """,
            new { IncludeDeleted = includeDeleted ? 1 : 0 });

        return rows.Select(ToModel).ToList();
    }

    public async Task<long> SavePasswordAsync(PasswordEntry entry, CancellationToken cancellationToken = default)
    {
        await migrator.MigrateAsync(cancellationToken);
        entry.UpdatedAt = DateTimeOffset.UtcNow;
        if (entry.CreatedAt == default)
        {
            entry.CreatedAt = entry.UpdatedAt;
        }

        await using var connection = connectionFactory.CreateConnection();
        if (entry.Id == 0)
        {
            const string sql =
                """
                INSERT INTO password_entries (
                    title, website, username, password, notes, created_at, updated_at, is_favorite, sort_order, is_group_cover,
                    app_package_name, app_name, email, phone, address_line, city, state, zip_code, country,
                    credit_card_number, credit_card_holder, credit_card_expiry, credit_card_cvv, category_id, bound_note_id,
                    keepass_database_id, keepass_group_path, keepass_entry_uuid, keepass_group_uuid, mdbx_database_id, mdbx_folder_id,
                    authenticator_key, passkey_bindings, ssh_key_data, login_type, sso_provider, sso_ref_entry_id, wifi_metadata,
                    custom_icon_type, custom_icon_value, custom_icon_updated_at, is_deleted, deleted_at, is_archived, archived_at,
                    replica_group_id, bitwarden_vault_id, bitwarden_cipher_id, bitwarden_folder_id, bitwarden_revision_date,
                    bitwarden_cipher_type, bitwarden_local_modified)
                VALUES (
                    @Title, @Website, @Username, @Password, @Notes, @CreatedAt, @UpdatedAt, @IsFavorite, @SortOrder, @IsGroupCover,
                    @AppPackageName, @AppName, @Email, @Phone, @AddressLine, @City, @State, @ZipCode, @Country,
                    @CreditCardNumber, @CreditCardHolder, @CreditCardExpiry, @CreditCardCvv, @CategoryId, @BoundNoteId,
                    @KeepassDatabaseId, @KeepassGroupPath, @KeepassEntryUuid, @KeepassGroupUuid, @MdbxDatabaseId, @MdbxFolderId,
                    @AuthenticatorKey, @PasskeyBindings, @SshKeyData, @LoginType, @SsoProvider, @SsoRefEntryId, @WifiMetadata,
                    @CustomIconType, @CustomIconValue, @CustomIconUpdatedAt, @IsDeleted, @DeletedAt, @IsArchived, @ArchivedAt,
                    @ReplicaGroupId, @BitwardenVaultId, @BitwardenCipherId, @BitwardenFolderId, @BitwardenRevisionDate,
                    @BitwardenCipherType, @BitwardenLocalModified);
                SELECT last_insert_rowid();
                """;
            entry.Id = await connection.ExecuteScalarAsync<long>(sql, ToRow(entry));
        }
        else
        {
            const string sql =
                """
                UPDATE password_entries SET
                    title=@Title, website=@Website, username=@Username, password=@Password, notes=@Notes,
                    updated_at=@UpdatedAt, is_favorite=@IsFavorite, sort_order=@SortOrder, is_group_cover=@IsGroupCover,
                    app_package_name=@AppPackageName, app_name=@AppName, email=@Email, phone=@Phone, address_line=@AddressLine,
                    city=@City, state=@State, zip_code=@ZipCode, country=@Country, credit_card_number=@CreditCardNumber,
                    credit_card_holder=@CreditCardHolder, credit_card_expiry=@CreditCardExpiry, credit_card_cvv=@CreditCardCvv,
                    category_id=@CategoryId, bound_note_id=@BoundNoteId, keepass_database_id=@KeepassDatabaseId,
                    keepass_group_path=@KeepassGroupPath, keepass_entry_uuid=@KeepassEntryUuid, keepass_group_uuid=@KeepassGroupUuid,
                    mdbx_database_id=@MdbxDatabaseId, mdbx_folder_id=@MdbxFolderId, authenticator_key=@AuthenticatorKey,
                    passkey_bindings=@PasskeyBindings, ssh_key_data=@SshKeyData, login_type=@LoginType, sso_provider=@SsoProvider,
                    sso_ref_entry_id=@SsoRefEntryId, wifi_metadata=@WifiMetadata, custom_icon_type=@CustomIconType,
                    custom_icon_value=@CustomIconValue, custom_icon_updated_at=@CustomIconUpdatedAt, is_deleted=@IsDeleted,
                    deleted_at=@DeletedAt, is_archived=@IsArchived, archived_at=@ArchivedAt, replica_group_id=@ReplicaGroupId,
                    bitwarden_vault_id=@BitwardenVaultId, bitwarden_cipher_id=@BitwardenCipherId, bitwarden_folder_id=@BitwardenFolderId,
                    bitwarden_revision_date=@BitwardenRevisionDate, bitwarden_cipher_type=@BitwardenCipherType,
                    bitwarden_local_modified=@BitwardenLocalModified
                WHERE id=@Id;
                """;
            await connection.ExecuteAsync(sql, ToRow(entry));
        }

        return entry.Id;
    }

    public async Task SoftDeletePasswordAsync(long id, CancellationToken cancellationToken = default)
    {
        await migrator.MigrateAsync(cancellationToken);
        await using var connection = connectionFactory.CreateConnection();
        var deletedAt = ToUnixMilliseconds(DateTimeOffset.UtcNow);
        await connection.ExecuteAsync(
            "UPDATE password_entries SET is_deleted = 1, deleted_at = @DeletedAt, updated_at = @DeletedAt WHERE id = @Id",
            new { Id = id, DeletedAt = deletedAt });
        await connection.ExecuteAsync(
            "UPDATE secure_items SET is_deleted = 1, deleted_at = @DeletedAt, updated_at = @DeletedAt WHERE bound_password_id = @Id AND item_type = 'TOTP'",
            new { Id = id, DeletedAt = deletedAt });
    }

    public async Task RestorePasswordAsync(long id, CancellationToken cancellationToken = default)
    {
        await migrator.MigrateAsync(cancellationToken);
        await using var connection = connectionFactory.CreateConnection();
        var updatedAt = ToUnixMilliseconds(DateTimeOffset.UtcNow);
        await connection.ExecuteAsync(
            "UPDATE password_entries SET is_deleted = 0, deleted_at = NULL, updated_at = @UpdatedAt WHERE id = @Id",
            new { Id = id, UpdatedAt = updatedAt });
        await connection.ExecuteAsync(
            "UPDATE secure_items SET is_deleted = 0, deleted_at = NULL, updated_at = @UpdatedAt WHERE bound_password_id = @Id AND item_type = 'TOTP'",
            new { Id = id, UpdatedAt = updatedAt });
    }

    public async Task DeletePasswordPermanentlyAsync(long id, CancellationToken cancellationToken = default)
    {
        await migrator.MigrateAsync(cancellationToken);
        await using var connection = connectionFactory.CreateConnection();
        await connection.OpenAsync(cancellationToken);
        await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
        await connection.ExecuteAsync("DELETE FROM custom_fields WHERE entry_id = @Id", new { Id = id }, transaction);
        await connection.ExecuteAsync("DELETE FROM secure_items WHERE bound_password_id = @Id AND item_type = 'TOTP'", new { Id = id }, transaction);
        await connection.ExecuteAsync("DELETE FROM password_entries WHERE id = @Id", new { Id = id }, transaction);
        await transaction.CommitAsync(cancellationToken);
    }

    public async Task<IReadOnlyList<CustomField>> GetCustomFieldsAsync(long entryId, CancellationToken cancellationToken = default)
    {
        await migrator.MigrateAsync(cancellationToken);
        await using var connection = connectionFactory.CreateConnection();
        var rows = await connection.QueryAsync<CustomFieldRow>(
            """
            SELECT id, entry_id, title, value, is_protected, sort_order
            FROM custom_fields
            WHERE entry_id = @EntryId
            ORDER BY sort_order ASC, id ASC
            """,
            new { EntryId = entryId });

        return rows.Select(ToModel).ToList();
    }

    public async Task<IReadOnlyDictionary<long, IReadOnlyList<CustomField>>> GetCustomFieldsByEntryIdsAsync(IReadOnlyList<long> entryIds, CancellationToken cancellationToken = default)
    {
        await migrator.MigrateAsync(cancellationToken);
        var ids = entryIds.Where(id => id > 0).Distinct().ToArray();
        if (ids.Length == 0)
        {
            return new Dictionary<long, IReadOnlyList<CustomField>>();
        }

        await using var connection = connectionFactory.CreateConnection();
        var rows = await connection.QueryAsync<CustomFieldRow>(
            """
            SELECT id, entry_id, title, value, is_protected, sort_order
            FROM custom_fields
            WHERE entry_id IN @EntryIds
            ORDER BY entry_id ASC, sort_order ASC, id ASC
            """,
            new { EntryIds = ids });

        return rows
            .Select(ToModel)
            .GroupBy(field => field.EntryId)
            .ToDictionary(group => group.Key, group => (IReadOnlyList<CustomField>)group.ToList());
    }

    public async Task ReplaceCustomFieldsAsync(long entryId, IReadOnlyList<CustomField> fields, CancellationToken cancellationToken = default)
    {
        await migrator.MigrateAsync(cancellationToken);
        await using var connection = connectionFactory.CreateConnection();
        await connection.OpenAsync(cancellationToken);
        await using var transaction = await connection.BeginTransactionAsync(cancellationToken);
        await connection.ExecuteAsync("DELETE FROM custom_fields WHERE entry_id = @EntryId", new { EntryId = entryId }, transaction);

        var rows = fields
            .Where(field => !string.IsNullOrWhiteSpace(field.Title) && !string.IsNullOrWhiteSpace(field.Value))
            .Select((field, index) => new
            {
                EntryId = entryId,
                Title = field.Title.Trim(),
                Value = field.Value.Trim(),
                IsProtected = field.IsProtected ? 1 : 0,
                SortOrder = index
            })
            .ToArray();

        if (rows.Length > 0)
        {
            await connection.ExecuteAsync(
                """
                INSERT INTO custom_fields(entry_id, title, value, is_protected, sort_order)
                VALUES(@EntryId, @Title, @Value, @IsProtected, @SortOrder)
                """,
                rows,
                transaction);
        }

        await connection.ExecuteAsync(
            "UPDATE password_entries SET updated_at = @UpdatedAt WHERE id = @EntryId",
            new { EntryId = entryId, UpdatedAt = ToUnixMilliseconds(DateTimeOffset.UtcNow) },
            transaction);
        await transaction.CommitAsync(cancellationToken);
    }

    public async Task<IReadOnlyList<long>> SearchEntryIdsByCustomFieldContentAsync(string query, CancellationToken cancellationToken = default)
    {
        await migrator.MigrateAsync(cancellationToken);
        if (string.IsNullOrWhiteSpace(query))
        {
            return [];
        }

        await using var connection = connectionFactory.CreateConnection();
        var ids = await connection.QueryAsync<long>(
            """
            SELECT DISTINCT entry_id
            FROM custom_fields
            WHERE title LIKE @Query OR value LIKE @Query
            ORDER BY entry_id ASC
            """,
            new { Query = $"%{query.Trim()}%" });

        return ids.ToList();
    }

    public async Task<IReadOnlyList<SecureItem>> GetSecureItemsAsync(VaultItemType? itemType = null, bool includeDeleted = false, CancellationToken cancellationToken = default)
    {
        await migrator.MigrateAsync(cancellationToken);
        await using var connection = connectionFactory.CreateConnection();
        var rows = await connection.QueryAsync<SecureItemRow>(
            """
            SELECT * FROM secure_items
            WHERE (@IncludeDeleted = 1 OR is_deleted = 0)
              AND (@ItemType IS NULL OR item_type = @ItemType)
            ORDER BY is_favorite DESC, sort_order ASC, updated_at DESC
            """,
            new { IncludeDeleted = includeDeleted ? 1 : 0, ItemType = itemType?.ToString().ToUpperInvariant() });
        return rows.Select(ToModel).ToList();
    }

    public async Task<IReadOnlyList<SecureItem>> GetSecureItemsByBoundPasswordIdAsync(long passwordId, bool includeDeleted = false, CancellationToken cancellationToken = default)
    {
        await migrator.MigrateAsync(cancellationToken);
        await using var connection = connectionFactory.CreateConnection();
        var rows = await connection.QueryAsync<SecureItemRow>(
            """
            SELECT * FROM secure_items
            WHERE bound_password_id = @PasswordId
              AND item_type = 'TOTP'
              AND (@IncludeDeleted = 1 OR is_deleted = 0)
            ORDER BY updated_at DESC, id DESC
            """,
            new { PasswordId = passwordId, IncludeDeleted = includeDeleted ? 1 : 0 });
        return rows.Select(ToModel).ToList();
    }

    public async Task<long> SaveSecureItemAsync(SecureItem item, CancellationToken cancellationToken = default)
    {
        await migrator.MigrateAsync(cancellationToken);
        item.UpdatedAt = DateTimeOffset.UtcNow;
        if (item.CreatedAt == default)
        {
            item.CreatedAt = item.UpdatedAt;
        }

        await using var connection = connectionFactory.CreateConnection();
        if (item.Id == 0)
        {
            const string sql =
                """
                INSERT INTO secure_items (
                    item_type, title, notes, is_favorite, sort_order, created_at, updated_at, item_data, image_paths, bound_password_id,
                    category_id, keepass_database_id, keepass_group_path, keepass_entry_uuid, keepass_group_uuid,
                    mdbx_database_id, mdbx_folder_id, is_deleted, deleted_at, replica_group_id, bitwarden_vault_id,
                    bitwarden_cipher_id, bitwarden_folder_id, bitwarden_revision_date, bitwarden_local_modified, sync_status)
                VALUES (
                    @ItemType, @Title, @Notes, @IsFavorite, @SortOrder, @CreatedAt, @UpdatedAt, @ItemData, @ImagePaths, @BoundPasswordId,
                    @CategoryId, @KeepassDatabaseId, @KeepassGroupPath, @KeepassEntryUuid, @KeepassGroupUuid,
                    @MdbxDatabaseId, @MdbxFolderId, @IsDeleted, @DeletedAt, @ReplicaGroupId, @BitwardenVaultId,
                    @BitwardenCipherId, @BitwardenFolderId, @BitwardenRevisionDate, @BitwardenLocalModified, @SyncStatus);
                SELECT last_insert_rowid();
                """;
            item.Id = await connection.ExecuteScalarAsync<long>(sql, ToRow(item));
        }
        else
        {
            const string sql =
                """
                UPDATE secure_items SET item_type=@ItemType, title=@Title, notes=@Notes, is_favorite=@IsFavorite,
                    sort_order=@SortOrder, updated_at=@UpdatedAt, item_data=@ItemData, image_paths=@ImagePaths,
                    bound_password_id=@BoundPasswordId,
                    category_id=@CategoryId, keepass_database_id=@KeepassDatabaseId, keepass_group_path=@KeepassGroupPath,
                    keepass_entry_uuid=@KeepassEntryUuid, keepass_group_uuid=@KeepassGroupUuid, mdbx_database_id=@MdbxDatabaseId,
                    mdbx_folder_id=@MdbxFolderId, is_deleted=@IsDeleted, deleted_at=@DeletedAt, replica_group_id=@ReplicaGroupId,
                    bitwarden_vault_id=@BitwardenVaultId, bitwarden_cipher_id=@BitwardenCipherId, bitwarden_folder_id=@BitwardenFolderId,
                    bitwarden_revision_date=@BitwardenRevisionDate, bitwarden_local_modified=@BitwardenLocalModified,
                    sync_status=@SyncStatus
                WHERE id=@Id;
                """;
            await connection.ExecuteAsync(sql, ToRow(item));
        }

        return item.Id;
    }

    public async Task SoftDeleteSecureItemAsync(long id, CancellationToken cancellationToken = default)
    {
        await migrator.MigrateAsync(cancellationToken);
        await using var connection = connectionFactory.CreateConnection();
        await connection.ExecuteAsync(
            "UPDATE secure_items SET is_deleted = 1, deleted_at = @DeletedAt, updated_at = @DeletedAt WHERE id = @Id",
            new { Id = id, DeletedAt = ToUnixMilliseconds(DateTimeOffset.UtcNow) });
    }

    public async Task<IReadOnlyList<Category>> GetCategoriesAsync(CancellationToken cancellationToken = default)
    {
        await migrator.MigrateAsync(cancellationToken);
        await using var connection = connectionFactory.CreateConnection();
        var rows = await connection.QueryAsync<CategoryRow>("SELECT id, name, sort_order, mdbx_database_id FROM categories ORDER BY sort_order ASC, name ASC");
        return rows.Select(row => new Category { Id = row.Id, Name = row.Name, SortOrder = row.SortOrder, MdbxDatabaseId = row.MdbxDatabaseId }).ToList();
    }

    public async Task<long> SaveCategoryAsync(Category category, CancellationToken cancellationToken = default)
    {
        await migrator.MigrateAsync(cancellationToken);
        await using var connection = connectionFactory.CreateConnection();
        if (category.Id == 0)
        {
            category.Id = await connection.ExecuteScalarAsync<long>(
                "INSERT INTO categories(name, sort_order, mdbx_database_id) VALUES(@Name, @SortOrder, @MdbxDatabaseId); SELECT last_insert_rowid();",
                new { category.Name, category.SortOrder, category.MdbxDatabaseId });
        }
        else
        {
            await connection.ExecuteAsync(
                "UPDATE categories SET name=@Name, sort_order=@SortOrder, mdbx_database_id=@MdbxDatabaseId WHERE id=@Id",
                new { category.Id, category.Name, category.SortOrder, category.MdbxDatabaseId });
        }

        return category.Id;
    }

    public async Task<IReadOnlyList<LocalMdbxDatabase>> GetMdbxDatabasesAsync(CancellationToken cancellationToken = default)
    {
        await migrator.MigrateAsync(cancellationToken);
        await using var connection = connectionFactory.CreateConnection();
        var rows = await connection.QueryAsync<MdbxDatabaseRow>("SELECT * FROM local_mdbx_databases ORDER BY sort_order ASC, created_at DESC");
        return rows.Select(ToModel).ToList();
    }

    public async Task<long> SaveMdbxDatabaseAsync(LocalMdbxDatabase database, CancellationToken cancellationToken = default)
    {
        await migrator.MigrateAsync(cancellationToken);
        await using var connection = connectionFactory.CreateConnection();
        if (database.CreatedAt == default)
        {
            database.CreatedAt = DateTimeOffset.UtcNow;
        }

        if (database.LastAccessedAt == default)
        {
            database.LastAccessedAt = database.CreatedAt;
        }

        if (database.Id == 0)
        {
            const string sql =
                """
                INSERT INTO local_mdbx_databases (
                    name, file_path, storage_location, source_type, source_id, tiga_mode, encrypted_password, unlock_method,
                    kdf_profile, key_file_name, key_file_uri, key_file_fingerprint, description, created_at, last_accessed_at,
                    last_synced_at, is_default, project_count, sort_order, working_copy_path, cache_copy_path, is_offline_available,
                    last_sync_status, last_sync_error)
                VALUES (
                    @Name, @FilePath, @StorageLocation, @SourceType, @SourceId, @TigaMode, @EncryptedPassword, @UnlockMethod,
                    @KdfProfile, @KeyFileName, @KeyFileUri, @KeyFileFingerprint, @Description, @CreatedAt, @LastAccessedAt,
                    @LastSyncedAt, @IsDefault, @ProjectCount, @SortOrder, @WorkingCopyPath, @CacheCopyPath, @IsOfflineAvailable,
                    @LastSyncStatus, @LastSyncError);
                SELECT last_insert_rowid();
                """;
            database.Id = await connection.ExecuteScalarAsync<long>(sql, ToRow(database));
        }
        else
        {
            const string sql =
                """
                UPDATE local_mdbx_databases SET name=@Name, file_path=@FilePath, storage_location=@StorageLocation,
                    source_type=@SourceType, source_id=@SourceId, tiga_mode=@TigaMode, encrypted_password=@EncryptedPassword,
                    unlock_method=@UnlockMethod, kdf_profile=@KdfProfile, key_file_name=@KeyFileName, key_file_uri=@KeyFileUri,
                    key_file_fingerprint=@KeyFileFingerprint, description=@Description, last_accessed_at=@LastAccessedAt,
                    last_synced_at=@LastSyncedAt, is_default=@IsDefault, project_count=@ProjectCount, sort_order=@SortOrder,
                    working_copy_path=@WorkingCopyPath, cache_copy_path=@CacheCopyPath, is_offline_available=@IsOfflineAvailable,
                    last_sync_status=@LastSyncStatus, last_sync_error=@LastSyncError
                WHERE id=@Id;
                """;
            await connection.ExecuteAsync(sql, ToRow(database));
        }

        return database.Id;
    }

    public async Task LogAsync(OperationLog log, CancellationToken cancellationToken = default)
    {
        await migrator.MigrateAsync(cancellationToken);
        await using var connection = connectionFactory.CreateConnection();
        await connection.ExecuteAsync(
            """
            INSERT INTO operation_logs(item_type, item_id, item_title, operation_type, changes_json, device_id, device_name, timestamp, is_reverted)
            VALUES(@ItemType, @ItemId, @ItemTitle, @OperationType, @ChangesJson, @DeviceId, @DeviceName, @Timestamp, @IsReverted)
            """,
            new
            {
                log.ItemType,
                log.ItemId,
                log.ItemTitle,
                log.OperationType,
                log.ChangesJson,
                log.DeviceId,
                log.DeviceName,
                Timestamp = ToUnixMilliseconds(log.Timestamp),
                IsReverted = log.IsReverted ? 1 : 0
            });
    }

    private static PasswordEntry ToModel(PasswordEntryRow row) => new()
    {
        Id = row.Id,
        Title = row.Title,
        Website = row.Website,
        Username = row.Username,
        Password = row.Password,
        Notes = row.Notes,
        CreatedAt = FromUnixMilliseconds(row.CreatedAt),
        UpdatedAt = FromUnixMilliseconds(row.UpdatedAt),
        IsFavorite = row.IsFavorite,
        SortOrder = row.SortOrder,
        IsGroupCover = row.IsGroupCover,
        AppPackageName = row.AppPackageName,
        AppName = row.AppName,
        Email = row.Email,
        Phone = row.Phone,
        AddressLine = row.AddressLine,
        City = row.City,
        State = row.State,
        ZipCode = row.ZipCode,
        Country = row.Country,
        CreditCardNumber = row.CreditCardNumber,
        CreditCardHolder = row.CreditCardHolder,
        CreditCardExpiry = row.CreditCardExpiry,
        CreditCardCvv = row.CreditCardCvv,
        CategoryId = row.CategoryId,
        BoundNoteId = row.BoundNoteId,
        KeepassDatabaseId = row.KeepassDatabaseId,
        KeepassGroupPath = row.KeepassGroupPath,
        KeepassEntryUuid = row.KeepassEntryUuid,
        KeepassGroupUuid = row.KeepassGroupUuid,
        MdbxDatabaseId = row.MdbxDatabaseId,
        MdbxFolderId = row.MdbxFolderId,
        AuthenticatorKey = row.AuthenticatorKey,
        PasskeyBindings = row.PasskeyBindings,
        SshKeyData = row.SshKeyData,
        LoginType = ParseLoginType(row.LoginType),
        SsoProvider = row.SsoProvider,
        SsoRefEntryId = row.SsoRefEntryId,
        WifiMetadata = row.WifiMetadata,
        CustomIconType = row.CustomIconType,
        CustomIconValue = row.CustomIconValue,
        CustomIconUpdatedAt = row.CustomIconUpdatedAt,
        IsDeleted = row.IsDeleted,
        DeletedAt = FromNullableUnixMilliseconds(row.DeletedAt),
        IsArchived = row.IsArchived,
        ArchivedAt = FromNullableUnixMilliseconds(row.ArchivedAt),
        ReplicaGroupId = row.ReplicaGroupId,
        BitwardenVaultId = row.BitwardenVaultId,
        BitwardenCipherId = row.BitwardenCipherId,
        BitwardenFolderId = row.BitwardenFolderId,
        BitwardenRevisionDate = row.BitwardenRevisionDate,
        BitwardenCipherType = row.BitwardenCipherType,
        BitwardenLocalModified = row.BitwardenLocalModified
    };

    private static object ToRow(PasswordEntry entry) => new
    {
        entry.Id,
        entry.Title,
        entry.Website,
        entry.Username,
        entry.Password,
        entry.Notes,
        CreatedAt = ToUnixMilliseconds(entry.CreatedAt),
        UpdatedAt = ToUnixMilliseconds(entry.UpdatedAt),
        IsFavorite = entry.IsFavorite ? 1 : 0,
        entry.SortOrder,
        IsGroupCover = entry.IsGroupCover ? 1 : 0,
        entry.AppPackageName,
        entry.AppName,
        entry.Email,
        entry.Phone,
        entry.AddressLine,
        entry.City,
        entry.State,
        entry.ZipCode,
        entry.Country,
        entry.CreditCardNumber,
        entry.CreditCardHolder,
        entry.CreditCardExpiry,
        entry.CreditCardCvv,
        entry.CategoryId,
        entry.BoundNoteId,
        entry.KeepassDatabaseId,
        entry.KeepassGroupPath,
        entry.KeepassEntryUuid,
        entry.KeepassGroupUuid,
        entry.MdbxDatabaseId,
        entry.MdbxFolderId,
        entry.AuthenticatorKey,
        entry.PasskeyBindings,
        entry.SshKeyData,
        LoginType = entry.LoginType.ToString().ToUpperInvariant(),
        entry.SsoProvider,
        entry.SsoRefEntryId,
        entry.WifiMetadata,
        entry.CustomIconType,
        entry.CustomIconValue,
        entry.CustomIconUpdatedAt,
        IsDeleted = entry.IsDeleted ? 1 : 0,
        DeletedAt = ToNullableUnixMilliseconds(entry.DeletedAt),
        IsArchived = entry.IsArchived ? 1 : 0,
        ArchivedAt = ToNullableUnixMilliseconds(entry.ArchivedAt),
        entry.ReplicaGroupId,
        entry.BitwardenVaultId,
        entry.BitwardenCipherId,
        entry.BitwardenFolderId,
        entry.BitwardenRevisionDate,
        entry.BitwardenCipherType,
        BitwardenLocalModified = entry.BitwardenLocalModified ? 1 : 0
    };

    private static CustomField ToModel(CustomFieldRow row) => new()
    {
        Id = row.Id,
        EntryId = row.EntryId,
        Title = row.Title,
        Value = row.Value,
        IsProtected = row.IsProtected,
        SortOrder = row.SortOrder
    };

    private static SecureItem ToModel(SecureItemRow row) => new()
    {
        Id = row.Id,
        ItemType = Enum.TryParse<VaultItemType>(row.ItemType, true, out var itemType) ? itemType : VaultItemType.Note,
        Title = row.Title,
        Notes = row.Notes,
        IsFavorite = row.IsFavorite,
        SortOrder = row.SortOrder,
        CreatedAt = FromUnixMilliseconds(row.CreatedAt),
        UpdatedAt = FromUnixMilliseconds(row.UpdatedAt),
        ItemData = row.ItemData,
        ImagePaths = row.ImagePaths,
        BoundPasswordId = row.BoundPasswordId,
        CategoryId = row.CategoryId,
        KeepassDatabaseId = row.KeepassDatabaseId,
        KeepassGroupPath = row.KeepassGroupPath,
        KeepassEntryUuid = row.KeepassEntryUuid,
        KeepassGroupUuid = row.KeepassGroupUuid,
        MdbxDatabaseId = row.MdbxDatabaseId,
        MdbxFolderId = row.MdbxFolderId,
        IsDeleted = row.IsDeleted,
        DeletedAt = FromNullableUnixMilliseconds(row.DeletedAt),
        ReplicaGroupId = row.ReplicaGroupId,
        BitwardenVaultId = row.BitwardenVaultId,
        BitwardenCipherId = row.BitwardenCipherId,
        BitwardenFolderId = row.BitwardenFolderId,
        BitwardenRevisionDate = row.BitwardenRevisionDate,
        BitwardenLocalModified = row.BitwardenLocalModified,
        SyncStatus = Enum.TryParse<SyncStatus>(row.SyncStatus, true, out var sync) ? sync : SyncStatus.None
    };

    private static object ToRow(SecureItem item) => new
    {
        item.Id,
        ItemType = item.ItemType.ToString().ToUpperInvariant(),
        item.Title,
        item.Notes,
        IsFavorite = item.IsFavorite ? 1 : 0,
        item.SortOrder,
        CreatedAt = ToUnixMilliseconds(item.CreatedAt),
        UpdatedAt = ToUnixMilliseconds(item.UpdatedAt),
        item.ItemData,
        item.ImagePaths,
        item.BoundPasswordId,
        item.CategoryId,
        item.KeepassDatabaseId,
        item.KeepassGroupPath,
        item.KeepassEntryUuid,
        item.KeepassGroupUuid,
        item.MdbxDatabaseId,
        item.MdbxFolderId,
        IsDeleted = item.IsDeleted ? 1 : 0,
        DeletedAt = ToNullableUnixMilliseconds(item.DeletedAt),
        item.ReplicaGroupId,
        item.BitwardenVaultId,
        item.BitwardenCipherId,
        item.BitwardenFolderId,
        item.BitwardenRevisionDate,
        BitwardenLocalModified = item.BitwardenLocalModified ? 1 : 0,
        SyncStatus = item.SyncStatus.ToString().ToUpperInvariant()
    };

    private static LocalMdbxDatabase ToModel(MdbxDatabaseRow row) => new()
    {
        Id = row.Id,
        Name = row.Name,
        FilePath = row.FilePath,
        StorageLocation = Enum.TryParse<MdbxStorageLocation>(row.StorageLocation.Replace("_", "", StringComparison.Ordinal), true, out var location) ? location : MdbxStorageLocation.RemoteWebDav,
        SourceType = row.SourceType,
        SourceId = row.SourceId,
        TigaMode = Enum.TryParse<MdbxTigaMode>(row.TigaMode, true, out var mode) ? mode : MdbxTigaMode.Multi,
        EncryptedPassword = row.EncryptedPassword,
        UnlockMethod = row.UnlockMethod switch
        {
            "key_file" => MdbxUnlockMethod.KeyFile,
            "password+key_file" => MdbxUnlockMethod.MasterPasswordAndKeyFile,
            "device_key" => MdbxUnlockMethod.DeviceKey,
            _ => MdbxUnlockMethod.MasterPassword
        },
        KdfProfile = row.KdfProfile,
        KeyFileName = row.KeyFileName,
        KeyFileUri = row.KeyFileUri,
        KeyFileFingerprint = row.KeyFileFingerprint,
        Description = row.Description,
        CreatedAt = FromUnixMilliseconds(row.CreatedAt),
        LastAccessedAt = FromUnixMilliseconds(row.LastAccessedAt),
        LastSyncedAt = FromNullableUnixMilliseconds(row.LastSyncedAt),
        IsDefault = row.IsDefault,
        ProjectCount = row.ProjectCount,
        SortOrder = row.SortOrder,
        WorkingCopyPath = row.WorkingCopyPath,
        CacheCopyPath = row.CacheCopyPath,
        IsOfflineAvailable = row.IsOfflineAvailable,
        LastSyncStatus = Enum.TryParse<SyncStatus>(row.LastSyncStatus.Replace("_", "", StringComparison.Ordinal), true, out var status) ? status : SyncStatus.LocalOnly,
        LastSyncError = row.LastSyncError
    };

    private static object ToRow(LocalMdbxDatabase database) => new
    {
        database.Id,
        database.Name,
        database.FilePath,
        StorageLocation = database.StorageLocation.ToString().ToUpperInvariant(),
        database.SourceType,
        database.SourceId,
        TigaMode = database.TigaMode.ToString().ToUpperInvariant(),
        database.EncryptedPassword,
        UnlockMethod = database.UnlockMethod switch
        {
            MdbxUnlockMethod.KeyFile => "key_file",
            MdbxUnlockMethod.MasterPasswordAndKeyFile => "password+key_file",
            MdbxUnlockMethod.DeviceKey => "device_key",
            _ => "password"
        },
        database.KdfProfile,
        database.KeyFileName,
        database.KeyFileUri,
        database.KeyFileFingerprint,
        database.Description,
        CreatedAt = ToUnixMilliseconds(database.CreatedAt),
        LastAccessedAt = ToUnixMilliseconds(database.LastAccessedAt),
        LastSyncedAt = ToNullableUnixMilliseconds(database.LastSyncedAt),
        IsDefault = database.IsDefault ? 1 : 0,
        database.ProjectCount,
        database.SortOrder,
        database.WorkingCopyPath,
        database.CacheCopyPath,
        IsOfflineAvailable = database.IsOfflineAvailable ? 1 : 0,
        LastSyncStatus = database.LastSyncStatus.ToString().ToUpperInvariant(),
        database.LastSyncError
    };

    private static PasswordLoginType ParseLoginType(string value) => value.ToUpperInvariant() switch
    {
        "SSO" => PasswordLoginType.Sso,
        "WIFI" => PasswordLoginType.Wifi,
        "SSHKEY" or "SSH_KEY" => PasswordLoginType.SshKey,
        _ => PasswordLoginType.Password
    };

    private static long ToUnixMilliseconds(DateTimeOffset value) => value.ToUnixTimeMilliseconds();
    private static long? ToNullableUnixMilliseconds(DateTimeOffset? value) => value?.ToUnixTimeMilliseconds();
    private static DateTimeOffset FromUnixMilliseconds(long value) => DateTimeOffset.FromUnixTimeMilliseconds(value);
    private static DateTimeOffset? FromNullableUnixMilliseconds(long? value) => value is null ? null : DateTimeOffset.FromUnixTimeMilliseconds(value.Value);

    private sealed class PasswordEntryRow
    {
        public long Id { get; init; }
        public string Title { get; init; } = "";
        public string Website { get; init; } = "";
        public string Username { get; init; } = "";
        public string Password { get; init; } = "";
        public string Notes { get; init; } = "";
        public long CreatedAt { get; init; }
        public long UpdatedAt { get; init; }
        public bool IsFavorite { get; init; }
        public int SortOrder { get; init; }
        public bool IsGroupCover { get; init; }
        public string AppPackageName { get; init; } = "";
        public string AppName { get; init; } = "";
        public string Email { get; init; } = "";
        public string Phone { get; init; } = "";
        public string AddressLine { get; init; } = "";
        public string City { get; init; } = "";
        public string State { get; init; } = "";
        public string ZipCode { get; init; } = "";
        public string Country { get; init; } = "";
        public string CreditCardNumber { get; init; } = "";
        public string CreditCardHolder { get; init; } = "";
        public string CreditCardExpiry { get; init; } = "";
        public string CreditCardCvv { get; init; } = "";
        public long? CategoryId { get; init; }
        public long? BoundNoteId { get; init; }
        public long? KeepassDatabaseId { get; init; }
        public string? KeepassGroupPath { get; init; }
        public string? KeepassEntryUuid { get; init; }
        public string? KeepassGroupUuid { get; init; }
        public long? MdbxDatabaseId { get; init; }
        public string? MdbxFolderId { get; init; }
        public string AuthenticatorKey { get; init; } = "";
        public string PasskeyBindings { get; init; } = "";
        public string SshKeyData { get; init; } = "";
        public string LoginType { get; init; } = "PASSWORD";
        public string SsoProvider { get; init; } = "";
        public long? SsoRefEntryId { get; init; }
        public string WifiMetadata { get; init; } = "";
        public string CustomIconType { get; init; } = "NONE";
        public string? CustomIconValue { get; init; }
        public long CustomIconUpdatedAt { get; init; }
        public bool IsDeleted { get; init; }
        public long? DeletedAt { get; init; }
        public bool IsArchived { get; init; }
        public long? ArchivedAt { get; init; }
        public string? ReplicaGroupId { get; init; }
        public long? BitwardenVaultId { get; init; }
        public string? BitwardenCipherId { get; init; }
        public string? BitwardenFolderId { get; init; }
        public string? BitwardenRevisionDate { get; init; }
        public int BitwardenCipherType { get; init; }
        public bool BitwardenLocalModified { get; init; }
    }

    private sealed class CustomFieldRow
    {
        public long Id { get; init; }
        public long EntryId { get; init; }
        public string Title { get; init; } = "";
        public string Value { get; init; } = "";
        public bool IsProtected { get; init; }
        public int SortOrder { get; init; }
    }

    private sealed class SecureItemRow
    {
        public long Id { get; init; }
        public string ItemType { get; init; } = "";
        public string Title { get; init; } = "";
        public string Notes { get; init; } = "";
        public bool IsFavorite { get; init; }
        public int SortOrder { get; init; }
        public long CreatedAt { get; init; }
        public long UpdatedAt { get; init; }
        public string ItemData { get; init; } = "{}";
        public string ImagePaths { get; init; } = "[]";
        public long? BoundPasswordId { get; init; }
        public long? CategoryId { get; init; }
        public long? KeepassDatabaseId { get; init; }
        public string? KeepassGroupPath { get; init; }
        public string? KeepassEntryUuid { get; init; }
        public string? KeepassGroupUuid { get; init; }
        public long? MdbxDatabaseId { get; init; }
        public string? MdbxFolderId { get; init; }
        public bool IsDeleted { get; init; }
        public long? DeletedAt { get; init; }
        public string? ReplicaGroupId { get; init; }
        public long? BitwardenVaultId { get; init; }
        public string? BitwardenCipherId { get; init; }
        public string? BitwardenFolderId { get; init; }
        public string? BitwardenRevisionDate { get; init; }
        public bool BitwardenLocalModified { get; init; }
        public string SyncStatus { get; init; } = "NONE";
    }

    private sealed class CategoryRow
    {
        public long Id { get; init; }
        public string Name { get; init; } = "";
        public int SortOrder { get; init; }
        public long? MdbxDatabaseId { get; init; }
    }

    private sealed class MdbxDatabaseRow
    {
        public long Id { get; init; }
        public string Name { get; init; } = "";
        public string FilePath { get; init; } = "";
        public string StorageLocation { get; init; } = "REMOTE_WEBDAV";
        public string SourceType { get; init; } = "REMOTE_WEBDAV";
        public long? SourceId { get; init; }
        public string TigaMode { get; init; } = "MULTI";
        public string? EncryptedPassword { get; init; }
        public string UnlockMethod { get; init; } = "password";
        public string KdfProfile { get; init; } = "argon2id";
        public string? KeyFileName { get; init; }
        public string? KeyFileUri { get; init; }
        public string? KeyFileFingerprint { get; init; }
        public string? Description { get; init; }
        public long CreatedAt { get; init; }
        public long LastAccessedAt { get; init; }
        public long? LastSyncedAt { get; init; }
        public bool IsDefault { get; init; }
        public int ProjectCount { get; init; }
        public int SortOrder { get; init; }
        public string? WorkingCopyPath { get; init; }
        public string? CacheCopyPath { get; init; }
        public bool IsOfflineAvailable { get; init; }
        public string LastSyncStatus { get; init; } = "LOCAL_ONLY";
        public string? LastSyncError { get; init; }
    }
}
