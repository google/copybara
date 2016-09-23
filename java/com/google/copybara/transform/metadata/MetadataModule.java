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
import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;

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

  @SuppressWarnings("unused")
  @SkylarkSignature(name = "save_author", returnType = Transformation.class,
      doc = "For a given change, store a copy of the author as a label with the name"
          + " ORIGINAL_AUTHOR.",
      parameters = {
          @Param(name = "self", type = MetadataModule.class, doc = "this object"),
          @Param(name = "label", type = String.class,
              doc = "The label to use for storing the author",
              defaultValue = "'ORIGINAL_AUTHOR'"),
      }, objectType = MetadataModule.class, useLocation = true)
  static final BuiltinFunction SAVE_ORIGINAL_AUTHOR = new BuiltinFunction("save_author") {
    public Transformation invoke(MetadataModule self, String label, Location location)
        throws EvalException {
      return new SaveOriginalAuthor(label);
    }
  };

  @SuppressWarnings("unused")
  @SkylarkSignature(name = "restore_author", returnType = Transformation.class,
      doc = "For a given change, restore the author present in the ORIGINAL_AUTHOR label as the"
          + " author of the change.",
      parameters = {
          @Param(name = "self", type = MetadataModule.class, doc = "this object"),
          @Param(name = "label", type = String.class,
              doc = "The label to use for restoring the author",
              defaultValue = "'ORIGINAL_AUTHOR'"),
      }, objectType = MetadataModule.class, useLocation = true)
  static final BuiltinFunction RESTORE_ORIGINAL_AUTHOR = new BuiltinFunction("restore_author") {
    public Transformation invoke(MetadataModule self, String label, Location location)
        throws EvalException {
      return new RestoreOriginalAuthor(label);
    }
  };

  @SuppressWarnings("unused")
  @SkylarkSignature(name = "add_header", returnType = Transformation.class,
      doc = "Adds a header line to the commit message. Any variable present in the message in the"
          + " form of ${LABEL_NAME} will be replaced by the corresponding label in the message."
          + " Note that this requires that the label is already in the message or in any of the"
          + " changes being imported. The label in the message takes priority over the ones in"
          + " the list of original messages of changes imported.\n",
      parameters = {
          @Param(name = "self", type = MetadataModule.class, doc = "this object"),
          @Param(name = "text", type = String.class,
              doc = "The header text to include in the message. For example "
                  + "'[Import of foo ${LABEL}]'. This would construct a message resolving ${LABEL}"
                  + " to the corresponding label."),
          @Param(name = "ignore_if_label_not_found", type = Boolean.class,
              doc = "If a label used in the template is not found, ignore the error and"
                  + " don't add the header. By default it will stop the migration and fail.",
              defaultValue = "False"),
      }, objectType = MetadataModule.class)
  static final BuiltinFunction ADD_HEADER = new BuiltinFunction("add_header") {
    public Transformation invoke(MetadataModule self, String header, Boolean ignoreIfLabelNotFound)
        throws EvalException {
      return new AddHeader(header, ignoreIfLabelNotFound);
    }
  };

  @SuppressWarnings("unused")
  @SkylarkSignature(name = "scrubber", returnType = Transformation.class,
      doc = "Removes part of the change message using a regex",
      parameters = {
          @Param(name = "self", type = MetadataModule.class, doc = "this object"),
          @Param(name = "regex", type = String.class,
              doc = "Any text matching the regex will be removed. Note that the regex is"
                  + " runs in multiline mode."),
          @Param(name = "replacement", type = String.class,
              doc = "Text replacement for the matching substrings. References to regex group"
                  + " numbers can be used in the form of $1, $2, etc.",
              defaultValue = "''"),
      }, objectType = MetadataModule.class, useLocation = true)
  static final BuiltinFunction SCRUB = new BuiltinFunction("scrubber") {
    public Transformation invoke(MetadataModule self, String regex, String replacement,
        Location location)
        throws EvalException {
      Pattern pattern;
      try {
        pattern = Pattern.compile(regex, Pattern.MULTILINE);
      } catch (PatternSyntaxException e) {
        throw new EvalException(location, "Invalid regex expression: " + e.getMessage());
      }
      return new Scrubber(pattern, replacement);
    }
  };
}