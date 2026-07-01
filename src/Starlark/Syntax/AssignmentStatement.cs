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

/// <summary>
/// Syntax node for an assignment statement (<c>lhs = rhs</c>) or augmented assignment statement
/// (<c>lhs op= rhs</c>).
/// </summary>
public sealed class AssignmentStatement : Statement
{
    private readonly Expression lhs; // = IDENTIFIER | DOT | INDEX | LIST_EXPR

    // non-null only when lhs is an identifier and we're not augmented
    private readonly Expression? type;

    private readonly TokenKind? op;
    private readonly int opOffset;

    private readonly Expression rhs;

    private readonly DocComments? docComments;

    internal AssignmentStatement(
        FileLocations locs,
        Expression lhs,
        Expression? type,
        TokenKind? op,
        int opOffset,
        Expression rhs,
        DocComments? docComments)
        : base(locs, StatementKind.ASSIGNMENT)
    {
        this.lhs = lhs;
        this.type = type;
        this.op = op;
        this.opOffset = opOffset;
        this.rhs = rhs;
        this.docComments = docComments;
        if (type != null)
        {
            if (lhs.Kind != Expression.ExpressionKind.IDENTIFIER)
            {
                throw new InvalidOperationException("Can't have type annotation on complex LHS");
            }
            if (op != null)
            {
                throw new InvalidOperationException("Can't have augmented assignment with type annotation");
            }
        }
    }

    /// <summary>Returns the LHS of the assignment.</summary>
    public Expression GetLHS() => lhs;

    /// <summary>Returns the type expression (if present) of the variable on the LHS.</summary>
    public new Expression? GetType() => type;

    /// <summary>Returns the operator of an augmented assignment, or null for an ordinary assignment.</summary>
    public TokenKind? GetOperator() => op;

    /// <summary>Returns the location of the assignment operator.</summary>
    public Location GetOperatorLocation() => Locs.GetLocation(opOffset);

    public override int GetStartOffset() => lhs.GetStartOffset();

    public override int GetEndOffset() => rhs.GetEndOffset();

    /// <summary>Reports whether this is an augmented assignment (<c>GetOperator() != null</c>).</summary>
    public bool IsAugmented() => op != null;

    /// <summary>Returns the RHS of the assignment.</summary>
    public Expression GetRHS() => rhs;

    /// <summary>Returns the Sphinx autodoc-style doc comments attached to this statement, if any.</summary>
    public DocComments? GetDocComments() => docComments;

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
