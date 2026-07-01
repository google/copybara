/*
 * Copyright (C) 2021 Google Inc.
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
using System.Text.RegularExpressions;

namespace Copybara.Onboard;

/// <summary>
/// A template object for a <c>core.workflow()</c> git-to-git Copybara workflow. Port of
/// <c>com.google.copybara.onboard.GitToGitTemplate</c>.
/// </summary>
internal sealed class GitToGitTemplate : IConfigTemplate
{
    // Java re2j used (?P<name>...); .NET regex uses (?<name>...). Only ever used for a full match.
    public static readonly Regex AuthorPattern = new("^(?<name>[^<]+)<(?<email>[^>]*)>$");

    private readonly ImmutableHashSet<RequiredField> _requiredFields =
        ImmutableHashSet.Create(
            RequiredField.Create(
                "origin_url",
                ConfigTemplateFieldClass.String,
                ConfigTemplateLocation.Named,
                "Git URL to serve as origin repository.",
                s => s != null),
            RequiredField.Create(
                "destination_url",
                ConfigTemplateFieldClass.String,
                ConfigTemplateLocation.Named,
                "Git URL to serve as destination repository",
                s => s != null),
            RequiredField.Create(
                "email",
                ConfigTemplateFieldClass.String,
                ConfigTemplateLocation.Named,
                "Team email to be used for authoring",
                s => AuthorPattern.IsMatch(s)));

    private readonly ImmutableHashSet<OptionalField> _optionalFields =
        ImmutableHashSet.Create(
            OptionalField.Create(
                "name",
                ConfigTemplateFieldClass.String,
                ConfigTemplateLocation.Keyword,
                "Name for the workflow",
                s => s != null,
                "default"));

    public IReadOnlySet<RequiredField> GetRequiredFields() => _requiredFields;

    public IReadOnlySet<OptionalField> GetOptionalFields() => _optionalFields;

    public bool Validate(string configInProgress) =>
        !_requiredFields.Any(x => configInProgress.Contains(x.Name));

    public string GetTemplateString() =>
        "transformations = [\n"
        + "    # TODO: Insert your transformations here\n"
        + "]\n"
        + "\n"
        + "core.workflow(\n"
        + "    name = 'default',\n"
        + "    origin = git.origin(\n"
        + "    url = ::origin_url::), \n"
        + "    destination = git.destination(\n"
        + "    url = ::destination_url::),\n"
        + "    authoring = authoring.pass_thru(::email::),\n"
        + "::keyword_params::\n"
        + "    transformations = transformations,\n"
        + ")";

    public string Name => "git_to_git";
}
