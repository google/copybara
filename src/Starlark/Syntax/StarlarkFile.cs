// Copyright 2014 The Bazel Authors. All rights reserved.
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

using System.Collections.Immutable;

namespace Starlark.Syntax;

/// <summary>
/// Syntax tree for a Starlark file, such as a Bazel BUILD or .bzl file.
///
/// <para>Call <c>Parse</c> to parse a file. Parser errors are recorded in the syntax tree (see
/// <c>Errors</c>), which may be incomplete.</para>
/// </summary>
public sealed class StarlarkFile : Node
{
    private readonly ImmutableArray<Statement> statements;
    private readonly FileOptions options;
    private readonly ImmutableArray<Comment> comments;
    internal readonly List<SyntaxError> errors; // appended to by Resolver
    // Map from global variable name to doc comments. Added to by Resolver.
    internal readonly Dictionary<string, DocComments> docCommentsMap = new();

    // set by resolver
    private Resolver.Function? resolved;

    public override int GetStartOffset() => 0;

    public override int GetEndOffset() => Locs.Size;

    private StarlarkFile(
        FileLocations locs,
        ImmutableArray<Statement> statements,
        FileOptions options,
        ImmutableArray<Comment> comments,
        List<SyntaxError> errors)
        : base(locs)
    {
        this.statements = statements;
        this.options = options;
        this.comments = comments;
        this.errors = errors;
    }

    /// <summary>
    /// Returns an unmodifiable view of the list of scanner, parser, and (perhaps) resolver errors
    /// accumulated in this Starlark file.
    /// </summary>
    public IReadOnlyList<SyntaxError> Errors() => errors.AsReadOnly();

    /// <summary>Returns Errors().Count == 0.</summary>
    public bool Ok() => errors.Count == 0;

    /// <summary>Returns an (immutable, ordered) list of statements in this BUILD file.</summary>
    public IReadOnlyList<Statement> GetStatements() => statements;

    /// <summary>Returns an (immutable, ordered) list of comments in this BUILD file.</summary>
    public IReadOnlyList<Comment> GetComments() => comments;

    public override string ToString() => "<StarlarkFile with " + statements.Length + " statements>";

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);

    internal void SetResolvedFunction(Resolver.Function resolved) => this.resolved = resolved;

    /// <summary>
    /// Returns information about the implicit function containing the top-level statements of the
    /// file. Set by the resolver.
    /// </summary>
    public Resolver.Function? GetResolvedFunction() => resolved;

    /// <summary>
    /// Parse a Starlark file. A syntax tree is always returned, even in case of error. Errors are
    /// recorded in the tree.
    /// </summary>
    public static StarlarkFile Parse(ParserInput input, FileOptions options)
    {
        Parser.ParseResult result = Parser.ParseFile(input, options);
        return new StarlarkFile(
            result.Locs, result.Statements, options, result.Comments, result.Errors);
    }

    /// <summary>Parse a Starlark file with default options.</summary>
    public static StarlarkFile Parse(ParserInput input) => Parse(input, FileOptions.DEFAULT);

    /// <summary>Returns the options specified when parsing this file.</summary>
    public FileOptions GetOptions() => options;

    /// <summary>Returns the name of this file, as specified to the parser.</summary>
    public string GetName() => Locs.File;

    /// <summary>A ParseProfiler records the start and end times of parse operations.</summary>
    public interface IParseProfiler
    {
        long Start();

        void End(long profileStartNanos, string filename);
    }

    /// <summary>Installs a global hook that will be notified of parse operations.</summary>
    public static void SetParseProfiler(IParseProfiler? p) => Parser.Profiler = p;
}
