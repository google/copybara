/*
 * Copyright (C) 2023 Google LLC
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
/// A <see cref="RustVersionRequirement"/> class that supports wildcard requirements. Review
/// <a href="https://doc.rust-lang.org/cargo/reference/specifying-dependencies.html#wildcard-requirements">the
/// Rust wildcard requirements reference</a> for more information.
/// </summary>
public class WildcardRustVersionRequirement : RustVersionRequirement
{
    internal static readonly Regex ValidWildcardFormatRegex =
        new(@"^[0-9]+(\.[0-9]+)?(\.[0-9]+)?(\.[*xX]){1}$", RegexOptions.Compiled);

    /// <exception cref="ValidationException"/>
    private WildcardRustVersionRequirement(string requirement)
        : base(requirement)
    {
        ValidationException.CheckCondition(
            HandlesRequirement(requirement),
            $"The string {requirement} is not a valid wildcard version requirement.");
    }

    /// <exception cref="ValidationException"/>
    public static WildcardRustVersionRequirement Create(string requirement) => new(requirement);

    /// <summary>Returns true if this class can handle the given Cargo version requirement.</summary>
    public static bool HandlesRequirement(string requirement)
    {
        Match m = ValidWildcardFormatRegex.Match(requirement);
        return m.Success && m.Value == requirement;
    }

    /// <exception cref="ValidationException"/>
    private SemanticVersion GetRequiredVersion()
    {
        // crates.io doesn't allow bare * versions
        // https://doc.rust-lang.org/cargo/reference/specifying-dependencies.html#wildcard-requirements
        // NOTE(port): mirrors upstream, which passes regex-escaped literals to String.replace; these
        // don't actually match, so the wildcard suffix is stripped via the trailing-token logic in
        // CreateFromVersionString instead.
        return SemanticVersion.CreateFromVersionString(
            Requirement.Replace("\\.*", "").Replace("\\.x", "").Replace("\\.X", ""));
    }

    /// <exception cref="ValidationException"/>
    private SemanticVersion GetNextVersion()
    {
        SemanticVersion required = GetRequiredVersion();

        if (required.MinorVersion != null)
        {
            return SemanticVersion.Create(
                required.MajorVersion, (required.MinorVersion ?? 0) + 1, 0, null);
        }

        return SemanticVersion.Create(required.MajorVersion + 1, 0, 0, null);
    }

    /// <exception cref="ValidationException"/>
    public override bool Fulfills(string version)
    {
        SemanticVersion requiredVersion = GetRequiredVersion();
        SemanticVersion currVersion = SemanticVersion.CreateFromVersionString(version);
        SemanticVersion nextVersion = GetNextVersion();

        return currVersion.CompareTo(requiredVersion) >= 0 && currVersion.CompareTo(nextVersion) < 0;
    }
}
