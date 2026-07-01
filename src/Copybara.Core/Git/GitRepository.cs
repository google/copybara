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

using System.Collections.Immutable;
using System.Globalization;
using System.Text;
using System.Text.RegularExpressions;
using Copybara.Authoring;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Util;

namespace Copybara.Git;

/// <summary>
/// A class for manipulating Git repositories. Port of
/// <c>com.google.copybara.git.GitRepository</c>.
///
/// <para>Most operations are backed by the <c>git</c> binary invoked through
/// <see cref="CommandRunner"/>, which mirrors the upstream Java implementation (which shells out for
/// nearly every operation). LibGit2Sharp is available (referenced by the project) for the few
/// operations where it maps cleanly.</para>
/// </summary>
public class GitRepository
{
    public static readonly TimeSpan DefaultRepoTimeout = TimeSpan.FromMinutes(15);

    // TODO: Make this generic (Using URIish).
    private static readonly Regex FullUri = new(
        "([a-z][a-z0-9+-]+@[a-zA-Z0-9_.-]+(:.+)?|^[a-z][a-z0-9+-]+://.*)$", RegexOptions.Compiled);

    private static readonly Regex LsTreeElement = new(
        @"([0-9]{6}) (commit|tag|tree|blob) ([a-f0-9]{40,64})\t(.*)", RegexOptions.Compiled);

    private static readonly Regex LsRemoteOutputLine = new(
        @"([a-f0-9]{40,64}|ref: refs/heads/\S+)\t(.+)", RegexOptions.Compiled);

    private static readonly Regex HashPattern = new("^[a-f0-9]{6,64}$", RegexOptions.Compiled);

    private static readonly Regex CompleteHashPattern = new(
        "^(?:[a-f0-9]{40}|[a-f0-9]{64})$", RegexOptions.Compiled);

    private static readonly Regex DefaultBranchPattern = new(
        @"ref: (refs/heads/(\S+))", RegexOptions.Compiled | RegexOptions.Singleline);

    // Pattern for matching URLs with a scheme, such as http:// or rpc://.
    private static readonly Regex UrlWithSchemePattern = new(
        "^([a-z][a-z0-9+-]+://)(.*)$", RegexOptions.Compiled);

    private static readonly Regex FailedRebase = new(
        "(Failed to merge in the changes|Could not apply.*)", RegexOptions.Compiled);

    private static readonly ImmutableArray<Regex> RefNotFoundErrors =
    [
        new Regex("pathspec '(.+)' did not match any file", RegexOptions.Compiled),
        new Regex(
            "ambiguous argument '(.+)': unknown revision or path not in the working tree",
            RegexOptions.Compiled),
    ];

    private static readonly Regex FetchCannotResolveErrors = new(
        "(fatal: [Cc]ouldn't find remote ref"
            + "|no such remote ref"
            + "|fatal: no matching remote head"
            + "|upload-pack: not our ref"
            + "|ERR want .+ not valid)",
        RegexOptions.Compiled);

    private static readonly Regex NoGitRepository = new(
        "does not appear to be a git repository", RegexOptions.Compiled);

    private static readonly Regex ProtectedBranch = new(
        "([Pp]rotected branch hook declined)", RegexOptions.Compiled);

    private static readonly Regex ScpUriPattern = new(
        @"^(?:([a-z][a-z0-9+-]+)@)?([a-zA-Z0-9_.-]+)([:/])([^/].*|/|/[^/].*)$",
        RegexOptions.Compiled);

    /// <summary>Label to be used for marking the original revision id for migrated commits.</summary>
    public const string GitOriginRevId = "GitOrigin-RevId";

    // Git exits with 128 in several circumstances. For example failed rebase.
    private static bool IsNonCrashErrorExitCode(int code) => (code >= 1 && code <= 10) || code == 128;

    private const int DefaultMaxLogLines = 4_000;
    protected const int DefaultMaxLsRemoteLines = int.MaxValue;

    public const string GitDescribeRequestedVersion = "GIT_DESCRIBE_REQUESTED_VERSION";
    public const string GitDescribeChangeVersion = "GIT_DESCRIBE_CHANGE_VERSION";
    public const string GitDescribeFirstParent = "GIT_DESCRIBE_FIRST_PARENT";
    public const string GitSequentialRevisionNumber = "GIT_SEQUENTIAL_REVISION_NUMBER";
    public const string GitDescribeAbbrev = "GIT_DESCRIBE_ABBREV";
    public const string GitTagPointsAt = "GIT_TAG_POINTS_TO";
    public const string HttpPermissionDenied = "The requested URL returned error: 403";
    public const string FullRefNamespace = "_copybara_full_ref";
    public const string CopybaraFetchNamespace = "refs/copybara_fetch";

    private readonly string _gitDir;
    private readonly string? _workTree;
    private readonly bool _verbose;
    protected readonly GitEnvironment GitEnv;
    private readonly TimeSpan _repoTimeout;
    protected readonly PushOptionsValidator PushOptionsValidatorField;
    protected readonly bool NoVerify;

    private static readonly Dictionary<char, StatusCode> CharToStatusCode =
        Enum.GetValues<StatusCode>().ToDictionary(StatusCodeMethods.GetCode);

    protected GitRepository(
        string gitDir,
        string? workTree,
        bool verbose,
        GitEnvironment gitEnv,
        TimeSpan repoTimeout,
        bool noVerify,
        PushOptionsValidator pushOptionsValidator)
    {
        _gitDir = Preconditions.CheckNotNull(gitDir);
        _workTree = workTree;
        _verbose = verbose;
        GitEnv = Preconditions.CheckNotNull(gitEnv);
        _repoTimeout = repoTimeout;
        NoVerify = noVerify;
        PushOptionsValidatorField = Preconditions.CheckNotNull(pushOptionsValidator);
    }

    /// <summary>Creates a new repository in the given directory. The new repo is not bare.</summary>
    public static GitRepository NewRepo(
        bool verbose, string path, GitEnvironment gitEnv, TimeSpan repoTimeout, bool noVerify) =>
        new(
            Path.Combine(path, ".git"),
            path,
            verbose,
            gitEnv,
            repoTimeout,
            noVerify,
            new PushOptionsValidator(null));

    /// <summary>Creates a new repository in the given directory. The new repo is not bare.</summary>
    public static GitRepository NewRepo(
        bool verbose,
        string path,
        GitEnvironment gitEnv,
        TimeSpan repoTimeout,
        bool noVerify,
        PushOptionsValidator pushOptionsValidator) =>
        new(Path.Combine(path, ".git"), path, verbose, gitEnv, repoTimeout, noVerify,
            pushOptionsValidator);

    /// <summary>Creates a new repository with a default repo timeout. The new repo is not bare.</summary>
    public static GitRepository NewRepo(bool verbose, string path, GitEnvironment gitEnv) =>
        NewRepo(verbose, path, gitEnv, DefaultRepoTimeout, noVerify: false);

    /// <summary>Create a new bare repository.</summary>
    public static GitRepository NewBareRepo(
        string gitDir, GitEnvironment gitEnv, bool verbose, TimeSpan repoTimeout, bool noVerify) =>
        new(gitDir, null, verbose, gitEnv, repoTimeout, noVerify, new PushOptionsValidator(null));

    /// <summary>Create a new bare repository with a push options validator.</summary>
    public static GitRepository NewBareRepo(
        string gitDir,
        GitEnvironment gitEnv,
        bool verbose,
        TimeSpan repoTimeout,
        bool noVerify,
        PushOptionsValidator pushOptionsValidator) =>
        new(gitDir, null, verbose, gitEnv, repoTimeout, noVerify, pushOptionsValidator);

    /// <summary>
    /// Get the version of git that will be used. Returns null if git cannot be found.
    /// </summary>
    private static string? Version(GitEnvironment gitEnv)
    {
        try
        {
            return ExecuteGit(
                Directory.GetCurrentDirectory(),
                new[] { "version" },
                gitEnv,
                verbose: false,
                timeout: null).GetStdout();
        }
        catch (CommandException)
        {
            return null;
        }
    }

    /// <summary>Validate that a refspec is valid.</summary>
    /// <exception cref="InvalidRefspecException">if the refspec is not valid</exception>
    internal static void ValidateRefSpec(GitEnvironment gitEnv, string cwd, string refspec)
    {
        if (QuickRefspecValidation(refspec))
        {
            return;
        }
        try
        {
            ExecuteGit(
                cwd,
                new[] { "check-ref-format", "--allow-onelevel", "--refspec-pattern", refspec },
                gitEnv,
                verbose: false,
                timeout: null);
        }
        catch (CommandException)
        {
            string? version = Version(gitEnv);
            throw new InvalidRefspecException(
                version != null
                    ? "Invalid refspec: " + refspec
                    : $"Cannot find git binary at '{gitEnv.ResolveGitBinary()}'");
        }
    }

    private static readonly Regex BasicRefspecComponent = new(
        "^[A-Za-z0-9_-][A-Za-z0-9_.-]*$", RegexOptions.Compiled);

    /// <summary>
    /// Skip calling the CLI for common refspecs that we know are safe. Returning false is always
    /// safe (but less optimal).
    /// </summary>
    private static bool QuickRefspecValidation(string refspec)
    {
        if (!refspec.StartsWith("refs/", StringComparison.Ordinal)
            || refspec.EndsWith('.')
            || refspec.Contains("..", StringComparison.Ordinal)
            || refspec.EndsWith(".lock", StringComparison.Ordinal))
        {
            return false;
        }
        bool wildcard = false;
        foreach (var component in refspec.Split('/'))
        {
            if (component == "*")
            {
                if (wildcard)
                {
                    return false;
                }
                wildcard = true;
                continue;
            }
            if (!BasicRefspecComponent.IsMatch(component))
            {
                return false;
            }
        }
        return true;
    }

    /// <summary>Fetch a reference from a git url.</summary>
    public GitRevision FetchSingleRef(string url, string @ref, bool partialFetch, int? depth) =>
        FetchSingleRefWithTags(url, @ref, fetchTags: false, partialFetch, depth);

    public GitRevision FetchSingleRefWithTags(
        string url, string @ref, bool fetchTags, bool partialFetch, int? depth)
    {
        if (@ref.Contains(':') || @ref.Contains('*'))
        {
            throw new CannotResolveRevisionException(
                "Fetching refspecs that contain local ref path locations or wildcards is not"
                    + " supported. Invalid ref: " + @ref);
        }
        bool isHashRef = IsHashReference(@ref);
        if (isHashRef)
        {
            bool tags = !partialFetch && fetchTags;
            try
            {
                Fetch(url, prune: false, force: true, Array.Empty<string>(), partialFetch, depth, tags);
            }
            catch (CannotResolveRevisionException)
            {
                // Some servers are configured without HEAD. That is fine, we'll try fetching the SHA.
            }
            try
            {
                return ResolveReferenceWithContext(@ref, contextRef: @ref, url);
            }
            catch (Exception e) when (e is RepoException or CannotResolveRevisionException)
            {
                // Ignore, the fetch below will attempt using the SHA-1.
            }
        }

        var refspec = new List<string>
        {
            $"{@ref}:{CopybaraFetchNamespace}/{@ref}",
        };
        if (fetchTags)
        {
            refspec.Add("refs/tags/*:refs/tags/*");
        }

        if (!@ref.StartsWith("refs/", StringComparison.Ordinal))
        {
            var fullRefspec = new List<string>(refspec);
            if (!isHashRef)
            {
                fullRefspec.Add(
                    $"refs/*/{@ref}:{CopybaraFetchNamespace}/refs/*/{@ref}{FullRefNamespace}");
            }

            try
            {
                Fetch(url, prune: false, force: true, fullRefspec, partialFetch, depth, tags: false);
                return ResolveReferenceWithContext(
                    $"{CopybaraFetchNamespace}/{@ref}", contextRef: @ref, url);
            }
            catch (Exception e) when (e is RepoException or CannotResolveRevisionException)
            {
                // Ignore, the fetch below will attempt using a simpler refspec.
            }
        }

        Fetch(url, prune: false, force: true, refspec, partialFetch, depth, tags: false);
        return ResolveReferenceWithContext(
            $"{CopybaraFetchNamespace}/{@ref}", contextRef: @ref, url);
    }

    public GitRevision AddDescribeVersion(GitRevision rev)
    {
        var describeLabels = ImmutableListMultimap<string, string>.CreateBuilder();
        describeLabels.Put(GitDescribeRequestedVersion, Describe(rev, false)!);
        describeLabels.Put(GitDescribeFirstParent, Describe(rev, true));
        string? describeAbbrev = DescribeAbbrev(rev);
        if (describeAbbrev != null)
        {
            describeLabels.Put(GitDescribeAbbrev, describeAbbrev);
        }
        return rev.WithLabels(describeLabels.Build());
    }

    internal string? Describe(GitRevision rev, bool fallback, params string[] arg)
    {
        try
        {
            var args = new List<string> { "describe" };
            args.AddRange(arg);
            args.Add("--");
            args.Add(rev.GetHash());
            return SimpleCommand(args).GetStdout().Trim();
        }
        catch (RepoException)
        {
            if (!fallback)
            {
                return null;
            }
            return SimpleCommand("describe", "--always", "--", rev.GetHash()).GetStdout().Trim();
        }
    }

    public string Describe(GitRevision rev, bool firstParent) =>
        Describe(rev, true, firstParent ? new[] { "--first-parent" } : Array.Empty<string>())!;

    /// <summary>Finds a tag that exactly points to the given revision.</summary>
    public string? DescribeExactMatch(GitRevision rev) =>
        Describe(rev, false, "--exact-match", "--tags");

    public string? DescribeAbbrev(GitRevision rev)
    {
        string? contextRef = rev.ContextReference();
        if (!string.IsNullOrEmpty(contextRef)
            && !GitRevision.CompleteGitHashPattern.IsMatch(contextRef))
        {
            try
            {
                foreach (var tag in TagPointsAt(rev))
                {
                    if (tag == contextRef)
                    {
                        return tag;
                    }
                }
            }
            catch (RepoException)
            {
                // Cannot get `git tag --points-at`. Falling back to `git describe`.
            }
        }
        return Describe(rev, false, "--tag", "--abbrev=0");
    }

    public IReadOnlyList<string> TagPointsAt(GitRevision rev) =>
        SimpleCommand("tag", "--points-at", rev.GetHash()).GetStdout().Trim().Split('\n');

    public string ShowDiff(string referenceFrom, string referenceTo)
    {
        Preconditions.CheckNotNull(referenceFrom, "Parameter referenceFrom should not be null");
        Preconditions.CheckNotNull(referenceTo, "Parameter referenceTo should not be null");
        return SimpleCommand("diff", referenceFrom, referenceTo).GetStdout();
    }

    /// <summary>Fetch zero or more refspecs in the local repository.</summary>
    public FetchResult Fetch(
        string url,
        bool prune,
        bool force,
        IEnumerable<string> refspecs,
        bool partialFetch,
        int? depth,
        bool tags)
    {
        var args = new List<string> { "fetch", ValidateUrl(url) };
        if (tags)
        {
            args.Add("--tags");
        }
        if (depth.HasValue)
        {
            args.Add($"--depth={depth.Value}");
        }
        if (partialFetch)
        {
            args.Add("--filter=blob:none");
        }
        args.Add("--verbose");
        args.Add("--progress");
        if (prune)
        {
            args.Add("-p");
        }
        if (force)
        {
            args.Add("-f");
        }

        var requestedRefs = new List<string>();
        foreach (var @ref in refspecs)
        {
            Refspec refSpec = CreateRefSpec(@ref);
            requestedRefs.Add(refSpec.GetOrigin());
            args.Add(@ref);
        }

        var before = ShowRef();
        CommandOutputWithStatus output = GitAllowNonZeroExit(CommandRunner.NoInput, args, _repoTimeout);
        if (output.GetTerminationStatus().Success())
        {
            var after = ShowRef();
            return new FetchResult(before, after);
        }
        CheckFetchError(
            output.GetStderr(), url, requestedRefs, output.GetTerminationStatus().GetExitCode());
        throw ThrowUnknownGitError(output, args);
    }

    public void CheckFetchError(
        string stdErr, string url, IReadOnlyList<string> requestedRefs, int exitCode)
    {
        if (stdErr.Length == 0 || FetchCannotResolveErrors.IsMatch(stdErr))
        {
            throw new CannotResolveRevisionException(
                "Cannot find reference(s): [" + string.Join(", ", requestedRefs) + "]");
        }
        if (NoGitRepository.IsMatch(stdErr))
        {
            throw new CannotResolveRevisionException(
                $"Invalid Git repository: {url}. Error: {stdErr}");
        }
        if (stdErr.Contains("Server does not allow request for unadvertised object"))
        {
            throw new CannotResolveRevisionException($"{url}: {stdErr.Trim()}");
        }
        if (stdErr.Contains("Permission denied")
            || stdErr.Contains("Could not read from remote repository")
            || stdErr.Contains(HttpPermissionDenied)
            || stdErr.Contains("Repository not found"))
        {
            throw new AccessValidationException(stdErr);
        }
    }

    /// <summary>Create a refspec from a string.</summary>
    public Refspec CreateRefSpec(string @ref) => Refspec.Create(GitEnv, _gitDir, @ref);

    public LogCmd Log(string referenceExpr) => LogCmd.Create(this, referenceExpr);

    public PushCmd Push() =>
        new(
            this,
            url: null,
            ImmutableArray<Refspec>.Empty,
            prune: false,
            force: false,
            ImmutableDictionary<string, string>.Empty,
            ImmutableArray<string>.Empty,
            PushOptionsValidatorField);

    public MergeCmd Merge(string branch, IReadOnlyList<string> commits) =>
        MergeCmd.Create(this, branch, commits, _ => true);

    public TagCmd Tag(string tagName) => new(this, tagName, null, false);

    /// <summary>Runs a git ls-remote for a repository url from the current directory.</summary>
    public static IReadOnlyDictionary<string, string> LsRemote(
        string url, ICollection<string> refs, GitEnvironment gitEnv, int maxLogLines) =>
        LsRemote(
            ".", url, refs, gitEnv, maxLogLines, DefaultMaxLsRemoteLines, Array.Empty<string>());

    /// <summary>Runs a git ls-remote for a repository url from the current directory.</summary>
    public static IReadOnlyDictionary<string, string> LsRemote(
        string url,
        ICollection<string> refs,
        GitEnvironment gitEnv,
        ICollection<string> flags,
        int maxLsRemoteLimit) =>
        LsRemote(".", url, refs, gitEnv, DefaultMaxLogLines, maxLsRemoteLimit, flags);

    private static IReadOnlyDictionary<string, string> LsRemote(
        string cwd,
        string url,
        ICollection<string> refs,
        GitEnvironment gitEnv,
        int maxLogLines,
        int maxLsRemoteLimit,
        ICollection<string> flags)
    {
        var result = ImmutableDictionary.CreateBuilder<string, string>();
        var args = new List<string> { "ls-remote" };
        args.AddRange(flags);
        try
        {
            args.Add(ValidateUrl(url));
        }
        catch (ValidationException e)
        {
            throw new RepoException("Invalid url: " + url, e);
        }
        args.AddRange(refs);

        CommandOutputWithStatus output;
        try
        {
            output = ExecuteGit(cwd, args, gitEnv, false, maxLogLines, null);
        }
        catch (BadExitStatusWithOutputException e)
        {
            string stderr = e.GetOutput().GetStderr();
            if (stderr.Contains("Please make sure you have the correct access rights")
                || stderr.Contains(HttpPermissionDenied))
            {
                throw new AccessValidationException(
                    $"Permission denied running ls-remote for '{url}' and refs"
                        + $" '[{string.Join(", ", refs)}]': Exit code"
                        + $" {e.GetOutput().GetTerminationStatus().GetExitCode()}, Output:\n{stderr}",
                    e);
            }
            throw new RepoException(
                $"Error running ls-remote for '{url}' and refs '[{string.Join(", ", refs)}]':"
                    + $" Exit code {e.GetOutput().GetTerminationStatus().GetExitCode()},"
                    + $" Output:\n{stderr}",
                e);
        }
        catch (CommandException e)
        {
            throw new RepoException(
                $"Error running ls-remote for '{url}' and refs '[{string.Join(", ", refs)}]'", e);
        }

        if (output.GetTerminationStatus().Success())
        {
            int rowsAccumulated = 0;
            foreach (var line in output.GetStdout().Split('\n'))
            {
                if (line.Length == 0)
                {
                    continue;
                }
                if (maxLsRemoteLimit >= 0 && rowsAccumulated >= maxLsRemoteLimit)
                {
                    break;
                }
                Match matcher = LsRemoteOutputLine.Match(line);
                if (!matcher.Success)
                {
                    throw new RepoException("Unexpected format for ls-remote output: " + line);
                }
                result[matcher.Groups[2].Value] = matcher.Groups[1].Value;
                rowsAccumulated++;
                if (DefaultBranchPattern.IsMatch(line))
                {
                    break;
                }
            }
        }
        return result.ToImmutable();
    }

    /// <summary>ls-remote using this repository environment and default max log lines.</summary>
    public IReadOnlyDictionary<string, string> LsRemote(string url, ICollection<string> refs) =>
        LsRemote(url, refs, DefaultMaxLogLines);

    public IReadOnlyDictionary<string, string> LsRemote(
        string url, ICollection<string> refs, int maxLogLines) =>
        LsRemote(url, refs, maxLogLines, Array.Empty<string>());

    public IReadOnlyDictionary<string, string> LsRemote(
        string url, ICollection<string> refs, int maxLogLines, ICollection<string> flags) =>
        LsRemote(GetCwd(), url, refs, GitEnv, maxLogLines, DefaultMaxLsRemoteLines, flags);

    public IReadOnlyDictionary<string, string> LsRemote(
        string url, ICollection<string> refs, ICollection<string> flags, int maxLsRemoteLimit) =>
        LsRemote(GetCwd(), url, refs, GitEnv, DefaultMaxLogLines, maxLsRemoteLimit, flags);

    public IReadOnlyDictionary<string, string> LsRemote(
        string url, ICollection<string> refs, ICollection<string> flags) =>
        LsRemote(GetCwd(), url, refs, GitEnv, DefaultMaxLogLines, DefaultMaxLsRemoteLines, flags);

    internal static string ValidateUrl(string url)
    {
        // support remote helper syntax <transport>::<address>
        int sep = url.IndexOf("::", StringComparison.Ordinal);
        if (sep >= 0 && url.IndexOf("::", sep + 2, StringComparison.Ordinal) < 0)
        {
            return url.Substring(0, sep) + "::" + ValidateUrl(url.Substring(sep + 2));
        }

        ValidationException.CheckCondition(
            !url.StartsWith("http://", StringComparison.Ordinal),
            "URL '%s' is not valid - should be using https.",
            url);
        if (FullUri.IsMatch(url))
        {
            return url;
        }

        // Support local folders
        if (Directory.Exists(url))
        {
            return url;
        }
        throw new RepoException($"URL '{url}' is not valid");
    }

    /// <summary>Execute show-ref and return a map from reference name to GitRevision(SHA-1).</summary>
    public IReadOnlyDictionary<string, GitRevision> ShowRef(IEnumerable<string> refs)
    {
        var result = ImmutableDictionary.CreateBuilder<string, GitRevision>();
        var args = new List<string> { "show-ref" };
        args.AddRange(refs);
        CommandOutput commandOutput =
            GitAllowNonZeroExit(CommandRunner.NoInput, args, CommandRunner.DefaultTimeout);

        if (commandOutput.GetStderr().Length != 0)
        {
            throw new RepoException(
                $"Error executing show-ref on {GetGitDir()} git repo:\n{commandOutput.GetStderr()}");
        }

        foreach (var line in commandOutput.GetStdout().Split('\n'))
        {
            if (line.Length == 0)
            {
                continue;
            }
            var strings = line.Split(' ');
            Preconditions.CheckState(
                strings.Length == 2 && CompleteHashPattern.IsMatch(strings[0]),
                "Cannot parse line: '%s'",
                line);
            result[strings[1]] = new GitRevision(this, strings[0]);
        }
        return result.ToImmutable();
    }

    /// <summary>Execute show-ref and return a map from reference name to GitRevision(SHA-1).</summary>
    public IReadOnlyDictionary<string, GitRevision> ShowRef() => ShowRef(Array.Empty<string>());

    internal string MergeBase(string commit1, string commit2) =>
        SimpleCommand("merge-base", commit1, commit2).GetStdout().Trim();

    internal bool IsAncestor(string ancestor, string commit)
    {
        CommandOutputWithStatus result = GitAllowNonZeroExit(
            CommandRunner.NoInput,
            new[] { "merge-base", "--is-ancestor", "--", ancestor, commit },
            CommandRunner.DefaultTimeout);
        if (result.GetTerminationStatus().Success())
        {
            return true;
        }
        if (result.GetTerminationStatus().GetExitCode() == 1)
        {
            return false;
        }
        throw new RepoException(
            "Error executing git merge-base --is-ancestor:\n" + result.GetStderr());
    }

    /// <summary>Returns an instance equivalent to this one but with a different work tree.</summary>
    public GitRepository WithWorkTree(string newWorkTree) =>
        new(_gitDir, newWorkTree, _verbose, GitEnv, _repoTimeout, NoVerify, PushOptionsValidatorField);

    /// <summary>The Git work tree. Returns null for bare repos.</summary>
    public string? GetWorkTree() => _workTree;

    public string GetGitDir() => _gitDir;

    /// <summary>Can be overwritten to add custom behavior.</summary>
    protected virtual string RunPush(PushCmd pushCmd)
    {
        var cmd = new List<string> { "push", "--progress" };

        foreach (var pushOption in pushCmd.PushOptions)
        {
            cmd.Add($"--push-option={pushOption}");
        }
        if (pushCmd.Prune)
        {
            cmd.Add("--prune");
        }
        if (pushCmd.Force)
        {
            cmd.Add("--force");
        }
        foreach (var entry in pushCmd.ForceLease)
        {
            cmd.Add($"--force-with-lease={entry.Key}:{entry.Value}");
        }
        if (NoVerify)
        {
            cmd.Add("--no-verify");
        }
        if (pushCmd.Url != null)
        {
            cmd.Add(ValidateUrl(pushCmd.Url));
            foreach (var refspec in pushCmd.Refspecs)
            {
                cmd.Add(refspec.ToString());
            }
        }
        try
        {
            return SimpleCommand(_repoTimeout, cmd).GetStderr();
        }
        catch (RepoException e)
        {
            if (e.Message.Contains(HttpPermissionDenied))
            {
                throw new AccessValidationException("Permission error pushing to " + pushCmd.Url, e);
            }
            throw;
        }
    }

    /// <summary>git branch command.</summary>
    public sealed class BranchCmd
    {
        private readonly GitRepository _repo;
        private readonly string _name;
        private readonly string? _startPoint;

        internal BranchCmd(GitRepository repo, string name, string? startPoint)
        {
            _repo = repo;
            _name = Preconditions.CheckNotNull(name);
            _startPoint = startPoint;
        }

        /// <summary>Create the branch from this commit. If not set, it uses current HEAD.</summary>
        public BranchCmd WithStartPoint(string startPoint) =>
            new(_repo, _name, Preconditions.CheckNotNull(startPoint));

        public void Run()
        {
            var args = new List<string> { "branch", _name };
            if (_startPoint != null)
            {
                args.Add(_startPoint);
            }
            _repo.SimpleCommand(args);
        }
    }

    public BranchCmd Branch(string name) => new(this, name, null);

    /// <summary>A class that represents 'git cherry-pick' command and options.</summary>
    public sealed class CherryPickCmd
    {
        private readonly GitRepository _repo;
        private readonly ImmutableArray<string> _commits;
        private readonly int? _parentNumber;
        private readonly bool _addCommitOriginInfo;
        private readonly bool _fastForward;
        private readonly bool _allowEmpty;

        internal CherryPickCmd(
            GitRepository repo,
            ImmutableArray<string> commits,
            int? parentNumber,
            bool addCommitOriginInfo,
            bool fastForward,
            bool allowEmpty)
        {
            _repo = repo;
            _commits = commits;
            _parentNumber = parentNumber;
            _addCommitOriginInfo = addCommitOriginInfo;
            _fastForward = fastForward;
            _allowEmpty = allowEmpty;
        }

        public CherryPickCmd ParentNumber(int parentNumber) =>
            new(_repo, _commits, parentNumber, _addCommitOriginInfo, _fastForward, _allowEmpty);

        public CherryPickCmd AddCommitOriginInfo(bool addCommitOriginInfo) =>
            new(_repo, _commits, _parentNumber, addCommitOriginInfo, _fastForward, _allowEmpty);

        public CherryPickCmd FastForward(bool fastForward) =>
            new(_repo, _commits, _parentNumber, _addCommitOriginInfo, fastForward, _allowEmpty);

        public CherryPickCmd AllowEmpty(bool allowEmpty) =>
            new(_repo, _commits, _parentNumber, _addCommitOriginInfo, _fastForward, allowEmpty);

        public void Run()
        {
            var args = new List<string> { "cherry-pick" };
            if (_parentNumber != null)
            {
                args.Add("-m");
                args.Add(_parentNumber.Value.ToString(CultureInfo.InvariantCulture));
            }
            if (_addCommitOriginInfo)
            {
                args.Add("-x");
            }
            if (_fastForward)
            {
                args.Add("--ff");
            }
            if (_allowEmpty)
            {
                args.Add("--allow-empty");
            }
            args.AddRange(_commits);
            _repo.SimpleCommand(args);
        }
    }

    public CherryPickCmd CherryPick(IEnumerable<string> commits) =>
        new(this, commits.ToImmutableArray(), null, false, false, false);

    public void AbortCherryPick() => SimpleCommand("cherry-pick", "--abort");

    /// <summary>An add command bound to the repo.</summary>
    public sealed class AddCmd
    {
        private readonly GitRepository _repo;
        private readonly bool _force;
        private readonly bool _all;
        private readonly IReadOnlyList<string> _files;
        private readonly string? _pathSpecFromFile;

        internal AddCmd(
            GitRepository repo,
            bool force,
            bool all,
            IReadOnlyList<string> files,
            string? pathSpecFromFile)
        {
            _repo = repo;
            _force = force;
            _all = all;
            _files = Preconditions.CheckNotNull(files);
            _pathSpecFromFile = pathSpecFromFile;
        }

        public AddCmd Force() => new(_repo, true, _all, _files, _pathSpecFromFile);

        public AddCmd All()
        {
            Preconditions.CheckState(_files.Count == 0, "'all' and passing files is incompatible");
            Preconditions.CheckState(
                _pathSpecFromFile == null, "'all' and pathSpecFromFile is incompatible");
            return new AddCmd(_repo, _force, true, _files, _pathSpecFromFile);
        }

        public AddCmd Files(IEnumerable<string> files)
        {
            Preconditions.CheckState(!_all, "'all' and passing files is incompatible");
            Preconditions.CheckState(
                _pathSpecFromFile == null, "'pathSpecFromFile' and passing files is incompatible");
            return new AddCmd(_repo, _force, false, files.ToImmutableArray(), _pathSpecFromFile);
        }

        public AddCmd PathSpecFromFile(string pathSpecFromFile)
        {
            Preconditions.CheckState(!_all, "'pathSpecFromFile' and passing files is incompatible");
            Preconditions.CheckState(
                _files.Count == 0, "'pathSpecFromFile' and passing files is incompatible");
            return new AddCmd(_repo, _force, false, _files, pathSpecFromFile);
        }

        public AddCmd Files(params string[] files) => Files((IEnumerable<string>)files);

        public void Run()
        {
            var @params = new List<string> { "add" };
            if (_force)
            {
                @params.Add("-f");
            }
            if (_all)
            {
                @params.Add("--all");
            }
            if (_pathSpecFromFile != null)
            {
                @params.Add("--pathspec-from-file=" + _pathSpecFromFile);
            }
            @params.Add("--");
            @params.AddRange(_files);
            _repo.Git(_repo.GetCwd(), null, _repo.AddGitDirAndWorkTreeParams(@params));
        }
    }

    public AddCmd Add() => new(this, false, false, Array.Empty<string>(), null);

    /// <summary>Get a field from a configuration.</summary>
    private string? GetConfigField(string field, string? configFile)
    {
        var @params = new List<string> { "config" };
        if (configFile != null)
        {
            @params.Add("-f");
            @params.Add(configFile);
        }
        @params.Add("--get");
        @params.Add(field);
        CommandOutputWithStatus @out =
            GitAllowNonZeroExit(CommandRunner.NoInput, @params, CommandRunner.DefaultTimeout);
        if (@out.GetTerminationStatus().Success())
        {
            return @out.GetStdout().Trim();
        }
        if (@out.GetTerminationStatus().GetExitCode() == 1 && @out.GetStderr().Length == 0)
        {
            return null;
        }
        throw new RepoException("Error executing git config:\n" + @out.GetStderr());
    }

    private IReadOnlySet<string> GetSubmoduleNames()
    {
        if (!File.Exists(Path.Combine(GetCwd(), ".gitmodules")))
        {
            return ImmutableHashSet<string>.Empty;
        }
        var @params = new List<string> { "config", "-f", ".gitmodules", "-l", "--name-only" };
        CommandOutputWithStatus @out = GitAllowNonZeroExit(
            CommandRunner.NoInput, AddGitDirAndWorkTreeParams(@params), CommandRunner.DefaultTimeout);
        if (@out.GetTerminationStatus().Success())
        {
            var modules = new LinkedHashSet<string>();
            foreach (var raw in @out.GetStdout().Trim().Split('\n'))
            {
                var line = raw.Trim();
                if (!line.StartsWith("submodule.", StringComparison.Ordinal))
                {
                    continue;
                }
                int lastDot = line.LastIndexOf('.');
                modules.Add(line.Substring(
                    "submodule.".Length,
                    (lastDot > 0 ? lastDot : line.Length) - "submodule.".Length));
            }
            return modules.ToImmutableHashSet();
        }
        if (@out.GetTerminationStatus().GetExitCode() == 1 && @out.GetStderr().Length == 0)
        {
            return ImmutableHashSet<string>.Empty;
        }
        throw new RepoException("Error executing git config:\n" + @out.GetStderr());
    }

    /// <summary>Resolves a git reference to the SHA-1 reference.</summary>
    public string ParseRef(string @ref)
    {
        CommandOutputWithStatus result = GitAllowNonZeroExit(
            CommandRunner.NoInput,
            new[] { "rev-list", "-1", @ref, "--" },
            CommandRunner.DefaultTimeout);
        if (!result.GetTerminationStatus().Success())
        {
            throw new CannotResolveRevisionException("Cannot find reference '" + @ref + "'");
        }
        string sha1 = result.GetStdout().Trim();
        Preconditions.CheckState(
            CompleteHashPattern.IsMatch(sha1), "Should be resolved to a complete hash: %s", sha1);
        return sha1;
    }

    internal bool RefExists(string @ref)
    {
        try
        {
            ParseRef(@ref);
            return true;
        }
        catch (CannotResolveRevisionException)
        {
            return false;
        }
    }

    /// <summary>An object capable of performing a 'git rebase' operation.</summary>
    public sealed class RebaseCmd
    {
        private readonly GitRepository _repo;
        private readonly string? _branch;
        private readonly string _upstream;
        private readonly string? _into;
        private readonly string? _errorAdvice;

        internal RebaseCmd(
            GitRepository repo,
            string upstream,
            string? branch,
            string? into,
            string? errorAdvice)
        {
            _repo = repo;
            _branch = branch;
            _upstream = upstream;
            _into = into;
            _errorAdvice = errorAdvice;
        }

        public RebaseCmd Branch(string branch) =>
            new(_repo, _upstream, branch, _into, _errorAdvice);

        public RebaseCmd Into(string into) => new(_repo, _upstream, _branch, into, _errorAdvice);

        public RebaseCmd ErrorAdvice(string errorAdvice) =>
            new(_repo, _upstream, _branch, _into, errorAdvice);

        /// <summary>Run 'git rebase'.</summary>
        /// <exception cref="RebaseConflictException">if there is a conflict</exception>
        public void Run()
        {
            var cmd = new List<string> { "rebase", _upstream };
            if (_branch != null)
            {
                cmd.Add(_branch);
            }
            if (_into != null)
            {
                cmd.Add("--into");
                cmd.Add(_into);
            }

            CommandOutputWithStatus output =
                _repo.GitAllowNonZeroExit(CommandRunner.NoInput, cmd, CommandRunner.DefaultTimeout);
            if (output.GetTerminationStatus().Success())
            {
                return;
            }
            if (FailedRebase.IsMatch(output.GetStderr()))
            {
                throw new RebaseConflictException(
                    $"Conflict detected while rebasing {_repo._workTree} to {_branch}. Please sync"
                        + " or update the change in the origin and retry. Git output was:\n"
                        + $"{output.GetStdout()}{(_errorAdvice != null ? ". " + _errorAdvice : "")}");
            }
            throw new RepoException(output.GetStderr());
        }
    }

    public RebaseCmd RebaseCmdFor(string upstream) =>
        new(this, Preconditions.CheckNotNull(upstream), null, null, null);

    /// <summary>Try to cherry pick a commit. If it fails, cherry-pick is aborted and false returned.</summary>
    public bool TryToCherryPick(string commit)
    {
        try
        {
            SimpleCommand("cherry-pick", commit);
            return true;
        }
        catch (RepoException)
        {
            try
            {
                AbortCherryPick();
            }
            catch (RepoException)
            {
                // cherry-pick --abort failed.
            }
            return false;
        }
    }

    /// <summary>Return the branch name of HEAD. If it fails, return the sha1 of HEAD.</summary>
    public GitRevision GetHeadRef()
    {
        try
        {
            string reference = GetPrimaryBranch();
            return new GitRevision(
                this, ParseRef(reference), null, reference,
                ImmutableListMultimap<string, string>.Empty, null);
        }
        catch (RepoException)
        {
            return new GitRevision(this, ResolveReference("HEAD").GetHash());
        }
    }

    /// <summary>Check whether the remote sha1's tree is the same as repo's HEAD.</summary>
    public bool HasSameTree(string remoteCommit)
    {
        GitLogEntry newChange = Log("HEAD").WithLimit(1).Run()[^1];
        SimpleCommand("checkout", "-b", "cherry_pick" + Guid.NewGuid(), "HEAD~1");
        if (TryToCherryPick(remoteCommit))
        {
            GitLogEntry oldWithCherryPick = Log("HEAD").WithLimit(1).Run()[^1];
            return oldWithCherryPick.Tree == newChange.Tree;
        }
        return false;
    }

    /// <summary>Checks out the given ref, quietly and throwing away local changes.</summary>
    public CommandOutput ForceCheckout(
        string @ref, IReadOnlySet<string> checkoutPaths, TimeSpan commandTimeout) =>
        ForceCheckout(@ref, commandTimeout, new[] { "-q", "-f" }, checkoutPaths);

    /// <summary>Checks out the given ref, quietly and throwing away local changes.</summary>
    public CommandOutput ForceCheckout(string @ref) => ForceCheckout(@ref, (TimeSpan?)null);

    /// <summary>Checks out the given ref, quietly and throwing away local changes.</summary>
    public CommandOutput ForceCheckout(string @ref, TimeSpan? commandTimeout) =>
        ForceCheckout(@ref, commandTimeout, new[] { "-q", "-f" }, ImmutableHashSet<string>.Empty);

    private CommandOutput ForceCheckout(
        string @ref,
        TimeSpan? commandTimeout,
        IReadOnlyList<string> checkoutArgs,
        IReadOnlySet<string> checkoutPaths)
    {
        Preconditions.CheckArgument(
            !string.IsNullOrEmpty(@ref),
            "Expected a non-empty ref for force checkout but got '%s'",
            @ref);

        var argv = new List<string> { "checkout" };
        argv.AddRange(checkoutArgs);
        argv.Add(@ref);
        argv.AddRange(checkoutPaths.Where(e => e.Length != 0));

        return SimpleCommand(commandTimeout, argv);
    }

    /// <summary>Set the sparse checkout.</summary>
    public CommandOutput SetSparseCheckout(IReadOnlySet<string> checkoutPaths)
    {
        var argv = new List<string> { "sparse-checkout", "set" };
        argv.AddRange(checkoutPaths.Where(s => s.Length != 0));
        argv.Add("--cone");
        return SimpleCommand(argv);
    }

    // Git's ISO8601 format does not deal with subseconds.
    private const string IsoOffsetDateTimeNoSubseconds = "yyyy-MM-dd HH:mm:sszzz";

    // The effective bytes for command-line arguments is ~128k. Setting an arbitrary max of 64k.
    private const int ArbitraryMaxArgSize = 64_000;

    public void Commit(string author, DateTimeOffset timestamp, string message) =>
        Commit(Preconditions.CheckNotNull(author), amend: false, timestamp,
            Preconditions.CheckNotNull(message));

    public void Commit(string? author, bool amend, DateTimeOffset? timestamp, string message)
    {
        if (IsEmptyStaging() && !amend)
        {
            string baseline = "unknown";
            try
            {
                baseline = ParseRef("HEAD");
            }
            catch (Exception e) when (e is CannotResolveRevisionException or RepoException)
            {
                // Cannot find baseline.
            }
            throw new EmptyChangeException(
                $"Migration of the revision resulted in an empty change from baseline"
                    + $" '{baseline}'.\nIs the change already migrated?");
        }

        var @params = new List<string> { "commit" };
        if (author != null)
        {
            @params.Add("--author");
            @params.Add(author);
        }
        if (timestamp != null)
        {
            @params.Add("--date");
            @params.Add(timestamp.Value.ToString(
                IsoOffsetDateTimeNoSubseconds, CultureInfo.InvariantCulture));
        }
        if (amend)
        {
            @params.Add("--amend");
        }
        if (NoVerify)
        {
            @params.Add("--no-verify");
        }
        string? descriptionFile = null;
        try
        {
            if (Encoding.UTF8.GetByteCount(message) > ArbitraryMaxArgSize)
            {
                descriptionFile = Path.Combine(GetCwd(), Guid.NewGuid() + ".desc");
                File.WriteAllBytes(descriptionFile, Encoding.UTF8.GetBytes(message));
                @params.Add("-F");
                @params.Add(Path.GetFullPath(descriptionFile));
            }
            else
            {
                @params.Add("-m");
                @params.Add(message);
            }
            Git(GetCwd(), null, AddGitDirAndWorkTreeParams(@params));
        }
        catch (IOException e)
        {
            throw new RepoException(
                "Could not commit change: Failed to write file " + descriptionFile, e);
        }
        finally
        {
            try
            {
                if (descriptionFile != null && File.Exists(descriptionFile))
                {
                    File.Delete(descriptionFile);
                }
            }
            catch (IOException)
            {
                // Could not delete description file.
            }
        }
    }

    /// <summary>Check if staging is empty (a commit would fail with EmptyChangeException).</summary>
    private bool IsEmptyStaging()
    {
        CommandOutput status = SimpleCommand("diff", "--staged", "--stat");
        return status.GetStdout().Trim().Length == 0;
    }

    public IReadOnlyList<StatusFile> Status()
    {
        CommandOutput output = Git(
            GetCwd(), null, AddGitDirAndWorkTreeParams(new[] { "status", "--porcelain" }));
        var builder = new List<StatusFile>();
        foreach (var line in output.GetStdout().Split('\n'))
        {
            if (line.Length == 0)
            {
                continue;
            }
            // Format 'XY file (-> file)?'
            var rest = line.Substring(3);
            int arrow = rest.IndexOf(" -> ", StringComparison.Ordinal);
            string fileName;
            string? newFileName;
            if (arrow < 0)
            {
                fileName = rest;
                newFileName = null;
            }
            else
            {
                fileName = rest.Substring(0, arrow);
                newFileName = rest.Substring(arrow + 4);
            }
            builder.Add(new StatusFile(
                fileName, newFileName, ToStatusCode(line[0]), ToStatusCode(line[1])));
        }
        return builder;
    }

    private StatusCode ToStatusCode(char c) =>
        CharToStatusCode.TryGetValue(c, out var code)
            ? code
            : throw new InvalidOperationException($"Cannot find status code for '{c}'");

    /// <summary>Find submodules information for the current repository.</summary>
    internal IEnumerable<Submodule> ListSubmodules(string currentRemoteUrl, GitRevision @ref)
    {
        var result = new List<Submodule>();
        foreach (var submoduleName in GetSubmoduleNames())
        {
            string? path = GetSubmoduleField(submoduleName, "path");
            if (path == null)
            {
                throw new RepoException("Path is required for submodule " + submoduleName);
            }
            string? url = GetSubmoduleField(submoduleName, "url");
            if (url == null)
            {
                throw new RepoException("Url is required for submodule " + submoduleName);
            }
            string? branch = GetSubmoduleField(submoduleName, "branch");
            if (branch != null && branch == ".")
            {
                branch = @ref.ContextReference();
            }
            FileUtil.CheckNormalizedRelative(path);
            if (url.StartsWith("../", StringComparison.Ordinal)
                || url.StartsWith("./", StringComparison.Ordinal))
            {
                url = ResolveRelativeUrl(currentRemoteUrl, submoduleName, url);
            }
            try
            {
                result.Add(new Submodule(ValidateUrl(url), submoduleName, branch, path));
            }
            catch (ValidationException e)
            {
                throw new RepoException("Invalid url: " + url, e);
            }
        }
        return result;
    }

    internal IReadOnlyList<TreeElement> LsTree(
        GitRevision reference, string? treeish, bool recursive, bool fullName)
    {
        var result = new List<TreeElement>();
        var args = new List<string> { "ls-tree", reference.GetHash() };
        if (recursive)
        {
            args.Add("-r");
        }
        if (fullName)
        {
            args.Add("--full-name");
        }
        args.Add("-z");
        if (treeish != null)
        {
            args.Add("--");
            args.Add(treeish);
        }

        string stdout = SimpleCommand(args).GetStdout();
        foreach (var line in stdout.Split('\0'))
        {
            if (line.Length == 0)
            {
                continue;
            }
            Match matcher = LsTreeElement.Match(line);
            if (!matcher.Success)
            {
                throw new RepoException("Unexpected format for ls-tree output: " + line);
            }
            string mode = matcher.Groups[1].Value;
            GitObjectType objectType = Enum.Parse<GitObjectType>(matcher.Groups[2].Value, true);
            string sha1 = matcher.Groups[3].Value;
            string path = matcher.Groups[4].Value;
            result.Add(new TreeElement(objectType, sha1, path, mode));
        }
        return result;
    }

    private string ResolveRelativeUrl(
        string currentRemoteUrl, string submoduleName, string relativeUrl)
    {
        Match scpUrl = ScpUriPattern.Match(currentRemoteUrl);
        if (scpUrl.Success)
        {
            string? user = scpUrl.Groups[1].Success ? scpUrl.Groups[1].Value : null;
            string separator = scpUrl.Groups[3].Value;
            // '/' separator with no user means it's not an scp url.
            if (!(separator == "/" && user == null))
            {
                return ResolveRelativeScpUrl(
                    user, scpUrl.Groups[2].Value, scpUrl.Groups[4].Value, submoduleName, relativeUrl);
            }
        }
        return ResolveRelativeStandardUrl(currentRemoteUrl, submoduleName, relativeUrl);
    }

    private string ResolveRelativeStandardUrl(
        string currentRemoteUrl, string submoduleName, string relativeUrl)
    {
        Match matcher = UrlWithSchemePattern.Match(currentRemoteUrl);
        if (!matcher.Success)
        {
            throw new RepoException("Cannot resolve relative URL for: " + currentRemoteUrl);
        }
        string scheme = matcher.Groups[1].Value;
        string path = matcher.Groups[2].Value;
        var traversable = path.Split('/').Where(s => s.Length != 0).ToList();
        string resolved = ResolveRelativeSegments(traversable, relativeUrl, submoduleName);
        return scheme + (path.StartsWith('/') ? "/" : "") + resolved;
    }

    private string ResolveRelativeScpUrl(
        string? user, string host, string path, string submoduleName, string relativeUrl)
    {
        var traversable = path.Split('/').Where(s => s.Length != 0).ToList();
        string resolved = ResolveRelativeSegments(traversable, relativeUrl, submoduleName);
        return (user != null ? user + "@" : "")
            + host
            + ":"
            + (path.StartsWith('/') ? "/" : "")
            + resolved;
    }

    private string ResolveRelativeSegments(
        IReadOnlyList<string> baseSegments, string relativeUrl, string submoduleName)
    {
        var segments = new List<string>(baseSegments);
        foreach (var part in relativeUrl.Split('/').Where(s => s.Length != 0))
        {
            if (part == ".")
            {
                continue;
            }
            if (part == "..")
            {
                if (segments.Count == 0)
                {
                    throw new RepoException(
                        $"Cannot resolve relative url '{relativeUrl}' for submodule"
                            + $" '{submoduleName}': navigating above root");
                }
                segments.RemoveAt(segments.Count - 1);
            }
            else
            {
                segments.Add(part);
            }
        }
        return string.Join("/", segments);
    }

    private string? GetSubmoduleField(string submoduleName, string field) =>
        GetConfigField($"submodule.{submoduleName}.{field}", ".gitmodules");

    private string GetCwd() => _workTree ?? _gitDir;

    private IReadOnlyList<string> AddGitDirAndWorkTreeParams(IEnumerable<string> argv)
    {
        Preconditions.CheckState(
            Directory.Exists(_gitDir),
            "git repository dir '%s' doesn't exist or is not a directory",
            _gitDir);
        var allArgv = new List<string> { "--git-dir=" + _gitDir };
        if (_workTree != null)
        {
            allArgv.Add("--work-tree=" + _workTree);
        }
        allArgv.AddRange(argv);
        return allArgv;
    }

    /// <summary>Initializes the repository.</summary>
    public GitRepository Init() => Init(null);

    /// <summary>Initializes the repository with the specified object format.</summary>
    public GitRepository Init(GitHashAlgorithm? objectFormat)
    {
        try
        {
            Directory.CreateDirectory(_gitDir);
            if (_workTree != null)
            {
                Directory.CreateDirectory(_workTree);
            }
        }
        catch (IOException e)
        {
            throw new RepoException("Cannot create directories: " + e.Message, e);
        }
        var args = new List<string> { "init" };
        if (objectFormat != null)
        {
            args.Add("--object-format=" + objectFormat.Value.ToString().ToLowerInvariant());
        }
        if (_workTree != null && Path.Combine(_workTree, ".git") == _gitDir)
        {
            args.Add(".");
            Git(_workTree, null, args);
        }
        else
        {
            args.Add("--bare");
            Git(_gitDir, null, args);
        }
        return this;
    }

    /// <summary>Returns whether the repository is initialized.</summary>
    public bool IsInitialized() =>
        File.Exists(Path.Combine(_gitDir, "HEAD"))
        || File.Exists(Path.Combine(_gitDir, ".git", "HEAD"));

    /// <summary>Returns the object format of the remote repository (sha1 or sha256).</summary>
    public GitHashAlgorithm GetRemoteObjectFormat(string fetchUrl)
    {
        CommandOutputWithStatus output;
        try
        {
            output = ExecuteGit(
                GetCwd(),
                new[] { "ls-remote", fetchUrl },
                GitEnv.WithVars(new Dictionary<string, string> { ["GIT_TRACE_PACKET"] = "1" }),
                verbose: false,
                maxLogLines: DefaultMaxLogLines,
                timeout: TimeSpan.FromSeconds(10));
        }
        catch (CommandException e)
        {
            throw new RepoException("Cannot get remote object format for " + fetchUrl, e);
        }
        string stderr = output.GetStderr();
        return stderr.Contains("object-format=sha256")
            ? GitHashAlgorithm.Sha256
            : GitHashAlgorithm.Sha1;
    }

    public GitRepository WithCredentialHelper(string credentialHelper)
    {
        ReplaceLocalConfigField("credential", "helper", Preconditions.CheckNotNull(credentialHelper));
        return this;
    }

    public GitRepository WithHttpFollowRedirectsOption(string option)
    {
        ReplaceLocalConfigField("http", "followRedirects", Preconditions.CheckNotNull(option));
        return this;
    }

    public void ReplaceLocalConfigField(string category, string field, string value) =>
        SimpleCommand("config", "--replace-all", "--local", $"{category}.{field}", value);

    public GitRepository EnablePartialFetch()
    {
        try
        {
            SimpleCommand("config", "core.repositoryFormatVersion", "1");
            SimpleCommand("config", "extensions.partialClone", "origin");
        }
        catch (Exception)
        {
            // Partial Clone not supported; ignore.
        }
        return this;
    }

    public void SetRemoteOriginUrl(string url)
    {
        try
        {
            SimpleCommand("config", "remote.origin.url", url);
        }
        catch (RepoException)
        {
            // Ignore.
        }
    }

    public GitCredential.UserPassword CredentialFill(string url) =>
        new GitCredential(TimeSpan.FromMinutes(1), GitEnv).Fill(_gitDir, url);

    /// <summary>Runs a git command with --git-dir and --work-tree set.</summary>
    public CommandOutput SimpleCommand(TimeSpan? timeout, params string[] argv) =>
        SimpleCommand(timeout, (IReadOnlyList<string>)argv);

    public CommandOutput SimpleCommand(params string[] argv) =>
        SimpleCommand((IReadOnlyList<string>)argv);

    public CommandOutput SimpleCommand(TimeSpan? timeout, IReadOnlyList<string> argv) =>
        Git(GetCwd(), timeout, AddGitDirAndWorkTreeParams(argv));

    public CommandOutput SimpleCommand(IReadOnlyList<string> argv) =>
        Git(GetCwd(), null, AddGitDirAndWorkTreeParams(argv));

    internal CommandOutput SimpleCommandNoRedirectOutput(params string[] argv)
    {
        var @params = AddGitDirAndWorkTreeParams(argv);
        try
        {
            return ExecuteGit(GetCwd(), @params, GitEnv, verbose: false, maxLogLines: 0, timeout: null);
        }
        catch (BadExitStatusWithOutputException e)
        {
            CommandOutputWithStatus output = e.GetOutput();
            foreach (var error in RefNotFoundErrors)
            {
                Match matcher = error.Match(output.GetStderr());
                if (matcher.Success)
                {
                    throw new RepoException("Cannot find reference '" + matcher.Groups[1].Value + "'");
                }
            }
            throw ThrowUnknownGitError(output, @params);
        }
        catch (CommandException e)
        {
            throw new RepoException("Error executing 'git': " + e.Message, e);
        }
    }

    internal void ForceClean()
    {
        Preconditions.CheckNotNull(
            _workTree, "Clean only acts on the worktree. A worktree is needed");
        SimpleCommand("clean", "-f", "-d");
    }

    /// <summary>Execute git apply.</summary>
    /// <exception cref="RebaseConflictException">if it cannot apply the change</exception>
    public void Apply(byte[] stdin, bool index)
    {
        CommandOutputWithStatus output = GitAllowNonZeroExit(
            stdin,
            index ? new[] { "apply", "--index" } : new[] { "apply" },
            CommandRunner.DefaultTimeout);
        if (output.GetTerminationStatus().Success())
        {
            return;
        }
        if (output.GetTerminationStatus().GetExitCode() == 1)
        {
            throw new RebaseConflictException("Couldn't apply patch:\n" + output.GetStderr());
        }
        throw new RepoException("Couldn't apply patch:\n" + output.GetStderr());
    }

    /// <summary>Invokes git in cwd and returns the output if successful.</summary>
    public CommandOutput Git(string cwd, params string[] @params) =>
        Git(cwd, null, @params);

    protected CommandOutput Git(string cwd, TimeSpan? timeout, IEnumerable<string> @params)
    {
        try
        {
            return ExecuteGit(cwd, @params, GitEnv, _verbose, timeout);
        }
        catch (BadExitStatusWithOutputException e)
        {
            CommandOutputWithStatus output = e.GetOutput();
            foreach (var error in RefNotFoundErrors)
            {
                Match matcher = error.Match(output.GetStderr());
                if (matcher.Success)
                {
                    throw new RepoException("Cannot find reference '" + matcher.Groups[1].Value + "'");
                }
            }
            throw ThrowUnknownGitError(output, @params);
        }
        catch (CommandException e)
        {
            throw new RepoException("Error executing 'git': " + e.Message, e);
        }
    }

    private RepoException ThrowUnknownGitError(
        CommandOutputWithStatus output, IEnumerable<string> @params) =>
        throw new RepoException(
            $"Error executing 'git {string.Join(' ', @params)}'(exit code"
                + $" {output.GetTerminationStatus().GetExitCode()}). Stderr: {output.GetStderr()}\n");

    /// <summary>
    /// Execute git allowing program non-zero exit codes (0-10 and 128). Still fails for exit codes
    /// like 127 (Command not found).
    /// </summary>
    protected CommandOutputWithStatus GitAllowNonZeroExit(
        byte[] stdin, IEnumerable<string> @params, TimeSpan defaultTimeout) =>
        GitAllowNonZeroExit(stdin, @params, defaultTimeout, -1);

    protected CommandOutputWithStatus GitAllowNonZeroExit(
        byte[] stdin, IEnumerable<string> @params, TimeSpan defaultTimeout, int maxLogLines)
    {
        try
        {
            var allParams = new List<string> { GitEnv.ResolveGitBinary() };
            allParams.AddRange(AddGitDirAndWorkTreeParams(@params));
            var cmd = new Command(allParams.ToArray(), GitEnv.GetEnvironment(), GetCwd());
            var runner = new CommandRunner(cmd, defaultTimeout)
                .WithVerbose(_verbose)
                .WithInput(stdin);
            if (maxLogLines != -1)
            {
                runner = runner.WithMaxStdOutLogLines(maxLogLines);
            }
            return runner.Execute();
        }
        catch (BadExitStatusWithOutputException e)
        {
            CommandOutputWithStatus output = e.GetOutput();
            int exitCode = output.GetTerminationStatus().GetExitCode();
            if (IsNonCrashErrorExitCode(exitCode))
            {
                return output;
            }
            throw ThrowUnknownGitError(output, @params);
        }
        catch (CommandException e)
        {
            throw new RepoException("Error executing 'git': " + e.Message, e);
        }
    }

    private static CommandOutputWithStatus ExecuteGit(
        string cwd,
        IEnumerable<string> @params,
        GitEnvironment gitEnv,
        bool verbose,
        TimeSpan? timeout) =>
        ExecuteGit(cwd, @params, gitEnv, verbose, DefaultMaxLogLines, timeout);

    private static CommandOutputWithStatus ExecuteGit(
        string cwd,
        IEnumerable<string> @params,
        GitEnvironment gitEnv,
        bool verbose,
        int maxLogLines,
        TimeSpan? timeout)
    {
        var allParams = new List<string> { gitEnv.ResolveGitBinary() };
        allParams.AddRange(@params);
        var cmd = new Command(allParams.ToArray(), gitEnv.GetEnvironment(), cwd);
        var runner = (timeout.HasValue
                ? new CommandRunner(cmd, timeout.Value)
                : new CommandRunner(cmd))
            .WithVerbose(verbose);
        return maxLogLines >= 0 ? runner.WithMaxStdOutLogLines(maxLogLines).Execute() : runner.Execute();
    }

    public override string ToString() =>
        $"GitRepository{{gitDir={_gitDir}, workTree={_workTree}, verbose={_verbose}}}";

    /// <summary>Resolve a reference.</summary>
    internal GitRevision ResolveReferenceWithContext(
        string reference, string? contextRef, string url)
    {
        if (GitRevision.CompleteGitHashPattern.IsMatch(reference))
        {
            if (CheckSha1Exists(reference))
            {
                return new GitRevision(this, reference, url);
            }
            throw new CannotResolveRevisionException(
                $"Cannot find '{reference}' object in the repository ({url})");
        }
        return new GitRevision(
            this, ParseRef(reference), null, contextRef,
            ImmutableListMultimap<string, string>.Empty, url);
    }

    /// <summary>Resolve a reference.</summary>
    public GitRevision ResolveReference(string reference)
    {
        if (GitRevision.CompleteGitHashPattern.IsMatch(reference))
        {
            if (CheckSha1Exists(reference))
            {
                return new GitRevision(this, reference);
            }
            throw new CannotResolveRevisionException(
                "Cannot find '" + reference + "' object in the repository");
        }
        return new GitRevision(this, ParseRef(reference));
    }

    /// <summary>Checks if a SHA-1 object exists in the repository.</summary>
    private bool CheckSha1Exists(string reference)
    {
        var @params = new[] { "cat-file", "-e", reference };
        CommandOutputWithStatus output =
            GitAllowNonZeroExit(CommandRunner.NoInput, @params, CommandRunner.DefaultTimeout);
        if (output.GetTerminationStatus().Success())
        {
            return true;
        }
        if (output.GetStderr().Length == 0)
        {
            return false;
        }
        throw ThrowUnknownGitError(output, @params);
    }

    public byte[] ReadFileBytes(string revision, string path)
    {
        CommandOutputWithStatus result = GitAllowNonZeroExit(
            CommandRunner.NoInput,
            new[] { "--no-pager", "show", $"{revision}:{path}" },
            CommandRunner.DefaultTimeout,
            0);
        if (!result.GetTerminationStatus().Success())
        {
            throw new RepoException($"Cannot read file '{path}' in '{revision}'");
        }
        return result.GetStdoutBytes();
    }

    /// <summary>Reads a file at the given revision.</summary>
    public string ReadFile(string revision, string path) =>
        Encoding.UTF8.GetString(ReadFileBytes(revision, path));

    public string ReadSymlink(string revision, string path) => ReadFile(revision, path);

    /// <summary>Returns the commit hash at which the given file was last modified.</summary>
    public string LastModified(string revision, string path)
    {
        CommandOutputWithStatus result = GitAllowNonZeroExit(
            CommandRunner.NoInput,
            new[] { "--no-pager", "log", "--pretty=format:%H", "--max-count=1", revision, "--", path },
            CommandRunner.DefaultTimeout,
            0);
        if (!result.GetTerminationStatus().Success())
        {
            throw new RepoException(
                $"Cannot get last modified revision of '{path}' in '{revision}'");
        }
        return result.GetStdout();
    }

    public void Checkout(Glob glob, string destRoot, GitRevision rev)
    {
        var treeElements = LsTree(rev, null, true, true);
        var pathMatcher = glob.RelativeTo(destRoot);

        void CheckoutFiles(IReadOnlyList<string> files)
        {
            var args = new List<string>
            {
                "--git-dir", _gitDir,
                "--work-tree", destRoot,
                "checkout", rev.GetHash(), "--",
            };
            args.AddRange(files);
            Git(GetCwd(), args.ToArray());
        }

        var pendingFiles = new List<string>();
        int pendingFilesLength = 0;
        foreach (var file in treeElements)
        {
            var path = file.Path;
            if (pathMatcher.Matches(Path.Combine(destRoot, path)))
            {
                pendingFiles.Add(path);
                pendingFilesLength += path.Length;
            }
            // Work around "argument list too long" by batching.
            if (pendingFilesLength > 128 * 1024)
            {
                CheckoutFiles(pendingFiles);
                pendingFiles = new List<string>();
                pendingFilesLength = 0;
            }
        }
        if (pendingFilesLength > 0)
        {
            CheckoutFiles(pendingFiles);
        }
    }

    internal GitRevision CommitTree(string message, string tree, IReadOnlyList<GitRevision> parents)
    {
        var args = new List<string> { "commit-tree", tree };
        foreach (var parent in parents)
        {
            args.Add("-p");
            args.Add(parent.GetHash());
        }
        args.Add("-m");
        args.Add(message);
        return new GitRevision(
            this, Git(GetCwd(), null, AddGitDirAndWorkTreeParams(args)).GetStdout().Trim());
    }

    /// <summary>Creates a reference from a complete SHA-1 without validating it exists.</summary>
    internal GitRevision CreateReferenceFromCompleteSha1(string @ref) => new(this, @ref);

    private bool IsHashReference(string @ref) => HashPattern.IsMatch(@ref);

    /// <summary>Information of a submodule of this repository.</summary>
    public sealed record Submodule(string Url, string Name, string? Branch, string Path);

    internal sealed record TreeElement(GitObjectType Type, string Ref, string Path, string Mode)
    {
        public const string SymlinkMode = "120000";
    }

    internal enum GitObjectType
    {
        Blob,
        Commit,
        Tag,
        Tree,
    }

    public sealed record StatusFile(
        string File, string? NewFileName, StatusCode IndexStatus, StatusCode WorkdirStatus);

    public enum StatusCode
    {
        Unmodified,
        Modified,
        Added,
        Deleted,
        Renamed,
        Copied,
        UpdatedButUnmerged,
        Untracked,
        Ignored,
        ChangeType,
    }

    /// <summary>Hook to rewrite exceptions thrown by the git invocation, e.g. user error.</summary>
    protected virtual void HandlePushException(Exception e, PushCmd cmd)
    {
        // Non-fast-forward errors usually mean the destination has commits the origin doesn't.
        if (e.Message.Contains("(non-fast-forward)") || e.Message.Contains("(fetch first)"))
        {
            throw new NonFastForwardRepositoryException(
                $"Failed to push to {cmd.Url} {FormatRefspecs(cmd.Refspecs)}, because local/origin"
                    + " history is behind destination",
                e);
        }
        if (e.Message.Contains("(stale info)"))
        {
            throw new NonFastForwardRepositoryException(
                $"Failed to push to {cmd.Url} {FormatRefspecs(cmd.Refspecs)}, because destination is"
                    + " not in expected state",
                e);
        }
        if (e is RepoException or ValidationException)
        {
            throw e;
        }
    }

    private static string FormatRefspecs(IReadOnlyList<Refspec> refspecs) =>
        "[" + string.Join(", ", refspecs.Select(r => r.ToString())) + "]";

    /// <summary>An object capable of performing a 'git push' operation to a remote repository.</summary>
    public sealed record PushCmd
    {
        private readonly GitRepository _repo;
        public string? Url { get; }
        public ImmutableArray<Refspec> Refspecs { get; }
        public bool Prune { get; }
        public bool Force { get; }
        public ImmutableDictionary<string, string> ForceLease { get; }
        public ImmutableArray<string> PushOptions { get; }
        private readonly PushOptionsValidator _pushOptionsValidator;

        public PushCmd(
            GitRepository repo,
            string? url,
            ImmutableArray<Refspec> refspecs,
            bool prune,
            bool force,
            ImmutableDictionary<string, string> forceLease,
            ImmutableArray<string> pushOptions,
            PushOptionsValidator pushOptionsValidator)
        {
            _repo = Preconditions.CheckNotNull(repo);
            Preconditions.CheckArgument(
                refspecs.IsEmpty || url != null, "refspec can only be used when a url is passed");
            Url = url;
            Refspecs = refspecs;
            Prune = prune;
            Force = force;
            ForceLease = Preconditions.CheckNotNull(forceLease);
            PushOptions = pushOptions;
            _pushOptionsValidator = pushOptionsValidator;
        }

        public PushCmd WithRefspecs(string url, IEnumerable<Refspec> refspecs) =>
            new(
                _repo, Preconditions.CheckNotNull(url), refspecs.ToImmutableArray(), Prune, Force,
                ForceLease, PushOptions, _pushOptionsValidator);

        public PushCmd WithForceLease(IReadOnlyDictionary<string, string> forceLease) =>
            new(
                _repo, Url, Refspecs, Prune, Force, forceLease.ToImmutableDictionary(), PushOptions,
                _pushOptionsValidator);

        public PushCmd WithPrune(bool prune) =>
            new(_repo, Url, Refspecs, prune, Force, ForceLease, PushOptions, _pushOptionsValidator);

        public PushCmd WithForce(bool force) =>
            new(_repo, Url, Refspecs, Prune, force, ForceLease, PushOptions, _pushOptionsValidator);

        /// <summary>Returns a new instance with the given push options.</summary>
        /// <exception cref="ValidationException">if the push options fail validation</exception>
        public PushCmd WithPushOptions(ImmutableArray<string> newPushOptions)
        {
            _pushOptionsValidator.Validate(newPushOptions);
            return new PushCmd(
                _repo, Url, Refspecs, Prune, Force, ForceLease, newPushOptions,
                _pushOptionsValidator);
        }

        /// <summary>Runs the push command and returns the response from the server.</summary>
        public string Run()
        {
            string? output = null;
            try
            {
                output = _repo.RunPush(this);
            }
            catch (Exception e) when (e is RepoException or ValidationException)
            {
                _repo.HandlePushException(e, this);
            }
            ValidationException.CheckCondition(
                output != null && !ProtectedBranch.IsMatch(output),
                "Cannot push to %s refspecs %s. Please request an admin of the repo to verify the"
                    + " branch protection rules at %s/settings/branches if you think it's a legit"
                    + " branch.",
                Url!,
                FormatRefspecs(Refspecs),
                Url!);
            return output!;
        }
    }

    /// <summary>An object capable of performing a 'git merge' operation.</summary>
    public class MergeCmd
    {
        protected string Branch;
        protected string MergeMessage;
        protected string FastForward;
        protected GitRepository Repo;
        protected IReadOnlyList<string> Commits;
        internal Func<IReadOnlyDictionary<string, string>, bool> Validator;

        public MergeCmd(
            GitRepository repo,
            string branch,
            string mergeMessage,
            IReadOnlyList<string> commits,
            string fastForward,
            Func<IReadOnlyDictionary<string, string>, bool> validator)
        {
            Preconditions.CheckArgument(
                fastForward is "--no-ff" or "--ff-only" or "--ff");
            Repo = Preconditions.CheckNotNull(repo);
            Validator = validator;
            Branch = Preconditions.CheckNotNull(branch);
            MergeMessage = mergeMessage;
            FastForward = Preconditions.CheckNotNull(fastForward);
            Commits = Preconditions.CheckNotNull(commits);
        }

        public static MergeCmd Create(
            GitRepository repo,
            string branch,
            IReadOnlyList<string> commits,
            Func<IReadOnlyDictionary<string, string>, bool> validator) =>
            new(repo, branch, "", commits, "--ff", validator);

        public MergeCmd WithFFMode(string ffMode) =>
            new(Repo, Branch, MergeMessage, Commits, ffMode, Validator);

        public MergeCmd WithMessage(string message) =>
            new(Repo, Branch, message, Commits, FastForward, Validator);

        public void Run(IReadOnlyDictionary<string, string> configs)
        {
            Preconditions.CheckArgument(
                Validator(configs), "Error could not validate git configs in %s", configs);
            var command = new List<string>();
            foreach (var entry in configs)
            {
                command.Add("-c");
                command.Add($"{entry.Key}={entry.Value}");
            }
            command.Add("merge");
            command.Add(Branch);
            if (!string.IsNullOrEmpty(MergeMessage))
            {
                command.Add("-m");
                command.Add(MergeMessage);
            }
            command.Add(FastForward);
            command.AddRange(Commits);
            if (Repo.NoVerify)
            {
                command.Add("--no-verify");
            }
            Repo.SimpleCommand(command);
        }
    }

    /// <summary>
    /// An object capable of performing a 'git log' operation and returning a list of
    /// <see cref="GitLogEntry"/>.
    /// </summary>
    public sealed record LogCmd
    {
        private readonly GitRepository _repo;
        private readonly string _refExpr;
        private readonly int _limit;
        private readonly ImmutableArray<string> _paths;
        private readonly bool _firstParent;
        private readonly bool _includeStat;
        private readonly bool _includeBody;
        private readonly string? _grepString;
        private readonly bool _includeMergeDiff;
        private readonly int _skip;
        private readonly int _batchSize;
        private readonly bool _includeTags;
        private readonly bool _noWalk;
        private readonly bool _topoOrder;

        private const string CommitField = "commit";
        private const string ParentsField = "parents";
        private const string TreeField = "tree";
        private const string AuthorField = "author";
        private const string AuthorDateField = "author_date";
        private const string CommitterField = "committer";
        private const string CommitterDate = "committer_date";
        private const string TagField = "tag";
        private const string BeginBody = "begin_body";
        private const string EndBody = "end_body";
        private const string CommitSeparator = "copybara";
        private static readonly Regex Unindent = new("\n    ", RegexOptions.Compiled);
        private const string Group = "--\n";

        private LogCmd(
            GitRepository repo,
            string refExpr,
            int limit,
            ImmutableArray<string> paths,
            bool firstParent,
            bool includeStat,
            bool includeBody,
            string? grepString,
            bool includeMergeDiff,
            int skip,
            int batchSize,
            bool includeTags,
            bool noWalk,
            bool topoOrder)
        {
            _repo = repo;
            _refExpr = refExpr;
            _limit = limit;
            _paths = paths;
            _firstParent = firstParent;
            _includeStat = includeStat;
            _includeBody = includeBody;
            _grepString = grepString;
            _includeMergeDiff = includeMergeDiff;
            _skip = skip;
            _batchSize = batchSize;
            _includeTags = includeTags;
            _noWalk = noWalk;
            _topoOrder = topoOrder;
        }

        internal static LogCmd Create(GitRepository repository, string refExpr) =>
            new(
                Preconditions.CheckNotNull(repository),
                Preconditions.CheckNotNull(refExpr),
                0,
                ImmutableArray<string>.Empty,
                firstParent: true,
                includeStat: false,
                includeBody: true,
                grepString: null,
                includeMergeDiff: false,
                skip: 0,
                batchSize: 0,
                includeTags: false,
                noWalk: false,
                topoOrder: false);

        private LogCmd Copy(
            int? limit = null,
            ImmutableArray<string>? paths = null,
            bool? firstParent = null,
            bool? includeStat = null,
            bool? includeBody = null,
            string? grepString = null,
            bool? includeMergeDiff = null,
            int? skip = null,
            int? batchSize = null,
            bool? includeTags = null,
            bool? noWalk = null,
            bool? topoOrder = null) =>
            new(
                _repo,
                _refExpr,
                limit ?? _limit,
                paths ?? _paths,
                firstParent ?? _firstParent,
                includeStat ?? _includeStat,
                includeBody ?? _includeBody,
                grepString ?? _grepString,
                includeMergeDiff ?? _includeMergeDiff,
                skip ?? _skip,
                batchSize ?? _batchSize,
                includeTags ?? _includeTags,
                noWalk ?? _noWalk,
                topoOrder ?? _topoOrder);

        public LogCmd WithLimit(int limit)
        {
            Preconditions.CheckArgument(limit > 0);
            return Copy(limit: limit);
        }

        public LogCmd WithSkip(int skip)
        {
            Preconditions.CheckArgument(skip >= 0);
            return Copy(skip: skip);
        }

        public LogCmd WithBatchSize(int batchSize)
        {
            Preconditions.CheckArgument(batchSize >= 0);
            return Copy(batchSize: batchSize);
        }

        public LogCmd WithPaths(ImmutableArray<string> paths)
        {
            Preconditions.CheckArgument(paths.All(s => s.Trim().Length != 0));
            return Copy(paths: paths);
        }

        public LogCmd FirstParent(bool firstParent) => Copy(firstParent: firstParent);

        public LogCmd TopoOrder(bool topoOrder) => Copy(topoOrder: topoOrder);

        public LogCmd IncludeFiles(bool includeStat) => Copy(includeStat: includeStat);

        public LogCmd IncludeMergeDiff(bool includeMergeDiff) =>
            Copy(includeMergeDiff: includeMergeDiff);

        public LogCmd IncludeBody(bool includeBody) => Copy(includeBody: includeBody);

        public LogCmd Grep(string? grepString) => Copy(grepString: grepString);

        public LogCmd IncludeTags(bool includeTags) => Copy(includeTags: includeTags);

        public LogCmd NoWalk(bool noWalk) => Copy(noWalk: noWalk);

        /// <summary>Run 'git log' and returns zero or more <see cref="GitLogEntry"/>.</summary>
        public IReadOnlyList<GitLogEntry> Run()
        {
            var cmd = new List<string> { "log", "--no-color", CreateFormat(_includeBody, _includeTags) };
            if (_includeStat)
            {
                cmd.Add("--name-only");
                cmd.Add("--no-renames");
            }
            if (_firstParent)
            {
                cmd.Add("--first-parent");
            }
            if (_includeMergeDiff)
            {
                cmd.Add("-m");
            }
            cmd.Add("-z");
            if (_includeTags)
            {
                cmd.Add("--tags");
            }
            if (_noWalk)
            {
                cmd.Add("--no-walk");
            }
            if (_topoOrder)
            {
                cmd.Add("--topo-order");
            }
            if (!string.IsNullOrEmpty(_grepString))
            {
                cmd.Add("--grep");
                cmd.Add(_grepString);
            }
            cmd.Add(_refExpr);
            if (!_paths.IsEmpty)
            {
                cmd.Add("--");
                cmd.AddRange(_paths);
            }
            return RunGitLog(cmd);
        }

        private IReadOnlyList<GitLogEntry> RunGitLog(List<string> cmd)
        {
            var res = new List<GitLogEntry>();
            IReadOnlyList<GitLogEntry> batchRes;
            int batchSkip = _skip;
            int overallLimit = _limit;
            do
            {
                var batchCmd = new List<string>(cmd);
                int batchLimit = _limit == 0
                    ? _batchSize
                    : (_batchSize == 0 ? overallLimit : Math.Min(_batchSize, overallLimit));
                if (batchSkip > 0)
                {
                    batchCmd.Add("--skip");
                    batchCmd.Add(batchSkip.ToString(CultureInfo.InvariantCulture));
                }
                if (batchLimit > 0)
                {
                    batchCmd.Add("-" + batchLimit);
                }
                // Avoid logging since git log can return LOT of entries.
                CommandOutput output = _limit is > 0 and < 10
                    ? _repo.SimpleCommand(batchCmd)
                    : _repo.SimpleCommandNoRedirectOutput(batchCmd.ToArray());
                batchRes = ParseLog(output.GetStdout(), _includeBody);
                if (_batchSize > 0)
                {
                    batchSkip += batchRes.Select(e => e.Commit.GetHash()).Distinct().Count();
                    overallLimit -= batchSkip;
                }
                res.AddRange(batchRes);
            }
            while (_batchSize > 0 && (_limit == 0 || overallLimit > 0) && batchRes.Count != 0);
            return res;
        }

        private IReadOnlyList<GitLogEntry> ParseLog(string log, bool includeBody)
        {
            if (log.Length == 0)
            {
                return Array.Empty<GitLogEntry>();
            }

            var commits = new List<GitLogEntry>();
            foreach (var msg in SplitOn(
                log.Substring(CommitSeparator.Length), "\0" + CommitSeparator))
            {
                var groups = SplitOn(msg, "\n" + Group);

                var fields = new Dictionary<string, string>();
                foreach (var kvLine in groups[0].Split('\n'))
                {
                    int idx = kvLine.IndexOf('=');
                    if (idx < 0)
                    {
                        continue;
                    }
                    fields[kvLine.Substring(0, idx)] = kvLine.Substring(idx + 1);
                }

                string? body = null;
                if (includeBody)
                {
                    body = Unindent.Replace(groups[1], "\n");
                    body = body.Substring(
                        BeginBody.Length + 1, body.Length - EndBody.Length - 1 - (BeginBody.Length + 1));
                    body = body.Replace("\r\n", "\n");
                }

                ImmutableHashSet<string>? files = null;
                if (_includeStat)
                {
                    string fileString = groups[2];
                    if (fileString.StartsWith("\0\n", StringComparison.Ordinal))
                    {
                        fileString = fileString.Substring(2);
                    }
                    files = fileString.Split('\0').Where(s => s.Length != 0).ToImmutableHashSet();
                }

                var parents = new List<GitRevision>();
                foreach (var parent in GetField(fields, ParentsField)
                             .Split(' ')
                             .Where(s => s.Length != 0))
                {
                    parents.Add(_repo.CreateReferenceFromCompleteSha1(parent));
                }

                string tree = GetField(fields, TreeField);
                string commit = GetField(fields, CommitField);

                string? tagString = _includeTags ? GetField(fields, TagField) : null;
                GitRevision? tag = tagString != null
                    ? _repo.CreateReferenceFromCompleteSha1(commit).WithContextReference(tagString)
                    : null;

                try
                {
                    commits.Add(new GitLogEntry(
                        _repo.CreateReferenceFromCompleteSha1(commit),
                        parents,
                        tree,
                        AuthorParser.Parse(GetField(fields, AuthorField)),
                        AuthorParser.Parse(GetField(fields, CommitterField)),
                        TryParseDate(fields, AuthorDateField, commit),
                        TryParseDate(fields, CommitterDate, commit),
                        body,
                        files,
                        tag));
                }
                catch (InvalidAuthorException e)
                {
                    throw new RepoException($"Error in commit '{commit}'. Invalid author.", e);
                }
            }
            return commits;
        }

        // Splitter.on(literal) semantics (not regex).
        private static List<string> SplitOn(string input, string separator)
        {
            var result = new List<string>();
            int start = 0;
            while (true)
            {
                int idx = input.IndexOf(separator, start, StringComparison.Ordinal);
                if (idx < 0)
                {
                    result.Add(input.Substring(start));
                    break;
                }
                result.Add(input.Substring(start, idx - start));
                start = idx + separator.Length;
            }
            return result;
        }

        private static DateTimeOffset TryParseDate(
            IReadOnlyDictionary<string, string> fields, string dateField, string commit)
        {
            string value = GetField(fields, dateField);
            if (DateTimeOffset.TryParse(
                    value,
                    CultureInfo.InvariantCulture,
                    DateTimeStyles.None,
                    out var parsed))
            {
                return parsed;
            }
            return DateTimeOffset.UnixEpoch;
        }

        private static string GetField(IReadOnlyDictionary<string, string> fields, string field) =>
            fields.TryGetValue(field, out var value)
                ? value
                : throw new InvalidOperationException($"{field} not present");

        private static string CreateFormat(bool includeBody, bool includeTags)
        {
            return ("--format="
                    + CommitSeparator
                    + CommitField + "=%H\n"
                    + ParentsField + "=%P\n"
                    + TreeField + "=%T\n"
                    + AuthorField + "=%an <%ae>\n"
                    + AuthorDateField + "=%aI\n"
                    + CommitterField + "=%cn <%ce>\n"
                    + CommitterDate + "=%cI"
                    + (includeTags ? "\n" + TagField + "=%S\n" : "\n")
                    + Group
                    + (includeBody
                        ? BeginBody + "\n" + "%w(0,4,4)%B%w(0,0,0)\n" + EndBody + "\n"
                        : "\n")
                    + Group)
                .Replace("\n", "%n")
                .Replace("", "%x01");
        }
    }

    /// <summary>An object that represents a commit as returned by 'git log'.</summary>
    public sealed record GitLogEntry(
        GitRevision Commit,
        IReadOnlyList<GitRevision> Parents,
        string Tree,
        Author Author,
        Author Committer,
        DateTimeOffset AuthorDate,
        DateTimeOffset CommitDate,
        string? Body,
        ImmutableHashSet<string>? Files,
        GitRevision? Tag);

    public string GitCmd() =>
        "git --git-dir=" + _gitDir + (_workTree != null ? " --work-tree=" + _workTree : "");

    /// <summary>An object capable of performing a 'git tag' operation.</summary>
    public sealed record TagCmd(
        GitRepository Repo, string TagName, string? TagMessage, bool ForceFlag)
    {
        internal static TagCmd Create(GitRepository gitRepository, string tagName) =>
            new(gitRepository, tagName, null, false);

        public TagCmd WithAnnotatedTag(string tagMessage) =>
            new(Repo, TagName, tagMessage, ForceFlag);

        public TagCmd Force(bool force) => new(Repo, TagName, TagMessage, force);

        public void Run()
        {
            var cmd = new List<string> { "tag" };
            if (TagMessage != null)
            {
                cmd.Add("-a");
            }
            cmd.Add(TagName);
            if (TagMessage != null)
            {
                cmd.Add("-m");
                cmd.Add(TagMessage);
            }
            if (ForceFlag)
            {
                cmd.Add("--force");
            }
            Repo.SimpleCommand(cmd);
        }
    }

    /// <summary>Returns the repo's primary branch, e.g. "main".</summary>
    public string GetPrimaryBranch() =>
        SimpleCommand("symbolic-ref", "--short", "HEAD").GetStdout().Trim();

    /// <summary>Returns the primary branch of a remote repo, e.g. "main".</summary>
    public string? GetPrimaryBranch(string uri)
    {
        IReadOnlyDictionary<string, string> refs;
        try
        {
            refs = LsRemote(
                uri, new[] { "HEAD", "main", "master" }, DefaultMaxLogLines, new[] { "--symref" });
        }
        catch (ValidationException e)
        {
            if (e is AccessValidationException)
            {
                throw;
            }
            throw new RepoException("Error parsing primary branch", e);
        }
        foreach (var key in refs.Values)
        {
            Match matcher = DefaultBranchPattern.Match(key);
            if (matcher.Success)
            {
                return matcher.Groups[2].Value;
            }
        }
        // Repo has no HEAD, try to guess by testing which branches exist.
        if (refs.ContainsKey("refs/heads/main") && !refs.ContainsKey("refs/heads/master"))
        {
            return "main";
        }
        if (refs.ContainsKey("refs/heads/master") && !refs.ContainsKey("refs/heads/main"))
        {
            return "master";
        }
        return null;
    }

    public string GetCurrentBranch()
    {
        try
        {
            string rev = SimpleCommand("symbolic-ref", "--short", "HEAD").GetStdout().Trim();
            return rev == "HEAD" ? "" : rev;
        }
        catch (RepoException re)
        {
            if (re.Message.Contains("ref HEAD is not a symbolic ref"))
            {
                return "";
            }
            throw;
        }
    }

    /// <summary>Interface for validating git options and providing useful error messages.</summary>
    public interface IOptionsValidator
    {
        void Validate(IReadOnlyList<string> options);
    }

    /// <summary>A validator that validates push options against an allowlist.</summary>
    public sealed record PushOptionsValidator(ImmutableArray<string>? AllowedOptions)
        : IOptionsValidator
    {
        public void Validate(IReadOnlyList<string> options)
        {
            if (AllowedOptions == null)
            {
                return;
            }
            var allowedKeys = AllowedOptions.Value.ToImmutableHashSet();
            var invalid = options.Where(o => !IsAllowedOption(o, allowedKeys)).ToList();
            if (invalid.Count != 0)
            {
                throw new ValidationException(
                    "Push options have failed validation. The allowed push options are ["
                        + string.Join(", ", allowedKeys)
                        + "], but found push options not on the allowlist: ["
                        + string.Join(", ", invalid) + "]");
            }
        }

        private static bool IsAllowedOption(string option, IReadOnlySet<string> allowedKeys)
        {
            int idx = option.IndexOf('=');
            if (idx >= 0)
            {
                return allowedKeys.Contains(option.Substring(0, idx));
            }
            return allowedKeys.Contains(option);
        }
    }
}

/// <summary>Helpers for <see cref="GitRepository.StatusCode"/>.</summary>
internal static class StatusCodeMethods
{
    public static char GetCode(this GitRepository.StatusCode code) =>
        code switch
        {
            GitRepository.StatusCode.Unmodified => ' ',
            GitRepository.StatusCode.Modified => 'M',
            GitRepository.StatusCode.Added => 'A',
            GitRepository.StatusCode.Deleted => 'D',
            GitRepository.StatusCode.Renamed => 'R',
            GitRepository.StatusCode.Copied => 'C',
            GitRepository.StatusCode.UpdatedButUnmerged => 'U',
            GitRepository.StatusCode.Untracked => '?',
            GitRepository.StatusCode.Ignored => '!',
            GitRepository.StatusCode.ChangeType => 'T',
            _ => throw new ArgumentOutOfRangeException(nameof(code)),
        };
}

/// <summary>A small insertion-ordered set. Mirrors Java's LinkedHashSet usage.</summary>
internal sealed class LinkedHashSet<T> : System.Collections.Generic.IEnumerable<T>
{
    private readonly List<T> _order = new();
    private readonly HashSet<T> _set = new();

    public bool Add(T item)
    {
        if (_set.Add(item))
        {
            _order.Add(item);
            return true;
        }
        return false;
    }

    public ImmutableHashSet<T> ToImmutableHashSet() => _order.ToImmutableHashSet();

    public System.Collections.Generic.IEnumerator<T> GetEnumerator() => _order.GetEnumerator();

    System.Collections.IEnumerator System.Collections.IEnumerable.GetEnumerator() =>
        GetEnumerator();
}
