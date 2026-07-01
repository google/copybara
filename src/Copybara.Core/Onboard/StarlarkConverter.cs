/*
 * Copyright (C) 2022 Google Inc.
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
using System.Text;

using Copybara.Config;
using Copybara.Exceptions;
using Copybara.Onboard.Core;
using Copybara.Util.Console;

using Console = Copybara.Util.Console.Console;
using Module = Starlark.Eval.Module;

namespace Copybara.Onboard;

/// <summary>
/// A converter that, given an arbitrary string, transforms it to the equivalent Starlark object.
/// Port of <c>com.google.copybara.onboard.StarlarkConverter</c>.
/// </summary>
public class StarlarkConverter : IConverter<object>
{
    private readonly Console _console;
    private readonly SkylarkParser _skylarkParser;
    private readonly ModuleSet _moduleSet;

    public StarlarkConverter(ModuleSet moduleSet, Console console)
    {
        _console = console;
        _skylarkParser = new SkylarkParser(moduleSet.GetStaticModules(), StarlarkMode.Strict);
        _moduleSet = moduleSet;
    }

    public object Convert(string value, IInputProviderResolver resolver)
    {
        var content =
            new MapConfigFile(
                ImmutableDictionary.CreateRange(
                    new[]
                    {
                        new KeyValuePair<string, byte[]>(
                            "copy.bara.sky", Encoding.UTF8.GetBytes("CONVERTED_VAR = " + value)),
                    }),
                "copy.bara.sky");
        try
        {
            Module module = _skylarkParser.ExecuteSkylark(content, _moduleSet, _console);
            object? converted = module.GetGlobal("CONVERTED_VAR");
            if (converted == null)
            {
                throw new CannotConvertException("Cannot convert value " + value + ": null result");
            }

            return converted;
        }
        catch (Exception e) when (e is ValidationException or IOException or ThreadInterruptedException)
        {
            // Not ideal, but given the scope of the call (narrow, for a conversion), it is fine.
            throw new CannotConvertException(
                "Cannot convert value " + value + ": " + e.Message, e);
        }
    }
}
