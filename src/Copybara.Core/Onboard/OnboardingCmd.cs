/*
 * Copyright (C) 2024 Google LLC
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

using Copybara.Onboard.Core;

using Console = Copybara.Util.Console.Console;

namespace Copybara.Onboard;

/// <summary>
/// A base for onboarding-specific commands. Port of
/// <c>com.google.copybara.onboard.OnboardingCmd</c>.
///
/// <para><b>Port note (CLI boundary):</b> upstream this interface extends <c>CopybaraCmd</c> (a
/// Copybara.Cli type) and its methods take a <c>CommandEnv</c>. Copybara.Core cannot reference
/// Copybara.Cli, so the shared onboarding logic is exposed here over the resolved
/// <see cref="Options"/>. The concrete CLI command should be a thin adapter in Copybara.Cli.</para>
/// TODO(port): wire into Copybara.Cli (implement <c>Copybara.Cli.ICopybaraCmd</c>).
/// </summary>
public abstract class OnboardingCmd
{
    public abstract ModuleSet GetModuleSet();

    /// <exception cref="CannotProvideException"/>
    public abstract IReadOnlyList<IInputProvider> GetInputProviders(Options options);

    /// <exception cref="CannotProvideException"/>
    public IInputProviderResolver CreateInputProviderResolver(Options options)
    {
        Console console = options.Get<GeneralOptions>().GetConsole();

        return InputProviderResolverImpl.Create(
            GetInputProviders(options),
            new StarlarkConverter(GetModuleSet(), console),
            options.Get<GeneratorOptions>().AskMode,
            console);
    }
}
