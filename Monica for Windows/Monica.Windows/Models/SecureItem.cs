using System;
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;
using CommunityToolkit.Mvvm.ComponentModel;

namespace Monica.Windows.Models
{
    public enum ItemType
    {
        Password = 0, // 兼容
        Totp = 1,
        BankCard = 2,
        Document = 3,
        Note = 4
    }

    [Table("SecureItems")]
    public partial class SecureItem : ObservableObject
    {
        [Key]
        [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
        public long Id { get; set; }

        public ItemType ItemType { get; set; }

        [Required]
        public string Title { get; set; } = string.Empty;

        public string Notes { get; set; } = string.Empty;

        // Encrypted JSON data containing type-specific fields
        public string ItemData { get; set; } = string.Empty;

        public DateTime CreatedAt { get; set; } = DateTime.Now;
        public DateTime UpdatedAt { get; set; } = DateTime.Now;

        [ObservableProperty]
        private bool _isFavorite;

        public int SortOrder { get; set; }

        // TOTP Display Properties (Not Mapped)
        [ObservableProperty]
        [property: NotMapped]
        private string _totpCode = "------";

        [ObservableProperty]
        [property: NotMapped]
        private string _totpTimeRemaining = "";

        [ObservableProperty]
        [property: NotMapped]
        private double _totpProgress = 0;

        [NotMapped]
        public object? CachedTotpData { get; set; }
    }
}
