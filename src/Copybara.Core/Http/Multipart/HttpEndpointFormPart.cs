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
using System.Net.Http.Headers;
using Copybara.Checks;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Http.Multipart;

/// <summary>Represents a single part of a multipart form data request.</summary>
public interface IHttpEndpointFormPart
{
    void AddToContent(MultipartFormDataContent content);

    void CheckPart(IChecker checker, Console console);

    /// <summary>
    /// Sets the content-disposition header for a form part on the given content, mirroring the Java
    /// helper. The .NET <see cref="MultipartFormDataContent"/> emits <c>form-data</c> automatically;
    /// this applies the <c>name</c> (and optional <c>filename</c>) parameters.
    /// </summary>
    static void SetContentDispositionHeader(HttpContent content, string name, string? filename)
    {
        var disposition = new ContentDispositionHeaderValue("form-data")
        {
            Name = $"\"{name}\"",
        };
        if (filename != null)
        {
            disposition.FileName = $"\"{filename}\"";
        }

        content.Headers.ContentDisposition = disposition;
    }
}
