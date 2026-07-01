/*
 * Copyright (C) 2018 Google Inc.
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
using Copybara.Common;

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// Request for creating commit statuses:
///
/// <para>https://developer.github.com/v3/repos/statuses/#create-a-status</para>
/// </summary>
public class CreateStatusRequest
{
    [JsonPropertyName("state")]
    public StatusState State { get; set; }

    [JsonPropertyName("target_url")]
    public string? TargetUrl { get; set; }

    [JsonPropertyName("description")]
    public string? Description { get; set; }

    [JsonPropertyName("context")]
    public string Context { get; set; }

    public CreateStatusRequest(
        StatusState state, string? targetUrl, string? description, string context)
    {
        State = state;
        TargetUrl = targetUrl;
        Description = description;
        Context = Preconditions.CheckNotNull(context);
    }

    public StatusState GetState() => State;

    public string? GetTargetUrl() => TargetUrl;

    public string? GetDescription() => Description;

    public string GetContext() => Context;
}
