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

/// <summary>Syntax node for a load statement.</summary>
public sealed class LoadStatement : Statement
{
    /// <summary>
    /// Binding represents a binding in a load statement. load("...", local = "orig").
    /// </summary>
    public sealed class Binding
    {
        private readonly Identifier local;
        private readonly Identifier orig;

        public Identifier GetLocalName() => local;

        public Identifier GetOriginalName() => orig;

        internal Binding(Identifier localName, Identifier originalName)
        {
            this.local = localName;
            this.orig = originalName;
        }
    }

    private readonly int loadOffset;
    private readonly StringLiteral module;
    private readonly ImmutableArray<Binding> bindings;
    private readonly int rparenOffset;

    internal LoadStatement(
        FileLocations locs,
        int loadOffset,
        StringLiteral module,
        ImmutableArray<Binding> bindings,
        int rparenOffset)
        : base(locs, StatementKind.LOAD)
    {
        this.loadOffset = loadOffset;
        this.module = module;
        this.bindings = bindings;
        this.rparenOffset = rparenOffset;
    }

    public IReadOnlyList<Binding> GetBindings() => bindings;

    public StringLiteral GetImport() => module;

    public override int GetStartOffset() => loadOffset;

    public override int GetEndOffset() => rparenOffset + 1;

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
