/*
 * Copyright (C) 2021 Google Inc.
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

using Console = Copybara.Util.Console.Console;

namespace Copybara.Onboard;

/// <summary>
/// Class to navigate users through a text adventure to populate a Copybara config file. Port of
/// <c>com.google.copybara.onboard.CommandLineGuide</c>.
///
/// <para><b>Port note:</b> Java took a <c>CommandEnv</c> (a Copybara.Cli type). Since Copybara.Core
/// cannot reference Copybara.Cli, this entry point takes the resolved <see cref="Options"/> directly.
/// </para>
/// </summary>
internal static class CommandLineGuide
{
    public static void RunForCommandLine(Options options)
    {
        Console console = options.Get<GeneralOptions>().GetConsole();
        console.Info("Welcome to Copybara's Assisted Onboarding Tool!\n");
        var configBuilder = new ConfigBuilder(new GitToGitTemplate());
        IReadOnlySet<RequiredField> requiredFields = configBuilder.GetRequiredFields();
        foreach (RequiredField field in requiredFields)
        {
            string? response =
                TryAskConsole(
                    console,
                    string.Format(
                        "What should be the value for field {0}? The field description is:\n\"{1}\"\n",
                        field.Name, field.HelpText),
                    "INVALID",
                    field.Predicate,
                    "Invalid response");
            if (response == null)
            {
                continue;
            }

            switch (field.Location)
            {
                case ConfigTemplateLocation.Named:
                    configBuilder.SetNamedStringParameter(field.Name, response);
                    break;
                case ConfigTemplateLocation.Keyword:
                    configBuilder.AddStringKeywordParameter(field.Name, response);
                    break;
            }
        }

        if (configBuilder.IsValid())
        {
            console.Info(
                string.Format(
                    "Config generation successful! Please paste this config text into a new file"
                        + " named copy.bara.sky:\n\n{0}",
                    configBuilder.Build()));
        }
    }

    private static string? TryAskConsole(
        Console console,
        string msg,
        string defaultAnswer,
        Func<string, bool> predicate,
        string errorMessage)
    {
        Preconditions.CheckNotNull(console, nameof(console));
        Preconditions.CheckNotNull(msg, nameof(msg));
        Preconditions.CheckNotNull(defaultAnswer, nameof(defaultAnswer));
        Preconditions.CheckNotNull(predicate, nameof(predicate));
        Preconditions.CheckNotNull(errorMessage, nameof(errorMessage));
        try
        {
            return console.Ask(msg, defaultAnswer, predicate);
        }
        catch (IOException e)
        {
            console.Error(string.Format("{0}\n{1}", errorMessage, e.Message));
            return null;
        }
    }
}
