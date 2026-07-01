/*
 * Copyright (C) 2024 Google LLC.
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
using System.Net.Http.Headers;
using System.Text;

namespace Copybara.Http.Endpoint;

/// <summary>
/// Intercepts HTTP content and resolves secrets.
///
/// <para>The Java version implements Google http-client's streaming <c>HttpContent</c>. Here we
/// materialize the wrapped content to a UTF-8 string, resolve any <c>${{issuer}}</c> templates,
/// and expose the result as a new <see cref="ByteArrayContent"/> that preserves the original
/// content type headers.</para>
/// </summary>
internal static class HttpContentInterceptor
{
    public static HttpContent Wrap(HttpContent content, HttpSecretInterceptor secretInterceptor)
    {
        string httpContent = content.ReadAsStringAsync().GetAwaiter().GetResult();
        string outputStr = secretInterceptor.ResolveStringSecrets(httpContent);

        var resolved = new ByteArrayContent(Encoding.UTF8.GetBytes(outputStr));
        // Preserve content-type and other content headers from the original content.
        foreach (var header in content.Headers)
        {
            resolved.Headers.TryAddWithoutValidation(header.Key, header.Value);
        }

        return resolved;
    }
}
