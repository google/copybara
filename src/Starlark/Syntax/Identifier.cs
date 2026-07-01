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

/// <summary>Syntax node for an identifier.</summary>
public sealed class Identifier : Expression
{
    private readonly string name;
    private readonly int nameOffset;

    // Set by Resolver if applicable.
    private Resolver.Binding? binding;

    internal Identifier(FileLocations locs, string name, int nameOffset)
        : base(locs, ExpressionKind.IDENTIFIER)
    {
        this.name = name;
        this.nameOffset = nameOffset;
    }

    public override int GetStartOffset() => nameOffset;

    public override int GetEndOffset() => nameOffset + name.Length;

    /// <summary>Returns the name of the Identifier.</summary>
    public string GetName() => name;

    public bool IsPrivate() => name.StartsWith("_", StringComparison.Ordinal);

    /// <summary>
    /// Returns information about the binding (symbol) that the identifier refers to. Set by the
    /// resolver. May be null.
    /// </summary>
    public Resolver.Binding? GetBinding() => binding;

    internal void SetBinding(Resolver.Binding bind)
    {
        if (this.binding != null)
        {
            throw new InvalidOperationException("binding already set");
        }
        this.binding = bind;
    }

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);

    /// <summary>Reports whether the string is a valid identifier.</summary>
    public static bool IsValid(string name)
    {
        // Keep consistent with Lexer.ScanIdentifier.
        for (int i = 0; i < name.Length; i++)
        {
            char c = name[i];
            if (!(('a' <= c && c <= 'z')
                || ('A' <= c && c <= 'Z')
                || (i > 0 && '0' <= c && c <= '9')
                || (c == '_')))
            {
                return false;
            }
        }
        return name.Length != 0;
    }

    /// <summary>Returns all names bound by an LHS expression.</summary>
    public static ImmutableHashSet<Identifier> BoundIdentifiers(Expression expr)
    {
        if (expr is Identifier id)
        {
            return ImmutableHashSet.Create(id);
        }
        var result = ImmutableHashSet.CreateBuilder<Identifier>();
        CollectBoundIdentifiers(expr, result);
        return result.ToImmutable();
    }

    private static void CollectBoundIdentifiers(
        Expression lhs, ImmutableHashSet<Identifier>.Builder result)
    {
        if (lhs is Identifier id)
        {
            result.Add(id);
            return;
        }
        if (lhs is ListExpression variables)
        {
            foreach (Expression expression in variables.GetElements())
            {
                CollectBoundIdentifiers(expression, result);
            }
        }
    }
}
