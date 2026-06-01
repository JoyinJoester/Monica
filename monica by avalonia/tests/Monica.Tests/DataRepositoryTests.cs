using Monica.Core.Models;
using Monica.Data;
using Monica.Data.Repositories;

namespace Monica.Tests;

public sealed class DataRepositoryTests
{
    [Fact]
    public async Task Migration_sets_current_schema_version()
    {
        var path = GetTempDatabasePath();
        var factory = new SqliteConnectionFactory(path);
        var migrator = new DatabaseMigrator(factory);

        await migrator.MigrateAsync();

        await using var connection = factory.CreateConnection();
        await connection.OpenAsync();
        await using var command = connection.CreateCommand();
        command.CommandText = "PRAGMA user_version;";
        var version = Convert.ToInt32(await command.ExecuteScalarAsync());
        Assert.Equal(DatabaseMigrator.CurrentSchemaVersion, version);
    }

    [Fact]
    public async Task Repository_saves_password_secure_item_category_and_mdbx_metadata()
    {
        var path = GetTempDatabasePath();
        var factory = new SqliteConnectionFactory(path);
        var migrator = new DatabaseMigrator(factory);
        var repository = new MonicaRepository(factory, migrator);

        var category = new Category { Name = "Work" };
        await repository.SaveCategoryAsync(category);

        var password = new PasswordEntry
        {
            Title = "GitHub",
            Username = "dev",
            Password = "encrypted",
            Website = "https://github.com",
            CategoryId = category.Id
        };
        await repository.SavePasswordAsync(password);

        var totp = new SecureItem
        {
            ItemType = VaultItemType.Totp,
            Title = "GitHub OTP",
            ItemData = """{"secret":"JBSWY3DPEHPK3PXP"}"""
        };
        await repository.SaveSecureItemAsync(totp);

        var mdbx = new LocalMdbxDatabase
        {
            Name = "Local",
            FilePath = Path.Combine(Path.GetTempPath(), "local.mdbx"),
            StorageLocation = MdbxStorageLocation.Internal,
            SourceType = "LOCAL_INTERNAL"
        };
        await repository.SaveMdbxDatabaseAsync(mdbx);

        Assert.Single(await repository.GetCategoriesAsync());
        Assert.Single(await repository.GetPasswordsAsync());
        Assert.Single(await repository.GetSecureItemsAsync(VaultItemType.Totp));
        Assert.Single(await repository.GetMdbxDatabasesAsync());
    }

    [Fact]
    public async Task Repository_soft_deletes_password()
    {
        var path = GetTempDatabasePath();
        var factory = new SqliteConnectionFactory(path);
        var repository = new MonicaRepository(factory, new DatabaseMigrator(factory));
        var entry = new PasswordEntry { Title = "Trash me", Password = "encrypted" };
        await repository.SavePasswordAsync(entry);

        await repository.SoftDeletePasswordAsync(entry.Id);

        Assert.Empty(await repository.GetPasswordsAsync());
        Assert.Single(await repository.GetPasswordsAsync(includeDeleted: true));
    }

    [Fact]
    public async Task Repository_restores_and_permanently_deletes_password_with_bound_data()
    {
        var path = GetTempDatabasePath();
        var factory = new SqliteConnectionFactory(path);
        var repository = new MonicaRepository(factory, new DatabaseMigrator(factory));
        var entry = new PasswordEntry { Title = "Recover me", Password = "encrypted" };
        await repository.SavePasswordAsync(entry);
        await repository.ReplaceCustomFieldsAsync(entry.Id, [new CustomField { Title = "PIN", Value = "1234" }]);
        var totp = new SecureItem
        {
            ItemType = VaultItemType.Totp,
            Title = "Recover me",
            BoundPasswordId = entry.Id,
            ItemData = """{"secret":"JBSWY3DPEHPK3PXP"}"""
        };
        await repository.SaveSecureItemAsync(totp);

        await repository.SoftDeletePasswordAsync(entry.Id);

        Assert.Empty(await repository.GetPasswordsAsync());
        Assert.Empty(await repository.GetSecureItemsByBoundPasswordIdAsync(entry.Id));
        Assert.Single(await repository.GetSecureItemsByBoundPasswordIdAsync(entry.Id, includeDeleted: true));

        await repository.RestorePasswordAsync(entry.Id);

        Assert.Single(await repository.GetPasswordsAsync());
        Assert.Single(await repository.GetSecureItemsByBoundPasswordIdAsync(entry.Id));

        await repository.SoftDeletePasswordAsync(entry.Id);
        await repository.DeletePasswordPermanentlyAsync(entry.Id);

        Assert.Empty(await repository.GetPasswordsAsync(includeDeleted: true));
        Assert.Empty(await repository.GetCustomFieldsAsync(entry.Id));
        Assert.Empty(await repository.GetSecureItemsByBoundPasswordIdAsync(entry.Id, includeDeleted: true));
    }

    private static string GetTempDatabasePath()
    {
        var path = Path.Combine(Path.GetTempPath(), "monica-tests", $"{Guid.NewGuid():N}.db");
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        return path;
    }
}
