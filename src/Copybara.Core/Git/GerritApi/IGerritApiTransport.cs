/*
 * Copyright (C) 2017 Google Inc.
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

using Copybara.Exceptions;

namespace Copybara.Git.GerritApi;

/// <summary>
/// Http transport interface for talking to a Gerrit host. Port of
/// <c>com.google.copybara.git.gerritapi.GerritApiTransport</c>.
/// </summary>
/// <remarks>
/// NOTE(port): Java passes a <c>java.lang.reflect.Type responseType</c> to each method. In the .NET
/// port the response type is expressed as a generic type argument <c>T</c>, which is both more
/// idiomatic and works directly with <see cref="System.Text.Json"/>. Calls are async because the
/// underlying HTTP work is I/O bound.
/// </remarks>
public interface IGerritApiTransport
{
    /// <summary>Do a http GET call.</summary>
    /// <exception cref="RepoException"/>
    /// <exception cref="ValidationException"/>
    Task<T?> GetAsync<T>(string path);

    /// <summary>Do a http POST call.</summary>
    /// <exception cref="RepoException"/>
    /// <exception cref="ValidationException"/>
    Task<T?> PostAsync<T>(string path, object request);

    /// <summary>Do a http PUT call.</summary>
    /// <exception cref="RepoException"/>
    /// <exception cref="ValidationException"/>
    Task<T?> PutAsync<T>(string path, object request);
}
