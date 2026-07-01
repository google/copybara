/*
 * Copyright (C) 2019 Google Inc.
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

using System.Text.Json.Serialization;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// Descriptive details about the run. https://developer.github.com/v3/checks/runs/#output-object
/// </summary>
[StarlarkBuiltin("output_obj", Doc = "Descriptive details about the run.")]
public class Output : IStarlarkValue
{
    [JsonPropertyName("title")]
    public string? Title { get; set; }

    [JsonPropertyName("summary")]
    public string? Summary { get; set; }

    [JsonPropertyName("text")]
    public string? Text { get; set; }

    [StarlarkMethod("title", Doc = "The title of the check run.", StructField = true)]
    public string? GetTitle() => Title;

    [StarlarkMethod(
        "summary", Doc = "The summary of the check run.", StructField = true, AllowReturnNones = true)]
    public string? GetSummary() => Summary;

    [StarlarkMethod(
        "text", Doc = "The details of the check run.", StructField = true, AllowReturnNones = true)]
    public string? GetText() => Text;

    public override string ToString() =>
        $"Output{{title={Title}, summary={Summary}, text={Text}}}";
}
