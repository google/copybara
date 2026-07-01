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

using System.Collections.Immutable;
using Starlark.Annot;
using Starlark.Eval;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Checks;

// TODO(port): minimal port of com.google.copybara.checks.Checker, created here because the http
// package depends on it. Consolidate if the checks package gets a fuller port.

/// <summary>A generic interface for performing checks on string contents and files.</summary>
[StarlarkBuiltin("checker", Doc = "A checker to be run on arbitrary data and files")]
public interface IChecker : IStarlarkValue
{
    /// <summary>Performs a check on the given contents.</summary>
    /// <exception cref="CheckerException">if the check produced errors.</exception>
    void DoCheck(ImmutableDictionary<string, string> fields, Console console);

    /// <summary>Performs a check on the files inside a given path.</summary>
    /// <exception cref="CheckerException">if the check produced errors.</exception>
    void DoCheck(string target, Console console);
}
