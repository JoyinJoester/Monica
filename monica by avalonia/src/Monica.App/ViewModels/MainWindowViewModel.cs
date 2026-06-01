using System.Collections.ObjectModel;
using System.ComponentModel;
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
    private readonly IPasswordEditorDialogService _passwordEditorDialogService;
    private readonly IPasswordDetailDialogService _passwordDetailDialogService;
    private readonly IVaultCredentialStore _credentialStore;
    private readonly IAppSettingsService _settingsService;
    private readonly ILocalizationService _localization;
    private readonly IReadOnlyList<PlatformCapability> _sourceCapabilities;
    private IReadOnlyDictionary<long, IReadOnlyList<CustomField>> _passwordCustomFields = new Dictionary<long, IReadOnlyList<CustomField>>();
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
        IPasswordEditorDialogService passwordEditorDialogService,
        IPasswordDetailDialogService passwordDetailDialogService,
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
        _passwordEditorDialogService = passwordEditorDialogService;
        _passwordDetailDialogService = passwordDetailDialogService;
        _settingsService = settingsService;
        _localization = localization;
        _localization.PropertyChanged += (_, _) => RefreshLocalizedProperties();
        _sourceCapabilities = platformCapabilityService.GetCapabilities();
        RefreshCapabilities();
        RefreshChoiceLabels();
    }

    public ILocalizationService L => _localization;
    public ObservableCollection<PasswordEntry> Passwords { get; } = [];
    public ObservableCollection<PasswordEntry> DeletedPasswords { get; } = [];
    public ObservableCollection<SecureItem> NoteItems { get; } = [];
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
    private SecureItem? _selectedNote;

    [ObservableProperty]
    private string _noteTitle = "";

    [ObservableProperty]
    private string _noteContent = "";

    [ObservableProperty]
    private string _noteTagsText = "";

    [ObservableProperty]
    private bool _noteIsMarkdown = true;

    [ObservableProperty]
    private bool _notePreviewMode;

    [ObservableProperty]
    private bool _noteIsFavorite;

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
    public string DeletedPasswordCountText => _localization.Format("DeletedPasswordCountFormat", DeletedPasswords.Count);
    public string SelectedPasswordCountText => _localization.Format("SelectedPasswordCountFormat", SelectedPasswordCount);
    public string NoteCountText => _localization.Format("NoteCountFormat", NoteItems.Count);
    public string TotpCountText => _localization.Format("TotpCountFormat", TotpItems.Count);
    public string WalletCountText => _localization.Format("WalletCountFormat", WalletItems.Count);
    public string NotePreviewMarkdown => NoteIsMarkdown ? NoteContent : "";
    public string NotePlainPreview => NoteContentCodec.ToPlainPreview(NoteContent, NoteIsMarkdown);
    public int SelectedPasswordCount => Passwords.Count(item => item.IsSelected);
    public bool HasSelectedPasswords => SelectedPasswordCount > 0;
    public bool AreAllFilteredPasswordsSelected
    {
        get
        {
            var filtered = FilteredPasswords.ToArray();
            return filtered.Length > 0 && filtered.All(item => item.IsSelected);
        }
        set
        {
            foreach (var item in FilteredPasswords)
            {
                item.IsSelected = value;
            }

            RaisePasswordSelectionState();
        }
    }

    public IEnumerable<PasswordEntry> FilteredPasswords =>
        string.IsNullOrWhiteSpace(SearchText)
            ? Passwords
            : Passwords.Where(item => MatchesPasswordSearch(item, SearchText));

    partial void OnSearchTextChanged(string value)
    {
        OnPropertyChanged(nameof(FilteredPasswords));
        RaisePasswordSelectionState();
    }

    partial void OnSelectedSectionChanged(string value) => OnPropertyChanged(nameof(SelectedSectionTitle));

    partial void OnNoteContentChanged(string value)
    {
        OnPropertyChanged(nameof(NotePreviewMarkdown));
        OnPropertyChanged(nameof(NotePlainPreview));
    }

    partial void OnNoteIsMarkdownChanged(bool value)
    {
        OnPropertyChanged(nameof(NotePreviewMarkdown));
        OnPropertyChanged(nameof(NotePlainPreview));
    }

    partial void OnSelectedNoteChanged(SecureItem? value) => LoadNoteIntoEditor(value);

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
            DeletedPasswords.Clear();
            NoteItems.Clear();
            TotpItems.Clear();
            WalletItems.Clear();
            Categories.Clear();
            MdbxDatabases.Clear();

            var passwords = await _repository.GetPasswordsAsync();
            _passwordCustomFields = await _repository.GetCustomFieldsByEntryIdsAsync(passwords.Select(item => item.Id).ToArray());
            foreach (var item in passwords)
            {
                RefreshPasswordTotpDisplay(item);
                item.IsSelected = false;
                TrackPasswordSelection(item);
                Passwords.Add(item);
            }

            foreach (var item in (await _repository.GetPasswordsAsync(includeDeleted: true)).Where(item => item.IsDeleted))
            {
                RefreshPasswordTotpDisplay(item);
                TrackPasswordSelection(item);
                DeletedPasswords.Add(item);
            }

            foreach (var item in await _repository.GetSecureItemsAsync(VaultItemType.Note))
            {
                NoteItems.Add(item);
            }

            foreach (var item in await _repository.GetSecureItemsAsync())
            {
                if (item.ItemType is VaultItemType.BankCard or VaultItemType.Document)
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

            await LoadTotpItemsAsync();
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
        var initialPassword = string.IsNullOrWhiteSpace(GeneratedPassword) ? "" : GeneratedPassword;
        var editor = await _passwordEditorDialogService.ShowAsync(
            null,
            Categories.ToList(),
            initialPassword,
            notes: NoteItems.ToList());
        if (editor is null)
        {
            return;
        }

        var entries = editor
            .BuildEntries(ProtectPasswords(editor.GetPasswordRows()))
            .ToList();
        foreach (var entry in entries)
        {
            await _repository.SavePasswordAsync(entry);
            await _repository.LogAsync(new OperationLog
            {
                ItemType = "PASSWORD",
                ItemId = entry.Id,
                ItemTitle = entry.Title,
                OperationType = "CREATE",
                DeviceName = Environment.MachineName
            });
        }

        var customFields = BindCustomFields(entries[0].Id, editor.GetCustomFields());
        await _repository.ReplaceCustomFieldsAsync(entries[0].Id, customFields);
        SetPasswordCustomFields(entries[0].Id, customFields);
        foreach (var entry in entries)
        {
            RefreshPasswordTotpDisplay(entry);
        }

        await SynchronizeBoundTotpAsync(entries[0]);
        ReplacePasswordGroup([], entries);
        await LoadTotpItemsAsync();
        RaiseCounts();
        OnPropertyChanged(nameof(FilteredPasswords));
        StatusMessage = _localization.Format("CreatedPasswordFormat", entries[0].Title);
    }

    [RelayCommand]
    private async Task EditPasswordAsync(PasswordEntry? entry)
    {
        if (entry is null)
        {
            return;
        }

        var siblings = GetPasswordSiblings(entry).ToList();
        var customFields = await GetGroupCustomFieldsAsync(entry, siblings);
        var editor = await _passwordEditorDialogService.ShowAsync(
            entry,
            Categories.ToList(),
            UnprotectPassword(entry.Password),
            siblings.Select(item => UnprotectPassword(item.Password)).ToArray(),
            NoteItems.ToList(),
            customFields);
        if (editor is null)
        {
            return;
        }

        var passwordRows = editor.GetPasswordRows();
        var storedPasswords = ProtectPasswords(passwordRows);
        var updatedEntries = new List<PasswordEntry>();
        for (var index = 0; index < storedPasswords.Count; index++)
        {
            var source = index < siblings.Count ? siblings[index] : null;
            var updated = editor.BuildEntryFrom(source, storedPasswords[index]);
            await _repository.SavePasswordAsync(updated);
            await _repository.LogAsync(new OperationLog
            {
                ItemType = "PASSWORD",
                ItemId = updated.Id,
                ItemTitle = updated.Title,
                OperationType = source is null ? "CREATE" : "UPDATE",
                DeviceName = Environment.MachineName
            });
            updatedEntries.Add(updated);
        }

        foreach (var removed in siblings.Skip(storedPasswords.Count))
        {
            await _repository.SoftDeletePasswordAsync(removed.Id);
        }

        var updatedCustomFields = BindCustomFields(updatedEntries[0].Id, editor.GetCustomFields());
        await _repository.ReplaceCustomFieldsAsync(updatedEntries[0].Id, updatedCustomFields);
        SetPasswordCustomFields(updatedEntries[0].Id, updatedCustomFields);
        foreach (var updated in updatedEntries)
        {
            RefreshPasswordTotpDisplay(updated);
        }

        await SynchronizeBoundTotpAsync(updatedEntries[0]);
        ReplacePasswordGroup(siblings, updatedEntries);
        await LoadTotpItemsAsync();
        RaiseCounts();
        OnPropertyChanged(nameof(FilteredPasswords));
        StatusMessage = _localization.Format("UpdatedPasswordFormat", updatedEntries[0].Title);
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
    private async Task CopyUsernameAsync(PasswordEntry? entry)
    {
        if (entry is null || string.IsNullOrWhiteSpace(entry.Username))
        {
            return;
        }

        await _clipboardService.SetTextAsync(entry.Username);
        StatusMessage = _localization.Format("CopiedUsernameFormat", entry.Title);
    }

    [RelayCommand]
    private async Task CopyWebsiteAsync(PasswordEntry? entry)
    {
        if (entry is null || string.IsNullOrWhiteSpace(entry.Website))
        {
            return;
        }

        await _clipboardService.SetTextAsync(entry.Website);
        StatusMessage = _localization.Format("CopiedWebsiteFormat", entry.Title);
    }

    [RelayCommand]
    private async Task ShowPasswordDetailsAsync(PasswordEntry? entry)
    {
        if (entry is null)
        {
            return;
        }

        var siblings = (entry.IsDeleted ? GetDeletedPasswordSiblings(entry) : GetPasswordSiblings(entry)).ToList();
        var customFields = await GetGroupCustomFieldsAsync(entry, siblings);
        var category = entry.CategoryId is null
            ? null
            : Categories.FirstOrDefault(item => item.Id == entry.CategoryId);
        var boundNote = entry.BoundNoteId is null
            ? null
            : NoteItems.FirstOrDefault(item => item.Id == entry.BoundNoteId);

        await _passwordDetailDialogService.ShowAsync(entry, siblings, category, boundNote, customFields);
    }

    [RelayCommand]
    private void TogglePasswordSelection(PasswordEntry? entry)
    {
        if (entry is null)
        {
            return;
        }

        entry.IsSelected = !entry.IsSelected;
        RaisePasswordSelectionState();
    }

    [RelayCommand]
    private void ClearPasswordSelection()
    {
        foreach (var entry in Passwords.Where(item => item.IsSelected))
        {
            entry.IsSelected = false;
        }

        RaisePasswordSelectionState();
    }

    [RelayCommand]
    private async Task FavoriteSelectedPasswordsAsync()
    {
        var selected = Passwords.Where(item => item.IsSelected).ToArray();
        foreach (var entry in selected)
        {
            if (!entry.IsFavorite)
            {
                entry.IsFavorite = true;
                await _repository.SavePasswordAsync(entry);
                await _repository.LogAsync(new OperationLog
                {
                    ItemType = "PASSWORD",
                    ItemId = entry.Id,
                    ItemTitle = entry.Title,
                    OperationType = "FAVORITE",
                    DeviceName = Environment.MachineName
                });
            }
        }

        foreach (var entry in selected)
        {
            entry.IsSelected = false;
        }

        RaisePasswordSelectionState();
        OnPropertyChanged(nameof(FilteredPasswords));
        StatusMessage = _localization.Format("FavoritedPasswordCountFormat", selected.Length);
    }

    [RelayCommand]
    private async Task DeleteSelectedPasswordsAsync()
    {
        var selected = Passwords.Where(item => item.IsSelected).ToArray();
        var handled = new HashSet<long>();
        foreach (var entry in selected)
        {
            if (!handled.Add(entry.Id))
            {
                continue;
            }

            var siblings = GetPasswordSiblings(entry).ToArray();
            foreach (var sibling in siblings)
            {
                handled.Add(sibling.Id);
            }

            await DeletePasswordGroupAsync(entry, siblings, updateStatus: false);
        }

        RaisePasswordSelectionState();
        StatusMessage = _localization.Format("MovedSelectedPasswordsToRecycleBinFormat", selected.Length);
    }

    [RelayCommand]
    private async Task CopyPasswordTotpAsync(PasswordEntry? entry)
    {
        if (entry is null)
        {
            return;
        }

        RefreshPasswordTotpDisplay(entry);
        await _clipboardService.SetTextAsync(entry.TotpCode);
        StatusMessage = _localization.Format("CopiedTotpFormat", entry.Title);
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

        var siblings = GetPasswordSiblings(entry).ToList();
        await DeletePasswordGroupAsync(entry, siblings, updateStatus: true);
    }

    private async Task DeletePasswordGroupAsync(PasswordEntry entry, IReadOnlyList<PasswordEntry> siblings, bool updateStatus)
    {
        foreach (var item in siblings)
        {
            await _repository.SoftDeletePasswordAsync(item.Id);
            item.IsSelected = false;
            Passwords.Remove(item);
            var current = Passwords.FirstOrDefault(password => password.Id == item.Id);
            if (current is not null)
            {
                current.IsSelected = false;
                Passwords.Remove(current);
            }

            item.IsDeleted = true;
            item.DeletedAt = DateTimeOffset.UtcNow;
            TrackPasswordSelection(item);
            DeletedPasswords.Insert(0, item);
        }

        await LoadTotpItemsAsync();
        RaiseCounts();
        RaisePasswordSelectionState();
        OnPropertyChanged(nameof(FilteredPasswords));
        if (updateStatus)
        {
            StatusMessage = _localization.Format("MovedToRecycleBinFormat", entry.Title);
        }
    }

    [RelayCommand]
    private async Task RestorePasswordAsync(PasswordEntry? entry)
    {
        if (entry is null)
        {
            return;
        }

        var siblings = GetDeletedPasswordSiblings(entry).ToList();
        foreach (var item in siblings)
        {
            await _repository.RestorePasswordAsync(item.Id);
            DeletedPasswords.Remove(item);
            var current = DeletedPasswords.FirstOrDefault(password => password.Id == item.Id);
            if (current is not null)
            {
                DeletedPasswords.Remove(current);
            }

            item.IsDeleted = false;
            item.DeletedAt = null;
            item.IsSelected = false;
            RefreshPasswordTotpDisplay(item);
        }

        ReplacePasswordGroup([], siblings);
        await LoadTotpItemsAsync();
        RaiseCounts();
        OnPropertyChanged(nameof(FilteredPasswords));
        StatusMessage = _localization.Format("RestoredPasswordFormat", entry.Title);
    }

    [RelayCommand]
    private async Task DeletePasswordPermanentlyAsync(PasswordEntry? entry)
    {
        if (entry is null)
        {
            return;
        }

        var siblings = GetDeletedPasswordSiblings(entry).ToList();
        foreach (var item in siblings)
        {
            await _repository.DeletePasswordPermanentlyAsync(item.Id);
            DeletedPasswords.Remove(item);
            var current = DeletedPasswords.FirstOrDefault(password => password.Id == item.Id);
            if (current is not null)
            {
                DeletedPasswords.Remove(current);
            }
        }

        await LoadTotpItemsAsync();
        RaiseCounts();
        StatusMessage = _localization.Format("DeletedPasswordPermanentlyFormat", entry.Title);
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
    private void AddNote()
    {
        SelectedNote = null;
        NoteTitle = "";
        NoteContent = "";
        NoteTagsText = "";
        NoteIsMarkdown = true;
        NotePreviewMode = false;
        NoteIsFavorite = false;
        StatusMessage = _localization.Get("EditingNewSecureNote");
    }

    [RelayCommand]
    private async Task SaveNoteAsync()
    {
        if (string.IsNullOrWhiteSpace(NoteTitle) && string.IsNullOrWhiteSpace(NoteContent))
        {
            StatusMessage = _localization.Get("NoteRequiresContent");
            return;
        }

        var payload = NoteContentCodec.BuildSavePayload(
            NoteTitle,
            NoteContent,
            NoteTagsText,
            NoteIsMarkdown,
            SelectedNote is null ? [] : NoteContentCodec.DecodeImagePaths(SelectedNote.ImagePaths));

        var item = SelectedNote ?? new SecureItem
        {
            ItemType = VaultItemType.Note,
            CreatedAt = DateTimeOffset.UtcNow
        };

        item.Title = payload.Title;
        item.Notes = payload.NotesCache;
        item.ItemData = payload.ItemData;
        item.ImagePaths = payload.ImagePaths;
        item.IsFavorite = NoteIsFavorite;
        item.ItemType = VaultItemType.Note;
        item.SyncStatus = item.BitwardenVaultId is null ? SyncStatus.None : SyncStatus.Pending;

        await _repository.SaveSecureItemAsync(item);
        await _repository.LogAsync(new OperationLog
        {
            ItemType = "NOTE",
            ItemId = item.Id,
            ItemTitle = item.Title,
            OperationType = SelectedNote is null ? "CREATE" : "UPDATE",
            DeviceName = Environment.MachineName
        });

        if (!NoteItems.Contains(item))
        {
            NoteItems.Insert(0, item);
        }

        SelectedNote = item;
        RaiseCounts();
        StatusMessage = _localization.Format("SavedNoteFormat", item.Title);
    }

    [RelayCommand]
    private async Task ToggleNoteFavoriteAsync()
    {
        NoteIsFavorite = !NoteIsFavorite;
        if (SelectedNote is null)
        {
            return;
        }

        SelectedNote.IsFavorite = NoteIsFavorite;
        await _repository.SaveSecureItemAsync(SelectedNote);
        StatusMessage = _localization.Format("SavedNoteFormat", SelectedNote.Title);
    }

    [RelayCommand]
    private async Task DeleteNoteAsync(SecureItem? item)
    {
        item ??= SelectedNote;
        if (item is null)
        {
            return;
        }

        await _repository.SoftDeleteSecureItemAsync(item.Id);
        NoteItems.Remove(item);
        if (ReferenceEquals(SelectedNote, item) || SelectedNote?.Id == item.Id)
        {
            AddNote();
        }

        RaiseCounts();
        StatusMessage = _localization.Format("MovedToRecycleBinFormat", item.Title);
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
        var data = TotpDataResolver.ParseStoredItemData(item.ItemData, item.Title, item.Notes);
        if (data is null || string.IsNullOrWhiteSpace(data.Secret))
        {
            item.TotpCode = "------";
            item.TotpTimeRemaining = "";
            item.TotpProgress = 0;
            return;
        }

        item.TotpCode = _totpService.GenerateCode(data.Secret, data.Period, data.Digits, data.OtpType, data.Counter);
        item.TotpTimeRemaining = $"{_totpService.GetRemainingSeconds(data.Period)}s";
        item.TotpProgress = _totpService.GetProgress(data.Period);
    }

    private void RaiseCounts()
    {
        OnPropertyChanged(nameof(PasswordCountText));
        OnPropertyChanged(nameof(DeletedPasswordCountText));
        OnPropertyChanged(nameof(NoteCountText));
        OnPropertyChanged(nameof(TotpCountText));
        OnPropertyChanged(nameof(WalletCountText));
    }

    private void RaisePasswordSelectionState()
    {
        OnPropertyChanged(nameof(SelectedPasswordCount));
        OnPropertyChanged(nameof(SelectedPasswordCountText));
        OnPropertyChanged(nameof(HasSelectedPasswords));
        OnPropertyChanged(nameof(AreAllFilteredPasswordsSelected));
    }

    private void TrackPasswordSelection(PasswordEntry entry)
    {
        entry.PropertyChanged -= PasswordEntryPropertyChanged;
        entry.PropertyChanged += PasswordEntryPropertyChanged;
    }

    private void PasswordEntryPropertyChanged(object? sender, PropertyChangedEventArgs e)
    {
        if (e.PropertyName == nameof(PasswordEntry.IsSelected))
        {
            RaisePasswordSelectionState();
        }
    }

    private string ProtectPassword(string password)
    {
        return _cryptoService.IsUnlocked ? _cryptoService.EncryptString(password) : password;
    }

    private IReadOnlyList<string> ProtectPasswords(IReadOnlyList<string> passwords)
    {
        if (passwords.Count == 0)
        {
            return [ProtectPassword("")];
        }

        return passwords.Select(ProtectPassword).ToArray();
    }

    private string UnprotectPassword(string storedPassword)
    {
        if (!_cryptoService.IsUnlocked)
        {
            return storedPassword;
        }

        try
        {
            return _cryptoService.DecryptString(storedPassword);
        }
        catch
        {
            return storedPassword;
        }
    }

    private void ReplacePasswordGroup(IReadOnlyList<PasswordEntry> previousEntries, IReadOnlyList<PasswordEntry> updatedEntries)
    {
        foreach (var previous in previousEntries)
        {
            Passwords.Remove(previous);
            var current = Passwords.FirstOrDefault(item => item.Id == previous.Id);
            if (current is not null)
            {
                Passwords.Remove(current);
            }
        }

        for (var index = updatedEntries.Count - 1; index >= 0; index--)
        {
            updatedEntries[index].IsSelected = false;
            TrackPasswordSelection(updatedEntries[index]);
            Passwords.Insert(0, updatedEntries[index]);
        }

        RaisePasswordSelectionState();
    }

    private static IReadOnlyList<CustomField> BindCustomFields(long entryId, IReadOnlyList<CustomField> fields)
    {
        return fields
            .Select((field, index) => new CustomField
            {
                EntryId = entryId,
                Title = field.Title,
                Value = field.Value,
                IsProtected = field.IsProtected,
                SortOrder = index
            })
            .ToArray();
    }

    private void SetPasswordCustomFields(long entryId, IReadOnlyList<CustomField> fields)
    {
        var next = _passwordCustomFields.ToDictionary(pair => pair.Key, pair => pair.Value);
        if (fields.Count == 0)
        {
            next.Remove(entryId);
        }
        else
        {
            next[entryId] = fields;
        }

        _passwordCustomFields = next;
    }

    private async Task<IReadOnlyList<CustomField>> GetGroupCustomFieldsAsync(PasswordEntry entry, IReadOnlyList<PasswordEntry> siblings)
    {
        foreach (var candidate in siblings)
        {
            var fields = await _repository.GetCustomFieldsAsync(candidate.Id);
            if (fields.Count > 0 || candidate.Id == entry.Id)
            {
                return fields;
            }
        }

        return [];
    }

    private async Task LoadTotpItemsAsync()
    {
        TotpItems.Clear();
        var storedTotps = await _repository.GetSecureItemsAsync(VaultItemType.Totp);
        var seenVirtualPasswordIds = new HashSet<long>();

        foreach (var item in storedTotps)
        {
            RefreshTotpDisplay(item);
            TotpItems.Add(item);
            if (item.BoundPasswordId is { } passwordId)
            {
                seenVirtualPasswordIds.Add(passwordId);
            }
        }

        foreach (var password in Passwords.Where(item => item.HasAuthenticator && !seenVirtualPasswordIds.Contains(item.Id)))
        {
            var virtualItem = BuildVirtualTotpItem(password);
            RefreshTotpDisplay(virtualItem);
            TotpItems.Add(virtualItem);
        }
    }

    private void RefreshPasswordTotpDisplay(PasswordEntry entry)
    {
        var data = TotpDataResolver.FromAuthenticatorKey(entry.AuthenticatorKey, entry.Title, entry.Username);
        if (data is null || string.IsNullOrWhiteSpace(data.Secret))
        {
            entry.TotpCode = "------";
            entry.TotpTimeRemaining = "";
            entry.TotpProgress = 0;
            return;
        }

        entry.TotpCode = _totpService.GenerateCode(data.Secret, data.Period, data.Digits, data.OtpType, data.Counter);
        entry.TotpTimeRemaining = $"{_totpService.GetRemainingSeconds(data.Period)}s";
        entry.TotpProgress = _totpService.GetProgress(data.Period);
    }

    private async Task SynchronizeBoundTotpAsync(PasswordEntry entry)
    {
        var existing = await _repository.GetSecureItemsByBoundPasswordIdAsync(entry.Id, includeDeleted: true);
        var active = existing.Where(item => !item.IsDeleted).OrderBy(item => item.Id).ToArray();
        var data = TotpDataResolver.FromAuthenticatorKey(entry.AuthenticatorKey, entry.Title, entry.Username);
        if (data is null || string.IsNullOrWhiteSpace(data.Secret))
        {
            foreach (var item in active)
            {
                await _repository.SoftDeleteSecureItemAsync(item.Id);
            }

            return;
        }

        var primary = active.FirstOrDefault() ?? existing.OrderBy(item => item.Id).FirstOrDefault() ?? new SecureItem
        {
            ItemType = VaultItemType.Totp,
            CreatedAt = DateTimeOffset.UtcNow
        };

        primary.ItemType = VaultItemType.Totp;
        primary.Title = entry.Title;
        primary.Notes = string.IsNullOrWhiteSpace(data.AccountName) ? entry.Username : data.AccountName;
        primary.ItemData = TotpDataResolver.ToItemData(data);
        primary.BoundPasswordId = entry.Id;
        primary.CategoryId = entry.CategoryId;
        primary.KeepassDatabaseId = entry.KeepassDatabaseId;
        primary.KeepassGroupPath = entry.KeepassGroupPath;
        primary.KeepassEntryUuid = entry.KeepassEntryUuid;
        primary.KeepassGroupUuid = entry.KeepassGroupUuid;
        primary.MdbxDatabaseId = entry.MdbxDatabaseId;
        primary.MdbxFolderId = entry.MdbxFolderId;
        primary.BitwardenVaultId = entry.BitwardenVaultId;
        primary.BitwardenFolderId = entry.BitwardenFolderId;
        primary.BitwardenCipherId = entry.BitwardenCipherId;
        primary.BitwardenRevisionDate = entry.BitwardenRevisionDate;
        primary.BitwardenLocalModified = entry.BitwardenLocalModified;
        primary.IsDeleted = false;
        primary.DeletedAt = null;
        primary.SyncStatus = entry.BitwardenVaultId is null ? SyncStatus.None : SyncStatus.Pending;
        await _repository.SaveSecureItemAsync(primary);

        foreach (var duplicate in active.Skip(1))
        {
            await _repository.SoftDeleteSecureItemAsync(duplicate.Id);
        }
    }

    private static SecureItem BuildVirtualTotpItem(PasswordEntry entry)
    {
        var data = TotpDataResolver.FromAuthenticatorKey(entry.AuthenticatorKey, entry.Title, entry.Username);
        return new SecureItem
        {
            Id = -entry.Id,
            ItemType = VaultItemType.Totp,
            Title = entry.Title,
            Notes = string.IsNullOrWhiteSpace(data?.AccountName) ? entry.Username : data.AccountName,
            ItemData = data is null ? "{}" : TotpDataResolver.ToItemData(data),
            BoundPasswordId = entry.Id,
            CategoryId = entry.CategoryId,
            CreatedAt = entry.CreatedAt,
            UpdatedAt = entry.UpdatedAt
        };
    }

    private bool MatchesPasswordSearch(PasswordEntry item, string query)
    {
        var term = query.Trim();
        if (term.Length == 0)
        {
            return true;
        }

        if (ContainsAny(term,
            item.Title,
            item.Username,
            item.Website,
            item.Notes,
            item.AuthenticatorKey,
            item.AppName,
            item.AppPackageName,
            item.Email,
            item.Phone,
            item.AddressLine,
            item.City,
            item.State,
            item.ZipCode,
            item.Country,
            item.CreditCardHolder,
            item.CreditCardExpiry,
            item.SsoProvider,
            item.PasskeyBindings,
            item.WifiMetadata,
            item.SshKeyData,
            item.KeepassGroupPath ?? "",
            item.MdbxFolderId ?? "",
            item.BitwardenFolderId ?? ""))
        {
            return true;
        }

        return _passwordCustomFields.TryGetValue(item.Id, out var fields) &&
            fields.Any(field => ContainsAny(term, field.Title, field.Value));
    }

    private static bool ContainsAny(string query, params string[] values) =>
        values.Any(value => value.Contains(query, StringComparison.OrdinalIgnoreCase));

    private IEnumerable<PasswordEntry> GetPasswordSiblings(PasswordEntry entry)
    {
        var key = BuildSiblingGroupKey(entry);
        return Passwords
            .Where(item => BuildSiblingGroupKey(item) == key)
            .OrderBy(item => item.Id == 0 ? long.MaxValue : item.Id);
    }

    private IEnumerable<PasswordEntry> GetDeletedPasswordSiblings(PasswordEntry entry)
    {
        var key = BuildSiblingGroupKey(entry);
        return DeletedPasswords
            .Where(item => BuildSiblingGroupKey(item) == key)
            .OrderBy(item => item.Id == 0 ? long.MaxValue : item.Id);
    }

    private static string BuildSiblingGroupKey(PasswordEntry entry)
    {
        var sourceKey = entry.BitwardenCipherId is not null
            ? $"bw:{entry.BitwardenVaultId}:{entry.BitwardenCipherId}"
            : entry.BitwardenVaultId is not null
                ? $"bw-local:{entry.BitwardenVaultId}:{entry.BitwardenFolderId ?? ""}"
                : entry.KeepassDatabaseId is not null
                    ? $"kp:{entry.KeepassDatabaseId}:{entry.KeepassGroupPath ?? ""}"
                    : $"local:{entry.CategoryId?.ToString() ?? "root"}";
        return string.Join("|",
            sourceKey,
            entry.Title.Trim().ToLowerInvariant(),
            NormalizeWebsiteForSiblingGroupKey(entry.Website),
            entry.Username.Trim().ToLowerInvariant());
    }

    private static string NormalizeWebsiteForSiblingGroupKey(string value)
    {
        var normalized = value
            .Trim()
            .ToLowerInvariant();

        if (normalized.StartsWith("http://", StringComparison.Ordinal))
        {
            normalized = normalized["http://".Length..];
        }
        else if (normalized.StartsWith("https://", StringComparison.Ordinal))
        {
            normalized = normalized["https://".Length..];
        }

        if (normalized.StartsWith("www.", StringComparison.Ordinal))
        {
            normalized = normalized["www.".Length..];
        }

        return normalized.TrimEnd('/');
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
        OnPropertyChanged(nameof(NotePreviewMarkdown));
        OnPropertyChanged(nameof(NotePlainPreview));
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
            new("RecycleBin", _localization.RecycleBin),
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
            "RecycleBin" => _localization.RecycleBin,
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

    private void LoadNoteIntoEditor(SecureItem? item)
    {
        if (item is null)
        {
            return;
        }

        var decoded = NoteContentCodec.DecodeFromItem(item);
        NoteTitle = item.Title;
        NoteContent = decoded.Content;
        NoteTagsText = string.Join(", ", decoded.Tags);
        NoteIsMarkdown = decoded.IsMarkdown;
        NoteIsFavorite = item.IsFavorite;
        NotePreviewMode = decoded.IsMarkdown;
        StatusMessage = _localization.Format("EditingNoteFormat", item.Title);
    }
}
