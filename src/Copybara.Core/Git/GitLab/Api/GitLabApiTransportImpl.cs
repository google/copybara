/*
 * Copyright (C) 2025 Google LLC
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
using System.Text;
using System.Text.Json;
using Copybara.Common;
using Copybara.Credentials;
using Copybara.Exceptions;
using Copybara.Git.GitLab.Api.Entities;
using Copybara.Http.Auth;
using Copybara.Json;
using CopybaraConsole = Copybara.Util.Console.Console;

namespace Copybara.Git.GitLab.Api;

/// <summary>
/// An implementation of <see cref="IGitLabApiTransport"/> that communicates with a GitLab API
/// endpoint using an <see cref="HttpClient"/>. Credentials are obtained from the provided
/// <see cref="IAuthInterceptor"/>.
/// </summary>
public class GitLabApiTransportImpl : IGitLabApiTransport
{
    private const string ApiPath = "api/v4";

    private static readonly JsonSerializerOptions RequestSerializerOptions = new()
    {
        DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
    };

    private readonly string _hostUrl;
    private readonly HttpClient _httpClient;
    private readonly CopybaraConsole _console;
    private readonly IAuthInterceptor? _authInterceptor;

    public GitLabApiTransportImpl(
        string repoUrl,
        HttpClient httpClient,
        CopybaraConsole console,
        IAuthInterceptor? authInterceptor)
    {
        _httpClient = httpClient;
        _hostUrl = GetGitLabHostUrl(repoUrl);
        _console = console;
        _authInterceptor = authInterceptor;
    }

    public T? Get<T>(string path, ImmutableListMultimap<string, string> headers)
    {
        Uri url = GetFullEndpointUri(path);
        try
        {
            _console.VerboseFmt("Sending GET request to %s", url);
            using HttpResponseMessage httpResponse = ExecuteRequest(HttpMethod.Get, url, headers, content: null);
            T? response = ParseResponse<T>(httpResponse);
            if (response is IPaginatedPageList paginatedPageList)
            {
                // This PaginatedPageList is guaranteed to cast back to a T.
                response = (T)paginatedPageList.WithPaginatedInfo(GetApiUrl(), httpResponse.Headers);
            }

            return response;
        }
        catch (HttpResponseException e)
        {
            throw new GitLabApiException($"Error calling GET on {url}", e.StatusCode, e);
        }
        catch (HttpRequestException e)
        {
            throw new GitLabApiException($"Error calling GET on {url}", e);
        }
        catch (ArgumentException e)
        {
            throw new GitLabApiException(
                $"Error calling GET on {url}. Failed to parse response. Cause: {e.Message}", e);
        }
    }

    public T? Post<T>(string path, IGitLabApiEntity request, ImmutableListMultimap<string, string> headers)
    {
        Uri url = GetFullEndpointUri(path);
        try
        {
            _console.VerboseFmt("Sending POST request to %s", url);
            using HttpResponseMessage httpResponse =
                ExecuteRequest(HttpMethod.Post, url, headers, ToJsonContent(request));
            return ParseResponse<T>(httpResponse);
        }
        catch (HttpResponseException e)
        {
            throw new GitLabApiException($"Error calling POST on {url}", e.StatusCode, e);
        }
        catch (HttpRequestException e)
        {
            throw new GitLabApiException($"Error calling {url}", e);
        }
    }

    public T? Put<T>(string path, IGitLabApiEntity request, ImmutableListMultimap<string, string> headers)
    {
        Uri url = GetFullEndpointUri(path);
        try
        {
            _console.VerboseFmt("Sending PUT request to %s", url);
            using HttpResponseMessage httpResponse =
                ExecuteRequest(HttpMethod.Put, url, headers, ToJsonContent(request));
            return ParseResponse<T>(httpResponse);
        }
        catch (HttpResponseException e)
        {
            throw new GitLabApiException($"Error calling PUT on {url}", e.StatusCode, e);
        }
        catch (HttpRequestException e)
        {
            throw new GitLabApiException($"Error calling {url}", e);
        }
    }

    public void Delete(string path)
    {
        Uri url = GetFullEndpointUri(path);
        try
        {
            _console.VerboseFmt("Sending DELETE request to %s", url);
            using HttpResponseMessage response =
                ExecuteRequest(HttpMethod.Delete, url, ImmutableListMultimap<string, string>.Empty, content: null);
        }
        catch (HttpResponseException e)
        {
            throw new GitLabApiException($"Error calling DELETE on {url}", e.StatusCode, e);
        }
        catch (HttpRequestException e)
        {
            throw new GitLabApiException($"Error calling {url}", e);
        }
    }

    private static T? ParseResponse<T>(HttpResponseMessage httpResponse)
    {
        return GsonParserUtil.ParseHttpResponseAsync<T>(httpResponse, stripNoExecutePrefix: false)
            .GetAwaiter()
            .GetResult();
    }

    private static HttpContent ToJsonContent(IGitLabApiEntity request)
    {
        string json = JsonSerializer.Serialize(request, request.GetType(), RequestSerializerOptions);
        return new StringContent(json, Encoding.UTF8, "application/json");
    }

    private HttpResponseMessage ExecuteRequest(
        HttpMethod method,
        Uri url,
        ImmutableListMultimap<string, string> headers,
        HttpContent? content)
    {
        using var request = new HttpRequestMessage(method, url);
        if (content is not null)
        {
            request.Content = content;
        }

        foreach (string key in headers.Keys)
        {
            request.Headers.TryAddWithoutValidation(key, (IEnumerable<string>)headers.Get(key));
        }

        if (_authInterceptor is not null)
        {
            try
            {
                _authInterceptor.Interceptor().Invoke(request);
            }
            catch (Exception e) when (e is CredentialRetrievalException or CredentialIssuingException)
            {
                throw new ValidationException(
                    $"There was an issue obtaining credentials for {url}: {e.Message}", e);
            }
        }

        HttpResponseMessage response = _httpClient.Send(request);
        if (!response.IsSuccessStatusCode)
        {
            int statusCode = (int)response.StatusCode;
            response.Dispose();
            throw new HttpResponseException(statusCode, $"HTTP {statusCode} calling {url}");
        }

        return response;
    }

    private Uri GetFullEndpointUri(string path)
    {
        string trimmedPath = path.StartsWith('/') ? path.Substring(1) : path;
        return new Uri(GetApiUrl() + "/" + trimmedPath);
    }

    private string GetApiUrl() => _hostUrl + "/" + ApiPath;

    private static string GetGitLabHostUrl(string repoUrl)
    {
        var parsed = new Uri(repoUrl);
        return parsed.Scheme + "://" + GetGitLabHost(parsed);
    }

    private static string GetGitLabHost(Uri uri) =>
        uri.Host + (uri.IsDefaultPort || uri.Port == -1 ? string.Empty : ":" + uri.Port);

    /// <summary>
    /// Signals a non-successful HTTP status. Mirrors google-http-client's
    /// <c>HttpResponseException</c>, whose status code the Java transport surfaces via
    /// <see cref="GitLabApiException"/>.
    /// </summary>
    private sealed class HttpResponseException : Exception
    {
        public HttpResponseException(int statusCode, string message)
            : base(message)
        {
            StatusCode = statusCode;
        }

        public int StatusCode { get; }
    }
}
