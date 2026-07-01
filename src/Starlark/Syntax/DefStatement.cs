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
using System.Text;

namespace Starlark.Syntax;

/// <summary>Syntax node for a 'def' statement, which defines a function.</summary>
public sealed class DefStatement : Statement
{
    private readonly int defOffset;
    private readonly Identifier identifier;
    private readonly ImmutableArray<Identifier> typeParameters; // No type params => empty list
    private readonly ImmutableArray<Statement> body; // non-empty if well formed
    private readonly ImmutableArray<Parameter> parameters;
    private readonly Expression? returnType; // No return type => null

    // set by resolver
    private Resolver.Function? resolved;

    internal DefStatement(
        FileLocations locs,
        int defOffset,
        Identifier identifier,
        ImmutableArray<Identifier> typeParameters,
        ImmutableArray<Parameter> parameters,
        Expression? returnType,
        ImmutableArray<Statement> body)
        : base(locs, StatementKind.DEF)
    {
        this.defOffset = defOffset;
        this.identifier = identifier;
        this.typeParameters = typeParameters;
        this.parameters = parameters;
        this.returnType = returnType;
        this.body = body;
    }

    public override string ToString()
    {
        var buf = new StringBuilder();
        new NodePrinter(buf).PrintDefSignature(this);
        buf.Append(" ...\n");
        return buf.ToString();
    }

    public Identifier GetIdentifier() => identifier;

    public IReadOnlyList<Statement> GetBody() => body;

    public IReadOnlyList<Identifier> GetTypeParameters() => typeParameters;

    public IReadOnlyList<Parameter> GetParameters() => parameters;

    public Expression? GetReturnType() => returnType;

    internal void SetResolvedFunction(Resolver.Function resolved) => this.resolved = resolved;

    /// <summary>Returns information about the resolved function. Set by the resolver.</summary>
    public Resolver.Function? GetResolvedFunction() => resolved;

    public override int GetStartOffset() => defOffset;

    public override int GetEndOffset() =>
        body.IsEmpty ? identifier.GetEndOffset() : body[body.Length - 1].GetEndOffset();

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
