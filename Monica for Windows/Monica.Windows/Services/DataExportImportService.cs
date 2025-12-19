using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Text;
using System.Text.Json;
using System.Threading.Tasks;
using Microsoft.EntityFrameworkCore;
using Monica.Windows.Data;
using Monica.Windows.Models;
using Windows.Storage;

namespace Monica.Windows.Services
{
    public enum ExportOption
    {
        All,
        Passwords,
        Totp,
        CardsDocs,
        Notes
    }

    public enum ExportFormat
    {
        Csv,
        Aegis
    }

    public class DataExportImportService
    {
        private readonly AppDbContext _dbContext;
        private readonly ISecurityService _securityService;

        private const string CsvSeparator = ",";
        private const string CsvQuote = "\"";
        private static readonly string[] CsvHeaders = new[]
        {
            "ID", "Type", "Title", "Data", "Notes", "IsFavorite",
            "ImagePaths", "CreatedAt", "UpdatedAt"
        };

        public DataExportImportService(AppDbContext dbContext, ISecurityService securityService)
        {
            _dbContext = dbContext;
            _securityService = securityService;
        }

        public async Task<string> ExportDataAsync(StorageFile file, ExportOption option, ExportFormat format, string? encryptionPassword = null)
        {
            try
            {
                // Fetch Data needed based on option
                var passwords = new List<PasswordEntry>();
                var secureItems = new List<SecureItem>();

                if (option == ExportOption.All || option == ExportOption.Passwords)
                {
                    passwords = await _dbContext.PasswordEntries.ToListAsync();
                }

                if (option == ExportOption.All || option != ExportOption.Passwords)
                {
                    var query = _dbContext.SecureItems.AsQueryable();
                    
                    if (option == ExportOption.Totp)
                    {
                        query = query.Where(i => i.ItemType == ItemType.Totp);
                    }
                    else if (option == ExportOption.CardsDocs)
                    {
                        query = query.Where(i => i.ItemType == ItemType.BankCard || i.ItemType == ItemType.Document);
                    }
                    else if (option == ExportOption.Notes)
                    {
                        query = query.Where(i => i.ItemType == ItemType.Note);
                    }
                    
                    secureItems = await query.ToListAsync();
                }

                // Handle AEGIS Export (Only for TOTP or when TOTP is selected)
                if (format == ExportFormat.Aegis)
                {
                    // Filter only TOTP items from the fetched list just in case
                    var totpItems = secureItems.Where(i => i.ItemType == ItemType.Totp).ToList();
                    string json = ExportToAegis(totpItems, encryptionPassword);
                    
                    using (var stream = await file.OpenStreamForWriteAsync())
                    using (var writer = new StreamWriter(stream, Encoding.UTF8))
                    {
                        await writer.WriteAsync(json);
                    }
                    return $"Successfully exported {totpItems.Count} TOTP items to Aegis JSON.";
                }

                // Handle CSV Export
                var lines = GenerateCsvLines(passwords, secureItems);

                using (var stream = await file.OpenStreamForWriteAsync())
                using (var writer = new StreamWriter(stream, Encoding.UTF8))
                {
                    writer.Write('\uFEFF'); // BOM
                    foreach (var line in lines)
                    {
                        await writer.WriteLineAsync(line);
                    }
                }

                return $"Successfully exported {passwords.Count + secureItems.Count} items to CSV.";
            }
            catch (Exception ex)
            {
                throw new Exception($"Export failed: {ex.Message}", ex);
            }
        }

        private string ExportToAegis(List<SecureItem> items, string? password)
        {
            var aegisEntries = new List<AegisExporter.AegisEntry>();
            var exporter = new AegisExporter();

            foreach (var item in items)
            {
                try
                {
                    string decryptedJson = _securityService.Decrypt(item.ItemData);
                    var totpData = JsonSerializer.Deserialize<TotpData>(decryptedJson);

                    if (totpData != null)
                    {
                        var entry = new AegisExporter.AegisEntry
                        {
                            Name = item.Title, 
                            Issuer = totpData.Issuer,
                            Note = item.Notes,
                            Secret = totpData.Secret,
                            Algorithm = totpData.Algorithm,
                            Digits = totpData.Digits,
                            Period = totpData.Period
                        };
                       
                        if (!string.IsNullOrEmpty(totpData.AccountName))
                        {
                            entry.Name = string.IsNullOrEmpty(totpData.AccountName) ? item.Title : totpData.AccountName;
                        }

                        aegisEntries.Add(entry);
                    }
                }
                catch (Exception ex)
                {
                    System.Diagnostics.Debug.WriteLine($"Error exporting TOTP {item.Id}: {ex.Message}");
                }
            }
            
            if (!string.IsNullOrEmpty(password))
            {
                return exporter.ExportToEncryptedAegisJson(aegisEntries, password);
            }
            else
            {
                return exporter.ExportToUnencryptedAegisJson(aegisEntries);
            }
        }

        private List<string> GenerateCsvLines(List<PasswordEntry> passwords, List<SecureItem> secureItems)
        {
            var lines = new List<string>();
            lines.Add(string.Join(CsvSeparator, CsvHeaders));

            foreach (var pwd in passwords)
            {
                try
                {
                    string decryptedPassword = _securityService.Decrypt(pwd.EncryptedPassword);
                    var dataBuilder = new StringBuilder();
                    dataBuilder.Append($"username:{pwd.Username};");
                    dataBuilder.Append($"password:{decryptedPassword}");
                    if (!string.IsNullOrEmpty(pwd.Website))
                    {
                        dataBuilder.Append($";website:{pwd.Website}");
                    }

                    var row = new string[]
                    {
                        pwd.Id.ToString(),
                        "PASSWORD",
                        EscapeCsvField(pwd.Title),
                        EscapeCsvField(dataBuilder.ToString()),
                        EscapeCsvField(pwd.Notes),
                        pwd.IsFavorite.ToString(),
                        "",
                        new DateTimeOffset(pwd.CreatedAt).ToUnixTimeMilliseconds().ToString(),
                        new DateTimeOffset(pwd.UpdatedAt).ToUnixTimeMilliseconds().ToString()
                    };
                    lines.Add(string.Join(CsvSeparator, row));
                }
                catch { }
            }

            foreach (var item in secureItems)
            {
                try
                {
                    string typeStr = item.ItemType switch
                    {
                        ItemType.Totp => "TOTP",
                        ItemType.BankCard => "BANK_CARD",
                        ItemType.Document => "DOCUMENT",
                        ItemType.Note => "NOTE",
                        ItemType.Password => "PASSWORD",
                        _ => item.ItemType.ToString().ToUpper()
                    };

                    string decryptedData = _securityService.Decrypt(item.ItemData);
                    string imagePaths = "";
                    
                    // For cards and documents, extract imagePaths from JSON and remove it
                    // Android expects imagePaths as a separate field, not embedded in itemData
                    if (item.ItemType == ItemType.BankCard || item.ItemType == ItemType.Document)
                    {
                        try
                        {
                            using var doc = JsonDocument.Parse(decryptedData);
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
                                if (propName.Equals("imagePaths", StringComparison.OrdinalIgnoreCase) ||
                                    propName.Equals("ImagePaths", StringComparison.OrdinalIgnoreCase))
                                {
                                    continue;
                                }
                                
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
                            
                            decryptedData = JsonSerializer.Serialize(cleanedData);
                        }
                        catch { }
                    }

                    var row = new string[]
                    {
                        item.Id.ToString(),
                        typeStr,
                        EscapeCsvField(item.Title),
                        EscapeCsvField(decryptedData),
                        EscapeCsvField(item.Notes),
                        item.IsFavorite.ToString(),
                        EscapeCsvField(imagePaths),
                        new DateTimeOffset(item.CreatedAt).ToUnixTimeMilliseconds().ToString(),
                        new DateTimeOffset(item.UpdatedAt).ToUnixTimeMilliseconds().ToString()
                    };
                    lines.Add(string.Join(CsvSeparator, row));
                }
                catch { }
            }
            return lines;
        }

        public async Task<string> ImportDataAsync(StorageFile file, string? password = null)
        {
             try
            {
                string content;
                using (var stream = await file.OpenStreamForReadAsync())
                using (var reader = new StreamReader(stream)) // Default UTF8
                {
                    content = await reader.ReadToEndAsync();
                }
                
                if (content.StartsWith('\uFEFF')) content = content.Substring(1);

                // Handle Aegis JSON Import
                if (content.TrimStart().StartsWith("{"))
                {
                    var exporter = new AegisExporter();
                    try
                    {
                        var aegisEntries = exporter.DecryptAegisJson(content, password);
                        int imported = 0;
                        int skipped = 0;

                        foreach (var entry in aegisEntries)
                        {
                            bool exists = await _dbContext.SecureItems.AnyAsync(s => s.Title == entry.Name && s.ItemType == ItemType.Totp);
                            if (exists)
                            {
                                skipped++;
                                continue;
                            }

                            var totpData = new TotpData
                            {
                                Secret = entry.Secret,
                                Issuer = entry.Issuer,
                                AccountName = entry.Name,
                                Period = entry.Period,
                                Digits = entry.Digits,
                                Algorithm = entry.Algorithm,
                                OtpType = "TOTP"
                            };

                            string jsonData = JsonSerializer.Serialize(totpData);
                            string encryptedData = _securityService.Encrypt(jsonData);

                            var newItem = new SecureItem
                            {
                                ItemType = ItemType.Totp,
                                Title = entry.Name,
                                ItemData = encryptedData,
                                Notes = entry.Note,
                                IsFavorite = false,
                                CreatedAt = DateTime.Now,
                                UpdatedAt = DateTime.Now
                            };

                            _dbContext.SecureItems.Add(newItem);
                            imported++;
                        }
                        
                        await _dbContext.SaveChangesAsync();
                        return $"Aegis Import Complete. Imported: {imported}, Skipped: {skipped}.";
                    }
                    catch (Exception ex)
                    {
                        if (ex.Message.Contains("Incorrect password")) return "Wrong_Password"; // Signal to UI to ask/retry
                        if (ex.Message.Contains("Password required")) return "Password_Required";
                        throw;
                    }
                }

                // Handle CSV Import
                var lines = ParseCsv(content);
                if (lines.Count == 0) return "File is empty.";

                var header = lines[0];
                if (header.Count < 9) return "Invalid CSV format (headers mismatch). CSV Import expects data exported from Monica.";
                
                // ... (CSV Import logic) ...
                int importedCount = 0;
                int skippedCount = 0;

                for (int i = 1; i < lines.Count; i++)
                {
                    var fields = lines[i];
                    if (fields.Count < 9) continue;

                    try
                    {
                        string typeStr = fields[1];
                        string title = fields[2];
                        string data = fields[3]; 
                        string notes = fields[4];
                        bool isFavorite = bool.TryParse(fields[5], out var fav) && fav;
                        long createdAtTs = long.TryParse(fields[7], out var c) ? c : DateTimeOffset.Now.ToUnixTimeMilliseconds();
                        long updatedAtTs = long.TryParse(fields[8], out var u) ? u : DateTimeOffset.Now.ToUnixTimeMilliseconds();

                        DateTime createdAt = DateTimeOffset.FromUnixTimeMilliseconds(createdAtTs).LocalDateTime;
                        DateTime updatedAt = DateTimeOffset.FromUnixTimeMilliseconds(updatedAtTs).LocalDateTime;

                        if (typeStr == "PASSWORD")
                        {
                            var parts = ParsePasswordData(data);
                            string username = parts.ContainsKey("username") ? parts["username"] : "";
                            string passwordStr = parts.ContainsKey("password") ? parts["password"] : "";
                            string website = parts.ContainsKey("website") ? parts["website"] : "";

                            bool exists = await _dbContext.PasswordEntries.AnyAsync(p => p.Title == title && p.Username == username);
                            if (!exists)
                            {
                                var newEntry = new PasswordEntry
                                {
                                    Title = title,
                                    Username = username,
                                    Website = website,
                                    EncryptedPassword = _securityService.Encrypt(passwordStr),
                                    Notes = notes,
                                    IsFavorite = isFavorite,
                                    CreatedAt = createdAt,
                                    UpdatedAt = updatedAt
                                };
                                _dbContext.PasswordEntries.Add(newEntry);
                                importedCount++;
                            }
                            else skippedCount++;
                        }
                        else
                        {
                            ItemType itemType = typeStr switch
                            {
                                "TOTP" => ItemType.Totp,
                                "BANK_CARD" => ItemType.BankCard,
                                "DOCUMENT" => ItemType.Document,
                                "NOTE" => ItemType.Note,
                                _ => ItemType.Note
                            };

                            bool exists = await _dbContext.SecureItems.AnyAsync(s => s.Title == title && s.ItemType == itemType);
                            if (!exists)
                            {
                                var newItem = new SecureItem
                                {
                                    ItemType = itemType,
                                    Title = title,
                                    ItemData = _securityService.Encrypt(data),
                                    Notes = notes,
                                    IsFavorite = isFavorite,
                                    CreatedAt = createdAt,
                                    UpdatedAt = updatedAt
                                };
                                _dbContext.SecureItems.Add(newItem);
                                importedCount++;
                            }
                            else skippedCount++;
                        }
                    }
                    catch { }
                }

                await _dbContext.SaveChangesAsync();
                return $"Import complete. Imported: {importedCount}, Skipped: {skippedCount}.";
            }
            catch (Exception ex)
            {
                throw new Exception($"Import failed: {ex.Message}", ex);
            }
        }

        private string EscapeCsvField(string field)
        {
            if (string.IsNullOrEmpty(field)) return "";
            if (field.Contains(CsvSeparator) || field.Contains(CsvQuote) || field.Contains("\n") || field.Contains("\r"))
            {
                return CsvQuote + field.Replace(CsvQuote, CsvQuote + CsvQuote) + CsvQuote;
            }
            return field;
        }

        private List<List<string>> ParseCsv(string content)
        {
            var result = new List<List<string>>();
            var currentLine = new List<string>();
            var currentField = new StringBuilder();
            bool inQuotes = false;

            for (int i = 0; i < content.Length; i++)
            {
                char c = content[i];

                if (inQuotes)
                {
                    if (c == '"')
                    {
                        if (i + 1 < content.Length && content[i + 1] == '"')
                        {
                            currentField.Append('"');
                            i++; 
                        }
                        else
                        {
                            inQuotes = false;
                        }
                    }
                    else
                    {
                        currentField.Append(c);
                    }
                }
                else
                {
                    if (c == '"') inQuotes = true;
                    else if (c == ',')
                    {
                        currentLine.Add(currentField.ToString());
                        currentField.Clear();
                    }
                    else if (c == '\r')
                    {
                        if (i + 1 < content.Length && content[i + 1] == '\n') i++; 
                        currentLine.Add(currentField.ToString());
                        result.Add(new List<string>(currentLine));
                        currentLine.Clear();
                        currentField.Clear();
                    }
                    else if (c == '\n')
                    {
                        currentLine.Add(currentField.ToString());
                        result.Add(new List<string>(currentLine));
                        currentLine.Clear();
                        currentField.Clear();
                    }
                    else currentField.Append(c);
                }
            }

            if (currentField.Length > 0 || currentLine.Count > 0)
            {
                currentLine.Add(currentField.ToString());
                result.Add(currentLine);
            }
            return result;
        }

        private Dictionary<string, string> ParsePasswordData(string data)
        {
            var result = new Dictionary<string, string>();
            if (string.IsNullOrEmpty(data)) return result;

            var parts = data.Split(';');
            foreach (var part in parts)
            {
                var kv = part.Split(new[] { ':' }, 2);
                if (kv.Length == 2) result[kv[0].Trim()] = kv[1].Trim();
            }
            return result;
        }
    }
}
