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
using Starlark.Eval;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Http.Multipart;

/// <summary>Represents a file field in a multipart http form payload.</summary>
public class FilePart : IHttpEndpointFormPart, IStarlarkValue
{
    private readonly string _name;
    private readonly string _filePath;
    private readonly string _contentType;
    private readonly string? _filename;

    public FilePart(string name, string filePath, string contentType, string? filename)
    {
        _name = name;
        _filePath = filePath;
        _contentType = contentType;
        _filename = filename;
    }

    public void AddToContent(MultipartFormDataContent content)
    {
        var part = new StreamContent(File.OpenRead(_filePath));
        part.Headers.ContentType = new MediaTypeHeaderValue(_contentType);
        IHttpEndpointFormPart.SetContentDispositionHeader(part, _name, _filename);
        content.Add(part);
    }

    public void CheckPart(IChecker checker, Console console)
    {
        checker.DoCheck(_filePath, console);
    }
}
