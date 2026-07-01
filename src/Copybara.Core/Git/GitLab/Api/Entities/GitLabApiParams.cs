/*
 * Copyright (C) 2025 Google LLC.
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

using System.Text;

namespace Copybara.Git.GitLab.Api.Entities;

/// <summary>
/// An interface for classes that contain a set of parameters to pass into a GitLab GET request as
/// an HTTP query string.
/// </summary>
/// <seealso href="https://docs.gitlab.com/api/rest/#request-payload">GitLab docs</seealso>
public interface IGitLabApiParams
{
    /// <summary>Returns the url params associated with this object.</summary>
    IReadOnlyList<Param> Params();

    /// <summary>
    /// Constructs a URL-encoded query string of params defined within this object.
    ///
    /// <para>The string used for the value is derived from the value's <c>ToString</c> method.</para>
    /// </summary>
    string GetQueryString()
    {
        return string.Join("&", Params().Select(param => param.EncodedKey() + "=" + param.EncodedValue()));
    }
}

/// <summary>Represents a param to be used for a GitLab GET HTTP request query string.</summary>
/// <param name="Key">the key of the query param</param>
/// <param name="Value">the value of the query param</param>
public sealed record Param(string Key, object Value)
{
    /// <summary>Encodes the key string into a URL-encoded format, and returns the string.</summary>
    public string EncodedKey() => UrlEncode(Key);

    /// <summary>Encodes the value string into a URL-encoded format, and returns the string.</summary>
    public string EncodedValue() => UrlEncode(Value.ToString() ?? string.Empty);

    // Mirrors java.net.URLEncoder.encode(str, UTF_8): application/x-www-form-urlencoded.
    private static string UrlEncode(string value)
    {
        var sb = new StringBuilder(value.Length);
        byte[] bytes = Encoding.UTF8.GetBytes(value);
        foreach (byte b in bytes)
        {
            char c = (char)b;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                || c == '.' || c == '-' || c == '*' || c == '_')
            {
                sb.Append(c);
            }
            else if (c == ' ')
            {
                sb.Append('+');
            }
            else
            {
                sb.Append('%');
                sb.Append(HexUpper((b >> 4) & 0xF));
                sb.Append(HexUpper(b & 0xF));
            }
        }

        return sb.ToString();
    }

    private static char HexUpper(int nibble) => (char)(nibble < 10 ? '0' + nibble : 'A' + (nibble - 10));
}
