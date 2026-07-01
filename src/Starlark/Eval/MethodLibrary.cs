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

using Starlark.Annot;

namespace Starlark.Eval;

/// <summary>
/// The universe of predeclared Starlark functions: <c>len</c>, <c>str</c>, <c>repr</c>, <c>type</c>,
/// <c>list</c>, <c>dict</c>, <c>tuple</c>, <c>range</c>, <c>sorted</c>, <c>enumerate</c>, etc. Port of
/// <c>net.starlark.java.eval.MethodLibrary</c>.
/// </summary>
public sealed class MethodLibrary : IStarlarkValue
{
    /// <summary>The singleton receiver for the universe builtins.</summary>
    public static readonly MethodLibrary INSTANCE = new();

    private MethodLibrary() { }

    [StarlarkMethod("len", UseStarlarkThread = true)]
    public StarlarkInt Len([Param(Name = "x")] object x, StarlarkThread thread)
    {
        int len = Starlark.Len(x);
        if (len < 0)
        {
            throw Starlark.Errorf("{0} is not iterable", Starlark.Type(x));
        }
        return StarlarkInt.Of(len);
    }

    [StarlarkMethod("str", UseStarlarkThread = true)]
    public string Str([Param(Name = "x")] object? x, StarlarkThread thread) =>
        Starlark.Str(x, thread.GetSemantics());

    [StarlarkMethod("repr", UseStarlarkThread = true)]
    public string Repr([Param(Name = "x")] object? x, StarlarkThread thread) =>
        Starlark.Repr(x, thread.GetSemantics());

    [StarlarkMethod("type")]
    public string Type([Param(Name = "x")] object? x) => Starlark.Type(x);

    [StarlarkMethod("bool")]
    public bool Bool([Param(Name = "x", DefaultValue = "False")] object? x) => Starlark.Truth(x);

    [StarlarkMethod("tuple")]
    public Tuple TupleFn([Param(Name = "x", DefaultValue = "()")] object x)
    {
        if (x is Tuple t)
        {
            return t;
        }
        return Tuple.Wrap(Starlark.ToArray(x));
    }

    [StarlarkMethod("list", UseStarlarkThread = true)]
    public StarlarkList ListFn([Param(Name = "x", DefaultValue = "[]")] object x, StarlarkThread thread) =>
        StarlarkList.Wrap(thread.Mutability, Starlark.ToArray(x));

    [StarlarkMethod("dict", UseStarlarkThread = true)]
    public Dict DictFn(
        [Param(Name = "pairs", DefaultValue = "[]")] object pairs,
        Dict kwargs,
        StarlarkThread thread)
    {
        if (pairs is StarlarkList sl && sl.Count == 0)
        {
            return kwargs;
        }
        Dict dict = Dict.Of(thread.Mutability);
        Dict.UpdateCommon("dict", dict, pairs, kwargs.Entries);
        return dict;
    }

    [StarlarkMethod("range", UseStarlarkThread = true)]
    public RangeList Range(
        [Param(Name = "start_or_stop")] StarlarkInt startOrStop,
        [Param(Name = "stop", DefaultValue = "unbound")] object? stopOrUnbound,
        [Param(Name = "step", DefaultValue = "1")] StarlarkInt stepI,
        StarlarkThread thread)
    {
        int start;
        int stop;
        if (ReferenceEquals(stopOrUnbound, Starlark.UNBOUND))
        {
            start = 0;
            stop = startOrStop.ToInt("stop");
        }
        else
        {
            start = startOrStop.ToInt("start");
            stop = Starlark.ToInt(stopOrUnbound, "stop");
        }
        int step = stepI.ToInt("step");
        if (step == 0)
        {
            throw Starlark.Errorf("step cannot be 0");
        }
        return new RangeList(start, stop, step);
    }

    [StarlarkMethod("enumerate", UseStarlarkThread = true)]
    public StarlarkList Enumerate(
        [Param(Name = "list", Named = true)] object input,
        [Param(Name = "start", DefaultValue = "0", Named = true)] StarlarkInt startI,
        StarlarkThread thread)
    {
        int start = startI.ToInt("start");
        object?[] array = Starlark.ToArray(input);
        for (int i = 0; i < array.Length; i++)
        {
            array[i] = Tuple.Pair(StarlarkInt.Of(i + start), array[i]);
        }
        return StarlarkList.Wrap(thread.Mutability, array);
    }

    [StarlarkMethod("zip", UseStarlarkThread = true)]
    public StarlarkList Zip(Tuple args, StarlarkThread thread)
    {
        StarlarkList result = StarlarkList.NewList(thread.Mutability);
        int ncols = args.Count;
        if (ncols > 0)
        {
            var iterators = new IEnumerator<object?>[ncols];
            for (int i = 0; i < ncols; i++)
            {
                iterators[i] = Starlark.ToIterable(args[i]).GetEnumerator();
            }
            while (true)
            {
                var elem = new object?[ncols];
                for (int i = 0; i < ncols; i++)
                {
                    if (!iterators[i].MoveNext())
                    {
                        return result;
                    }
                    elem[i] = iterators[i].Current;
                }
                result.AddElement(Tuple.Wrap(elem));
            }
        }
        return result;
    }

    [StarlarkMethod("reversed", UseStarlarkThread = true)]
    public StarlarkList Reversed([Param(Name = "sequence")] object sequence, StarlarkThread thread)
    {
        object?[] array = Starlark.ToArray(sequence);
        Array.Reverse(array);
        return StarlarkList.Wrap(thread.Mutability, array);
    }

    [StarlarkMethod("sorted", UseStarlarkThread = true)]
    public StarlarkList Sorted(
        [Param(Name = "iterable")] object iterable,
        [Param(Name = "key", DefaultValue = "None", Named = true, Noneable = true)] object? key,
        [Param(Name = "reverse", DefaultValue = "False", Named = true)] bool reverse,
        StarlarkThread thread)
    {
        object?[] array = Starlark.ToArray(iterable);
        Comparison<object?> baseCmp = (a, b) => Starlark.CompareUnchecked(a, b);
        if (ReferenceEquals(key, Starlark.None))
        {
            StableSort(array, baseCmp, reverse);
            return StarlarkList.Wrap(thread.Mutability, array);
        }

        var keyed = new (object? K, object? V)[array.Length];
        for (int i = 0; i < array.Length; i++)
        {
            object? k = Starlark.Fastcall(thread, key, new[] { array[i] }, Array.Empty<object?>());
            keyed[i] = (k, array[i]);
        }
        var indices = Enumerable.Range(0, keyed.Length).ToArray();
        Array.Sort(indices, (ia, ib) =>
        {
            int cmp = Starlark.CompareUnchecked(keyed[ia].K, keyed[ib].K);
            if (reverse)
            {
                cmp = -cmp;
            }
            return cmp != 0 ? cmp : ia.CompareTo(ib); // stable
        });
        var result = new object?[keyed.Length];
        for (int i = 0; i < indices.Length; i++)
        {
            result[i] = keyed[indices[i]].V;
        }
        return StarlarkList.Wrap(thread.Mutability, result);
    }

    private static void StableSort(object?[] array, Comparison<object?> cmp, bool reverse)
    {
        var indices = Enumerable.Range(0, array.Length).ToArray();
        Array.Sort(indices, (ia, ib) =>
        {
            int c = cmp(array[ia], array[ib]);
            if (reverse)
            {
                c = -c;
            }
            return c != 0 ? c : ia.CompareTo(ib);
        });
        object?[] copy = (object?[])array.Clone();
        for (int i = 0; i < indices.Length; i++)
        {
            array[i] = copy[indices[i]];
        }
    }

    [StarlarkMethod("min", UseStarlarkThread = true)]
    public object? Min(
        [Param(Name = "key", DefaultValue = "None", Named = true, Positional = false, Noneable = true)] object? key,
        Tuple args,
        StarlarkThread thread) => FindExtreme(args, key, /* max= */ false, thread);

    [StarlarkMethod("max", UseStarlarkThread = true)]
    public object? Max(
        [Param(Name = "key", DefaultValue = "None", Named = true, Positional = false, Noneable = true)] object? key,
        Tuple args,
        StarlarkThread thread) => FindExtreme(args, key, /* max= */ true, thread);

    private static object? FindExtreme(Tuple args, object? key, bool max, StarlarkThread thread)
    {
        IEnumerable<object?> items;
        if (args.Count == 1)
        {
            items = Starlark.ToIterable(args[0]);
        }
        else if (args.Count == 0)
        {
            throw Starlark.Errorf("expected at least one item");
        }
        else
        {
            items = args;
        }

        bool haveKey = !ReferenceEquals(key, Starlark.None);
        object? best = null;
        object? bestKey = null;
        bool first = true;
        foreach (object? item in items)
        {
            object? itemKey = haveKey
                ? Starlark.Fastcall(thread, key, new[] { item }, Array.Empty<object?>())
                : item;
            if (first)
            {
                best = item;
                bestKey = itemKey;
                first = false;
                continue;
            }
            int cmp = Starlark.CompareUnchecked(itemKey, bestKey);
            if (max ? cmp > 0 : cmp < 0)
            {
                best = item;
                bestKey = itemKey;
            }
        }
        if (first)
        {
            throw Starlark.Errorf("argument is empty");
        }
        return best;
    }

    [StarlarkMethod("abs")]
    public object Abs([Param(Name = "x")] object x)
    {
        if (x is StarlarkInt si)
        {
            return si.Signum() < 0 ? StarlarkInt.Uminus(si) : si;
        }
        if (x is StarlarkFloat sf)
        {
            return StarlarkFloat.Of(Math.Abs(sf.ToDouble()));
        }
        throw Starlark.Errorf("got {0} for x, want int or float", Starlark.Type(x));
    }

    [StarlarkMethod("all")]
    public bool All([Param(Name = "elements")] object elements)
    {
        foreach (object? x in Starlark.ToIterable(elements))
        {
            if (!Starlark.Truth(x))
            {
                return false;
            }
        }
        return true;
    }

    [StarlarkMethod("any")]
    public bool Any([Param(Name = "elements")] object elements)
    {
        foreach (object? x in Starlark.ToIterable(elements))
        {
            if (Starlark.Truth(x))
            {
                return true;
            }
        }
        return false;
    }

    [StarlarkMethod("int")]
    public StarlarkInt IntFn(
        [Param(Name = "x")] object x,
        [Param(Name = "base", DefaultValue = "unbound", Named = true)] object? baseO)
    {
        if (x is string s)
        {
            int @base = ReferenceEquals(baseO, Starlark.UNBOUND) ? 10 : Starlark.ToInt(baseO, "base");
            try
            {
                return StarlarkInt.Parse(s, @base);
            }
            catch (FormatException ex)
            {
                throw Starlark.Errorf("{0}", ex.Message);
            }
            catch (ArgumentException ex)
            {
                throw Starlark.Errorf("{0}", ex.Message);
            }
        }
        if (!ReferenceEquals(baseO, Starlark.UNBOUND))
        {
            throw Starlark.Errorf("int() can't convert non-string with explicit base");
        }
        switch (x)
        {
            case bool b:
                return StarlarkInt.Of(b ? 1 : 0);
            case StarlarkInt si:
                return si;
            case StarlarkFloat sf:
                return StarlarkInt.OfFiniteDouble(sf.ToDouble());
            default:
                throw Starlark.Errorf("{0} is not of type string or int or float", Starlark.Type(x));
        }
    }

    [StarlarkMethod("hash")]
    public StarlarkInt Hash([Param(Name = "value")] string value)
    {
        // Java String.hashCode algorithm, for cross-implementation determinism.
        int h = 0;
        foreach (char c in value)
        {
            h = unchecked(31 * h + c);
        }
        return StarlarkInt.Of(h);
    }

    [StarlarkMethod("print", UseStarlarkThread = true)]
    public void Print(
        [Param(Name = "sep", DefaultValue = "\" \"", Named = true, Positional = false)] string sep,
        Tuple args,
        StarlarkThread thread)
    {
        var p = new Printer();
        string separator = "";
        foreach (object? x in args)
        {
            p.Append(separator);
            p.DebugPrint(x, thread);
            separator = sep;
        }
        thread.GetPrintHandler()(thread, p.ToString());
    }

    [StarlarkMethod("fail", UseStarlarkThread = true)]
    public void Fail(
        [Param(Name = "msg", DefaultValue = "None", Named = true, Positional = false, Noneable = true)] object? msg,
        [Param(Name = "sep", DefaultValue = "\" \"", Named = true, Positional = false)] string sep,
        Tuple args,
        StarlarkThread thread)
    {
        var printer = new Printer();
        bool needSep = false;
        if (!ReferenceEquals(msg, Starlark.None))
        {
            printer.DebugPrint(msg, thread);
            needSep = true;
        }
        foreach (object? arg in args)
        {
            if (needSep)
            {
                printer.Append(sep);
            }
            printer.DebugPrint(arg, thread);
            needSep = true;
        }
        throw new EvalException(printer.ToString());
    }

    [StarlarkMethod("getattr", UseStarlarkThread = true)]
    public object? Getattr(
        [Param(Name = "x")] object? x,
        [Param(Name = "name")] string name,
        [Param(Name = "default", DefaultValue = "unbound")] object? defaultValue,
        StarlarkThread thread)
    {
        object? def = ReferenceEquals(defaultValue, Starlark.UNBOUND) ? null : defaultValue;
        return Starlark.GetAttr(thread, x, name, def);
    }

    [StarlarkMethod("hasattr", UseStarlarkThread = true)]
    public bool Hasattr(
        [Param(Name = "x")] object? x,
        [Param(Name = "name")] string name,
        StarlarkThread thread) => Starlark.HasAttr(thread, x, name);

    [StarlarkMethod("dir", UseStarlarkThread = true)]
    public StarlarkList DirFn([Param(Name = "x")] object? x, StarlarkThread thread) =>
        Starlark.Dir(thread, x);
}
