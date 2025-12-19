using Microsoft.EntityFrameworkCore;
using System;
using System.IO;
using Monica.Windows.Models;

namespace Monica.Windows.Data
{
    public class AppDbContext : DbContext
    {
        public DbSet<PasswordEntry> PasswordEntries { get; set; }
        public DbSet<SecureItem> SecureItems { get; set; }
        public DbSet<Category> Categories { get; set; }

        public string DbPath { get; }

        public AppDbContext()
        {
            var folder = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            var path = Path.Combine(folder, "Monica");
            Directory.CreateDirectory(path);
            DbPath = Path.Combine(path, "monica.db");
        }

        protected override void OnConfiguring(DbContextOptionsBuilder options)
            => options.UseSqlite($"Data Source={DbPath}");
            
        // Categories are NOT seeded - they come from WebDAV backup only
    }
}
