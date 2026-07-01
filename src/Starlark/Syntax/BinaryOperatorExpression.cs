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

/// <summary>A BinaryExpression represents a binary operator expression 'x op y'.</summary>
public sealed class BinaryOperatorExpression : Expression
{
    private readonly Expression x;
    private readonly TokenKind op; // one of 'Operators'
    private readonly int opOffset;
    private readonly Expression y;

    /// <summary>The set of valid binary operators.</summary>
    public static readonly IReadOnlySet<TokenKind> Operators = new HashSet<TokenKind>
    {
        TokenKind.AND,
        TokenKind.EQUALS_EQUALS,
        TokenKind.GREATER,
        TokenKind.GREATER_EQUALS,
        TokenKind.IN,
        TokenKind.LESS,
        TokenKind.LESS_EQUALS,
        TokenKind.MINUS,
        TokenKind.NOT_EQUALS,
        TokenKind.NOT_IN,
        TokenKind.OR,
        TokenKind.PERCENT,
        TokenKind.SLASH,
        TokenKind.SLASH_SLASH,
        TokenKind.PLUS,
        TokenKind.PIPE,
        TokenKind.STAR,
    };

    internal BinaryOperatorExpression(
        FileLocations locs, Expression x, TokenKind op, int opOffset, Expression y)
        : base(locs, ExpressionKind.BINARY_OPERATOR)
    {
        this.x = x;
        this.op = op;
        this.opOffset = opOffset;
        this.y = y;
    }

    /// <summary>Returns the left operand.</summary>
    public Expression GetX() => x;

    /// <summary>Returns the operator.</summary>
    public TokenKind GetOperator() => op;

    public Location GetOperatorLocation() => Locs.GetLocation(opOffset);

    /// <summary>Returns the right operand.</summary>
    public Expression GetY() => y;

    public override int GetStartOffset() => x.GetStartOffset();

    public override int GetEndOffset() => y.GetEndOffset();

    public override string ToString() => x + " " + op.ToDisplayString() + " " + y;

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
