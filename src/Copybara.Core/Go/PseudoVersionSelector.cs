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
using Copybara.Version;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Go;

/// <summary>
/// Given a requested version, if that version is a go pseudoversion, it returns the short sha1.
/// </summary>
public class PseudoVersionSelector : IVersionSelector
{
    private static readonly Regex GoPseudoVersion =
        new(@"^v?\d+[.]\d+[.]\d+-(?:[\d+\w]+[.])?\d+-([a-f0-9]+)(?:\+.*)?$", RegexOptions.Compiled);

    public string? Select(IVersionList versionList, string? requestedRef, Console console)
    {
        Match matcher = GoPseudoVersion.Match(requestedRef ?? "");
        if (matcher.Success)
        {
            return matcher.Groups[1].Value;
        }

        return null;
    }

    public override string ToString() => nameof(PseudoVersionSelector);
}
