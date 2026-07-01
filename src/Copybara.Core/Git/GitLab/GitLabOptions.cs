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

using Copybara.Credentials;
using Copybara.Git.GitLab.Api;
using Copybara.Http;
using Copybara.Http.Auth;
using CopybaraConsole = Copybara.Util.Console.Console;

namespace Copybara.Git.GitLab;

/// <summary>
/// Options related to GitLab endpoints. Port of
/// <c>com.google.copybara.git.gitlab.GitLabOptions</c>.
/// </summary>
public class GitLabOptions : IOption
{
    private readonly HttpOptions _httpOptions;
    private HttpClient? _cachedTransport;
    private Func<IGitLabApiTransport, GitLabApi> _gitLabApiSupplier = transport => new GitLabApi(transport);
    private Func<Uri, UsernamePasswordIssuer, CredentialFileHandler> _credentialFileHandlerSupplier =
        (url, issuer) =>
            new CredentialFileHandler(
                url.Host, url.AbsolutePath, issuer.Username, issuer.Password);

    // --gitlab-destination-delete-mr-branch
    public bool? GitlabDeleteMrBranch { get; set; }

    public GitLabOptions()
        : this(new HttpOptions())
    {
    }

    public GitLabOptions(HttpOptions httpOptions)
    {
        _httpOptions = httpOptions;
    }

    /// <summary>
    /// Obtains a supplier that returns a global instance of an HttpClient to be used for
    /// GitLab-related traffic.
    /// </summary>
    public Func<HttpClient> GetHttpTransportSupplier() =>
        () => _cachedTransport ??= _httpOptions.GetTransport();

    /// <summary>
    /// Creates an object for interacting with the GitLab API that communicates via the given transport.
    /// </summary>
    public GitLabApi GetGitLabApi(IGitLabApiTransport transport) => _gitLabApiSupplier(transport);

    /// <summary>Creates a credential file handler for the given GitLab URL and credential issuer.</summary>
    public CredentialFileHandler GetCredentialFileHandler(Uri url, UsernamePasswordIssuer issuer) =>
        _credentialFileHandlerSupplier(url, issuer);

    /// <summary>Creates a <see cref="IGitLabApiTransport"/> using the provided parameters.</summary>
    public static IGitLabApiTransport GetApiTransport(
        string repoUrl,
        HttpClient httpTransport,
        CopybaraConsole console,
        IAuthInterceptor? authInterceptor) =>
        new GitLabApiTransportImpl(repoUrl, httpTransport, console, authInterceptor);

    /// <summary>Sets the function responsible for supplying a new <see cref="GitLabApi"/> object.</summary>
    public void SetGitLabApiSupplier(Func<IGitLabApiTransport, GitLabApi> function) =>
        _gitLabApiSupplier = function;

    /// <summary>Sets the function responsible for supplying a new credential file handler.</summary>
    public void SetCredentialFileHandlerSupplier(
        Func<Uri, UsernamePasswordIssuer, CredentialFileHandler> function) =>
        _credentialFileHandlerSupplier = function;
}
