using System;
using System.Collections.Generic;
using System.IO;
using System.IO.Compression;
using System.Linq;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Serialization;
using System.Threading.Tasks;
using Microsoft.EntityFrameworkCore;
using Monica.Windows.Data;
using Monica.Windows.Models;
using System.Xml.Linq;

namespace Monica.Windows.Services
{
    public class WebDavConfig
    {
        public string ServerUrl { get; set; } = "";
        public string Username { get; set; } = "";
        public string Password { get; set; } = "";
    }

    public class BackupOptions
    {
        public bool IncludePasswords { get; set; } = true;
        public bool IncludeTotp { get; set; } = true;
        public bool IncludeNotes { get; set; } = true;
        public bool IncludeCards { get; set; } = true;
        public bool IncludeImages { get; set; } = true;
        public bool IncludeCategories { get; set; } = true;
    }

    public interface IWebDavService
    {
        WebDavConfig? GetCurrentConfig();
        void SaveConfig(WebDavConfig config);
        Task<bool> TestConnectionAsync();
        Task<List<string>> ListBackupsAsync();
        Task<string> CreateBackupAsync(bool encrypt = false, string? encryptPassword = null, BackupOptions? options = null);
        Task<string> RestoreBackupAsync(string fileName, string? decryptPassword = null);
        Task DeleteBackupAsync(string fileName);
    }

    public class WebDavService : IWebDavService
    {
        private readonly AppDbContext _dbContext;
        private readonly ISecurityService _securityService;
        private readonly IImageStorageService _imageStorageService;
        private HttpClient _client;
        
        // Configuration
        private string _serverUrl;
        private string _username;
        private string _password;

        public WebDavService(AppDbContext dbContext, ISecurityService securityService, IImageStorageService imageStorageService)
        {
            _dbContext = dbContext;
            _securityService = securityService;
            _imageStorageService = imageStorageService;
            _client = new HttpClient();
            _client.DefaultRequestHeaders.UserAgent.ParseAdd("Monica-Windows/1.0");
            
            // Load saved config on startup
            LoadSavedConfig();
        }

        private void LoadSavedConfig()
        {
            try
            {
                var configPath = GetConfigPath();
                if (File.Exists(configPath))
                {
                    var json = File.ReadAllText(configPath);
                    var config = JsonSerializer.Deserialize<WebDavConfig>(json);
                    if (config != null && !string.IsNullOrEmpty(config.ServerUrl))
                    {
                        Configure(config.ServerUrl, config.Username, config.Password);
                    }
                }
            }
            catch { }
        }

        private string GetConfigPath()
        {
            var folder = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            var appFolder = Path.Combine(folder, "Monica");
            Directory.CreateDirectory(appFolder);
            return Path.Combine(appFolder, "webdav_config.json");
        }

        public WebDavConfig? GetCurrentConfig()
        {
            if (string.IsNullOrEmpty(_serverUrl)) return null;
            return new WebDavConfig
            {
                ServerUrl = _serverUrl,
                Username = _username,
                Password = _password
            };
        }

        public void SaveConfig(WebDavConfig config)
        {
            Configure(config.ServerUrl, config.Username, config.Password);
            
            try
            {
                var json = JsonSerializer.Serialize(config);
                File.WriteAllText(GetConfigPath(), json);
            }
            catch { }
        }

        public void Configure(string url, string username, string password)
        {
            _serverUrl = url.TrimEnd('/');
            _username = username;
            _password = password;

            var authBytes = Encoding.UTF8.GetBytes($"{_username}:{_password}");
            _client.DefaultRequestHeaders.Authorization = new AuthenticationHeaderValue("Basic", Convert.ToBase64String(authBytes));
        }

        public async Task<bool> TestConnectionAsync()
        {
            try
            {
                // Standard WebDAV check: PROPFIND Depth 0
                var request = new HttpRequestMessage(new HttpMethod("PROPFIND"), _serverUrl);
                request.Headers.Add("Depth", "0");
                
                var response = await _client.SendAsync(request);
                
                return response.IsSuccessStatusCode || (int)response.StatusCode == 207;
            }
            catch
            {
                return false;
            }
        }

        public async Task<List<string>> ListBackupsAsync()
        {
            var results = new List<string>();
            try
            {
                // Android stores backups in 'Monica_Backups' subfolder
                string backupFolder = "Monica_Backups";
                string path = $"{_serverUrl}/{backupFolder}";
                
                var request = new HttpRequestMessage(new HttpMethod("PROPFIND"), path);
                request.Headers.Add("Depth", "1");
                var response = await _client.SendAsync(request);

                // Check if folder doesn't exist (404)
                if (response.StatusCode == System.Net.HttpStatusCode.NotFound)
                {
                    return results; // Return empty list if folder not found
                }

                if (!response.IsSuccessStatusCode && (int)response.StatusCode != 207)
                {
                    return results;
                }

                string xmlContent = await response.Content.ReadAsStringAsync();
                
                // Try to parse XML
                var doc = XDocument.Parse(xmlContent);
                var ns = doc.Root?.GetDefaultNamespace();
                
                // Handle DAV: namespace prefix
                XNamespace davNs = ns ?? "DAV:";
                
                var resources = doc.Descendants(davNs + "response");
                if (!resources.Any())
                {
                    // Try alternate namespace
                    davNs = "DAV:";
                    resources = doc.Descendants(davNs + "response");
                }
                
                foreach (var res in resources)
                {
                    var href = res.Element(davNs + "href")?.Value;
                    if (href != null && (href.EndsWith(".zip") || href.EndsWith(".enc.zip")))
                    {
                        // Decode URL encoded name
                        string fileName = System.Net.WebUtility.UrlDecode(Path.GetFileName(href));
                        results.Add(fileName);
                    }
                }
                
                return results.OrderByDescending(x => x).ToList();
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"ListBackups failed: {ex.Message}");
                return results;
            }
        }

        public async Task<string> CreateBackupAsync(bool encrypt, string? encryptPassword, BackupOptions? options = null)
        {
            options ??= new BackupOptions();
            string tempPath = Path.Combine(Path.GetTempPath(), $"monica_backup_{DateTime.Now:yyyyMMdd_HHmmss}");
            Directory.CreateDirectory(tempPath);

            try
            {
                // 1. Fetch Data (including categories)
                var passwords = await _dbContext.PasswordEntries.Include(p => p.Category).ToListAsync();
                var secureItems = await _dbContext.SecureItems.ToListAsync();
                var categories = await _dbContext.Categories.ToListAsync();

                // 2. Prepare Data Structure
                // passwords/
                string passwordsDir = Path.Combine(tempPath, "passwords");
                Directory.CreateDirectory(passwordsDir);
                
                // notes/
                string notesDir = Path.Combine(tempPath, "notes");
                Directory.CreateDirectory(notesDir);
                
                // 3. Export categories.json
                if (options.IncludeCategories && categories.Any())
                {
                    var categoryBackups = categories.Select(c => new { id = (long)c.Id, name = c.Name, sortOrder = c.SortOrder }).ToList();
                    var categoriesJson = JsonSerializer.Serialize(categoryBackups);
                    File.WriteAllText(Path.Combine(tempPath, "categories.json"), categoriesJson);
                }
                
                // 4. Serialize Passwords (JSON) with category info
                foreach (var pwd in passwords)
                {
                    if (!options.IncludePasswords) break;
                    try 
                    {
                        string decryptedPass = _securityService.Decrypt(pwd.EncryptedPassword);
                        var backupEntry = new PasswordBackupEntry
                        {
                            id = pwd.Id,
                            title = pwd.Title,
                            username = pwd.Username,
                            password = decryptedPass,
                            website = pwd.Website,
                            notes = pwd.Notes,
                            isFavorite = pwd.IsFavorite,
                            categoryId = pwd.CategoryId,
                            categoryName = pwd.Category?.Name,
                            createdAt = new DateTimeOffset(pwd.CreatedAt).ToUnixTimeMilliseconds(),
                            updatedAt = new DateTimeOffset(pwd.UpdatedAt).ToUnixTimeMilliseconds()
                        };
                        
                        string json = JsonSerializer.Serialize(backupEntry);
                        string fileName = $"password_{pwd.Id}_{backupEntry.createdAt}.json";
                        File.WriteAllText(Path.Combine(passwordsDir, fileName), json);
                    }
                    catch {}
                }

                // 4. Serialize Notes (JSON) & TOTP/Cards (CSV)
                var totpItems = new List<SecureItem>();
                var cardDocItems = new List<SecureItem>();

                foreach (var item in secureItems)
                {
                    try
                    {
                        string decryptedData = _securityService.Decrypt(item.ItemData);
                        
                        if (item.ItemType == ItemType.Note && options.IncludeNotes)
                        {
                            var backupEntry = new NoteBackupEntry
                            {
                                id = item.Id,
                                title = item.Title,
                                notes = item.Notes,
                                itemData = decryptedData, // Note content usually in Data or Notes? Android treats 'itemData' as note content for NOTE type sometimes? 
                                // Actually checking Android: itemData is main content.
                                isFavorite = item.IsFavorite,
                                createdAt = new DateTimeOffset(item.CreatedAt).ToUnixTimeMilliseconds(),
                                updatedAt = new DateTimeOffset(item.UpdatedAt).ToUnixTimeMilliseconds()
                            };
                            string json = JsonSerializer.Serialize(backupEntry);
                            string fileName = $"note_{item.Id}_{backupEntry.createdAt}.json";
                            File.WriteAllText(Path.Combine(notesDir, fileName), json);
                        }
                        else if (item.ItemType == ItemType.Totp && options.IncludeTotp)
                        {
                            totpItems.Add(item);
                        }
                        else if ((item.ItemType == ItemType.BankCard || item.ItemType == ItemType.Document) && options.IncludeCards)
                        {
                            cardDocItems.Add(item);
                        }
                    }
                    catch {}
                }

                // 5. Generate CSVs
                string timestamp = DateTime.Now.ToString("yyyyMMdd_HHmmss");
                
                // 5a. Generate Password CSV (Android compatible format)
                if (options.IncludePasswords && passwords.Count > 0)
                {
                    var pwdCsv = new StringBuilder();
                    pwdCsv.Append('\uFEFF'); // BOM
                    pwdCsv.AppendLine("ID,Title,Username,Password,Website,Notes,IsFavorite,CreatedAt,UpdatedAt");
                    
                    foreach (var pwd in passwords)
                    {
                        try
                        {
                            string decryptedPass = _securityService.Decrypt(pwd.EncryptedPassword);
                            var cols = new string[]
                            {
                                pwd.Id.ToString(),
                                EscapeCsv(pwd.Title),
                                EscapeCsv(pwd.Username),
                                EscapeCsv(decryptedPass),
                                EscapeCsv(pwd.Website),
                                EscapeCsv(pwd.Notes),
                                pwd.IsFavorite.ToString(),
                                new DateTimeOffset(pwd.CreatedAt).ToUnixTimeMilliseconds().ToString(),
                                new DateTimeOffset(pwd.UpdatedAt).ToUnixTimeMilliseconds().ToString()
                            };
                            pwdCsv.AppendLine(string.Join(",", cols));
                        }
                        catch {}
                    }
                    File.WriteAllText(Path.Combine(tempPath, $"Monica_{timestamp}_password.csv"), pwdCsv.ToString(), Encoding.UTF8);
                }
                
                // 5b. Generate TOTP CSV
                if (totpItems.Count > 0)
                {
                    string csv = GenerateCsv(totpItems);
                    File.WriteAllText(Path.Combine(tempPath, $"Monica_{timestamp}_totp.csv"), csv, Encoding.UTF8);
                }
                
                // 5c. Generate Cards/Docs CSV with ImagePaths
                if (cardDocItems.Count > 0)
                {
                    string csv = GenerateCsvWithImages(cardDocItems);
                    File.WriteAllText(Path.Combine(tempPath, $"Monica_{timestamp}_cards_docs.csv"), csv, Encoding.UTF8);
                }
                
                // 5d. Backup images folder
                if (options.IncludeImages)
                {
                    await BackupImagesAsync(tempPath, cardDocItems);
                }

                // 6. Zip (Use custom method to ensure forward slashes for cross-platform compatibility)
                string zipPath = Path.Combine(Path.GetTempPath(), $"monica_backup_{timestamp}.zip");
                CreateZipWithForwardSlashes(tempPath, zipPath);
                
                string finalPath = zipPath;
                string remoteFileName = Path.GetFileName(zipPath);

                // 7. Encrypt if needed
                if (encrypt && !string.IsNullOrEmpty(encryptPassword))
                {
                    string encPath = Path.Combine(Path.GetTempPath(), $"monica_backup_{timestamp}.enc.zip");
                    BackupEncryptionHelper.EncryptFile(zipPath, encPath, encryptPassword);
                    finalPath = encPath;
                    remoteFileName = Path.GetFileName(encPath);
                }

                // 8. Upload
                await UploadFileAsync(finalPath, remoteFileName);

                return remoteFileName;
            }
            finally
            {
                // Cleanup
                try { Directory.Delete(tempPath, true); } catch { }
            }
        }

        public async Task DeleteBackupAsync(string fileName)
        {
            // Android stores backups in 'Monica_Backups' subfolder
            string backupFolder = "Monica_Backups";
            string path = $"{_serverUrl}/{backupFolder}/{fileName}";
            var request = new HttpRequestMessage(new HttpMethod("DELETE"), path);
            var response = await _client.SendAsync(request);
            response.EnsureSuccessStatusCode();
        }

        private async Task UploadFileAsync(string localPath, string remoteName)
        {
            // Android stores backups in 'Monica_Backups' subfolder
            string backupFolder = "Monica_Backups";
            string backupUrl = $"{_serverUrl}/{backupFolder}";
            
            // Try to create folder if it doesn't exist (ignore errors if already exists)
            try
            {
                var mkcolRequest = new HttpRequestMessage(new HttpMethod("MKCOL"), backupUrl);
                await _client.SendAsync(mkcolRequest);
            }
            catch { /* Folder may already exist */ }

            using (var fs = File.OpenRead(localPath))
            {
                var content = new StreamContent(fs);
                // WebDAV PUT
                string url = $"{backupUrl}/{remoteName}";
                var response = await _client.PutAsync(url, content);
                response.EnsureSuccessStatusCode();
            }
        }

        public async Task<string> RestoreBackupAsync(string fileName, string? password)
        {
            string downloadPath = Path.Combine(Path.GetTempPath(), fileName);
            string extractPath = Path.Combine(Path.GetTempPath(), $"restore_{DateTime.Now.Ticks}");

            try
            {
                // 1. Download from Monica_Backups subfolder
                string backupUrl = $"{_serverUrl}/Monica_Backups/{fileName}";
                var response = await _client.GetAsync(backupUrl);
                response.EnsureSuccessStatusCode();
                using (var fs = File.Create(downloadPath))
                {
                    await response.Content.CopyToAsync(fs);
                }

                // 2. Decrypt if needed
                string zipPath = downloadPath;
                if (BackupEncryptionHelper.IsEncryptedFile(downloadPath))
                {
                    if (string.IsNullOrEmpty(password)) throw new Exception("Backup_Password_Required");
                    
                    string decryptedPath = downloadPath + ".decrypted.zip";
                    try 
                    {
                        BackupEncryptionHelper.DecryptFile(downloadPath, decryptedPath, password);
                        zipPath = decryptedPath;
                    } 
                    catch (Exception) 
                    {
                        throw new Exception("Wrong_Password");
                    }
                }

                // 3. Unzip
                Directory.CreateDirectory(extractPath);
                ZipFile.ExtractToDirectory(zipPath, extractPath);
                
                // Debug: Log basic extracted content
                System.Diagnostics.Debug.WriteLine($"=== RESTORE DEBUG ===");
                System.Diagnostics.Debug.WriteLine($"Extract path: {extractPath}");
                try
                {
                    System.Diagnostics.Debug.WriteLine($"Top-level files: {string.Join(", ", Directory.GetFiles(extractPath).Select(Path.GetFileName))}");
                    System.Diagnostics.Debug.WriteLine($"Top-level folders: {string.Join(", ", Directory.GetDirectories(extractPath).Select(Path.GetFileName))}");
                }
                catch { }
                
                // Check for nested folder (sometimes zip extracts into subfolder)
                var topDirs = Directory.GetDirectories(extractPath);
                if (topDirs.Length >= 1 && Directory.GetFiles(extractPath).Length == 0)
                {
                    // Backup might be in a subfolder - check if it has the expected content
                    foreach (var dir in topDirs)
                    {
                        try
                        {
                            // Look for CSV files or images folder in subdirs
                            if (Directory.GetFiles(dir, "*.csv").Length > 0 || 
                                Directory.Exists(Path.Combine(dir, "images")) ||
                                Directory.Exists(Path.Combine(dir, "passwords")))
                            {
                                extractPath = dir;
                                System.Diagnostics.Debug.WriteLine($"Found nested folder with content, adjusting to: {extractPath}");
                                break;
                            }
                        }
                        catch { }
                    }
                }

                int imported = 0;

                // 4. Import Passwords (JSON)
                string passwordsDir = Path.Combine(extractPath, "passwords");
                if (Directory.Exists(passwordsDir))
                {
                    foreach (var file in Directory.GetFiles(passwordsDir, "*.json"))
                    {
                        try
                        {
                            var json = File.ReadAllText(file);
                            var entry = JsonSerializer.Deserialize<PasswordBackupEntry>(json);
                            if (entry != null)
                            {
                                // Debug: Log what we're importing
                                System.Diagnostics.Debug.WriteLine($"Import password '{entry.title}': password length = {entry.password?.Length ?? 0}, looks encrypted = {entry.password?.Contains("==") == true}");
                                
                                // Check duplicate
                                bool exists = await _dbContext.PasswordEntries.AnyAsync(p => p.Title == entry.title && p.Username == entry.username);
                                if (!exists)
                                {
                                    string finalPassword = entry.password;
                                    bool isAlreadyEncrypted = false;
                                    
                                    // Smart detection: Check if password likely already encrypted (Android backup)
                                    // Android ciphertext is Base64, usually > 24 chars, and contains typical Base64 chars
                                    if (!string.IsNullOrEmpty(entry.password) && entry.password.Length > 24)
                                    {
                                        try
                                        {
                                            Convert.FromBase64String(entry.password);
                                            // It is valid Base64. Assume it's encrypted if it looks like a blob.
                                            // Heuristic: Plaintext passwords rarely match this profile exactly.
                                            isAlreadyEncrypted = true;
                                            System.Diagnostics.Debug.WriteLine($"Import: Password for '{entry.title}' detected as ALREADY ENCRYPTED (Length: {entry.password.Length})");
                                        }
                                        catch
                                        {
                                            // Not Base64, treat as plaintext
                                        }
                                    }

                                    if (!isAlreadyEncrypted)
                                    {
                                        finalPassword = _securityService.Encrypt(entry.password);
                                        System.Diagnostics.Debug.WriteLine($"Import: Encrypted plaintext password for '{entry.title}'");
                                    }
                                    
                                    // ✅ Resolve category by name
                                    int? resolvedCategoryId = null;
                                    if (!string.IsNullOrEmpty(entry.categoryName))
                                    {
                                        var existingCategory = await _dbContext.Categories
                                            .FirstOrDefaultAsync(c => c.Name == entry.categoryName);
                                        
                                        if (existingCategory != null)
                                        {
                                            resolvedCategoryId = existingCategory.Id;
                                        }
                                        else
                                        {
                                            // Create new category
                                            var maxSortOrder = await _dbContext.Categories.MaxAsync(c => (int?)c.SortOrder) ?? 0;
                                            var newCategory = new Category
                                            {
                                                Name = entry.categoryName,
                                                SortOrder = maxSortOrder + 1
                                            };
                                            _dbContext.Categories.Add(newCategory);
                                            await _dbContext.SaveChangesAsync();
                                            resolvedCategoryId = newCategory.Id;
                                            System.Diagnostics.Debug.WriteLine($"Created category: {entry.categoryName} (id={resolvedCategoryId})");
                                        }
                                    }
                                    
                                    _dbContext.PasswordEntries.Add(new PasswordEntry
                                    {
                                        Title = entry.title,
                                        Username = entry.username,
                                        EncryptedPassword = finalPassword,
                                        Website = entry.website,
                                        Notes = entry.notes,
                                        IsFavorite = entry.isFavorite,
                                        CategoryId = resolvedCategoryId,  // ✅ Set category
                                        CreatedAt = DateTimeOffset.FromUnixTimeMilliseconds(entry.createdAt).LocalDateTime,
                                        UpdatedAt = DateTimeOffset.FromUnixTimeMilliseconds(entry.updatedAt).LocalDateTime
                                    });
                                    imported++;
                                }
                            }
                        }
                        catch (Exception ex) 
                        {
                            System.Diagnostics.Debug.WriteLine($"Failed to import password: {ex.Message}");
                        }
                    }
                }

                // 5. Import Notes (JSON)
                string notesDir = Path.Combine(extractPath, "notes");
                if (Directory.Exists(notesDir))
                {
                    foreach (var file in Directory.GetFiles(notesDir, "*.json"))
                    {
                        try
                        {
                            var json = File.ReadAllText(file);
                            var entry = JsonSerializer.Deserialize<NoteBackupEntry>(json);
                            if (entry != null)
                            {
                                bool exists = await _dbContext.SecureItems.AnyAsync(s => s.Title == entry.title && s.ItemType == ItemType.Note);
                                if (!exists)
                                {
                                    _dbContext.SecureItems.Add(new SecureItem
                                    {
                                        Title = entry.title,
                                        ItemType = ItemType.Note,
                                        ItemData = _securityService.Encrypt(entry.itemData), // itemData is note content
                                        Notes = entry.notes,
                                        IsFavorite = entry.isFavorite,
                                        CreatedAt = DateTimeOffset.FromUnixTimeMilliseconds(entry.createdAt).LocalDateTime,
                                        UpdatedAt = DateTimeOffset.FromUnixTimeMilliseconds(entry.updatedAt).LocalDateTime
                                    });
                                    imported++;
                                }
                            }
                        }
                        catch {}
                    }
                }

                // 5.5 Import images from images/ folder
                int imageCount = 0;
                int skippedImageCount = 0;
                string imagesDir = Path.Combine(extractPath, "images");
                
                // Debug: List ALL contents at extractPath
                System.Diagnostics.Debug.WriteLine($"=== IMAGE IMPORT DEBUG ===");
                try {
                    System.Diagnostics.Debug.WriteLine($"Extract path: {extractPath}");
                    System.Diagnostics.Debug.WriteLine($"Images dir path: {imagesDir}");
                    System.Diagnostics.Debug.WriteLine($"Images dir exists: {Directory.Exists(imagesDir)}");
                    
                    /* Debug logging retained but simplified */
                } catch {}
                
                if (Directory.Exists(imagesDir))
                {
                    System.Diagnostics.Debug.WriteLine($"Found images folder: {imagesDir}");
                    var imageFiles = Directory.GetFiles(imagesDir);
                    
                    foreach (var imageFile in imageFiles)
                    {
                        try
                        {
                            // Use the image storage service to import each image
                            // Returns (FileName, IsNew) - IsNew is true only if actually imported (not skipped)
                            var result = await _imageStorageService.SaveImageFromPathAsync(imageFile);
                            if (result.IsNew)
                            {
                                imageCount++;
                                System.Diagnostics.Debug.WriteLine($"  SUCCESS: Imported {Path.GetFileName(imageFile)}");
                            }
                            else
                            {
                                skippedImageCount++;
                                System.Diagnostics.Debug.WriteLine($"  SKIPPED: {Path.GetFileName(imageFile)} already exists");
                            }
                        }
                        catch (Exception ex)
                        {
                            System.Diagnostics.Debug.WriteLine($"  FAILED: {Path.GetFileName(imageFile)} - {ex.Message}");
                        }
                    }
                }
                else
                {
                    System.Diagnostics.Debug.WriteLine($"WARNING: images folder NOT found at: {imagesDir}");
                }
                System.Diagnostics.Debug.WriteLine($"=== IMAGE IMPORT COMPLETE: {imageCount} new, {skippedImageCount} skipped ===");

                // 6. Import CSVs (TOTP / Cards)
                // Look for CSVs in root - no imageMapping needed since we keep original filenames
                foreach (var file in Directory.GetFiles(extractPath, "*.csv"))
                {
                    try
                    {
                        System.Diagnostics.Debug.WriteLine($"Processing CSV: {Path.GetFileName(file)}");
                        string content = File.ReadAllText(file);
                        imported += await ImportCsvContent(content, null);
                    }
                    catch (Exception ex)
                    {
                        System.Diagnostics.Debug.WriteLine($"Failed to process CSV: {ex.Message}");
                    }
                }

                try
                {
                    await _dbContext.SaveChangesAsync();
                }
                catch (Exception ex)
                {
                    // Extract inner exception for EF errors
                    var inner = ex.InnerException;
                    string details = inner != null ? inner.Message : ex.Message;
                    throw new Exception($"保存数据失败: {details}");
                }
                
                string msg = $"恢复完成。成功导入 {imported} 条数据，{imageCount} 张新照片";
                if (skippedImageCount > 0)
                {
                    msg += $" (跳过 {skippedImageCount} 张已存在)";
                }
                return msg + "。";

            }
            finally
            {
                try { File.Delete(downloadPath); } catch {}
                try { Directory.Delete(extractPath, true); } catch {}
            }
        }

        private string GenerateCsv(List<SecureItem> items)
        {
            var sb = new StringBuilder();
            sb.Append('\uFEFF'); // BOM
            sb.AppendLine("ID,Type,Title,Data,Notes,IsFavorite,ImagePaths,CreatedAt,UpdatedAt");
            
            foreach(var item in items)
            {
                string typeStr = item.ItemType switch {
                    ItemType.Totp => "TOTP",
                    ItemType.BankCard => "BANK_CARD",
                    ItemType.Document => "DOCUMENT",
                    _ => "NOTE"
                };
                string data = _securityService.Decrypt(item.ItemData);
                
                var cols = new string[]
                {
                    item.Id.ToString(),
                    typeStr,
                    EscapeCsv(item.Title),
                    EscapeCsv(data),
                    EscapeCsv(item.Notes),
                    item.IsFavorite.ToString(),
                    "",
                    new DateTimeOffset(item.CreatedAt).ToUnixTimeMilliseconds().ToString(),
                    new DateTimeOffset(item.UpdatedAt).ToUnixTimeMilliseconds().ToString()
                };
                sb.AppendLine(string.Join(",", cols));
            }
            return sb.ToString();
        }

        /// <summary>
        /// Generate CSV for cards/documents with ImagePaths populated from itemData
        /// </summary>
        private string GenerateCsvWithImages(List<SecureItem> items)
        {
            var sb = new StringBuilder();
            sb.Append('\uFEFF'); // BOM
            sb.AppendLine("ID,Type,Title,Data,Notes,IsFavorite,ImagePaths,CreatedAt,UpdatedAt");
            
            foreach(var item in items)
            {
                string typeStr = item.ItemType switch {
                    ItemType.BankCard => "BANK_CARD",
                    ItemType.Document => "DOCUMENT",
                    _ => "NOTE"
                };
                string data = _securityService.Decrypt(item.ItemData);
                
                // Extract imagePaths from itemData JSON and remove it from data
                // Android expects imagePaths as a separate field, not embedded in itemData
                string imagePaths = "";
                try
                {
                    using var doc = JsonDocument.Parse(data);
                    var root = doc.RootElement;
                    
                    // Extract imagePaths
                    if (root.TryGetProperty("imagePaths", out var imgPathsElem))
                    {
                        imagePaths = imgPathsElem.ToString();
                    }
                    else if (root.TryGetProperty("ImagePaths", out var imgPathsElem2))
                    {
                        imagePaths = imgPathsElem2.ToString();
                    }
                    
                    // Rebuild JSON without imagePaths for Android compatibility
                    var cleanedData = new Dictionary<string, object?>();
                    foreach (var prop in root.EnumerateObject())
                    {
                        string propName = prop.Name;
                        // Skip imagePaths since it's exported separately
                        if (propName.Equals("imagePaths", StringComparison.OrdinalIgnoreCase) ||
                            propName.Equals("ImagePaths", StringComparison.OrdinalIgnoreCase))
                        {
                            continue;
                        }
                        
                        // Copy other properties
                        cleanedData[propName] = prop.Value.ValueKind switch
                        {
                            JsonValueKind.String => prop.Value.GetString(),
                            JsonValueKind.Number => prop.Value.TryGetInt64(out var l) ? l : prop.Value.GetDouble(),
                            JsonValueKind.True => true,
                            JsonValueKind.False => false,
                            JsonValueKind.Null => null,
                            _ => prop.Value.ToString()
                        };
                    }
                    
                    data = JsonSerializer.Serialize(cleanedData);
                }
                catch { }
                
                var cols = new string[]
                {
                    item.Id.ToString(),
                    typeStr,
                    EscapeCsv(item.Title),
                    EscapeCsv(data),
                    EscapeCsv(item.Notes),
                    item.IsFavorite.ToString(),
                    EscapeCsv(imagePaths),
                    new DateTimeOffset(item.CreatedAt).ToUnixTimeMilliseconds().ToString(),
                    new DateTimeOffset(item.UpdatedAt).ToUnixTimeMilliseconds().ToString()
                };
                sb.AppendLine(string.Join(",", cols));
            }
            return sb.ToString();
        }

        /// <summary>
        /// Backup images referenced by cards/documents to images/ folder
        /// </summary>
        private async Task BackupImagesAsync(string backupPath, List<SecureItem> cardDocItems)
        {
            // Collect all image filenames from items
            var imageFileNames = new HashSet<string>();
            
            foreach (var item in cardDocItems)
            {
                try
                {
                    string data = _securityService.Decrypt(item.ItemData);
                    using var doc = JsonDocument.Parse(data);
                    
                    JsonElement imgPathsElem;
                    if (doc.RootElement.TryGetProperty("imagePaths", out imgPathsElem) ||
                        doc.RootElement.TryGetProperty("ImagePaths", out imgPathsElem))
                    {
                        if (imgPathsElem.ValueKind == JsonValueKind.Array)
                        {
                            foreach (var pathElem in imgPathsElem.EnumerateArray())
                            {
                                string path = pathElem.GetString() ?? "";
                                if (!string.IsNullOrEmpty(path))
                                {
                                    imageFileNames.Add(path);
                                }
                            }
                        }
                    }
                }
                catch { }
            }
            
            if (imageFileNames.Count == 0) return;
            
            // Create images folder
            string imagesDir = Path.Combine(backupPath, "images");
            Directory.CreateDirectory(imagesDir);
            
            // Get image source paths (check both locations)
            string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
            string monicaAttachmentsPath = Path.Combine(localAppData, "MonicaAttachments");
            string uwpAttachmentsPath = "";
            
            try
            {
                var localFolder = global::Windows.Storage.ApplicationData.Current.LocalFolder;
                uwpAttachmentsPath = Path.Combine(localFolder.Path, "Attachments");
            }
            catch { }
            
            int copied = 0;
            foreach (var fileName in imageFileNames)
            {
                try
                {
                    string? sourcePath = null;
                    
                    // Check MonicaAttachments first
                    string monicaPath = Path.Combine(monicaAttachmentsPath, fileName);
                    if (File.Exists(monicaPath))
                    {
                        sourcePath = monicaPath;
                    }
                    else if (!string.IsNullOrEmpty(uwpAttachmentsPath))
                    {
                        string uwpPath = Path.Combine(uwpAttachmentsPath, fileName);
                        if (File.Exists(uwpPath))
                        {
                            sourcePath = uwpPath;
                        }
                    }
                    
                    if (sourcePath != null)
                    {
                        // Read Windows-encrypted file, decrypt, then re-encrypt with Android format
                        string encryptedContent = await File.ReadAllTextAsync(sourcePath);
                        string base64 = _securityService.Decrypt(encryptedContent);
                        byte[] imageBytes = Convert.FromBase64String(base64);
                        
                        // Encrypt with Android format (AES-CBC)
                        byte[] androidEncrypted = EncryptForAndroid(imageBytes);
                        
                        string destPath = Path.Combine(imagesDir, fileName);
                        await File.WriteAllBytesAsync(destPath, androidEncrypted);
                        copied++;
                    }
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"Failed to backup image {fileName}: {ex.Message}");
                }
            }
            
            System.Diagnostics.Debug.WriteLine($"Backed up {copied}/{imageFileNames.Count} images");
        }

        /// <summary>
        /// Encrypt image bytes using Android format (AES/CBC/PKCS7 with fixed key/IV)
        /// </summary>
        private byte[] EncryptForAndroid(byte[] imageBytes)
        {
            byte[] key = System.Text.Encoding.UTF8.GetBytes("MonicaSecureKey1");
            byte[] iv = System.Text.Encoding.UTF8.GetBytes("MonicaSecureIV16");
            
            using var aes = System.Security.Cryptography.Aes.Create();
            aes.Key = key;
            aes.IV = iv;
            aes.Mode = System.Security.Cryptography.CipherMode.CBC;
            aes.Padding = System.Security.Cryptography.PaddingMode.PKCS7;
            
            using var encryptor = aes.CreateEncryptor();
            return encryptor.TransformFinalBlock(imageBytes, 0, imageBytes.Length);
        }

        private string EscapeCsv(string field)
        {
            if (string.IsNullOrEmpty(field)) return "";
            if (field.Contains(",") || field.Contains("\"") || field.Contains("\n") || field.Contains("\r"))
            {
                return "\"" + field.Replace("\"", "\"\"") + "\"";
            }
            return field;
        }

        private async Task<int> ImportCsvContent(string content, Dictionary<string, string>? imageMapping = null)
        {
            // Reuse logic similar to DataExportImportService but simplified for internal use
            // Assume valid format as it comes from backup
            int count = 0;
            // Parse CSV manually
            // ... (Simplified parser for time saving, assuming standard quotes)
            
            // To be safe, simple line split won't work for multiline notes.
            // Using a basic regex or state machine parser is better.
            
            var lines = new List<List<string>>();
            bool inQuotes = false;
            var currentLine = new List<string>();
            var currentField = new StringBuilder();
            
            if (content.StartsWith('\uFEFF')) content = content.Substring(1);

            for (int i=0; i<content.Length; i++)
            {
               char c = content[i];
               if (inQuotes)
               {
                   if (c == '"')
                   {
                       if (i+1 < content.Length && content[i+1] == '"') { currentField.Append('"'); i++; }
                       else inQuotes = false;
                   }
                   else currentField.Append(c);
               }
               else
               {
                   if (c == '"') inQuotes = true;
                   else if (c == ',') { currentLine.Add(currentField.ToString()); currentField.Clear(); }
                   else if (c == '\r' || c == '\n')
                   {
                       if (c=='\r' && i+1<content.Length && content[i+1]=='\n') i++;
                       if (currentLine.Count > 0 || currentField.Length > 0) 
                       {
                           currentLine.Add(currentField.ToString());
                           lines.Add(new List<string>(currentLine));
                           currentLine.Clear();
                           currentField.Clear();
                       }
                   }
                   else currentField.Append(c);
               }
            }
            if (currentLine.Count > 0 || currentField.Length > 0)
            {
                currentLine.Add(currentField.ToString());
                lines.Add(currentLine);
            }

            // Skip header
            foreach(var fields in lines.Skip(1))
            {
                if (fields.Count < 9) continue;
                try
                {
                    string title = fields[2];
                    string typeStr = fields[1];
                    string data = fields[3];
                    string notes = fields[4];
                    bool isFav = bool.TryParse(fields[5], out var f) && f;
                    string imagePaths = fields[6]; // Column 6: ImagePaths (JSON array or comma-separated)
                    long created = long.TryParse(fields[7], out var c) ? c : 0;
                    long updated = long.TryParse(fields[8], out var u) ? u : 0;

                    // Skip PASSWORD type - passwords are imported from JSON files, not from CSV
                    if (typeStr == "PASSWORD")
                    {
                        continue;
                    }

                    ItemType type = typeStr switch {
                        "TOTP" => ItemType.Totp,
                        "BANK_CARD" => ItemType.BankCard,
                        "DOCUMENT" => ItemType.Document,
                        "NOTE" => ItemType.Note,
                        _ => ItemType.Note // Default for unknown types, but PASSWORD is skipped above
                    };

                    bool exists = await _dbContext.SecureItems.AnyAsync(s => s.Title == title && s.ItemType == type);
                    if (!exists)
                    {
                        // Process imagePaths - directly inject from CSV to itemData JSON
                        // Since we copy images with original filenames, no mapping needed
                        string processedData = data;
                        if (!string.IsNullOrEmpty(imagePaths) && imagePaths != "[]" && (type == ItemType.Document || type == ItemType.BankCard))
                        {
                            try
                            {
                                System.Diagnostics.Debug.WriteLine($"Processing imagePaths for {title}: {imagePaths}");
                                
                                // Parse the imagePaths JSON array from CSV column 6
                                var imagePathsList = JsonSerializer.Deserialize<List<string>>(imagePaths);
                                if (imagePathsList != null && imagePathsList.Count > 0 && imagePathsList.Any(p => !string.IsNullOrEmpty(p)))
                                {
                                    // Filter out empty strings
                                    var validPaths = imagePathsList.Where(p => !string.IsNullOrEmpty(p)).ToList();
                                    
                                    // Inject imagePaths into data JSON
                                    using var doc = JsonDocument.Parse(data);
                                    using var ms = new MemoryStream();
                                    using (var writer = new Utf8JsonWriter(ms))
                                    {
                                        writer.WriteStartObject();
                                        foreach (var prop in doc.RootElement.EnumerateObject())
                                        {
                                            // Skip existing imagePaths (we'll add fresh one)
                                            if (prop.Name.Equals("imagePaths", StringComparison.OrdinalIgnoreCase))
                                            {
                                                continue;
                                            }
                                            prop.WriteTo(writer);
                                        }
                                        // Add imagePaths from CSV
                                        writer.WritePropertyName("imagePaths");
                                        JsonSerializer.Serialize(writer, validPaths);
                                        writer.WriteEndObject();
                                    }
                                    processedData = System.Text.Encoding.UTF8.GetString(ms.ToArray());
                                    System.Diagnostics.Debug.WriteLine($"Injected imagePaths for {title}: {string.Join(", ", validPaths)}");
                                }
                            }
                            catch (Exception ex)
                            {
                                System.Diagnostics.Debug.WriteLine($"Failed to process imagePaths for {title}: {ex.Message}");
                            }
                        }

                        _dbContext.SecureItems.Add(new SecureItem
                        {
                             Title = title,
                             ItemType = type,
                             ItemData = _securityService.Encrypt(processedData),
                             Notes = notes,
                             IsFavorite = isFav,
                             CreatedAt = DateTimeOffset.FromUnixTimeMilliseconds(created).LocalDateTime,
                             UpdatedAt = DateTimeOffset.FromUnixTimeMilliseconds(updated).LocalDateTime
                        });
                        count++;
                    }
                }
                catch {}
            }
            return count;
        }

        // DTOs with null defaults
        private class PasswordBackupEntry
        {
            public long id { get; set; }
            public string title { get; set; } = "";
            public string username { get; set; } = "";
            public string password { get; set; } = "";
            public string website { get; set; } = "";
            public string notes { get; set; } = "";
            public bool isFavorite { get; set; }
            public long? categoryId { get; set; }
            public string? categoryName { get; set; }  // ✅ Added for Android compatibility
            public long createdAt { get; set; } = DateTimeOffset.Now.ToUnixTimeMilliseconds();
            public long updatedAt { get; set; } = DateTimeOffset.Now.ToUnixTimeMilliseconds();
        }

        private class NoteBackupEntry
        {
            public long id { get; set; }
            public string title { get; set; } = "";
            public string notes { get; set; } = "";
            public string itemData { get; set; } = "";
            public bool isFavorite { get; set; }
            public long createdAt { get; set; } = DateTimeOffset.Now.ToUnixTimeMilliseconds();
            public long updatedAt { get; set; } = DateTimeOffset.Now.ToUnixTimeMilliseconds();
        }

        // Helper to recursively log directory contents
        private void LogDirectoryContents(string path, int depth)
        {
            string indent = new string(' ', depth * 2);
            try
            {
                foreach (var file in Directory.GetFiles(path))
                {
                    var info = new FileInfo(file);
                    System.Diagnostics.Debug.WriteLine($"{indent}[F] {info.Name} ({info.Length} bytes)");
                }
                foreach (var dir in Directory.GetDirectories(path))
                {
                    System.Diagnostics.Debug.WriteLine($"{indent}[D] {Path.GetFileName(dir)}/");
                    if (depth < 3) // Limit recursion depth
                    {
                        LogDirectoryContents(dir, depth + 1);
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"{indent}[ERROR] {ex.Message}");
            }
        }

        /// <summary>
        /// Create a ZIP file with forward slashes in entry paths for cross-platform compatibility.
        /// </summary>
        private void CreateZipWithForwardSlashes(string sourceDir, string zipPath)
        {
            using var archive = ZipFile.Open(zipPath, ZipArchiveMode.Create);
            var baseDir = new DirectoryInfo(sourceDir);
            
            foreach (var file in baseDir.EnumerateFiles("*", SearchOption.AllDirectories))
            {
                // Get relative path and convert backslashes to forward slashes
                string relativePath = Path.GetRelativePath(sourceDir, file.FullName);
                string entryName = relativePath.Replace('\\', '/');
                
                archive.CreateEntryFromFile(file.FullName, entryName);
            }
        }
    }
}
