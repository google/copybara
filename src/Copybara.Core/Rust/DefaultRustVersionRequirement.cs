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

using System.Text.RegularExpressions;
using Copybara.Exceptions;
using SemanticVersion = Copybara.Rust.RustVersionRequirement.SemanticVersion;

namespace Copybara.Rust;

/// <summary>
/// Class that represents the default cargo version requirement. e.g., "1.2.3" or "^1.2.3".
/// </summary>
public class DefaultRustVersionRequirement : RustVersionRequirement
{
    internal static readonly Regex ValidDefaultFormatRegex =
        new(@"^\^?[0-9]+(\.[0-9]+)?(\.[0-9]+)?(-(.*))?(-(.*))?$", RegexOptions.Compiled);

    /// <exception cref="ValidationException"/>
    private DefaultRustVersionRequirement(string requirement)
        : base(requirement)
    {
        ValidationException.CheckCondition(
            IsFullMatch(requirement),
            $"The string {requirement} is not a valid default or caret version requirement.");
        Requirement = requirement.Replace("^", "");
    }

    /// <exception cref="ValidationException"/>
    public static DefaultRustVersionRequirement Create(string requirement) => new(requirement);

    /// <summary>Returns true if this class can handle the given Cargo version requirement.</summary>
    public static bool HandlesRequirement(string requirement) => IsFullMatch(requirement);

    private static bool IsFullMatch(string requirement)
    {
        Match m = ValidDefaultFormatRegex.Match(requirement);
        return m.Success && m.Value == requirement;
    }

    /// <exception cref="ValidationException"/>
    private SemanticVersion GetRequiredVersion() =>
        SemanticVersion.CreateFromVersionString(Requirement);

    /// <summary>
    /// Gets the next version, according to the passed in required version. This is the earliest
    /// version that no longer satisfies the requirement. Therefore, any acceptable version must be
    /// less than this.
    /// </summary>
    /// <exception cref="ValidationException"/>
    private SemanticVersion GetNextVersion()
    {
        // Handle special cases: 0 and 0.0
        if (Requirement == "0")
        {
            return SemanticVersion.Create(1, 0, 0, null);
        }

        if (Requirement == "0.0")
        {
            return SemanticVersion.Create(0, 1, 0, null);
        }

        SemanticVersion requiredVersion = GetRequiredVersion();
        if (requiredVersion.MajorVersion > 0)
        {
            return SemanticVersion.Create(requiredVersion.MajorVersion + 1, 0, 0, null);
        }

        if ((requiredVersion.MinorVersion ?? 0) > 0)
        {
            return SemanticVersion.Create(0, (requiredVersion.MinorVersion ?? 0) + 1, 0, null);
        }

        return SemanticVersion.Create(0, 0, (requiredVersion.PatchVersion ?? 0) + 1, null);
    }

    /// <exception cref="ValidationException"/>
    public override bool Fulfills(string version)
    {
        SemanticVersion requiredVersion = GetRequiredVersion();
        SemanticVersion currVersion = SemanticVersion.CreateFromVersionString(version);
        // Ensure that a pre-release of a next major version (which compares less than the next major
        // version) doesn't fulfill a requirement for the previous major version.
        SemanticVersion currVersionNoPreRelease = currVersion.WithPreReleaseIdentifier(null);
        SemanticVersion nextVersion = GetNextVersion();

        return currVersion.CompareTo(requiredVersion) >= 0
            && currVersionNoPreRelease.CompareTo(nextVersion) < 0;
    }
}
