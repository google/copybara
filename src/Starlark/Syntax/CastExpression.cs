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

/// <summary>Syntax node for cast() expressions.</summary>
public sealed class CastExpression : Expression
{
    private readonly int startOffset;
    private readonly Expression type;
    private readonly Expression value;
    private readonly int rparenOffset;
    // Set by type tagging.
    private StarlarkType? starlarkType;

    internal CastExpression(
        FileLocations locs, int startOffset, Expression type, Expression value, int rparenOffset)
        : base(locs, ExpressionKind.CAST)
    {
        this.startOffset = startOffset;
        this.type = type;
        this.value = value;
        this.rparenOffset = rparenOffset;
    }

    public override int GetStartOffset() => startOffset;

    public override int GetEndOffset() => rparenOffset + 1;

    public new Expression GetType() => type;

    /// <summary>
    /// Returns the Starlark type extracted from the <see cref="GetType"/> expression. Non-null after
    /// type tagging.
    /// </summary>
    public StarlarkType? GetStarlarkType() => starlarkType;

    /// <summary>Intended for use by the type tagger.</summary>
    internal void SetStarlarkType(StarlarkType starlarkType) => this.starlarkType = starlarkType;

    public Expression GetValue() => value;

    public override string ToString()
    {
        var buf = new StringBuilder();
        buf.Append("cast(");
        buf.Append(type);
        buf.Append(", ");
        buf.Append(value);
        buf.Append(')');
        return buf.ToString();
    }

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
