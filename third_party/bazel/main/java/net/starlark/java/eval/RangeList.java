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

package net.starlark.java.eval;

import com.google.common.base.Preconditions;
import com.google.common.collect.UnmodifiableIterator;
import java.util.AbstractList;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import javax.annotation.concurrent.Immutable;
import net.starlark.java.annot.StarlarkBuiltin;

/**
 * A sequence returned by the {@code range} function invocation.
 *
 * <p>Instead of eagerly allocating an array with all elements of the sequence, this class uses
 * simple math to compute a value at each index. This is particularly useful when range is huge or
 * only a few elements from it are used.
 *
 * <p>The start, stop, step, and size of the range must all fit within 32-bit signed integers.
 *
 * <p>Eventually {@code range} function should produce an instance of the {@code range} type as is
 * the case in Python 3, but for now to preserve backwards compatibility with Python 2, {@code list}
 * is returned.
 */
@StarlarkBuiltin(
    name = "range",
    category = "core",
    doc =
        "A language built-in type to support ranges. Example of range literal:<br>"
            + "<pre class=language-python>x = range(1, 10, 3)</pre>"
            + "Accessing elements is possible using indexing (starts from <code>0</code>):<br>"
            + "<pre class=language-python>e = x[1]   # e == 2</pre>"
            + "Ranges do not support the <code>+</code> operator for concatenation."
            + "Similar to strings, ranges support slice operations:"
            + "<pre class=language-python>range(10)[1:3]   # range(1, 3)\n"
            + "range(10)[::2]  # range(0, 10, 2)\n"
            + "range(10)[3:0:-1]  # range(3, 0, -1)</pre>"
            + "Ranges are immutable, as in Python 3.")
@Immutable
final class RangeList extends AbstractList<StarlarkInt> implements Sequence<StarlarkInt> {

  private final int start;
  private final int stop;
  private final int step;
  private final int size; // (derived)

  RangeList(int start, int stop, int step) throws EvalException {
    Preconditions.checkArgument(step != 0);

    this.start = start;
    this.stop = stop;
    this.step = step;

    // compute size.
    // Python version:
    // https://github.com/python/cpython/blob/09bb918a61031377d720f1a0fa1fe53c962791b6/Objects/rangeobject.c#L144
    int low; // [low,high) is a half-open interval
    int high;
    long absStep;
    if (step > 0) {
      low = start;
      high = stop;
      absStep = step;
    } else {
      low = stop;
      high = start;
      absStep = -(long) step;
    }
    if (low >= high) {
      this.size = 0;
    } else {
      long diff = (long) high - low - 1;
      long size = diff / absStep + 1;
      if ((int) size != size) {
        throw Starlark.errorf("len(%s) exceeds signed 32-bit range", Starlark.repr(this));
      }
      this.size = (int) size;
    }
  }

  @Override
  public boolean contains(Object x) {
    if (!(x instanceof StarlarkInt)) {
      return false;
    }
    try {
      int i = ((StarlarkInt) x).toIntUnchecked();

      // constant-time implementation
      if (step > 0) {
        return start <= i && i < stop && (i - start) % step == 0;
      } else {
        return stop < i && i <= start && (i - start) % step == 0;
      }
    } catch (IllegalArgumentException ex) {
      return false; // x is not a signed 32-bit int
    }
  }

  @Override
  public StarlarkInt get(int index) {
    if (index < 0 || index >= size()) {
      throw new ArrayIndexOutOfBoundsException(index + ":" + this);
    }
    return StarlarkInt.of(at(index));
  }

  @Override
  public int size() {
    return size;
  }

  @Override
  public int hashCode() {
    if (size == 0) {
      return 234982346;
    } else if (size == 1) {
      return Integer.hashCode(start);
    } else {
      return Objects.hash(start, size, step);
    }
  }

  @Override
  public boolean equals(Object other) {
    if (!(other instanceof RangeList)) {
      return false;
    }
    RangeList that = (RangeList) other;

    // Two RangeLists compare equal if they denote the same sequence.
    if (this.size != that.size) {
      return false; // sequences differ in length
    }
    if (this.size == 0) {
      return true; // both sequences are empty
    }
    if (this.start != that.start) {
      return false; // first element differs
    }
    return this.size == 1 || this.step == that.step;
  }

  @Override
  public Iterator<StarlarkInt> iterator() {
    return new UnmodifiableIterator<StarlarkInt>() {
      long cursor = start; // returned by next() if hasNext() is true

      @Override
      public boolean hasNext() {
        return (step > 0) ? (cursor < stop) : (cursor > stop);
      }

      @Override
      public StarlarkInt next() {
        if (!hasNext()) {
          throw new NoSuchElementException();
        }
        // If cursor is valid, it's guaranteed to be in [start, stop) range, thus a 32-bit value.
        int current = (int) cursor;
        cursor += step;
        return StarlarkInt.of(current);
      }
    };
  }

  @Override
  public Sequence<StarlarkInt> getSlice(Mutability mu, int start, int stop, int step)
      throws EvalException {
    long sliceStep = (long) step * (long) this.step;
    if (sliceStep != (int) sliceStep) {
      // It is not an error to take a slice of a RangeList such that the slice step * list step
      // doesn't fit in a 32-bit int; the result ought to be a RangeList containing only one
      // element (the start). Since difference between 2 successive elements of a RangeList must be
      // a 32-bit int, clamping the step to Integer.MAX_VALUE or MIN_VALUE and moving stop to start
      // +/- 1 gives us the 1-element RangeList we need.
      sliceStep = sliceStep > 0 ? Integer.MAX_VALUE : Integer.MIN_VALUE; // note sliceStep != 0
      if (stop > start) {
        stop = start + 1;
      } else if (stop < start) {
        stop = start - 1;
      }
    }
    return new RangeList(at(start), at(stop), (int) sliceStep);
  }

  // Like get, but without bounds check or Integer allocation.
  int at(int i) {
    return start + step * i;
  }

  @Override
  public void repr(Printer printer) {
    if (step == 1) {
      printer.append(String.format("range(%d, %d)", start, stop));
    } else {
      printer.append(String.format("range(%d, %d, %d)", start, stop, step));
    }
  }
}
