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

using Copybara.Util;

namespace Copybara.Onboard;

/// <summary>
/// Command that assists users in creating a config file. This is still experimental and a work in
/// progress. In the near term, users are able to generate a simple config by running
/// <c>copybara onboard</c> and following onscreen prompts. Port of
/// <c>com.google.copybara.onboard.OnboardCmd</c>.
///
/// <para><b>Port note (CLI boundary):</b> the upstream class implements <c>CopybaraCmd</c> and takes
/// a <c>CommandEnv</c> in <c>run</c>. Both live in the Copybara.Cli project, which Copybara.Core
/// cannot reference. The logic is preserved here behind a plain <see cref="Run(Options)"/> entry that
/// takes the already-resolved <see cref="Options"/>.</para>
/// TODO(port): wire into Copybara.Cli (implement <c>Copybara.Cli.ICopybaraCmd</c> in a thin adapter
/// that forwards <c>CommandEnv.getOptions()</c> to <see cref="Run(Options)"/>).
/// </summary>
public class OnboardCmd
{
    public OnboardCmd()
    {
    }

    public ExitCode Run(Options options)
    {
        try
        {
            CommandLineGuide.RunForCommandLine(options);
            return ExitCode.Success;
        }
        catch (Exception)
        {
            return ExitCode.CommandLineError;
        }
    }

    public string Name => "onboard";
}
