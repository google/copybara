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

using System.Collections.Immutable;
using System.Text.Json.Serialization;
using Copybara.Common;

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// Request type for adding a label to an issue.
/// https://developer.github.com/v3/issues/labels/#add-labels-to-an-issue
/// </summary>
public class AddLabels
{
    [JsonPropertyName("labels")]
    public List<string>? Labels { get; set; }

    public AddLabels(IReadOnlyList<string> labels)
    {
        Preconditions.CheckNotNull(labels);
        Labels = new List<string>(labels);
    }

    public AddLabels()
    {
    }

    public IReadOnlyList<string> GetLabels() =>
        Labels == null ? ImmutableArray<string>.Empty : Labels.ToImmutableArray();
}
