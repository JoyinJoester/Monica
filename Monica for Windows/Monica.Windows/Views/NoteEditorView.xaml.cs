using Microsoft.Extensions.DependencyInjection;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using Monica.Windows.Data;
using Monica.Windows.Models;
using Monica.Windows.Services;
using System;
using System.Threading.Tasks;

namespace Monica.Windows.Views
{
    public sealed partial class NoteEditorView : Page
    {
        private readonly ISecurityService _securityService;
        private readonly AppDbContext _context;
        private IServiceScope _scope;
        
        private SecureItem? _item;
        private bool _isNewNote;
        private bool _isDirty;
        private DispatcherTimer _autoSaveTimer;
        
        // Event to notify parent when title changes (for tab header update)
        public event Action<string>? TitleChanged;
        // Event to notify parent when note is saved (for refreshing home list)
        public event Action? NoteSaved;

        public NoteEditorView()
        {
            this.InitializeComponent();
            
            _scope = ((App)App.Current).Services.CreateScope();
            _securityService = _scope.ServiceProvider.GetRequiredService<ISecurityService>();
            _context = _scope.ServiceProvider.GetRequiredService<AppDbContext>();
            
            // Auto-save timer (save 2 seconds after last edit)
            _autoSaveTimer = new DispatcherTimer();
            _autoSaveTimer.Interval = TimeSpan.FromSeconds(2);
            _autoSaveTimer.Tick += AutoSaveTimer_Tick;
            
            this.Unloaded += NoteEditorView_Unloaded;
        }

        public void LoadNote(SecureItem item)
        {
            _item = item;
            _isNewNote = false;
            
            // Decrypt and load content
            try
            {
                var decrypted = _securityService.Decrypt(item.ItemData);
                TitleBox.Text = item.Title;
                
                // Parse JSON format (Android compatibility: {"content":"..."})
                if (!string.IsNullOrEmpty(decrypted) && decrypted.TrimStart().StartsWith("{"))
                {
                    try
                    {
                        var json = System.Text.Json.JsonDocument.Parse(decrypted);
                        if (json.RootElement.TryGetProperty("content", out var contentProp))
                        {
                            ContentBox.Text = contentProp.GetString() ?? "";
                        }
                        else
                        {
                            ContentBox.Text = decrypted;
                        }
                    }
                    catch
                    {
                        ContentBox.Text = decrypted;
                    }
                }
                else
                {
                    ContentBox.Text = decrypted ?? "";
                }
            }
            catch
            {
                ContentBox.Text = "";
            }
            
            _isDirty = false;
            UpdateSaveStatus("已保存");
        }

        public void CreateNewNote()
        {
            _item = new SecureItem
            {
                Title = "新笔记",
                Notes = "",
                ItemData = "",
                ItemType = ItemType.Note,
                CreatedAt = DateTime.Now,
                UpdatedAt = DateTime.Now
            };
            _isNewNote = true;
            
            TitleBox.Text = "";
            ContentBox.Text = "";
            _isDirty = false;
            UpdateSaveStatus("未保存");
            
            // Focus on title
            TitleBox.Focus(FocusState.Programmatic);
        }

        public long? GetNoteId() => _item?.Id;

        private void TitleBox_TextChanged(object sender, TextChangedEventArgs e)
        {
            _isDirty = true;
            UpdateSaveStatus("正在编辑...");
            _autoSaveTimer.Stop();
            _autoSaveTimer.Start();
            
            TitleChanged?.Invoke(string.IsNullOrWhiteSpace(TitleBox.Text) ? "新笔记" : TitleBox.Text);
        }

        private void ContentBox_TextChanged(object sender, TextChangedEventArgs e)
        {
            _isDirty = true;
            UpdateSaveStatus("正在编辑...");
            _autoSaveTimer.Stop();
            _autoSaveTimer.Start();
        }

        private async void AutoSaveTimer_Tick(object? sender, object e)
        {
            _autoSaveTimer.Stop();
            if (_isDirty)
            {
                await SaveNoteAsync();
            }
        }

        private async Task SaveNoteAsync()
        {
            if (_item == null) return;
            
            UpdateSaveStatus("保存中...");
            
            try
            {
                var title = string.IsNullOrWhiteSpace(TitleBox.Text) ? "新笔记" : TitleBox.Text.Trim();
                var content = ContentBox.Text ?? "";
                
                // Wrap in JSON format for Android compatibility
                var jsonContent = System.Text.Json.JsonSerializer.Serialize(new { content = content });
                
                // Encrypt content
                var encrypted = _securityService.Encrypt(jsonContent);
                
                // Generate preview (first 100 chars of content)
                var preview = content.Length > 100 ? content.Substring(0, 100) + "..." : content;
                
                _item.Title = title;
                _item.Notes = preview;
                _item.ItemData = encrypted;
                _item.UpdatedAt = DateTime.Now;
                
                if (_isNewNote)
                {
                    _context.SecureItems.Add(_item);
                    await _context.SaveChangesAsync();
                    _isNewNote = false;
                }
                else
                {
                    var existing = await _context.SecureItems.FindAsync(_item.Id);
                    if (existing != null)
                    {
                        existing.Title = _item.Title;
                        existing.Notes = _item.Notes;
                        existing.ItemData = _item.ItemData;
                        existing.UpdatedAt = _item.UpdatedAt;
                        await _context.SaveChangesAsync();
                    }
                }
                
                _isDirty = false;
                UpdateSaveStatus("已保存");
                NoteSaved?.Invoke();
            }
            catch (Exception ex)
            {
                UpdateSaveStatus($"保存失败: {ex.Message}");
            }
        }

        private void UpdateSaveStatus(string status)
        {
            SaveStatus.Text = status;
            SaveIcon.Glyph = status == "已保存" ? "\uE73E" : "\uE895";
        }

        private async void NoteEditorView_Unloaded(object sender, RoutedEventArgs e)
        {
            _autoSaveTimer.Stop();
            
            // Force save if dirty - AWAIT before disposing
            if (_isDirty && _item != null)
            {
                try
                {
                    await SaveNoteAsync();
                }
                catch { }
            }
            
            _scope?.Dispose();
        }
    }
}
