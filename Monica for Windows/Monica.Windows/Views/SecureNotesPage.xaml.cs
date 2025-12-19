using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Monica.Windows.Models;
using Monica.Windows.Services;
using System;
using System.Collections.Generic;

namespace Monica.Windows.Views
{
    public sealed partial class SecureNotesPage : Page
    {
        private NotesHomeView? _homeView;
        private readonly Dictionary<long, TabViewItem> _openNoteTabs = new();
        private TabViewItem? _homeTab;

        public SecureNotesPage()
        {
            this.InitializeComponent();
            this.Loaded += SecureNotesPage_Loaded;
        }

        private void SecureNotesPage_Loaded(object sender, RoutedEventArgs e)
        {
            // Create Home tab (pinned, cannot close)
            _homeView = new NotesHomeView();
            _homeView.OpenNoteRequested += OnOpenNoteRequested;
            _homeView.CreateNoteRequested += OnCreateNoteRequested;

            _homeTab = new TabViewItem
            {
                Header = "主页",
                IconSource = new SymbolIconSource { Symbol = Symbol.Home },
                IsClosable = false,
                Content = _homeView
            };

            NotesTabView.TabItems.Add(_homeTab);
            NotesTabView.SelectedItem = _homeTab;
        }

        private void OnOpenNoteRequested(SecureItem item)
        {
            // Check if already open
            if (_openNoteTabs.TryGetValue(item.Id, out var existingTab))
            {
                NotesTabView.SelectedItem = existingTab;
                return;
            }

            // Create new tab
            var editor = new NoteEditorView();
            editor.LoadNote(item);
            editor.TitleChanged += (title) => UpdateTabHeader(item.Id, title);
            editor.NoteSaved += () => _homeView?.RefreshData();

            // Extract first line only for tab header
            var tabTitle = GetFirstLine(item.Title);

            var tab = new TabViewItem
            {
                Header = tabTitle,
                IconSource = new SymbolIconSource { Symbol = Symbol.Edit },
                Content = editor,
                Tag = item.Id
            };

            _openNoteTabs[item.Id] = tab;
            NotesTabView.TabItems.Add(tab);
            NotesTabView.SelectedItem = tab;
        }

        private void OnCreateNoteRequested()
        {
            var editor = new NoteEditorView();
            editor.CreateNewNote();
            
            var tempId = -DateTime.Now.Ticks; // Temporary negative ID for new notes
            
            editor.TitleChanged += (title) => UpdateTabHeader(tempId, title);
            editor.NoteSaved += () =>
            {
                // Update the tab's tag to the real ID after save
                var realId = editor.GetNoteId();
                if (realId.HasValue && _openNoteTabs.ContainsKey(tempId))
                {
                    var tab = _openNoteTabs[tempId];
                    _openNoteTabs.Remove(tempId);
                    tab.Tag = realId.Value;
                    _openNoteTabs[realId.Value] = tab;
                }
                _homeView?.RefreshData();
            };

            var tab = new TabViewItem
            {
                Header = "新笔记",
                IconSource = new SymbolIconSource { Symbol = Symbol.Edit },
                Content = editor,
                Tag = tempId
            };

            _openNoteTabs[tempId] = tab;
            NotesTabView.TabItems.Add(tab);
            NotesTabView.SelectedItem = tab;
        }

        private void UpdateTabHeader(long noteId, string title)
        {
            if (_openNoteTabs.TryGetValue(noteId, out var tab))
            {
                tab.Header = GetFirstLine(title);
            }
        }

        private static string GetFirstLine(string? text)
        {
            if (string.IsNullOrWhiteSpace(text)) return "新笔记";
            
            // Get first line only
            var firstLine = text.Split(new[] { '\r', '\n' }, StringSplitOptions.RemoveEmptyEntries);
            var result = firstLine.Length > 0 ? firstLine[0].Trim() : "新笔记";
            
            // Limit to 20 characters for tab display
            if (result.Length > 20) result = result.Substring(0, 20) + "...";
            
            return result;
        }

        private void NotesTabView_AddTabButtonClick(TabView sender, object args)
        {
            OnCreateNoteRequested();
        }

        private void NotesTabView_TabCloseRequested(TabView sender, TabViewTabCloseRequestedEventArgs args)
        {
            if (args.Tab == _homeTab) return; // Cannot close home tab

            if (args.Tab.Tag is long noteId)
            {
                _openNoteTabs.Remove(noteId);
            }

            NotesTabView.TabItems.Remove(args.Tab);
        }

        private void NotesTabView_SelectionChanged(object sender, SelectionChangedEventArgs e)
        {
            // Refresh home view when switching back to it
            if (NotesTabView.SelectedItem == _homeTab)
            {
                _homeView?.RefreshData();
            }
        }
    }
}
