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
/// Syntax node for list and dict comprehensions.
///
/// <para>A comprehension contains one or more clauses, e.g. [a+d for a in b if c for d in e]
/// contains three clauses: "for a in b", "if c", "for d in e".</para>
/// </summary>
public sealed class Comprehension : Expression
{
    /// <summary>For or If.</summary>
    public abstract class Clause : Node
    {
        private protected Clause(FileLocations locs)
            : base(locs)
        {
        }
    }

    /// <summary>A for clause in a comprehension, e.g. "for a in b" in the example above.</summary>
    public sealed class For : Clause
    {
        private readonly int forOffset;
        private readonly Expression vars;
        private readonly Expression iterable;

        internal For(FileLocations locs, int forOffset, Expression vars, Expression iterable)
            : base(locs)
        {
            this.forOffset = forOffset;
            this.vars = vars;
            this.iterable = iterable;
        }

        public Expression GetVars() => vars;

        public Expression GetIterable() => iterable;

        public override int GetStartOffset() => forOffset;

        public override int GetEndOffset() => iterable.GetEndOffset();

        public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
    }

    /// <summary>An if clause in a comprehension, e.g. "if c" in the example above.</summary>
    public sealed class If : Clause
    {
        private readonly int ifOffset;
        private readonly Expression condition;

        internal If(FileLocations locs, int ifOffset, Expression condition)
            : base(locs)
        {
            this.ifOffset = ifOffset;
            this.condition = condition;
        }

        public Expression GetCondition() => condition;

        public override int GetStartOffset() => ifOffset;

        public override int GetEndOffset() => condition.GetEndOffset();

        public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
    }

    private readonly bool isDict; // {k: v for vars in iterable}
    private readonly int lbracketOffset;
    private readonly Node body; // Expression or DictExpression.Entry
    private readonly ImmutableArray<Clause> clauses;
    private readonly int rbracketOffset;

    internal Comprehension(
        FileLocations locs,
        bool isDict,
        int lbracketOffset,
        Node body,
        ImmutableArray<Clause> clauses,
        int rbracketOffset)
        : base(locs, ExpressionKind.COMPREHENSION)
    {
        this.isDict = isDict;
        this.lbracketOffset = lbracketOffset;
        this.body = body;
        this.clauses = clauses;
        this.rbracketOffset = rbracketOffset;
    }

    public bool IsDict() => isDict;

    /// <summary>
    /// Returns the loop body: an expression for a list comprehension, or a DictExpression.Entry for a
    /// dict comprehension.
    /// </summary>
    public Node GetBody() => body;

    public IReadOnlyList<Clause> GetClauses() => clauses;

    public override int GetStartOffset() => lbracketOffset;

    public override int GetEndOffset() => rbracketOffset + 1;

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
