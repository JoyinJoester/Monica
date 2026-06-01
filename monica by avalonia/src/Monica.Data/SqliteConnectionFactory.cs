using Microsoft.Data.Sqlite;
using SQLitePCL;
using Dapper;

namespace Monica.Data;

public interface ISqliteConnectionFactory
{
    string DatabasePath { get; }
    SqliteConnection CreateConnection();
}

public sealed class SqliteConnectionFactory : ISqliteConnectionFactory
{
    private static int _initialized;

    public SqliteConnectionFactory(string? databasePath = null)
    {
        if (Interlocked.Exchange(ref _initialized, 1) == 0)
        {
            Batteries_V2.Init();
            DefaultTypeMap.MatchNamesWithUnderscores = true;
        }

        DatabasePath = databasePath ?? GetDefaultDatabasePath();
    }

    public string DatabasePath { get; }

    public SqliteConnection CreateConnection()
    {
        var builder = new SqliteConnectionStringBuilder
        {
            DataSource = DatabasePath,
            Mode = SqliteOpenMode.ReadWriteCreate,
            Cache = SqliteCacheMode.Shared,
            ForeignKeys = true
        };

        return new SqliteConnection(builder.ToString());
    }

    private static string GetDefaultDatabasePath()
    {
        var root = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "Monica");
        Directory.CreateDirectory(root);
        return Path.Combine(root, "monica.db");
    }
}
