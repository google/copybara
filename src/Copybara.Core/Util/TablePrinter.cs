/*
 * Copyright (C) 2019 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

using System.Collections.Immutable;

namespace Copybara.Util;

/// <summary>
/// Utility for printing tabular data to an ASCII string. Currently only supports single-line cells.
/// Port of <c>com.google.copybara.util.TablePrinter</c>.
/// </summary>
public class TablePrinter
{
    private readonly ImmutableArray<string> _headers;
    private readonly List<IReadOnlyList<string>> _rows = new();
    private readonly int[] _columnWidths;

    public TablePrinter(params string[] header)
    {
        _headers = header.ToImmutableArray();
        _columnWidths = new int[header.Length];
        for (int col = 0; col < header.Length; col++)
        {
            _columnWidths[col] = header[col].Length;
        }
    }

    /// <summary>Add a row, which must have the same number of elements as the header.</summary>
    public TablePrinter AddRow(params object?[] row)
    {
        if (row.Length != _headers.Length)
        {
            throw new ArgumentException(string.Format(
                "Wrong number of values in row; expected {0}. Got: {1}", _headers.Length, row.Length));
        }
        // null friendly, no line breaks.
        var strings = row
            .Select(o => ("" + o).Replace("\n", ""))
            .ToImmutableArray();
        _rows.Add(strings);
        for (int col = 0; col < strings.Length; col++)
        {
            _columnWidths[col] = Math.Max(strings[col].Length, _columnWidths[col]);
        }
        return this;
    }

    /// <summary>Build the table.</summary>
    public IReadOnlyList<string> Build()
    {
        var lines = new List<string>
        {
            PrintRow('+', '-', ImmutableArray<string>.Empty),
            PrintRow('|', ' ', _headers),
            PrintRow('+', '-', ImmutableArray<string>.Empty),
        };
        foreach (var row in _rows)
        {
            lines.Add(PrintRow('|', ' ', row));
        }
        lines.Add(PrintRow('+', '-', ImmutableArray<string>.Empty));
        return lines;
    }

    /// <summary>Build the table into a single newline-joined string.</summary>
    public string Print() => string.Join('\n', Build());

    private string PrintRow(char delim, char filler, IReadOnlyList<string> vals)
    {
        var paddedVals = new List<string>();
        for (int col = 0; col < _columnWidths.Length; col++)
        {
            string val = vals.Count > col ? vals[col] : "";
            paddedVals.Add(PadEnd(val, _columnWidths[col] + 1, filler));
        }
        return delim + string.Join(delim, paddedVals) + delim;
    }

    private static string PadEnd(string s, int minLength, char padChar) =>
        s.Length >= minLength ? s : s + new string(padChar, minLength - s.Length);
}
