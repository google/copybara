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
using Copybara.Http.Endpoint;
using Starlark.Eval;

namespace Copybara.Http.Multipart;

/// <summary>Constructs data for an HTTP request containing a urlencoded form data payload.</summary>
public class HttpEndpointUrlEncodedFormContent : IHttpEndpointBody, IStarlarkValue
{
    private readonly object? _data;
    private HttpContent? _body;

    public HttpEndpointUrlEncodedFormContent(object? data)
    {
        _data = data;
    }

    public HttpContent GetContent()
    {
        if (_body == null)
        {
            var pairs = new List<KeyValuePair<string, string>>();
            if (_data is Dict dict)
            {
                foreach (var entry in dict.Entries)
                {
                    pairs.Add(new KeyValuePair<string, string>(
                        entry.Key?.ToString() ?? string.Empty,
                        entry.Value?.ToString() ?? string.Empty));
                }
            }

            _body = new FormUrlEncodedContent(pairs);
        }

        return _body;
    }
}
