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

using Copybara.Exceptions;
using Copybara.Util;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Hg;

/// <summary>Options for <see cref="HgOrigin"/>.</summary>
public class HgOriginOptions : IOption
{
    [Flag(
        "--hg-origin-checkout-hook",
        "A command to be executed when a checkout happens for a hg origin. Only intended to run"
            + " tools that update the repository to latest sources",
        Hidden = true)]
    public string? OriginCheckoutHook { get; set; }

    internal void MaybeRunCheckoutHook(string checkoutDir, GeneralOptions generalOptions)
    {
        if (string.IsNullOrEmpty(OriginCheckoutHook))
        {
            return;
        }

        RunCheckoutHook(OriginCheckoutHook, checkoutDir, generalOptions, "hg.origin");
    }

    // Port of OriginUtil.CheckoutHook.run (inlined here since OriginUtil is not yet ported).
    private static void RunCheckoutHook(
        string checkoutHook, string checkoutDir, GeneralOptions generalOptions, string originType)
    {
        Console console = generalOptions.GetConsole();
        try
        {
            var cmd = new Command(
                new[] { checkoutHook }, generalOptions.GetEnvironment(), checkoutDir);
            CommandOutputWithStatus result = generalOptions.NewCommandRunner(cmd)
                .WithVerbose(generalOptions.IsVerbose())
                .Execute();
            LogLines(console, GetPrefix("Stdout", originType), result.GetStdout());
            LogLines(console, GetPrefix("Stderr", originType), result.GetStderr());
        }
        catch (BadExitStatusWithOutputException e)
        {
            LogLines(console, GetPrefix("Stdout", originType), e.GetOutput().GetStdout());
            LogLines(console, GetPrefix("Stderr", originType), e.GetOutput().GetStderr());
            throw new RepoException("Error executing the checkout hook: " + checkoutHook, e);
        }
        catch (CommandException e)
        {
            throw new RepoException("Error executing the checkout hook: " + checkoutHook, e);
        }
    }

    private static string GetPrefix(string channel, string originType) =>
        $"{channel} hook ({originType}): ";

    private static void LogLines(Console console, string prefix, string content)
    {
        foreach (string line in content.Split('\n'))
        {
            if (line.Length == 0)
            {
                continue;
            }

            console.Verbose(prefix + line);
        }
    }
}
