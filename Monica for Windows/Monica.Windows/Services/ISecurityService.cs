namespace Monica.Windows.Services
{
    public interface ISecurityService
    {
        bool IsMasterPasswordSet();
        void SetMasterPassword(string password);
        bool Unlock(string password);
        void Lock();
        bool IsUnlocked { get; }
        
        bool IsSecurityQuestionSet();
        void SetSecurityQuestion(string question, string answer);
        bool ValidateSecurityQuestion(string answer);
        string? GetSecurityQuestion();
        void ClearAllData();

        string Encrypt(string plainText);
        string Decrypt(string cipherText);
    }
}
