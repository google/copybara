// Copyright 2017 The Bazel Authors. All rights reserved.
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

/// <summary>Syntax node for a slice expression, <c>object[start:stop:step]</c>.</summary>
public sealed class SliceExpression : Expression
{
    private readonly Expression obj;
    private readonly int lbracketOffset;
    private readonly Expression? start;
    private readonly Expression? stop;
    private readonly Expression? step;
    private readonly int rbracketOffset;

    internal SliceExpression(
        FileLocations locs,
        Expression obj,
        int lbracketOffset,
        Expression? start,
        Expression? stop,
        Expression? step,
        int rbracketOffset)
        : base(locs, ExpressionKind.SLICE)
    {
        this.obj = obj;
        this.lbracketOffset = lbracketOffset;
        this.start = start;
        this.stop = stop;
        this.step = step;
        this.rbracketOffset = rbracketOffset;
    }

    public Expression GetObject() => obj;

    public Expression? GetStart() => start;

    public Expression? GetStop() => stop;

    public Expression? GetStep() => step;

    public override int GetStartOffset() => obj.GetStartOffset();

    public override int GetEndOffset() => rbracketOffset + 1;

    public Location GetLbracketLocation() => Locs.GetLocation(lbracketOffset);

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
