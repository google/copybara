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
using System.Text;

namespace Starlark.Syntax;

/// <summary>Syntax node for list and tuple expressions.</summary>
public sealed class ListExpression : Expression
{
    private readonly bool isTuple;
    private readonly int lbracketOffset; // -1 => unparenthesized non-empty tuple
    private readonly ImmutableArray<Expression> elements;
    private readonly int rbracketOffset; // -1 => unparenthesized non-empty tuple

    internal ListExpression(
        FileLocations locs,
        bool isTuple,
        int lbracketOffset,
        ImmutableArray<Expression> elements,
        int rbracketOffset)
        : base(locs, ExpressionKind.LIST_EXPR)
    {
        // An unparenthesized tuple must be non-empty.
        if (elements.IsEmpty && !(lbracketOffset >= 0 && rbracketOffset >= 0))
        {
            throw new ArgumentException("empty unparenthesized tuple");
        }
        this.lbracketOffset = lbracketOffset;
        this.isTuple = isTuple;
        this.elements = elements;
        this.rbracketOffset = rbracketOffset;
    }

    public IReadOnlyList<Expression> GetElements() => elements;

    /// <summary>Reports whether this is a tuple expression.</summary>
    public bool IsTuple() => isTuple;

    public override int GetStartOffset() =>
        lbracketOffset < 0 ? elements[0].GetStartOffset() : lbracketOffset;

    public override int GetEndOffset() =>
        rbracketOffset < 0 ? elements[elements.Length - 1].GetEndOffset() : rbracketOffset + 1;

    public override string ToString()
    {
        var buf = new StringBuilder();
        buf.Append(IsTuple() ? '(' : '[');
        AppendNodes(buf, elements);
        if (IsTuple() && elements.Length == 1)
        {
            buf.Append(',');
        }
        buf.Append(IsTuple() ? ')' : ']');
        return buf.ToString();
    }

    // Appends elements to buf, comma-separated, abbreviating if they are numerous or long.
    // (Also used by CallExpression, TypeApplication, TypeAliasStatement.)
    internal static void AppendNodes<T>(StringBuilder buf, IReadOnlyList<T> elements) where T : Node
    {
        int n = elements.Count;
        for (int i = 0; i < n; i++)
        {
            if (i > 0)
            {
                buf.Append(", ");
            }
            int mark = buf.Length;
            buf.Append(elements[i]);
            // Abbreviate, dropping this element, if we exceed 32 chars,
            // or 4 elements (with more elements following).
            if (buf.Length >= 32 || (i == 4 && i + 1 < n))
            {
                buf.Length = mark;
                buf.Append(string.Format("+{0} more", n - i));
                break;
            }
        }
    }

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
