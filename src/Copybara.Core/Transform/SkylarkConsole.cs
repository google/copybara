/*
 * Copyright (C) 2018 Google Inc.
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

using Copybara.Util.Console;
using Starlark.Annot;
using Starlark.Eval;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Transform;

/// <summary>
/// A console that can be used in skylark transformations to print info, warning or error messages.
/// </summary>
[StarlarkBuiltin(
    "console",
    Doc =
        "A console that can be used in skylark transformations to print info, warning or"
        + " error messages.")]
public class SkylarkConsole : Console, IStarlarkValue
{
    private int _errorCount;
    private readonly Console _delegate;

    public SkylarkConsole(Console @delegate)
    {
        _delegate = @delegate;
    }

    public void StartupMessage(string version) =>
        throw new NotSupportedException("Shouldn't be called from skylark");

    [StarlarkMethod(
        "error",
        Doc = "Show an error in the log. Note that this will stop Copybara execution.")]
    public void Error([Param(Name = "message", Doc = "message to log")] string message)
    {
        _delegate.Error(message);
        _errorCount++;
    }

    public bool IsVerbose => _delegate.IsVerbose;

    [StarlarkMethod("warn", Doc = "Show a warning in the console")]
    public void Warn([Param(Name = "message", Doc = "message to log")] string message) =>
        _delegate.Warn(message);

    [StarlarkMethod(
        "verbose",
        Doc = "Show an info message in the console if verbose logging is enabled.")]
    public void Verbose([Param(Name = "message", Doc = "message to log")] string message) =>
        _delegate.Verbose(message);

    [StarlarkMethod("info", Doc = "Show an info message in the console")]
    public void Info([Param(Name = "message", Doc = "message to log")] string message) =>
        _delegate.Info(message);

    [StarlarkMethod("progress", Doc = "Show a progress message in the console")]
    public void Progress([Param(Name = "message", Doc = "message to log")] string progress) =>
        _delegate.Progress(progress);

    public bool PromptConfirmation(string message) =>
        throw new NotSupportedException("Shouldn't be called from skylark");

    public string Colorize(AnsiColor ansiColor, string message) =>
        _delegate.Colorize(ansiColor, message);

    public string Ask(string msg, string? defaultAnswer, Func<string, bool> validator) =>
        _delegate.Ask(msg, defaultAnswer, validator);

    /// <summary>Print a format string as error on the console.</summary>
    public void ErrorFmt(string format, params object?[] args)
    {
        Error(ConsoleFormat.Printf(format, args));
    }

    /// <summary>Print a format string as warn on the console.</summary>
    public void WarnFmt(string format, params object?[] args)
    {
        Warn(ConsoleFormat.Printf(format, args));
    }

    /// <summary>Print a format string as info on the console.</summary>
    public void InfoFmt(string format, params object?[] args)
    {
        Info(ConsoleFormat.Printf(format, args));
    }

    /// <summary>Print a format string as progress on the console.</summary>
    public void ProgressFmt(string format, params object?[] args)
    {
        Progress(ConsoleFormat.Printf(format, args));
    }

    public int GetErrorCount() => _errorCount;
}
