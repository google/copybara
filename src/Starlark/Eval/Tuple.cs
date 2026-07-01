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
using Starlark.Annot;

namespace Starlark.Eval;

/// <summary>
/// A Tuple is an immutable finite sequence of values. Port of <c>net.starlark.java.eval.Tuple</c>.
///
/// <para>The Java Regular/Singleton subclass split is collapsed to a single array-backed class.</para>
/// </summary>
[StarlarkBuiltin("tuple", Category = "core", Doc = "The built-in tuple type.")]
public sealed class Tuple : ISequence<object?>, IComparable<Tuple>
{
    private static readonly Tuple EmptyTuple = new(Array.Empty<object?>());

    private readonly object?[] elems;

    private Tuple(object?[] elems) => this.elems = elems;

    /// <summary>Returns the empty tuple.</summary>
    public static Tuple Empty() => EmptyTuple;

    /// <summary>Returns a Tuple that wraps the array, which must not be subsequently modified.</summary>
    internal static Tuple Wrap(object?[] array) => array.Length == 0 ? EmptyTuple : new Tuple(array);

    /// <summary>Returns a tuple containing the given elements.</summary>
    public static Tuple CopyOf(IEnumerable<object?> seq)
    {
        if (seq is Tuple t)
        {
            return t;
        }
        object?[] array = seq is ICollection<object?> c ? c.ToArray() : seq.ToArray();
        return Wrap(array);
    }

    /// <summary>Returns a tuple containing the given elements.</summary>
    public static Tuple Of(params object?[] elems) => Wrap((object?[])elems.Clone());

    /// <summary>Returns a two-element tuple.</summary>
    public static Tuple Pair(object? a, object? b) => Wrap(new[] { a, b });

    /// <summary>Returns a three-element tuple.</summary>
    public static Tuple Triple(object? a, object? b, object? c) => Wrap(new[] { a, b, c });

    /// <summary>Returns a tuple that is the concatenation of two tuples.</summary>
    public static Tuple Concat(Tuple x, Tuple y)
    {
        if (x.elems.Length == 0)
        {
            return y;
        }
        if (y.elems.Length == 0)
        {
            return x;
        }
        object?[] res = new object?[x.elems.Length + y.elems.Length];
        Array.Copy(x.elems, 0, res, 0, x.elems.Length);
        Array.Copy(y.elems, 0, res, x.elems.Length, y.elems.Length);
        return Wrap(res);
    }

    public int Count => elems.Length;

    public object? this[int index] => elems[index];

    public bool IsImmutable()
    {
        foreach (object? e in elems)
        {
            if (!Starlark.IsImmutable(e))
            {
                return false;
            }
        }
        return true;
    }

    public int CompareTo(Tuple? that) => Sequence.Compare(this, that!);

    public override bool Equals(object? that) =>
        ReferenceEquals(this, that) || (that is Tuple o && Sequence.SameElems(this, o));

    public override int GetHashCode()
    {
        // Match the semantics of Java's AbstractList.hashCode for tuples.
        int result = 1;
        foreach (object? e in elems)
        {
            result = unchecked(31 * result + (e?.GetHashCode() ?? 0));
        }
        return result;
    }

    public void Repr(Printer printer, StarlarkSemantics semantics)
    {
        printer.Append("(");
        string sep = "";
        foreach (object? e in elems)
        {
            printer.Append(sep);
            sep = ", ";
            printer.Repr(e, semantics);
        }
        if (elems.Length == 1)
        {
            printer.Append(",");
        }
        printer.Append(")");
    }

    public override string ToString() => Starlark.Repr(this, StarlarkSemantics.DEFAULT);

    public ISequence<object?> GetSlice(Mutability? mu, int start, int stop, int step)
    {
        var indices = new RangeList(start, stop, step);
        int n = indices.Count;
        object?[] res = new object?[n];
        for (int i = 0; i < n; i++)
        {
            res[i] = elems[indices.At(i)];
        }
        return Wrap(res);
    }

    /// <summary>Returns a Tuple containing n consecutive repeats of this tuple.</summary>
    public Tuple Repeat(StarlarkInt n)
    {
        if (n.Signum() <= 0 || elems.Length == 0)
        {
            return EmptyTuple;
        }
        int ni = n.ToInt("repeat");
        long sz = (long)ni * elems.Length;
        if (sz > StarlarkList.MAX_ALLOC)
        {
            throw Starlark.Errorf("excessive repeat ({0} * {1} elements)", elems.Length, ni);
        }
        object?[] res = new object?[(int)sz];
        for (int i = 0; i < ni; i++)
        {
            Array.Copy(elems, 0, res, i * elems.Length, elems.Length);
        }
        return Wrap(res);
    }

    public IEnumerator<object?> GetEnumerator() => ((IEnumerable<object?>)elems).GetEnumerator();

    IEnumerator IEnumerable.GetEnumerator() => elems.GetEnumerator();
}
