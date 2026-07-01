/*
 * Copyright (C) 2024 Google LLC.
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
using Starlark.Eval;

namespace Copybara.Util;

/// <summary>
/// A "glob" that matches lists of fully qualified file names. Port of
/// <c>com.google.copybara.util.SequenceGlob</c>.
/// </summary>
public class SequenceGlob : Glob
{
    private SequenceGlob(IEnumerable<GlobAtom> include)
        : base(include, ImmutableArray<Glob>.Empty, null)
    {
    }

    internal override string ToStringWithParentheses(bool isRootGlob) => ToStringList(Include);

    public override IPathMatcher RelativeTo(string root)
    {
        var matchPaths = new HashSet<string>();
        foreach (var atom in Include)
        {
            matchPaths.Add(GlobAtom.GetRelativePath(root, atom.Pattern()));
        }
        return new ReadablePathMatcher(new SetPathMatcher(matchPaths), ToString());
    }

    private sealed class SetPathMatcher : IPathMatcher
    {
        private readonly HashSet<string> _matchPaths;

        public SetPathMatcher(HashSet<string> matchPaths) => _matchPaths = matchPaths;

        public bool Matches(string path) => _matchPaths.Contains(NormalizePath(path));

        private static string NormalizePath(string path) => GlobAtom.GetRelativePath("", path);
    }

    public static SequenceGlob OfStarlarkList(IEnumerable<object> patterns)
    {
        var atoms = ImmutableArray.CreateBuilder<GlobAtom>();
        foreach (var pattern in patterns)
        {
            string s = pattern.ToString() ?? "";
            atoms.Add(GlobAtom.Of(s, GlobAtom.AtomType.SingleFile));
            if (pattern is not string)
            {
                throw new EvalException("Only strings are supported in file lists.");
            }
            if (GlobAtom.IsMeta(s))
            {
                throw new EvalException("Wildcards are not supported in file lists.");
            }
        }
        return new SequenceGlob(atoms.ToImmutable());
    }

    /// <summary>Creates a <see cref="SequenceGlob"/> from an iterable of paths.</summary>
    public static SequenceGlob OfIterable(IEnumerable<string> paths)
    {
        var atoms = ImmutableArray.CreateBuilder<GlobAtom>();
        foreach (var path in paths)
        {
            atoms.Add(GlobAtom.Of(path, GlobAtom.AtomType.SingleFile));
        }
        return new SequenceGlob(atoms.ToImmutable());
    }
}
