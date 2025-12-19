using System.Text.Json.Serialization;

namespace Monica.Windows.Models
{
    /// <summary>
    /// TOTP data model compatible with Android version
    /// </summary>
    public class TotpData
    {
        [JsonPropertyName("secret")]
        public string Secret { get; set; } = string.Empty;

        [JsonPropertyName("issuer")]
        public string Issuer { get; set; } = string.Empty;

        [JsonPropertyName("accountName")]
        public string AccountName { get; set; } = string.Empty;

        [JsonPropertyName("period")]
        public int Period { get; set; } = 30;

        [JsonPropertyName("digits")]
        public int Digits { get; set; } = 6;

        [JsonPropertyName("algorithm")]
        public string Algorithm { get; set; } = "SHA1";

        [JsonPropertyName("otpType")]
        public string OtpType { get; set; } = "TOTP";

        [JsonPropertyName("counter")]
        public long Counter { get; set; } = 0;

        [JsonPropertyName("pin")]
        public string Pin { get; set; } = string.Empty;

        [JsonPropertyName("link")]
        public string Link { get; set; } = string.Empty;

        [JsonPropertyName("associatedApp")]
        public string AssociatedApp { get; set; } = string.Empty;

        [JsonPropertyName("boundPasswordId")]
        public long? BoundPasswordId { get; set; }
    }

    /// <summary>
    /// Document types matching Android enum
    /// </summary>
    public enum DocumentType
    {
        [JsonPropertyName("ID_CARD")]
        IdCard,

        [JsonPropertyName("PASSPORT")]
        Passport,

        [JsonPropertyName("DRIVER_LICENSE")]
        DriverLicense,

        [JsonPropertyName("SOCIAL_SECURITY")]
        SocialSecurity,

        [JsonPropertyName("OTHER")]
        Other
    }

    /// <summary>
    /// Document data model compatible with Android version
    /// </summary>
    public class DocumentData
    {
        [JsonPropertyName("documentNumber")]
        public string DocumentNumber { get; set; } = string.Empty;

        [JsonPropertyName("fullName")]
        public string FullName { get; set; } = string.Empty;

        [JsonPropertyName("issuedDate")]
        public string IssuedDate { get; set; } = string.Empty;

        [JsonPropertyName("expiryDate")]
        public string ExpiryDate { get; set; } = string.Empty;

        [JsonPropertyName("issuedBy")]
        public string IssuedBy { get; set; } = string.Empty;

        [JsonPropertyName("nationality")]
        public string Nationality { get; set; } = string.Empty;

        [JsonPropertyName("documentType")]
        public string DocumentTypeString { get; set; } = "ID_CARD";

        [JsonPropertyName("imagePaths")]
        public System.Collections.Generic.List<string> ImagePaths { get; set; } = new();
    }

    /// <summary>
    /// Bank card types matching Android enum
    /// </summary>
    public enum CardType
    {
        Debit,
        Credit,
        Prepaid
    }

    /// <summary>
    /// Bank card data model compatible with Android version
    /// </summary>
    public class BankCardData
    {
        [JsonPropertyName("cardNumber")]
        public string CardNumber { get; set; } = string.Empty;

        [JsonPropertyName("cardholderName")]
        public string CardholderName { get; set; } = string.Empty;

        [JsonPropertyName("expiryMonth")]
        public string ExpiryMonth { get; set; } = string.Empty;

        [JsonPropertyName("expiryYear")]
        public string ExpiryYear { get; set; } = string.Empty;

        [JsonPropertyName("cvv")]
        public string Cvv { get; set; } = string.Empty;

        [JsonPropertyName("bankName")]
        public string BankName { get; set; } = string.Empty;

        [JsonPropertyName("cardType")]
        public string CardTypeString { get; set; } = "DEBIT";

        [JsonPropertyName("billingAddress")]
        public string BillingAddress { get; set; } = string.Empty;

        [JsonPropertyName("imagePaths")]
        public System.Collections.Generic.List<string> ImagePaths { get; set; } = new();
    }

    /// <summary>
    /// Note data model compatible with Android version
    /// </summary>
    public class NoteData
    {
        [JsonPropertyName("content")]
        public string Content { get; set; } = string.Empty;
    }
}
