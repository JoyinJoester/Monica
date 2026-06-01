using System.Text.Json;
using System.Text.Json.Serialization;
using Monica.Core.Models;

namespace Monica.Core.ImportExport;

public interface IImportExportService
{
    string ExportJson(IEnumerable<PasswordEntry> passwords, IEnumerable<SecureItem> secureItems);
    MonicaExportPackage ImportJson(string json);
}

public sealed record MonicaExportPackage(int SchemaVersion, IReadOnlyList<PasswordEntry> Passwords, IReadOnlyList<SecureItem> SecureItems);

public sealed class ImportExportService : IImportExportService
{
    public string ExportJson(IEnumerable<PasswordEntry> passwords, IEnumerable<SecureItem> secureItems)
    {
        var package = new MonicaExportPackage(68, passwords.ToList(), secureItems.ToList());
        return JsonSerializer.Serialize(package, MonicaJsonContext.Default.MonicaExportPackage);
    }

    public MonicaExportPackage ImportJson(string json)
    {
        var package = JsonSerializer.Deserialize(json, MonicaJsonContext.Default.MonicaExportPackage);
        return package ?? new MonicaExportPackage(68, [], []);
    }
}

[JsonSourceGenerationOptions(JsonSerializerDefaults.Web, WriteIndented = true)]
[JsonSerializable(typeof(MonicaExportPackage))]
[JsonSerializable(typeof(PasswordEntry))]
[JsonSerializable(typeof(SecureItem))]
internal sealed partial class MonicaJsonContext : JsonSerializerContext;
