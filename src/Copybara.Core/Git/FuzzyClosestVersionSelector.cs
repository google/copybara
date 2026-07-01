/*
 * Copyright (C) 2020 Google Inc.
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

using Copybara.Exceptions;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Git;

/// <summary>
/// A VersionSelector that heuristically tries to match a version to a git tag. This is best effort
/// and only recommended for testing. Port of
/// <c>com.google.copybara.git.FuzzyClosestVersionSelector</c>.
///
/// <para>The upstream implementation composes an <c>OrderedVersionSelector</c> over a
/// <c>TagVersionList</c>. Those git/version helper types are owned by a peer port; until they land,
/// this falls back to the documented behavior of returning the requested ref.</para>
/// </summary>
public class FuzzyClosestVersionSelector
{
    public string SelectVersion(string? requestedRef, GitRepository repo, string url, Console console)
    {
        ValidationException.CheckCondition(
            !string.IsNullOrEmpty(requestedRef),
            "Fuzzy version finding requires a ref to be explicitly specified");

        // TODO(peer): Wire up OrderedVersionSelector over the git TagVersionList once the
        // git/version helper types (TagVersionList, RequestedShaVersionSelector) are ported. Until
        // then, degrade to returning the requested ref, which is the documented best-effort
        // fallback for this selector.
        return requestedRef!;
    }
}
