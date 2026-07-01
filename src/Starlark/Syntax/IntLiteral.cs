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

using System.Globalization;
using System.Numerics;

namespace Starlark.Syntax;

/// <summary>
/// Syntax node for a non-negative int literal. (Negative integers are parsed as a
/// <see cref="UnaryOperatorExpression"/> operating on a positive <see cref="IntLiteral"/> argument.)
/// </summary>
public sealed class IntLiteral : Expression
{
    private readonly string raw;
    private readonly int tokenOffset;
    private readonly object value; // = int | long | BigInteger

    /// <summary>
    /// Constructs an IntLiteral. <paramref name="value"/> must be either an int or long or
    /// BigInteger, and the smallest type capable of exactly representing the number must be used.
    /// </summary>
    internal IntLiteral(FileLocations locs, string raw, int tokenOffset, object value)
        : base(locs, ExpressionKind.INT_LITERAL)
    {
        this.raw = raw;
        this.tokenOffset = tokenOffset;
        this.value = value;
    }

    /// <summary>
    /// Returns the value denoted by this literal as an int, long, or BigInteger, using the narrowest
    /// type capable of exactly representing the value.
    /// </summary>
    public object GetValue() => value;

    /// <summary>
    /// Returns the value denoted by this literal as an int, or null if it can't be represented
    /// exactly.
    /// </summary>
    public int? GetIntValueExact() => value is int intValue ? intValue : null;

    /// <summary>Returns the raw source text of the literal.</summary>
    public string GetRaw() => raw;

    public override int GetStartOffset() => tokenOffset;

    public override int GetEndOffset() => tokenOffset + raw.Length;

    public override void Accept(NodeVisitor visitor) => visitor.Visit(this);

    /// <summary>
    /// Returns the value denoted by a non-negative integer literal with an optional base prefix (but
    /// no +/- sign), using the narrowest type of int, long, or BigInteger capable of exactly
    /// representing the value.
    /// </summary>
    /// <exception cref="FormatException">if the string is not a valid literal.</exception>
    public static object Scan(string str)
    {
        string orig = str;
        int radix = 10;
        if (str.Length > 1 && str[0] == '0')
        {
            switch (str[1])
            {
                case 'x':
                case 'X':
                    radix = 16;
                    str = str.Substring(2);
                    break;
                case 'o':
                case 'O':
                    radix = 8;
                    str = str.Substring(2);
                    break;
                default:
                    throw new FormatException(
                        "invalid octal literal: " + str + " (use '0o" + str.Substring(1) + "')");
            }
        }

        if (TryParseLong(str, radix, out long v))
        {
            if (v == (int)v)
            {
                return (int)v;
            }
            return v;
        }
        if (TryParseBigInteger(str, radix, out BigInteger big))
        {
            return big;
        }
        throw new FormatException("invalid base-" + radix + " integer literal: " + orig);
    }

    private static bool TryParseLong(string str, int radix, out long result)
    {
        result = 0;
        if (str.Length == 0)
        {
            return false;
        }
        if (radix == 10)
        {
            return long.TryParse(str, NumberStyles.None, CultureInfo.InvariantCulture, out result);
        }
        try
        {
            long acc = 0;
            checked
            {
                foreach (char c in str)
                {
                    int digit = DigitValue(c);
                    if (digit < 0 || digit >= radix)
                    {
                        return false;
                    }
                    acc = acc * radix + digit;
                }
            }
            result = acc;
            return true;
        }
        catch (OverflowException)
        {
            return false;
        }
    }

    private static bool TryParseBigInteger(string str, int radix, out BigInteger result)
    {
        result = BigInteger.Zero;
        if (str.Length == 0)
        {
            return false;
        }
        BigInteger acc = BigInteger.Zero;
        BigInteger b = radix;
        foreach (char c in str)
        {
            int digit = DigitValue(c);
            if (digit < 0 || digit >= radix)
            {
                return false;
            }
            acc = acc * b + digit;
        }
        result = acc;
        return true;
    }

    private static int DigitValue(char c)
    {
        if (c >= '0' && c <= '9')
        {
            return c - '0';
        }
        if (c >= 'a' && c <= 'z')
        {
            return c - 'a' + 10;
        }
        if (c >= 'A' && c <= 'Z')
        {
            return c - 'A' + 10;
        }
        return -1;
    }
}
