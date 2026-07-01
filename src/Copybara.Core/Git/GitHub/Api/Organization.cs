/*
 * Copyright (C) 2022 Google Inc.
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

namespace Copybara.Git.GitHub.Api;

/// <summary>
/// Corresponds to JSON schema response for getting a GitHub organization detailed in
/// https://docs.github.com/en/rest/orgs/orgs#get-an-organization.
///
/// <para>Not all property keys are included here. Add them as needed.</para>
/// </summary>
public class Organization
{
    [JsonPropertyName("name")]
    public string? Name { get; set; }

    [JsonPropertyName("two_factor_requirement_enabled")]
    public bool? TwoFactorRequirementEnabled { get; set; }

    public bool? GetTwoFactorRequirementEnabled() => TwoFactorRequirementEnabled;

    public string? GetName() => Name;

    public override string ToString() =>
        $"Organization{{name={Name}, two_factor_requirement_enabled={TwoFactorRequirementEnabled}}}";
}
