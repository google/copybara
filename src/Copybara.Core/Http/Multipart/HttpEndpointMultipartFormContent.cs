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
using Copybara.Checks;
using Copybara.Http.Endpoint;
using Starlark.Eval;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Http.Multipart;

/// <summary>Constructs data for an http request containing a multipart form data payload.</summary>
public class HttpEndpointMultipartFormContent : IHttpEndpointBody, IStarlarkValue
{
    private readonly IReadOnlyList<IHttpEndpointFormPart> _parts;
    private HttpContent? _body;

    public HttpEndpointMultipartFormContent(IReadOnlyList<IHttpEndpointFormPart> parts)
    {
        _parts = parts;
    }

    public HttpContent GetContent()
    {
        if (_body == null)
        {
            var content = new MultipartFormDataContent(Guid.NewGuid().ToString());
            foreach (var part in _parts)
            {
                part.AddToContent(content);
            }

            _body = content;
        }

        return _body;
    }

    public void CheckContent(IChecker checker, Console console)
    {
        foreach (var part in _parts)
        {
            part.CheckPart(checker, console);
        }
    }
}
