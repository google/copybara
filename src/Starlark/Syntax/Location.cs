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

using System.Text;

namespace Starlark.Syntax;

/// <summary>
/// A Location denotes a position within a Starlark file.
///
/// <para>A location is a triple <c>(file, line, column)</c>, where <c>file</c> is the apparent name
/// of the file, <c>line</c> is the optional 1-based line number, and <c>column</c> is the optional
/// 1-based column number measured in UTF-16 code units. If the column is zero it is not displayed.
/// If the line number is also zero, it too is not displayed; in this case, the location denotes the
/// file as a whole.</para>
/// </summary>
public sealed class Location : IComparable<Location>
{
    private readonly string file;
    private readonly int line;
    private readonly int column;

    public Location(string file, int line, int column)
    {
        this.file = file ?? throw new ArgumentNullException(nameof(file));
        this.line = line;
        this.column = column;
    }

    /// <summary>Returns the name of the file containing this location.</summary>
    public string File => file;

    /// <summary>Returns the line number of this location.</summary>
    public int Line => line;

    /// <summary>Returns the column number of this location.</summary>
    public int Column => column;

    /// <summary>
    /// Returns a Location for the given file, line and column. If <c>column</c> is non-zero,
    /// <c>line</c> too must be non-zero.
    /// </summary>
    public static Location FromFileLineColumn(string file, int line, int column)
    {
        if (line == 0 && column != 0)
        {
            throw new ArgumentException("non-zero column but no line number");
        }
        return new Location(file, line, column);
    }

    /// <summary>Returns a Location for the file as a whole.</summary>
    public static Location FromFile(string file) => new(file, 0, 0);

    /// <summary>
    /// Formats the location as <c>"file:line:col"</c>. If the column is zero, it is omitted. If the
    /// line is also zero, it too is omitted.
    /// </summary>
    public override string ToString()
    {
        var buf = new StringBuilder();
        buf.Append(file);
        if (line != 0)
        {
            buf.Append(':').Append(line);
            if (column != 0)
            {
                buf.Append(':').Append(column);
            }
        }
        return buf.ToString();
    }

    /// <summary>Returns a three-valued lexicographical comparison of two Locations.</summary>
    public int CompareTo(Location? that)
    {
        if (that is null)
        {
            return 1;
        }
        int cmp = string.CompareOrdinal(this.file, that.file);
        if (cmp != 0)
        {
            return cmp;
        }
        long a = ((long)this.line << 32) | (uint)this.column;
        long b = ((long)that.line << 32) | (uint)that.column;
        return a.CompareTo(b);
    }

    public override int GetHashCode() => 97 * file.GetHashCode() + 37 * line + column;

    public override bool Equals(object? that)
    {
        return ReferenceEquals(this, that)
            || (that is Location other
                && this.file == other.file
                && this.line == other.line
                && this.column == other.column);
    }

    /// <summary>A location for built-in functions.</summary>
    public static readonly Location BUILTIN = FromFile("<builtin>");
}
