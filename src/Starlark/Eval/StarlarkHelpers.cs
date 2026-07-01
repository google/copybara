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

using System.Collections;
using System.Collections.Immutable;
using System.Numerics;
using Starlark.Annot;

namespace Starlark.Eval;

/// <summary>
/// Static entry points and helpers of the Starlark interpreter. Port of the value/runtime helpers of
/// <c>net.starlark.java.eval.Starlark</c>. (The core surface — <c>None</c>, <c>Errorf</c> — lives in
/// StarlarkCore.cs; this file extends the partial class.)
///
/// <para>Deferred vs. Java: the reflective builtin registry (UNIVERSE/MethodLibrary/CallUtils), the
/// <c>call</c>/<c>fastcall</c> machinery, static type accessors (<c>getStarlarkType</c>), and
/// doc-string helpers are not ported here.</para>
/// </summary>
public static partial class Starlark
{
    /// <summary>
    /// A sentinel value passed to optional parameters of StarlarkMethod-annotated methods to
    /// indicate that no argument value was supplied.
    /// </summary>
    public static readonly object UNBOUND = new UnboundMarker();

    /// <summary>A type representing no argument passed to StarlarkMethods.</summary>
    public sealed class UnboundMarker : IStarlarkPrintableValue
    {
        internal UnboundMarker() { }

        public override string ToString() => "<unbound>";

        public bool IsImmutable() => true;

        public void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append("<unbound>");
    }

    /// <summary>
    /// The universal bindings predeclared in every Starlark file: the literal constants plus the
    /// core builtin functions from <see cref="MethodLibrary"/>.
    /// </summary>
    public static readonly ImmutableDictionary<string, object> UNIVERSE = MakeUniverse();

    private static ImmutableDictionary<string, object> MakeUniverse()
    {
        var env = new Dictionary<string, object>
        {
            ["False"] = false,
            ["True"] = true,
            ["None"] = None,
        };
        AddMethods(env, MethodLibrary.INSTANCE);
        return env.ToImmutableDictionary();
    }

    /// <summary>
    /// Adds to <paramref name="env"/> a <see cref="BuiltinFunction"/> for each StarlarkMethod-annotated
    /// method of <paramref name="receiver"/>'s type (excluding struct fields and the selfCall method).
    /// </summary>
    public static void AddMethods(IDictionary<string, object> env, object receiver)
    {
        foreach (var e in CallUtils.GetAnnotatedMethods(receiver.GetType()))
        {
            MethodDescriptor desc = e.Value;
            if (desc.IsStructField)
            {
                continue;
            }
            env[e.Key] = new BuiltinFunction(receiver, desc.Name, desc);
        }
    }

    /// <summary>Thrown when a value is not a legal Starlark value.</summary>
    public sealed class InvalidStarlarkValueException : ArgumentException
    {
        public Type? InvalidClass { get; }

        internal InvalidStarlarkValueException(Type? invalidClass)
            : base("invalid Starlark value: " + (invalidClass == null ? "null" : invalidClass.Name))
        {
            InvalidClass = invalidClass;
        }
    }

    /// <summary>Reports whether the argument is a legal Starlark value.</summary>
    public static bool Valid(object? x) => x is string || x is bool || x is IStarlarkValue;

    /// <summary>Returns x if it is a valid Starlark value, else throws.</summary>
    public static T CheckValid<T>(T x)
    {
        if (!Valid(x))
        {
            throw new InvalidStarlarkValueException(x?.GetType());
        }
        return x;
    }

    /// <summary>Reports whether x is null or Starlark None.</summary>
    public static bool IsNullOrNone(object? x) => x == null || ReferenceEquals(x, None);

    /// <summary>Reports whether a Starlark value is assumed to be deeply immutable.</summary>
    public static bool IsImmutable(object? x)
    {
        if (x is string || x is bool)
        {
            return true;
        }
        if (x is IStarlarkValue v)
        {
            return v.IsImmutable();
        }
        throw new InvalidStarlarkValueException(x?.GetType());
    }

    /// <summary>Returns normally if the Starlark value is hashable and thus suitable as a dict key.</summary>
    public static void CheckHashable(object? x)
    {
        if (x is string)
        {
            // Strings are the most common dict keys.
        }
        else if (x is IStarlarkPrintableValue pv)
        {
            pv.CheckHashable();
        }
        else if (x is IStarlarkValue v)
        {
            if (!v.IsImmutable())
            {
                throw Errorf("unhashable type: '{0}'", Type(x));
            }
        }
        else
        {
            CheckValid(x);
        }
    }

    /// <summary>Converts a Java/.NET value to a Starlark one, if not already valid.</summary>
    public static object FromJava(object? x, Mutability? mutability)
    {
        switch (x)
        {
            case null:
                return None;
            case string:
            case bool:
            case IStarlarkValue:
                return x;
            case int i:
                return StarlarkInt.Of(i);
            case long l:
                return StarlarkInt.Of(l);
            case BigInteger b:
                return StarlarkInt.Of(b);
            case double d:
                return StarlarkFloat.Of(d);
            case IEnumerable<KeyValuePair<object?, object?>> map:
                return Dict.CopyOf(mutability, map);
            case IEnumerable<object?> list:
                return StarlarkList.CopyOf(mutability, list);
        }
        throw new InvalidStarlarkValueException(x.GetType());
    }

    /// <summary>Returns the truth value of a valid Starlark value.</summary>
    public static bool Truth(object? x)
    {
        switch (x)
        {
            case bool b:
                return b;
            case IStarlarkValue v:
                return v.Truth();
            case string s:
                return s.Length != 0;
            default:
                throw new InvalidStarlarkValueException(x?.GetType());
        }
    }

    /// <summary>Checks whether the Freezable value is mutable; throws if not.</summary>
    public static void CheckMutable(IFreezable x)
    {
        if (x.Mutability.IsFrozen)
        {
            throw Errorf("trying to mutate a frozen {0} value", Type(x));
        }
        if (x.UpdateIteratorCount(0))
        {
            throw Errorf(
                "{0} value is temporarily immutable due to active for-loop iteration", Type(x));
        }
    }

    /// <summary>Returns an iterable view of x if it is an iterable Starlark value; throws otherwise.</summary>
    public static IEnumerable<object?> ToIterable(object? x)
    {
        if (x is IStarlarkIterable<object?> it)
        {
            return it;
        }
        if (x is IEnumerable en && x is IStarlarkValue)
        {
            return en.Cast<object?>();
        }
        throw Errorf("type '{0}' is not iterable", Type(x));
    }

    /// <summary>Returns a new array containing the elements of a Starlark iterable value.</summary>
    public static object?[] ToArray(object? x)
    {
        switch (x)
        {
            case ISequence<object?> seq:
                return seq.ToArray();
            case Dict dict:
                return dict.Keys.ToArray();
            default:
                return ToIterable(x).ToArray();
        }
    }

    /// <summary>Returns the length of a value as if by <c>len(x)</c>, or -1 if it has no length.</summary>
    public static int Len(object? x)
    {
        switch (x)
        {
            case string s:
                return s.Length;
            case ISequence<object?> seq:
                return seq.Count;
            case Dict dict:
                return dict.Count;
            case IStarlarkIterable<object?> it:
                return it.Count();
            default:
                CheckValid(x);
                return -1;
        }
    }

    /// <summary>Returns the name of the type of a value as if by <c>type(x)</c>.</summary>
    public static string Type(object? x) => ClassType(x?.GetType());

    /// <summary>Returns the name of the type of instances of a class.</summary>
    public static string ClassType(System.Type? c)
    {
        if (c == null)
        {
            return "NoneType";
        }
        if (c == typeof(string))
        {
            return "string";
        }
        if (typeof(StarlarkInt).IsAssignableFrom(c))
        {
            return "int";
        }
        if (c == typeof(bool))
        {
            return "bool";
        }
        if (c == typeof(StarlarkFloat))
        {
            return "float";
        }
        if (typeof(StarlarkList).IsAssignableFrom(c))
        {
            return "list";
        }
        if (typeof(Tuple).IsAssignableFrom(c))
        {
            return "tuple";
        }
        if (c == typeof(Dict))
        {
            return "dict";
        }
        if (c == typeof(NoneType))
        {
            return "NoneType";
        }
        if (c == typeof(RangeList))
        {
            return "range";
        }
        if (c == typeof(UnboundMarker))
        {
            return "unbound";
        }
        if (c == typeof(int) || c == typeof(long) || c == typeof(BigInteger))
        {
            return "int";
        }
        if (c == typeof(double))
        {
            return "float";
        }

        // Honor a [StarlarkBuiltin] annotation if present.
        var attr = (StarlarkBuiltinAttribute?)Attribute.GetCustomAttribute(c, typeof(StarlarkBuiltinAttribute));
        if (attr != null)
        {
            return attr.Name;
        }

        return c.Name;
    }

    /// <summary>The strict weak ordering of Starlark values. Throws on failure.</summary>
    public static int CompareUnchecked(object? x, object? y)
    {
        if (x == null || y == null)
        {
            throw new InvalidCastException(
                string.Format("unsupported comparison: {0} <=> {1}", Type(x), Type(y)));
        }
        if (SameType(x, y))
        {
            switch (x)
            {
                case string sx:
                    return string.CompareOrdinal(sx, (string)y);
                case bool bx:
                    return bx.CompareTo((bool)y);
                case StarlarkInt ix:
                    return StarlarkInt.Compare(ix, (StarlarkInt)y);
                case StarlarkFloat fx:
                    return fx.CompareTo((StarlarkFloat)y);
                case IComparable cx:
                    return cx.CompareTo(y);
            }
        }
        else
        {
            if (x is StarlarkFloat xf && y is StarlarkInt yi)
            {
                double xd = xf.ToDouble();
                return double.IsNaN(xd) ? +1 : -StarlarkInt.CompareIntAndDouble(yi, xd);
            }
            if (x is StarlarkInt xi && y is StarlarkFloat yf)
            {
                double yd = yf.ToDouble();
                return double.IsNaN(yd) ? -1 : StarlarkInt.CompareIntAndDouble(xi, yd);
            }
        }
        throw new InvalidCastException(
            string.Format("unsupported comparison: {0} <=> {1}", Type(x), Type(y)));
    }

    private static bool SameType(object x, object y) =>
        x.GetType() == y.GetType() || Type(x) == Type(y);

    /// <summary>Returns the string form of a value as if by <c>str(x)</c>.</summary>
    public static string Str(object? x, StarlarkSemantics semantics) =>
        new Printer().Str(x, semantics).ToString();

    /// <summary>Returns the string form of a value as if by <c>repr(x)</c>.</summary>
    public static string Repr(object? x, StarlarkSemantics semantics) =>
        new Printer().Repr(x, semantics).ToString();

    /// <summary>Returns a string formatted as if by <c>pattern % arguments</c>.</summary>
    public static string Format(StarlarkSemantics semantics, string pattern, params object?[] arguments)
    {
        var pr = new Printer();
        Printer.Format(pr, semantics, pattern, arguments);
        return pr.ToString();
    }

    /// <summary>Returns a string formatted as if by <c>pattern % arguments</c>.</summary>
    public static string FormatWithList(
        StarlarkSemantics semantics, string pattern, IReadOnlyList<object?> arguments)
    {
        var pr = new Printer();
        Printer.FormatWithList(pr, semantics, pattern, arguments);
        return pr.ToString();
    }

    /// <summary>Returns the signed 32-bit value of a Starlark int, or throws with <paramref name="what"/>.</summary>
    public static int ToInt(object? x, string what)
    {
        if (x is StarlarkInt si)
        {
            return si.ToInt(what);
        }
        if (x is int)
        {
            throw new ArgumentException("Integer is not a legal Starlark value");
        }
        throw Errorf("got {0} for {1}, want int", Type(x), what);
    }

    /// <summary>Returns a slice of a sequence as if by <c>x[start:stop:step]</c>.</summary>
    public static object Slice(
        Mutability? mu, object x, object startObj, object stopObj, object stepObj)
    {
        int n;
        if (x is string s)
        {
            n = s.Length;
        }
        else if (x is ISequence<object?> seq)
        {
            n = seq.Count;
        }
        else
        {
            throw Errorf("invalid slice operand: {0}", Type(x));
        }

        int step;
        if (ReferenceEquals(stepObj, None))
        {
            step = 1;
        }
        else
        {
            step = ToInt(stepObj, "slice step");
            if (step == 0)
            {
                throw Errorf("slice step cannot be zero");
            }
        }

        int start;
        int stop;
        if (step > 0)
        {
            start = ReferenceEquals(startObj, None) ? 0 : ToSliceBound(ToInt(startObj, "start index"), n);
            stop = ReferenceEquals(stopObj, None) ? n : ToSliceBound(ToInt(stopObj, "stop index"), n);
            if (stop < start)
            {
                stop = start;
            }
        }
        else
        {
            start = ReferenceEquals(startObj, None)
                ? n - 1
                : ToReverseSliceBound(ToInt(startObj, "start index"), n);
            stop = ReferenceEquals(stopObj, None)
                ? -1
                : ToReverseSliceBound(ToInt(stopObj, "stop index"), n);
            if (start < stop)
            {
                start = stop;
            }
        }

        if (x is string str)
        {
            return SliceString(str, start, stop, step);
        }
        return ((ISequence<object?>)x).GetSlice(mu, start, stop, step);
    }

    private static string SliceString(string s, int start, int stop, int step)
    {
        if (step == 1)
        {
            return start <= stop ? s.Substring(start, stop - start) : "";
        }
        var sb = new System.Text.StringBuilder();
        if (step > 0)
        {
            for (int i = start; i < stop; i += step)
            {
                sb.Append(s[i]);
            }
        }
        else
        {
            for (int i = start; i > stop; i += step)
            {
                sb.Append(s[i]);
            }
        }
        return sb.ToString();
    }

    // Ports of SyntaxUtils.toSliceBound / toReverseSliceBound (clamping helpers).
    internal static int ToSliceBound(int index, int length)
    {
        if (index < 0)
        {
            index += length;
            if (index < 0)
            {
                index = 0;
            }
        }
        else if (index > length)
        {
            index = length;
        }
        return index;
    }

    internal static int ToReverseSliceBound(int index, int length)
    {
        if (index < 0)
        {
            index += length;
            if (index < 0)
            {
                index = -1;
            }
        }
        else if (index >= length)
        {
            index = length - 1;
        }
        return index;
    }
}
