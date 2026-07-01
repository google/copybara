/*
 * Copyright (C) 2023 Google Inc.
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

namespace Copybara.Go;

/// <summary>
/// A data class that can be used to parse a json string into an object for response from
/// https://go.dev/ref/mod#goproxy-protocol
/// </summary>
public class GoVersionObject : IStarlarkValue
{
    [JsonPropertyName("Version")]
    public string? Version { get; set; }

    [JsonPropertyName("Time")]
    public string? Time { get; set; }

    [JsonPropertyName("Origin")]
    public GoOrigin? Origin { get; set; }

    public GoVersionObject()
    {
    }

    [StarlarkMethod("version", Doc = "The Version value from goproxy", StructField = true)]
    public string GetVersion() => Version!;

    [StarlarkMethod("time", Doc = "The Time value from goproxy", StructField = true)]
    public string GetTime() => Time!;

    [StarlarkMethod("origin",
        Doc = "The Origin value from goproxy, if any",
        StructField = true,
        AllowReturnNones = true)]
    public GoOrigin? GetOrigin() => Origin;

    public override string ToString() =>
        $"GoVersionObject{{Version={Version}, Time={Time}}}";

    /// <summary>A data class that represents the optional Origin field in GoVersionObject.</summary>
    public class GoOrigin : IStarlarkValue
    {
        [JsonPropertyName("VCS")]
        public string? Vcs { get; set; }

        [JsonPropertyName("URL")]
        public string? Url { get; set; }

        [JsonPropertyName("Ref")]
        public string? Ref { get; set; }

        [JsonPropertyName("Hash")]
        public string? Hash { get; set; }

        [StarlarkMethod("vcs",
            Doc = "The Origin.VCS value from goproxy",
            StructField = true,
            AllowReturnNones = true)]
        public string? GetVcs() => Vcs;

        [StarlarkMethod("url",
            Doc = "The Origin.URL value from goproxy",
            StructField = true,
            AllowReturnNones = true)]
        public string? GetUrl() => Url;

        [StarlarkMethod("ref",
            Doc = "The Origin.Ref value from goproxy",
            StructField = true,
            AllowReturnNones = true)]
        public string? GetRef() => Ref;

        [StarlarkMethod("hash",
            Doc = "The Origin.Hash value from goproxy",
            StructField = true,
            AllowReturnNones = true)]
        public string? GetHash() => Hash;
    }
}
