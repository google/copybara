/*
 * Copyright (C) 2023 Google LLC
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
using Copybara.Buildozer;
using Copybara.Exceptions;
using BuildozerCommand = Copybara.Buildozer.BuildozerOptions.BuildozerCommand;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Buildozer;

/// <summary>A class that can run a 'buildozer print' command.</summary>
public class BuildozerPrintExecutor
{
    private readonly BuildozerOptions _options;
    private readonly Console _console;

    private BuildozerPrintExecutor(BuildozerOptions options, Console console)
    {
        _options = options;
        _console = console;
    }

    public static BuildozerPrintExecutor Create(BuildozerOptions options, Console console) =>
        new(options, console);

    /// <summary>
    /// Runs a Buildozer print command.
    /// </summary>
    /// <param name="checkoutDir">The checkout directory to run in.</param>
    /// <param name="attr">The attribute from the target rule to print.</param>
    /// <param name="target">The target to print from.</param>
    /// <returns>A string with the buildozer print output.</returns>
    /// <exception cref="ValidationException">If there is an issue running buildozer print.</exception>
    public string Run(string checkoutDir, string attr, string target)
    {
        try
        {
            var command = new BuildozerCommand(target, $"print {attr}");
            return _options.RunCaptureOutput(
                _console, checkoutDir, ImmutableArray.Create(command));
        }
        catch (TargetNotFoundException e)
        {
            throw new ValidationException("Buildozer could not find the specified target", e);
        }
    }
}
