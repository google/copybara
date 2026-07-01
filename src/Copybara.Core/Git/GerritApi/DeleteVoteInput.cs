/*
 * Copyright (C) 2020 Google Inc.
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

namespace Copybara.Git.GerritApi;

/// <summary>
/// See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#delete-vote-input
/// request json.
/// <para>NotifyInfo (notify_details) not included for now.</para>
/// <para>label not included for now since it matches the label in the URL.</para>
/// </summary>
public class DeleteVoteInput
{
    [JsonPropertyName("notify")]
    public string? Notify { get; set; }

    public DeleteVoteInput(NotifyType? notify)
    {
        Notify = notify?.ToWireValue();
    }
}
