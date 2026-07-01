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

using System.Text;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Version;

/// <summary>
/// A version selector that, given a requested version, tries to find a matching tag by using several
/// fuzzing heuristics.
/// </summary>
public class CorrectorVersionSelector : IVersionSelector
{
    private readonly Console _console;

    public CorrectorVersionSelector(Console console)
    {
        _console = console;
    }

    public string? Select(IVersionList versionList, string? requestedRef, Console console)
    {
        if (requestedRef == null)
        {
            return null;
        }
        string cleanedVersion = StripVersion(requestedRef);
        foreach (string tag in versionList.List())
        {
            if (StripVersion(tag) == cleanedVersion)
            {
                _console.InfoFmt(
                    "Assuming version {0} references {1} ({2})", requestedRef, tag, cleanedVersion);
                return tag;
            }
        }
        return null;
    }

    private static bool IsDigit(char c) => c >= '0' && c <= '9';

    private static bool IsVersionPart(char c) => IsDigit(c) || c == '.';

    private static string StripVersion(string version)
    {
        // Trim leading non-numeric characters.
        int start = 0;
        while (start < version.Length && !IsDigit(version[start]))
        {
            start++;
        }
        string strippedPrefix = version.Substring(start);

        // Normalize separators ",;-_" to '.'.
        var normalizedBuilder = new StringBuilder(strippedPrefix.Length);
        foreach (char c in strippedPrefix)
        {
            normalizedBuilder.Append(c is ',' or ';' or '-' or '_' ? '.' : c);
        }
        string normalizedSeparator = normalizedBuilder.ToString();

        var strippedVersion = new StringBuilder();
        int index = 0;
        while (index < normalizedSeparator.Length)
        {
            if (RegionMatchesIgnoreCase(normalizedSeparator, index, "RC"))
            {
                strippedVersion.Append("RC");
                index += 2;
                continue;
            }
            if (RegionMatchesIgnoreCase(normalizedSeparator, index, "PL"))
            {
                strippedVersion.Append("PL");
                index += 2;
                continue;
            }
            if (IsVersionPart(normalizedSeparator[index]))
            {
                strippedVersion.Append(normalizedSeparator[index]);
                index++;
                continue;
            }
            // fast-forward through strings that might contain but not start with RC/PL
            while (index < normalizedSeparator.Length
                   && !IsVersionPart(normalizedSeparator[index]))
            {
                index++;
            }
        }
        return strippedVersion.ToString();
    }

    private static bool RegionMatchesIgnoreCase(string s, int index, string other)
    {
        if (index + other.Length > s.Length)
        {
            return false;
        }
        return string.Compare(
            s, index, other, 0, other.Length, StringComparison.OrdinalIgnoreCase) == 0;
    }

    public override string ToString() => nameof(CorrectorVersionSelector);
}
