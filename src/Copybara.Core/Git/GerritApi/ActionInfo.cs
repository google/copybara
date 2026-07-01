/*
 * Copyright (C) 2020 Google Inc.
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

namespace Copybara.Git.GerritApi;

/// <summary>https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#action-info</summary>
[StarlarkBuiltin("gerritapi.getActionInfo", Doc = "Gerrit actions information.")]
public class ActionInfo : IStarlarkPrintableValue
{
    [JsonPropertyName("method")]
    public string? Method { get; set; }

    [JsonPropertyName("label")]
    public string? Label { get; set; }

    [JsonPropertyName("title")]
    public string? Title { get; set; }

    [JsonPropertyName("enabled")]
    public bool Enabled { get; set; }

    public ActionInfo()
    {
    }

    public ActionInfo(string method, string label, string title, bool enabled)
    {
        Method = method;
        Label = label;
        Title = title;
        Enabled = enabled;
    }

    public string? GetMethod() => Method;

    [StarlarkMethod(
        "label",
        Doc = "Short title to display to a user describing the action",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetLabel() => Label;

    public string? GetTitle() => Title;

    [StarlarkMethod(
        "enabled",
        Doc =
            "If true the action is permitted at this time and the caller is likely "
            + "allowed to execute it.",
        StructField = true)]
    public bool GetEnabled() => Enabled;

    public void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append(ToString());

    public override string ToString() =>
        $"ActionInfo{{method={Method}, label={Label}, title={Title}, enabled={Enabled}}}";
}
