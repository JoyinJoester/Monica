using Avalonia.Controls;
using ReactiveUI.Avalonia;

namespace BD.WTTS.UI.Views.Pages;

public partial class DownloadPage : PageBase<DownloadPageViewModel>
{
    public DownloadPage()
    {
        InitializeComponent();
        DataContext ??= new DownloadPageViewModel();
    }
}
