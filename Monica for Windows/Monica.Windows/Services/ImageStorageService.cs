using System;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using Microsoft.UI.Xaml.Media.Imaging;
using Windows.Storage;
using Windows.Storage.Streams;

namespace Monica.Windows.Services
{
    public interface IImageStorageService
    {
        Task<string> SaveImageAsync(global::Windows.Storage.StorageFile file);
        Task<(string FileName, bool IsNew)> SaveImageFromPathAsync(string sourcePath);
        Task<BitmapImage?> LoadImageAsync(string fileName);
        Task DeleteImageAsync(string fileName);
        
        /// <summary>
        /// Initialize the service (cache folder path) from UI thread before background operations
        /// </summary>
        Task EnsureInitializedAsync();
    }

    public class ImageStorageService : IImageStorageService
    {
        private readonly ISecurityService _securityService;
        private const string AttachmentsFolderName = "Attachments";
        
        // Cached path for background thread access (WinRT APIs fail on background threads)
        private string? _attachmentsFolderPath;
        private readonly object _pathLock = new object();

        public ImageStorageService(ISecurityService securityService)
        {
            _securityService = securityService;
        }
        
        // Lazy initialization - gets path on first use (from UI thread)
        private string GetCachedAttachmentsPath()
        {
            if (_attachmentsFolderPath == null)
            {
                lock (_pathLock)
                {
                    if (_attachmentsFolderPath == null)
                    {
                        try
                        {
                            var localFolder = ApplicationData.Current.LocalFolder;
                            _attachmentsFolderPath = Path.Combine(localFolder.Path, AttachmentsFolderName);
                            System.Diagnostics.Debug.WriteLine($"ImageStorageService: Cached path = {_attachmentsFolderPath}");
                        }
                        catch (Exception ex)
                        {
                            System.Diagnostics.Debug.WriteLine($"ImageStorageService: Failed to get LocalFolder: {ex.Message}");
                            throw new InvalidOperationException("Cannot access attachments folder path from background thread. This operation must be initiated from UI thread first.");
                        }
                    }
                }
            }
            return _attachmentsFolderPath;
        }

        private async Task<StorageFolder> GetAttachmentsFolderAsync()
        {
            var localFolder = ApplicationData.Current.LocalFolder;
            var folder = await localFolder.CreateFolderAsync(AttachmentsFolderName, CreationCollisionOption.OpenIfExists);
            
            // Cache the path when we get it successfully
            if (_attachmentsFolderPath == null)
            {
                _attachmentsFolderPath = folder.Path;
                System.Diagnostics.Debug.WriteLine($"ImageStorageService: Cached path from async = {_attachmentsFolderPath}");
            }
            
            return folder;
        }

        /// <summary>
        /// Initialize the service by caching the attachments folder path
        /// MUST be called from UI thread before any background operations
        /// </summary>
        public async Task EnsureInitializedAsync()
        {
            if (_attachmentsFolderPath == null)
            {
                var folder = await GetAttachmentsFolderAsync();
                System.Diagnostics.Debug.WriteLine($"ImageStorageService: Initialized path = {folder.Path}");
            }
        }

        public async Task<string> SaveImageAsync(global::Windows.Storage.StorageFile file)
        {
            try
            {
                // 1. Read file bytes
                byte[] fileBytes;
                using (var stream = await file.OpenReadAsync())
                {
                    fileBytes = new byte[stream.Size];
                    using (var reader = new DataReader(stream))
                    {
                        await reader.LoadAsync((uint)stream.Size);
                        reader.ReadBytes(fileBytes);
                    }
                }

                // 2. Convert to Base64
                string base64 = Convert.ToBase64String(fileBytes);

                // 3. Encrypt
                string encrypted = _securityService.Encrypt(base64);

                // 4. Generate unique filename
                string fileName = $"{Guid.NewGuid()}.enc";

                // 5. Save to file
                var folder = await GetAttachmentsFolderAsync();
                var storageFile = await folder.CreateFileAsync(fileName, CreationCollisionOption.ReplaceExisting);
                await FileIO.WriteTextAsync(storageFile, encrypted);

                return fileName;
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Error saving image: {ex.Message}");
                throw;
            }
        }

        public async Task<BitmapImage?> LoadImageAsync(string fileName)
        {
            try
            {
                if (string.IsNullOrEmpty(fileName)) return null;

                byte[] rawBytes;
                
                // First, try the MonicaAttachments path (for WebDAV restored images)
                string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
                string monicaPath = Path.Combine(localAppData, "MonicaAttachments", fileName);
                
                if (File.Exists(monicaPath))
                {
                    System.Diagnostics.Debug.WriteLine($"Loading image from MonicaAttachments: {fileName}");
                    rawBytes = await File.ReadAllBytesAsync(monicaPath);
                }
                else
                {
                    // Fall back to UWP Attachments folder (for locally saved images)
                    System.Diagnostics.Debug.WriteLine($"Loading image from UWP Attachments: {fileName}");
                    var folder = await GetAttachmentsFolderAsync();
                    var file = await folder.GetFileAsync(fileName);
                    
                    var buffer = await FileIO.ReadBufferAsync(file);
                    rawBytes = new byte[buffer.Length];
                    using (var reader = global::Windows.Storage.Streams.DataReader.FromBuffer(buffer))
                    {
                        reader.ReadBytes(rawBytes);
                    }
                }

                byte[] imageBytes;
                
                // Check if it's Android format (binary AES-CBC encrypted) or Windows format (text base64)
                // Windows format starts with printable ASCII/text, Android format is raw binary
                bool isTextFormat = rawBytes.Length > 0 && rawBytes.All(b => b >= 32 && b < 127 || b == '\r' || b == '\n');
                
                if (isTextFormat)
                {
                    // Windows format: text file with encrypted base64
                    string encrypted = System.Text.Encoding.UTF8.GetString(rawBytes);
                    string base64 = _securityService.Decrypt(encrypted);
                    if (string.IsNullOrEmpty(base64)) return null;
                    imageBytes = Convert.FromBase64String(base64);
                    System.Diagnostics.Debug.WriteLine($"Decrypted image {fileName} using Windows format");
                }
                else
                {
                    // Android format: binary AES-CBC encrypted
                    imageBytes = DecryptAndroidImage(rawBytes);
                    System.Diagnostics.Debug.WriteLine($"Decrypted image {fileName} using Android format");
                }

                // Create BitmapImage
                using (var stream = new InMemoryRandomAccessStream())
                {
                    using (var writer = new DataWriter(stream))
                    {
                        writer.WriteBytes(imageBytes);
                        await writer.StoreAsync();
                        await writer.FlushAsync();
                        stream.Seek(0);

                        var image = new BitmapImage();
                        await image.SetSourceAsync(stream);
                        return image;
                    }
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Error loading image {fileName}: {ex.Message}");
                return null;
            }
        }

        public async Task DeleteImageAsync(string fileName)
        {
            try
            {
                if (string.IsNullOrEmpty(fileName)) return;

                var folder = await GetAttachmentsFolderAsync();
                var file = await folder.GetFileAsync(fileName);
                await file.DeleteAsync();
            }
            catch (FileNotFoundException)
            {
                // Already deleted
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Error deleting image {fileName}: {ex.Message}");
            }
        }

        /// <summary>
        /// Import an image from a raw file path (used for WebDAV restore)
        /// Handles Android-encrypted images (AES-CBC with fixed key)
        /// IMPORTANT: This preserves the original filename so CSV imagePaths references work
        /// Uses standard .NET I/O ONLY to avoid Windows.Storage threading issues
        /// Returns (FileName, IsNew) - IsNew is false if image already existed and import was skipped
        /// </summary>
        public async Task<(string FileName, bool IsNew)> SaveImageFromPathAsync(string sourcePath)
        {
            try
            {
                // Get original filename - MUST preserve this as CSV references it
                string originalFileName = Path.GetFileName(sourcePath);
                
                // Determine destination path first to check existence
                string fileName = originalFileName;
                if (!fileName.EndsWith(".enc", StringComparison.OrdinalIgnoreCase))
                {
                    fileName = Path.GetFileNameWithoutExtension(fileName) + ".enc";
                }

                string localAppData = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
                string attachmentsPath = Path.Combine(localAppData, "MonicaAttachments");
                string destPath = Path.Combine(attachmentsPath, fileName);
                
                // Check if file already exists AND is readable with current key
                if (File.Exists(destPath))
                {
                    // Try to verify the existing file can be decrypted
                    bool canDecrypt = await VerifyExistingImageAsync(destPath);
                    if (canDecrypt)
                    {
                        System.Diagnostics.Debug.WriteLine($"Image {fileName} already exists and is valid. Skipping import.");
                        return (fileName, false);
                    }
                    else
                    {
                        // Existing file is corrupt or encrypted with wrong key - reimport it
                        System.Diagnostics.Debug.WriteLine($"Image {fileName} exists but cannot be decrypted. Reimporting...");
                        File.Delete(destPath);
                    }
                }

                System.Diagnostics.Debug.WriteLine($"SaveImageFromPathAsync: importing {originalFileName}");
                
                // 1. Read encrypted file bytes from Android backup
                byte[] encryptedBytes = await File.ReadAllBytesAsync(sourcePath);
                System.Diagnostics.Debug.WriteLine($"Read {encryptedBytes.Length} bytes from source");
                
                // 2. Decrypt using Android's encryption scheme (AES-CBC with fixed key/IV)
                byte[] imageBytes = DecryptAndroidImage(encryptedBytes);
                System.Diagnostics.Debug.WriteLine($"Decrypted to {imageBytes.Length} bytes");
                
                // 3. Convert to Base64
                string base64 = Convert.ToBase64String(imageBytes);

                // 4. Re-encrypt using Windows' encryption scheme
                string encrypted = _securityService.Encrypt(base64);
                System.Diagnostics.Debug.WriteLine($"Re-encrypted, length: {encrypted.Length}");

                // 5. Ensure directory exists (using path defined at start)
                if (!Directory.Exists(attachmentsPath))
                {
                    Directory.CreateDirectory(attachmentsPath);
                    System.Diagnostics.Debug.WriteLine($"Created MonicaAttachments folder at: {attachmentsPath}");
                }
                
                // 6. Save to file (using path defined at start)
                await File.WriteAllTextAsync(destPath, encrypted);
                
                System.Diagnostics.Debug.WriteLine($"Saved image to: {destPath}");

                return (fileName, true);
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Error importing image from path: {ex.Message}");
                throw;
            }
        }

        /// <summary>
        /// Decrypt Android-encrypted image data
        /// Android uses AES/CBC/PKCS5Padding with fixed key and IV
        /// </summary>
        private byte[] DecryptAndroidImage(byte[] encryptedData)
        {
            try
            {
                // Android encryption key and IV (from ImageManager.kt)
                byte[] key = System.Text.Encoding.UTF8.GetBytes("MonicaSecureKey1"); // 16 bytes
                byte[] iv = System.Text.Encoding.UTF8.GetBytes("MonicaSecureIV16");  // 16 bytes

                using var aes = System.Security.Cryptography.Aes.Create();
                aes.Key = key;
                aes.IV = iv;
                aes.Mode = System.Security.Cryptography.CipherMode.CBC;
                aes.Padding = System.Security.Cryptography.PaddingMode.PKCS7;

                using var decryptor = aes.CreateDecryptor();
                using var ms = new MemoryStream(encryptedData);
                using var cs = new System.Security.Cryptography.CryptoStream(ms, decryptor, System.Security.Cryptography.CryptoStreamMode.Read);
                using var resultMs = new MemoryStream();
                cs.CopyTo(resultMs);
                return resultMs.ToArray();
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"Android image decryption failed: {ex.Message}");
                // If decryption fails, assume it's a raw image and return as-is
                return encryptedData;
            }
        }

        /// <summary>
        /// Verify that an existing image file can be decrypted with the current key
        /// </summary>
        private async Task<bool> VerifyExistingImageAsync(string filePath)
        {
            try
            {
                byte[] rawBytes = await File.ReadAllBytesAsync(filePath);
                
                // Check if it's Windows format (text) - try to decrypt
                bool isTextFormat = rawBytes.Length > 0 && rawBytes.All(b => b >= 32 && b < 127 || b == '\r' || b == '\n');
                
                if (isTextFormat)
                {
                    // Windows format: text file with encrypted base64
                    string encrypted = System.Text.Encoding.UTF8.GetString(rawBytes);
                    string base64 = _securityService.Decrypt(encrypted);
                    if (string.IsNullOrEmpty(base64)) return false;
                    
                    // Try to decode base64 to verify it's valid
                    byte[] imageBytes = Convert.FromBase64String(base64);
                    return imageBytes.Length > 0;
                }
                else
                {
                    // Android format - try to decrypt using fixed key
                    byte[] imageBytes = DecryptAndroidImage(rawBytes);
                    return imageBytes.Length > 0;
                }
            }
            catch (Exception ex)
            {
                System.Diagnostics.Debug.WriteLine($"VerifyExistingImageAsync failed for {filePath}: {ex.Message}");
                return false;
            }
        }
    }
}
