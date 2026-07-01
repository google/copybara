/*
 * Copyright (C) 2016 Google Inc.
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

/// <summary>https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#git-person-info</summary>
[StarlarkBuiltin("gerritapi.GitPersonInfo", Doc = "Git person information.")]
public class GitPersonInfo : IStarlarkPrintableValue
{
    [JsonPropertyName("name")]
    public string? Name { get; set; }

    [JsonPropertyName("email")]
    public string? Email { get; set; }

    [JsonPropertyName("date")]
    public string? Date { get; set; }

    [JsonPropertyName("tz")]
    public int Tz { get; set; }

    [StarlarkMethod(
        "name",
        Doc = "The name of the author/committer.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetName() => Name;

    [StarlarkMethod(
        "email",
        Doc = "The email address of the author/committer.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetEmail() => Email;

    public DateTimeOffset GetDate() =>
        GerritApiUtil.ParseTimestamp(Date!).ToOffset(TimeSpan.FromMinutes(Tz));

    [StarlarkMethod(
        "date",
        Doc = "The timestamp of when this identity was constructed.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetDateForSkylark() => Date;

    public void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append(ToString());

    public override string ToString() =>
        $"GitPersonInfo{{name={Name}, email={Email}, date={Date}, tz={Tz}}}";
}
