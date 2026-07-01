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

/// <summary>Class that represents a Cargo comparison version requirement, e.g. &gt;= 1.2.0.</summary>
public class ComparisonRustVersionRequirement : RustVersionRequirement
{
    internal static readonly Regex ValidComparisonFormatRegex =
        new(@"^([<>=]=?)\s*([0-9].*)", RegexOptions.Compiled);

    private readonly string _operator;
    private readonly SemanticVersion _requirementVersion;

    /// <exception cref="ValidationException"/>
    private ComparisonRustVersionRequirement(string requirement)
        : base(requirement)
    {
        Match matcher = ValidComparisonFormatRegex.Match(requirement);
        ValidationException.CheckCondition(
            matcher.Success && matcher.Index == 0,
            $"The string {requirement} is not a valid default or caret version requirement.");
        _operator = matcher.Groups[1].Value;
        _requirementVersion = SemanticVersion.CreateFromVersionString(matcher.Groups[2].Value);
    }

    /// <exception cref="ValidationException"/>
    public static ComparisonRustVersionRequirement Create(string requirement) => new(requirement);

    /// <summary>Returns true if this class can handle the given Cargo version requirement.</summary>
    public static bool HandlesRequirement(string requirement)
    {
        Match m = ValidComparisonFormatRegex.Match(requirement);
        return m.Success && m.Index == 0;
    }

    /// <exception cref="ValidationException"/>
    public override bool Fulfills(string version)
    {
        SemanticVersion currentVersion = SemanticVersion.CreateFromVersionString(version);

        return _operator switch
        {
            ">" => CompareVersions(currentVersion, _requirementVersion) > 0,
            ">=" => CompareVersions(currentVersion, _requirementVersion) >= 0,
            "<" => CompareVersions(currentVersion, _requirementVersion) < 0,
            "<=" => CompareVersions(currentVersion, _requirementVersion) <= 0,
            "=" => CompareVersions(currentVersion, _requirementVersion) == 0,
            _ => false,
        };
    }

    private static int CompareVersions(SemanticVersion currentVersion, SemanticVersion requirementVersion)
    {
        // Comparison requirements treat absent minor/patch components as "don't care" (a 0
        // comparison), unlike the default SemanticVersion comparator which treats them as 0.
        int result = currentVersion.MajorVersion.CompareTo(requirementVersion.MajorVersion);
        if (result != 0)
        {
            return result;
        }

        result = CompareOptional(currentVersion.MinorVersion, requirementVersion.MinorVersion);
        if (result != 0)
        {
            return result;
        }

        result = CompareOptional(currentVersion.PatchVersion, requirementVersion.PatchVersion);
        if (result != 0)
        {
            return result;
        }

        return SemanticVersion.CompareEmptiesLast(
            currentVersion.PreReleaseIdentifier,
            requirementVersion.PreReleaseIdentifier,
            SemanticVersion.GetPreReleaseComparator());
    }

    private static int CompareOptional(int? k1, int? k2) =>
        k1 == null || k2 == null ? 0 : k1.Value.CompareTo(k2.Value);
}
