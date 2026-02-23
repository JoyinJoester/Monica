using Avalonia.Controls;
using ReactiveUI.Avalonia;

namespace BD.WTTS.UI.Views.Windows;

public partial class CloudArchiveWindow : ReactiveAppWindow<CloudArchiveAppPageViewModel>
{
    public CloudArchiveWindow()
    {
        InitializeComponent();
    }

    public CloudArchiveWindow(int appid) : this()
    {
        DataContext ??= new CloudArchiveAppPageViewModel(appid);
    }
}
