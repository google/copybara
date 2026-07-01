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
/// A StarlarkList is a mutable finite sequence of values. Port of
/// <c>net.starlark.java.eval.StarlarkList</c>.
///
/// <para>The Java implementation splits into several JVM-memory-optimized subclasses
/// (Regular/Immutable/Singleton/Lazy/Mutable). This .NET port collapses them into one class backed
/// by an <c>object?[]</c> plus a size, honoring the shared <see cref="Mutability"/> for freezing;
/// behavior is identical.</para>
/// </summary>
[StarlarkBuiltin("list", Category = "core", Doc = "The built-in list type.")]
public sealed class StarlarkList : ISequence<object?>, IFreezable, IComparable<StarlarkList>
{
    internal const int MAX_ALLOC = 1 << 30;
    private static readonly object?[] EMPTY_ARRAY = Array.Empty<object?>();

    private static readonly StarlarkList EmptyImmutable =
        new(Mutability.IMMUTABLE, EMPTY_ARRAY, 0);

    private object?[] elems;
    private int size;
    private int iteratorCount;
    private Mutability mutability;

    private StarlarkList(Mutability mutability, object?[] elems, int size)
    {
        this.mutability = mutability;
        this.elems = elems;
        this.size = size;
    }

    /// <summary>
    /// Takes ownership of the array and returns a new StarlarkList wrapping it. If mutability is null
    /// or frozen, the result is immutable.
    /// </summary>
    internal static StarlarkList Wrap(Mutability? mutability, object?[] elems)
    {
        if (mutability == null || mutability.IsFrozen)
        {
            if (elems.Length == 0)
            {
                return Empty();
            }
            return new StarlarkList(Mutability.IMMUTABLE, elems, elems.Length);
        }
        return new StarlarkList(mutability, elems, elems.Length);
    }

    /// <summary>Returns an empty frozen list.</summary>
    public static StarlarkList Empty() => EmptyImmutable;

    /// <summary>Returns a new, empty list with the specified Mutability.</summary>
    public static StarlarkList NewList(Mutability? mutability) => Wrap(mutability, EMPTY_ARRAY);

    /// <summary>Returns a StarlarkList with the given items and Mutability (null means immutable).</summary>
    public static StarlarkList CopyOf(Mutability? mutability, IEnumerable<object?> elems)
    {
        if (mutability == null && elems is StarlarkList existing && existing.IsImmutable())
        {
            return existing;
        }
        object?[] array = ToArray(elems);
        foreach (object? e in array)
        {
            Starlark.CheckValid(e);
        }
        return Wrap(mutability, array);
    }

    /// <summary>Returns an immutable list with the given elements.</summary>
    public static StarlarkList ImmutableCopyOf(IEnumerable<object?> elems) => CopyOf(null, elems);

    /// <summary>Returns a StarlarkList with the given items and Mutability (null means immutable).</summary>
    public static StarlarkList Of(Mutability? mutability, params object?[] elems)
    {
        if (elems.Length == 0)
        {
            return NewList(mutability);
        }
        foreach (object? e in elems)
        {
            Starlark.CheckValid(e);
        }
        return Wrap(mutability, (object?[])elems.Clone());
    }

    private static object?[] ToArray(IEnumerable<object?> elems) =>
        elems is ICollection<object?> c ? c.ToArray() : elems.ToArray();

    // Returns the backing array (shared; caller must not modify beyond size).
    internal object?[] Elems => elems;

    public Mutability Mutability => mutability;

    public int Count => size;

    public object? this[int index]
    {
        get
        {
            if (index < 0 || index >= size)
            {
                throw new IndexOutOfRangeException(index.ToString());
            }
            return elems[index];
        }
    }

    public void CheckHashable() => throw Starlark.Errorf("unhashable type: 'list'");

    public bool IsImmutable() => mutability.IsFrozen;

    public bool UpdateIteratorCount(int delta)
    {
        if (mutability.IsFrozen)
        {
            return false;
        }
        if (delta > 0)
        {
            iteratorCount++;
        }
        else if (delta < 0)
        {
            iteratorCount--;
        }
        return iteratorCount > 0;
    }

    public void UnsafeShallowFreeze()
    {
        Mutability.CheckUnsafeShallowFreezePrecondition(this);
        mutability = Mutability.IMMUTABLE;
    }

    /// <summary>Returns a new StarlarkList that is the concatenation of two lists.</summary>
    public static StarlarkList Concat(StarlarkList x, StarlarkList y, Mutability? mutability)
    {
        int n = AddSizesAndFailIfExcessive(x.size, y.size);
        object?[] res = new object?[n];
        Array.Copy(x.elems, 0, res, 0, x.size);
        Array.Copy(y.elems, 0, res, x.size, y.size);
        return Wrap(mutability, res);
    }

    private static int AddSizesAndFailIfExcessive(int xsize, int ysize)
    {
        int sum = xsize + ysize;
        if (sum < 0 || sum > MAX_ALLOC)
        {
            throw Starlark.Errorf("excessive capacity requested ({0} + {1} elements)", xsize, ysize);
        }
        return sum;
    }

    public int CompareTo(StarlarkList? that) => Sequence.Compare(this, that!);

    public override bool Equals(object? that) =>
        ReferenceEquals(this, that) || (that is StarlarkList o && Sequence.SameElems(this, o));

    public override int GetHashCode()
    {
        int result = 1;
        for (int i = 0; i < size; i++)
        {
            result = unchecked(31 * result + (elems[i]?.GetHashCode() ?? 0));
        }
        return unchecked(6047 + 4673 * result);
    }

    public void Repr(Printer printer, StarlarkSemantics semantics) =>
        printer.PrintList(this, "[", ", ", "]", semantics);

    public override string ToString() => Starlark.Repr(this, StarlarkSemantics.DEFAULT);

    /// <summary>Returns a new StarlarkList containing n consecutive repeats of this list.</summary>
    public StarlarkList Repeat(StarlarkInt n, Mutability? mutability)
    {
        if (n.Signum() <= 0)
        {
            return Wrap(mutability, EMPTY_ARRAY);
        }
        int ni = n.ToInt("repeat");
        long sz = (long)ni * size;
        if (sz > MAX_ALLOC)
        {
            throw Starlark.Errorf("excessive repeat ({0} * {1} elements)", size, ni);
        }
        object?[] res = new object?[(int)sz];
        for (int i = 0; i < ni; i++)
        {
            Array.Copy(elems, 0, res, i * size, size);
        }
        return Wrap(mutability, res);
    }

    public ISequence<object?> GetSlice(Mutability? mu, int start, int stop, int step)
    {
        var indices = new RangeList(start, stop, step);
        int n = indices.Count;
        object?[] res = new object?[n];
        if (step == 1)
        {
            Array.Copy(elems, indices.At(0), res, 0, n);
        }
        else
        {
            for (int i = 0; i < n; i++)
            {
                res[i] = elems[indices.At(i)];
            }
        }
        return Wrap(mu, res);
    }

    // -- mutators (checked against freezing) --

    private void CheckMutable() => Starlark.CheckMutable(this);

    private void EnsureCapacity(int min)
    {
        if (min > elems.Length)
        {
            int newCap = Math.Max(min, elems.Length == 0 ? 4 : elems.Length * 2);
            Array.Resize(ref elems, newCap);
        }
    }

    /// <summary>Appends an element to the end of the list.</summary>
    public void AddElement(object? element)
    {
        CheckMutable();
        EnsureCapacity(size + 1);
        elems[size++] = element;
    }

    /// <summary>Inserts an element at a given (already validated) position.</summary>
    public void AddElementAt(int index, object? element)
    {
        CheckMutable();
        EnsureCapacity(size + 1);
        Array.Copy(elems, index, elems, index + 1, size - index);
        elems[index] = element;
        size++;
    }

    /// <summary>Appends all the elements to the end of the list.</summary>
    public void AddElements(IEnumerable<object?> elements)
    {
        CheckMutable();
        if (elements is StarlarkList sl)
        {
            EnsureCapacity(size + sl.size);
            Array.Copy(sl.elems, 0, elems, size, sl.size);
            size += sl.size;
            return;
        }
        foreach (object? e in elements)
        {
            AddElement(e);
        }
    }

    /// <summary>Removes the element at a given (already validated) index.</summary>
    public void RemoveElementAt(int index)
    {
        CheckMutable();
        Array.Copy(elems, index + 1, elems, index, size - index - 1);
        elems[--size] = null;
    }

    /// <summary>Sets the element at the given index. Precondition: 0 &lt;= index &lt; Count.</summary>
    public void SetElementAt(int index, object? value)
    {
        CheckMutable();
        elems[index] = value;
    }

    [StarlarkMethod("remove", Doc = "Removes the first item whose value is x.")]
    public void RemoveElement([Param(Name = "x")] object? x)
    {
        for (int i = 0; i < size; i++)
        {
            if (Equals(elems[i], x))
            {
                RemoveElementAt(i);
                return;
            }
        }
        throw Starlark.Errorf("item {0} not found in list", Starlark.Repr(x, StarlarkSemantics.DEFAULT));
    }

    [StarlarkMethod("append", Doc = "Adds an item to the end of the list.")]
    public void Append([Param(Name = "item")] object? item) => AddElement(item);

    [StarlarkMethod("clear", Doc = "Removes all the elements of the list.")]
    public void ClearElements()
    {
        CheckMutable();
        Array.Clear(elems, 0, size);
        size = 0;
    }

    [StarlarkMethod("insert", Doc = "Inserts an item at a given position.")]
    public void Insert(
        [Param(Name = "index")] StarlarkInt index, [Param(Name = "item")] object? item) =>
        AddElementAt(ToSliceBound(index.ToInt("index"), size), item);

    [StarlarkMethod("extend", Doc = "Adds all items to the end of the list.")]
    public void Extend([Param(Name = "items")] IEnumerable<object?> items) => AddElements(items);

    [StarlarkMethod("index", Doc = "Returns the index of the first item whose value is x.")]
    public int Index(
        [Param(Name = "x")] object? x,
        [Param(Name = "start", DefaultValue = "unbound")] object start,
        [Param(Name = "end", DefaultValue = "unbound")] object end)
    {
        int i = ReferenceEquals(start, Starlark.UNBOUND)
            ? 0
            : ToSliceBound(Starlark.ToInt(start, "start"), size);
        int j = ReferenceEquals(end, Starlark.UNBOUND)
            ? size
            : ToSliceBound(Starlark.ToInt(end, "end"), size);
        for (; i < j; i++)
        {
            if (Equals(elems[i], x))
            {
                return i;
            }
        }
        throw Starlark.Errorf("item {0} not found in list", Starlark.Repr(x, StarlarkSemantics.DEFAULT));
    }

    [StarlarkMethod("pop", Doc = "Removes the item at the given position and returns it.")]
    public object? Pop([Param(Name = "i", DefaultValue = "-1")] StarlarkInt arg)
    {
        int index = EvalUtils.GetSequenceIndex(arg.ToInt("i"), size);
        object? result = elems[index];
        RemoveElementAt(index);
        return result;
    }

    public IEnumerator<object?> GetEnumerator()
    {
        for (int i = 0; i < size; i++)
        {
            yield return elems[i];
        }
    }

    IEnumerator IEnumerable.GetEnumerator() => GetEnumerator();

    // Clamps an index into [0, length] as for a slice start bound (port of SyntaxUtils.toSliceBound).
    private static int ToSliceBound(int index, int length)
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
}
