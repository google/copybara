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

namespace Starlark.Eval;

/// <summary>
/// A Sequence is a finite iterable sequence of Starlark values, such as a list or tuple. Port of
/// <c>net.starlark.java.eval.Sequence</c>.
///
/// <para>In the .NET port this exposes the read-only list surface via <see cref="IReadOnlyList{E}"/>
/// (rather than Java's full <c>List</c>), plus indexing/membership hooks.</para>
/// </summary>
public interface ISequence<E> : IStarlarkPrintableValue, IReadOnlyList<E>, IStarlarkIndexable,
    IStarlarkIterable<E>
{
    bool IStarlarkValue.Truth() => Count != 0;

    /// <summary>Retrieves an entry from a Sequence.</summary>
    object? IStarlarkIndexable.GetIndex(StarlarkSemantics semantics, object key)
    {
        int index = Starlark.ToInt(key, "sequence index");
        return this[EvalUtils.GetSequenceIndex(index, Count)];
    }

    bool IStarlarkMembershipTestable.ContainsKey(StarlarkSemantics semantics, object key) =>
        SequenceContains(this, key);

    /// <summary>
    /// Returns the slice <c>this[start:stop:step]</c>. The caller must ensure the indices are valid
    /// and step is non-zero.
    /// </summary>
    ISequence<E> GetSlice(Mutability? mu, int start, int stop, int step);

    private static bool SequenceContains(IEnumerable seq, object key)
    {
        foreach (object? e in seq)
        {
            if (Equals(e, key))
            {
                return true;
            }
        }
        return false;
    }
}

/// <summary>Static helpers for sequences. Port of the static members of Java's Sequence.</summary>
public static class Sequence
{
    /// <summary>
    /// Compares two sequences elementwise. If one is a prefix of another, compares by size.
    /// </summary>
    public static int Compare(IReadOnlyList<object?> x, IReadOnlyList<object?> y)
    {
        int n = Math.Min(x.Count, y.Count);
        for (int i = 0; i < n; i++)
        {
            object? xelem = x[i];
            object? yelem = y[i];
            if (ReferenceEquals(xelem, yelem) || Equals(xelem, yelem))
            {
                continue;
            }
            int cmp = Starlark.CompareUnchecked(xelem, yelem);
            if (cmp == 0)
            {
                throw new InvalidOperationException(string.Format(
                    "x.equals(y) yet x.compareTo(y)==0 (x: {0}, y: {1})",
                    Starlark.Type(xelem), Starlark.Type(yelem)));
            }
            return cmp;
        }
        return x.Count.CompareTo(y.Count);
    }

    /// <summary>Compares two sequences for equality (same size and elementwise equal).</summary>
    public static bool SameElems(IReadOnlyList<object?> x, IReadOnlyList<object?> y)
    {
        if (ReferenceEquals(x, y))
        {
            return true;
        }
        if (x.Count != y.Count)
        {
            return false;
        }
        for (int i = 0; i < x.Count; i++)
        {
            if (!ReferenceEquals(x[i], y[i]) && !Equals(x[i], y[i]))
            {
                return false;
            }
        }
        return true;
    }

    /// <summary>
    /// Casts a non-null Starlark value to a sequence, checking each element is of the given type.
    /// </summary>
    public static ISequence<T> Cast<T>(object x, string what)
    {
        if (x is not ISequence<object?> seq)
        {
            throw Starlark.Errorf("for {0}, got {1}, want sequence", what, Starlark.Type(x));
        }
        int i = 0;
        foreach (object? elem in seq)
        {
            if (elem is not T)
            {
                throw Starlark.Errorf(
                    "at index {0} of {1}, got element of type {2}, want {3}",
                    i, what, Starlark.Type(elem), typeof(T).Name);
            }
            i++;
        }
        return (ISequence<T>)x;
    }
}
