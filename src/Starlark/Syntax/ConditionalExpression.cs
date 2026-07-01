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

/// <summary>Syntax node for an expression of the form <c>t if cond else f</c>.</summary>
public sealed class ConditionalExpression : Expression
{
    private readonly Expression t;
    private readonly Expression cond;
    private readonly Expression f;

    public Expression GetThenCase() => t;

    public Expression GetCondition() => cond;

    public Expression GetElseCase() => f;

    /// <summary>Constructor for a conditional expression.</summary>
    internal ConditionalExpression(FileLocations locs, Expression t, Expression cond, Expression f)
        : base(locs, ExpressionKind.CONDITIONAL)
    {
        this.t = t;
        this.cond = cond;
        this.f = f;
    }

    public override int GetStartOffset() => t.GetStartOffset();

    public override int GetEndOffset() => f.GetEndOffset();

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
