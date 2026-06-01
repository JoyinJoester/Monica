using Monica.Core.ImportExport;
using Monica.Core.Services;
using Monica.Data;
using Monica.Data.Repositories;
using Monica.App.ViewModels;
using Monica.App.Services;
using Monica.Platform.Services;

namespace Monica.Tests;

public sealed class VaultCredentialTests
{
    [Fact]
    public async Task Credential_store_roundtrips_master_password_hash()
    {
        var path = GetTempDatabasePath();
        var factory = new SqliteConnectionFactory(path);
        var store = new VaultCredentialStore(factory, new DatabaseMigrator(factory));
        var crypto = new CryptoService();
        var hash = crypto.HashMasterPassword("correct password");

        await store.SaveAsync(hash);
        var loaded = await store.GetAsync();

        Assert.NotNull(loaded);
        Assert.True(new CryptoService().VerifyMasterPassword("correct password", loaded));
        Assert.False(new CryptoService().VerifyMasterPassword("wrong password", loaded));
    }

    [Fact]
    public async Task ViewModel_requires_first_run_password_confirmation()
    {
        var viewModel = CreateViewModel(GetTempDatabasePath());
        await viewModel.InitializeAsync();

        Assert.False(viewModel.IsVaultInitialized);

        viewModel.MasterPassword = "password-one";
        viewModel.ConfirmMasterPassword = "password-two";
        await viewModel.UnlockCommand.ExecuteAsync(null);

        Assert.False(viewModel.IsUnlocked);
        Assert.Equal(viewModel.L.Get("ConfirmationMismatch"), viewModel.StatusMessage);
    }

    [Fact]
    public async Task ViewModel_creates_vault_then_rejects_wrong_password()
    {
        var path = GetTempDatabasePath();
        var first = CreateViewModel(path);
        await first.InitializeAsync();
        first.MasterPassword = "correct password";
        first.ConfirmMasterPassword = "correct password";
        await first.UnlockCommand.ExecuteAsync(null);

        Assert.True(first.IsUnlocked);
        Assert.True(first.IsVaultInitialized);

        var second = CreateViewModel(path);
        await second.InitializeAsync();
        second.MasterPassword = "wrong password";
        await second.UnlockCommand.ExecuteAsync(null);

        Assert.False(second.IsUnlocked);
        Assert.Equal(second.L.Get("WrongMasterPassword"), second.StatusMessage);

        second.MasterPassword = "correct password";
        await second.UnlockCommand.ExecuteAsync(null);

        Assert.True(second.IsUnlocked);
    }

    private static MainWindowViewModel CreateViewModel(string databasePath)
    {
        var factory = new SqliteConnectionFactory(databasePath);
        var migrator = new DatabaseMigrator(factory);
        var repository = new MonicaRepository(factory, migrator);
        return new MainWindowViewModel(
            repository,
            new VaultCredentialStore(factory, migrator),
            new CryptoService(),
            new TotpService(),
            new PasswordGeneratorService(),
            new ImportExportService(),
            new PlatformCapabilityService(),
            new NoopClipboardService(),
            new MdbxVaultService(),
            new AppSettingsService(GetTempSettingsPath()),
            new LocalizationService());
    }

    private static string GetTempDatabasePath()
    {
        var path = Path.Combine(Path.GetTempPath(), "monica-tests", $"{Guid.NewGuid():N}.db");
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        return path;
    }

    private static string GetTempSettingsPath()
    {
        var path = Path.Combine(Path.GetTempPath(), "monica-tests", $"{Guid.NewGuid():N}.settings.json");
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        return path;
    }

    private sealed class NoopClipboardService : IClipboardService
    {
        public Task SetTextAsync(string text, CancellationToken cancellationToken = default) => Task.CompletedTask;
    }
}
