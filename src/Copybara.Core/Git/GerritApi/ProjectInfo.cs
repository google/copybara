/*
 * Copyright (C) 2017 Google Inc.
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
using Starlark.Eval;

namespace Copybara.Git.GerritApi;

/// <summary>See https://gerrit-review.googlesource.com/Documentation/rest-api-projects.html#project-info</summary>
public class ProjectInfo : IStarlarkPrintableValue
{
    [JsonPropertyName("id")]
    public string? Id { get; set; }

    [JsonPropertyName("name")]
    public string? Name { get; set; }

    [JsonPropertyName("parent")]
    public string? Parent { get; set; }

    [JsonPropertyName("description")]
    public string? Description { get; set; }

    [JsonPropertyName("state")]
    public string? StateString { get; set; }

    [JsonPropertyName("branches")]
    public IReadOnlyDictionary<string, string>? Branches { get; set; }

    public enum State
    {
        ACTIVE,
        READ_ONLY,
        HIDDEN,
    }

    public string? GetId() => Id;

    public string? GetName() => Name;

    public string? GetParent() => Parent;

    public string? GetDescription() => Description;

    public State? GetState() => StateString is null ? null : Enum.Parse<State>(StateString);

    public IReadOnlyDictionary<string, string>? GetBranches() => Branches;

    public void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append(ToString());

    public override string ToString() =>
        $"ProjectInfo{{id={Id}, name={Name}, parent={Parent}, description={Description}, "
        + $"state={StateString}, branches={Branches}}}";
}
