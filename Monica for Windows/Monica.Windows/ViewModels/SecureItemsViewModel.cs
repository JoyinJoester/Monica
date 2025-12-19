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
    public partial class SecureItemsViewModel : ObservableObject
    {
        private readonly AppDbContext _context;
        private ObservableCollection<SecureItem> _allItems = new();

        [ObservableProperty]
        private ObservableCollection<SecureItem> _filteredItems = new();

        [ObservableProperty]
        private bool _isLoading;

        private ItemType _currentItemType;
        private ItemType[] _itemTypes = Array.Empty<ItemType>(); // Support multiple types

        public SecureItemsViewModel(AppDbContext context)
        {
            _context = context;
        }

        public void Initialize(ItemType type)
        {
            _currentItemType = type;
            _itemTypes = new[] { type };
        }
        
        // Initialize with multiple types (for CardsPage which shows both BankCard and Document)
        public void Initialize(params ItemType[] types)
        {
            _itemTypes = types;
            _currentItemType = types.Length > 0 ? types[0] : ItemType.Note;
        }

        public async Task LoadDataAsync()
        {
            IsLoading = true;
            try
            {
                var items = await _context.SecureItems
                    .Where(x => _itemTypes.Contains(x.ItemType))
                    .OrderByDescending(x => x.UpdatedAt)
                    .ToListAsync();

                _allItems = new ObservableCollection<SecureItem>(items);
                FilteredItems = new ObservableCollection<SecureItem>(_allItems);
            }
            finally
            {
                IsLoading = false;
            }
        }

        public async Task AddItemAsync(SecureItem item)
        {
            item.ItemType = _currentItemType;
            item.CreatedAt = DateTime.Now;
            item.UpdatedAt = DateTime.Now;
            
            _context.SecureItems.Add(item);
            await _context.SaveChangesAsync();
            
            _allItems.Insert(0, item);
            FilteredItems = new ObservableCollection<SecureItem>(_allItems);
        }

        public async Task DeleteItemAsync(SecureItem item)
        {
            var existing = await _context.SecureItems.FindAsync(item.Id);
            if (existing != null)
            {
                _context.SecureItems.Remove(existing);
                await _context.SaveChangesAsync();
                
                _allItems.Remove(item);
                FilteredItems = new ObservableCollection<SecureItem>(_allItems);
            }
        }

        public async Task UpdateItemAsync(SecureItem item)
        {
            var existing = await _context.SecureItems.FindAsync(item.Id);
            if (existing != null)
            {
                existing.Title = item.Title;
                existing.Notes = item.Notes;
                existing.ItemData = item.ItemData;
                existing.IsFavorite = item.IsFavorite;
                existing.UpdatedAt = DateTime.Now;
                
                await _context.SaveChangesAsync();
            }
        }
    }
}
