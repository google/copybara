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
/// An object that represents the update of a reference.
/// https://developer.github.com/v3/git/refs/#update-a-reference
/// </summary>
public class UpdateReferenceRequest
{
    [JsonPropertyName("sha")]
    public string Sha { get; set; }

    [JsonPropertyName("force")]
    public bool Force { get; set; }

    public string GetSha1() => Sha;

    public bool GetForce() => Force;

    public UpdateReferenceRequest(string sha, bool force)
    {
        Sha = Preconditions.CheckNotNull(sha);
        Force = force;
    }
}
