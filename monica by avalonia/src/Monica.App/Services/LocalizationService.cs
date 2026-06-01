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
    string Search { get; }
    string AddPassword { get; }
    string Favorite { get; }
    string CopyPassword { get; }
    string MoveToRecycleBin { get; }
    string TwoStepVerification { get; }
    string AddAuthenticator { get; }
    string CopyCode { get; }
    string Wallet { get; }
    string AddItem { get; }
    string DesktopEquivalents { get; }
    string DesktopEquivalentsMessage { get; }
    string CreateMdbxMetadata { get; }
    string FeatureParityMap { get; }
    string ExportPreview { get; }
    string PasswordGenerator { get; }
    string Generate { get; }
    string SaveAsLogin { get; }
    string SecureNotesDescription { get; }
    string CreateSecureItem { get; }
    string SettingsSubtitle { get; }
    string General { get; }
    string Language { get; }
    string Theme { get; }
    string StartupView { get; }
    string Security { get; }
    string AutoLock { get; }
    string AutoLockDescription { get; }
    string AutoLockAfter { get; }
    string ClearClipboard { get; }
    string ClearClipboardDescription { get; }
    string ClearClipboardAfter { get; }
    string RequirePasswordBeforeExport { get; }
    string Desktop { get; }
    string MinimizeToTray { get; }
    string QuickSearch { get; }
    string QuickSearchHotkey { get; }
    string BrowserIntegration { get; }
    string BrowserIntegrationPort { get; }
    string CompactPasswordList { get; }
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
    public string Search => Text();
    public string AddPassword => Text();
    public string Favorite => Text();
    public string CopyPassword => Text();
    public string MoveToRecycleBin => Text();
    public string TwoStepVerification => Text();
    public string AddAuthenticator => Text();
    public string CopyCode => Text();
    public string Wallet => Text();
    public string AddItem => Text();
    public string DesktopEquivalents => Text();
    public string DesktopEquivalentsMessage => Text();
    public string CreateMdbxMetadata => Text();
    public string FeatureParityMap => Text();
    public string ExportPreview => Text();
    public string PasswordGenerator => Text();
    public string Generate => Text();
    public string SaveAsLogin => Text();
    public string SecureNotesDescription => Text();
    public string CreateSecureItem => Text();
    public string SettingsSubtitle => Text();
    public string General => Text();
    public string Language => Text();
    public string Theme => Text();
    public string StartupView => Text();
    public string Security => Text();
    public string AutoLock => Text();
    public string AutoLockDescription => Text();
    public string AutoLockAfter => Text();
    public string ClearClipboard => Text();
    public string ClearClipboardDescription => Text();
    public string ClearClipboardAfter => Text();
    public string RequirePasswordBeforeExport => Text();
    public string Desktop => Text();
    public string MinimizeToTray => Text();
    public string QuickSearch => Text();
    public string QuickSearchHotkey => Text();
    public string BrowserIntegration => Text();
    public string BrowserIntegrationPort => Text();
    public string CompactPasswordList => Text();
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
        ["Search"] = "Search...",
        ["AddPassword"] = "Add Password",
        ["Favorite"] = "Favorite",
        ["CopyPassword"] = "Copy password",
        ["MoveToRecycleBin"] = "Move to recycle bin",
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
        ["SettingsSubtitle"] = "Configure Monica desktop behavior, security, appearance and integration options.",
        ["General"] = "General",
        ["Language"] = "Language",
        ["Theme"] = "Theme",
        ["StartupView"] = "Startup view",
        ["Security"] = "Security",
        ["AutoLock"] = "Auto lock",
        ["AutoLockDescription"] = "Lock the vault after a period of desktop inactivity.",
        ["AutoLockAfter"] = "Auto-lock after",
        ["ClearClipboard"] = "Clear clipboard",
        ["ClearClipboardDescription"] = "Remove copied passwords and TOTP codes after a timeout.",
        ["ClearClipboardAfter"] = "Clear after",
        ["RequirePasswordBeforeExport"] = "Require master password before export",
        ["Desktop"] = "Desktop",
        ["MinimizeToTray"] = "Minimize to tray",
        ["QuickSearch"] = "Quick search overlay",
        ["QuickSearchHotkey"] = "Quick search hotkey",
        ["BrowserIntegration"] = "Browser extension bridge",
        ["BrowserIntegrationPort"] = "Local bridge port",
        ["CompactPasswordList"] = "Compact password list",
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
        ["CopiedPasswordFormat"] = "Copied password for {0}",
        ["CopiedTotpFormat"] = "Copied TOTP for {0}",
        ["MovedToRecycleBinFormat"] = "Moved {0} to recycle bin",
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
        ["Favorite"] = "收藏",
        ["CopyPassword"] = "复制密码",
        ["MoveToRecycleBin"] = "移到回收站",
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
        ["CopiedPasswordFormat"] = "已复制 {0} 的密码",
        ["CopiedTotpFormat"] = "已复制 {0} 的动态口令",
        ["MovedToRecycleBinFormat"] = "已将 {0} 移到回收站",
        ["GeneratedPassword"] = "已生成密码",
        ["ExportPrepared"] = "已准备 Monica JSON 导出预览",
        ["CreatedMdbxMetadata"] = "已创建 MDBX 元数据和本地工作文件路径"
    };
}
