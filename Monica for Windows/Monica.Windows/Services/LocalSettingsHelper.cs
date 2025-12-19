using System;
using System.Collections.Generic;
using System.IO;
using System.Text.Json;

namespace Monica.Windows.Services
{
    public static class LocalSettingsHelper
    {
        private static string SettingsPath => Path.Combine(
            Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData),
            "Monica",
            "settings.json");

        private static Dictionary<string, object> _settingsCache;

        private static void EnsureLoaded()
        {
            if (_settingsCache != null) return;

            try
            {
                if (File.Exists(SettingsPath))
                {
                    string json = File.ReadAllText(SettingsPath);
                    _settingsCache = JsonSerializer.Deserialize<Dictionary<string, object>>(json) ?? new Dictionary<string, object>();
                }
                else
                {
                    _settingsCache = new Dictionary<string, object>();
                }
            }
            catch
            {
                _settingsCache = new Dictionary<string, object>();
            }
        }

        private static void Save()
        {
            try
            {
                string dir = Path.GetDirectoryName(SettingsPath);
                if (!Directory.Exists(dir)) Directory.CreateDirectory(dir);

                string json = JsonSerializer.Serialize(_settingsCache);
                File.WriteAllText(SettingsPath, json);
            }
            catch { }
        }

        public static T Get<T>(string key, T defaultValue = default)
        {
            EnsureLoaded();
            if (_settingsCache.TryGetValue(key, out object val))
            {
                if (val is JsonElement element)
                {
                    // Handle JSON deserialization for basic types
                    if (typeof(T) == typeof(string)) return (T)(object)element.ToString();
                    if (typeof(T) == typeof(bool)) return (T)(object)element.GetBoolean();
                    if (typeof(T) == typeof(int)) return (T)(object)element.GetInt32();
                }
                if (val is T typedVal) return typedVal;
            }
            return defaultValue;
        }

        public static void Set<T>(string key, T value)
        {
            EnsureLoaded();
            _settingsCache[key] = value;
            Save();
        }
    }
}
