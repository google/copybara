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
package net.starlark.java.eval;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Arrays;
import java.util.IllegalFormatException;
import java.util.List;
import java.util.Map;
import java.util.MissingFormatWidthException;

/**
 * A printer of Starlark values.
 *
 * <p>Subclasses may override methods such as {@link #repr} and {@link #printList} to alter the
 * formatting behavior.
 */
// TODO(adonovan): disallow printing of objects that are not Starlark values.
public class Printer {

  private final StringBuilder buffer;

  // Stack of values in the middle of being printed.
  // Each renders as "..." if recursively encountered,
  // indicating a cycle.
  private Object[] stack;
  private int depth;

  /** Creates a printer that writes to the given buffer. */
  public Printer(StringBuilder buffer) {
    this.buffer = buffer;
  }

  /** Creates a printer that uses a fresh buffer. */
  public Printer() {
    this(new StringBuilder());
  }

  /** Appends a char to the printer's buffer */
  @CanIgnoreReturnValue
  public final Printer append(char c) {
    buffer.append(c);
    return this;
  }

  /** Appends a char sequence to the printer's buffer */
  @CanIgnoreReturnValue
  public final Printer append(CharSequence s) {
    buffer.append(s);
    return this;
  }

  /** Appends a char subsequence to the printer's buffer */
  @CanIgnoreReturnValue
  public final Printer append(CharSequence s, int start, int end) {
    buffer.append(s, start, end);
    return this;
  }

  /** Appends an integer to the printer's buffer */
  @CanIgnoreReturnValue
  public final Printer append(int i) {
    buffer.append(i);
    return this;
  }

  /** Appends a long integer to the printer's buffer */
  @CanIgnoreReturnValue
  public final Printer append(long l) {
    buffer.append(l);
    return this;
  }

  /**
   * Appends a list to the printer's buffer. List elements are rendered with {@code repr}.
   *
   * <p>May be overridden by subclasses.
   *
   * @param list the list of objects to repr (each as with repr)
   * @param before a string to print before the list items, e.g. an opening bracket
   * @param separator a separator to print between items
   * @param after a string to print after the list items, e.g. a closing bracket
   */
  public Printer printList(Iterable<?> list, String before, String separator, String after) {
    this.append(before);
    String sep = "";
    for (Object elem : list) {
      this.append(sep);
      sep = separator;
      this.repr(elem);
    }
    return this.append(after);
  }

  @Override
  public final String toString() {
    return buffer.toString();
  }

  /**
   * Appends the {@code StarlarkValue.debugPrint} representation of a value (as used by the Starlark
   * {@code print} statement) to the printer's buffer.
   *
   * <p>Implementations of StarlarkValue may define their own behavior of {@code debugPrint}.
   */
  public Printer debugPrint(Object o, StarlarkThread thread) {
    if (o instanceof StarlarkValue) {
      ((StarlarkValue) o).debugPrint(this, thread);
      return this;
    }

    return this.str(o, thread.getSemantics());
  }

  /**
   * Appends the {@code StarlarkValue.str} representation of a value to the printer's buffer. Unlike
   * {@code repr(x)}, it does not quote strings at top level, though strings and other values
   * appearing as elements of other structures are quoted as if by {@code repr}.
   *
   * <p>Implementations of StarlarkValue may define their own behavior of {@code str}.
   */
  public Printer str(Object o, StarlarkSemantics semantics) {
    if (o instanceof String) {
      return this.append((String) o);

    } else if (o instanceof StarlarkValue) {
      ((StarlarkValue) o).str(this, semantics);
      return this;

    } else {
      return this.repr(o);
    }
  }

  /**
   * Appends the {@code StarlarkValue.repr} (quoted) representation of a value to the printer's
   * buffer. The quoted form is often a Starlark expression that evaluates to the value.
   *
   * <p>Implementations of StarlarkValue may define their own behavior of {@code repr}.
   *
   * <p>Cyclic values are rendered as {@code ...} if they are recursively encountered by the same
   * printer. (Implementations of {@link StarlarkValue#repr} should avoid calling {@code
   * Starlark#repr}, as it creates another printer, which hide cycles from the cycle detector.)
   *
   * <p>In addition to Starlark values, {@code repr} also prints instances of classes Map, List,
   * Map.Entry, or Class. All other values are formatted using their {@code toString} method.
   * TODO(adonovan): disallow that.
   */
  public Printer repr(Object o) {
    // atomic values (leaves of the object graph)
    if (o == null) {
      // Java null is not a valid Starlark value, but sometimes printers are used on non-Starlark
      // values such as Locations or Nodes.
      return this.append("null");

    } else if (o instanceof String) {
      appendQuoted((String) o);
      return this;

    } else if (o instanceof StarlarkInt) {
      ((StarlarkInt) o).repr(this);
      return this;

    } else if (o instanceof Boolean) {
      this.append(((boolean) o) ? "True" : "False");
      return this;

    } else if (o instanceof Integer) { // a non-Starlark value
      this.buffer.append((int) o);
      return this;

    } else if (o instanceof Class) { // a non-Starlark value
      this.append(Starlark.classType((Class<?>) o));
      return this;
    }

    // compound values (may form cycles in the object graph)

    if (!push(o)) {
      return this.append("..."); // elided cycle
    }
    try {
      if (o instanceof StarlarkValue) {
        ((StarlarkValue) o).repr(this);

        // -- non-Starlark values --

      } else if (o instanceof Map) {
        Map<?, ?> dict = (Map<?, ?>) o;
        this.printList(dict.entrySet(), "{", ", ", "}");

      } else if (o instanceof List) {
        this.printList((List) o, "[", ", ", "]");

      } else if (o instanceof Map.Entry) {
        Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
        this.repr(entry.getKey());
        this.append(": ");
        this.repr(entry.getValue());

      } else {
        // All other non-Starlark Java values (e.g. Node, Location).
        // Starlark code cannot access values of o that would reach here,
        // and native code is already trusted to be deterministic.
        this.append(o.toString());
      }
    } finally {
      pop();
    }

    return this;
  }

  private Printer appendQuoted(String s) {
    this.append('"');
    int len = s.length();
    for (int i = 0; i < len; i++) {
      char c = s.charAt(i);
      escapeCharacter(c);
    }
    return this.append('"');
  }

  private Printer backslashChar(char c) {
    return this.append('\\').append(c);
  }

  private Printer escapeCharacter(char c) {
    if (c == '"') {
      return backslashChar(c);
    }
    switch (c) {
      case '\\':
        return backslashChar('\\');
      case '\r':
        return backslashChar('r');
      case '\n':
        return backslashChar('n');
      case '\t':
        return backslashChar('t');
      default:
        if (c < 32) {
          // TODO(bazel-team): support \x escapes
          return this.append(String.format("\\x%02x", (int) c));
        }
        return this.append(c); // no need to support UTF-8
    }
  }

  // Reports whether x is already present on the visitation stack, pushing it if not.
  private boolean push(Object x) {
    // cyclic?
    for (int i = 0; i < depth; i++) {
      if (x == stack[i]) {
        return false;
      }
    }

    if (stack == null) {
      this.stack = new Object[4];
    } else if (depth == stack.length) {
      this.stack = Arrays.copyOf(stack, 2 * stack.length);
    }
    this.stack[depth++] = x;
    return true;
  }

  private void pop() {
    this.stack[--depth] = null;
  }

  /**
   * Appends a string, formatted as if by Starlark's {@code str % tuple} operator, to the printer's
   * buffer.
   *
   * <p>Supported conversions:
   *
   * <ul>
   *   <li>{@code %s} (convert as if by {@code str()})
   *   <li>{@code %r} (convert as if by {@code repr()})
   *   <li>{@code %d} (convert an integer to its decimal representation)
   * </ul>
   *
   * To encode a literal percent character, escape it as {@code %%}. It is an error to have a
   * non-escaped {@code %} at the end of the string or followed by any character not listed above.
   *
   * @param format the format string
   * @param arguments an array containing arguments to substitute into the format operators in order
   * @throws IllegalFormatException if the format string is invalid or the arguments do not match it
   */
  public static void format(
      Printer printer, StarlarkSemantics semantics, String format, Object... arguments) {
    formatWithList(printer, semantics, format, Arrays.asList(arguments));
  }

  /** Same as {@link #format}, but with a list instead of variadic args. */
  @SuppressWarnings("FormatString") // see b/178189609
  public static void formatWithList(
      Printer printer, StarlarkSemantics semantics, String pattern, List<?> arguments) {
    // N.B. MissingFormatWidthException is the only kind of IllegalFormatException
    // whose constructor can take and display arbitrary error message, hence its use below.
    // TODO(adonovan): this suggests we're using the wrong exception. Throw IAE?

    int length = pattern.length();
    int argLength = arguments.size();
    int i = 0; // index of next character in pattern
    int a = 0; // index of next argument in arguments

    while (i < length) {
      int p = pattern.indexOf('%', i);
      if (p == -1) {
        printer.append(pattern, i, length);
        break;
      }
      if (p > i) {
        printer.append(pattern, i, p);
      }
      if (p == length - 1) {
        throw new MissingFormatWidthException(
            "incomplete format pattern ends with %: " + Starlark.repr(pattern));
      }
      char conv = pattern.charAt(p + 1);
      i = p + 2;

      // %%: literal %
      if (conv == '%') {
        printer.append('%');
        continue;
      }

      // get argument
      if (a >= argLength) {
        throw new MissingFormatWidthException(
            "not enough arguments for format pattern "
                + Starlark.repr(pattern)
                + ": "
                + Starlark.repr(Tuple.copyOf(arguments)));
      }
      Object arg = arguments.get(a++);

      switch (conv) {
        case 'd':
        case 'o':
        case 'x':
        case 'X':
          {
            Number n;
            if (arg instanceof StarlarkInt) {
              n = ((StarlarkInt) arg).toNumber();
            } else if (arg instanceof Integer) {
              n = (Number) arg;
            } else if (arg instanceof StarlarkFloat) {
              double d = ((StarlarkFloat) arg).toDouble();
              try {
                n = StarlarkInt.ofFiniteDouble(d).toNumber();
              } catch (IllegalArgumentException unused) {
                throw new MissingFormatWidthException("got " + arg + ", want a finite number");
              }
            } else {
              throw new MissingFormatWidthException(
                  String.format(
                      "got %s for '%%%c' format, want int or float", Starlark.type(arg), conv));
            }
            printer.append(
                String.format(
                    conv == 'd' ? "%d" : conv == 'o' ? "%o" : conv == 'x' ? "%x" : "%X", n));
            continue;
          }

        case 'e':
        case 'f':
        case 'g':
        case 'E':
        case 'F':
        case 'G':
          double v;
          if (arg instanceof Integer) {
            v = (double) (Integer) arg;
          } else if (arg instanceof StarlarkInt) {
            v = ((StarlarkInt) arg).toDouble();
          } else if (arg instanceof StarlarkFloat) {
            v = ((StarlarkFloat) arg).toDouble();
          } else {
            throw new MissingFormatWidthException(
                String.format(
                    "got %s for '%%%c' format, want int or float", Starlark.type(arg), conv));
          }
          printer.append(StarlarkFloat.format(v, conv));
          continue;

        case 'r':
          printer.repr(arg);
          continue;

        case 's':
          printer.str(arg, semantics);
          continue;

        default:
          // The call to Starlark.repr doesn't cause an infinite recursion
          // because it's only used to format a string properly.
          throw new MissingFormatWidthException(
              String.format(
                  "unsupported format character \"%s\" at index %s in %s",
                  String.valueOf(conv), p + 1, Starlark.repr(pattern)));
      }
    }
    if (a < argLength) {
      throw new MissingFormatWidthException("not all arguments converted during string formatting");
    }
  }
}
