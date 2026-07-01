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
using System.Text.RegularExpressions;
using Copybara.Action;
using Copybara.Approval;
using Copybara.Checks;
using Copybara.Common;
using Copybara.Config;
using Copybara.Credentials;
using Copybara.Exceptions;
using Copybara.Git.GerritApi;
using Copybara.Git.GitHub.Api;
using Copybara.Git.GitLab;
using Copybara.Git.GitLab.Api.Entities;
using Copybara.Transform;
using Copybara.Transform.Patch;
using Copybara.Version;
using Starlark.Annot;
using Starlark.Eval;
using Starlark.Syntax;

// Domain 'Console' collides with System.Console.
using Console = Copybara.Util.Console.Console;

// Static 'Starlark' helper collides with the root namespace segment.
using StarlarkRt = Starlark.Eval.Starlark;

// Java's net.starlark.java.eval.Sequence<?> maps to the concrete StarlarkList in this port.
using StarlarkSequence = Starlark.Eval.StarlarkList;

// GitHubHost / GitHubUtil live in the github.util package upstream.
// TODO(port): reconcile — GitHubHost / GitHubUtil are being ported concurrently by peers.
using GitHubHost = Copybara.Git.GitHub.Util.GitHubHost;
using GitHubUtil = Copybara.Git.GitHub.Util.GitHubUtil;

namespace Copybara.Git;

/// <summary>
/// Main module that groups all the functions that create Git origins and destinations.
///
/// <para>This is a faithful port of <c>com.google.copybara.git.GitModule</c>. Many of the concrete
/// provider types (GitHubPrOrigin, GitHubPrDestination, GerritOrigin, GerritDestination,
/// GitLabMr*, GitHubEndPoint, GerritEndpoint, GitHubTrigger, GerritTrigger, approvals providers,
/// GitHubHost/GitHubUtil, etc.) are being ported concurrently by peers. Where a peer signature is
/// still uncertain, the call is made "as the Java does" and marked with <c>// TODO(port):
/// reconcile</c> for the consolidation pass.</para>
/// </summary>
[StarlarkBuiltin("git", Doc = "Set of functions to define Git origins and destinations.")]
public class GitModule : ILabelsAwareModule, IStarlarkValue
{
    internal const string DefaultIntegrateLabel = "COPYBARA_INTEGRATE_REVIEW";

    public const string CredentialDoc =
        "EXPERIMENTAL: Read credentials from config file to access the Git Repo. This expects a"
        + " 'credentials.username_password' specifying the username to use for the remote git"
        + " host and a password or token. This is gated by the '"
        + GitOptions.UseCredentialsFromConfig
        + "' flag";

    private const string ExperimentalPrefix = "**EXPERIMENTAL feature** ";

    public const string GitLabCredentialDoc =
        "Read credentials from config file to access the GitLab Repo. This expects a"
        + " `credentials.username_password` specifying the username to use for the remote GitLab"
        + " host and a password or token.";

    private const string GerritTrigger = "gerrit_trigger";
    private const string GerritApiName = "gerrit_api";
    private const string GitHubTrigger = "github_trigger";
    private const string GitHubApiName = "github_api";
    private const string PatchField = "patch";

    private const string PatchFieldDesc =
        "Patch the checkout dir. The difference with `patch.apply` transformation is"
        + " that here we can apply it using three-way";

    private const string DescribeVersionFieldDoc =
        "Download tags and use 'git describe' to create four labels with a meaningful version"
        + " identifier:<br><br>  - `GIT_DESCRIBE_CHANGE_VERSION`: The version for the change or"
        + " changes being migrated. The value changes per change in `ITERATIVE` mode and will be"
        + " the latest migrated change in `SQUASH` (In other words, doesn't include excluded"
        + " changes). this is normally what users want to use.<br> -"
        + " `GIT_DESCRIBE_REQUESTED_VERSION`: `git describe` for the requested/head version."
        + " Constant in `ITERATIVE` mode and includes filtered changes.<br> "
        + " -`GIT_DESCRIBE_FIRST_PARENT`: `git describe` for the first parent version.<br> "
        + " -`GIT_SEQUENTIAL_REVISION_NUMBER`: The sequential number of the commit. Falls back to"
        + " the SHA1 if not applicable.<br>";

    /// <summary>Primary branch name that will be ignored if autodetect is enabled.</summary>
    public static readonly IReadOnlySet<string> PrimaryBranches =
        ImmutableHashSet.Create("master", "main");

    protected readonly Options Options;
    protected ConfigFile MainConfigFile = null!;
    private string? _workflowName;
    private StarlarkThread.PrintHandler? _printHandler;

    private readonly StarlarkSequence _defaultGitIntegrate;

    public GitModule(Options options)
    {
        Options = Preconditions.CheckNotNull(options);
        bool failIfCommonBaselineNotFound =
            options
                .Get<GeneralOptions>()
                .IsTemporaryFeature("GIT_INTEGRATE_FAIL_IF_COMMON_BASELINE_NOT_FOUND", false);
        bool ignoreErrors = !failIfCommonBaselineNotFound;
        _defaultGitIntegrate =
            StarlarkList.ImmutableCopyOf(
                new object?[]
                {
                    new GitIntegrateChanges(
                        DefaultIntegrateLabel,
                        GitIntegrateChanges.Strategy.FakeMergeAndIncludeFiles,
                        ignoreErrors),
                });
    }

    [StarlarkMethod("origin",
        Doc =
            "Defines a standard Git origin. For Git specific origins use: `github_origin` or "
            + "`gerrit_origin`.<br><br>All the origins in this module accept several string"
            + " formats as reference (When copybara is called in the form of `copybara config"
            + " workflow reference`):<br><ul><li>**Branch name:** For example"
            + " `master`</li><li>**An arbitrary reference:**"
            + " `refs/changes/20/50820/1`</li><li>**A SHA-1:** Note that it has to be reachable"
            + " from the default refspec</li><li>**A Git repository URL and reference:**"
            + " `http://github.com/foo master`</li><li>**A GitHub pull request URL:**"
            + " `https://github.com/some_project/pull/1784`</li></ul>",
        UseStarlarkThread = true)]
    public GitOrigin Origin(
        [Param(Name = "url", Named = true, Doc = "Indicates the URL of the git repository")]
        string url,
        [Param(Name = "ref", Named = true, DefaultValue = "None",
            Doc = "Represents the default reference that will be used for reading the revision.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object @ref,
        [Param(Name = "submodules", Named = true, Positional = false, DefaultValue = "'NO'",
            Doc = "Download submodules. Valid values: NO, YES, RECURSIVE.")]
        string submodules,
        [Param(Name = "excluded_submodules", Named = true, Positional = false, DefaultValue = "[]",
            Doc = "A list of names of submodules that will not be downloaded.",
            AllowedTypes = new[] { typeof(StarlarkSequence) })]
        object excludedSubmodules,
        [Param(Name = "include_branch_commit_logs", Named = true, Positional = false, DefaultValue = "False",
            Doc = "Whether to include raw logs of branch commits in the migrated change message.")]
        bool includeBranchCommitLogs,
        [Param(Name = "first_parent", Named = true, Positional = false, DefaultValue = "True",
            Doc = "If true, it only uses the first parent when looking for changes.")]
        bool firstParent,
        [Param(Name = "partial_fetch", Named = true, Positional = false, DefaultValue = "False",
            Doc = "If true, partially fetch git repository by only fetching affected files.")]
        bool partialFetch,
        [Param(Name = PatchField, Named = true, Positional = false, DefaultValue = "None",
            Doc = PatchFieldDesc,
            AllowedTypes = new[] { typeof(ITransformation), typeof(NoneType) })]
        object patch,
        [Param(Name = "describe_version", Named = true, Positional = false, DefaultValue = "None",
            Doc = DescribeVersionFieldDoc,
            AllowedTypes = new[] { typeof(bool), typeof(NoneType) })]
        object describeVersion,
        [Param(Name = "version_selector", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Select a custom version (tag) to migrate instead of 'ref'.",
            AllowedTypes = new[] { typeof(IVersionSelector), typeof(NoneType) })]
        object versionSelector,
        [Param(Name = "primary_branch_migration", Named = true, Positional = false, DefaultValue = "False",
            Doc = "When enabled, copybara will ignore the 'ref' param if it is 'master' or 'main'.",
            AllowedTypes = new[] { typeof(bool) })]
        bool primaryBranchMigration,
        [Param(Name = "credentials", Named = true, Positional = false, DefaultValue = "None",
            Doc = CredentialDoc,
            AllowedTypes = new[] { typeof(UsernamePasswordIssuer), typeof(NoneType) })]
        object? credentials,
        [Param(Name = "repo_id", Named = true, Positional = false, DefaultValue = "None",
            Doc = "(Experimental) The repo id of the git repository.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object? repoId,
        StarlarkThread thread)
    {
        SkylarkUtil.CheckNotEmpty(url, "url");
        var patchTransformation = MaybeGetPatchTransformation(patch);

        if (!ReferenceEquals(versionSelector, StarlarkRt.None))
        {
            SkylarkUtil.Check(
                ReferenceEquals(@ref, StarlarkRt.None),
                "Cannot use ref field and version_selector. Version selector will decide the ref"
                    + " to migrate");
        }

        var excludedSubmoduleList = SkylarkUtil.ConvertStringList(excludedSubmodules, "excluded_submodules");
        CheckSubmoduleConfig(submodules, excludedSubmoduleList);
        string fixedUrl = FixHttp(url, thread.GetCallerLocation());
        var credentialHandler = GetCredentialHandler(fixedUrl, credentials);
        var gitRepositoryHook =
            MaybeGetGitRepositoryHook(
                new IGitRepositoryHook.GitRepositoryData(
                    SkylarkUtil.ConvertFromNoneable<string?>(repoId, null), fixedUrl));
        var gitHubHost = new GitHubHost("github.com");

        // TODO(port): reconcile — GitOrigin.NewGitOrigin static factory is provided by a peer.
        return NewGitOrigin(
            Options,
            fixedUrl,
            SkylarkUtil.ConvertOptionalString(@ref),
            GitRepoType.Git,
            SkylarkUtil.StringToEnum<GitOrigin.SubmoduleStrategy>("submodules", submodules),
            excludedSubmoduleList,
            includeBranchCommitLogs,
            firstParent,
            partialFetch,
            primaryBranchMigration,
            patchTransformation,
            ConvertDescribeVersion(describeVersion),
            ValidateVersionSelector(versionSelector),
            MainConfigFile.Path(),
            _workflowName,
            gitHubHost.IsGitHubUrl(url)
                ? GitHubPostSubmitApprovalsProvider(
                    fixedUrl, SkylarkUtil.ConvertOptionalString(@ref), credentialHandler)
                : ApprovalsProvider(url),
            enableLfs: false,
            credentialHandler,
            gitRepositoryHook);
    }

    // Port of GitOrigin.newGitOrigin static factory (kept here because the peer GitOrigin exposes
    // only its internal constructor).
    private GitOrigin NewGitOrigin(
        Options options,
        string url,
        string? @ref,
        GitRepoType type,
        GitOrigin.SubmoduleStrategy submoduleStrategy,
        IReadOnlyList<string> excludedSubmodules,
        bool includeBranchCommitLogs,
        bool firstParent,
        bool partialClone,
        bool primaryBranchMigrationMode,
        ITransformation? patchTransformation,
        bool describeVersion,
        IVersionSelector? versionSelector,
        string? configPath,
        string? workflowName,
        IApprovalsProvider approvalsProvider,
        bool enableLfs,
        CredentialFileHandler? credentials,
        IGitRepositoryHook? gitRepositoryHook) =>
        new GitOrigin(
            options.Get<GeneralOptions>(),
            url,
            @ref,
            type,
            options.Get<GitOptions>(),
            options.Get<GitOriginOptions>(),
            submoduleStrategy,
            excludedSubmodules,
            includeBranchCommitLogs,
            firstParent,
            partialClone,
            patchTransformation,
            describeVersion,
            versionSelector,
            configPath,
            workflowName,
            primaryBranchMigrationMode,
            approvalsProvider,
            enableLfs,
            credentials,
            gitRepositoryHook);

    private IVersionSelector? ValidateVersionSelector(object versionSelector)
    {
        var selector = SkylarkUtil.ConvertFromNoneable<IVersionSelector?>(versionSelector, null);
        if (selector == null)
        {
            return null;
        }

        foreach (var searchPattern in selector.SearchPatterns())
        {
            if (searchPattern.IsNone() || searchPattern.IsAll())
            {
                continue;
            }

            SkylarkUtil.Check(
                searchPattern.Tokens()[0].GetValue().StartsWith("refs/"),
                "Git version selector matches complete references (e.g. 'refs/tags/${{n}})'. The"
                    + " version selector provided doesn't start with the 'refs/' prefix: {0}",
                selector);
        }

        return selector;
    }

    private ITransformation? MaybeGetPatchTransformation(object patch)
    {
        if (StarlarkRt.IsNullOrNone(patch))
        {
            return null;
        }

        SkylarkUtil.Check(
            patch is PatchTransformation,
            "'{0}' is not a patch.apply(...) transformation",
            PatchField);
        return (PatchTransformation)patch;
    }

    [StarlarkMethod("integrate",
        Doc = "Integrate changes from a url present in the migrated change label.")]
    public GitIntegrateChanges Integrate(
        [Param(Name = "label", Named = true,
            Doc = "The migration label that will contain the url to the change to integrate.",
            DefaultValue = "\"" + DefaultIntegrateLabel + "\"")]
        string label,
        [Param(Name = "strategy", Named = true, DefaultValue = "\"FAKE_MERGE_AND_INCLUDE_FILES\"",
            Doc = "How to integrate the change.")]
        string strategy,
        [Param(Name = "ignore_errors", Named = true, DefaultValue = "True",
            Doc = "If we should ignore integrate errors and continue the migration.")]
        bool ignoreErrors) =>
        new(label, GitIntegrateChanges.Strategy.ValueOf(strategy), ignoreErrors);

    [StarlarkMethod("mirror",
        Doc = "Mirror git references between repositories",
        UseStarlarkThread = true)]
    public NoneType Mirror(
        [Param(Name = "name", Named = true, Doc = "Migration name")]
        string name,
        [Param(Name = "origin", Named = true, Doc = "Indicates the URL of the origin git repository")]
        string origin,
        [Param(Name = "destination", Named = true, Doc = "Indicates the URL of the destination git repository")]
        string destination,
        [Param(Name = "refspecs", Named = true, DefaultValue = "['refs/heads/*']",
            Doc = "Represents a list of git refspecs to mirror between origin and destination.",
            AllowedTypes = new[] { typeof(StarlarkSequence) })]
        StarlarkSequence strRefSpecs,
        [Param(Name = "prune", Named = true, DefaultValue = "False",
            Doc = "Remove remote refs that don't have a origin counterpart.")]
        bool prune,
        [Param(Name = "partial_fetch", Named = true, Positional = false, DefaultValue = "False",
            Doc = "This is an experimental feature that only works for certain origin globs.")]
        bool partialFetch,
        [Param(Name = "description", Named = true, Positional = false, DefaultValue = "None",
            Doc = "A description of what this migration achieves",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object description,
        [Param(Name = "action", Named = true, Positional = false, DefaultValue = "None",
            Doc = "An action to execute when the migration is triggered.")]
        object rawAction,
        [Param(Name = "origin_checker", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Checker for applicable gerrit or github apis that can be inferred from the origin url.",
            AllowedTypes = new[] { typeof(IChecker), typeof(NoneType) })]
        object rawOriginChecker,
        [Param(Name = "destination_checker", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Checker for applicable gerrit or github apis inferred from the destination url.",
            AllowedTypes = new[] { typeof(IChecker), typeof(NoneType) })]
        object rawDestinationChecker,
        [Param(Name = "origin_credentials", Named = true, Positional = false, DefaultValue = "None",
            Doc = CredentialDoc,
            AllowedTypes = new[] { typeof(UsernamePasswordIssuer), typeof(NoneType) })]
        object? originCreds,
        [Param(Name = "destination_credentials", Named = true, Positional = false, DefaultValue = "None",
            Doc = CredentialDoc,
            AllowedTypes = new[] { typeof(UsernamePasswordIssuer), typeof(NoneType) })]
        object? destinationCreds,
        StarlarkThread thread)
    {
        var generalOptions = Options.Get<GeneralOptions>();
        var gitOptions = Options.Get<GitOptions>();
        var refspecs = new List<Refspec>();

        foreach (var refspec in SkylarkUtil.ConvertStringList(strRefSpecs, "refspecs"))
        {
            try
            {
                refspecs.Add(
                    Refspec.Create(
                        gitOptions.GetGitEnvironment(generalOptions.GetEnvironment()),
                        generalOptions.GetCwd(),
                        refspec));
            }
            catch (InvalidRefspecException e)
            {
                throw StarlarkRt.Errorf("{0}", e.Message);
            }
        }

        string fixedOriginHttp = FixHttp(origin, thread.GetCallerLocation());
        string fixedDestinationHttp = FixHttp(destination, thread.GetCallerLocation());

        var originCredential = GetCredentialHandler(fixedOriginHttp, originCreds);
        var destinationCredential = GetCredentialHandler(fixedDestinationHttp, destinationCreds);

        var creds =
            new[] { originCredential, destinationCredential }
                .Where(c => c != null)
                .Select(c => c!)
                .ToImmutableArray();

        var originChecker = SkylarkUtil.ConvertFromNoneable<IChecker?>(rawOriginChecker, null);
        var destinationChecker = SkylarkUtil.ConvertFromNoneable<IChecker?>(rawDestinationChecker, null);
        var action = !ReferenceEquals(rawAction, StarlarkRt.None)
            ? MaybeWrapAction(_printHandler, rawAction)
            : null;
        var module = Module.OfInnermostEnclosingStarlarkFunction(thread)!;

        // TODO(port): reconcile — Mirror constructor / GlobalMigrations argument shapes.
        GlobalMigrations.GetGlobalMigrations(module)
            .AddMigration(
                name,
                new Mirror(
                    generalOptions,
                    gitOptions,
                    name,
                    fixedOriginHttp,
                    fixedDestinationHttp,
                    refspecs,
                    Options.Get<GitDestinationOptions>(),
                    prune,
                    partialFetch,
                    MainConfigFile,
                    SkylarkUtil.ConvertFromNoneable<string?>(description, null),
                    action,
                    GetEndpointProvider(fixedOriginHttp, originChecker, originCredential, false, thread),
                    GetEndpointProvider(
                        fixedDestinationHttp, destinationChecker, destinationCredential, false, thread),
                    creds,
                    thread.GetCallStack()));
        return StarlarkRt.None;
    }

    private static IAction MaybeWrapAction(StarlarkThread.PrintHandler? printHandler, object action)
    {
        if (action is IStarlarkCallable callable)
        {
            return new StarlarkAction(callable.Name, callable, Dict.Empty(), printHandler);
        }

        if (action is IAction a)
        {
            return a;
        }

        throw StarlarkRt.Errorf("Invalid feedback action '{0}' of type: {1}", action, action.GetType());
    }

    [StarlarkMethod("gerrit_origin",
        Doc = "Defines a Git origin for Gerrit reviews.",
        UseStarlarkThread = true)]
    public GitOrigin GerritOrigin(
        [Param(Name = "url", Named = true, Doc = "Indicates the URL of the git repository")]
        string url,
        [Param(Name = "ref", Named = true, DefaultValue = "None",
            Doc = "DEPRECATED. Use git.origin for submitted branches.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object @ref,
        [Param(Name = "submodules", Named = true, DefaultValue = "'NO'",
            Doc = "Download submodules. Valid values: NO, YES, RECURSIVE.")]
        string submodules,
        [Param(Name = "excluded_submodules", Named = true, Positional = false, DefaultValue = "[]",
            Doc = "A list of names of submodules that will not be downloaded.",
            AllowedTypes = new[] { typeof(StarlarkSequence) })]
        object excludedSubmodules,
        [Param(Name = "first_parent", Named = true, Positional = false, DefaultValue = "True",
            Doc = "If true, it only uses the first parent when looking for changes.")]
        bool firstParent,
        [Param(Name = "partial_fetch", Named = true, Positional = false, DefaultValue = "False",
            Doc = "If true, partially fetch git repository by only fetching affected files.")]
        bool partialFetch,
        [Param(Name = "api_checker", Named = true, Positional = false, DefaultValue = "None",
            Doc = "A checker for the Gerrit API endpoint provided for after_migration hooks.",
            AllowedTypes = new[] { typeof(IChecker), typeof(NoneType) })]
        object checkerObj,
        [Param(Name = PatchField, Named = true, Positional = false, DefaultValue = "None",
            Doc = PatchFieldDesc,
            AllowedTypes = new[] { typeof(ITransformation), typeof(NoneType) })]
        object patch,
        [Param(Name = "branch", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Limit the import to changes that are for this branch.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object branch,
        [Param(Name = "describe_version", Named = true, Positional = false, DefaultValue = "None",
            Doc = DescribeVersionFieldDoc,
            AllowedTypes = new[] { typeof(bool), typeof(NoneType) })]
        object describeVersion,
        [Param(Name = "ignore_gerrit_noop", Named = true, Positional = false, DefaultValue = "False",
            Doc = "Option to not migrate Gerrit changes that do not change origin_files")]
        bool ignoreGerritNoop,
        [Param(Name = "primary_branch_migration", Named = true, Positional = false, DefaultValue = "False",
            Doc = "When enabled, copybara will ignore the 'ref' param if it is 'master' or 'main'.",
            AllowedTypes = new[] { typeof(bool) })]
        bool primaryBranchMigration,
        [Param(Name = "import_wip_changes", Named = true, Positional = false, DefaultValue = "True",
            Doc = "When set to true, Copybara will migrate changes marked as Work in Progress (WIP).",
            AllowedTypes = new[] { typeof(bool) })]
        bool importWipChanges,
        StarlarkThread thread)
    {
        SkylarkUtil.CheckNotEmpty(url, "url");
        url = FixHttp(url, thread.GetCallerLocation());
        string? refField = SkylarkUtil.ConvertOptionalString(@ref);

        var patchTransformation = MaybeGetPatchTransformation(patch);

        var excludedSubmoduleList = SkylarkUtil.ConvertStringList(excludedSubmodules, "excluded_submodules");
        CheckSubmoduleConfig(submodules, excludedSubmoduleList);

        if (!string.IsNullOrEmpty(refField))
        {
            GetGeneralConsole()
                .Warn(
                    "'ref' field detected in configuration with value '"
                        + refField
                        + "'. git.gerrit_origin"
                        + " is deprecating its usage for submitted changes. Use git.origin instead.");
            // TODO(port): reconcile — GitOrigin.NewGitOrigin static factory is provided by a peer.
            return NewGitOrigin(
                Options,
                url,
                refField,
                GitRepoType.Gerrit,
                SkylarkUtil.StringToEnum<GitOrigin.SubmoduleStrategy>("submodules", submodules),
                excludedSubmoduleList,
                includeBranchCommitLogs: false,
                firstParent,
                partialFetch,
                primaryBranchMigration,
                patchTransformation,
                ConvertDescribeVersion(describeVersion),
                versionSelector: null,
                MainConfigFile.Path(),
                _workflowName,
                ApprovalsProvider(url),
                enableLfs: false,
                credentials: null,
                gitRepositoryHook: null);
        }

        return global::Copybara.Git.GerritOrigin.NewGerritOrigin(
            Options.Get<GeneralOptions>(),
            Options.Get<GitOptions>(),
            Options.Get<GitOriginOptions>(),
            Options.Get<GerritOptions>(),
            Options.Get<GitDestinationOptions>(),
            url,
            SkylarkUtil.StringToEnum<GitOrigin.SubmoduleStrategy>("submodules", submodules),
            excludedSubmoduleList,
            firstParent,
            partialFetch,
            SkylarkUtil.ConvertFromNoneable<IChecker?>(checkerObj, null),
            patchTransformation,
            SkylarkUtil.ConvertFromNoneable<string?>(branch, null),
            ConvertDescribeVersion(describeVersion),
            ignoreGerritNoop,
            primaryBranchMigration,
            ApprovalsProvider(url),
            importWipChanges,
            gitRepositoryHook: null);
    }

    internal const string GitHubPrOriginName = "github_pr_origin";

    [StarlarkMethod(GitHubPrOriginName,
        Doc = "Defines a Git origin for Github pull requests.",
        UseStarlarkThread = true)]
    public IOrigin<GitRevision> GithubPrOrigin(
        [Param(Name = "url", Named = true, Doc = "Indicates the URL of the GitHub repository")]
        string url,
        [Param(Name = "use_merge", Named = true, Positional = false, DefaultValue = "False",
            Doc = "If the content for refs/pull/<ID>/merge should be used instead of the PR head.")]
        bool merge,
        [Param(Name = GitHubUtil.RequiredLabels, Named = true, Positional = false, DefaultValue = "[]",
            Doc = "Required labels to import the PR.",
            AllowedTypes = new[] { typeof(StarlarkSequence) })]
        StarlarkSequence requiredLabels,
        [Param(Name = GitHubUtil.RequiredStatusContextNames, Named = true, Positional = false, DefaultValue = "[]",
            Doc = "A list of names of services which must all mark the PR with 'success'.",
            AllowedTypes = new[] { typeof(StarlarkSequence) })]
        StarlarkSequence requiredStatusContextNames,
        [Param(Name = GitHubUtil.RequiredCheckRuns, Named = true, Positional = false, DefaultValue = "[]",
            Doc = "A list of check runs which must all have a value of 'success'.",
            AllowedTypes = new[] { typeof(StarlarkSequence) })]
        StarlarkSequence requiredCheckRuns,
        [Param(Name = GitHubUtil.RetryableLabels, Named = true, Positional = false, DefaultValue = "[]",
            Doc = "Required labels to import the PR that should be retried.",
            AllowedTypes = new[] { typeof(StarlarkSequence) })]
        StarlarkSequence retryableLabels,
        [Param(Name = "submodules", Named = true, Positional = false, DefaultValue = "'NO'",
            Doc = "Download submodules. Valid values: NO, YES, RECURSIVE.")]
        string submodules,
        [Param(Name = "excluded_submodules", Named = true, Positional = false, DefaultValue = "[]",
            Doc = "A list of names of submodules that will not be downloaded.",
            AllowedTypes = new[] { typeof(StarlarkSequence) })]
        object excludedSubmodules,
        [Param(Name = "baseline_from_branch", Named = true, Positional = false, DefaultValue = "False",
            Doc = "Use this field only for github -> git CHANGE_REQUEST workflows.")]
        bool baselineFromBranch,
        [Param(Name = "first_parent", Named = true, Positional = false, DefaultValue = "True",
            Doc = "If true, it only uses the first parent when looking for changes.")]
        bool firstParent,
        [Param(Name = "partial_fetch", Named = true, Positional = false, DefaultValue = "False",
            Doc = "This is an experimental feature that only works for certain origin globs.")]
        bool partialClone,
        [Param(Name = "state", Named = true, Positional = false, DefaultValue = "'OPEN'",
            Doc = "Only migrate Pull Request with that state. Values: 'OPEN', 'CLOSED', 'ALL'.")]
        string state,
        [Param(Name = "review_state", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Required state of the reviews associated with the Pull Request.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object reviewStateParam,
        [Param(Name = "review_approvers", Named = true, Positional = false, DefaultValue = "None",
            Doc = "The set of reviewer types that are considered for approvals.",
            AllowedTypes = new[] { typeof(StarlarkSequence), typeof(NoneType) })]
        object reviewApproversParam,
        [Param(Name = "api_checker", Named = true, Positional = false, DefaultValue = "None",
            Doc = "A checker for the GitHub API endpoint provided for after_migration hooks.",
            AllowedTypes = new[] { typeof(IChecker), typeof(NoneType) })]
        object checkerObj,
        [Param(Name = PatchField, Named = true, Positional = false, DefaultValue = "None",
            Doc = PatchFieldDesc,
            AllowedTypes = new[] { typeof(ITransformation), typeof(NoneType) })]
        object patch,
        [Param(Name = "branch", Named = true, Positional = false, DefaultValue = "None",
            Doc = "If set, it will only migrate pull requests for this base branch",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object branch,
        [Param(Name = "describe_version", Named = true, Positional = false, DefaultValue = "None",
            Doc = DescribeVersionFieldDoc,
            AllowedTypes = new[] { typeof(bool), typeof(NoneType) })]
        object describeVersion,
        [Param(Name = "credentials", Named = true, Positional = false, DefaultValue = "None",
            Doc = CredentialDoc,
            AllowedTypes = new[] { typeof(UsernamePasswordIssuer), typeof(NoneType) })]
        object? credentials,
        [Param(Name = "github_host_name", Named = true, Positional = false, DefaultValue = "'github.com'",
            Doc = "The host name of the GitHub repository, used to construct the URL.")]
        string gitHubHostName,
        StarlarkThread thread)
    {
        SkylarkUtil.CheckNotEmpty(url, "url");
        var gitHubHost = new GitHubHost(gitHubHostName);
        SkylarkUtil.Check(gitHubHost.IsGitHubUrl(url), "Invalid GitHub URL: {0}", url);
        var patchTransformation = MaybeGetPatchTransformation(patch);

        var excludedSubmoduleList = SkylarkUtil.ConvertStringList(excludedSubmodules, "excluded_submodules");
        CheckSubmoduleConfig(submodules, excludedSubmoduleList);

        string? reviewStateString = SkylarkUtil.ConvertFromNoneable<string?>(reviewStateParam, null);
        var reviewApproversStrings =
            SkylarkUtil.ConvertFromNoneable<StarlarkSequence?>(reviewApproversParam, null);

        // TODO(port): reconcile — GitHubPrOrigin.ReviewState / StateFilter enums provided by peer.
        GitHubPrOrigin.ReviewState? reviewState;
        ImmutableHashSet<AuthorAssociation> reviewApprovers;
        if (reviewStateString == null)
        {
            reviewState = null;
            SkylarkUtil.Check(
                reviewApproversStrings == null,
                "'review_approvers' cannot be set if `review_state` is not set");
            reviewApprovers = ImmutableHashSet<AuthorAssociation>.Empty;
        }
        else
        {
            reviewState = Enum.Parse<GitHubPrOrigin.ReviewState>(reviewStateString);
            reviewApproversStrings ??=
                StarlarkList.ImmutableCopyOf(new object?[] { "COLLABORATOR", "MEMBER", "OWNER" });
            var approvers = new HashSet<AuthorAssociation>();
            foreach (var r in reviewApproversStrings)
            {
                bool added = approvers.Add(SkylarkUtil.StringToEnum<AuthorAssociation>("review_approvers", (string)r!));
                SkylarkUtil.Check(added, "Repeated element {0}", r!);
            }

            reviewApprovers = approvers.ToImmutableHashSet();
        }

        string fixedUrl = FixHttp(url, thread.GetCallerLocation());
        var prOpts = Options.Get<GitHubPrOriginOptions>();
        var credHandler = GetCredentialHandler(fixedUrl, credentials);
        if (prOpts.Repo != null)
        {
            var split = prOpts.Repo.Split(' ');
            string repo = split[0];
            string prRef = split.Length > 1 ? split[1] : "main";
            // TODO(port): reconcile — GitOrigin full constructor argument shape provided by peer.
            return new GitOrigin(
                Options.Get<GeneralOptions>(),
                repo,
                prRef,
                GitRepoType.Git,
                Options.Get<GitOptions>(),
                Options.Get<GitOriginOptions>(),
                SkylarkUtil.StringToEnum<GitOrigin.SubmoduleStrategy>("submodules", submodules),
                excludedSubmoduleList,
                false,
                firstParent,
                partialClone,
                patchTransformation,
                ConvertDescribeVersion(describeVersion),
                null,
                MainConfigFile.Path(),
                _workflowName,
                false,
                GitHubPostSubmitApprovalsProvider(fixedUrl, prRef, credHandler),
                enableLfs: false,
                credHandler,
                null);
        }

        // TODO(port): reconcile — GitHubPrOrigin constructor argument shape provided by peer.
        return new GitHubPrOrigin(
            fixedUrl,
            prOpts.OverrideMerge ?? merge,
            Options.Get<GeneralOptions>(),
            Options.Get<GitOptions>(),
            Options.Get<GitOriginOptions>(),
            Options.Get<GitHubOptions>(),
            prOpts,
            SkylarkUtil.ConvertStringList(requiredLabels, GitHubUtil.RequiredLabels).ToImmutableHashSet(),
            SkylarkUtil.ConvertStringList(requiredStatusContextNames, GitHubUtil.RequiredStatusContextNames)
                .ToImmutableHashSet(),
            SkylarkUtil.ConvertStringList(requiredCheckRuns, GitHubUtil.RequiredCheckRuns).ToImmutableHashSet(),
            SkylarkUtil.ConvertStringList(retryableLabels, GitHubUtil.RetryableLabels).ToImmutableHashSet(),
            SkylarkUtil.StringToEnum<GitOrigin.SubmoduleStrategy>("submodules", submodules),
            excludedSubmoduleList,
            baselineFromBranch,
            firstParent,
            partialClone,
            SkylarkUtil.StringToEnum<GitHubPrOrigin.StateFilter>("state", state),
            reviewState,
            reviewApprovers,
            SkylarkUtil.ConvertFromNoneable<IChecker?>(checkerObj, null),
            patchTransformation,
            SkylarkUtil.ConvertFromNoneable<string?>(branch, null),
            ConvertDescribeVersion(describeVersion),
            gitHubHost,
            GitHubPreSubmitApprovalsProvider(fixedUrl, credHandler),
            credHandler,
            gitRepositoryHook: null);
    }

    [StarlarkMethod("github_origin",
        Doc = "Defines a Git origin for a Github repository.",
        UseStarlarkThread = true)]
    public GitOrigin GithubOrigin(
        [Param(Name = "url", Named = true, Doc = "Indicates the URL of the git repository")]
        string url,
        [Param(Name = "ref", Named = true, DefaultValue = "None",
            Doc = "Represents the default reference that will be used for reading the revision.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object @ref,
        [Param(Name = "submodules", Named = true, DefaultValue = "'NO'",
            Doc = "Download submodules. Valid values: NO, YES, RECURSIVE.")]
        string submodules,
        [Param(Name = "excluded_submodules", Named = true, Positional = false, DefaultValue = "[]",
            Doc = "A list of names of submodules that will not be downloaded.",
            AllowedTypes = new[] { typeof(StarlarkSequence) })]
        object excludedSubmodules,
        [Param(Name = "first_parent", Named = true, Positional = false, DefaultValue = "True",
            Doc = "If true, it only uses the first parent when looking for changes.")]
        bool firstParent,
        [Param(Name = "partial_fetch", Named = true, Positional = false, DefaultValue = "False",
            Doc = "If true, partially fetch git repository by only fetching affected files.")]
        bool partialFetch,
        [Param(Name = PatchField, Named = true, Positional = false, DefaultValue = "None",
            Doc = PatchFieldDesc,
            AllowedTypes = new[] { typeof(ITransformation), typeof(NoneType) })]
        object patch,
        [Param(Name = "describe_version", Named = true, Positional = false, DefaultValue = "None",
            Doc = DescribeVersionFieldDoc,
            AllowedTypes = new[] { typeof(bool), typeof(NoneType) })]
        object describeVersion,
        [Param(Name = "version_selector", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Select a custom version (tag) to migrate instead of 'ref'.",
            AllowedTypes = new[] { typeof(IVersionSelector), typeof(NoneType) })]
        object versionSelector,
        [Param(Name = "primary_branch_migration", Named = true, Positional = false, DefaultValue = "False",
            Doc = "When enabled, copybara will ignore the 'ref' param if it is 'master' or 'main'.",
            AllowedTypes = new[] { typeof(bool) })]
        bool primaryBranchMigration,
        [Param(Name = "enable_lfs", Named = true, Positional = false, DefaultValue = "False",
            Doc = "If true, Large File Storage support is enabled for the origin.")]
        bool enableLfs,
        [Param(Name = "credentials", Named = true, Positional = false, DefaultValue = "None",
            Doc = CredentialDoc,
            AllowedTypes = new[] { typeof(UsernamePasswordIssuer), typeof(NoneType) })]
        object? credentials,
        [Param(Name = "repo_id", Named = true, Positional = false, DefaultValue = "None",
            Doc = "The repo id of the github repository, used as a stable reference to the repo.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object? repoId,
        [Param(Name = "github_host_name", Named = true, Positional = false, DefaultValue = "'github.com'",
            Doc = ExperimentalPrefix + "The github host name of the repository.")]
        string gitHubHostName,
        StarlarkThread thread)
    {
        var gitHubHost = new GitHubHost(gitHubHostName);
        SkylarkUtil.Check(
            gitHubHost.IsGitHubUrl(SkylarkUtil.CheckNotEmpty(url, "url")), "Invalid GitHub URL: {0}", url);

        if (!ReferenceEquals(versionSelector, StarlarkRt.None))
        {
            SkylarkUtil.Check(
                ReferenceEquals(@ref, StarlarkRt.None),
                "Cannot use ref field and version_selector. Version selector will decide the ref"
                    + " to migrate");
        }

        var excludedSubmoduleList = SkylarkUtil.ConvertStringList(excludedSubmodules, "excluded_submodules");
        CheckSubmoduleConfig(submodules, excludedSubmoduleList);
        string fixedUrl = FixHttp(url, thread.GetCallerLocation());
        var patchTransformation = MaybeGetPatchTransformation(patch);

        var credentialHandler = GetCredentialHandler(fixedUrl, credentials);
        var gitRepositoryHook =
            MaybeGetGitRepositoryHook(
                new IGitRepositoryHook.GitRepositoryData(
                    SkylarkUtil.ConvertFromNoneable<string?>(repoId, null), fixedUrl));

        // TODO(port): reconcile — GitOrigin.NewGitOrigin static factory is provided by a peer.
        return NewGitOrigin(
            Options,
            fixedUrl,
            SkylarkUtil.ConvertOptionalString(@ref),
            GitRepoType.GitHub,
            SkylarkUtil.StringToEnum<GitOrigin.SubmoduleStrategy>("submodules", submodules),
            excludedSubmoduleList,
            includeBranchCommitLogs: false,
            firstParent,
            partialFetch,
            primaryBranchMigration,
            patchTransformation,
            ConvertDescribeVersion(describeVersion),
            SkylarkUtil.ConvertFromNoneable<IVersionSelector?>(versionSelector, null),
            MainConfigFile.Path(),
            _workflowName,
            GitHubPostSubmitApprovalsProvider(
                fixedUrl, SkylarkUtil.ConvertOptionalString(@ref), credentialHandler),
            enableLfs,
            credentialHandler,
            gitRepositoryHook);
    }

    [StarlarkMethod("gitlab_origin",
        Doc = "Defines a Git origin for a GitLab hosted repository.",
        Documented = false,
        UseStarlarkThread = true)]
    public GitOrigin GitlabOrigin(
        [Param(Name = "url", Named = true, Doc = "Indicates the URL of the git repository")]
        string url,
        [Param(Name = "ref", Named = true, DefaultValue = "None",
            Doc = "Represents the default reference that will be used for reading the revision.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object @ref,
        [Param(Name = "submodules", Named = true, DefaultValue = "'NO'",
            Doc = "Download submodules. Valid values: NO, YES, RECURSIVE.")]
        string submodules,
        [Param(Name = "excluded_submodules", Named = true, Positional = false, DefaultValue = "[]",
            Doc = "A list of names of submodules that will not be downloaded.",
            AllowedTypes = new[] { typeof(StarlarkSequence) })]
        object excludedSubmodules,
        [Param(Name = "first_parent", Named = true, Positional = false, DefaultValue = "True",
            Doc = "If true, it only uses the first parent when looking for changes.")]
        bool firstParent,
        [Param(Name = "partial_fetch", Named = true, Positional = false, DefaultValue = "False",
            Doc = "If true, partially fetch git repository by only fetching affected files.")]
        bool partialFetch,
        [Param(Name = PatchField, Named = true, Positional = false, DefaultValue = "None",
            Doc = PatchFieldDesc,
            AllowedTypes = new[] { typeof(ITransformation), typeof(NoneType) })]
        object patch,
        [Param(Name = "describe_version", Named = true, Positional = false, DefaultValue = "None",
            Doc = DescribeVersionFieldDoc,
            AllowedTypes = new[] { typeof(bool), typeof(NoneType) })]
        object describeVersion,
        [Param(Name = "version_selector", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Select a custom version (tag) to migrate instead of 'ref'.",
            AllowedTypes = new[] { typeof(IVersionSelector), typeof(NoneType) })]
        object versionSelector,
        [Param(Name = "primary_branch_migration", Named = true, Positional = false, DefaultValue = "False",
            Doc = "When enabled, copybara will ignore the 'ref' param if it is 'master' or 'main'.",
            AllowedTypes = new[] { typeof(bool) })]
        bool primaryBranchMigration,
        [Param(Name = "enable_lfs", Named = true, Positional = false, DefaultValue = "False",
            Doc = "If true, Large File Storage support is enabled for the origin.")]
        bool enableLfs,
        [Param(Name = "credentials", Named = true, Positional = false, DefaultValue = "None",
            Doc = CredentialDoc,
            AllowedTypes = new[] { typeof(UsernamePasswordIssuer), typeof(NoneType) })]
        object? credentials,
        StarlarkThread thread)
    {
        SkylarkUtil.Check(
            ReferenceEquals(versionSelector, StarlarkRt.None) || ReferenceEquals(@ref, StarlarkRt.None),
            "Cannot use ref field and version_selector. Version selector will decide the ref"
                + " to migrate");

        var excludedSubmoduleList = SkylarkUtil.ConvertStringList(excludedSubmodules, "excluded_submodules");
        CheckSubmoduleConfig(submodules, excludedSubmoduleList);
        string fixedUrl = FixHttp(url, thread.GetCallerLocation());
        var patchTransformation = MaybeGetPatchTransformation(patch);
        var credentialHandler = GetCredentialHandler(fixedUrl, credentials);

        // TODO(port): reconcile — GitOrigin.NewGitOrigin static factory is provided by a peer.
        return NewGitOrigin(
            Options,
            fixedUrl,
            SkylarkUtil.ConvertOptionalString(@ref),
            GitRepoType.GitLab,
            SkylarkUtil.StringToEnum<GitOrigin.SubmoduleStrategy>("submodules", submodules),
            excludedSubmoduleList,
            includeBranchCommitLogs: false,
            firstParent,
            partialFetch,
            primaryBranchMigration,
            patchTransformation,
            ConvertDescribeVersion(describeVersion),
            SkylarkUtil.ConvertFromNoneable<IVersionSelector?>(versionSelector, null),
            MainConfigFile.Path(),
            _workflowName,
            new NoneApprovedProvider(),
            enableLfs,
            credentialHandler,
            gitRepositoryHook: null);
    }

    // Mirrors Java's `Starlark.isNullOrNone(integrates) ? defaultGitIntegrate
    // : Sequence.cast(integrates, GitIntegrateChanges.class, "integrates")`.
    private IEnumerable<GitIntegrateChanges> ConvertIntegrates(object integrates)
    {
        var sequence = StarlarkRt.IsNullOrNone(integrates)
            ? _defaultGitIntegrate
            : (StarlarkSequence)integrates;
        return sequence.Cast<GitIntegrateChanges>().ToImmutableArray();
    }

    private bool ConvertDescribeVersion(object describeVersion) =>
        SkylarkUtil.ConvertFromNoneable(describeVersion, Options.Get<GitOriginOptions>().GitDescribeDefault);

    [StarlarkMethod("destination",
        Doc = "Creates a commit in a git repository using the transformed worktree.",
        UseStarlarkThread = true)]
    public GitDestination Destination(
        [Param(Name = "url", Named = true, Doc = "Indicates the URL to push to.")]
        string url,
        [Param(Name = "push", Named = true, DefaultValue = "'master'",
            Doc = "Reference to use for pushing the change, for example 'main'.")]
        string push,
        [Param(Name = "tag_name", Named = true, DefaultValue = "None",
            Doc = "A template string that refers to a tag name.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object tagName,
        [Param(Name = "tag_msg", Named = true, DefaultValue = "None",
            Doc = "A template string that refers to the commit msg of a tag.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object tagMsg,
        [Param(Name = "fetch", Named = true, DefaultValue = "None",
            Doc = "Indicates the ref from which to get the parent commit.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object fetch,
        [Param(Name = "partial_fetch", Named = true, Positional = false, DefaultValue = "False",
            Doc = "This is an experimental feature that only works for certain origin globs.")]
        bool partialFetch,
        [Param(Name = "integrates", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Integrate changes from a url present in the migrated change label.",
            AllowedTypes = new[] { typeof(StarlarkSequence), typeof(NoneType) })]
        object integrates,
        [Param(Name = "primary_branch_migration", Named = true, Positional = false, DefaultValue = "False",
            Doc = "When enabled, copybara will ignore the 'push' and 'fetch' params if either is 'master' or 'main'.",
            AllowedTypes = new[] { typeof(bool) })]
        bool primaryBranchMigration,
        [Param(Name = "checker", Named = true, Positional = false, DefaultValue = "None",
            Doc = "A checker that can check leaks or other checks in the commit created.",
            AllowedTypes = new[] { typeof(IChecker), typeof(NoneType) })]
        object checker,
        [Param(Name = "credentials", Named = true, Positional = false, DefaultValue = "None",
            Doc = CredentialDoc,
            AllowedTypes = new[] { typeof(UsernamePasswordIssuer), typeof(NoneType) })]
        object? credentials,
        StarlarkThread thread)
    {
        var destinationOptions = Options.Get<GitDestinationOptions>();
        string resolvedPush = SkylarkUtil.CheckNotEmpty(FirstNotNull(destinationOptions.Push, push), "push");
        var generalOptions = Options.Get<GeneralOptions>();
        var maybeChecker = SkylarkUtil.ConvertFromNoneable<IChecker?>(checker, null);
        if (maybeChecker != null && Options.Get<GitDestinationOptions>().SkipGitChecker)
        {
            maybeChecker = null;
            GetGeneralConsole()
                .Warn(
                    "Skipping git checker for git.destination. Note that this could"
                        + " cause leaks or other problems");
        }

        var credentialHandler = GetCredentialHandler(url, credentials);
        // TODO(port): reconcile — GitDestination public constructor / DefaultWriteHook shape.
        return new GitDestination(
            FixHttp(
                SkylarkUtil.CheckNotEmpty(FirstNotNull(destinationOptions.Url, url), "url"),
                thread.GetCallerLocation()),
            SkylarkUtil.CheckNotEmpty(
                FirstNotNull(
                    destinationOptions.Fetch,
                    SkylarkUtil.ConvertFromNoneable<string?>(fetch, null),
                    resolvedPush),
                "fetch"),
            resolvedPush,
            partialFetch,
            primaryBranchMigration,
            SkylarkUtil.ConvertFromNoneable<string?>(tagName, null),
            SkylarkUtil.ConvertFromNoneable<string?>(tagMsg, null),
            destinationOptions,
            Options.Get<GitOptions>(),
            generalOptions,
            new GitDestination.DefaultWriteHook(),
            ConvertIntegrates(integrates),
            maybeChecker,
            credentialHandler);
    }

    [StarlarkMethod("github_destination",
        Doc = "Creates a commit in a GitHub repository branch (for example master).",
        UseStarlarkThread = true)]
    public GitDestination GitHubDestination(
        [Param(Name = "url", Named = true, Doc = "Indicates the URL to push to.")]
        string url,
        [Param(Name = "push", Named = true, DefaultValue = "'master'",
            Doc = "Reference to use for pushing the change, for example 'main'.")]
        string push,
        [Param(Name = "fetch", Named = true, DefaultValue = "None",
            Doc = "Indicates the ref from which to get the parent commit.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object fetch,
        [Param(Name = "pr_branch_to_update", Named = true, DefaultValue = "None",
            Doc = "A template string that refers to a pull request branch in the same repository.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object prBranchToUpdate,
        [Param(Name = "partial_fetch", Named = true, DefaultValue = "False",
            Doc = "This is an experimental feature that only works for certain origin globs.")]
        bool partialFetch,
        [Param(Name = "delete_pr_branch", Named = true, DefaultValue = "None",
            Doc = "When `pr_branch_to_update` is enabled, it will delete the branch reference after the push.",
            AllowedTypes = new[] { typeof(bool), typeof(NoneType) })]
        object deletePrBranchParam,
        [Param(Name = "integrates", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Integrate changes from a url present in the migrated change label.",
            AllowedTypes = new[] { typeof(StarlarkSequence), typeof(NoneType) })]
        object integrates,
        [Param(Name = "api_checker", Named = true, Positional = false, DefaultValue = "None",
            Doc = "A checker for the API endpoint provided for after_migration hooks.",
            AllowedTypes = new[] { typeof(IChecker), typeof(NoneType) })]
        object apiChecker,
        [Param(Name = "primary_branch_migration", Named = true, Positional = false, DefaultValue = "False",
            Doc = "When enabled, copybara will ignore the 'push' and 'fetch' params if either is 'master' or 'main'.",
            AllowedTypes = new[] { typeof(bool) })]
        bool primaryBranchMigration,
        [Param(Name = "tag_name", Named = true, Positional = false, DefaultValue = "None",
            Doc = "A template string that specifies a tag name.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object tagName,
        [Param(Name = "tag_msg", Named = true, Positional = false, DefaultValue = "None",
            Doc = "A template string that refers to the commit msg for a tag.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object tagMsg,
        [Param(Name = "checker", Named = true, Positional = false, DefaultValue = "None",
            Doc = "A checker that validates the commit files & message.",
            AllowedTypes = new[] { typeof(IChecker), typeof(NoneType) })]
        object checker,
        [Param(Name = "credentials", Named = true, Positional = false, DefaultValue = "None",
            Doc = CredentialDoc,
            AllowedTypes = new[] { typeof(UsernamePasswordIssuer), typeof(NoneType) })]
        object? credentials,
        [Param(Name = "push_to_fork", Named = true, Positional = false, DefaultValue = "False",
            Documented = false,
            AllowedTypes = new[] { typeof(bool) })]
        bool pushToFork,
        [Param(Name = "github_host_name", Named = true, Positional = false, DefaultValue = "'github.com'",
            Doc = "The host name of the GitHub repository, used to construct the URL.")]
        string gitHubHostName,
        StarlarkThread thread)
    {
        var destinationOptions = Options.Get<GitDestinationOptions>();
        string resolvedPush = SkylarkUtil.CheckNotEmpty(FirstNotNull(destinationOptions.Push, push), "push");
        var generalOptions = Options.Get<GeneralOptions>();
        string repoUrl =
            FixHttp(
                SkylarkUtil.CheckNotEmpty(FirstNotNull(destinationOptions.Url, url), "url"),
                thread.GetCallerLocation());
        string? branchToUpdate = SkylarkUtil.ConvertFromNoneable<string?>(prBranchToUpdate, null);
        bool? deletePrBranch = SkylarkUtil.ConvertFromNoneable<bool?>(deletePrBranchParam, null);
        SkylarkUtil.Check(
            branchToUpdate != null || deletePrBranch == null,
            "'delete_pr_branch' can only be set if 'pr_branch_to_update' is used");
        var gitHubOptions = Options.Get<GitHubOptions>();

        string? effectivePrBranchToUpdate = branchToUpdate;
        if (Options.Get<WorkflowOptions>().IsInitHistory())
        {
            generalOptions
                .GetConsole()
                .InfoFmt("Ignoring field 'pr_branch_to_update' as '--init-history' is set.");
            effectivePrBranchToUpdate = null;
        }

        bool effectiveDeletePrBranch =
            gitHubOptions.GitHubDeletePrBranch ?? deletePrBranch ?? false;

        var apiCheckerObj = SkylarkUtil.ConvertFromNoneable<IChecker?>(apiChecker, null);
        var checkerObj = SkylarkUtil.ConvertFromNoneable<IChecker?>(checker, null);
        var gitHubHost = new GitHubHost(gitHubHostName);
        CredentialFileHandler? credentialHandler;
        try
        {
            credentialHandler =
                GetCredentialHandler(gitHubHost.GetHost(), gitHubHost.GetProjectNameFromUrl(url), credentials);
        }
        catch (ValidationException e)
        {
            throw new EvalException("Cannot parse url", e);
        }

        // TODO(port): reconcile — GitDestination constructor / GitHubWriteHook shape.
        return new GitDestination(
            repoUrl,
            SkylarkUtil.CheckNotEmpty(
                FirstNotNull(
                    destinationOptions.Fetch,
                    SkylarkUtil.ConvertFromNoneable<string?>(fetch, null),
                    resolvedPush),
                "fetch"),
            resolvedPush,
            partialFetch,
            primaryBranchMigration,
            SkylarkUtil.ConvertFromNoneable<string?>(tagName, null),
            SkylarkUtil.ConvertFromNoneable<string?>(tagMsg, null),
            destinationOptions,
            Options.Get<GitOptions>(),
            generalOptions,
            new GitHubWriteHook(
                generalOptions,
                repoUrl,
                gitHubOptions,
                effectivePrBranchToUpdate,
                effectiveDeletePrBranch,
                GetGeneralConsole(),
                apiCheckerObj ?? checkerObj,
                gitHubHost,
                credentialHandler,
                pushToFork),
            ConvertIntegrates(integrates),
            checkerObj,
            credentialHandler);
    }

    [StarlarkMethod("github_pr_destination",
        Doc = "Creates changes in a new pull request in the destination.",
        UseStarlarkThread = true)]
    public GitHubPrDestination GithubPrDestination(
        [Param(Name = "url", Named = true, Doc = "Url of the GitHub project.")]
        string url,
        [Param(Name = "destination_ref", Named = true, DefaultValue = "'master'",
            Doc = "Destination reference for the change.")]
        string destinationRef,
        [Param(Name = "pr_branch", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Customize the pull request branch.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object prBranch,
        [Param(Name = "partial_fetch", Named = true, Positional = false, DefaultValue = "False",
            Doc = "This is an experimental feature that only works for certain origin globs.")]
        bool partialFetch,
        [Param(Name = "allow_empty_diff", Named = true, Positional = false, DefaultValue = "True",
            Doc = "If set, copybara will skip pushing a change to an existing PR only if the tree is the same.")]
        bool allowEmptyDiff,
        [Param(Name = "allow_empty_diff_merge_statuses", Named = true, Positional = false, DefaultValue = "[]",
            Doc = ExperimentalPrefix + "Merge state statuses that will still upload.",
            AllowedTypes = new[] { typeof(StarlarkSequence) })]
        StarlarkSequence allowEmptyDiffMergeStatuses,
        [Param(Name = "allow_empty_diff_check_suites_to_conclusion", Named = true, Positional = false,
            DefaultValue = "{}",
            Doc = ExperimentalPrefix + "Check suite slugs and conclusions for uploads.",
            AllowedTypes = new[] { typeof(Dict) })]
        Dict allowEmptyDiffCheckSuitesToConclusion,
        [Param(Name = "title", Named = true, Positional = false, DefaultValue = "None",
            Doc = "When creating a pull request, use this title.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object title,
        [Param(Name = "body", Named = true, Positional = false, DefaultValue = "None",
            Doc = "When creating a pull request, use this body.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object body,
        [Param(Name = "assignees", Named = true, Positional = false, DefaultValue = "[]",
            Doc = "The assignees to set when creating a new pull request.",
            AllowedTypes = new[] { typeof(StarlarkSequence) })]
        StarlarkSequence assignees,
        [Param(Name = "integrates", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Integrate changes from a url present in the migrated change label.",
            AllowedTypes = new[] { typeof(StarlarkSequence), typeof(NoneType) })]
        object integrates,
        [Param(Name = "api_checker", Named = true, Positional = false, DefaultValue = "None",
            Doc = "A checker for the GitHub API endpoint provided for after_migration hooks.",
            AllowedTypes = new[] { typeof(IChecker), typeof(NoneType) })]
        object apiChecker,
        [Param(Name = "update_description", Named = true, Positional = false, DefaultValue = "False",
            Doc = "If this field is set to true, it will update those fields for every update.")]
        bool updateDescription,
        [Param(Name = "primary_branch_migration", Named = true, Positional = false, DefaultValue = "False",
            Doc = "When enabled, copybara will ignore the 'destination_ref' param if it is 'master' or 'main'.")]
        bool primaryBranchMigrationMode,
        [Param(Name = "checker", Named = true, Positional = false, DefaultValue = "None",
            Doc = "A checker that validates the commit files & message.",
            AllowedTypes = new[] { typeof(IChecker), typeof(NoneType) })]
        object checker,
        [Param(Name = "draft", Named = true, Positional = false, DefaultValue = "False",
            Doc = "Flag create pull request as draft or not.")]
        bool isDraft,
        [Param(Name = "credentials", Named = true, Positional = false, DefaultValue = "None",
            Doc = CredentialDoc,
            AllowedTypes = new[] { typeof(UsernamePasswordIssuer), typeof(NoneType) })]
        object? credentials,
        [Param(Name = "github_host_name", Named = true, Positional = false, DefaultValue = "'github.com'",
            Doc = ExperimentalPrefix + "The GitHub host name to use for the migration.")]
        string gitHubHostName,
        StarlarkThread thread)
    {
        var generalOptions = Options.Get<GeneralOptions>();
        var gitHubHost = new GitHubHost(gitHubHostName);
        SkylarkUtil.Check(
            gitHubHost.IsGitHubUrl(url),
            "'{0}' is not a valid GitHub url for the given host name '{1}'",
            url,
            gitHubHostName);
        var destinationOptions = Options.Get<GitDestinationOptions>();
        var gitHubOptions = Options.Get<GitHubOptions>();
        string? destinationPrBranch = SkylarkUtil.ConvertFromNoneable<string?>(prBranch, null);
        var apiCheckerObj = SkylarkUtil.ConvertFromNoneable<IChecker?>(apiChecker, null);
        var checkerObj = SkylarkUtil.ConvertFromNoneable<IChecker?>(checker, null);
        CredentialFileHandler? credentialHandler;
        try
        {
            credentialHandler =
                GetCredentialHandler(gitHubHost.GetHost(), gitHubHost.GetProjectNameFromUrl(url), credentials);
        }
        catch (ValidationException e)
        {
            throw new EvalException("Cannot parse url", e);
        }

        // TODO(port): reconcile — GitHubPrDestination / GitHubPrWriteHook constructor shapes.
        return new GitHubPrDestination(
            FixHttp(
                SkylarkUtil.CheckNotEmpty(FirstNotNull(destinationOptions.Url, url), "url"),
                thread.GetCallerLocation()),
            destinationRef,
            SkylarkUtil.ConvertFromNoneable<string?>(prBranch, null),
            partialFetch,
            isDraft,
            generalOptions,
            Options.Get<GitHubOptions>(),
            destinationOptions,
            Options.Get<GitHubDestinationOptions>(),
            Options.Get<GitOptions>(),
            new GitHubPrWriteHook(
                generalOptions,
                url,
                gitHubOptions,
                destinationPrBranch,
                partialFetch,
                allowEmptyDiff,
                SkylarkUtil.ConvertStringList(allowEmptyDiffMergeStatuses, "empty_diff_merge_statuses")
                    .ToImmutableHashSet(),
                ConvertSlugToConclusion(allowEmptyDiffCheckSuitesToConclusion),
                GetGeneralConsole(),
                gitHubHost,
                credentialHandler),
            ConvertIntegrates(integrates),
            SkylarkUtil.ConvertFromNoneable<string?>(title, null),
            SkylarkUtil.ConvertFromNoneable<string?>(body, null),
            SkylarkUtil.ConvertStringList(assignees, "assignees").ToImmutableArray(),
            MainConfigFile,
            apiCheckerObj ?? checkerObj,
            updateDescription,
            gitHubHost,
            primaryBranchMigrationMode,
            checkerObj,
            credentialHandler);
    }

    private ImmutableSetMultimap<string, CheckRunConclusion> ConvertSlugToConclusion(
        Dict allowEmptyDiffCheckSuitesToConclusion)
    {
        var builder = ImmutableSetMultimap<string, CheckRunConclusion>.CreateBuilder();
        foreach (var k in allowEmptyDiffCheckSuitesToConclusion.Keys)
        {
            if (k is not string keyStr)
            {
                throw StarlarkRt.Errorf(
                    "Invalid key '{0}' for allow_empty_diff_check_suites_to_conclusion."
                        + " The value has to be an string with the slug name of the check suite."
                        + " e.g. \"github-actions\"",
                    k!);
            }

            var conclusionStr =
                SkylarkUtil.ConvertStringList(
                    allowEmptyDiffCheckSuitesToConclusion.Get(k),
                    "allow_empty_diff_check_suites_to_conclusion[\"" + k + "\"]");
            foreach (var c in conclusionStr)
            {
                var conclusion = CheckRunConclusions.FromValue(c);
                if (conclusion == null)
                {
                    throw StarlarkRt.Errorf(
                        "Invalid conclusion value {0}. Valid values: {1}",
                        c,
                        string.Join(
                            ", ",
                            Enum.GetValues<CheckRunConclusion>().Select(v => v.GetApiVal())));
                }

                builder.Put(keyStr, conclusion.Value);
            }
        }

        return builder.Build();
    }

    private static string? FirstNotNull(params string?[] values)
    {
        foreach (var value in values)
        {
            if (!string.IsNullOrEmpty(value))
            {
                return value;
            }
        }

        return null;
    }

    [StarlarkMethod("gerrit_destination",
        Doc = "Creates a change in Gerrit using the transformed worktree.",
        UseStarlarkThread = true)]
    public GerritDestination GerritDestination(
        [Param(Name = "url", Named = true, Doc = "Indicates the URL to push to.")]
        string url,
        [Param(Name = "fetch", Named = true, Doc = "Indicates the ref from which to get the parent commit")]
        string fetch,
        [Param(Name = "push_to_refs_for", Named = true, DefaultValue = "None",
            Doc = "Review branch to push the change to. Defaults to 'fetch' value.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object pushToRefsFor,
        [Param(Name = "submit", Named = true, DefaultValue = "False",
            Doc = "If true, skip the push thru Gerrit refs/for/branch and directly push to branch.")]
        bool submit,
        [Param(Name = "partial_fetch", Named = true, DefaultValue = "False",
            Doc = "This is an experimental feature that only works for certain origin globs.")]
        bool partialFetch,
        [Param(Name = "notify", Named = true, DefaultValue = "None",
            Doc = "Type of Gerrit notify option. Sends notifications by default.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object notifyOptionObj,
        [Param(Name = "change_id_policy", Named = true, DefaultValue = "'FAIL_IF_PRESENT'",
            Doc = "What to do in the presence or absence of Change-Id in message.")]
        string changeIdPolicy,
        [Param(Name = "allow_empty_diff_patchset", Named = true, DefaultValue = "True",
            Doc = "If false, Copybara will download current PatchSet and check the diff.")]
        bool allowEmptyPatchSet,
        [Param(Name = "reviewers", Named = true, DefaultValue = "[]",
            Doc = "The list of the reviewers to add.")]
        StarlarkSequence reviewers,
        [Param(Name = "cc", Named = true, DefaultValue = "[]",
            Doc = "The list of the email addresses or users that will be CCed in the review.")]
        StarlarkSequence ccParam,
        [Param(Name = "labels", Named = true, DefaultValue = "[]",
            Doc = "The list of labels to be pushed with the change.")]
        StarlarkSequence labelsParam,
        [Param(Name = "api_checker", Named = true, Positional = false, DefaultValue = "None",
            Doc = "A checker for the Gerrit API endpoint provided for after_migration hooks.",
            AllowedTypes = new[] { typeof(IChecker), typeof(NoneType) })]
        object apiChecker,
        [Param(Name = "integrates", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Integrate changes from a url present in the migrated change label.",
            AllowedTypes = new[] { typeof(StarlarkSequence), typeof(NoneType) })]
        object integrates,
        [Param(Name = "topic", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Sets the topic of the Gerrit change created.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object topicObj,
        [Param(Name = "gerrit_submit", Named = true, Positional = false, DefaultValue = "False",
            Doc = "If enabled, it will update the Gerrit change with the latest commit and submit using Gerrit.")]
        bool gerritSubmit,
        [Param(Name = "primary_branch_migration", Named = true, Positional = false, DefaultValue = "False",
            Doc = "When enabled, copybara will ignore the 'push_to_refs_for' and 'fetch' params if either is 'master' or 'main'.",
            AllowedTypes = new[] { typeof(bool) })]
        bool primaryBranchMigrationMode,
        [Param(Name = "checker", Named = true, Positional = false, DefaultValue = "None",
            Doc = "A checker that validates the commit files & message.",
            AllowedTypes = new[] { typeof(IChecker), typeof(NoneType) })]
        object checker,
        [Param(Name = "credentials", Named = true, Positional = false, DefaultValue = "None",
            Doc = CredentialDoc,
            AllowedTypes = new[] { typeof(UsernamePasswordIssuer), typeof(NoneType) })]
        object? credentials,
        StarlarkThread thread)
    {
        SkylarkUtil.CheckNotEmpty(url, "url");
        if (gerritSubmit)
        {
            Preconditions.CheckArgument(submit, "Only set gerrit_submit if submit is true");
        }

        var newReviewers = SkylarkUtil.ConvertStringList(reviewers, "reviewers");
        var cc = SkylarkUtil.ConvertStringList(ccParam, "cc");
        var labels = SkylarkUtil.ConvertStringList(labelsParam, "labels");

        string? notifyOptionStr = SkylarkUtil.ConvertFromNoneable<string?>(notifyOptionObj, null);
        SkylarkUtil.Check(
            !(submit && notifyOptionStr != null),
            "Cannot set 'notify' with 'submit = True' in git.gerrit_destination().");

        string? topicStr = SkylarkUtil.ConvertFromNoneable<string?>(topicObj, null);
        SkylarkUtil.Check(
            !(submit && topicStr != null),
            "Cannot set 'topic' with 'submit = True' in git.gerrit_destination().");

        // TODO(port): reconcile — GerritDestination.NotifyOption / ChangeIdPolicy enums provided by peer.
        GerritDestination.NotifyOption? notifyOption =
            notifyOptionStr == null
                ? null
                : SkylarkUtil.StringToEnum<GerritDestination.NotifyOption>("notify", notifyOptionStr);

        var apiCheckerObj = SkylarkUtil.ConvertFromNoneable<IChecker?>(apiChecker, null);
        var checkerObj = SkylarkUtil.ConvertFromNoneable<IChecker?>(checker, null);
        var credentialHandler = GetCredentialHandler(url, credentials);

        return global::Copybara.Git.GerritDestination.NewGerritDestination(
            Options.Get<GeneralOptions>(),
            Options.Get<GerritOptions>(),
            Options.Get<GitOptions>(),
            Options.Get<GitDestinationOptions>(),
            FixHttp(url, thread.GetCallerLocation()),
            SkylarkUtil.CheckNotEmpty(FirstNotNull(Options.Get<GitDestinationOptions>().Fetch, fetch), "fetch"),
            SkylarkUtil.CheckNotEmpty(
                FirstNotNull(
                    SkylarkUtil.ConvertFromNoneable<string?>(pushToRefsFor, null),
                    Options.Get<GitDestinationOptions>().Fetch,
                    fetch),
                "push_to_refs_for"),
            submit,
            partialFetch,
            notifyOption,
            SkylarkUtil.StringToEnum<GerritDestination.ChangeIdPolicy>("change_id_policy", changeIdPolicy),
            allowEmptyPatchSet,
            newReviewers,
            cc,
            labels,
            apiCheckerObj ?? checkerObj,
            ConvertIntegrates(integrates),
            topicStr,
            gerritSubmit,
            primaryBranchMigrationMode,
            checkerObj,
            credentialHandler);
    }

    [StarlarkMethod("gitlab_mr_origin",
        Doc = "Creates a GitLab Merge Request origin. WARNING: experimental; please do not use.",
        Documented = false)]
    public GitLabMrOrigin GitLabMrOrigin(
        [Param(Name = "url", Named = true, Positional = false, Doc = "The URL of the GitLab repository.")]
        string url,
        [Param(Name = "credentials", Named = true, Positional = false, DefaultValue = "None",
            Doc = GitLabCredentialDoc,
            AllowedTypes = new[] { typeof(UsernamePasswordIssuer), typeof(NoneType) })]
        object usernamePasswordIssuer,
        [Param(Name = "partial_fetch", Named = true, Positional = false, DefaultValue = "False",
            Doc = "If true, partially fetch the Git repository by only fetching affected files.")]
        bool partialFetch,
        [Param(Name = "use_merge_commit", Named = true, Positional = false, DefaultValue = "False",
            Doc = "If the content for GitLab's generated merge commit should be used instead of the MR head.")]
        bool useMergeCommit,
        [Param(Name = "describe_version", Named = true, Positional = false, DefaultValue = "True",
            Doc = DescribeVersionFieldDoc)]
        bool describeVersion,
        [Param(Name = "first_parent", Named = true, Positional = false, DefaultValue = "True",
            Doc = "If true, it only uses the first parent when looking for changes.")]
        bool firstParent,
        [Param(Name = "submodules", Named = true, Positional = false, DefaultValue = "'NO'",
            Doc = "Download submodules. Valid values: NO, YES, RECURSIVE.")]
        string submodules,
        [Param(Name = "excluded_submodules", Named = true, Positional = false, DefaultValue = "[]",
            Doc = "A list of names of submodules that will not be downloaded.",
            AllowedTypes = new[] { typeof(StarlarkSequence) })]
        StarlarkSequence excludedSubmodules,
        [Param(Name = PatchField, Named = true, Positional = false, DefaultValue = "None",
            Doc = PatchFieldDesc,
            AllowedTypes = new[] { typeof(ITransformation), typeof(NoneType) })]
        object patch)
    {
        SkylarkUtil.CheckNotEmpty(url, "url");

        var gitLabOptions = Options.Get<GitLabOptions>();
        var console = GetGeneralConsole();
        var patchTransformation = MaybeGetPatchTransformation(patch);

        // TODO(port): reconcile — GitLabMrOrigin.Builder API provided by a peer.
        var originBuilder =
            global::Copybara.Git.GitLabMrOrigin.NewBuilder()
                .SetConsole(console)
                .SetUsernamePasswordIssuer(
                    SkylarkUtil.ConvertToOptional<UsernamePasswordIssuer>(usernamePasswordIssuer))
                .SetRepoUrl(new Uri(url))
                .SetGitOptions(Options.Get<GitOptions>())
                .SetGitOriginOptions(Options.Get<GitOriginOptions>())
                .SetGitLabOptions(Options.Get<GitLabOptions>())
                .SetGeneralOptions(Options.Get<GeneralOptions>())
                .SetSubmoduleStrategy(
                    SkylarkUtil.StringToEnum<GitOrigin.SubmoduleStrategy>("submodules", submodules))
                .SetExcludedSubmodules(
                    SkylarkUtil.ConvertStringList(excludedSubmodules, "excluded_submodules").ToImmutableArray())
                .SetPartialFetch(partialFetch)
                .SetDescribeVersion(describeVersion)
                .SetFirstParent(firstParent)
                .SetUseMergeCommit(useMergeCommit);

        if (patchTransformation != null)
        {
            originBuilder.SetPatchTransformation(patchTransformation);
        }

        return originBuilder.Build();
    }

    [StarlarkMethod("gitlab_mr_destination",
        Doc = "Creates a GitLab Merge Request destination. WARNING: experimental; please do not use.",
        Documented = false)]
    public GitLabMrDestination GitLabMrDestination(
        [Param(Name = "url", Named = true, Positional = false, Doc = "The URL of the GitLab repository.")]
        string url,
        [Param(Name = "credentials", Named = true, Positional = false, Doc = GitLabCredentialDoc)]
        UsernamePasswordIssuer usernamePasswordIssuer,
        [Param(Name = "source_branch", Named = true, Positional = false, DefaultValue = "None",
            Doc = "The source branch to use for creating the merge request.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object sourceBranchTemplate,
        [Param(Name = "target_branch", Named = true, Positional = false,
            Doc = "The target branch to use for creating the merge request.")]
        string targetBranch,
        [Param(Name = "title", Named = true, Positional = false, DefaultValue = "None",
            Doc = "The title to use for creating the merge request's title.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object titleTemplate,
        [Param(Name = "body", Named = true, Positional = false, DefaultValue = "None",
            Doc = "The body to use for creating the merge request.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object bodyTemplate,
        [Param(Name = "assignees", Named = true, Positional = false, DefaultValue = "[]",
            Doc = "The assignees to set when creating a new merge request.",
            AllowedTypes = new[] { typeof(StarlarkSequence) })]
        StarlarkSequence assigneeTemplates,
        [Param(Name = "allow_empty_diff", Named = true, Positional = false, DefaultValue = "False",
            Doc = "If False, copybara will skip pushing a change to an existing MR if the tree is the same.")]
        bool allowEmptyDiff,
        [Param(Name = "allow_empty_diff_merge_statuses", Named = true, Positional = false, DefaultValue = "[]",
            Doc = "Merge statuses that will still upload.",
            AllowedTypes = new[] { typeof(StarlarkSequence) })]
        StarlarkSequence allowEmptyDiffMergeStatuses,
        [Param(Name = "partial_fetch", Named = true, Positional = false, DefaultValue = "False",
            Doc = "If true, partially fetch the Git repository by only fetching affected files.")]
        bool partialFetch,
        [Param(Name = "integrates", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Integrate changes from a url present in the migrated change label.",
            AllowedTypes = new[] { typeof(StarlarkSequence), typeof(NoneType) })]
        object integrates,
        [Param(Name = "checker", Named = true, Positional = false, DefaultValue = "None",
            Doc = "A checker that validates the commit files & message.",
            AllowedTypes = new[] { typeof(IChecker), typeof(NoneType) })]
        object checker)
    {
        SkylarkUtil.CheckNotEmpty(url, "url");

        var gitLabOptions = Options.Get<GitLabOptions>();
        var console = GetGeneralConsole();

        return new global::Copybara.Git.GitLabMrDestination.GitLabMrDestinationParams(
                RepoUrl: new Uri(url),
                UsernamePasswordIssuer: usernamePasswordIssuer,
                TitleTemplate: SkylarkUtil.ConvertToOptional<string>(titleTemplate),
                BodyTemplate: SkylarkUtil.ConvertToOptional<string>(bodyTemplate),
                AssigneeTemplates:
                    SkylarkUtil.ConvertStringList(assigneeTemplates, "assignee_templates").ToImmutableArray(),
                SourceBranchTemplate: SkylarkUtil.ConvertToOptional<string>(sourceBranchTemplate),
                TargetBranch: targetBranch,
                ConfigFile: MainConfigFile,
                AllowEmptyDiff: allowEmptyDiff,
                AllowEmptyDiffMergeStatuses:
                    SkylarkUtil.StringListToEnumList<DetailedMergeStatus>(
                            SkylarkUtil.ConvertStringList(
                                allowEmptyDiffMergeStatuses, "allow_empty_diff_merge_statuses"),
                            "allow_empty_diff_merge_statuses",
                            console)
                        .ToImmutableHashSet(),
                GeneralOptions: Options.Get<GeneralOptions>(),
                GitOptions: Options.Get<GitOptions>(),
                GitLabOptions: Options.Get<GitLabOptions>(),
                DestinationOptions: Options.Get<GitDestinationOptions>(),
                PartialFetch: partialFetch,
                Integrates: ConvertIntegrates(integrates),
                Checker: SkylarkUtil.ConvertToOptional<IChecker>(checker))
            .CreateDestination();
    }

    [StarlarkMethod(GitHubApiName,
        Doc = "Defines a feedback API endpoint for GitHub, that exposes relevant GitHub API operations.",
        UseStarlarkThread = true)]
    public EndpointProvider<GitHubEndPoint> GithubApi(
        [Param(Name = "url", Named = true, Doc = "Indicates the GitHub repo URL.")]
        string url,
        [Param(Name = "checker", Named = true, DefaultValue = "None",
            Doc = "A checker for the GitHub API transport.",
            AllowedTypes = new[] { typeof(IChecker), typeof(NoneType) })]
        object checkerObj,
        [Param(Name = "credentials", Named = true, Positional = false, DefaultValue = "None",
            Doc = CredentialDoc,
            AllowedTypes = new[] { typeof(UsernamePasswordIssuer), typeof(NoneType) })]
        object? credentials,
        StarlarkThread thread)
    {
        SkylarkUtil.CheckNotEmpty(url, "url");
        string cleanedUrl = FixHttp(url, thread.GetCallerLocation());
        var checker = SkylarkUtil.ConvertFromNoneable<IChecker?>(checkerObj, null);
        ValidateEndpointChecker(checker, GitHubApiName);
        var gitHubOptions = Options.Get<GitHubOptions>();
        var credentialHandler = GetCredentialHandler(url, credentials);
        var gitHubHost = new GitHubHost("github.com");
        // TODO(port): reconcile — GitHubEndPoint / newGitHubApiSupplier provided by a peer.
        return EndpointProvider.Wrap(
            new GitHubEndPoint(
                gitHubOptions.NewGitHubApiSupplier(cleanedUrl, checker, credentialHandler, gitHubHost),
                cleanedUrl,
                GetGeneralConsole(),
                gitHubHost,
                credentialHandler));
    }

    [StarlarkMethod(GerritApiName,
        Doc = "Defines a feedback API endpoint for Gerrit, that exposes relevant Gerrit API operations.",
        UseStarlarkThread = true)]
    public EndpointProvider<GerritEndpoint> GerritApi(
        [Param(Name = "url", Named = true, Doc = "Indicates the Gerrit repo URL.")]
        string url,
        [Param(Name = "checker", Named = true, DefaultValue = "None",
            Doc = "A checker for the Gerrit API transport.",
            AllowedTypes = new[] { typeof(IChecker), typeof(NoneType) })]
        object checkerObj,
        [Param(Name = "allow_submit", Named = true, DefaultValue = "False",
            Doc = "Enable the submit_change method")]
        bool allowSubmit,
        StarlarkThread thread)
    {
        SkylarkUtil.CheckNotEmpty(url, "url");
        string cleanedUrl = FixHttp(url, thread.GetCallerLocation());
        var checker = SkylarkUtil.ConvertFromNoneable<IChecker?>(checkerObj, null);
        ValidateEndpointChecker(checker, GerritApiName);
        var gerritOptions = Options.Get<GerritOptions>();
        // TODO(port): reconcile — GerritEndpoint / newGerritApiSupplier provided by a peer.
        return EndpointProvider.Wrap(
            new GerritEndpoint(
                gerritOptions.NewGerritApiSupplier(cleanedUrl, checker),
                cleanedUrl,
                GetGeneralConsole(),
                allowSubmit));
    }

    private Console GetGeneralConsole() => Options.Get<GeneralOptions>().GetConsole();

    [StarlarkMethod(GerritTrigger,
        Doc = "Defines a feedback trigger based on updates on a Gerrit change.",
        UseStarlarkThread = true)]
    public global::Copybara.Git.GerritTrigger GerritTriggerMethod(
        [Param(Name = "url", Named = true, Doc = "Indicates the Gerrit repo URL.")]
        string url,
        [Param(Name = "checker", Named = true, DefaultValue = "None",
            Doc = "A checker for the Gerrit API transport provided by this trigger.",
            AllowedTypes = new[] { typeof(IChecker), typeof(NoneType) })]
        object checkerObj,
        [Param(Name = "events", Named = true, DefaultValue = "[]",
            Doc = "Types of events to monitor.",
            AllowedTypes = new[] { typeof(StarlarkSequence), typeof(Dict), typeof(NoneType) })]
        object events,
        [Param(Name = "allow_submit", Named = true, DefaultValue = "False",
            Doc = "Enable the submit_change method in the endpoint provided")]
        bool allowSubmit,
        StarlarkThread thread)
    {
        SkylarkUtil.CheckNotEmpty(url, "url");
        url = FixHttp(url, thread.GetCallerLocation());
        var checker = SkylarkUtil.ConvertFromNoneable<IChecker?>(checkerObj, null);
        ValidateEndpointChecker(checker, GerritTrigger);
        var parsedEvents = HandleGerritEventTypes(events);
        var gerritOptions = Options.Get<GerritOptions>();
        // TODO(port): reconcile — GerritTrigger / GerritEventTrigger provided by a peer.
        return new global::Copybara.Git.GerritTrigger(
            gerritOptions.NewGerritApiSupplier(url, checker),
            url,
            parsedEvents,
            GetGeneralConsole(),
            allowSubmit);
    }

    private ImmutableHashSet<GerritEventTrigger> HandleGerritEventTypes(object events)
    {
        var eventBuilder = new List<GerritEventTrigger>();
        var types = new HashSet<GerritEventType>();

        if (events is StarlarkSequence)
        {
            foreach (var e in SkylarkUtil.ConvertStringList(events, "events"))
            {
                var eventType = SkylarkUtil.StringToEnum<GerritEventType>("events", e);
                var trigger = GerritEventTrigger.Create(eventType, ImmutableHashSet<string>.Empty);
                SkylarkUtil.Check(!eventBuilder.Contains(trigger), "Repeated element {0}", e);
                eventBuilder.Add(trigger);
            }
        }
        else if (events is Dict dict)
        {
            foreach (var entry in dict.Entries)
            {
                string key = (string)entry.Key!;
                var eventType = SkylarkUtil.StringToEnum<GerritEventType>("events", key);
                SkylarkUtil.Check(types.Add(eventType), "Repeated element {0}", entry);
                var values = SkylarkUtil.ConvertStringList(entry.Value, "events");
                if (eventType == GerritEventType.STATUS)
                {
                    var allowedStatuses =
                        ImmutableHashSet.Create(
                            "PENDING", "ABANDONED", "MERGED", "CLOSED", "REVIEWED", "OPEN");
                    foreach (var status in values)
                    {
                        SkylarkUtil.Check(
                            allowedStatuses.Contains(status),
                            "Invalid status '{0}'. Valid values are {1}",
                            status,
                            string.Join(", ", allowedStatuses));
                    }
                }

                eventBuilder.Add(GerritEventTrigger.Create(eventType, values.ToImmutableHashSet()));
            }
        }

        return eventBuilder.ToImmutableHashSet();
    }

    [StarlarkMethod(GitHubTrigger,
        Doc = "Defines a feedback trigger based on updates on a GitHub PR.",
        UseStarlarkThread = true)]
    public global::Copybara.Git.GitHubTrigger GitHubTriggerMethod(
        [Param(Name = "url", Named = true, Doc = "Indicates the GitHub repo URL.")]
        string url,
        [Param(Name = "checker", Named = true, DefaultValue = "None",
            Doc = "A checker for the GitHub API transport provided by this trigger.",
            AllowedTypes = new[] { typeof(IChecker), typeof(NoneType) })]
        object checkerObj,
        [Param(Name = "events", Named = true, DefaultValue = "[]",
            Doc = "Types of events to subscribe.",
            AllowedTypes = new[] { typeof(StarlarkSequence), typeof(Dict) })]
        object events,
        [Param(Name = "credentials", Named = true, Positional = false, DefaultValue = "None",
            Doc = CredentialDoc,
            AllowedTypes = new[] { typeof(UsernamePasswordIssuer), typeof(NoneType) })]
        object? credentials,
        StarlarkThread thread)
    {
        SkylarkUtil.CheckNotEmpty(url, "url");
        url = FixHttp(url, thread.GetCallerLocation());
        var checker = SkylarkUtil.ConvertFromNoneable<IChecker?>(checkerObj, null);
        var eventBuilder = new List<EventTrigger>();
        var types = new HashSet<GitHubEventType>();
        var parsedEvents = HandleEventTypes(events, eventBuilder, types);
        ValidateEndpointChecker(checker, GitHubTrigger);
        var gitHubOptions = Options.Get<GitHubOptions>();
        CredentialFileHandler? credentialHandler;
        var gitHubHost = new GitHubHost("github.com");
        try
        {
            credentialHandler =
                GetCredentialHandler(gitHubHost.GetHost(), gitHubHost.GetProjectNameFromUrl(url), credentials);
        }
        catch (ValidationException e)
        {
            throw new EvalException("Cannot parse url", e);
        }

        // TODO(port): reconcile — GitHubTrigger provided by a peer.
        return new global::Copybara.Git.GitHubTrigger(
            gitHubOptions.NewGitHubApiSupplier(url, checker, credentialHandler, gitHubHost),
            url,
            parsedEvents,
            GetGeneralConsole(),
            gitHubHost,
            credentialHandler);
    }

    private ImmutableHashSet<EventTrigger> HandleEventTypes(
        object events, List<EventTrigger> eventBuilder, HashSet<GitHubEventType> types)
    {
        if (events is StarlarkSequence)
        {
            foreach (var e in SkylarkUtil.ConvertStringList(events, "events"))
            {
                var evt = SkylarkUtil.StringToEnum<GitHubEventType>("events", e);
                var trigger = EventTrigger.Create(evt, ImmutableHashSet<string>.Empty);
                SkylarkUtil.Check(!eventBuilder.Contains(trigger), "Repeated element {0}", e);
                eventBuilder.Add(trigger);
            }
        }
        else if (events is Dict dict)
        {
            foreach (var entry in dict.Entries)
            {
                string key = (string)entry.Key!;
                SkylarkUtil.Check(
                    types.Add(SkylarkUtil.StringToEnum<GitHubEventType>("events", key)),
                    "Repeated element {0}",
                    entry);
                var values = SkylarkUtil.ConvertStringList(entry.Value, "events");
                eventBuilder.Add(
                    EventTrigger.Create(
                        SkylarkUtil.StringToEnum<GitHubEventType>("events", key),
                        values.ToImmutableHashSet()));
            }
        }

        foreach (var trigger in eventBuilder)
        {
            SkylarkUtil.Check(
                GitHubEventTypes.WatchableEvents.Contains(trigger.Type()),
                "{0} is not a valid value. Values: {1}",
                trigger.Type(),
                string.Join(", ", GitHubEventTypes.WatchableEvents));
        }

        SkylarkUtil.Check(eventBuilder.Count != 0, "events cannot be empty");
        return eventBuilder.ToImmutableHashSet();
    }

    [StarlarkMethod("review_input",
        Doc = "Creates a review to be posted on Gerrit.")]
    public SetReviewInput ReviewInput(
        [Param(Name = "labels", Named = true, DefaultValue = "{}", Doc = "The labels to post.")]
        Dict labels,
        [Param(Name = "message", Named = true, DefaultValue = "None",
            Doc = "The message to be added as review comment.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object message,
        [Param(Name = "tag", Named = true, DefaultValue = "None",
            Doc = "Tag to be applied to the review, for instance 'autogenerated:copybara'.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object tag,
        [Param(Name = "notify", Named = true, DefaultValue = "'ALL'",
            Doc = "Notify setting, defaults to 'ALL'",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object notify)
    {
        var copy = ImmutableDictionary.CreateBuilder<string, int>();
        foreach (var e in labels.Entries)
        {
            if (e.Key is not string key)
            {
                throw StarlarkRt.Errorf(
                    "Gerrit review labels: in dict key, got {0}, want string", StarlarkRt.Type(e.Key));
            }

            if (e.Value is not StarlarkInt si)
            {
                throw StarlarkRt.Errorf(
                    "Gerrit review labels: in value for dict key '{0}', got {1}, want int",
                    key, StarlarkRt.Type(e.Value));
            }

            copy[key] = si.ToInt("element of Gerrit review labels");
        }

        string notifyStr = SkylarkUtil.ConvertFromNoneable(notify, "ALL")!;
        if (!Enum.TryParse<NotifyType>(notifyStr, out var notifyVal) || !Enum.IsDefined(notifyVal))
        {
            throw new EvalException(
                $"{notifyStr} is not a valid NotifyType, valid values are: "
                    + string.Join(", ", Enum.GetNames<NotifyType>()));
        }

        return SetReviewInput.Create(
            SkylarkUtil.ConvertFromNoneable<string?>(message, null),
            copy.ToImmutable(),
            SkylarkUtil.ConvertFromNoneable<string?>(tag, null),
            notifyVal);
    }

    [StarlarkMethod("latest_version",
        Doc =
            "DEPRECATED: Use core.latest_version.\n\n"
            + "Customize what version of the available branches and tags to pick.",
        UseStarlarkThread = true)]
    public IVersionSelector VersionSelectorMethod(
        [Param(Name = "refspec_format", Named = true, DefaultValue = "\"refs/tags/${n0}.${n1}.${n2}\"",
            Doc = "The format of the branch/tag")]
        string refspec,
        [Param(Name = "refspec_groups", Named = true,
            DefaultValue = "{'n0' : '[0-9]+', 'n1' : '[0-9]+', 'n2' : '[0-9]+'}",
            Doc = "A set of named regexes that can be used to match part of the versions.")]
        Dict groups,
        StarlarkThread thread)
    {
        var groupsMap = SkylarkUtil.ConvertStringMap(groups, "refspec_groups");
        var elements = new SortedDictionary<int, LatestVersionSelector.VersionElementType>();
        var regexKey = new Regex("^([sn])([0-9])$");
        foreach (var s in groupsMap.Keys)
        {
            var matcher = regexKey.Match(s);
            SkylarkUtil.Check(
                matcher.Success,
                "Incorrect key for refspec_group. Should be in the "
                    + "format of n0, n1, etc. or s0, s1, etc. Value: {0}",
                s);
            var type = matcher.Groups[1].Value == "s"
                ? LatestVersionSelector.VersionElementType.ALPHABETIC
                : LatestVersionSelector.VersionElementType.NUMERIC;
            int num = int.Parse(matcher.Groups[2].Value);
            SkylarkUtil.Check(
                !elements.ContainsKey(num) || elements[num] == type,
                "Cannot use same n in both s{0} and n{1}: {2}",
                num,
                num,
                s);
            elements[num] = type;
        }

        foreach (var num in elements.Keys)
        {
            if (num > 0)
            {
                SkylarkUtil.Check(
                    elements.ContainsKey(num - 1),
                    "Cannot have s{0} or n{1} if s{2} or n{3} doesn't exist",
                    num,
                    num,
                    num - 1,
                    num - 1);
            }
        }

        var versionPicker =
            new LatestVersionSelector(
                refspec, Replace.ParsePatterns(groupsMap), elements, thread.GetCallerLocation());
        var extraGroups = versionPicker.GetUnmatchedGroups();
        SkylarkUtil.Check(extraGroups.Count == 0, "Extra refspec_groups not used in pattern: {0}", extraGroups);

        var generalOptions = Options.Get<GeneralOptions>();
        if (generalOptions.IsForced() || generalOptions.IsVersionSelectorUseCliRef())
        {
            return new OrderedVersionSelector(
                ImmutableArray.Create<IVersionSelector>(
                    new RequestedVersionSelector(), versionPicker));
        }

        return versionPicker;
    }

    public void SetConfigFile(ConfigFile mainConfigFile, ConfigFile currentConfigFile) =>
        MainConfigFile = mainConfigFile;

    public void SetWorkflowName(string workflowName) => _workflowName = workflowName;

    public void SetPrintHandler(StarlarkThread.PrintHandler printHandler) => _printHandler = printHandler;

    private string FixHttp(string url, Location location)
    {
        try
        {
            ValidateNotHttp(url);
        }
        catch (ValidationException)
        {
            string fixed_ = "https" + url.Substring("http".Length);
            GetGeneralConsole()
                .WarnFmt(
                    "{0}: Url '{1}' does not use https - please change the URL. Proceeding with '{2}'.",
                    location, url, fixed_);
            return fixed_;
        }

        return url;
    }

    // Port of RepositoryUtil.validateNotHttp.
    // TODO(port): reconcile — move to a shared Copybara.Util.RepositoryUtil once ported.
    private static void ValidateNotHttp(string url)
    {
        if (url.StartsWith("http://"))
        {
            throw new ValidationException(
                "URL '" + url + "' is not using https. This is unsafe. Please use https.");
        }
    }

    /// <summary>Do not use this for github origins.</summary>
    protected IApprovalsProvider ApprovalsProvider(string url)
    {
        var gitHubHost = new GitHubHost("github.com");
        Preconditions.CheckArgument(
            !gitHubHost.IsGitHubUrl(url),
            "Git origins with github should use github approval providers!");
        return Options.Get<GitOriginOptions>().ApprovalsProvider;
    }

    // TODO(port): reconcile — GitHub approvals providers (GitHubPreSubmitApprovalsProvider,
    // GitHubPostSubmitApprovalsProvider, GitHubSecuritySettingsValidator,
    // GitHubUserApprovalsValidator, GetCommitHistoryParams) are being ported concurrently by peers.
    protected IApprovalsProvider GitHubPreSubmitApprovalsProvider(string url, CredentialFileHandler? creds)
    {
        var gitHubHost = new GitHubHost("github.com");
        var generalOptions = Options.Get<GeneralOptions>();
        var githubOptions = Options.Get<GitHubOptions>();
        return new GitHubPreSubmitApprovalsProvider(
            githubOptions,
            gitHubHost,
            new GitHubSecuritySettingsValidator(
                githubOptions.NewGitHubApiSupplier(url, null, creds, gitHubHost),
                githubOptions.AllStarAppIds.ToImmutableArray(),
                generalOptions.GetConsole()),
            new GitHubUserApprovalsValidator(
                githubOptions.NewGitHubApiSupplier(url, null, creds, gitHubHost),
                githubOptions.NewGitHubGraphQLApiSupplier(url, null, creds, gitHubHost),
                generalOptions.GetConsole(),
                gitHubHost,
                new GitHubGraphQLApi.GetCommitHistoryParams(
                    githubOptions.GqlOverride[0],
                    githubOptions.GqlOverride[1],
                    githubOptions.GqlOverride[2])),
            creds);
    }

    protected IApprovalsProvider GitHubPostSubmitApprovalsProvider(
        string url, string? branch, CredentialFileHandler? creds)
    {
        var gitHubHost = new GitHubHost("github.com");
        var generalOptions = Options.Get<GeneralOptions>();
        var githubOptions = Options.Get<GitHubOptions>();
        return new GitHubPostSubmitApprovalsProvider(
            gitHubHost,
            branch,
            new GitHubSecuritySettingsValidator(
                githubOptions.NewGitHubApiSupplier(url, null, creds, gitHubHost),
                githubOptions.AllStarAppIds.ToImmutableArray(),
                generalOptions.GetConsole()),
            new GitHubUserApprovalsValidator(
                githubOptions.NewGitHubApiSupplier(url, null, creds, gitHubHost),
                githubOptions.NewGitHubGraphQLApiSupplier(url, null, creds, gitHubHost),
                generalOptions.GetConsole(),
                gitHubHost,
                new GitHubGraphQLApi.GetCommitHistoryParams(
                    githubOptions.GqlOverride[0],
                    githubOptions.GqlOverride[1],
                    githubOptions.GqlOverride[2])));
    }

    /// <summary>Validates the <see cref="IChecker"/> provided to a feedback endpoint.</summary>
    protected virtual void ValidateEndpointChecker(IChecker? checker, string functionName)
    {
    }

    private void CheckSubmoduleConfig(string submodules, List<string> excludedSubmodules) =>
        SkylarkUtil.Check(
            submodules != "NO" || excludedSubmodules.Count == 0,
            "Expected excluded submodule list to be empty when submodules is NO, but got {0}",
            string.Join(", ", excludedSubmodules));

    // TODO(port): reconcile — Mirror endpoint provider uses LazyResourceLoader<EndpointProvider<?>>.
    // The lazy loader type is being ported by a peer; the callable-based shape below mirrors the Java.
    protected LazyResourceLoader<IEndpointProvider>? GetEndpointProvider(
        string? url, IChecker? checker, CredentialFileHandler? creds, bool allowSubmit, StarlarkThread thread)
    {
        if (url == null)
        {
            return null;
        }

        var gerritApiLoader = MaybeGetGerritApi(url, checker, allowSubmit, thread);
        if (gerritApiLoader != null)
        {
            return gerritApiLoader;
        }

        var githubApiLoader = MaybeGetGitHubApi(url, checker, creds, thread);
        if (githubApiLoader != null)
        {
            return githubApiLoader;
        }

        return null;
    }

    protected LazyResourceLoader<IEndpointProvider>? MaybeGetGerritApi(
        string url, IChecker? checker, bool allowSubmit, StarlarkThread thread)
    {
        string? host = new Uri(url).Host;
        if (string.IsNullOrEmpty(host) || !host.EndsWith(".googlesource.com"))
        {
            return null;
        }

        return LazyResourceLoader.Memoized<IEndpointProvider>(console =>
        {
            try
            {
                return GerritApi(url, checker!, allowSubmit, thread);
            }
            catch (EvalException e)
            {
                throw new ValidationException(
                    string.Format(
                        "Detected a gerrit repository URL, but was not able to construct a Gerrit API"
                            + " loader. Error = '{0}'",
                        e.Message),
                    e);
            }
        });
    }

    protected LazyResourceLoader<IEndpointProvider>? MaybeGetGitHubApi(
        string url, IChecker? checker, CredentialFileHandler? creds, StarlarkThread thread)
    {
        var gitHubHost = new GitHubHost("github.com");
        if (!gitHubHost.IsGitHubUrl(url))
        {
            return null;
        }

        return LazyResourceLoader.Memoized<IEndpointProvider>(console =>
        {
            try
            {
                return GithubApi(url, checker!, creds, thread);
            }
            catch (EvalException e)
            {
                throw new ValidationException(
                    string.Format(
                        "Detected a GitHub repository URL, but was not able to construct a GitHub API"
                            + " loader. Error = '{0}'",
                        e.Message),
                    e);
            }
        });
    }

    protected CredentialFileHandler? GetCredentialHandler(string host, string path, object? starlarkValue)
    {
        var issuer = SkylarkUtil.ConvertFromNoneable<UsernamePasswordIssuer?>(starlarkValue, null);
        if (issuer == null)
        {
            return null;
        }

        return new CredentialFileHandler(
            host,
            Regex.Replace(path, "^/+", ""),
            issuer.Username,
            issuer.Password,
            Options.Get<GitOptions>().UseConfigCredentials);
    }

    protected CredentialFileHandler? GetCredentialHandler(string url, object? starlarkValue)
    {
        var gitHubHost = new GitHubHost("github.com");
        try
        {
            if (gitHubHost.IsGitHubUrl(url))
            {
                url = gitHubHost.NormalizeUrl(url);
            }

            var uri = new Uri(url);
            return GetCredentialHandler(uri.Host, uri.AbsolutePath, starlarkValue);
        }
        catch (Exception parseEx) when (parseEx is ValidationException or ArgumentException or UriFormatException)
        {
            Options.Get<GeneralOptions>().GetConsole().VerboseFmt("Unable to parse {0} as URI", url);
        }

        return null;
    }

    // TODO(linjordan): Remove this method once the experiment is fully rolled out.
    protected bool IsGitRepositoryHookExperimentEnabled() =>
        Options.Get<GeneralOptions>().IsTemporaryFeature("enable_git_repository_hook_experiment", true);

    protected IGitRepositoryHook? MaybeGetGitRepositoryHook(IGitRepositoryHook.GitRepositoryData gitRepositoryData)
    {
        var gitHubHost = new GitHubHost("github.com");
        if (!IsGitRepositoryHookExperimentEnabled())
        {
            return null;
        }

        if (gitHubHost.IsGitHubUrl(gitRepositoryData.Url))
        {
            // TODO(port): reconcile — GitHubOptions.GetGitRepositoryHook provided by a peer.
            return Options.Get<GitHubOptions>().GetGitRepositoryHook(gitRepositoryData, null);
        }

        return null;
    }
}
