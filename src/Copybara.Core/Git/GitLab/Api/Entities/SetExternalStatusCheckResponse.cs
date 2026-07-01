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

/// <summary>Represents the response from setting an external status check on a GitLab project.</summary>
/// <seealso href="https://docs.gitlab.com/api/status_checks"/>
public sealed class SetExternalStatusCheckResponse : IGitLabApiEntity
{
    [JsonPropertyName("id")]
    public int SetExternalStatusCheckResponseId { get; set; }

    [JsonPropertyName("merge_request")]
    public MergeRequest? MergeRequest { get; set; }

    [JsonPropertyName("external_status_check")]
    public ExternalStatusCheck? ExternalStatusCheck { get; set; }

    /// <summary>Creates a new instance of <see cref="SetExternalStatusCheckResponse"/>.</summary>
    public SetExternalStatusCheckResponse()
    {
    }

    public SetExternalStatusCheckResponse(
        int setExternalStatusCheckResponseId,
        MergeRequest? mergeRequest,
        ExternalStatusCheck? externalStatusCheck)
    {
        SetExternalStatusCheckResponseId = setExternalStatusCheckResponseId;
        MergeRequest = mergeRequest;
        ExternalStatusCheck = externalStatusCheck;
    }

    public int GetSetExternalStatusCheckResponseId() => SetExternalStatusCheckResponseId;

    public MergeRequest? GetMergeRequest() => MergeRequest;

    public ExternalStatusCheck? GetExternalStatusCheck() => ExternalStatusCheck;
}
