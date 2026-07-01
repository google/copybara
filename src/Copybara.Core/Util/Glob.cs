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
using System.Text;
using Copybara.Common;
using Starlark.Annot;
using Starlark.Eval;
using StarlarkRuntime = Starlark.Eval.Starlark;

namespace Copybara.Util;

/// <summary>
/// A <see cref="IPathMatcher"/> builder that creates a matcher relative to a root path. Port of
/// <c>com.google.copybara.util.Glob</c>.
///
/// <para>The returned <see cref="IPathMatcher"/> returns true if any of the <c>paths</c> expressions
/// match. If <c>paths</c> is empty it will not match any file.</para>
/// </summary>
[StarlarkBuiltin(
    "glob",
    Doc = "A glob represents a set of relative filepaths in the Copybara workdir. Most consumers "
        + "will also accept a list of fully qualified (no wildcards) file names instead.")]
public class Glob : IStarlarkValue, IEquatable<Glob>
{
    public static readonly Glob AllFiles = CreateGlob(ImmutableArray.Create("**"));

    protected readonly ImmutableArray<GlobAtom> Include;
    private readonly ImmutableArray<Glob> _globInclude;
    private readonly Glob? _exclude;

    internal Glob(IEnumerable<GlobAtom> include, IEnumerable<Glob> globInclude, Glob? exclude)
    {
        Include = Preconditions.CheckNotNull(include).ToImmutableArray();
        _globInclude = Preconditions.CheckNotNull(globInclude).ToImmutableArray();
        _exclude = exclude;
    }

    /// <summary>
    /// Implements the Starlark <c>+</c> (union / list concatenation) and <c>-</c> (difference)
    /// operators. Port of Java's <c>binaryOp</c>.
    /// </summary>
    public Glob BinaryOp(string op, object that, bool thisLeft)
    {
        switch (op)
        {
            case "+":
                if (that is Glob addGlob)
                {
                    return Union(this, addGlob);
                }
                if (that is IEnumerable<string> addList)
                {
                    return new Glob(
                        ImmutableArray<GlobAtom>.Empty,
                        new[] { this, SequenceGlob.OfIterable(addList) },
                        null);
                }
                throw StarlarkRuntime.Errorf(
                    "Cannot concatenate {0} with {1}. Only a glob can be concatenated to a glob",
                    this, that);
            case "-":
                if (that is Glob subGlob)
                {
                    return Difference(this, subGlob);
                }
                if (that is IEnumerable<string> subList)
                {
                    Glob list = SequenceGlob.OfIterable(subList);
                    return new Glob(
                        Include, _globInclude, _exclude != null ? Union(_exclude, list) : list);
                }
                throw StarlarkRuntime.Errorf(
                    "Cannot subtract {0} from {1}. Only a glob can be subtracted from a glob", that, this);
            default:
                throw StarlarkRuntime.Errorf("Glob does not support {0}", op);
        }
    }

    /// <summary>
    /// Compute the 'set union' of two Globs, which is a Glob that will match any Path matched by at
    /// least one of those two Globs.
    /// </summary>
    public static Glob Union(Glob glob1, Glob glob2)
    {
        if (Equals(glob1._exclude, glob2._exclude))
        {
            return new Glob(
                glob1.Include.Concat(glob2.Include),
                glob1._globInclude.Concat(glob2._globInclude),
                glob1._exclude);
        }
        if (glob1._exclude == null)
        {
            return new Glob(
                glob1.Include, glob1._globInclude.Concat(new[] { glob2 }), null);
        }
        if (glob2._exclude == null)
        {
            return new Glob(
                glob2.Include, glob2._globInclude.Concat(new[] { glob1 }), null);
        }
        return new Glob(ImmutableArray<GlobAtom>.Empty, new[] { glob1, glob2 }, null);
    }

    /// <summary>
    /// Compute the 'set difference' of two Globs, which is a Glob that will match any Path which is
    /// matched by the first Glob, but not matched by the second Glob.
    /// </summary>
    public static Glob Difference(Glob glob1, Glob glob2)
    {
        if (glob1._exclude == null)
        {
            return new Glob(glob1.Include, glob1._globInclude, glob2);
        }
        return new Glob(glob1.Include, glob1._globInclude, Union(glob1._exclude, glob2));
    }

    /// <summary>Checks if the given <paramref name="changedFiles"/> are or are descendants of the roots.</summary>
    public static bool AffectsRoots(
        ImmutableHashSet<string> roots, IReadOnlyCollection<string>? changedFiles)
    {
        if (changedFiles == null || IsEmptyRoot(roots))
        {
            return true;
        }
        foreach (var file in changedFiles)
        {
            foreach (var root in roots)
            {
                if (file == root || file.StartsWith(root + "/"))
                {
                    return true;
                }
            }
        }
        return false;
    }

    public virtual IPathMatcher RelativeTo(string path)
    {
        var includeList = new List<IPathMatcher>();
        foreach (var atom in Include)
        {
            includeList.Add(atom.Matcher(path));
        }
        foreach (var g in _globInclude)
        {
            includeList.Add(g.RelativeTo(path));
        }
        IPathMatcher excludeMatcher =
            _exclude == null ? FileUtil.AnyPathMatcher(ImmutableArray<IPathMatcher>.Empty) : _exclude.RelativeTo(path);
        return new GlobMatcher(FileUtil.AnyPathMatcher(includeList), excludeMatcher, ToString());
    }

    private sealed class GlobMatcher : IPathMatcher, IEquatable<GlobMatcher>
    {
        private readonly IPathMatcher _includeMatcher;
        private readonly IPathMatcher _excludeMatcher;
        private readonly string _toString;

        public GlobMatcher(IPathMatcher includeMatcher, IPathMatcher excludeMatcher, string toString)
        {
            _includeMatcher = includeMatcher;
            _excludeMatcher = excludeMatcher;
            _toString = toString;
        }

        public bool Matches(string path) =>
            _includeMatcher.Matches(path) && !_excludeMatcher.Matches(path);

        public override string ToString() => _toString;

        public bool Equals(GlobMatcher? other) =>
            other is not null
            && Equals(_includeMatcher, other._includeMatcher)
            && Equals(_excludeMatcher, other._excludeMatcher);

        public override bool Equals(object? obj) => Equals(obj as GlobMatcher);

        public override int GetHashCode() => HashCode.Combine(_includeMatcher, _excludeMatcher);
    }

    /// <summary>Creates a <see cref="Glob"/> from include and exclude patterns.</summary>
    public static Glob CreateGlob(IEnumerable<string> include, IEnumerable<string> exclude)
    {
        var excludeList = exclude as IReadOnlyCollection<string> ?? exclude.ToList();
        return new Glob(
            GlobAtom.OfIterable(include, GlobAtom.AtomType.JavaGlob).ToImmutableArray(),
            ImmutableArray<Glob>.Empty,
            excludeList.Count == 0 ? null : CreateGlob(excludeList));
    }

    /// <summary>Creates a <see cref="Glob"/> that matches the given single file paths.</summary>
    public static Glob CreateSingleFilesGlob(IEnumerable<string> singleFilePaths) =>
        SequenceGlob.OfIterable(singleFilePaths);

    /// <summary>Handles a Glob/Sequence parameter passed to a Glob-consuming function.</summary>
    public static Glob? WrapGlob(object? globOrList, Glob? defaultValue)
    {
        if (globOrList is null || ReferenceEquals(globOrList, StarlarkRuntime.None))
        {
            return defaultValue;
        }
        if (globOrList is Glob glob)
        {
            return glob;
        }
        if (globOrList is IEnumerable<string> list)
        {
            return SequenceGlob.OfIterable(list);
        }
        throw new EvalException("Glob can only be created from a Glob or a list of strings");
    }

    /// <summary>Creates a <see cref="Glob"/> from include patterns only.</summary>
    public static Glob CreateGlob(IEnumerable<string> include) =>
        CreateGlob(include, ImmutableArray<string>.Empty);

    /// <summary>
    /// Calculates a set of paths which recursively contain all files that could possibly match a file
    /// in this glob. See the Java documentation for the exact semantics.
    /// </summary>
    public ImmutableHashSet<string> Roots() => Roots(false);

    /// <summary>Similar to <see cref="Roots()"/> but returns the longest shared paths for git cones.</summary>
    public ImmutableHashSet<string> Tips() => ComputeTipsFromIncludes(GetIncludes());

    /// <summary>
    /// If <paramref name="allowFiles"/> is set, then paths containing no meta characters are retained
    /// exactly as they are.
    /// </summary>
    public ImmutableHashSet<string> Roots(bool allowFiles) =>
        ComputeRootsFromIncludes(GetIncludes(), allowFiles);

    /// <summary>If roots is empty or contains a single element that is not a subdirectory.</summary>
    public static bool IsEmptyRoot(IEnumerable<string> roots)
    {
        using var it = roots.GetEnumerator();
        return !it.MoveNext() || it.Current == "";
    }

    protected virtual IEnumerable<GlobAtom> GetIncludes() =>
        Include.Concat(_globInclude.SelectMany(g => g.GetIncludes()));

    private static ImmutableHashSet<string> ComputeRootsFromIncludes(
        IEnumerable<GlobAtom> includes, bool allowFiles)
    {
        var roots = new List<string>();
        foreach (var atom in includes)
        {
            roots.Add(atom.Root(allowFiles));
        }

        roots.Sort(CompareRoots);
        if (roots.Contains(""))
        {
            return ImmutableHashSet.Create("");
        }
        int r = 0;
        while (r < roots.Count - 1)
        {
            if (roots[r + 1].StartsWith(roots[r] + "/"))
            {
                roots.RemoveAt(r + 1);
            }
            else
            {
                r++;
            }
        }

        return roots
            .Select(s => CollapseSlashes(s))
            .ToImmutableHashSet();
    }

    private static string CollapseSlashes(string s)
    {
        // Replace "//+" with "/" then remove a trailing "/".
        var sb = new StringBuilder(s.Length);
        bool prevSlash = false;
        foreach (var c in s)
        {
            if (c == '/')
            {
                if (!prevSlash)
                {
                    sb.Append('/');
                }
                prevSlash = true;
            }
            else
            {
                sb.Append(c);
                prevSlash = false;
            }
        }
        string result = sb.ToString();
        if (result.EndsWith('/'))
        {
            result = result.Substring(0, result.Length - 1);
        }
        return result;
    }

    private static ImmutableHashSet<string> ComputeTipsFromIncludes(IEnumerable<GlobAtom> includes)
    {
        var wildcards = new List<string>();
        var singleFiles = new List<string>();
        foreach (var atom in includes)
        {
            Root root = atom.AnnotatedRoot(false);
            if (!root.IsRecursive())
            {
                singleFiles.Add(root.GetRoot());
            }
            else
            {
                wildcards.Add(root.GetRoot());
            }
        }
        wildcards.Sort(CompareRoots);
        if (wildcards.Contains(""))
        {
            return ImmutableHashSet.Create("");
        }
        int r = 0;
        while (r < wildcards.Count - 1)
        {
            if (wildcards[r + 1].StartsWith(wildcards[r] + "/"))
            {
                wildcards.RemoveAt(r + 1);
            }
            else
            {
                r++;
            }
        }

        singleFiles.Sort((a, b) => CompareRoots(b, a));
        int t = 0;
        while (t < singleFiles.Count - 1)
        {
            if (singleFiles[t].StartsWith(singleFiles[t + 1] + "/"))
            {
                singleFiles.RemoveAt(t + 1);
            }
            else
            {
                t++;
            }
        }
        var tips = new List<string>(wildcards);

        foreach (var single in singleFiles)
        {
            if (!wildcards.Any(w => (single + "/").StartsWith(w + "/")))
            {
                tips.Add(single);
            }
        }
        tips.AddRange(wildcards);
        return tips.ToImmutableHashSet();
    }

    /// <summary>A lexicographical string comparator that sorts the '/' char before any other char.</summary>
    private static int CompareRoots(string s1, string s2)
    {
        int len1 = s1.Length;
        int len2 = s2.Length;
        int lim = Math.Min(len1, len2);
        for (int k = 0; k < lim; k++)
        {
            int c1 = s1[k];
            c1 = c1 == '/' ? -1 : c1;
            int c2 = s2[k];
            c2 = c2 == '/' ? -1 : c2;
            if (c1 != c2)
            {
                return c1 - c2;
            }
        }
        return len1 - len2;
    }

    public override string ToString() => ToStringWithParentheses(true);

    internal virtual string ToStringWithParentheses(bool isRootGlob)
    {
        var builder = new StringBuilder();
        int numberOfTerms = 0;
        bool inlineExclude =
            _globInclude.IsEmpty
            && _exclude != null
            && _exclude.GetType() == GetType()
            && _exclude._globInclude.IsEmpty
            && _exclude._exclude == null;
        if (!Include.IsEmpty || _globInclude.IsEmpty)
        {
            builder
                .Append("glob(include = ")
                .Append(ToStringList(Include))
                .Append(inlineExclude ? ", exclude = " + ToStringList(_exclude!.Include) : "")
                .Append(')');
            numberOfTerms += 1;
        }
        foreach (var g in _globInclude)
        {
            if (numberOfTerms > 0)
            {
                builder.Append(" + ");
            }
            builder.Append(g.ToStringWithParentheses(false));
            numberOfTerms += 1;
        }
        if (_exclude != null && !inlineExclude)
        {
            builder.Append(" - ").Append(_exclude.ToStringWithParentheses(false));
            numberOfTerms += 1;
        }

        if (!isRootGlob && numberOfTerms > 1)
        {
            return "(" + builder + ")";
        }
        return builder.ToString();
    }

    internal string ToStringList(IEnumerable<GlobAtom> iterable)
    {
        var sb = new StringBuilder("[");
        bool first = true;
        foreach (var atom in iterable)
        {
            if (first)
            {
                first = false;
            }
            else
            {
                sb.Append(", ");
            }
            sb.Append('"').Append(Sanitize(atom.ToString())).Append('"');
        }
        return sb.Append(']').ToString();
    }

    private static string Sanitize(string s) =>
        s.Replace("\\", "\\\\")
            .Replace("\"", "\\\"")
            .Replace("\n", "\\n")
            .Replace("\r", "\\r")
            .Replace("\t", "\\t")
            .Replace("\f", "\\f")
            .Replace("\b", "\\b")
            .Replace("\0", "\\000");

    public bool Equals(Glob? other) =>
        other is not null
        && Include.SequenceEqual(other.Include)
        && _globInclude.SequenceEqual(other._globInclude)
        && Equals(_exclude, other._exclude);

    public override bool Equals(object? obj) => Equals(obj as Glob);

    public override int GetHashCode()
    {
        var hc = new HashCode();
        foreach (var a in Include)
        {
            hc.Add(a);
        }
        foreach (var g in _globInclude)
        {
            hc.Add(g);
        }
        hc.Add(_exclude);
        return hc.ToHashCode();
    }

    public int HeightOfGlobTree()
    {
        int includeHeight = _globInclude.Length == 0
            ? -1
            : _globInclude.Max(g => g.HeightOfGlobTree());
        int excludeHeight = _exclude == null ? -1 : _exclude.HeightOfGlobTree();
        return 1 + Math.Max(includeHeight, excludeHeight);
    }
}
