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

namespace Starlark.Syntax;

/// <summary>
/// Syntax node for a variable type annotation appearing as its own statement (<c>foo : int</c>), as
/// opposed to in an assignment statement where there's an initializer on the right-hand side.
/// </summary>
public sealed class VarStatement : Statement
{
    private readonly Identifier identifier;

    private readonly Expression type;

    private readonly DocComments? docComments;

    internal VarStatement(
        FileLocations locs,
        Identifier identifier,
        Expression type,
        DocComments? docComments)
        : base(locs, StatementKind.VAR)
    {
        this.identifier = identifier;
        this.type = type;
        this.docComments = docComments;
    }

    public override int GetStartOffset() => identifier.GetStartOffset();

    public override int GetEndOffset() => type.GetEndOffset();

    /// <summary>Returns the variable being declared and annotated.</summary>
    public Identifier GetIdentifier() => identifier;

    /// <summary>Returns the type expression associated with the variable.</summary>
    public new Expression GetType() => type;

    /// <summary>Returns the Sphinx autodoc-style doc comments attached to this statement, if any.</summary>
    public DocComments? GetDocComments() => docComments;

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);
}
