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

/// <summary>Restricted version of <see cref="CommitInfo"/> for describing parents.</summary>
[StarlarkBuiltin("gerritapi.ParentCommitInfo", Doc = "Gerrit parent commit information.")]
public class ParentCommitInfo : IStarlarkPrintableValue
{
    [JsonPropertyName("commit")]
    public string? Commit { get; set; }

    [JsonPropertyName("subject")]
    public string? Subject { get; set; }

    [StarlarkMethod(
        "commit",
        Doc =
            "The commit ID. Not set if included in a RevisionInfo entity that is contained "
            + "in a map which has the commit ID as key.",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetCommit() => Commit;

    [StarlarkMethod(
        "subject",
        Doc = "The subject of the commit (header line of the commit message).",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetSubject() => Subject;

    public void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append(ToString());

    public override string ToString() =>
        $"ParentCommitInfo{{commit={Commit}, subject={Subject}}}";
}
