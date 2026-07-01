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

/// <summary>Params used when calling the "set external status check" API endpoint.</summary>
/// <seealso href="https://docs.gitlab.com/api/status_checks/#set-status-of-an-external-status-check"/>
public sealed record SetExternalStatusCheckParams(
    [property: JsonPropertyName("id")] int ProjectId,
    [property: JsonPropertyName("merge_request_iid")] int MergeRequestIid,
    [property: JsonPropertyName("sha")] string Sha,
    [property: JsonPropertyName("external_status_check_id")] int ExternalStatusCheckId,
    [property: JsonPropertyName("status")] string Status)
    : IGitLabApiEntity;
