/*
 * Copyright (C) 2024 Google LLC.
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

namespace Copybara.TsJs.Npm;

/// <summary>
/// A data class that can be used to convert the JSON response from the Npm registry
/// (https://registry.npmjs.com). For more details on the registry, see the official documentation on
/// Github: https://github.com/npm/registry/blob/master/docs/REGISTRY-API.md
/// </summary>
public class NpmVersionListResponseObject
{
    [JsonPropertyName("dist-tags")]
    public Dictionary<string, string>? DistTags { get; set; }

    [JsonPropertyName("versions")]
    public Dictionary<string, NpmVersionInfo>? Versions { get; set; }

    public NpmVersionListResponseObject()
    {
    }

    public NpmVersionInfo GetLatestVersion()
    {
        string versionId = DistTags!["latest"];
        return Versions![versionId];
    }

    public NpmVersionInfo GetVersionInfo(string versionId) => Versions![versionId];

    public ISet<string> GetAllVersions() => new HashSet<string>(Versions!.Keys);

    public override string ToString() =>
        $"NpmVersionListResponseObject{{dist-tags={DistTags}, versions={Versions}}}";
}
