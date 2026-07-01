// Copyright 2020 The Bazel Authors. All rights reserved.
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

namespace Starlark.Syntax;

/// <summary>
/// FileOptions is a set of options that affect the static processing---scanning, parsing, validation
/// (identifier resolution), and compilation---of a single Starlark file.
///
/// <para>The <see cref="DEFAULT"/> options represent the desired behavior for new uses of Starlark.</para>
/// </summary>
public sealed class FileOptions
{
    private FileOptions(
        bool allowLoadPrivateSymbols,
        bool allowToplevelRebinding,
        bool loadBindsGlobally,
        bool requireLoadStatementsFirst,
        bool stringLiteralsAreAsciiOnly,
        bool allowTypeSyntax,
        bool resolveTypeSyntax,
        bool tolerateInvalidTypeExpressions,
        bool staticTypeChecking)
    {
        AllowLoadPrivateSymbols = allowLoadPrivateSymbols;
        AllowToplevelRebinding = allowToplevelRebinding;
        LoadBindsGlobally = loadBindsGlobally;
        RequireLoadStatementsFirst = requireLoadStatementsFirst;
        StringLiteralsAreAsciiOnly = stringLiteralsAreAsciiOnly;
        AllowTypeSyntax = allowTypeSyntax;
        ResolveTypeSyntax = resolveTypeSyntax;
        TolerateInvalidTypeExpressions = tolerateInvalidTypeExpressions;
        StaticTypeChecking = staticTypeChecking;
    }

    /// <summary>The default options for Starlark static processing.</summary>
    public static readonly FileOptions DEFAULT = Builder().Build();

    /// <summary>During resolution, permit load statements to access private names such as <c>_x</c>.</summary>
    public bool AllowLoadPrivateSymbols { get; }

    /// <summary>
    /// During resolution, permit multiple assignments to a given top-level binding.
    /// </summary>
    public bool AllowToplevelRebinding { get; }

    /// <summary>
    /// During resolution, make load statements bind global variables of the module.
    /// </summary>
    public bool LoadBindsGlobally { get; }

    /// <summary>During resolution, require load statements to appear before other kinds of statements.</summary>
    public bool RequireLoadStatementsFirst { get; }

    /// <summary>During lexing, whether to ban non-ASCII characters in string literals.</summary>
    public bool StringLiteralsAreAsciiOnly { get; }

    /// <summary>Whether type annotations and related syntax are allowed in the source code.</summary>
    public bool AllowTypeSyntax { get; }

    /// <summary>Whether type annotations are processed by the resolver.</summary>
    public bool ResolveTypeSyntax { get; }

    /// <summary>
    /// If true, type expressions in annotations and <c>type</c> declarations may be any valid
    /// expression (except for unparenthesized tuples).
    /// </summary>
    public bool TolerateInvalidTypeExpressions { get; }

    /// <summary>Whether to perform static type checking.</summary>
    public bool StaticTypeChecking { get; }

    public static FileOptionsBuilder Builder()
    {
        // These are the DEFAULT values.
        return new FileOptionsBuilder()
            .AllowLoadPrivateSymbols(false)
            .AllowToplevelRebinding(false)
            .LoadBindsGlobally(false)
            .RequireLoadStatementsFirst(true)
            .StringLiteralsAreAsciiOnly(false)
            .AllowTypeSyntax(false)
            .ResolveTypeSyntax(false)
            .TolerateInvalidTypeExpressions(false)
            .StaticTypeChecking(false);
    }

    public FileOptionsBuilder ToBuilder()
    {
        return new FileOptionsBuilder()
            .AllowLoadPrivateSymbols(AllowLoadPrivateSymbols)
            .AllowToplevelRebinding(AllowToplevelRebinding)
            .LoadBindsGlobally(LoadBindsGlobally)
            .RequireLoadStatementsFirst(RequireLoadStatementsFirst)
            .StringLiteralsAreAsciiOnly(StringLiteralsAreAsciiOnly)
            .AllowTypeSyntax(AllowTypeSyntax)
            .ResolveTypeSyntax(ResolveTypeSyntax)
            .TolerateInvalidTypeExpressions(TolerateInvalidTypeExpressions)
            .StaticTypeChecking(StaticTypeChecking);
    }

    /// <summary>Builder for <see cref="FileOptions"/>.</summary>
    public sealed class FileOptionsBuilder
    {
        private bool allowLoadPrivateSymbols;
        private bool allowToplevelRebinding;
        private bool loadBindsGlobally;
        private bool requireLoadStatementsFirst;
        private bool stringLiteralsAreAsciiOnly;
        private bool allowTypeSyntax;
        private bool resolveTypeSyntax;
        private bool tolerateInvalidTypeExpressions;
        private bool staticTypeChecking;

        public FileOptionsBuilder AllowLoadPrivateSymbols(bool value) { allowLoadPrivateSymbols = value; return this; }
        public FileOptionsBuilder AllowToplevelRebinding(bool value) { allowToplevelRebinding = value; return this; }
        public FileOptionsBuilder LoadBindsGlobally(bool value) { loadBindsGlobally = value; return this; }
        public FileOptionsBuilder RequireLoadStatementsFirst(bool value) { requireLoadStatementsFirst = value; return this; }
        public FileOptionsBuilder StringLiteralsAreAsciiOnly(bool value) { stringLiteralsAreAsciiOnly = value; return this; }
        public FileOptionsBuilder AllowTypeSyntax(bool value) { allowTypeSyntax = value; return this; }
        public FileOptionsBuilder ResolveTypeSyntax(bool value) { resolveTypeSyntax = value; return this; }
        public FileOptionsBuilder TolerateInvalidTypeExpressions(bool value) { tolerateInvalidTypeExpressions = value; return this; }
        public FileOptionsBuilder StaticTypeChecking(bool value) { staticTypeChecking = value; return this; }

        public FileOptions Build()
        {
            var options = new FileOptions(
                allowLoadPrivateSymbols,
                allowToplevelRebinding,
                loadBindsGlobally,
                requireLoadStatementsFirst,
                stringLiteralsAreAsciiOnly,
                allowTypeSyntax,
                resolveTypeSyntax,
                tolerateInvalidTypeExpressions,
                staticTypeChecking);
            if (options.StaticTypeChecking)
            {
                if (!options.ResolveTypeSyntax)
                {
                    throw new ArgumentException("staticTypeChecking requires that resolveTypeSyntax is set");
                }
                if (options.TolerateInvalidTypeExpressions)
                {
                    throw new ArgumentException("staticTypeChecking requires that tolerateInvalidTypeExpressions is not set");
                }
            }
            return options;
        }
    }
}
