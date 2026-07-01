// Copyright 2015 The Bazel Authors. All rights reserved.
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
using System.Text;

namespace Starlark.Eval;

/// <summary>
/// A printer of Starlark values. Port of <c>net.starlark.java.eval.Printer</c>.
/// </summary>
public class Printer
{
    private readonly StringBuilder buffer;

    // Stack of values in the middle of being printed (for cycle detection).
    private object?[]? stack;
    private int depth;

    public Printer(StringBuilder buffer) => this.buffer = buffer;

    public Printer() : this(new StringBuilder()) { }

    public Printer Append(char c)
    {
        buffer.Append(c);
        return this;
    }

    public Printer Append(string s)
    {
        buffer.Append(s);
        return this;
    }

    public Printer Append(string s, int start, int end)
    {
        buffer.Append(s, start, end - start);
        return this;
    }

    public Printer Append(int i)
    {
        buffer.Append(i.ToString(CultureInfo.InvariantCulture));
        return this;
    }

    public Printer Append(long l)
    {
        buffer.Append(l.ToString(CultureInfo.InvariantCulture));
        return this;
    }

    /// <summary>Appends a list to the buffer, each element rendered with repr.</summary>
    public virtual Printer PrintList(
        IEnumerable list, string before, string separator, string after, StarlarkSemantics semantics)
    {
        Append(before);
        string sep = "";
        foreach (object? elem in list)
        {
            Append(sep);
            sep = separator;
            Repr(elem, semantics);
        }
        return Append(after);
    }

    public override string ToString() => buffer.ToString();

    /// <summary>Appends the debug representation of a value.</summary>
    public Printer DebugPrint(object? o, StarlarkThread thread)
    {
        if (o is IStarlarkPrintableValue v)
        {
            v.DebugPrint(this, thread);
            return this;
        }
        return Str(o, thread.GetSemantics());
    }

    /// <summary>Appends the str representation of a value (strings unquoted at top level).</summary>
    public Printer Str(object? o, StarlarkSemantics semantics)
    {
        switch (o)
        {
            case string s:
                return Append(s);
            case IStarlarkPrintableValue v:
                v.Str(this, semantics);
                return this;
            default:
                return Repr(o, semantics);
        }
    }

    /// <summary>Appends the repr (quoted) representation of a value.</summary>
    public Printer Repr(object? o, StarlarkSemantics semantics)
    {
        // atomic values (leaves of the object graph)
        switch (o)
        {
            case null:
                return Append("null");
            case string s:
                return AppendQuoted(s);
            case StarlarkInt starlarkInt:
                starlarkInt.Repr(this, semantics);
                return this;
            case bool b:
                return Append(b ? "True" : "False");
            case int i:
                return Append(i); // a non-Starlark value
        }

        // compound values (may form cycles)
        if (!Push(o))
        {
            return Append("..."); // elided cycle
        }
        try
        {
            switch (o)
            {
                case IStarlarkPrintableValue value:
                    value.Repr(this, semantics);
                    break;
                case IDictionary map:
                    PrintList(map, "{", ", ", "}", semantics);
                    break;
                case DictionaryEntry entry:
                    Repr(entry.Key, semantics).Append(": ").Repr(entry.Value, semantics);
                    break;
                case IEnumerable list:
                    PrintList(list, "[", ", ", "]", semantics);
                    break;
                default:
                    Append(o.ToString() ?? "null");
                    break;
            }
        }
        finally
        {
            Pop();
        }
        return this;
    }

    private Printer AppendQuoted(string s)
    {
        Append('"');
        foreach (char c in s)
        {
            EscapeCharacter(c);
        }
        return Append('"');
    }

    private Printer BackslashChar(char c) => Append('\\').Append(c);

    private Printer EscapeCharacter(char c)
    {
        if (c == '"')
        {
            return BackslashChar(c);
        }
        switch (c)
        {
            case '\\':
                return BackslashChar('\\');
            case '\r':
                return BackslashChar('r');
            case '\n':
                return BackslashChar('n');
            case '\t':
                return BackslashChar('t');
            default:
                if (c < 32)
                {
                    return Append("\\x" + ((int)c).ToString("x2", CultureInfo.InvariantCulture));
                }
                return Append(c);
        }
    }

    private bool Push(object x)
    {
        for (int i = 0; i < depth; i++)
        {
            if (ReferenceEquals(x, stack![i]))
            {
                return false;
            }
        }
        if (stack == null)
        {
            stack = new object?[4];
        }
        else if (depth == stack.Length)
        {
            Array.Resize(ref stack, 2 * stack.Length);
        }
        stack[depth++] = x;
        return true;
    }

    private void Pop() => stack![--depth] = null;

    /// <summary>Appends a string, formatted as if by Starlark's <c>str % tuple</c> operator.</summary>
    public static void Format(
        Printer printer, StarlarkSemantics semantics, string format, params object?[] arguments) =>
        FormatWithList(printer, semantics, format, arguments);

    /// <summary>Same as Format, but with a list instead of variadic args.</summary>
    public static void FormatWithList(
        Printer printer, StarlarkSemantics semantics, string pattern, IReadOnlyList<object?> arguments)
    {
        int length = pattern.Length;
        int argLength = arguments.Count;
        int i = 0; // index of next character in pattern
        int a = 0; // index of next argument

        while (i < length)
        {
            int p = pattern.IndexOf('%', i);
            if (p == -1)
            {
                printer.Append(pattern, i, length);
                break;
            }
            if (p > i)
            {
                printer.Append(pattern, i, p);
            }
            if (p == length - 1)
            {
                throw new FormatException(
                    "incomplete format pattern ends with %: " + Starlark.Repr(pattern, semantics));
            }
            char conv = pattern[p + 1];
            i = p + 2;

            if (conv == '%')
            {
                printer.Append('%');
                continue;
            }

            if (a >= argLength)
            {
                throw new FormatException(
                    "not enough arguments for format pattern "
                        + Starlark.Repr(pattern, semantics)
                        + ": "
                        + Starlark.Repr(Tuple.CopyOf(arguments), semantics));
            }
            object? arg = arguments[a++];

            switch (conv)
            {
                case 'd':
                case 'o':
                case 'x':
                case 'X':
                    {
                        System.Numerics.BigInteger n = ToBigIntArg(arg, conv);
                        printer.Append(conv switch
                        {
                            'd' => n.ToString(CultureInfo.InvariantCulture),
                            'o' => ToOctal(n),
                            'x' => ToHex(n, false),
                            _ => ToHex(n, true),
                        });
                        break;
                    }

                case 'e':
                case 'f':
                case 'g':
                case 'E':
                case 'F':
                case 'G':
                    {
                        double v = arg switch
                        {
                            int integer => integer,
                            StarlarkInt si => si.ToDouble(),
                            StarlarkFloat sf => sf.ToDouble(),
                            _ => throw new FormatException(string.Format(
                                "got {0} for '%{1}' format, want int or float",
                                Starlark.Type(arg), conv)),
                        };
                        printer.Append(StarlarkFloat.Format(v, conv));
                        break;
                    }

                case 'r':
                    printer.Repr(arg, semantics);
                    break;

                case 's':
                    printer.Str(arg, semantics);
                    break;

                default:
                    throw new FormatException(string.Format(
                        "unsupported format character \"{0}\" at index {1} in {2}",
                        conv, p + 1, Starlark.Repr(pattern, semantics)));
            }
        }
        if (a < argLength)
        {
            throw new FormatException("not all arguments converted during string formatting");
        }
    }

    private static System.Numerics.BigInteger ToBigIntArg(object? arg, char conv)
    {
        switch (arg)
        {
            case StarlarkInt si:
                return si.ToBigInteger();
            case int integer:
                return integer;
            case StarlarkFloat sf:
                try
                {
                    return StarlarkFloat.FiniteDoubleToIntExact(sf.ToDouble()).ToBigInteger();
                }
                catch (ArgumentException)
                {
                    throw new FormatException("got " + arg + ", want a finite number");
                }
            default:
                throw new FormatException(string.Format(
                    "got {0} for '%{1}' format, want int or float", Starlark.Type(arg), conv));
        }
    }

    private static string ToOctal(System.Numerics.BigInteger n)
    {
        if (n.Sign < 0)
        {
            return "-" + ToOctal(-n);
        }
        if (n.IsZero)
        {
            return "0";
        }
        var sb = new StringBuilder();
        while (n > 0)
        {
            sb.Insert(0, (char)('0' + (int)(n % 8)));
            n /= 8;
        }
        return sb.ToString();
    }

    private static string ToHex(System.Numerics.BigInteger n, bool upper)
    {
        if (n.Sign < 0)
        {
            return "-" + ToHex(-n, upper);
        }
        string s = n.ToString(upper ? "X" : "x", CultureInfo.InvariantCulture);
        // BigInteger may prepend a leading 0 to keep sign positive; trim it.
        s = s.TrimStart('0');
        return s.Length == 0 ? "0" : s;
    }
}
