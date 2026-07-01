/*
 * Copyright (C) 2025 Google LLC.
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
/// Class that represents an "epoch" version requirement.
///
/// <para>This is distinct from DefaultVersionRequirement, a DefaultVersionRequirement can be e.g.
/// <c>2.1.0</c>, whereas here the epoch is the same for <c>2.0</c>. An additional benefit of using
/// epoch version requirements is that they allow prereleases to match the main release branch, so
/// <c>2.0.0-pre</c> will match the requirement <c>2</c>.</para>
///
/// <para>This has a pretty restrictive set of versions allowed: only <c>x</c>, <c>0.x</c>, and
/// <c>0.0.x</c> are supported.</para>
///
/// <para>This should not be used with version numbers that come from Cargo, but may be used with
/// version numbers in copy.bara.sky.</para>
///
/// <para>This type of version requirement is sometimes found in projects that vendor at most one
/// copy per major version stream (like google3).</para>
/// </summary>
public class EpochRustVersionRequirement : RustVersionRequirement
{
    internal static readonly Regex ValidDefaultFormatRegex =
        new(@"^(0\.)?(0\.)?[0-9]$", RegexOptions.Compiled);

    /// <exception cref="ValidationException"/>
    private EpochRustVersionRequirement(string requirement)
        : base(requirement)
    {
        ValidationException.CheckCondition(
            HandlesRequirement(requirement),
            $"The string {requirement} is not a valid default or caret version requirement.");
        Requirement = requirement.Replace("^", "");
    }

    /// <exception cref="ValidationException"/>
    public static EpochRustVersionRequirement Create(string requirement) => new(requirement);

    /// <summary>Returns true if this class can handle the given Cargo version requirement.</summary>
    public static bool HandlesRequirement(string requirement)
    {
        Match m = ValidDefaultFormatRegex.Match(requirement);
        return m.Success && m.Value == requirement;
    }

    /// <exception cref="ValidationException"/>
    private SemanticVersion GetRequiredVersion() =>
        SemanticVersion.CreateFromVersionString(Requirement);

    /// <exception cref="ValidationException"/>
    public override bool Fulfills(string version)
    {
        SemanticVersion requiredVersion = GetRequiredVersion();
        SemanticVersion currVersion = SemanticVersion.CreateFromVersionString(version);

        if (requiredVersion.MajorVersion != currVersion.MajorVersion)
        {
            return false;
        }

        if (requiredVersion.MinorVersion != null
            && requiredVersion.MinorVersion != currVersion.MinorVersion)
        {
            return false;
        }

        if (requiredVersion.PatchVersion != null
            && requiredVersion.PatchVersion != currVersion.PatchVersion)
        {
            return false;
        }

        // Explicitly doesn't handle prereleases: all prereleases are compatible within an epoch
        // even though they are not semver-compatible.
        return true;
    }
}
