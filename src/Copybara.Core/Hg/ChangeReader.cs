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
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Revision;
using static Copybara.Hg.HgRepository;
using Author = Copybara.Authoring.Author;
using AuthorParser = Copybara.Authoring.AuthorParser;
using AuthoringModel = Copybara.Authoring.Authoring;
using Console = Copybara.Util.Console.Console;
using InvalidAuthorException = Copybara.Authoring.InvalidAuthorException;

namespace Copybara.Hg;

/// <summary>Utility class to introspect the log of a Mercurial (Hg) repository.</summary>
internal sealed class ChangeReader
{
    private static readonly string NullGlobalId = new('0', 40);

    private readonly HgRepository _repository;
    private readonly int _limit;
    private readonly int _skip;
    private readonly Console _console;
    private readonly AuthoringModel? _authoring;
    private readonly string? _keyword;

    private ChangeReader(
        HgRepository repository, int limit, int skip, Console console, AuthoringModel? authoring, string? keyword)
    {
        _repository = repository;
        _limit = limit;
        _skip = skip;
        _console = console;
        _authoring = authoring;
        _keyword = keyword;
    }

    internal IReadOnlyList<Change<HgRevision>> Run(string refExpression)
    {
        LogCmd logCmd = _repository.Log();

        if (_keyword is not null)
        {
            logCmd = logCmd.WithKeyword(_keyword);
        }

        if (_limit > 0)
        {
            if (_skip >= 0)
            {
                logCmd = logCmd.WithReferenceExpression(
                    string.Format("limit(::{0}, {1}, {2})", refExpression, _limit, _skip));
                return ParseChanges(logCmd.Run());
            }

            logCmd = logCmd.WithLimit(_limit);
        }

        logCmd = logCmd.WithReferenceExpression(refExpression);
        return ParseChanges(logCmd.Run());
    }

    internal sealed class Builder
    {
        private readonly HgRepository _repository;
        private readonly Console _console;
        private int _limit;
        private int _skip;
        private string? _keyword;
        private AuthoringModel? _authoring;

        private Builder(HgRepository repository, Console console)
        {
            _repository = Preconditions.CheckNotNull(repository);
            _console = Preconditions.CheckNotNull(console);
            _limit = 0;
            _skip = -1;
        }

        internal static Builder ForDestination(HgRepository repository, Console console) =>
            new(repository, console);

        internal static Builder ForOrigin(HgRepository repository, AuthoringModel authoring, Console console) =>
            new Builder(repository, console).SetAuthoring(authoring);

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

        internal Builder SetKeyword(string keyword)
        {
            _keyword = Preconditions.CheckNotNull(keyword);
            return this;
        }

        internal Builder SetAuthoring(AuthoringModel authoring)
        {
            _authoring = Preconditions.CheckNotNull(authoring);
            return this;
        }

        internal ChangeReader Build() =>
            new(_repository, _limit, _skip, _console, _authoring, _keyword);
    }

    private IReadOnlyList<Change<HgRevision>> ParseChanges(IReadOnlyList<HgLogEntry> logEntries)
    {
        var result = ImmutableArray.CreateBuilder<Change<HgRevision>>();

        foreach (HgLogEntry entry in logEntries)
        {
            var rev = new HgRevision(entry.GetGlobalId());
            if (NullGlobalId == rev.GetGlobalId())
            {
                continue;
            }

            Author user;
            try
            {
                user = AuthorParser.Parse(entry.GetUser() ?? string.Empty);
            }
            catch (InvalidAuthorException e)
            {
                _console.Warn($"Cannot parse commit user and email: {e.Message}");
                user = (_authoring ?? throw new RepoException("No default author provided."))
                    .GetDefaultAuthor();
            }

            ImmutableArray<HgRevision> parents =
                entry.GetParents().Select(p => new HgRevision(p)).ToImmutableArray();

            result.Add(new Change<HgRevision>(
                rev,
                user,
                entry.GetDescription() ?? string.Empty,
                entry.GetZonedDate(),
                ChangeMessage.ParseAllAsLabels(entry.GetDescription() ?? string.Empty).LabelsAsMultimap(),
                entry.GetFiles().ToHashSet(),
                parents.Length > 1,
                parents));
        }

        return result.ToImmutable();
    }
}
