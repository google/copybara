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
using Console = Copybara.Util.Console.Console;

namespace Copybara.Http.Endpoint;

/// <summary>An object containing the content portion of an http request.</summary>
public interface IHttpEndpointBody
{
    /// <summary>
    /// Returns the .NET <see cref="HttpContent"/> for this body. Replaces the Google http-client
    /// <c>HttpContent</c> used by the Java implementation.
    /// </summary>
    HttpContent GetContent();

    // TODO(port): confirm IChecker / CheckerException names once Copybara.Checks lands.
    void CheckContent(IChecker checker, Console console) =>
        throw new CheckerException("checker not implemented for this content type");
}
