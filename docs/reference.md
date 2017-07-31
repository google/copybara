# Table of Contents


  - [author](#author)
  - [authoring](#authoring)
    - [authoring.overwrite](#authoring.overwrite)
    - [new_author](#new_author)
    - [authoring.pass_thru](#authoring.pass_thru)
    - [authoring.whitelisted](#authoring.whitelisted)
  - [authoring_class](#authoring_class)
  - [Console](#console)
  - [metadata](#metadata)
    - [metadata.squash_notes](#metadata.squash_notes)
    - [metadata.save_author](#metadata.save_author)
    - [metadata.map_author](#metadata.map_author)
    - [metadata.use_last_change](#metadata.use_last_change)
    - [metadata.expose_label](#metadata.expose_label)
    - [metadata.restore_author](#metadata.restore_author)
    - [metadata.add_header](#metadata.add_header)
    - [metadata.scrubber](#metadata.scrubber)
    - [metadata.verify_match](#metadata.verify_match)
    - [metadata.map_references](#metadata.map_references)
  - [core](#core)
    - [glob](#glob)
    - [core.reverse](#core.reverse)
    - [core.workflow](#core.workflow)
    - [core.move](#core.move)
    - [core.copy](#core.copy)
    - [core.remove](#core.remove)
    - [core.replace](#core.replace)
    - [core.verify_match](#core.verify_match)
    - [core.transform](#core.transform)
  - [folder](#folder)
    - [folder.destination](#folder.destination)
    - [folder.origin](#folder.origin)
  - [git](#git)
    - [git.origin](#git.origin)
    - [git.mirror](#git.mirror)
    - [git.gerrit_origin](#git.gerrit_origin)
    - [git.github_origin](#git.github_origin)
    - [git.destination](#git.destination)
    - [git.gerrit_destination](#git.gerrit_destination)
  - [patch](#patch)
    - [patch.apply](#patch.apply)


# author

Represents the author of a change


# authoring

The authors mapping between an origin and a destination

<a id="authoring.overwrite" aria-hidden="true"></a>
## authoring.overwrite

Use the default author for all the submits in the destination. Note that some destinations might choose to ignore this author and use the current user running the tool (In other words they don't allow impersonation).

`authoring_class authoring.overwrite(default)`

### Parameters:

Parameter | Description
--------- | -----------
default|`string`<br><p>The default author for commits in the destination</p>


### Example:

#### Overwrite usage example:

Create an authoring object that will overwrite any origin author with noreply@foobar.com mail.

```python
authoring.overwrite("Foo Bar <noreply@foobar.com>")
```

<a id="new_author" aria-hidden="true"></a>
## new_author

Create a new author from a string with the form 'name <foo@bar.com>'

`author new_author(author_string)`

### Parameters:

Parameter | Description
--------- | -----------
author_string|`string`<br><p>A string representation of the author with the form 'name <foo@bar.com>'</p>


### Example:

#### Create a new author:



```python
new_author('Foo Bar <foobar@myorg.com>')
```

<a id="authoring.pass_thru" aria-hidden="true"></a>
## authoring.pass_thru

Use the origin author as the author in the destination, no whitelisting.

`authoring_class authoring.pass_thru(default)`

### Parameters:

Parameter | Description
--------- | -----------
default|`string`<br><p>The default author for commits in the destination. This is used in squash mode workflows or if author cannot be determined.</p>


### Example:

#### Pass thru usage example:



```python
authoring.pass_thru(default = "Foo Bar <noreply@foobar.com>")
```

<a id="authoring.whitelisted" aria-hidden="true"></a>
## authoring.whitelisted

Create an individual or team that contributes code.

`authoring_class authoring.whitelisted(default, whitelist)`

### Parameters:

Parameter | Description
--------- | -----------
default|`string`<br><p>The default author for commits in the destination. This is used in squash mode workflows or when users are not whitelisted.</p>
whitelist|`sequence of string`<br><p>List of white listed authors in the origin. The authors must be unique</p>


### Examples:

#### Only pass thru whitelisted users:



```python
authoring.whitelisted(
    default = "Foo Bar <noreply@foobar.com>",
    whitelist = [
       "someuser@myorg.com",
       "other@myorg.com",
       "another@myorg.com",
    ],
)
```

#### Only pass thru whitelisted LDAPs/usernames:

Some repositories are not based on email but use LDAPs/usernames. This is also supported since it is up to the origin how to check whether two authors are the same.

```python
authoring.whitelisted(
    default = "Foo Bar <noreply@foobar.com>",
    whitelist = [
       "someuser",
       "other",
       "another",
    ],
)
```


# authoring_class

The authors mapping between an origin and a destination


# Console

A console that can be used in skylark transformations to print info, warning or error messages.


# metadata

Core transformations for the change metadata

<a id="metadata.squash_notes" aria-hidden="true"></a>
## metadata.squash_notes

Generate a message that includes a constant prefix text and a list of changes included in the squash change.

`transformation metadata.squash_notes(prefix='Copybara import of the project:\n\n', max=100, compact=True, show_ref=True, show_author=True, oldest_first=False)`

### Parameters:

Parameter | Description
--------- | -----------
prefix|`string`<br><p>A prefix to be printed before the list of commits.</p>
max|`integer`<br><p>Max number of commits to include in the message. For the rest a comment like (and x more) will be included. By default 100 commits are included.</p>
compact|`boolean`<br><p>If compact is set, each change will be shown in just one line</p>
show_ref|`boolean`<br><p>If each change reference should be present in the notes</p>
show_author|`boolean`<br><p>If each change author should be present in the notes</p>
oldest_first|`boolean`<br><p>If set to true, the list shows the oldest changes first. Otherwise it shows the changes in descending order.</p>


### Examples:

#### Simple usage:

'Squash notes' default is to print one line per change with information about the author

```python
metadata.squash_notes("Changes for Project Foo:\n")
```

This transform will generate changes like:

```
Changes for Project Foo:

  - 1234abcde second commit description by Foo Bar <foo@bar.com>
  - a4321bcde first commit description by Foo Bar <foo@bar.com>
```


#### Removing authors and reversing the order:



```python
metadata.squash_notes("Changes for Project Foo:\n",
    oldest_first = True,
    show_author = False,
)
```

This transform will generate changes like:

```
Changes for Project Foo:

  - a4321bcde first commit description
  - 1234abcde second commit description
```


#### Showing the full message:



```python
metadata.squash_notes(
  prefix = 'Changes for Project Foo:',
  compact = False
)
```

This transform will generate changes like:

```
Changes for Project Foo:
--
2 by Foo Baz <foo@baz.com>:

second commit

Extended text
--
1 by Foo Bar <foo@bar.com>:

first commit

Extended text
```


<a id="metadata.save_author" aria-hidden="true"></a>
## metadata.save_author

For a given change, store a copy of the author as a label with the name ORIGINAL_AUTHOR.

`transformation metadata.save_author(label='ORIGINAL_AUTHOR')`

### Parameters:

Parameter | Description
--------- | -----------
label|`string`<br><p>The label to use for storing the author</p>


<a id="metadata.map_author" aria-hidden="true"></a>
## metadata.map_author

Map the author name and mail to another author. The mapping can be done by both name and mail or only using any of the two.

`transformation metadata.map_author(authors, reversible=False, fail_if_not_found=False, reverse_fail_if_not_found=False)`

### Parameters:

Parameter | Description
--------- | -----------
authors|`dict`<br><p>The author mapping. Keys can be in the form of 'Your Name', 'some@mail' or 'Your Name <some@mail>'. The mapping applies heuristics to know which field to use in the mapping. The value has to be always in the form of 'Your Name <some@mail>'</p>
reversible|`boolean`<br><p>If the transform is automatically reversible. Workflows using the reverse of this transform will be able to automatically map values to keys.</p>
fail_if_not_found|`boolean`<br><p>Fail if a mapping cannot be found. Helps discovering early authors that should be in the map</p>
reverse_fail_if_not_found|`boolean`<br><p>Same as fail_if_not_found but when the transform is used in a inverse workflow.</p>


### Example:

#### Map some names, emails and complete authors:

Here we show how to map authors using different options:

```python
metadata.map_author({
    'john' : 'Some Person <some@example.com>',
    'madeupexample@google.com' : 'Other Person <someone@example.com>',
    'John Example <john.example@example.com>' : 'Another Person <some@email.com>',
})
```

<a id="metadata.use_last_change" aria-hidden="true"></a>
## metadata.use_last_change

Use metadata (message or/and author) from the last change being migrated. Useful when using 'SQUASH' mode but user only cares about the last change.

`transformation metadata.use_last_change(author=True, message=True, default_message=None)`

### Parameters:

Parameter | Description
--------- | -----------
author|`boolean`<br><p>Replace author with the last change author (Could still be the default author if not whitelisted or using `authoring.overwrite`.</p>
message|`boolean`<br><p>Replace message with last change message.</p>
default_message|`string`<br><p>Replace message with last change message.</p>


<a id="metadata.expose_label" aria-hidden="true"></a>
## metadata.expose_label

Certain labels are present in the internal metadata but are not exposed in the message by default. This transformations find a label in the internal metadata and exposes it in the message. If the label is already present in the message it will update it to use the new name and separator.

`transformation metadata.expose_label(name, new_name=label, separator="=", ignore_label_not_found=True)`

### Parameters:

Parameter | Description
--------- | -----------
name|`string`<br><p>The label to search</p>
new_name|`string`<br><p>The name to use in the message</p>
separator|`string`<br><p>The separator to use when adding the label to the message</p>
ignore_label_not_found|`boolean`<br><p>If a label is not found, ignore the error and continue.</p>


### Examples:

#### Simple usage:

Expose a hidden label called 'REVIEW_URL':

```python
metadata.expose_label('REVIEW_URL')
```

This would add it as `REVIEW_URL=the_value`.

#### New label name:

Expose a hidden label called 'REVIEW_URL' as GIT_REVIEW_URL:

```python
metadata.expose_label('REVIEW_URL', 'GIT_REVIEW_URL')
```

This would add it as `GIT_REVIEW_URL=the_value`.

#### Custom separator:

Expose the label with a custom separator

```python
metadata.expose_label('REVIEW_URL', separator = ': ')
```

This would add it as `REVIEW_URL: the_value`.

<a id="metadata.restore_author" aria-hidden="true"></a>
## metadata.restore_author

For a given change, restore the author present in the ORIGINAL_AUTHOR label as the author of the change.

`transformation metadata.restore_author(label='ORIGINAL_AUTHOR')`

### Parameters:

Parameter | Description
--------- | -----------
label|`string`<br><p>The label to use for restoring the author</p>


<a id="metadata.add_header" aria-hidden="true"></a>
## metadata.add_header

Adds a header line to the commit message. Any variable present in the message in the form of ${LABEL_NAME} will be replaced by the corresponding label in the message. Note that this requires that the label is already in the message or in any of the changes being imported. The label in the message takes priority over the ones in the list of original messages of changes imported.


`transformation metadata.add_header(text, ignore_label_not_found=False, new_line=True)`

### Parameters:

Parameter | Description
--------- | -----------
text|`string`<br><p>The header text to include in the message. For example '[Import of foo ${LABEL}]'. This would construct a message resolving ${LABEL} to the corresponding label.</p>
ignore_label_not_found|`boolean`<br><p>If a label used in the template is not found, ignore the error and don't add the header. By default it will stop the migration and fail.</p>
new_line|`boolean`<br><p>If a new line should be added between the header and the original message. This allows to create messages like `HEADER: ORIGINAL_MESSAGE`</p>


### Examples:

#### Add a header always:

Adds a header to any message

```python
metadata.add_header("COPYBARA CHANGE")
```

Messages like:

```
A change

Example description for
documentation
```

Will be transformed into:

```
COPYBARA CHANGE
A change

Example description for
documentation
```



#### Add a header that uses a label:

Adds a header to messages that contain a label. Otherwise it skips the message manipulation.

```python
metadata.add_header("COPYBARA CHANGE FOR ${GIT_URL}",
    ignore_label_not_found = True,
)
```

Messages like:

```
A change

Example description for
documentation

GIT_URL=http://foo.com/1234```

Will be transformed into:

```
COPYBARA CHANGE FOR http://foo.com/1234
Example description for
documentation

GIT_URL=http://foo.com/1234```

But any change without that label will not be transformed.

#### Add a header without new line:

Adds a header without adding a new line before the original message:

```python
metadata.add_header("COPYBARA CHANGE: ", new_line = False)
```

Messages like:

```
A change

Example description for
documentation
```

Will be transformed into:

```
COPYBARA CHANGE: A change

Example description for
documentation
```



<a id="metadata.scrubber" aria-hidden="true"></a>
## metadata.scrubber

Removes part of the change message using a regex

`transformation metadata.scrubber(regex, replacement='')`

### Parameters:

Parameter | Description
--------- | -----------
regex|`string`<br><p>Any text matching the regex will be removed. Note that the regex is runs in multiline mode.</p>
replacement|`string`<br><p>Text replacement for the matching substrings. References to regex group numbers can be used in the form of $1, $2, etc.</p>


### Examples:

#### Remove from a keyword to the end of the message:

When change messages are in the following format:

```
Public change description

This is a public description for a commit

CONFIDENTIAL:
This fixes internal project foo-bar
```

Using the following transformation:

```python
metadata.scrubber('(^|\n)CONFIDENTIAL:(.|\n)*')
```

Will remove the confidential part, leaving the message as:

```
Public change description

This is a public description for a commit

```



#### Keep only message enclosed in tags:

The previous example is prone to leak confidential information since a developer could easily forget to include the CONFIDENTIAL label. A different approach for this is to scrub everything by default except what is explicitly allowed. For example, the following scrubber would remove anything not enclosed in <public></public> tags:


```python
metadata.scrubber('^(?:\n|.)*<public>((?:\n|.)*)</public>(?:\n|.)*$', replacement = '$1')
```

So a message like:

```
this
is
very confidential<public>but this is public
very public
</public>
and this is a secret too
```

would be transformed into:

```
but this is public
very public
```



<a id="metadata.verify_match" aria-hidden="true"></a>
## metadata.verify_match

Verifies that a RegEx matches (or not matches) the change message. Does not, transform anything, but will stop the workflow if it fails.

`transformation metadata.verify_match(regex, verify_no_match=False)`

### Parameters:

Parameter | Description
--------- | -----------
regex|`string`<br><p>The regex pattern to verify. The re2j pattern will be applied in multiline mode, i.e. '^' refers to the beginning of a file and '$' to its end.</p>
verify_no_match|`boolean`<br><p>If true, the transformation will verify that the RegEx does not match.</p>


### Example:

#### Check that a text is present in the change description:

Check that the change message contains a text enclosed in <public></public>:

```python
metadata.verify_match("<public>(.|\n)*</public>")
```

<a id="metadata.map_references" aria-hidden="true"></a>
## metadata.map_references

Allows updating links to references in commit messages to match the destination's format. Note that this will only consider the 5000 latest commits.

`referenceMigrator metadata.map_references(before, after, regex_groups={}, additional_import_labels=[])`

### Parameters:

Parameter | Description
--------- | -----------
before|`string`<br><p>Template for origin references in the change message. Use a '${reference}' token to capture the actual references. E.g. if the origin uses linkslike 'http://changes?1234', the template would be 'http://internalReviews.com/${reference}', with reference_regex = '[0-9]+'</p>
after|`string`<br><p>Format for references in the destination, use the token '${reference}' to represent the destination reference. E.g. 'http://changes(${reference})'.</p>
regex_groups|`dict`<br><p>Regexes for the ${reference} token's content. Requires one 'before_ref' entry matching the ${reference} token's content on the before side. Optionally accepts one 'after_ref' used for validation.</p>
additional_import_labels|`sequence of string`<br><p>Meant to be used when migrating from another tool: Per default, copybara will only recognize the labels defined in the workflow's endpoints. The tool will use these additional labels to find labels created by other invocations and tools.</p>


### Example:

#### Map references, origin source of truth:

Finds links to commits in change messages, searches destination to find the equivalent reference in destination. Then replaces matches of 'before' with 'after', replacing the subgroup matched with the destination reference. Assume a message like 'Fixes bug introduced in origin/abcdef', where the origin change 'abcdef' was migrated as '123456' to the destination.

```python
metadata.map_references(
    before = "origin/${reference}",
    after = "destination/${reference}",
    regex_groups = {
        "before_ref": "[0-9a-f]+",
        "after_ref": "[0-9]+",
    },
),
```

This would be translated into 'Fixes bug introduced in destination/123456', provided that a change with the proper label was found - the message remains unchanged otherwise.


# core

Core functionality for creating migrations, and basic transformations.

<a id="glob" aria-hidden="true"></a>
## glob

Glob returns a list of every file in the workdir that matches at least one pattern in include and does not match any of the patterns in exclude.

`glob glob(include, exclude=[])`

### Parameters:

Parameter | Description
--------- | -----------
include|`sequence of string`<br><p>The list of glob patterns to include</p>
exclude|`sequence of string`<br><p>The list of glob patterns to exclude</p>


### Examples:

#### Simple usage:

Include all the files under a folder except for `internal` folder files:

```python
glob(["foo/**"], exclude = ["foo/internal/**"])
```

#### Multiple folders:

Globs can have multiple inclusive rules:

```python
glob(["foo/**", "bar/**", "baz/**.java"])
```

This will include all files inside `foo` and `bar` folders and Java files inside `baz` folder.

#### Multiple excludes:

Globs can have multiple exclusive rules:

```python
glob(["foo/**"], exclude = ["foo/internal/**", "foo/confidential/**" ])
```

Include all the files of `foo` except the ones in `internal` and `confidential` folders

#### All BUILD files recursively:

Copybara uses Java globbing. The globbing is very similar to Bash one. This means that recursive globbing for a filename is a bit more tricky:

```python
glob(["BUILD", "**/BUILD"])
```

This is the correct way of matching all `BUILD` files recursively, including the one in the root. `**/BUILD` would only match `BUILD` files in subdirectories.

#### Matching multiple strings with one expression:

While two globs can be used for matching two directories, there is a more compact approach:

```python
glob(["{java,javatests}/**"])
```

This matches any file in `java` and `javatests` folders.

<a id="core.reverse" aria-hidden="true"></a>
## core.reverse

Given a list of transformations, returns the list of transformations equivalent to undoing all the transformations

`sequence core.reverse(transformations)`

### Parameters:

Parameter | Description
--------- | -----------
transformations|`sequence of transformation`<br><p>The transformations to reverse</p>


<a id="core.workflow" aria-hidden="true"></a>
## core.workflow

Defines a migration pipeline which can be invoked via the Copybara command.

Implicit labels that can be used/exposed:

  - COPYBARA_CONTEXT_REFERENCE: Requested reference. For example if copybara is invoked as `copybara copy.bara.sky workflow master`, the value would be `master`.

`core.workflow(name, origin, destination, authoring, transformations=[], origin_files=glob(['**']), destination_files=glob(['**']), mode="SQUASH", reversible_check=True for 'CHANGE_REQUEST' mode. False otherwise, check_last_rev_state=False, ask_for_confirmation=False, dry_run=False)`

### Parameters:

Parameter | Description
--------- | -----------
name|`string`<br><p>The name of the workflow.</p>
origin|`origin`<br><p>Where to read from the code to be migrated, before applying the transformations. This is usually a VCS like Git, but can also be a local folder or even a pending change in a code review system like Gerrit.</p>
destination|`destination`<br><p>Where to write to the code being migrated, after applying the transformations. This is usually a VCS like Git, but can also be a local folder or even a pending change in a code review system like Gerrit.</p>
authoring|`authoring_class`<br><p>The author mapping configuration from origin to destination.</p>
transformations|`sequence`<br><p>The transformations to be run for this workflow. They will run in sequence.</p>
origin_files|`glob`<br><p>A glob relative to the workdir that will be read from the origin during the import. For example glob(["**.java"]), all java files, recursively, which excludes all other file types.</p>
destination_files|`glob`<br><p>A glob relative to the root of the destination repository that matches files that are part of the migration. Files NOT matching this glob will never be removed, even if the file does not exist in the source. For example glob(['**'], exclude = ['**/BUILD']) keeps all BUILD files in destination when the origin does not have any BUILD files. You can also use this to limit the migration to a subdirectory of the destination, e.g. glob(['java/src/**'], exclude = ['**/BUILD']) to only affect non-BUILD files in java/src.</p>
mode|`string`<br><p>Workflow mode. Currently we support three modes:<br><ul><li><b>'SQUASH'</b>: Create a single commit in the destination with new tree state.</li><li><b>'ITERATIVE'</b>: Import each origin change individually.</li><li><b>'CHANGE_REQUEST'</b>: Import an origin tree state diffed by a common parent in destination. This could be a GH Pull Request, a Gerrit Change, etc.</li></ul></p>
reversible_check|`boolean`<br><p>Indicates if the tool should try to to reverse all the transformations at the end to check that they are reversible.<br/>The default value is True for 'CHANGE_REQUEST' mode. False otherwise</p>
check_last_rev_state|`boolean`<br><p>If set to true, Copybara will validate that the destination didn't change since last-rev import for destination_files. Note that this flag doesn't work for CHANGE_REQUEST mode.</p>
ask_for_confirmation|`boolean`<br><p>Indicates that the tool should show the diff and require user's confirmation before making a change in the destination.</p>
dry_run|`boolean`<br><p>Run the migration in dry-run mode. Some destination implementations might have some side effects (like creating a code review), but never submit to a main branch.</p>




**Command line flags:**

Name | Type | Description
---- | ----------- | -----------
--change_request_parent | *string* | Commit revision to be used as parent when importing a commit using CHANGE_REQUEST workflow mode. this shouldn't be needed in general as Copybara is able to detect the parent commit message.
--last-rev | *string* | Last revision that was migrated to the destination
--iterative-limit-changes | *int* | Import just a number of changes instead of all the pending ones
--ignore-noop | *boolean* | Only warn about operations/transforms that didn't have any effect. For example: A transform that didn't modify any file, non-existent origin directories, etc.
--squash-skip-history | *boolean* | Avoid exposing the history of changes that are being migrated. This is useful when we want to migrate a new repository but we don't want to expose all the change history to metadata.squash_notes.
--iterative-all-changes, --import-noop-changes | *boolean* | By default Copybara will only try to migrate changes that could affect the destination. Ignoring changes that only affect excluded files in origin_files. This flag disables that behavior and runs for all the changes.
--check-last-rev-state | *boolean* | If enabled, Copybara will validate that the destination didn't change since last-rev import for destination_files. Note that this flag doesn't work for CHANGE_REQUEST mode.
--dry-run | *boolean* | Run the migration in dry-run mode. Some destination implementations might have some side effects (like creating a code review), but never submit to a main branch.

<a id="core.move" aria-hidden="true"></a>
## core.move

Moves files between directories and renames files

`transformation core.move(before, after, paths=glob(["**"]), overwrite=False)`

### Parameters:

Parameter | Description
--------- | -----------
before|`string`<br><p>The name of the file or directory before moving. If this is the empty string and 'after' is a directory, then all files in the workdir will be moved to the sub directory specified by 'after', maintaining the directory tree.</p>
after|`string`<br><p>The name of the file or directory after moving. If this is the empty string and 'before' is a directory, then all files in 'before' will be moved to the repo root, maintaining the directory tree inside 'before'.</p>
paths|`glob`<br><p>A glob expression relative to 'before' if it represents a directory. Only files matching the expression will be moved. For example, glob(["**.java"]), matches all java files recursively inside 'before' folder. Defaults to match all the files recursively.</p>
overwrite|`boolean`<br><p>Overwrite destination files if they already exist. Note that this makes the transformation non-reversible, since there is no way to know if the file was overwritten or not in the reverse workflow.</p>


### Examples:

#### Move a directory:

Move all the files in a directory to another directory:

```python
core.move("foo/bar_internal", "bar")
```

In this example, `foo/bar_internal/one` will be moved to `bar/one`.

#### Move all the files to a subfolder:

Move all the files in the checkout dir into a directory called foo:

```python
core.move("", "foo")
```

In this example, `one` and `two/bar` will be moved to `foo/one` and `foo/two/bar`.

#### Move a subfolder's content to the root:

Move the contents of a folder to the checkout root directory:

```python
core.move("foo", "")
```

In this example, `foo/bar` would be moved to `bar`.

<a id="core.copy" aria-hidden="true"></a>
## core.copy

Copy files between directories and renames files

`transformation core.copy(before, after, paths=glob(["**"]), overwrite=False)`

### Parameters:

Parameter | Description
--------- | -----------
before|`string`<br><p>The name of the file or directory to copy. If this is the empty string and 'after' is a directory, then all files in the workdir will be copied to the sub directory specified by 'after', maintaining the directory tree.</p>
after|`string`<br><p>The name of the file or directory destination. If this is the empty string and 'before' is a directory, then all files in 'before' will be copied to the repo root, maintaining the directory tree inside 'before'.</p>
paths|`glob`<br><p>A glob expression relative to 'before' if it represents a directory. Only files matching the expression will be copied. For example, glob(["**.java"]), matches all java files recursively inside 'before' folder. Defaults to match all the files recursively.</p>
overwrite|`boolean`<br><p>Overwrite destination files if they already exist. Note that this makes the transformation non-reversible, since there is no way to know if the file was overwritten or not in the reverse workflow.</p>


### Examples:

#### Copy a directory:

Move all the files in a directory to another directory:

```python
core.copy("foo/bar_internal", "bar")
```

In this example, `foo/bar_internal/one` will be copied to `bar/one`.

#### Copy with reversal:

Copy all static files to a 'static' folder and use remove for reverting the change

```python
core.transform(
    [core.copy("foo", "foo/static", paths = glob(["**.css","**.html", ]))],
    reversal = [core.remove(glob(['foo/static/**.css', 'foo/static/**.html']))]
)
```

<a id="core.remove" aria-hidden="true"></a>
## core.remove

Remove files from the workdir. **This transformation is only mean to be used inside core.transform for reversing core.copy like transforms**. For regular file filtering use origin_files exclude mechanism.

`remove core.remove(paths)`

### Parameters:

Parameter | Description
--------- | -----------
paths|`glob`<br><p>The files to be deleted</p>


### Examples:

#### Reverse a file copy:

Move all the files in a directory to another directory:

```python
core.transform(
    [core.copy("foo", "foo/public")],
    reversal = [core.remove(glob(["foo/public/**"]))])
```

In this example, `foo/bar_internal/one` will be moved to `bar/one`.

#### Copy with reversal:

Copy all static files to a 'static' folder and use remove for reverting the change

```python
core.transform(
    [core.copy("foo", "foo/static", paths = glob(["**.css","**.html", ]))],
    reversal = [core.remove(glob(['foo/static/**.css', 'foo/static/**.html']))]
)
```

<a id="core.replace" aria-hidden="true"></a>
## core.replace

Replace a text with another text using optional regex groups. This tranformer can be automatically reversed.

`replace core.replace(before, after, regex_groups={}, paths=glob(["**"]), first_only=False, multiline=False, repeated_groups=False, ignore=[])`

### Parameters:

Parameter | Description
--------- | -----------
before|`string`<br><p>The text before the transformation. Can contain references to regex groups. For example "foo${x}text".<p>If '$' literal character needs to be matched, '`$$`' should be used. For example '`$$FOO`' would match the literal '$FOO'.</p>
after|`string`<br><p>The text after the transformation. It can also contain references to regex groups, like 'before' field.</p>
regex_groups|`dict`<br><p>A set of named regexes that can be used to match part of the replaced text. For example {"x": "[A-Za-z]+"}</p>
paths|`glob`<br><p>A glob expression relative to the workdir representing the files to apply the transformation. For example, glob(["**.java"]), matches all java files recursively. Defaults to match all the files recursively.</p>
first_only|`boolean`<br><p>If true, only replaces the first instance rather than all. In single line mode, replaces the first instance on each line. In multiline mode, replaces the first instance in each file.</p>
multiline|`boolean`<br><p>Whether to replace text that spans more than one line.</p>
repeated_groups|`boolean`<br><p>Allow to use a group multiple times. For example foo${repeated}/${repeated}. Note that this mechanism doesn't use backtracking. In other words, the group instances are treated as different groups in regex construction and then a validation is done after that.</p>
ignore|`sequence`<br><p>A set of regexes. Any text that matches any expression in this set, which might otherwise be transformed, will be ignored.</p>


### Examples:

#### Simple replacement:

Replaces the text "internal" with "external" in all java files

```python
core.replace(
    before = "internal",
    after = "external",
    paths = glob(["**.java"]),
)
```

#### Replace using regex groups:

In this example we map some urls from the internal to the external version in all the files of the project.

```python
core.replace(
        before = "https://some_internal/url/${pkg}.html",
        after = "https://example.com/${pkg}.html",
        regex_groups = {
            "pkg": ".*",
        },
    )
```

So a url like `https://some_internal/url/foo/bar.html` will be transformed to `https://example.com/foo/bar.html`.

#### Remove confidential blocks:

This example removes blocks of text/code that are confidential and thus shouldn'tbe exported to a public repository.

```python
core.replace(
        before = "${x}",
        after = "",
        multiline = True,
        regex_groups = {
            "x": "(?m)^.*BEGIN-INTERNAL[\\w\\W]*?END-INTERNAL.*$\\n",
        },
    )
```

This replace would transform a text file like:

```
This is
public
 // BEGIN-INTERNAL
 confidential
 information
 // END-INTERNAL
more public code
 // BEGIN-INTERNAL
 more confidential
 information
 // END-INTERNAL
```

Into:

```
This is
public
more public code
```



<a id="core.verify_match" aria-hidden="true"></a>
## core.verify_match

Verifies that a RegEx matches (or not matches) the specified files. Does not, transform anything, but will stop the workflow if it fails.

`verifyMatch core.verify_match(regex, paths=glob(["**"]), verify_no_match=False)`

### Parameters:

Parameter | Description
--------- | -----------
regex|`string`<br><p>The regex pattern to verify. To satisfy the validation, there has to be atleast one (or no matches if verify_no_match) match in each of the files included in paths. The re2j pattern will be applied in multiline mode, i.e. '^' refers to the beginning of a file and '$' to its end.</p>
paths|`glob`<br><p>A glob expression relative to the workdir representing the files to apply the transformation. For example, glob(["**.java"]), matches all java files recursively. Defaults to match all the files recursively.</p>
verify_no_match|`boolean`<br><p>If true, the transformation will verify that the RegEx does not match.</p>


<a id="core.transform" aria-hidden="true"></a>
## core.transform

Groups some transformations in a transformation that can contain a particular, manually-specified, reversal, where the forward version and reversed version of the transform are represented as lists of transforms. The is useful if a transformation does not automatically reverse, or if the automatic reversal does not work for some reason.<br>If reversal is not provided, the transform will try to compute the reverse of the transformations list.

`transformation core.transform(transformations, reversal=The reverse of 'transformations', ignore_noop=False)`

### Parameters:

Parameter | Description
--------- | -----------
transformations|`sequence of transformation`<br><p>The list of transformations to run as a result of running this transformation.</p>
reversal|`sequence of transformation`<br><p>The list of transformations to run as a result of running this transformation in reverse.</p>
ignore_noop|`boolean`<br><p>In case a noop error happens in the group of transformations (Both forward and reverse), it will be ignored. In general this is a bad idea and prevents Copybara for detecting important transformation errors.</p>



# folder

Module for dealing with local filesytem folders

<a id="folder.destination" aria-hidden="true"></a>
## folder.destination

A folder destination is a destination that puts the output in a folder

`destination folder.destination()`



**Command line flags:**

Name | Type | Description
---- | ----------- | -----------
--folder-dir | *string* | Local directory to put the output of the transformation

<a id="folder.origin" aria-hidden="true"></a>
## folder.origin

A folder origin is a origin that uses a folder as input

`folderOrigin folder.origin(materialize_outside_symlinks=False)`

### Parameters:

Parameter | Description
--------- | -----------
materialize_outside_symlinks|`boolean`<br><p>By default folder.origin will refuse any symlink in the migration folder that is an absolute symlink or that refers to a file outside of the folder. If this flag is set, it will materialize those symlinks as regular files in the checkout directory.</p>




**Command line flags:**

Name | Type | Description
---- | ----------- | -----------
--folder-origin-author | *string* | Author of the change being migrated from folder.origin()
--folder-origin-message | *string* | Message of the change being migrated from folder.origin()


# git

Set of functions to define Git origins and destinations.

<a id="git.origin" aria-hidden="true"></a>
## git.origin

Defines a standard Git origin. For Git specific origins use: `github_origin` or `gerrit_origin`.<br><br>All the origins in this module accept several string formats as reference (When copybara is called in the form of `copybara config workflow reference`):<br><ul><li>**Branch name:** For example `master`</li><li>**An arbitrary reference:** `refs/changes/20/50820/1`</li><li>**A SHA-1:** Note that it has to be reachable from the default refspec</li><li>**A Git repository URL and reference:** `http://github.com/foo master`</li><li>**A GitHub pull request URL:** `https://github.com/some_project/pull/1784`</li></ul><br>So for example, Copybara can be invoked for a `git.origin` in the CLI as:<br>`copybara copy.bara.sky my_workflow https://github.com/some_project/pull/1784`<br>This will use the pull request as the origin URL and reference.

`gitOrigin git.origin(url, ref=None, submodules='NO', include_branch_commit_logs=False)`

### Parameters:

Parameter | Description
--------- | -----------
url|`string`<br><p>Indicates the URL of the git repository</p>
ref|`string`<br><p>Represents the default reference that will be used for reading the revision from the git repository. For example: 'master'</p>
submodules|`string`<br><p>Download submodules. Valid values: NO, YES, RECURSIVE.</p>
include_branch_commit_logs|`boolean`<br><p>Whether to include raw logs of branch commits in the migrated change message. This setting *only* affects merge commits.</p>


<a id="git.mirror" aria-hidden="true"></a>
## git.mirror

Mirror git references between repositories

`git.mirror(name, origin, destination, refspecs=['refs/heads/*'], prune=False)`

### Parameters:

Parameter | Description
--------- | -----------
name|`string`<br><p>Migration name</p>
origin|`string`<br><p>Indicates the URL of the origin git repository</p>
destination|`string`<br><p>Indicates the URL of the destination git repository</p>
refspecs|`sequence of string`<br><p>Represents a list of git refspecs to mirror between origin and destination.For example 'refs/heads/*:refs/remotes/origin/*' will mirror any referenceinside refs/heads to refs/remotes/origin.</p>
prune|`boolean`<br><p>Remove remote refs that don't have a origin counterpart</p>




**Command line flags:**

Name | Type | Description
---- | ----------- | -----------
--git-mirror-force | *boolean* | Force push even if it is not fast-forward

<a id="git.gerrit_origin" aria-hidden="true"></a>
## git.gerrit_origin

Defines a Git origin for Gerrit reviews.

Implicit labels that can be used/exposed:

  - GERRIT_CHANGE_NUMBER: The change number for the gerrit review.

`gitOrigin git.gerrit_origin(url, ref=None, submodules='NO')`

### Parameters:

Parameter | Description
--------- | -----------
url|`string`<br><p>Indicates the URL of the git repository</p>
ref|`string`<br><p>DEPRECATED. Use git.origin for submitted branches.</p>
submodules|`string`<br><p>Download submodules. Valid values: NO, YES, RECURSIVE.</p>


<a id="git.github_origin" aria-hidden="true"></a>
## git.github_origin

Defines a Git origin of type Github.

`gitOrigin git.github_origin(url, ref=None, submodules='NO')`

### Parameters:

Parameter | Description
--------- | -----------
url|`string`<br><p>Indicates the URL of the git repository</p>
ref|`string`<br><p>Represents the default reference that will be used for reading the revision from the git repository. For example: 'master'</p>
submodules|`string`<br><p>Download submodules. Valid values: NO, YES, RECURSIVE.</p>


<a id="git.destination" aria-hidden="true"></a>
## git.destination

Creates a commit in a git repository using the transformed worktree.<br><br>Given that Copybara doesn't ask for user/password in the console when doing the push to remote repos, you have to use ssh protocol, have the credentials cached or use a credential manager.

`gitDestination git.destination(url, push=master, fetch=push reference, skip_push=False)`

### Parameters:

Parameter | Description
--------- | -----------
url|`string`<br><p>Indicates the URL to push to as well as the URL from which to get the parent commit</p>
push|`string`<br><p>Reference to use for pushing the change, for example 'master'</p>
fetch|`string`<br><p>Indicates the ref from which to get the parent commit</p>
skip_push|`boolean`<br><p>If set, copybara will not actually push the result to the destination. This is meant for testing workflows and dry runs.</p>




**Command line flags:**

Name | Type | Description
---- | ----------- | -----------
--git-committer-name | *string* | If set, overrides the committer name for the generated commits in git destination.
--git-committer-email | *string* | If set, overrides the committer e-mail for the generated commits in git destination.
--git-destination-url | *string* | If set, overrides the git destination URL.
--git-destination-fetch | *string* | If set, overrides the git destination fetch reference.
--git-destination-push | *string* | If set, overrides the git destination push reference.
--git-destination-path | *string* | If set, the tool will use this directory for the local repository. Note that if the directory exists it needs to be a git repository. Copybara will revert any staged/unstaged changes.
--git-destination-skip-push | *boolean* | If set, the tool will not push to the remote destination
--git-destination-last-rev-first-parent | *boolean* | Use git --first-parent flag when looking for last-rev in previous commits
--git-destination-non-fast-forward | *boolean* | Allow non-fast-forward pushes to the destination. We only allow this when used with different push != fetch references.

<a id="git.gerrit_destination" aria-hidden="true"></a>
## git.gerrit_destination

Creates a change in Gerrit using the transformed worktree. If this is used in iterative mode, then each commit pushed in a single Copybara invocation will have the correct commit parent. The reviews generated can then be easily done in the correct order without rebasing.

`gerritDestination git.gerrit_destination(url, fetch, push_to_refs_for='')`

### Parameters:

Parameter | Description
--------- | -----------
url|`string`<br><p>Indicates the URL to push to as well as the URL from which to get the parent commit</p>
fetch|`string`<br><p>Indicates the ref from which to get the parent commit</p>
push_to_refs_for|`string`<br><p>Review branch to push the change to, for example setting this to 'feature_x' causes the destination to push to 'refs/for/feature_x'. It defaults to 'fetch' value.</p>




**Command line flags:**

Name | Type | Description
---- | ----------- | -----------
--git-committer-name | *string* | If set, overrides the committer name for the generated commits in git destination.
--git-committer-email | *string* | If set, overrides the committer e-mail for the generated commits in git destination.
--git-destination-url | *string* | If set, overrides the git destination URL.
--git-destination-fetch | *string* | If set, overrides the git destination fetch reference.
--git-destination-push | *string* | If set, overrides the git destination push reference.
--git-destination-path | *string* | If set, the tool will use this directory for the local repository. Note that if the directory exists it needs to be a git repository. Copybara will revert any staged/unstaged changes.
--git-destination-skip-push | *boolean* | If set, the tool will not push to the remote destination
--git-destination-last-rev-first-parent | *boolean* | Use git --first-parent flag when looking for last-rev in previous commits
--git-destination-non-fast-forward | *boolean* | Allow non-fast-forward pushes to the destination. We only allow this when used with different push != fetch references.


# patch

Module for applying patches.

<a id="patch.apply" aria-hidden="true"></a>
## patch.apply

A transformation that applies the given patch files. If a path does not exist in a patch, it will be ignored.

`patchTransformation patch.apply(patches=[], excluded_patch_paths=[], series=None)`

### Parameters:

Parameter | Description
--------- | -----------
patches|`sequence of string`<br><p>The list of patchfiles to apply, relative to the current config file.The files will be applied relative to the checkout dir and the leading pathcomponent will be stripped (-p1).</p>
excluded_patch_paths|`sequence of string`<br><p>The list of paths to exclude from each of the patches. Each of the paths will be excluded from all the patches. Note that these are not workdir paths, but paths relative to the patch itself.</p>
series|`string`<br><p>The config file that contains a list of patches to apply. The <i>series</i> file contains names of the patch files one per line. The names of the patch files are relative to the <i>series</i> config file. The files will be applied relative to the checkout dir and the leading path component will be stripped (-p1).</p>



