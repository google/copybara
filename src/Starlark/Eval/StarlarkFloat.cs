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
using System.Text;
using Starlark.Annot;

namespace Starlark.Eval;

/// <summary>The Starlark float data type. Port of <c>net.starlark.java.eval.StarlarkFloat</c>.</summary>
[StarlarkBuiltin("float", Category = "core", Doc = "The type of floating-point numbers in Starlark.")]
public sealed class StarlarkFloat : IStarlarkPrintableValue, IComparable<StarlarkFloat>
{
    private readonly double v;

    private StarlarkFloat(double v) => this.v = v;

    /// <summary>Returns the Starlark float value that represents x.</summary>
    public static StarlarkFloat Of(double v) => new(v);

    /// <summary>Returns the value of this float.</summary>
    public double ToDouble() => v;

    public override string ToString() => Format(v, 'g');

    public void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append(ToString());

    public bool IsImmutable() => true;

    public bool Truth() => v != 0.0;

    /// <summary>
    /// Total order over float values. +0 and -0 compare equal. NaN compares equal to itself and
    /// greater than +Inf.
    /// </summary>
    public int CompareTo(StarlarkFloat? that)
    {
        double x = v;
        double y = that!.v;
        if (x > y)
        {
            return +1;
        }
        if (x < y)
        {
            return -1;
        }
        if (x == y)
        {
            return 0; // 0.0 == -0.0
        }
        // At least one operand is NaN.
        long xbits = BitConverter.DoubleToInt64Bits(x);
        long ybits = BitConverter.DoubleToInt64Bits(y);
        return xbits.CompareTo(ybits); // NaN > non-NaN
    }

    public override int GetHashCode()
    {
        if (double.IsFinite(v) && v == Math.Round(v, MidpointRounding.ToEven))
        {
            return StarlarkInt.OfFiniteDouble(v).GetHashCode();
        }
        long bits = BitConverter.DoubleToInt64Bits(v);
        return unchecked((int)(bits ^ (long)((ulong)bits >> 32)));
    }

    public override bool Equals(object? that) =>
        (that is StarlarkFloat f && Equal(v, f.v))
        || (that is StarlarkInt i && StarlarkInt.IntEqualsFloat(i, this));

    private static bool Equal(double x, double y) =>
        x == y || (double.IsNaN(x) && double.IsNaN(y));

    // Performs printf-style string conversion of a double. conv is one of [efgEFG].
    internal static string Format(double v, char conv)
    {
        if (!double.IsFinite(v))
        {
            if (double.IsPositiveInfinity(v))
            {
                return "+inf";
            }
            if (double.IsNegativeInfinity(v))
            {
                return "-inf";
            }
            return "nan";
        }

        string s;
        switch (conv)
        {
            case 'e':
                s = FormatExp(v, false);
                break;
            case 'E':
                s = FormatExp(v, true);
                break;
            case 'f':
            case 'F':
                s = v.ToString("F6", CultureInfo.InvariantCulture);
                break;
            case 'g':
                s = FormatG(v, false);
                break;
            case 'G':
                s = FormatG(v, true);
                break;
            default:
                throw new ArgumentException("unsupported conversion: " + conv);
        }

        if (conv is 'g' or 'G')
        {
            char e = conv == 'g' ? 'e' : 'E';
            int ei = s.IndexOf(e);
            if (ei < 0)
            {
                int dot = s.IndexOf('.');
                if (dot < 0)
                {
                    s += ".0";
                }
                else
                {
                    int i;
                    for (i = s.Length - 1; i > dot + 1 && s[i] == '0'; i--)
                    {
                    }
                    s = s[..(i + 1)];
                }
            }
            else
            {
                int i;
                for (i = ei - 1; s[i] == '0'; i--)
                {
                }
                if (s[i] == '.')
                {
                    i--;
                }
                if (i < ei - 1)
                {
                    s = new StringBuilder(i + 1 + s.Length - ei)
                        .Append(s, 0, i + 1)
                        .Append(s, ei, s.Length - ei)
                        .ToString();
                }
            }
        }

        return s;
    }

    // Emulates C printf "%.17g": 17 significant digits, exponent form when needed.
    private static string FormatG(double v, bool upper)
    {
        // "R"/"G17" round-trips; then convert to a %g-like shape (17 sig digits max).
        string s = v.ToString("G17", CultureInfo.InvariantCulture);
        // .NET uses "E+xx" uppercase; normalize the exponent marker.
        int e = s.IndexOfAny(new[] { 'e', 'E' });
        if (e >= 0)
        {
            string mantissa = s[..e];
            string exp = s[(e + 1)..];
            // Ensure sign and at least two exponent digits, matching C.
            char sign = '+';
            if (exp.StartsWith('+') || exp.StartsWith('-'))
            {
                sign = exp[0];
                exp = exp[1..];
            }
            exp = exp.TrimStart('0');
            if (exp.Length < 2)
            {
                exp = exp.PadLeft(2, '0');
            }
            char marker = upper ? 'E' : 'e';
            return mantissa + marker + sign + exp;
        }
        return s;
    }

    private static string FormatExp(double v, bool upper)
    {
        string s = v.ToString("E6", CultureInfo.InvariantCulture);
        if (!upper)
        {
            s = s.Replace('E', 'e');
        }
        return s;
    }

    /// <summary>Returns x // y (floor of division).</summary>
    internal static StarlarkFloat Floordiv(double x, double y)
    {
        if (y == 0.0)
        {
            throw Starlark.Errorf("integer division by zero");
        }
        return Of(Math.Floor(x / y));
    }

    /// <summary>Returns x / y (floating-point division).</summary>
    internal static StarlarkFloat Div(double x, double y)
    {
        if (y == 0.0)
        {
            throw Starlark.Errorf("floating-point division by zero");
        }
        return Of(x / y);
    }

    /// <summary>Returns x % y (floating-point remainder).</summary>
    internal static StarlarkFloat Mod(double x, double y)
    {
        if (y == 0.0)
        {
            throw Starlark.Errorf("floating-point modulo by zero");
        }
        double z = x % y;
        if ((x < 0) != (y < 0) && z != 0)
        {
            z += y;
        }
        return Of(z);
    }

    /// <summary>
    /// Returns the Starlark int value closest to x, truncating towards zero. Throws if x is not finite.
    /// </summary>
    internal static StarlarkInt FiniteDoubleToIntExact(double x)
    {
        if (long.MinValue <= x && x <= long.MaxValue)
        {
            return StarlarkInt.Of((long)x);
        }
        int shift = GetShift(x);
        if (shift <= 0)
        {
            throw new InvalidOperationException("non-positive shift");
        }
        long mantissa = GetMantissa(x);
        return StarlarkInt.Of((BigInteger)mantissa << shift);
    }

    private const int EXPONENT_MASK = (1 << 11) - 1;

    // Returns the effective signed mantissa of x. Precondition: x is finite.
    internal static long GetMantissa(double x)
    {
        long bits = BitConverter.DoubleToInt64Bits(x);
        long mantissa = bits & ((1L << 52) - 1);
        int exp = (int)((ulong)bits >> 52) & EXPONENT_MASK;
        switch (exp)
        {
            case 0: // denormal
                break;
            case EXPONENT_MASK:
                throw new ArgumentException("not finite: " + x);
            default: // normal
                mantissa |= 1L << 52;
                break;
        }
        return x < 0 ? -mantissa : mantissa;
    }

    // Returns the effective left (+) or right (-) shift required of GetMantissa(x). Precondition: finite.
    internal static int GetShift(double x)
    {
        long bits = BitConverter.DoubleToInt64Bits(x);
        int exp = (int)((ulong)bits >> 52) & EXPONENT_MASK;
        switch (exp)
        {
            case 0: // denormal
                exp -= 1022;
                break;
            case EXPONENT_MASK:
                throw new ArgumentException("not finite: " + x);
            default: // normal
                exp -= 1023;
                break;
        }
        return exp - 52;
    }
}
