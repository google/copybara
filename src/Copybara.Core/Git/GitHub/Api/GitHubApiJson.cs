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

using System.Text.Json;

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// Shared <see cref="JsonSerializerOptions"/> for the GitHub REST API client.
/// </summary>
/// <remarks>
/// Registers the <see cref="PaginatedListJsonConverterFactory"/> so bare-array endpoints deserialize
/// into <see cref="PaginatedList{T}"/>. Enum wire values are governed per-enum by
/// <c>[JsonConverter(typeof(JsonStringEnumConverter&lt;T&gt;))]</c> plus
/// <c>[JsonStringEnumMemberName]</c>, matching the Java gson <c>@Value</c> annotations. Property
/// names are declared explicitly on each type via <c>[JsonPropertyName]</c> to match the Java
/// <c>@Key</c> wire format exactly, so no global naming policy is applied. Null-valued request
/// members are omitted, mirroring gson's default behavior for the request POJOs.
/// </remarks>
public static class GitHubApiJson
{
    public static readonly JsonSerializerOptions Options = CreateOptions();

    private static JsonSerializerOptions CreateOptions()
    {
        var options = new JsonSerializerOptions
        {
            DefaultIgnoreCondition = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
            PropertyNameCaseInsensitive = false,
            ReadCommentHandling = JsonCommentHandling.Skip,
            AllowTrailingCommas = true,
        };
        options.Converters.Add(new PaginatedListJsonConverterFactory());
        return options;
    }
}
