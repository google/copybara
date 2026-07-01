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

/// <summary>
/// A console that prints the output using fancy ANSI capabilities.
/// </summary>
public sealed class AnsiConsole : Console
{
    private static readonly string RemoveLine = AnsiEscapes.OneLineUp() + AnsiEscapes.DeleteLine();

    private readonly TextReader _input;
    private readonly TextWriter _output;
    private readonly object _lock = new();
    private readonly bool _verbose;

    private int _lastProgressLines = 0;

    // blue red yellow blue green red
    public AnsiConsole(TextReader input, TextWriter output, bool verbose)
    {
        _input = Preconditions.CheckNotNull(input);
        _output = Preconditions.CheckNotNull(output);
        _verbose = verbose;
    }

    public void StartupMessage(string version)
    {
        // Just because we can!
        _output.WriteLine(
            AnsiColor.Blue.Write("C")
            + AnsiColor.Red.Write("o")
            + AnsiColor.Yellow.Write("p")
            + AnsiColor.Blue.Write("y")
            + AnsiColor.Green.Write("b")
            + AnsiColor.Red.Write("a")
            + AnsiColor.Blue.Write("r")
            + AnsiColor.Red.Write("a")
            + " source mover (Version: " + version + ")");
    }

    public void Error(string message)
    {
        lock (_lock)
        {
            _lastProgressLines = 0;
            _output.WriteLine(AnsiColor.Red.Write("ERROR: ") + message);
        }
    }

    public void Warn(string message)
    {
        lock (_lock)
        {
            _lastProgressLines = 0;
            _output.WriteLine(AnsiColor.Yellow.Write("WARN: ") + message);
        }
    }

    public void Info(string message)
    {
        lock (_lock)
        {
            _lastProgressLines = 0;
            _output.WriteLine(AnsiColor.Green.Write("INFO: ") + message);
        }
    }

    public string Ask(string msg, string? defaultAnswer, Func<string, bool> validator)
    {
        _output.Write(AnsiColor.Blue.Write("Question: ") + msg);
        string? line;
        while ((line = _input.ReadLine()) != null)
        {
            string answer = line.Trim();
            if (string.IsNullOrEmpty(answer) && defaultAnswer != null)
            {
                return defaultAnswer;
            }
            if (validator(answer))
            {
                return answer;
            }
            Error("Invalid answer: " + answer);
            _output.Write(AnsiColor.Blue.Write("Question: ") + msg);
        }
        // TODO(malcon): Refactor console to throw interrupted in this case.
        throw new IOException("Cancelled by user");
    }

    public string AskWithErrorMessage(
        string msg, string? defaultAnswer, EnhancedPredicate enhancedValidator)
    {
        return ((Console)this).AskWithErrorMessage(_input, _output, msg, defaultAnswer, enhancedValidator);
    }

    public bool IsVerbose => _verbose;

    public void Progress(string progress)
    {
        lock (_lock)
        {
            if (_lastProgressLines > 0)
            {
                _output.Write(Repeat(RemoveLine, _lastProgressLines));
            }
            _output.WriteLine(AnsiColor.Green.Write("Task: ") + progress);
            _lastProgressLines = 1 + CountNewlines(progress);
        }
    }

    public bool PromptConfirmation(string message)
    {
        return new ConsolePrompt(
            _input,
            new DelegatingPromptPrinter(msg =>
            {
                lock (_lock)
                {
                    _lastProgressLines = 0;
                    _output.Write(AnsiColor.Yellow.Write("WARN: ") + msg + " [y/n] ");
                }
            })).PromptConfirmation(message);
    }

    public string Colorize(AnsiColor ansiColor, string message) => ansiColor.Write(message);

    private static int CountNewlines(string text)
    {
        int count = 0;
        foreach (char c in text)
        {
            if (c == '\n')
            {
                count++;
            }
        }
        return count;
    }

    private static string Repeat(string value, int times)
    {
        return times <= 0 ? string.Empty : string.Concat(Enumerable.Repeat(value, times));
    }
}
