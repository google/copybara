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
/// A visitor for visiting the nodes of a syntax tree in lexical order (not evaluation order!).
///
/// <para>Comments are *not* visited.</para>
/// </summary>
public class NodeVisitor
{
    /// <summary>
    /// If set, we only visit <see cref="Identifier"/>s that correspond to a definition or use of a
    /// symbol in the current file.
    /// </summary>
    protected bool skipNonSymbolIdentifiers = false;

    /// <summary>Entrypoint for visiting a node. Clients should avoid calling node-specific overloads.</summary>
    public virtual void Visit(Node node)
    {
        // Double-dispatch pattern.
        node.Accept(this);
    }

    // ==== Miscellaneous node types ====

    /// <summary>Handles all four Argument node types uniformly.</summary>
    public virtual void Visit(Argument node)
    {
        if (!skipNonSymbolIdentifiers && node is Argument.Keyword keyword)
        {
            Visit(keyword.GetIdentifier());
        }
        Visit(node.GetValue());
    }

    /// <summary>Not supported.</summary>
    public virtual void Visit(Comment node)
    {
        throw new NotSupportedException("NodeVisitor does not support visiting comments");
    }

    /// <summary>Handles all four Parameter node types uniformly.</summary>
    public virtual void Visit(Parameter node)
    {
        if (node.GetIdentifier() != null)
        {
            Visit(node.GetIdentifier()!);
        }
        if (node.GetType() != null)
        {
            Visit(node.GetType()!);
        }
        if (node.GetDefaultValue() != null)
        {
            Visit(node.GetDefaultValue()!);
        }
    }

    public virtual void Visit(StarlarkFile node)
    {
        VisitBlock(node.GetStatements());
    }

    // ==== Statement nodes ====

    public virtual void Visit(AssignmentStatement node)
    {
        Visit(node.GetLHS());
        if (node.GetType() != null)
        {
            Visit(node.GetType()!);
        }
        Visit(node.GetRHS());
    }

    public virtual void Visit(ExpressionStatement node)
    {
        Visit(node.GetExpression());
    }

    public virtual void Visit(FlowStatement node)
    {
    }

    public virtual void Visit(ForStatement node)
    {
        Visit(node.GetVars());
        Visit(node.GetCollection());
        VisitBlock(node.GetBody());
    }

    public virtual void Visit(DefStatement node)
    {
        Visit(node.GetIdentifier());
        VisitAll(node.GetTypeParameters());
        VisitAll(node.GetParameters());
        if (node.GetReturnType() != null)
        {
            Visit(node.GetReturnType()!);
        }
        VisitBlock(node.GetBody());
    }

    public virtual void Visit(IfStatement node)
    {
        Visit(node.GetCondition());
        VisitBlock(node.GetThenBlock());
        if (node.GetElseBlock() != null)
        {
            VisitBlock(node.GetElseBlock()!);
        }
    }

    public virtual void Visit(LoadStatement node)
    {
        foreach (LoadStatement.Binding binding in node.GetBindings())
        {
            Visit(binding.GetLocalName());
            // We don't visit the original name.
        }
    }

    public virtual void Visit(ReturnStatement node)
    {
        if (node.GetResult() != null)
        {
            Visit(node.GetResult()!);
        }
    }

    public virtual void Visit(TypeAliasStatement node)
    {
        Visit(node.GetIdentifier());
        VisitAll(node.GetParameters());
        Visit(node.GetDefinition());
    }

    public virtual void Visit(VarStatement node)
    {
        Visit(node.GetIdentifier());
        Visit(node.GetType());
    }

    // ==== Expression nodes ====

    public virtual void Visit(BinaryOperatorExpression node)
    {
        Visit(node.GetX());
        Visit(node.GetY());
    }

    public virtual void Visit(CallExpression node)
    {
        Visit(node.GetFunction());
        VisitAll(node.GetArguments());
    }

    public virtual void Visit(CastExpression node)
    {
        Visit(node.GetType());
        Visit(node.GetValue());
    }

    public virtual void Visit(Comprehension node)
    {
        Visit(node.GetBody());
        foreach (Comprehension.Clause clause in node.GetClauses())
        {
            if (clause is Comprehension.For f)
            {
                Visit(f);
            }
            else
            {
                Visit((Comprehension.If)clause);
            }
        }
    }

    public virtual void Visit(Comprehension.For node)
    {
        Visit(node.GetVars());
        Visit(node.GetIterable());
    }

    public virtual void Visit(Comprehension.If node)
    {
        Visit(node.GetCondition());
    }

    public virtual void Visit(ConditionalExpression node)
    {
        Visit(node.GetThenCase());
        Visit(node.GetCondition());
        if (node.GetElseCase() != null)
        {
            Visit(node.GetElseCase());
        }
    }

    public virtual void Visit(DictExpression node)
    {
        VisitAll(node.GetEntries());
    }

    public virtual void Visit(DictExpression.Entry node)
    {
        Visit(node.GetKey());
        Visit(node.GetValue());
    }

    public virtual void Visit(DotExpression node)
    {
        Visit(node.GetObject());
        if (!skipNonSymbolIdentifiers)
        {
            Visit(node.GetField());
        }
    }

    public virtual void Visit(Ellipsis node)
    {
    }

    public virtual void Visit(FloatLiteral node)
    {
    }

    public virtual void Visit(Identifier node)
    {
    }

    public virtual void Visit(IndexExpression node)
    {
        Visit(node.GetObject());
        Visit(node.GetKey());
    }

    public virtual void Visit(IntLiteral node)
    {
    }

    public virtual void Visit(IsInstanceExpression node)
    {
        Visit(node.GetValue());
        Visit(node.GetType());
    }

    public virtual void Visit(LambdaExpression node)
    {
        VisitAll(node.GetParameters());
        Visit(node.GetBody());
    }

    public virtual void Visit(ListExpression node)
    {
        VisitAll(node.GetElements());
    }

    public virtual void Visit(SliceExpression node)
    {
        Visit(node.GetObject());
        if (node.GetStart() != null)
        {
            Visit(node.GetStart()!);
        }
        if (node.GetStop() != null)
        {
            Visit(node.GetStop()!);
        }
        if (node.GetStep() != null)
        {
            Visit(node.GetStep()!);
        }
    }

    public virtual void Visit(StringLiteral node)
    {
    }

    public virtual void Visit(UnaryOperatorExpression node)
    {
        Visit(node.GetX());
    }

    public virtual void Visit(TypeApplication node)
    {
        Visit(node.GetConstructor());
        VisitAll(node.GetArguments());
    }

    // ==== Helpers for sequences of nodes ====

    /// <summary>Visits a sequence of nodes (e.g. a list of arguments).</summary>
    public void VisitAll<T>(IReadOnlyList<T> nodes) where T : Node
    {
        foreach (Node node in nodes)
        {
            Visit(node);
        }
    }

    /// <summary>Convenience/readability method for visiting a block of statements.</summary>
    public void VisitBlock(IReadOnlyList<Statement> statements)
    {
        VisitAll(statements);
    }
}
