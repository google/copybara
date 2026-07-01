/*
 * Copyright (C) 2016 Google Inc.
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

namespace Copybara.Util;

/// <summary>
/// Utility functions for Bourne shell commands, including escaping. Port of the subset of Bazel's
/// <c>ShellUtils</c> used by Copybara's <see cref="CommandRunner"/>.
/// </summary>
public static class ShellUtils
{
    private const string SafePunctuation = "@%-_+:,./";

    /// <summary>Escapes a word so it can be safely used as a single Bourne shell argument.</summary>
    public static string ShellEscape(string word)
    {
        int len = word.Length;
        if (len == 0)
        {
            // Empty string is a special case: needs to be quoted to ensure it becomes a separate
            // argument.
            return "''";
        }
        for (int ii = 0; ii < len; ii++)
        {
            char c = word[ii];
            if (!char.IsLetterOrDigit(c) && SafePunctuation.IndexOf(c) == -1)
            {
                return "'" + word.Replace("'", "'\\''") + "'";
            }
        }
        return word;
    }

    /// <summary>
    /// Given an argv array such as might be passed to execve(2), returns a string that can be copied
    /// and pasted into a Bourne shell for a similar effect.
    /// </summary>
    public static string PrettyPrintArgv(IEnumerable<string> argv)
    {
        var buf = new StringBuilder();
        foreach (var arg in argv)
        {
            if (buf.Length > 0)
            {
                buf.Append(' ');
            }
            buf.Append(ShellEscape(arg));
        }
        return buf.ToString();
    }
}
