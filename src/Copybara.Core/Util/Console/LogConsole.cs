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
/// A simple console logger that prefixes the output with the time.
/// </summary>
public sealed class LogConsole : Console
{
    private const string DatePrefixFmt = "MMdd HH:mm:ss.fff";

    private readonly TextReader? _input;
    private readonly TextWriter _output;
    private readonly bool _verbose;

    /// <summary>Creates a new instance of <see cref="LogConsole"/> with write capabilities, only.</summary>
    public static LogConsole WriteOnlyConsole(TextWriter output, bool verbose)
    {
        return new LogConsole(null, Preconditions.CheckNotNull(output), verbose);
    }

    /// <summary>Creates a new instance of <see cref="LogConsole"/> with read and write capabilities.</summary>
    public static LogConsole ReadWriteConsole(TextReader input, TextWriter output, bool verbose)
    {
        return new LogConsole(
            Preconditions.CheckNotNull(input), Preconditions.CheckNotNull(output), verbose);
    }

    private LogConsole(TextReader? input, TextWriter output, bool verbose)
    {
        _input = input;
        _output = Preconditions.CheckNotNull(output);
        _verbose = verbose;
    }

    public void StartupMessage(string version)
    {
        _output.WriteLine("Copybara (Version: " + version + ")");
    }

    public bool IsVerbose => _verbose;

    public void Error(string message) => PrintMessage("ERROR", message);

    public void Warn(string message) => PrintMessage("WARN", message);

    public void Info(string message) => PrintMessage("INFO", message);

    public void Progress(string task) => PrintMessage("TASK", task);

    public bool PromptConfirmation(string message)
    {
        Preconditions.CheckState(
            _input != null,
            "LogConsole cannot read user input if system console is not present.");
        return new ConsolePrompt(
            _input!,
            new DelegatingPromptPrinter(msg => _output.Write($"{NowToString()} WARN: {msg} [y/n] ")))
            .PromptConfirmation(message);
    }

    public string Colorize(AnsiColor ansiColor, string message) => message;

    private void PrintMessage(string messageKind, string message)
    {
        _output.WriteLine($"{NowToString()} {messageKind}: {message}");
    }

    private static string NowToString()
    {
        return DateTime.Now.ToString(DatePrefixFmt, System.Globalization.CultureInfo.InvariantCulture);
    }
}
