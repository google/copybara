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
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Rust;

/// <summary>Represents a Cargo version requirement.</summary>
[StarlarkBuiltin("rust_version_requirement", Doc = "Represents a Cargo version requirement.")]
public abstract class RustVersionRequirement : IStarlarkValue
{
    protected string Requirement;

    protected RustVersionRequirement(string requirement)
    {
        Requirement = requirement;
    }

    /// <exception cref="ValidationException"/>
    public static RustVersionRequirement GetVersionRequirement(string requirement, bool allowEpochs)
    {
        // TODO(chriscampos): Support additional types of version requirements
        if (MultipleRustVersionRequirement.HandlesRequirement(requirement))
        {
            return MultipleRustVersionRequirement.Create(requirement);
        }

        if (allowEpochs && EpochRustVersionRequirement.HandlesRequirement(requirement))
        {
            return EpochRustVersionRequirement.Create(requirement);
        }

        if (DefaultRustVersionRequirement.HandlesRequirement(requirement))
        {
            return DefaultRustVersionRequirement.Create(requirement);
        }

        if (ComparisonRustVersionRequirement.HandlesRequirement(requirement))
        {
            return ComparisonRustVersionRequirement.Create(requirement);
        }

        if (TildeRustVersionRequirement.HandlesRequirement(requirement))
        {
            return TildeRustVersionRequirement.Create(requirement);
        }

        if (WildcardRustVersionRequirement.HandlesRequirement(requirement))
        {
            return WildcardRustVersionRequirement.Create(requirement);
        }

        throw new ValidationException(
            $"The requirement {requirement} is currently not supported.");
    }

    /// <summary>
    /// Given a semantic version string, returns true if the version fulfills this version
    /// requirement.
    /// </summary>
    /// <param name="version">The semantic version string.</param>
    /// <returns>A boolean indicating if the version fulfills this version requirement.</returns>
    /// <exception cref="ValidationException">If there is an issue parsing the version string.</exception>
    [StarlarkMethod("fulfills",
        Doc =
            "Given a semantic version string, returns true if the version fulfills this version"
            + " requirement.")]
    public abstract bool Fulfills(
        [Param(Name = "fulfills", Named = true, Doc = "The version requirement")]
        string version);

    public string GetRequirementString() => Requirement;

    /// <summary>Represents a semantic version for a Rust crate.</summary>
    public sealed class SemanticVersion : IComparable<SemanticVersion>
    {
        private static readonly Regex ValidVersionPattern =
            new(@"^([0-9]+)(\.[0-9]+)?(\.[0-9]+)?(-(.*))?(\+?.*)?$", RegexOptions.Compiled);

        public int MajorVersion { get; }

        public int? MinorVersion { get; }

        public int? PatchVersion { get; }

        public string? PreReleaseIdentifier { get; }

        private SemanticVersion(
            int majorVersion,
            int? minorVersion,
            int? patchVersion,
            string? preReleaseIdentifier)
        {
            MajorVersion = majorVersion;
            MinorVersion = minorVersion;
            PatchVersion = patchVersion;
            PreReleaseIdentifier = preReleaseIdentifier;
        }

        public static SemanticVersion Create(
            int majorVersion,
            int minorVersion,
            int patchVersion,
            string? preReleaseIdentifier) =>
            new(majorVersion, minorVersion, patchVersion, preReleaseIdentifier);

        /// <exception cref="ValidationException"/>
        public static SemanticVersion CreateFromVersionString(string version)
        {
            Match matcher = ValidVersionPattern.Match(version);
            ValidationException.CheckCondition(
                matcher.Success && matcher.Value == version,
                $"The string {version} is not a valid Rust semantic version.");

            int majorVersion = int.Parse(matcher.Groups[1].Value);
            int? minorVersion = matcher.Groups[2].Success
                ? int.Parse(matcher.Groups[2].Value.Replace(".", ""))
                : null;
            int? patchVersion = matcher.Groups[3].Success
                ? int.Parse(matcher.Groups[3].Value.Replace(".", ""))
                : null;
            string? preReleaseIdentifier = matcher.Groups[5].Success ? matcher.Groups[5].Value : null;

            return new SemanticVersion(majorVersion, minorVersion, patchVersion, preReleaseIdentifier);
        }

        public SemanticVersion WithPreReleaseIdentifier(string? preReleaseIdentifier) =>
            new(MajorVersion, MinorVersion, PatchVersion, preReleaseIdentifier);

        public int CompareTo(SemanticVersion? other)
        {
            if (other == null)
            {
                return 1;
            }

            int result = MajorVersion.CompareTo(other.MajorVersion);
            if (result != 0)
            {
                return result;
            }

            result = (MinorVersion ?? 0).CompareTo(other.MinorVersion ?? 0);
            if (result != 0)
            {
                return result;
            }

            result = (PatchVersion ?? 0).CompareTo(other.PatchVersion ?? 0);
            if (result != 0)
            {
                return result;
            }

            // preReleaseIdentifier comparison with empties (null) last.
            return CompareEmptiesLast(PreReleaseIdentifier, other.PreReleaseIdentifier, GetPreReleaseComparator());
        }

        /// <summary>
        /// Compares two optional values so that a present value sorts before an absent one (empties
        /// last), mirroring Guava's <c>Comparators.emptiesLast</c>.
        /// </summary>
        internal static int CompareEmptiesLast(
            string? o1, string? o2, Comparison<string> comparator)
        {
            bool e1 = o1 == null;
            bool e2 = o2 == null;
            if (e1 && e2)
            {
                return 0;
            }

            if (e1)
            {
                return 1;
            }

            if (e2)
            {
                return -1;
            }

            return comparator(o1!, o2!);
        }

        public static Comparison<string> GetPreReleaseComparator() =>
            (o1, o2) =>
            {
                // This follows the SemVer specification: https://semver.org/#spec-item-11
                if (o1 == o2)
                {
                    return 0;
                }

                // Split the pre-release strings into lists, separated by .
                string[] list1 = o1.Split('.');
                string[] list2 = o2.Split('.');

                int min = Math.Min(list1.Length, list2.Length);
                for (int i = 0; i < min; i++)
                {
                    // If both elements are numeric, they are compared as numbers.
                    int result;
                    string elem1 = list1[i];
                    string elem2 = list2[i];
                    if (int.TryParse(elem1, out int n1) && int.TryParse(elem2, out int n2))
                    {
                        result = n1.CompareTo(n2);
                    }
                    else
                    {
                        result = string.CompareOrdinal(elem1, elem2);
                    }

                    if (result != 0)
                    {
                        return result;
                    }
                }

                // If the pre-release identifiers are equal to this point, the larger identifier wins.
                return list1.Length.CompareTo(list2.Length);
            };
    }
}
