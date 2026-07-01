/*
 * Copyright (C) 2016 Google Inc.
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

namespace Copybara.Util.Console;

/// <summary>Reads the input from a <see cref="Console"/>.</summary>
internal sealed class ConsolePrompt
{
    private static readonly HashSet<string> Yes = new(StringComparer.Ordinal) { "y", "yes" };
    private static readonly HashSet<string> No = new(StringComparer.Ordinal) { "n", "no" };

    private readonly TextReader _input;
    private readonly Console.PromptPrinter _promptPrinter;

    internal ConsolePrompt(TextReader input, Console.PromptPrinter promptPrinter)
    {
        _input = Preconditions.CheckNotNull(input);
        _promptPrinter = Preconditions.CheckNotNull(promptPrinter);
    }

    internal bool PromptConfirmation(string message)
    {
        _promptPrinter.Print(message);
        string? line;
        while ((line = _input.ReadLine()) != null)
        {
            string answer = line.Trim().ToLowerInvariant();
            if (Yes.Contains(answer))
            {
                return true;
            }
            if (No.Contains(answer))
            {
                return false;
            }
            _promptPrinter.Print(message);
        }
        // EOF while reading from the input (user cancelled)
        return false;
    }
}

/// <summary>
/// Adapts a delegate to the <see cref="Console.PromptPrinter"/> interface, replacing the Java
/// lambda-based prompt printers.
/// </summary>
internal sealed class DelegatingPromptPrinter : Console.PromptPrinter
{
    private readonly Action<string> _print;

    internal DelegatingPromptPrinter(Action<string> print)
    {
        _print = Preconditions.CheckNotNull(print);
    }

    public void Print(string message) => _print(message);
}
