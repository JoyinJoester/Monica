using Monica.App.Services;
using Monica.App.ViewModels;
using Monica.Core.ImportExport;
using Monica.Core.Models;
using Monica.Core.Services;
using Monica.Data;
using Monica.Data.Repositories;
using Monica.Platform.Services;

namespace Monica.Tests;

public sealed class SecureNoteTests
{
    [Fact]
    public void Note_content_codec_roundtrips_markdown_tags_and_preview()
    {
        var payload = NoteContentCodec.BuildSavePayload(
            "",
            "# Recovery codes\n\n- alpha\n- beta\n\n![](monica-image://img-1)",
            "recovery, private, recovery",
            isMarkdown: true);

        Assert.Equal("Recovery codes", payload.Title);
        Assert.Contains("\"isMarkdown\":true", payload.ItemData);
        Assert.Equal("""["img-1"]""", payload.ImagePaths);

        var decoded = NoteContentCodec.Decode(payload.ItemData, payload.NotesCache);
        Assert.True(decoded.IsMarkdown);
        Assert.Equal(["recovery", "private"], decoded.Tags);
        Assert.Contains("alpha", NoteContentCodec.ToPlainPreview(decoded.Content, decoded.IsMarkdown));
    }

    [Fact]
    public async Task ViewModel_creates_edits_favorites_and_deletes_secure_note()
    {
        var viewModel = CreateViewModel();

        viewModel.NoteTitle = "Recovery";
        viewModel.NoteContent = "# Codes\n\n123456";
        viewModel.NoteTagsText = "account, emergency";
        viewModel.NoteIsMarkdown = true;
        await viewModel.SaveNoteCommand.ExecuteAsync(null);

        Assert.Single(viewModel.NoteItems);
        Assert.Equal("Recovery", viewModel.SelectedNote?.Title);
        Assert.Contains("\"tags\":[\"account\",\"emergency\"]", viewModel.SelectedNote?.ItemData);
        Assert.Equal("1 notes", viewModel.NoteCountText);

        await viewModel.ToggleNoteFavoriteCommand.ExecuteAsync(null);
        Assert.True(viewModel.SelectedNote?.IsFavorite);

        viewModel.NoteContent = "plain content";
        viewModel.NoteIsMarkdown = false;
        await viewModel.SaveNoteCommand.ExecuteAsync(null);
        Assert.Equal("plain content", viewModel.SelectedNote?.Notes);

        await viewModel.DeleteNoteCommand.ExecuteAsync(viewModel.SelectedNote);
        Assert.Empty(viewModel.NoteItems);
    }

    [Fact]
    public async Task Repository_soft_deletes_secure_note()
    {
        var path = GetTempDatabasePath();
        var factory = new SqliteConnectionFactory(path);
        var repository = new MonicaRepository(factory, new DatabaseMigrator(factory));
        var payload = NoteContentCodec.BuildSavePayload("Note", "secret", "", true);
        var note = new SecureItem
        {
            ItemType = VaultItemType.Note,
            Title = payload.Title,
            Notes = payload.NotesCache,
            ItemData = payload.ItemData,
            ImagePaths = payload.ImagePaths
        };
        await repository.SaveSecureItemAsync(note);

        await repository.SoftDeleteSecureItemAsync(note.Id);

        Assert.Empty(await repository.GetSecureItemsAsync(VaultItemType.Note));
        Assert.Single(await repository.GetSecureItemsAsync(VaultItemType.Note, includeDeleted: true));
    }

    private static MainWindowViewModel CreateViewModel()
    {
        var databasePath = GetTempDatabasePath();
        var factory = new SqliteConnectionFactory(databasePath);
        var migrator = new DatabaseMigrator(factory);
        return new MainWindowViewModel(
            new MonicaRepository(factory, migrator),
            new VaultCredentialStore(factory, migrator),
            new CryptoService(),
            new TotpService(),
            new PasswordGeneratorService(),
            new ImportExportService(),
            new PlatformCapabilityService(),
            new NoopClipboardService(),
            new MdbxVaultService(),
            new NoopPasswordEditorDialogService(),
            new NoopPasswordDetailDialogService(),
            new AppSettingsService(GetTempSettingsPath()),
            new LocalizationService());
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

    private sealed class NoopClipboardService : IClipboardService
    {
        public Task SetTextAsync(string text, CancellationToken cancellationToken = default) => Task.CompletedTask;
    }

    private sealed class NoopPasswordEditorDialogService : IPasswordEditorDialogService
    {
        public Task<PasswordEditorViewModel?> ShowAsync(
            PasswordEntry? entry,
            IReadOnlyList<Category> categories,
            string plainPassword,
            IReadOnlyList<string>? siblingPasswords = null,
            IReadOnlyList<SecureItem>? notes = null,
            IReadOnlyList<CustomField>? customFields = null,
            CancellationToken cancellationToken = default) =>
            Task.FromResult<PasswordEditorViewModel?>(null);
    }

    private sealed class NoopPasswordDetailDialogService : IPasswordDetailDialogService
    {
        public Task ShowAsync(
            PasswordEntry entry,
            IReadOnlyList<PasswordEntry> siblings,
            Category? category,
            SecureItem? boundNote,
            IReadOnlyList<CustomField> customFields,
            CancellationToken cancellationToken = default) =>
            Task.CompletedTask;
    }
}
