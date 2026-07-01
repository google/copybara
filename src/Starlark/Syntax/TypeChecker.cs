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
/// A visitor for validating that expressions and statements respect the types of the symbols
/// appearing within them, as determined by the type tagger.
///
/// <para>In addition, this visitor modifies the function type on the <see cref="Resolver.Function"/>
/// objects of <see cref="LambdaExpression"/>s in the AST (originally populated by the
/// <see cref="TypeTagger"/>) to have a more precise return type, if possible; and populates the
/// types on the <see cref="Resolver.Binding"/> objects of untyped variables with the inferred types
/// of their values in their first assignments in typed code.</para>
///
/// <para>Type annotations are not traversed by this visitor.</para>
/// </summary>
public sealed class TypeChecker : NodeVisitor
{
    private readonly List<SyntaxError> errors;
    private readonly TypeContext typeContext;

    // Empty if we were invoked via inferTypeOf() to type-check an expression. Populated and mutated
    // by visitation. Used as a stack: top == last element.
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

    private void BinaryOperatorError(
        StarlarkType xType,
        TokenKind op,
        Location operatorLocation,
        StarlarkType yType,
        bool augmentedAssignment,
        string extraMessage)
    {
        Errorf(
            operatorLocation,
            "operator '{0}{1}' cannot be applied to types '{2}' and '{3}'{4}",
            op.ToDisplayString(),
            augmentedAssignment ? "=" : "",
            xType,
            yType,
            extraMessage.Length == 0 ? "" : ": " + extraMessage);
    }

    private void BinaryOperatorError(
        StarlarkType xType,
        TokenKind op,
        Location operatorLocation,
        StarlarkType yType,
        bool augmentedAssignment)
    {
        BinaryOperatorError(xType, op, operatorLocation, yType, augmentedAssignment, "");
    }

    private static string Plural(int n)
    {
        return n == 1 ? "" : "s";
    }

    private TypeChecker(List<SyntaxError> errors, TypeContext typeContext)
    {
        this.errors = errors;
        this.typeContext = typeContext;
    }

    /// <summary>
    /// Returns the annotated type of an identifier's symbol, asserting that the binding information
    /// is present.
    ///
    /// <para>If a type is not set on the binding it is taken to be <c>Any</c>.</para>
    /// </summary>
    private StarlarkType GetType(Identifier id)
    {
        Resolver.Binding? binding = id.GetBinding();
        if (binding == null)
        {
            throw new ArgumentNullException(nameof(id));
        }
        StarlarkType? type = binding.GetType();
        return type != null ? type : Types.ANY;
    }

    private void ErrorIfKeyNotInt(IndexExpression index, StarlarkType objType, StarlarkType keyType)
    {
        if (!StarlarkType.AssignableFrom(Types.INT, keyType))
        {
            Errorf(
                index.GetLbracketLocation(),
                "'{0}' of type '{1}' must be indexed by an integer, but got '{2}'",
                index.GetObject(),
                objType,
                keyType);
        }
    }

    /// <summary>
    /// Infers the type of an expression from a bottom-up traversal, relying on type information
    /// stored in identifier bindings by the <see cref="TypeTagger"/>.
    ///
    /// <para>May not be called on type expressions (annotations, var statements, type alias
    /// statements).</para>
    /// </summary>
    private StarlarkType Infer(Expression expr)
    {
        switch (expr.Kind)
        {
            case Expression.ExpressionKind.IDENTIFIER:
                return GetType((Identifier)expr);

            case Expression.ExpressionKind.STRING_LITERAL:
                return Types.STR;

            case Expression.ExpressionKind.INT_LITERAL:
                return Types.INT;

            case Expression.ExpressionKind.FLOAT_LITERAL:
                return Types.FLOAT;

            case Expression.ExpressionKind.CAST:
                {
                    var cast = (CastExpression)expr;
                    var unused = Infer(cast.GetValue()); // only to verify the value expr is well-typed
                    return cast.GetStarlarkType()!;
                }

            case Expression.ExpressionKind.DOT:
                return InferDot((DotExpression)expr);

            case Expression.ExpressionKind.INDEX:
                return InferIndex((IndexExpression)expr);

            case Expression.ExpressionKind.SLICE:
                return InferSlice((SliceExpression)expr);

            case Expression.ExpressionKind.LAMBDA:
                {
                    var lambda = (LambdaExpression)expr;
                    StarlarkType inferedReturnType = Infer(lambda.GetBody());
                    Types.CallableType originalType = lambda.GetResolvedFunction()!.GetFunctionType()!;
                    if (!originalType.GetReturnType().Equals(inferedReturnType))
                    {
                        // Update the lambda function type with a more precise return type.
                        lambda
                            .GetResolvedFunction()!
                            .SetFunctionType(
                                Types.Callable(
                                    originalType.GetParameterNames(),
                                    originalType.GetParameterTypes(),
                                    originalType.GetNumPositionalOnlyParameters(),
                                    originalType.GetNumPositionalParameters(),
                                    originalType.GetMandatoryParameters(),
                                    originalType.GetVarargsType(),
                                    originalType.GetKwargsType(),
                                    inferedReturnType));
                    }
                    return lambda.GetResolvedFunction()!.GetFunctionType()!;
                }

            case Expression.ExpressionKind.LIST_EXPR:
                {
                    var list = (ListExpression)expr;
                    var elementTypes = new List<StarlarkType>();
                    foreach (Expression element in list.GetElements())
                    {
                        elementTypes.Add(Infer(element));
                    }
                    return list.IsTuple()
                        ? Types.Tuple(elementTypes)
                        : Types.List(Types.Union(elementTypes));
                }

            case Expression.ExpressionKind.DICT_EXPR:
                {
                    var dict = (DictExpression)expr;
                    var keyTypes = new List<StarlarkType>();
                    var valueTypes = new List<StarlarkType>();
                    foreach (var entry in dict.GetEntries())
                    {
                        keyTypes.Add(Infer(entry.GetKey()));
                        valueTypes.Add(Infer(entry.GetValue()));
                    }
                    return Types.Dict(Types.Union(keyTypes), Types.Union(valueTypes));
                }

            case Expression.ExpressionKind.CALL:
                return InferCall((CallExpression)expr);

            case Expression.ExpressionKind.CONDITIONAL:
                {
                    var cond = (ConditionalExpression)expr;
                    return Types.Union(Infer(cond.GetThenCase()), Infer(cond.GetElseCase()));
                }

            case Expression.ExpressionKind.BINARY_OPERATOR:
                {
                    var binop = (BinaryOperatorExpression)expr;
                    StarlarkType xType = Infer(binop.GetX());
                    StarlarkType yType = Infer(binop.GetY());
                    return InferBinaryOperator(
                        binop.GetX(),
                        xType,
                        binop.GetOperator(),
                        binop.GetOperatorLocation(),
                        binop.GetY(),
                        yType,
                        /* augmentedAssignment= */ false);
                }

            case Expression.ExpressionKind.UNARY_OPERATOR:
                {
                    var unop = (UnaryOperatorExpression)expr;
                    if (unop.GetOperator() == TokenKind.NOT)
                    {
                        // NOT always returns a boolean (even if applied to Any or unions).
                        return Types.BOOL;
                    }
                    StarlarkType xType = Infer(unop.GetX());
                    if (xType.Equals(Types.ANY)
                        || ((unop.GetOperator() == TokenKind.MINUS || unop.GetOperator() == TokenKind.PLUS)
                            && StarlarkType.AssignableFrom(Types.NUMERIC, xType))
                        || (unop.GetOperator() == TokenKind.TILDE && xType.Equals(Types.INT)))
                    {
                        // Unary operators other than NOT preserve the type of their operand.
                        return xType;
                    }
                    Errorf(
                        unop.GetStartLocation(),
                        "operator '{0}' cannot be applied to type '{1}'",
                        unop.GetOperator().ToDisplayString(),
                        xType);
                    return Types.ANY;
                }

            case Expression.ExpressionKind.COMPREHENSION:
                return InferComprehension((Comprehension)expr);

            default:
                // TODO: #28037 - support isinstance expressions.
                Errorf(expr, "UNSUPPORTED: cannot typecheck {0} expression", expr.Kind);
                return Types.ANY;
        }
    }

    /// <summary>
    /// Returns the integer value of an expression if it's an integer value (or a unary expression
    /// negating an integer value) which can be exactly represented as a 32-bit integer, or null
    /// otherwise (in particular, if the expression itself is null).
    /// </summary>
    private static int? GetIntValueExact(Expression? expr)
    {
        if (expr is IntLiteral intLiteral)
        {
            return intLiteral.GetIntValueExact();
        }
        else if (expr is UnaryOperatorExpression unop
            && unop.GetOperator() == TokenKind.MINUS
            && unop.GetX() is IntLiteral negatedIntLiteral)
        {
            int? x = negatedIntLiteral.GetIntValueExact();
            if (x != null)
            {
                return -x.Value; // safe since x >= 0
            }
        }
        return null;
    }

    private StarlarkType InferDot(DotExpression dot)
    {
        return Types.Union(InferDotUnfolded(dot, Infer(dot.GetObject())));
    }

    /// <summary>
    /// Infers the non-flattened unfolded list of possible types of a dot expression.
    /// </summary>
    private ImmutableArray<StarlarkType> InferDotUnfolded(DotExpression dot, StarlarkType objType)
    {
        string name = dot.GetField().GetName();

        if (objType.Equals(Types.ANY))
        {
            return ImmutableArray.Create(Types.ANY);
        }

        IReadOnlyCollection<StarlarkType> objElemTypes = Types.UnfoldUnion(objType);
        var resultTypes = ImmutableArray.CreateBuilder<StarlarkType>(objElemTypes.Count);
        foreach (StarlarkType objElemType in objElemTypes)
        {
            StarlarkType? fieldType = objElemType.GetField(name, typeContext);
            if (fieldType == null)
            {
                Errorf(
                    dot.GetDotLocation(),
                    "'{0}' of type '{1}' does not have field '{2}'",
                    dot.GetObject(),
                    objType,
                    name);
                return ImmutableArray.Create(Types.ANY);
            }
            resultTypes.Add(fieldType);
        }
        return resultTypes.ToImmutable();
    }

    private StarlarkType InferIndex(IndexExpression index)
    {
        return Types.Union(
            InferIndexUnfolded(index, Infer(index.GetObject()), Infer(index.GetKey())));
    }

    /// <summary>
    /// Infers the non-flattened unfolded list of possible types of an index expression.
    /// </summary>
    private ImmutableArray<StarlarkType> InferIndexUnfolded(
        IndexExpression index, StarlarkType objType, StarlarkType keyType)
    {
        Expression obj = index.GetObject();
        Expression key = index.GetKey();

        if (objType.Equals(Types.ANY))
        {
            return ImmutableArray.Create(Types.ANY);
        }

        IReadOnlyCollection<StarlarkType> objElemTypes = Types.UnfoldUnion(objType);
        var resultTypes = ImmutableArray.CreateBuilder<StarlarkType>(objElemTypes.Count);
        foreach (StarlarkType objElemType in objElemTypes)
        {
            if (objElemType.Equals(Types.ANY))
            {
                resultTypes.Add(Types.ANY);
            }
            else if (objElemType is Types.FixedLengthTupleType tupleType)
            {
                ErrorIfKeyNotInt(index, objElemType, keyType);
                var elementTypes = tupleType.GetElementTypes();
                StarlarkType? resultType = null;
                // Project out the type of the specific component if we can statically determine the index.
                int? intKey = GetIntValueExact(key);
                if (intKey != null)
                {
                    int i = intKey.Value;
                    if (i < 0)
                    {
                        // Same logic as for EvalUtils#getSequenceIndex.
                        i += elementTypes.Count;
                    }
                    if (0 <= i && i < elementTypes.Count)
                    {
                        resultType = elementTypes[i];
                    }
                    else
                    {
                        Errorf(
                            index.GetLbracketLocation(),
                            "'{0}' of type '{1}' is indexed by integer {2}, which is out-of-range",
                            obj,
                            objType,
                            intKey);
                        // Don't complain about uses of the result type when we don't even know what
                        // result type the user wanted.
                        return ImmutableArray.Create(Types.ANY);
                    }
                }
                if (resultType == null)
                {
                    resultType = tupleType.ToHomogeneous().GetElementType();
                }
                resultTypes.Add(resultType);
            }
            else if (objElemType is Types.AbstractSequenceType sequenceType)
            {
                ErrorIfKeyNotInt(index, objType, keyType); // fall through on error
                resultTypes.Add(sequenceType.GetElementType());
            }
            else if (objElemType is Types.AbstractMappingType mappingType)
            {
                if (!StarlarkType.AssignableFrom(mappingType.GetKeyType(), keyType))
                {
                    Errorf(
                        index.GetLbracketLocation(),
                        "'{0}' of type '{1}' requires key type '{2}', but got '{3}'",
                        obj,
                        objType,
                        mappingType.GetKeyType(),
                        keyType);
                    // Fall through to returning the value type.
                }
                resultTypes.Add(mappingType.GetValueType());
            }
            else if (objElemType.Equals(Types.STR))
            {
                ErrorIfKeyNotInt(index, objType, keyType); // fall through on error
                resultTypes.Add(Types.STR);
            }
            else
            {
                Errorf(index.GetLbracketLocation(), "cannot index '{0}' of type '{1}'", obj, objType);
                return ImmutableArray.Create(Types.ANY);
            }
        }
        return resultTypes.ToImmutable();
    }

    private StarlarkType InferSlice(SliceExpression slice)
    {
        int? step = GetIntValueExact(slice.GetStep());
        if (step == null)
        {
            step = 1;
            if (slice.GetStep() != null)
            {
                StarlarkType stepType = Infer(slice.GetStep()!);
                if (!StarlarkType.AssignableFrom(Types.INT, stepType))
                {
                    Errorf(slice.GetStep()!, "got '{0}' for slice step, want int", stepType);
                    return Types.ANY;
                }
            }
        }
        else if (step == 0)
        {
            Errorf(slice.GetStep()!, "slice step cannot be zero");
            return Types.ANY;
        }
        if (slice.GetStart() != null)
        {
            StarlarkType startType = Infer(slice.GetStart()!);
            if (!StarlarkType.AssignableFrom(Types.INT, startType))
            {
                Errorf(slice.GetStart()!, "got '{0}' for start index, want int", startType);
                return Types.ANY;
            }
        }
        if (slice.GetStop() != null)
        {
            StarlarkType stopType = Infer(slice.GetStop()!);
            if (!StarlarkType.AssignableFrom(Types.INT, stopType))
            {
                Errorf(slice.GetStop()!, "got '{0}' for stop index, want int", stopType);
                return Types.ANY;
            }
        }

        StarlarkType objType = Infer(slice.GetObject());
        if (objType.Equals(Types.ANY))
        {
            return Types.ANY;
        }
        var resultTypes = new List<StarlarkType>();
        foreach (StarlarkType objElemType in Types.UnfoldUnion(objType))
        {
            if (objElemType.Equals(Types.ANY))
            {
                resultTypes.Add(Types.ANY);
            }
            else if (objElemType.Equals(Types.STR))
            {
                resultTypes.Add(Types.STR);
            }
            else if (objElemType is Types.FixedLengthTupleType tupleType)
            {
                IReadOnlyList<StarlarkType> tupleElementTypes = tupleType.GetElementTypes();
                int len = tupleElementTypes.Count;
                int? start = GetIntValueExact(slice.GetStart());
                int? stop = GetIntValueExact(slice.GetStop());
                var resultTupleElementTypes = ImmutableArray.CreateBuilder<StarlarkType>();
                if (step != null
                    && HaveExactSliceBound(slice.GetStart(), start)
                    && HaveExactSliceBound(slice.GetStop(), stop))
                {
                    if (step > 0)
                    {
                        int startClamped = start != null ? SyntaxUtils.ToSliceBound(start.Value, len) : 0;
                        int stopClamped = stop != null ? SyntaxUtils.ToSliceBound(stop.Value, len) : len;
                        for (long i = startClamped; i < stopClamped && (int)i == i; i += step.Value)
                        {
                            resultTupleElementTypes.Add(tupleElementTypes[(int)i]);
                        }
                    }
                    else
                    {
                        int startClamped =
                            start != null ? SyntaxUtils.ToReverseSliceBound(start.Value, len) : len - 1;
                        int stopClamped =
                            stop != null ? SyntaxUtils.ToReverseSliceBound(stop.Value, len) : -1;
                        for (long i = startClamped; i > stopClamped && (int)i == i; i += step.Value)
                        {
                            resultTupleElementTypes.Add(tupleElementTypes[(int)i]);
                        }
                    }
                    resultTypes.Add(Types.Tuple(resultTupleElementTypes.ToImmutable()));
                }
                else
                {
                    resultTypes.Add(tupleType.ToHomogeneous());
                }
            }
            else if (objElemType is Types.AbstractSequenceType sequenceType)
            {
                resultTypes.Add(sequenceType);
            }
            else
            {
                Errorf(
                    slice.GetLbracketLocation(),
                    "invalid slice operand '{0}' of type '{1}', expected Sequence or str",
                    slice.GetObject(),
                    objElemType);
                resultTypes.Add(Types.ANY);
            }
        }
        return Types.Union(resultTypes);
    }

    private static bool HaveExactSliceBound(Expression? expr, int? exprIntValueExact)
    {
        if (expr == null)
        {
            // Bound not specified, so we know its exact value (the default value)
            return true;
        }
        if (exprIntValueExact != null)
        {
            // Bound is specified and is a 32-bit integer literal (or negation)
            return true;
        }
        return false;
    }

    private StarlarkType InferBinaryOperator(
        Expression xExpr,
        StarlarkType xType,
        TokenKind op,
        Location operatorLocation,
        Expression yExpr,
        StarlarkType yType,
        bool augmentedAssignment)
    {
        switch (op)
        {
            case TokenKind.AND:
            case TokenKind.OR:
            case TokenKind.EQUALS_EQUALS:
            case TokenKind.NOT_EQUALS:
                // Boolean regardless of LHS and RHS.
                return Types.BOOL;

            case TokenKind.LESS:
            case TokenKind.LESS_EQUALS:
            case TokenKind.GREATER:
            case TokenKind.GREATER_EQUALS:
                // Boolean or type error.
                if (StarlarkType.Comparable(xType, yType))
                {
                    return Types.BOOL;
                }
                BinaryOperatorError(xType, op, operatorLocation, yType, augmentedAssignment);
                return Types.ANY;

            default:
                {
                    // Take the union of all types inferred by crossing the left and right union
                    // elements (each of which must be a valid combination of rhs and lhs for the
                    // operator).
                    IReadOnlyCollection<StarlarkType> xTypes = Types.UnfoldUnion(xType);
                    IReadOnlyCollection<StarlarkType> yTypes = Types.UnfoldUnion(yType);
                    var resultTypes = new List<StarlarkType>();
                    foreach (StarlarkType xElemType in xTypes)
                    {
                        foreach (StarlarkType yElemType in yTypes)
                        {
                            StarlarkType? resultType = xElemType.InferBinaryOperator(op, yElemType, true);
                            if (resultType == null)
                            {
                                resultType = yElemType.InferBinaryOperator(op, xElemType, false);
                            }
                            if (resultType == null && op == TokenKind.STAR)
                            {
                                // Tuple repetition is the only case where we need to examine the exprs.
                                if (StarlarkType.AssignableFrom(Types.INT, xElemType)
                                    && yElemType is Types.TupleType tupleY)
                                {
                                    resultType = InferTupleRepetition(tupleY, xExpr);
                                }
                                else if (StarlarkType.AssignableFrom(Types.INT, yElemType)
                                    && xElemType is Types.TupleType tupleX)
                                {
                                    resultType = InferTupleRepetition(tupleX, yExpr);
                                }
                            }
                            if (resultType == null)
                            {
                                BinaryOperatorError(xType, op, operatorLocation, yType, augmentedAssignment);
                                return Types.ANY;
                            }
                            resultTypes.Add(resultType);
                        }
                    }
                    return Types.Union(resultTypes);
                }
        }
    }

    private StarlarkType InferCall(CallExpression call)
    {
        // Collect and check the shape of the call's *args/**kwargs.
        VarargsArgument? varargs = null;
        KwargsArgument? kwargs = null;
        int numArgs = call.GetArguments().Count;
        if (numArgs > 0 && call.GetArguments()[numArgs - 1] is Argument.StarStar starStarArg)
        {
            kwargs = KwargsArgument.Of(starStarArg, this);
            if (kwargs == null)
            {
                // error already reported
                return Types.ANY;
            }
            numArgs--;
        }
        if (numArgs > 0 && call.GetArguments()[numArgs - 1] is Argument.Star starArg)
        {
            varargs = VarargsArgument.Of(starArg, this);
            if (varargs == null)
            {
                // error already reported
                return Types.ANY;
            }
            numArgs--;
        }

        StarlarkType callFunctionType = Infer(call.GetFunction());
        if (callFunctionType.Equals(Types.ANY))
        {
            return Types.ANY;
        }

        // Collect call's argument types (excluding *args and **kwargs).
        ImmutableArray<StarlarkType> argTypes =
            call.GetArguments()
                .Take(numArgs)
                .Select(a => a.GetValue())
                .Select(Infer)
                .ToImmutableArray();

        IReadOnlyCollection<StarlarkType> callFunctionTypes = Types.UnfoldUnion(callFunctionType);
        var returnTypes = new List<StarlarkType>();
        foreach (StarlarkType callFunctionElemType in callFunctionTypes)
        {
            if (callFunctionElemType.Equals(Types.ANY))
            {
                // Nothing we can check.
                returnTypes.Add(Types.ANY);
                continue;
            }
            Types.CallableType? callable =
                callFunctionElemType is Types.CallableType c ? c : null;
            if (callable == null)
            {
                Errorf(
                    call.GetFunction(),
                    "'{0}' is not callable; got type '{1}'",
                    call.GetFunction(),
                    callFunctionType);
                return Types.ANY;
            }
            // Indices of residual arguments in call.GetArguments() and their corresponding types in
            // argTypes.
            var residualPositional = new List<int>(0);
            var residualNamed = new List<int>(0);
            // Names of mandatory parameters (both positional and named) having a corresponding arg.
            var seenMandatoryParameters = new List<string>(callable.GetMandatoryParameters().Count);
            for (int i = 0; i < numArgs; i++)
            {
                Argument arg = call.GetArguments()[i];
                int parameterIndex;
                if (i < call.GetNumPositionalArguments())
                {
                    // positional argument
                    if (i < callable.GetNumPositionalParameters())
                    {
                        parameterIndex = i;
                    }
                    else
                    {
                        residualPositional.Add(i);
                        continue;
                    }
                }
                else
                {
                    // keyword argument
                    parameterIndex = IndexOf(callable.GetParameterNames(), arg.GetName());
                    if (parameterIndex < callable.GetNumPositionalOnlyParameters())
                    {
                        // Either no param was found (i<0) or it's positional-only (0<=i<numPosOnly).
                        residualNamed.Add(i);
                        continue;
                    }
                }
                // Argument is not residual; check it against the corresponding parameter.
                string parameterName = callable.GetParameterNames()[parameterIndex];
                StarlarkType parameterType = callable.GetParameterTypeByPos(parameterIndex);
                if (callable.GetMandatoryParameters().Contains(parameterName))
                {
                    seenMandatoryParameters.Add(parameterName);
                }
                if (!StarlarkType.AssignableFrom(parameterType, argTypes[i]))
                {
                    Errorf(
                        call.GetArguments()[i],
                        "in call to '{0}()', parameter '{1}' got value of type '{2}', want '{3}'",
                        call.GetFunction(),
                        parameterName,
                        argTypes[i],
                        parameterType);
                    return Types.ANY;
                }
            }
            if (!CheckCallResidualPositionals(residualPositional, call, callable, argTypes)
                || !CheckCallResidualNamed(residualNamed, call, callable, argTypes))
            {
                return Types.ANY;
            }
            if (!CheckCallMissingMandatoryArgs(
                seenMandatoryParameters,
                /* callHasVarargs= */ varargs != null,
                /* callHasKwargs= */ kwargs != null,
                call,
                callable))
            {
                return Types.ANY;
            }
            if (varargs != null
                && !CheckAssignable(
                    callable.GetVarargsType(),
                    varargs.ElementType,
                    call,
                    varargs.Expr,
                    "elements of argument after *"))
            {
                return Types.ANY;
            }
            if (kwargs != null
                && !CheckAssignable(
                    callable.GetKwargsType(),
                    kwargs.ValueType,
                    call,
                    kwargs.Expr,
                    "values of argument after **"))
            {
                return Types.ANY;
            }
            returnTypes.Add(callable.GetReturnType());
        }
        return Types.Union(returnTypes);
    }

    private static int IndexOf(IReadOnlyList<string> names, string? name)
    {
        if (name == null)
        {
            return -1;
        }
        for (int i = 0; i < names.Count; i++)
        {
            if (names[i].Equals(name, StringComparison.Ordinal))
            {
                return i;
            }
        }
        return -1;
    }

    private sealed class VarargsArgument
    {
        public Expression Expr { get; }
        public StarlarkType ElementType { get; }

        private VarargsArgument(Expression expr, StarlarkType elementType)
        {
            this.Expr = expr;
            this.ElementType = elementType;
        }

        public static VarargsArgument? Of(Argument.Star arg, TypeChecker checker)
        {
            Expression varargs = arg.GetValue();
            StarlarkType varargsType = checker.Infer(varargs);
            StarlarkType? varargsElementType = FindElementType(varargsType);
            if (varargsElementType == null)
            {
                checker.Errorf(varargs, "argument after * must be a sequence, not '{0}'", varargsType);
                return null;
            }
            return new VarargsArgument(varargs, varargsElementType);
        }

        /// <summary>
        /// Finds the smallest <c>Sequence[E]</c> type which is a supertype of the given type, and
        /// return E; or null if the given type does not have such a supertype.
        /// </summary>
        private static StarlarkType? FindElementType(StarlarkType maybeSequence)
        {
            if (maybeSequence.Equals(Types.ANY))
            {
                return Types.ANY;
            }
            IReadOnlyCollection<StarlarkType> unfolded = Types.UnfoldUnion(maybeSequence);
            var elements = new List<StarlarkType>(unfolded.Count);
            foreach (StarlarkType unfoldedElem in unfolded)
            {
                if (unfoldedElem is Types.AbstractSequenceType sequence)
                {
                    elements.Add(sequence.GetElementType());
                }
                else
                {
                    return null;
                }
            }
            return Types.Union(elements);
        }
    }

    private sealed class KwargsArgument
    {
        public Expression Expr { get; }
        public StarlarkType ValueType { get; }

        private KwargsArgument(Expression expr, StarlarkType valueType)
        {
            this.Expr = expr;
            this.ValueType = valueType;
        }

        public static KwargsArgument? Of(Argument.StarStar arg, TypeChecker checker)
        {
            Expression kwargs = arg.GetValue();
            StarlarkType kwargsType = checker.Infer(kwargs);
            StarlarkType? kwargsValueType = FindValueType(kwargsType);
            if (kwargsValueType == null)
            {
                checker.Errorf(
                    kwargs, "argument after ** must be a dict with string keys, not '{0}'", kwargsType);
                return null;
            }
            return new KwargsArgument(kwargs, kwargsValueType);
        }

        /// <summary>
        /// Finds the smallest <c>Mapping[K, V]</c> type which is a supertype of the given type such
        /// that K is a subtype of str, and returns V; or null if the given type does not have such a
        /// supertype.
        /// </summary>
        private static StarlarkType? FindValueType(StarlarkType maybeMapping)
        {
            if (maybeMapping.Equals(Types.ANY))
            {
                return Types.ANY;
            }
            IReadOnlyCollection<StarlarkType> unfolded = Types.UnfoldUnion(maybeMapping);
            var values = new List<StarlarkType>(unfolded.Count);
            foreach (StarlarkType unfoldedElem in unfolded)
            {
                if (unfoldedElem is Types.AbstractMappingType mapping
                    && StarlarkType.AssignableFrom(Types.STR, mapping.GetKeyType()))
                {
                    values.Add(mapping.GetValueType());
                }
                else
                {
                    return null;
                }
            }
            return Types.Union(values);
        }
    }

    /// <summary>
    /// Returns true if the call's residual positional arguments (if any) satisfy the type checker.
    /// Otherwise, reports an error and returns false.
    /// </summary>
    private bool CheckCallResidualPositionals(
        List<int> residualPositional,
        CallExpression call,
        Types.CallableType callable,
        IReadOnlyList<StarlarkType> argTypes)
    {
        if (residualPositional.Count == 0)
        {
            return true;
        }
        else if (callable.GetVarargsType() == null)
        {
            // callable cannot accept residual positional args
            if (callable.GetNumPositionalParameters() > 0)
            {
                Errorf(
                    call.GetArguments()[callable.GetNumPositionalParameters()],
                    "'{0}()' accepts no more than {1} positional argument{2} but got {3}",
                    call.GetFunction(),
                    callable.GetNumPositionalParameters(),
                    Plural(callable.GetNumPositionalParameters()),
                    call.GetNumPositionalArguments());
            }
            else
            {
                Errorf(
                    call.GetArguments()[0],
                    "'{0}()' does not accept positional arguments, but got {1}",
                    call.GetFunction(),
                    call.GetNumPositionalArguments());
            }
            return false;
        }
        else
        {
            // residual positional args go into callable's varargs
            foreach (int argIndex in residualPositional)
            {
                Argument arg = call.GetArguments()[argIndex];
                StarlarkType argType = argTypes[argIndex];
                if (!CheckAssignable(
                    callable.GetVarargsType(), argType, call, arg, "residual positional arguments"))
                {
                    return false;
                }
            }
        }
        return true;
    }

    /// <summary>
    /// Returns true if the call's residual named arguments (if any) satisfy the type checker.
    /// Otherwise, reports an error and returns false.
    /// </summary>
    private bool CheckCallResidualNamed(
        List<int> residualNamed,
        CallExpression call,
        Types.CallableType callable,
        IReadOnlyList<StarlarkType> argTypes)
    {
        if (residualNamed.Count == 0)
        {
            return true;
        }
        else if (callable.GetKwargsType() == null)
        {
            // callable cannot accept residual named args
            var residualNamedArgs =
                residualNamed.Select(i => call.GetArguments()[i]).ToList();
            Errorf(
                residualNamedArgs[0],
                "'{0}()' got unexpected keyword argument{1}: {2}{3}",
                call.GetFunction(),
                Plural(residualNamedArgs.Count),
                string.Join(", ", residualNamedArgs.Select(a => a.GetName())),
                // If there are multiple residual named args, it's likely due to calling the wrong
                // function or misunderstanding the API, so arg spelling suggestions would not help.
                residualNamedArgs.Count == 1
                    ? SpellChecker.DidYouMean(
                        residualNamedArgs[0].GetName()!,
                        Sublist(
                            callable.GetParameterNames(),
                            callable.GetNumPositionalOnlyParameters(),
                            callable.GetParameterNames().Count))
                    : "");
            return false;
        }
        else
        {
            // residual named args go into callable's kwargs
            foreach (int argIndex in residualNamed)
            {
                Argument arg = call.GetArguments()[argIndex];
                StarlarkType argType = argTypes[argIndex];
                if (!CheckAssignable(
                    callable.GetKwargsType(), argType, call, arg, "residual keyword arguments"))
                {
                    return false;
                }
            }
        }
        return true;
    }

    private static IReadOnlyList<string> Sublist(IReadOnlyList<string> list, int fromInclusive, int toExclusive)
    {
        var result = new List<string>(toExclusive - fromInclusive);
        for (int i = fromInclusive; i < toExclusive; i++)
        {
            result.Add(list[i]);
        }
        return result;
    }

    /// <summary>
    /// Returns true if all mandatory parameters were explicitly supplied by the call or potentially
    /// supplied through *args or **kwargs. Otherwise, reports an error and returns false.
    /// </summary>
    private bool CheckCallMissingMandatoryArgs(
        List<string> seenMandatoryParameters,
        bool callHasVarargs,
        bool callHasKwargs,
        CallExpression call,
        Types.CallableType callable)
    {
        if (seenMandatoryParameters.Count < callable.GetMandatoryParameters().Count)
        {
            var seenMandatorySet = seenMandatoryParameters.ToHashSet();
            // Identify mandatory parameters which were not seen and which cannot be possibly supplied
            // from the call's *args or **kwargs.
            var missingMandatory = new List<string>(0);
            for (int i = 0; i < callable.GetParameterNames().Count; i++)
            {
                string name = callable.GetParameterNames()[i];
                if (!seenMandatorySet.Contains(name))
                {
                    if (i < callable.GetNumPositionalOnlyParameters() && !callHasVarargs)
                    {
                        missingMandatory.Add(name);
                    }
                    else if (i < callable.GetNumPositionalParameters()
                        && !callHasVarargs
                        && !callHasKwargs)
                    {
                        missingMandatory.Add(name);
                    }
                    else if (i >= callable.GetNumPositionalParameters() && !callHasKwargs)
                    {
                        missingMandatory.Add(name);
                    }
                }
            }
            if (missingMandatory.Count != 0)
            {
                Errorf(
                    call.GetLparenLocation(),
                    "'{0}()' missing {1} required argument{2}: {3}",
                    call.GetFunction(),
                    missingMandatory.Count,
                    Plural(missingMandatory.Count),
                    string.Join(", ", missingMandatory));
                return false;
            }
        }
        return true;
    }

    private StarlarkType InferComprehension(Comprehension comp)
    {
        foreach (Comprehension.Clause clause in comp.GetClauses())
        {
            switch (clause)
            {
                case Comprehension.For forClause:
                    CheckForClause(
                        forClause.GetVars(), forClause.GetIterable(), "comprehension 'for' clause");
                    break;
                case Comprehension.If ifClause:
                    // Infer only to type-check. Condition is evaluated as truthy/falsy, which is
                    // valid for every type.
                    var unused = Infer(ifClause.GetCondition());
                    break;
            }
        }
        if (comp.IsDict())
        {
            var bodyEntry = (DictExpression.Entry)comp.GetBody();
            return Types.Dict(Infer(bodyEntry.GetKey()), Infer(bodyEntry.GetValue()));
        }
        else
        {
            var bodyElement = (Expression)comp.GetBody();
            return Types.List(Infer(bodyElement));
        }
    }

    /// <summary>Recursively type-checks the vars and the iterable, and assigns the vars to the iterable.</summary>
    private void CheckForClause(Expression vars, Expression iterable, string what)
    {
        StarlarkType iterableType = Infer(iterable);
        StarlarkType varsRhsType; // The type of the value assigned to the vars expression.
        if (iterableType.Equals(Types.ANY))
        {
            varsRhsType = Types.ANY;
        }
        else
        {
            var varUnionElements = new List<StarlarkType>();
            foreach (StarlarkType iterableUnionElement in Types.UnfoldUnion(iterableType))
            {
                if (iterableUnionElement.Equals(Types.ANY))
                {
                    varUnionElements.Add(Types.ANY);
                }
                else if (iterableUnionElement is Types.AbstractCollectionType collection)
                {
                    varUnionElements.Add(collection.GetElementType());
                }
                else
                {
                    Errorf(iterable, "{0} operand must be an iterable, got '{1}'", what, iterableType);
                }
            }
            varsRhsType = Types.Union(varUnionElements);
        }
        Assign(vars, varsRhsType);
    }

    private bool CheckAssignable(
        StarlarkType? lhs,
        StarlarkType? rhs,
        CallExpression call,
        Node node,
        string nodeDescription)
    {
        if (lhs != null && rhs != null)
        {
            if (!StarlarkType.AssignableFrom(lhs, rhs))
            {
                Errorf(
                    node,
                    "in call to '{0}()', {1} must be '{2}', not '{3}'",
                    call.GetFunction(),
                    nodeDescription,
                    lhs,
                    rhs);
                return false;
            }
        }
        return true;
    }

    private static StarlarkType InferTupleRepetition(Types.TupleType tuple, Expression timesExpr)
    {
        int? times = GetIntValueExact(timesExpr);
        if (times != null)
        {
            return tuple.Repeat(times.Value);
        }
        return tuple.ToHomogeneous();
    }

    /// <summary>
    /// Returns the inferred type of an expression.
    ///
    /// <para>The expression must have already been resolved and type-tagged.</para>
    /// </summary>
    /// <exception cref="SyntaxError.Exception">if a static type error is present in the expression.</exception>
    public static StarlarkType InferTypeOf(Expression expr, TypeContext typeContext)
    {
        var errors = new List<SyntaxError>();
        var tc = new TypeChecker(errors, typeContext);
        StarlarkType result = tc.Infer(expr);
        if (errors.Count != 0)
        {
            throw new SyntaxError.Exception(tc.errors);
        }
        return result;
    }

    /// <summary>
    /// Recursively typechecks the assignment of type <paramref name="rhsType"/> to the target
    /// expression <paramref name="lhs"/>.
    /// </summary>
    private void Assign(Expression lhs, StarlarkType rhsType)
    {
        CheckState(UsesTypeSyntax());

        if (lhs.Kind == Expression.ExpressionKind.LIST_EXPR)
        {
            AssignSequence((ListExpression)lhs, rhsType);
            return;
        }

        ImmutableArray<StarlarkType> lhsMeet = InferIndividualAssignmentTarget(lhs);
        foreach (StarlarkType lhsType in lhsMeet)
        {
            if (!StarlarkType.AssignableFrom(lhsType, rhsType))
            {
                Errorf(lhs, "cannot assign type '{0}' to {1}", rhsType, FormatExprWithMeetType(lhs, lhsMeet));
                break;
            }
        }

        if (lhs is Identifier id && id.GetBinding()!.GetType() == null)
        {
            // If a variable has not been typed, infer its type from the rhs of the 1st assignment.
            id.GetBinding()!.SetType(rhsType);
        }
    }

    private static string FormatExprWithMeetType(Expression expr, ImmutableArray<StarlarkType> types)
    {
        if (types.Length == 1)
        {
            return string.Format("'{0}' of type '{1}'", expr, types[0]);
        }
        else
        {
            return string.Format(
                "'{0}' which expects a value satisfying all of the {1} types [{2}]",
                expr,
                types.Length,
                string.Join(", ", types.Select(t => string.Format("'{0}'", t))));
        }
    }

    /// <summary>
    /// Verifies that the expression can be used as the target of a non-sequence assignment (or
    /// augmented assignment). Returns a non-flattened unfolded list of LHS acceptor types.
    /// </summary>
    private ImmutableArray<StarlarkType> InferIndividualAssignmentTarget(Expression lhs)
    {
        switch (lhs.Kind)
        {
            case Expression.ExpressionKind.INDEX:
                {
                    var indexExpr = (IndexExpression)lhs;
                    StarlarkType objectType = Infer(indexExpr.GetObject());
                    StarlarkType keyType = Infer(indexExpr.GetKey());
                    if (!objectType.HasSetIndex())
                    {
                        Errorf(
                            lhs,
                            "{0} of type '{1}' does not support item assignment",
                            indexExpr.GetObject(),
                            objectType);
                    }
                    return InferIndexUnfolded(indexExpr, objectType, keyType);
                }

            case Expression.ExpressionKind.DOT:
                {
                    var dotExpr = (DotExpression)lhs;
                    StarlarkType objectType = Infer(dotExpr.GetObject());
                    if (!objectType.HasSetField())
                    {
                        Errorf(
                            lhs,
                            "{0} of type '{1}' does not support field assignment",
                            dotExpr.GetObject(),
                            objectType);
                    }
                    return InferDotUnfolded(dotExpr, objectType);
                }

            case Expression.ExpressionKind.IDENTIFIER:
                return ImmutableArray.Create(Infer(lhs));

            default:
                {
                    StarlarkType lhsType = Infer(lhs);
                    Errorf(lhs, "{0} of type '{1}' is not a valid target for assignment", lhs, lhsType);
                    return ImmutableArray.Create(Types.ANY);
                }
        }
    }

    private void AssignSequence(ListExpression lhs, StarlarkType rhsType)
    {
        if (rhsType.Equals(Types.ANY))
        {
            foreach (Expression element in lhs.GetElements())
            {
                Assign(element, Types.ANY);
            }
            return;
        }

        // We effectively need to transform what may be a union of iterables into a fixed-length
        // tuple of unions.
        IReadOnlyCollection<StarlarkType> rhsUnionElements = Types.UnfoldUnion(rhsType);
        foreach (StarlarkType rhsUnionElement in rhsUnionElements)
        {
            if (rhsUnionElement is Types.FixedLengthTupleType rhsTuple)
            {
                if (lhs.GetElements().Count != rhsTuple.GetElementTypes().Count)
                {
                    Errorf(
                        lhs,
                        "cannot assign type '{0}' to '{1}'; want {2}-element sequence",
                        rhsType,
                        lhs,
                        lhs.GetElements().Count);
                    return;
                }
            }
            else if (!rhsUnionElement.Equals(Types.ANY)
                && rhsUnionElement is not Types.AbstractCollectionType)
            {
                Errorf(lhs, "cannot assign non-iterable type '{0}' to '{1}'", rhsType, lhs);
                return;
            }
        }
        for (int i = 0; i < lhs.GetElements().Count; i++)
        {
            var rhsElementTypes = new List<StarlarkType>(rhsUnionElements.Count);
            foreach (StarlarkType rhsUnionElement in rhsUnionElements)
            {
                if (rhsUnionElement is Types.FixedLengthTupleType rhsTuple)
                {
                    rhsElementTypes.Add(rhsTuple.GetElementTypes()[i]);
                }
                else if (rhsUnionElement is Types.AbstractCollectionType rhsCollection)
                {
                    rhsElementTypes.Add(rhsCollection.GetElementType());
                }
                else if (rhsUnionElement.Equals(Types.ANY))
                {
                    rhsElementTypes.Add(Types.ANY);
                }
            }
            Assign(lhs.GetElements()[i], Types.Union(rhsElementTypes));
        }
    }

    public override void Visit(StarlarkFile file)
    {
        CheckState(
            functionStack.Count == 0,
            "When type-checking a StarlarkFile, functionStack is expected to be initially empty");
        Resolver.Function toplevel = file.GetResolvedFunction()!;
        Push(toplevel);
        base.Visit(file);
        CheckState(Pop().Equals(toplevel));
    }

    // Expressions should only be visited via infer(), not the visit() dispatch mechanism.
    // Override Visit(Identifier) as a poison pill.
    public override void Visit(Identifier id)
    {
        throw new InvalidOperationException(
            string.Format(
                "TypeChecker.Visit should not have reached Identifier node '{0}'", id.GetName()));
    }

    public override void Visit(AssignmentStatement assignment)
    {
        if (!UsesTypeSyntax())
        {
            return;
        }

        if (assignment.IsAugmented())
        {
            TokenKind op = assignment.GetOperator()!.Value;
            Location operatorLocation = assignment.GetOperatorLocation();
            Expression lhs = assignment.GetLHS();
            Expression rhs = assignment.GetRHS();
            ImmutableArray<StarlarkType> lhsMeet = InferIndividualAssignmentTarget(lhs);
            StarlarkType rhsType = Infer(assignment.GetRHS());
            foreach (StarlarkType lhsType in lhsMeet)
            {
                StarlarkType resultType =
                    InferBinaryOperator(
                        lhs,
                        lhsType,
                        op,
                        operatorLocation,
                        rhs,
                        rhsType,
                        /* augmentedAssignment= */ true);
                if (!StarlarkType.AssignableFrom(lhsType, resultType))
                {
                    BinaryOperatorError(
                        lhsType,
                        op,
                        operatorLocation,
                        rhsType,
                        /* augmentedAssignment= */ true,
                        string.Format(
                            "cannot update {0} with a result value of type '{1}'",
                            FormatExprWithMeetType(lhs, lhsMeet),
                            resultType));
                }
            }
        }
        else
        {
            var rhsType = Infer(assignment.GetRHS());
            Assign(assignment.GetLHS(), rhsType);
        }
    }

    public override void Visit(ForStatement node)
    {
        if (UsesTypeSyntax())
        {
            CheckForClause(node.GetVars(), node.GetCollection(), "'for' loop");
        }
        // Visit the for loop body even in untyped code; it may contain nested typed def statements.
        VisitBlock(node.GetBody());
    }

    public override void Visit(DefStatement def)
    {
        Resolver.Function function = def.GetResolvedFunction()!;
        Push(function);
        if (function.UsesTypeSyntax())
        {
            Types.CallableType callableType = CheckNotNull(function.GetFunctionType());
            int numOrdinaryParams = callableType.GetParameterTypes().Count;
            for (int i = 0; i < numOrdinaryParams; i++)
            {
                Parameter param = def.GetParameters()[i];
                if (param.GetDefaultValue() != null)
                {
                    StarlarkType defaultValueType = Infer(param.GetDefaultValue()!);
                    if (!StarlarkType.AssignableFrom(
                        callableType.GetParameterTypeByPos(i), defaultValueType))
                    {
                        Errorf(
                            param.GetDefaultValue()!.GetStartLocation(),
                            "{0}(): parameter '{1}' has default value of type '{2}', declares '{3}'",
                            def.GetIdentifier().GetName(),
                            param.GetName(),
                            defaultValueType,
                            callableType.GetParameterTypeByPos(i));
                    }
                }
            }

            Statement? implicitNoneReturn = GetImplicitNoneReturn(def.GetBody());
            if (implicitNoneReturn != null
                && !StarlarkType.AssignableFrom(callableType.GetReturnType(), Types.NONE))
            {
                Errorf(
                    implicitNoneReturn,
                    "{0}() declares return type '{1}' but may exit without an explicit 'return'",
                    def.GetIdentifier().GetName(),
                    callableType.GetReturnType());
            }
        }

        // Visit body even in untyped code; it may contain nested typed def statements.
        VisitBlock(def.GetBody());
        CheckState(Poll() == function);
    }

    public override void Visit(IfStatement node)
    {
        if (UsesTypeSyntax())
        {
            // Check type constraints in the condition.
            Infer(node.GetCondition());
        }
        // Visit then/else blocks even in untyped code; they may contain nested typed def statements.
        VisitBlock(node.GetThenBlock());
        if (node.GetElseBlock() != null)
        {
            VisitBlock(node.GetElseBlock()!);
        }
    }

    public override void Visit(ExpressionStatement expr)
    {
        if (!UsesTypeSyntax())
        {
            return;
        }
        // Check constraints in the expression, but ignore the resulting type.
        // Don't dispatch to it via visit().
        Infer(expr.GetExpression());
    }

    // No need to override visit() for FlowStatement.

    public override void Visit(LoadStatement load)
    {
        // Don't descend into children.
    }

    public override void Visit(ReturnStatement ret)
    {
        if (!UsesTypeSyntax())
        {
            return;
        }
        StarlarkType returnType = ret.GetResult() == null ? Types.NONE : Infer(ret.GetResult()!);
        CheckState(functionStack.Count != 0);
        Resolver.Function function = Peek();
        Types.CallableType callableType = function.GetFunctionType()!;
        if (!StarlarkType.AssignableFrom(callableType.GetReturnType(), returnType))
        {
            Errorf(
                ret.GetResult()!.GetStartLocation(),
                "{0}() declares return type '{1}' but may return '{2}'",
                function.GetName(),
                callableType.GetReturnType(),
                returnType);
        }
    }

    public override void Visit(TypeAliasStatement alias)
    {
        // Don't descend into children.
    }

    public override void Visit(VarStatement var)
    {
        // Don't descend into children.
    }

    /// <summary>
    /// Heuristically checks whether a function body ends with an implicit <c>None</c> return, i.e. a
    /// non-return statement, and if so, retrieves the statement after which the implicit <c>None</c>
    /// return occurs. Recurses into if statement bodies.
    /// </summary>
    private static Statement? GetImplicitNoneReturn(IReadOnlyList<Statement> body)
    {
        Statement last = body[body.Count - 1];
        if (last is ReturnStatement)
        {
            return null;
        }
        else if (last is IfStatement ifStmt)
        {
            // An if statement is considered to have an explicit return if it has both `then` and
            // `else` branches, and both branches end with an explicit return.
            if (ifStmt.GetElseBlock() == null)
            {
                return ifStmt;
            }
            Statement? thenImplicitNoneReturn = GetImplicitNoneReturn(ifStmt.GetThenBlock());
            return thenImplicitNoneReturn != null
                ? thenImplicitNoneReturn
                : GetImplicitNoneReturn(ifStmt.GetElseBlock()!);
        }
        return last;
    }

    /// <summary>
    /// Returns true if the current function is considered to use type syntax, or if we were invoked
    /// via <see cref="InferTypeOf"/>. If false, the current node must not be type-checked.
    /// </summary>
    private bool UsesTypeSyntax()
    {
        return functionStack.Count == 0 || Peek().UsesTypeSyntax();
    }

    /// <summary>
    /// Checks that the given file's AST satisfies the types in the bindings of its identifiers.
    /// </summary>
    public static void CheckFile(StarlarkFile file, TypeContext typeContext)
    {
        var checker = new TypeChecker(file.errors, typeContext);
        checker.Visit(file);
    }

    // ==== helpers ====

    private static void CheckState(bool cond)
    {
        if (!cond)
        {
            throw new InvalidOperationException();
        }
    }

    private static void CheckState(bool cond, string message)
    {
        if (!cond)
        {
            throw new InvalidOperationException(message);
        }
    }

    private static T CheckNotNull<T>(T? value) where T : class
    {
        return value ?? throw new ArgumentNullException(nameof(value));
    }

    // ==== Stack helpers (top == last element, matching Java ArrayDeque push/peek/pop/poll) ====

    private void Push(Resolver.Function f) => functionStack.Add(f);

    private Resolver.Function Peek() => functionStack[functionStack.Count - 1];

    private Resolver.Function Pop()
    {
        Resolver.Function f = functionStack[functionStack.Count - 1];
        functionStack.RemoveAt(functionStack.Count - 1);
        return f;
    }

    private Resolver.Function Poll()
    {
        // Java ArrayDeque.poll() retrieves and removes the head (top). Returns null if empty, but
        // it's never empty at the call site.
        return Pop();
    }
}
