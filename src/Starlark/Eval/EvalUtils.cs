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

using Starlark.Syntax;

namespace Starlark.Eval;

/// <summary>
/// Internal declarations used by the evaluator. Port of <c>net.starlark.java.eval.EvalUtils</c>.
///
/// <para>Deferred vs. Java: the StarlarkSet operator branches (set union/intersection/etc.) are
/// omitted pending the StarlarkSet port; string <c>%</c> formatting delegates to
/// <see cref="Starlark"/> helpers.</para>
/// </summary>
public static class EvalUtils
{
    internal static void AddIterator(object? x)
    {
        if (x is IFreezable f)
        {
            f.UpdateIteratorCount(+1);
        }
    }

    internal static void RemoveIterator(object? x)
    {
        if (x is IFreezable f)
        {
            f.UpdateIteratorCount(-1);
        }
    }

    /// <summary>
    /// Resolves a positive or negative index into [0, length), or throws if out of range. Negative
    /// indices count backward from length.
    /// </summary>
    public static int GetSequenceIndex(int index, int length)
    {
        int actualIndex = index;
        if (actualIndex < 0)
        {
            actualIndex += length;
        }
        if (actualIndex < 0 || actualIndex >= length)
        {
            throw Starlark.Errorf(
                "index out of range (index is {0}, but sequence has {1} elements)", index, length);
        }
        return actualIndex;
    }

    /// <summary>Evaluates an eager binary operation, <c>x op y</c>. (Excludes AND and OR.)</summary>
    public static object? BinaryOp(TokenKind op, object? x, object? y, StarlarkThread thread)
    {
        StarlarkSemantics semantics = thread.GetSemantics();
        Mutability mu = thread.Mutability;
        switch (op)
        {
            case TokenKind.PLUS:
                if (x is StarlarkInt xip)
                {
                    if (y is StarlarkInt yip)
                    {
                        return StarlarkInt.Add(xip, yip);
                    }
                    if (y is StarlarkFloat yfp)
                    {
                        return StarlarkFloat.Of(xip.ToFiniteDouble() + yfp.ToDouble());
                    }
                }
                else if (x is string xsp)
                {
                    if (y is string ysp)
                    {
                        return xsp + ysp;
                    }
                }
                else if (x is Tuple xtp)
                {
                    if (y is Tuple ytp)
                    {
                        return Tuple.Concat(xtp, ytp);
                    }
                }
                else if (x is StarlarkList xlp)
                {
                    if (y is StarlarkList ylp)
                    {
                        return StarlarkList.Concat(xlp, ylp, mu);
                    }
                }
                else if (x is StarlarkFloat xfp)
                {
                    if (y is StarlarkFloat yfp2)
                    {
                        return StarlarkFloat.Of(xfp.ToDouble() + yfp2.ToDouble());
                    }
                    if (y is StarlarkInt yip2)
                    {
                        return StarlarkFloat.Of(xfp.ToDouble() + yip2.ToFiniteDouble());
                    }
                }
                break;

            case TokenKind.PIPE:
                if (x is StarlarkInt xio && y is StarlarkInt yio)
                {
                    return StarlarkInt.Or(xio, yio);
                }
                if (x is Dict xd && y is Dict yd)
                {
                    return Dict.NewBuilder().PutAll(xd.Entries).PutAll(yd.Entries).Build(mu);
                }
                break;

            case TokenKind.AMPERSAND:
                if (x is StarlarkInt xia && y is StarlarkInt yia)
                {
                    return StarlarkInt.And(xia, yia);
                }
                break;

            case TokenKind.CARET:
                if (x is StarlarkInt xic && y is StarlarkInt yic)
                {
                    return StarlarkInt.Xor(xic, yic);
                }
                break;

            case TokenKind.GREATER_GREATER:
                if (x is StarlarkInt xigg && y is StarlarkInt yigg)
                {
                    return StarlarkInt.ShiftRight(xigg, yigg);
                }
                break;

            case TokenKind.LESS_LESS:
                if (x is StarlarkInt xill && y is StarlarkInt yill)
                {
                    return StarlarkInt.ShiftLeft(xill, yill);
                }
                break;

            case TokenKind.MINUS:
                if (x is StarlarkInt xim)
                {
                    if (y is StarlarkInt yim)
                    {
                        return StarlarkInt.Subtract(xim, yim);
                    }
                    if (y is StarlarkFloat yfm)
                    {
                        return StarlarkFloat.Of(xim.ToFiniteDouble() - yfm.ToDouble());
                    }
                }
                else if (x is StarlarkFloat xfm)
                {
                    if (y is StarlarkFloat yfm2)
                    {
                        return StarlarkFloat.Of(xfm.ToDouble() - yfm2.ToDouble());
                    }
                    if (y is StarlarkInt yim2)
                    {
                        return StarlarkFloat.Of(xfm.ToDouble() - yim2.ToFiniteDouble());
                    }
                }
                break;

            case TokenKind.STAR:
                if (x is StarlarkInt xis)
                {
                    if (y is StarlarkInt yis)
                    {
                        return StarlarkInt.Multiply(xis, yis);
                    }
                    if (y is string yss)
                    {
                        return RepeatString(yss, xis);
                    }
                    if (y is Tuple yts)
                    {
                        return yts.Repeat(xis);
                    }
                    if (y is StarlarkList yls)
                    {
                        return yls.Repeat(xis, mu);
                    }
                    if (y is StarlarkFloat yfs)
                    {
                        return StarlarkFloat.Of(xis.ToFiniteDouble() * yfs.ToDouble());
                    }
                }
                else if (x is string xss)
                {
                    if (y is StarlarkInt yis2)
                    {
                        return RepeatString(xss, yis2);
                    }
                }
                else if (x is Tuple xts)
                {
                    if (y is StarlarkInt yis3)
                    {
                        return xts.Repeat(yis3);
                    }
                }
                else if (x is StarlarkList xls)
                {
                    if (y is StarlarkInt yis4)
                    {
                        return xls.Repeat(yis4, mu);
                    }
                }
                else if (x is StarlarkFloat xfs)
                {
                    if (y is StarlarkFloat yfs2)
                    {
                        return StarlarkFloat.Of(xfs.ToDouble() * yfs2.ToDouble());
                    }
                    if (y is StarlarkInt yis5)
                    {
                        return StarlarkFloat.Of(xfs.ToDouble() * yis5.ToFiniteDouble());
                    }
                }
                break;

            case TokenKind.SLASH: // real division
                if (x is StarlarkInt xisl)
                {
                    double xf = xisl.ToFiniteDouble();
                    if (y is StarlarkInt yisl)
                    {
                        return StarlarkFloat.Div(xf, yisl.ToFiniteDouble());
                    }
                    if (y is StarlarkFloat yfsl)
                    {
                        return StarlarkFloat.Div(xf, yfsl.ToDouble());
                    }
                }
                else if (x is StarlarkFloat xfsl)
                {
                    double xf = xfsl.ToDouble();
                    if (y is StarlarkFloat yfsl2)
                    {
                        return StarlarkFloat.Div(xf, yfsl2.ToDouble());
                    }
                    if (y is StarlarkInt yisl2)
                    {
                        return StarlarkFloat.Div(xf, yisl2.ToFiniteDouble());
                    }
                }
                break;

            case TokenKind.SLASH_SLASH:
                if (x is StarlarkInt xiss)
                {
                    if (y is StarlarkInt yiss)
                    {
                        return StarlarkInt.Floordiv(xiss, yiss);
                    }
                    if (y is StarlarkFloat yfss)
                    {
                        return StarlarkFloat.Floordiv(xiss.ToFiniteDouble(), yfss.ToDouble());
                    }
                }
                else if (x is StarlarkFloat xfss)
                {
                    double xf = xfss.ToDouble();
                    if (y is StarlarkFloat yfss2)
                    {
                        return StarlarkFloat.Floordiv(xf, yfss2.ToDouble());
                    }
                    if (y is StarlarkInt yiss2)
                    {
                        return StarlarkFloat.Floordiv(xf, yiss2.ToFiniteDouble());
                    }
                }
                break;

            case TokenKind.PERCENT:
                if (x is StarlarkInt xipc)
                {
                    if (y is StarlarkInt yipc)
                    {
                        return StarlarkInt.Mod(xipc, yipc);
                    }
                    if (y is StarlarkFloat yfpc)
                    {
                        return StarlarkFloat.Mod(xipc.ToFiniteDouble(), yfpc.ToDouble());
                    }
                }
                else if (x is string xspc)
                {
                    try
                    {
                        if (y is Tuple ytpc)
                        {
                            return Starlark.FormatWithList(semantics, xspc, ytpc);
                        }
                        return Starlark.Format(semantics, xspc, y);
                    }
                    catch (FormatException ex)
                    {
                        throw new EvalException(ex.Message);
                    }
                }
                else if (x is StarlarkFloat xfpc)
                {
                    double xf = xfpc.ToDouble();
                    if (y is StarlarkFloat yfpc2)
                    {
                        return StarlarkFloat.Mod(xf, yfpc2.ToDouble());
                    }
                    if (y is StarlarkInt yipc2)
                    {
                        return StarlarkFloat.Mod(xf, yipc2.ToFiniteDouble());
                    }
                }
                break;

            case TokenKind.EQUALS_EQUALS:
                return Equals(x, y);

            case TokenKind.NOT_EQUALS:
                return !Equals(x, y);

            case TokenKind.LESS:
                return Compare(x, y) < 0;

            case TokenKind.LESS_EQUALS:
                return Compare(x, y) <= 0;

            case TokenKind.GREATER:
                return Compare(x, y) > 0;

            case TokenKind.GREATER_EQUALS:
                return Compare(x, y) >= 0;

            case TokenKind.IN:
                if (y is IStarlarkMembershipTestable mt)
                {
                    return mt.ContainsKey(semantics, x!);
                }
                if (y is IStarlarkIndexable.IThreaded th)
                {
                    return th.ContainsKey(thread, semantics, x!);
                }
                if (y is string ys)
                {
                    if (x is not string xs2)
                    {
                        throw Starlark.Errorf(
                            "'in <string>' requires string as left operand, not '{0}'", Starlark.Type(x));
                    }
                    return ys.Contains(xs2, StringComparison.Ordinal);
                }
                break;

            case TokenKind.NOT_IN:
                object? z = BinaryOp(TokenKind.IN, x, y, thread);
                if (z != null)
                {
                    return !Starlark.Truth(z);
                }
                break;

            default:
                throw new InvalidOperationException("not a binary operator: " + op);
        }

        // custom binary operator?
        if (x is IHasBinary xhb)
        {
            object? z = xhb.BinaryOp(op, y!, true);
            if (z != null)
            {
                return z;
            }
        }
        if (y is IHasBinary yhb)
        {
            object? z = yhb.BinaryOp(op, x!, false);
            if (z != null)
            {
                return z;
            }
        }

        throw Starlark.Errorf(
            "unsupported binary operation: {0} {1} {2}", Starlark.Type(x), op, Starlark.Type(y));
    }

    private static int Compare(object? x, object? y)
    {
        try
        {
            return Starlark.CompareUnchecked(x, y);
        }
        catch (InvalidCastException ex)
        {
            throw new EvalException(ex.Message);
        }
    }

    private static string RepeatString(string s, StarlarkInt @in)
    {
        int n = @in.ToInt("repeat");
        if (n <= 0)
        {
            return "";
        }
        if ((long)s.Length * n > int.MaxValue)
        {
            throw Starlark.Errorf("excessive repeat ({0} * {1} characters)", s.Length, n);
        }
        return string.Concat(Enumerable.Repeat(s, n));
    }

    /// <summary>Evaluates a unary operation.</summary>
    public static object? UnaryOp(TokenKind op, object? x)
    {
        switch (op)
        {
            case TokenKind.NOT:
                return !Starlark.Truth(x);

            case TokenKind.MINUS:
                if (x is StarlarkInt xi)
                {
                    return StarlarkInt.Uminus(xi);
                }
                if (x is StarlarkFloat xf)
                {
                    return StarlarkFloat.Of(-xf.ToDouble());
                }
                break;

            case TokenKind.PLUS:
                if (x is StarlarkInt or StarlarkFloat)
                {
                    return x;
                }
                break;

            case TokenKind.TILDE:
                if (x is StarlarkInt xit)
                {
                    return StarlarkInt.Bitnot(xit);
                }
                break;
        }
        throw Starlark.Errorf("unsupported unary operation: {0}{1}", op, Starlark.Type(x));
    }

    /// <summary>Returns the element of sequence or mapping <paramref name="obj"/> indexed by key.</summary>
    public static object? Index(StarlarkThread thread, object? obj, object key)
    {
        Mutability mu = thread.Mutability;
        StarlarkSemantics semantics = thread.GetSemantics();

        if (obj is IStarlarkIndexable.IThreaded th)
        {
            return th.GetIndex(thread, semantics, key);
        }
        if (obj is IStarlarkIndexable ix)
        {
            object? result = ix.GetIndex(semantics, key);
            return result == null ? null : Starlark.FromJava(result, mu);
        }
        if (obj is string s)
        {
            int index = Starlark.ToInt(key, "string index");
            index = GetSequenceIndex(index, s.Length);
            return s[index].ToString();
        }
        throw Starlark.Errorf(
            "type '{0}' has no operator []({1})", Starlark.Type(obj), Starlark.Type(key));
    }

    /// <summary>Updates an object as if by <c>object[key] = value</c>.</summary>
    public static void SetIndex(object? obj, object? key, object? value)
    {
        if (obj is Dict dict)
        {
            dict.PutEntry(key, value);
        }
        else if (obj is StarlarkList list)
        {
            int index = Starlark.ToInt(key, "list index");
            index = GetSequenceIndex(index, list.Count);
            list.SetElementAt(index, value);
        }
        else
        {
            throw Starlark.Errorf(
                "can only assign an element in a dictionary or a list, not in a '{0}'",
                Starlark.Type(obj));
        }
    }

    /// <summary>Updates the named field of x as if by <c>x.field = value</c>.</summary>
    public static void SetField(object? x, string field, object value)
    {
        if (x is IStructure structure)
        {
            structure.SetField(field, value);
        }
        else
        {
            throw Starlark.Errorf("cannot set .{0} field of {1} value", field, Starlark.Type(x));
        }
    }
}
