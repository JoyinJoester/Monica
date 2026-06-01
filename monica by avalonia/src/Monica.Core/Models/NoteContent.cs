using System.Text.Json;
using System.Text.Json.Serialization;
using System.Text.RegularExpressions;

namespace Monica.Core.Models;

public sealed record DecodedNoteContent(string Content, IReadOnlyList<string> Tags, bool IsMarkdown);

public sealed record NoteSavePayload(string Title, string Content, string ItemData, string NotesCache, string ImagePaths, bool IsMarkdown, IReadOnlyList<string> Tags);

public static partial class NoteContentCodec
{
    private const string InlineImageScheme = "monica-image://";
    private static readonly JsonSerializerOptions JsonOptions = new(JsonSerializerDefaults.Web);

    public static DecodedNoteContent Decode(string itemData, string fallbackNotes)
    {
        var raw = itemData.Trim();
        if (string.IsNullOrEmpty(raw))
        {
            return new DecodedNoteContent(fallbackNotes, [], false);
        }

        if (raw.StartsWith('{'))
        {
            try
            {
                var data = JsonSerializer.Deserialize<NoteData>(raw, JsonOptions);
                if (data is not null)
                {
                    return new DecodedNoteContent(
                        string.IsNullOrWhiteSpace(data.Content) ? fallbackNotes : data.Content,
                        NormalizeTags(data.Tags),
                        data.IsMarkdown);
                }
            }
            catch (JsonException)
            {
            }
        }

        return new DecodedNoteContent(raw.Trim('"').Replace("\\n", "\n", StringComparison.Ordinal), [], false);
    }

    public static DecodedNoteContent DecodeFromItem(SecureItem item) => Decode(item.ItemData, item.Notes);

    public static NoteSavePayload BuildSavePayload(
        string title,
        string content,
        string tagsText,
        bool isMarkdown,
        IReadOnlyList<string>? imagePaths = null)
    {
        var normalizedContent = content.TrimEnd();
        var tags = NormalizeTags(tagsText.Split(',', '\n'));
        var inlineImages = ExtractInlineImageIds(normalizedContent);
        var mergedImages = NormalizeTags((imagePaths ?? []).Concat(inlineImages));
        var resolvedTitle = ResolveTitle(title, normalizedContent);
        var itemData = JsonSerializer.Serialize(new NoteData(normalizedContent, tags, isMarkdown), JsonOptions);

        return new NoteSavePayload(
            resolvedTitle,
            normalizedContent,
            itemData,
            normalizedContent,
            EncodeStringArray(mergedImages),
            isMarkdown,
            tags);
    }

    public static string ResolveTitle(string? title, string content)
    {
        if (!string.IsNullOrWhiteSpace(title))
        {
            return title.Trim();
        }

        var firstLine = content
            .Split('\n', StringSplitOptions.TrimEntries | StringSplitOptions.RemoveEmptyEntries)
            .FirstOrDefault();
        if (string.IsNullOrWhiteSpace(firstLine))
        {
            return "New Note";
        }

        var normalized = firstLine.TrimStart('#', '-', '*', '+', '>', ' ', '\t');
        return normalized.Length > 60 ? normalized[..60] + "..." : normalized;
    }

    public static IReadOnlyList<string> DecodeImagePaths(string imagePaths)
    {
        if (string.IsNullOrWhiteSpace(imagePaths))
        {
            return [];
        }

        try
        {
            return NormalizeTags(JsonSerializer.Deserialize<string[]>(imagePaths, JsonOptions) ?? []);
        }
        catch (JsonException)
        {
            return imagePaths.StartsWith('[') ? [] : NormalizeTags([imagePaths]);
        }
    }

    public static string EncodeStringArray(IEnumerable<string> values) => JsonSerializer.Serialize(NormalizeTags(values), JsonOptions);

    public static string BuildInlineImageMarkdown(string imageId)
    {
        var normalized = imageId.Trim();
        return string.IsNullOrEmpty(normalized) ? "" : $"![]({InlineImageScheme}{normalized})";
    }

    public static IReadOnlyList<string> ExtractInlineImageIds(string content)
    {
        if (string.IsNullOrWhiteSpace(content))
        {
            return [];
        }

        return NormalizeTags(InlineImageRegex()
            .Matches(content)
            .Select(match => match.Groups[1].Value));
    }

    public static string ToPlainPreview(string content, bool isMarkdown)
    {
        return isMarkdown ? MarkdownToPlainText(content) : content;
    }

    public static string MarkdownToPlainText(string content)
    {
        return FencedCodeRegex().Replace(content, " ")
            .Replace("`", "", StringComparison.Ordinal)
            .Replace("*", "", StringComparison.Ordinal)
            .Replace("_", "", StringComparison.Ordinal)
            .Replace("~", "", StringComparison.Ordinal)
            .Trim();
    }

    private static IReadOnlyList<string> NormalizeTags(IEnumerable<string>? values)
    {
        return (values ?? [])
            .Select(value => value.Trim())
            .Where(value => value.Length > 0)
            .Distinct(StringComparer.OrdinalIgnoreCase)
            .ToArray();
    }

    [GeneratedRegex("!\\[[^\\]]*\\]\\(monica-image://([^\\)\\s]+)\\)")]
    private static partial Regex InlineImageRegex();

    [GeneratedRegex("```[\\s\\S]*?```")]
    private static partial Regex FencedCodeRegex();

    private sealed record NoteData(
        [property: JsonPropertyName("content")] string Content,
        [property: JsonPropertyName("tags")] IReadOnlyList<string> Tags,
        [property: JsonPropertyName("isMarkdown")] bool IsMarkdown);
}
