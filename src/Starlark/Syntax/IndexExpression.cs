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
/// An index expression (<c>obj[field]</c>). Not to be confused with a slice expression
/// (<c>obj[from:to]</c>).
/// </summary>
public sealed class IndexExpression : Expression
{
    private readonly Expression obj;
    private readonly int lbracketOffset;
    private readonly Expression key;
    private readonly int rbracketOffset;

    internal IndexExpression(
        FileLocations locs, Expression obj, int lbracketOffset, Expression key, int rbracketOffset)
        : base(locs, ExpressionKind.INDEX)
    {
        this.obj = obj;
        this.lbracketOffset = lbracketOffset;
        this.key = key;
        this.rbracketOffset = rbracketOffset;
    }

    public Expression GetObject() => obj;

    public Expression GetKey() => key;

    public override int GetStartOffset() => obj.GetStartOffset();

    public override int GetEndOffset() => rbracketOffset + 1;

    public Location GetLbracketLocation() => Locs.GetLocation(lbracketOffset);

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
