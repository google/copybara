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
using Starlark.Eval;

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// Common fields for issues and pull requests.
///
/// <para>There are more fields but they are ignored for now.</para>
/// </summary>
public class PullRequestOrIssue : IStarlarkValue
{
    [JsonPropertyName("number")]
    public long Number { get; set; }

    [JsonPropertyName("state")]
    public string? State { get; set; }

    [JsonPropertyName("title")]
    public string? Title { get; set; }

    [JsonPropertyName("body")]
    public string? Body { get; set; }

    [JsonPropertyName("created_at")]
    public string? CreatedAt { get; set; }

    [JsonPropertyName("updated_at")]
    public string? UpdatedAt { get; set; }

    [JsonPropertyName("html_url")]
    public string? HtmlUrl { get; set; }

    [JsonPropertyName("user")]
    public User? UserValue { get; set; }

    [JsonPropertyName("assignee")]
    public User? Assignee { get; set; }

    [JsonPropertyName("assignees")]
    public List<User>? AssigneesList { get; set; }

    public long GetNumber() => Number;

    [StarlarkMethod("number", Doc = "Pull Request number", StructField = true)]
    public StarlarkInt GetNumberForSkylark() => StarlarkInt.Of(Number);

    public string? GetState() => State;

    [StarlarkMethod("state", Doc = "Pull Request state", StructField = true)]
    public string? GetStateForSkylark() => State?.ToUpperInvariant();

    [StarlarkMethod("title", Doc = "Pull Request title", StructField = true)]
    public string? GetTitle() => Title;

    [StarlarkMethod("body", Doc = "Pull Request body", StructField = true)]
    public string? GetBody() => Body;

    public DateTimeOffset GetCreatedAt() => DateTimeOffset.Parse(CreatedAt!);

    public DateTimeOffset GetModifiedAt() => DateTimeOffset.Parse(CreatedAt!);

    public string? GetHtmlUrl() => HtmlUrl;

    public bool IsOpen() => "open".Equals(State);

    [StarlarkMethod("assignee", Doc = "Pull Request assignee", StructField = true, AllowReturnNones = true)]
    public User? GetAssignee() => Assignee;

    [StarlarkMethod("user", Doc = "Pull Request owner", StructField = true)]
    public User? GetUser() => UserValue;

    public IReadOnlyList<User> GetAssignees() =>
        AssigneesList == null ? ImmutableArray<User>.Empty : AssigneesList.ToImmutableArray();

    public override string ToString() =>
        $"{{number={Number}, state={State}, title={Title}, body={Body}, created_at={CreatedAt},"
        + $" updated_at={UpdatedAt}}}";
}
