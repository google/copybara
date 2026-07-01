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

namespace Copybara.Http;

/// <summary>
/// Options relating to the http endpoint.
///
/// <para>The Java version exposes a Google http-client <c>HttpTransport</c>; this port exposes a
/// <see cref="HttpClient"/> (from <c>System.Net.Http</c>) as the transport.</para>
/// </summary>
public class HttpOptions : IOption
{
    private HttpClient? _transport;

    /// <exception cref="Copybara.Exceptions.ValidationException">If the transport cannot be created.</exception>
    public virtual HttpClient GetTransport()
    {
        return _transport ??= new HttpClient(new HttpClientHandler
        {
            // Follow-redirect behavior is decided per HttpEndpoint request; enable auto-redirect on
            // the handler and let the endpoint gate it. The default matches the Java default (true).
            AllowAutoRedirect = true,
        });
    }
}
