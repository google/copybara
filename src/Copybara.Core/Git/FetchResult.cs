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

namespace Copybara.Git;

/// <summary>
/// The result of executing a git fetch command. Port of
/// <c>com.google.copybara.git.FetchResult</c>.
/// </summary>
public sealed class FetchResult
{
    private readonly ImmutableDictionary<string, GitRevision> _deleted;
    private readonly ImmutableDictionary<string, GitRevision> _inserted;
    private readonly ImmutableDictionary<string, RefUpdate> _updated;

    public FetchResult(
        IReadOnlyDictionary<string, GitRevision> before,
        IReadOnlyDictionary<string, GitRevision> after)
    {
        var deleted = ImmutableDictionary.CreateBuilder<string, GitRevision>();
        var inserted = ImmutableDictionary.CreateBuilder<string, GitRevision>();
        var updated = ImmutableDictionary.CreateBuilder<string, RefUpdate>();

        foreach (var entry in before)
        {
            if (!after.TryGetValue(entry.Key, out var afterVal))
            {
                deleted[entry.Key] = entry.Value;
            }
            else if (!Equals(afterVal, entry.Value))
            {
                updated[entry.Key] = new RefUpdate(entry.Value, afterVal);
            }
        }
        foreach (var entry in after)
        {
            if (!before.ContainsKey(entry.Key))
            {
                inserted[entry.Key] = entry.Value;
            }
        }

        _deleted = deleted.ToImmutable();
        _inserted = inserted.ToImmutable();
        _updated = updated.ToImmutable();
    }

    public override string ToString() =>
        $"FetchResult{{deleted={FormatMap(_deleted)}, inserted={FormatMap(_inserted)},"
        + $" updated={FormatMap(_updated)}}}";

    private static string FormatMap<T>(ImmutableDictionary<string, T> map) =>
        "{" + string.Join(", ", map.Select(e => $"{e.Key}={e.Value}")) + "}";

    public IReadOnlyDictionary<string, GitRevision> GetDeleted() => _deleted;

    public IReadOnlyDictionary<string, GitRevision> GetInserted() => _inserted;

    public IReadOnlyDictionary<string, RefUpdate> GetUpdated() => _updated;

    /// <summary>A reference update for a fetch command. Contains before and after SHA-1.</summary>
    public sealed class RefUpdate
    {
        private readonly GitRevision _before;
        private readonly GitRevision _after;

        public RefUpdate(GitRevision before, GitRevision after)
        {
            _before = before;
            _after = after;
        }

        public GitRevision GetBefore() => _before;

        public GitRevision GetAfter() => _after;

        public override string ToString() => _before.GetHash() + " -> " + _after.GetHash();
    }
}
