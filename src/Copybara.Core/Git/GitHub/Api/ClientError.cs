/*
 * Copyright (C) 2018 Google Inc.
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
using System.Text.Json;
using System.Text.Json.Serialization;

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// GitHub client errors always have a message and sometimes a documentation_url.
/// </summary>
public class ClientError
{
    [JsonPropertyName("message")]
    public string? Message { get; set; }

    [JsonPropertyName("documentation_url")]
    public string? DocumentationUrl { get; set; }

    [JsonPropertyName("errors")]
    public List<ErrorItem>? Errors { get; set; }

    public string? GetMessage() => Message;

    public string? GetDocumentationUrl() => DocumentationUrl;

    public IReadOnlyList<ErrorItem> GetErrors() =>
        Errors == null ? ImmutableArray<ErrorItem>.Empty : Errors.ToImmutableArray();

    public override string ToString()
    {
        try
        {
            return JsonSerializer.Serialize(this);
        }
        catch (Exception e)
        {
            throw new InvalidOperationException("Unexpected error: ", e);
        }
    }

    /// <summary>An individual error entry within a <see cref="ClientError"/>.</summary>
    public class ErrorItem
    {
        [JsonPropertyName("resource")]
        public string? Resource { get; set; }

        [JsonPropertyName("field")]
        public string? Field { get; set; }

        [JsonPropertyName("code")]
        public ErrorType Code { get; set; }

        [JsonPropertyName("message")]
        public string? Message { get; set; }

        public string? GetResource() => Resource;

        public string? GetField() => Field;

        public ErrorType GetCode() => Code;

        public string? GetMessage() => Message;
    }
}

/// <summary>Error type codes reported by GitHub client errors.</summary>
[JsonConverter(typeof(JsonStringEnumConverter<ErrorType>))]
public enum ErrorType
{
    [JsonStringEnumMemberName("missing")]
    MISSING,

    [JsonStringEnumMemberName("missing_field")]
    MISSING_FIELD,

    [JsonStringEnumMemberName("invalid")]
    INVALID,

    [JsonStringEnumMemberName("already_exists")]
    ALREADY_EXISTS,

    [JsonStringEnumMemberName("custom")]
    CUSTOM,
}
