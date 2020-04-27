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

package com.google.copybara.hg;

import static com.google.copybara.config.SkylarkUtil.checkNotEmpty;

import com.google.common.base.Preconditions;
import com.google.copybara.Options;
import com.google.copybara.config.LabelsAwareModule;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.StarlarkBuiltin;
import com.google.devtools.build.lib.skylarkinterface.StarlarkDocumentationCategory;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.StarlarkValue;

/** Main module for Mercurial (Hg) origins and destinations */
@StarlarkBuiltin(
    name = "hg",
    doc = "Set of functions to define Mercurial (Hg) origins and destinations.",
    category = StarlarkDocumentationCategory.BUILTIN)
@UsesFlags(HgOptions.class)
public class HgModule implements LabelsAwareModule, StarlarkValue {

  protected final Options options;

  public HgModule(Options options) { this.options = Preconditions.checkNotNull(options); }

  // TODO(jlliu): look into adding parameter for bookmark
  @SkylarkCallable(
      name = "origin",
      doc = "<b>EXPERIMENTAL:</b> Defines a standard Mercurial (Hg) origin.",
      parameters = {
        @Param(
            name = "url",
            type = String.class,
            named = true,
            doc = "Indicates the URL of the Hg repository"),
        @Param(
            name = "ref",
            type = String.class,
            named = true,
            defaultValue = "\"default\"",
            doc =
                "Represents the default reference that will be used to read a revision "
                    + "from the repository. The reference defaults to `default`, the most recent "
                    + "revision on the default branch. References can be in a variety of "
                    + "formats:<br>"
                    + "<ul> "
                    + "<li> A global identifier for a revision."
                    + " Example: f4e0e692208520203de05557244e573e981f6c72</li>"
                    + "<li> A bookmark in the repository.</li>"
                    + "<li> A branch in the repository, which returns the tip of that branch."
                    + " Example: default</li>"
                    + "<li> A tag in the repository. Example: tip</li>"
                    + "</ul>")
      })
  public HgOrigin origin(String url, String ref) throws EvalException {
    return HgOrigin.newHgOrigin(options, checkNotEmpty(url, "url"), ref);
  }
}
