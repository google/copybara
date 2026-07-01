/*
 * Copyright (C) 2023 Google Inc.
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

using Copybara.Version;
using SemanticVersion = Copybara.Rust.RustVersionRequirement.SemanticVersion;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Rust;

/// <summary>
/// A <see cref="IVersionSelector"/> that selects the latest version that satisfies the given cargo
/// version requirement.
/// </summary>
public class RustCratesIoVersionSelector : IVersionSelector
{
    private readonly RustVersionRequirement _requirement;

    public RustCratesIoVersionSelector(RustVersionRequirement requirement)
    {
        _requirement = requirement;
    }

    /// <exception cref="Copybara.Exceptions.ValidationException"/>
    /// <exception cref="Copybara.Exceptions.RepoException"/>
    public string? Select(IVersionList versionList, string? requestedRef, Console console)
    {
        string? latestVersion = null;

        foreach (string @ref in versionList.List())
        {
            if (_requirement.Fulfills(@ref))
            {
                if (requestedRef != null
                    && SemanticVersion.CreateFromVersionString(requestedRef)
                        .CompareTo(SemanticVersion.CreateFromVersionString(@ref)) == 0)
                {
                    latestVersion = @ref;
                    break;
                }

                if (latestVersion == null
                    || SemanticVersion.CreateFromVersionString(@ref)
                        .CompareTo(SemanticVersion.CreateFromVersionString(latestVersion)) > 0)
                {
                    latestVersion = @ref;
                }
            }
        }

        return latestVersion;
    }

    public override string ToString() =>
        $"rust.crates_io_version_selector(requirement = \"{_requirement.GetRequirementString()}\")";
}
