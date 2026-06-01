using Avalonia.Controls;
using FluentAvalonia.UI.Controls;
using Monica.App;
using Monica.App.ViewModels;
using Monica.Core.Models;
using Monica.Core.Services;

namespace Monica.App.Services;

public interface IPasswordEditorDialogService
{
    Task<PasswordEditorViewModel?> ShowAsync(
        PasswordEntry? entry,
        IReadOnlyList<Category> categories,
        string plainPassword,
        IReadOnlyList<string>? siblingPasswords = null,
        IReadOnlyList<SecureItem>? notes = null,
        IReadOnlyList<CustomField>? customFields = null,
        CancellationToken cancellationToken = default);
}

public sealed class PasswordEditorDialogService(
    Func<Window> ownerProvider,
    ILocalizationService localization,
    IPasswordGeneratorService passwordGenerator) : IPasswordEditorDialogService
{
    public async Task<PasswordEditorViewModel?> ShowAsync(
        PasswordEntry? entry,
        IReadOnlyList<Category> categories,
        string plainPassword,
        IReadOnlyList<string>? siblingPasswords = null,
        IReadOnlyList<SecureItem>? notes = null,
        IReadOnlyList<CustomField>? customFields = null,
        CancellationToken cancellationToken = default)
    {
        cancellationToken.ThrowIfCancellationRequested();

        var editor = new PasswordEditorViewModel(localization, passwordGenerator, entry, categories, plainPassword, siblingPasswords, notes, customFields);
        var dialog = new FAContentDialog
        {
            Title = editor.DialogTitle,
            Content = new PasswordEditorDialog { DataContext = editor },
            PrimaryButtonText = localization.Save,
            CloseButtonText = localization.Cancel,
            DefaultButton = FAContentDialogButton.Primary
        };

        dialog.PrimaryButtonClick += (_, args) =>
        {
            if (!editor.Validate())
            {
                args.Cancel = true;
            }
        };

        var result = await dialog.ShowAsync(ownerProvider());
        return result == FAContentDialogResult.Primary ? editor : null;
    }
}
