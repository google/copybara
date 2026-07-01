// Copyright 2018 The Bazel Authors. All rights reserved.
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

/// <summary>Syntax node for a function call expression.</summary>
public sealed class CallExpression : Expression
{
    private readonly Expression function;
    private readonly Location lparenLocation;
    private readonly ImmutableArray<Argument> arguments;
    private readonly int rparenOffset;

    private readonly int numPositionalArgs;

    internal CallExpression(
        FileLocations locs,
        Expression function,
        Location lparenLocation,
        ImmutableArray<Argument> arguments,
        int rparenOffset)
        : base(locs, ExpressionKind.CALL)
    {
        this.function = function ?? throw new ArgumentNullException(nameof(function));
        this.lparenLocation = lparenLocation;
        this.arguments = arguments;
        this.rparenOffset = rparenOffset;

        int n = 0;
        foreach (Argument arg in arguments)
        {
            if (arg is Argument.Positional)
            {
                n++;
            }
        }
        this.numPositionalArgs = n;
    }

    /// <summary>Returns the function that is called.</summary>
    public Expression GetFunction() => function;

    /// <summary>Returns the number of arguments of type <see cref="Argument.Positional"/>.</summary>
    public int GetNumPositionalArguments() => numPositionalArgs;

    /// <summary>Returns the function call's arguments.</summary>
    public IReadOnlyList<Argument> GetArguments() => arguments;

    public override int GetStartOffset() => function.GetStartOffset();

    public override int GetEndOffset() => rparenOffset + 1;

    public Location GetLparenLocation() => lparenLocation;

    public override string ToString()
    {
        var buf = new StringBuilder();
        buf.Append(function);
        buf.Append('(');
        ListExpression.AppendNodes(buf, arguments);
        buf.Append(')');
        return buf.ToString();
    }

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
