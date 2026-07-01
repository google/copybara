/*
 * Copyright (C) 2019 Google Inc.
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
using Copybara.Common;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Checks;

/// <summary>
/// A checker for API clients that delegates on a <see cref="IChecker"/> and provides convenience
/// methods for checking one or more pairs of field names and values, plus error handling.
/// </summary>
public class ApiChecker
{
    private readonly IChecker _checker;
    private readonly Console _console;

    public ApiChecker(IChecker checker, Console console)
    {
        _checker = Preconditions.CheckNotNull(checker);
        _console = Preconditions.CheckNotNull(console);
    }

    /// <summary>Performs a check on the given request field.</summary>
    /// <exception cref="CheckerException"/>
    public void Check(string field, object value) =>
        DoCheck(ImmutableDictionary.CreateRange(new[]
        {
            KeyValuePair.Create(field, value.ToString()!),
        }));

    /// <summary>Performs a check on the given request fields.</summary>
    /// <exception cref="CheckerException"/>
    public void Check(string field1, object value1, string field2, object value2) =>
        DoCheck(ImmutableDictionary.CreateRange(new[]
        {
            KeyValuePair.Create(field1, value1.ToString()!),
            KeyValuePair.Create(field2, value2.ToString()!),
        }));

    /// <summary>Performs a check on the given request fields.</summary>
    /// <exception cref="CheckerException"/>
    public void Check(
        string field1, object value1, string field2, object value2, string field3, object value3) =>
        DoCheck(ImmutableDictionary.CreateRange(new[]
        {
            KeyValuePair.Create(field1, value1.ToString()!),
            KeyValuePair.Create(field2, value2.ToString()!),
            KeyValuePair.Create(field3, value3.ToString()!),
        }));

    private void DoCheck(ImmutableDictionary<string, string> data)
    {
        try
        {
            _checker.DoCheck(data, _console);
        }
        catch (IOException e)
        {
            throw new InvalidOperationException("Error running checker", e);
        }
    }
}
