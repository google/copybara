// Copyright 2019 The Bazel Authors. All rights reserved.
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
/// Syntax node for a non-negative float literal. (Negative floats are parsed as a
/// <see cref="UnaryOperatorExpression"/> operating on a positive <see cref="FloatLiteral"/> argument.)
/// </summary>
public sealed class FloatLiteral : Expression
{
    private readonly string raw;
    private readonly int tokenOffset;
    private readonly double value;

    internal FloatLiteral(FileLocations locs, string raw, int tokenOffset, double value)
        : base(locs, ExpressionKind.FLOAT_LITERAL)
    {
        this.raw = raw;
        this.tokenOffset = tokenOffset;
        this.value = value;
    }

    /// <summary>Returns the value denoted by this literal.</summary>
    public double GetValue() => value;

    /// <summary>Returns the raw source text of the literal.</summary>
    public string GetRaw() => raw;

    public override int GetStartOffset() => tokenOffset;

    public override int GetEndOffset() => tokenOffset + raw.Length;

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
