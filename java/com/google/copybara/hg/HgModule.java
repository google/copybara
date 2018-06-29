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
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.EvalException;

/**
 * Main module for Mercurial (Hg) origins and destinations
 */
@SkylarkModule(
    name = "hg",
    doc = "Set of functions to define Mercurial (Hg) origins and destinations.",
    documented = false,
    category = SkylarkModuleCategory.BUILTIN)
public class HgModule implements LabelsAwareModule{

  protected final Options options;

  public HgModule(Options options) { this.options = Preconditions.checkNotNull(options); }

  //TODO(jlliu): look into adding parameter for bookmark
  @SkylarkCallable(name = "origin",
      doc = "<b>EXPERIMENTAL:</b> Defines a standard Mercurial (Hg) origin.",
      parameters = {
          @Param(name = "url",
              type = String.class,
              named = true,
              doc = "Indicates the URL of the Hg repository"),
          @Param(name = "branch",
              type = String.class,
              named = true,
              defaultValue = "'default'",
              doc = "Represents the branch that will be used to read from the repository. "
                  + "The branch defaults to 'default'.")},
          useLocation = true)
  public HgOrigin origin(String url, String branch, Location location) throws EvalException {
    return HgOrigin.newHgOrigin(options, checkNotEmpty(url, "url", location), branch);
  }
}