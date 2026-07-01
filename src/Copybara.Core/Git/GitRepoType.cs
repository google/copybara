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
using Copybara.Common;

// Domain 'Console' collides with System.Console; qualify.
using Console = Copybara.Util.Console.Console;

namespace Copybara.Git;

/// <summary>
/// Git repository type. Knowing the repository type allows us to provide a better experience, like
/// allowing to import GitHub PR / Gerrit changes using the web url as the reference. Port of
/// <c>com.google.copybara.git.GitRepoType</c>.
///
/// <para>NOTE: The GitHub / GitLab / Gerrit resolution paths are owned by peer files
/// (Gerrit*/GitHub*/GitLab*) that are being ported separately. Until those land, those repo types
/// delegate to the standard GIT resolution. The GIT path (the common case) is fully ported.</para>
/// </summary>
public enum GitRepoType
{
    /// <summary>A standard git repository. This is the default.</summary>
    Git,

    /// <summary>A git repository hosted in GitHub.</summary>
    GitHub,

    /// <summary>A git repository hosted in GitLab.</summary>
    GitLab,

    /// <summary>A Gerrit code review repository.</summary>
    Gerrit,
}

/// <summary>Resolution logic for <see cref="GitRepoType"/>.</summary>
public static class GitRepoTypeMethods
{
    private static readonly Regex GitUrl = new(
        @"(\w+://)(.+@)*([\w.]+)(:[\d]+)?/*(.*)", RegexOptions.Compiled);

    private static readonly Regex FileUrl = new("file://(.*)", RegexOptions.Compiled);

    /// <summary>Example: "54d2a09b272f22a6d27e76b891f36213b98e0ddc random text"</summary>
    private static readonly Regex Sha1WithReviewData = new(
        "^(?:[a-f0-9]{40}|[a-f0-9]{64}) (.+)$", RegexOptions.Compiled);

    private static readonly Regex Sha1WithReviewDataCapture = new(
        "^((?:[a-f0-9]{40}|[a-f0-9]{64})) (.+)$", RegexOptions.Compiled);

    /// <summary>
    /// Resolve a reference for the given repo type. Currently GitHub/GitLab/Gerrit delegate to the
    /// standard GIT resolution (their specialized handling is owned by peer files).
    /// </summary>
    public static GitRevision ResolveRef(
        this GitRepoType type,
        GitRepository repository,
        string repoUrl,
        string @ref,
        GeneralOptions generalOptions,
        bool describeVersion,
        bool partialFetch,
        int? fetchDepth)
    {
        // TODO(peer): GitHub PR / GitLab MR / Gerrit change resolution is provided by the
        // Gerrit*/GitHub*/GitLab* files being ported separately. Until then, all types fall through
        // to the standard GIT resolution below.
        return ResolveGitRef(
            repository, repoUrl, @ref, generalOptions, describeVersion, partialFetch, fetchDepth);
    }

    /// <summary>
    /// Standard git resolution. Supports SHA-1 references reachable from heads, valid git refs, and
    /// fetching HEAD or a reference from a git url.
    /// </summary>
    private static GitRevision ResolveGitRef(
        GitRepository repository,
        string repoUrl,
        string @ref,
        GeneralOptions generalOptions,
        bool describeVersion,
        bool partialFetch,
        int? fetchDepth)
    {
        Match sha1WithPatchSet = Sha1WithReviewDataCapture.Match(@ref);
        if (sha1WithPatchSet.Success)
        {
            GitRevision rev = repository.FetchSingleRefWithTags(
                repoUrl,
                sha1WithPatchSet.Groups[1].Value,
                fetchTags: describeVersion,
                partialFetch,
                fetchDepth);
            return new GitRevision(
                repository,
                rev.GetHash(),
                sha1WithPatchSet.Groups[2].Value,
                rev.ContextReference(),
                rev.AssociatedLabels(),
                repoUrl);
        }

        if (!GitUrl.IsMatch(@ref) && !FileUrl.IsMatch(@ref))
        {
            // If ref is not a url try a normal fetch of repoUrl and ref.
            return FetchFromUrl(
                repository, repoUrl, @ref, describeVersion, partialFetch, fetchDepth);
        }

        Console console = generalOptions.GetConsole();
        console.Warn("Git origin URL overwritten in the command line as " + @ref);
        console.Progress("Fetching HEAD for " + @ref);

        int spaceIdx = @ref.LastIndexOf(' ');
        // Treat "http://someurl ref" as a url and a reference.
        if (spaceIdx != -1)
        {
            return FetchFromUrl(
                repository,
                @ref.Substring(0, spaceIdx),
                @ref.Substring(spaceIdx + 1),
                describeVersion,
                partialFetch,
                fetchDepth);
        }
        return FetchFromUrl(repository, @ref, "HEAD", describeVersion, partialFetch, fetchDepth);
    }

    private static GitRevision FetchFromUrl(
        GitRepository repository,
        string repoUrl,
        string @ref,
        bool describeVersion,
        bool partialFetch,
        int? fetchDepth) =>
        repository.FetchSingleRefWithTags(
            repoUrl, @ref, fetchTags: describeVersion, partialFetch, fetchDepth);
}
