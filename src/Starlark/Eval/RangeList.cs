// Copyright 2018 The Bazel Authors. All rights reserved.
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
using System.Globalization;
using Starlark.Annot;

namespace Starlark.Eval;

/// <summary>
/// A lazily-computed sequence returned by the <c>range</c> function. Port of
/// <c>net.starlark.java.eval.RangeList</c>. Also used internally to enumerate slice indices.
/// </summary>
[StarlarkBuiltin("range", Category = "core", Doc = "A language built-in type to support ranges.")]
public sealed class RangeList : ISequence<StarlarkInt>
{
    private readonly int start;
    private readonly int stop;
    private readonly int step;
    private readonly int size;

    public RangeList(int start, int stop, int step)
    {
        if (step == 0)
        {
            throw new ArgumentException("step must be non-zero");
        }
        this.start = start;
        this.stop = stop;
        this.step = step;

        int low;
        int high;
        long absStep;
        if (step > 0)
        {
            low = start;
            high = stop;
            absStep = step;
        }
        else
        {
            low = stop;
            high = start;
            absStep = -(long)step;
        }
        if (low >= high)
        {
            size = 0;
        }
        else
        {
            long diff = (long)high - low - 1;
            long sz = diff / absStep + 1;
            if ((int)sz != sz)
            {
                throw Starlark.Errorf(
                    "len({0}) exceeds signed 32-bit range", Starlark.Repr(this, StarlarkSemantics.DEFAULT));
            }
            size = (int)sz;
        }
    }

    public int Count => size;

    public bool Contains(object? x)
    {
        if (x is not StarlarkInt si)
        {
            return false;
        }
        try
        {
            int i = si.ToIntUnchecked();
            if (step > 0)
            {
                return start <= i && i < stop && (i - start) % step == 0;
            }
            return stop < i && i <= start && (i - start) % step == 0;
        }
        catch (ArgumentException)
        {
            return false;
        }
    }

    public StarlarkInt this[int index]
    {
        get
        {
            if (index < 0 || index >= size)
            {
                throw new IndexOutOfRangeException(index + ":" + this);
            }
            return StarlarkInt.Of(At(index));
        }
    }

    public override int GetHashCode() => size switch
    {
        0 => 234982346,
        1 => start.GetHashCode(),
        _ => HashCode.Combine(start, size, step),
    };

    public override bool Equals(object? other)
    {
        if (other is not RangeList that)
        {
            return false;
        }
        if (size != that.size)
        {
            return false;
        }
        if (size == 0)
        {
            return true;
        }
        if (start != that.start)
        {
            return false;
        }
        return size == 1 || step == that.step;
    }

    public IEnumerator<StarlarkInt> GetEnumerator()
    {
        long cursor = start;
        while (step > 0 ? cursor < stop : cursor > stop)
        {
            yield return StarlarkInt.Of((int)cursor);
            cursor += step;
        }
    }

    IEnumerator IEnumerable.GetEnumerator() => GetEnumerator();

    public ISequence<StarlarkInt> GetSlice(Mutability? mu, int sstart, int sstop, int sstep)
    {
        long sliceStep = (long)sstep * step;
        if (sliceStep != (int)sliceStep)
        {
            sliceStep = sliceStep > 0 ? int.MaxValue : int.MinValue;
            if (sstop > sstart)
            {
                sstop = sstart + 1;
            }
            else if (sstop < sstart)
            {
                sstop = sstart - 1;
            }
        }
        return new RangeList(At(sstart), At(sstop), (int)sliceStep);
    }

    // Like the indexer, but without bounds check.
    internal int At(int i) => start + step * i;

    public void Repr(Printer printer, StarlarkSemantics semantics)
    {
        printer.Append(step == 1
            ? string.Format(CultureInfo.InvariantCulture, "range({0}, {1})", start, stop)
            : string.Format(CultureInfo.InvariantCulture, "range({0}, {1}, {2})", start, stop, step));
    }
}
