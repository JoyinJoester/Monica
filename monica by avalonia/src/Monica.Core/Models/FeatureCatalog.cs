namespace Monica.Core.Models;

public static class FeatureCatalog
{
    public static IReadOnlyList<PlatformCapability> AndroidParityFeatures { get; } =
    [
        new("passwords", "Passwords", "Login credentials with websites, app bindings, folders, favorites, archive, recycle bin and history.", PlatformFeatureStatus.Available),
        new("notes", "Secure Notes", "Encrypted notes and note binding for password entries.", PlatformFeatureStatus.Available),
        new("totp", "TOTP", "TOTP/HOTP/Steam-compatible authenticator records with QR import and copy actions.", PlatformFeatureStatus.Available),
        new("cards", "Wallet", "Bank cards, identity documents and images stored as secure items.", PlatformFeatureStatus.Available),
        new("passkeys", "Passkeys", "WebAuthn/FIDO2 metadata with Bitwarden and KeePass-compatible modes.", PlatformFeatureStatus.DesktopEquivalent),
        new("wifi", "Wi-Fi", "Wi-Fi secrets stored as typed credential entries.", PlatformFeatureStatus.Available),
        new("ssh", "SSH Keys", "Structured SSH key records stored alongside password entries.", PlatformFeatureStatus.Available),
        new("security-analysis", "Security Analysis", "Weak, duplicate and stale password checks.", PlatformFeatureStatus.Available),
        new("generator", "Generator", "Password and passphrase generation.", PlatformFeatureStatus.Available),
        new("import-export", "Import / Export", "Monica JSON, CSV, Bitwarden JSON, KeePass KDBX and Aegis-oriented pipelines.", PlatformFeatureStatus.Available),
        new("trash", "Recycle Bin", "Soft-delete and restore flows.", PlatformFeatureStatus.Available),
        new("timeline", "Timeline", "Operation log and rollback metadata.", PlatformFeatureStatus.Available),
        new("categories", "Folders", "Local categories plus KeePass, Bitwarden and MDBX ownership metadata.", PlatformFeatureStatus.Available),
        new("customization", "Personalization", "Page, card, icon and list customization entry points.", PlatformFeatureStatus.DesktopEquivalent),
        new("plus", "Monica Plus", "Subscription/status page shell for parity with mobile.", PlatformFeatureStatus.DesktopEquivalent),
        new("bitwarden", "Bitwarden", "Vault mapping and sync service boundary.", PlatformFeatureStatus.DesktopEquivalent),
        new("keepass", "KeePass", "KDBX metadata and library-backed open/read boundary.", PlatformFeatureStatus.DesktopEquivalent),
        new("mdbx", "MDBX", "Vault create/open/sync metadata and local file-stream management.", PlatformFeatureStatus.DesktopEquivalent),
        new("webdav", "WebDAV", "Remote backup and sync path handling.", PlatformFeatureStatus.Available),
        new("onedrive", "OneDrive", "Microsoft Graph/MSAL service boundary.", PlatformFeatureStatus.DesktopEquivalent),
        new("autofill", "Desktop Autofill", "Android Autofill/IME/Accessibility becomes quick search, clipboard, tray and browser-extension bridge.", PlatformFeatureStatus.PlatformLimited),
        new("credential-provider", "Credential Provider", "Android Credential Provider equivalent is platform-specific and exposed as limited status.", PlatformFeatureStatus.PlatformLimited)
    ];
}
