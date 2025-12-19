using System;
using System.Collections.ObjectModel;
using System.Linq;
using System.Threading.Tasks;
using CommunityToolkit.Mvvm.ComponentModel;
using Microsoft.EntityFrameworkCore;
using Monica.Windows.Data;
using Monica.Windows.Models;

namespace Monica.Windows.ViewModels
{
    public partial class PasswordListViewModel : ObservableObject
    {
        private readonly AppDbContext _context;
        private ObservableCollection<PasswordEntry> _allPasswords = new();

        [ObservableProperty]
        private ObservableCollection<PasswordEntry> _filteredPasswords = new();

        [ObservableProperty]
        private bool _isLoading;

        private string _searchQuery = "";
        public string SearchQuery
        {
            get => _searchQuery;
            set
            {
                if (SetProperty(ref _searchQuery, value))
                {
                    ApplyFilter();
                }
            }
        }

        public PasswordListViewModel(AppDbContext context)
        {
            _context = context;
        }

        public async Task LoadDataAsync(object? filterParameter = null, string? quickFilter = null)
        {
            IsLoading = true;
            try
            {
                IQueryable<PasswordEntry> query = _context.PasswordEntries.Include(x => x.Category);
                
                // Apply navigation filter (sidebar selection)
                if (filterParameter is string filter && filter == "Favorites")
                {
                    query = query.Where(x => x.IsFavorite);
                }
                else if (filterParameter is int categoryId)
                {
                    query = query.Where(x => x.CategoryId == categoryId);
                }
                
                // Apply quick filter (header dropdown)
                if (!string.IsNullOrEmpty(quickFilter))
                {
                    query = quickFilter switch
                    {
                        "Uncategorized" => query.Where(x => x.CategoryId == null),
                        "NotFavorite" => query.Where(x => !x.IsFavorite),
                        _ => query // "All" - no additional filter
                    };
                }
                
                var items = await query
                    .OrderByDescending(x => x.IsFavorite)  // 收藏的排在最前
                    .ThenByDescending(x => x.UpdatedAt)
                    .ToListAsync();

                _allPasswords = new ObservableCollection<PasswordEntry>(items);
                ApplyFilter();
            }
            finally
            {
                IsLoading = false;
            }
        }

        private void ApplyFilter()
        {
            if (string.IsNullOrWhiteSpace(SearchQuery))
            {
                FilteredPasswords = new ObservableCollection<PasswordEntry>(_allPasswords);
            }
            else
            {
                var query = SearchQuery.ToLower();
                var filtered = _allPasswords.Where(p =>
                    p.Title.ToLower().Contains(query) ||
                    p.Username.ToLower().Contains(query) ||
                    p.Website.ToLower().Contains(query)).ToList();
                FilteredPasswords = new ObservableCollection<PasswordEntry>(filtered);
            }
        }

        public async Task AddEntryAsync(PasswordEntry entry)
        {
            entry.CreatedAt = DateTime.Now;
            entry.UpdatedAt = DateTime.Now;
            
            _context.PasswordEntries.Add(entry);
            await _context.SaveChangesAsync();
            
            _allPasswords.Insert(0, entry);
            ApplyFilter();
        }

        public async Task UpdateEntryAsync(PasswordEntry entry)
        {
            var existing = await _context.PasswordEntries.FindAsync(entry.Id);
            if (existing != null)
            {
                existing.Title = entry.Title;
                existing.Website = entry.Website;
                existing.Username = entry.Username;
                existing.EncryptedPassword = entry.EncryptedPassword;
                existing.Notes = entry.Notes;
                existing.UpdatedAt = DateTime.Now;

                await _context.SaveChangesAsync();

                // Update local collection
                var existingItem = _allPasswords.FirstOrDefault(p => p.Id == entry.Id);
                if (existingItem != null)
                {
                    var index = _allPasswords.IndexOf(existingItem);
                    if (index >= 0)
                    {
                        _allPasswords[index] = existing;
                    }
                }
                ApplyFilter();
            }
        }

        public async Task DeleteEntryAsync(PasswordEntry entry)
        {
            var existing = await _context.PasswordEntries.FindAsync(entry.Id);
            if (existing != null)
            {
                _context.PasswordEntries.Remove(existing);
                await _context.SaveChangesAsync();

                var toRemove = _allPasswords.FirstOrDefault(p => p.Id == entry.Id);
                if (toRemove != null)
                {
                    _allPasswords.Remove(toRemove);
                }
                ApplyFilter();
            }
        }
    }
}
