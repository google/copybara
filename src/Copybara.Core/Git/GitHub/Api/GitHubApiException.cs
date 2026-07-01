/*
 * Copyright (C) 2018 Google Inc.
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

using System.Text;
using Copybara.Exceptions;

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// Exception that contains the error object from GitHub and maps the Http error codes.
/// </summary>
public class GitHubApiException : RepoException
{
    private readonly string _httpMethod;
    private readonly string _path;
    private readonly string? _request;
    private readonly string? _response;

    public GitHubApiException(
        int httpCode,
        ClientError? error,
        string httpMethod,
        string path,
        string? request,
        string? response)
        : base(DetailedError(httpMethod, path, request, response, httpCode))
    {
        HttpCode = httpCode;
        ResponseCode = ParseResponseCode(httpCode);
        Error = error;
        _httpMethod = httpMethod;
        _path = path;
        _request = request;
        _response = response;
    }

    public GitHubApiResponseCode ResponseCode { get; }

    public int HttpCode { get; }

    public ClientError? Error { get; }

    public GitHubApiResponseCode GetResponseCode() => ResponseCode;

    public int GetHttpCode() => HttpCode;

    public ClientError? GetError() => Error;

    public string GetRawError() =>
        DetailedError(_httpMethod, _path, _request, _response, HttpCode);

    private static string DetailedError(
        string httpMethod, string path, string? request, string? response, int httpCode)
    {
        var sb = new StringBuilder("GitHub API call failed with code ")
            .Append(httpCode)
            .Append(" The request was ")
            .Append(httpMethod)
            .Append(' ')
            .Append(path)
            .Append('\n');
        if (request != null)
        {
            sb.Append("Request object:\n").Append(request).Append('\n');
        }

        sb.Append("Response:\n").Append(response).Append('\n');
        return sb.ToString();
    }

    private static GitHubApiResponseCode ParseResponseCode(int code) =>
        code switch
        {
            400 => GitHubApiResponseCode.BAD_REQUEST,
            401 => GitHubApiResponseCode.UNAUTHORIZED,
            403 => GitHubApiResponseCode.FORBIDDEN,
            404 => GitHubApiResponseCode.NOT_FOUND,
            409 => GitHubApiResponseCode.CONFLICT,
            422 => GitHubApiResponseCode.UNPROCESSABLE_ENTITY,
            _ => GitHubApiResponseCode.UNKNOWN,
        };
}

/// <summary>
/// Known GitHub response codes.
///
/// <para>Note that UNKNOWN will be used for any other not in this list.</para>
/// </summary>
public enum GitHubApiResponseCode
{
    UNKNOWN = 0,
    BAD_REQUEST = 400,
    UNAUTHORIZED = 401,
    FORBIDDEN = 403,
    NOT_FOUND = 404,
    CONFLICT = 409,
    UNPROCESSABLE_ENTITY = 422,
}
