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

using System.Collections.Immutable;

using Copybara.Onboard.Core;
using Copybara.Onboard.Core.Template;

namespace Copybara.Onboard;

/// <summary>
/// A template object for a <c>core.workflow()</c> git-to-git Copybara workflow. Port of
/// <c>com.google.copybara.onboard.GitToGitGenerator</c>.
/// </summary>
public sealed class GitToGitGenerator : TemplateConfigGenerator
{
    private const string Template =
        "core.workflow(\n"
        + "    name = '::name::',\n"
        + "    origin = git.origin(\n"
        + "        url = \"::origin_url::\",\n"
        + "    ), \n"
        + "    destination = git.destination(\n"
        + "        url = \"::destination_url::\",\n"
        + "    ),\n"
        + "    authoring = authoring.pass_thru(\"::email::\"),\n"
        + "    ::keyword_params::\n"
        + "    transformations = [\n"
        + "        # TODO: Insert your transformations here\n"
        + "    ],\n"
        + ")\n";

    public GitToGitGenerator()
        : base(Template)
    {
    }

    protected override IReadOnlyDictionary<Field, object> Resolve(IInputProviderResolver resolver)
    {
        var result = new Dictionary<Field, object>();

        result[Field.CreateRequired("origin_url")] = resolver.Resolve(Inputs.GitOriginUrl);
        result[Field.CreateRequired("destination_url")] = resolver.Resolve(Inputs.GitDestinationUrl);
        result[Field.CreateRequired("email")] = resolver.Resolve(Inputs.DefaultAuthor);

        string? name = resolver.ResolveOptional(Inputs.MigrationName);
        result[Field.CreateRequired("name")] =
            !string.IsNullOrEmpty(name) ? name : "default";

        return result;
    }

    public override string Name => "git_to_git";

    public override IReadOnlySet<IInput> Consumes() =>
        ImmutableHashSet.Create<IInput>(
            Inputs.GitOriginUrl,
            Inputs.GitDestinationUrl,
            Inputs.DefaultAuthor,
            Inputs.MigrationName);

    public override string ToString() => Name;

    /// <summary>No autodetection for this template for now.</summary>
    public override bool IsGenerator(IInputProviderResolver resolver) => false;
}
