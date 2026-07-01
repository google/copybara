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

using System.Collections.Immutable;
using Copybara.Exceptions;
using Copybara.Git.Version;
using Copybara.Go;
using Copybara.Version;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Git;

/// <summary>
/// A VersionSelector that heuristically tries to match a version to a git tag. This is best effort
/// and only recommended for testing. Port of
/// <c>com.google.copybara.git.FuzzyClosestVersionSelector</c>.
/// </summary>
public class FuzzyClosestVersionSelector
{
    public string SelectVersion(string? requestedRef, GitRepository repo, string url, Console console)
    {
        // Move this check where it is used
        ValidationException.CheckCondition(
            !string.IsNullOrEmpty(requestedRef),
            "Fuzzy version finding requires a ref to be explicitly specified");

        var selector =
            new OrderedVersionSelector(
                ImmutableArray.Create<IVersionSelector>(
                    new PseudoVersionSelector(),
                    new RequestedShaVersionSelector(),
                    new RequestedExactMatchSelector(),
                    new CorrectorVersionSelector(console),
                    new RequestedVersionSelector()));
        try
        {
            return selector.Select(
                new RefspecVersionList.TagVersionList(repo, url), requestedRef, console)!;
        }
        catch (RepoException e)
        {
            // Technically this could be a real RepoException, but the current interface
            //
            console.WarnFmt("Unable to obtain tags for {0}. {1}", url, e);
            return requestedRef!;
        }
    }
}
