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
/// Syntax node for a parameter in a function definition.
///
/// <para>Parameters may be of four forms, as in <c>def f(a, b=c, *args, **kwargs)</c>. They are
/// represented by the subclasses Mandatory, Optional, Star, and StarStar.</para>
/// </summary>
public abstract class Parameter : Node
{
    // Null in the case of a bare * parameter, non-null for any other case.
    private readonly Identifier? id;

    private readonly Expression? type;

    private protected Parameter(FileLocations locs, Identifier? id, Expression? type)
        : base(locs)
    {
        this.id = id;
        this.type = type;
    }

    public string? GetName() => id?.GetName();

    public Identifier? GetIdentifier() => id;

    public virtual Expression? GetDefaultValue() => null;

    public new Expression? GetType() => type;

    /// <summary>
    /// Syntax node for a mandatory parameter, <c>f(id)</c>. It may be positional or keyword-only
    /// depending on its position.
    /// </summary>
    public sealed class Mandatory : Parameter
    {
        internal Mandatory(FileLocations locs, Identifier id, Expression? type)
            : base(locs, id, type)
        {
        }

        public override int GetStartOffset() => GetIdentifier()!.GetStartOffset();

        public override int GetEndOffset() =>
            GetType() != null ? GetType()!.GetEndOffset() : GetIdentifier()!.GetEndOffset();
    }

    /// <summary>
    /// Syntax node for an optional parameter, <c>f(id=expr)</c>. It may be positional or keyword-only
    /// depending on its position.
    /// </summary>
    public sealed class Optional : Parameter
    {
        public readonly Expression defaultValue;

        internal Optional(FileLocations locs, Identifier id, Expression? type, Expression defaultValue)
            : base(locs, id, type)
        {
            this.defaultValue = defaultValue;
        }

        public override Expression? GetDefaultValue() => defaultValue;

        public override int GetStartOffset() => GetIdentifier()!.GetStartOffset();

        public override int GetEndOffset() => GetDefaultValue()!.GetEndOffset();

        public override string ToString() => GetName() + "=" + defaultValue;
    }

    /// <summary>Syntax node for a star parameter, <c>f(*id)</c> or <c>f(..., *, ...)</c>.</summary>
    public sealed class Star : Parameter
    {
        private readonly int starOffset;

        internal Star(FileLocations locs, int starOffset, Identifier? id, Expression? type)
            : base(locs, id, type)
        {
            if (id == null && type != null)
            {
                throw new ArgumentException("Star parameter without id cannot have a type");
            }
            this.starOffset = starOffset;
        }

        public override int GetStartOffset() => starOffset;

        public override int GetEndOffset() =>
            GetType() != null ? GetType()!.GetEndOffset() : GetIdentifier()!.GetEndOffset();
    }

    /// <summary>Syntax node for a parameter of the form <c>f(**id)</c>.</summary>
    public sealed class StarStar : Parameter
    {
        private readonly int starStarOffset;

        internal StarStar(FileLocations locs, int starStarOffset, Identifier id, Expression? type)
            : base(locs, id, type)
        {
            this.starStarOffset = starStarOffset;
        }

        public override int GetStartOffset() => starStarOffset;

        public override int GetEndOffset() =>
            GetType() != null ? GetType()!.GetEndOffset() : GetIdentifier()!.GetEndOffset();
    }

    public override void Accept(NodeVisitor visitor)
    {
        // All Parameter subclasses dispatch to NodeVisitor.Visit(Parameter).
        visitor.Visit(this);
    }
}
