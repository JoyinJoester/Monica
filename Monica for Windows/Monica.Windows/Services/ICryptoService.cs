namespace Monica.Windows.Services
{
    public interface ICryptoService
    {
        void SetMasterPassword(string password);
        bool AccessRepository(); // Verify password or set up key
        bool IsMasterPasswordSet();
        
        string Encrypt(string plainText);
        string Decrypt(string cipherText);
        
        (string Hash, byte[] Salt) HashMasterPassword(string password, byte[]? salt = null);
        bool VerifyMasterPassword(string inputPassword, string storedHash, byte[] storedSalt);
    }
}
