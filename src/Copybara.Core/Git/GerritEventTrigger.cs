/*
 * Copyright (C) 2022 Google Inc.
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
using Copybara.Git.GerritApi;

namespace Copybara.Git;

/// <summary>
/// A simple pair to express Gerrit Events with arbitrary subtypes (Labels). Port of
/// <c>com.google.copybara.git.GerritEventTrigger</c>.
/// </summary>
public sealed class GerritEventTrigger
{
    private GerritEventTrigger(GerritEventType type, IReadOnlyList<string> subtypes)
    {
        Type = type;
        Subtypes = subtypes;
    }

    public GerritEventType Type { get; }

    public IReadOnlyList<string> Subtypes { get; }

    public static GerritEventTrigger Create(GerritEventType type, IEnumerable<string> subtypes) =>
        new(type, subtypes.ToImmutableArray());

    public override string ToString() => Type.ToString();
}
