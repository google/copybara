/*
 * Copyright (C) 2018 Google Inc.
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
using Copybara.Common;
using Copybara.Effect;
using Copybara.Exceptions;
using Copybara.Revision;
using Copybara.Util;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Hg;

/// <summary>A Mercurial (Hg) repository destination.</summary>
public class HgDestination : IDestination<HgRevision>
{
    private const string OriginLabelSeparator = ": ";

    private sealed class MessageInfo
    {
        internal readonly IReadOnlyList<LabelFinder> LabelsToAdd;

        internal MessageInfo(IReadOnlyList<LabelFinder> labelsToAdd)
        {
            LabelsToAdd = Preconditions.CheckNotNull(labelsToAdd);
        }
    }

    private readonly string _repoUrl;
    private readonly string _fetch;
    private readonly string _push;
    private readonly GeneralOptions _generalOptions;
    private readonly HgOptions _hgOptions;

    private HgDestination(
        string repoUrl, string fetch, string push, GeneralOptions generalOptions, HgOptions hgOptions)
    {
        _repoUrl = repoUrl;
        _fetch = fetch;
        _push = push;
        _generalOptions = generalOptions;
        _hgOptions = hgOptions;
    }

    public IDestination<HgRevision>.IWriter<HgRevision> NewWriter(WriterContext writerContext) =>
        new WriterImpl(
            _repoUrl, _fetch, _push, _generalOptions, _hgOptions, _hgOptions.VisitChangeDepth);

    public string GetLabelNameWhenOrigin() => HgRepository.HgOriginRevId;

    internal sealed class WriterImpl : IDestination<HgRevision>.IWriter<HgRevision>
    {
        private readonly string _repoUrl;
        private readonly string _remoteFetch;
        private readonly string _remotePush;
        private readonly GeneralOptions _generalOptions;
        private readonly HgOptions _hgOptions;
        private readonly bool _force;
        private readonly int _visitChangePageSize;
        private readonly Console _baseConsole;

        internal WriterImpl(
            string repoUrl,
            string remoteFetch,
            string remotePush,
            GeneralOptions generalOptions,
            HgOptions hgOptions,
            int visitChangePageSize)
        {
            _repoUrl = Preconditions.CheckNotNull(repoUrl);
            _remoteFetch = Preconditions.CheckNotNull(remoteFetch);
            _remotePush = Preconditions.CheckNotNull(remotePush);
            _generalOptions = generalOptions;
            _hgOptions = hgOptions;
            _force = generalOptions.IsForced();
            _visitChangePageSize = visitChangePageSize;
            _baseConsole = Preconditions.CheckNotNull(generalOptions.GetConsole());
        }

        private HgRevision? GetStartRef(HgRepository repo)
        {
            try
            {
                return repo.Identify(_remoteFetch);
            }
            catch (CannotResolveRevisionException)
            {
                if (_force)
                {
                    return null;
                }

                throw new RepoException(
                    $"Could not find {_remoteFetch} in {_repoUrl} and '--force' was not used");
            }
        }

        public DestinationStatus? GetDestinationStatus(Glob destinationFiles, string labelName)
        {
            HgRepository localRepo = GetRepository();
            PullFromRemote(_baseConsole, localRepo, _repoUrl, _remoteFetch);
            HgRevision? startRef = GetStartRef(localRepo);

            if (startRef == null)
            {
                return null;
            }

            IPathMatcher pathMatcher = destinationFiles.RelativeTo(string.Empty);
            var visitor = new DestinationStatusVisitor(pathMatcher, labelName);
            ChangeReader.Builder changeReader =
                ChangeReader.Builder
                    .ForDestination(localRepo, _baseConsole)
                    .SetKeyword(labelName + OriginLabelSeparator);

            HgVisitorUtil.VisitChanges(
                startRef,
                visitor,
                changeReader,
                _generalOptions,
                "get_destination_status",
                _visitChangePageSize);
            return visitor.GetDestinationStatus();
        }

        public bool SupportsHistory() =>
            throw new NotSupportedException("Not implemented yet");

        private HgRepository GetRepository() => _hgOptions.CachedBareRepoForUrl(_repoUrl);

        /// <summary>Returns the message for a change with any labels, if set.</summary>
        internal static ChangeMessage GetChangeMessage(
            TransformResult transformResult, string originLabelSeparator)
        {
            var messageInfo = new MessageInfo(
                transformResult.IsSetRevId()
                    ? ImmutableArray.Create(new LabelFinder(
                        transformResult.GetRevIdLabel() + originLabelSeparator
                        + transformResult.GetCurrentRevision().AsString()))
                    : ImmutableArray<LabelFinder>.Empty);
            ChangeMessage msg = ChangeMessage.ParseMessage(transformResult.GetSummary());
            foreach (LabelFinder label in messageInfo.LabelsToAdd)
            {
                msg = msg.WithNewOrReplacedLabel(label.GetName(), label.GetSeparator(), label.GetValue());
            }

            return msg;
        }

        private void PullFromRemote(
            Console console, HgRepository repo, string repoUrl, string reference)
        {
            using (_generalOptions.Profiler().Start("hg_destination_pull"))
            {
                try
                {
                    console.ProgressFmt("Hg Destination: Pulling: %s from %s", reference, repoUrl);
                    repo.PullFromRef(repoUrl, reference);
                }
                catch (CannotResolveRevisionException)
                {
                    string warning =
                        $"Hg Destination: '{reference}' doesn't exist in '{repoUrl}'";
                    ValidationException.CheckCondition(
                        _force,
                        "{0}. Use --force flag if you want to push anyway",
                        warning);
                    console.Warn(warning);
                }
            }
        }

        /// <summary>
        /// Add and delete files from a repository, based on the computed diff between the repository
        /// and a <paramref name="workDir"/>.
        /// </summary>
        private void GetDiffAndStageChanges(Glob destinationFiles, string workDir, HgRepository localRepo)
        {
            // Create a temp archive of the remote repository to compute diff with
            string tempArchivePath = _generalOptions.GetDirFactory().NewTempDir("tempArchive");
            localRepo.Archive(tempArchivePath);

            // Find excluded files in the archive
            ImmutableHashSet<string> excluded =
                FindExcludes(tempArchivePath, destinationFiles.RelativeTo(tempArchivePath));

            try
            {
                // Compute the diff between an archive of the remote repo and the workdir
                var diffFiles = DiffUtil.DiffFiles(
                    tempArchivePath, workDir, _generalOptions.IsVerbose(), _generalOptions.GetEnvironment());

                foreach (DiffFile diff in diffFiles)
                {
                    if (excluded.Contains(diff.GetName()))
                    {
                        continue;
                    }

                    DiffFile.Operation diffOp = diff.GetOperation();

                    if (diffOp == DiffFile.Operation.ADD)
                    {
                        CopyFile(
                            Path.Combine(workDir, diff.GetName()),
                            Path.Combine(localRepo.GetHgDir(), diff.GetName()),
                            overwrite: false);
                        localRepo.Hg(localRepo.GetHgDir(), "add", diff.GetName());
                    }

                    if (diffOp == DiffFile.Operation.MODIFIED)
                    {
                        CopyFile(
                            Path.Combine(workDir, diff.GetName()),
                            Path.Combine(localRepo.GetHgDir(), diff.GetName()),
                            overwrite: true);
                    }

                    if (diffOp == DiffFile.Operation.DELETE)
                    {
                        try
                        {
                            localRepo.Hg(localRepo.GetHgDir(), "remove", diff.GetName());
                        }
                        catch (RepoException e)
                        {
                            // Ignore a .hg_archival file that is not in the workdir nor in the local repo.
                            if (e.Message is null
                                || !e.Message.Contains(".hg_archival.txt: No such file or directory"))
                            {
                                throw;
                            }
                        }
                    }
                }
            }
            catch (InsideGitDirException e)
            {
                throw new RepoException($"Error computing file diff: {e.Message}", e);
            }
            finally
            {
                FileUtil.DeleteRecursively(tempArchivePath);
            }
        }

        private static void CopyFile(string source, string dest, bool overwrite)
        {
            string? dir = Path.GetDirectoryName(dest);
            if (!string.IsNullOrEmpty(dir))
            {
                Directory.CreateDirectory(dir);
            }

            File.Copy(source, dest, overwrite);
        }

        // Port of the HgExcludesFinder SimpleFileVisitor: collect files in the archive not matched by
        // destinationFiles, relative to the archive directory.
        private static ImmutableHashSet<string> FindExcludes(string directory, IPathMatcher destinationFiles)
        {
            var excluded = ImmutableHashSet.CreateBuilder<string>();
            foreach (string file in Directory.EnumerateFiles(directory, "*", SearchOption.AllDirectories))
            {
                if (!destinationFiles.Matches(file))
                {
                    excluded.Add(Path.GetRelativePath(directory, file));
                }
            }

            return excluded.ToImmutable();
        }

        /// <summary>Writes the changes in <paramref name="transformResult"/> to the destination repository.</summary>
        public IReadOnlyList<DestinationEffect> Write(
            TransformResult transformResult, Glob destinationFiles, Console console)
        {
            string workdir = transformResult.GetPath();

            HgRepository localRepo = GetRepository();
            console.Progress("Hg Destination: Pulling from " + _remoteFetch);
            PullFromRemote(console, localRepo, _repoUrl, _remoteFetch);
            localRepo.CleanUpdate(_remoteFetch);

            // Set the default path of the local repo to be the remote repo, so we can push to it
            File.WriteAllText(
                Path.Combine(localRepo.GetHgDir(), ".hg", "hgrc"),
                $"[paths]\ndefault = {_repoUrl}\n",
                new UTF8Encoding(encoderShouldEmitUTF8Identifier: false));

            console.Progress("Hg Destination: Computing diff");
            GetDiffAndStageChanges(destinationFiles, workdir, localRepo);

            console.Progress("Hg Destination: Creating a local commit");

            ChangeMessage msg = GetChangeMessage(transformResult, OriginLabelSeparator);
            string date = transformResult.GetTimestamp().ToString("r", CultureInfo.InvariantCulture);

            localRepo.Hg(
                localRepo.GetHgDir(),
                "commit",
                "--user",
                transformResult.GetAuthor().ToString(),
                "--date",
                date,
                "-m",
                msg.ToString());

            console.Progress($"Hg Destination: Pushing to {_repoUrl} {_remotePush}");
            localRepo.Hg(localRepo.GetHgDir(), "push", "--rev", _remotePush, _repoUrl);

            string tip = localRepo.Identify("tip").GetGlobalId();

            return ImmutableArray.Create(
                new DestinationEffect(
                    DestinationEffect.EffectType.CREATED,
                    $"Created revision {tip}",
                    transformResult.GetChanges().GetCurrent().Cast<OriginRef>().ToImmutableArray(),
                    new DestinationEffect.DestinationRef(tip, "commit", _repoUrl)));
        }

        public void VisitChanges(HgRevision? start, IChangesVisitor visitor) =>
            throw new NotSupportedException("Not implemented yet");
    }

    /// <summary>Builds a new <see cref="HgDestination"/>.</summary>
    internal static HgDestination NewHgDestination(
        string url, string fetch, string push, GeneralOptions generalOptions, HgOptions hgOptions) =>
        new(url, fetch, push, generalOptions, hgOptions);
}
