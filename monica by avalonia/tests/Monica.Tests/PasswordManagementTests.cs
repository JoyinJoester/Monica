using Monica.App.Services;
using Monica.App.ViewModels;
using Monica.Core.ImportExport;
using Monica.Core.Models;
using Monica.Core.Services;
using Monica.Data;
using Monica.Data.Repositories;
using Monica.Platform.Services;

namespace Monica.Tests;

public sealed class PasswordManagementTests
{
    [Fact]
    public async Task ViewModel_adds_password_from_editor_dialog()
    {
        var harness = CreateHarness();
        var category = new Category { Name = "Work", SortOrder = 1 };
        await harness.Repository.SaveCategoryAsync(category);
        await harness.ViewModel.LoadAsync();
        harness.Crypto.InitializeSession("correct password", new byte[16]);

        harness.Dialog.ConfigureNext(editor =>
        {
            editor.Title = "GitHub";
            editor.WebsiteLines = "github.com\nhttps://github.example";
            editor.Username = "dev@example.com";
            editor.PasswordLines = "plain-secret";
            editor.Notes = "Recovery account";
            editor.AuthenticatorKey = "JBSWY3DPEHPK3PXP";
            editor.AppName = "GitHub";
            editor.AppPackageName = "com.github.android";
            editor.Email = "security@example.com|recovery@example.com";
            editor.Phone = "15551234567";
            editor.AddressLine = "1 Octocat Way";
            editor.City = "San Francisco";
            editor.State = "CA";
            editor.ZipCode = "94107";
            editor.Country = "US";
            editor.CreditCardNumber = "4111111111111111";
            editor.CreditCardHolder = "Monica User";
            editor.CreditCardExpiry = "12/29";
            editor.CreditCardCvv = "123";
            editor.PasskeyBindings = """[{"rpId":"github.com"}]""";
            editor.WifiMetadata = """{"ssid":"Monica"}""";
            editor.SshKeyData = "ssh-ed25519 AAAA";
            editor.SelectedLoginType = editor.LoginTypeOptions.Single(choice => choice.Value == PasswordLoginType.SshKey);
            editor.IsFavorite = true;
            editor.SelectedCategory = editor.CategoryOptions.Single(choice => choice.Id == category.Id);
        });

        await harness.ViewModel.AddPasswordCommand.ExecuteAsync(null);

        var saved = Assert.Single(await harness.Repository.GetPasswordsAsync());
        Assert.Equal("GitHub", saved.Title);
        Assert.Equal("github.com, https://github.example", saved.Website);
        Assert.Equal("dev@example.com", saved.Username);
        Assert.Equal("Recovery account", saved.Notes);
        Assert.Equal("JBSWY3DPEHPK3PXP", saved.AuthenticatorKey);
        Assert.Equal("GitHub", saved.AppName);
        Assert.Equal("com.github.android", saved.AppPackageName);
        Assert.Equal("security@example.com|recovery@example.com", saved.Email);
        Assert.Equal("15551234567", saved.Phone);
        Assert.Equal("1 Octocat Way", saved.AddressLine);
        Assert.Equal("San Francisco", saved.City);
        Assert.Equal("CA", saved.State);
        Assert.Equal("94107", saved.ZipCode);
        Assert.Equal("US", saved.Country);
        Assert.Equal("4111111111111111", saved.CreditCardNumber);
        Assert.Equal("Monica User", saved.CreditCardHolder);
        Assert.Equal("12/29", saved.CreditCardExpiry);
        Assert.Equal("123", saved.CreditCardCvv);
        Assert.Equal("""[{"rpId":"github.com"}]""", saved.PasskeyBindings);
        Assert.Equal("""{"ssid":"Monica"}""", saved.WifiMetadata);
        Assert.Equal("ssh-ed25519 AAAA", saved.SshKeyData);
        Assert.Equal(PasswordLoginType.SshKey, saved.LoginType);
        Assert.Equal(category.Id, saved.CategoryId);
        Assert.True(saved.IsFavorite);
        Assert.NotEqual("plain-secret", saved.Password);
        Assert.Equal("plain-secret", harness.Crypto.DecryptString(saved.Password));
        Assert.Equal("GitHub", Assert.Single(harness.ViewModel.Passwords).Title);
    }

    [Fact]
    public async Task ViewModel_saves_bound_note_and_custom_fields()
    {
        var harness = CreateHarness();
        var payload = NoteContentCodec.BuildSavePayload("Recovery note", "codes", "", true);
        var note = new SecureItem
        {
            ItemType = VaultItemType.Note,
            Title = payload.Title,
            Notes = payload.NotesCache,
            ItemData = payload.ItemData,
            ImagePaths = payload.ImagePaths
        };
        await harness.Repository.SaveSecureItemAsync(note);
        await harness.ViewModel.LoadAsync();

        harness.Dialog.ConfigureNext(editor =>
        {
            Assert.Contains(editor.BoundNoteOptions, option => option.Id == note.Id && option.Title == "Recovery note");
            editor.Title = "With extras";
            editor.Username = "extra-user";
            editor.PasswordLines = "extra-secret";
            editor.SelectedBoundNote = editor.BoundNoteOptions.Single(option => option.Id == note.Id);
            editor.CustomFieldsText = "Security question=First school\n!Backup code=123456";
        });

        await harness.ViewModel.AddPasswordCommand.ExecuteAsync(null);

        var saved = Assert.Single(await harness.Repository.GetPasswordsAsync());
        Assert.Equal(note.Id, saved.BoundNoteId);
        var fields = await harness.Repository.GetCustomFieldsAsync(saved.Id);
        Assert.Equal(2, fields.Count);
        Assert.Equal("Security question", fields[0].Title);
        Assert.Equal("First school", fields[0].Value);
        Assert.False(fields[0].IsProtected);
        Assert.Equal("Backup code", fields[1].Title);
        Assert.Equal("123456", fields[1].Value);
        Assert.True(fields[1].IsProtected);
        Assert.Equal([saved.Id], await harness.Repository.SearchEntryIdsByCustomFieldContentAsync("school"));
    }

    [Fact]
    public async Task ViewModel_saves_password_authenticator_as_bound_totp_and_searches_rich_fields()
    {
        var harness = CreateHarness();
        await harness.ViewModel.LoadAsync();

        harness.Dialog.ConfigureNext(editor =>
        {
            editor.Title = "GitHub";
            editor.Username = "dev@example.com";
            editor.PasswordLines = "secret";
            editor.Notes = "recovery words live elsewhere";
            editor.AuthenticatorKey = "otpauth://totp/GitHub:dev%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&period=45&digits=8";
            editor.AppName = "GitHub Desktop";
            editor.Email = "security@example.com";
            editor.PasskeyBindings = """[{"rpId":"github.com"}]""";
            editor.CustomFieldsText = "Recovery hint=blue";
        });

        await harness.ViewModel.AddPasswordCommand.ExecuteAsync(null);

        var saved = Assert.Single(await harness.Repository.GetPasswordsAsync());
        Assert.True(saved.HasAuthenticator);
        var displayed = Assert.Single(harness.ViewModel.Passwords);
        Assert.Matches("^[0-9]{8}$", displayed.TotpCode);
        var boundTotp = Assert.Single(await harness.Repository.GetSecureItemsByBoundPasswordIdAsync(saved.Id));
        Assert.Equal(saved.Id, boundTotp.BoundPasswordId);
        Assert.Equal("GitHub", boundTotp.Title);
        Assert.Contains("JBSWY3DPEHPK3PXP", boundTotp.ItemData, StringComparison.Ordinal);
        Assert.Single(harness.ViewModel.TotpItems, item => item.BoundPasswordId == saved.Id);

        harness.ViewModel.SearchText = "blue";
        Assert.Equal([saved.Id], harness.ViewModel.FilteredPasswords.Select(item => item.Id).ToArray());
        harness.ViewModel.SearchText = "GitHub Desktop";
        Assert.Equal([saved.Id], harness.ViewModel.FilteredPasswords.Select(item => item.Id).ToArray());
        harness.ViewModel.SearchText = "github.com";
        Assert.Equal([saved.Id], harness.ViewModel.FilteredPasswords.Select(item => item.Id).ToArray());
    }

    [Fact]
    public async Task ViewModel_adds_grouped_passwords_from_multiple_password_lines()
    {
        var harness = CreateHarness();
        await harness.ViewModel.LoadAsync();
        harness.Crypto.InitializeSession("correct password", new byte[16]);

        harness.Dialog.ConfigureNext(editor =>
        {
            editor.Title = "Grouped";
            editor.WebsiteLines = "example.com\nhttps://example.org\nexample.com";
            editor.Username = "group-user";
            editor.PasswordLines = "first-secret\nsecond-secret";
        });

        await harness.ViewModel.AddPasswordCommand.ExecuteAsync(null);

        var saved = (await harness.Repository.GetPasswordsAsync()).OrderBy(item => item.Id).ToArray();
        Assert.Equal(2, saved.Length);
        Assert.All(saved, item =>
        {
            Assert.Equal("Grouped", item.Title);
            Assert.Equal("example.com, https://example.org", item.Website);
            Assert.Equal("group-user", item.Username);
        });
        Assert.Equal("first-secret", harness.Crypto.DecryptString(saved[0].Password));
        Assert.Equal("second-secret", harness.Crypto.DecryptString(saved[1].Password));
        Assert.Equal(2, harness.ViewModel.Passwords.Count);
    }

    [Fact]
    public async Task ViewModel_edits_existing_password_and_preserves_id()
    {
        var harness = CreateHarness();
        harness.Crypto.InitializeSession("correct password", new byte[16]);
        var category = new Category { Name = "Personal" };
        await harness.Repository.SaveCategoryAsync(category);
        var existingFields = new[]
        {
            new CustomField { Title = "Old field", Value = "old", IsProtected = false }
        };
        var entry = new PasswordEntry
        {
            Title = "Old",
            Website = "https://old.example",
            Username = "old-user",
            Password = harness.Crypto.EncryptString("old-secret"),
            Notes = "old notes"
        };
        await harness.Repository.SavePasswordAsync(entry);
        await harness.Repository.ReplaceCustomFieldsAsync(entry.Id, existingFields);
        var sibling = new PasswordEntry
        {
            Title = "Old",
            Website = "https://old.example",
            Username = "old-user",
            Password = harness.Crypto.EncryptString("sibling-secret")
        };
        await harness.Repository.SavePasswordAsync(sibling);
        var removedSibling = new PasswordEntry
        {
            Title = "Old",
            Website = "https://old.example",
            Username = "old-user",
            Password = harness.Crypto.EncryptString("remove-me")
        };
        await harness.Repository.SavePasswordAsync(removedSibling);
        await harness.ViewModel.LoadAsync();

        harness.Dialog.ConfigureNext(editor =>
        {
            Assert.Equal(["old-secret", "sibling-secret", "remove-me"], SplitRows(editor.PasswordLines));
            Assert.Equal("Old field=old", editor.CustomFieldsText);
            editor.Title = "Updated";
            editor.WebsiteLines = "https://updated.example";
            editor.Username = "new-user";
            editor.PasswordLines = "new-secret\nsecond-new-secret";
            editor.Notes = "new notes";
            editor.SsoProvider = "GITHUB";
            editor.PasskeyBindings = """[{"credentialId":"abc"}]""";
            editor.WifiMetadata = """{"ssid":"Updated"}""";
            editor.CustomFieldsText = "New field=new";
            editor.SelectedLoginType = editor.LoginTypeOptions.Single(choice => choice.Value == PasswordLoginType.Sso);
            editor.SelectedCategory = editor.CategoryOptions.Single(choice => choice.Id == category.Id);
        });

        await harness.ViewModel.EditPasswordCommand.ExecuteAsync(harness.ViewModel.Passwords.First(item => item.Id == entry.Id));

        var saved = (await harness.Repository.GetPasswordsAsync()).OrderBy(item => item.Id).ToArray();
        Assert.Equal(2, saved.Length);
        Assert.Equal([entry.Id, sibling.Id], saved.Select(item => item.Id).ToArray());
        Assert.All(saved, item =>
        {
            Assert.Equal("Updated", item.Title);
            Assert.Equal("https://updated.example", item.Website);
            Assert.Equal("new-user", item.Username);
            Assert.Equal("new notes", item.Notes);
            Assert.Equal("GITHUB", item.SsoProvider);
            Assert.Equal("""[{"credentialId":"abc"}]""", item.PasskeyBindings);
            Assert.Equal("""{"ssid":"Updated"}""", item.WifiMetadata);
            Assert.Equal(PasswordLoginType.Sso, item.LoginType);
            Assert.Equal(category.Id, item.CategoryId);
        });
        Assert.Equal("new-secret", harness.Crypto.DecryptString(saved[0].Password));
        Assert.Equal("second-new-secret", harness.Crypto.DecryptString(saved[1].Password));
        Assert.Equal(2, harness.ViewModel.Passwords.Count);
        var updatedFields = await harness.Repository.GetCustomFieldsAsync(entry.Id);
        var updatedField = Assert.Single(updatedFields);
        Assert.Equal("New field", updatedField.Title);
        Assert.Equal("new", updatedField.Value);
        var deleted = (await harness.Repository.GetPasswordsAsync(includeDeleted: true)).Single(item => item.Id == removedSibling.Id);
        Assert.True(deleted.IsDeleted);
    }

    [Fact]
    public async Task ViewModel_edit_updates_existing_bound_totp_and_removes_it_when_authenticator_is_cleared()
    {
        var harness = CreateHarness();
        var entry = new PasswordEntry
        {
            Title = "Old",
            Username = "dev",
            Password = "secret",
            AuthenticatorKey = "JBSWY3DPEHPK3PXP"
        };
        await harness.Repository.SavePasswordAsync(entry);
        var duplicateTotp = new SecureItem
        {
            ItemType = VaultItemType.Totp,
            Title = "Duplicate",
            BoundPasswordId = entry.Id,
            ItemData = TotpDataResolver.ToItemData(TotpDataResolver.FromAuthenticatorKey("JBSWY3DPEHPK3PXP")!)
        };
        await harness.Repository.SaveSecureItemAsync(duplicateTotp);
        await harness.ViewModel.LoadAsync();

        harness.Dialog.ConfigureNext(editor =>
        {
            editor.Title = "Updated";
            editor.Username = "dev";
            editor.PasswordLines = "secret";
            editor.AuthenticatorKey = "otpauth://totp/Updated:dev?secret=JBSWY3DPEHPK3PXP&issuer=Updated&period=60";
        });

        await harness.ViewModel.EditPasswordCommand.ExecuteAsync(harness.ViewModel.Passwords.Single());

        var updatedTotp = Assert.Single(await harness.Repository.GetSecureItemsByBoundPasswordIdAsync(entry.Id));
        Assert.Equal(duplicateTotp.Id, updatedTotp.Id);
        Assert.Equal("Updated", updatedTotp.Title);
        Assert.Contains(@"""period"":60", updatedTotp.ItemData, StringComparison.Ordinal);

        harness.Dialog.ConfigureNext(editor =>
        {
            editor.Title = "No TOTP";
            editor.Username = "dev";
            editor.PasswordLines = "secret";
            editor.AuthenticatorKey = "";
        });

        await harness.ViewModel.EditPasswordCommand.ExecuteAsync(harness.ViewModel.Passwords.Single());

        Assert.Empty(await harness.Repository.GetSecureItemsByBoundPasswordIdAsync(entry.Id));
        var deleted = Assert.Single(await harness.Repository.GetSecureItemsByBoundPasswordIdAsync(entry.Id, includeDeleted: true));
        Assert.True(deleted.IsDeleted);
    }

    [Fact]
    public async Task ViewModel_delete_password_moves_entire_password_group_to_recycle_bin()
    {
        var harness = CreateHarness();
        var first = new PasswordEntry { Title = "Grouped", Website = "example.com", Username = "dev", Password = "one" };
        var second = new PasswordEntry { Title = "Grouped", Website = "example.com", Username = "dev", Password = "two" };
        await harness.Repository.SavePasswordAsync(first);
        await harness.Repository.SavePasswordAsync(second);
        await harness.ViewModel.LoadAsync();

        await harness.ViewModel.DeletePasswordCommand.ExecuteAsync(harness.ViewModel.Passwords.First(item => item.Id == first.Id));

        Assert.Empty(await harness.Repository.GetPasswordsAsync());
        Assert.All(await harness.Repository.GetPasswordsAsync(includeDeleted: true), item => Assert.True(item.IsDeleted));
        Assert.Empty(harness.ViewModel.Passwords);
    }

    [Fact]
    public async Task ViewModel_restores_and_permanently_deletes_password_group_from_recycle_bin()
    {
        var harness = CreateHarness();
        var first = new PasswordEntry
        {
            Title = "Recoverable",
            Website = "example.com",
            Username = "dev",
            Password = "one",
            AuthenticatorKey = "JBSWY3DPEHPK3PXP"
        };
        var second = new PasswordEntry
        {
            Title = "Recoverable",
            Website = "example.com",
            Username = "dev",
            Password = "two"
        };
        await harness.Repository.SavePasswordAsync(first);
        await harness.Repository.SavePasswordAsync(second);
        await harness.Repository.ReplaceCustomFieldsAsync(first.Id, [new CustomField { Title = "Question", Value = "Answer" }]);
        var totp = new SecureItem
        {
            ItemType = VaultItemType.Totp,
            Title = "Recoverable",
            BoundPasswordId = first.Id,
            ItemData = TotpDataResolver.ToItemData(TotpDataResolver.FromAuthenticatorKey(first.AuthenticatorKey)!)
        };
        await harness.Repository.SaveSecureItemAsync(totp);
        await harness.ViewModel.LoadAsync();

        await harness.ViewModel.DeletePasswordCommand.ExecuteAsync(harness.ViewModel.Passwords.First(item => item.Id == first.Id));

        Assert.Empty(harness.ViewModel.Passwords);
        Assert.Equal(2, harness.ViewModel.DeletedPasswords.Count);
        Assert.DoesNotContain(harness.ViewModel.TotpItems, item => item.BoundPasswordId == first.Id);
        Assert.Empty(await harness.Repository.GetSecureItemsByBoundPasswordIdAsync(first.Id));

        await harness.ViewModel.RestorePasswordCommand.ExecuteAsync(harness.ViewModel.DeletedPasswords.First(item => item.Id == first.Id));

        Assert.Equal(2, harness.ViewModel.Passwords.Count);
        Assert.Empty(harness.ViewModel.DeletedPasswords);
        Assert.Single(harness.ViewModel.TotpItems, item => item.BoundPasswordId == first.Id);
        Assert.Single(await harness.Repository.GetSecureItemsByBoundPasswordIdAsync(first.Id));

        await harness.ViewModel.DeletePasswordCommand.ExecuteAsync(harness.ViewModel.Passwords.First(item => item.Id == first.Id));
        await harness.ViewModel.DeletePasswordPermanentlyCommand.ExecuteAsync(harness.ViewModel.DeletedPasswords.First(item => item.Id == first.Id));

        Assert.Empty(harness.ViewModel.DeletedPasswords);
        Assert.Empty(await harness.Repository.GetPasswordsAsync(includeDeleted: true));
        Assert.Empty(await harness.Repository.GetCustomFieldsAsync(first.Id));
        Assert.Empty(await harness.Repository.GetSecureItemsByBoundPasswordIdAsync(first.Id, includeDeleted: true));
    }

    [Fact]
    public async Task ViewModel_shows_password_details_and_copies_individual_fields()
    {
        var harness = CreateHarness();
        harness.Crypto.InitializeSession("correct password", new byte[16]);
        var category = new Category { Name = "Engineering", SortOrder = 1 };
        await harness.Repository.SaveCategoryAsync(category);
        var notePayload = NoteContentCodec.BuildSavePayload("Recovery", "backup codes stored here", "ops", true);
        var note = new SecureItem
        {
            ItemType = VaultItemType.Note,
            Title = notePayload.Title,
            Notes = notePayload.NotesCache,
            ItemData = notePayload.ItemData,
            ImagePaths = notePayload.ImagePaths
        };
        await harness.Repository.SaveSecureItemAsync(note);
        var first = new PasswordEntry
        {
            Title = "GitHub",
            Website = "github.com",
            Username = "dev@example.com",
            Password = harness.Crypto.EncryptString("primary-secret"),
            Notes = "main account",
            CategoryId = category.Id,
            BoundNoteId = note.Id,
            AuthenticatorKey = "otpauth://totp/GitHub:dev%40example.com?secret=JBSWY3DPEHPK3PXP&issuer=GitHub&period=45&digits=8",
            AppName = "GitHub Desktop",
            PasskeyBindings = """[{"rpId":"github.com"}]"""
        };
        await harness.Repository.SavePasswordAsync(first);
        var second = new PasswordEntry
        {
            Title = "GitHub",
            Website = "github.com",
            Username = "dev@example.com",
            Password = harness.Crypto.EncryptString("backup-secret"),
            CategoryId = category.Id,
            BoundNoteId = note.Id
        };
        await harness.Repository.SavePasswordAsync(second);
        await harness.Repository.ReplaceCustomFieldsAsync(first.Id, [
            new CustomField { Title = "Recovery hint", Value = "blue", SortOrder = 0 },
            new CustomField { Title = "Backup code", Value = "654321", IsProtected = true, SortOrder = 1 }
        ]);
        await harness.ViewModel.LoadAsync();

        await harness.ViewModel.ShowPasswordDetailsCommand.ExecuteAsync(harness.ViewModel.Passwords.First(item => item.Id == first.Id));

        var details = Assert.IsType<PasswordDetailViewModel>(harness.DetailDialog.LastDetails);
        Assert.Equal(2, harness.DetailDialog.LastSiblings.Count);
        Assert.Equal("Engineering", harness.DetailDialog.LastCategory?.Name);
        Assert.Equal(note.Id, harness.DetailDialog.LastBoundNote?.Id);
        Assert.Equal(2, harness.DetailDialog.LastCustomFields.Count);

        var fields = details.Groups.SelectMany(group => group.Fields).ToArray();
        Assert.Contains(fields, field => field.Label == details.L.Username && field.DisplayValue == "dev@example.com");
        Assert.Contains(fields, field => field.Label == $"{details.L.Password} 1" && field.DisplayValue == "primary-secret");
        Assert.Contains(fields, field => field.Label == $"{details.L.Password} 2" && field.DisplayValue == "backup-secret");
        Assert.Contains(fields, field => field.Label == details.L.Category && field.DisplayValue == "Engineering");
        Assert.Contains(fields, field => field.Label == details.L.BoundNote && field.DisplayValue.Contains("backup codes", StringComparison.Ordinal));
        Assert.Contains(fields, field => field.Label == details.L.TotpCode && field.DisplayValue.Length == 8);
        Assert.Contains(fields, field => field.Label == "Backup code" && field.DisplayValue == "654321" && field.IsSensitive);

        var backupCode = fields.Single(field => field.Label == "Backup code");
        await details.CopyFieldCommand.ExecuteAsync(backupCode);

        Assert.Equal("654321", harness.Clipboard.Text);
        Assert.Equal(details.L.Format("CopiedFieldFormat", "Backup code"), details.StatusText);
    }

    [Fact]
    public async Task ViewModel_copies_username_and_batches_selected_passwords()
    {
        var harness = CreateHarness();
        var first = new PasswordEntry { Title = "First", Website = "one.example", Username = "one-user", Password = "one" };
        var second = new PasswordEntry { Title = "Second", Website = "two.example", Username = "two-user", Password = "two" };
        await harness.Repository.SavePasswordAsync(first);
        await harness.Repository.SavePasswordAsync(second);
        await harness.ViewModel.LoadAsync();

        await harness.ViewModel.CopyUsernameCommand.ExecuteAsync(harness.ViewModel.Passwords.First(item => item.Id == first.Id));

        Assert.Equal("one-user", harness.Clipboard.Text);

        var displayedFirst = harness.ViewModel.Passwords.First(item => item.Id == first.Id);
        var displayedSecond = harness.ViewModel.Passwords.First(item => item.Id == second.Id);
        displayedFirst.IsSelected = true;
        displayedSecond.IsSelected = true;

        await harness.ViewModel.FavoriteSelectedPasswordsCommand.ExecuteAsync(null);

        Assert.False(displayedFirst.IsSelected);
        Assert.False(displayedSecond.IsSelected);
        Assert.All(await harness.Repository.GetPasswordsAsync(), item => Assert.True(item.IsFavorite));
        Assert.False(harness.ViewModel.HasSelectedPasswords);

        displayedFirst.IsSelected = true;
        displayedSecond.IsSelected = true;

        await harness.ViewModel.DeleteSelectedPasswordsCommand.ExecuteAsync(null);

        Assert.Empty(harness.ViewModel.Passwords);
        Assert.Equal(2, harness.ViewModel.DeletedPasswords.Count);
        Assert.Empty(await harness.Repository.GetPasswordsAsync());
        Assert.All(await harness.Repository.GetPasswordsAsync(includeDeleted: true), item => Assert.True(item.IsDeleted));
        Assert.Equal(harness.ViewModel.L.Format("MovedSelectedPasswordsToRecycleBinFormat", 2), harness.ViewModel.StatusMessage);
    }

    [Fact]
    public async Task ViewModel_does_not_save_when_editor_is_cancelled()
    {
        var harness = CreateHarness();
        harness.Dialog.CancelNext();

        await harness.ViewModel.AddPasswordCommand.ExecuteAsync(null);

        Assert.Empty(await harness.Repository.GetPasswordsAsync());
        Assert.Empty(harness.ViewModel.Passwords);
    }

    private static PasswordHarness CreateHarness()
    {
        var databasePath = GetTempDatabasePath();
        var factory = new SqliteConnectionFactory(databasePath);
        var migrator = new DatabaseMigrator(factory);
        var repository = new MonicaRepository(factory, migrator);
        var crypto = new CryptoService();
        var localization = new LocalizationService();
        var generator = new PasswordGeneratorService();
        var dialog = new FakePasswordEditorDialogService(localization, generator);
        var clipboard = new CapturingClipboardService();
        var detailDialog = new FakePasswordDetailDialogService(localization, clipboard, crypto, new TotpService());
        var viewModel = new MainWindowViewModel(
            repository,
            new VaultCredentialStore(factory, migrator),
            crypto,
            new TotpService(),
            generator,
            new ImportExportService(),
            new PlatformCapabilityService(),
            clipboard,
            new MdbxVaultService(),
            dialog,
            detailDialog,
            new AppSettingsService(GetTempSettingsPath()),
            localization);

        return new PasswordHarness(viewModel, repository, crypto, dialog, detailDialog, clipboard);
    }

    private static string GetTempDatabasePath()
    {
        var path = Path.Combine(Path.GetTempPath(), "monica-tests", $"{Guid.NewGuid():N}.db");
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        return path;
    }

    private static string GetTempSettingsPath()
    {
        var path = Path.Combine(Path.GetTempPath(), "monica-tests", $"{Guid.NewGuid():N}.settings.json");
        Directory.CreateDirectory(Path.GetDirectoryName(path)!);
        return path;
    }

    private static string[] SplitRows(string value)
    {
        return value.Split(["\r\n", "\n", "\r"], StringSplitOptions.RemoveEmptyEntries);
    }

    private sealed record PasswordHarness(
        MainWindowViewModel ViewModel,
        IMonicaRepository Repository,
        ICryptoService Crypto,
        FakePasswordEditorDialogService Dialog,
        FakePasswordDetailDialogService DetailDialog,
        CapturingClipboardService Clipboard);

    private sealed class CapturingClipboardService : IClipboardService
    {
        public string Text { get; private set; } = "";

        public Task SetTextAsync(string text, CancellationToken cancellationToken = default)
        {
            Text = text;
            return Task.CompletedTask;
        }
    }

    private sealed class FakePasswordEditorDialogService(
        ILocalizationService localization,
        IPasswordGeneratorService passwordGenerator) : IPasswordEditorDialogService
    {
        private Action<PasswordEditorViewModel>? _configureNext;
        private bool _cancelNext;

        public void ConfigureNext(Action<PasswordEditorViewModel> configure)
        {
            _cancelNext = false;
            _configureNext = configure;
        }

        public void CancelNext()
        {
            _cancelNext = true;
            _configureNext = null;
        }

        public Task<PasswordEditorViewModel?> ShowAsync(
            PasswordEntry? entry,
            IReadOnlyList<Category> categories,
            string plainPassword,
            IReadOnlyList<string>? siblingPasswords = null,
            IReadOnlyList<SecureItem>? notes = null,
            IReadOnlyList<CustomField>? customFields = null,
            CancellationToken cancellationToken = default)
        {
            if (_cancelNext)
            {
                _cancelNext = false;
                return Task.FromResult<PasswordEditorViewModel?>(null);
            }

            var editor = new PasswordEditorViewModel(localization, passwordGenerator, entry, categories, plainPassword, siblingPasswords, notes, customFields);
            _configureNext?.Invoke(editor);
            _configureNext = null;
            return Task.FromResult<PasswordEditorViewModel?>(editor.Validate() ? editor : null);
        }
    }

    private sealed class FakePasswordDetailDialogService(
        ILocalizationService localization,
        IClipboardService clipboardService,
        ICryptoService cryptoService,
        ITotpService totpService) : IPasswordDetailDialogService
    {
        public PasswordDetailViewModel? LastDetails { get; private set; }
        public IReadOnlyList<PasswordEntry> LastSiblings { get; private set; } = [];
        public Category? LastCategory { get; private set; }
        public SecureItem? LastBoundNote { get; private set; }
        public IReadOnlyList<CustomField> LastCustomFields { get; private set; } = [];

        public Task ShowAsync(
            PasswordEntry entry,
            IReadOnlyList<PasswordEntry> siblings,
            Category? category,
            SecureItem? boundNote,
            IReadOnlyList<CustomField> customFields,
            CancellationToken cancellationToken = default)
        {
            LastSiblings = siblings;
            LastCategory = category;
            LastBoundNote = boundNote;
            LastCustomFields = customFields;
            LastDetails = new PasswordDetailViewModel(
                localization,
                clipboardService,
                cryptoService,
                totpService,
                entry,
                siblings,
                category,
                boundNote,
                customFields);
            return Task.CompletedTask;
        }
    }
}
