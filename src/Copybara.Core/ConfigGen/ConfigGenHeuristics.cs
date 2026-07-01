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

using System.Collections.Immutable;
using System.Security.Cryptography;
using System.Text.RegularExpressions;
using Copybara;
using Copybara.Common;
using Copybara.Util;
using Console = Copybara.Util.Console.Console;

namespace Copybara.ConfigGen;

/// <summary>
/// Given a set of files from the origin and a set of files from the destination, it generates
/// origin_globs, destination_globs and core.moves to minimize the number of transformations for
/// converting code from origin to destination.
///
/// <para>Note that the generation is not perfect and should be reviewed by a human.</para>
/// </summary>
public class ConfigGenHeuristics
{
    // Regex to parse upstream semver-like version refs. Group 3 is expected to capture the separator
    // character.
    private static readonly Regex UpstreamVersionRefRegex =
        new(@"^v?(\d+)(([^\d\n])(?:\d+)){0,2}$");

    private readonly string _origin;
    private readonly string _destination;
    private readonly ImmutableHashSet<string> _destinationOnlyPaths;
    private readonly int _percentSimilar;
    private readonly bool _ignoreCarriageReturn;
    private readonly bool _ignoreWhitespace;
    private readonly GeneralOptions _generalOptions;
    private readonly ImmutableArray<string> _versions;

    /// <summary>Creates the Generator object.</summary>
    /// <param name="origin">the root folder for the files of the origin repository.</param>
    /// <param name="destination">the root folder for the files of the repository.</param>
    /// <param name="destinationOnlyPaths">paths known to be only in the destination so they are
    ///     skipped in the similarity check.</param>
    /// <param name="percentSimilar">percentage of similar lines to consider two files the same.</param>
    /// <param name="ignoreCarriageReturn">whether to ignore carriage return characters in file
    ///     content comparisons.</param>
    /// <param name="ignoreWhitespace">whether to ignore whitespace characters in file content
    ///     comparisons.</param>
    /// <param name="generalOptions">the <see cref="GeneralOptions"/> object.</param>
    /// <param name="versions">the list of version refs from the upstream.</param>
    public ConfigGenHeuristics(
        string origin,
        string destination,
        ImmutableHashSet<string> destinationOnlyPaths,
        int percentSimilar,
        bool ignoreCarriageReturn,
        bool ignoreWhitespace,
        GeneralOptions generalOptions,
        ImmutableArray<string> versions)
    {
        _origin = Preconditions.CheckNotNull(origin);
        _destination = Preconditions.CheckNotNull(destination);
        _destinationOnlyPaths = Preconditions.CheckNotNull(destinationOnlyPaths);
        _percentSimilar = percentSimilar;
        _ignoreCarriageReturn = ignoreCarriageReturn;
        _ignoreWhitespace = ignoreWhitespace;
        _generalOptions = generalOptions;
        _versions = versions;
    }

    /// <summary>Result of the config generation.</summary>
    public sealed class Result
    {
        private readonly Glob _originGlob;
        private readonly GeneratorTransformations _transformations;
        private readonly DestinationExcludePaths _destinationExcludePaths;
        private readonly bool _shouldUseVersionSelector;
        private readonly string? _versionSeparator;

        public Result(
            Glob originFiles,
            GeneratorTransformations transformations,
            DestinationExcludePaths destinationExcludePaths,
            bool shouldUseVersionSelector,
            string? versionSeparator)
        {
            _originGlob = originFiles;
            _transformations = transformations;
            _destinationExcludePaths = destinationExcludePaths;
            _shouldUseVersionSelector = shouldUseVersionSelector;
            _versionSeparator = versionSeparator;
        }

        public Glob GetOriginGlob() => _originGlob;

        public GeneratorTransformations GetTransformations() => _transformations;

        public DestinationExcludePaths GetDestinationExcludePaths() => _destinationExcludePaths;

        public bool GetShouldUseVersionSelector() => _shouldUseVersionSelector;

        public string? GetVersionSeparator() => _versionSeparator;
    }

    /// <summary>A path and its similarity score to some other path.</summary>
    public readonly record struct PathAndScore(string Path, int Score);

    /// <summary>
    /// Run the config generation to find a good origin_files, destination_files and core.moves needed
    /// to convert the code from origin to destination.
    /// </summary>
    /// <returns>an object containing all the heuristic results.</returns>
    public Result Run()
    {
        var gitFiles = ListFiles(_origin);
        var g3Files = ListFiles(_destination);
        var destinationToOriginMapping =
            GetDestinationToOriginMapping(gitFiles, g3Files, _generalOptions.GetConsole());

        // Map of Origin file paths to destination file paths.
        // If multiple destination files map to the same origin file, we preserve the mapping with
        // the highest score.
        var similarFiles = new Dictionary<string, string>();
        foreach (var group in destinationToOriginMapping.GroupBy(e => e.Value.Path))
        {
            var best = group.Aggregate((a, b) => a.Value.Score >= b.Value.Score ? a : b);
            similarFiles[best.Key] = group.Key;
        }

        var originGlob = GetOriginGlob(gitFiles, similarFiles, g3Files);
        var moves = GenerateMoves(similarFiles);
        var destinationExcludePaths =
            new DestinationExcludePaths(
                GetDestinationExcludePaths(g3Files, similarFiles, _destinationOnlyPaths));
        string? tagSeparator = GetVersionStringSeparator(_versions);

        return new Result(
            originGlob.GlobValue,
            new GeneratorTransformations(moves),
            destinationExcludePaths,
            tagSeparator != null,
            tagSeparator);
    }

    /// <summary>Generates a mapping of destination files to origin files.</summary>
    /// <param name="gitFiles">the list of paths in the origin.</param>
    /// <param name="g3Files">the list of paths in the destination.</param>
    /// <returns>a map of destination to origin files, with their similarity scores.</returns>
    protected Dictionary<string, PathAndScore> GetDestinationToOriginMapping(
        ImmutableHashSet<string> gitFiles, ImmutableHashSet<string> g3Files, Console console)
    {
        var similarityDetector =
            SimilarityDetector.Create(
                _origin,
                gitFiles,
                _destinationOnlyPaths,
                _percentSimilar,
                _ignoreCarriageReturn,
                _ignoreWhitespace);
        // Map of destination file paths to origin file paths with similarity score. Sorted for
        // deterministic behavior (Java uses a TreeMap).
        var destinationToOriginMapping =
            new SortedDictionary<string, PathAndScore>(StringComparer.Ordinal);

        foreach (var file in g3Files)
        {
            var originPathAndScore = similarityDetector.Find(PathOps.Resolve(_destination, file));
            // If we find an origin file with a higher similarity score to the destination file, map
            // that origin file instead to the destination file.
            if (originPathAndScore.HasValue)
            {
                var pathAndScore = originPathAndScore.Value;
                if (!destinationToOriginMapping.TryGetValue(file, out var existing)
                    || pathAndScore.Score > existing.Score)
                {
                    destinationToOriginMapping[file] = pathAndScore;
                }
            }
        }
        return new Dictionary<string, PathAndScore>(destinationToOriginMapping);
    }

    private static string? GetVersionStringSeparator(ImmutableArray<string> versions)
    {
        var separators = new Dictionary<string, int>();
        if (versions.IsEmpty)
        {
            return null;
        }

        foreach (var version in versions)
        {
            var matcher = UpstreamVersionRefRegex.Match(version);
            if (matcher.Success && matcher.Groups.Count >= 4)
            {
                string sep = matcher.Groups[3].Value;
                separators[sep] = separators.GetValueOrDefault(sep, 0) + 1;
            }
        }

        if (separators.Count == 0)
        {
            return null;
        }
        return separators.OrderByDescending(e => e.Value).First().Key;
    }

    private IncludesGlob GetOriginGlob(
        ImmutableHashSet<string> gitFiles,
        Dictionary<string, string> similarFiles,
        ImmutableHashSet<string> g3Files)
    {
        var originOnly = new HashSet<string>(gitFiles);
        originOnly.ExceptWith(similarFiles.Keys);

        var destinationOnly = new HashSet<string>(g3Files);
        destinationOnly.ExceptWith(similarFiles.Values);

        var originGlob =
            new IncludesGlob(ImmutableHashSet.Create("**"), ImmutableHashSet<string>.Empty)
                .MinimizeScore(similarFiles.Keys.ToList(), originOnly, 0);

        // Enable to debug what is being generated:
        if (_generalOptions.IsVerbose())
        {
            Debug(similarFiles, destinationOnly, originGlob);
        }

        return ConsolidateCommonPattern(
            originGlob, similarFiles.Keys.ToHashSet(), p => p.StartsWith('.'), ".**");
    }

    /// <summary>
    /// Returns the set of files that are in the destination but not in the origin. This is the union
    /// of the known destinationOnlyPaths and the files that are in g3Files but not in similarFiles.
    /// </summary>
    private ImmutableHashSet<string> GetDestinationExcludePaths(
        ImmutableHashSet<string> g3Files,
        Dictionary<string, string> similarFiles,
        ImmutableHashSet<string> destinationOnlyPaths)
    {
        var similarValues = similarFiles.Values.ToHashSet();
        return destinationOnlyPaths
            .Union(g3Files.Where(p => !similarValues.Contains(p)))
            .ToImmutableHashSet();
    }

    /// <summary>
    /// Generates the minimal amount of core.moves to map files from the origin to the destination.
    /// See the Java documentation for the full algorithm description.
    /// </summary>
    private ImmutableArray<GeneratorMove> GenerateMoves(Dictionary<string, string> similarFiles)
    {
        var set = new LinkedList<KeyValuePair<string, string>>(similarFiles);
        var result = new MovesTrie();
        while (set.Count > 0)
        {
            var entry = set.First!.Value;
            set.RemoveFirst();
            string origin = entry.Key;
            string dest = entry.Value;
            if (origin == dest)
            {
                // already correctly positioned
                continue;
            }
            string commonSuffix = CommonSuffix(origin, dest);
            bool handled = false;
            while (commonSuffix.Length != 0)
            {
                int suffixCount = NameCount(commonSuffix);
                string originPrefix =
                    suffixCount != NameCount(origin)
                        ? Subpath(origin, 0, NameCount(origin) - suffixCount)
                        : "";
                string destPrefix =
                    suffixCount != NameCount(dest)
                        ? Subpath(dest, 0, NameCount(dest) - suffixCount)
                        : "";
                bool tooBroad = false;
                var includedPaths = new HashSet<KeyValuePair<string, string>>();
                foreach (var e in similarFiles)
                {
                    if (StartsWith(e.Key, originPrefix))
                    {
                        string relocated =
                            Resolve(
                                destPrefix,
                                Subpath(e.Key, NameCount(originPrefix), NameCount(e.Key)));
                        if (relocated == e.Value)
                        {
                            includedPaths.Add(e);
                        }
                        else
                        {
                            tooBroad = true;
                            break;
                        }
                    }
                }
                if (tooBroad)
                {
                    commonSuffix = suffixCount == 1
                        ? "" // 'foo' -> "", subpath doesn't work here.
                        : Subpath(commonSuffix, 1, suffixCount);
                }
                else
                {
                    // Successfully moves a bunch of files with a directory move.
                    foreach (var p in includedPaths)
                    {
                        set.Remove(p);
                    }
                    result.InsertMove(new GeneratorMove(originPrefix, destPrefix));
                    handled = true;
                    break;
                }
            }
            if (!handled)
            {
                result.InsertMove(new GeneratorMove(origin, dest));
            }
        }
        return result.GetMovesInOrder();
    }

    private void Debug(
        Dictionary<string, string> similarFiles,
        HashSet<string> destinationOnly,
        IncludesGlob originGlob)
    {
        Console console = _generalOptions.GetConsole();
        foreach (var e in similarFiles)
        {
            console.Verbose(e.Key + " -> " + e.Value);
        }

        console.Verbose("git_files = " + originGlob.GlobValue);

        foreach (var path in destinationOnly)
        {
            console.Verbose("G3 Only: " + path);
        }
    }

    /// <summary>
    /// Consolidate more than one pattern that matches the predicate with a single replacement pattern
    /// when there is no file being migrated that matches the predicate.
    /// </summary>
    private IncludesGlob ConsolidateCommonPattern(
        IncludesGlob originGlob,
        HashSet<string> migratedFiles,
        Func<string, bool> filePredicate,
        string replacement)
    {
        if (originGlob.Excludes.Count(filePredicate) <= 1
            || migratedFiles.Any(p => filePredicate(p)))
        {
            return originGlob;
        }
        var newExcludes = ImmutableHashSet.CreateBuilder<string>();
        newExcludes.Add(replacement);
        foreach (var pattern in originGlob.Excludes)
        {
            if (filePredicate(pattern))
            {
                continue;
            }
            newExcludes.Add(pattern);
        }
        return new IncludesGlob(originGlob.Includes, newExcludes.ToImmutable());
    }

    private sealed class ExcludesGlob : IncludesGlob
    {
        internal ExcludesGlob(IReadOnlySet<string> includes, IReadOnlySet<string> excludes)
            : base(includes, excludes)
        {
        }

        protected override int Score() =>
            // If excludes needs excludes, it is not a good excludes.
            Excludes.Count == 0 ? base.Score() : int.MaxValue;

        protected override IncludesGlob WithExcludes(
            IReadOnlyCollection<string> toBeIncluded, IReadOnlySet<string> toBeExcluded)
        {
            var matchingExcludes = FindMatchingExcludes(toBeExcluded);
            return Create(Includes, matchingExcludes);
        }

        protected override IncludesGlob Create(IReadOnlySet<string> includes, IReadOnlySet<string> excludes) =>
            new ExcludesGlob(includes, excludes);
    }

    private class IncludesGlob : IComparable<IncludesGlob>
    {
        internal readonly IReadOnlySet<string> Includes;
        internal readonly IReadOnlySet<string> Excludes;
        internal readonly Glob GlobValue;

        internal IncludesGlob(IReadOnlySet<string> includes, IReadOnlySet<string> excludes)
        {
            Includes = includes;
            Excludes = excludes;
            GlobValue = Glob.CreateGlob(includes, excludes);
        }

        protected virtual IncludesGlob Create(IReadOnlySet<string> includes, IReadOnlySet<string> excludes) =>
            // Use sorted set to have it sorted.
            new IncludesGlob(
                new SortedSet<string>(includes, StringComparer.Ordinal),
                new SortedSet<string>(excludes, StringComparer.Ordinal));

        protected virtual int Score() =>
            Math.Max(Includes.Count, 1) * Math.Max(Excludes.Count, 1);

        public int CompareTo(IncludesGlob? o) =>
            o == null ? 1 : Score().CompareTo(o.Score());

        internal IncludesGlob MinimizeScore(
            IReadOnlyCollection<string> toBeIncluded, IReadOnlySet<string> toBeExcluded, int level)
        {
            IncludesGlob globAndScore = WithExcludes(toBeIncluded, toBeExcluded);

            var recursiveIncludes = new Dictionary<string, HashSet<string>>();
            var newIncludes = new HashSet<string>();
            var newExcludes = new HashSet<string>();
            foreach (var p in toBeIncluded)
            {
                if (NameCount(p) <= level + 1)
                {
                    newIncludes.Add(p);
                }
                else
                {
                    string key = Subpath(p, 0, level + 1) + "/**";
                    if (!recursiveIncludes.TryGetValue(key, out var bucket))
                    {
                        bucket = new HashSet<string>();
                        recursiveIncludes[key] = bucket;
                    }
                    bucket.Add(p);
                }
            }
            // For each recursive pattern, try to optimize for fewer entries and see if the
            // combination has fewer entries than globAndScore.
            foreach (var pattern in recursiveIncludes.Keys)
            {
                IncludesGlob newGlob =
                    Create(ImmutableHashSet.Create(pattern), ImmutableHashSet<string>.Empty)
                        .MinimizeScore(recursiveIncludes[pattern].ToList(), toBeExcluded, level + 1);
                newIncludes.UnionWith(newGlob.Includes);
                newExcludes.UnionWith(newGlob.Excludes);
            }
            IncludesGlob comboGlob = Create(newIncludes, newExcludes);

            return comboGlob.Score() < globAndScore.Score() ? comboGlob : globAndScore;
        }

        protected virtual IncludesGlob WithExcludes(
            IReadOnlyCollection<string> toBeIncluded, IReadOnlySet<string> toBeExcluded)
        {
            var excludedPaths = FindMatchingExcludes(toBeExcluded);
            IncludesGlob optimizedExcludes =
                new ExcludesGlob(ImmutableHashSet.Create("**"), ImmutableHashSet<string>.Empty)
                    .MinimizeScore(excludedPaths.ToList(), toBeIncluded.ToImmutableHashSet(), 0);

            // Found a better excludes than the naive approach of listing all excludes!
            if (optimizedExcludes.Excludes.Count == 0)
            {
                return Create(Includes, optimizedExcludes.Includes);
            }

            return Create(Includes, excludedPaths);
        }

        protected IReadOnlySet<string> FindMatchingExcludes(IReadOnlySet<string> toBeExcluded)
        {
            IPathMatcher pathMatcher = Glob.CreateGlob(Includes).RelativeTo("/");
            var excludedPaths = ImmutableHashSet.CreateBuilder<string>();
            foreach (var ex in toBeExcluded)
            {
                if (pathMatcher.Matches(PathOps.Resolve("/", ex)))
                {
                    excludedPaths.Add(ex);
                }
            }
            return excludedPaths.ToImmutable();
        }

        public override string ToString() =>
            $"{GetType().Name}(score: {Score()}, {GlobValue})";
    }

    private static string CommonSuffix(string a, string b)
    {
        var aNames = Names(a);
        var bNames = Names(b);
        var paths = new LinkedList<string>();
        for (int aIndex = aNames.Length - 1, bIndex = bNames.Length - 1;
             aIndex >= 0 && bIndex >= 0;
             aIndex--, bIndex--)
        {
            if (aNames[aIndex] != bNames[bIndex])
            {
                break;
            }
            paths.AddFirst(aNames[aIndex]);
        }
        return string.Join("/", paths);
    }

    /// <summary>
    /// Detects similar files based on hash and similarity. Used to find similar files in the origin
    /// and destination, and map them together.
    /// </summary>
    protected sealed class SimilarityDetector
    {
        // Useful for binaries.
        private readonly ImmutableListMultimap<string, string> _hashBased;
        private readonly RenameDetector<string> _similarLines;
        private readonly ImmutableHashSet<string> _destinationOnlyPaths;
        private readonly int _percentSimilar;

        private SimilarityDetector(
            ImmutableListMultimap<string, string> hashBased,
            RenameDetector<string> similarLines,
            ImmutableHashSet<string> destinationOnlyPaths,
            int percentSimilar)
        {
            _hashBased = hashBased;
            _similarLines = similarLines;
            _destinationOnlyPaths = destinationOnlyPaths;
            _percentSimilar = percentSimilar;
        }

        internal PathAndScore? Find(string path)
        {
            if (_destinationOnlyPaths.Contains(PathOps.GetFileName(path)))
            {
                return null;
            }

            byte[] content = File.ReadAllBytes(path);

            // Highest priority same hash. RenameDetector fails for small files.
            string? hashFinding = null;
            int bestSuffix = -1;
            foreach (var o in _hashBased.Get(Hash(content)))
            {
                int suffix = NameCount(CommonSuffix(path, o));
                if (suffix > bestSuffix)
                {
                    bestSuffix = suffix;
                    hashFinding = o;
                }
            }
            if (hashFinding != null)
            {
                return new PathAndScore(hashFinding, RenameDetector<string>.MaxScore);
            }

            // Second priority similarity.
            var scores = _similarLines.ScoresForLaterFile(new MemoryStream(content));
            var score = scores.Length > 0 ? scores[0] : null;
            if (score != null
                && score.GetScore() > RenameDetector<string>.MaxScore * _percentSimilar / 100)
            {
                return new PathAndScore(score.GetKey(), score.GetScore());
            }
            return null;
        }

        internal static SimilarityDetector Create(
            string parent,
            ImmutableHashSet<string> files,
            ImmutableHashSet<string> destinationOnlyPaths,
            int percentSimilar,
            bool ignoreCarriageReturn,
            bool ignoreWhitespace)
        {
            var similarLines =
                new RenameDetector<string>(
                    ignoreCarriageReturn, ignoreWhitespace, skipNewlinesInHash: true);
            var hashes = ImmutableListMultimap<string, string>.CreateBuilder();
            foreach (var file in files)
            {
                byte[] bytes = File.ReadAllBytes(PathOps.Resolve(parent, file));
                hashes.Put(Hash(bytes), file);
                similarLines.AddPriorFile(file, new MemoryStream(bytes));
            }
            return new SimilarityDetector(
                hashes.Build(), similarLines, destinationOnlyPaths, percentSimilar);
        }

        private static string Hash(byte[] bytes) =>
            Convert.ToHexStringLower(SHA256.HashData(bytes));
    }

    private static ImmutableHashSet<string> ListFiles(string path)
    {
        var result = ImmutableHashSet.CreateBuilder<string>();
        if (!Directory.Exists(path))
        {
            return result.ToImmutable();
        }
        foreach (var file in Directory.EnumerateFiles(path, "*", SearchOption.AllDirectories))
        {
            var attrs = File.GetAttributes(file);
            if ((attrs & FileAttributes.ReparsePoint) != 0)
            {
                continue;
            }
            result.Add(PathOps.Relativize(path, file));
        }
        return result.ToImmutable();
    }

    /// <summary>Represents a core.move() to be included in the generation.</summary>
    public sealed class GeneratorMove : IEquatable<GeneratorMove>
    {
        private readonly string _before;
        private readonly string _after;

        public GeneratorMove(string before, string after)
        {
            _before = before;
            _after = after;
        }

        public string GetBefore() => _before;

        public string GetAfter() => _after;

        public bool Equals(GeneratorMove? that) =>
            that is not null && _before == that._before && _after == that._after;

        public override bool Equals(object? o) => Equals(o as GeneratorMove);

        public override int GetHashCode() => HashCode.Combine(_before, _after);

        public override string ToString() => $"core.move(\"{_before}\", \"{_after}\")";
    }

    /// <summary>Represents a collection of transformations to be included in the generation.</summary>
    public sealed class GeneratorTransformations
    {
        private readonly ImmutableArray<GeneratorMove> _moves;

        public GeneratorTransformations(ImmutableArray<GeneratorMove> moves)
        {
            _moves = moves;
        }

        public ImmutableArray<GeneratorMove> GetMoves() => _moves;
    }

    /// <summary>
    /// Represents a collection of paths that are found to only be present in the destination. This
    /// should be a union of the given destinationOnlyPaths and files found to only exist in the
    /// destination.
    /// </summary>
    public sealed class DestinationExcludePaths
    {
        private readonly ImmutableHashSet<string> _paths;

        public DestinationExcludePaths(ImmutableHashSet<string> paths)
        {
            _paths = paths;
        }

        public ImmutableHashSet<string> GetPaths() => _paths;

        public override string ToString() => string.Join(", ", _paths);
    }

    /// <summary>
    /// A prefix tree (trie) used to maintain the order of core.move transforms, such that each
    /// transform does not interfere with the operations of another.
    /// </summary>
    private sealed class MovesTrie
    {
        private readonly MovesTrieNode _root = new();

        private sealed class MovesTrieNode
        {
            private readonly Dictionary<string, MovesTrieNode> _children = new();
            private GeneratorMove? _move;

            public GeneratorMove? GetMove() => _move;

            public void SetMove(GeneratorMove move) => _move = move;

            public void AddChildren(string path, MovesTrieNode node) => _children[path] = node;

            public IReadOnlyDictionary<string, MovesTrieNode> GetChildren() => _children;
        }

        public void InsertMove(GeneratorMove move)
        {
            MovesTrieNode currentNode = _root;

            // Edge case - if the before path is an empty path, this means it's moving the root.
            if (move.GetBefore().Length == 0)
            {
                currentNode.SetMove(move);
                return;
            }

            foreach (var name in Names(move.GetBefore()))
            {
                if (!currentNode.GetChildren().ContainsKey(name))
                {
                    currentNode.AddChildren(name, new MovesTrieNode());
                }
                currentNode = currentNode.GetChildren()[name];
            }
            currentNode.SetMove(move);
        }

        public ImmutableArray<GeneratorMove> GetMovesInOrder() => GetMovesInOrder(_root);

        private static ImmutableArray<GeneratorMove> GetMovesInOrder(MovesTrieNode startNode)
        {
            var moves = ImmutableArray.CreateBuilder<GeneratorMove>();
            foreach (var child in startNode.GetChildren().Values)
            {
                moves.AddRange(GetMovesInOrder(child));
            }
            var move = startNode.GetMove();
            if (move != null)
            {
                moves.Add(move);
            }
            return moves.ToImmutable();
        }
    }

    // ---- java.nio.file.Path shims (forward-slash separated string paths) ----

    private static string[] Names(string path) =>
        path.Split('/', StringSplitOptions.RemoveEmptyEntries);

    private static int NameCount(string path) => Names(path).Length;

    /// <summary>Equivalent of <c>Path.subpath(begin, end)</c>.</summary>
    private static string Subpath(string path, int begin, int end)
    {
        var names = Names(path);
        return string.Join("/", names[begin..end]);
    }

    /// <summary>Equivalent of <c>Path.startsWith(other)</c> on name components.</summary>
    private static bool StartsWith(string path, string prefix)
    {
        if (prefix.Length == 0)
        {
            return true;
        }
        var pathNames = Names(path);
        var prefixNames = Names(prefix);
        if (prefixNames.Length > pathNames.Length)
        {
            return false;
        }
        for (int i = 0; i < prefixNames.Length; i++)
        {
            if (pathNames[i] != prefixNames[i])
            {
                return false;
            }
        }
        return true;
    }

    /// <summary>Equivalent of <c>base.resolve(other)</c>.</summary>
    private static string Resolve(string basePath, string other)
    {
        if (other.Length == 0)
        {
            return basePath;
        }
        if (basePath.Length == 0)
        {
            return other;
        }
        return basePath + "/" + other;
    }
}
