/*
 * Copyright (C) 2022 Google Inc.
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
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara;

/// <summary><c>struct()</c> constructor.</summary>
[StarlarkBuiltin("struct", Doc = "Immutable struct type.")]
public sealed class StructModule : IStarlarkValue
{
    [StarlarkMethod("constructor",
        Doc =
            "Creates a new immutable struct. Structs with the same keys/values are equal. The "
            + "struct's keys and values are passed in as keyword arguments.",
        SelfCall = true)]
    public StructImpl Create(
        [Param(Name = "kwargs", DefaultValue = "{}", Doc = "Dictionary of Args.")]
        Dict kwargs)
    {
        var builder = ImmutableDictionary.CreateBuilder<string, object?>();
        foreach (var e in kwargs.Entries)
        {
            builder[(string)e.Key!] = e.Value;
        }

        return new StructImpl(builder.ToImmutable());
    }

    /// <summary>Trivial struct implementation based on an immutable map.</summary>
    public sealed class StructImpl : IStructure, IStarlarkPrintableValue
    {
        private readonly ImmutableDictionary<string, object?> _dict;

        public StructImpl(ImmutableDictionary<string, object?> dict)
        {
            _dict = Preconditions.CheckNotNull(dict);
        }

        public object? GetValue(string name)
        {
            if (!_dict.ContainsKey(name))
            {
                throw new EvalException(GetErrorMessageForUnknownField(name)!);
            }

            return _dict[name];
        }

        public IReadOnlyCollection<string> GetFieldNames() => _dict.Keys.ToImmutableArray();

        public string? GetErrorMessageForUnknownField(string field) =>
            string.Format(
                "Field {0} is unknown, available fields are {1}.",
                field, string.Join(", ", _dict.Keys));

        public void Repr(Printer printer, StarlarkSemantics semantics)
        {
            printer.Append("struct(");
            string sep = "";
            foreach (var e in _dict)
            {
                printer.Append(sep).Append(e.Key).Append('=').Repr(e.Value, semantics);
                sep = ", ";
            }

            printer.Append(")");
        }

        public bool IsImmutable() => true;

        public override bool Equals(object? other) =>
            other is StructImpl s && DictEquals(_dict, s._dict);

        public override int GetHashCode()
        {
            int h = 0;
            foreach (var e in _dict)
            {
                h ^= HashCode.Combine(e.Key, e.Value);
            }

            return h;
        }

        private static bool DictEquals(
            ImmutableDictionary<string, object?> a, ImmutableDictionary<string, object?> b)
        {
            if (a.Count != b.Count)
            {
                return false;
            }

            foreach (var e in a)
            {
                if (!b.TryGetValue(e.Key, out var v) || !Equals(e.Value, v))
                {
                    return false;
                }
            }

            return true;
        }
    }
}
