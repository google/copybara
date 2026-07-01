/*
 * Copyright (C) 2020 Google Inc.
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
using Copybara.Common;
using Copybara.Exceptions;

namespace Copybara.Git.GitHub.Util;

/// <summary>
/// An object that parses GitHub urls in their components (project, name, etc.). Port of
/// <c>com.google.copybara.git.github.util.GitHubHost</c>.
/// </summary>
public class GitHubHost
{
    /// <summary>GitHub.com host singleton, matching the Java GITHUB_COM constant.</summary>
    public static readonly GitHubHost GitHubCom = new("github.com");

    private readonly Regex _gitHubPrUrlPattern;
    private readonly string _host;

    private static readonly Regex TrailingGitOrSlash =
        new("([.]git|/)$", RegexOptions.Compiled);

    private static readonly Regex LeadingSlash = new("^/", RegexOptions.Compiled);

    private static readonly Regex FirstTwoSegments =
        new("^([^/]+/[^/]+).*$", RegexOptions.Compiled);

    public GitHubHost(string host)
    {
        _host = Preconditions.CheckNotNull(host);
        _gitHubPrUrlPattern = new Regex(
            "^https://" + Regex.Escape(host) + "/(.+)/pull/([0-9]+)$", RegexOptions.Compiled);
    }

    /// <summary>
    /// Return the username part of a github url. For example in https://github.com/foo/bar/baz, 'foo'
    /// would be the user.
    /// </summary>
    public string GetUserNameFromUrl(string url)
    {
        string project = GetProjectNameFromUrl(url);
        int i = project.IndexOf('/');
        return i == -1 ? project : project.Substring(0, i);
    }

    /// <summary>
    /// Given a GitHub host name and a url that represents a GitHub repository, return the project
    /// name, e.g. org/repo.
    /// </summary>
    public string GetProjectNameFromUrl(string url)
    {
        ValidationException.CheckCondition(!string.IsNullOrEmpty(url), "Empty url");

        string gitProtocolPrefix = "git@" + _host + ":";
        if (url.StartsWith(gitProtocolPrefix, StringComparison.Ordinal))
        {
            return TrailingGitOrSlash.Replace(url.Substring(gitProtocolPrefix.Length), "");
        }

        Uri uri;
        try
        {
            if (!Uri.TryCreate(url, UriKind.Absolute, out uri!) || uri.Scheme == null)
            {
                // Mirror Java's URI.create("notimportant://" + url) fallback for scheme-less urls.
                uri = new Uri("notimportant://" + url);
            }
        }
        catch (UriFormatException e)
        {
            throw new ValidationException("Cannot find project name from url " + url, e);
        }

        ValidationException.CheckCondition(
            _host.Equals(uri.Host, StringComparison.Ordinal),
            "Not a github url: {0}. Expected host: {1}",
            url,
            _host);

        string name = TrailingGitOrSlash.Replace(LeadingSlash.Replace(uri.AbsolutePath, ""), "");
        Match firstTwo = FirstTwoSegments.Match(name);
        if (firstTwo.Success)
        {
            name = firstTwo.Groups[1].Value;
        }

        ValidationException.CheckCondition(
            !string.IsNullOrEmpty(name), "Cannot find project name from url {0}", url);
        return name;
    }

    /// <summary>Returns true if url is a GitHub url for a given GitHub or Enterprise host.</summary>
    public bool IsGitHubUrl(string url)
    {
        try
        {
            GetProjectNameFromUrl(url);
            return true;
        }
        catch (ValidationException)
        {
            return false;
        }
    }

    public string ProjectAsUrl(string project) => "https://" + _host + "/" + project;

    public string GetHost() => _host;

    public string NormalizeUrl(string url) => ProjectAsUrl(GetProjectNameFromUrl(url));

    /// <summary>Given a reference, parse it as a GitHub PR data if it is a url for a PR.</summary>
    public GitHubPrUrl? MaybeParseGithubPrUrl(string @ref)
    {
        Match matcher = _gitHubPrUrlPattern.Match(@ref);
        return matcher.Success
            ? new GitHubPrUrl(matcher.Groups[1].Value, int.Parse(matcher.Groups[2].Value))
            : null;
    }

    /// <summary>A GitHub PR project and number.</summary>
    public sealed class GitHubPrUrl
    {
        private readonly string _project;
        private readonly int _prNumber;

        public GitHubPrUrl(string project, int prNumber)
        {
            _project = project;
            _prNumber = prNumber;
        }

        public string GetProject() => _project;

        public int GetPrNumber() => _prNumber;

        public override string ToString() =>
            $"GitHubPrUrl{{project={_project}, prNumber={_prNumber}}}";
    }
}
