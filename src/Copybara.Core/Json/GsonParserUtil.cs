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

using System.Net;
using System.Net.Http;
using System.Text;
using System.Text.Json;

namespace Copybara.Json;

/// <summary>
/// Utility class for parsing remote API JSON responses, with special handling if a malformed JSON
/// document is encountered.
/// </summary>
/// <remarks>
/// <para>NOTE(port): upstream uses google-http-client + gson. This port uses BCL
/// <see cref="HttpResponseMessage"/> and <see cref="System.Text.Json"/>. When a parse error occurs
/// we surface the raw content in the exception, as upstream does.</para>
/// </remarks>
public static class GsonParserUtil
{
    /// <summary>Gerrit's "magic prefix line" that guards JSON responses against XSSI.</summary>
    public const string GsonNoExecutePrefix = ")]}'\n";

    /// <summary>Options mirroring gson's lenient behavior reasonably closely.</summary>
    private static readonly JsonSerializerOptions Options = new()
    {
        PropertyNameCaseInsensitive = true,
        ReadCommentHandling = JsonCommentHandling.Skip,
        AllowTrailingCommas = true,
    };

    /// <summary>
    /// Parses an <see cref="HttpResponseMessage"/>, with special handling if malformed JSON is
    /// encountered. Returns <c>null</c> for empty (no-content) responses.
    /// </summary>
    /// <exception cref="ArgumentException">if the JSON can't be parsed to the given type.</exception>
    public static async Task<T?> ParseHttpResponseAsync<T>(
        HttpResponseMessage response,
        bool stripNoExecutePrefix)
    {
        if (await IsResponseEmptyAsync(response).ConfigureAwait(false))
        {
            return default;
        }

        byte[] bytes = await response.Content.ReadAsByteArrayAsync().ConfigureAwait(false);
        Encoding charset = GetCharset(response);

        return ParseBytes<T>(bytes, charset, stripNoExecutePrefix);
    }

    private static async Task<bool> IsResponseEmptyAsync(HttpResponseMessage response)
    {
        if (response.StatusCode == HttpStatusCode.NoContent)
        {
            return true;
        }

        // Peek at the content length; if unknown, buffer and check.
        if (response.Content.Headers.ContentLength is { } len)
        {
            return len == 0;
        }

        byte[] bytes = await response.Content.ReadAsByteArrayAsync().ConfigureAwait(false);
        return bytes.Length == 0;
    }

    private static Encoding GetCharset(HttpResponseMessage response)
    {
        string? charsetName = response.Content.Headers.ContentType?.CharSet;
        if (!string.IsNullOrEmpty(charsetName))
        {
            try
            {
                return Encoding.GetEncoding(charsetName);
            }
            catch (ArgumentException)
            {
                // Fall through to UTF-8.
            }
        }

        return Encoding.UTF8;
    }

    /// <summary>
    /// Parses a string, with special handling if malformed JSON is encountered.
    /// </summary>
    /// <exception cref="ArgumentException">if the JSON can't be parsed to the given type.</exception>
    public static T? ParseString<T>(string @string, bool stripNoExecutePrefix)
    {
        var charset = Encoding.UTF8;
        return ParseBytes<T>(charset.GetBytes(@string), charset, stripNoExecutePrefix);
    }

    /// <summary>
    /// Parses a byte array, with special handling if malformed JSON is encountered.
    /// </summary>
    /// <exception cref="ArgumentException">if the JSON can't be parsed to the given type.</exception>
    public static T? ParseBytes<T>(byte[] bytes, Encoding charset, bool stripNoExecutePrefix)
    {
        ReadOnlySpan<byte> span = bytes;

        if (stripNoExecutePrefix)
        {
            byte[] prefix = charset.GetBytes(GsonNoExecutePrefix);
            if (span.Length >= prefix.Length && span.Slice(0, prefix.Length).SequenceEqual(prefix))
            {
                span = span.Slice(prefix.Length);
            }
        }

        try
        {
            string json = charset.GetString(span);
            return JsonSerializer.Deserialize<T>(json, Options);
        }
        catch (Exception e) when (e is JsonException or ArgumentException or NotSupportedException)
        {
            throw new ArgumentException(
                string.Format(
                    "Cannot parse content as type {0}.\nContent: {1}\n",
                    typeof(T), charset.GetString(bytes)),
                e);
        }
    }
}
