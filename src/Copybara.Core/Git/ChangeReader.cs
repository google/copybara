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
using Copybara.Revision;
using Copybara.Util;
using Console = Copybara.Util.Console.Console;
using AuthoringT = Copybara.Authoring.Authoring;
using Author = Copybara.Authoring.Author;

namespace Copybara.Git;

/// <summary>
/// Utility class to introspect the log of a Git repository. Port of
/// <c>com.google.copybara.git.ChangeReader</c>.
/// </summary>
internal sealed class ChangeReader
{
    internal const string BranchCommitLogHeading = "-- Branch commit log --";

    private readonly AuthoringT? _authoring;
    private readonly GitRepository _repository;
    private readonly int _limit;
    private readonly ImmutableArray<string> _roots;
    private readonly bool _includeBranchCommitLogs;
    private readonly string? _url;
    private readonly bool _firstParent;
    private readonly bool _partialFetch;
    private readonly bool _topoOrder;
    private readonly int _skip;
    private readonly int _batchSize;
    private readonly string? _grepString;

    private ChangeReader(
        AuthoringT? authoring,
        GitRepository repository,
        int limit,
        IEnumerable<string> roots,
        bool includeBranchCommitLogs,
        string? url,
        bool firstParent,
        bool partialFetch,
        bool topoOrder,
        int skip,
        int batchSize,
        string? grepString)
    {
        _authoring = authoring;
        _repository = Preconditions.CheckNotNull(repository, "repository");
        _limit = limit;
        _roots = roots.ToImmutableArray();
        _includeBranchCommitLogs = includeBranchCommitLogs;
        _url = url;
        _firstParent = firstParent;
        _partialFetch = partialFetch;
        _topoOrder = topoOrder;
        _skip = skip;
        _batchSize = batchSize;
        _grepString = grepString;
    }

    internal IReadOnlyList<Change<GitRevision>> Run(GitRevision rev) =>
        Run(
            null,
            rev,
            historyIsNonLinear: false,
            ImmutableDictionary<string, ImmutableListMultimap<string, string>>.Empty);

    /// <summary>
    /// Computes a list of changes from the refExpression and propagates labels contained in
    /// <paramref name="labels"/> onto the resulting change list.
    /// </summary>
    internal IReadOnlyList<Change<GitRevision>> Run(
        GitRevision? fromRev,
        GitRevision toRev,
        bool historyIsNonLinear,
        IReadOnlyDictionary<string, ImmutableListMultimap<string, string>> labels)
    {
        string refExpression =
            fromRev == null || historyIsNonLinear
                ? toRev.GetHash()
                : fromRev.GetHash() + ".." + toRev.GetHash();
        GitRepository.LogCmd logCmd =
            _repository.Log(refExpression).FirstParent(_firstParent).TopoOrder(_topoOrder);
        if (_limit != -1)
        {
            logCmd = logCmd.WithLimit(_limit);
        }
        if (_skip > 0)
        {
            logCmd = logCmd.WithSkip(_skip);
        }
        if (_batchSize > 0)
        {
            logCmd = logCmd.WithBatchSize(_batchSize);
        }
        if (_grepString != null)
        {
            logCmd = logCmd.Grep(_grepString);
        }
        if (_partialFetch && _roots.Contains(""))
        {
            throw new ValidationException(
                "Config error: partial_fetch feature is not compatible with fetching the whole"
                    + " repo.");
        }
        if (_partialFetch)
        {
            logCmd = logCmd.WithPaths(_roots);
        }

        // Log command does not filter by roots here because of how git log works. Some commits (e.g.
        // fake merges) might not include the files in the log, and filtering here would return
        // incorrect results. We do filter later on the changes to match the actual glob.
        return ParseChanges(
            logCmd.IncludeFiles(true).IncludeMergeDiff(true).Run(), labels, toRev);
    }

    private string BranchCommitLog(GitRevision @ref, IReadOnlyList<GitRevision> parents)
    {
        if (parents.Count <= 1)
        {
            // Not a merge commit, so don't bother showing full log of branch commits.
            return "";
        }
        if (!_includeBranchCommitLogs)
        {
            return "";
        }

        IReadOnlyList<GitRepository.GitLogEntry> entries =
            _repository
                .Log(parents[0].GetHash() + ".." + @ref.GetHash())
                .WithPaths(Glob.IsEmptyRoot(_roots) ? ImmutableArray<string>.Empty : _roots)
                .FirstParent(false)
                .Run();

        if (entries.Count == 0)
        {
            return "";
        }
        // Remove the merge commit. Since we already have that in the body.
        entries = entries.Skip(1).ToList();

        var sb = new StringBuilder("\n").Append(BranchCommitLogHeading).Append('\n');
        bool first = true;
        foreach (var e in entries)
        {
            if (!first)
            {
                sb.Append('\n');
            }
            sb.Append("commit ")
                .Append(e.Commit.GetHash())
                .Append('\n')
                .Append("Author:  ")
                .Append(FilterAuthor(e.Author))
                .Append('\n')
                .Append("Date:    ")
                .Append(e.AuthorDate)
                .Append('\n')
                .Append('\n')
                .Append("    ")
                .Append((e.Body ?? "").Replace("\n", "    \n"));
            first = false;
        }
        return sb.ToString();
    }

    private IReadOnlyList<Change<GitRevision>> ParseChanges(
        IReadOnlyList<GitRepository.GitLogEntry> logEntries,
        IReadOnlyDictionary<string, ImmutableListMultimap<string, string>> labels,
        GitRevision toRev)
    {
        var result = new List<Change<GitRevision>>();
        GitRevision? last = null;
        foreach (var e in logEntries)
        {
            // Keep the first commit if repeated (merge commits).
            if (last != null && last.Equals(e.Commit))
            {
                continue;
            }
            last = e.Commit;
            ImmutableListMultimap<string, string> labelsToCopy =
                labels.TryGetValue(e.Commit.GetHash(), out var found)
                    ? found
                    : ImmutableListMultimap<string, string>.Empty;
            // Carry over the context reference to the corresponding change in the list.
            if (last.GetHash() == toRev.GetHash() && toRev.ContextReference() != null)
            {
                last = last.WithContextReference(toRev.ContextReference()!);
            }
            result.Add(
                new Change<GitRevision>(
                    (_url != null ? last.WithUrl(_url) : last).WithLabels(labelsToCopy),
                    FilterAuthor(e.Author),
                    (e.Body ?? "") + BranchCommitLog(last, e.Parents),
                    e.AuthorDate,
                    ChangeMessage.ParseAllAsLabels(e.Body ?? "").LabelsAsMultimap(),
                    e.Files,
                    e.Parents.Count > 1,
                    e.Parents.ToImmutableArray()));
        }
        result.Reverse();
        return result;
    }

    private Author FilterAuthor(Author author) =>
        _authoring == null || _authoring.UseAuthor(author.Email)
            ? author
            : _authoring.GetDefaultAuthor();

    /// <summary>Builder for <see cref="ChangeReader"/>.</summary>
    internal sealed class Builder
    {
        private AuthoringT? _authoring;
        private readonly GitRepository _repository;
        private int _limit = -1;
        private ImmutableArray<string> _roots = ImmutableArray.Create("");
        private bool _includeBranchCommitLogs;
        private string? _url;
        private bool _firstParent;
        private bool _topoOrder;
        private bool _partialFetch;
        private int _skip;
        private int _batchSize;
        private string? _grepString;

        internal static Builder ForDestination(GitRepository repository, Console console) =>
            new(repository, console);

        internal static Builder ForOrigin(
            AuthoringT authoring, GitRepository repository, Console console) =>
            new Builder(repository, console).SetAuthoring(authoring);

        private Builder(GitRepository repository, Console console)
        {
            _repository = Preconditions.CheckNotNull(repository, "repository");
            Preconditions.CheckNotNull(console, "console");
        }

        internal Builder SetLimit(int limit)
        {
            Preconditions.CheckArgument(limit > 0);
            _limit = limit;
            return this;
        }

        internal Builder SetSkip(int skip)
        {
            Preconditions.CheckArgument(skip >= 0);
            _skip = skip;
            return this;
        }

        internal Builder SetBatchSize(int batchSize)
        {
            Preconditions.CheckArgument(batchSize >= 0);
            _batchSize = batchSize;
            return this;
        }

        private Builder SetAuthoring(AuthoringT authoring)
        {
            _authoring = Preconditions.CheckNotNull(authoring, "authoring");
            return this;
        }

        internal Builder SetPartialFetch(bool partialFetch)
        {
            _partialFetch = partialFetch;
            return this;
        }

        internal Builder SetFirstParent(bool firstParent)
        {
            _firstParent = firstParent;
            return this;
        }

        internal Builder SetTopoOrder(bool topoOrder)
        {
            _topoOrder = topoOrder;
            return this;
        }

        internal Builder SetIncludeBranchCommitLogs(bool includeBranchCommitLogs)
        {
            _includeBranchCommitLogs = includeBranchCommitLogs;
            return this;
        }

        internal Builder SetUrl(string? url)
        {
            _url = url;
            return this;
        }

        /// <summary>Only return commits that match the given paths in the Git log command.</summary>
        internal Builder SetRoots(IEnumerable<string> roots)
        {
            _roots = roots.ToImmutableArray();
            return this;
        }

        /// <summary>Grep for the given pattern in the Git log command.</summary>
        internal Builder Grep(string grepString)
        {
            _grepString = grepString;
            return this;
        }

        internal ChangeReader Build() =>
            new(
                _authoring,
                _repository,
                _limit,
                _roots,
                _includeBranchCommitLogs,
                _url,
                _firstParent,
                _partialFetch,
                _topoOrder,
                _skip,
                _batchSize,
                _grepString);
    }
}
