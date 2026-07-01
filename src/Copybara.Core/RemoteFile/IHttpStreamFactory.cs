/*
 * Copyright (C) 2020 Google Inc.
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

using Copybara.Common;
using Copybara.Http.Auth;

namespace Copybara.RemoteFile;

/// <summary>Interface for opening a URL for downloading a file.</summary>
public interface IHttpStreamFactory
{
    /// <summary>Open the referenced URL and return the stream to the contents.</summary>
    /// <param name="url">The URL to open.</param>
    /// <param name="auth">The interceptor to use for authentication. If null, no authentication is used.</param>
    /// <exception cref="IOException"/>
    /// <exception cref="Copybara.Credentials.CredentialRetrievalException"/>
    /// <exception cref="Copybara.Credentials.CredentialIssuingException"/>
    Stream Open(Uri url, IAuthInterceptor? auth) =>
        Open(url, auth, ImmutableListMultimap<string, string>.Empty);

    /// <summary>Open the referenced URL and return the stream to the contents.</summary>
    /// <param name="url">The URL to open.</param>
    /// <param name="auth">The interceptor to use for authentication. If null, no authentication is used.</param>
    /// <param name="headers">The headers to send in the HTTP request.</param>
    /// <exception cref="IOException"/>
    /// <exception cref="Copybara.Credentials.CredentialRetrievalException"/>
    /// <exception cref="Copybara.Credentials.CredentialIssuingException"/>
    Stream Open(Uri url, IAuthInterceptor? auth, ImmutableListMultimap<string, string> headers);
}
