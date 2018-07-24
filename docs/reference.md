# Table of Contents


  - [author](#author)
  - [authoring](#authoring)
    - [authoring.overwrite](#authoring.overwrite)
    - [authoring.pass_thru](#authoring.pass_thru)
    - [authoring.whitelisted](#authoring.whitelisted)
    - [new_author](#new_author)
  - [authoring_class](#authoring_class)
  - [change](#change)
  - [ChangeMessage](#changemessage)
    - [message.label_values](#message.label_values)
  - [Changes](#changes)
  - [Console](#console)
    - [console.error](#console.error)
    - [console.info](#console.info)
    - [console.progress](#console.progress)
    - [console.verbose](#console.verbose)
    - [console.warn](#console.warn)
  - [core](#core)
    - [core.copy](#core.copy)
    - [core.dynamic_transform](#core.dynamic_transform)
    - [core.feedback](#core.feedback)
    - [core.move](#core.move)
    - [core.remove](#core.remove)
    - [core.replace](#core.replace)
    - [core.reverse](#core.reverse)
    - [core.todo_replace](#core.todo_replace)
    - [core.transform](#core.transform)
    - [core.verify_match](#core.verify_match)
    - [core.workflow](#core.workflow)
  - [feedback.context](#feedback.context)
    - [feedback.context.error](#feedback.context.error)
    - [feedback.context.noop](#feedback.context.noop)
    - [feedback.context.record_effect](#feedback.context.record_effect)
    - [feedback.context.success](#feedback.context.success)
  - [feedback.finish_hook_context](#feedback.finish_hook_context)
  - [feedback.revision_context](#feedback.revision_context)
  - [folder](#folder)
    - [folder.destination](#folder.destination)
    - [folder.origin](#folder.origin)
  - [gerritapi.AccountInfo](#gerritapi.accountinfo)
  - [gerritapi.ApprovalInfo](#gerritapi.approvalinfo)
  - [gerritapi.ChangeInfo](#gerritapi.changeinfo)
  - [gerritapi.ChangeMessageInfo](#gerritapi.changemessageinfo)
  - [gerritapi.ChangesQuery](#gerritapi.changesquery)
  - [gerritapi.CommitInfo](#gerritapi.commitinfo)
  - [gerritapi.GitPersonInfo](#gerritapi.gitpersoninfo)
  - [gerritapi.LabelInfo](#gerritapi.labelinfo)
  - [gerritapi.ParentCommitInfo](#gerritapi.parentcommitinfo)
  - [gerritapi.ReviewResult](#gerritapi.reviewresult)
  - [gerritapi.RevisionInfo](#gerritapi.revisioninfo)
  - [gerrit_api_obj](#gerrit_api_obj)
    - [gerrit_api_obj.get_change](#gerrit_api_obj.get_change)
    - [gerrit_api_obj.list_changes_by_commit](#gerrit_api_obj.list_changes_by_commit)
    - [gerrit_api_obj.post_review](#gerrit_api_obj.post_review)
  - [git](#git)
    - [git.destination](#git.destination)
    - [git.gerrit_api](#git.gerrit_api)
    - [git.gerrit_destination](#git.gerrit_destination)
    - [git.gerrit_origin](#git.gerrit_origin)
    - [git.gerrit_trigger](#git.gerrit_trigger)
    - [git.github_api](#git.github_api)
    - [git.github_origin](#git.github_origin)
    - [git.github_pr_destination](#git.github_pr_destination)
    - [git.github_pr_origin](#git.github_pr_origin)
    - [git.integrate](#git.integrate)
    - [git.mirror](#git.mirror)
    - [git.origin](#git.origin)
    - [git.review_input](#git.review_input)
  - [github_api_obj](#github_api_obj)
    - [github_api_obj.create_status](#github_api_obj.create_status)
    - [github_api_obj.get_reference](#github_api_obj.get_reference)
    - [github_api_obj.get_references](#github_api_obj.get_references)
    - [github_api_obj.update_reference](#github_api_obj.update_reference)
  - [Globals](#globals)
    - [glob](#glob)
    - [parse_message](#parse_message)
  - [metadata](#metadata)
    - [metadata.add_header](#metadata.add_header)
    - [metadata.expose_label](#metadata.expose_label)
    - [metadata.map_author](#metadata.map_author)
    - [metadata.map_references](#metadata.map_references)
    - [metadata.remove_label](#metadata.remove_label)
    - [metadata.replace_message](#metadata.replace_message)
    - [metadata.restore_author](#metadata.restore_author)
    - [metadata.save_author](#metadata.save_author)
    - [metadata.scrubber](#metadata.scrubber)
    - [metadata.squash_notes](#metadata.squash_notes)
    - [metadata.use_last_change](#metadata.use_last_change)
    - [metadata.verify_match](#metadata.verify_match)
  - [patch](#patch)
    - [patch.apply](#patch.apply)
  - [Path](#path)
    - [path.read_symlink](#path.read_symlink)
    - [path.relativize](#path.relativize)
    - [path.resolve](#path.resolve)
    - [path.resolve_sibling](#path.resolve_sibling)
  - [PathAttributes](#pathattributes)
  - [SetReviewInput](#setreviewinput)
  - [TransformWork](#transformwork)
    - [ctx.add_label](#ctx.add_label)
    - [ctx.add_or_replace_label](#ctx.add_or_replace_label)
    - [ctx.add_text_before_labels](#ctx.add_text_before_labels)
    - [ctx.find_all_labels](#ctx.find_all_labels)
    - [ctx.find_label](#ctx.find_label)
    - [ctx.new_path](#ctx.new_path)
    - [ctx.now_as_string](#ctx.now_as_string)
    - [ctx.read_path](#ctx.read_path)
    - [ctx.remove_label](#ctx.remove_label)
    - [ctx.replace_label](#ctx.replace_label)
    - [ctx.run](#ctx.run)
    - [ctx.set_author](#ctx.set_author)
    - [ctx.set_message](#ctx.set_message)
    - [ctx.write_path](#ctx.write_path)



## author

Represents the author of a change


#### Fields:

Name | Description
---- | -----------
email | The email of the author
name | The name of the author



## authoring

The authors mapping between an origin and a destination

<a id="authoring.overwrite" aria-hidden="true"></a>
### authoring.overwrite

Use the default author for all the submits in the destination. Note that some destinations might choose to ignore this author and use the current user running the tool (In other words they don't allow impersonation).

`authoring_class authoring.overwrite(default)`


#### Parameters:

Parameter | Description
--------- | -----------
default | `string`<br><p>The default author for commits in the destination</p>


#### Example:


##### Overwrite usage example:

Create an authoring object that will overwrite any origin author with noreply@foobar.com mail.

```python
authoring.overwrite("Foo Bar <noreply@foobar.com>")
```


<a id="authoring.pass_thru" aria-hidden="true"></a>
### authoring.pass_thru

Use the origin author as the author in the destination, no whitelisting.

`authoring_class authoring.pass_thru(default)`


#### Parameters:

Parameter | Description
--------- | -----------
default | `string`<br><p>The default author for commits in the destination. This is used in squash mode workflows or if author cannot be determined.</p>


#### Example:


##### Pass thru usage example:



```python
authoring.pass_thru(default = "Foo Bar <noreply@foobar.com>")
```


<a id="authoring.whitelisted" aria-hidden="true"></a>
### authoring.whitelisted

Create an individual or team that contributes code.

`authoring_class authoring.whitelisted(default, whitelist)`


#### Parameters:

Parameter | Description
--------- | -----------
default | `string`<br><p>The default author for commits in the destination. This is used in squash mode workflows or when users are not whitelisted.</p>
whitelist | `sequence of string`<br><p>List of white listed authors in the origin. The authors must be unique</p>


#### Examples:


##### Only pass thru whitelisted users:



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


##### Only pass thru whitelisted LDAPs/usernames:

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


<a id="new_author" aria-hidden="true"></a>
### new_author

Create a new author from a string with the form 'name <foo@bar.com>'

`author new_author(author_string)`


#### Parameters:

Parameter | Description
--------- | -----------
author_string | `string`<br><p>A string representation of the author with the form 'name <foo@bar.com>'</p>


#### Example:


##### Create a new author:



```python
new_author('Foo Bar <foobar@myorg.com>')
```




## authoring_class

The authors mapping between an origin and a destination



## change

A change metadata. Contains information like author, change message or detected labels


#### Fields:

Name | Description
---- | -----------
author | The author of the change
date_time_iso_offset | Return a ISO offset date time. Example:  2011-12-03T10:15:30+01:00'
first_line_message | The message of the change
labels | A dictionary with the labels detected for the change. If the label is present multiple times it returns the last value. Note that this is an heuristic and it could include things that are not labels.
labels_all_values | A dictionary with the labels detected for the change. Note that the value is a collection of the values for each time the label was found. Use 'labels' instead if you are only interested in the last value. Note that this is an heuristic and it could include things that are not labels.
merge | Returns true if the change represents a merge
message | The message of the change
original_author | The author of the change before any mapping
ref | Origin reference ref



## ChangeMessage

Represents a well formed parsed change message with its associated labels.


#### Fields:

Name | Description
---- | -----------
first_line | First line of this message
text | The text description this message, not including the labels.

<a id="message.label_values" aria-hidden="true"></a>
### message.label_values

Returns a list of values associated with the label name.

`sequence of string message.label_values(label_name)`


#### Parameters:

Parameter | Description
--------- | -----------
label_name | `string`<br><p>The label name.</p>



## Changes

Data about the set of changes that are being migrated. Each change includes information like: original author, change message, labels, etc. You receive this as a field in TransformWork object for used defined transformations


#### Fields:

Name | Description
---- | -----------
current | List of changes that will be migrated
migrated | List of changes that where migrated in previous Copybara executions or if using ITERATIVE mode in previous iterations of this workflow.



## Console

A console that can be used in skylark transformations to print info, warning or error messages.

<a id="console.error" aria-hidden="true"></a>
### console.error

Show an error in the log. Note that this will stop Copybara execution.

`console.error(message)`


#### Parameters:

Parameter | Description
--------- | -----------
message | `string`<br><p>message to log</p>

<a id="console.info" aria-hidden="true"></a>
### console.info

Show an info message in the console

`console.info(message)`


#### Parameters:

Parameter | Description
--------- | -----------
message | `string`<br><p>message to log</p>

<a id="console.progress" aria-hidden="true"></a>
### console.progress

Show a progress message in the console

`console.progress(message)`


#### Parameters:

Parameter | Description
--------- | -----------
message | `string`<br><p>message to log</p>

<a id="console.verbose" aria-hidden="true"></a>
### console.verbose

Show an info message in the console if verbose logging is enabled.

`console.verbose(message)`


#### Parameters:

Parameter | Description
--------- | -----------
message | `string`<br><p>message to log</p>

<a id="console.warn" aria-hidden="true"></a>
### console.warn

Show a warning in the console

`console.warn(message)`


#### Parameters:

Parameter | Description
--------- | -----------
message | `string`<br><p>message to log</p>



## core

Core functionality for creating migrations, and basic transformations.



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<nobr>`--config-root`</nobr> | *string* | Configuration root path to be used for resolving absolute config labels like '//foo/bar'
<nobr>`--console-file-path`</nobr> | *string* | If set, write the console output also to the given file path.
<nobr>`--disable-reversible-check`</nobr> | *boolean* | If set, all workflows will be executed without reversible_check, overriding the  workflow config and the normal behavior for CHANGE_REQUEST mode.
<nobr>`--dry-run`</nobr> | *boolean* | Run the migration in dry-run mode. Some destination implementations might have some side effects (like creating a code review), but never submit to a main branch.
<nobr>`--force`</nobr> | *boolean* | Force the migration even if Copybara cannot find in the destination a change that is an ancestor of the one(s) being migrated. This should be used with care, as it could lose changes when migrating a previous/conflicting change.
<nobr>`--noansi`</nobr> | *boolean* | Don't use ANSI output for messages
<nobr>`--nocleanup`</nobr> | *boolean* | Cleanup the output directories. This includes the workdir, scratch clones of Git repos, etc. By default is set to false and directories will be cleaned prior to the execution. If set to true, the previous run output will not be cleaned up. Keep in mind that running in this mode will lead to an ever increasing disk usage.
<nobr>`--output-limit`</nobr> | *int* | Limit the output in the console to a number of records. Each subcommand might use this flag differently. Defaults to 0, which shows all the output.
<nobr>`--output-root`</nobr> | *string* | The root directory where to generate output files. If not set, ~/copybara/out is used by default. Use with care, Copybara might remove files inside this root if necessary.
<nobr>`-v, --verbose`</nobr> | *boolean* | Verbose output.

<a id="core.copy" aria-hidden="true"></a>
### core.copy

Copy files between directories and renames files

`transformation core.copy(before, after, paths=glob(["**"]), overwrite=False)`


#### Parameters:

Parameter | Description
--------- | -----------
before | `string`<br><p>The name of the file or directory to copy. If this is the empty string and 'after' is a directory, then all files in the workdir will be copied to the sub directory specified by 'after', maintaining the directory tree.</p>
after | `string`<br><p>The name of the file or directory destination. If this is the empty string and 'before' is a directory, then all files in 'before' will be copied to the repo root, maintaining the directory tree inside 'before'.</p>
paths | `glob`<br><p>A glob expression relative to 'before' if it represents a directory. Only files matching the expression will be copied. For example, glob(["**.java"]), matches all java files recursively inside 'before' folder. Defaults to match all the files recursively.</p>
overwrite | `boolean`<br><p>Overwrite destination files if they already exist. Note that this makes the transformation non-reversible, since there is no way to know if the file was overwritten or not in the reverse workflow.</p>


#### Examples:


##### Copy a directory:

Move all the files in a directory to another directory:

```python
core.copy("foo/bar_internal", "bar")
```

In this example, `foo/bar_internal/one` will be copied to `bar/one`.


##### Copy with reversal:

Copy all static files to a 'static' folder and use remove for reverting the change

```python
core.transform(
    [core.copy("foo", "foo/static", paths = glob(["**.css","**.html", ]))],
    reversal = [core.remove(glob(['foo/static/**.css', 'foo/static/**.html']))]
)
```


<a id="core.dynamic_transform" aria-hidden="true"></a>
### core.dynamic_transform

Create a dynamic Skylark transformation. This should only be used by libraries developers

`transformation core.dynamic_transform(impl, params={})`


#### Parameters:

Parameter | Description
--------- | -----------
impl | `baseFunction`<br><p>The Skylark function to call</p>
params | `dict`<br><p>The parameters to the function. Will be available under ctx.params</p>


#### Example:


##### Create a dynamic transformation with parameter:

If you want to create a library that uses dynamic transformations, you probably want to make them customizable. In order to do that, in your library.bara.sky, you need to hide the dynamic transformation (prefix with '_' and instead expose a function that creates the dynamic transformation with the param:

```python
def _test_impl(ctx):
  ctx.set_message(ctx.message + ctx.params['name'] + str(ctx.params['number']) + '\n')

def test(name, number = 2):
  return core.dynamic_transform(impl = _test_impl,
                           params = { 'name': name, 'number': number})

  
```

After defining this function, you can use `test('example', 42)` as a transformation in `core.workflow`.


<a id="core.feedback" aria-hidden="true"></a>
### core.feedback

[EXPERIMENTAL] This feature is currently under development.

Defines a migration of changes' metadata, that can be invoked via the Copybara command in the same way as a regular workflow migrates the change itself.

It is considered change metadata any information associated with a change (pending or submitted) that is not core to the change itself. A few examples:
<ul>
  <li> Comments: Present in any code review system. Examples: Github PRs or Gerrit     code reviews.</li>
  <li> Labels: Used in code review systems for approvals and/or CI results.     Examples: Github labels, Gerrit code review labels.</li>
</ul>
For the purpose of this workflow, it is not considered metadata the commit message in Git, or any of the contents of the file tree.



`core.feedback(name, origin, destination, actions=[])`


#### Parameters:

Parameter | Description
--------- | -----------
name | `string`<br><p>The name of the feedback workflow.</p>
origin | `trigger`<br><p>The trigger of a feedback migration.</p>
destination | `api`<br><p>Where to write change metadata to. This is usually a code review system like Gerrit or Github PR.</p>
actions | `sequence`<br><p>A list of feedback actions to perform, with the following semantics:
  - There is no guarantee of the order of execution.
  - Actions need to be independent from each other.
  - Failure in one action might prevent other actions from executing.
</p>

<a id="core.move" aria-hidden="true"></a>
### core.move

Moves files between directories and renames files

`transformation core.move(before, after, paths=glob(["**"]), overwrite=False)`


#### Parameters:

Parameter | Description
--------- | -----------
before | `string`<br><p>The name of the file or directory before moving. If this is the empty string and 'after' is a directory, then all files in the workdir will be moved to the sub directory specified by 'after', maintaining the directory tree.</p>
after | `string`<br><p>The name of the file or directory after moving. If this is the empty string and 'before' is a directory, then all files in 'before' will be moved to the repo root, maintaining the directory tree inside 'before'.</p>
paths | `glob`<br><p>A glob expression relative to 'before' if it represents a directory. Only files matching the expression will be moved. For example, glob(["**.java"]), matches all java files recursively inside 'before' folder. Defaults to match all the files recursively.</p>
overwrite | `boolean`<br><p>Overwrite destination files if they already exist. Note that this makes the transformation non-reversible, since there is no way to know if the file was overwritten or not in the reverse workflow.</p>


#### Examples:


##### Move a directory:

Move all the files in a directory to another directory:

```python
core.move("foo/bar_internal", "bar")
```

In this example, `foo/bar_internal/one` will be moved to `bar/one`.


##### Move all the files to a subfolder:

Move all the files in the checkout dir into a directory called foo:

```python
core.move("", "foo")
```

In this example, `one` and `two/bar` will be moved to `foo/one` and `foo/two/bar`.


##### Move a subfolder's content to the root:

Move the contents of a folder to the checkout root directory:

```python
core.move("foo", "")
```

In this example, `foo/bar` would be moved to `bar`.


<a id="core.remove" aria-hidden="true"></a>
### core.remove

Remove files from the workdir. **This transformation is only mean to be used inside core.transform for reversing core.copy like transforms**. For regular file filtering use origin_files exclude mechanism.

`remove core.remove(paths)`


#### Parameters:

Parameter | Description
--------- | -----------
paths | `glob`<br><p>The files to be deleted</p>


#### Examples:


##### Reverse a file copy:

Move all the files in a directory to another directory:

```python
core.transform(
    [core.copy("foo", "foo/public")],
    reversal = [core.remove(glob(["foo/public/**"]))])
```

In this example, `foo/bar_internal/one` will be moved to `bar/one`.


##### Copy with reversal:

Copy all static files to a 'static' folder and use remove for reverting the change

```python
core.transform(
    [core.copy("foo", "foo/static", paths = glob(["**.css","**.html", ]))],
    reversal = [core.remove(glob(['foo/static/**.css', 'foo/static/**.html']))]
)
```


<a id="core.replace" aria-hidden="true"></a>
### core.replace

Replace a text with another text using optional regex groups. This tranformer can be automatically reversed.

`replace core.replace(before, after, regex_groups={}, paths=glob(["**"]), first_only=False, multiline=False, repeated_groups=False, ignore=[])`


#### Parameters:

Parameter | Description
--------- | -----------
before | `string`<br><p>The text before the transformation. Can contain references to regex groups. For example "foo${x}text".<p>If '$' literal character needs to be matched, '`$$`' should be used. For example '`$$FOO`' would match the literal '$FOO'.</p>
after | `string`<br><p>The text after the transformation. It can also contain references to regex groups, like 'before' field.</p>
regex_groups | `dict`<br><p>A set of named regexes that can be used to match part of the replaced text.Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax. For example {"x": "[A-Za-z]+"}</p>
paths | `glob`<br><p>A glob expression relative to the workdir representing the files to apply the transformation. For example, glob(["**.java"]), matches all java files recursively. Defaults to match all the files recursively.</p>
first_only | `boolean`<br><p>If true, only replaces the first instance rather than all. In single line mode, replaces the first instance on each line. In multiline mode, replaces the first instance in each file.</p>
multiline | `boolean`<br><p>Whether to replace text that spans more than one line.</p>
repeated_groups | `boolean`<br><p>Allow to use a group multiple times. For example foo${repeated}/${repeated}. Note that this mechanism doesn't use backtracking. In other words, the group instances are treated as different groups in regex construction and then a validation is done after that.</p>
ignore | `sequence`<br><p>A set of regexes. Any text that matches any expression in this set, which might otherwise be transformed, will be ignored.</p>


#### Examples:


##### Simple replacement:

Replaces the text "internal" with "external" in all java files

```python
core.replace(
    before = "internal",
    after = "external",
    paths = glob(["**.java"]),
)
```


##### Replace using regex groups:

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


##### Remove confidential blocks:

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




<a id="core.reverse" aria-hidden="true"></a>
### core.reverse

Given a list of transformations, returns the list of transformations equivalent to undoing all the transformations

`sequence of transformation core.reverse(transformations)`


#### Parameters:

Parameter | Description
--------- | -----------
transformations | `sequence of transformation`<br><p>The transformations to reverse</p>

<a id="core.todo_replace" aria-hidden="true"></a>
### core.todo_replace

Replace Google style TODOs. For example `TODO(username, othername)`.

`todoReplace core.todo_replace(tags=['TODO', 'NOTE'], mapping={}, mode='MAP_OR_IGNORE', paths=glob(["**"]), default=None)`


#### Parameters:

Parameter | Description
--------- | -----------
tags | `sequence of string`<br><p>Prefix tag to look for</p>
mapping | `dict`<br><p>Mapping of users/strings</p>
mode | `string`<br><p>Mode for the replace:<ul><li>'MAP_OR_FAIL': Try to use the mapping and if not found fail.</li><li>'MAP_OR_IGNORE': Try to use the mapping but ignore if no mapping found.</li><li>'MAP_OR_DEFAULT': Try to use the mapping and use the default if not found.</li><li>'SCRUB_NAMES': Scrub all names from TODOs. Transforms 'TODO(foo)' to 'TODO'</li><li>'USE_DEFAULT': Replace any TODO(foo, bar) with TODO(default_string)</li></ul></p>
paths | `glob`<br><p>A glob expression relative to the workdir representing the files to apply the transformation. For example, glob(["**.java"]), matches all java files recursively. Defaults to match all the files recursively.</p>
default | `string`<br><p>Default value if mapping not found. Only valid for 'MAP_OR_DEFAULT' or 'USE_DEFAULT' modes</p>


#### Examples:


##### Simple update:

Replace TODOs and NOTES for users in the mapping:

```python
core.todo_replace(
  mapping = {
    'test1' : 'external1',
    'test2' : 'external2'
  }
)
```

Would replace texts like TODO(test1) or NOTE(test1, test2) with TODO(external1) or NOTE(external1, external2)


##### Scrubbing:

Remove text from inside TODOs

```python
core.todo_replace(
  mode = 'SCRUB_NAMES'
)
```

Would replace texts like TODO(test1): foo or NOTE(test1, test2):foo with TODO:foo and NOTE:foo


<a id="core.transform" aria-hidden="true"></a>
### core.transform

Groups some transformations in a transformation that can contain a particular, manually-specified, reversal, where the forward version and reversed version of the transform are represented as lists of transforms. The is useful if a transformation does not automatically reverse, or if the automatic reversal does not work for some reason.<br>If reversal is not provided, the transform will try to compute the reverse of the transformations list.

`transformation core.transform(transformations, reversal=The reverse of 'transformations', ignore_noop=None)`


#### Parameters:

Parameter | Description
--------- | -----------
transformations | `sequence of transformation`<br><p>The list of transformations to run as a result of running this transformation.</p>
reversal | `sequence of transformation`<br><p>The list of transformations to run as a result of running this transformation in reverse.</p>
ignore_noop | `boolean`<br><p>In case a noop error happens in the group of transformations (Both forward and reverse), it will be ignored, but the rest of the transformations in the group will still be executed. If ignore_noop is not set, we will apply the closest parent's ignore_noop.</p>

<a id="core.verify_match" aria-hidden="true"></a>
### core.verify_match

Verifies that a RegEx matches (or not matches) the specified files. Does not transform anything, but will stop the workflow if it fails.

`verifyMatch core.verify_match(regex, paths=glob(["**"]), verify_no_match=False)`


#### Parameters:

Parameter | Description
--------- | -----------
regex | `string`<br><p>The regex pattern to verify. To satisfy the validation, there has to be atleast one (or no matches if verify_no_match) match in each of the files included in paths. The re2j pattern will be applied in multiline mode, i.e. '^' refers to the beginning of a file and '$' to its end. Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax.</p>
paths | `glob`<br><p>A glob expression relative to the workdir representing the files to apply the transformation. For example, glob(["**.java"]), matches all java files recursively. Defaults to match all the files recursively.</p>
verify_no_match | `boolean`<br><p>If true, the transformation will verify that the RegEx does not match.</p>

<a id="core.workflow" aria-hidden="true"></a>
### core.workflow

Defines a migration pipeline which can be invoked via the Copybara command.

Implicit labels that can be used/exposed:

  - COPYBARA_CONTEXT_REFERENCE: Requested reference. For example if copybara is invoked as `copybara copy.bara.sky workflow master`, the value would be `master`.
  - COPYBARA_LAST_REV: Last reference that was migrated
  - COPYBARA_CURRENT_REV: The current reference being migrated
  - COPYBARA_CURRENT_MESSAGE: The current message at this point of the transformations
  - COPYBARA_CURRENT_MESSAGE_TITLE: The current message title (first line) at this point of the transformations
  - COPYBARA_AUTHOR: The author of the change


`core.workflow(name, origin, destination, authoring, transformations=[], origin_files=glob(["**"]), destination_files=glob(["**"]), mode="SQUASH", reversible_check=True for 'CHANGE_REQUEST' mode. False otherwise, check_last_rev_state=True for CHANGE_REQUEST, ask_for_confirmation=False, dry_run=False, after_migration=[], change_identity=None, set_rev_id=True, smart_prune=False, migrate_noop_changes=False)`


#### Parameters:

Parameter | Description
--------- | -----------
name | `string`<br><p>The name of the workflow.</p>
origin | `origin`<br><p>Where to read from the code to be migrated, before applying the transformations. This is usually a VCS like Git, but can also be a local folder or even a pending change in a code review system like Gerrit.</p>
destination | `destination`<br><p>Where to write to the code being migrated, after applying the transformations. This is usually a VCS like Git, but can also be a local folder or even a pending change in a code review system like Gerrit.</p>
authoring | `authoring_class`<br><p>The author mapping configuration from origin to destination.</p>
transformations | `sequence`<br><p>The transformations to be run for this workflow. They will run in sequence.</p>
origin_files | `glob`<br><p>A glob relative to the workdir that will be read from the origin during the import. For example glob(["**.java"]), all java files, recursively, which excludes all other file types.</p>
destination_files | `glob`<br><p>A glob relative to the root of the destination repository that matches files that are part of the migration. Files NOT matching this glob will never be removed, even if the file does not exist in the source. For example glob(['**'], exclude = ['**/BUILD']) keeps all BUILD files in destination when the origin does not have any BUILD files. You can also use this to limit the migration to a subdirectory of the destination, e.g. glob(['java/src/**'], exclude = ['**/BUILD']) to only affect non-BUILD files in java/src.</p>
mode | `string`<br><p>Workflow mode. Currently we support three modes:<br><ul><li><b>'SQUASH'</b>: Create a single commit in the destination with new tree state.</li><li><b>'ITERATIVE'</b>: Import each origin change individually.</li><li><b>'CHANGE_REQUEST'</b>: Import a pending change to the Source-of-Truth. This could be a GH Pull Request, a Gerrit Change, etc. The final intention should be to submit the change.</li><li><b>'CHANGE_REQUEST_FROM_SOT'</b>: Import a pending change **from** the Source-of-Truth. This mode is useful when, despite the pending change being already in the SoT, the users want to review the code on a different system. The final intention should never be to submit in the destination, but just review or test</li></ul></p>
reversible_check | `boolean`<br><p>Indicates if the tool should try to to reverse all the transformations at the end to check that they are reversible.<br/>The default value is True for 'CHANGE_REQUEST' mode. False otherwise</p>
check_last_rev_state | `boolean`<br><p>If set to true, Copybara will validate that the destination didn't change since last-rev import for destination_files. Note that this flag doesn't work for CHANGE_REQUEST mode.</p>
ask_for_confirmation | `boolean`<br><p>Indicates that the tool should show the diff and require user's confirmation before making a change in the destination.</p>
dry_run | `boolean`<br><p>Run the migration in dry-run mode. Some destination implementations might have some side effects (like creating a code review), but never submit to a main branch.</p>
after_migration | `sequence`<br><p>Run a feedback workflow after one migration happens.</p>
change_identity | `string`<br><p>By default, Copybara hashes several fields so that each change has an unique identifier that at the same time reuses the generated destination change. This allows to customize the identity hash generation so that the same identity is used in several workflows. At least ${copybara_config_path} has to be present. Current user is added to the hash automatically.<br><br>Available variables:<ul>  <li>${copybara_config_path}: Main config file path</li>  <li>${copybara_workflow_name}: The name of the workflow being run</li>  <li>${copybara_reference}: The requested reference. In general Copybara tries its best to give a repetable reference. For example Gerrit change number or change-id or GitHub Pull Request number. If it cannot find a context reference it uses the resolved revision.</li>  <li>${label:label_name}: A label present for the current change. Exposed in the message or not.</li></ul>If any of the labels cannot be found it defaults to the default identity (The effect would be no reuse of destination change between workflows)</p>
set_rev_id | `boolean`<br><p>Copybara adds labels like 'GitOrigin-RevId' in the destination in order to track what was the latest change imported. For certain workflows like `CHANGE_REQUEST` it not used and is purely informational. This field allows to disable it for that mode. Destinations might ignore the flag.</p>
smart_prune | `boolean`<br><p>By default CHANGE_REQUEST workflows cannot restore scrubbed files. This flag does a best-effort approach in restoring the non-affected snippets. For now we only revert the non-affected files. This only works for CHANGE_REQUEST mode.</p>
migrate_noop_changes | `boolean`<br><p>By default, Copybara tries to only migrate changes that affect origin_files or config files. This flag allows to include all the changes. Note that it might generate more empty changes errors. In `ITERATIVE` mode it might fail if some transformation is validating the message (Like has to contain 'PUBLIC' and the change doesn't contain it because it is internal).</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<nobr>`--change-request-from-sot-limit`</nobr> | *int* | Number of origin baseline changes to use for trying to match one in the destination. It can be used if the are many parent changes in the origin that are a no-op in the destination
<nobr>`--change-request-from-sot-retry`</nobr> | *integer>* | Number of retries and delay between retries when we cannot find the baseline in the destination for CHANGE_REQUEST_FROM_SOT. For example '10,30,60' will retry three times. The first retry will be delayed 10s, the second one 30s and the third one 60s
<nobr>`--change_request_parent`</nobr> | *string* | Commit revision to be used as parent when importing a commit using CHANGE_REQUEST workflow mode. this shouldn't be needed in general as Copybara is able to detect the parent commit message.
<nobr>`--check-last-rev-state`</nobr> | *boolean* | If enabled, Copybara will validate that the destination didn't change since last-rev import for destination_files. Note that this flag doesn't work for CHANGE_REQUEST mode.
<nobr>`--default-author`</nobr> | *string* | Use this author as default instead of the one in the config file.Format should be 'Foo Bar <foobar@example.com>'
<nobr>`--ignore-noop`</nobr> | *boolean* | Only warn about operations/transforms that didn't have any effect. For example: A transform that didn't modify any file, non-existent origin directories, etc.
<nobr>`--import-noop-changes`</nobr> | *boolean* | By default Copybara will only try to migrate changes that could affect the destination. Ignoring changes that only affect excluded files in origin_files. This flag disables that behavior and runs for all the changes.
<nobr>`--init-history`</nobr> | *boolean* | Import all the changes from the beginning of the history up to the resolved ref. For 'ITERATIVE' workflows this will import individual changes since the first one. For 'SQUASH' it will import the squashed change up to the resolved ref. WARNING: Use with care, this flag should be used only for the very first run of Copybara for a workflow.
<nobr>`--iterative-limit-changes`</nobr> | *int* | Import just a number of changes instead of all the pending ones
<nobr>`--last-rev`</nobr> | *string* | Last revision that was migrated to the destination
<nobr>`--nosmart-prune`</nobr> | *boolean* | Disable smart prunning
<nobr>`--notransformation-join`</nobr> | *boolean* | By default Copybara tries to join certain transformations in one so that it is more efficient. This disables the feature.
<nobr>`--read-config-from-change`</nobr> | *boolean* | For each imported origin change, load the configuration from that change.
<nobr>`--squash-skip-history`</nobr> | *boolean* | Avoid exposing the history of changes that are being migrated. This is useful when we want to migrate a new repository but we don't want to expose all the change history to metadata.squash_notes.
<nobr>`--threads`</nobr> | *int* | Number of threads to use when running transformations that change lot of files
<nobr>`--threads-min-size`</nobr> | *int* | Minimum size of the lists to process to run them in parallel
<nobr>`--workflow-identity-user`</nobr> | *string* | Use a custom string as a user for computing change identity



## feedback.context

Gives access to the feedback migration information and utilities.


#### Fields:

Name | Description
---- | -----------
action_name | The name of the current action.
console | Get an instance of the console to report errors or warnings
destination | An object representing the destination. Can be used to query or modify the destination state
feedback_name | The name of the Feedback migration calling this action.
origin | An object representing the origin. Can be used to query about the ref or modifying the origin state
params | Parameters for the function if created with core.dynamic_feedback
ref | A string representation of the entity that triggered the event

<a id="feedback.context.error" aria-hidden="true"></a>
### feedback.context.error

Returns an error action result.

`feedback.action_result feedback.context.error(msg)`


#### Parameters:

Parameter | Description
--------- | -----------
msg | `string`<br><p>The error message</p>

<a id="feedback.context.noop" aria-hidden="true"></a>
### feedback.context.noop

Returns a no op action result with an optional message.

`feedback.action_result feedback.context.noop(msg=None)`


#### Parameters:

Parameter | Description
--------- | -----------
msg | `string`<br><p>The no op message</p>

<a id="feedback.context.record_effect" aria-hidden="true"></a>
### feedback.context.record_effect

Records an effect of the current action.

`feedback.context.record_effect(summary, origin_refs, destination_ref, errors=[])`


#### Parameters:

Parameter | Description
--------- | -----------
summary | `string`<br><p>The summary of this effect</p>
origin_refs | `sequence of origin_ref`<br><p>The origin refs</p>
destination_ref | `destination_ref`<br><p>The destination ref</p>
errors | `sequence of string`<br><p>An optional list of errors</p>

<a id="feedback.context.success" aria-hidden="true"></a>
### feedback.context.success

Returns a successful action result.

`feedback.action_result feedback.context.success()`



## feedback.finish_hook_context

Gives access to the feedback migration information and utilities.


#### Fields:

Name | Description
---- | -----------
console | Get an instance of the console to report errors or warnings
destination | An object representing the destination. Can be used to query or modify the destination state
effects | The list of effects that happened in the destination
origin | An object representing the origin. Can be used to query about the state
params | Parameters for the function if created with core.dynamic_feedback
revision | Get the requested/resolved revision



## feedback.revision_context

Information about the revision request/resolved for the migration


#### Fields:

Name | Description
---- | -----------
labels | A dictionary with the labels detected for the requested/resolved revision.



## folder

Module for dealing with local filesytem folders

<a id="folder.destination" aria-hidden="true"></a>
### folder.destination

A folder destination is a destination that puts the output in a folder. It can be used both for testing or real production migrations.Given that folder destination does not support a lot of the features of real VCS, there are some limitations on how to use it:<ul><li>It requires passing a ref as an argument, as there is no way of calculating previous migrated changes. Alternatively, --last-rev can be used, which could migrate N changes.<li>Most likely, the workflow should use 'SQUASH' mode, as history is not supported.<li>If 'ITERATIVE' mode is used, a new temp directory will be created for each change migrated.</ul>

`destination folder.destination()`



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<nobr>`--folder-dir`</nobr> | *string* | Local directory to write the output of the migration to. If the directory exists, all files will be deleted. By default Copybara will generate a temporary directory, so you shouldn't need this.

<a id="folder.origin" aria-hidden="true"></a>
### folder.origin

A folder origin is a origin that uses a folder as input

`folderOrigin folder.origin(materialize_outside_symlinks=False)`


#### Parameters:

Parameter | Description
--------- | -----------
materialize_outside_symlinks | `boolean`<br><p>By default folder.origin will refuse any symlink in the migration folder that is an absolute symlink or that refers to a file outside of the folder. If this flag is set, it will materialize those symlinks as regular files in the checkout directory.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<nobr>`--folder-origin-author`</nobr> | *string* | Author of the change being migrated from folder.origin()
<nobr>`--folder-origin-message`</nobr> | *string* | Message of the change being migrated from folder.origin()



## gerritapi.AccountInfo

Gerrit account information.


#### Fields:

Name | Description
---- | -----------
account_id | The numeric ID of the account.
email | The email address the user prefers to be contacted through.
Only set if detailed account information is requested.
See option DETAILED_ACCOUNTS for change queries
and options DETAILS and ALL_EMAILS for account queries.
name | The full name of the user.
Only set if detailed account information is requested.
See option DETAILED_ACCOUNTS for change queries
and option DETAILS for account queries.
secondary_emails | A list of the secondary email addresses of the user.
Only set for account queries when the ALL_EMAILS option or the suggest parameter is set.
Secondary emails are only included if the calling user has the Modify Account, and hence is allowed to see secondary emails of other users.
username | The username of the user.
Only set if detailed account information is requested.
See option DETAILED_ACCOUNTS for change queries
and option DETAILS for account queries.



## gerritapi.ApprovalInfo

Gerrit approval information.


#### Fields:

Name | Description
---- | -----------
account_id | The numeric ID of the account.
date | The time and date describing when the approval was made.
email | The email address the user prefers to be contacted through.
Only set if detailed account information is requested.
See option DETAILED_ACCOUNTS for change queries
and options DETAILS and ALL_EMAILS for account queries.
name | The full name of the user.
Only set if detailed account information is requested.
See option DETAILED_ACCOUNTS for change queries
and option DETAILS for account queries.
secondary_emails | A list of the secondary email addresses of the user.
Only set for account queries when the ALL_EMAILS option or the suggest parameter is set.
Secondary emails are only included if the calling user has the Modify Account, and hence is allowed to see secondary emails of other users.
username | The username of the user.
Only set if detailed account information is requested.
See option DETAILED_ACCOUNTS for change queries
and option DETAILS for account queries.
value | The vote that the user has given for the label. If present and zero, the user is permitted to vote on the label. If absent, the user is not permitted to vote on that label.



## gerritapi.ChangeInfo

Gerrit change information.


#### Fields:

Name | Description
---- | -----------
branch | The name of the target branch.
The refs/heads/ prefix is omitted.
change_id | The Change-Id of the change.
created | The timestamp of when the change was created.
current_revision | The commit ID of the current patch set of this change.
Only set if the current revision is requested or if all revisions are requested.
id | The ID of the change in the format "'<project>~<branch>~<Change-Id>'", where 'project', 'branch' and 'Change-Id' are URL encoded. For 'branch' the refs/heads/ prefix is omitted.
labels | The labels of the change as a map that maps the label names to LabelInfo entries.
Only set if labels or detailed labels are requested.
messages | Messages associated with the change as a list of ChangeMessageInfo entities.
Only set if messages are requested.
number | The legacy numeric ID of the change.
owner | The owner of the change as an AccountInfo entity.
project | The name of the project.
revisions | All patch sets of this change as a map that maps the commit ID of the patch set to a RevisionInfo entity.
Only set if the current revision is requested (in which case it will only contain a key for the current revision) or if all revisions are requested.
status | The status of the change (NEW, MERGED, ABANDONED).
subject | The subject of the change (header line of the commit message).
submitted | The timestamp of when the change was submitted.
topic | The topic to which this change belongs.
updated | The timestamp of when the change was last updated.



## gerritapi.ChangeMessageInfo

Gerrit change message information.


#### Fields:

Name | Description
---- | -----------
author | Author of the message as an AccountInfo entity.
Unset if written by the Gerrit system.
date | The timestamp of when this identity was constructed.
id | The ID of the message.
message | The text left by the user.
real_author | Real author of the message as an AccountInfo entity.
Set if the message was posted on behalf of another user.
revision_number | Which patchset (if any) generated this message.
tag | Value of the tag field from ReviewInput set while posting the review. NOTE: To apply different tags on on different votes/comments multiple invocations of the REST call are required.



## gerritapi.ChangesQuery

Input for listing Gerrit changes. See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#list-changes



## gerritapi.CommitInfo

Gerrit commit information.


#### Fields:

Name | Description
---- | -----------
author | The author of the commit as a GitPersonInfo entity.
commit | The commit ID. Not set if included in a RevisionInfo entity that is contained in a map which has the commit ID as key.
committer | The committer of the commit as a GitPersonInfo entity.
message | The commit message.
parents | The parent commits of this commit as a list of CommitInfo entities. In each parent only the commit and subject fields are populated.
subject | The subject of the commit (header line of the commit message).



## gerritapi.GitPersonInfo

Git person information.


#### Fields:

Name | Description
---- | -----------
date | The timestamp of when this identity was constructed.
email | The email address of the author/committer.
name | The name of the author/committer.



## gerritapi.LabelInfo

Gerrit label information.


#### Fields:

Name | Description
---- | -----------
all | List of all approvals for this label as a list of ApprovalInfo entities. Items in this list may not represent actual votes cast by users; if a user votes on any label, a corresponding ApprovalInfo will appear in this list for all labels.
approved | One user who approved this label on the change (voted the maximum value) as an AccountInfo entity.
blocking | If true, the label blocks submit operation. If not set, the default is false.
default_value | The default voting value for the label. This value may be outside the range specified in permitted_labels.
disliked | One user who disliked this label on the change (voted negatively, but not the minimum value) as an AccountInfo entity.
recommended | One user who recommended this label on the change (voted positively, but not the maximum value) as an AccountInfo entity.
rejected | One user who rejected this label on the change (voted the minimum value) as an AccountInfo entity.
value | The voting value of the user who recommended/disliked this label on the change if it is not “+1”/“-1”.
values | A map of all values that are allowed for this label. The map maps the values (“-2”, “-1”, " `0`", “+1”, “+2”) to the value descriptions.



## gerritapi.ParentCommitInfo

Gerrit parent commit information.


#### Fields:

Name | Description
---- | -----------
commit | The commit ID. Not set if included in a RevisionInfo entity that is contained in a map which has the commit ID as key.
subject | The subject of the commit (header line of the commit message).



## gerritapi.ReviewResult

Gerrit review result.


#### Fields:

Name | Description
---- | -----------
labels | Map of labels to values after the review was posted.
ready | If true, the change was moved from WIP to ready for review as a result of this action. Not set if false.



## gerritapi.RevisionInfo

Gerrit revision information.


#### Fields:

Name | Description
---- | -----------
commit | The commit of the patch set as CommitInfo entity.
created | The timestamp of when the patch set was created.
kind | The change kind. Valid values are REWORK, TRIVIAL_REBASE, MERGE_FIRST_PARENT_UPDATE, NO_CODE_CHANGE, and NO_CHANGE.
patchset_number | The patch set number, or edit if the patch set is an edit.
ref | The Git reference for the patch set.
uploader | The uploader of the patch set as an AccountInfo entity.



## gerrit_api_obj

[EXPERIMENTAL] Gerrit API endpoint implementation for feedback migrations and after migration hooks.


#### Fields:

Name | Description
---- | -----------
url | Return the URL of this endpoint.

<a id="gerrit_api_obj.get_change" aria-hidden="true"></a>
### gerrit_api_obj.get_change

Retrieve a Gerrit change.

`gerritapi.ChangeInfo gerrit_api_obj.get_change(id, include_results=['LABELS'])`


#### Parameters:

Parameter | Description
--------- | -----------
id | `string`<br><p>The change id or change number.</p>
include_results | `sequence of string`<br><p>What to include in the response. See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#query-options</p>

<a id="gerrit_api_obj.list_changes_by_commit" aria-hidden="true"></a>
### gerrit_api_obj.list_changes_by_commit

Get changes from Gerrit based on a query. See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#list-changes.


`sequence of gerritapi.ChangeInfo gerrit_api_obj.list_changes_by_commit(commit, include_results=[])`


#### Parameters:

Parameter | Description
--------- | -----------
commit | `string`<br><p>The commit sha to list changes by. See https://gerrit-review.googlesource.com/Documentation/user-search.html#_basic_change_search.</p>
include_results | `sequence of string`<br><p>What to include in the response. See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#query-options</p>

<a id="gerrit_api_obj.post_review" aria-hidden="true"></a>
### gerrit_api_obj.post_review

Post a label to a Gerrit change for a particular revision. The label will be authored by the user running the tool, or the role account if running in the service.


`gerritapi.ReviewResult gerrit_api_obj.post_review(change_id, revision_id, review_input)`


#### Parameters:

Parameter | Description
--------- | -----------
change_id | `string`<br><p>The Gerrit change id.</p>
revision_id | `string`<br><p>The revision for which the comment will be posted.</p>
review_input | `SetReviewInput`<br><p>The review to post to Gerrit.</p>



## git

Set of functions to define Git origins and destinations.



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<nobr>`--git-credential-helper-store-file`</nobr> | *string* | Credentials store file to be used. See https://git-scm.com/docs/git-credential-store
<nobr>`--nogit-credential-helper-store`</nobr> | *boolean* | Disable using credentials store. See https://git-scm.com/docs/git-credential-store

<a id="git.destination" aria-hidden="true"></a>
### git.destination

Creates a commit in a git repository using the transformed worktree.<br><br>Given that Copybara doesn't ask for user/password in the console when doing the push to remote repos, you have to use ssh protocol, have the credentials cached or use a credential manager.

`gitDestination git.destination(url, push='master', fetch=None, skip_push=False, integrates=None)`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Indicates the URL to push to as well as the URL from which to get the parent commit</p>
push | `string`<br><p>Reference to use for pushing the change, for example 'master'</p>
fetch | `string`<br><p>Indicates the ref from which to get the parent commit. Defaults to push value if None</p>
skip_push | `boolean`<br><p>If set, copybara will not actually push the result to the destination. This is meant for testing workflows and dry runs.</p>
integrates | `sequence of git_integrate`<br><p>Integrate changes from a url present in the migrated change label. Defaults to a semi-fake merge if COPYBARA_INTEGRATE_REVIEW label is present in the message</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<nobr>`--git-committer-email`</nobr> | *string* | If set, overrides the committer e-mail for the generated commits in git destination.
<nobr>`--git-committer-name`</nobr> | *string* | If set, overrides the committer name for the generated commits in git destination.
<nobr>`--git-destination-fetch`</nobr> | *string* | If set, overrides the git destination fetch reference.
<nobr>`--git-destination-ignore-integration-errors`</nobr> | *boolean* | If an integration error occurs, ignore it and continue without the integrate
<nobr>`--git-destination-last-rev-first-parent`</nobr> | *boolean* | Use git --first-parent flag when looking for last-rev in previous commits
<nobr>`--git-destination-non-fast-forward`</nobr> | *boolean* | Allow non-fast-forward pushes to the destination. We only allow this when used with different push != fetch references.
<nobr>`--git-destination-path`</nobr> | *string* | If set, the tool will use this directory for the local repository. Note that if the directory exists it needs to be a git repository. Copybara will revert any staged/unstaged changes.
<nobr>`--git-destination-push`</nobr> | *string* | If set, overrides the git destination push reference.
<nobr>`--git-destination-skip-push`</nobr> | *boolean* | If set, the tool will not push to the remote destination
<nobr>`--git-destination-url`</nobr> | *string* | If set, overrides the git destination URL.
<nobr>`--nogit-destination-rebase`</nobr> | *boolean* | Don't rebase the change automatically for workflows CHANGE_REQUEST mode

<a id="git.gerrit_api" aria-hidden="true"></a>
### git.gerrit_api

[EXPERIMENTAL] Defines a feedback API endpoint for Gerrit, that exposes relevant Gerrit API operations.

`gerrit_api_obj git.gerrit_api(url, checker=None)`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Indicates the Gerrit repo URL.</p>
checker | `checker`<br><p>A checker for the Gerrit API transport.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<nobr>`--gerrit-change-id`</nobr> | *string* | ChangeId to use in the generated commit message. Use this flag if you want to reuse the same Gerrit review for an export.
<nobr>`--gerrit-new-change`</nobr> | *boolean* | Create a new change instead of trying to reuse an existing one.
<nobr>`--gerrit-topic`</nobr> | *string* | Gerrit topic to use

<a id="git.gerrit_destination" aria-hidden="true"></a>
### git.gerrit_destination

Creates a change in Gerrit using the transformed worktree. If this is used in iterative mode, then each commit pushed in a single Copybara invocation will have the correct commit parent. The reviews generated can then be easily done in the correct order without rebasing.

`gerritDestination git.gerrit_destination(url, fetch, push_to_refs_for=fetch value, submit=False, change_id_policy='FAIL_IF_PRESENT', allow_empty_diff_patchset=True)`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Indicates the URL to push to as well as the URL from which to get the parent commit</p>
fetch | `string`<br><p>Indicates the ref from which to get the parent commit</p>
push_to_refs_for | `string`<br><p>Review branch to push the change to, for example setting this to 'feature_x' causes the destination to push to 'refs/for/feature_x'. It defaults to 'fetch' value.</p>
submit | `boolean`<br><p>If true, skip the push thru Gerrit refs/for/branch and directly push to branch. This is effectively a git.destination that sets a Change-Id</p>
change_id_policy | `string`<br><p>What to do in the presence or absent of Change-Id in message:<ul>  <li>`'REQUIRE'`: Require that the change_id is present in the message as a valid label</li>  <li>`'FAIL_IF_PRESENT'`: Fail if found in message</li>  <li>`'REUSE'`: Reuse if present. Otherwise generate a new one</li>  <li>`'REPLACE'`: Replace with a new one if found</li></ul></p>
allow_empty_diff_patchset | `boolean`<br><p>By default Copybara will upload a new PatchSet to Gerrit without checking the previous one. If this set to false, Copybara will download current PatchSet and check the diff against the new diff.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<nobr>`--git-committer-email`</nobr> | *string* | If set, overrides the committer e-mail for the generated commits in git destination.
<nobr>`--git-committer-name`</nobr> | *string* | If set, overrides the committer name for the generated commits in git destination.
<nobr>`--git-destination-fetch`</nobr> | *string* | If set, overrides the git destination fetch reference.
<nobr>`--git-destination-ignore-integration-errors`</nobr> | *boolean* | If an integration error occurs, ignore it and continue without the integrate
<nobr>`--git-destination-last-rev-first-parent`</nobr> | *boolean* | Use git --first-parent flag when looking for last-rev in previous commits
<nobr>`--git-destination-non-fast-forward`</nobr> | *boolean* | Allow non-fast-forward pushes to the destination. We only allow this when used with different push != fetch references.
<nobr>`--git-destination-path`</nobr> | *string* | If set, the tool will use this directory for the local repository. Note that if the directory exists it needs to be a git repository. Copybara will revert any staged/unstaged changes.
<nobr>`--git-destination-push`</nobr> | *string* | If set, overrides the git destination push reference.
<nobr>`--git-destination-skip-push`</nobr> | *boolean* | If set, the tool will not push to the remote destination
<nobr>`--git-destination-url`</nobr> | *string* | If set, overrides the git destination URL.
<nobr>`--nogit-destination-rebase`</nobr> | *boolean* | Don't rebase the change automatically for workflows CHANGE_REQUEST mode

<a id="git.gerrit_origin" aria-hidden="true"></a>
### git.gerrit_origin

Defines a Git origin for Gerrit reviews.

Implicit labels that can be used/exposed:

  - GERRIT_CHANGE_NUMBER: The change number for the Gerrit review.
  - GERRIT_CHANGE_ID: The change id for the Gerrit review.
  - GERRIT_CHANGE_DESCRIPTION: The description of the Gerrit review.
  - COPYBARA_INTEGRATE_REVIEW: A label that when exposed, can be used to integrate automatically in the reverse workflow.


`gitOrigin git.gerrit_origin(url, ref=None, submodules='NO', first_parent=True)`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Indicates the URL of the git repository</p>
ref | `string`<br><p>DEPRECATED. Use git.origin for submitted branches.</p>
submodules | `string`<br><p>Download submodules. Valid values: NO, YES, RECURSIVE.</p>
first_parent | `boolean`<br><p>If true, it only uses the first parent when looking for changes. Note that when disabled in ITERATIVE mode, it will try to do a migration for each change of the merged branch.</p>

<a id="git.gerrit_trigger" aria-hidden="true"></a>
### git.gerrit_trigger

[EXPERIMENTAL] Defines a feedback trigger based on updates on a Gerrit change.

`gerritTrigger git.gerrit_trigger(url, checker=None)`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Indicates the Gerrit repo URL.</p>
checker | `checker`<br><p>A checker for the Gerrit API transport provided by this trigger.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<nobr>`--gerrit-change-id`</nobr> | *string* | ChangeId to use in the generated commit message. Use this flag if you want to reuse the same Gerrit review for an export.
<nobr>`--gerrit-new-change`</nobr> | *boolean* | Create a new change instead of trying to reuse an existing one.
<nobr>`--gerrit-topic`</nobr> | *string* | Gerrit topic to use

<a id="git.github_api" aria-hidden="true"></a>
### git.github_api

[EXPERIMENTAL] Defines a feedback API endpoint for GitHub, that exposes relevant GitHub API operations.

`github_api_obj git.github_api(url, checker=None)`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Indicates the GitHub repo URL.</p>
checker | `checker`<br><p>A checker for the GitHub API transport.</p>

<a id="git.github_origin" aria-hidden="true"></a>
### git.github_origin

Defines a Git origin for a Github repository. This origin should be used for public branches. Use github_pr_origin for importing Pull Requests.

`gitOrigin git.github_origin(url, ref=None, submodules='NO', first_parent=True)`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Indicates the URL of the git repository</p>
ref | `string`<br><p>Represents the default reference that will be used for reading the revision from the git repository. For example: 'master'</p>
submodules | `string`<br><p>Download submodules. Valid values: NO, YES, RECURSIVE.</p>
first_parent | `boolean`<br><p>If true, it only uses the first parent when looking for changes. Note that when disabled in ITERATIVE mode, it will try to do a migration for each change of the merged branch.</p>

<a id="git.github_pr_destination" aria-hidden="true"></a>
### git.github_pr_destination

Creates changes in a new pull request in the destination.

`gitHubPrDestination git.github_pr_destination(url, destination_ref="master", skip_push=False, title=None, body=None, integrates=None)`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Url of the GitHub project. For example "https://github.com/google/copybara'"</p>
destination_ref | `string`<br><p>Destination reference for the change. By default 'master'</p>
skip_push | `boolean`<br><p>If set, copybara will not actually push the result to the destination. This is meant for testing workflows and dry runs.</p>
title | `string`<br><p>When creating a pull request, use this title. By default it uses the change first line.</p>
body | `string`<br><p>When creating a pull request, use this body. By default it uses the change summary.</p>
integrates | `sequence of git_integrate`<br><p>Integrate changes from a url present in the migrated change label. Defaults to a semi-fake merge if COPYBARA_INTEGRATE_REVIEW label is present in the message</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<nobr>`--git-committer-email`</nobr> | *string* | If set, overrides the committer e-mail for the generated commits in git destination.
<nobr>`--git-committer-name`</nobr> | *string* | If set, overrides the committer name for the generated commits in git destination.
<nobr>`--git-destination-fetch`</nobr> | *string* | If set, overrides the git destination fetch reference.
<nobr>`--git-destination-ignore-integration-errors`</nobr> | *boolean* | If an integration error occurs, ignore it and continue without the integrate
<nobr>`--git-destination-last-rev-first-parent`</nobr> | *boolean* | Use git --first-parent flag when looking for last-rev in previous commits
<nobr>`--git-destination-non-fast-forward`</nobr> | *boolean* | Allow non-fast-forward pushes to the destination. We only allow this when used with different push != fetch references.
<nobr>`--git-destination-path`</nobr> | *string* | If set, the tool will use this directory for the local repository. Note that if the directory exists it needs to be a git repository. Copybara will revert any staged/unstaged changes.
<nobr>`--git-destination-push`</nobr> | *string* | If set, overrides the git destination push reference.
<nobr>`--git-destination-skip-push`</nobr> | *boolean* | If set, the tool will not push to the remote destination
<nobr>`--git-destination-url`</nobr> | *string* | If set, overrides the git destination URL.
<nobr>`--github-destination-pr-branch`</nobr> | *string* | If set, uses this branch for creating the pull request instead of using a generated one
<nobr>`--github-destination-pr-create`</nobr> | *boolean* | If the pull request should be created
<nobr>`--nogit-destination-rebase`</nobr> | *boolean* | Don't rebase the change automatically for workflows CHANGE_REQUEST mode

<a id="git.github_pr_origin" aria-hidden="true"></a>
### git.github_pr_origin

Defines a Git origin for Github pull requests.

Implicit labels that can be used/exposed:

  - GITHUB_PR_NUMBER: The pull request number if the reference passed was in the form of `https://github.com/project/pull/123`,  `refs/pull/123/head` or `refs/pull/123/master`.
  - COPYBARA_INTEGRATE_REVIEW: A label that when exposed, can be used to integrate automatically in the reverse workflow.
  - GITHUB_BASE_BRANCH: The base branch name used for the Pull Request.
  - GITHUB_BASE_BRANCH_SHA1: The base branch SHA-1 used as baseline.
  - GITHUB_PR_TITLE: Title of the Pull Request.
  - GITHUB_PR_BODY: Body of the Pull Request.
  - GITHUB_PR_HEAD_SHA: The SHA-1 of the head commit of the pull request.
  - GITHUB_PR_USER: The login of the author the pull request.
  - GITHUB_PR_ASSIGNEE: A repeated label with the login of the assigned users.
  - GITHUB_PR_REVIEWER_APPROVER: A repeated label with the login of users that have participated in the review and that can approve the import. Only populated if `review_state` field is set. Every reviewers type matching `review_approvers` will be added to this list.
  - GITHUB_PR_REVIEWER_OTHER: A repeated label with the login of users that have participated in the review but cannot approve the import. Only populated if `review_state` field is set.


`gitHubPROrigin git.github_pr_origin(url, use_merge=False, required_labels=[], retryable_labels=[], submodules='NO', baseline_from_branch=False, first_parent=True, state='OPEN', review_state=None, review_approvers=["COLLABORATOR", "MEMBER", "OWNER"], api_checker=None)`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Indicates the URL of the GitHub repository</p>
use_merge | `boolean`<br><p>If the content for refs/pull/<ID>/merge should be used instead of the PR head. The GitOrigin-RevId still will be the one from refs/pull/<ID>/head revision.</p>
required_labels | `sequence of string`<br><p>Required labels to import the PR. All the labels need to be present in order to migrate the Pull Request.</p>
retryable_labels | `sequence of string`<br><p>Required labels to import the PR that should be retried. This parameter must be a subset of required_labels.</p>
submodules | `string`<br><p>Download submodules. Valid values: NO, YES, RECURSIVE.</p>
baseline_from_branch | `boolean`<br><p>WARNING: Use this field only for github -> git CHANGE_REQUEST workflows.<br>When the field is set to true for CHANGE_REQUEST workflows it will find the baseline comparing the Pull Request with the base branch instead of looking for the *-RevId label in the commit message.</p>
first_parent | `boolean`<br><p>If true, it only uses the first parent when looking for changes. Note that when disabled in ITERATIVE mode, it will try to do a migration for each change of the merged branch.</p>
state | `string`<br><p>Only migrate Pull Request with that state. Possible values: `'OPEN'`, `'CLOSED'` or `'ALL'`. Default 'OPEN'</p>
review_state | `string`<br><p>Required state of the reviews associated with the Pull Request Possible values: `'HEAD_COMMIT_APPROVED'`, `'ANY_COMMIT_APPROVED'`, `'HAS_REVIEWERS'` or `'ANY'`. Default `None`. This field is required if the user wants `GITHUB_PR_REVIEWER_APPROVER` and `GITHUB_PR_REVIEWER_OTHER` labels populated</p>
review_approvers | `sequence of string`<br><p>The set of reviewer types that are considered for approvals. In order to have any effect, `review_state` needs to be set. GITHUB_PR_REVIEWER_APPROVER` will be populated for these types. See the valid types here: https://developer.github.com/v4/reference/enum/commentauthorassociation/</p>
api_checker | `checker`<br><p>A checker for the GitHub API endpoint provided for after_migration hooks. This field is not used if the workflow doesn't have hooks.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<nobr>`--github-required-label`</nobr> | *string>* | Required labels in the Pull Request to be imported by github_pr_origin
<nobr>`--github-retryable-label`</nobr> | *string>* | Required labels in the Pull Request that should be retryed to be imported by github_pr_origin
<nobr>`--github-skip-required-labels`</nobr> | *boolean* | Skip checking labels for importing Pull Requests. Note that this is dangerous as it might import an unsafe PR.

<a id="git.integrate" aria-hidden="true"></a>
### git.integrate

Integrate changes from a url present in the migrated change label.

`git_integrate git.integrate(label="COPYBARA_INTEGRATE_REVIEW", strategy="FAKE_MERGE_AND_INCLUDE_FILES", ignore_errors=True)`


#### Parameters:

Parameter | Description
--------- | -----------
label | `string`<br><p>The migration label that will contain the url to the change to integrate.</p>
strategy | `string`<br><p>How to integrate the change:<br><ul> <li><b>'FAKE_MERGE'</b>: Add the url revision/reference as parent of the migration change but ignore all the files from the url. The commit message will be a standard merge one but will include the corresponding RevId label</li> <li><b>'FAKE_MERGE_AND_INCLUDE_FILES'</b>: Same as 'FAKE_MERGE' but any change to files that doesn't match destination_files will be included as part of the merge commit. So it will be a semi fake merge: Fake for destination_files but merge for non destination files.</li> <li><b>'INCLUDE_FILES'</b>: Same as 'FAKE_MERGE_AND_INCLUDE_FILES' but it it doesn't create a merge but only include changes not matching destination_files</li></ul></p>
ignore_errors | `boolean`<br><p>If we should ignore integrate errors and continue the migration without the integrate</p>


#### Example:


##### Integrate changes from a review url:

Assuming we have a git.destination defined like this:

```python
git.destination(
        url = "https://example.com/some_git_repo",
        integrates = [git.integrate()],

)
```

It will look for `COPYBARA_INTEGRATE_REVIEW` label during the worklow migration. If the label is found, it will fetch the git url and add that change as an additional parent to the migration commit (merge). It will fake-merge any change from the url that matches destination_files but it will include changes not matching it.


<a id="git.mirror" aria-hidden="true"></a>
### git.mirror

Mirror git references between repositories

`git.mirror(name, origin, destination, refspecs=['refs/heads/*'], prune=False)`


#### Parameters:

Parameter | Description
--------- | -----------
name | `string`<br><p>Migration name</p>
origin | `string`<br><p>Indicates the URL of the origin git repository</p>
destination | `string`<br><p>Indicates the URL of the destination git repository</p>
refspecs | `sequence of string`<br><p>Represents a list of git refspecs to mirror between origin and destination.For example 'refs/heads/*:refs/remotes/origin/*' will mirror any referenceinside refs/heads to refs/remotes/origin.</p>
prune | `boolean`<br><p>Remove remote refs that don't have a origin counterpart</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<nobr>`--git-mirror-force`</nobr> | *boolean* | Force push even if it is not fast-forward

<a id="git.origin" aria-hidden="true"></a>
### git.origin

Defines a standard Git origin. For Git specific origins use: `github_origin` or `gerrit_origin`.<br><br>All the origins in this module accept several string formats as reference (When copybara is called in the form of `copybara config workflow reference`):<br><ul><li>**Branch name:** For example `master`</li><li>**An arbitrary reference:** `refs/changes/20/50820/1`</li><li>**A SHA-1:** Note that it has to be reachable from the default refspec</li><li>**A Git repository URL and reference:** `http://github.com/foo master`</li><li>**A GitHub pull request URL:** `https://github.com/some_project/pull/1784`</li></ul><br>So for example, Copybara can be invoked for a `git.origin` in the CLI as:<br>`copybara copy.bara.sky my_workflow https://github.com/some_project/pull/1784`<br>This will use the pull request as the origin URL and reference.

`gitOrigin git.origin(url, ref=None, submodules='NO', include_branch_commit_logs=False, first_parent=True)`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Indicates the URL of the git repository</p>
ref | `string`<br><p>Represents the default reference that will be used for reading the revision from the git repository. For example: 'master'</p>
submodules | `string`<br><p>Download submodules. Valid values: NO, YES, RECURSIVE.</p>
include_branch_commit_logs | `boolean`<br><p>Whether to include raw logs of branch commits in the migrated change message.WARNING: This field is deprecated in favor of 'first_parent' one. This setting *only* affects merge commits.</p>
first_parent | `boolean`<br><p>If true, it only uses the first parent when looking for changes. Note that when disabled in ITERATIVE mode, it will try to do a migration for each change of the merged branch.</p>

<a id="git.review_input" aria-hidden="true"></a>
### git.review_input

Creates a review to be posted on Gerrit.

`SetReviewInput git.review_input(labels={}, message=None)`


#### Parameters:

Parameter | Description
--------- | -----------
labels | `dict`<br><p>The labels to post.</p>
message | `string`<br><p>The message to be added as review comment.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<nobr>`--gerrit-change-id`</nobr> | *string* | ChangeId to use in the generated commit message. Use this flag if you want to reuse the same Gerrit review for an export.
<nobr>`--gerrit-new-change`</nobr> | *boolean* | Create a new change instead of trying to reuse an existing one.
<nobr>`--gerrit-topic`</nobr> | *string* | Gerrit topic to use



## github_api_obj

[EXPERIMENTAL] GitHub API endpoint implementation for feedback migrations and after migration hooks.


#### Fields:

Name | Description
---- | -----------
url | Return the URL of this endpoint.

<a id="github_api_obj.create_status" aria-hidden="true"></a>
### github_api_obj.create_status

Create or update a status for a commit. Returns the status created.

`github_api_status_obj github_api_obj.create_status(sha, state, context, description, target_url=None)`


#### Parameters:

Parameter | Description
--------- | -----------
sha | `string`<br><p>The SHA-1 for which we want to create or update the status</p>
state | `string`<br><p>The state of the commit status: 'success', 'error', 'pending' or 'failure'</p>
context | `string`<br><p>The context for the commit status. Use a value like 'copybara/import_successful' or similar</p>
description | `string`<br><p>Description about what happened</p>
target_url | `string`<br><p>Url with expanded information about the event</p>

<a id="github_api_obj.get_reference" aria-hidden="true"></a>
### github_api_obj.get_reference

Get a reference SHA-1 from GitHub

`github_api_ref_obj github_api_obj.get_reference(ref)`


#### Parameters:

Parameter | Description
--------- | -----------
ref | `string`<br><p>The name of the reference</p>

<a id="github_api_obj.get_references" aria-hidden="true"></a>
### github_api_obj.get_references

Get all the reference SHA-1s from GitHub. Note that Copybara only returns a maximum number of 500.

`sequence of github_api_ref_obj github_api_obj.get_references()`

<a id="github_api_obj.update_reference" aria-hidden="true"></a>
### github_api_obj.update_reference

Update a reference to point to a new commit. Returns the info of the reference.

`github_api_ref_obj github_api_obj.update_reference(ref, sha, force)`


#### Parameters:

Parameter | Description
--------- | -----------
ref | `string`<br><p>The name of the reference.</p>
sha | `string`<br><p>The id for the commit status.</p>
force | `boolean`<br><p>Indicates whether to force the update or to make sure the update is a fast-forward update. Leaving this out or setting it to false will make sure you're not overwriting work. Default: false</p>



## Globals

Global functions available in Copybara

<a id="glob" aria-hidden="true"></a>
### glob

Glob returns a list of every file in the workdir that matches at least one pattern in include and does not match any of the patterns in exclude.

`glob glob(include, exclude=[])`


#### Parameters:

Parameter | Description
--------- | -----------
include | `sequence of string`<br><p>The list of glob patterns to include</p>
exclude | `sequence of string`<br><p>The list of glob patterns to exclude</p>


#### Examples:


##### Simple usage:

Include all the files under a folder except for `internal` folder files:

```python
glob(["foo/**"], exclude = ["foo/internal/**"])
```


##### Multiple folders:

Globs can have multiple inclusive rules:

```python
glob(["foo/**", "bar/**", "baz/**.java"])
```

This will include all files inside `foo` and `bar` folders and Java files inside `baz` folder.


##### Multiple excludes:

Globs can have multiple exclusive rules:

```python
glob(["foo/**"], exclude = ["foo/internal/**", "foo/confidential/**" ])
```

Include all the files of `foo` except the ones in `internal` and `confidential` folders


##### All BUILD files recursively:

Copybara uses Java globbing. The globbing is very similar to Bash one. This means that recursive globbing for a filename is a bit more tricky:

```python
glob(["BUILD", "**/BUILD"])
```

This is the correct way of matching all `BUILD` files recursively, including the one in the root. `**/BUILD` would only match `BUILD` files in subdirectories.


##### Matching multiple strings with one expression:

While two globs can be used for matching two directories, there is a more compact approach:

```python
glob(["{java,javatests}/**"])
```

This matches any file in `java` and `javatests` folders.


<a id="parse_message" aria-hidden="true"></a>
### parse_message

Returns a ChangeMessage parsed from a well formed string.

`ChangeMessage parse_message(message)`


#### Parameters:

Parameter | Description
--------- | -----------
message | `string`<br><p>The contents of the change message</p>



## metadata

Core transformations for the change metadata

<a id="metadata.add_header" aria-hidden="true"></a>
### metadata.add_header

Adds a header line to the commit message. Any variable present in the message in the form of ${LABEL_NAME} will be replaced by the corresponding label in the message. Note that this requires that the label is already in the message or in any of the changes being imported. The label in the message takes priority over the ones in the list of original messages of changes imported.


`transformation metadata.add_header(text, ignore_label_not_found=False, new_line=True)`


#### Parameters:

Parameter | Description
--------- | -----------
text | `string`<br><p>The header text to include in the message. For example '[Import of foo ${LABEL}]'. This would construct a message resolving ${LABEL} to the corresponding label.</p>
ignore_label_not_found | `boolean`<br><p>If a label used in the template is not found, ignore the error and don't add the header. By default it will stop the migration and fail.</p>
new_line | `boolean`<br><p>If a new line should be added between the header and the original message. This allows to create messages like `HEADER: ORIGINAL_MESSAGE`</p>


#### Examples:


##### Add a header always:

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




##### Add a header that uses a label:

Adds a header to messages that contain a label. Otherwise it skips the message manipulation.

```python
metadata.add_header("COPYBARA CHANGE FOR https://github.com/myproject/foo/pull/${GITHUB_PR_NUMBER}",
    ignore_label_not_found = True,
)
```

A change message, imported using git.github_pr_origin, like:

```
A change

Example description for
documentation

```

Will be transformed into:

```
COPYBARA CHANGE FOR https://github.com/myproject/foo/pull/1234
Example description for
documentation

GIT_URL=http://foo.com/1234
```

Assuming the PR number is 1234. But any change without that label will not be transformed.


##### Add a header without new line:

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




<a id="metadata.expose_label" aria-hidden="true"></a>
### metadata.expose_label

Certain labels are present in the internal metadata but are not exposed in the message by default. This transformations find a label in the internal metadata and exposes it in the message. If the label is already present in the message it will update it to use the new name and separator.

`transformation metadata.expose_label(name, new_name=label, separator="=", ignore_label_not_found=True, all=False)`


#### Parameters:

Parameter | Description
--------- | -----------
name | `string`<br><p>The label to search</p>
new_name | `string`<br><p>The name to use in the message</p>
separator | `string`<br><p>The separator to use when adding the label to the message</p>
ignore_label_not_found | `boolean`<br><p>If a label is not found, ignore the error and continue.</p>
all | `boolean`<br><p>By default Copybara tries to find the most relevant instance of the label. First looking into the message and then looking into the changes in order. If this field is true it exposes all the matches instead.</p>


#### Examples:


##### Simple usage:

Expose a hidden label called 'REVIEW_URL':

```python
metadata.expose_label('REVIEW_URL')
```

This would add it as `REVIEW_URL=the_value`.


##### New label name:

Expose a hidden label called 'REVIEW_URL' as GIT_REVIEW_URL:

```python
metadata.expose_label('REVIEW_URL', 'GIT_REVIEW_URL')
```

This would add it as `GIT_REVIEW_URL=the_value`.


##### Custom separator:

Expose the label with a custom separator

```python
metadata.expose_label('REVIEW_URL', separator = ': ')
```

This would add it as `REVIEW_URL: the_value`.


##### Expose multiple labels:

Expose all instances of a label in all the changes (SQUASH for example)

```python
metadata.expose_label('REVIEW_URL', all = True)
```

This would add 0 or more `REVIEW_URL: the_value` labels to the message.


<a id="metadata.map_author" aria-hidden="true"></a>
### metadata.map_author

Map the author name and mail to another author. The mapping can be done by both name and mail or only using any of the two.

`transformation metadata.map_author(authors, reversible=False, noop_reverse=False, fail_if_not_found=False, reverse_fail_if_not_found=False, map_all_changes=False)`


#### Parameters:

Parameter | Description
--------- | -----------
authors | `dict`<br><p>The author mapping. Keys can be in the form of 'Your Name', 'some@mail' or 'Your Name <some@mail>'. The mapping applies heuristics to know which field to use in the mapping. The value has to be always in the form of 'Your Name <some@mail>'</p>
reversible | `boolean`<br><p>If the transform is automatically reversible. Workflows using the reverse of this transform will be able to automatically map values to keys.</p>
noop_reverse | `boolean`<br><p>If true, the reversal of the transformation doesn't do anything. This is useful to avoid having to write `core.transformation(metadata.map_author(...), reversal = [])`.</p>
fail_if_not_found | `boolean`<br><p>Fail if a mapping cannot be found. Helps discovering early authors that should be in the map</p>
reverse_fail_if_not_found | `boolean`<br><p>Same as fail_if_not_found but when the transform is used in a inverse workflow.</p>
map_all_changes | `boolean`<br><p>If all changes being migrated should be mapped. Useful for getting a mapped metadata.squash_notes. By default we only map the current author.</p>


#### Example:


##### Map some names, emails and complete authors:

Here we show how to map authors using different options:

```python
metadata.map_author({
    'john' : 'Some Person <some@example.com>',
    'madeupexample@google.com' : 'Other Person <someone@example.com>',
    'John Example <john.example@example.com>' : 'Another Person <some@email.com>',
})
```


<a id="metadata.map_references" aria-hidden="true"></a>
### metadata.map_references

Allows updating links to references in commit messages to match the destination's format. Note that this will only consider the 5000 latest commits.

`referenceMigrator metadata.map_references(before, after, regex_groups={}, additional_import_labels=[])`


#### Parameters:

Parameter | Description
--------- | -----------
before | `string`<br><p>Template for origin references in the change message. Use a '${reference}' token to capture the actual references. E.g. if the origin uses linkslike 'http://changes?1234', the template would be 'http://internalReviews.com/${reference}', with reference_regex = '[0-9]+'</p>
after | `string`<br><p>Format for references in the destination, use the token '${reference}' to represent the destination reference. E.g. 'http://changes(${reference})'.</p>
regex_groups | `dict`<br><p>Regexes for the ${reference} token's content. Requires one 'before_ref' entry matching the ${reference} token's content on the before side. Optionally accepts one 'after_ref' used for validation. Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax.</p>
additional_import_labels | `sequence of string`<br><p>Meant to be used when migrating from another tool: Per default, copybara will only recognize the labels defined in the workflow's endpoints. The tool will use these additional labels to find labels created by other invocations and tools.</p>


#### Example:


##### Map references, origin source of truth:

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


<a id="metadata.remove_label" aria-hidden="true"></a>
### metadata.remove_label

Remove a label from the message

`transformation metadata.remove_label(name)`


#### Parameters:

Parameter | Description
--------- | -----------
name | `string`<br><p>The label name</p>


#### Example:


##### Remove a label:

Remove Change-Id label from the message:

```python
metadata.remove_label('Change-Id')
```


<a id="metadata.replace_message" aria-hidden="true"></a>
### metadata.replace_message

Replace the change message with a template text. Any variable present in the message in the form of ${LABEL_NAME} will be replaced by the corresponding label in the message. Note that this requires that the label is already in the message or in any of the changes being imported. The label in the message takes priority over the ones in the list of original messages of changes imported.


`transformation metadata.replace_message(text, ignore_label_not_found=False)`


#### Parameters:

Parameter | Description
--------- | -----------
text | `string`<br><p>The template text to use for the message. For example '[Import of foo ${LABEL}]'. This would construct a message resolving ${LABEL} to the corresponding label.</p>
ignore_label_not_found | `boolean`<br><p>If a label used in the template is not found, ignore the error and don't add the header. By default it will stop the migration and fail.</p>


#### Example:


##### Replace the message:

Replace the original message with a text:

```python
metadata.replace_message("COPYBARA CHANGE: Import of ${GITHUB_PR_NUMBER}\n\n${GITHUB_PR_BODY}\n")
```

Will transform the message to:

```
COPYBARA CHANGE: Import of 12345
Body from Github Pull Request
```




<a id="metadata.restore_author" aria-hidden="true"></a>
### metadata.restore_author

For a given change, restore the author present in the ORIGINAL_AUTHOR label as the author of the change.

`transformation metadata.restore_author(label='ORIGINAL_AUTHOR', search_all_changes=False)`


#### Parameters:

Parameter | Description
--------- | -----------
label | `string`<br><p>The label to use for restoring the author</p>
search_all_changes | `boolean`<br><p>By default Copybara only looks in the last current change for the author label. This allows to do the search in all current changes (Only makes sense for SQUASH/CHANGE_REQUEST).</p>

<a id="metadata.save_author" aria-hidden="true"></a>
### metadata.save_author

For a given change, store a copy of the author as a label with the name ORIGINAL_AUTHOR.

`transformation metadata.save_author(label='ORIGINAL_AUTHOR')`


#### Parameters:

Parameter | Description
--------- | -----------
label | `string`<br><p>The label to use for storing the author</p>

<a id="metadata.scrubber" aria-hidden="true"></a>
### metadata.scrubber

Removes part of the change message using a regex

`transformation metadata.scrubber(regex, replacement='')`


#### Parameters:

Parameter | Description
--------- | -----------
regex | `string`<br><p>Any text matching the regex will be removed. Note that the regex is runs in multiline mode.</p>
replacement | `string`<br><p>Text replacement for the matching substrings. References to regex group numbers can be used in the form of $1, $2, etc.</p>


#### Examples:


##### Remove from a keyword to the end of the message:

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




##### Keep only message enclosed in tags:

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




<a id="metadata.squash_notes" aria-hidden="true"></a>
### metadata.squash_notes

Generate a message that includes a constant prefix text and a list of changes included in the squash change.

`transformation metadata.squash_notes(prefix='Copybara import of the project:\n\n', max=100, compact=True, show_ref=True, show_author=True, show_description=True, oldest_first=False, use_merge=True)`


#### Parameters:

Parameter | Description
--------- | -----------
prefix | `string`<br><p>A prefix to be printed before the list of commits.</p>
max | `integer`<br><p>Max number of commits to include in the message. For the rest a comment like (and x more) will be included. By default 100 commits are included.</p>
compact | `boolean`<br><p>If compact is set, each change will be shown in just one line</p>
show_ref | `boolean`<br><p>If each change reference should be present in the notes</p>
show_author | `boolean`<br><p>If each change author should be present in the notes</p>
show_description | `boolean`<br><p>If each change description should be present in the notes</p>
oldest_first | `boolean`<br><p>If set to true, the list shows the oldest changes first. Otherwise it shows the changes in descending order.</p>
use_merge | `boolean`<br><p>If true then merge changes are included in the squash notes</p>


#### Examples:


##### Simple usage:

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



##### Removing authors and reversing the order:



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



##### Removing description:



```python
metadata.squash_notes("Changes for Project Foo:\n",
    show_description = False,
)
```

This transform will generate changes like:

```
Changes for Project Foo:

  - a4321bcde by Foo Bar <foo@bar.com>
  - 1234abcde by Foo Bar <foo@bar.com>
```



##### Showing the full message:



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



<a id="metadata.use_last_change" aria-hidden="true"></a>
### metadata.use_last_change

Use metadata (message or/and author) from the last change being migrated. Useful when using 'SQUASH' mode but user only cares about the last change.

`transformation metadata.use_last_change(author=True, message=True, default_message=None, use_merge=True)`


#### Parameters:

Parameter | Description
--------- | -----------
author | `boolean`<br><p>Replace author with the last change author (Could still be the default author if not whitelisted or using `authoring.overwrite`.</p>
message | `boolean`<br><p>Replace message with last change message.</p>
default_message | `string`<br><p>Replace message with last change message.</p>
use_merge | `boolean`<br><p>If true then merge changes are taken into account for looking for the last change.</p>

<a id="metadata.verify_match" aria-hidden="true"></a>
### metadata.verify_match

Verifies that a RegEx matches (or not matches) the change message. Does not transform anything, but will stop the workflow if it fails.

`transformation metadata.verify_match(regex, verify_no_match=False)`


#### Parameters:

Parameter | Description
--------- | -----------
regex | `string`<br><p>The regex pattern to verify. The re2j pattern will be applied in multiline mode, i.e. '^' refers to the beginning of a file and '$' to its end.</p>
verify_no_match | `boolean`<br><p>If true, the transformation will verify that the RegEx does not match.</p>


#### Example:


##### Check that a text is present in the change description:

Check that the change message contains a text enclosed in <public></public>:

```python
metadata.verify_match("<public>(.|\n)*</public>")
```




## patch

Module for applying patches.

<a id="patch.apply" aria-hidden="true"></a>
### patch.apply

A transformation that applies the given patch files. If a path does not exist in a patch, it will be ignored.

`patchTransformation patch.apply(patches=[], excluded_patch_paths=[], series=None)`


#### Parameters:

Parameter | Description
--------- | -----------
patches | `sequence of string`<br><p>The list of patchfiles to apply, relative to the current config file.The files will be applied relative to the checkout dir and the leading pathcomponent will be stripped (-p1).</p>
excluded_patch_paths | `sequence of string`<br><p>The list of paths to exclude from each of the patches. Each of the paths will be excluded from all the patches. Note that these are not workdir paths, but paths relative to the patch itself. If not empty, the patch will be applied using 'git apply' instead of GNU Patch.</p>
series | `string`<br><p>The config file that contains a list of patches to apply. The <i>series</i> file contains names of the patch files one per line. The names of the patch files are relative to the <i>series</i> config file. The files will be applied relative to the checkout dir and the leading path component will be stripped (-p1).</p>



## Path

Represents a path in the checkout directory


#### Fields:

Name | Description
---- | -----------
attr | Get the file attributes, for example size.
name | Filename of the path. For foo/bar/baz.txt it would be baz.txt
parent | Get the parent path
path | Full path relative to the checkout directory

<a id="path.read_symlink" aria-hidden="true"></a>
### path.read_symlink

Read the symlink

`Path path.read_symlink()`

<a id="path.relativize" aria-hidden="true"></a>
### path.relativize

Constructs a relative path between this path and a given path. For example:<br>    path('a/b').relativize('a/b/c/d')<br>returns 'c/d'

`Path path.relativize(other)`


#### Parameters:

Parameter | Description
--------- | -----------
other | `Path`<br><p>The path to relativize against this path</p>

<a id="path.resolve" aria-hidden="true"></a>
### path.resolve

Resolve the given path against this path.

`Path path.resolve(child)`


#### Parameters:

Parameter | Description
--------- | -----------
child | `object`<br><p>Resolve the given path against this path. The parameter can be a string or a Path.</p>

<a id="path.resolve_sibling" aria-hidden="true"></a>
### path.resolve_sibling

Resolve the given path against this path.

`Path path.resolve_sibling(other)`


#### Parameters:

Parameter | Description
--------- | -----------
other | `object`<br><p>Resolve the given path against this path. The parameter can be a string or a Path.</p>



## PathAttributes

Represents a path attributes like size.


#### Fields:

Name | Description
---- | -----------
size | The size of the file. Throws an error if file size > 2GB.
symlink | Returns true if it is a symlink



## SetReviewInput

Input for posting a review to Gerrit. See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#review-input



## TransformWork

Data about the set of changes that are being migrated. It includes information about changes like: the author to be used for commit, change message, etc. You receive a TransformWork object as an argument to the <code>transformations</code> functions used in <code>core.workflow</code>


#### Fields:

Name | Description
---- | -----------
author | Author to be used in the change
changes | List of changes that will be migrated
console | Get an instance of the console to report errors or warnings
message | Message to be used in the change
params | Parameters for the function if created with core.dynamic_transform

<a id="ctx.add_label" aria-hidden="true"></a>
### ctx.add_label

Add a label to the end of the description

`ctx.add_label(label, value, separator="=", hidden=False)`


#### Parameters:

Parameter | Description
--------- | -----------
label | `string`<br><p>The label to replace</p>
value | `string`<br><p>The new value for the label</p>
separator | `string`<br><p>The separator to use for the label</p>
hidden | `boolean`<br><p>Don't show the label in the message but only keep it internally</p>

<a id="ctx.add_or_replace_label" aria-hidden="true"></a>
### ctx.add_or_replace_label

Replace an existing label or add it to the end of the description

`ctx.add_or_replace_label(label, value, separator="=")`


#### Parameters:

Parameter | Description
--------- | -----------
label | `string`<br><p>The label to replace</p>
value | `string`<br><p>The new value for the label</p>
separator | `string`<br><p>The separator to use for the label</p>

<a id="ctx.add_text_before_labels" aria-hidden="true"></a>
### ctx.add_text_before_labels

Add a text to the description before the labels paragraph

`ctx.add_text_before_labels(text)`


#### Parameters:

Parameter | Description
--------- | -----------
text | `string`<br><p></p>

<a id="ctx.find_all_labels" aria-hidden="true"></a>
### ctx.find_all_labels

Tries to find all the values for a label. First it looks at the generated message (IOW labels that might have been added by previous steps), then looks in all the commit messages being imported and finally in the resolved reference passed in the CLI.

`sequence of string ctx.find_all_labels(message)`


#### Parameters:

Parameter | Description
--------- | -----------
message | `string`<br><p></p>

<a id="ctx.find_label" aria-hidden="true"></a>
### ctx.find_label

Tries to find a label. First it looks at the generated message (IOW labels that might have been added by previous steps), then looks in all the commit messages being imported and finally in the resolved reference passed in the CLI.

`string ctx.find_label(label)`


#### Parameters:

Parameter | Description
--------- | -----------
label | `string`<br><p></p>

<a id="ctx.new_path" aria-hidden="true"></a>
### ctx.new_path

Create a new path

`Path ctx.new_path(path)`


#### Parameters:

Parameter | Description
--------- | -----------
path | `string`<br><p>The string representing the path</p>

<a id="ctx.now_as_string" aria-hidden="true"></a>
### ctx.now_as_string

Get current date as a string

`string ctx.now_as_string(format="yyyy-MM-dd", zone="UTC")`


#### Parameters:

Parameter | Description
--------- | -----------
format | `string`<br><p>The format to use. See: https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html for details.</p>
zone | `object`<br><p>The timezone id to use. See https://docs.oracle.com/javase/8/docs/api/java/time/ZoneId.html. By default UTC </p>

<a id="ctx.read_path" aria-hidden="true"></a>
### ctx.read_path

Read the content of path as UTF-8

`string ctx.read_path(path)`


#### Parameters:

Parameter | Description
--------- | -----------
path | `Path`<br><p>The string representing the path</p>

<a id="ctx.remove_label" aria-hidden="true"></a>
### ctx.remove_label

Remove a label from the message if present

`ctx.remove_label(label, whole_message=False)`


#### Parameters:

Parameter | Description
--------- | -----------
label | `string`<br><p>The label to delete</p>
whole_message | `boolean`<br><p>By default Copybara only looks in the last paragraph for labels. This flagmake it replace labels in the whole message.</p>

<a id="ctx.replace_label" aria-hidden="true"></a>
### ctx.replace_label

Replace a label if it exist in the message

`ctx.replace_label(label, value, separator="=", whole_message=False)`


#### Parameters:

Parameter | Description
--------- | -----------
label | `string`<br><p>The label to replace</p>
value | `string`<br><p>The new value for the label</p>
separator | `string`<br><p>The separator to use for the label</p>
whole_message | `boolean`<br><p>By default Copybara only looks in the last paragraph for labels. This flagmake it replace labels in the whole message.</p>

<a id="ctx.run" aria-hidden="true"></a>
### ctx.run

Run a glob or a transform. For example:<br><code>files = ctx.run(glob(['**.java']))</code><br>or<br><code>ctx.run(core.move("foo", "bar"))</code><br>or<br>

`object ctx.run(runnable)`


#### Parameters:

Parameter | Description
--------- | -----------
runnable | `object`<br><p>A glob or a transform (Transforms still not implemented)</p>

<a id="ctx.set_author" aria-hidden="true"></a>
### ctx.set_author

Update the author to be used in the change

`ctx.set_author(author)`


#### Parameters:

Parameter | Description
--------- | -----------
author | `author`<br><p></p>

<a id="ctx.set_message" aria-hidden="true"></a>
### ctx.set_message

Update the message to be used in the change

`ctx.set_message(message)`


#### Parameters:

Parameter | Description
--------- | -----------
message | `string`<br><p></p>

<a id="ctx.write_path" aria-hidden="true"></a>
### ctx.write_path

Write an arbitrary string to a path (UTF-8 will be used)

`ctx.write_path(path, content)`


#### Parameters:

Parameter | Description
--------- | -----------
path | `Path`<br><p>The string representing the path</p>
content | `string`<br><p>The content of the file</p>


