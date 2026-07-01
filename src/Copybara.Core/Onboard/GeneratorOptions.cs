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

using Copybara.Onboard.Core;

namespace Copybara.Onboard;

/// <summary>
/// Options for the generator command. Port of
/// <c>com.google.copybara.onboard.GeneratorOptions</c>.
/// </summary>
public class GeneratorOptions : IOption
{
    [Flag(
        "--generator-ask",
        "Config generator mode when a value is not found. Valid modes:auto, confirm, fail")]
    public AskMode AskMode { get; set; } = AskMode.Confirm;

    [Flag("--template", "Name of the template to use for generating the config")]
    public string? Template { get; set; }

    // Java uses MapConverter to parse comma-separated key=value pairs.
    [Flag("--inputs", "Inputs for code generation")]
    public ImmutableDictionary<string, string> Inputs { get; set; } =
        ImmutableDictionary<string, string>.Empty;

    [Flag(
        "--optimize-globs",
        "When true, ensures no path containing 'AUTOPATCHES' is added to the destination excludes"
            + " lists.")]
    public bool OptimizeGlobs { get; set; } = false;

    [Flag(
        "--new-package",
        "Whether or not files from this package exist in the destination.")]
    public bool NewPackage { get; set; }

    [Flag(
        "--compute-glob-ignore-carriage-return",
        "Whether to ignore carriage return characters in file content comparisons during glob"
            + " generation.",
        Arity = 1)]
    public bool ComputeGlobIgnoreCarriageReturn { get; set; } = true;

    [Flag(
        "--compute-glob-ignore-whitespace",
        "Whether to ignore whitespace in file content comparisons during glob generation.",
        Arity = 1)]
    public bool ComputeGlobIgnoreWhitespace { get; set; } = true;

    [Flag(
        "--compute-glob-percentage-similar",
        "Percentage of similarity required for considering an origin file similar enough to a"
            + " destination file in heuristics.")]
    public int ComputeGlobPercentageSimilar { get; set; } = 30;
}
