/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.copybara.transform.metadata;

import com.google.copybara.Transformation;
import com.google.copybara.config.base.SkylarkUtil;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import com.google.devtools.build.lib.syntax.EvalException;

/**
 * Metadata module for manipulating metadata of the changes. This is intended to be used by the
 * users for example as:
 * <pre>
 *    metadata_transformations = [
 *      metadata.squash_notes(
 *           prefix = 'Import of Foo project:\n',
 *      ),
 *    ]
 *  </pre>
 */
@SkylarkModule(
    name = "metadata",
    doc = "Core transformations for the change metadata",
    category = SkylarkModuleCategory.BUILTIN)
public class MetadataModule {

  @SuppressWarnings("unused")
  @SkylarkSignature(name = "squash_notes", returnType = Transformation.class,
      doc = "Generate a message that includes a constant prefix text and a list of changes"
          + " included in the squash change.",
      parameters = {
          @Param(name = "self", type = MetadataModule.class, doc = "this object"),
          @Param(name = "prefix", type = String.class,
              doc = "A prefix to be printed before the list of commits.",
              defaultValue = "'Copybara import of the project:\\n\\n'"),
          @Param(name = "max", type = Integer.class,
              doc = "Max number of commits to include in the message. For the rest a comment"
                  + " like (and x more) will be included. By default 100 commits are"
                  + " included.",
              defaultValue = "100"),
          @Param(name = "compact", type = Boolean.class,
              doc = "If compact is set, each change will be shown in just one line",
              defaultValue = "True"),
          @Param(name = "oldest_first", type = Boolean.class,
              doc = "If set to true, the list shows the oldest changes first. Otherwise"
                  + " it shows the changes in descending order.",
              defaultValue = "False"),
      }, objectType = MetadataModule.class, useLocation = true)
  static final BuiltinFunction SQUASH_NOTES = new BuiltinFunction("squash_notes") {
    public Transformation invoke(MetadataModule self, String prefix, Integer max,
        Boolean compact, Boolean oldestFirst, Location location)
        throws EvalException {
      return new MetadataSquashNotes(SkylarkUtil.checkNotEmpty(prefix, "prefix", location),
          max, compact, oldestFirst);
    }
  };

}