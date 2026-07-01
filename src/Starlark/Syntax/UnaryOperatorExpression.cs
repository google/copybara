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

namespace Starlark.Syntax;

/// <summary>A UnaryOperatorExpression represents a unary operator expression, 'op x'.</summary>
public sealed class UnaryOperatorExpression : Expression
{
    private readonly TokenKind op; // NOT, TILDE, MINUS or PLUS
    private readonly int opOffset;
    private readonly Expression x;

    internal UnaryOperatorExpression(FileLocations locs, TokenKind op, int opOffset, Expression x)
        : base(locs, ExpressionKind.UNARY_OPERATOR)
    {
        this.op = op;
        this.opOffset = opOffset;
        this.x = x;
    }

    /// <summary>Returns the operator.</summary>
    public TokenKind GetOperator() => op;

    public override int GetStartOffset() => opOffset;

    public override int GetEndOffset() => x.GetEndOffset();

    /// <summary>Returns the operand.</summary>
    public Expression GetX() => x;

    public override string ToString() =>
        (op == TokenKind.NOT ? "not " : op.ToDisplayString()) + x;

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
