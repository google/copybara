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
using Copybara.Approval;
using Copybara.Authoring;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Revision;
using Copybara.Util;
using Copybara.Version;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Git;

/// <summary>
/// A class for manipulating Git repositories as an origin. Port of
/// <c>com.google.copybara.git.GitOrigin</c>.
/// </summary>
public class GitOrigin : IOrigin<GitRevision>
{
    /// <summary>A temporary ref used locally, for Git commands that need one (like rebase).</summary>
    private const string CopybaraTmpRef = "refs/heads/copybara_dont_use_internal";

    // Mirrors GitModule.PRIMARY_BRANCHES.
    private static readonly ImmutableHashSet<string> PrimaryBranches =
        ImmutableHashSet.Create("master", "main");

    private static readonly ImmutableArray<string> RefPrefixes =
        ImmutableArray.Create("refs/heads/", "refs/tags/");

    /// <summary>How downloading submodules should be handled by Git origins.</summary>
    public enum SubmoduleStrategy
    {
        /// <summary>Don't download any submodule.</summary>
        No,

        /// <summary>Download just the first level of submodules, but don't download recursively.</summary>
        Yes,

        /// <summary>Download all the submodules recursively.</summary>
        Recursive,
    }

    /// <summary>Url of the repository.</summary>
    internal readonly string RepoUrl;

    private string? _resolvedRef;

    private readonly string? _configRef;
    private readonly Console _console;
    private readonly GeneralOptions _generalOptions;
    private readonly GitRepoType _repoType;
    private readonly GitOptions _gitOptions;
    private readonly GitOriginOptions _gitOriginOptions;
    private readonly SubmoduleStrategy _submoduleStrategy;
    private readonly IReadOnlyList<string> _excludedSubmodules;
    private readonly bool _includeBranchCommitLogs;
    internal bool FirstParent;
    private readonly bool _partialFetch;
    private readonly ITransformation? _patchTransformation;
    protected readonly bool DescribeVersion;
    private readonly IVersionSelector? _versionSelector;
    private readonly string? _configPath;
    private readonly string? _workflowName;
    protected readonly bool PrimaryBranchMigrationMode;
    private readonly IApprovalsProvider _approvalsProvider;
    private readonly bool _enableLfs;
    private readonly CredentialFileHandler? _credentials;
    protected readonly IGitRepositoryHook? GitRepositoryHook;

    internal GitOrigin(
        GeneralOptions generalOptions,
        string repoUrl,
        string? configRef,
        GitRepoType repoType,
        GitOptions gitOptions,
        GitOriginOptions gitOriginOptions,
        SubmoduleStrategy submoduleStrategy,
        IReadOnlyList<string> excludedSubmodules,
        bool includeBranchCommitLogs,
        bool firstParent,
        bool partialClone,
        ITransformation? patchTransformation,
        bool describeVersion,
        IVersionSelector? versionSelector,
        string? configPath,
        string? workflowName,
        bool primaryBranchMigrationMode,
        IApprovalsProvider approvalsProvider,
        bool enableLfs,
        CredentialFileHandler? credentials,
        IGitRepositoryHook? gitRepositoryHook)
    {
        _generalOptions = generalOptions;
        _console = generalOptions.GetConsole();
        // Remove a possible trailing '/' so that the url is normalized.
        Preconditions.CheckNotNull(repoUrl);
        RepoUrl = repoUrl.EndsWith('/') ? repoUrl.Substring(0, repoUrl.Length - 1) : repoUrl;
        _configRef = configRef;
        _repoType = repoType;
        _gitOptions = Preconditions.CheckNotNull(gitOptions);
        _gitOriginOptions = Preconditions.CheckNotNull(gitOriginOptions);
        _submoduleStrategy = submoduleStrategy;
        _excludedSubmodules = excludedSubmodules;
        _includeBranchCommitLogs = includeBranchCommitLogs;
        FirstParent = firstParent;
        _partialFetch = partialClone;
        _patchTransformation = patchTransformation;
        DescribeVersion = describeVersion;
        _versionSelector = versionSelector;
        _configPath = configPath;
        _workflowName = workflowName;
        PrimaryBranchMigrationMode = primaryBranchMigrationMode;
        _approvalsProvider = approvalsProvider;
        _enableLfs = enableLfs;
        _credentials = credentials;
        GitRepositoryHook = gitRepositoryHook;
    }

    public GitRepository GetRepository()
    {
        GitRepository repo;
        if (_partialFetch)
        {
            string prefixedRepoUrl = $"{_configPath}:{_workflowName}{RepoUrl}";
            repo = _gitOptions.CachedBareRepoForUrl(prefixedRepoUrl).EnablePartialFetch();
        }
        else
        {
            repo = _gitOptions.CachedBareRepoForUrl(RepoUrl);
        }
        if (_enableLfs)
        {
            repo.SetRemoteOriginUrl(RepoUrl);
        }
        if (_credentials != null)
        {
            try
            {
                _credentials.Install(repo, _gitOptions.GetConfigCredsFile(_generalOptions));
            }
            catch (IOException e)
            {
                throw new RepoException("Unable to store credentials", e);
            }
        }
        return repo;
    }

    public IApprovalsProvider GetApprovalsProvider() => _approvalsProvider;

    public virtual IOrigin<GitRevision>.IReader<GitRevision> NewReader(
        Glob originFiles, Authoring.Authoring authoring) =>
        new ReaderImpl(
            RepoUrl,
            originFiles,
            authoring,
            _gitOptions,
            _gitOriginOptions,
            _generalOptions,
            _includeBranchCommitLogs,
            _submoduleStrategy,
            _excludedSubmodules,
            FirstParent,
            _partialFetch,
            _patchTransformation,
            _configPath,
            _workflowName,
            _credentials,
            GitRepositoryHook);

    public virtual GitRevision Resolve(string? reference)
    {
        _console.Progress("Git Origin: Initializing local repo");
        string? @ref;
        bool canUseResolverOnCliRef =
            _generalOptions.IsVersionSelectorUseCliRef() || _generalOptions.IsForced();

        if (_gitOriginOptions.UseGitVersionSelector() && _versionSelector != null)
        {
            if (canUseResolverOnCliRef && !string.IsNullOrEmpty(reference))
            {
                _console.WarnFmt(
                    "Ignoring git.version_selector as {0} or {1} is being used. Using cli ref {2}"
                        + " instead.",
                    GeneralOptions.Force,
                    "--version-selector-use-cli-ref",
                    reference);
                @ref = reference;
            }
            else
            {
                GitRepository repository = GetRepository();
                var specs = GetVersionSelectorRefspec(repository);
                var list = new Version.RefspecVersionList(repository, specs, RepoUrl);
                foreach (var prefix in RefPrefixes)
                {
                    if (reference != null && list.List().Contains(prefix + reference))
                    {
                        reference = prefix + reference;
                    }
                }
                string? res = _versionSelector.Select(list, reference, _console);
                ValidationException.CheckCondition(
                    res != null,
                    "Cannot find any matching version for latest_version expression {0}.\n\n"
                        + "Please run 'git ls-remote {1}' to obtain a list of references that are"
                        + " present in the remote repo.\n",
                    _versionSelector,
                    RepoUrl);
                @ref = res!;
                // It is rare that a branch and a tag has the same name. The reason for this is that
                // destinations expect that the context_reference is a non-full reference. Also it is
                // more readable when we use it in transformations.
                foreach (var prefix in RefPrefixes)
                {
                    if (@ref.StartsWith(prefix, StringComparison.Ordinal))
                    {
                        @ref = @ref.Substring(prefix.Length);
                    }
                }
            }
        }
        else if (string.IsNullOrEmpty(reference))
        {
            ValidationException.CheckCondition(
                GetConfigRef() != null,
                "No reference was passed as a command line argument for {0} and no default"
                    + " reference was configured in the config file",
                RepoUrl);
            @ref = GetConfigRef();
        }
        else
        {
            @ref = reference;
        }

        return ResolveStringRef(@ref!);
    }

    public GitRevision ResolveAncestorRef(string ancestorRef, GitRevision descendantRev) =>
        ResolveAncestorRef(this, GetRepository(), ancestorRef, descendantRev);

    /// <summary>
    /// Resolves a reference into a revision, but only if the provided descendantRev is an ancestor
    /// of ancestorRef.
    /// </summary>
    public static GitRevision ResolveAncestorRef(
        IOrigin<GitRevision> gitOrigin,
        GitRepository gitRepository,
        string ancestorRef,
        GitRevision descendantRev)
    {
        if (!gitRepository.IsAncestor(ancestorRef, descendantRev.FixedReference()!))
        {
            throw new ValidationException(
                $"{ancestorRef} is not an ancestor of {descendantRev.AsString()}.");
        }

        GitRevision resolvedRev = gitOrigin.Resolve(ancestorRef);
        if (!string.IsNullOrEmpty(descendantRev.ContextReference()))
        {
            resolvedRev = resolvedRev.WithContextReference(descendantRev.ContextReference()!);
        }

        return resolvedRev;
    }

    private GitRevision ResolveStringRef(string @ref)
    {
        GitRevision gitRevision = _repoType.ResolveRef(
            GetRepository(),
            RepoUrl,
            @ref,
            _generalOptions,
            DescribeVersion,
            _partialFetch,
            _gitOptions.GetFetchDepth());
        if (!DescribeVersion)
        {
            return gitRevision;
        }

        string? describeAsTag =
            _generalOptions.IsTemporaryFeature("SHA1_AS_TAG", true)
                ? GetRepository().DescribeExactMatch(gitRevision)
                : null;
        return gitRevision.ContextReference() == null
            ? GetRepository().AddDescribeVersion(gitRevision)
                .WithContextReference(describeAsTag ?? "")
            : GetRepository().AddDescribeVersion(gitRevision);
    }

    public GitRevision ResolveLastRev(string @ref)
    {
        if (_gitOriginOptions.UseGitFuzzyLastRev())
        {
            var selector = new FuzzyClosestVersionSelector();
            @ref = selector.SelectVersion(@ref, GetRepository(), RepoUrl, _generalOptions.GetConsole());
        }
        return ResolveStringRef(@ref);
    }

    public string? ShowDiff(GitRevision revisionFrom, GitRevision revisionTo) =>
        GetRepository().ShowDiff(revisionFrom.GetHash(), revisionTo.GetHash());

    internal class ReaderImpl : IOrigin<GitRevision>.IReader<GitRevision>
    {
        private readonly string _repoUrl;
        internal readonly Glob OriginFiles;
        internal readonly Authoring.Authoring Authoring;
        private readonly GitOptions _gitOptions;
        private readonly GitOriginOptions _gitOriginOptions;
        private readonly GeneralOptions _generalOptions;
        private readonly bool _includeBranchCommitLogs;
        private readonly SubmoduleStrategy _submoduleStrategy;
        private readonly IReadOnlyList<string> _excludedSubmodules;
        private readonly bool _firstParent;
        private readonly bool _partialFetch;
        private readonly ITransformation? _patchTransformation;
        private readonly string? _configPath;
        private readonly string? _workflowName;
        private readonly CredentialFileHandler? _credentials;
        private readonly IGitRepositoryHook? _gitRepositoryHook;

        internal ReaderImpl(
            string repoUrl,
            Glob originFiles,
            Authoring.Authoring authoring,
            GitOptions gitOptions,
            GitOriginOptions gitOriginOptions,
            GeneralOptions generalOptions,
            bool includeBranchCommitLogs,
            SubmoduleStrategy submoduleStrategy,
            IReadOnlyList<string> excludedSubmodules,
            bool firstParent,
            bool partialFetch,
            ITransformation? patchTransformation,
            string? configPath,
            string? workflowName,
            CredentialFileHandler? credentials,
            IGitRepositoryHook? gitRepositoryHook)
        {
            _repoUrl = Preconditions.CheckNotNull(repoUrl);
            OriginFiles = Preconditions.CheckNotNull(originFiles, "originFiles");
            Authoring = Preconditions.CheckNotNull(authoring, "authoring");
            _gitOptions = Preconditions.CheckNotNull(gitOptions);
            _gitOriginOptions = gitOriginOptions;
            _generalOptions = Preconditions.CheckNotNull(generalOptions);
            _includeBranchCommitLogs = includeBranchCommitLogs;
            _submoduleStrategy = submoduleStrategy;
            _excludedSubmodules = excludedSubmodules;
            _firstParent = firstParent;
            _partialFetch = partialFetch;
            _patchTransformation = patchTransformation;
            _configPath = configPath;
            _workflowName = workflowName;
            _credentials = credentials;
            _gitRepositoryHook = gitRepositoryHook;
        }

        internal ChangeReader.Builder ChangeReaderBuilder(string repoUrl) =>
            ChangeReader.Builder
                .ForOrigin(Authoring, GetRepository(), _generalOptions.GetConsole())
                .SetIncludeBranchCommitLogs(_includeBranchCommitLogs)
                .SetRoots(OriginFiles.Roots(allowFiles: true))
                .SetPartialFetch(_partialFetch)
                .SetBatchSize(_gitOriginOptions.GitOriginLogBatchSize)
                .SetUrl(repoUrl);

        internal GitRepository GetRepository()
        {
            GitRepository repo;
            if (_partialFetch)
            {
                string prefixedRepoUrl = $"{_configPath}:{_workflowName}{_repoUrl}";
                repo = _gitOptions
                    .CachedBareRepoForUrl(prefixedRepoUrl, _gitRepositoryHook)
                    .EnablePartialFetch();
            }
            else
            {
                repo = _gitOptions.CachedBareRepoForUrl(_repoUrl, _gitRepositoryHook);
            }
            if (_credentials != null)
            {
                try
                {
                    string credentialHelper = _gitOptions.GetConfigCredsFile(_generalOptions);
                    _credentials.Install(repo, credentialHelper);
                }
                catch (IOException e)
                {
                    throw new RepoException("Unable to store credentials", e);
                }
            }
            return repo;
        }

        /// <summary>
        /// Creates a worktree with the contents of the git reference. Any content in the workdir is
        /// removed/overwritten.
        /// </summary>
        public void Checkout(GitRevision reference, string checkoutDir)
        {
            CheckoutRepo(
                GetRepository(), _repoUrl, checkoutDir, _submoduleStrategy, reference,
                topLevelCheckout: true);
            _gitOriginOptions.MaybeRunCheckoutHook(checkoutDir, _generalOptions);
            if (_patchTransformation != null)
            {
                _generalOptions.GetConsole().Progress("Patching the checkout directory");
                // TODO(peer): PatchTransformation.patch() is owned by the transform/patch peer port.
                // Wire it up once that type is available.
            }
        }

        private GitRepository CheckoutWorktree(
            GitRepository repository, string workdir, GitRevision reference)
        {
            GitRepository repo = repository.WithWorkTree(workdir);
            if (_partialFetch)
            {
                repo.SetSparseCheckout(OriginFiles.Tips());
                repo.ForceCheckout(reference.GetHash(), _generalOptions.CommandsTimeout);
                return repo;
            }
            repo.ForceCheckout(
                reference.GetHash(),
                _gitOptions.ExperimentCheckoutAffectedFiles
                    ? OriginFiles.Roots()
                    : ImmutableHashSet<string>.Empty,
                _generalOptions.CommandsTimeout);
            return repo;
        }

        /// <summary>Checks out the repository, and rebases to a ref if necessary.</summary>
        internal void CheckoutRepo(
            GitRepository repository,
            string currentRemoteUrl,
            string workdir,
            SubmoduleStrategy submoduleStrategy,
            GitRevision reference,
            bool topLevelCheckout)
        {
            if (_includeBranchCommitLogs)
            {
                _generalOptions.GetConsole().WarnFmt(
                    "'include_branch_commit_logs' is deprecated. Use first_parent = False instead."
                        + " metadata.squash_notes and metadata.use_last_change don't include merge"
                        + " commits by default");
            }
            GitRepository repo = CheckoutWorktree(repository, workdir, reference);
            if (topLevelCheckout)
            {
                MaybeRebase(repo, reference, workdir);
            }

            if (submoduleStrategy == SubmoduleStrategy.No)
            {
                return;
            }
            foreach (var submodule in repo.ListSubmodules(currentRemoteUrl, reference))
            {
                if (_excludedSubmodules.Contains(submodule.Name))
                {
                    _generalOptions.GetConsole().InfoFmt(
                        "Submodule '{0}' is excluded, skipping checkout", submodule.Name);
                    continue;
                }

                var elements = repo.LsTree(reference, submodule.Path, false, false);
                if (elements.Count != 1)
                {
                    throw new RepoException(
                        $"Cannot find one tree element for submodule {submodule.Path}. Found the"
                            + $" following elements: {string.Join(", ", elements)}");
                }
                var element = elements[0];
                Preconditions.CheckArgument(element.Path == submodule.Path);

                _generalOptions.GetConsole().VerboseFmt(
                    "Checking out submodule '{0}' with reference '{1}'", submodule, element.Ref);
                string submoduleUrl = _gitOptions.RewriteSubmoduleUrl(submodule.Url);
                GitRepository subRepo = _gitOptions.CachedBareRepoForUrl(submoduleUrl);

                if (submodule.Branch != null)
                {
                    subRepo.FetchSingleRef(submoduleUrl, submodule.Branch, _partialFetch, null);
                }
                else
                {
                    subRepo.Fetch(
                        submoduleUrl,
                        prune: true,
                        force: true,
                        new[] { "refs/heads/*:refs/heads/*", "refs/tags/*:refs/tags/*" },
                        _partialFetch,
                        depth: null,
                        tags: false);
                }
                GitRevision submoduleRef =
                    subRepo.ResolveReferenceWithContext(element.Ref, submodule.Name, submoduleUrl);

                string subdir = Path.Combine(workdir, submodule.Path);
                try
                {
                    Directory.CreateDirectory(subdir);
                }
                catch (IOException)
                {
                    throw new RepoException(
                        $"Cannot create subdirectory {subdir} for submodule: {submodule}");
                }

                CheckoutRepo(
                    subRepo,
                    submoduleUrl,
                    subdir,
                    submoduleStrategy == SubmoduleStrategy.Recursive
                        ? SubmoduleStrategy.Recursive
                        : SubmoduleStrategy.No,
                    submoduleRef,
                    topLevelCheckout: false);
            }
        }

        protected virtual void MaybeRebase(GitRepository repo, GitRevision reference, string workdir)
        {
            string? rebaseToRef = _gitOriginOptions.OriginRebaseRef;
            if (rebaseToRef == null)
            {
                return;
            }
            _generalOptions.GetConsole().Info($"Rebasing {rebaseToRef} to {rebaseToRef}");
            GitRevision rebaseRev =
                repo.FetchSingleRef(_repoUrl, rebaseToRef, _partialFetch, null);
            repo.SimpleCommand("update-ref", CopybaraTmpRef, rebaseRev.GetHash());
            repo.RebaseCmdFor(CopybaraTmpRef)
                .ErrorAdvice(
                    "Please consider not using the flag --git-origin-rebase-ref as a workaround")
                .Run();
        }

        public virtual Origin.ChangesResponse<GitRevision> Changes(
            GitRevision? fromRef, GitRevision toRef)
        {
            ChangeReader changeReader = ChangeReaderBuilder(_repoUrl)
                .SetFirstParent(_firstParent)
                .SetTopoOrder(_gitOriginOptions.HistoryIsNonLinear)
                .Build();
            var labelsToPropagate =
                new Dictionary<string, ImmutableListMultimap<string, string>>
                {
                    [toRef.GetHash()] = toRef.AssociatedLabels(),
                };
            var gitChanges = changeReader.Run(
                fromRef, toRef, _gitOriginOptions.HistoryIsNonLinear, labelsToPropagate);
            if (_gitOriginOptions.HistoryIsNonLinear && fromRef != null)
            {
                gitChanges = gitChanges
                    .SkipWhile(c => c.GetRevision().GetHash() != fromRef.GetHash())
                    .Skip(1)
                    .ToList();
            }
            if (gitChanges.Count != 0)
            {
                return Origin.ChangesResponse<GitRevision>.ForChangesWithMerges(gitChanges);
            }
            if (fromRef == null)
            {
                return Origin.ChangesResponse<GitRevision>.NoChanges(Origin.EmptyReason.NoChanges);
            }
            if (fromRef.GetHash() == toRef.GetHash()
                || GetRepository().IsAncestor(toRef.GetHash(), fromRef.GetHash()))
            {
                return Origin.ChangesResponse<GitRevision>.NoChanges(Origin.EmptyReason.ToIsAncestor);
            }
            if (GetRepository().IsAncestor(fromRef.GetHash(), toRef.GetHash()))
            {
                return Origin.ChangesResponse<GitRevision>.NoChanges(Origin.EmptyReason.NoChanges);
            }
            return Origin.ChangesResponse<GitRevision>.NoChanges(
                Origin.EmptyReason.UnrelatedRevisions);
        }

        public Change<GitRevision> Change(GitRevision reference)
        {
            // The limit=1 flag guarantees that only one change is returned.
            ChangeReader changeReader = ChangeReaderBuilder(_repoUrl)
                .SetLimit(1)
                .SetFirstParent(_firstParent)
                .Build();
            var changes = changeReader.Run(reference);

            if (changes.Count == 0)
            {
                throw new EmptyChangeException(
                    $"'{reference.AsString()}' revision cannot be found in the origin or it didn't"
                        + " affect the origin paths.");
            }
            // 'git log -1 -m' for a merge commit returns two entries.
            Change<GitRevision> rev = changes[0];
            return new Change<GitRevision>(
                    reference,
                    rev.GetAuthor(),
                    rev.GetMessage(),
                    rev.GetDateTime(),
                    rev.GetLabels(),
                    rev.GetChangeFiles(),
                    rev.IsMerge(),
                    rev.GetParents())
                .WithLabels(reference.AssociatedLabels());
        }

        public void VisitChanges(GitRevision? start, IChangesVisitor visitor)
        {
            ChangeReader.Builder queryChanges = ChangeReaderBuilder(_repoUrl)
                .SetFirstParent(_firstParent);
            var roots = OriginFiles.Roots();

            GitVisitorUtil.VisitChanges(
                start!,
                new RootsFilterVisitor(roots, visitor),
                queryChanges,
                _generalOptions,
                "origin",
                _gitOptions.VisitChangePageSize);
        }

        public void VisitChangesWithAnyLabel(
            GitRevision? start,
            IReadOnlyCollection<string> labels,
            IChangesLabelVisitor visitor) =>
            throw new NotSupportedException(
                "visitChangesWithAnyLabel is not implemented for git.origin");

        // Declared here (rather than relying on the interface default) so that subclasses such as
        // GerritOrigin/GitLabMrOrigin readers can override them and have virtual dispatch work
        // through the IReader interface.
        public virtual IReadOnlyList<GitRevision> FindBaselinesWithoutLabel(
            GitRevision startRevision, int limit) =>
            throw new ValidationException("Origin doesn't support this workflow mode");

        public virtual Origin.Baseline<GitRevision>? FindBaseline(
            GitRevision startRevision, string label)
        {
            var visitor = new Origin.FindLatestWithLabel<GitRevision>(startRevision, label);
            VisitChanges(startRevision, visitor);
            return visitor.GetBaseline();
        }

        public virtual IEndpoint GetFeedbackEndPoint(Console console) => IEndpoint.NoopEndpoint;

        // Accessors for subclasses.
        protected string RepoUrl => _repoUrl;

        protected GeneralOptions GeneralOptions => _generalOptions;

        protected bool PartialFetch => _partialFetch;

        public IReadOnlyList<Change<GitRevision>> GetVersions()
        {
            var result = new List<Change<GitRevision>>();

            var output = GetRepository().Log("*").IncludeTags(true).NoWalk(true).Run();

            foreach (var entry in output)
            {
                if (entry.Tag != null)
                {
                    result.Add(
                        new Change<GitRevision>(
                            entry.Tag,
                            entry.Author,
                            entry.Body ?? "",
                            entry.CommitDate,
                            ImmutableListMultimap<string, string>.Empty));
                }
            }

            return result;
        }

        private sealed class RootsFilterVisitor : IChangesVisitor
        {
            private readonly ImmutableHashSet<string> _roots;
            private readonly IChangesVisitor _delegate;

            internal RootsFilterVisitor(ImmutableHashSet<string> roots, IChangesVisitor @delegate)
            {
                _roots = roots;
                _delegate = @delegate;
            }

            public VisitResult Visit(Change<IRevision> input) =>
                Glob.AffectsRoots(_roots, input.GetChangeFiles())
                    ? _delegate.Visit(input)
                    : VisitResult.Continue;
        }
    }

    public string GetLabelName() => GitRepository.GitOriginRevId;

    public override string ToString()
    {
        string repoId =
            GitRepositoryHook != null
                && !string.IsNullOrEmpty(GitRepositoryHook.GetGitRepositoryData().Id)
                ? $", repoId={GitRepositoryHook.GetGitRepositoryData().Id}"
                : "";
        return $"GitOrigin{{repoUrl={RepoUrl}, ref={_configRef}, repoType={_repoType},"
            + $" primaryBranchMigrationMode={PrimaryBranchMigrationMode}{repoId}}}";
    }

    public string GetType() => "git.origin";

    public virtual ImmutableListMultimap<string, string> Describe(Glob? originFiles)
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        builder.Put("type", GetType());
        builder.Put("repoType", _repoType.ToString());
        builder.Put("url", RepoUrl);
        builder.Put("submodules", _submoduleStrategy.ToString());
        builder.Put("primaryBranchMigrationMode", PrimaryBranchMigrationMode.ToString());
        if (originFiles != null
            && !originFiles.Roots().IsEmpty
            && !originFiles.Roots().Contains(""))
        {
            builder.PutAll("root", originFiles.Roots());
        }
        if (_partialFetch)
        {
            builder.Put("partialFetch", _partialFetch.ToString());
        }
        if (_configRef != null)
        {
            builder.Put("ref", _configRef);
        }
        if (_versionSelector != null)
        {
            builder.PutAll("refspec", ToRefspec());
        }
        if (_enableLfs)
        {
            builder.Put("enableLfs", _enableLfs.ToString());
        }
        if (GitRepositoryHook != null
            && !string.IsNullOrEmpty(GitRepositoryHook.GetGitRepositoryData().Id))
        {
            builder.Put("repo_id", GitRepositoryHook.GetGitRepositoryData().Id!);
        }
        return builder.Build();
    }

    private IReadOnlySet<string> ToRefspec()
    {
        var searchPatterns = _versionSelector!.SearchPatterns();
        if (searchPatterns.Any(p => p.IsAll()))
        {
            return ImmutableHashSet.Create("refs/*");
        }
        var refspecs = ImmutableHashSet.CreateBuilder<string>();
        foreach (var searchPattern in searchPatterns)
        {
            if (searchPattern.IsNone())
            {
                continue;
            }
            var patternBuilder = new System.Text.StringBuilder();
            foreach (var token in searchPattern.Tokens())
            {
                if (token.GetTokenType() == TemplateToken.TokenType.Literal)
                {
                    patternBuilder.Append(token.GetValue());
                }
                else
                {
                    // Only support prefixes for now.
                    patternBuilder.Append('*');
                    break;
                }
            }
            string pattern = patternBuilder.ToString();
            if (!pattern.StartsWith("refs/", StringComparison.Ordinal))
            {
                pattern = "refs/*";
            }
            refspecs.Add(pattern);
        }
        return refspecs.ToImmutable();
    }

    private ImmutableArray<Refspec> GetVersionSelectorRefspec(GitRepository repository)
    {
        Preconditions.CheckNotNull(
            _versionSelector,
            "version selector presence should be checked outside of the method call");
        var specs = ImmutableArray.CreateBuilder<Refspec>();
        foreach (var prefix in ToRefspec())
        {
            specs.Add(repository.CreateRefSpec(prefix));
        }
        return specs.ToImmutable();
    }

    private string? GetConfigRef()
    {
        if (_resolvedRef != null)
        {
            return _resolvedRef;
        }
        if (PrimaryBranchMigrationMode && _configRef != null && PrimaryBranches.Contains(_configRef))
        {
            _resolvedRef = GetRepository().GetPrimaryBranch(RepoUrl);
            _console.InfoFmt("Detected primary origin branch '{0}'", _resolvedRef);
        }
        _resolvedRef ??= _configRef;
        return _resolvedRef;
    }

    public IReadOnlyList<ImmutableListMultimap<string, string>> DescribeCredentials()
    {
        if (_credentials == null)
        {
            return ImmutableArray<ImmutableListMultimap<string, string>>.Empty;
        }
        return GitDescribeCredentials.Convert(_credentials.DescribeCredentials());
    }
}

/// <summary>
/// Helper to convert credential descriptions (set multimaps) into the list-multimap shape used by
/// <see cref="IConfigItemDescription.DescribeCredentials()"/>.
/// </summary>
internal static class GitDescribeCredentials
{
    internal static IReadOnlyList<ImmutableListMultimap<string, string>> Convert(
        IReadOnlyList<ImmutableSetMultimap<string, string>> creds)
    {
        var builder = ImmutableArray.CreateBuilder<ImmutableListMultimap<string, string>>();
        foreach (var cred in creds)
        {
            var credBuilder = ImmutableListMultimap<string, string>.CreateBuilder();
            foreach (var entry in cred)
            {
                credBuilder.Put(entry.Key, entry.Value);
            }
            builder.Add(credBuilder.Build());
        }
        return builder.ToImmutable();
    }
}
