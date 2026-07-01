/*
 * Copyright (C) 2025 Google LLC
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

namespace Copybara.Git.GitLab.Api.Entities;

/// <summary>Params used when updating a merge request using the GitLab API.</summary>
public sealed record UpdateMergeRequestParams(
    [property: JsonPropertyName("id")] int ProjectId,
    [property: JsonPropertyName("merge_request_iid")] int MergeRequestIid,
    [property: JsonPropertyName("title")]
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    string? Title,
    [property: JsonPropertyName("description")]
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    string? Description,
    [property: JsonPropertyName("assignee_ids")] IReadOnlyList<int> AssigneeIds,
    [property: JsonPropertyName("state_event")]
    [property: JsonIgnore(Condition = JsonIgnoreCondition.WhenWritingNull)]
    UpdateMergeRequestParams.StateEvent? StateEventValue)
    : IGitLabApiEntity
{
    /// <summary>Represents the states that we can update a merge request to.</summary>
    [JsonConverter(typeof(JsonStringEnumConverter<StateEvent>))]
    public enum StateEvent
    {
        [JsonStringEnumMemberName("close")]
        Close,

        [JsonStringEnumMemberName("reopen")]
        Reopen,
    }
}
