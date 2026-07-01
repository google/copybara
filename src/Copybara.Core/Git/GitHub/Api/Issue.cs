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

using System.Collections.Immutable;
using System.Text.Json.Serialization;
using Starlark.Annot;

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// Represents an issue returned by https://api.github.com/repos/REPO_ID/issues/NUMBER.
/// </summary>
[StarlarkBuiltin("Issue", Doc = "Github issue object")]
public class Issue : PullRequestOrIssue
{
    [JsonPropertyName("labels")]
    public List<Label>? Labels { get; set; }

    public List<Label>? GetLabels() => Labels;

    public void SetLabels(List<Label> labels) => Labels = labels;

    public override string ToString() => base.ToString();

    /// <summary>
    /// Represents https://docs.github.com/en/rest/reference/issues#create-an-issue--parameters.
    /// </summary>
    public class CreateIssueRequest
    {
        [JsonPropertyName("title")]
        public string? Title { get; set; }

        [JsonPropertyName("body")]
        public string? Body { get; set; }

        [JsonPropertyName("assignees")]
        public List<string>? Assignees { get; set; }

        public CreateIssueRequest(string title, string body, IReadOnlyList<string> assignees)
        {
            Title = title;
            Body = body;
            Assignees = new List<string>(assignees);
        }

        public CreateIssueRequest()
        {
        }

        public string? GetTitle() => Title;

        public string? GetBody() => Body;

        public IReadOnlyList<string> GetAssignees() =>
            Assignees == null ? ImmutableArray<string>.Empty : Assignees.ToImmutableArray();
    }
}
