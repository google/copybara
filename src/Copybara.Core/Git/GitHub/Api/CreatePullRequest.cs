/*
 * Copyright (C) 2017 Google Inc.
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

/// <summary>An object that represents the creation of a Pull Request.</summary>
public class CreatePullRequest
{
    [JsonPropertyName("title")]
    public string? Title { get; set; }

    [JsonPropertyName("body")]
    public string? Body { get; set; }

    /// <summary>
    /// Branch to use for the pull request, can be a reference to another github repository if
    /// somerepo:branch format is used.
    /// </summary>
    [JsonPropertyName("head")]
    public string? Head { get; set; }

    /// <summary>Base of the pull request, usually something like 'master'.</summary>
    [JsonPropertyName("base")]
    public string? Base { get; set; }

    [JsonPropertyName("draft")]
    public bool Draft { get; set; }

    public string? GetTitle() => Title;

    public string? GetBody() => Body;

    public string? GetHead() => Head;

    public string? GetBase() => Base;

    public void SetTitle(string title) => Title = title;

    public bool GetDraft() => Draft;

    public CreatePullRequest(string title, string body, string head, string @base, bool draft)
    {
        Title = Preconditions.CheckNotNull(title);
        Body = Preconditions.CheckNotNull(body);
        Head = Preconditions.CheckNotNull(head);
        Base = Preconditions.CheckNotNull(@base);
        Draft = draft;
    }

    public CreatePullRequest()
    {
    }
}
