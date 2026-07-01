/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

using System.Net;
using System.Net.Http;
using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Git;
using ConsoleT = Copybara.Util.Console.Console;

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// Base implementation of <see cref="IGitHubApiTransport"/> that uses
/// <see cref="System.Net.Http.HttpClient"/> and <c>System.Text.Json</c> for the requests.
/// </summary>
/// <remarks>
/// Port of <c>com.google.copybara.git.github.api.AbstractGitHubApiTransport</c> (google-http-client
/// based). Credentials are resolved through the git credential helper, exactly as upstream: a token
/// for the API host is preferred, falling back to the web host.
/// </remarks>
public abstract class AbstractGitHubApiTransport : IGitHubApiTransport
{
    private const string GitHubDotComApiUrl = "https://api.github.com";
    private const string GitHubDotComWebUrl = "https://github.com";

    protected readonly string ApiUrl;
    protected readonly string WebUrl;
    protected readonly GitRepository Repo;
    protected readonly HttpClient HttpClient;
    protected readonly string StorePath;
    protected readonly ConsoleT Console;
    protected readonly bool BearerAuth;

    protected AbstractGitHubApiTransport(
        GitRepository repo,
        HttpClient httpClient,
        string storePath,
        bool bearerAuth,
        ConsoleT console,
        string webUrl)
    {
        Repo = Preconditions.CheckNotNull(repo);
        HttpClient = Preconditions.CheckNotNull(httpClient);
        StorePath = storePath;
        Console = Preconditions.CheckNotNull(console);
        BearerAuth = bearerAuth;
        WebUrl = BuildWebUrl(Preconditions.CheckNotNull(webUrl));
        ApiUrl = DetermineApiUrl(WebUrl);
    }

    /// <summary>Sends the request, allowing subclasses to intercept it (e.g. for tests).</summary>
    protected abstract Task<HttpResponseMessage> ExecuteRequestAsync(HttpRequestMessage request);

    public async Task<T?> GetAsync<T>(
        string path,
        ImmutableListMultimap<string, string> headers,
        string requestDescription)
    {
        GitCredential.UserPassword? credentials = GetCredentialsIfPresent();
        Uri url = GetFullEndpointUrl(path);
        try
        {
            Console.VerboseFmt("Executing {0}", requestDescription);
            using var request = new HttpRequestMessage(HttpMethod.Get, url);
            ApplyHeaders(request, credentials, headers);
            using HttpResponseMessage response = await ExecuteRequestAsync(request).ConfigureAwait(false);
            await ThrowIfError(response, "GET", path, null).ConfigureAwait(false);
            return await ParseResponseAsync<T>(response).ConfigureAwait(false);
        }
        catch (HttpRequestException e)
        {
            throw new RepoException("Error running GitHub API operation " + path, e);
        }
    }

    public Task<T?> GetAsync<T>(string path, string requestDescription) =>
        GetAsync<T>(path, ImmutableListMultimap<string, string>.Empty, requestDescription);

    public async Task<T?> PostAsync<T>(string path, object request, string requestType)
    {
        GitCredential.UserPassword credentials = GetCredentials();
        Uri url = GetFullEndpointUrl(path);
        string requestJson = JsonSerializer.Serialize(request, request.GetType(), GitHubApiJson.Options);
        try
        {
            Console.VerboseFmt("Executing {0}", requestType);
            using var httpRequest = new HttpRequestMessage(HttpMethod.Post, url)
            {
                Content = new StringContent(requestJson, Encoding.UTF8, "application/json"),
            };
            ApplyHeaders(httpRequest, credentials, ImmutableListMultimap<string, string>.Empty);
            using HttpResponseMessage response =
                await ExecuteRequestAsync(httpRequest).ConfigureAwait(false);
            await ThrowIfError(response, "POST", path, requestJson).ConfigureAwait(false);
            return await ParseResponseAsync<T>(response).ConfigureAwait(false);
        }
        catch (HttpRequestException e)
        {
            throw new RepoException("Error running GitHub API operation " + path, e);
        }
    }

    public abstract Task DeleteAsync(string path, string requestType);

    private async Task<T?> ParseResponseAsync<T>(HttpResponseMessage response)
    {
        T? responseObj = await ParseHttpResponseAsync<T>(response).ConfigureAwait(false);
        if (responseObj is IPaginatedPayload paginatedPayload)
        {
            return (T)paginatedPayload.AnnotatePayload(ApiUrl, MaybeGetLinkHeader(response));
        }

        return responseObj;
    }

    private static async Task<T?> ParseHttpResponseAsync<T>(HttpResponseMessage response)
    {
        if (response.StatusCode == HttpStatusCode.NoContent)
        {
            return default;
        }

        byte[] bytes = await response.Content.ReadAsByteArrayAsync().ConfigureAwait(false);
        if (bytes.Length == 0)
        {
            return default;
        }

        try
        {
            return JsonSerializer.Deserialize<T>(bytes, GitHubApiJson.Options);
        }
        catch (Exception e) when (e is JsonException or NotSupportedException)
        {
            throw new RepoException(
                $"Cannot parse content as type {typeof(T)}.\nContent: {Encoding.UTF8.GetString(bytes)}\n",
                e);
        }
    }

    /// <summary>Throws a <see cref="GitHubApiException"/> if the response is not a success.</summary>
    protected async Task ThrowIfError(
        HttpResponseMessage response, string method, string path, string? request)
    {
        if (response.IsSuccessStatusCode)
        {
            return;
        }

        string? content = response.Content == null
            ? null
            : await response.Content.ReadAsStringAsync().ConfigureAwait(false);
        throw new GitHubApiException(
            (int)response.StatusCode,
            ParseErrorOrIgnore(content),
            method,
            path,
            request,
            content);
    }

    protected Uri GetFullEndpointUrl(string path)
    {
        string maybePrefix = path.StartsWith("/", StringComparison.Ordinal) ? "" : "/";
        return new Uri(ApiUrl + maybePrefix + path);
    }

    protected static ClientError? ParseErrorOrIgnore(string? content)
    {
        if (content == null)
        {
            return null;
        }

        try
        {
            return JsonSerializer.Deserialize<ClientError>(content, GitHubApiJson.Options);
        }
        catch (JsonException)
        {
            return new ClientError();
        }
    }

    protected static string? MaybeGetLinkHeader(HttpResponseMessage response)
    {
        if (response.Headers.TryGetValues("Link", out IEnumerable<string>? link))
        {
            return link.FirstOrDefault();
        }

        return null;
    }

    protected void ApplyHeaders(
        HttpRequestMessage request,
        GitCredential.UserPassword? userPassword,
        ImmutableListMultimap<string, string> headers)
    {
        if (userPassword != null)
        {
            if (BearerAuth)
            {
                request.Headers.Authorization =
                    new AuthenticationHeaderValue("Bearer", userPassword.GetPasswordBeCareful());
            }
            else
            {
                string basic = Convert.ToBase64String(
                    Encoding.ASCII.GetBytes(
                        userPassword.GetUsername() + ":" + userPassword.GetPasswordBeCareful()));
                request.Headers.Authorization = new AuthenticationHeaderValue("Basic", basic);
            }
        }

        foreach (KeyValuePair<string, string> header in headers)
        {
            request.Headers.TryAddWithoutValidation(header.Key, header.Value);
        }
    }

    /// <summary>Credentials for API should be optional for any read operation (GET).</summary>
    protected GitCredential.UserPassword? GetCredentialsIfPresent()
    {
        try
        {
            return GetCredentials();
        }
        catch (ValidationException)
        {
            string msg =
                $"GitHub credentials not found in {StorePath}. Assuming the repository is public.";
            Console.Info(msg);
            return null;
        }
    }

    /// <summary>
    /// Gets the credentials from git credential helper. First tries the apiUrl host, then falls back
    /// to the webUrl host.
    /// </summary>
    /// <exception cref="RepoException"/>
    /// <exception cref="ValidationException"/>
    protected GitCredential.UserPassword GetCredentials()
    {
        try
        {
            return Repo.CredentialFill(ApiUrl);
        }
        catch (ValidationException)
        {
            try
            {
                return Repo.CredentialFill(WebUrl);
            }
            catch (ValidationException e1)
            {
                throw new ValidationException(
                    $"Cannot get credentials for host {WebUrl} or {ApiUrl} from credentials helper."
                    + " Make sure either your credential helper has the username and password/token"
                    + $" or if you don't use one, that file '{StorePath}' contains one of the two"
                    + " lines: \nEither:\n"
                    + $"https://USERNAME:TOKEN@{RemoveHttpsPrefix(ApiUrl)}\n"
                    + "or:\n"
                    + $"https://USERNAME:TOKEN@{RemoveHttpsPrefix(WebUrl)}\n"
                    + "\n"
                    + "Note that spaces or other special characters need to be escaped. For example"
                    + " ' ' should be %20 and '@' should be %40 (For example when using the email"
                    + " as username)",
                    e1);
            }
        }
    }

    private static string RemoveHttpsPrefix(string url) => url.Replace("https://", "");

    private static string BuildWebUrl(string hostName) => "https://" + hostName;

    private static string DetermineApiUrl(string hostName)
    {
        // Github.com has a unique API URL.
        // GitHub Enterprise instances have a specific format of API URL.
        if (hostName == GitHubDotComWebUrl)
        {
            return GitHubDotComApiUrl;
        }

        return hostName + "/api/v3";
    }

    public string GetApiUrl() => ApiUrl;

    public string GetWebUrl() => WebUrl;
}
