/*
 * Copyright (C) 2019 Google Inc.
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

/// <summary>Request type for updating a pull request.</summary>
public class UpdatePullRequest
{
    [JsonPropertyName("title")]
    public string? Title { get; set; }

    [JsonPropertyName("body")]
    public string? Body { get; set; }

    [JsonPropertyName("state")]
    [JsonConverter(typeof(JsonStringEnumConverter<UpdatePullRequestState>))]
    public UpdatePullRequestState? State { get; set; }

    public UpdatePullRequest()
    {
    }

    public UpdatePullRequest(string? title, string? body, UpdatePullRequestState? state)
    {
        Title = title;
        Body = body;
        State = state;
        Preconditions.CheckArgument(
            title != null || body != null || state != null,
            "No state change provided. At least one field needs to be not-null");
    }

    public string? GetTitle() => Title;

    public string? GetBody() => Body;

    public UpdatePullRequestState? GetState() => State;
}

/// <summary>The state a pull request can be updated to.</summary>
public enum UpdatePullRequestState
{
    [JsonStringEnumMemberName("open")]
    OPEN,

    [JsonStringEnumMemberName("closed")]
    CLOSED,
}
