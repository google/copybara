/*
 * Copyright (C) 2016 Google LLC
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

using System.Text.RegularExpressions;
using Copybara.Checks;
using Copybara.Exceptions;
using Copybara.Git.GerritApi;
using Copybara.Http;
using Console = Copybara.Util.Console.Console;
using GerritApiClient = Copybara.Git.GerritApi.GerritApi;
using ProfilerType = Copybara.Profiler.Profiler;

namespace Copybara.Git;

/// <summary>
/// Arguments for <see cref="GerritDestination"/>. Port of
/// <c>com.google.copybara.git.GerritOptions</c>.
/// </summary>
public class GerritOptions : IOption
{
    private static readonly Regex ChangeIdPattern = new("^I[0-9a-f]{40}$", RegexOptions.Compiled);

    protected readonly GeneralOptions GeneralOptions;
    protected GitOptions GitOptions;
    private ISleeper _sleeper = SystemSleeper.Instance;

    // The HttpOptions are used to obtain an HttpClient. Java uses NetHttpTransport directly.
    private readonly HttpOptions _httpOptions;

    public GerritOptions(GeneralOptions generalOptions, GitOptions gitOptions)
        : this(generalOptions, gitOptions, new HttpOptions())
    {
    }

    public GerritOptions(GeneralOptions generalOptions, GitOptions gitOptions, HttpOptions httpOptions)
    {
        GeneralOptions = generalOptions;
        GitOptions = gitOptions;
        _httpOptions = httpOptions;
    }

    public ISleeper GetSleeper() => _sleeper;

    public void SetSleeper(ISleeper sleeper) => _sleeper = sleeper;

    // --gerrit-change-id
    public string GerritChangeId { get; set; } = "";

    // --gerrit-new-change
    public bool NewChange { get; set; }

    // --gerrit-topic
    public string GerritTopic { get; set; } = "";

    // --force-gerrit-submit
    public bool? ForceGerritSubmit { get; set; }

    // --nogerrit-rev-id-label (DEPRECATED)
    public bool NoRevIdDeprecated { get; set; }

    /// <summary>Validate that the argument is a valid Gerrit Change-id.</summary>
    public static void ValidateChangeId(string name, string value)
    {
        if (!string.IsNullOrEmpty(value) && !ChangeIdPattern.IsMatch(value))
        {
            throw new ArgumentException(
                $"{name} value '{value}' does not match Gerrit Change ID pattern: {ChangeIdPattern}");
        }
    }

    /// <summary>Returns a lazy supplier of <see cref="GerritApi"/>.</summary>
    internal LazyResourceLoader<GerritApiClient> NewGerritApiSupplier(string url, IChecker? checker) =>
        LazyResourceLoader.Memoized<GerritApiClient>(console =>
            checker == null
                ? NewGerritApi(url)
                : NewGerritApi(url, checker, console!));

    /// <summary>Override this method in a class for a specific Gerrit implementation.</summary>
    public virtual GerritApiClient NewGerritApi(string url) => NewGerritApi(url, null, null);

    /// <summary>Creates a new <see cref="GerritApi"/> enforcing the given checker.</summary>
    protected virtual GerritApiClient NewGerritApi(string url, IChecker? checker, Console? console)
    {
        if (checker == null)
        {
            return new GerritApiClient(NewGerritApiTransport(HostUrl(url)), GeneralOptions.Profiler());
        }
        return new GerritApiClient(
            NewGerritApiTransport(HostUrl(url), checker, console!), GeneralOptions.Profiler());
    }

    /// <summary>Return the url removing the path part, since the API needs the host.</summary>
    protected static Uri HostUrl(string url)
    {
        Uri result = AsUri(url);
        ValidationException.CheckCondition(result.Host != null, "Wrong url: {0}", url);
        ValidationException.CheckCondition(result.Scheme != null, "Wrong url: {0}", url);
        var builder = new UriBuilder(result.Scheme, result.Host, result.IsDefaultPort ? -1 : result.Port)
        {
            UserName = result.UserInfo,
            Path = string.Empty,
            Query = string.Empty,
            Fragment = string.Empty,
        };
        return builder.Uri;
    }

    private static Uri AsUri(string url)
    {
        try
        {
            return new Uri(url);
        }
        catch (UriFormatException)
        {
            throw new ValidationException("Invalid URL " + url);
        }
    }

    /// <summary>
    /// Given a repo url, return the project part.
    ///
    /// <para>Not static on purpose, since we might introduce different behavior based on other flags
    /// in the future.</para>
    /// </summary>
    public string GetProject(string url)
    {
        string file = AsUri(url).AbsolutePath;
        if (file.StartsWith('/'))
        {
            file = file.Substring(1);
        }
        if (file.EndsWith('/'))
        {
            file = file.Substring(0, file.Length - 1);
        }
        return Regex.Replace(file, "[ \"'&]", "");
    }

    /// <summary>Create a Gerrit http transport for a URI.</summary>
    protected virtual IGerritApiTransport NewGerritApiTransport(Uri uri) =>
        new GerritApiTransportImpl(GetCredentialsRepo(), uri, _httpOptions.GetTransport());

    /// <summary>Create a Gerrit http transport for a URI and checker.</summary>
    protected virtual IGerritApiTransport NewGerritApiTransport(
        Uri uri, IChecker checker, Console console) =>
        new GerritApiTransportWithChecker(NewGerritApiTransport(uri), checker, console);

    protected virtual GitRepository GetCredentialsRepo() =>
        GitOptions.CachedBareRepoForUrl("just_for_github_api");

    /// <summary>Validate if a checker is valid to use with a Gerrit endpoint for repoUrl.</summary>
    public virtual void ValidateEndpointChecker(IChecker? checker, string repoUrl)
    {
        // Accept any by default
    }
}

/// <summary>Abstraction over Thread.Sleep to allow overriding in tests. Port of TestSleeper.</summary>
public interface ISleeper
{
    void Sleep(long millis);
}

/// <summary>The system sleeper implementation.</summary>
public sealed class SystemSleeper : ISleeper
{
    public static readonly SystemSleeper Instance = new();

    public void Sleep(long millis) => Thread.Sleep((int)millis);
}
