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

using Copybara.Common;
using Copybara.Config;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Hg;

/// <summary>Main module for Mercurial (Hg) origins and destinations.</summary>
[StarlarkBuiltin("hg", Doc = "Set of functions to define Mercurial (Hg) origins and destinations.")]
public class HgModule : ILabelsAwareModule, IStarlarkValue
{
    protected readonly Options Options;

    public HgModule(Options options)
    {
        Options = Preconditions.CheckNotNull(options);
    }

    // TODO(jlliu): look into adding parameter for bookmark
    [StarlarkMethod(
        "origin",
        Doc = "<b>EXPERIMENTAL:</b> Defines a standard Mercurial (Hg) origin.")]
    public HgOrigin Origin(
        [Param(Name = "url", Named = true, Doc = "Indicates the URL of the Hg repository")]
        string url,
        [Param(
            Name = "ref",
            Named = true,
            DefaultValue = "\"default\"",
            Doc =
                "Represents the default reference that will be used to read a revision from the"
                + " repository. The reference defaults to `default`, the most recent revision on the"
                + " default branch. References can be in a variety of formats:<br>"
                + "<ul> "
                + "<li> A global identifier for a revision."
                + " Example: f4e0e692208520203de05557244e573e981f6c72</li>"
                + "<li> A bookmark in the repository.</li>"
                + "<li> A branch in the repository, which returns the tip of that branch."
                + " Example: default</li>"
                + "<li> A tag in the repository. Example: tip</li>"
                + "</ul>")]
        string @ref)
    {
        return HgOrigin.NewHgOrigin(Options, SkylarkUtil.CheckNotEmpty(url, "url"), @ref);
    }
}
