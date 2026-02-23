using Avalonia;
using Avalonia.Controls;
using Avalonia.Markup.Xaml;
using ReactiveUI.Avalonia;

namespace BD.WTTS.UI.Views.Pages;

public partial class AuthenticatorGeneralImportPage : ReactiveUserControl<AuthenticatorGeneralImportPageViewModel>
{
    public AuthenticatorGeneralImportPage()
    {
        InitializeComponent();
        //DataContext ??= new AuthenticatorGeneralImportPageViewModel();
    }
}