using CommunityToolkit.Mvvm.ComponentModel;

namespace Monica.Core.Models;

public partial class SecureItem : ObservableObject
{
    public long Id { get; set; }
    public VaultItemType ItemType { get; set; }

    [ObservableProperty]
    private string _title = "";

    [ObservableProperty]
    private string _notes = "";

    [ObservableProperty]
    private bool _isFavorite;

    public int SortOrder { get; set; }
    public DateTimeOffset CreatedAt { get; set; } = DateTimeOffset.UtcNow;
    public DateTimeOffset UpdatedAt { get; set; } = DateTimeOffset.UtcNow;
    public string ItemData { get; set; } = "{}";
    public string ImagePaths { get; set; } = "[]";
    public long? BoundPasswordId { get; set; }
    public long? CategoryId { get; set; }
    public long? KeepassDatabaseId { get; set; }
    public string? KeepassGroupPath { get; set; }
    public string? KeepassEntryUuid { get; set; }
    public string? KeepassGroupUuid { get; set; }
    public long? MdbxDatabaseId { get; set; }
    public string? MdbxFolderId { get; set; }
    public bool IsDeleted { get; set; }
    public DateTimeOffset? DeletedAt { get; set; }
    public string? ReplicaGroupId { get; set; }
    public long? BitwardenVaultId { get; set; }
    public string? BitwardenCipherId { get; set; }
    public string? BitwardenFolderId { get; set; }
    public string? BitwardenRevisionDate { get; set; }
    public bool BitwardenLocalModified { get; set; }
    public SyncStatus SyncStatus { get; set; } = SyncStatus.None;

    [ObservableProperty]
    private string _totpCode = "------";

    [ObservableProperty]
    private string _totpTimeRemaining = "";

    [ObservableProperty]
    private double _totpProgress;
}
