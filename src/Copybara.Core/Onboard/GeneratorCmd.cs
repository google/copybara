/*
 * Copyright (C) 2022 Google Inc.
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

using Copybara.Git;
using Copybara.Onboard.Core;
using Copybara.Onboard.Core.Template;
using Copybara.Util;

using Console = Copybara.Util.Console.Console;

namespace Copybara.Onboard;

/// <summary>
/// A command that generates a config file based on user and inferred inputs. Port of
/// <c>com.google.copybara.onboard.GeneratorCmd</c>.
///
/// <para><b>Port note (CLI boundary):</b> upstream implements <c>OnboardingCmd</c>/<c>CopybaraCmd</c>
/// and takes a <c>CommandEnv</c>. Since Copybara.Core cannot reference Copybara.Cli, this class
/// exposes a plain <see cref="Run(Options)"/> entry point over the resolved <see cref="Options"/>.
/// </para>
/// TODO(port): wire into Copybara.Cli (implement <c>Copybara.Cli.ICopybaraCmd</c>).
/// </summary>
public class GeneratorCmd : OnboardingCmd
{
    private readonly ModuleSet _moduleSet;
    private readonly GeneratorCmdImpl _generatorCmd = new();

    public GeneratorCmd(ModuleSet moduleSet)
    {
        _moduleSet = moduleSet;
    }

    public ExitCode Run(Options options)
    {
        Console console = options.Get<GeneralOptions>().GetConsole();

        IReadOnlyList<IConfigGenerator> generators = GetGeneratorCmdImpl().Generators();
        Inputs.MaybeSetTemplates(generators);
        foreach (IConfigGenerator generator in generators)
        {
            // Force the generator to initialize its Inputs so that they are declared in the registry.
            _ = generator.Consumes();
        }

        try
        {
            IInputProviderResolver resolver = CreateInputProviderResolver(options);
            return GetGeneratorCmdImpl().ExecuteCommand(options, resolver);
        }
        catch (CannotProvideException e)
        {
            console.Error("Cannot resolve input field: " + e.Message);
            return ExitCode.CommandLineError;
        }
    }

    public GeneratorCmdImpl GetGeneratorCmdImpl() => _generatorCmd;

    public override ModuleSet GetModuleSet() => _moduleSet;

    public override IReadOnlyList<IInputProvider> GetInputProviders(Options options)
    {
        GeneratorOptions genOpts = options.Get<GeneratorOptions>();
        GeneralOptions generalOptions = options.Get<GeneralOptions>();
        Console console = generalOptions.GetConsole();

        var result = ImmutableArray.CreateBuilder<IInputProvider>();
        result.Add(
            new ConstantProvider<GeneratorFolder>(
                Inputs.GeneratorFolder,
                new GeneratorFolder(generalOptions.GetCwd())));
        result.Add(
            new ConfigHeuristicsInputProvider(
                options.Get<GitOptions>(),
                generalOptions,
                genOpts,
                ImmutableHashSet<string>.Empty,
                genOpts.ComputeGlobPercentageSimilar,
                console,
                db => db.Resolve(Inputs.GeneratorFolder).Path));
        result.Add(
            new MapBasedInputProvider(genOpts.Inputs, IInputProvider.CommandLinePriority));
        return result.ToImmutable();
    }

    // TODO(malcon, joshgoldman): Rename to 'generate' once we remove old version.
    public string Name => "generator";
}
