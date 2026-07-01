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

using Copybara.Common;
using Starlark.Eval;
using Console = Copybara.Util.Console.Console;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara.Config;

/// <summary>
/// Utilities for dealing with Skylark parameter objects and converting them to Java ones.
/// </summary>
public static class SkylarkUtil
{
    /// <summary>
    /// Converts an object that can be the NoneType to the actual object if it is not, or returns the
    /// default value if none.
    /// </summary>
    public static T? ConvertFromNoneable<T>(object? obj, T? defaultValue)
    {
        if (StarlarkRt.IsNullOrNone(obj))
        {
            return defaultValue;
        }
        return (T)obj!; // wildly unsound cast, matching upstream
    }

    /// <summary>
    /// Converts a noneable Starlark object into a nullable reference. If the object is null or None,
    /// it will be converted to null.
    ///
    /// <para>Upstream returns <c>Optional&lt;T&gt;</c>; this port uses nullable per project
    /// conventions.</para>
    /// </summary>
    public static T? ConvertToOptional<T>(object? obj)
        where T : class =>
        ConvertFromNoneable<T?>(obj, null);

    /// <summary>Converts a string to the corresponding enum or fails if invalid value.</summary>
    /// <exception cref="EvalException">if the value is not a valid enum member</exception>
    public static T StringToEnum<T>(string fieldName, string value)
        where T : struct, Enum
    {
        if (Enum.TryParse(value, out T result) && Enum.IsDefined(result))
        {
            return result;
        }
        throw StarlarkRt.Errorf(
            "Invalid value '{0}' for field '{1}'. Valid values are: {2}",
            value, fieldName, string.Join(", ", Enum.GetNames<T>()));
    }

    /// <summary>Converts a sequence of strings to a list of the corresponding enum values.</summary>
    /// <exception cref="EvalException">if any string cannot be cast to the enum</exception>
    public static IReadOnlyList<T> StringListToEnumList<T>(
        IEnumerable<string> sequence, string fieldName, Console console)
        where T : struct, Enum
    {
        var list = sequence.ToList();
        try
        {
            return list.Select(value =>
            {
                if (Enum.TryParse(value, out T result) && Enum.IsDefined(result))
                {
                    return result;
                }
                throw new ArgumentException(value);
            }).ToList();
        }
        catch (ArgumentException e)
        {
            console.ErrorFmt(
                "Failed to convert list of strings '{0}' to list of enums. Cause: {1}",
                string.Join(", ", list), e.Message);
            throw StarlarkRt.Errorf(
                "Invalid value '{0}' for field '{1}'. Valid values are: {2}",
                string.Join(", ", list), fieldName, string.Join(", ", Enum.GetNames<T>()));
        }
    }

    /// <summary>Checks that a mandatory string field is not empty.</summary>
    /// <exception cref="EvalException">if the value is null or empty</exception>
    public static string CheckNotEmpty(string? value, string name)
    {
        Check(!string.IsNullOrEmpty(value), "Invalid empty field '{0}'.", name);
        return value!;
    }

    /// <summary>Checks a condition or throws <see cref="EvalException"/>.</summary>
    /// <exception cref="EvalException">if the condition is false</exception>
    public static void Check(bool condition, string format, params object?[] args)
    {
        if (!condition)
        {
            throw StarlarkRt.Errorf(format, args);
        }
    }

    /// <summary>
    /// Converts a Starlark sequence value (such as a list or tuple) to a list of strings. The result
    /// is a new, mutable copy. It throws EvalException if x is not a Starlark iterable or if any of
    /// its elements are not strings. The message argument is prefixed to any error message.
    /// </summary>
    /// <exception cref="EvalException">if x is not a sequence or an element is not a string</exception>
    public static List<string> ConvertStringList(object? x, string message)
    {
        if (x is not ISequence<object?> seq)
        {
            throw StarlarkRt.Errorf("{0}: got {1}, want sequence", message, StarlarkRt.Type(x));
        }

        var result = new List<string>();
        foreach (object? elem in seq)
        {
            if (elem is not string s)
            {
                throw StarlarkRt.Errorf(
                    "{0}: at index #{1}, got {2}, want string",
                    message, result.Count, StarlarkRt.Type(elem));
            }
            result.Add(s);
        }
        return result;
    }

    /// <summary>
    /// Converts a Starlark dict value to a map of strings to strings. The result is a new, mutable
    /// copy. It throws EvalException if x is not a Starlark dict or if any of its keys or values are
    /// not strings. The message argument is prefixed to any error message.
    /// </summary>
    /// <exception cref="EvalException">if x is not a dict or a key/value is not a string</exception>
    public static Dictionary<string, string> ConvertStringMap(object? x, string message)
    {
        if (x is not Dict dict)
        {
            throw StarlarkRt.Errorf("{0}: got {1}, want dict", message, StarlarkRt.Type(x));
        }
        var result = new Dictionary<string, string>();
        foreach (var e in dict.Entries)
        {
            if (e.Key is not string key)
            {
                throw StarlarkRt.Errorf(
                    "{0}: in dict key, got {1}, want string", message, StarlarkRt.Type(e.Key));
            }
            if (e.Value is not string value)
            {
                throw StarlarkRt.Errorf(
                    "{0}: in value for dict key '{1}', got {2}, want string",
                    message, e.Key, StarlarkRt.Type(e.Value));
            }
            result[key] = value;
        }
        return result;
    }

    /// <summary>
    /// Converts a Starlark optional string value (string or None) to a nullable String reference.
    /// </summary>
    public static string? ConvertOptionalString(object? x) =>
        StarlarkRt.IsNullOrNone(x) ? null : (string)x!;
}
