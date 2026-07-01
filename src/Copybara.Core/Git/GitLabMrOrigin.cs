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

using System.Collections.Immutable;
using Copybara.Common;
using Copybara.Credentials;
using Copybara.Exceptions;
using Copybara.Git.GitLab;
using Copybara.Git.GitLab.Api;
using Copybara.Git.GitLab.Api.Entities;
using Copybara.Http.Auth;
using Copybara.Revision;
using Copybara.Util;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Git;

/// <summary>
/// An <see cref="IOrigin{R}"/> that reads <see cref="GitRevision"/>s from Merge Requests of a given
/// GitLab Project. Port of <c>com.google.copybara.git.GitLabMrOrigin</c>.
/// </summary>
public sealed class GitLabMrOrigin : IOrigin<GitRevision>
{
    public const string GitLabMrTitle = "GITLAB_MR_TITLE";
    public const string GitLabMrUrl = "GITLAB_MR_URL";
    public const string GitLabMrDescription = "GITLAB_MR_DESCRIPTION";
    internal const string GitLabBaseBranchRef = "GITLAB_BASE_BRANCH_REF";

    private readonly Console _console;
    private readonly UsernamePasswordIssuer? _usernamePasswordIssuer;
    private readonly Uri _repoUrl;
    private readonly GitOptions _gitOptions;
    private readonly GitOriginOptions _gitOriginOptions;
    private readonly GitLabOptions _gitLabOptions;
    private readonly GeneralOptions _generalOptions;
    private readonly CredentialFileHandler? _credentialFileHandler;
    private readonly GitOrigin.SubmoduleStrategy _submoduleStrategy;
    private readonly IReadOnlyList<string> _excludedSubmodules;
    private readonly ITransformation? _patchTransformation;
    private readonly bool _partialFetch;
    private readonly bool _describeVersion;
    private readonly bool _firstParent;
    private readonly bool _useMergeCommit;
    private readonly IGitRepositoryHook? _gitRepositoryHook;

    private GitLabMrOrigin(Builder builder)
    {
        _console = Preconditions.CheckNotNull(builder.Console);
        _usernamePasswordIssuer = builder.UsernamePasswordIssuer;
        _repoUrl = Preconditions.CheckNotNull(builder.RepoUrl);
        _gitOptions = Preconditions.CheckNotNull(builder.GitOptions);
        _gitOriginOptions = Preconditions.CheckNotNull(builder.GitOriginOptions);
        _gitLabOptions = Preconditions.CheckNotNull(builder.GitLabOptions);
        _generalOptions = Preconditions.CheckNotNull(builder.GeneralOptions);
        _credentialFileHandler =
            _usernamePasswordIssuer != null
                ? _gitLabOptions.GetCredentialFileHandler(_repoUrl, _usernamePasswordIssuer)
                : null;
        _submoduleStrategy = builder.SubmoduleStrategy;
        _excludedSubmodules = builder.ExcludedSubmodules;
        _patchTransformation = builder.PatchTransformation;
        _partialFetch = builder.PartialFetch;
        _describeVersion = builder.DescribeVersion;
        _firstParent = builder.FirstParent;
        _useMergeCommit = builder.UseMergeCommit;
        _gitRepositoryHook = builder.GitRepositoryHook;
    }

    private IGitLabApiTransport GetGitLabApiTransport() =>
        GitLabOptions.GetApiTransport(
            _repoUrl.ToString(),
            _gitLabOptions.GetHttpTransportSupplier()(),
            _console,
            _usernamePasswordIssuer != null
                ? new BearerInterceptor(_usernamePasswordIssuer.Password)
                : null);

    public static Builder NewBuilder() => new();

    public GitRevision Resolve(string reference)
    {
        ValidationException.CheckCondition(
            reference != null,
            "A merge request reference is expected as argument in the command line.\n"
                + "Example:\n"
                + "   copybara path/to/copy.bara.sky workflow_name merge_request_number");

        GitLabApi gitLabApi = _gitLabOptions.GetGitLabApi(GetGitLabApiTransport());
        _console.ProgressFmt("Parsing Merge Request reference {0} at {1}", reference!, _repoUrl);
        int mergeRequestId = ParseReference(reference!);

        string urlEncodedProjectPath = GitLabUtil.GetUrlEncodedProjectPath(_repoUrl);
        _console.ProgressFmt("Resolving numeric Project ID for {0}", urlEncodedProjectPath);
        int projectId =
            (gitLabApi.GetProject(urlEncodedProjectPath)
                ?? throw new ValidationException(
                    $"Could not find Project {urlEncodedProjectPath} in {_repoUrl}.")).GetId();

        _console.ProgressFmt(
            "Resolving Merge Request {0} for Project id {1}", mergeRequestId, projectId);
        MergeRequest mergeRequest =
            gitLabApi.GetMergeRequest(projectId, mergeRequestId)
                ?? throw new RepoException(
                    $"Could not get Merge Request info for ID {mergeRequestId}.");

        ValidationException.CheckCondition(
            mergeRequest.GetState() != State.Closed
                && mergeRequest.GetState() != State.Merged,
            "The merge request {0} must not be marked as closed or merged.",
            mergeRequest.GetWebUrl()!);

        _console.ProgressFmt(
            "Fetching Merge Request {0} from origin {1}", mergeRequest.GetIid(), _repoUrl);
        return GetRevisionForMr(mergeRequest);
    }

    public GitRevision ResolveLastRev(string reference)
    {
        reference = reference.Trim();
        string? sha1 = GitRevision.CompleteGitHashPattern.IsMatch(reference) ? reference : null;
        GitRepository repo = GetRepository();

        if (sha1 != null)
        {
            DoFetch(repo, new[] { sha1 });
            return repo.ResolveReference(sha1);
        }
        throw new CannotResolveRevisionException($"'{reference}' is not a valid SHA.");
    }

    private static int ParseReference(string reference)
    {
        // For now, we just support the numeric ID as a reference.
        if (int.TryParse(reference, out int result))
        {
            return result;
        }
        throw new ValidationException(
            $"The merge request reference {reference} is not a valid numeric identifier.");
    }

    private GitRevision GetRevisionForMr(MergeRequest mergeRequest)
    {
        // GitLab produces a merge commit for us, which is the merge result of the MR head and the
        // target branch. If the user wants to use this merge commit, use the appropriate ref.
        string refToUse =
            _useMergeCommit ? GetMrMergeFullRef(mergeRequest) : GetMrHeadFullRef(mergeRequest);
        var refspecs = new List<string> { refToUse + ":" + refToUse };
        // Fetch the source ref as well, which will allow the revision reader to find the baseline
        // later using git merge, if needed.
        refspecs.Add(
            "refs/heads/" + mergeRequest.GetSourceBranch() + ":" + GetMrBaseLocalFullRef(mergeRequest));
        GitRepository repository = GetRepository();
        DoFetch(repository, refspecs);

        return repository
            .ResolveReference(refToUse)
            .WithLabels(GenerateLabels(mergeRequest))
            .WithContextReference(refToUse);
    }

    private void DoFetch(GitRepository repository, IReadOnlyList<string> refspecs)
    {
        using (_generalOptions.Profiler().Start("fetch"))
        {
            repository.Fetch(
                _repoUrl.ToString(),
                prune: false,
                _generalOptions.IsForced(),
                refspecs,
                _partialFetch,
                depth: null,
                tags: false);
        }
    }

    private ImmutableListMultimap<string, string> GenerateLabels(MergeRequest mergeRequest)
    {
        var labels = ImmutableListMultimap<string, string>.CreateBuilder();
        labels.Put(GitLabBaseBranchRef, GetMrBaseLocalFullRef(mergeRequest));
        labels.Put(GitLabMrTitle, mergeRequest.GetTitle() ?? "");
        labels.Put(GitLabMrUrl, mergeRequest.GetWebUrl() ?? "");
        labels.Put(GitLabMrDescription, mergeRequest.GetDescription() ?? "");
        return labels.Build();
    }

    private static string GetMrHeadFullRef(MergeRequest mergeRequest) =>
        "refs/merge-requests/" + mergeRequest.GetIid() + "/head";

    private static string GetMrMergeFullRef(MergeRequest mergeRequest) =>
        "refs/merge-requests/" + mergeRequest.GetIid() + "/merge";

    private static string GetMrBaseLocalFullRef(MergeRequest mergeRequest) =>
        "refs/merge-requests/" + mergeRequest.GetIid() + "/base";

    private GitRepository GetRepository()
    {
        GitRepository repo = _gitOptions.CachedBareRepoForUrl(_repoUrl.ToString());

        if (_credentialFileHandler == null)
        {
            _console.Info("No credentials provided.");
            return repo;
        }

        try
        {
            _credentialFileHandler.Install(repo, _gitOptions.GetConfigCredsFile(_generalOptions));
        }
        catch (IOException e)
        {
            throw new RepoException("Unable to store credentials.", e);
        }
        return repo;
    }

    public ImmutableListMultimap<string, string> Describe(Glob? originFiles)
    {
        var options = ImmutableListMultimap<string, string>.CreateBuilder();
        options.Put("type", GetType());
        options.Put("url", _repoUrl.ToString());
        options.Put("submoduleStrategy", _submoduleStrategy.ToString());
        options.Put("excludedSubmodules", "[" + string.Join(", ", _excludedSubmodules) + "]");
        options.Put("firstParent", _firstParent.ToString());
        options.Put("partialFetch", _partialFetch.ToString());
        options.Put("describeVersion", _describeVersion.ToString());
        options.Put("useMergeCommit", _useMergeCommit.ToString());
        if (originFiles != null
            && !originFiles.Roots().IsEmpty
            && !originFiles.Roots().Contains(""))
        {
            options.PutAll("root", originFiles.Roots());
        }

        return options.Build();
    }

    public IReadOnlyList<ImmutableListMultimap<string, string>> DescribeCredentials() =>
        _usernamePasswordIssuer != null
            ? GitDescribeCredentials.Convert(_usernamePasswordIssuer.DescribeCredentials())
            : ImmutableArray<ImmutableListMultimap<string, string>>.Empty;

    public IOrigin<GitRevision>.IReader<GitRevision> NewReader(
        Glob originFiles, Authoring.Authoring authoring) =>
        new GitLabMrReaderImpl(
            _repoUrl.ToString(),
            originFiles,
            authoring,
            _gitOptions,
            _gitOriginOptions,
            _generalOptions,
            includeBranchCommitLogs: false,
            _submoduleStrategy,
            _excludedSubmodules,
            _firstParent,
            _partialFetch,
            _patchTransformation,
            configPath: null,
            workflowName: null,
            _credentialFileHandler,
            _gitRepositoryHook);

    public string GetLabelName() => GitRepository.GitOriginRevId;

    public string GetType() => "git.gitlab_mr_origin";

    private sealed class GitLabMrReaderImpl : GitOrigin.ReaderImpl
    {
        internal GitLabMrReaderImpl(
            string repoUrl,
            Glob originFiles,
            Authoring.Authoring authoring,
            GitOptions gitOptions,
            GitOriginOptions gitOriginOptions,
            GeneralOptions generalOptions,
            bool includeBranchCommitLogs,
            GitOrigin.SubmoduleStrategy submoduleStrategy,
            IReadOnlyList<string> excludedSubmodules,
            bool firstParent,
            bool partialFetch,
            ITransformation? patchTransformation,
            string? configPath,
            string? workflowName,
            CredentialFileHandler? credentials,
            IGitRepositoryHook? gitRepositoryHook)
            : base(
                repoUrl,
                originFiles,
                authoring,
                gitOptions,
                gitOriginOptions,
                generalOptions,
                includeBranchCommitLogs,
                submoduleStrategy,
                excludedSubmodules,
                firstParent,
                partialFetch,
                patchTransformation,
                configPath,
                workflowName,
                credentials,
                gitRepositoryHook)
        {
        }

        protected override void MaybeRebase(GitRepository repo, GitRevision reference, string workdir)
        {
            // Disable rebase, as this is controlled by useMergeCommit (GitLab does this for us
            // automatically with the merge commit).
        }

        public override IReadOnlyList<GitRevision> FindBaselinesWithoutLabel(
            GitRevision startRevision, int limit)
        {
            GitRepository repository = GetRepository();
            // We have to look at the labels of the revision to get the base branch ref, because the
            // ref at which it is stored is generated based on the merge request number, and that
            // context isn't preserved in a GitRevision object.
            var baseBranchLabels = startRevision.AssociatedLabel(GitLabBaseBranchRef);
            string? baseBranchRef = baseBranchLabels.Count > 0 ? baseBranchLabels[^1] : null;
            Preconditions.CheckNotNull(
                baseBranchRef,
                "{0} label should be present in {1}.",
                GitLabBaseBranchRef,
                startRevision);

            string mergeBase =
                repository.MergeBase(
                    startRevision.GetHash(), repository.ResolveReference(baseBranchRef!).GetHash());
            GitRevision baseline = repository.ResolveReference(mergeBase);
            var visitor = new BaselinesWithoutLabelVisitor<GitRevision>(
                OriginFiles, limit, toSkip: null, skipFirst: false);
            VisitChanges(baseline, visitor);
            return visitor.GetResult();
        }
    }

    /// <summary>A builder class for <see cref="GitLabMrOrigin"/>.</summary>
    public sealed class Builder
    {
        internal Console Console { get; private set; } = null!;
        internal UsernamePasswordIssuer? UsernamePasswordIssuer { get; private set; }
        internal Uri RepoUrl { get; private set; } = null!;
        internal GitOptions GitOptions { get; private set; } = null!;
        internal GitOriginOptions GitOriginOptions { get; private set; } = null!;
        internal GitLabOptions GitLabOptions { get; private set; } = null!;
        internal GeneralOptions GeneralOptions { get; private set; } = null!;
        internal GitOrigin.SubmoduleStrategy SubmoduleStrategy { get; private set; }
        internal IReadOnlyList<string> ExcludedSubmodules { get; private set; } =
            ImmutableArray<string>.Empty;
        internal ITransformation? PatchTransformation { get; private set; }
        internal bool PartialFetch { get; private set; }
        internal bool DescribeVersion { get; private set; }
        internal bool FirstParent { get; private set; }
        internal bool UseMergeCommit { get; private set; }
        internal IGitRepositoryHook? GitRepositoryHook { get; private set; }

        public Builder SetConsole(Console val)
        {
            Console = val;
            return this;
        }

        public Builder SetUsernamePasswordIssuer(UsernamePasswordIssuer? val)
        {
            UsernamePasswordIssuer = val;
            return this;
        }

        public Builder SetRepoUrl(Uri val)
        {
            RepoUrl = val;
            return this;
        }

        public Builder SetGitOptions(GitOptions val)
        {
            GitOptions = val;
            return this;
        }

        public Builder SetGitOriginOptions(GitOriginOptions val)
        {
            GitOriginOptions = val;
            return this;
        }

        public Builder SetGitLabOptions(GitLabOptions val)
        {
            GitLabOptions = val;
            return this;
        }

        public Builder SetGeneralOptions(GeneralOptions val)
        {
            GeneralOptions = val;
            return this;
        }

        public Builder SetSubmoduleStrategy(GitOrigin.SubmoduleStrategy val)
        {
            SubmoduleStrategy = val;
            return this;
        }

        public Builder SetExcludedSubmodules(IReadOnlyList<string> val)
        {
            ExcludedSubmodules = val;
            return this;
        }

        public Builder SetPatchTransformation(ITransformation val)
        {
            PatchTransformation = Preconditions.CheckNotNull(val);
            return this;
        }

        public Builder SetPartialFetch(bool val)
        {
            PartialFetch = val;
            return this;
        }

        public Builder SetDescribeVersion(bool val)
        {
            DescribeVersion = val;
            return this;
        }

        public Builder SetFirstParent(bool val)
        {
            FirstParent = val;
            return this;
        }

        public Builder SetUseMergeCommit(bool val)
        {
            UseMergeCommit = val;
            return this;
        }

        public Builder SetGitRepositoryHook(IGitRepositoryHook? val)
        {
            GitRepositoryHook = val;
            return this;
        }

        public GitLabMrOrigin Build() => new(this);
    }
}
