using System;
using System.Collections.Generic;
using System.Linq;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace Monica.Windows.Services
{
    public class AegisExporter
    {
        private const int AEGIS_VERSION = 1;
        private const int SCRYPT_N = 32768; // CPU/RAM cost
        private const int SCRYPT_R = 8;     // Block size
        private const int SCRYPT_P = 1;     // Parallelization
        private const int SCRYPT_KEY_LENGTH = 32; // 256 bits
        private const int GCM_IV_LENGTH = 12; // 96 bits
        private const int GCM_TAG_LENGTH = 16; // 128 bits
        private const int SLOT_KEY_LENGTH = 32; // 256 bits

        public class AegisEntry
        {
            public string Uuid { get; set; } = Guid.NewGuid().ToString();
            public string Name { get; set; } = "";
            public string Issuer { get; set; } = "";
            public string Note { get; set; } = "";
            public string Secret { get; set; } = "";
            public string Algorithm { get; set; } = "SHA1";
            public int Digits { get; set; } = 6;
            public int Period { get; set; } = 30;
        }

        public string ExportToUnencryptedAegisJson(List<AegisEntry> entries)
        {
            var root = new JsonObject
            {
                ["version"] = AEGIS_VERSION,
                ["header"] = new JsonObject
                {
                    ["slots"] = new JsonArray(),
                    ["params"] = new JsonObject
                    {
                        ["nonce"] = "",
                        ["tag"] = ""
                    }
                },
                ["db"] = ConvertEntriesToJson(entries)
            };

            return root.ToJsonString(new JsonSerializerOptions { WriteIndented = true });
        }

        public string ExportToEncryptedAegisJson(List<AegisEntry> entries, string password)
        {
            // 1. Generate master key (32 bytes)
            byte[] masterKey = new byte[32];
            RandomNumberGenerator.Fill(masterKey);

            // 2. Generate salt (32 bytes)
            byte[] salt = new byte[32];
            RandomNumberGenerator.Fill(salt);

            // 3. Derive key using Scrypt
            byte[] derivedKey = ScryptEncoder.DeriveKey(password, salt, SCRYPT_N, SCRYPT_R, SCRYPT_P, SCRYPT_KEY_LENGTH);

            // 4. Generate slot nonce (IV)
            byte[] slotNonce = new byte[GCM_IV_LENGTH];
            RandomNumberGenerator.Fill(slotNonce);

            // 5. Encrypt master key with derived key
            byte[] slotCipherText = new byte[masterKey.Length];
            byte[] slotTag = new byte[GCM_TAG_LENGTH];
            using (var aes = new AesGcm(derivedKey, GCM_TAG_LENGTH))
            {
                aes.Encrypt(slotNonce, masterKey, slotCipherText, slotTag);
            }

            // 6. Encrypt DB content with master key
            var dbJson = ConvertEntriesToJson(entries).ToJsonString();
            byte[] dbBytes = Encoding.UTF8.GetBytes(dbJson);
            
            byte[] dbNonce = new byte[GCM_IV_LENGTH];
            RandomNumberGenerator.Fill(dbNonce);
            
            byte[] dbCipherText = new byte[dbBytes.Length];
            byte[] dbTag = new byte[GCM_TAG_LENGTH];
            using (var aes = new AesGcm(masterKey, GCM_TAG_LENGTH))
            {
                aes.Encrypt(dbNonce, dbBytes, dbCipherText, dbTag);
            }
            
            // Combine DB ciphertext + tag for Aegis convention? 
            // Aegis Json has 'db' field as base64.
            // Typically AesGcm implementation appends tag or separates it.
            // Android code: dbCipherText + dbTag.
            
            byte[] encryptedDb = new byte[dbCipherText.Length + dbTag.Length];
            Buffer.BlockCopy(dbCipherText, 0, encryptedDb, 0, dbCipherText.Length);
            Buffer.BlockCopy(dbTag, 0, encryptedDb, dbCipherText.Length, dbTag.Length);

            // 7. Construct JSON
            var root = new JsonObject
            {
                ["version"] = AEGIS_VERSION,
                ["header"] = new JsonObject
                {
                    ["slots"] = new JsonArray
                    {
                        new JsonObject
                        {
                            ["type"] = 1, // 1 = Password slot
                            ["uuid"] = Guid.NewGuid().ToString(),
                            ["key"] = Convert.ToHexString(slotCipherText).ToLower() + Convert.ToHexString(slotTag).ToLower(), // Slot key = cipher + tag
                            ["key_params"] = new JsonObject
                            {
                                ["nonce"] = Convert.ToHexString(slotNonce).ToLower(),
                                ["tag"] = Convert.ToHexString(slotTag).ToLower() // Wait, Aegis repeats tag here? Or separate? 
                            },
                            ["n"] = SCRYPT_N,
                            ["r"] = SCRYPT_R,
                            ["p"] = SCRYPT_P,
                            ["salt"] = Convert.ToHexString(salt).ToLower(),
                            ["repaired"] = false
                        }
                    },
                    ["params"] = new JsonObject
                    {
                        ["nonce"] = Convert.ToHexString(dbNonce).ToLower(),
                        ["tag"] = Convert.ToHexString(dbTag).ToLower()
                    }
                },
                ["db"] = Convert.ToBase64String(encryptedDb)
            };

            return root.ToJsonString(new JsonSerializerOptions { WriteIndented = true });
        }

        private JsonObject ConvertEntriesToJson(List<AegisEntry> entries)
        {
            var dbObj = new JsonObject
            {
                ["version"] = 3,
                ["entries"] = new JsonArray()
            };

            var entriesArray = dbObj["entries"]!.AsArray();
            foreach (var entry in entries)
            {
                var entryObj = new JsonObject
                {
                    ["type"] = "totp",
                    ["uuid"] = entry.Uuid,
                    ["name"] = entry.Name,
                    ["issuer"] = entry.Issuer
                };
                if (!string.IsNullOrEmpty(entry.Note)) entryObj["note"] = entry.Note;
                
                entryObj["info"] = new JsonObject
                {
                    ["secret"] = entry.Secret,
                    ["algo"] = entry.Algorithm,
                    ["digits"] = entry.Digits,
                    ["period"] = entry.Period
                };
                entriesArray.Add(entryObj);
            }
            return dbObj;
        }

        public List<AegisEntry> DecryptAegisJson(string json, string? password)
        {
            var root = JsonNode.Parse(json);
            if (root == null) throw new Exception("Invalid JSON");

            // Check if encrypted
            var dbNode = root["db"];
            if (dbNode == null) throw new Exception("Invalid Aegis file (no db)");

            string dbContent;
            
            if (dbNode.GetValueKind() == JsonValueKind.String)
            {
                // Encrypted (Base64 string)
                if (string.IsNullOrEmpty(password)) throw new Exception("Password required for encrypted Aegis file");

                // 1. Parse Header
                var header = root["header"];
                if (header == null) throw new Exception("No header found");
                
                var slots = header["slots"]!.AsArray();
                var slot = slots.FirstOrDefault(s => (int?)s["type"] == 1); // Type 1 = Password
                if (slot == null) throw new Exception("No password slot found");

                // 2. Parse Slot Params
                string saltHex = slot["salt"]!.GetValue<string>();
                int n = slot["n"]!.GetValue<int>();
                int r = slot["r"]!.GetValue<int>();
                int p = slot["p"]!.GetValue<int>();
                string keyHex = slot["key"]!.GetValue<string>(); // Cipher + Tag
                string nonceHex = slot["key_params"]!["nonce"]!.GetValue<string>();
                
                byte[] salt = Convert.FromHexString(saltHex);
                byte[] iv = Convert.FromHexString(nonceHex);
                byte[] keyCipherWithTag = Convert.FromHexString(keyHex);

                // 3. Derive Key
                byte[] derivedKey = ScryptEncoder.DeriveKey(password, salt, n, r, p, 32);

                // 4. Decrypt Master Key
                byte[] masterKey = new byte[32];
                byte[] keyTag = new byte[GCM_TAG_LENGTH];
                byte[] keyCipher = new byte[keyCipherWithTag.Length - GCM_TAG_LENGTH];
                
                Buffer.BlockCopy(keyCipherWithTag, 0, keyCipher, 0, keyCipher.Length);
                Buffer.BlockCopy(keyCipherWithTag, keyCipher.Length, keyTag, 0, GCM_TAG_LENGTH);

                using (var aes = new AesGcm(derivedKey, GCM_TAG_LENGTH))
                {
                    try
                    {
                        aes.Decrypt(iv, keyCipher, keyTag, masterKey);
                    }
                    catch (CryptographicException)
                    {
                        throw new Exception("Incorrect password");
                    }
                }

                // 5. Decrypt DB
                string dbNonceHex = header["params"]!["nonce"]!.GetValue<string>();
                string dbTagHex = header["params"]!["tag"]!.GetValue<string>();
                string dbBase64 = dbNode.GetValue<string>();
                
                byte[] dbNonce = Convert.FromHexString(dbNonceHex);
                byte[] dbTag = Convert.FromHexString(dbTagHex); // Aegis might store tag in `params` OR append to DB ciphertext. 
                // Let's check Android impl. 
                // Android export: put("db", Base64(encDb)). encDb = cipher + tag.
                // Android import: check header params["tag"]. If present, use it? Or use appended?
                // Standard Aegis: GCM tag is usually in params. 
                // Wait, in my Export I appended tag to `encryptedDb`. 
                // BUT I ALSO put tag in `params`. This is redundant but safe.
                
                // Let's assume standard behavior: Tag is in params. 
                // If tag in params is empty, maybe it's appended?
                // Actually my export put tag in params. So let's use that.
                
                byte[] fullDbCipher = Convert.FromBase64String(dbBase64);
                // If I appended tag in export, I need to separate it if I use AesGcm.Decrypt with separate tag arg.
                // But wait, if tag is in params, I should use that tag.
                // BUT, if I appended it, the `fullDbCipher` includes it.
                // Re-reading my export logic:
                // byte[] encryptedDb = new byte[dbCipherText.Length + dbTag.Length]; (Appended)
                // params["tag"] = dbTag; (Also in params)
                
                // So if importing my own export: `fullDbCipher` has tag at end. 
                // BUT correct import logic should handle standard Aegis files.
                // Aegis spec says tag is in `params`. If so, main DB blob is just ciphertext?
                // Let's assume `fullDbCipher` MIGHT have tag at end.
                // If `fullDbCipher` length == expected length + 16, it has tag?
                // Let's rely on standard practice: Tag passed separately to Decrypt.
                // So ciphertext is just the payload.
                
                // Check if `fullDbCipher` has tag appended. 
                // If I use the tag from params, I should truncate `fullDbCipher` by 16 bytes IF it was appended.
                // How to know? 
                // If I only rely on params, and `fullDbCipher` is too long, Decrypt might fail or trailing garbage.
                // Actually AesGcm expects exact ciphertext size matching plaintext size.
                
                // Let's try to decrypt. If I assume it's just ciphertext:
                byte[] dbCipherText = fullDbCipher;
                if (fullDbCipher.Length > 0)
                {
                     // If the file was exported by me, it has tag appended. 
                     // If exported by official Aegis, it might NOT have tag appended (since it's in params).
                     // Let's assume strict adherence to params tag. 
                     // If I appended it, I must remove it.
                     // Safe bet: The file content length.
                     
                     // Helper: Just try to decrypt using `fullDbCipher` first (assuming it is PURE ciphertext).
                     // If that fails (or length mismatch logic), try truncating.
                     
                     // Actually, if `fullDbCipher` has appended tag, standard GCM decrypt (taking tag arg) will treat appended tag as extra ciphertext bytes -> fail Auth.
                     // So I MUST strip it if it's there.
                     
                     // How to detect? 
                     // Official Aegis source: `encrypted = cipher.doFinal(plain)`. `encrypted` includes tag in Java/Android default GCM.
                     // AND they put it in params.
                     // So `db` field contains Cipher+Tag.
                     // And `params.tag` contains Tag.
                     // Use `params.tag` is safest source of truth for Tag.
                     // Ciphertext = `db` bytes minus last 16 bytes.
                     
                     if (fullDbCipher.Length > GCM_TAG_LENGTH)
                     {
                         dbCipherText = new byte[fullDbCipher.Length - GCM_TAG_LENGTH];
                         Buffer.BlockCopy(fullDbCipher, 0, dbCipherText, 0, dbCipherText.Length);
                     }
                }

                byte[] dbPlain = new byte[dbCipherText.Length];
                using (var aes = new AesGcm(masterKey, GCM_TAG_LENGTH))
                {
                    aes.Decrypt(dbNonce, dbCipherText, dbTag, dbPlain);
                }
                
                dbContent = Encoding.UTF8.GetString(dbPlain);
            }
            else
            {
                // Unencrypted (JsonObject)
                dbContent = dbNode.ToJsonString();
            }

            // Parse DB entries
            var dbRoot = JsonNode.Parse(dbContent);
            var entriesJson = dbRoot["entries"]!.AsArray();
            var list = new List<AegisEntry>();

            foreach (var node in entriesJson)
            {
                var entry = new AegisEntry
                {
                    Uuid = node["uuid"]?.GetValue<string>() ?? Guid.NewGuid().ToString(),
                    Name = node["name"]!.GetValue<string>(),
                    Issuer = node["issuer"]?.GetValue<string>() ?? "",
                    Note = node["note"]?.GetValue<string>() ?? ""
                };
                
                var info = node["info"];
                if (info != null)
                {
                    entry.Secret = info["secret"]!.GetValue<string>();
                    entry.Algorithm = info["algo"]?.GetValue<string>() ?? "SHA1";
                    entry.Digits = info["digits"]?.GetValue<int>() ?? 6;
                    entry.Period = info["period"]?.GetValue<int>() ?? 30;
                }
                list.Add(entry);
            }

            return list;
        }
    }
}
