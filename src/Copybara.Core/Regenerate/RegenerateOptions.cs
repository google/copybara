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

namespace Copybara.Regenerate;

/// <summary>RegenerateOptions modifies behavior of the regenerate command.</summary>
public class RegenerateOptions : IOption
{
    public RegenerateOptions()
    {
    }

    /// <summary>A value identifying a destination revision with consistent patch files state.</summary>
    [Flag(
        "--regen-baseline",
        "a value identifying a destination revision with consistent patch files state")]
    private string? RegenBaseline { get; set; }

    /// <summary>Create the baseline by doing a workflow import.</summary>
    [Flag(
        "--regen-import-baseline",
        "create the baseline by doing a workflow import",
        Arity = 1)]
    private bool RegenImportBaseline { get; set; }

    /// <summary>
    /// A value identifying the current destination revision to generate patch files against.
    /// </summary>
    [Flag(
        "--regen-target",
        "a value identifying the current destination revision to generate patch files against")]
    private string? RegenTarget { get; set; }

    public string? GetRegenBaseline() => RegenBaseline;

    public bool GetUseImportBaseline() => RegenImportBaseline;

    public string? GetRegenTarget() => RegenTarget;

    public void SetRegenBaseline(string? regenBaseline) => RegenBaseline = regenBaseline;

    public void SetRegenImportBaseline(bool regenImportBaseline) =>
        RegenImportBaseline = regenImportBaseline;

    public void SetRegenTarget(string? regenTarget) => RegenTarget = regenTarget;
}
