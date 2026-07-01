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

using System.Text.RegularExpressions;
using Copybara.Common;

namespace Copybara.Util;

/// <summary>
/// A wrapper around a single String literal passed to the Starlark <c>glob(...)</c> function. Port of
/// <c>com.google.copybara.util.GlobAtom</c>.
/// </summary>
public sealed class GlobAtom : IEquatable<GlobAtom>
{
    /// <summary>The format that the pattern takes.</summary>
    public enum AtomType
    {
        JavaGlob,
        SingleFile,
    }

    private readonly AtomType _type;
    private readonly string _pattern;

    private GlobAtom(string pattern, AtomType type)
    {
        _pattern = Preconditions.CheckNotNull(pattern);
        _type = type;
    }

    public static GlobAtom Of(string pattern, AtomType type)
    {
        Preconditions.CheckArgument(pattern.Length != 0, "unexpected empty string in glob list");
        FileUtil.CheckNormalizedRelative(pattern);
        if (type == AtomType.JavaGlob)
        {
            // Try to create a matcher to check that the glob pattern is correct.
            var unused = GlobPathMatcher.Translate(pattern);
        }
        return new GlobAtom(pattern, type);
    }

    public static IEnumerable<GlobAtom> OfIterable(IEnumerable<string> patterns, AtomType type) =>
        patterns.Select(p => Of(p, type));

    public IPathMatcher Matcher(string root) => MatcherFor(_type, root, _pattern);

    public string Root(bool allowFiles) => RootFor(_type, _pattern, allowFiles).GetRoot();

    public Root AnnotatedRoot(bool allowFiles) => RootFor(_type, _pattern, allowFiles);

    public string Pattern() => _pattern;

    public AtomType GetAtomType() => _type;

    public override string ToString() => _pattern;

    /// <summary>
    /// Resolves the <paramref name="filePath"/> against the <paramref name="root"/> path, returning a
    /// normalized, <c>/</c>-separated string.
    /// </summary>
    public static string GetRelativePath(string root, string filePath)
    {
        string rootStr = PathNormalizer.Normalize(root);
        if (rootStr.Length > 0 && !rootStr.EndsWith('/'))
        {
            rootStr += "/";
        }
        return NormalizePath(rootStr + filePath);
    }

    public bool Equals(GlobAtom? other) =>
        other is not null && _type == other._type && _pattern == other._pattern;

    public override bool Equals(object? obj) => Equals(obj as GlobAtom);

    public override int GetHashCode() => HashCode.Combine(_type, _pattern);

    private static IPathMatcher MatcherFor(AtomType type, string root, string pattern)
    {
        switch (type)
        {
            case AtomType.JavaGlob:
                return ReadablePathMatcher.RelativeGlob(root, pattern);
            case AtomType.SingleFile:
                string relativePath = GetRelativePath(root, pattern);
                return new SingleFilePathMatcher(relativePath, pattern);
            default:
                throw new ArgumentOutOfRangeException(nameof(type));
        }
    }

    private static Root RootFor(AtomType type, string pattern, bool allowFiles)
    {
        switch (type)
        {
            case AtomType.JavaGlob:
                return JavaGlobRoot(pattern, allowFiles);
            case AtomType.SingleFile:
                int lastSlash = pattern.LastIndexOf('/');
                return lastSlash == -1
                    ? new Root(false, "")
                    : new Root(false, pattern.Substring(0, lastSlash));
            default:
                throw new ArgumentOutOfRangeException(nameof(type));
        }
    }

    private static Root JavaGlobRoot(string pattern, bool allowFiles)
    {
        string root;
        bool isSingleFile = true;
        bool isRecursive = pattern.Contains("**");
        var components = new List<string>();
        string[] parts = pattern.Split('/');
        for (int idx = 0; idx < parts.Length; idx++)
        {
            string component = parts[idx];
            components.Add(Unescape(component));
            if (IsMeta(component))
            {
                isSingleFile = false;
                bool hasNext = idx < parts.Length - 1;
                isRecursive = component.Contains("**") || hasNext;
                break;
            }
        }
        if (!(allowFiles && isSingleFile))
        {
            if (components.Count > 0)
            {
                components.RemoveAt(components.Count - 1);
            }
        }
        root = components.Count == 0 ? "" : string.Join('/', components);
        return new Root(isRecursive, root);
    }

    private static readonly Regex UnescapeRegex = new(@"\\(.)");

    private static string Unescape(string pathComponent) =>
        UnescapeRegex.Replace(pathComponent, "$1");

    internal static bool IsMeta(string pathComponent)
    {
        int c = 0;
        while (c < pathComponent.Length)
        {
            switch (pathComponent[c])
            {
                case '*':
                case '{':
                case '[':
                case '?':
                    return true;
                case '\\':
                    c++;
                    break;
                default:
                    break;
            }
            c++;
        }
        return false;
    }

    private static string NormalizePath(string path)
    {
        string p = PathNormalizer.Normalize(path);
        // Collapse "." and ".." components to mirror Java's Path.normalize().
        var stack = new List<string>();
        bool absolute = p.StartsWith('/');
        foreach (var seg in p.Split('/'))
        {
            if (seg.Length == 0 || seg == ".")
            {
                continue;
            }
            if (seg == "..")
            {
                if (stack.Count > 0 && stack[^1] != "..")
                {
                    stack.RemoveAt(stack.Count - 1);
                }
                else if (!absolute)
                {
                    stack.Add("..");
                }
            }
            else
            {
                stack.Add(seg);
            }
        }
        string joined = string.Join('/', stack);
        return absolute ? "/" + joined : joined;
    }

    /// <summary>A matcher matching a single normalized file path exactly.</summary>
    private sealed class SingleFilePathMatcher : IPathMatcher
    {
        private readonly string _relativePath;
        private readonly string _toString;

        public SingleFilePathMatcher(string relativePath, string toString)
        {
            _relativePath = relativePath;
            _toString = toString;
        }

        public bool Matches(string path) => NormalizePath(path) == _relativePath;

        public override string ToString() => _toString;
    }
}

/// <summary>
/// Describes the root of a glob atom: the recursive flag plus the leading non-meta path prefix. Port
/// of <c>com.google.copybara.util.GlobAtom.Root</c>.
/// </summary>
public sealed class Root : IEquatable<Root>
{
    private readonly bool _isRecursive;
    private readonly string _root;

    public Root(bool isRecursive, string root)
    {
        _isRecursive = isRecursive;
        _root = root;
    }

    public bool IsRecursive() => _isRecursive;

    public string GetRoot() => _root;

    public bool Equals(Root? other) =>
        other is not null && _root == other._root && _isRecursive == other._isRecursive;

    public override bool Equals(object? obj) => Equals(obj as Root);

    public override int GetHashCode() => HashCode.Combine(_root, _isRecursive);
}
