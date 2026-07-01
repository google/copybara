/*
 * Copyright (C) 2016 Google Inc.
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
using System.Text;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Util;
using Starlark.Annot;
using Starlark.Eval;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Git;

/// <summary>
/// Integrate changes from a url present in the migrated change label. Port of
/// <c>com.google.copybara.git.GitIntegrateChanges</c>.
/// </summary>
[StarlarkBuiltin("git_integrate", Doc = "", Documented = false)]
public sealed class GitIntegrateChanges : IStarlarkValue
{
    private readonly string _label;
    private readonly Strategy _strategy;
    private readonly bool _ignoreErrors;

    internal GitIntegrateChanges(string label, Strategy strategy, bool ignoreErrors)
    {
        _label = Preconditions.CheckNotNull(label);
        _strategy = strategy;
        _ignoreErrors = ignoreErrors;
    }

    /// <summary>
    /// Perform an integrate of changes for matching labels in the existing repository HEAD.
    /// </summary>
    internal IIntegrateLabel? Run(
        GitRepository repository,
        string repoUrl,
        GeneralOptions generalOptions,
        GitDestination.MessageInfo messageInfo,
        Func<string, bool> externalFileMatcher,
        TransformResult result,
        bool ignoreIntegrationErrors)
    {
        IIntegrateLabel? integrateLabel = null;
        try
        {
            integrateLabel = DoIntegrate(
                repository, repoUrl, generalOptions, externalFileMatcher, result, messageInfo);
        }
        catch (CannotIntegrateException e)
        {
            if (ignoreIntegrationErrors || _ignoreErrors)
            {
                generalOptions.GetConsole().WarnFmt("Cannot integrate changes: {0}", e.Message);
            }
            else
            {
                throw;
            }
        }
        catch (RepoException e)
        {
            if (ignoreIntegrationErrors || _ignoreErrors)
            {
                generalOptions.GetConsole().WarnFmt("Cannot integrate changes: {0}", e.Message);
            }
            else
            {
                throw;
            }
        }
        return integrateLabel;
    }

    private IIntegrateLabel? DoIntegrate(
        GitRepository repository,
        string repoUrl,
        GeneralOptions generalOptions,
        Func<string, bool> externalFiles,
        TransformResult result,
        GitDestination.MessageInfo messageInfo)
    {
        IIntegrateLabel? optionalIntegrateLabel = null;
        foreach (var label in result.FindAllLabels())
        {
            if (!label.IsLabel() || _label != label.GetName())
            {
                continue;
            }
            if (string.IsNullOrEmpty(label.GetValue()))
            {
                throw new CannotIntegrateException("Found an empty value for label " + _label);
            }
            using (generalOptions.Profiler().Start("integrate"))
            {
                generalOptions
                    .GetConsole()
                    .ProgressFmt(
                        "Integrating change from '{0}' using strategy {1}",
                        label.GetValue(),
                        _strategy);
                try
                {
                    // TODO(peer): GitHubPrIntegrateLabel / GerritIntegrateLabel parsing is owned by
                    // the GitHub/Gerrit peer ports. Until they land, fall back to generic git
                    // revision resolution (which is the base case in upstream).
                    GitRevision gitRevision =
                        GitRepoType.Git.ResolveRef(
                            repository,
                            repoUrl,
                            label.GetValue(),
                            generalOptions,
                            describeVersion: false,
                            partialFetch: false,
                            fetchDepth: null);
                    IIntegrateLabel integrateLabel = IIntegrateLabel.GenericGitRevision(gitRevision);

                    _strategy.Integrate(
                        repository,
                        integrateLabel,
                        externalFiles,
                        label,
                        messageInfo,
                        generalOptions.GetConsole(),
                        generalOptions.GetDirFactory(),
                        generalOptions.IsTemporaryFeature(
                            "GIT_INTEGRATE_FAIL_IF_COMMON_BASELINE_NOT_FOUND", false));
                    optionalIntegrateLabel = integrateLabel;
                }
                catch (ValidationException e)
                {
                    throw new CannotIntegrateException("Error resolving " + label.GetValue(), e);
                }
            }
        }
        return optionalIntegrateLabel;
    }

    /// <summary>What should we do when we find a change to be integrated.</summary>
    public sealed class Strategy
    {
        /// <summary>A simple git fake-merge: Ignore any content from the change url.</summary>
        public static readonly Strategy FakeMerge = new("FAKE_MERGE", StrategyKind.FakeMerge);

        /// <summary>
        /// A hybrid that includes the changes that don't match destination_files but fake-merges
        /// the rest.
        /// </summary>
        public static readonly Strategy FakeMergeAndIncludeFiles =
            new("FAKE_MERGE_AND_INCLUDE_FILES", StrategyKind.FakeMergeAndIncludeFiles);

        /// <summary>
        /// Include changes that don't match destination_files but don't create a merge commit.
        /// </summary>
        public static readonly Strategy IncludeFiles =
            new("INCLUDE_FILES", StrategyKind.IncludeFiles);

        private readonly string _name;
        private readonly StrategyKind _kind;

        private Strategy(string name, StrategyKind kind)
        {
            _name = name;
            _kind = kind;
        }

        internal enum StrategyKind
        {
            FakeMerge,
            FakeMergeAndIncludeFiles,
            IncludeFiles,
        }

        public static Strategy ValueOf(string name) =>
            name switch
            {
                "FAKE_MERGE" => FakeMerge,
                "FAKE_MERGE_AND_INCLUDE_FILES" => FakeMergeAndIncludeFiles,
                "INCLUDE_FILES" => IncludeFiles,
                _ => throw new ArgumentException("Unknown integrate strategy: " + name),
            };

        internal void Integrate(
            GitRepository repository,
            IIntegrateLabel integrateLabel,
            Func<string, bool> externalFiles,
            LabelFinder rawLabelValue,
            GitDestination.MessageInfo messageInfo,
            Console console,
            DirFactory dirFactory,
            bool failIfIntegrateCommitNotFound)
        {
            switch (_kind)
            {
                case StrategyKind.FakeMerge:
                    IntegrateFakeMerge(
                        repository, integrateLabel, messageInfo, console,
                        failIfIntegrateCommitNotFound);
                    break;
                case StrategyKind.FakeMergeAndIncludeFiles:
                    IntegrateFakeMerge(
                        repository, integrateLabel, messageInfo, console,
                        failIfIntegrateCommitNotFound);
                    IntegrateIncludeFiles(
                        repository, integrateLabel, externalFiles, rawLabelValue, console,
                        failIfIntegrateCommitNotFound);
                    break;
                case StrategyKind.IncludeFiles:
                    IntegrateIncludeFiles(
                        repository, integrateLabel, externalFiles, rawLabelValue, console,
                        failIfIntegrateCommitNotFound);
                    break;
                default:
                    throw new CannotIntegrateException(this + " integrate mode is still not supported");
            }
        }

        private static void IntegrateFakeMerge(
            GitRepository repository,
            IIntegrateLabel integrateLabel,
            GitDestination.MessageInfo messageInfo,
            Console console,
            bool failIfIntegrateCommitNotFound)
        {
            GitRepository.GitLogEntry head = GetHeadCommit(repository);

            if (FindCommonBaseline(
                    repository, integrateLabel, head, failIfIntegrateCommitNotFound, console) == null)
            {
                console.WarnFmt(
                    "Skipping creation of merge for '{0}' as Copybara cannot find a common parent."
                        + " This normally means that the integrate label reference is for an"
                        + " unrelated repository",
                    integrateLabel);
                return;
            }

            string msg = integrateLabel.MergeMessage(messageInfo.LabelsToAdd);
            // If there is already a merge, don't overwrite the merge but create a new one.
            // Otherwise amend the last commit as a merge.
            GitRevision commit;
            if (head.Parents.Count > 1)
            {
                commit = repository.CommitTree(
                    msg, head.Tree, new[] { head.Commit, integrateLabel.GetRevision() });
            }
            else
            {
                var parents = new List<GitRevision>(head.Parents) { integrateLabel.GetRevision() };
                commit = repository.CommitTree(msg, head.Tree, parents);
            }
            repository.SimpleCommand("update-ref", "HEAD", commit.GetHash());
        }

        private static void IntegrateIncludeFiles(
            GitRepository repository,
            IIntegrateLabel integrateLabel,
            Func<string, bool> externalFiles,
            LabelFinder rawLabelValue,
            Console console,
            bool failIfIntegrateCommitNotFound)
        {
            // Save HEAD commit before starting messing with the repo.
            GitRepository.GitLogEntry head = GetHeadCommit(repository);
            byte[] diff = Encoding.UTF8.GetBytes(
                ComputeExternalDiff(
                    repository, integrateLabel, externalFiles, head,
                    failIfIntegrateCommitNotFound, console));
            if (diff.Length == 0)
            {
                return;
            }
            try
            {
                // Apply the patch to the current branch.
                repository.Apply(diff, index: true);
            }
            catch (RebaseConflictException e)
            {
                throw new CannotIntegrateException(
                    "Cannot apply the changes from " + integrateLabel, e);
            }

            var toRevert = new List<string>();
            foreach (var statusFile in repository.Status())
            {
                // Just in case the worktree is dirty.
                if (statusFile.IndexStatus == GitRepository.StatusCode.Unmodified)
                {
                    continue;
                }
                if (statusFile.IndexStatus == GitRepository.StatusCode.Copied)
                {
                    RevertIfInternal(toRevert, externalFiles, statusFile.NewFileName!);
                }
                else if (statusFile.IndexStatus == GitRepository.StatusCode.Renamed)
                {
                    RevertIfInternal(toRevert, externalFiles, statusFile.File);
                    RevertIfInternal(toRevert, externalFiles, statusFile.NewFileName!);
                }
                else
                {
                    RevertIfInternal(toRevert, externalFiles, statusFile.File);
                }
            }
            // Batch to prevent going over max arguments length.
            for (int i = 0; i < toRevert.Count; i += 20)
            {
                var batch = toRevert.Skip(i).Take(20);
                var @params = new List<string> { "reset", "HEAD", "--" };
                @params.AddRange(batch);
                repository.SimpleCommand(@params.ToArray());
            }
            ChangeMessage msg = ChangeMessage.ParseAllAsLabels(head.Body ?? "")
                .WithRemovedLabelByNameAndValue(rawLabelValue.GetName(), rawLabelValue.GetValue());

            // Amend last commit with the external files and remove the integration label.
            try
            {
                repository.Commit(author: null, amend: true, timestamp: null, msg.ToString());
            }
            catch (ValidationException)
            {
                // This is expected. There might not be any external file.
            }
            // Cleanup any non-committed file.
            repository.SimpleCommand("reset", "--hard");
            repository.ForceClean();
        }

        private static string ComputeExternalDiff(
            GitRepository repository,
            IIntegrateLabel integrateLabel,
            Func<string, bool> externalFiles,
            GitRepository.GitLogEntry head,
            bool failIfIntegrateCommitNotFound,
            Console console)
        {
            string commonBaseline =
                FindCommonBaseline(
                    repository, integrateLabel, head, failIfIntegrateCommitNotFound, console)
                ?? head.Commit.GetHash();
            byte[] diffs =
                repository
                    .SimpleCommandNoRedirectOutput(
                        "diff", commonBaseline + ".." + integrateLabel.GetRevision().GetHash())
                    .GetStdoutBytes();
            return DiffUtil.FilterDiff(diffs, externalFiles);
        }

        private static void RevertIfInternal(
            List<string> toRevert, Func<string, bool> externalFiles, string file)
        {
            if (!externalFiles(file))
            {
                toRevert.Add(file);
            }
        }

        /// <summary>
        /// Tries to find the common commit between HEAD and the integrate label commit. If the
        /// integrate sha cannot be found it defaults to HEAD. If the sha can be found but a common
        /// parent cannot be found, it returns null.
        /// </summary>
        private static string? FindCommonBaseline(
            GitRepository repository,
            IIntegrateLabel integrateLabel,
            GitRepository.GitLogEntry head,
            bool failIfIntegrateCommitNotFound,
            Console console)
        {
            GitRevision? previousHead = head.Parents.Count > 0 ? head.Parents[0] : null;
            if (previousHead == null)
            {
                return head.Commit.GetHash();
            }
            string sha1;
            try
            {
                sha1 = integrateLabel.GetRevision().GetHash();
            }
            catch (Exception e) when (e is RepoException or ValidationException)
            {
                if (!e.Message.Contains("Could not access submodule"))
                {
                    if (failIfIntegrateCommitNotFound)
                    {
                        console.WarnFmt(
                            "failIfIntegrateCommitNotFound is true, re-throwing exception: {0}",
                            e.Message);
                        throw;
                    }
                    return head.Commit.GetHash();
                }
                try
                {
                    sha1 = integrateLabel.GetRevision().GetHash();
                }
                catch (RepoException retry)
                {
                    if (failIfIntegrateCommitNotFound)
                    {
                        console.WarnFmt(
                            "failIfIntegrateCommitNotFound is true, re-throwing retry exception: {0}",
                            retry.Message);
                        throw;
                    }
                    return head.Commit.GetHash();
                }
            }
            try
            {
                return repository.MergeBase(previousHead.GetHash(), sha1);
            }
            catch (RepoException)
            {
                return null;
            }
        }

        private static GitRepository.GitLogEntry GetHeadCommit(GitRepository repository)
        {
            var entries = repository.Log("HEAD").WithLimit(1).Run();
            return entries[0];
        }

        public override string ToString() => _name;
    }

    public override bool Equals(object? o) =>
        o is GitIntegrateChanges that
        && _ignoreErrors == that._ignoreErrors
        && _label == that._label
        && ReferenceEquals(_strategy, that._strategy);

    public Strategy GetStrategy() => _strategy;

    public string GetLabel() => _label;

    public override int GetHashCode() => HashCode.Combine(_label, _strategy, _ignoreErrors);

    public override string ToString() =>
        $"GitIntegrateChanges{{label={_label}, strategy={_strategy}, ignoreErrors={_ignoreErrors}}}";
}
