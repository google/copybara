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

using Copybara.Util;

namespace Copybara.Cli;

/// <summary>
/// A Copybara command like 'info', 'migrate', etc.
/// </summary>
public interface ICopybaraCmd
{
    /// <summary>Run the command.</summary>
    /// <param name="commandEnv">Command environment: params, workdir, etc.</param>
    /// <returns>Result exit code.</returns>
    /// <exception cref="Copybara.Exceptions.ValidationException"/>
    /// <exception cref="System.IO.IOException"/>
    /// <exception cref="Copybara.Exceptions.RepoException"/>
    ExitCode Run(CommandEnv commandEnv);

    /// <summary>Command name.</summary>
    string Name { get; }
}
