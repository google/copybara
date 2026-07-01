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
/// Syntax node for an argument to a function.
///
/// <para>Arguments may be of four forms, as in <c>f(expr, id=expr, *expr, **expr)</c>. These are
/// represented by the subclasses Positional, Keyword, Star, and StarStar.</para>
/// </summary>
public abstract class Argument : Node
{
    protected readonly Expression value;

    internal Argument(FileLocations locs, Expression value)
        : base(locs)
    {
        this.value = value ?? throw new ArgumentNullException(nameof(value));
    }

    public Expression GetValue() => value;

    public override int GetEndOffset() => value.GetEndOffset();

    /// <summary>Return the name of this argument's parameter, or null if it is not a Keyword argument.</summary>
    public virtual string? GetName() => null;

    /// <summary>Syntax node for a positional argument, <c>f(expr)</c>.</summary>
    public sealed class Positional : Argument
    {
        internal Positional(FileLocations locs, Expression value)
            : base(locs, value)
        {
        }

        public override int GetStartOffset() => value.GetStartOffset();
    }

    /// <summary>Syntax node for a keyword argument, <c>f(id=expr)</c>.</summary>
    public sealed class Keyword : Argument
    {
        internal readonly Identifier id;

        internal Keyword(FileLocations locs, Identifier id, Expression value)
            : base(locs, value)
        {
            this.id = id;
        }

        public Identifier GetIdentifier() => id;

        public override string? GetName() => id.GetName();

        public override int GetStartOffset() => id.GetStartOffset();
    }

    /// <summary>Syntax node for an argument of the form <c>f(*expr)</c>.</summary>
    public sealed class Star : Argument
    {
        private readonly int starOffset;

        internal Star(FileLocations locs, int starOffset, Expression value)
            : base(locs, value)
        {
            this.starOffset = starOffset;
        }

        public override int GetStartOffset() => starOffset;
    }

    /// <summary>Syntax node for an argument of the form <c>f(**expr)</c>.</summary>
    public sealed class StarStar : Argument
    {
        private readonly int starStarOffset;

        internal StarStar(FileLocations locs, int starStarOffset, Expression value)
            : base(locs, value)
        {
            this.starStarOffset = starStarOffset;
        }

        public override int GetStartOffset() => starStarOffset;
    }

    public override void Accept(NodeVisitor visitor)
    {
        // All Argument subclasses dispatch to NodeVisitor.Visit(Argument).
        visitor.Visit(this);
    }
}
