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

/// <summary>Syntax node for an if or elif statement.</summary>
public sealed class IfStatement : Statement
{
    private readonly TokenKind token; // IF or ELIF
    private readonly int ifOffset;
    private readonly Expression condition;
    // These blocks may be non-null but empty after a misparse:
    private readonly ImmutableArray<Statement> thenBlock; // non-empty
    private ImmutableArray<Statement>? elseBlock; // non-empty if non-null; set after construction

    internal IfStatement(
        FileLocations locs,
        TokenKind token,
        int ifOffset,
        Expression condition,
        IReadOnlyList<Statement> thenBlock)
        : base(locs, StatementKind.IF)
    {
        this.token = token;
        this.ifOffset = ifOffset;
        this.condition = condition;
        this.thenBlock = thenBlock.ToImmutableArray();
    }

    /// <summary>Reports whether this is an 'elif' statement.</summary>
    public bool IsElif() => token == TokenKind.ELIF;

    public Expression GetCondition() => condition;

    public IReadOnlyList<Statement> GetThenBlock() => thenBlock;

    public IReadOnlyList<Statement>? GetElseBlock() =>
        elseBlock.HasValue ? elseBlock.Value : null;

    internal void SetElseBlock(ImmutableArray<Statement> elseBlock) => this.elseBlock = elseBlock;

    public override int GetStartOffset() => ifOffset;

    public override int GetEndOffset()
    {
        ImmutableArray<Statement> body = elseBlock ?? thenBlock;
        return body.IsEmpty ? condition.GetEndOffset() : body[body.Length - 1].GetEndOffset();
    }

    public override string ToString() => string.Format("if {0}: ...\n", condition);

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
