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

namespace Starlark.Syntax;

/// <summary>Syntax node for dict expressions.</summary>
public sealed class DictExpression : Expression
{
    /// <summary>A key/value pair in a dict expression or comprehension.</summary>
    public sealed class Entry : Node
    {
        private readonly Expression key;
        private readonly int colonOffset;
        private readonly Expression value;

        internal Entry(FileLocations locs, Expression key, int colonOffset, Expression value)
            : base(locs)
        {
            this.key = key;
            this.colonOffset = colonOffset;
            this.value = value;
        }

        public Expression GetKey() => key;

        public Expression GetValue() => value;

        public override int GetStartOffset() => key.GetStartOffset();

        public override int GetEndOffset() => value.GetEndOffset();

        public Location GetColonLocation() => Locs.GetLocation(colonOffset);

        public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
    }

    private readonly int lbraceOffset;
    private readonly ImmutableArray<Entry> entries;
    private readonly int rbraceOffset;

    internal DictExpression(FileLocations locs, int lbraceOffset, IReadOnlyList<Entry> entries, int rbraceOffset)
        : base(locs, ExpressionKind.DICT_EXPR)
    {
        this.lbraceOffset = lbraceOffset;
        this.entries = entries.ToImmutableArray();
        this.rbraceOffset = rbraceOffset;
    }

    public override int GetStartOffset() => lbraceOffset;

    public override int GetEndOffset() => rbraceOffset + 1;

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);

    public IReadOnlyList<Entry> GetEntries() => entries;
}
