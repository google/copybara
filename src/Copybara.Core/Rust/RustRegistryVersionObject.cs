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

namespace Copybara.Rust;

/// <summary>
/// A data class that represents a version returned from a Rust crate registry, such as crates.io.
/// <a href="https://github.com/rust-lang/rfcs/blob/master/text/2141-alternative-registries.md#registry-index-format-specification">...</a>
/// </summary>
public class RustRegistryVersionObject
{
    [JsonPropertyName("name")]
    public string? Name { get; set; }

    [JsonPropertyName("vers")]
    public string? Vers { get; set; }

    [JsonPropertyName("deps")]
    public List<Deps>? DepsList { get; set; }

    [JsonPropertyName("cksum")]
    public string? Cksum { get; set; }

    [JsonPropertyName("features")]
    public Dictionary<string, List<string>>? Features { get; set; }

    [JsonPropertyName("yanked")]
    public bool Yanked { get; set; }

    public RustRegistryVersionObject()
    {
    }

    public string GetName() => Name!;

    public string GetVers() => Vers!;

    public List<Deps>? GetDeps() => DepsList;

    public Dictionary<string, List<string>>? GetFeatures() => Features;

    public bool IsYanked() => Yanked;

    public override string ToString() =>
        $"RustRegistryVersionObject{{name={Name}, vers={Vers}, cksum={Cksum}, yanked={Yanked}}}";

    /// <summary>A class that represents a crate dependency.</summary>
    public class Deps
    {
        [JsonPropertyName("name")]
        public string? Name { get; set; }

        [JsonPropertyName("req")]
        public string? Req { get; set; }

        [JsonPropertyName("registry")]
        public string? Registry { get; set; }

        [JsonPropertyName("features")]
        public List<string>? Features { get; set; }

        [JsonPropertyName("optional")]
        public bool Optional { get; set; }

        [JsonPropertyName("default_features")]
        public bool DefaultFeatures { get; set; }

        [JsonPropertyName("target")]
        public string? Target { get; set; }

        [JsonPropertyName("kind")]
        public DepsKinds Kind { get; set; }

        public Deps()
        {
        }

        [JsonConverter(typeof(JsonStringEnumConverter<DepsKinds>))]
        public enum DepsKinds
        {
            [JsonStringEnumMemberName("normal")]
            Normal,

            [JsonStringEnumMemberName("build")]
            Build,

            [JsonStringEnumMemberName("dev")]
            Dev,
        }

        public string GetName() => Name!;

        public List<string>? GetFeatures() => Features;

        public override string ToString() =>
            $"Deps{{name={Name}, req={Req}, registry={Registry}, optional={Optional},"
            + $" defaultFeatures={DefaultFeatures}, target={Target}, kind={Kind}}}";
    }
}
