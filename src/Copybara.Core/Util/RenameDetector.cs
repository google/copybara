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

using System.Collections.Immutable;

namespace Copybara.Util;

/// <summary>
/// Class for detecting renames between two repo versions. This is intended to be used when
/// implementing <c>Destination</c> for repositories that don't automatically detect renames
/// (e.g. Mercurial).
/// </summary>
/// <typeparam name="TKey">type of key to use for referencing files in the prior revision.</typeparam>
public sealed class RenameDetector<TKey>
{
    /// <summary>
    /// The maximum score that can be returned. This value gives high-enough resolution for
    /// reasonably sized files and eliminates the risk of overflow for source files with fewer than
    /// 2,000,000 lines (roughly <c>int.MaxValue / MaxScore</c>).
    /// </summary>
    public const int MaxScore = 1000;

    private readonly bool _ignoreCarriageReturn;
    private readonly bool _ignoreWhitespace;
    private readonly bool _skipNewlinesInHash;
    private readonly bool _considerFilenames;
    private readonly ImmutableHashSet<string> _filenameExceptions;

    private readonly List<PriorFile> _priorFiles = new();

    public RenameDetector(bool ignoreCarriageReturn, bool ignoreWhitespace)
        : this(ignoreCarriageReturn, ignoreWhitespace, false)
    {
    }

    public RenameDetector(
        bool ignoreCarriageReturn, bool ignoreWhitespace, bool skipNewlinesInHash)
        : this(ignoreCarriageReturn, ignoreWhitespace, skipNewlinesInHash, false)
    {
    }

    public RenameDetector(
        bool ignoreCarriageReturn,
        bool ignoreWhitespace,
        bool skipNewlinesInHash,
        bool considerFilenames)
        : this(
            ignoreCarriageReturn,
            ignoreWhitespace,
            skipNewlinesInHash,
            considerFilenames,
            ImmutableHashSet<string>.Empty)
    {
    }

    public RenameDetector(
        bool ignoreCarriageReturn,
        bool ignoreWhitespace,
        bool skipNewlinesInHash,
        bool considerFilenames,
        ImmutableHashSet<string> filenameExceptions)
    {
        _ignoreCarriageReturn = ignoreCarriageReturn;
        _ignoreWhitespace = ignoreWhitespace;
        _skipNewlinesInHash = skipNewlinesInHash;
        _considerFilenames = considerFilenames;
        _filenameExceptions = filenameExceptions;
    }

    private sealed class PriorFile
    {
        public TKey Key = default!;
        public int[] Hashes = Array.Empty<int>();
    }

    /// <summary>Hashes a single file until the end of the stream.</summary>
    private int[] Hashes(Stream input)
    {
        using (input)
        {
            var hashes = new HashSet<int>();
            int hash = 0;
            bool hasPendingContent = false;
            var buffer = new byte[8192];
            int read;
            while ((read = input.Read(buffer, 0, buffer.Length)) > 0)
            {
                for (int i = 0; i < read; i++)
                {
                    byte b = buffer[i];
                    if (_ignoreCarriageReturn && b == (byte)'\r')
                    {
                        // Skip carriage return in Windows-style line endings when hashing.
                        continue;
                    }
                    if (_ignoreWhitespace && (b == (byte)' ' || b == (byte)'\t'))
                    {
                        continue;
                    }
                    if (b == (byte)'\n')
                    {
                        if (!_skipNewlinesInHash)
                        {
                            hash *= 31;
                            hash += b;
                            hashes.Add(hash);
                        }
                        else if (hasPendingContent)
                        {
                            hashes.Add(hash);
                        }
                        hash = 0;
                        hasPendingContent = false;
                    }
                    else
                    {
                        hash *= 31;
                        hash += b;
                        hasPendingContent = true;
                    }
                }
            }

            if (!_skipNewlinesInHash || hasPendingContent)
            {
                hashes.Add(hash);
            }
            int[] hashesArray = hashes.ToArray();
            Array.Sort(hashesArray);
            return hashesArray;
        }
    }

    /// <summary>
    /// Hashes a single file in the prior revision so it can be checked for similarities with files
    /// in the later revision. Closes <paramref name="input"/> before returning.
    /// </summary>
    public void AddPriorFile(TKey key, Stream input)
    {
        _priorFiles.Add(new PriorFile { Key = key, Hashes = Hashes(input) });
    }

    /// <summary>
    /// Hashes a single file in the later revision so it can be checked for similarities with all
    /// files in the prior revision added previously. Closes <paramref name="input"/> before
    /// returning.
    /// </summary>
    public ImmutableArray<Score> ScoresForLaterFile(Stream input)
    {
        if (_considerFilenames)
        {
            throw new InvalidOperationException(
                "Cannot call ScoresForLaterFile without laterKey when considerFilenames is true");
        }
        return ScoresForLaterFile(default!, input);
    }

    public ImmutableArray<Score> ScoresForLaterFile(TKey laterKey, Stream input)
    {
        var results = new List<Score>();
        int[] laterHashes = Hashes(input);
        if (IsEmpty(laterHashes))
        {
            return ImmutableArray<Score>.Empty;
        }
        string? laterFilename = _considerFilenames ? GetFilename(laterKey) : null;

        foreach (var priorFile in _priorFiles)
        {
            if (_considerFilenames)
            {
                string priorFilename = GetFilename(priorFile.Key);
                bool isException =
                    _filenameExceptions.Any(e => string.Equals(e, priorFilename, StringComparison.OrdinalIgnoreCase))
                    || (laterFilename != null
                        && _filenameExceptions.Any(
                            e => string.Equals(e, laterFilename, StringComparison.OrdinalIgnoreCase)));
                if (!isException && IsTooFar(priorFilename, laterFilename!))
                {
                    continue;
                }
            }

            // Determine the number of hashes that priorFile.Hashes and laterHashes have in common.
            int matchCount = 0;
            int priorIndex = 0;
            int laterIndex = 0;
            while (priorIndex < priorFile.Hashes.Length && laterIndex < laterHashes.Length)
            {
                int priorHash = priorFile.Hashes[priorIndex];
                int laterHash = laterHashes[laterIndex];
                if (laterHash > priorHash)
                {
                    priorIndex++;
                }
                else
                {
                    laterIndex++;
                    if (priorHash == laterHash)
                    {
                        matchCount++;
                    }
                }
            }
            if (matchCount != 0 && !IsEmpty(priorFile.Hashes))
            {
                int size = laterHashes.Length > priorFile.Hashes.Length
                    ? laterHashes.Length
                    : priorFile.Hashes.Length;
                results.Add(new Score(priorFile.Key, matchCount * MaxScore / size));
            }
        }

        results.Sort((a, b) => b.GetScore().CompareTo(a.GetScore()));
        return results.ToImmutableArray();
    }

    private static bool IsEmpty(int[] hashes) =>
        hashes.Length == 0 || (hashes.Length == 1 && hashes[0] == 0);

    private static bool IsTooFar(string name1, string name2)
    {
        int maxLen = Math.Max(name1.Length, name2.Length);
        if (maxLen == 0)
        {
            return false;
        }
        int distance = LevenshteinDistance(name1, name2);
        return distance * 2 > maxLen;
    }

    private static int LevenshteinDistance(string s, string t)
    {
        var distance = new int[s.Length + 1, t.Length + 1];
        for (int i = 0; i <= s.Length; i++)
        {
            distance[i, 0] = i;
        }
        for (int j = 1; j <= t.Length; j++)
        {
            distance[0, j] = j;
        }
        for (int i = 1; i <= s.Length; i++)
        {
            for (int j = 1; j <= t.Length; j++)
            {
                distance[i, j] = Math.Min(
                    Math.Min(distance[i - 1, j] + 1, distance[i, j - 1] + 1),
                    distance[i - 1, j - 1] + (s[i - 1] == t[j - 1] ? 0 : 1));
            }
        }
        return distance[s.Length, t.Length];
    }

    private static string GetFilename(object? key)
    {
        if (key == null)
        {
            return "";
        }
        string s = key.ToString() ?? "";
        int lastSlash = s.LastIndexOf('/');
        return lastSlash >= 0 ? s.Substring(lastSlash + 1) : s;
    }

    /// <summary>A prior-file key together with its similarity score to a later file.</summary>
    public sealed class Score
    {
        private readonly TKey _key;
        private readonly int _score;

        internal Score(TKey key, int score)
        {
            _key = key;
            _score = score;
        }

        public TKey GetKey() => _key;

        public int GetScore() => _score;

        public override string ToString() => $"Score{{key={_key}, score={_score}}}";
    }
}
