using System.Collections.ObjectModel;
using System.Text.Json;
using Avalonia;
using Avalonia.Styling;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Monica.App.Services;
using Monica.Core.ImportExport;
using Monica.Core.Models;
using Monica.Core.Services;
using Monica.Data;
using Monica.Data.Repositories;
using Monica.Platform.Services;

namespace Monica.App.ViewModels;

public sealed record SettingsChoice(object Value, string Label);
public sealed record LocalizedPlatformCapability(string Key, string Title, string Description, string Status);

public sealed partial class MainWindowViewModel : ObservableObject
{
    private readonly IMonicaRepository _repository;
    private readonly ICryptoService _cryptoService;
    private readonly ITotpService _totpService;
    private readonly IPasswordGeneratorService _passwordGenerator;
    private readonly IImportExportService _importExportService;
    private readonly IClipboardService _clipboardService;
    private readonly IMdbxVaultService _mdbxVaultService;
    private readonly IVaultCredentialStore _credentialStore;
    private readonly IAppSettingsService _settingsService;
    private readonly ILocalizationService _localization;
    private readonly IReadOnlyList<PlatformCapability> _sourceCapabilities;
    private bool _isApplyingSettings;

    public MainWindowViewModel(
        IMonicaRepository repository,
        IVaultCredentialStore credentialStore,
        ICryptoService cryptoService,
        ITotpService totpService,
        IPasswordGeneratorService passwordGenerator,
        IImportExportService importExportService,
        IPlatformCapabilityService platformCapabilityService,
        IClipboardService clipboardService,
        IMdbxVaultService mdbxVaultService,
        IAppSettingsService settingsService,
        ILocalizationService localization)
    {
        _repository = repository;
        _credentialStore = credentialStore;
        _cryptoService = cryptoService;
        _totpService = totpService;
        _passwordGenerator = passwordGenerator;
        _importExportService = importExportService;
        _clipboardService = clipboardService;
        _mdbxVaultService = mdbxVaultService;
        _settingsService = settingsService;
        _localization = localization;
        _localization.PropertyChanged += (_, _) => RefreshLocalizedProperties();
        _sourceCapabilities = platformCapabilityService.GetCapabilities();
        RefreshCapabilities();
        RefreshChoiceLabels();
    }

    public ILocalizationService L => _localization;
    public ObservableCollection<PasswordEntry> Passwords { get; } = [];
    public ObservableCollection<SecureItem> TotpItems { get; } = [];
    public ObservableCollection<SecureItem> WalletItems { get; } = [];
    public ObservableCollection<Category> Categories { get; } = [];
    public ObservableCollection<LocalizedPlatformCapability> Capabilities { get; } = [];
    public ObservableCollection<LocalMdbxDatabase> MdbxDatabases { get; } = [];
    public ObservableCollection<SettingsChoice> LanguageOptions { get; } = [];
    public ObservableCollection<SettingsChoice> ThemeOptions { get; } = [];
    public ObservableCollection<SettingsChoice> StartupSectionOptions { get; } = [];
    public ObservableCollection<SettingsChoice> AutoLockMinuteOptions { get; } = [];
    public ObservableCollection<SettingsChoice> ClipboardSecondOptions { get; } = [];
    public ObservableCollection<SettingsChoice> ConflictStrategyOptions { get; } = [];

    [ObservableProperty]
    private bool _isUnlocked;

    [ObservableProperty]
    private bool _isVaultInitialized;

    [ObservableProperty]
    private string _selectedSection = "Passwords";

    [ObservableProperty]
    private string _masterPassword = "";

    [ObservableProperty]
    private string _confirmMasterPassword = "";

    [ObservableProperty]
    private string _searchText = "";

    [ObservableProperty]
    private string _statusMessage = "Locked";

    [ObservableProperty]
    private string _generatedPassword = "";

    [ObservableProperty]
    private string _exportPreview = "";

    [ObservableProperty]
    private string _settingsLanguage = "system";

    [ObservableProperty]
    private string _settingsTheme = "system";

    [ObservableProperty]
    private string _startupSection = "Passwords";

    [ObservableProperty]
    private bool _autoLockEnabled = true;

    [ObservableProperty]
    private int _autoLockMinutes = 5;

    [ObservableProperty]
    private bool _clearClipboardEnabled = true;

    [ObservableProperty]
    private int _clipboardClearSeconds = 30;

    [ObservableProperty]
    private bool _requirePasswordBeforeExport = true;

    [ObservableProperty]
    private bool _minimizeToTray;

    [ObservableProperty]
    private bool _quickSearchEnabled = true;

    [ObservableProperty]
    private string _quickSearchHotkey = "Ctrl+Shift+Space";

    [ObservableProperty]
    private bool _browserIntegrationEnabled;

    [ObservableProperty]
    private int _browserIntegrationPort = 49152;

    [ObservableProperty]
    private bool _compactPasswordList;

    [ObservableProperty]
    private bool _webDavEnabled;

    [ObservableProperty]
    private string _webDavServerUrl = "";

    [ObservableProperty]
    private string _webDavUsername = "";

    [ObservableProperty]
    private string _webDavRemotePath = "/Monica";

    [ObservableProperty]
    private bool _webDavSyncOnStartup;

    [ObservableProperty]
    private bool _webDavSyncAfterChanges;

    [ObservableProperty]
    private string _syncConflictStrategy = "ask";

    [ObservableProperty]
    private bool _oneDriveEnabled;

    [ObservableProperty]
    private bool _mdbxLocalCacheEnabled = true;

    public string SelectedSectionTitle => SectionTitle(SelectedSection);
    public string LoginTitle => IsVaultInitialized ? _localization.UnlockMonica : _localization.CreateMonicaVault;
    public string LoginDescription => IsVaultInitialized
        ? _localization.UnlockDescription
        : _localization.CreateVaultDescription;
    public string LoginButtonText => IsVaultInitialized ? _localization.Unlock : _localization.CreateVault;

    public string PasswordCountText => _localization.Format("PasswordCountFormat", Passwords.Count);
    public string TotpCountText => _localization.Format("TotpCountFormat", TotpItems.Count);
    public string WalletCountText => _localization.Format("WalletCountFormat", WalletItems.Count);

    public IEnumerable<PasswordEntry> FilteredPasswords =>
        string.IsNullOrWhiteSpace(SearchText)
            ? Passwords
            : Passwords.Where(item =>
                item.Title.Contains(SearchText, StringComparison.OrdinalIgnoreCase) ||
                item.Username.Contains(SearchText, StringComparison.OrdinalIgnoreCase) ||
                item.Website.Contains(SearchText, StringComparison.OrdinalIgnoreCase));

    partial void OnSearchTextChanged(string value) => OnPropertyChanged(nameof(FilteredPasswords));

    partial void OnSelectedSectionChanged(string value) => OnPropertyChanged(nameof(SelectedSectionTitle));

    partial void OnIsVaultInitializedChanged(bool value)
    {
        OnPropertyChanged(nameof(LoginTitle));
        OnPropertyChanged(nameof(LoginDescription));
        OnPropertyChanged(nameof(LoginButtonText));
    }

    partial void OnSettingsLanguageChanged(string value)
    {
        if (_isApplyingSettings)
        {
            return;
        }

        _localization.SetLanguage(value);
        _settingsService.Current.Language = value;
        QueueSaveSettings();
    }

    partial void OnSettingsThemeChanged(string value)
    {
        ApplyTheme(value);
        UpdateSettings(settings => settings.Theme = value);
    }

    partial void OnStartupSectionChanged(string value) => UpdateSettings(settings => settings.StartupSection = value);
    partial void OnAutoLockEnabledChanged(bool value) => UpdateSettings(settings => settings.AutoLockEnabled = value);
    partial void OnAutoLockMinutesChanged(int value) => UpdateSettings(settings => settings.AutoLockMinutes = value);
    partial void OnClearClipboardEnabledChanged(bool value) => UpdateSettings(settings => settings.ClearClipboardEnabled = value);
    partial void OnClipboardClearSecondsChanged(int value) => UpdateSettings(settings => settings.ClipboardClearSeconds = value);
    partial void OnRequirePasswordBeforeExportChanged(bool value) => UpdateSettings(settings => settings.RequirePasswordBeforeExport = value);
    partial void OnMinimizeToTrayChanged(bool value) => UpdateSettings(settings => settings.MinimizeToTray = value);
    partial void OnQuickSearchEnabledChanged(bool value) => UpdateSettings(settings => settings.QuickSearchEnabled = value);
    partial void OnQuickSearchHotkeyChanged(string value) => UpdateSettings(settings => settings.QuickSearchHotkey = value);
    partial void OnBrowserIntegrationEnabledChanged(bool value) => UpdateSettings(settings => settings.BrowserIntegrationEnabled = value);
    partial void OnBrowserIntegrationPortChanged(int value) => UpdateSettings(settings => settings.BrowserIntegrationPort = value);
    partial void OnCompactPasswordListChanged(bool value) => UpdateSettings(settings => settings.CompactPasswordList = value);
    partial void OnWebDavEnabledChanged(bool value) => UpdateSettings(settings => settings.WebDavEnabled = value);
    partial void OnWebDavServerUrlChanged(string value) => UpdateSettings(settings => settings.WebDavServerUrl = value);
    partial void OnWebDavUsernameChanged(string value) => UpdateSettings(settings => settings.WebDavUsername = value);
    partial void OnWebDavRemotePathChanged(string value) => UpdateSettings(settings => settings.WebDavRemotePath = value);
    partial void OnWebDavSyncOnStartupChanged(bool value) => UpdateSettings(settings => settings.WebDavSyncOnStartup = value);
    partial void OnWebDavSyncAfterChangesChanged(bool value) => UpdateSettings(settings => settings.WebDavSyncAfterChanges = value);
    partial void OnSyncConflictStrategyChanged(string value) => UpdateSettings(settings => settings.SyncConflictStrategy = value);
    partial void OnOneDriveEnabledChanged(bool value) => UpdateSettings(settings => settings.OneDriveEnabled = value);
    partial void OnMdbxLocalCacheEnabledChanged(bool value) => UpdateSettings(settings => settings.MdbxLocalCacheEnabled = value);

    [RelayCommand]
    public async Task InitializeAsync()
    {
        try
        {
            await _settingsService.LoadAsync();
            ApplySettings(_settingsService.Current);
            IsVaultInitialized = await _credentialStore.GetAsync() is not null;
            StatusMessage = IsVaultInitialized
                ? _localization.Get("VaultLocked")
                : _localization.Get("FirstRunCreateMasterPassword");
        }
        catch (Exception ex)
        {
            StatusMessage = _localization.Format("VaultMetadataLoadFailedFormat", ex.Message);
        }
    }

    [RelayCommand]
    private async Task UnlockAsync()
    {
        try
        {
            if (string.IsNullOrWhiteSpace(MasterPassword))
            {
                StatusMessage = _localization.Get("EnterMasterPassword");
                return;
            }

            var storedHash = await _credentialStore.GetAsync();
            if (storedHash is null)
            {
                if (MasterPassword.Length < 8)
                {
                    StatusMessage = _localization.Get("MasterPasswordMinLength");
                    return;
                }

                if (!string.Equals(MasterPassword, ConfirmMasterPassword, StringComparison.Ordinal))
                {
                    StatusMessage = _localization.Get("ConfirmationMismatch");
                    return;
                }

                storedHash = _cryptoService.HashMasterPassword(MasterPassword);
                await _credentialStore.SaveAsync(storedHash);
                IsVaultInitialized = true;
            }

            if (!_cryptoService.VerifyMasterPassword(MasterPassword, storedHash))
            {
                StatusMessage = _localization.Get("WrongMasterPassword");
                MasterPassword = "";
                ConfirmMasterPassword = "";
                return;
            }

            IsUnlocked = true;
            MasterPassword = "";
            ConfirmMasterPassword = "";
            StatusMessage = _localization.Get("VaultUnlocked");
            await LoadAsync();
        }
        catch (Exception ex)
        {
            IsUnlocked = false;
            StatusMessage = _localization.Format("UnlockFailedFormat", ex.Message);
        }
    }

    [RelayCommand]
    public async Task LoadAsync()
    {
        try
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

            RaiseCounts();
            OnPropertyChanged(nameof(FilteredPasswords));
        }
        catch (Exception ex)
        {
            IsUnlocked = false;
            StatusMessage = _localization.Format("VaultLoadFailedFormat", ex.Message);
        }
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
        StatusMessage = _localization.Format("CreatedPasswordFormat", entry.Title);
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
        StatusMessage = _localization.Format("CopiedPasswordFormat", entry.Title);
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
        StatusMessage = _localization.Format("MovedToRecycleBinFormat", entry.Title);
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
        StatusMessage = _localization.Format("CopiedTotpFormat", item.Title);
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
        StatusMessage = _localization.Get("GeneratedPassword");
    }

    [RelayCommand]
    private void ExportData()
    {
        ExportPreview = _importExportService.ExportJson(Passwords, TotpItems.Concat(WalletItems));
        StatusMessage = _localization.Get("ExportPrepared");
    }

    [RelayCommand]
    private async Task CreateMdbxVaultAsync()
    {
        var root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Monica", "mdbx");
        var metadata = await _mdbxVaultService.CreateLocalMetadataAsync("Local Monica Vault", Path.Combine(root, "local.mdbx"));
        await _repository.SaveMdbxDatabaseAsync(metadata);
        MdbxDatabases.Add(metadata);
        StatusMessage = _localization.Get("CreatedMdbxMetadata");
    }

    public void RefreshTotpDisplay(SecureItem item)
    {
        var data = ParseTotpData(item.ItemData);
        item.TotpCode = _totpService.GenerateCode(data.Secret, data.Period, data.Digits, data.OtpType, data.Counter);
        item.TotpTimeRemaining = $"{_totpService.GetRemainingSeconds(data.Period)}s";
        item.TotpProgress = _totpService.GetProgress(data.Period);
    }

    private void RaiseCounts()
    {
        OnPropertyChanged(nameof(PasswordCountText));
        OnPropertyChanged(nameof(TotpCountText));
        OnPropertyChanged(nameof(WalletCountText));
    }

    private void ApplySettings(DesktopAppSettings settings)
    {
        _isApplyingSettings = true;
        try
        {
            _localization.SetLanguage(settings.Language);
            SettingsLanguage = settings.Language;
            SettingsTheme = settings.Theme;
            StartupSection = settings.StartupSection;
            AutoLockEnabled = settings.AutoLockEnabled;
            AutoLockMinutes = settings.AutoLockMinutes;
            ClearClipboardEnabled = settings.ClearClipboardEnabled;
            ClipboardClearSeconds = settings.ClipboardClearSeconds;
            RequirePasswordBeforeExport = settings.RequirePasswordBeforeExport;
            MinimizeToTray = settings.MinimizeToTray;
            QuickSearchEnabled = settings.QuickSearchEnabled;
            QuickSearchHotkey = settings.QuickSearchHotkey;
            BrowserIntegrationEnabled = settings.BrowserIntegrationEnabled;
            BrowserIntegrationPort = settings.BrowserIntegrationPort;
            CompactPasswordList = settings.CompactPasswordList;
            WebDavEnabled = settings.WebDavEnabled;
            WebDavServerUrl = settings.WebDavServerUrl;
            WebDavUsername = settings.WebDavUsername;
            WebDavRemotePath = settings.WebDavRemotePath;
            WebDavSyncOnStartup = settings.WebDavSyncOnStartup;
            WebDavSyncAfterChanges = settings.WebDavSyncAfterChanges;
            SyncConflictStrategy = settings.SyncConflictStrategy;
            OneDriveEnabled = settings.OneDriveEnabled;
            MdbxLocalCacheEnabled = settings.MdbxLocalCacheEnabled;
            ApplyTheme(settings.Theme);
            RefreshLocalizedProperties();
        }
        finally
        {
            _isApplyingSettings = false;
        }
    }

    private void UpdateSettings(Action<DesktopAppSettings> update)
    {
        if (_isApplyingSettings)
        {
            return;
        }

        update(_settingsService.Current);
        QueueSaveSettings();
    }

    private void QueueSaveSettings()
    {
        _ = SaveSettingsAsync();
    }

    private async Task SaveSettingsAsync()
    {
        try
        {
            await _settingsService.SaveAsync();
            StatusMessage = _localization.Get("SettingsSaved");
        }
        catch (Exception ex)
        {
            StatusMessage = _localization.Format("VaultMetadataLoadFailedFormat", ex.Message);
        }
    }

    private void RefreshLocalizedProperties()
    {
        RefreshChoiceLabels();
        RefreshCapabilities();
        OnPropertyChanged(nameof(SelectedSectionTitle));
        OnPropertyChanged(nameof(LoginTitle));
        OnPropertyChanged(nameof(LoginDescription));
        OnPropertyChanged(nameof(LoginButtonText));
        RaiseCounts();
    }

    private void RefreshChoiceLabels()
    {
        ReplaceOptions(LanguageOptions,
            new("system", _localization.GetLanguageName("system")),
            new("en-US", _localization.GetLanguageName("en-US")),
            new("zh-CN", _localization.GetLanguageName("zh-CN")));

        ReplaceOptions(ThemeOptions,
            new("system", _localization.Get("SystemDefault")),
            new("light", _localization.Get("Light")),
            new("dark", _localization.Get("Dark")));

        ReplaceOptions(StartupSectionOptions,
            new("Passwords", _localization.Passwords),
            new("Notes", _localization.SecureNotes),
            new("Totp", _localization.Totp),
            new("Cards", _localization.Cards),
            new("Generator", _localization.Generator),
            new("Sync", _localization.SyncAndBackup),
            new("Settings", _localization.Settings));

        ReplaceOptions(AutoLockMinuteOptions,
            new(1, _localization.Format("MinuteFormat", 1)),
            new(5, _localization.Format("MinuteFormat", 5)),
            new(15, _localization.Format("MinuteFormat", 15)),
            new(30, _localization.Format("MinuteFormat", 30)),
            new(60, _localization.Format("MinuteFormat", 60)));

        ReplaceOptions(ClipboardSecondOptions,
            new(10, _localization.Format("SecondFormat", 10)),
            new(30, _localization.Format("SecondFormat", 30)),
            new(60, _localization.Format("SecondFormat", 60)),
            new(120, _localization.Format("SecondFormat", 120)));

        ReplaceOptions(ConflictStrategyOptions,
            new("ask", _localization.Get("AskEveryTime")),
            new("local-wins", _localization.Get("LocalWins")),
            new("remote-wins", _localization.Get("RemoteWins")));
    }

    private static void ReplaceOptions(ObservableCollection<SettingsChoice> target, params SettingsChoice[] choices)
    {
        target.Clear();
        foreach (var choice in choices)
        {
            target.Add(choice);
        }
    }

    private void RefreshCapabilities()
    {
        Capabilities.Clear();
        foreach (var capability in _sourceCapabilities)
        {
            Capabilities.Add(new LocalizedPlatformCapability(
                capability.Key,
                _localization.Get($"Capability.{capability.Key}.Title"),
                _localization.Get($"Capability.{capability.Key}.Description"),
                LocalizeFeatureStatus(capability.Status)));
        }
    }

    private string LocalizeFeatureStatus(PlatformFeatureStatus status)
    {
        return status switch
        {
            PlatformFeatureStatus.Available => _localization.Available,
            PlatformFeatureStatus.DesktopEquivalent => _localization.DesktopEquivalent,
            PlatformFeatureStatus.PlatformLimited => _localization.PlatformLimited,
            PlatformFeatureStatus.Planned => _localization.Planned,
            _ => status.ToString()
        };
    }

    private string SectionTitle(string section)
    {
        return section switch
        {
            "Passwords" => _localization.Passwords,
            "Notes" => _localization.SecureNotes,
            "Totp" => _localization.Totp,
            "Cards" => _localization.Cards,
            "Generator" => _localization.Generator,
            "Sync" => _localization.SyncAndBackup,
            "Settings" => _localization.Settings,
            _ => section
        };
    }

    private static void ApplyTheme(string theme)
    {
        if (Application.Current is null)
        {
            return;
        }

        Application.Current.RequestedThemeVariant = theme switch
        {
            "light" => ThemeVariant.Light,
            "dark" => ThemeVariant.Dark,
            _ => ThemeVariant.Default
        };
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
