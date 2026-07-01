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

namespace Copybara.Util.Console;

/// <summary>
/// Write user messages to the console.
/// </summary>
public interface Console : IDisposable
{
    /// <summary>Print the Copybara welcome message.</summary>
    void StartupMessage(string version);

    /// <summary>Print an error in the console.</summary>
    void Error(string message);

    /// <summary>Print a format string as error on the console.</summary>
    void ErrorFmt(string format, params object?[] args)
    {
        Error(ConsoleFormat.Printf(format, args));
    }

    /// <summary>Print a warning in the console.</summary>
    void Warn(string message);

    /// <summary>Print a format string as warn on the console.</summary>
    void WarnFmt(string format, params object?[] args)
    {
        Warn(ConsoleFormat.Printf(format, args));
    }

    /// <summary>Console warn if <paramref name="condition"/> is true, otherwise do nothing.</summary>
    void WarnFmtIf(bool condition, string format, params object?[] args)
    {
        if (condition)
        {
            WarnFmt(format, args);
        }
    }

    /// <summary>Returns true if verbose.</summary>
    bool IsVerbose { get; }

    /// <summary>Print an informational message in the console, if verbose logging is enabled.</summary>
    void Verbose(string message)
    {
        if (IsVerbose)
        {
            Info(message);
        }
    }

    /// <summary>Print a format string as info on the console, if verbose logging is enabled.</summary>
    void VerboseFmt(string format, params object?[] args)
    {
        Verbose(ConsoleFormat.Printf(format, args));
    }

    /// <summary>
    /// Print an informational message in the console.
    ///
    /// <para>Warning: Do not abuse the usage of this method. We don't want to spam our users. When in
    /// doubt, use verbose.</para>
    /// </summary>
    void Info(string message);

    /// <summary>Print a format string as info on the console.</summary>
    void InfoFmt(string format, params object?[] args)
    {
        Info(ConsoleFormat.Printf(format, args));
    }

    /// <summary>Print a progress message in the console.</summary>
    void Progress(string progress);

    /// <summary>Print a format string as progress on the console.</summary>
    void ProgressFmt(string format, params object?[] args)
    {
        Progress(ConsoleFormat.Printf(format, args));
    }

    /// <summary>
    /// Returns true if this Console's input registers Y/y after showing the prompt message.
    /// </summary>
    bool PromptConfirmation(string message);

    /// <summary>Like <see cref="PromptConfirmation"/>, but takes a format String as argument.</summary>
    bool PromptConfirmationFmt(string format, params object?[] args)
    {
        return PromptConfirmation(ConsoleFormat.Printf(format, args));
    }

    string Ask(string msg, string? defaultAnswer, Func<string, bool> validator)
    {
        throw new InvalidOperationException("Interactive prompt not allowed in " + GetType());
    }

    // TODO(malcon): Delete method
    string AskWithErrorMessage(
        TextReader input,
        TextWriter output,
        string msg,
        string? defaultAnswer,
        EnhancedPredicate enhancedValidator)
    {
        output.Write(Colorize(AnsiColor.Blue, "Question: ") + msg);
        string? line;
        while ((line = input.ReadLine()) != null)
        {
            string answer = line.Trim();
            if (string.IsNullOrEmpty(answer) && defaultAnswer != null)
            {
                return defaultAnswer;
            }
            if (enhancedValidator.Predicate(answer))
            {
                return answer;
            }
            Error("Invalid answer: " + answer);
            Error(enhancedValidator.ErrorMsg);
            output.Write(AnsiColor.Blue.Write("Question: ") + msg);
        }
        throw new IOException("Cancelled by user");
    }

    string AskWithErrorMessage(string msg, string? defaultAnswer, EnhancedPredicate enhancedValidator)
    {
        throw new InvalidOperationException("Interactive prompt not allowed in " + GetType());
    }

    /// <summary>
    /// Given a message and a console that support colors, return a string that prints the message in
    /// the <paramref name="ansiColor"/>.
    ///
    /// <para>Note that not all consoles support colors, so messages should be readable without
    /// colors.</para>
    /// </summary>
    string Colorize(AnsiColor ansiColor, string message);

    /// <summary>Prints a prompt message.</summary>
    interface PromptPrinter
    {
        void Print(string message);
    }

    /// <summary>Close this console, freeing resources.</summary>
    void IDisposable.Dispose()
    {
    }
}
