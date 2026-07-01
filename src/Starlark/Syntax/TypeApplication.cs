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

using System.Collections.Immutable;
using System.Text;

namespace Starlark.Syntax;

/// <summary>Syntax node for a type application expression.</summary>
public sealed class TypeApplication : Expression
{
    private readonly Identifier constructor;
    private readonly ImmutableArray<Expression> arguments;
    private readonly int rbracketOffset;

    internal TypeApplication(
        FileLocations locs,
        Identifier constructor,
        ImmutableArray<Expression> arguments,
        int rbracketOffset)
        : base(locs, ExpressionKind.TYPE_APPLICATION)
    {
        this.constructor = constructor ?? throw new ArgumentNullException(nameof(constructor));
        this.arguments = arguments;
        this.rbracketOffset = rbracketOffset;
    }

    /// <summary>Returns the type constructor.</summary>
    public Identifier GetConstructor() => constructor;

    /// <summary>Returns the type arguments.</summary>
    public IReadOnlyList<Expression> GetArguments() => arguments;

    public override int GetStartOffset() => constructor.GetStartOffset();

    public override int GetEndOffset() => rbracketOffset + 1;

    public override string ToString()
    {
        var buf = new StringBuilder();
        buf.Append(constructor);
        buf.Append('[');
        ListExpression.AppendNodes(buf, arguments);
        buf.Append(']');
        return buf.ToString();
    }

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
