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
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Git.GitHub.Api;

/// <summary>Represents the current status of a ref, as returned by the git/refs API call.</summary>
[StarlarkBuiltin(
    "github_api_ref_obj",
    Doc =
        "Information about a commit status as defined in"
        + " https://developer.github.com/v3/repos/statuses. This is a subset of the available"
        + " fields in GitHub")]
public class Ref : IStarlarkValue
{
    [JsonPropertyName("ref")]
    public string? RefName { get; set; }

    [JsonPropertyName("url")]
    public string? Url { get; set; }

    [JsonPropertyName("object")]
    public RefData? Object { get; set; }

    /// <summary>The internal data field.</summary>
    public class RefData
    {
        [JsonPropertyName("sha")]
        public string? Sha { get; set; }

        [JsonPropertyName("type")]
        public string? Type { get; set; }

        [JsonPropertyName("url")]
        public string? Url { get; set; }
    }

    [StarlarkMethod("ref", Doc = "The name of the reference", StructField = true)]
    public string? GetRef() => RefName;

    [StarlarkMethod("url", Doc = "The url of the reference", StructField = true)]
    public string? GetUrl() => Url;

    [StarlarkMethod("sha", Doc = "The sha of the reference", StructField = true)]
    public string? GetSha() => Object?.Sha;

    public override string ToString() =>
        $"Ref{{ref={RefName}, url={Url}, object.sha={Object?.Sha}, object.type={Object?.Type},"
        + $" object.url={Object?.Url}}}";
}
