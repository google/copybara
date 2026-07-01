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

using System.Numerics;
using Starlark.Syntax;

namespace Starlark.Eval;

/// <summary>
/// The tree-walking evaluator for Starlark statements and expressions. Port of
/// <c>net.starlark.java.eval.Eval</c>.
/// </summary>
internal static class Eval
{
    // Control flow signal returned by statement execution.
    private const TokenKind PASS = TokenKind.PASS;

    /// <summary>
    /// Executes the body of a StarlarkFunction. The frame at depth 0 is the callable's frame (pushed
    /// by <see cref="Starlark.Fastcall"/>); its locals are installed here.
    /// </summary>
    internal static object? ExecFunctionBody(
        StarlarkThread thread, StarlarkFunction fn, object?[] locals, IReadOnlyList<Statement> statements)
    {
        StarlarkThread.Frame fr = thread.FrameAt(0);
        fr.Locals = locals;
        fr.Result = Starlark.None;
        ExecStatements(thread, fr, fn, statements);
        return fr.Result;
    }

    private static StarlarkFunction Fn(StarlarkThread.Frame fr) => (StarlarkFunction)fr.Fn;

    private static TokenKind ExecStatements(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn,
        IReadOnlyList<Statement> statements)
    {
        for (int i = 0; i < statements.Count; i++)
        {
            TokenKind flow = Exec(thread, fr, fn, statements[i]);
            if (flow != PASS)
            {
                return flow;
            }
        }
        return PASS;
    }

    private static TokenKind Exec(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn, Statement st)
    {
        if (++thread.steps >= thread.stepLimit)
        {
            throw new EvalException("Starlark computation cancelled: too many steps");
        }

        switch (st.Kind)
        {
            case Statement.StatementKind.ASSIGNMENT:
                ExecAssignment(thread, fr, fn, (AssignmentStatement)st);
                return PASS;
            case Statement.StatementKind.EXPRESSION:
                Eval_(thread, fr, fn, ((ExpressionStatement)st).GetExpression());
                return PASS;
            case Statement.StatementKind.FLOW:
                return ((FlowStatement)st).GetFlowKind();
            case Statement.StatementKind.FOR:
                return ExecFor(thread, fr, fn, (ForStatement)st);
            case Statement.StatementKind.DEF:
                {
                    var def = (DefStatement)st;
                    StarlarkFunction newFn = NewFunction(thread, fr, fn, def.GetResolvedFunction()!);
                    AssignIdentifier(fr, fn, def.GetIdentifier(), newFn);
                    return PASS;
                }
            case Statement.StatementKind.IF:
                return ExecIf(thread, fr, fn, (IfStatement)st);
            case Statement.StatementKind.LOAD:
                ExecLoad(thread, fr, fn, (LoadStatement)st);
                return PASS;
            case Statement.StatementKind.RETURN:
                return ExecReturn(thread, fr, fn, (ReturnStatement)st);
            case Statement.StatementKind.TYPE_ALIAS:
            case Statement.StatementKind.VAR:
                return PASS;
        }
        throw new ArgumentException("unexpected statement: " + st.Kind);
    }

    private static void ExecAssignment(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn, AssignmentStatement node)
    {
        try
        {
            if (node.IsAugmented())
            {
                ExecAugmentedAssignment(thread, fr, fn, node);
            }
            else
            {
                object? rvalue = Eval_(thread, fr, fn, node.GetRHS());
                Assign(thread, fr, fn, node.GetLHS(), rvalue);
            }
        }
        catch (EvalException)
        {
            fr.SetErrorLocation(node.GetOperatorLocation());
            throw;
        }
    }

    private static TokenKind ExecFor(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn, ForStatement node)
    {
        object? seqObj = Eval_(thread, fr, fn, node.GetCollection());
        IEnumerable<object?> seq;
        try
        {
            seq = Starlark.ToIterable(seqObj);
        }
        catch (EvalException)
        {
            fr.SetErrorLocation(node.GetCollection().GetStartLocation());
            throw;
        }
        EvalUtils.AddIterator(seqObj);
        try
        {
            foreach (object? item in seq)
            {
                Assign(thread, fr, fn, node.GetVars(), item);
                switch (ExecStatements(thread, fr, fn, node.GetBody()))
                {
                    case TokenKind.PASS:
                    case TokenKind.CONTINUE:
                        continue;
                    case TokenKind.BREAK:
                        return PASS;
                    case TokenKind.RETURN:
                        return TokenKind.RETURN;
                    default:
                        throw new InvalidOperationException("unreachable");
                }
            }
        }
        catch (EvalException)
        {
            fr.SetErrorLocation(node.GetStartLocation());
            throw;
        }
        finally
        {
            EvalUtils.RemoveIterator(seqObj);
        }
        return PASS;
    }

    private static TokenKind ExecIf(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn, IfStatement node)
    {
        bool cond = Starlark.Truth(Eval_(thread, fr, fn, node.GetCondition()));
        if (cond)
        {
            return ExecStatements(thread, fr, fn, node.GetThenBlock());
        }
        if (node.GetElseBlock() != null)
        {
            return ExecStatements(thread, fr, fn, node.GetElseBlock()!);
        }
        return PASS;
    }

    private static TokenKind ExecReturn(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn, ReturnStatement node)
    {
        Expression? result = node.GetResult();
        if (result != null)
        {
            fr.Result = Eval_(thread, fr, fn, result);
        }
        return TokenKind.RETURN;
    }

    private static void ExecLoad(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn, LoadStatement node)
    {
        StarlarkThread.Loader? loader = thread.GetLoader();
        if (loader == null)
        {
            fr.SetErrorLocation(node.GetStartLocation());
            throw Starlark.Errorf("load statements may not be executed in this thread");
        }
        string moduleName = node.GetImport().GetValue();
        Module? module = loader(moduleName);
        if (module == null)
        {
            fr.SetErrorLocation(node.GetStartLocation());
            throw Starlark.Errorf("module '{0}' not found", moduleName);
        }
        foreach (LoadStatement.Binding binding in node.GetBindings())
        {
            Identifier orig = binding.GetOriginalName();
            object? value = module.GetGlobal(orig.GetName());
            if (value == null)
            {
                fr.SetErrorLocation(orig.GetStartLocation());
                throw Starlark.Errorf(
                    "file '{0}' does not contain symbol '{1}'", moduleName, orig.GetName());
            }
            AssignIdentifier(fr, fn, binding.GetLocalName(), value);
        }
    }

    // ---- Assignment ----

    private static void Assign(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn, Expression lhs, object? value)
    {
        switch (lhs)
        {
            case Identifier ident:
                AssignIdentifier(fr, fn, ident, value);
                break;
            case IndexExpression index:
                {
                    object? obj = Eval_(thread, fr, fn, index.GetObject());
                    object? key = Eval_(thread, fr, fn, index.GetKey());
                    EvalUtils.SetIndex(obj, key, value);
                    break;
                }
            case ListExpression list:
                AssignSequence(thread, fr, fn, list.GetElements(), value);
                break;
            case DotExpression dot:
                {
                    object? obj = Eval_(thread, fr, fn, dot.GetObject());
                    string field = dot.GetField().GetName();
                    try
                    {
                        EvalUtils.SetField(obj, field, value!);
                    }
                    catch (EvalException)
                    {
                        fr.SetErrorLocation(dot.GetDotLocation());
                        throw;
                    }
                    break;
                }
            default:
                throw Starlark.Errorf("cannot assign to '{0}'", lhs);
        }
    }

    private static void AssignIdentifier(
        StarlarkThread.Frame fr, StarlarkFunction fn, Identifier id, object? value)
    {
        Resolver.Binding bind = id.GetBinding()!;
        switch (bind.GetScope())
        {
            case Resolver.Scope.LOCAL:
                fr.Locals![bind.GetIndex()] = value;
                break;
            case Resolver.Scope.CELL:
                ((StarlarkFunction.Cell)fr.Locals![bind.GetIndex()]!).X = value;
                break;
            case Resolver.Scope.GLOBAL:
                fn.SetGlobal(bind.GetIndex(), value!);
                break;
            default:
                throw new InvalidOperationException(bind.GetScope().ToString());
        }
    }

    private static void AssignSequence(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn,
        IReadOnlyList<Expression> lhs, object? x)
    {
        int nrhs = Starlark.Len(x);
        int nlhs = lhs.Count;
        if (nrhs < 0 || x is string)
        {
            throw Starlark.Errorf(
                "got '{0}' in sequence assignment (want {1}-element sequence)", Starlark.Type(x), nlhs);
        }
        object?[] rhs = Starlark.ToArray(x);
        if (nlhs != nrhs)
        {
            throw Starlark.Errorf(
                "too {0} values to unpack (got {1}, want {2})", nrhs < nlhs ? "few" : "many", nrhs, nlhs);
        }
        for (int i = 0; i < nlhs; i++)
        {
            Assign(thread, fr, fn, lhs[i], rhs[i]);
        }
    }

    private static void ExecAugmentedAssignment(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn, AssignmentStatement stmt)
    {
        Expression lhs = stmt.GetLHS();
        TokenKind op = stmt.GetOperator()!.Value;
        Expression rhs = stmt.GetRHS();

        if (lhs is Identifier ident)
        {
            object? x = Eval_(thread, fr, fn, lhs);
            object? y = Eval_(thread, fr, fn, rhs);
            object? z;
            try
            {
                z = InplaceBinaryOp(thread, op, x, y);
            }
            catch (EvalException)
            {
                fr.SetErrorLocation(stmt.GetOperatorLocation());
                throw;
            }
            AssignIdentifier(fr, fn, ident, z);
        }
        else if (lhs is IndexExpression index)
        {
            object? obj = Eval_(thread, fr, fn, index.GetObject());
            object? key = Eval_(thread, fr, fn, index.GetKey());
            object? x = EvalUtils.Index(thread, obj, key!);
            object? y = Eval_(thread, fr, fn, rhs);
            object? z;
            try
            {
                z = InplaceBinaryOp(thread, op, x, y);
                EvalUtils.SetIndex(obj, key, z);
            }
            catch (EvalException)
            {
                fr.SetErrorLocation(stmt.GetOperatorLocation());
                throw;
            }
        }
        else if (lhs is DotExpression dot)
        {
            object? obj = Eval_(thread, fr, fn, dot.GetObject());
            string field = dot.GetField().GetName();
            try
            {
                object? x = Starlark.GetAttr(thread, obj, field, null);
                object? y = Eval_(thread, fr, fn, rhs);
                object? z = InplaceBinaryOp(thread, op, x, y);
                EvalUtils.SetField(obj, field, z!);
            }
            catch (EvalException)
            {
                fr.SetErrorLocation(dot.GetDotLocation());
                throw;
            }
        }
        else
        {
            fr.SetErrorLocation(stmt.GetOperatorLocation());
            throw Starlark.Errorf("cannot perform augmented assignment on '{0}'", lhs);
        }
    }

    private static object? InplaceBinaryOp(StarlarkThread thread, TokenKind op, object? x, object? y)
    {
        if (op == TokenKind.PLUS && x is StarlarkList xList && y is StarlarkList yList)
        {
            xList.Extend(yList);
            return xList;
        }
        if (op == TokenKind.PIPE && x is Dict xDict && y is Dict yDict)
        {
            xDict.PutEntries(yDict.Entries);
            return xDict;
        }
        return EvalUtils.BinaryOp(op, x, y, thread);
    }

    // ---- Expressions ----

    private static object? Eval_(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn, Expression expr)
    {
        if (++thread.steps >= thread.stepLimit)
        {
            throw new EvalException("Starlark computation cancelled: too many steps");
        }

        switch (expr.Kind)
        {
            case Expression.ExpressionKind.BINARY_OPERATOR:
                return EvalBinaryOperator(thread, fr, fn, (BinaryOperatorExpression)expr);
            case Expression.ExpressionKind.COMPREHENSION:
                return EvalComprehension(thread, fr, fn, (Comprehension)expr);
            case Expression.ExpressionKind.CONDITIONAL:
                {
                    var cond = (ConditionalExpression)expr;
                    object? v = Eval_(thread, fr, fn, cond.GetCondition());
                    return Eval_(thread, fr, fn, Starlark.Truth(v) ? cond.GetThenCase() : cond.GetElseCase());
                }
            case Expression.ExpressionKind.DICT_EXPR:
                return EvalDict(thread, fr, fn, (DictExpression)expr);
            case Expression.ExpressionKind.DOT:
                return EvalDot(thread, fr, fn, (DotExpression)expr);
            case Expression.ExpressionKind.CALL:
                return EvalCall(thread, fr, fn, (CallExpression)expr);
            case Expression.ExpressionKind.CAST:
                return Eval_(thread, fr, fn, ((CastExpression)expr).GetValue());
            case Expression.ExpressionKind.ISINSTANCE:
                fr.SetErrorLocation(expr.GetStartLocation());
                throw new EvalException("isinstance() is not yet supported");
            case Expression.ExpressionKind.IDENTIFIER:
                return EvalIdentifier(thread, fr, fn, (Identifier)expr);
            case Expression.ExpressionKind.INDEX:
                return EvalIndex(thread, fr, fn, (IndexExpression)expr);
            case Expression.ExpressionKind.INT_LITERAL:
                {
                    object n = ((IntLiteral)expr).GetValue();
                    return n switch
                    {
                        int i => StarlarkInt.Of(i),
                        long l => StarlarkInt.Of(l),
                        BigInteger b => StarlarkInt.Of(b),
                        _ => throw new InvalidOperationException("bad int literal"),
                    };
                }
            case Expression.ExpressionKind.FLOAT_LITERAL:
                return StarlarkFloat.Of(((FloatLiteral)expr).GetValue());
            case Expression.ExpressionKind.LAMBDA:
                return NewFunction(thread, fr, fn, ((LambdaExpression)expr).GetResolvedFunction()!);
            case Expression.ExpressionKind.LIST_EXPR:
                return EvalList(thread, fr, fn, (ListExpression)expr);
            case Expression.ExpressionKind.SLICE:
                return EvalSlice(thread, fr, fn, (SliceExpression)expr);
            case Expression.ExpressionKind.STRING_LITERAL:
                return ((StringLiteral)expr).GetValue();
            case Expression.ExpressionKind.UNARY_OPERATOR:
                return EvalUnaryOperator(thread, fr, fn, (UnaryOperatorExpression)expr);
        }
        throw new ArgumentException("unexpected expression: " + expr.Kind);
    }

    private static object? EvalBinaryOperator(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn, BinaryOperatorExpression binop)
    {
        object? x = Eval_(thread, fr, fn, binop.GetX());
        switch (binop.GetOperator())
        {
            case TokenKind.AND:
                return Starlark.Truth(x) ? Eval_(thread, fr, fn, binop.GetY()) : x;
            case TokenKind.OR:
                return Starlark.Truth(x) ? x : Eval_(thread, fr, fn, binop.GetY());
            default:
                object? y = Eval_(thread, fr, fn, binop.GetY());
                try
                {
                    return EvalUtils.BinaryOp(binop.GetOperator(), x, y, thread);
                }
                catch (EvalException)
                {
                    fr.SetErrorLocation(binop.GetOperatorLocation());
                    throw;
                }
        }
    }

    private static object? EvalDict(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn, DictExpression dictexpr)
    {
        Dict dict = Dict.Of(thread.Mutability);
        foreach (DictExpression.Entry entry in dictexpr.GetEntries())
        {
            object? k = Eval_(thread, fr, fn, entry.GetKey());
            object? v = Eval_(thread, fr, fn, entry.GetValue());
            int before = dict.Count;
            try
            {
                dict.PutEntry(k, v);
            }
            catch (EvalException)
            {
                fr.SetErrorLocation(entry.GetColonLocation());
                throw;
            }
            if (dict.Count == before)
            {
                fr.SetErrorLocation(entry.GetColonLocation());
                throw Starlark.Errorf(
                    "dictionary expression has duplicate key: {0}", Starlark.Repr(k, thread.GetSemantics()));
            }
        }
        return dict;
    }

    private static object? EvalDot(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn, DotExpression dot)
    {
        object? obj = Eval_(thread, fr, fn, dot.GetObject());
        string nm = dot.GetField().GetName();
        try
        {
            return Starlark.GetAttr(thread, obj, nm, null);
        }
        catch (EvalException)
        {
            fr.SetErrorLocation(dot.GetDotLocation());
            throw;
        }
    }

    private static object? EvalIndex(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn, IndexExpression index)
    {
        object? obj = Eval_(thread, fr, fn, index.GetObject());
        object? key = Eval_(thread, fr, fn, index.GetKey());
        try
        {
            return EvalUtils.Index(thread, obj, key!);
        }
        catch (EvalException)
        {
            fr.SetErrorLocation(index.GetLbracketLocation());
            throw;
        }
    }

    private static object? EvalSlice(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn, SliceExpression slice)
    {
        object? x = Eval_(thread, fr, fn, slice.GetObject());
        object start = slice.GetStart() == null ? Starlark.None : Eval_(thread, fr, fn, slice.GetStart()!)!;
        object stop = slice.GetStop() == null ? Starlark.None : Eval_(thread, fr, fn, slice.GetStop()!)!;
        object step = slice.GetStep() == null ? Starlark.None : Eval_(thread, fr, fn, slice.GetStep()!)!;
        try
        {
            return Starlark.Slice(thread.Mutability, x!, start, stop, step);
        }
        catch (EvalException)
        {
            fr.SetErrorLocation(slice.GetLbracketLocation());
            throw;
        }
    }

    private static object? EvalUnaryOperator(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn, UnaryOperatorExpression unop)
    {
        object? x = Eval_(thread, fr, fn, unop.GetX());
        try
        {
            return EvalUtils.UnaryOp(unop.GetOperator(), x);
        }
        catch (EvalException)
        {
            fr.SetErrorLocation(unop.GetStartLocation());
            throw;
        }
    }

    private static object? EvalIdentifier(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn, Identifier id)
    {
        Resolver.Binding bind = id.GetBinding()!;
        object? result;
        switch (bind.GetScope())
        {
            case Resolver.Scope.LOCAL:
                result = fr.Locals![bind.GetIndex()];
                break;
            case Resolver.Scope.CELL:
                result = ((StarlarkFunction.Cell)fr.Locals![bind.GetIndex()]!).X;
                break;
            case Resolver.Scope.FREE:
                result = fn.GetFreeVar(bind.GetIndex()).X;
                break;
            case Resolver.Scope.GLOBAL:
                result = fn.GetGlobal(bind.GetIndex());
                break;
            case Resolver.Scope.PREDECLARED:
                result = fn.Module.GetPredeclared(id.GetName());
                break;
            case Resolver.Scope.UNIVERSAL:
                result = Starlark.UNIVERSE.TryGetValue(id.GetName(), out object? v) ? v : null;
                break;
            default:
                throw new InvalidOperationException(bind.ToString());
        }
        if (result == null)
        {
            fr.SetErrorLocation(id.GetStartLocation());
            throw Starlark.Errorf(
                "{0} variable '{1}' is referenced before assignment.",
                Resolver.ScopeToString(bind.GetScope()), id.GetName());
        }
        return result;
    }

    private static object? EvalList(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn, ListExpression expr)
    {
        IReadOnlyList<Expression> elems = expr.GetElements();
        int n = elems.Count;
        object?[] array = new object?[n];
        for (int i = 0; i < n; i++)
        {
            array[i] = Eval_(thread, fr, fn, elems[i]);
        }
        return expr.IsTuple() ? Tuple.Wrap(array) : StarlarkList.Wrap(thread.Mutability, array);
    }

    private static object? EvalCall(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn, CallExpression call)
    {
        object? fnValue = Eval_(thread, fr, fn, call.GetFunction());

        IReadOnlyList<Argument> arguments = call.GetArguments();
        int numNonStarArgs = arguments.Count;
        Argument.StarStar? starstar = null;
        if (numNonStarArgs > 0 && arguments[numNonStarArgs - 1] is Argument.StarStar ss)
        {
            starstar = ss;
            numNonStarArgs--;
        }
        Argument.Star? star = null;
        if (numNonStarArgs > 0 && arguments[numNonStarArgs - 1] is Argument.Star s)
        {
            star = s;
            numNonStarArgs--;
        }
        int numPositionalArguments = call.GetNumPositionalArguments();

        var positional = new List<object?>();
        var named = new List<object?>();

        int i;
        for (i = 0; i < numPositionalArguments; i++)
        {
            positional.Add(Eval_(thread, fr, fn, arguments[i].GetValue()));
        }
        for (; i < numNonStarArgs; i++)
        {
            Argument arg = arguments[i];
            named.Add(arg.GetName());
            named.Add(Eval_(thread, fr, fn, arg.GetValue()));
        }
        if (star != null)
        {
            object? value = Eval_(thread, fr, fn, star.GetValue());
            if (value is not IStarlarkIterable<object?> && !(value is System.Collections.IEnumerable && value is IStarlarkValue))
            {
                fr.SetErrorLocation(star.GetStartLocation());
                throw Starlark.Errorf("argument after * must be an iterable, not {0}", Starlark.Type(value));
            }
            foreach (object? o in Starlark.ToIterable(value))
            {
                positional.Add(o);
            }
        }
        if (starstar != null)
        {
            object? value = Eval_(thread, fr, fn, starstar.GetValue());
            if (value is not Dict kwargs)
            {
                fr.SetErrorLocation(starstar.GetStartLocation());
                throw Starlark.Errorf("argument after ** must be a dict, not {0}", Starlark.Type(value));
            }
            foreach (var e in kwargs.Entries)
            {
                if (e.Key is not string key)
                {
                    fr.SetErrorLocation(starstar.GetStartLocation());
                    throw Starlark.Errorf("keywords must be strings, not {0}", Starlark.Type(e.Key));
                }
                named.Add(key);
                named.Add(e.Value);
            }
        }

        Location loc = call.GetLparenLocation();
        fr.SetLocation(loc);
        try
        {
            return Starlark.Fastcall(thread, fnValue, positional.ToArray(), named.ToArray());
        }
        catch (EvalException)
        {
            fr.SetErrorLocation(loc);
            throw;
        }
    }

    private static object? EvalComprehension(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn, Comprehension comp)
    {
        Dict? dict = comp.IsDict() ? Dict.Of(thread.Mutability) : null;
        StarlarkList? list = comp.IsDict() ? null : StarlarkList.NewList(thread.Mutability);
        IReadOnlyList<Comprehension.Clause> clauses = comp.GetClauses();

        void ExecClauses(int index)
        {
            if (index < clauses.Count)
            {
                Comprehension.Clause clause = clauses[index];
                if (clause is Comprehension.For forClause)
                {
                    object? seqObj = Eval_(thread, fr, fn, forClause.GetIterable());
                    IEnumerable<object?> seq = Starlark.ToIterable(seqObj);
                    EvalUtils.AddIterator(seqObj);
                    try
                    {
                        foreach (object? elem in seq)
                        {
                            Assign(thread, fr, fn, forClause.GetVars(), elem);
                            ExecClauses(index + 1);
                        }
                    }
                    catch (EvalException)
                    {
                        fr.SetErrorLocation(forClause.GetStartLocation());
                        throw;
                    }
                    finally
                    {
                        EvalUtils.RemoveIterator(seqObj);
                    }
                }
                else
                {
                    var ifClause = (Comprehension.If)clause;
                    if (Starlark.Truth(Eval_(thread, fr, fn, ifClause.GetCondition())))
                    {
                        ExecClauses(index + 1);
                    }
                }
                return;
            }

            if (dict != null)
            {
                var body = (DictExpression.Entry)comp.GetBody();
                object? k = Eval_(thread, fr, fn, body.GetKey());
                Starlark.CheckHashable(k);
                object? v = Eval_(thread, fr, fn, body.GetValue());
                dict.PutEntry(k, v);
            }
            else
            {
                list!.AddElement(Eval_(thread, fr, fn, (Expression)comp.GetBody()));
            }
        }

        ExecClauses(0);
        return comp.IsDict() ? dict : list;
    }

    // ---- Function creation ----

    private static StarlarkFunction NewFunction(
        StarlarkThread thread, StarlarkThread.Frame fr, StarlarkFunction fn, Resolver.Function rfn)
    {
        int nparams =
            rfn.GetParameters().Count - (rfn.HasKwargs() ? 1 : 0) - (rfn.HasVarargs() ? 1 : 0);

        object?[]? defaults = null;
        for (int i = 0; i < nparams; i++)
        {
            Expression? defExpr = rfn.GetParameters()[i].GetDefaultValue();
            if (defExpr == null && defaults == null)
            {
                continue; // skip prefix of required parameters
            }
            defaults ??= new object?[nparams - i];
            object? defaultValue = defExpr == null ? StarlarkFunction.MANDATORY : Eval_(thread, fr, fn, defExpr);
            defaults[i - (nparams - defaults.Length)] = defaultValue;
        }
        defaults ??= Array.Empty<object?>();

        // Capture cells for free variables.
        IReadOnlyList<Resolver.Binding> freeVarBindings = rfn.GetFreeVars();
        object?[] freevars = new object?[freeVarBindings.Count];
        int j = 0;
        foreach (Resolver.Binding bind in freeVarBindings)
        {
            switch (bind.GetScope())
            {
                case Resolver.Scope.FREE:
                    freevars[j++] = fn.GetFreeVar(bind.GetIndex());
                    break;
                case Resolver.Scope.CELL:
                    freevars[j++] = fr.Locals![bind.GetIndex()];
                    break;
                default:
                    throw new InvalidOperationException("unexpected: " + bind);
            }
        }

        return new StarlarkFunction(
            rfn,
            fn.Module,
            fn.GlobalIndex,
            Tuple.Wrap(defaults),
            Tuple.Wrap(freevars));
    }
}
