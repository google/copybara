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

/// <summary>Syntax node for a for loop statement, <c>for vars in iterable: ...</c>.</summary>
public sealed class ForStatement : Statement
{
    private readonly int forOffset;
    private readonly Expression vars;
    private readonly Expression iterable;
    private readonly ImmutableArray<Statement> body; // non-empty if well formed

    internal ForStatement(
        FileLocations locs,
        int forOffset,
        Expression vars,
        Expression iterable,
        ImmutableArray<Statement> body)
        : base(locs, StatementKind.FOR)
    {
        this.forOffset = forOffset;
        this.vars = vars;
        this.iterable = iterable;
        this.body = body;
    }

    /// <summary>Returns variables assigned by each iteration.</summary>
    public Expression GetVars() => vars;

    /// <summary>Returns the iterable value.</summary>
    public Expression GetCollection() => iterable;

    /// <summary>Returns the statements of the loop body. Non-empty if parsing succeeded.</summary>
    public IReadOnlyList<Statement> GetBody() => body;

    public override int GetStartOffset() => forOffset;

    public override int GetEndOffset() =>
        body.IsEmpty ? iterable.GetEndOffset() : body[body.Length - 1].GetEndOffset();

    public override string ToString() => "for " + vars + " in " + iterable + ": ...\n";

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
