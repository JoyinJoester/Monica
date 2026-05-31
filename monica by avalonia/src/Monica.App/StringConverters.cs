using System.Globalization;
using Avalonia.Data.Converters;

namespace Monica.App;

public static class StringConverters
{
    public static IValueConverter IsPasswords { get; } = new SectionConverter("Passwords");
    public static IValueConverter IsTotp { get; } = new SectionConverter("Totp");
    public static IValueConverter IsCards { get; } = new SectionConverter("Cards");
    public static IValueConverter IsNotes { get; } = new SectionConverter("Notes");
    public static IValueConverter IsGenerator { get; } = new SectionConverter("Generator");
    public static IValueConverter IsSettingsOrSync { get; } = new SectionSetConverter(new HashSet<string>(StringComparer.OrdinalIgnoreCase) { "Settings", "Sync" });

    private sealed class SectionConverter(string section) : IValueConverter
    {
        public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture) =>
            string.Equals(value?.ToString(), section, StringComparison.OrdinalIgnoreCase);

        public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) =>
            throw new NotSupportedException();
    }

    private sealed class SectionSetConverter(IReadOnlySet<string> sections) : IValueConverter
    {
        public object Convert(object? value, Type targetType, object? parameter, CultureInfo culture) =>
            value is not null && sections.Contains(value.ToString() ?? "");

        public object ConvertBack(object? value, Type targetType, object? parameter, CultureInfo culture) =>
            throw new NotSupportedException();
    }
}
