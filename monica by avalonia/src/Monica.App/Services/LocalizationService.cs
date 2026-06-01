using System.ComponentModel;
using System.Globalization;
using System.Runtime.CompilerServices;

namespace Monica.App.Services;

public interface ILocalizationService : INotifyPropertyChanged
{
    string SelectedLanguage { get; }
    CultureInfo Culture { get; }
    string Get(string key);
    string Format(string key, params object[] args);
    string GetLanguageName(string language);
    void SetLanguage(string language);

    string Passwords { get; }
    string SecureNotes { get; }
    string Totp { get; }
    string Cards { get; }
    string Generator { get; }
    string RecycleBin { get; }
    string SyncAndBackup { get; }
    string Settings { get; }
    string Folders { get; }
    string Personal { get; }
    string Refresh { get; }
    string Export { get; }
    string UnlockMonica { get; }
    string CreateMonicaVault { get; }
    string UnlockDescription { get; }
    string CreateVaultDescription { get; }
    string MasterPasswordWatermark { get; }
    string ConfirmMasterPasswordWatermark { get; }
    string Unlock { get; }
    string CreateVault { get; }
    string PasswordManager { get; }
    string DeletedPasswords { get; }
    string Search { get; }
    string AddPassword { get; }
    string EditPassword { get; }
    string PasswordDetails { get; }
    string Favorite { get; }
    string Copy { get; }
    string CopyPassword { get; }
    string CopyUsername { get; }
    string CopyWebsite { get; }
    string BatchFavorite { get; }
    string BatchDelete { get; }
    string MoveToRecycleBin { get; }
    string RestorePassword { get; }
    string DeletePermanently { get; }
    string Save { get; }
    string Cancel { get; }
    string NoFolder { get; }
    string NewPassword { get; }
    string PasswordTitleRequired { get; }
    string PasswordValueRequired { get; }
    string PasswordTitle { get; }
    string Website { get; }
    string Username { get; }
    string Password { get; }
    string Category { get; }
    string BoundNote { get; }
    string SecurityVerification { get; }
    string AuthenticatorKey { get; }
    string AuthenticatorKeyHint { get; }
    string TotpCode { get; }
    string RemainingTime { get; }
    string Issuer { get; }
    string Account { get; }
    string TotpSecret { get; }
    string AppBinding { get; }
    string AppName { get; }
    string AppPackageName { get; }
    string NoBoundNote { get; }
    string Untitled { get; }
    string PersonalInfo { get; }
    string Email { get; }
    string Phone { get; }
    string AddressLine { get; }
    string City { get; }
    string State { get; }
    string ZipCode { get; }
    string Country { get; }
    string CardInfo { get; }
    string CreditCardNumber { get; }
    string CreditCardHolder { get; }
    string CreditCardExpiry { get; }
    string CreditCardCvv { get; }
    string AdvancedLogin { get; }
    string LoginType { get; }
    string LoginTypePassword { get; }
    string LoginTypeSso { get; }
    string LoginTypeWifi { get; }
    string LoginTypeSshKey { get; }
    string SsoProvider { get; }
    string PasskeyBindings { get; }
    string WifiMetadata { get; }
    string SshKeyData { get; }
    string CustomFields { get; }
    string CustomFieldsHint { get; }
    string Notes { get; }
    string SourceMetadata { get; }
    string CreatedAt { get; }
    string UpdatedAt { get; }
    string Close { get; }
    string TwoStepVerification { get; }
    string AddAuthenticator { get; }
    string CopyCode { get; }
    string Wallet { get; }
    string AddItem { get; }
    string DesktopEquivalents { get; }
    string DesktopEquivalentsMessage { get; }
    string CreateMdbxMetadata { get; }
    string FeatureParityMap { get; }
    string FeatureParityMapDescription { get; }
    string ExportPreview { get; }
    string PasswordGenerator { get; }
    string Generate { get; }
    string SaveAsLogin { get; }
    string SecureNotesDescription { get; }
    string CreateSecureItem { get; }
    string NewSecureNote { get; }
    string NoteTitleWatermark { get; }
    string NoteTagsWatermark { get; }
    string NoteContentWatermark { get; }
    string PlainText { get; }
    string Edit { get; }
    string Preview { get; }
    string SaveNote { get; }
    string SettingsSubtitle { get; }
    string General { get; }
    string GeneralSettingsDescription { get; }
    string Language { get; }
    string LanguageDescription { get; }
    string Theme { get; }
    string ThemeDescription { get; }
    string StartupView { get; }
    string StartupViewDescription { get; }
    string Security { get; }
    string SecuritySettingsDescription { get; }
    string AutoLock { get; }
    string AutoLockDescription { get; }
    string AutoLockAfter { get; }
    string AutoLockAfterDescription { get; }
    string ClearClipboard { get; }
    string ClearClipboardDescription { get; }
    string ClearClipboardAfter { get; }
    string ClearClipboardAfterDescription { get; }
    string RequirePasswordBeforeExport { get; }
    string RequirePasswordBeforeExportDescription { get; }
    string Desktop { get; }
    string DesktopSettingsDescription { get; }
    string MinimizeToTray { get; }
    string MinimizeToTrayDescription { get; }
    string QuickSearch { get; }
    string QuickSearchDescription { get; }
    string QuickSearchHotkey { get; }
    string QuickSearchHotkeyDescription { get; }
    string BrowserIntegration { get; }
    string BrowserIntegrationDescription { get; }
    string BrowserIntegrationPort { get; }
    string BrowserIntegrationPortDescription { get; }
    string CompactPasswordList { get; }
    string CompactPasswordListDescription { get; }
    string SyncSubtitle { get; }
    string WebDav { get; }
    string EnableWebDav { get; }
    string WebDavServerUrl { get; }
    string WebDavUsername { get; }
    string WebDavRemotePath { get; }
    string SyncOnStartup { get; }
    string SyncAfterChanges { get; }
    string ConflictStrategy { get; }
    string OneDrive { get; }
    string EnableOneDrive { get; }
    string MdbxLocalCache { get; }
    string Available { get; }
    string DesktopEquivalent { get; }
    string PlatformLimited { get; }
    string Planned { get; }
}

public sealed class LocalizationService : ILocalizationService
{
    private const string SystemLanguage = "system";
    private string _selectedLanguage = SystemLanguage;
    private Dictionary<string, string> _strings = English;

    public event PropertyChangedEventHandler? PropertyChanged;

    public string SelectedLanguage => _selectedLanguage;
    public CultureInfo Culture { get; private set; } = CultureInfo.CurrentUICulture;

    public void SetLanguage(string language)
    {
        _selectedLanguage = NormalizeLanguage(language);
        Culture = ResolveCulture(_selectedLanguage);
        CultureInfo.CurrentUICulture = Culture;
        _strings = Culture.Name.StartsWith("zh", StringComparison.OrdinalIgnoreCase) ? Chinese : English;
        PropertyChanged?.Invoke(this, new PropertyChangedEventArgs(null));
    }

    public string Get(string key) => _strings.TryGetValue(key, out var value)
        ? value
        : English.TryGetValue(key, out var english)
            ? english
            : key;

    public string Format(string key, params object[] args) => string.Format(Culture, Get(key), args);

    public string GetLanguageName(string language)
    {
        return NormalizeLanguage(language) switch
        {
            "en-US" => Get("English"),
            "zh-CN" => Get("SimplifiedChinese"),
            _ => Get("SystemDefault")
        };
    }

    public string Passwords => Text();
    public string SecureNotes => Text();
    public string Totp => Text();
    public string Cards => Text();
    public string Generator => Text();
    public string RecycleBin => Text();
    public string SyncAndBackup => Text();
    public string Settings => Text();
    public string Folders => Text();
    public string Personal => Text();
    public string Refresh => Text();
    public string Export => Text();
    public string UnlockMonica => Text();
    public string CreateMonicaVault => Text();
    public string UnlockDescription => Text();
    public string CreateVaultDescription => Text();
    public string MasterPasswordWatermark => Text();
    public string ConfirmMasterPasswordWatermark => Text();
    public string Unlock => Text();
    public string CreateVault => Text();
    public string PasswordManager => Text();
    public string DeletedPasswords => Text();
    public string Search => Text();
    public string AddPassword => Text();
    public string EditPassword => Text();
    public string PasswordDetails => Text();
    public string Favorite => Text();
    public string Copy => Text();
    public string CopyPassword => Text();
    public string CopyUsername => Text();
    public string CopyWebsite => Text();
    public string BatchFavorite => Text();
    public string BatchDelete => Text();
    public string MoveToRecycleBin => Text();
    public string RestorePassword => Text();
    public string DeletePermanently => Text();
    public string Save => Text();
    public string Cancel => Text();
    public string NoFolder => Text();
    public string NewPassword => Text();
    public string PasswordTitleRequired => Text();
    public string PasswordValueRequired => Text();
    public string PasswordTitle => Text();
    public string Website => Text();
    public string Username => Text();
    public string Password => Text();
    public string Category => Text();
    public string BoundNote => Text();
    public string SecurityVerification => Text();
    public string AuthenticatorKey => Text();
    public string AuthenticatorKeyHint => Text();
    public string TotpCode => Text();
    public string RemainingTime => Text();
    public string Issuer => Text();
    public string Account => Text();
    public string TotpSecret => Text();
    public string AppBinding => Text();
    public string AppName => Text();
    public string AppPackageName => Text();
    public string NoBoundNote => Text();
    public string Untitled => Text();
    public string PersonalInfo => Text();
    public string Email => Text();
    public string Phone => Text();
    public string AddressLine => Text();
    public string City => Text();
    public string State => Text();
    public string ZipCode => Text();
    public string Country => Text();
    public string CardInfo => Text();
    public string CreditCardNumber => Text();
    public string CreditCardHolder => Text();
    public string CreditCardExpiry => Text();
    public string CreditCardCvv => Text();
    public string AdvancedLogin => Text();
    public string LoginType => Text();
    public string LoginTypePassword => Text();
    public string LoginTypeSso => Text();
    public string LoginTypeWifi => Text();
    public string LoginTypeSshKey => Text();
    public string SsoProvider => Text();
    public string PasskeyBindings => Text();
    public string WifiMetadata => Text();
    public string SshKeyData => Text();
    public string CustomFields => Text();
    public string CustomFieldsHint => Text();
    public string Notes => Text();
    public string SourceMetadata => Text();
    public string CreatedAt => Text();
    public string UpdatedAt => Text();
    public string Close => Text();
    public string TwoStepVerification => Text();
    public string AddAuthenticator => Text();
    public string CopyCode => Text();
    public string Wallet => Text();
    public string AddItem => Text();
    public string DesktopEquivalents => Text();
    public string DesktopEquivalentsMessage => Text();
    public string CreateMdbxMetadata => Text();
    public string FeatureParityMap => Text();
    public string FeatureParityMapDescription => Text();
    public string ExportPreview => Text();
    public string PasswordGenerator => Text();
    public string Generate => Text();
    public string SaveAsLogin => Text();
    public string SecureNotesDescription => Text();
    public string CreateSecureItem => Text();
    public string NewSecureNote => Text();
    public string NoteTitleWatermark => Text();
    public string NoteTagsWatermark => Text();
    public string NoteContentWatermark => Text();
    public string PlainText => Text();
    public string Edit => Text();
    public string Preview => Text();
    public string SaveNote => Text();
    public string SettingsSubtitle => Text();
    public string General => Text();
    public string GeneralSettingsDescription => Text();
    public string Language => Text();
    public string LanguageDescription => Text();
    public string Theme => Text();
    public string ThemeDescription => Text();
    public string StartupView => Text();
    public string StartupViewDescription => Text();
    public string Security => Text();
    public string SecuritySettingsDescription => Text();
    public string AutoLock => Text();
    public string AutoLockDescription => Text();
    public string AutoLockAfter => Text();
    public string AutoLockAfterDescription => Text();
    public string ClearClipboard => Text();
    public string ClearClipboardDescription => Text();
    public string ClearClipboardAfter => Text();
    public string ClearClipboardAfterDescription => Text();
    public string RequirePasswordBeforeExport => Text();
    public string RequirePasswordBeforeExportDescription => Text();
    public string Desktop => Text();
    public string DesktopSettingsDescription => Text();
    public string MinimizeToTray => Text();
    public string MinimizeToTrayDescription => Text();
    public string QuickSearch => Text();
    public string QuickSearchDescription => Text();
    public string QuickSearchHotkey => Text();
    public string QuickSearchHotkeyDescription => Text();
    public string BrowserIntegration => Text();
    public string BrowserIntegrationDescription => Text();
    public string BrowserIntegrationPort => Text();
    public string BrowserIntegrationPortDescription => Text();
    public string CompactPasswordList => Text();
    public string CompactPasswordListDescription => Text();
    public string SyncSubtitle => Text();
    public string WebDav => Text();
    public string EnableWebDav => Text();
    public string WebDavServerUrl => Text();
    public string WebDavUsername => Text();
    public string WebDavRemotePath => Text();
    public string SyncOnStartup => Text();
    public string SyncAfterChanges => Text();
    public string ConflictStrategy => Text();
    public string OneDrive => Text();
    public string EnableOneDrive => Text();
    public string MdbxLocalCache => Text();
    public string Available => Text();
    public string DesktopEquivalent => Text();
    public string PlatformLimited => Text();
    public string Planned => Text();

    private string Text([CallerMemberName] string key = "") => Get(key);

    private static string NormalizeLanguage(string? language)
    {
        return language switch
        {
            "en-US" or "zh-CN" => language,
            _ => SystemLanguage
        };
    }

    private static CultureInfo ResolveCulture(string language)
    {
        if (language == SystemLanguage)
        {
            return CultureInfo.CurrentUICulture.Name.StartsWith("zh", StringComparison.OrdinalIgnoreCase)
                ? CultureInfo.GetCultureInfo("zh-CN")
                : CultureInfo.GetCultureInfo("en-US");
        }

        return CultureInfo.GetCultureInfo(language);
    }

    private static readonly Dictionary<string, string> English = new()
    {
        ["Passwords"] = "Passwords",
        ["SecureNotes"] = "Secure Notes",
        ["Totp"] = "TOTP",
        ["Cards"] = "Cards",
        ["Generator"] = "Generator",
        ["RecycleBin"] = "Recycle Bin",
        ["SyncAndBackup"] = "Sync and Backup",
        ["Settings"] = "Settings",
        ["Folders"] = "Folders",
        ["Personal"] = "Personal",
        ["Refresh"] = "Refresh",
        ["Export"] = "Export",
        ["UnlockMonica"] = "Unlock Monica",
        ["CreateMonicaVault"] = "Create Monica Vault",
        ["UnlockDescription"] = "Use your master password to open the Avalonia desktop vault.",
        ["CreateVaultDescription"] = "Choose a master password. It will be required every time this desktop vault opens.",
        ["MasterPasswordWatermark"] = "Master password",
        ["ConfirmMasterPasswordWatermark"] = "Confirm master password",
        ["Unlock"] = "Unlock",
        ["CreateVault"] = "Create Vault",
        ["PasswordManager"] = "Password Manager",
        ["DeletedPasswords"] = "Deleted Passwords",
        ["Search"] = "Search...",
        ["AddPassword"] = "Add Password",
        ["EditPassword"] = "Edit Password",
        ["PasswordDetails"] = "Password Details",
        ["Favorite"] = "Favorite",
        ["Copy"] = "Copy",
        ["CopyPassword"] = "Copy password",
        ["CopyUsername"] = "Copy username",
        ["CopyWebsite"] = "Copy website",
        ["BatchFavorite"] = "Favorite selected",
        ["BatchDelete"] = "Delete selected",
        ["MoveToRecycleBin"] = "Move to recycle bin",
        ["RestorePassword"] = "Restore password",
        ["DeletePermanently"] = "Delete permanently",
        ["Save"] = "Save",
        ["Cancel"] = "Cancel",
        ["NoFolder"] = "No folder",
        ["NewPassword"] = "New Password",
        ["PasswordTitleRequired"] = "Enter a title for this password.",
        ["PasswordValueRequired"] = "Enter a password value.",
        ["PasswordTitle"] = "Title",
        ["Website"] = "Website",
        ["Username"] = "Username",
        ["Password"] = "Password",
        ["Category"] = "Category",
        ["BoundNote"] = "Bound note",
        ["SecurityVerification"] = "Security verification",
        ["AuthenticatorKey"] = "Authenticator secret",
        ["AuthenticatorKeyHint"] = "Optional TOTP secret from the Android authenticator field. QR import and multi-password storage will be layered onto this same model.",
        ["TotpCode"] = "TOTP code",
        ["RemainingTime"] = "Remaining time",
        ["Issuer"] = "Issuer",
        ["Account"] = "Account",
        ["TotpSecret"] = "TOTP secret",
        ["AppBinding"] = "App binding",
        ["AppName"] = "App name",
        ["AppPackageName"] = "App package or bundle id",
        ["NoBoundNote"] = "No bound note",
        ["Untitled"] = "Untitled",
        ["PersonalInfo"] = "Personal information",
        ["Email"] = "Email",
        ["Phone"] = "Phone",
        ["AddressLine"] = "Address",
        ["City"] = "City",
        ["State"] = "State or province",
        ["ZipCode"] = "ZIP or postal code",
        ["Country"] = "Country",
        ["CardInfo"] = "Card information",
        ["CreditCardNumber"] = "Card number",
        ["CreditCardHolder"] = "Cardholder name",
        ["CreditCardExpiry"] = "Expiry",
        ["CreditCardCvv"] = "CVV",
        ["AdvancedLogin"] = "Advanced login",
        ["LoginType"] = "Login type",
        ["LoginTypePassword"] = "Password",
        ["LoginTypeSso"] = "SSO",
        ["LoginTypeWifi"] = "Wi-Fi",
        ["LoginTypeSshKey"] = "SSH key",
        ["SsoProvider"] = "SSO provider",
        ["PasskeyBindings"] = "Passkey bindings",
        ["WifiMetadata"] = "Wi-Fi metadata",
        ["SshKeyData"] = "SSH key data",
        ["CustomFields"] = "Custom fields",
        ["CustomFieldsHint"] = "One field per line. Use Title=Value, and prefix the title with ! for protected fields.",
        ["Notes"] = "Notes",
        ["SourceMetadata"] = "Source metadata",
        ["CreatedAt"] = "Created",
        ["UpdatedAt"] = "Updated",
        ["Close"] = "Close",
        ["TwoStepVerification"] = "Two-Step Verification",
        ["AddAuthenticator"] = "Add Authenticator",
        ["CopyCode"] = "Copy code",
        ["Wallet"] = "Wallet",
        ["AddItem"] = "Add Item",
        ["DesktopEquivalents"] = "Desktop equivalents",
        ["DesktopEquivalentsMessage"] = "Android Autofill, IME, Accessibility and Credential Provider features are represented through quick search, clipboard, tray/browser extension boundaries, or platform-limited status.",
        ["CreateMdbxMetadata"] = "Create MDBX Metadata",
        ["FeatureParityMap"] = "Feature parity map",
        ["ExportPreview"] = "Export Preview",
        ["PasswordGenerator"] = "Password Generator",
        ["Generate"] = "Generate",
        ["SaveAsLogin"] = "Save as Login",
        ["SecureNotesDescription"] = "Notes are stored as secure_items with NOTE item type and share the same encryption, folder, KeePass, Bitwarden and MDBX ownership model.",
        ["CreateSecureItem"] = "Create Secure Item",
        ["NewSecureNote"] = "New Note",
        ["NoteTitleWatermark"] = "Title",
        ["NoteTagsWatermark"] = "Tags, separated by commas",
        ["NoteContentWatermark"] = "Write a private note...",
        ["PlainText"] = "Plain text",
        ["Edit"] = "Edit",
        ["Preview"] = "Preview",
        ["SaveNote"] = "Save Note",
        ["SettingsSubtitle"] = "Configure Monica desktop behavior, security, appearance and integration options.",
        ["General"] = "General",
        ["GeneralSettingsDescription"] = "Language, visual theme, and the page shown after unlock.",
        ["Language"] = "Language",
        ["LanguageDescription"] = "Choose the display language used by Monica desktop.",
        ["Theme"] = "Theme",
        ["ThemeDescription"] = "Follow the system theme or force a light or dark appearance.",
        ["StartupView"] = "Startup view",
        ["StartupViewDescription"] = "Choose the first page shown after the vault is unlocked.",
        ["Security"] = "Security",
        ["SecuritySettingsDescription"] = "Locking, clipboard, and export confirmation controls.",
        ["AutoLock"] = "Auto lock",
        ["AutoLockDescription"] = "Lock the vault after a period of desktop inactivity.",
        ["AutoLockAfter"] = "Auto-lock after",
        ["AutoLockAfterDescription"] = "Set how long Monica waits before locking an inactive vault.",
        ["ClearClipboard"] = "Clear clipboard",
        ["ClearClipboardDescription"] = "Remove copied passwords and TOTP codes after a timeout.",
        ["ClearClipboardAfter"] = "Clear after",
        ["ClearClipboardAfterDescription"] = "Set how long copied sensitive values remain on the clipboard.",
        ["RequirePasswordBeforeExport"] = "Require master password before export",
        ["RequirePasswordBeforeExportDescription"] = "Ask for the master password before preparing export data.",
        ["Desktop"] = "Desktop",
        ["DesktopSettingsDescription"] = "Desktop-only controls for tray, search, browser bridge, and list density.",
        ["MinimizeToTray"] = "Minimize to tray",
        ["MinimizeToTrayDescription"] = "Keep Monica available from the system tray when the window is closed or minimized.",
        ["QuickSearch"] = "Quick search overlay",
        ["QuickSearchDescription"] = "Enable a desktop search entry point for credentials and secure notes.",
        ["QuickSearchHotkey"] = "Quick search hotkey",
        ["QuickSearchHotkeyDescription"] = "Keyboard shortcut reserved for opening quick search.",
        ["BrowserIntegration"] = "Browser extension bridge",
        ["BrowserIntegrationDescription"] = "Expose a local bridge endpoint for browser extension integration.",
        ["BrowserIntegrationPort"] = "Local bridge port",
        ["BrowserIntegrationPortDescription"] = "Local TCP port used by the desktop browser bridge.",
        ["CompactPasswordList"] = "Compact password list",
        ["CompactPasswordListDescription"] = "Use denser password rows for scanning large vaults.",
        ["SyncSubtitle"] = "Configure remote sync, backup targets and conflict behavior.",
        ["WebDav"] = "WebDAV",
        ["EnableWebDav"] = "Enable WebDAV sync",
        ["WebDavServerUrl"] = "Server URL",
        ["WebDavUsername"] = "Username",
        ["WebDavRemotePath"] = "Remote path",
        ["SyncOnStartup"] = "Sync on startup",
        ["SyncAfterChanges"] = "Sync after local changes",
        ["ConflictStrategy"] = "Conflict strategy",
        ["OneDrive"] = "OneDrive",
        ["EnableOneDrive"] = "Enable OneDrive boundary",
        ["MdbxLocalCache"] = "Keep MDBX local cache",
        ["FeatureParityMapDescription"] = "Desktop availability for Android-originated Monica features.",
        ["Available"] = "Available",
        ["DesktopEquivalent"] = "Desktop equivalent",
        ["PlatformLimited"] = "Platform limited",
        ["Planned"] = "Planned",
        ["Capability.passwords.Title"] = "Passwords",
        ["Capability.passwords.Description"] = "Login credentials with websites, app bindings, folders, favorites, archive, recycle bin and history.",
        ["Capability.notes.Title"] = "Secure Notes",
        ["Capability.notes.Description"] = "Encrypted notes and note binding for password entries.",
        ["Capability.totp.Title"] = "TOTP",
        ["Capability.totp.Description"] = "TOTP/HOTP/Steam-compatible authenticator records with QR import and copy actions.",
        ["Capability.cards.Title"] = "Wallet",
        ["Capability.cards.Description"] = "Bank cards, identity documents and images stored as secure items.",
        ["Capability.passkeys.Title"] = "Passkeys",
        ["Capability.passkeys.Description"] = "WebAuthn/FIDO2 metadata with Bitwarden and KeePass-compatible modes.",
        ["Capability.wifi.Title"] = "Wi-Fi",
        ["Capability.wifi.Description"] = "Wi-Fi secrets stored as typed credential entries.",
        ["Capability.ssh.Title"] = "SSH Keys",
        ["Capability.ssh.Description"] = "Structured SSH key records stored alongside password entries.",
        ["Capability.security-analysis.Title"] = "Security Analysis",
        ["Capability.security-analysis.Description"] = "Weak, duplicate and stale password checks.",
        ["Capability.generator.Title"] = "Generator",
        ["Capability.generator.Description"] = "Password and passphrase generation.",
        ["Capability.import-export.Title"] = "Import / Export",
        ["Capability.import-export.Description"] = "Monica JSON, CSV, Bitwarden JSON, KeePass KDBX and Aegis-oriented pipelines.",
        ["Capability.trash.Title"] = "Recycle Bin",
        ["Capability.trash.Description"] = "Soft-delete and restore flows.",
        ["Capability.timeline.Title"] = "Timeline",
        ["Capability.timeline.Description"] = "Operation log and rollback metadata.",
        ["Capability.categories.Title"] = "Folders",
        ["Capability.categories.Description"] = "Local categories plus KeePass, Bitwarden and MDBX ownership metadata.",
        ["Capability.customization.Title"] = "Personalization",
        ["Capability.customization.Description"] = "Page, card, icon and list customization entry points.",
        ["Capability.plus.Title"] = "Monica Plus",
        ["Capability.plus.Description"] = "Subscription/status page shell for parity with mobile.",
        ["Capability.bitwarden.Title"] = "Bitwarden",
        ["Capability.bitwarden.Description"] = "Vault mapping and sync service boundary.",
        ["Capability.keepass.Title"] = "KeePass",
        ["Capability.keepass.Description"] = "KDBX metadata and library-backed open/read boundary.",
        ["Capability.mdbx.Title"] = "MDBX",
        ["Capability.mdbx.Description"] = "Vault create/open/sync metadata and local file-stream management.",
        ["Capability.webdav.Title"] = "WebDAV",
        ["Capability.webdav.Description"] = "Remote backup and sync path handling.",
        ["Capability.onedrive.Title"] = "OneDrive",
        ["Capability.onedrive.Description"] = "Microsoft Graph/MSAL service boundary.",
        ["Capability.autofill.Title"] = "Desktop Autofill",
        ["Capability.autofill.Description"] = "Android Autofill/IME/Accessibility becomes quick search, clipboard, tray and browser-extension bridge.",
        ["Capability.credential-provider.Title"] = "Credential Provider",
        ["Capability.credential-provider.Description"] = "Android Credential Provider equivalent is platform-specific and exposed as limited status.",
        ["SystemDefault"] = "System default",
        ["English"] = "English",
        ["SimplifiedChinese"] = "Simplified Chinese",
        ["Light"] = "Light",
        ["Dark"] = "Dark",
        ["AskEveryTime"] = "Ask every time",
        ["LocalWins"] = "Local wins",
        ["RemoteWins"] = "Remote wins",
        ["MinuteFormat"] = "{0} min",
        ["SecondFormat"] = "{0} sec",
        ["PasswordCountFormat"] = "{0} items",
        ["SelectedPasswordCountFormat"] = "{0} selected",
        ["DeletedPasswordCountFormat"] = "{0} deleted passwords",
        ["NoteCountFormat"] = "{0} notes",
        ["TotpCountFormat"] = "{0} authenticators",
        ["WalletCountFormat"] = "{0} cards and documents",
        ["Locked"] = "Locked",
        ["VaultLocked"] = "Vault locked",
        ["FirstRunCreateMasterPassword"] = "First run: create a master password.",
        ["VaultMetadataLoadFailedFormat"] = "Vault metadata could not be loaded: {0}",
        ["SettingsLoaded"] = "Settings loaded",
        ["SettingsSaved"] = "Settings saved",
        ["EnterMasterPassword"] = "Enter a master password.",
        ["MasterPasswordMinLength"] = "Use at least 8 characters for the master password.",
        ["ConfirmationMismatch"] = "The confirmation password does not match.",
        ["WrongMasterPassword"] = "Wrong master password.",
        ["VaultUnlocked"] = "Vault unlocked",
        ["UnlockFailedFormat"] = "Unlock failed: {0}",
        ["VaultLoadFailedFormat"] = "Vault load failed: {0}",
        ["CreatedPasswordFormat"] = "Created {0}",
        ["UpdatedPasswordFormat"] = "Updated {0}",
        ["RestoredPasswordFormat"] = "Restored {0}",
        ["EditingNewSecureNote"] = "Editing a new secure note",
        ["EditingNoteFormat"] = "Editing {0}",
        ["NoteRequiresContent"] = "Enter a title or note content.",
        ["SavedNoteFormat"] = "Saved note {0}",
        ["CopiedPasswordFormat"] = "Copied password for {0}",
        ["CopiedUsernameFormat"] = "Copied username for {0}",
        ["CopiedWebsiteFormat"] = "Copied website for {0}",
        ["CopiedTotpFormat"] = "Copied TOTP for {0}",
        ["CopiedFieldFormat"] = "Copied {0}",
        ["FavoritedPasswordCountFormat"] = "Favorited {0} passwords",
        ["MovedToRecycleBinFormat"] = "Moved {0} to recycle bin",
        ["MovedSelectedPasswordsToRecycleBinFormat"] = "Moved {0} selected passwords to recycle bin",
        ["DeletedPasswordPermanentlyFormat"] = "Permanently deleted {0}",
        ["GeneratedPassword"] = "Generated password",
        ["ExportPrepared"] = "Prepared Monica JSON export preview",
        ["CreatedMdbxMetadata"] = "Created MDBX metadata and local working file path"
    };

    private static readonly Dictionary<string, string> Chinese = new()
    {
        ["Passwords"] = "密码",
        ["SecureNotes"] = "安全笔记",
        ["Totp"] = "动态口令",
        ["Cards"] = "卡包",
        ["Generator"] = "生成器",
        ["SyncAndBackup"] = "同步与备份",
        ["Settings"] = "设置",
        ["Folders"] = "文件夹",
        ["Personal"] = "个人",
        ["Refresh"] = "刷新",
        ["Export"] = "导出",
        ["UnlockMonica"] = "解锁 Monica",
        ["CreateMonicaVault"] = "创建 Monica 保险库",
        ["UnlockDescription"] = "使用主密码打开 Avalonia 桌面保险库。",
        ["CreateVaultDescription"] = "设置一个主密码。之后每次打开桌面保险库都需要它。",
        ["MasterPasswordWatermark"] = "主密码",
        ["ConfirmMasterPasswordWatermark"] = "确认主密码",
        ["Unlock"] = "解锁",
        ["CreateVault"] = "创建保险库",
        ["PasswordManager"] = "密码管理",
        ["Search"] = "搜索...",
        ["AddPassword"] = "添加密码",
        ["EditPassword"] = "编辑密码",
        ["Favorite"] = "收藏",
        ["CopyPassword"] = "复制密码",
        ["MoveToRecycleBin"] = "移到回收站",
        ["Save"] = "保存",
        ["Cancel"] = "取消",
        ["NoFolder"] = "无文件夹",
        ["NewPassword"] = "新建密码",
        ["PasswordTitleRequired"] = "请输入密码标题。",
        ["PasswordValueRequired"] = "请输入密码内容。",
        ["PasswordTitle"] = "标题",
        ["Website"] = "网站",
        ["Username"] = "用户名",
        ["Password"] = "密码",
        ["SecurityVerification"] = "安全验证",
        ["AuthenticatorKey"] = "验证器密钥",
        ["AuthenticatorKeyHint"] = "可选的 TOTP 密钥，对应 Android 端的验证器字段。二维码导入和多密码存储会继续复用这个模型扩展。",
        ["AppBinding"] = "应用绑定",
        ["AppName"] = "应用名称",
        ["AppPackageName"] = "应用包名或 Bundle ID",
        ["NoBoundNote"] = "不绑定笔记",
        ["Untitled"] = "未命名",
        ["PersonalInfo"] = "个人信息",
        ["Email"] = "邮箱",
        ["Phone"] = "电话",
        ["AddressLine"] = "地址",
        ["City"] = "城市",
        ["State"] = "省/州",
        ["ZipCode"] = "邮编",
        ["Country"] = "国家/地区",
        ["CardInfo"] = "卡片信息",
        ["CreditCardNumber"] = "卡号",
        ["CreditCardHolder"] = "持卡人",
        ["CreditCardExpiry"] = "有效期",
        ["CreditCardCvv"] = "CVV",
        ["AdvancedLogin"] = "高级登录",
        ["LoginTypePassword"] = "密码",
        ["LoginTypeSso"] = "SSO",
        ["LoginTypeWifi"] = "Wi-Fi",
        ["LoginTypeSshKey"] = "SSH 密钥",
        ["SsoProvider"] = "SSO 提供商",
        ["PasskeyBindings"] = "Passkey 绑定",
        ["WifiMetadata"] = "Wi-Fi 元数据",
        ["SshKeyData"] = "SSH 密钥数据",
        ["CustomFields"] = "自定义字段",
        ["CustomFieldsHint"] = "每行一个字段，格式为 标题=值；标题前加 ! 表示受保护字段。",
        ["Notes"] = "备注",
        ["TwoStepVerification"] = "两步验证",
        ["AddAuthenticator"] = "添加验证器",
        ["CopyCode"] = "复制验证码",
        ["Wallet"] = "卡包",
        ["AddItem"] = "添加项目",
        ["DesktopEquivalents"] = "桌面等价能力",
        ["DesktopEquivalentsMessage"] = "Android 的自动填充、输入法、无障碍和凭据提供程序能力，在桌面端通过快速搜索、剪贴板、托盘/浏览器扩展接口或平台受限状态呈现。",
        ["CreateMdbxMetadata"] = "创建 MDBX 元数据",
        ["FeatureParityMap"] = "功能对齐表",
        ["ExportPreview"] = "导出预览",
        ["PasswordGenerator"] = "密码生成器",
        ["Generate"] = "生成",
        ["SaveAsLogin"] = "保存为登录项",
        ["SecureNotesDescription"] = "笔记以 NOTE 类型存储在 secure_items 中，并共享同一套加密、文件夹、KeePass、Bitwarden 和 MDBX 归属模型。",
        ["CreateSecureItem"] = "创建安全项目",
        ["SettingsSubtitle"] = "配置 Monica 桌面端的行为、安全、外观和集成选项。",
        ["General"] = "通用",
        ["Language"] = "语言",
        ["Theme"] = "主题",
        ["StartupView"] = "启动页",
        ["Security"] = "安全",
        ["AutoLock"] = "自动锁定",
        ["AutoLockDescription"] = "桌面端空闲一段时间后锁定保险库。",
        ["AutoLockAfter"] = "自动锁定时间",
        ["ClearClipboard"] = "清空剪贴板",
        ["ClearClipboardDescription"] = "复制密码或动态口令后，按超时时间清空剪贴板。",
        ["ClearClipboardAfter"] = "清空时间",
        ["RequirePasswordBeforeExport"] = "导出前要求主密码",
        ["Desktop"] = "桌面",
        ["MinimizeToTray"] = "最小化到托盘",
        ["QuickSearch"] = "快速搜索浮层",
        ["QuickSearchHotkey"] = "快速搜索快捷键",
        ["BrowserIntegration"] = "浏览器扩展桥接",
        ["BrowserIntegrationPort"] = "本地桥接端口",
        ["CompactPasswordList"] = "紧凑密码列表",
        ["SyncSubtitle"] = "配置远程同步、备份目标和冲突处理方式。",
        ["WebDav"] = "WebDAV",
        ["EnableWebDav"] = "启用 WebDAV 同步",
        ["WebDavServerUrl"] = "服务器地址",
        ["WebDavUsername"] = "用户名",
        ["WebDavRemotePath"] = "远程路径",
        ["SyncOnStartup"] = "启动时同步",
        ["SyncAfterChanges"] = "本地变更后同步",
        ["ConflictStrategy"] = "冲突处理",
        ["OneDrive"] = "OneDrive",
        ["EnableOneDrive"] = "启用 OneDrive 接口",
        ["MdbxLocalCache"] = "保留 MDBX 本地缓存",
        ["Available"] = "可用",
        ["DesktopEquivalent"] = "桌面等价",
        ["PlatformLimited"] = "平台受限",
        ["Planned"] = "计划中",
        ["Capability.passwords.Title"] = "密码",
        ["Capability.passwords.Description"] = "登录凭据，支持网站、应用绑定、文件夹、收藏、归档、回收站和历史记录。",
        ["Capability.notes.Title"] = "安全笔记",
        ["Capability.notes.Description"] = "加密笔记，以及密码条目的笔记绑定。",
        ["Capability.totp.Title"] = "动态口令",
        ["Capability.totp.Description"] = "支持 TOTP、HOTP 和 Steam 兼容验证器记录，包含二维码导入和复制操作。",
        ["Capability.cards.Title"] = "卡包",
        ["Capability.cards.Description"] = "银行卡、身份证件和图片以安全项目形式保存。",
        ["Capability.passkeys.Title"] = "Passkey",
        ["Capability.passkeys.Description"] = "WebAuthn/FIDO2 元数据，兼容 Bitwarden 和 KeePass 模式。",
        ["Capability.wifi.Title"] = "Wi-Fi",
        ["Capability.wifi.Description"] = "Wi-Fi 密钥以类型化凭据条目保存。",
        ["Capability.ssh.Title"] = "SSH 密钥",
        ["Capability.ssh.Description"] = "结构化 SSH 密钥记录与密码条目一同保存。",
        ["Capability.security-analysis.Title"] = "安全分析",
        ["Capability.security-analysis.Description"] = "弱密码、重复密码和过期密码检查。",
        ["Capability.generator.Title"] = "生成器",
        ["Capability.generator.Description"] = "密码和密码短语生成。",
        ["Capability.import-export.Title"] = "导入 / 导出",
        ["Capability.import-export.Description"] = "Monica JSON、CSV、Bitwarden JSON、KeePass KDBX 和 Aegis 导入导出管线。",
        ["Capability.trash.Title"] = "回收站",
        ["Capability.trash.Description"] = "软删除和恢复流程。",
        ["Capability.timeline.Title"] = "时间线",
        ["Capability.timeline.Description"] = "操作日志和回滚元数据。",
        ["Capability.categories.Title"] = "文件夹",
        ["Capability.categories.Description"] = "本地分类，以及 KeePass、Bitwarden 和 MDBX 归属元数据。",
        ["Capability.customization.Title"] = "个性化",
        ["Capability.customization.Description"] = "页面、卡片、图标和列表自定义入口。",
        ["Capability.plus.Title"] = "Monica Plus",
        ["Capability.plus.Description"] = "与移动端对齐的订阅/状态页面框架。",
        ["Capability.bitwarden.Title"] = "Bitwarden",
        ["Capability.bitwarden.Description"] = "保险库映射和同步服务边界。",
        ["Capability.keepass.Title"] = "KeePass",
        ["Capability.keepass.Description"] = "KDBX 元数据，以及基于库的打开/读取边界。",
        ["Capability.mdbx.Title"] = "MDBX",
        ["Capability.mdbx.Description"] = "保险库创建、打开、同步元数据和本地文件流管理。",
        ["Capability.webdav.Title"] = "WebDAV",
        ["Capability.webdav.Description"] = "远程备份和同步路径处理。",
        ["Capability.onedrive.Title"] = "OneDrive",
        ["Capability.onedrive.Description"] = "Microsoft Graph/MSAL 服务边界。",
        ["Capability.autofill.Title"] = "桌面自动填充",
        ["Capability.autofill.Description"] = "Android 自动填充、输入法和无障碍能力在桌面端转换为快速搜索、剪贴板、托盘和浏览器扩展桥接。",
        ["Capability.credential-provider.Title"] = "凭据提供程序",
        ["Capability.credential-provider.Description"] = "Android 凭据提供程序的桌面等价能力依赖具体平台，因此显示为受限状态。",
        ["SystemDefault"] = "跟随系统",
        ["English"] = "英语",
        ["SimplifiedChinese"] = "简体中文",
        ["Light"] = "浅色",
        ["Dark"] = "深色",
        ["AskEveryTime"] = "每次询问",
        ["LocalWins"] = "本地优先",
        ["RemoteWins"] = "远端优先",
        ["MinuteFormat"] = "{0} 分钟",
        ["SecondFormat"] = "{0} 秒",
        ["PasswordCountFormat"] = "{0} 项",
        ["TotpCountFormat"] = "{0} 个验证器",
        ["WalletCountFormat"] = "{0} 张卡片与证件",
        ["Locked"] = "已锁定",
        ["VaultLocked"] = "保险库已锁定",
        ["FirstRunCreateMasterPassword"] = "首次运行：请创建主密码。",
        ["VaultMetadataLoadFailedFormat"] = "无法加载保险库元数据：{0}",
        ["SettingsLoaded"] = "设置已加载",
        ["SettingsSaved"] = "设置已保存",
        ["EnterMasterPassword"] = "请输入主密码。",
        ["MasterPasswordMinLength"] = "主密码至少需要 8 个字符。",
        ["ConfirmationMismatch"] = "两次输入的主密码不一致。",
        ["WrongMasterPassword"] = "主密码错误。",
        ["VaultUnlocked"] = "保险库已解锁",
        ["UnlockFailedFormat"] = "解锁失败：{0}",
        ["VaultLoadFailedFormat"] = "保险库加载失败：{0}",
        ["CreatedPasswordFormat"] = "已创建 {0}",
        ["UpdatedPasswordFormat"] = "已更新 {0}",
        ["CopiedPasswordFormat"] = "已复制 {0} 的密码",
        ["CopiedTotpFormat"] = "已复制 {0} 的动态口令",
        ["MovedToRecycleBinFormat"] = "已将 {0} 移到回收站",
        ["GeneratedPassword"] = "已生成密码",
        ["ExportPrepared"] = "已准备 Monica JSON 导出预览",
        ["CreatedMdbxMetadata"] = "已创建 MDBX 元数据和本地工作文件路径"
    };
}
