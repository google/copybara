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

import com.google.common.collect.ImmutableList;
import com.google.copybara.Transformation;
import com.google.copybara.config.base.SkylarkUtil;
import com.google.copybara.doc.annotations.Example;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.Type;
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
          @Param(name = "show_ref", type = Boolean.class,
              doc = "If each change reference should be present in the notes",
              defaultValue = "True"),
          @Param(name = "show_author", type = Boolean.class,
              doc = "If each change author should be present in the notes",
              defaultValue = "True"),
          @Param(name = "oldest_first", type = Boolean.class,
              doc = "If set to true, the list shows the oldest changes first. Otherwise"
                  + " it shows the changes in descending order.",
              defaultValue = "False"),
      }, objectType = MetadataModule.class, useLocation = true)
  @Example(title = "Simple usage",
      before = "'Squash notes' default is to print one line per change with information about"
          + " the author",
      code = "metadata.squash_notes(\"Changes for Project Foo:\\n\")",
      after = "This transform will generate changes like:\n\n"
          + "```\n"
          + "Changes for Project Foo:\n\n"
          + "  - 1234abcde second commit description by Foo Bar <foo@bar.com>\n"
          + "  - a4321bcde first commit description by Foo Bar <foo@bar.com>\n"
          + "```\n")
  @Example(title = "Removing authors and reversing the order",
      before = "",
      code = "metadata.squash_notes(\"Changes for Project Foo:\\n\",\n"
          + "    oldest_first = True,\n"
          + "    show_author = False,\n"
          + ")",
      after = "This transform will generate changes like:\n\n"
          + "```\n"
          + "Changes for Project Foo:\n\n"
          + "  - a4321bcde first commit description\n"
          + "  - 1234abcde second commit description\n"
          + "```\n")
  @Example(title = "Showing the full message",
      before = "",
      code = "metadata.squash_notes(\n"
          + "  prefix = 'Changes for Project Foo:',\n"
          + "  compact = False\n"
          + ")",
      after = "This transform will generate changes like:\n\n"
          + "```\n"
          + "Changes for Project Foo:\n"
          + "--\n"
          + "2 by Foo Baz <foo@baz.com>:\n"
          + "\n"
          + "second commit\n"
          + "\n"
          + "Extended text\n"
          + "--\n"
          + "1 by Foo Bar <foo@bar.com>:\n"
          + "\n"
          + "first commit\n"
          + "\n"
          + "Extended text\n" + "```\n")
  static final BuiltinFunction SQUASH_NOTES = new BuiltinFunction("squash_notes") {
    public Transformation invoke(MetadataModule self, String prefix, Integer max,
        Boolean compact, Boolean showRef, Boolean showAuthor, Boolean oldestFirst,
        Location location) throws EvalException {
      return new MetadataSquashNotes(SkylarkUtil.checkNotEmpty(prefix, "prefix", location),
          max, compact, showRef, showAuthor, oldestFirst);
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
  @Example(title = "Remove from a keyword to the end of the message",
      before = "When change messages are in the following format:\n\n"
          + "```\n"
          + "Public change description\n\n"
          + "This is a public description for a commit\n\n"
          + "CONFIDENTIAL:\n"
          + "This fixes internal project foo-bar\n"
          + "```\n\n"
          + "Using the following transformation:",
      code = "metadata.scrubber('(^|\\n)CONFIDENTIAL:(.|\\n)*')",
      after = "Will remove the confidential part, leaving the message as:\n"
          + "```\n"
          + "Public change description\n\n"
          + "This is a public description for a commit\n\n"
          + "```\n\n")
  @Example(title = "Keep only message enclosed in tags",
      before = "The previous example is prone to leak confidential information since a developer"
          + " could easily forget to include the CONFIDENTIAL label. A different approach for this"
          + " is to scrub everything by default except what is explicitly allowed. For example,"
          + " the following scrubber would remove anything not enclosed in <public></public>"
          + " tags:\n",
      code = "metadata.scrubber('^(?:\\n|.)*<public>((?:\\n|.)*)</public>(?:\\n|.)*$', "
          + "replacement = '$1')",
      after = "So a message like:\n\n"
          + "```\n"
          + "this\nis\nvery confidential<public>but this is public\nvery public\n</public>"
          + "\nand this is a secret too\n"
          + "```\n\n"
          + "would be transformed into:\n\n"
          + "```\n"
          + "but this is public\nvery public\n"
          + "```\n\n")
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


  @SkylarkSignature(name = "map_references", returnType = ReferenceMigrator.class,
      doc = "Allows updating links to references in commit messages to match the destination's "
          + "format. Note that this will only consider the 5000 latest commits.",
      parameters = {
          @Param(name = "self", type = MetadataModule.class, doc = "this object"),
          @Param(name = "before", type = String.class,
              doc = "Template for origin references in the change message. Use a '${reference}'"
                  + " token to capture the actual references. E.g. if the origin uses links"
                  + "like 'http://changes?1234', the template would be "
                  + "'http://internalReviews.com/${reference}', with reference_regex = '[0-9]+'"),
          @Param(name = "after", type = String.class,
              doc = "Format for references in the destination, use the token '${reference}' "
                  + "to represent the destination reference. E.g. 'http://changes(${reference})'."),
          @Param(name = "regex_groups", type = SkylarkDict.class, defaultValue = "{}",
              doc = "Regexes for the ${reference} token's content. Requires one 'before_ref' entry"
                  + " matching the ${reference} token's content on the before side. Optionally"
                  + " accepts one 'after_ref' used for validation."),
          @Param(name = "additional_import_labels",
              type = SkylarkList.class, generic1 = String.class, defaultValue = "[]",
              doc = "Meant to be used when migrating from another tool: Per default, copybara will "
                  + "only recognize the labels defined in the workflow's endpoints. The tool will "
                  + "use these additional labels to find labels created by other invocations and "
                  + "tools."),
      },
      objectType = MetadataModule.class, useLocation = true)
  @Example(title = "Map references, origin source of truth",
      before = "Finds links to commits in change messages, searches destination to find the "
          + "equivalent reference in destination. Then replaces matches of 'before' with 'after', "
          + "replacing the subgroup matched with the destination reference. Assume a message like"
          + " 'Fixes bug introduced in origin/abcdef', where the origin change 'abcdef' was "
          + "migrated as '123456' to the destination.",
      code = "metadata.map_references(\n"
          + "    before = \"origin/${reference}\",\n"
          + "    after = \"destination/${reference}\",\n"
          + "    regex_groups = {\n"
          + "        \"before_ref\": \"[0-9a-f]+\",\n"
          + "        \"after_ref\": \"[0-9]+\",\n"
          + "    },\n"
          + "),",
      after = "This would be translated into 'Fixes bug introduced in destination/123456', provided"
          + " that a change with the proper label was found - the message remains unchanged "
          + "otherwise.")
  public static final BuiltinFunction MAP_REFERENCES = new BuiltinFunction("map_references") {
    public ReferenceMigrator invoke(MetadataModule self, String originPattern,
        String destinationFormat, SkylarkDict<String, String> groups, SkylarkList<String> labels,
        Location location)
        throws EvalException {
      if (!groups.containsKey("before_ref")
          || (groups.size() == 2 && !groups.containsKey("after_ref"))
          || groups.size() > 2) {
        throw new EvalException(location,
            String.format("Invalid 'regex_groups' - Should only contain 'before_ref' and "
                + "optionally 'after_ref'. Was: %s.", groups.keySet()));
      }
      Pattern beforePattern;
      Pattern afterPattern = null;
      try {
        beforePattern = Pattern.compile(groups.get("before_ref"));
      } catch (java.util.regex.PatternSyntaxException exception) {
        throw new EvalException(location,
            String.format("Invalid before_ref regex '%s'.", groups.get("before_ref")));
      }
      if (groups.containsKey("after_ref")) {
        try {
          afterPattern = Pattern.compile(groups.get("after_ref"));
        } catch (java.util.regex.PatternSyntaxException exception) {
          throw new EvalException(location,
              String.format("Invalid after_ref regex '%s'.", groups.get("after_ref")));
        }
      }
      return
          ReferenceMigrator.create(
              originPattern,
              destinationFormat,
              beforePattern,
              afterPattern,
              ImmutableList.<String>copyOf(Type.STRING_LIST.convert(labels, "labels")),
              location);
    }
  };
}