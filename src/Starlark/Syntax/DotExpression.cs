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

/// <summary>Syntax node for a dot expression. e.g. obj.field, but not obj.method().</summary>
public sealed class DotExpression : Expression
{
    private readonly Expression obj;
    private readonly int dotOffset;
    // This Identifier's `binding` is left null by the resolver.
    private readonly Identifier field;

    internal DotExpression(FileLocations locs, Expression obj, int dotOffset, Identifier field)
        : base(locs, ExpressionKind.DOT)
    {
        this.obj = obj;
        this.dotOffset = dotOffset;
        this.field = field;
    }

    public Expression GetObject() => obj;

    public Identifier GetField() => field;

    public override int GetStartOffset() => obj.GetStartOffset();

    public override int GetEndOffset() => field.GetEndOffset();

    public Location GetDotLocation() => Locs.GetLocation(dotOffset);

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
