// Copyright 2024 The Bazel Authors. All rights reserved.
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

/// <summary>Represents a type alias statement in the Starlark AST.</summary>
public sealed class TypeAliasStatement : Statement
{
    private readonly int startOffset;
    private readonly Identifier identifier;
    private readonly ImmutableArray<Identifier> parameters;
    private readonly Expression definition;

    internal TypeAliasStatement(
        FileLocations locs,
        int startOffset,
        Identifier identifier,
        ImmutableArray<Identifier> parameters,
        Expression definition)
        : base(locs, StatementKind.TYPE_ALIAS)
    {
        this.startOffset = startOffset;
        this.identifier = identifier;
        this.parameters = parameters;
        this.definition = definition;
    }

    public override string ToString()
    {
        var buf = new StringBuilder();
        buf.Append("type ");
        buf.Append(identifier.GetName());
        if (!parameters.IsEmpty)
        {
            buf.Append('[');
            ListExpression.AppendNodes(buf, parameters);
            buf.Append(']');
        }
        buf.Append(" = ...\n");
        return buf.ToString();
    }

    public Identifier GetIdentifier() => identifier;

    public IReadOnlyList<Identifier> GetParameters() => parameters;

    public Expression GetDefinition() => definition;

    /// <summary>Note that this is the start offset of the statement's <c>type</c> keyword.</summary>
    public override int GetStartOffset() => startOffset;

    public override int GetEndOffset() => definition.GetEndOffset();

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
