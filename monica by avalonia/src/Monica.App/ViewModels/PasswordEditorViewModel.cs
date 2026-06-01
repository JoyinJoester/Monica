using System.Collections.ObjectModel;
using CommunityToolkit.Mvvm.ComponentModel;
using CommunityToolkit.Mvvm.Input;
using Monica.App.Services;
using Monica.Core.Models;
using Monica.Core.Services;

namespace Monica.App.ViewModels;

public sealed record PasswordCategoryChoice(long? Id, string Name);
public sealed record PasswordLoginTypeChoice(PasswordLoginType Value, string Label);
public sealed record BoundNoteChoice(long? Id, string Title);

public sealed partial class PasswordEditorViewModel : ObservableObject
{
    private readonly IPasswordGeneratorService _passwordGenerator;

    public PasswordEditorViewModel(
        ILocalizationService localization,
        IPasswordGeneratorService passwordGenerator,
        PasswordEntry? source,
        IEnumerable<Category> categories,
        string plainPassword,
        IEnumerable<string>? siblingPasswords = null,
        IEnumerable<SecureItem>? notes = null,
        IEnumerable<CustomField>? customFields = null)
    {
        L = localization;
        _passwordGenerator = passwordGenerator;
        Source = source;
        IsNew = source is null;

        CategoryOptions.Add(new PasswordCategoryChoice(null, localization.Get("NoFolder")));
        foreach (var category in categories.OrderBy(item => item.SortOrder).ThenBy(item => item.Name))
        {
            CategoryOptions.Add(new PasswordCategoryChoice(category.Id, category.Name));
        }

        var selectedCategoryId = source?.CategoryId;
        SelectedCategory = CategoryOptions.FirstOrDefault(item => item.Id == selectedCategoryId) ?? CategoryOptions[0];

        BoundNoteOptions.Add(new BoundNoteChoice(null, localization.Get("NoBoundNote")));
        foreach (var note in (notes ?? []).OrderByDescending(item => item.UpdatedAt).ThenBy(item => item.Title))
        {
            BoundNoteOptions.Add(new BoundNoteChoice(note.Id, string.IsNullOrWhiteSpace(note.Title) ? localization.Get("Untitled") : note.Title));
        }

        SelectedBoundNote = BoundNoteOptions.FirstOrDefault(item => item.Id == source?.BoundNoteId) ?? BoundNoteOptions[0];

        Title = source?.Title ?? "";
        WebsiteLines = string.Join(Environment.NewLine, ParseWebsiteRows(source?.Website ?? ""));
        Username = source?.Username ?? "";
        PasswordLines = string.Join(
            Environment.NewLine,
            NormalizePasswordRows(siblingPasswords ?? [plainPassword]));
        Notes = source?.Notes ?? "";
        AuthenticatorKey = source?.AuthenticatorKey ?? "";
        AppPackageName = source?.AppPackageName ?? "";
        AppName = source?.AppName ?? "";
        Email = source?.Email ?? "";
        Phone = source?.Phone ?? "";
        AddressLine = source?.AddressLine ?? "";
        City = source?.City ?? "";
        State = source?.State ?? "";
        ZipCode = source?.ZipCode ?? "";
        Country = source?.Country ?? "";
        CreditCardNumber = source?.CreditCardNumber ?? "";
        CreditCardHolder = source?.CreditCardHolder ?? "";
        CreditCardExpiry = source?.CreditCardExpiry ?? "";
        CreditCardCvv = source?.CreditCardCvv ?? "";
        PasskeyBindings = source?.PasskeyBindings ?? "";
        SshKeyData = source?.SshKeyData ?? "";
        SsoProvider = source?.SsoProvider ?? "";
        WifiMetadata = source?.WifiMetadata ?? "";
        IsFavorite = source?.IsFavorite ?? false;

        LoginTypeOptions.Add(new PasswordLoginTypeChoice(PasswordLoginType.Password, localization.Get("LoginTypePassword")));
        LoginTypeOptions.Add(new PasswordLoginTypeChoice(PasswordLoginType.Sso, localization.Get("LoginTypeSso")));
        LoginTypeOptions.Add(new PasswordLoginTypeChoice(PasswordLoginType.Wifi, localization.Get("LoginTypeWifi")));
        LoginTypeOptions.Add(new PasswordLoginTypeChoice(PasswordLoginType.SshKey, localization.Get("LoginTypeSshKey")));
        SelectedLoginType = LoginTypeOptions.FirstOrDefault(item => item.Value == source?.LoginType) ?? LoginTypeOptions[0];
        CustomFieldsText = EncodeCustomFields(customFields ?? []);
    }

    public ILocalizationService L { get; }
    public PasswordEntry? Source { get; }
    public bool IsNew { get; }
    public ObservableCollection<PasswordCategoryChoice> CategoryOptions { get; } = [];
    public ObservableCollection<PasswordLoginTypeChoice> LoginTypeOptions { get; } = [];
    public ObservableCollection<BoundNoteChoice> BoundNoteOptions { get; } = [];
    public string DialogTitle => IsNew ? L.Get("NewPassword") : L.Get("EditPassword");
    public string Website
    {
        get => WebsiteLines;
        set
        {
            WebsiteLines = value;
            OnPropertyChanged();
        }
    }

    public string Password
    {
        get => PasswordLines;
        set
        {
            PasswordLines = value;
            OnPropertyChanged();
        }
    }

    [ObservableProperty]
    private string _title = "";

    [ObservableProperty]
    private string _websiteLines = "";

    [ObservableProperty]
    private string _username = "";

    [ObservableProperty]
    private string _passwordLines = "";

    [ObservableProperty]
    private string _notes = "";

    [ObservableProperty]
    private string _authenticatorKey = "";

    [ObservableProperty]
    private string _appPackageName = "";

    [ObservableProperty]
    private string _appName = "";

    [ObservableProperty]
    private string _email = "";

    [ObservableProperty]
    private string _phone = "";

    [ObservableProperty]
    private string _addressLine = "";

    [ObservableProperty]
    private string _city = "";

    [ObservableProperty]
    private string _state = "";

    [ObservableProperty]
    private string _zipCode = "";

    [ObservableProperty]
    private string _country = "";

    [ObservableProperty]
    private string _creditCardNumber = "";

    [ObservableProperty]
    private string _creditCardHolder = "";

    [ObservableProperty]
    private string _creditCardExpiry = "";

    [ObservableProperty]
    private string _creditCardCvv = "";

    [ObservableProperty]
    private string _passkeyBindings = "";

    [ObservableProperty]
    private string _sshKeyData = "";

    [ObservableProperty]
    private string _ssoProvider = "";

    [ObservableProperty]
    private string _wifiMetadata = "";

    [ObservableProperty]
    private string _customFieldsText = "";

    [ObservableProperty]
    private bool _isFavorite;

    [ObservableProperty]
    private PasswordCategoryChoice? _selectedCategory;

    [ObservableProperty]
    private PasswordLoginTypeChoice? _selectedLoginType;

    [ObservableProperty]
    private BoundNoteChoice? _selectedBoundNote;

    [ObservableProperty]
    private string _validationMessage = "";

    public bool Validate()
    {
        if (string.IsNullOrWhiteSpace(Title))
        {
            ValidationMessage = L.Get("PasswordTitleRequired");
            return false;
        }

        if (GetPasswordRows().Count == 0 && SelectedLoginType?.Value != PasswordLoginType.Sso)
        {
            ValidationMessage = L.Get("PasswordValueRequired");
            return false;
        }

        ValidationMessage = "";
        return true;
    }

    public PasswordEntry BuildEntry(string storedPassword)
    {
        return BuildEntryFrom(Source, storedPassword);
    }

    public PasswordEntry BuildEntryFrom(PasswordEntry? source, string storedPassword)
    {
        var entry = source is null ? new PasswordEntry() : Clone(source);
        entry.Title = Title.Trim();
        entry.Website = EncodeWebsites();
        entry.Username = Username.Trim();
        entry.Password = storedPassword;
        entry.Notes = Notes.Trim();
        entry.AuthenticatorKey = AuthenticatorKey.Trim();
        entry.AppPackageName = AppPackageName.Trim();
        entry.AppName = AppName.Trim();
        entry.Email = Email.Trim();
        entry.Phone = Phone.Trim();
        entry.AddressLine = AddressLine.Trim();
        entry.City = City.Trim();
        entry.State = State.Trim();
        entry.ZipCode = ZipCode.Trim();
        entry.Country = Country.Trim();
        entry.CreditCardNumber = CreditCardNumber.Trim();
        entry.CreditCardHolder = CreditCardHolder.Trim();
        entry.CreditCardExpiry = CreditCardExpiry.Trim();
        entry.CreditCardCvv = CreditCardCvv.Trim();
        entry.PasskeyBindings = PasskeyBindings.Trim();
        entry.SshKeyData = SshKeyData.Trim();
        entry.SsoProvider = SsoProvider.Trim();
        entry.WifiMetadata = WifiMetadata.Trim();
        entry.LoginType = SelectedLoginType?.Value ?? PasswordLoginType.Password;
        entry.CategoryId = SelectedCategory?.Id;
        entry.BoundNoteId = SelectedBoundNote?.Id;
        entry.IsFavorite = IsFavorite;
        return entry;
    }

    public IReadOnlyList<PasswordEntry> BuildEntries(IReadOnlyList<string> storedPasswords)
    {
        if (storedPasswords.Count == 0)
        {
            return [BuildEntry("")];
        }

        return storedPasswords.Select(BuildEntry).ToArray();
    }

    public IReadOnlyList<string> GetPasswordRows()
    {
        return NormalizePasswordRows(SplitRows(PasswordLines));
    }

    public IReadOnlyList<CustomField> GetCustomFields()
    {
        return SplitRows(CustomFieldsText)
            .Select((row, index) => ParseCustomField(row, index))
            .Where(field => field is not null)
            .Select(field => field!)
            .ToArray();
    }

    [RelayCommand]
    private void GeneratePassword()
    {
        var rows = GetPasswordRows().ToList();
        if (rows.Count == 0)
        {
            rows.Add(_passwordGenerator.GeneratePassword(24));
        }
        else
        {
            rows[0] = _passwordGenerator.GeneratePassword(24);
        }

        PasswordLines = string.Join(Environment.NewLine, rows);
    }

    private string EncodeWebsites()
    {
        return string.Join(", ", ParseWebsiteRows(WebsiteLines));
    }

    private static IReadOnlyList<string> ParseWebsiteRows(string value)
    {
        return SplitRows(value.Replace('，', ','))
            .SelectMany(row => row.Split(',', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries))
            .Select(row => row.Trim())
            .Where(row => row.Length > 0)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToArray();
    }

    private static IReadOnlyList<string> NormalizePasswordRows(IEnumerable<string> values)
    {
        return values
            .Select(row => row.Trim())
            .Where(row => row.Length > 0)
            .ToArray();
    }

    private static IEnumerable<string> SplitRows(string value)
    {
        return value.Split(["\r\n", "\n", "\r"], StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries);
    }

    private static string EncodeCustomFields(IEnumerable<CustomField> fields)
    {
        return string.Join(
            Environment.NewLine,
            fields
                .OrderBy(field => field.SortOrder)
                .ThenBy(field => field.Id)
                .Where(field => !string.IsNullOrWhiteSpace(field.Title) && !string.IsNullOrWhiteSpace(field.Value))
                .Select(field => $"{(field.IsProtected ? "!" : "")}{field.Title.Trim()}={field.Value.Trim()}"));
    }

    private static CustomField? ParseCustomField(string row, int sortOrder)
    {
        var separator = row.IndexOf('=');
        if (separator < 0)
        {
            separator = row.IndexOf(':');
        }

        if (separator <= 0)
        {
            return null;
        }

        var rawTitle = row[..separator].Trim();
        var isProtected = rawTitle.StartsWith('!');
        var title = isProtected ? rawTitle[1..].Trim() : rawTitle;
        var value = row[(separator + 1)..].Trim();
        if (string.IsNullOrWhiteSpace(title) || string.IsNullOrWhiteSpace(value))
        {
            return null;
        }

        return new CustomField
        {
            Title = title,
            Value = value,
            IsProtected = isProtected,
            SortOrder = sortOrder
        };
    }

    private static PasswordEntry Clone(PasswordEntry source)
    {
        return new PasswordEntry
        {
            Id = source.Id,
            Title = source.Title,
            Website = source.Website,
            Username = source.Username,
            Password = source.Password,
            Notes = source.Notes,
            IsFavorite = source.IsFavorite,
            SortOrder = source.SortOrder,
            IsGroupCover = source.IsGroupCover,
            AppPackageName = source.AppPackageName,
            AppName = source.AppName,
            Email = source.Email,
            Phone = source.Phone,
            AddressLine = source.AddressLine,
            City = source.City,
            State = source.State,
            ZipCode = source.ZipCode,
            Country = source.Country,
            CreditCardNumber = source.CreditCardNumber,
            CreditCardHolder = source.CreditCardHolder,
            CreditCardExpiry = source.CreditCardExpiry,
            CreditCardCvv = source.CreditCardCvv,
            CategoryId = source.CategoryId,
            BoundNoteId = source.BoundNoteId,
            KeepassDatabaseId = source.KeepassDatabaseId,
            KeepassGroupPath = source.KeepassGroupPath,
            KeepassEntryUuid = source.KeepassEntryUuid,
            KeepassGroupUuid = source.KeepassGroupUuid,
            MdbxDatabaseId = source.MdbxDatabaseId,
            MdbxFolderId = source.MdbxFolderId,
            AuthenticatorKey = source.AuthenticatorKey,
            PasskeyBindings = source.PasskeyBindings,
            SshKeyData = source.SshKeyData,
            LoginType = source.LoginType,
            SsoProvider = source.SsoProvider,
            SsoRefEntryId = source.SsoRefEntryId,
            WifiMetadata = source.WifiMetadata,
            CustomIconType = source.CustomIconType,
            CustomIconValue = source.CustomIconValue,
            CustomIconUpdatedAt = source.CustomIconUpdatedAt,
            IsDeleted = source.IsDeleted,
            DeletedAt = source.DeletedAt,
            IsArchived = source.IsArchived,
            ArchivedAt = source.ArchivedAt,
            ReplicaGroupId = source.ReplicaGroupId,
            BitwardenVaultId = source.BitwardenVaultId,
            BitwardenCipherId = source.BitwardenCipherId,
            BitwardenFolderId = source.BitwardenFolderId,
            BitwardenRevisionDate = source.BitwardenRevisionDate,
            BitwardenCipherType = source.BitwardenCipherType,
            BitwardenLocalModified = source.BitwardenLocalModified,
            CreatedAt = source.CreatedAt,
            UpdatedAt = source.UpdatedAt
        };
    }
}
