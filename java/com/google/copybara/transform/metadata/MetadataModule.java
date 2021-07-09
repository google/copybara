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

import static com.google.copybara.config.SkylarkUtil.check;
import static com.google.copybara.config.SkylarkUtil.convertFromNoneable;

import com.google.common.collect.ImmutableList;
import com.google.copybara.LabelFinder;
import com.google.copybara.Transformation;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.doc.annotations.DocDefault;
import com.google.copybara.doc.annotations.Example;
import com.google.re2j.Pattern;
import com.google.re2j.PatternSyntaxException;
import java.util.Map;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkValue;

/**
 * Metadata module for manipulating metadata of the changes. This is intended to be used by the
 * users for example as:
 *
 * <pre>
 *    metadata_transformations = [
 *      metadata.squash_notes(
 *           prefix = 'Import of Foo project:\n',
 *      ),
 *    ]
 *  </pre>
 */
@StarlarkBuiltin(name = "metadata", doc = "Core transformations for the change metadata")
public class MetadataModule implements StarlarkValue {

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "squash_notes",
      doc =
          "Generate a message that includes a constant prefix text and a list of changes"
              + " included in the squash change.",
      parameters = {
        @Param(
            name = "prefix",
            named = true,
            doc = "A prefix to be printed before the list of commits.",
            defaultValue = "'Copybara import of the project:\\n\\n'"),
        @Param(
            name = "max",
            named = true,
            doc =
                "Max number of commits to include in the message. For the rest a comment"
                    + " like (and x more) will be included. By default 100 commits are"
                    + " included.",
            defaultValue = "100"),
        @Param(
            name = "compact",
            named = true,
            doc = "If compact is set, each change will be shown in just one line",
            defaultValue = "True"),
        @Param(
            name = "show_ref",
            named = true,
            doc = "If each change reference should be present in the notes",
            defaultValue = "True"),
        @Param(
            name = "show_author",
            named = true,
            doc = "If each change author should be present in the notes",
            defaultValue = "True"),
        @Param(
            name = "show_description",
            named = true,
            doc = "If each change description should be present in the notes",
            defaultValue = "True"),
        @Param(
            name = "oldest_first",
            named = true,
            doc =
                "If set to true, the list shows the oldest changes first. Otherwise"
                    + " it shows the changes in descending order.",
            defaultValue = "False"),
        @Param(
            name = "use_merge",
            named = true,
            doc = "If true then merge changes are included in the squash notes",
            defaultValue = "True",
            positional = false),
      },
      useStarlarkThread = true)
  @Example(
      title = "Simple usage",
      before =
          "'Squash notes' default is to print one line per change with information about"
              + " the author",
      code = "metadata.squash_notes(\"Changes for Project Foo:\\n\")",
      after =
          "This transform will generate changes like:\n\n"
              + "```\n"
              + "Changes for Project Foo:\n\n"
              + "  - 1234abcde second commit description by Foo Bar <foo@bar.com>\n"
              + "  - a4321bcde first commit description by Foo Bar <foo@bar.com>\n"
              + "```\n")
  @Example(
      title = "Removing authors and reversing the order",
      before = "",
      code =
          "metadata.squash_notes(\"Changes for Project Foo:\\n\",\n"
              + "    oldest_first = True,\n"
              + "    show_author = False,\n"
              + ")",
      after =
          "This transform will generate changes like:\n\n"
              + "```\n"
              + "Changes for Project Foo:\n\n"
              + "  - a4321bcde first commit description\n"
              + "  - 1234abcde second commit description\n"
              + "```\n")
  @Example(
      title = "Removing description",
      before = "",
      code =
          "metadata.squash_notes(\"Changes for Project Foo:\\n\",\n"
              + "    show_description = False,\n"
              + ")",
      after =
          "This transform will generate changes like:\n\n"
              + "```\n"
              + "Changes for Project Foo:\n\n"
              + "  - a4321bcde by Foo Bar <foo@bar.com>\n"
              + "  - 1234abcde by Foo Bar <foo@bar.com>\n"
              + "```\n")
  @Example(
      title = "Showing the full message",
      before = "",
      code =
          "metadata.squash_notes(\n"
              + "  prefix = 'Changes for Project Foo:',\n"
              + "  compact = False\n"
              + ")",
      after =
          "This transform will generate changes like:\n\n"
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
              + "Extended text\n"
              + "```\n")
  public Transformation squashNotes(
      String prefix,
      StarlarkInt max,
      Boolean compact,
      Boolean showRef,
      Boolean showAuthor,
      Boolean showDescription,
      Boolean oldestFirst,
      Boolean useMerge,
      StarlarkThread thread)
      throws EvalException {
    return new MetadataSquashNotes(
        SkylarkUtil.checkNotEmpty(prefix, "prefix"),
        max.toInt("max"),
        compact,
        showRef,
        showAuthor,
        showDescription,
        oldestFirst,
        useMerge,
        thread.getCallerLocation());
    }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "save_author",
      doc =
          "For a given change, store a copy of the author as a label with the name"
              + " ORIGINAL_AUTHOR.",
      parameters = {
        @Param(
            name = "label",
            named = true,
            doc = "The label to use for storing the author",
            defaultValue = "'ORIGINAL_AUTHOR'"),
      },
      useStarlarkThread = true)
  public Transformation saveAuthor(String label, StarlarkThread thread) {
    return new SaveOriginalAuthor(label, thread.getCallerLocation());
  }

  static final String MAP_AUTHOR_EXAMPLE_SIMPLE = ""
      + "metadata.map_author({\n"
      + "    'john' : 'Some Person <some@example.com>',\n"
      + "    'madeupexample@google.com' : 'Other Person <someone@example.com>',\n"
      + "    'John Example <john.example@example.com>' : 'Another Person <some@email.com>',\n"
      + "})";

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "map_author",
      doc =
          "Map the author name and mail to another author. The mapping can be done by both name"
              + " and mail or only using any of the two.",
      parameters = {
        @Param(
            name = "authors",
            named = true,
            doc =
                "The author mapping. Keys can be in the form of 'Your Name', 'some@mail' or 'Your"
                    + " Name &lt;some@mail&gt;'. The mapping applies heuristics to know which field"
                    + " to use in the mapping. The value has to be always in the form of 'Your Name"
                    + " &lt;some@mail&gt;'"),
        @Param(
            name = "reversible",
            named = true,
            doc =
                "If the transform is automatically reversible. Workflows using the reverse of"
                    + " this transform will be able to automatically map values to keys.",
            defaultValue = "False"),
        @Param(
            name = "noop_reverse",
            named = true,
            doc =
                "If true, the reversal of the transformation doesn't do anything. This is"
                    + " useful to avoid having to write "
                    + "`core.transformation(metadata.map_author(...), reversal = [])`.",
            defaultValue = "False"),
        @Param(
            name = "fail_if_not_found",
            named = true,
            doc =
                "Fail if a mapping cannot be found. Helps discovering early authors that should"
                    + " be in the map",
            defaultValue = "False"),
        @Param(
            name = "reverse_fail_if_not_found",
            named = true,
            doc =
                "Same as fail_if_not_found but when the transform is used in a inverse"
                    + " workflow.",
            defaultValue = "False"),
        @Param(
            name = "map_all_changes",
            named = true,
            doc =
                "If all changes being migrated should be mapped. Useful for getting a mapped"
                    + " metadata.squash_notes. By default we only map the current author.",
            defaultValue = "False")
      },
      useStarlarkThread = true)
  @Example(
      title = "Map some names, emails and complete authors",
      before = "Here we show how to map authors using different options:",
      code = MAP_AUTHOR_EXAMPLE_SIMPLE)
  public Transformation mapAuthor(
      Dict<?, ?> authors, // <String, String>
      Boolean reversible,
      Boolean noopReverse,
      Boolean failIfNotFound,
      Boolean reverseFailIfNotFound,
      Boolean mapAll,
      StarlarkThread thread)
      throws EvalException {
    check(
        reversible || !reverseFailIfNotFound,
        "'reverse_fail_if_not_found' can only be true if 'reversible' is true");

    check(
        !noopReverse || !reverseFailIfNotFound,
        "'reverse_fail_if_not_found' can only be true if 'noop_reverse' is not set");

    return MapAuthor.create(
        thread.getCallerLocation(),
        SkylarkUtil.convertStringMap(authors, "authors"),
        reversible,
        noopReverse,
        failIfNotFound,
        reverseFailIfNotFound,
        mapAll);
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "use_last_change",
      doc =
          "Use metadata (message or/and author) from the last change being migrated."
              + " Useful when using 'SQUASH' mode but user only cares about the last change.",
      parameters = {
        @Param(
            name = "author",
            named = true,
            doc =
                "Replace author with the last change author (Could still be the default"
                    + " author if not on the allowlist or using `authoring.overwrite`.)",
            defaultValue = "True",
            positional = false),
        @Param(
            name = "message",
            named = true,
            doc = "Replace message with last change message.",
            defaultValue = "True",
            positional = false),
        @Param(
            name = "default_message",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            doc = "Replace message with last change message.",
            defaultValue = "None",
            positional = false),
        @Param(
            name = "use_merge",
            named = true,
            doc =
                "If true then merge changes are taken into account for looking for the last"
                    + " change.",
            defaultValue = "True",
            positional = false),
      },
      useStarlarkThread = true)
  public Transformation useLastChange(
      Boolean useAuthor, Boolean useMsg, Object defaultMsg, Boolean useMerge, StarlarkThread thread)
      throws EvalException {
    check(useAuthor || useMsg, "author or message should" + " be enabled");
      String defaultMessage = convertFromNoneable(defaultMsg, null);

    check(defaultMessage == null || useMsg, "default_message can only be used if message = True ");
    return new UseLastChange(
        useAuthor, useMsg, defaultMessage, useMerge, thread.getCallerLocation());
    }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "expose_label",
      doc =
          "Certain labels are present in the internal metadata but are not exposed in the message"
              + " by default. This transformations find a label in the internal metadata and"
              + " exposes it in the message. If the label is already present in the message it"
              + " will update it to use the new name and separator.",
      parameters = {
        @Param(name = "name", doc = "The label to search", named = true),
        @Param(
            name = "new_name",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            doc = "The name to use in the message",
            named = true,
            defaultValue = "None"),
        @Param(
            name = "separator",
            named = true,
            doc = "The separator to use when" + " adding the label to the message",
            defaultValue = "\"=\""),
        @Param(
            name = "ignore_label_not_found",
            named = true,
            doc = "If a label is not found, ignore the error and continue.",
            defaultValue = "True"),
        @Param(
            name = "all",
            named = true,
            doc =
                "By default Copybara tries to find the most relevant instance of the label."
                    + " First looking into the message and then looking into the changes in order."
                    + " If this field is true it exposes all the matches instead.",
            defaultValue = "False"),
      },
      useStarlarkThread = true)
  @Example(
      title = "Simple usage",
      before = "Expose a hidden label called 'REVIEW_URL':",
      code = "metadata.expose_label('REVIEW_URL')",
      after = "This would add it as `REVIEW_URL=the_value`.")
  @Example(
      title = "New label name",
      before = "Expose a hidden label called 'REVIEW_URL' as" + " GIT_REVIEW_URL:",
      code = "metadata.expose_label('REVIEW_URL', 'GIT_REVIEW_URL')",
      after = "This would add it as `GIT_REVIEW_URL=the_value`.")
  @Example(
      title = "Custom separator",
      before = "Expose the label with a custom separator",
      code = "metadata.expose_label('REVIEW_URL', separator = ': ')",
      after = "This would add it as `REVIEW_URL: the_value`.")
  @Example(
      title = "Expose multiple labels",
      before = "Expose all instances of a label in all the changes (SQUASH for example)",
      code = "metadata.expose_label('REVIEW_URL', all = True)",
      after = "This would add 0 or more `REVIEW_URL: the_value` labels to the message.")
  @DocDefault(field = "new_name", value = "label")
  public Transformation exposeLabel(
      String label,
      Object newName,
      String separator,
      Boolean ignoreIfLabelNotFound,
      Boolean all,
      StarlarkThread thread)
      throws EvalException {
    check(
        LabelFinder.VALID_LABEL.matcher(label).matches(), "'name': Invalid label name'%s'", label);

      String newLabelName = convertFromNoneable(newName, label);

    check(
        LabelFinder.VALID_LABEL.matcher(newLabelName).matches(),
        "'new_name': Invalid label name '%s'",
        newLabelName);

    return new ExposeLabelInMessage(
        label, newLabelName, separator, ignoreIfLabelNotFound, all, thread.getCallerLocation());
    }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "remove_label",
      doc = "Remove a label from the message",
      parameters = {
        @Param(name = "name", doc = "The label name", named = true),
      },
      useStarlarkThread = true)
  @Example(
      title = "Remove a label",
      before = "Remove Change-Id label from the message:",
      code = "metadata.remove_label('Change-Id')")
  public Transformation removeLabel(String label, StarlarkThread thread) throws EvalException {
    check(
        LabelFinder.VALID_LABEL.matcher(label).matches(), "'name': Invalid label name'%s'", label);

    return new RemoveLabelInMessage(label, thread.getCallerLocation());
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "restore_author",
      doc =
          "For a given change, restore the author present in the ORIGINAL_AUTHOR label as the"
              + " author of the change.",
      parameters = {
        @Param(
            name = "label",
            named = true,
            doc = "The label to use for restoring the author",
            defaultValue = "'ORIGINAL_AUTHOR'"),
        @Param(
            name = "search_all_changes",
            named = true,
            doc =
                "By default Copybara only looks in the last current change for the author label."
                    + " This allows to do the search in all current changes (Only makes sense for"
                    + " SQUASH/CHANGE_REQUEST).",
            defaultValue = "False"),
      },
      useStarlarkThread = true)
  public Transformation restoreAuthor(
      String label, Boolean searchAllChanges, StarlarkThread thread) {
    return new RestoreOriginalAuthor(label, searchAllChanges, thread.getCallerLocation());
    }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "add_header",
      doc =
          "Adds a header line to the commit message. Any variable present in the message in the"
              + " form of ${LABEL_NAME} will be replaced by the corresponding label in the message."
              + " Note that this requires that the label is already in the message or in any of the"
              + " changes being imported. The label in the message takes priority over the ones in"
              + " the list of original messages of changes imported.\n",
      parameters = {
        @Param(
            name = "text",
            named = true,
            doc =
                "The header text to include in the message. For example '[Import of foo"
                    + " ${LABEL}]'. This would construct a message resolving ${LABEL} to the"
                    + " corresponding label."),
        @Param(
            name = "ignore_label_not_found",
            named = true,
            doc =
                "If a label used in the template is not found, ignore the error and"
                    + " don't add the header. By default it will stop the migration and fail.",
            defaultValue = "False"),
        @Param(
            name = "new_line",
            named = true,
            doc =
                "If a new line should be added between the header and the original message."
                    + " This allows to create messages like `HEADER: ORIGINAL_MESSAGE`",
            defaultValue = "True"),
      },
      useStarlarkThread = true)
  @Example(
      title = "Add a header always",
      before = "Adds a header to any message",
      code = "metadata.add_header(\"COPYBARA CHANGE\")",
      after =
          "Messages like:\n\n"
              + "```\n"
              + "A change\n\n"
              + "Example description for\n"
              + "documentation\n"
              + "```\n\n"
              + "Will be transformed into:\n\n"
              + "```\n"
              + "COPYBARA CHANGE\n"
              + "A change\n\n"
              + "Example description for\n"
              + "documentation\n"
              + "```\n\n")
  @Example(
      title = "Add a header that uses a label",
      before =
          "Adds a header to messages that contain a label. Otherwise it skips the message"
              + " manipulation.",
      code =
          "metadata.add_header(\"COPYBARA CHANGE FOR"
              + " https://github.com/myproject/foo/pull/${GITHUB_PR_NUMBER}\",\n"
              + "    ignore_label_not_found = True,\n"
              + ")",
      after =
          "A change message, imported using git.github_pr_origin, like:\n\n"
              + "```\n"
              + "A change\n\n"
              + "Example description for\n"
              + "documentation\n"
              + "```\n\n"
              + "Will be transformed into:\n\n"
              + "```\n"
              + "COPYBARA CHANGE FOR https://github.com/myproject/foo/pull/1234\n"
              + "Example description for\n"
              + "documentation\n"
              + "```\n\n"
              + "Assuming the PR number is 1234. But any change without that label will not be"
              + " transformed.")
  @Example(
      title = "Add a header without new line",
      before = "Adds a header without adding a new line before the original message:",
      code = "metadata.add_header(\"COPYBARA CHANGE: \", new_line = False)",
      after =
          "Messages like:\n\n"
              + "```\n"
              + "A change\n\n"
              + "Example description for\n"
              + "documentation\n"
              + "```\n\n"
              + "Will be transformed into:\n\n"
              + "```\n"
              + "COPYBARA CHANGE: "
              + "A change\n\n"
              + "Example description for\n"
              + "documentation\n"
              + "```\n\n")
  public Transformation addHeader(
      String header, Boolean ignoreIfLabelNotFound, Boolean newLine, StarlarkThread thread) {
    return new TemplateMessage(
        header,
        ignoreIfLabelNotFound,
        newLine,
        /*replaceMessage=*/ false,
        thread.getCallerLocation());
    }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "replace_message",
      doc =
          "Replace the change message with a template text. Any variable present in the message in"
              + " the form of ${LABEL_NAME} will be replaced by the corresponding label in the"
              + " message. Note that this requires that the label is already in the message or in"
              + " any of the changes being imported. The label in the message takes priority over"
              + " the ones in the list of original messages of changes imported.\n",
      parameters = {
        @Param(
            name = "text",
            named = true,
            doc =
                "The template text to use for the message. For example '[Import of foo ${LABEL}]'."
                    + " This would construct a message resolving ${LABEL} to the corresponding"
                    + " label."),
        @Param(
            name = "ignore_label_not_found",
            named = true,
            doc =
                "If a label used in the template is not found, ignore the error and"
                    + " don't add the header. By default it will stop the migration and fail.",
            defaultValue = "False"),
      },
      useStarlarkThread = true)
  @Example(
      title = "Replace the message",
      before = "Replace the original message with a text:",
      code =
          "metadata.replace_message(\"COPYBARA CHANGE: Import of ${GITHUB_PR_NUMBER}\\n"
              + "\\n"
              + "${GITHUB_PR_BODY}\\n\")",
      after =
          "Will transform the message to:\n\n"
              + "```\n"
              + "COPYBARA CHANGE: Import of 12345"
              + "\n"
              + "Body from Github Pull Request"
              + "\n"
              + "```\n\n")
  public Transformation replaceMessage(
      String template, Boolean ignoreIfLabelNotFound, StarlarkThread thread) {
    return new TemplateMessage(
        template,
        ignoreIfLabelNotFound,
        /*newLine=*/ false,
        /*replaceMessage=*/ true,
        thread.getCallerLocation());
    }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "scrubber",
      doc = "Removes part of the change message using a regex",
      parameters = {
        @Param(
            name = "regex",
            named = true,
            doc =
                "Any text matching the regex will be removed. Note that the regex is"
                    + " runs in multiline mode."),
        @Param(
            name = "msg_if_no_match",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            doc = "If set, Copybara will use this text when the scrubbing regex doesn't match.",
            defaultValue = "None"),
        @Param(
            name = "fail_if_no_match",
            named = true,
            doc =
                "If set, msg_if_no_match must be None and then fail if the scrubbing "
                    + "regex doesn't match. ",
            defaultValue = "False"),
        @Param(
            name = "replacement",
            named = true,
            doc =
                "Text replacement for the matching substrings. References to regex group"
                    + " numbers can be used in the form of $1, $2, etc.",
            defaultValue = "''"),
      },
      useStarlarkThread = true)
  @Example(
      title = "Remove from a keyword to the end of the message",
      before =
          "When change messages are in the following format:\n\n"
              + "```\n"
              + "Public change description\n\n"
              + "This is a public description for a commit\n\n"
              + "CONFIDENTIAL:\n"
              + "This fixes internal project foo-bar\n"
              + "```\n\n"
              + "Using the following transformation:",
      code = "metadata.scrubber('(^|\\n)CONFIDENTIAL:(.|\\n)*')",
      after =
          "Will remove the confidential part, leaving the message as:\n\n"
              + "```\n"
              + "Public change description\n\n"
              + "This is a public description for a commit\n"
              + "```\n\n")
  @Example(
      title = "Keep only message enclosed in tags",
      before =
          "The previous example is prone to leak confidential information since a developer could"
              + " easily forget to include the CONFIDENTIAL label. A different approach for this"
              + " is to scrub everything by default except what is explicitly allowed. For"
              + " example, the following scrubber would remove anything not enclosed in"
              + " &lt;public&gt;&lt;/public&gt; tags:\n",
      code =
          "metadata.scrubber('^(?:\\n|.)*<public>((?:\\n|.)*)</public>(?:\\n|.)*$', "
              + "replacement = '$1')",
      after =
          "So a message like:\n\n"
              + "```\n"
              + "this\nis\nvery confidential<public>but this is public\nvery public\n</public>"
              + "\nand this is a secret too\n"
              + "```\n\n"
              + "would be transformed into:\n\n"
              + "```\n"
              + "but this is public\nvery public\n"
              + "```\n\n")
  @Example(
      title = "Use default msg when the scrubbing regex doesn't match",
      before = "Assign msg_if_no_match a default msg. For example:\n",
      code =
          "metadata.scrubber('^(?:\\n|.)*<public>((?:\\n|.)*)</public>(?:\\n|.)*$', "
              + "msg_if_no_match = 'Internal Change.', replacement = '$1')",
      after =
          "So a message like:\n\n"
              + "```\n"
              + "this\nis\nvery confidential\nThis is not public msg.\n"
              + "\nand this is a secret too\n"
              + "```\n\n"
              + "would be transformed into:\n\n"
              + "```\n"
              + "Internal Change.\n"
              + "```\n\n")
  @Example(
      title = "Fail if the scrubbing regex doesn't match",
      before = "Set fail_if_no_match to true",
      code =
          "metadata.scrubber('^(?:\\n|.)*<public>((?:\\n|.)*)</public>(?:\\n|.)*$', "
              + "fail_if_no_match = True, replacement = '$1')",
      after =
          "So a message like:\n\n"
              + "```\n"
              + "this\n"
              + "is\n"
              + "very confidential\n"
              + "but this is not public\n"
              + "\n"
              + "and this is a secret too\n"
              + "\n"
              + "```\n\n"
              + "This would fail. Error msg:\n\n"
              + "```\n"
              + "Scrubber regex: \'^(?:\\n"
              + "|.)*<public>((?:\\n"
              + "|.)*)</public>(?:\\n"
              + "|.)*$\' didn't match for description: this\n"
              + "is\n"
              + "very confidential\n"
              + "but this is not public\n"
              + "\n"
              + "and this is a secret too\n"
              + "```\n\n")
  public Transformation scrubber(
      String regex,
      Object msgIfNoMatchObj,
      Boolean failIfNoMatch,
      String replacement,
      StarlarkThread thread)
      throws EvalException {
      Pattern pattern;
      try {
        pattern = Pattern.compile(regex, Pattern.MULTILINE);
      } catch (PatternSyntaxException e) {
      throw Starlark.errorf("Invalid regex expression: %s", e.getMessage());
      }
      String msgIfNoMatch = convertFromNoneable(msgIfNoMatchObj, null);
    check(
        !failIfNoMatch || msgIfNoMatch == null,
        "If fail_if_no_match is true, msg_if_no_match should be None.");
    return new Scrubber(
        pattern, msgIfNoMatch, failIfNoMatch, replacement, thread.getCallerLocation());
    }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "verify_match",
      doc =
          "Verifies that a RegEx matches (or not matches) the change message. Does not"
              + " transform anything, but will stop the workflow if it fails.",
      parameters = {
        @Param(
            name = "regex",
            named = true,
            doc =
                "The regex pattern to verify. The re2j pattern will be applied in multiline"
                    + " mode, i.e. '^' refers to the beginning of a file and '$' to its end."),
        @Param(
            name = "verify_no_match",
            named = true,
            doc = "If true, the transformation will verify that the RegEx does not match.",
            defaultValue = "False"),
      },
      useStarlarkThread = true)
  @Example(
      title = "Check that a text is present in the change description",
      before =
          "Check that the change message contains a text enclosed in"
              + " &lt;public&gt;&lt;/public&gt;:",
      code = "metadata.verify_match(\"<public>(.|\\n)*</public>\")")
  public Transformation verifyMatch(String regex, Boolean verifyNoMatch, StarlarkThread thread)
      throws EvalException {
    Pattern pattern;
    try {
      pattern = Pattern.compile(regex, Pattern.MULTILINE);
    } catch (PatternSyntaxException e) {
      throw Starlark.errorf("Invalid regex expression: %s", e.getMessage());
    }
    return new MetadataVerifyMatch(pattern, verifyNoMatch, thread.getCallerLocation());
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "map_references",
      doc =
          "Allows updating links to references in commit messages to match the destination's "
              + "format. Note that this will only consider the 5000 latest commits.",
      parameters = {
        @Param(
            name = "before",
            named = true,
            doc =
                "Template for origin references in the change message. Use a '${reference}'"
                    + " token to capture the actual references. E.g. if the origin uses links"
                    + " like 'http://changes?1234', the template would be "
                    + "'http://changes?${reference}', with reference_regex = '[0-9]+'"),
        @Param(
            name = "after",
            named = true,
            doc =
                "Format for destination references in the change message. Use a '${reference}'"
                    + " token to represent the destination reference.  E.g. if the destination uses"
                    + " links like 'http://changes?1234', the template would be "
                    + "'http://changes?${reference}', with reference_regex = '[0-9]+'"),
        @Param(
            name = "regex_groups",
            defaultValue = "{}",
            named = true,
            doc =
                "Regexes for the ${reference} token's content. Requires one 'before_ref' entry"
                    + " matching the ${reference} token's content on the before side. Optionally"
                    + " accepts one 'after_ref' used for validation."
                    + " Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax."),
        @Param(
            name = "additional_import_labels",
            named = true,
            allowedTypes = {
              @ParamType(type = Sequence.class, generic1 = String.class),
            },
            defaultValue = "[]",
            doc =
                "Meant to be used when migrating from another tool: Per default, copybara will"
                    + " only recognize the labels defined in the workflow's endpoints. The tool"
                    + " will use these additional labels to find labels created by other"
                    + " invocations and tools."),
      },
      useStarlarkThread = true)
  @Example(
      title = "Map references, origin source of truth",
      before =
          "Finds links to commits in change messages, searches destination to find the equivalent"
              + " reference in destination. Then replaces matches of 'before' with 'after',"
              + " replacing the subgroup matched with the destination reference. Assume a message"
              + " like 'Fixes bug introduced in origin/abcdef', where the origin change 'abcdef'"
              + " was migrated as '123456' to the destination.",
      code =
          "metadata.map_references(\n"
              + "    before = \"origin/${reference}\",\n"
              + "    after = \"destination/${reference}\",\n"
              + "    regex_groups = {\n"
              + "        \"before_ref\": \"[0-9a-f]+\",\n"
              + "        \"after_ref\": \"[0-9]+\",\n"
              + "    },\n"
              + ")",
      after =
          "This would be translated into 'Fixes bug introduced in destination/123456', provided"
              + " that a change with the proper label was found - the message remains unchanged "
              + "otherwise.")
  public ReferenceMigrator mapReferences(
      String originPattern,
      String destinationFormat,
      Dict<?, ?> groups, // <String, String>
      Sequence<?> labels, // <String>
      StarlarkThread thread)
      throws EvalException {
    Map<String, String> groupsMap = Dict.cast(groups, String.class, String.class, "regex_groups");
    check(
        groupsMap.containsKey("before_ref")
            && (groupsMap.size() != 2 || groupsMap.containsKey("after_ref"))
            && groupsMap.size() <= 2,
        "Invalid 'regex_groups' - Should only contain 'before_ref' and "
            + "optionally 'after_ref'. Was: %s.",
        groupsMap.keySet());
    Pattern beforePattern;
    Pattern afterPattern = null;
    try {
      beforePattern = Pattern.compile(groupsMap.get("before_ref"));
    } catch (java.util.regex.PatternSyntaxException exception) {
      throw Starlark.errorf("Invalid before_ref regex '%s'.", groupsMap.get("before_ref"));
    }
    if (groupsMap.containsKey("after_ref")) {
      try {
        afterPattern = Pattern.compile(groupsMap.get("after_ref"));
      } catch (java.util.regex.PatternSyntaxException exception) {
        throw Starlark.errorf("Invalid after_ref regex '%s'.", groupsMap.get("after_ref"));
      }
    }
    return ReferenceMigrator.create(
        originPattern,
        destinationFormat,
        beforePattern,
        afterPattern,
        ImmutableList.copyOf(SkylarkUtil.convertStringList(labels, "labels")),
        thread.getCallerLocation());
    }
}
