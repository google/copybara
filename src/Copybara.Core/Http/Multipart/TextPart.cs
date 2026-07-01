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

using System.Collections.Immutable;
using System.Net.Http;
using Copybara.Checks;
using Starlark.Eval;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Http.Multipart;

/// <summary>Represents a text field in a multipart http form payload.</summary>
public class TextPart : IHttpEndpointFormPart, IStarlarkValue
{
    private readonly string _name;
    private readonly string _text;

    public TextPart(string name, string text)
    {
        _name = name;
        _text = text;
    }

    public void AddToContent(MultipartFormDataContent content)
    {
        var part = new StringContent(_text);
        part.Headers.ContentType = null;
        IHttpEndpointFormPart.SetContentDispositionHeader(part, _name, null);
        content.Add(part);
    }

    public void CheckPart(IChecker checker, Console console)
    {
        checker.DoCheck(
            ImmutableDictionary.CreateRange(new[]
            {
                new KeyValuePair<string, string>("name", _name),
                new KeyValuePair<string, string>("text", _text),
            }),
            console);
    }
}
