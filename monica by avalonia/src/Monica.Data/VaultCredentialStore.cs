using Microsoft.Data.Sqlite;
using Monica.Core.Services;

namespace Monica.Data;

public interface IVaultCredentialStore
{
    Task<MasterPasswordHash?> GetAsync(CancellationToken cancellationToken = default);
    Task SaveAsync(MasterPasswordHash hash, CancellationToken cancellationToken = default);
}

public sealed class VaultCredentialStore(ISqliteConnectionFactory connectionFactory, IDatabaseMigrator migrator) : IVaultCredentialStore
{
    public async Task<MasterPasswordHash?> GetAsync(CancellationToken cancellationToken = default)
    {
        await migrator.MigrateAsync(cancellationToken);
        await using var connection = connectionFactory.CreateConnection();
        await connection.OpenAsync(cancellationToken);
        await using var command = connection.CreateCommand();
        command.CommandText =
            """
            SELECT password_hash, salt, kdf, iterations, memory_kib, parallelism
            FROM vault_credentials
            WHERE id = 1
            """;

        await using var reader = await command.ExecuteReaderAsync(cancellationToken);
        if (!await reader.ReadAsync(cancellationToken))
        {
            return null;
        }

        return new MasterPasswordHash(
            reader.GetString(0),
            Convert.FromBase64String(reader.GetString(1)),
            reader.GetString(2),
            reader.GetInt32(3),
            reader.GetInt32(4),
            reader.GetInt32(5));
    }

    public async Task SaveAsync(MasterPasswordHash hash, CancellationToken cancellationToken = default)
    {
        await migrator.MigrateAsync(cancellationToken);
        await using var connection = connectionFactory.CreateConnection();
        await connection.OpenAsync(cancellationToken);
        await using var command = connection.CreateCommand();
        command.CommandText =
            """
            INSERT INTO vault_credentials (
                id, password_hash, salt, kdf, iterations, memory_kib, parallelism, created_at, updated_at)
            VALUES (
                1, $password_hash, $salt, $kdf, $iterations, $memory_kib, $parallelism, $now, $now)
            ON CONFLICT(id) DO UPDATE SET
                password_hash = excluded.password_hash,
                salt = excluded.salt,
                kdf = excluded.kdf,
                iterations = excluded.iterations,
                memory_kib = excluded.memory_kib,
                parallelism = excluded.parallelism,
                updated_at = excluded.updated_at
            """;
        var now = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds();
        AddParameter(command, "$password_hash", hash.Hash);
        AddParameter(command, "$salt", Convert.ToBase64String(hash.Salt));
        AddParameter(command, "$kdf", hash.Kdf);
        AddParameter(command, "$iterations", hash.Iterations);
        AddParameter(command, "$memory_kib", hash.MemoryKiB);
        AddParameter(command, "$parallelism", hash.Parallelism);
        AddParameter(command, "$now", now);
        await command.ExecuteNonQueryAsync(cancellationToken);
    }

    private static void AddParameter(SqliteCommand command, string name, object value)
    {
        var parameter = command.CreateParameter();
        parameter.ParameterName = name;
        parameter.Value = value;
        command.Parameters.Add(parameter);
    }
}
