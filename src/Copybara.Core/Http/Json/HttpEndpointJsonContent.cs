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

using System.Net.Http;
using System.Text;
using Copybara.Http.Endpoint;
using Starlark.Eval;

namespace Copybara.Http.Json;

/// <summary>Constructs data for an HTTP request containing JSON data payload.</summary>
public class HttpEndpointJsonContent : IHttpEndpointBody, IStarlarkValue
{
    private readonly object? _data;
    private HttpContent? _body;

    public HttpEndpointJsonContent(object? data)
    {
        _data = data;
    }

    public HttpContent GetContent()
    {
        if (_body == null)
        {
            // The Java version uses gson; here we serialize Starlark values with a small
            // recursive converter and produce an application/json body (System.Text.Json).
            string json = StarlarkJson.Serialize(_data);
            _body = new StringContent(json, Encoding.UTF8, "application/json");
        }

        return _body;
    }
}
