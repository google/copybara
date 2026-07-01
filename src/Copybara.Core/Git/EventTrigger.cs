/*
 * Copyright (C) 2021 Google Inc.
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
using Copybara.Git.GitHub.Api;

namespace Copybara.Git;

/// <summary>
/// A simple pair to express GitHub Events with arbitrary subtypes (Status, CheckRun). Port of
/// <c>com.google.copybara.git.EventTrigger</c>.
/// </summary>
public sealed class EventTrigger
{
    private readonly GitHubEventType _type;
    private readonly ImmutableHashSet<string> _subtypes;

    private EventTrigger(GitHubEventType type, ImmutableHashSet<string> subtypes)
    {
        _type = type;
        _subtypes = subtypes;
    }

    public GitHubEventType Type() => _type;

    public IReadOnlySet<string> Subtypes() => _subtypes;

    public static EventTrigger Create(GitHubEventType type, IEnumerable<string> subtypes) =>
        new(type, subtypes.ToImmutableHashSet());

    public override bool Equals(object? o) =>
        o is EventTrigger other && _type == other._type && _subtypes.SetEquals(other._subtypes);

    public override int GetHashCode()
    {
        var hash = new HashCode();
        hash.Add(_type);
        foreach (var s in _subtypes.OrderBy(x => x, StringComparer.Ordinal))
        {
            hash.Add(s);
        }
        return hash.ToHashCode();
    }

    public override string ToString() =>
        $"EventTrigger{{type={_type}, subtypes=[{string.Join(", ", _subtypes)}]}}";
}
