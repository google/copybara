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

using System.Text;

namespace Starlark.Syntax;

/// <summary>Syntax node for isinstance() expressions.</summary>
public sealed class IsInstanceExpression : Expression
{
    private readonly int startOffset;
    private readonly Expression value;
    private readonly Expression type;
    private readonly int rparenOffset;

    internal IsInstanceExpression(
        FileLocations locs, int startOffset, Expression value, Expression type, int rparenOffset)
        : base(locs, ExpressionKind.ISINSTANCE)
    {
        this.startOffset = startOffset;
        this.value = value;
        this.type = type;
        this.rparenOffset = rparenOffset;
    }

    public override int GetStartOffset() => startOffset;

    public override int GetEndOffset() => rparenOffset + 1;

    public Expression GetValue() => value;

    public new Expression GetType() => type;

    public override string ToString()
    {
        var buf = new StringBuilder();
        buf.Append("isinstance(");
        buf.Append(value);
        buf.Append(", ");
        buf.Append(type);
        buf.Append(')');
        return buf.ToString();
    }

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
