/*
 * Copyright (C) 2024 Google LLC.
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

using Copybara.Format;
using Copybara.Onboard.Core;
using Copybara.Onboard.Core.Template;
using Copybara.Util;

using Console = Copybara.Util.Console.Console;

namespace Copybara.Onboard;

/// <summary>
/// Implementation library class for <see cref="GeneratorCmd"/>. Port of
/// <c>com.google.copybara.onboard.GeneratorCmdImpl</c>.
///
/// <para><b>Port note (CLI boundary):</b> takes the resolved <see cref="Options"/> instead of the
/// Copybara.Cli <c>CommandEnv</c>.</para>
/// </summary>
public class GeneratorCmdImpl
{
    public ExitCode ExecuteCommand(Options options, IInputProviderResolver resolver)
    {
        GeneratorOptions genOpts = options.Get<GeneratorOptions>();
        Console console = options.Get<GeneralOptions>().GetConsole();

        IReadOnlyList<IConfigGenerator> generators = Generators();
        Inputs.MaybeSetTemplates(generators);
        foreach (IConfigGenerator generator in generators)
        {
            // Force the generator to initialize its Inputs so that they are declared in the registry.
            _ = generator.Consumes();
        }

        try
        {
            GeneratorFolder? path = resolver.ResolveOptional(Inputs.GeneratorFolder);
            if (path == null)
            {
                console.Error("Cannot infer a path to place the generated config");
                return ExitCode.CommandLineError;
            }

            IConfigGenerator template;
            try
            {
                template = SelectGenerator(resolver, genOpts.Template, console);
            }
            catch (CannotConvertException)
            {
                console.Error("Cannot infer a template for generating a config. Use --template flag.");
                return ExitCode.CommandLineError;
            }

            string config = template.Generate(resolver);

            string configDestination = System.IO.Path.Combine(path.Path, "copy.bara.sky");
            string? parent = System.IO.Path.GetDirectoryName(configDestination);
            if (parent != null && !Directory.Exists(parent))
            {
                Directory.CreateDirectory(parent);
            }

            File.WriteAllText(configDestination, config);

            Format(options, configDestination);

            console.InfoFmt("%s created", configDestination);
        }
        catch (ThreadInterruptedException e)
        {
            console.Error("Interrupted: " + e.Message);
            return ExitCode.Interrupted;
        }
        catch (CannotProvideException e)
        {
            console.Error("Cannot resolve input field: " + e.Message);
            return ExitCode.CommandLineError;
        }

        return ExitCode.Success;
    }

    /// <exception cref="CannotProvideException"/>
    private static void Format(Options options, string config)
    {
        GeneralOptions generalOptions = options.Get<GeneralOptions>();
        BuildifierOptions buildifierOptions = options.Get<BuildifierOptions>();
        string? parent = System.IO.Path.GetDirectoryName(System.IO.Path.GetFullPath(config));
        var cmd =
            new Command(
                new[]
                {
                    buildifierOptions.BuildifierBin, "-type=bzl", System.IO.Path.GetFullPath(config),
                },
                environmentVariables: null,
                parent);
        try
        {
            _ = generalOptions.NewCommandRunner(cmd)
                .WithVerbose(generalOptions.IsVerbose())
                .Execute();
        }
        catch (CommandException e)
        {
            throw new CannotProvideException("Cannot format generated config " + config, e);
        }
    }

    /// <exception cref="CannotConvertException"/>
    /// <exception cref="CannotProvideException"/>
    /// <exception cref="System.Threading.ThreadInterruptedException"/>
    private IConfigGenerator SelectGenerator(
        IInputProviderResolver resolver, string? cliTemplate, Console console)
    {
        IReadOnlyList<IConfigGenerator> generators = Generators();
        if (cliTemplate != null)
        {
            return Inputs.TemplateInput().Convert(cliTemplate, resolver);
        }

        foreach (IConfigGenerator generator in generators)
        {
            if (generator.IsGenerator(resolver))
            {
                console.Info("Using '" + generator.Name + "' template");
                return generator;
            }
        }

        return resolver.Resolve(Inputs.TemplateInput());
    }

    /// <summary>A priority-ordered list of templates that can be used.</summary>
    public virtual IReadOnlyList<IConfigGenerator> Generators() =>
        ImmutableArray.Create<IConfigGenerator>(new GitToGitGenerator());
}
