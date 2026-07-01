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

using System.Net.Http;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Http.Endpoint;

/// <summary>Simple object to read an http response.</summary>
[StarlarkBuiltin("http_response", Doc = "A http response.")]
public class HttpEndpointResponse : IStarlarkValue
{
    private readonly HttpResponseMessage _response;

    public HttpEndpointResponse(HttpResponseMessage response)
    {
        _response = response;
    }

    [StarlarkMethod("code", Doc = "http status code")]
    public int GetStatusCode() => (int)_response.StatusCode;

    [StarlarkMethod("status", Doc = "http status message")]
    public string GetStatusMessage() => _response.ReasonPhrase ?? _response.StatusCode.ToString();

    [StarlarkMethod("contents_string", Doc = "response contents as string")]
    public string ResponseAsString() => _response.Content.ReadAsStringAsync().GetAwaiter().GetResult();

    [StarlarkMethod(
        "header",
        Doc = "Returns the value of the response header specified by the field name")]
    public IReadOnlyList<string> ResponseHeader(
        [Param(Name = "key", Named = true, AllowedTypes = new[] { typeof(string) })] string key)
    {
        var values = new List<string>();
        if (_response.Headers.TryGetValues(key, out var headerValues))
        {
            values.AddRange(headerValues);
        }

        if (_response.Content.Headers.TryGetValues(key, out var contentValues))
        {
            values.AddRange(contentValues);
        }

        return values;
    }

    [StarlarkMethod(
        "download",
        Doc = "Writes the content of the HTTP response into the given destination path")]
    public void Download(
        [Param(Name = "path", Doc = "The destination Path")] CheckoutPath path)
    {
        using var output = File.Create(path.FullPath());
        using var input = _response.Content.ReadAsStreamAsync().GetAwaiter().GetResult();
        input.CopyTo(output);
    }
}
