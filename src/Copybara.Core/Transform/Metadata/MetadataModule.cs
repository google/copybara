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

using System.Collections.Immutable;
using System.Text.RegularExpressions;
using Starlark.Annot;
using Starlark.Eval;
using Starlark.Syntax;
using StarlarkRt = Starlark.Eval.Starlark;
using Sequence = Starlark.Eval.Sequence;

namespace Copybara.Transform.Metadata;

/// <summary>
/// Metadata module for manipulating metadata of the changes. This is intended to be used by users,
/// for example:
///
/// <code>
///    metadata_transformations = [
///      metadata.squash_notes(
///           prefix = 'Import of Foo project:\n',
///      ),
///    ]
/// </code>
/// </summary>
[StarlarkBuiltin("metadata", Doc = "Core transformations for the change metadata")]
public class MetadataModule : IStarlarkValue
{
    [StarlarkMethod(
        "squash_notes",
        Doc =
            "Generate a message that includes a constant prefix text and a list of changes"
            + " included in the squash change.",
        UseStarlarkThread = true)]
    public ITransformation SquashNotes(
        [Param(
            Name = "prefix",
            Named = true,
            Doc = "A prefix to be printed before the list of commits.",
            DefaultValue = "'Copybara import of the project:\\n\\n'")]
        string prefix,
        [Param(
            Name = "max",
            Named = true,
            Doc = "Max number of commits to include in the message. For the rest a comment like (and"
                + " x more) will be included. By default 100 commits are included.",
            DefaultValue = "100")]
        StarlarkInt max,
        [Param(
            Name = "compact",
            Named = true,
            Doc = "If compact is set, each change will be shown in just one line",
            DefaultValue = "True")]
        bool compact,
        [Param(
            Name = "show_ref",
            Named = true,
            Doc = "If each change reference should be present in the notes",
            DefaultValue = "True")]
        bool showRef,
        [Param(
            Name = "show_author",
            Named = true,
            Doc = "If each change author should be present in the notes",
            DefaultValue = "True")]
        bool showAuthor,
        [Param(
            Name = "show_description",
            Named = true,
            Doc = "If each change description should be present in the notes",
            DefaultValue = "True")]
        bool showDescription,
        [Param(
            Name = "oldest_first",
            Named = true,
            Doc = "If set to true, the list shows the oldest changes first. Otherwise it shows the"
                + " changes in descending order.",
            DefaultValue = "False")]
        bool oldestFirst,
        [Param(
            Name = "use_merge",
            Named = true,
            Doc = "If true then merge changes are included in the squash notes",
            DefaultValue = "True",
            Positional = false)]
        bool useMerge,
        StarlarkThread thread) =>
        new MetadataSquashNotes(
            CheckNotEmpty(prefix, "prefix"),
            max.ToInt("max"),
            compact,
            showRef,
            showAuthor,
            showDescription,
            oldestFirst,
            useMerge,
            thread.GetCallerLocation());

    [StarlarkMethod(
        "save_author",
        Doc =
            "For a given change, store a copy of the author as a label with the name"
            + " ORIGINAL_AUTHOR.",
        UseStarlarkThread = true)]
    public ITransformation SaveAuthor(
        [Param(
            Name = "label",
            Named = true,
            Doc = "The label to use for storing the author",
            DefaultValue = "'ORIGINAL_AUTHOR'")]
        string label,
        [Param(
            Name = "separator",
            Named = true,
            Doc = "The separator to use between the label and the value",
            DefaultValue = "\"=\"")]
        string separator,
        StarlarkThread thread) =>
        new SaveOriginalAuthor(label, separator, thread.GetCallerLocation());

    [StarlarkMethod(
        "map_author",
        Doc =
            "Map the author name and mail to another author. The mapping can be done by both name"
            + " and mail or only using any of the two.",
        UseStarlarkThread = true)]
    public ITransformation MapAuthor(
        [Param(
            Name = "authors",
            Named = true,
            Doc = "The author mapping. Keys can be in the form of 'Your Name', 'some@mail' or 'Your"
                + " Name <some@mail>'. The mapping applies heuristics to know which field to use in"
                + " the mapping. The value has to be always in the form of 'Your Name <some@mail>'")]
        Dict authors,
        [Param(
            Name = "reversible",
            Named = true,
            Doc = "If the transform is automatically reversible. Workflows using the reverse of this"
                + " transform will be able to automatically map values to keys.",
            DefaultValue = "False")]
        bool reversible,
        [Param(
            Name = "noop_reverse",
            Named = true,
            Doc = "If true, the reversal of the transformation doesn't do anything.",
            DefaultValue = "False")]
        bool noopReverse,
        [Param(
            Name = "fail_if_not_found",
            Named = true,
            Doc = "Fail if a mapping cannot be found. Helps discovering early authors that should be"
                + " in the map",
            DefaultValue = "False")]
        bool failIfNotFound,
        [Param(
            Name = "reverse_fail_if_not_found",
            Named = true,
            Doc = "Same as fail_if_not_found but when the transform is used in a inverse workflow.",
            DefaultValue = "False")]
        bool reverseFailIfNotFound,
        [Param(
            Name = "map_all_changes",
            Named = true,
            Doc = "If all changes being migrated should be mapped. Useful for getting a mapped"
                + " metadata.squash_notes. By default we only map the current author.",
            DefaultValue = "False")]
        bool mapAll,
        StarlarkThread thread)
    {
        Check(
            reversible || !reverseFailIfNotFound,
            "'reverse_fail_if_not_found' can only be true if 'reversible' is true");
        Check(
            !noopReverse || !reverseFailIfNotFound,
            "'reverse_fail_if_not_found' can only be true if 'noop_reverse' is not set");

        return Metadata.MapAuthor.Create(
            thread.GetCallerLocation(),
            ConvertStringMap(authors, "authors"),
            reversible,
            noopReverse,
            failIfNotFound,
            reverseFailIfNotFound,
            mapAll);
    }

    [StarlarkMethod(
        "use_last_change",
        Doc =
            "Use metadata (message or/and author) from the last change being migrated. Useful when"
            + " using 'SQUASH' mode but user only cares about the last change.",
        UseStarlarkThread = true)]
    public ITransformation UseLastChange(
        [Param(
            Name = "author",
            Named = true,
            Doc = "Replace author with the last change author",
            DefaultValue = "True",
            Positional = false)]
        bool useAuthor,
        [Param(
            Name = "message",
            Named = true,
            Doc = "Replace message with last change message.",
            DefaultValue = "True",
            Positional = false)]
        bool useMsg,
        [Param(
            Name = "default_message",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) },
            Named = true,
            Doc = "Replace message with last change message.",
            DefaultValue = "None",
            Positional = false)]
        object defaultMsg,
        [Param(
            Name = "use_merge",
            Named = true,
            Doc = "If true then merge changes are taken into account for looking for the last"
                + " change.",
            DefaultValue = "True",
            Positional = false)]
        bool useMerge,
        StarlarkThread thread)
    {
        Check(useAuthor || useMsg, "author or message should be enabled");
        string? defaultMessage = ConvertFromNoneable(defaultMsg, null);
        Check(
            defaultMessage == null || useMsg, "default_message can only be used if message = True ");
        return new UseLastChange(
            useAuthor, useMsg, defaultMessage, useMerge, thread.GetCallerLocation());
    }

    [StarlarkMethod(
        "expose_label",
        Doc =
            "Certain labels are present in the internal metadata but are not exposed in the message"
            + " by default. This transformations find a label in the internal metadata and exposes"
            + " it in the message. If the label is already present in the message it will update it"
            + " to use the new name and separator.",
        UseStarlarkThread = true)]
    public ITransformation ExposeLabel(
        [Param(Name = "name", Doc = "The label to search", Named = true)]
        string label,
        [Param(
            Name = "new_name",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) },
            Doc = "The name to use in the message",
            Named = true,
            DefaultValue = "None")]
        object newName,
        [Param(
            Name = "separator",
            Named = true,
            Doc = "The separator to use when adding the label to the message",
            DefaultValue = "\"=\"")]
        string separator,
        [Param(
            Name = "ignore_label_not_found",
            Named = true,
            Doc = "If a label is not found, ignore the error and continue.",
            DefaultValue = "True")]
        bool ignoreIfLabelNotFound,
        [Param(
            Name = "all",
            Named = true,
            Doc = "By default Copybara tries to find the most relevant instance of the label. First"
                + " looking into the message and then looking into the changes in order. If this"
                + " field is true it exposes all the matches instead.",
            DefaultValue = "False")]
        bool all,
        [Param(
            Name = "concat_separator",
            Named = true,
            Doc = "If all is set, copybara will expose multiple values in one per line. If a"
                + " separator is specified, it will concat the values instead.",
            DefaultValue = "None")]
        object joiner,
        StarlarkThread thread)
    {
        Check(
            LabelFinder.VALID_LABEL.IsMatch(label), "'name': Invalid label name'{0}'", label);
        string newLabelName = ConvertFromNoneable(newName, label)!;
        Check(
            LabelFinder.VALID_LABEL.IsMatch(newLabelName),
            "'new_name': Invalid label name '{0}'",
            newLabelName);

        string? join = ConvertFromNoneable(joiner, null);
        Check(join == null || all, "'joiner': Cannot be set unless all is True.");
        return new ExposeLabelInMessage(
            label,
            newLabelName,
            separator,
            ignoreIfLabelNotFound,
            all,
            join,
            thread.GetCallerLocation());
    }

    [StarlarkMethod(
        "remove_label",
        Doc = "Remove a label from the message",
        UseStarlarkThread = true)]
    public ITransformation RemoveLabel(
        [Param(Name = "name", Doc = "The label name", Named = true)]
        string label,
        StarlarkThread thread)
    {
        Check(
            LabelFinder.VALID_LABEL.IsMatch(label), "'name': Invalid label name'{0}'", label);
        return new RemoveLabelInMessage(label, thread.GetCallerLocation());
    }

    [StarlarkMethod(
        "restore_author",
        Doc =
            "For a given change, restore the author present in the ORIGINAL_AUTHOR label as the"
            + " author of the change.",
        UseStarlarkThread = true)]
    public ITransformation RestoreAuthor(
        [Param(
            Name = "label",
            Named = true,
            Doc = "The label to use for restoring the author",
            DefaultValue = "'ORIGINAL_AUTHOR'")]
        string label,
        [Param(
            Name = "separator",
            Named = true,
            Doc = "The separator to use between the label and the value",
            DefaultValue = "\"=\"")]
        string separator,
        [Param(
            Name = "search_all_changes",
            Named = true,
            Doc = "By default Copybara only looks in the last current change for the author label."
                + " This allows to do the search in all current changes (Only makes sense for"
                + " SQUASH/CHANGE_REQUEST).",
            DefaultValue = "False")]
        bool searchAllChanges,
        StarlarkThread thread) =>
        new RestoreOriginalAuthor(label, separator, searchAllChanges, thread.GetCallerLocation());

    [StarlarkMethod(
        "add_header",
        Doc =
            "Adds a header line to the commit message. Any variable present in the message in the"
            + " form of ${LABEL_NAME} will be replaced by the corresponding label in the message."
            + " Note that this requires that the label is already in the message or in any of the"
            + " changes being imported. The label in the message takes priority over the ones in"
            + " the list of original messages of changes imported.\n",
        UseStarlarkThread = true)]
    public ITransformation AddHeader(
        [Param(
            Name = "text",
            Named = true,
            Doc = "The header text to include in the message. For example '[Import of foo ${LABEL}]'."
                + " This would construct a message resolving ${LABEL} to the corresponding label.")]
        string header,
        [Param(
            Name = "ignore_label_not_found",
            Named = true,
            Doc = "If a label used in the template is not found, ignore the error and don't add the"
                + " header. By default it will stop the migration and fail.",
            DefaultValue = "False")]
        bool ignoreIfLabelNotFound,
        [Param(
            Name = "new_line",
            Named = true,
            Doc = "If a new line should be added between the header and the original message. This"
                + " allows to create messages like `HEADER: ORIGINAL_MESSAGE`",
            DefaultValue = "True")]
        bool newLine,
        StarlarkThread thread) =>
        new TemplateMessage(
            header,
            ignoreIfLabelNotFound,
            newLine,
            replaceMessage: false,
            thread.GetCallerLocation());

    [StarlarkMethod(
        "replace_message",
        Doc =
            "Replace the change message with a template text. Any variable present in the message in"
            + " the form of ${LABEL_NAME} will be replaced by the corresponding label in the"
            + " message. Note that this requires that the label is already in the message or in any"
            + " of the changes being imported. The label in the message takes priority over the ones"
            + " in the list of original messages of changes imported.\n",
        UseStarlarkThread = true)]
    public ITransformation ReplaceMessage(
        [Param(
            Name = "text",
            Named = true,
            Doc = "The template text to use for the message. For example '[Import of foo ${LABEL}]'."
                + " This would construct a message resolving ${LABEL} to the corresponding label.")]
        string template,
        [Param(
            Name = "ignore_label_not_found",
            Named = true,
            Doc = "If a label used in the template is not found, ignore the error and don't add the"
                + " header. By default it will stop the migration and fail.",
            DefaultValue = "False")]
        bool ignoreIfLabelNotFound,
        StarlarkThread thread) =>
        new TemplateMessage(
            template,
            ignoreIfLabelNotFound,
            newLine: false,
            replaceMessage: true,
            thread.GetCallerLocation());

    [StarlarkMethod(
        "scrubber",
        Doc = "Removes part of the change message using a regex",
        UseStarlarkThread = true)]
    public ITransformation Scrubber(
        [Param(
            Name = "regex",
            Named = true,
            Doc = "Any text matching the regex will be removed. Note that the regex is runs in"
                + " multiline mode.")]
        string regex,
        [Param(
            Name = "msg_if_no_match",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) },
            Named = true,
            Doc = "If set, Copybara will use this text when the scrubbing regex doesn't match.",
            DefaultValue = "None")]
        object msgIfNoMatchObj,
        [Param(
            Name = "fail_if_no_match",
            Named = true,
            Doc = "If set, msg_if_no_match must be None and then fail if the scrubbing regex doesn't"
                + " match. ",
            DefaultValue = "False")]
        bool failIfNoMatch,
        [Param(
            Name = "replacement",
            Named = true,
            Doc = "Text replacement for the matching substrings. References to regex group numbers"
                + " can be used in the form of $1, $2, etc.",
            DefaultValue = "''")]
        string replacement,
        StarlarkThread thread)
    {
        Regex pattern;
        try
        {
            pattern = new Regex(regex, RegexOptions.Multiline);
        }
        catch (ArgumentException e)
        {
            throw StarlarkRt.Errorf("Invalid regex expression: {0}", e.Message);
        }
        string? msgIfNoMatch = ConvertFromNoneable(msgIfNoMatchObj, null);
        Check(
            !failIfNoMatch || msgIfNoMatch == null,
            "If fail_if_no_match is true, msg_if_no_match should be None.");
        return new Scrubber(
            pattern, msgIfNoMatch, failIfNoMatch, replacement, thread.GetCallerLocation());
    }

    [StarlarkMethod(
        "verify_match",
        Doc =
            "Verifies that a RegEx matches (or not matches) the change message. Does not transform"
            + " anything, but will stop the workflow if it fails.",
        UseStarlarkThread = true)]
    public ITransformation VerifyMatch(
        [Param(
            Name = "regex",
            Named = true,
            Doc = "The regex pattern to verify. The re2j pattern will be applied in multiline mode,"
                + " i.e. '^' refers to the beginning of a file and '$' to its end.")]
        string regex,
        [Param(
            Name = "verify_no_match",
            Named = true,
            Doc = "If true, the transformation will verify that the RegEx does not match.",
            DefaultValue = "False")]
        bool verifyNoMatch,
        StarlarkThread thread)
    {
        Regex pattern;
        try
        {
            pattern = new Regex(regex, RegexOptions.Multiline);
        }
        catch (ArgumentException e)
        {
            throw StarlarkRt.Errorf("Invalid regex expression: {0}", e.Message);
        }
        return new MetadataVerifyMatch(pattern, verifyNoMatch, thread.GetCallerLocation());
    }

    [StarlarkMethod(
        "map_references",
        Doc =
            "Allows updating links to references in commit messages to match the destination's"
            + " format. Note that this will only consider the 5000 latest commits.",
        UseStarlarkThread = true)]
    public ReferenceMigrator MapReferences(
        [Param(
            Name = "before",
            Named = true,
            Doc = "Template for origin references in the change message. Use a '${reference}' token"
                + " to capture the actual references.")]
        string originPattern,
        [Param(
            Name = "after",
            Named = true,
            Doc = "Format for destination references in the change message. Use a '${reference}'"
                + " token to represent the destination reference.")]
        string destinationFormat,
        [Param(
            Name = "regex_groups",
            DefaultValue = "{}",
            Named = true,
            Doc = "Regexes for the ${reference} token's content. Requires one 'before_ref' entry"
                + " matching the ${reference} token's content on the before side. Optionally accepts"
                + " one 'after_ref' used for validation.")]
        Dict groups,
        [Param(
            Name = "additional_import_labels",
            Named = true,
            AllowedTypes = new[] { typeof(Sequence) },
            DefaultValue = "[]",
            Doc = "Meant to be used when migrating from another tool: Per default, copybara will only"
                + " recognize the labels defined in the workflow's endpoints. The tool will use"
                + " these additional labels to find labels created by other invocations and tools.")]
        object labels,
        StarlarkThread thread)
    {
        var groupsMap = ConvertStringMap(groups, "regex_groups");
        Check(
            groupsMap.ContainsKey("before_ref")
                && (groupsMap.Count != 2 || groupsMap.ContainsKey("after_ref"))
                && groupsMap.Count <= 2,
            "Invalid 'regex_groups' - Should only contain 'before_ref' and optionally 'after_ref'."
                + " Was: {0}.",
            string.Join(", ", groupsMap.Keys));
        Regex beforePattern;
        Regex? afterPattern = null;
        try
        {
            beforePattern = new Regex(groupsMap["before_ref"]);
        }
        catch (ArgumentException)
        {
            throw StarlarkRt.Errorf("Invalid before_ref regex '{0}'.", groupsMap["before_ref"]);
        }
        if (groupsMap.ContainsKey("after_ref"))
        {
            try
            {
                afterPattern = new Regex(groupsMap["after_ref"]);
            }
            catch (ArgumentException)
            {
                throw StarlarkRt.Errorf("Invalid after_ref regex '{0}'.", groupsMap["after_ref"]);
            }
        }
        return ReferenceMigrator.Create(
            originPattern,
            destinationFormat,
            beforePattern,
            afterPattern,
            ConvertStringList(labels, "labels").ToImmutableArray(),
            thread.GetCallerLocation());
    }

    // ---- Helpers (inlined from SkylarkUtil until that class is ported) ----

    private static void Check(bool condition, string format, params object?[] args)
    {
        if (!condition)
        {
            throw StarlarkRt.Errorf(format, args);
        }
    }

    private static string CheckNotEmpty(string? value, string name)
    {
        if (string.IsNullOrEmpty(value))
        {
            throw StarlarkRt.Errorf("Invalid empty field '{0}'.", name);
        }
        return value;
    }

    private static string? ConvertFromNoneable(object? value, string? defaultValue) =>
        StarlarkRt.IsNullOrNone(value) ? defaultValue : (string)value!;

    private static IReadOnlyDictionary<string, string> ConvertStringMap(Dict dict, string name)
    {
        var result = new Dictionary<string, string>();
        foreach (var entry in dict.Entries)
        {
            if (entry.Key is not string k)
            {
                throw StarlarkRt.Errorf(
                    "Expected string key for '{0}' but got: {1}", name, entry.Key);
            }
            if (entry.Value is not string v)
            {
                throw StarlarkRt.Errorf(
                    "Expected string value for '{0}' but got: {1}", name, entry.Value);
            }
            result[k] = v;
        }
        return result;
    }

    private static IReadOnlyList<string> ConvertStringList(object value, string name)
    {
        var seq = Starlark.Eval.Sequence.Cast<string>(value, name);
        return seq.ToList();
    }
}
