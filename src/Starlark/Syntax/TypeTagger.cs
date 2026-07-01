// Copyright 2025 The Bazel Authors. All rights reserved.
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
using System.Linq;
using Starlark.Spelling;

namespace Starlark.Syntax;

/// <summary>
/// A visitor for tagging the data structures of a resolved file with type information.
///
/// <para>This populates the function type on the <see cref="Resolver.Function"/> objects in the AST
/// and records whether or not a given <see cref="Resolver.Function"/> is considered to use static
/// type syntax; populates the variable types on the <see cref="Resolver.Binding"/> objects; and
/// populates the Starlark type stored in <see cref="CastExpression"/>s. These type fields must all
/// be null prior to running the visitor.</para>
///
/// <para>The types assigned to the fields are based solely on the type annotations in the program.
/// No type inference is done here.</para>
///
/// <para>Only a file that has passed the <c>Resolver</c> without errors should be run through this
/// visitor.</para>
/// </summary>
public sealed class TypeTagger : NodeVisitor
{
    private readonly Resolver.IModule module;

    private readonly List<SyntaxError> errors;

    // Empty if we are tagging a type expression (inside which no function definitions are allowed).
    // Populated and mutated by visitation. Used as a stack: top is the most recently pushed.
    private readonly List<Resolver.Function> functionStack = new();

    // Formats and reports an error at the start of the specified node.
    private void Errorf(Node node, string format, params object?[] args)
    {
        Errorf(node.GetStartLocation(), format, args);
    }

    // Formats and reports an error at the specified location.
    private void Errorf(Location loc, string format, params object?[] args)
    {
        errors.Add(new SyntaxError(loc, string.Format(format, args)));
    }

    private TypeTagger(List<SyntaxError> errors, Resolver.IModule module)
    {
        this.errors = errors;
        this.module = module;
    }

    private TypeTagger(List<SyntaxError> errors, Resolver.IModule module, Resolver.Function toplevel)
        : this(errors, module)
    {
        functionStack.Add(toplevel);
    }

    /// <summary>
    /// Given an identifier denoting a type constructor, obtains the type constructor from the module.
    ///
    /// <para>If no match, logs an error at the given node and returns null.</para>
    /// </summary>
    private TypeConstructor? ResolveTypeConstructor(Identifier id)
    {
        string name = id.GetName();

        var scope = id.GetBinding()!.GetScope();
        if (!(scope == Resolver.Scope.UNIVERSAL
            || scope == Resolver.Scope.PREDECLARED
            || scope == Resolver.Scope.GLOBAL))
        {
            // Local names cannot be types. Don't allow `x: Foo` to succeed if Foo is a local
            // shadowing a type name.
            Errorf(id, "local symbol '{0}' cannot be used as a type", name);
            return null;
        }

        try
        {
            TypeConstructor? constructor = module.GetTypeConstructor(name);
            if (constructor == null)
            {
                Errorf(id, "{0} symbol '{1}' cannot be used as a type", Resolver.ScopeToString(scope), name);
                return null;
            }
            return constructor;
        }
        catch (Resolver.Undefined ex)
        {
            string suggestion =
                ex.Candidates != null ? SpellChecker.DidYouMean(name, ex.Candidates) : "";
            Errorf(id, "{0}{1}", ex.Message, suggestion);
            return null;
        }
    }

    private TypeConstructor.Arg ExtractArg(Expression expr)
    {
        switch (expr.Kind)
        {
            case Expression.ExpressionKind.BINARY_OPERATOR:
                {
                    // Syntax sugar for union types, i.e. a|b == Union[a,b]
                    var binop = (BinaryOperatorExpression)expr;
                    if (binop.GetOperator() == TokenKind.PIPE)
                    {
                        StarlarkType x = ExtractType(binop.GetX());
                        StarlarkType y = ExtractType(binop.GetY());
                        return Types.Union(x, y);
                    }
                    Errorf(expr, "binary operator '{0}' is not supported", binop.GetOperator().ToDisplayString());
                    return Types.ANY;
                }

            case Expression.ExpressionKind.TYPE_APPLICATION:
                {
                    var app = (TypeApplication)expr;

                    TypeConstructor? constructor = ResolveTypeConstructor(app.GetConstructor());
                    if (constructor == null)
                    {
                        return Types.ANY;
                    }
                    IReadOnlyList<TypeConstructor.Arg> arguments =
                        app.GetArguments().Select(ExtractArg).ToList();

                    try
                    {
                        return constructor.CreateStarlarkType(arguments);
                    }
                    catch (TypeConstructor.Failure e)
                    {
                        Errorf(expr, "{0}", e.Message);
                        return Types.ANY;
                    }
                }

            case Expression.ExpressionKind.IDENTIFIER:
                {
                    TypeConstructor? constructor = ResolveTypeConstructor((Identifier)expr);
                    if (constructor == null)
                    {
                        return Types.ANY;
                    }
                    try
                    {
                        return constructor.CreateStarlarkType(
                            System.Array.Empty<TypeConstructor.Arg>());
                    }
                    catch (TypeConstructor.Failure e)
                    {
                        Errorf(expr, "{0}", e.Message);
                        return Types.ANY;
                    }
                }

            case Expression.ExpressionKind.ELLIPSIS:
                {
                    return TypeConstructor.Arg.ELLIPSIS;
                }

            case Expression.ExpressionKind.LIST_EXPR:
                {
                    var listExpr = (ListExpression)expr;
                    if (listExpr.IsTuple() && listExpr.GetElements().Count == 0)
                    {
                        return TypeConstructor.Arg.EMPTY_TUPLE;
                    }
                    break;
                }

            default:
                // fall through
                break;
        }

        // TODO(ilist@): full evaluation: lists and dicts
        Errorf(expr, "unexpected expression '{0}'", expr);
        return Types.ANY;
    }

    private StarlarkType ExtractType(Expression expr)
    {
        TypeConstructor.Arg arg = ExtractArg(expr);
        if (arg is not StarlarkType type)
        {
            Errorf(expr, "expression '{0}' is not a valid type.", expr);
            return Types.ANY;
        }
        return type;
    }

    /// <summary>
    /// Statically evaluates a type expression to the <see cref="StarlarkType"/> it denotes.
    /// </summary>
    /// <exception cref="SyntaxError.Exception">
    /// if expr is not a type expression or if it could not be evaluated to a type.
    /// </exception>
    public static StarlarkType ExtractType(Expression expr, Resolver.IModule module)
    {
        var errors = new List<SyntaxError>();
        var r = new TypeTagger(errors, module);
        StarlarkType result = r.ExtractType(expr);
        if (errors.Count != 0)
        {
            throw new SyntaxError.Exception(r.errors);
        }
        return result;
    }

    private Types.CallableType CreateFunctionType(
        IReadOnlyList<Parameter> parameters, Expression? returnTypeExpr)
    {
        var names = ImmutableArray.CreateBuilder<string>();
        var types = ImmutableArray.CreateBuilder<StarlarkType>();
        var mandatoryParameters = ImmutableHashSet.CreateBuilder<string>();

        int nparams = parameters.Count;
        int numPositionalParameters = 0;
        Parameter.Star? star = null;
        Parameter.StarStar? starStar = null;
        for (int i = 0; i < nparams; i++)
        {
            Parameter param = parameters[i];
            if (param is Parameter.Star pstar)
            {
                star = pstar;
                continue;
            }
            if (param is Parameter.StarStar pstarstar)
            {
                starStar = pstarstar;
                continue;
            }
            if (star == null)
            {
                numPositionalParameters++;
            }

            string name = param.GetName()!;
            Expression? typeExpr = param.GetType();

            names.Add(name);
            types.Add(typeExpr == null ? Types.ANY : ExtractType(typeExpr));
            if (param is Parameter.Mandatory)
            {
                mandatoryParameters.Add(name);
            }
        }

        StarlarkType? varargsType = null;
        if (star != null && star.GetIdentifier() != null)
        {
            Expression? typeExpr = star.GetType();
            varargsType = typeExpr == null ? Types.ANY : ExtractType(typeExpr);
        }

        StarlarkType? kwargsType = null;
        if (starStar != null)
        {
            Expression? typeExpr = starStar.GetType();
            kwargsType = typeExpr == null ? Types.ANY : ExtractType(typeExpr);
        }

        StarlarkType returnType = Types.ANY;
        if (returnTypeExpr != null)
        {
            returnType = ExtractType(returnTypeExpr);
        }

        return Types.Callable(
            names.ToImmutable(),
            types.ToImmutable(),
            /* numPositionalOnlyParameters= */ 0,
            numPositionalParameters,
            mandatoryParameters.ToImmutable(),
            varargsType,
            kwargsType,
            returnType);
    }

    /// <summary>
    /// Sets an identifier's type.
    ///
    /// <para>The <c>Binding</c> on the identifier must have already been set by the resolver.</para>
    ///
    /// <para>Logs an error if the identifier is not the first binding occurrence of the
    /// <c>Binding</c>. In this case, the type is not updated.</para>
    /// </summary>
    private void SetType(Node node, Identifier id, StarlarkType type)
    {
        Resolver.Binding? binding = id.GetBinding();
        if (binding == null)
        {
            throw new ArgumentNullException(
                nameof(id), string.Format("no binding set on identifier '{0}'", id.GetName()));
        }

        if (binding.GetFirst() != id)
        {
            if (node is DefStatement)
            {
                // A def statement appearing in typed code constitutes an implicit type annotation on
                // the function identifier's symbol.
                Errorf(id, "function '{0}' was previously declared", id.GetName());
            }
            else
            {
                Errorf(id, "type annotation on '{0}' may only appear at its declaration", id.GetName());
            }
            if (binding.IsSyntactic())
            {
                Errorf(binding.GetFirst(), "'{0}' previously declared here", id.GetName());
            }
            return;
        }

        if (binding.GetType() != null)
        {
            throw new ArgumentException(
                string.Format(
                    "Expected type of binding {0} to be null but was {1}", binding, binding.GetType()));
        }
        binding.SetType(type);
    }

    /// <summary>
    /// Sets a resolved function's type.
    /// </summary>
    private static void SetType(Resolver.Function resolved, Types.CallableType type)
    {
        if (resolved == null)
        {
            throw new ArgumentNullException(nameof(resolved));
        }
        if (resolved.GetFunctionType() != null)
        {
            throw new ArgumentException(
                string.Format(
                    "Expected type of resolved function {0} to be null but was {1}",
                    resolved.GetName(),
                    resolved.GetFunctionType()));
        }
        resolved.SetFunctionType(type);
    }

    public override void Visit(StarlarkFile file)
    {
        if (functionStack.Count != 0)
        {
            throw new InvalidOperationException(
                "When tagging a StarlarkFile, functionStack is expected to be initially empty");
        }
        Resolver.Function toplevel = file.GetResolvedFunction()!;
        Push(toplevel);
        base.Visit(file);
        if (!Pop().Equals(toplevel))
        {
            throw new InvalidOperationException();
        }
    }

    public override void Visit(AssignmentStatement assignment)
    {
        if (assignment.GetType() != null)
        {
            SetUsesTypeSyntax();
            StarlarkType type = ExtractType(assignment.GetType()!);
            SetType(assignment, (Identifier)assignment.GetLHS(), type);
        }

        // Traverse children; RHS could contain a lambda.
        base.Visit(assignment);
    }

    public override void Visit(DefStatement def)
    {
        Resolver.Function resolvedFunction = def.GetResolvedFunction()!;
        Push(resolvedFunction);
        Types.CallableType type = CreateFunctionType(def.GetParameters(), def.GetReturnType());
        SetType(resolvedFunction, type);
        SetType(def, def.GetIdentifier(), type);
        // Parameter types handled by Visit(Parameter).
        if (def.GetReturnType() != null || def.GetTypeParameters().Count != 0)
        {
            SetUsesTypeSyntax();
        }

        base.Visit(def);
        if (!Pop().Equals(resolvedFunction))
        {
            throw new InvalidOperationException();
        }
    }

    public override void Visit(Parameter param)
    {
        if (param.GetIdentifier() != null)
        {
            // Default to ANY for unannotated params. This matches the behavior for the
            // Resolver.Function's type.
            StarlarkType type = Types.ANY;
            if (param.GetType() != null)
            {
                SetUsesTypeSyntax();
                type = ExtractType(param.GetType()!);
            }
            SetType(param, param.GetIdentifier()!, type);
        }

        base.Visit(param);
    }

    public override void Visit(TypeAliasStatement node)
    {
        SetUsesTypeSyntax();
        base.Visit(node);
    }

    public override void Visit(VarStatement var)
    {
        StarlarkType type = ExtractType(var.GetType());
        SetType(var, var.GetIdentifier(), type);
        SetUsesTypeSyntax();

        // No need to descend into type expression child.
    }

    public override void Visit(CastExpression cast)
    {
        SetUsesTypeSyntax();
        cast.SetStarlarkType(ExtractType(cast.GetType()));
        base.Visit(cast);
    }

    public override void Visit(LambdaExpression lambda)
    {
        Types.CallableType type =
            CreateFunctionType(lambda.GetParameters(), /* returnTypeExpr= */ null);
        SetType(lambda.GetResolvedFunction()!, type);

        base.Visit(lambda);
    }

    /// <summary>
    /// Sets the Starlark types of the <see cref="Resolver.Function"/>s and
    /// <see cref="Resolver.Binding"/>s in the given AST (which must have already been processed by
    /// <see cref="Resolver"/>), based on the supplied annotations.
    /// </summary>
    public static void TagFile(StarlarkFile file, Resolver.IModule module)
    {
        var r = new TypeTagger(file.errors, module);
        r.Visit(file);
    }

    /// <summary>
    /// Same as <see cref="TagFile"/>, but for an individual expression.
    /// </summary>
    /// <exception cref="SyntaxError.Exception">on tagging error.</exception>
    public static void TagExpr(Expression expr, Resolver.Function function, Resolver.IModule module)
    {
        var errors = new List<SyntaxError>();
        var r = new TypeTagger(errors, module, function);

        r.Visit(expr);

        if (errors.Count != 0)
        {
            throw new SyntaxError.Exception(errors);
        }
    }

    /// <summary>
    /// Sets the Starlark type on a <see cref="Resolver.Function"/> that the resolver generated to
    /// wrap an expression.
    /// </summary>
    public static void TagExprFunction(Resolver.Function function, StarlarkType exprType)
    {
        Types.CallableType functionType =
            Types.Callable(
                /* parameterNames= */ ImmutableArray<string>.Empty,
                /* parameterTypes= */ ImmutableArray<StarlarkType>.Empty,
                /* numPositionalOnlyParameters= */ 0,
                /* numPositionalParameters= */ 0,
                /* mandatoryParams= */ ImmutableHashSet<string>.Empty,
                /* varargsType= */ null,
                /* kwargsType= */ null,
                /* returns= */ exprType);
        SetType(function, functionType);
    }

    private void SetUsesTypeSyntax()
    {
        // If anything in the file (or in the expr if TypeTagger is invoked via tagExpr()) uses type
        // syntax, the toplevel is considered to use type syntax.
        functionStack[0].SetUsesTypeSyntax();
        // If anything nested in the most proximate def statement uses type syntax, the def statement
        // is considered to use type syntax.
        functionStack[functionStack.Count - 1].SetUsesTypeSyntax();
    }

    // ==== Stack helpers (top == last element, matching Java ArrayDeque push/peek/pop) ====

    private void Push(Resolver.Function f) => functionStack.Add(f);

    private Resolver.Function Pop()
    {
        Resolver.Function f = functionStack[functionStack.Count - 1];
        functionStack.RemoveAt(functionStack.Count - 1);
        return f;
    }
}
