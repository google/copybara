/*
 * Copyright (C) 2016 Google Inc.
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
using Copybara.Common;

namespace Copybara.Util;

/// <summary>
/// A single changed file, as reported by <c>git diff --name-status</c>. Port of the nested
/// <c>com.google.copybara.util.DiffUtil.DiffFile</c> type (lifted to a top-level type in the C#
/// port).
/// </summary>
public sealed class DiffFile
{
    private readonly string _name;
    private readonly Operation _operation;

    internal static readonly ImmutableDictionary<string, Operation> OpByChar =
        Enum.GetValues<Operation>().ToImmutableDictionary(CharType);

    public DiffFile(string name, Operation operation)
    {
        _name = Preconditions.CheckNotNull(name);
        _operation = operation;
    }

    public string GetName() => _name;

    public Operation GetOperation() => _operation;

    /// <summary>Git Diff status letters.</summary>
    public enum Operation
    {
        ADD,
        DELETE,
        MODIFIED,
        COPY,
        RENAME,
        TYPE_CHANGE,
        UNMERGED,
        // X is omitted because it indicates a bug
    }

    /// <summary>Returns the single-letter git status character for the operation.</summary>
    internal static string CharType(Operation op) => op switch
    {
        Operation.ADD => "A",
        Operation.DELETE => "D",
        Operation.MODIFIED => "M",
        Operation.COPY => "C",
        Operation.RENAME => "R",
        Operation.TYPE_CHANGE => "T",
        Operation.UNMERGED => "U",
        _ => throw new ArgumentOutOfRangeException(nameof(op), op, null),
    };

    public override string ToString() =>
        $"DiffFile{{name={_name}, operation={_operation}}}";
}
