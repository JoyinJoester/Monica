using System;
using System.ComponentModel.DataAnnotations;
using System.ComponentModel.DataAnnotations.Schema;

namespace Monica.Windows.Models
{
    [Table("PasswordEntries")]
    public class PasswordEntry
    {
        [Key]
        [DatabaseGenerated(DatabaseGeneratedOption.Identity)]
        public long Id { get; set; }

        [Required]
        public string Title { get; set; } = string.Empty;

        [NotMapped]
        public string TitleInitial => !string.IsNullOrEmpty(Title) ? Title.Substring(0, 1).ToUpper() : "?";

        public string Website { get; set; } = string.Empty;

        public string Username { get; set; } = string.Empty;

        [Required]
        public string EncryptedPassword { get; set; } = string.Empty; // Encrypted

        public string Notes { get; set; } = string.Empty;

        public DateTime CreatedAt { get; set; } = DateTime.Now;
        public DateTime UpdatedAt { get; set; } = DateTime.Now;
        public bool IsFavorite { get; set; }
        public int SortOrder { get; set; }

        public string? Email { get; set; }
        public string? Phone { get; set; }

        // Address
        public string? AddressLine { get; set; }
        public string? City { get; set; }
        public string? State { get; set; }
        public string? ZipCode { get; set; }
        public string? Country { get; set; }

        // Payment (Encrypted)
        public string? EncryptedCreditCardNumber { get; set; }
        public string? CreditCardHolder { get; set; }
        public string? CreditCardExpiry { get; set; } // MM/YY
        public string? EncryptedCreditCardCVV { get; set; }

        public int? CategoryId { get; set; }
        
        [ForeignKey("CategoryId")]
        public virtual Category? Category { get; set; }
    }
}
