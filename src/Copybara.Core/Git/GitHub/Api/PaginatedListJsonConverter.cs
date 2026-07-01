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

using System.Text.Json;
using System.Text.Json.Serialization;

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// Deserializes a JSON array directly into a <see cref="PaginatedList{T}"/>.
/// </summary>
/// <remarks>
/// <para>NOTE(port): upstream relies on gson deserializing into a subtype of <c>ArrayList</c>. Here
/// <see cref="PaginatedList{T}"/> is not itself a collection type recognized by
/// <c>System.Text.Json</c>, so this converter bridges the JSON array to the wrapper. Pagination
/// link-header info is attached later by the transport via
/// <see cref="PaginatedList{T}.WithPaginationInfo"/>.</para>
/// </remarks>
public sealed class PaginatedListJsonConverterFactory : JsonConverterFactory
{
    public override bool CanConvert(Type typeToConvert) =>
        typeToConvert.IsGenericType
        && typeToConvert.GetGenericTypeDefinition() == typeof(PaginatedList<>);

    public override JsonConverter CreateConverter(Type typeToConvert, JsonSerializerOptions options)
    {
        Type elementType = typeToConvert.GetGenericArguments()[0];
        Type converterType = typeof(PaginatedListJsonConverter<>).MakeGenericType(elementType);
        return (JsonConverter)Activator.CreateInstance(converterType)!;
    }
}

/// <summary>Converter for a specific <see cref="PaginatedList{T}"/> element type.</summary>
public sealed class PaginatedListJsonConverter<T> : JsonConverter<PaginatedList<T>>
{
    public override PaginatedList<T> Read(
        ref Utf8JsonReader reader, Type typeToConvert, JsonSerializerOptions options)
    {
        if (reader.TokenType == JsonTokenType.Null)
        {
            return new PaginatedList<T>();
        }

        var elements = JsonSerializer.Deserialize<List<T>>(ref reader, options) ?? new List<T>();
        return new PaginatedList<T>(elements);
    }

    public override void Write(
        Utf8JsonWriter writer, PaginatedList<T> value, JsonSerializerOptions options)
    {
        JsonSerializer.Serialize(writer, value.GetElements(), options);
    }
}
