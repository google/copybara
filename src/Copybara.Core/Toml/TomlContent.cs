/*
 * Copyright (C) 2023 Google Inc.
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

using Copybara.Exceptions;
using Starlark.Annot;
using Starlark.Eval;

using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara.Toml;

/// <summary>Represents parsed TOML content.</summary>
[StarlarkBuiltin("TomlContent", Doc = "Object containing parsed TOML values.")]
public sealed class TomlContent : IStarlarkValue
{
    private readonly IReadOnlyDictionary<string, object?> _parsedToml;

    internal TomlContent(IReadOnlyDictionary<string, object?> parsedToml)
    {
        _parsedToml = parsedToml;
    }

    [StarlarkMethod(
        "get",
        Doc = "Retrieve the value from the parsed TOML for the given key. "
            + "If the key is not defined, this will return None.")]
    public object? Get(
        [Param(Name = "key", Doc = "The dotted key expression", Named = true,
            AllowedTypes = new[] { typeof(string) })]
        string key)
    {
        try
        {
            return ConvertToStarlarkValue(Lookup(key));
        }
        catch (Exception e) when (e is ArgumentException or InvalidCastException)
        {
            throw new EvalException(
                $"There was an error retrieving the value for the given key {key}", e);
        }
    }

    [StarlarkMethod(
        "get_or_default",
        Doc = "Retrieve the value from the parsed TOML for the given key. "
            + "If the key is not defined, this will return the default value.")]
    public object? GetOrDefault(
        [Param(Name = "key", Doc = "The dotted key expression", Named = true,
            AllowedTypes = new[] { typeof(string) })]
        string key,
        [Param(Name = "default", Doc = "The default value to return if the key isn't found.",
            Named = true)]
        object? defaultValue)
    {
        try
        {
            object? value = ConvertToStarlarkValue(Lookup(key));
            if (Equals(value, StarlarkRt.None))
            {
                return defaultValue;
            }
            return value;
        }
        catch (Exception e) when (e is ArgumentException or InvalidCastException)
        {
            throw new EvalException(
                $"There was an error retrieving the value for the given key {key}", e);
        }
    }

    /// <summary>Resolves a dotted key expression against the parsed tree.</summary>
    private object? Lookup(string dottedKey)
    {
        object? node = _parsedToml;
        foreach (string part in dottedKey.Split('.'))
        {
            if (node is IReadOnlyDictionary<string, object?> dict && dict.TryGetValue(part, out var v))
            {
                node = v;
            }
            else
            {
                return null;
            }
        }
        return node;
    }

    /// <summary>
    /// Converts a parsed TOML value to an object that can be cast to a Starlark value.
    /// </summary>
    /// <remarks>
    /// Return type is <c>object</c> because strings and booleans are valid Starlark values despite
    /// not implementing <see cref="IStarlarkValue"/>.
    /// </remarks>
    private object ConvertToStarlarkValue(object? value)
    {
        switch (value)
        {
            case null:
                return StarlarkRt.None;
            case DateTimeOffset dto:
                return new StarlarkDateTimeModule.StarlarkDateTime(
                    dto.ToUnixTimeSeconds(), "UTC");
            case IReadOnlyList<object?> array:
            {
                var builder = new List<object?>(array.Count);
                foreach (object? item in array)
                {
                    builder.Add(ConvertToStarlarkValue(item));
                }
                return StarlarkList.ImmutableCopyOf(builder);
            }
            case IReadOnlyDictionary<string, object?> table:
            {
                var entries = new List<KeyValuePair<object?, object?>>(table.Count);
                foreach (var entry in table)
                {
                    entries.Add(new KeyValuePair<object?, object?>(
                        entry.Key, ConvertToStarlarkValue(entry.Value)));
                }
                return Dict.ImmutableCopyOf(entries);
            }
            case long l:
                return StarlarkInt.Of(l);
            case int i:
                return StarlarkInt.Of(i);
            default:
                // string, bool, double are valid Starlark values as-is.
                return value;
        }
    }
}
