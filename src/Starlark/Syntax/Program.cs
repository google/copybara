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

using System.Collections.Immutable;
using System.Linq;

namespace Starlark.Syntax;

/// <summary>
/// An opaque, executable representation of a valid Starlark program.
/// </summary>
public sealed class Program
{
    private readonly Resolver.Function body;
    private readonly ImmutableArray<string> loads;
    private readonly ImmutableArray<Location> loadLocations;
    private readonly ImmutableDictionary<string, DocComments> docCommentsMap;
    private readonly ImmutableArray<Comment> unusedDocCommentLines;

    private Program(
        Resolver.Function body,
        ImmutableArray<string> loads,
        ImmutableArray<Location> loadLocations,
        ImmutableDictionary<string, DocComments> docCommentsMap,
        ImmutableArray<Comment> unusedDocCommentLines)
    {
        if (loads.Length != loadLocations.Length)
        {
            throw new ArgumentException("each load must have a corresponding location");
        }
        this.body = body;
        this.loads = loads;
        this.loadLocations = loadLocations;
        this.docCommentsMap = docCommentsMap;
        this.unusedDocCommentLines = unusedDocCommentLines;
    }

    public Resolver.Function GetResolvedFunction() => body;

    /// <summary>Returns the file name of this compiled program.</summary>
    public string GetFilename() => body.GetLocation().File;

    /// <summary>Returns the list of load strings of this compiled program, in source order.</summary>
    public IReadOnlyList<string> GetLoads() => loads;

    /// <summary>Returns the location of the ith load (see <see cref="GetLoads"/>).</summary>
    public Location GetLoadLocation(int i) => loadLocations[i];

    /// <summary>
    /// Returns a map from global variable names to Sphinx autodoc-style doc comments associated with
    /// the variable's declarations.
    /// </summary>
    public IReadOnlyDictionary<string, DocComments> GetDocCommentsMap() => docCommentsMap;

    /// <summary>Returns the list of doc comments not associated with any global variable.</summary>
    public IReadOnlyList<Comment> GetUnusedDocCommentLines() => unusedDocCommentLines;

    /// <summary>
    /// Resolves a file syntax tree in the specified environment and compiles it to a Program. This
    /// operation mutates the syntax tree.
    /// </summary>
    /// <exception cref="SyntaxError.Exception">in case of resolution error.</exception>
    public static Program CompileFile(StarlarkFile file, Resolver.IModule env)
    {
        Resolver.ResolveFile(file, env);
        if (!file.Ok())
        {
            throw new SyntaxError.Exception(file.Errors());
        }

        if (file.GetOptions().ResolveTypeSyntax)
        {
            TypeTagger.TagFile(file, env);
            if (!file.Ok())
            {
                throw new SyntaxError.Exception(file.Errors());
            }
        }

        if (file.GetOptions().StaticTypeChecking)
        {
            TypeChecker.CheckFile(file, env);
            if (!file.Ok())
            {
                throw new SyntaxError.Exception(file.Errors());
            }
        }

        // Extract load statements.
        var loads = ImmutableArray.CreateBuilder<string>();
        var loadLocations = ImmutableArray.CreateBuilder<Location>();
        foreach (Statement stmt in file.GetStatements())
        {
            if (stmt is LoadStatement load)
            {
                string module = load.GetImport().GetValue();
                loads.Add(module);
                loadLocations.Add(load.GetImport().GetLocation());
            }
        }

        // Find unused doc comments.
        ImmutableDictionary<string, DocComments> docCommentsMap = file.docCommentsMap.ToImmutableDictionary();
        var usedDocCommentLines = new HashSet<Comment>();
        foreach (DocComments docComments in docCommentsMap.Values)
        {
            foreach (Comment c in docComments.GetLines())
            {
                usedDocCommentLines.Add(c);
            }
        }
        ImmutableArray<Comment> unusedDocCommentLines =
            file.GetComments()
                .Where(c => c.HasDocCommentPrefix() && !usedDocCommentLines.Contains(c))
                .ToImmutableArray();

        return new Program(
            file.GetResolvedFunction()!,
            loads.ToImmutable(),
            loadLocations.ToImmutable(),
            docCommentsMap,
            unusedDocCommentLines);
    }

    /// <summary>
    /// Resolves an expression syntax tree in the specified environment and compiles it to a Program.
    /// </summary>
    /// <exception cref="SyntaxError.Exception">in case of resolution error.</exception>
    public static Program CompileExpr(Expression expr, Resolver.IModule module, FileOptions options)
    {
        Resolver.Function body = Resolver.ResolveExpr(expr, module, options);

        if (options.ResolveTypeSyntax)
        {
            TypeTagger.TagExpr(expr, body, module);
        }

        if (options.StaticTypeChecking)
        {
            StarlarkType exprType = TypeChecker.InferTypeOf(expr, module);
            TypeTagger.TagExprFunction(body, exprType);
        }

        return new Program(
            body,
            ImmutableArray<string>.Empty,
            ImmutableArray<Location>.Empty,
            ImmutableDictionary<string, DocComments>.Empty,
            ImmutableArray<Comment>.Empty);
    }
}
