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
using Copybara.Checks;
using Copybara.Common;
using Copybara.Effect;
using Copybara.Exceptions;
using Copybara.Revision;
using Copybara.Util;
using Starlark.Annot;
using Starlark.Eval;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Git;

/// <summary>A Git repository destination. Port of <c>com.google.copybara.git.GitDestination</c>.</summary>
public class GitDestination : IDestination<GitRevision>
{
    private const string OriginLabelSeparator = ": ";
    public const int SmallNumFilesCheckerThreshold = 100;

    // Mirrors GitModule.PRIMARY_BRANCHES.
    private static readonly ImmutableHashSet<string> PrimaryBranches =
        ImmutableHashSet.Create("master", "main");

    /// <summary>Holder for the labels that should be added to the destination change message.</summary>
    public class MessageInfo
    {
        public IReadOnlyList<LabelFinder> LabelsToAdd { get; }

        public MessageInfo(IReadOnlyList<LabelFinder> labelsToAdd)
        {
            LabelsToAdd = Preconditions.CheckNotNull(labelsToAdd);
        }
    }

    private readonly string _repoUrl;
    private readonly string _fetch;
    protected readonly string PushRef;
    private readonly bool _partialFetch;
    internal readonly bool PrimaryBranchMigrationMode;

    private readonly string? _tagName;
    private readonly string? _tagMsg;
    private readonly GitDestinationOptions _destinationOptions;
    private readonly GitOptions _gitOptions;
    private readonly GeneralOptions _generalOptions;

    private string? _resolvedPrimary;
    private readonly IEnumerable<GitIntegrateChanges> _integrates;
    private readonly IWriteHook _writerHook;
    private readonly IChecker? _checker;
    private readonly LazyResourceLoader<GitRepository> _localRepo;
    private readonly CredentialFileHandler? _credentials;

    internal GitDestination(
        string repoUrl,
        string fetch,
        string push,
        bool partialFetch,
        bool primaryBranchMigrationMode,
        string? tagName,
        string? tagMsg,
        GitDestinationOptions destinationOptions,
        GitOptions gitOptions,
        GeneralOptions generalOptions,
        IWriteHook writerHook,
        IEnumerable<GitIntegrateChanges> integrates,
        IChecker? checker,
        CredentialFileHandler? credentials)
    {
        _repoUrl = Preconditions.CheckNotNull(repoUrl);
        _fetch = Preconditions.CheckNotNull(fetch);
        PushRef = Preconditions.CheckNotNull(push);
        _partialFetch = partialFetch;
        PrimaryBranchMigrationMode = primaryBranchMigrationMode;
        _tagName = tagName;
        _tagMsg = tagMsg;
        _destinationOptions = Preconditions.CheckNotNull(destinationOptions);
        _gitOptions = Preconditions.CheckNotNull(gitOptions);
        _generalOptions = Preconditions.CheckNotNull(generalOptions);
        _integrates = Preconditions.CheckNotNull(integrates);
        _writerHook = Preconditions.CheckNotNull(writerHook);
        _checker = checker;
        _localRepo = LazyResourceLoader.Memoized<GitRepository>(
            _ => destinationOptions.LocalGitRepo(repoUrl, credentials));
        _credentials = credentials;
    }

    /// <summary>
    /// Throws an exception if the user.email or user.name Git configuration settings are not set.
    /// </summary>
    private static void VerifyUserInfoConfigured(GitRepository repo)
    {
        string output = repo.SimpleCommand("config", "-l").GetStdout();
        bool nameConfigured = false;
        bool emailConfigured = false;
        foreach (var line in output.Split('\n'))
        {
            if (line.StartsWith("user.name=", StringComparison.Ordinal))
            {
                nameConfigured = true;
            }
            else if (line.StartsWith("user.email=", StringComparison.Ordinal))
            {
                emailConfigured = true;
            }
        }
        ValidationException.CheckCondition(
            nameConfigured && emailConfigured,
            "'user.name' and/or 'user.email' are not configured. Please run `git config --global"
                + " SETTING VALUE` to set them");
    }

    public IDestination<GitRevision>.IWriter<GitRevision> NewWriter(WriterContext writerContext)
    {
        var state = new WriterState(
            _localRepo, _destinationOptions.GetLocalBranch(GetPush(), writerContext.IsDryRun()));

        return new WriterImpl<WriterState>(
            writerContext.IsDryRun(),
            _repoUrl,
            GetFetch(),
            GetPush(),
            _partialFetch,
            _tagName,
            _tagMsg,
            _generalOptions,
            _gitOptions,
            _writerHook,
            state,
            _destinationOptions.NonFastForwardPush,
            _integrates,
            _destinationOptions.LastRevFirstParent,
            _destinationOptions.IgnoreIntegrationErrors,
            _destinationOptions.LocalRepoPath,
            _destinationOptions.CommitterName,
            _destinationOptions.CommitterEmail,
            _destinationOptions.RebaseWhenBaseline(),
            _gitOptions.VisitChangePageSize,
            _gitOptions.GitTagOverwrite,
            _checker,
            _destinationOptions,
            _credentials);
    }

    /// <summary>State to be maintained between writer instances.</summary>
    public class WriterState
    {
        internal bool AlreadyFetched;
        internal bool FirstWrite = true;
        internal readonly LazyResourceLoader<GitRepository> LocalRepo;
        internal readonly string LocalBranch;

        internal WriterState(LazyResourceLoader<GitRepository> localRepo, string localBranch)
        {
            LocalRepo = localRepo;
            LocalBranch = localBranch;
        }
    }

    /// <summary>A write hook allows customizing the behavior of the git.destination writer.</summary>
    public interface IWriteHook
    {
        /// <summary>Customize the writer for a particular destination.</summary>
        MessageInfo GenerateMessageInfo(TransformResult transformResult);

        /// <summary>Validate or modify the current change to be pushed.</summary>
        void BeforePush(
            GitRepository repo,
            MessageInfo messageInfo,
            bool skipPush,
            IReadOnlyList<IIntegrateLabel> integrateLabels,
            IReadOnlyList<object> originChanges)
        {
        }

        /// <summary>Construct the reference to push based on the pushToRefsFor reference.</summary>
        string GetPushReference(
            GitRepository primaryBranch, string pushToRefsFor, TransformResult transformResult);

        /// <summary>Process the server response from the push command and compute the effects.</summary>
        IReadOnlyList<DestinationEffect> AfterPush(
            string serverResponse,
            MessageInfo messageInfo,
            GitRevision pushedRevision,
            IReadOnlyList<object> originChanges);

        IEndpoint GetFeedbackEndPoint(Console console) => IEndpoint.NoopEndpoint;

        ImmutableListMultimap<string, string> Describe() =>
            ImmutableListMultimap<string, string>.Empty;
    }

    /// <summary>A write hook for standard git repositories.</summary>
    public class DefaultWriteHook : IWriteHook
    {
        public MessageInfo GenerateMessageInfo(TransformResult transformResult)
        {
            IRevision rev = transformResult.GetCurrentRevision();
            return new MessageInfo(
                transformResult.IsSetRevId()
                    ? new List<LabelFinder>
                    {
                        new(transformResult.GetRevIdLabel() + OriginLabelSeparator + rev.AsString()),
                    }
                    : new List<LabelFinder>());
        }

        public virtual IReadOnlyList<DestinationEffect> AfterPush(
            string serverResponse,
            MessageInfo messageInfo,
            GitRevision pushedRevision,
            IReadOnlyList<object> originChanges) =>
            ImmutableArray.Create(
                new DestinationEffect(
                    DestinationEffect.EffectType.CREATED,
                    $"Created revision {pushedRevision.GetHash()}",
                    originChanges.Cast<OriginRef>().ToList(),
                    new DestinationEffect.DestinationRef(
                        pushedRevision.GetHash(), "commit", url: null)));

        public string GetPushReference(
            GitRepository repo, string pushToRefsFor, TransformResult transformResult) =>
            pushToRefsFor;

        public virtual IEndpoint GetFeedbackEndPoint(Console console) => IEndpoint.NoopEndpoint;

        public virtual ImmutableListMultimap<string, string> Describe() =>
            ImmutableListMultimap<string, string>.Empty;
    }

    /// <summary>
    /// A writer for git.*destination destinations. Not a public interface; don't use directly.
    /// </summary>
    public class WriterImpl<TS> : IDestination<GitRevision>.IWriter<GitRevision>
        where TS : WriterState
    {
        internal readonly bool SkipPush;
        private readonly string _repoUrl;
        private readonly string _remoteFetch;
        private readonly string _remotePush;
        private readonly string? _tagNameTemplate;
        private readonly string? _tagMsgTemplate;
        private readonly bool _force;
        private readonly bool _partialFetch;
        private readonly Console _baseConsole;
        private readonly GeneralOptions _generalOptions;
        private readonly GitOptions _gitOptions;
        private readonly IWriteHook _writeHook;
        internal readonly TS State;
        private readonly bool _nonFastForwardPush;
        private readonly IEnumerable<GitIntegrateChanges> _integrates;
        private readonly bool _lastRevFirstParent;
        private readonly bool _ignoreIntegrationErrors;
        private readonly string? _localRepoPath;
        private readonly string _committerName;
        private readonly string _committerEmail;
        private readonly bool _rebase;
        private readonly int _visitChangePageSize;
        private readonly bool _gitTagOverwrite;
        private readonly IChecker? _checker;
        private readonly GitDestinationOptions _destinationOptions;

        internal WriterImpl(
            bool skipPush,
            string repoUrl,
            string remoteFetch,
            string remotePush,
            bool partialFetch,
            string? tagNameTemplate,
            string? tagMsgTemplate,
            GeneralOptions generalOptions,
            GitOptions gitOptions,
            IWriteHook writeHook,
            TS state,
            bool nonFastForwardPush,
            IEnumerable<GitIntegrateChanges> integrates,
            bool lastRevFirstParent,
            bool ignoreIntegrationErrors,
            string? localRepoPath,
            string committerName,
            string committerEmail,
            bool rebase,
            int visitChangePageSize,
            bool gitTagOverwrite,
            IChecker? checker,
            GitDestinationOptions destinationOptions,
            CredentialFileHandler? credentials)
        {
            SkipPush = skipPush;
            _repoUrl = Preconditions.CheckNotNull(repoUrl);
            _remoteFetch = Preconditions.CheckNotNull(remoteFetch);
            _remotePush = Preconditions.CheckNotNull(remotePush);
            _partialFetch = partialFetch;
            _tagNameTemplate = tagNameTemplate;
            _tagMsgTemplate = tagMsgTemplate;
            _force = generalOptions.IsForced();
            _baseConsole = Preconditions.CheckNotNull(generalOptions.GetConsole());
            _generalOptions = generalOptions;
            _gitOptions = Preconditions.CheckNotNull(gitOptions);
            _writeHook = Preconditions.CheckNotNull(writeHook);
            State = Preconditions.CheckNotNull(state);
            _nonFastForwardPush = nonFastForwardPush;
            _integrates = Preconditions.CheckNotNull(integrates);
            _lastRevFirstParent = lastRevFirstParent;
            _ignoreIntegrationErrors = ignoreIntegrationErrors;
            _localRepoPath = localRepoPath;
            _committerName = committerName;
            _committerEmail = committerEmail;
            _rebase = rebase;
            _visitChangePageSize = visitChangePageSize;
            _gitTagOverwrite = gitTagOverwrite;
            _checker = checker;
            _destinationOptions = Preconditions.CheckNotNull(destinationOptions);
        }

        public void VisitChanges(GitRevision? start, IChangesVisitor visitor)
        {
            GitRepository repository = GetRepository(_baseConsole);
            try
            {
                FetchIfNeeded(repository, _baseConsole);
            }
            catch (ValidationException e)
            {
                throw new CannotResolveRevisionException(
                    "Cannot visit changes because fetch failed. Does the destination branch exist?",
                    e);
            }
            GitRevision? startRef = GetLocalBranchRevision(repository);
            if (startRef == null)
            {
                return;
            }
            ChangeReader.Builder queryChanges =
                ChangeReader.Builder.ForDestination(repository, _baseConsole);

            GitVisitorUtil.VisitChanges(
                start ?? startRef,
                visitor,
                queryChanges,
                _generalOptions,
                "destination",
                _visitChangePageSize);
        }

        private void FetchIfNeeded(GitRepository repo, Console console)
        {
            if (!State.AlreadyFetched)
            {
                GitRevision? revision = FetchFromRemote(console, repo, _repoUrl, _remoteFetch);
                if (revision != null)
                {
                    try
                    {
                        repo.Branch(State.LocalBranch).WithStartPoint(revision.GetHash()).Run();
                    }
                    catch (RepoException e)
                    {
                        if (e.Message.Contains($"{State.LocalBranch} already exists"))
                        {
                            return;
                        }
                        throw;
                    }
                }
                State.AlreadyFetched = true;
            }
        }

        public DestinationStatus? GetDestinationStatus(Glob destinationFiles, string labelName)
        {
            GitRepository repo = GetRepository(_baseConsole);
            try
            {
                FetchIfNeeded(repo, _baseConsole);
            }
            catch (AccessValidationException)
            {
                throw;
            }
            catch (ValidationException e)
            {
                _baseConsole.WarnFmt("Error caught when fetching from destination: {0}", e.Message);
                return null;
            }
            GitRevision? startRef = GetLocalBranchRevision(repo);
            if (startRef == null)
            {
                return null;
            }

            var pathMatcher = destinationFiles.RelativeTo("");
            var visitor = new DestinationStatusVisitor(pathMatcher, labelName);
            ChangeReader.Builder changeReader =
                ChangeReader.Builder.ForDestination(repo, _baseConsole)
                    .SetFirstParent(_lastRevFirstParent)
                    .Grep("^" + labelName + OriginLabelSeparator);
            try
            {
                GitVisitorUtil.VisitChanges(
                    startRef,
                    visitor,
                    changeReader,
                    _generalOptions,
                    "get_destination_status",
                    _visitChangePageSize);
            }
            catch (CannotResolveRevisionException e)
            {
                _baseConsole.WarnFmt("Error caught when visiting changes: {0}", e.Message);
                return null;
            }
            return visitor.GetDestinationStatus();
        }

        public virtual IEndpoint GetFeedbackEndPoint(Console console) =>
            _writeHook.GetFeedbackEndPoint(console);

        private GitRevision? GetLocalBranchRevision(GitRepository gitRepository)
        {
            try
            {
                return gitRepository.ResolveReference(State.LocalBranch);
            }
            catch (CannotResolveRevisionException)
            {
                if (_force)
                {
                    return null;
                }
                throw new RepoException(
                    $"Could not find {_remoteFetch} in {_repoUrl} and '{GeneralOptions.Force}' was"
                        + " not used");
            }
        }

        public bool SupportsHistory() => true;

        public virtual IReadOnlyList<DestinationEffect> Write(
            TransformResult transformResult, Glob destinationFiles, Console console)
        {
            string? baseline = transformResult.GetBaseline();
            GitRepository scratchClone = GetRepository(console);
            FetchIfNeeded(scratchClone, console);

            console.ProgressFmt("Git Destination: Checking out {0}", _remoteFetch);

            GitRevision? localBranchRevision = GetLocalBranchRevision(scratchClone);
            UpdateLocalBranchToBaseline(scratchClone, baseline);
            if (State.FirstWrite)
            {
                string reference = baseline ?? State.LocalBranch;
                ConfigForPush(GetRepository(console), _repoUrl, _remotePush);
                if (!_force && localBranchRevision == null)
                {
                    throw new RepoException(
                        $"Cannot checkout '{reference}' from '{_repoUrl}'. Use"
                            + $" '{GeneralOptions.Force}' if the destination is a new git repo or you"
                            + " don't care about the destination current status");
                }
                if (localBranchRevision != null)
                {
                    scratchClone.SimpleCommand(
                        GetMaxRepoTimeout(), "checkout", "-f", "-q", reference);
                }
                else
                {
                    // Configure the commit to go to local branch instead of main branch.
                    scratchClone.SimpleCommand(
                        "symbolic-ref", "HEAD", GetCompleteRef(State.LocalBranch));
                }
                State.FirstWrite = false;
            }
            else
            {
                if (!SkipPush)
                {
                    FetchFromRemote(console, scratchClone, _repoUrl, _remoteFetch);
                }
                // Checkout again in case the origin checkout changed the branch (origin = destination)
                if (string.IsNullOrEmpty(scratchClone.GetCurrentBranch()))
                {
                    scratchClone.SimpleCommand(
                        GetMaxRepoTimeout(), "checkout", "-q", "-f", State.LocalBranch);
                }
            }
            var pathMatcher = destinationFiles.RelativeTo(scratchClone.GetWorkTree()!);
            // Get the submodules before we stage them for deletion with add --all.
            var excludedAdder = new AddExcludedFilesToIndex(scratchClone, pathMatcher);
            excludedAdder.Prepare(transformResult.GetPath());
            excludedAdder.FindSubmodules(console);

            GitRepository alternate = scratchClone.WithWorkTree(transformResult.GetPath());

            console.Progress("Git Destination: Adding all files");
            using (_generalOptions.Profiler().Start("add_files"))
            {
                alternate.Add().Force().All().Run();
            }

            console.Progress("Git Destination: Excluding files");
            using (_generalOptions.Profiler().Start("exclude_files"))
            {
                excludedAdder.Add();
            }

            console.Progress("Git Destination: Creating a local commit");
            MessageInfo messageInfo = _writeHook.GenerateMessageInfo(transformResult);

            alternate.Commit(
                transformResult.GetAuthor().ToString(),
                transformResult.GetTimestamp(),
                AddDestinationLabels(
                    messageInfo,
                    transformResult.GetSummary().Trim().Length == 0
                        ? "Internal change"
                        : transformResult.GetSummary()));

            MaybeCheckHeadCommit(alternate, transformResult.GetSummary(), messageInfo);

            var integrateLabels = new List<IIntegrateLabel>();
            foreach (var integrate in _integrates)
            {
                IIntegrateLabel? integrateLabel =
                    integrate.Run(
                        alternate,
                        _repoUrl,
                        _generalOptions,
                        messageInfo,
                        path => !pathMatcher.Matches(Path.Combine(scratchClone.GetWorkTree()!, path)),
                        transformResult,
                        _ignoreIntegrationErrors);

                if (integrateLabel != null)
                {
                    integrateLabels.Add(integrateLabel);
                }
            }

            ValidationException.CheckCondition(
                transformResult.GetSummary().Trim().Length != 0,
                "Change description is empty - this can be the result of scrubbing or an origin"
                    + " change without description.");

            // Don't leave unstaged/untracked files in the work-tree.
            scratchClone.SimpleCommand("reset", "--hard");
            scratchClone.ForceClean();

            GitRevision? afterRebaseRev = null;
            if (baseline != null && _rebase)
            {
                var rebaseLocks = new[]
                {
                    Path.Combine(alternate.GetGitDir(), "rebase-apply"),
                    Path.Combine(alternate.GetGitDir(), "rebase-merge"),
                };
                foreach (var rebaseLock in rebaseLocks)
                {
                    if (Directory.Exists(rebaseLock) || File.Exists(rebaseLock))
                    {
                        console.Warn("Removing previous rebase failure lock: " + rebaseLock);
                        FileUtil.DeleteRecursively(rebaseLock);
                    }
                }

                alternate.SimpleCommand("reset", "--hard");
                ValidationException.CheckCondition(
                    localBranchRevision != null,
                    "Unable to rebase because the local branch's revision was not resolvable.");
                alternate
                    .RebaseCmdFor(localBranchRevision!.GetHash())
                    .ErrorAdvice(
                        "Please consider to use flag --nogit-destination-rebase to workaround")
                    .Run();
                afterRebaseRev = alternate.ResolveReference("HEAD");
                if (afterRebaseRev.GetHash() == localBranchRevision.GetHash())
                {
                    throw new EmptyChangeException(
                        "Empty change after rebase. The only affected paths were already applied in"
                            + " main branch. This usually happens if in presubmit workflows where"
                            + " the used config file is more up-to-date than the origin change"
                            + " baseline.");
                }
            }

            string localBranchName = "";
            if (_localRepoPath != null)
            {
                if (afterRebaseRev != null)
                {
                    localBranchName = "copybara/local";
                    alternate.SimpleCommand(
                        GetMaxRepoTimeout(), "checkout", "-B", localBranchName,
                        afterRebaseRev.GetHash());
                }
                scratchClone.SimpleCommand(GetMaxRepoTimeout(), "checkout", State.LocalBranch);
            }

            if (transformResult.IsConfirmedInOrigin())
            {
                // Diffs were shown and approved in origin.
            }
            else if (transformResult.IsAskForConfirmation())
            {
                console.Info(
                    DiffUtil.Colorize(
                        console, scratchClone.SimpleCommand("show", "HEAD").GetStdout()));
                if (!console.PromptConfirmationFmt(
                        "Proceed with push to {0} {1}?", _repoUrl, _remotePush))
                {
                    console.Warn("Migration aborted by user.");
                    throw new ChangeRejectedException(
                        "User aborted execution: did not confirm diff changes.");
                }
            }

            GitRevision head = scratchClone.ResolveReference("HEAD");
            IReadOnlyList<object> originChanges = transformResult.GetChanges().GetCurrent();
            string? tagName = CreateTag(scratchClone, console, transformResult);
            _writeHook.BeforePush(scratchClone, messageInfo, SkipPush, integrateLabels, originChanges);

            if (SkipPush)
            {
                console.InfoFmt(
                    "Git Destination: skipped push to remote. Check the local commits by running:"
                        + " GIT_DIR={0} git log {1}",
                    scratchClone.GetGitDir(),
                    localBranchName);
                return ImmutableArray.Create(
                    new DestinationEffect(
                        DestinationEffect.EffectType.CREATED,
                        $"Dry run commit '{head}' created locally at {scratchClone.GetGitDir()}",
                        originChanges.Cast<OriginRef>().ToList(),
                        new DestinationEffect.DestinationRef(head.GetHash(), "commit", url: null)));
            }
            string push =
                _writeHook.GetPushReference(scratchClone, GetCompleteRef(_remotePush), transformResult);
            console.Progress($"Git Destination: Pushing to {_repoUrl} {push}");
            ValidationException.CheckCondition(
                !_nonFastForwardPush || _remoteFetch != _remotePush,
                "non fast-forward push is only allowed when fetch != push");

            string capturedTag = tagName!;
            string capturedPush = push;
            string serverResponse =
                _generalOptions.RepoTask(
                    "push",
                    () =>
                        scratchClone
                            .Push()
                            .WithRefspecs(
                                _repoUrl,
                                capturedTag != null
                                    ? new[]
                                    {
                                        scratchClone.CreateRefSpec(
                                            (_nonFastForwardPush ? "+" : "") + "HEAD:" + capturedPush),
                                        scratchClone.CreateRefSpec(
                                            (_gitTagOverwrite ? "+" : "") + capturedTag),
                                    }
                                    : new[]
                                    {
                                        scratchClone.CreateRefSpec(
                                            (_nonFastForwardPush ? "+" : "") + "HEAD:" + capturedPush),
                                    })
                            .WithPushOptions(_gitOptions.GitPushOptions.ToImmutableArray())
                            .Run());
            return _writeHook.AfterPush(serverResponse, messageInfo, head, originChanges);
        }

        private string AddDestinationLabels(MessageInfo messageInfo, string summary)
        {
            ChangeMessage msg = ChangeMessage.ParseMessage(summary);
            foreach (var label in messageInfo.LabelsToAdd)
            {
                msg = msg.WithNewOrReplacedLabel(
                    label.GetName(), label.GetSeparator(), label.GetValue());
            }
            return msg.ToString();
        }

        private TimeSpan GetMaxRepoTimeout() =>
            _generalOptions.RepoTimeout > _generalOptions.CommandsTimeout
                ? _generalOptions.RepoTimeout
                : _generalOptions.CommandsTimeout;

        /// <summary>
        /// Given a change in HEAD, if a checker is configured, it checks the affected files and the
        /// commit message.
        /// </summary>
        private void MaybeCheckHeadCommit(
            GitRepository alternate, string beforeCommitMsg, MessageInfo messageInfo)
        {
            if (_checker == null)
            {
                return;
            }
            var head = alternate.Log("HEAD").WithLimit(1).IncludeFiles(true).IncludeBody(true).Run();
            var commit = head[0];
            var files = commit.Files;
            string target = alternate.GetWorkTree()!;
            // If only a few files, create a copy so the checker doesn't check the whole tree.
            if (files != null && files.Count < SmallNumFilesCheckerThreshold)
            {
                string dest = _generalOptions.GetDirFactory().NewTempDir("git_dest_checker");
                FileUtil.CopyFilesRecursively(
                    alternate.GetWorkTree()!,
                    dest,
                    FileUtil.CopySymlinkStrategy.IgnoreInvalidSymlinks,
                    Glob.CreateSingleFilesGlob(files));
                target = dest;
            }
            _checker.DoCheck(target, _baseConsole);

            // TODO(peer): DescriptionChecker processing is owned by the checks peer port. Wire it up
            // once DescriptionChecker is available.
        }

        private string? CreateTag(
            GitRepository gitRepository, Console console, TransformResult transformResult)
        {
            if (_tagNameTemplate == null)
            {
                return null;
            }

            string? tagName = null;
            string? tagMsg = null;
            try
            {
                tagName = LabelFinder.MapLabels(
                    transformResult.GetLabelFinder(), _tagNameTemplate);
                if (_tagMsgTemplate != null)
                {
                    tagMsg = LabelFinder.MapLabels(
                        transformResult.GetLabelFinder(), _tagMsgTemplate);
                }
            }
            catch (ValidationException e)
            {
                console.WarnFmt("Get label failed. Error: {0}", e.Message);
            }
            if (tagName == null)
            {
                return null;
            }

            try
            {
                if (tagMsg == null)
                {
                    gitRepository.Tag(tagName).Force(_gitTagOverwrite).Run();
                }
                else
                {
                    gitRepository.Tag(tagName).WithAnnotatedTag(tagMsg).Force(_gitTagOverwrite).Run();
                }
                return tagName;
            }
            catch (Exception e) when (e is RepoException or ValidationException)
            {
                if (e.Message.Contains($"tag '{tagName}' already exists"))
                {
                    console.WarnFmt(
                        "Tag {0} exists. To overwrite it please use flag '--git-tag-overwrite'",
                        _tagNameTemplate);
                }
                else
                {
                    console.WarnFmt(
                        "Create tag failed. Error: {0}. Note that we don't want to fail because of"
                            + " this",
                        e.Message);
                }
                return null;
            }
        }

        /// <summary>Get the local <see cref="GitRepository"/> associated with the writer.</summary>
        public GitRepository GetRepository(Console console) => State.LocalRepo.Load(console);

        private void UpdateLocalBranchToBaseline(GitRepository repo, string? baseline)
        {
            if (baseline != null && !repo.RefExists(baseline))
            {
                throw new RepoException(
                    "Cannot find baseline '" + baseline
                        + (GetLocalBranchRevision(repo) != null
                            ? "' from fetch reference '" + _remoteFetch + "'"
                            : "' and fetch reference '" + _remoteFetch + "' itself")
                        + " in " + _repoUrl + ".");
            }
            if (baseline != null)
            {
                repo.SimpleCommand("update-ref", State.LocalBranch, baseline);
            }
        }

        private GitRevision? FetchFromRemote(
            Console console, GitRepository repo, string repoUrl, string fetch)
        {
            string completeFetchRef = GetCompleteRef(fetch);
            using (_generalOptions.Profiler().Start("destination_fetch"))
            {
                console.Progress("Git Destination: Fetching: " + repoUrl + " " + completeFetchRef);
                try
                {
                    return repo.FetchSingleRef(
                        repoUrl, completeFetchRef, _partialFetch, _destinationOptions.GetFetchDepth());
                }
                catch (CannotResolveRevisionException)
                {
                    string warning =
                        $"Git Destination: '{completeFetchRef}' doesn't exist in '{repoUrl}'";
                    ValidationException.CheckCondition(
                        _force,
                        "{0}. Use {1} flag if you want to push anyway",
                        warning,
                        GeneralOptions.Force);
                    console.Warn(warning);
                }
            }
            return null;
        }

        private static string GetCompleteRef(string fetch) =>
            fetch.StartsWith("refs/", StringComparison.Ordinal) ? fetch : "refs/heads/" + fetch;

        private void ConfigForPush(GitRepository repo, string repoUrl, string push)
        {
            if (_localRepoPath != null)
            {
                repo.SimpleCommand("config", "remote.copybara_remote.url", repoUrl);
                repo.SimpleCommand(
                    "config", "remote.copybara_remote.push", State.LocalBranch + ":" + push);
                repo.SimpleCommand(
                    "config", "branch." + State.LocalBranch + ".remote", "copybara_remote");
            }
            if (!string.IsNullOrEmpty(_committerName))
            {
                repo.SimpleCommand("config", "user.name", _committerName);
            }
            if (!string.IsNullOrEmpty(_committerEmail))
            {
                repo.SimpleCommand("config", "user.email", _committerEmail);
            }
            VerifyUserInfoConfigured(repo);
        }

        public DestinationReader GetDestinationReader(
            Console console, Origin.Baseline<IRevision>? baseline, string workdir) =>
            GetDestinationReader(console, baseline?.GetBaseline(), workdir);

        public DestinationReader GetDestinationReader(
            Console console, string? baseline, string workdir)
        {
            GitRepository repo = GetRepository(console);
            FetchIfNeeded(repo, console);
            GitRevision? rev;
            if (baseline != null)
            {
                rev = repo.ResolveReference(baseline);
            }
            else
            {
                rev = GetLocalBranchRevision(repo);
            }
            // In case of --force, the destination might be empty and have no revisions. Do not fail.
            if (rev == null)
            {
                console.Info(
                    "Destination reader requested, but destination is empty. Using noop reader");
                return DestinationReader.NoopDestinationReader;
            }
            return new GitDestinationReader(repo, rev, workdir);
        }
    }

    internal string GetFetch()
    {
        if (PrimaryBranchMigrationMode && PrimaryBranches.Contains(_fetch))
        {
            string? resolved = GetResolvedPrimary();
            if (resolved != null)
            {
                return resolved;
            }
        }
        return _fetch;
    }

    internal string GetPush()
    {
        if (PrimaryBranchMigrationMode && PrimaryBranches.Contains(PushRef))
        {
            string? resolved = GetResolvedPrimary();
            if (resolved != null)
            {
                return resolved;
            }
        }
        return PushRef;
    }

    protected string? GetResolvedPrimary()
    {
        if (_resolvedPrimary == null)
        {
            try
            {
                _resolvedPrimary =
                    GetLocalRepo().Load(_generalOptions.GetConsole()).GetPrimaryBranch(_repoUrl);
            }
            catch (RepoException)
            {
                return null;
            }
        }
        return _resolvedPrimary;
    }

    public IEnumerable<GitIntegrateChanges> GetIntegrates() => _integrates;

    public string GetLabelNameWhenOrigin() => GitRepository.GitOriginRevId;

    public override string ToString() =>
        $"GitDestination{{repoUrl={_repoUrl}, fetch={_fetch}, push={PushRef},"
            + $" partialFetch={_partialFetch}, primaryBranchMigrationMode={PrimaryBranchMigrationMode}}}";

    public IWriteHook GetWriterHook() => _writerHook;

    /// <summary>Not a public API. It is subject to change.</summary>
    public LazyResourceLoader<GitRepository> GetLocalRepo() => _localRepo;

    public string GetType() => "git.destination";

    public ImmutableListMultimap<string, string> Describe(Glob? destinationFiles)
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        builder.Put("type", GetType());
        builder.Put("url", _repoUrl);
        builder.Put("fetch", _fetch);
        builder.Put("push", PushRef);
        builder.Put("primaryBranchMigrationMode", PrimaryBranchMigrationMode.ToString());
        builder.PutAll(_writerHook.Describe());
        if (destinationFiles != null
            && !destinationFiles.Roots().IsEmpty
            && !destinationFiles.Roots().Contains(""))
        {
            builder.PutAll("root", destinationFiles.Roots());
        }
        if (_partialFetch)
        {
            builder.Put("partialFetch", _partialFetch.ToString());
        }
        if (_tagName != null)
        {
            builder.Put("tagName", _tagName);
        }
        if (_tagMsg != null)
        {
            builder.Put("tagMsg", _tagMsg);
        }
        if (_checker != null)
        {
            builder.Put("checker", _checker.GetType().FullName ?? _checker.GetType().Name);
        }
        foreach (var integrate in _integrates)
        {
            builder.Put("integrate", $"{integrate.GetLabel()}:{integrate.GetStrategy()}");
        }

        return builder.Build();
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
