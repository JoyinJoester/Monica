using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Monica.App.Services;
using Monica.Core.Models;
using Monica.Core.Services;
using Monica.Platform.Services;

namespace Monica.App.ViewModels;

public sealed record PasswordDetailGroup(string Title, bool IsExpanded, IReadOnlyList<PasswordDetailField> Fields);

public sealed record PasswordDetailField(
    string Label,
    string DisplayValue,
    string CopyValue,
    bool CanCopy = true,
    bool IsSensitive = false);

public sealed partial class PasswordDetailViewModel : ObservableObject
{
    private readonly IClipboardService _clipboardService;

    public PasswordDetailViewModel(
        ILocalizationService localization,
        IClipboardService clipboardService,
        ICryptoService cryptoService,
        ITotpService totpService,
        PasswordEntry entry,
        IReadOnlyList<PasswordEntry> siblings,
        Category? category,
        SecureItem? boundNote,
        IReadOnlyList<CustomField> customFields)
    {
        L = localization;
        _clipboardService = clipboardService;
        Entry = entry;
        DialogTitle = localization.Get("PasswordDetails");
        Title = entry.Title;
        Subtitle = string.Join(" - ", new[] { entry.Username, entry.Website }.Where(value => !string.IsNullOrWhiteSpace(value)));
        Initial = entry.TitleInitial;

        foreach (var group in BuildGroups(localization, cryptoService, totpService, entry, NormalizeSiblings(entry, siblings), category, boundNote, customFields))
        {
            Groups.Add(group);
        }
    }

    public ILocalizationService L { get; }
    public PasswordEntry Entry { get; }
    public string DialogTitle { get; }
    public string Title { get; }
    public string Subtitle { get; }
    public string Initial { get; }
    public string CopyLabel => L.Get("Copy");
    public ObservableCollection<PasswordDetailGroup> Groups { get; } = [];

    [ObservableProperty]
    private string _statusText = "";

    [RelayCommand]
    private async Task CopyFieldAsync(PasswordDetailField? field)
    {
        if (field is null || !field.CanCopy || string.IsNullOrWhiteSpace(field.CopyValue))
        {
            return;
        }

        await _clipboardService.SetTextAsync(field.CopyValue);
        StatusText = L.Format("CopiedFieldFormat", field.Label);
    }

    private static IReadOnlyList<PasswordDetailGroup> BuildGroups(
        ILocalizationService localization,
        ICryptoService cryptoService,
        ITotpService totpService,
        PasswordEntry entry,
        IReadOnlyList<PasswordEntry> siblings,
        Category? category,
        SecureItem? boundNote,
        IReadOnlyList<CustomField> customFields)
    {
        var groups = new List<PasswordDetailGroup>();

        AddGroup(groups, localization.Get("General"), true,
            Field(localization.Get("PasswordTitle"), entry.Title),
            Field(localization.Get("Username"), entry.Username),
            Field(localization.Get("Website"), entry.Website),
            Field(localization.Get("Category"), category?.Name ?? ""),
            Field(localization.Get("BoundNote"), boundNote?.Title ?? ""));

        var passwordFields = new List<PasswordDetailField>();
        for (var index = 0; index < siblings.Count; index++)
        {
            var password = TryUnprotectPassword(siblings[index].Password, cryptoService);
            var label = siblings.Count == 1
                ? localization.Get("Password")
                : $"{localization.Get("Password")} {index + 1}";
            passwordFields.Add(Field(
                label,
                password.DisplayValue,
                password.CopyValue,
                password.CanCopy,
                isSensitive: true));
        }

        AddGroup(groups, localization.Get("Passwords"), true, passwordFields.ToArray());

        var totpData = TotpDataResolver.FromAuthenticatorKey(entry.AuthenticatorKey, entry.Title, entry.Username);
        if (totpData is not null)
        {
            var code = totpService.GenerateCode(totpData.Secret, totpData.Period, totpData.Digits, totpData.OtpType, totpData.Counter);
            AddGroup(groups, localization.Get("SecurityVerification"), true,
                Field(localization.Get("TotpCode"), code),
                Field(localization.Get("RemainingTime"), $"{totpService.GetRemainingSeconds(totpData.Period)}s", canCopy: false),
                Field(localization.Get("Issuer"), totpData.Issuer),
                Field(localization.Get("Account"), totpData.AccountName),
                Field(localization.Get("TotpSecret"), totpData.Secret, isSensitive: true),
                Field(localization.Get("AuthenticatorKey"), entry.AuthenticatorKey, isSensitive: true));
        }

        AddGroup(groups, localization.Get("AppBinding"), false,
            Field(localization.Get("AppName"), entry.AppName),
            Field(localization.Get("AppPackageName"), entry.AppPackageName));

        AddGroup(groups, localization.Get("PersonalInfo"), false,
            Field(localization.Get("Email"), entry.Email),
            Field(localization.Get("Phone"), entry.Phone),
            Field(localization.Get("AddressLine"), entry.AddressLine),
            Field(localization.Get("City"), entry.City),
            Field(localization.Get("State"), entry.State),
            Field(localization.Get("ZipCode"), entry.ZipCode),
            Field(localization.Get("Country"), entry.Country));

        AddGroup(groups, localization.Get("CardInfo"), false,
            Field(localization.Get("CreditCardNumber"), entry.CreditCardNumber, isSensitive: true),
            Field(localization.Get("CreditCardHolder"), entry.CreditCardHolder),
            Field(localization.Get("CreditCardExpiry"), entry.CreditCardExpiry),
            Field(localization.Get("CreditCardCvv"), entry.CreditCardCvv, isSensitive: true));

        AddGroup(groups, localization.Get("AdvancedLogin"), false,
            Field(localization.Get("LoginType"), LocalizeLoginType(localization, entry.LoginType)),
            Field(localization.Get("SsoProvider"), entry.SsoProvider),
            Field(localization.Get("PasskeyBindings"), entry.PasskeyBindings),
            Field(localization.Get("WifiMetadata"), entry.WifiMetadata),
            Field(localization.Get("SshKeyData"), entry.SshKeyData));

        AddGroup(groups, localization.Get("Notes"), false,
            Field(localization.Get("Notes"), entry.Notes),
            Field(localization.Get("BoundNote"), boundNote is null ? "" : NoteContentCodec.ToPlainPreview(
                NoteContentCodec.DecodeFromItem(boundNote).Content,
                NoteContentCodec.DecodeFromItem(boundNote).IsMarkdown)));

        AddGroup(groups, localization.Get("CustomFields"), false,
            customFields
                .OrderBy(field => field.SortOrder)
                .ThenBy(field => field.Id)
                .Select(field => Field(field.Title, field.Value, isSensitive: field.IsProtected))
                .ToArray());

        AddGroup(groups, localization.Get("SourceMetadata"), false,
            Field("Bitwarden vault", entry.BitwardenVaultId?.ToString() ?? ""),
            Field("Bitwarden cipher", entry.BitwardenCipherId ?? ""),
            Field("KeePass database", entry.KeepassDatabaseId?.ToString() ?? ""),
            Field("KeePass group", entry.KeepassGroupPath ?? ""),
            Field("MDBX database", entry.MdbxDatabaseId?.ToString() ?? ""),
            Field("MDBX folder", entry.MdbxFolderId ?? ""),
            Field(localization.Get("CreatedAt"), entry.CreatedAt.ToString("g", localization.Culture), canCopy: false),
            Field(localization.Get("UpdatedAt"), entry.UpdatedAt.ToString("g", localization.Culture), canCopy: false));

        return groups;
    }

    private static IReadOnlyList<PasswordEntry> NormalizeSiblings(PasswordEntry entry, IReadOnlyList<PasswordEntry> siblings)
    {
        return siblings.Count == 0
            ? [entry]
            : siblings;
    }

    private static void AddGroup(List<PasswordDetailGroup> groups, string title, bool isExpanded, params PasswordDetailField[] fields)
    {
        var visibleFields = fields
            .Where(field => !string.IsNullOrWhiteSpace(field.DisplayValue))
            .ToArray();
        if (visibleFields.Length > 0)
        {
            groups.Add(new PasswordDetailGroup(title, isExpanded, visibleFields));
        }
    }

    private static PasswordDetailField Field(
        string label,
        string value,
        string? copyValue = null,
        bool canCopy = true,
        bool isSensitive = false)
    {
        var normalizedValue = value.Trim();
        return new PasswordDetailField(
            label,
            normalizedValue,
            copyValue ?? normalizedValue,
            canCopy && normalizedValue.Length > 0,
            isSensitive);
    }

    private static (string DisplayValue, string CopyValue, bool CanCopy) TryUnprotectPassword(string storedPassword, ICryptoService cryptoService)
    {
        if (string.IsNullOrWhiteSpace(storedPassword))
        {
            return ("", "", false);
        }

        if (!cryptoService.IsUnlocked)
        {
            return ("********", "", false);
        }

        try
        {
            var plainText = cryptoService.DecryptString(storedPassword);
            return (plainText, plainText, true);
        }
        catch
        {
            return (storedPassword, storedPassword, true);
        }
    }

    private static string LocalizeLoginType(ILocalizationService localization, PasswordLoginType loginType)
    {
        return loginType switch
        {
            PasswordLoginType.Sso => localization.Get("LoginTypeSso"),
            PasswordLoginType.Wifi => localization.Get("LoginTypeWifi"),
            PasswordLoginType.SshKey => localization.Get("LoginTypeSshKey"),
            _ => localization.Get("LoginTypePassword")
        };
    }
}
