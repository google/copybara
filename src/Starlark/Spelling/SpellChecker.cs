// Copyright 2016 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

namespace Starlark.Spelling;

/// <summary>
/// Class that provides functions to do spell checking, i.e. detect typos and make suggestions.
/// </summary>
public static class SpellChecker
{
    /// <summary>
    /// Computes the edit distance between two strings. The edit distance is the minimum number of
    /// insertions, deletions and replacements to transform a string into the other string.
    ///
    /// <para><paramref name="maxEditDistance"/> is the maximum distance the function can return. If
    /// it would be greater, the function returns -1. It is useful for speeding up the
    /// computations.</para>
    /// </summary>
    public static int EditDistance(string s1, string s2, int maxEditDistance)
    {
        // This is the Levenshtein distance, as described here:
        // http://en.wikipedia.org/wiki/Levenshtein_distance
        //
        // We don't need to keep the full matrix. To update a cell, we only
        // need top-left, top, and left values. Using a single array is
        // sufficient. Top value is still in row[j] from the last iteration.
        // Top-left value is stored in 'previous'. Left value is row[j - 1].

        if (s1.Equals(s2, StringComparison.Ordinal))
        {
            return 0;
        }

        // Short-circuit based on string length.
        if (Math.Abs(s1.Length - s2.Length) > maxEditDistance)
        {
            return -1;
        }

        int[] row = new int[s2.Length + 1];
        for (int i = 0; i <= s2.Length; i++)
        {
            row[i] = i;
        }

        for (int i = 1; i <= s1.Length; i++)
        {
            row[0] = i;
            int bestInTheRow = row[0];
            int previous = i - 1;

            for (int j = 1; j <= s2.Length; j++)
            {
                int old = row[j];

                row[j] = Math.Min(
                    previous + (s1[i - 1] == s2[j - 1] ? 0 : 1),
                    1 + Math.Min(row[j - 1], row[j]));
                previous = old;
                bestInTheRow = Math.Min(bestInTheRow, row[j]);
            }

            if (bestInTheRow > maxEditDistance)
            {
                return -1;
            }
        }

        int result = row[s2.Length];
        return result <= maxEditDistance ? result : -1;
    }

    /// <summary>
    /// Find in words which string is the most similar to input (according to the edit distance,
    /// ignoring case) - or null if no string is similar enough. In case of equality, the first one
    /// in words wins.
    /// </summary>
    public static string? Suggest(string input, IEnumerable<string> words)
    {
        string? best = null;
        // Heuristic: the expected number of typos depends on the length of the word.
        int bestDistance = Math.Min(5, (input.Length + 1) / 2);
        input = input.ToLowerInvariant();
        foreach (string candidate in words)
        {
            int d = EditDistance(input, candidate.ToLowerInvariant(), bestDistance);
            if (d >= 0 && d < bestDistance)
            {
                bestDistance = d;
                best = candidate;
            }
        }

        return best;
    }

    /// <summary>
    /// Return a string to be used at the end of an error message. It is either an empty string, or a
    /// spelling suggestion, e.g. " (did you mean 'x'?)".
    /// </summary>
    public static string DidYouMean(string input, IEnumerable<string> words)
    {
        string? suggestion = Suggest(input, words);
        if (suggestion == null)
        {
            return "";
        }
        else
        {
            return " (did you mean '" + suggestion + "'?)";
        }
    }
}
