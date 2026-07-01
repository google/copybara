// Copyright 2020 The Bazel Authors. All rights reserved.
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

using System.Globalization;
using System.Numerics;
using Starlark.Annot;

namespace Starlark.Eval;

/// <summary>
/// The Starlark int data type. Port of <c>net.starlark.java.eval.StarlarkInt</c>.
///
/// <para>Mirrors the Java three-representation structure: <c>Int32</c> (fits in a C# int),
/// <c>Int64</c> (fits in a long), and <c>Big</c> (backed by <see cref="BigInteger"/>).</para>
/// </summary>
[StarlarkBuiltin("int", Category = "core", Doc = "The type of integers in Starlark.")]
public abstract class StarlarkInt : IStarlarkPrintableValue, IComparable<StarlarkInt>
{
    // A cache of small integers >= LEAST_SMALLINT.
    private const int LEAST_SMALLINT = -128;
    private static readonly Int32Value[] smallints = new Int32Value[100_000];

    internal static readonly StarlarkInt ZERO = Of(0);
    private static readonly StarlarkInt ONE = Of(1);
    private static readonly StarlarkInt MINUS_ONE = Of(-1);

    private StarlarkInt() { }

    /// <summary>Returns the Starlark int value that represents x.</summary>
    public static StarlarkInt Of(int x)
    {
        long index = (long)x - LEAST_SMALLINT;
        if (0 <= index && index < smallints.Length)
        {
            Int32Value? xi = smallints[index];
            if (xi == null)
            {
                xi = new Int32Value(x);
                smallints[index] = xi;
            }
            return xi;
        }
        return new Int32Value(x);
    }

    /// <summary>Returns the Starlark int value that represents x.</summary>
    public static StarlarkInt Of(long x)
    {
        if ((int)x == x)
        {
            return Of((int)x);
        }
        return new Int64Value(x);
    }

    /// <summary>Returns the Starlark int value that represents x.</summary>
    public static StarlarkInt Of(BigInteger x)
    {
        if (x >= long.MinValue && x <= long.MaxValue)
        {
            return Of((long)x);
        }
        return new BigValue(x);
    }

    /// <summary>Returns the StarlarkInt value that most closely approximates x.</summary>
    internal static StarlarkInt OfFiniteDouble(double x) => StarlarkFloat.FiniteDoubleToIntExact(x);

    /// <summary>
    /// Returns the int denoted by a literal string in the specified base, as if by <c>int(s, base)</c>.
    /// </summary>
    public static StarlarkInt Parse(string s, int @base)
    {
        string stringForErrors = s;
        if (s.Length == 0)
        {
            throw new FormatException("empty string");
        }

        bool isNegative = false;
        char c = s[0];
        if (c == '+')
        {
            s = s[1..];
        }
        else if (c == '-')
        {
            s = s[1..];
            isNegative = true;
        }

        string digits = s;

        if (s.Length > 1 && s[0] == '0')
        {
            int prefixBase = 0;
            c = s[1];
            if (c is 'b' or 'B')
            {
                prefixBase = 2;
            }
            else if (c is 'o' or 'O')
            {
                prefixBase = 8;
            }
            else if (c is 'x' or 'X')
            {
                prefixBase = 16;
            }
            if (prefixBase != 0 && (@base == 0 || @base == prefixBase))
            {
                @base = prefixBase;
                digits = s[2..];
            }
        }

        if (ReferenceEquals(digits, s) && @base == 0)
        {
            if (s.Length > 1 && s[0] == '0')
            {
                throw new FormatException(
                    "cannot infer base when string begins with a 0: "
                        + Starlark.Repr(stringForErrors, StarlarkSemantics.DEFAULT));
            }
            @base = 10;
        }
        if (@base < 2 || @base > 36)
        {
            throw new FormatException(
                string.Format(CultureInfo.InvariantCulture, "invalid base {0} (want 2 <= base <= 36)", @base));
        }

        if (digits.StartsWith('+') || digits.StartsWith('-'))
        {
            throw new FormatException(string.Format(
                "invalid base-{0} literal: {1}",
                @base, Starlark.Repr(stringForErrors, StarlarkSemantics.DEFAULT)));
        }

        StarlarkInt result;
        if (TryParseBigInteger(digits, @base, out BigInteger big))
        {
            result = Of(big);
        }
        else
        {
            throw new FormatException(string.Format(
                "invalid base-{0} literal: {1}",
                @base, Starlark.Repr(stringForErrors, StarlarkSemantics.DEFAULT)));
        }
        return isNegative ? Uminus(result) : result;
    }

    private static bool TryParseBigInteger(string digits, int @base, out BigInteger value)
    {
        value = BigInteger.Zero;
        if (digits.Length == 0)
        {
            return false;
        }
        BigInteger b = @base;
        foreach (char ch in digits)
        {
            int d;
            if (ch >= '0' && ch <= '9')
            {
                d = ch - '0';
            }
            else if (ch >= 'a' && ch <= 'z')
            {
                d = ch - 'a' + 10;
            }
            else if (ch >= 'A' && ch <= 'Z')
            {
                d = ch - 'A' + 10;
            }
            else
            {
                return false;
            }
            if (d >= @base)
            {
                return false;
            }
            value = value * b + d;
        }
        return true;
    }

    // Subclass for values exactly representable in a C# int.
    private sealed class Int32Value : StarlarkInt
    {
        internal readonly int V;

        internal Int32Value(int v) => V = v;

        public override int ToInt(string what) => V;

        public override long ToLong(string what) => V;

        internal override bool TryToLong(out long value)
        {
            value = V;
            return true;
        }

        public override BigInteger ToBigInteger() => V;

        public override object ToNumber() => V;

        public override int Signum() => Math.Sign(V);

        public override void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append(V);

        public override int GetHashCode() =>
            unchecked(0x316c5239 * V.GetHashCode() ^ 0x67c4a7d5);

        public override bool Equals(object? that) =>
            (that is Int32Value o && V == o.V)
            || (that is StarlarkFloat f && IntEqualsFloat(this, f));
    }

    // Subclass for values exactly representable in a C# long.
    private sealed class Int64Value : StarlarkInt
    {
        internal readonly long V;

        internal Int64Value(long v) => V = v;

        public override long ToLong(string what) => V;

        internal override bool TryToLong(out long value)
        {
            value = V;
            return true;
        }

        public override BigInteger ToBigInteger() => V;

        public override object ToNumber() => V;

        public override int Signum() => Math.Sign(V);

        public override void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append(V);

        public override int GetHashCode() =>
            unchecked((int)(0x67c4a7d5 * (long)V.GetHashCode() ^ 0xee914a1b));

        public override bool Equals(object? that) =>
            (that is Int64Value o && V == o.V)
            || (that is StarlarkFloat f && IntEqualsFloat(this, f));
    }

    // Subclass for values not exactly representable in a long.
    private sealed class BigValue : StarlarkInt
    {
        internal readonly BigInteger V;

        internal BigValue(BigInteger v) => V = v;

        public override BigInteger ToBigInteger() => V;

        public override object ToNumber() => V;

        public override int Signum() => V.Sign;

        public override void Repr(Printer printer, StarlarkSemantics semantics) =>
            printer.Append(V.ToString(CultureInfo.InvariantCulture));

        public override int GetHashCode() =>
            unchecked((int)(0xee914a1b * (long)V.GetHashCode() ^ 0x6406918f));

        public override bool Equals(object? that) =>
            (that is BigValue o && V == o.V)
            || (that is StarlarkFloat f && IntEqualsFloat(this, f));
    }

    /// <summary>Returns the value as a boxed Number (int, long, or BigInteger).</summary>
    public abstract object ToNumber();

    /// <summary>Returns the signum of this StarlarkInt (-1, 0, or +1).</summary>
    public abstract int Signum();

    public override string ToString() => this switch
    {
        Int32Value i => i.V.ToString(CultureInfo.InvariantCulture),
        Int64Value l => l.V.ToString(CultureInfo.InvariantCulture),
        _ => ToBigInteger().ToString(CultureInfo.InvariantCulture),
    };

    public abstract void Repr(Printer printer, StarlarkSemantics semantics);

    /// <summary>Returns the signed 32-bit value, or fails if not exactly representable.</summary>
    public virtual int ToInt(string what) =>
        throw Starlark.Errorf("got {0} for {1}, want value in signed 32-bit range", this, what);

    /// <summary>Returns the signed 64-bit value, or fails if not exactly representable.</summary>
    public virtual long ToLong(string what) =>
        throw Starlark.Errorf("got {0} for {1}, want value in the signed 64-bit range", this, what);

    // Fast path used by arithmetic: returns false instead of throwing when out of long range.
    internal virtual bool TryToLong(out long value)
    {
        value = 0;
        return false;
    }

    /// <summary>Returns the nearest IEEE-754 double closest to this int, which may be +/-Inf.</summary>
    public double ToDouble() => this switch
    {
        Int32Value i => i.V,
        Int64Value l => l.V,
        _ => (double)ToBigInteger(),
    };

    /// <summary>Returns the nearest finite double, or fails if too large.</summary>
    public double ToFiniteDouble()
    {
        double d = ToDouble();
        if (!double.IsFinite(d))
        {
            throw Starlark.Errorf("int too large to convert to float");
        }
        return d;
    }

    /// <summary>Returns the BigInteger value of this StarlarkInt.</summary>
    public abstract BigInteger ToBigInteger();

    /// <summary>Returns the value as a C# signed 32-bit int, or throws if out of range.</summary>
    public int ToIntUnchecked()
    {
        if (this is Int32Value i)
        {
            return i.V;
        }
        throw new ArgumentException("not a signed 32-bit value");
    }

    /// <summary>Returns the result of truncating this value into the signed 32-bit range.</summary>
    public int TruncateToInt() => this switch
    {
        Int32Value i => i.V,
        Int64Value l => unchecked((int)l.V),
        _ => unchecked((int)(long)(ToBigInteger() & 0xFFFFFFFF)),
    };

    public bool IsImmutable() => true;

    public bool Truth() => !ReferenceEquals(this, ZERO) && Signum() != 0;

    public int CompareTo(StarlarkInt? x) => Compare(this, x!);

    // binary operators

    /// <summary>Returns signum(x - y).</summary>
    public static int Compare(StarlarkInt x, StarlarkInt y)
    {
        if (x.TryToLong(out long xl))
        {
            if (y.TryToLong(out long yl))
            {
                return xl.CompareTo(yl);
            }
            return -((BigValue)y).V.Sign; // (long, big)
        }
        return y is BigValue yb
            ? ((BigValue)x).V.CompareTo(yb.V) // (big, big)
            : ((BigValue)x).V.Sign; // (big, long)
    }

    /// <summary>Returns x + y.</summary>
    public static StarlarkInt Add(StarlarkInt x, StarlarkInt y)
    {
        if (x.TryToLong(out long xl) && y.TryToLong(out long yl))
        {
            long zl = unchecked(xl + yl);
            bool overflow = ((xl ^ zl) & (yl ^ zl)) < 0;
            if (!overflow)
            {
                return Of(zl);
            }
        }
        return Of(x.ToBigInteger() + y.ToBigInteger());
    }

    /// <summary>Returns x - y.</summary>
    public static StarlarkInt Subtract(StarlarkInt x, StarlarkInt y)
    {
        if (x.TryToLong(out long xl) && y.TryToLong(out long yl))
        {
            long zl = unchecked(xl - yl);
            bool overflow = ((xl ^ yl) & (xl ^ zl)) < 0;
            if (!overflow)
            {
                return Of(zl);
            }
        }
        return Of(x.ToBigInteger() - y.ToBigInteger());
    }

    /// <summary>Returns x * y.</summary>
    public static StarlarkInt Multiply(StarlarkInt x, StarlarkInt y)
    {
        if (x.TryToLong(out long xl) && y.TryToLong(out long yl))
        {
            long hi = Math.BigMul(xl, yl, out long lo);
            // Check int128 result is within int64 range.
            if (hi == (lo >> 63))
            {
                return Of(lo);
            }
        }
        return Of(x.ToBigInteger() * y.ToBigInteger());
    }

    /// <summary>Returns x // y (floor of integer division).</summary>
    public static StarlarkInt Floordiv(StarlarkInt x, StarlarkInt y)
    {
        if (ReferenceEquals(y, ZERO) || y.Signum() == 0)
        {
            throw Starlark.Errorf("integer division by zero");
        }
        if (x.TryToLong(out long xl) && y.TryToLong(out long yl)
            && !(xl == long.MinValue && yl == -1))
        {
            long quo = FloorDiv(xl, yl);
            return Of(quo);
        }
        BigInteger xbig = x.ToBigInteger();
        BigInteger ybig = y.ToBigInteger();
        BigInteger quotient = BigInteger.DivRem(xbig, ybig, out BigInteger rem);
        if ((xbig.Sign < 0) != (ybig.Sign < 0) && rem.Sign != 0)
        {
            quotient -= BigInteger.One;
        }
        return Of(quotient);
    }

    /// <summary>Returns x % y.</summary>
    public static StarlarkInt Mod(StarlarkInt x, StarlarkInt y)
    {
        if (ReferenceEquals(y, ZERO) || y.Signum() == 0)
        {
            throw Starlark.Errorf("integer modulo by zero");
        }
        if (x.TryToLong(out long xl) && y.TryToLong(out long yl))
        {
            return Of(FloorMod(xl, yl));
        }
        BigInteger xbig = x.ToBigInteger();
        BigInteger ybig = y.ToBigInteger();
        BigInteger z = xbig % ybig;
        if ((x.Signum() < 0) != (y.Signum() < 0) && z.Sign != 0)
        {
            z += ybig;
        }
        return Of(z);
    }

    /// <summary>Returns x &gt;&gt; y.</summary>
    public static StarlarkInt ShiftRight(StarlarkInt x, StarlarkInt y)
    {
        int yi = y.ToInt("shift count");
        if (yi < 0)
        {
            throw Starlark.Errorf("negative shift count: {0}", yi);
        }
        if (x.TryToLong(out long xl))
        {
            if (yi >= 64)
            {
                return xl < 0 ? Of(-1) : ZERO;
            }
            return Of(xl >> yi);
        }
        return Of(x.ToBigInteger() >> yi);
    }

    /// <summary>Returns x &lt;&lt; y.</summary>
    public static StarlarkInt ShiftLeft(StarlarkInt x, StarlarkInt y)
    {
        int yi = y.ToInt("shift count");
        if (yi < 0)
        {
            throw Starlark.Errorf("negative shift count: {0}", yi);
        }
        if (yi >= 512)
        {
            throw Starlark.Errorf("shift count too large: {0}", yi);
        }
        if (x.TryToLong(out long xl))
        {
            long z = unchecked(xl << yi);
            if (yi < 64 && (z >> yi) == xl)
            {
                return Of(z);
            }
        }
        return Of(x.ToBigInteger() << yi);
    }

    /// <summary>Returns x ^ y.</summary>
    public static StarlarkInt Xor(StarlarkInt x, StarlarkInt y)
    {
        if (x.TryToLong(out long xl) && y.TryToLong(out long yl))
        {
            return Of(xl ^ yl);
        }
        return Of(x.ToBigInteger() ^ y.ToBigInteger());
    }

    /// <summary>Returns x | y.</summary>
    public static StarlarkInt Or(StarlarkInt x, StarlarkInt y)
    {
        if (x.TryToLong(out long xl) && y.TryToLong(out long yl))
        {
            return Of(xl | yl);
        }
        return Of(x.ToBigInteger() | y.ToBigInteger());
    }

    /// <summary>Returns x &amp; y.</summary>
    public static StarlarkInt And(StarlarkInt x, StarlarkInt y)
    {
        if (x.TryToLong(out long xl) && y.TryToLong(out long yl))
        {
            return Of(xl & yl);
        }
        return Of(x.ToBigInteger() & y.ToBigInteger());
    }

    /// <summary>Returns ~x.</summary>
    public static StarlarkInt Bitnot(StarlarkInt x)
    {
        if (x.TryToLong(out long xl))
        {
            return Of(~xl);
        }
        return Of(-((BigValue)x).V - BigInteger.One);
    }

    /// <summary>Returns -x.</summary>
    public static StarlarkInt Uminus(StarlarkInt x)
    {
        switch (x)
        {
            case Int32Value i:
                return Of(-(long)i.V);
            case Int64Value l when l.V != long.MinValue:
                return Of(-l.V);
            default:
                return Of(-x.ToBigInteger());
        }
    }

    /// <summary>Reports whether int x exactly equals float y.</summary>
    internal static bool IntEqualsFloat(StarlarkInt x, StarlarkFloat y)
    {
        double yf = y.ToDouble();
        return !double.IsNaN(yf) && CompareIntAndDouble(x, yf) == 0;
    }

    /// <summary>Returns an exact three-valued comparison of int x with (non-NaN) double y.</summary>
    internal static int CompareIntAndDouble(StarlarkInt x, double y)
    {
        if (double.IsInfinity(y))
        {
            return y > 0 ? -1 : +1;
        }

        if (x is Int32Value || (x is Int64Value l && LongHasExactDouble(l.V)))
        {
            double xf = x.ToDouble();
            if (xf > y)
            {
                return +1;
            }
            if (xf < y)
            {
                return -1;
            }
            return 0;
        }

        int xsign = x.Signum();
        int ysign = Math.Sign(y);
        if (xsign > ysign)
        {
            return +1;
        }
        if (xsign < ysign)
        {
            return -1;
        }

        int shift = StarlarkFloat.GetShift(y);
        BigInteger xbig = x.ToBigInteger();
        if (shift < 0)
        {
            xbig <<= -shift;
        }
        BigInteger ybig = StarlarkFloat.GetMantissa(y);
        if (shift > 0)
        {
            ybig <<= shift;
        }
        return xbig.CompareTo(ybig);
    }

    private static bool LongHasExactDouble(long x) => (long)(double)x == x;

    // Java Math.floorDiv/floorMod equivalents.
    private static long FloorDiv(long x, long y)
    {
        long q = x / y;
        if ((x ^ y) < 0 && q * y != x)
        {
            q--;
        }
        return q;
    }

    private static long FloorMod(long x, long y)
    {
        long r = x % y;
        if (r != 0 && (r ^ y) < 0)
        {
            r += y;
        }
        return r;
    }
}
