using System.Net;
using Monica.Core.Models;
using WebDav;

namespace Monica.Platform.Services;

public sealed class WebDavBackupService(IHttpClientFactory httpClientFactory) : IWebDavBackupService
{
    public string NormalizeRemotePath(string rootPath, string relativePath)
    {
        var root = string.IsNullOrWhiteSpace(rootPath) ? "/" : rootPath.Trim();
        root = "/" + root.Trim('/');
        var relative = string.IsNullOrWhiteSpace(relativePath) ? "" : relativePath.Trim('/');
        var combined = string.IsNullOrEmpty(relative) ? root : $"{root}/{relative}";
        return combined.Replace("//", "/", StringComparison.Ordinal);
    }

    public async Task<IReadOnlyList<RemoteFileEntry>> ListAsync(WebDavProfile profile, string relativePath, CancellationToken cancellationToken = default)
    {
        using var client = httpClientFactory.CreateClient("webdav");
        client.BaseAddress = profile.BaseUri;
        if (!string.IsNullOrEmpty(profile.Username))
        {
            var credentials = Convert.ToBase64String(System.Text.Encoding.UTF8.GetBytes($"{profile.Username}:{profile.Password}"));
            client.DefaultRequestHeaders.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Basic", credentials);
        }

        var webDavClient = new WebDavClient(client);
        var path = NormalizeRemotePath(profile.RootPath, relativePath);
        var response = await webDavClient.Propfind(path).ConfigureAwait(false);
        if (!response.IsSuccessful)
        {
            if (response.StatusCode == (int)HttpStatusCode.NotFound)
            {
                return [];
            }

            throw new InvalidOperationException($"WebDAV PROPFIND failed for '{path}' with status {(int)response.StatusCode}.");
        }

        return response.Resources
            .Select(resource => new RemoteFileEntry(resource.Uri ?? "", resource.IsCollection, resource.ContentLength, resource.LastModifiedDate))
            .ToList();
    }
}
