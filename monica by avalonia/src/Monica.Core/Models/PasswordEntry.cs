using CommunityToolkit.Mvvm.ComponentModel;

namespace Monica.Core.Models;

public partial class PasswordEntry : ObservableObject
{
    public long Id { get; set; }

    [ObservableProperty]
    private string _title = "";

    [ObservableProperty]
    private string _website = "";

    [ObservableProperty]
    private string _username = "";

    [ObservableProperty]
    private string _password = "";

    [ObservableProperty]
    private string _notes = "";

    [ObservableProperty]
    private bool _isFavorite;

    public int SortOrder { get; set; }
    public bool IsGroupCover { get; set; }
    public string AppPackageName { get; set; } = "";
    public string AppName { get; set; } = "";
    public string Email { get; set; } = "";
    public string Phone { get; set; } = "";
    public string AddressLine { get; set; } = "";
    public string City { get; set; } = "";
    public string State { get; set; } = "";
    public string ZipCode { get; set; } = "";
    public string Country { get; set; } = "";
    public string CreditCardNumber { get; set; } = "";
    public string CreditCardHolder { get; set; } = "";
    public string CreditCardExpiry { get; set; } = "";
    public string CreditCardCvv { get; set; } = "";
    public long? CategoryId { get; set; }
    public long? BoundNoteId { get; set; }
    public long? KeepassDatabaseId { get; set; }
    public string? KeepassGroupPath { get; set; }
    public string? KeepassEntryUuid { get; set; }
    public string? KeepassGroupUuid { get; set; }
    public long? MdbxDatabaseId { get; set; }
    public string? MdbxFolderId { get; set; }
    public string AuthenticatorKey { get; set; } = "";
    public string PasskeyBindings { get; set; } = "";
    public string SshKeyData { get; set; } = "";
    public PasswordLoginType LoginType { get; set; } = PasswordLoginType.Password;
    public string SsoProvider { get; set; } = "";
    public long? SsoRefEntryId { get; set; }
    public string WifiMetadata { get; set; } = "";
    public string CustomIconType { get; set; } = "NONE";
    public string? CustomIconValue { get; set; }
    public long CustomIconUpdatedAt { get; set; }
    public bool IsDeleted { get; set; }
    public DateTimeOffset? DeletedAt { get; set; }
    public bool IsArchived { get; set; }
    public DateTimeOffset? ArchivedAt { get; set; }
    public string? ReplicaGroupId { get; set; }
    public long? BitwardenVaultId { get; set; }
    public string? BitwardenCipherId { get; set; }
    public string? BitwardenFolderId { get; set; }
    public string? BitwardenRevisionDate { get; set; }
    public int BitwardenCipherType { get; set; } = 1;
    public bool BitwardenLocalModified { get; set; }
    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
    public DateTimeOffset UpdatedAt { get; set; } = DateTimeOffset.UtcNow;

    public string TitleInitial => string.IsNullOrWhiteSpace(Title) ? "?" : Title.Trim()[0].ToString().ToUpperInvariant();
    public bool IsWifiEntry => LoginType == PasswordLoginType.Wifi;
    public bool IsSshKeyEntry => LoginType == PasswordLoginType.SshKey;
    public bool IsBitwardenEntry => BitwardenVaultId is not null;
    public bool IsKeePassEntry => KeepassDatabaseId is not null;
    public bool IsMdbxEntry => MdbxDatabaseId is not null;
}
