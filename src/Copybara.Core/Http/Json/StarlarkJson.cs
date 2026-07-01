/*
 * Copyright (C) 2023 Google LLC.
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

using System.Text.Json;
using Starlark.Eval;

namespace Copybara.Http.Json;

/// <summary>
/// Serializes Starlark values to JSON. The Java implementation relies on the Google http-client
/// gson factory; this port walks the Starlark value graph and emits JSON via
/// <c>System.Text.Json</c>.
/// </summary>
internal static class StarlarkJson
{
    public static string Serialize(object? data)
    {
        using var stream = new MemoryStream();
        using (var writer = new Utf8JsonWriter(stream))
        {
            Write(writer, data);
        }

        return System.Text.Encoding.UTF8.GetString(stream.ToArray());
    }

    private static void Write(Utf8JsonWriter writer, object? value)
    {
        switch (value)
        {
            case null:
            case NoneType:
                writer.WriteNullValue();
                break;
            case bool b:
                writer.WriteBooleanValue(b);
                break;
            case string s:
                writer.WriteStringValue(s);
                break;
            case StarlarkInt i:
                writer.WriteNumberValue(i.ToBigInteger() is var big
                    && big >= long.MinValue && big <= long.MaxValue
                        ? (decimal)(long)big
                        : (decimal)big);
                break;
            case StarlarkFloat f:
                writer.WriteNumberValue(f.ToDouble());
                break;
            case Dict dict:
                writer.WriteStartObject();
                foreach (var entry in dict.Entries)
                {
                    writer.WritePropertyName(entry.Key?.ToString() ?? "null");
                    Write(writer, entry.Value);
                }

                writer.WriteEndObject();
                break;
            case System.Collections.IEnumerable seq:
                writer.WriteStartArray();
                foreach (var item in seq)
                {
                    Write(writer, item);
                }

                writer.WriteEndArray();
                break;
            default:
                writer.WriteStringValue(value.ToString());
                break;
        }
    }
}
