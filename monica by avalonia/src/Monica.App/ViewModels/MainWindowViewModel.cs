using System.Collections.ObjectModel;
using System.Text.Json;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Monica.Core.ImportExport;
using Monica.Core.Models;
using Monica.Core.Services;
using Monica.Data.Repositories;
using Monica.Platform.Services;

namespace Monica.App.ViewModels;

public sealed partial class MainWindowViewModel : ObservableObject
{
    private readonly IMonicaRepository _repository;
    private readonly ICryptoService _cryptoService;
    private readonly ITotpService _totpService;
    private readonly IPasswordGeneratorService _passwordGenerator;
    private readonly IImportExportService _importExportService;
    private readonly IClipboardService _clipboardService;
    private readonly IMdbxVaultService _mdbxVaultService;

    public MainWindowViewModel(
        IMonicaRepository repository,
        ICryptoService cryptoService,
        ITotpService totpService,
        IPasswordGeneratorService passwordGenerator,
        IImportExportService importExportService,
        IPlatformCapabilityService platformCapabilityService,
        IClipboardService clipboardService,
        IMdbxVaultService mdbxVaultService)
    {
        _repository = repository;
        _cryptoService = cryptoService;
        _totpService = totpService;
        _passwordGenerator = passwordGenerator;
        _importExportService = importExportService;
        _clipboardService = clipboardService;
        _mdbxVaultService = mdbxVaultService;
        Capabilities = new ObservableCollection<PlatformCapability>(platformCapabilityService.GetCapabilities());
    }

    public ObservableCollection<PasswordEntry> Passwords { get; } = [];
    public ObservableCollection<SecureItem> TotpItems { get; } = [];
    public ObservableCollection<SecureItem> WalletItems { get; } = [];
    public ObservableCollection<Category> Categories { get; } = [];
    public ObservableCollection<PlatformCapability> Capabilities { get; }
    public ObservableCollection<LocalMdbxDatabase> MdbxDatabases { get; } = [];

    [ObservableProperty]
    private bool _isUnlocked;

    [ObservableProperty]
    private string _selectedSection = "Passwords";

    [ObservableProperty]
    private string _masterPassword = "";

    [ObservableProperty]
    private string _searchText = "";

    [ObservableProperty]
    private string _statusMessage = "Locked";

    [ObservableProperty]
    private string _generatedPassword = "";

    [ObservableProperty]
    private string _exportPreview = "";

    public string PasswordCountText => $"{Passwords.Count} items";
    public string TotpCountText => $"{TotpItems.Count} authenticators";
    public string WalletCountText => $"{WalletItems.Count} cards and documents";

    public IEnumerable<PasswordEntry> FilteredPasswords =>
        string.IsNullOrWhiteSpace(SearchText)
            ? Passwords
            : Passwords.Where(item =>
                item.Title.Contains(SearchText, StringComparison.OrdinalIgnoreCase) ||
                item.Username.Contains(SearchText, StringComparison.OrdinalIgnoreCase) ||
                item.Website.Contains(SearchText, StringComparison.OrdinalIgnoreCase));

    partial void OnSearchTextChanged(string value) => OnPropertyChanged(nameof(FilteredPasswords));

    [RelayCommand]
    private async Task UnlockAsync()
    {
        if (string.IsNullOrWhiteSpace(MasterPassword))
        {
            StatusMessage = "Enter a master password to unlock the desktop vault.";
            return;
        }

        var salt = "Monica Avalonia Desktop"u8.ToArray();
        _cryptoService.InitializeSession(MasterPassword, salt);
        IsUnlocked = true;
        StatusMessage = "Vault unlocked";
        await LoadAsync();
    }

    [RelayCommand]
    public async Task LoadAsync()
    {
        Passwords.Clear();
        TotpItems.Clear();
        WalletItems.Clear();
        Categories.Clear();
        MdbxDatabases.Clear();

        foreach (var item in await _repository.GetPasswordsAsync())
        {
            Passwords.Add(item);
        }

        foreach (var item in await _repository.GetSecureItemsAsync(VaultItemType.Totp))
        {
            RefreshTotpDisplay(item);
            TotpItems.Add(item);
        }

        foreach (var item in await _repository.GetSecureItemsAsync())
        {
            if (item.ItemType is VaultItemType.BankCard or VaultItemType.Document or VaultItemType.Note)
            {
                WalletItems.Add(item);
            }
        }

        foreach (var category in await _repository.GetCategoriesAsync())
        {
            Categories.Add(category);
        }

        foreach (var database in await _repository.GetMdbxDatabasesAsync())
        {
            MdbxDatabases.Add(database);
        }

        if (Passwords.Count == 0 && TotpItems.Count == 0 && WalletItems.Count == 0)
        {
            await SeedDemoDataAsync();
        }

        RaiseCounts();
        OnPropertyChanged(nameof(FilteredPasswords));
    }

    [RelayCommand]
    private void SelectSection(string? section)
    {
        if (!string.IsNullOrWhiteSpace(section))
        {
            SelectedSection = section;
        }
    }

    [RelayCommand]
    private async Task AddPasswordAsync()
    {
        var password = GeneratedPassword;
        if (string.IsNullOrWhiteSpace(password))
        {
            password = _passwordGenerator.GeneratePassword();
        }

        var entry = new PasswordEntry
        {
            Title = $"New Login {Passwords.Count + 1}",
            Website = "https://example.com",
            Username = "user@example.com",
            Password = _cryptoService.IsUnlocked ? _cryptoService.EncryptString(password) : password,
            Notes = "Created from Monica by Avalonia.",
            IsFavorite = Passwords.Count == 0
        };

        await _repository.SavePasswordAsync(entry);
        await _repository.LogAsync(new OperationLog
        {
            ItemType = "PASSWORD",
            ItemId = entry.Id,
            ItemTitle = entry.Title,
            OperationType = "CREATE",
            DeviceName = Environment.MachineName
        });
        Passwords.Insert(0, entry);
        RaiseCounts();
        OnPropertyChanged(nameof(FilteredPasswords));
        StatusMessage = $"Created {entry.Title}";
    }

    [RelayCommand]
    private async Task CopyPasswordAsync(PasswordEntry? entry)
    {
        if (entry is null)
        {
            return;
        }

        var text = entry.Password;
        if (_cryptoService.IsUnlocked)
        {
            try
            {
                text = _cryptoService.DecryptString(entry.Password);
            }
            catch
            {
                text = entry.Password;
            }
        }

        await _clipboardService.SetTextAsync(text);
        StatusMessage = $"Copied password for {entry.Title}";
    }

    [RelayCommand]
    private async Task ToggleFavoriteAsync(PasswordEntry? entry)
    {
        if (entry is null)
        {
            return;
        }

        entry.IsFavorite = !entry.IsFavorite;
        await _repository.SavePasswordAsync(entry);
        OnPropertyChanged(nameof(FilteredPasswords));
    }

    [RelayCommand]
    private async Task DeletePasswordAsync(PasswordEntry? entry)
    {
        if (entry is null)
        {
            return;
        }

        await _repository.SoftDeletePasswordAsync(entry.Id);
        Passwords.Remove(entry);
        RaiseCounts();
        OnPropertyChanged(nameof(FilteredPasswords));
        StatusMessage = $"Moved {entry.Title} to recycle bin";
    }

    [RelayCommand]
    private async Task AddTotpAsync()
    {
        var item = new SecureItem
        {
            ItemType = VaultItemType.Totp,
            Title = $"Authenticator {TotpItems.Count + 1}",
            Notes = "Monica desktop sample",
            ItemData = """{"secret":"JBSWY3DPEHPK3PXP","period":30,"digits":6,"otpType":"TOTP"}"""
        };
        RefreshTotpDisplay(item);
        await _repository.SaveSecureItemAsync(item);
        TotpItems.Insert(0, item);
        RaiseCounts();
    }

    [RelayCommand]
    private async Task CopyTotpAsync(SecureItem? item)
    {
        if (item is null)
        {
            return;
        }

        RefreshTotpDisplay(item);
        await _clipboardService.SetTextAsync(item.TotpCode);
        StatusMessage = $"Copied TOTP for {item.Title}";
    }

    [RelayCommand]
    private async Task AddWalletItemAsync()
    {
        var item = new SecureItem
        {
            ItemType = WalletItems.Count % 2 == 0 ? VaultItemType.BankCard : VaultItemType.Document,
            Title = WalletItems.Count % 2 == 0 ? "Bank Card" : "Identity Document",
            Notes = "Desktop wallet entry",
            ItemData = "{}"
        };
        await _repository.SaveSecureItemAsync(item);
        WalletItems.Insert(0, item);
        RaiseCounts();
    }

    [RelayCommand]
    private void GeneratePassword()
    {
        GeneratedPassword = _passwordGenerator.GeneratePassword(24);
        var analysis = _passwordGenerator.Analyze(GeneratedPassword);
        StatusMessage = $"Generated {analysis.Label.ToLowerInvariant()} password";
    }

    [RelayCommand]
    private void ExportData()
    {
        ExportPreview = _importExportService.ExportJson(Passwords, TotpItems.Concat(WalletItems));
        StatusMessage = "Prepared Monica JSON export preview";
    }

    [RelayCommand]
    private async Task CreateMdbxVaultAsync()
    {
        var root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Monica", "mdbx");
        var metadata = await _mdbxVaultService.CreateLocalMetadataAsync("Local Monica Vault", Path.Combine(root, "local.mdbx"));
        await _repository.SaveMdbxDatabaseAsync(metadata);
        MdbxDatabases.Add(metadata);
        StatusMessage = "Created MDBX metadata and local working file path";
    }

    public void RefreshTotpDisplay(SecureItem item)
    {
        var data = ParseTotpData(item.ItemData);
        item.TotpCode = _totpService.GenerateCode(data.Secret, data.Period, data.Digits, data.OtpType, data.Counter);
        item.TotpTimeRemaining = $"{_totpService.GetRemainingSeconds(data.Period)}s";
        item.TotpProgress = _totpService.GetProgress(data.Period);
    }

    private async Task SeedDemoDataAsync()
    {
        var defaultCategory = new Category { Name = "Personal", SortOrder = 0 };
        await _repository.SaveCategoryAsync(defaultCategory);
        Categories.Add(defaultCategory);

        var encryptedPassword = _cryptoService.IsUnlocked ? _cryptoService.EncryptString("Monica-Desktop-2026!") : "Monica-Desktop-2026!";
        var password = new PasswordEntry
        {
            Title = "Monica Account",
            Website = "https://monica.local",
            Username = "desktop@monica",
            Password = encryptedPassword,
            Notes = "Seed entry showing the migrated WinUI card layout.",
            IsFavorite = true,
            CategoryId = defaultCategory.Id
        };
        await _repository.SavePasswordAsync(password);
        Passwords.Add(password);

        var totp = new SecureItem
        {
            ItemType = VaultItemType.Totp,
            Title = "Monica TOTP",
            Notes = "Issuer: Monica",
            ItemData = """{"secret":"JBSWY3DPEHPK3PXP","period":30,"digits":6,"otpType":"TOTP"}""",
            IsFavorite = true
        };
        RefreshTotpDisplay(totp);
        await _repository.SaveSecureItemAsync(totp);
        TotpItems.Add(totp);

        var card = new SecureItem
        {
            ItemType = VaultItemType.BankCard,
            Title = "Primary Card",
            Notes = "Wallet item parity with Android card package.",
            ItemData = "{}"
        };
        await _repository.SaveSecureItemAsync(card);
        WalletItems.Add(card);
    }

    private void RaiseCounts()
    {
        OnPropertyChanged(nameof(PasswordCountText));
        OnPropertyChanged(nameof(TotpCountText));
        OnPropertyChanged(nameof(WalletCountText));
    }

    private static TotpData ParseTotpData(string json)
    {
        try
        {
            using var document = JsonDocument.Parse(json);
            var root = document.RootElement;
            return new TotpData(
                root.TryGetProperty("secret", out var secret) ? secret.GetString() ?? "" : "",
                root.TryGetProperty("period", out var period) ? period.GetInt32() : 30,
                root.TryGetProperty("digits", out var digits) ? digits.GetInt32() : 6,
                root.TryGetProperty("otpType", out var otpType) ? otpType.GetString() ?? "TOTP" : "TOTP",
                root.TryGetProperty("counter", out var counter) ? counter.GetInt64() : 0);
        }
        catch
        {
            return new TotpData("", 30, 6, "TOTP", 0);
        }
    }

    private sealed record TotpData(string Secret, int Period, int Digits, string OtpType, long Counter);
}
