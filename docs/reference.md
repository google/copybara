# Table of Contents


  - [Changes](#Changes)
  - [TransformWork](#TransformWork)
  - [author](#author)
  - [authoring](#authoring)
  - [authoring_class](#authoring_class)
  - [authoring_mode](#authoring_mode)
  - [change](#change)
  - [destination](#destination)
  - [origin](#origin)
  - [transformation](#transformation)
  - [metadata](#metadata)
  - [core](#core)
  - [folder](#folder)
  - [git](#git)


# Changes

Data about the set of changes that are being migrated. Each change includes information like: original author, change message, labels, etc. You receive this as a field in TransformWork object for used defined transformations


# TransformWork

Data about the set of changes that are being migrated. It includes information about changes like: the author to be used for commit, change message, etc. You receive a TransformWork object as an argument to the <code>transformations</code> functions used in <code>core.workflow</code>


# author

Represents the author of a change


# authoring

The authors mapping between an origin and a destination

## overwrite

Use the default author for all the submits in the destination.

`authoring_class authoring.overwrite(default)`

### Parameters:

Parameter | Description
--------- | -----------
default|`string`<br><p>The default author for commits in the destination</p>


## new_author

Create a new author from a string with the form 'name <foo@bar.com>'

`author new_author(author_string)`

### Parameters:

Parameter | Description
--------- | -----------
author_string|`string`<br><p>A string representation of the author with the form 'name <foo@bar.com>'</p>


## pass_thru

Use the origin author as the author in the destination, no whitelisting.

`authoring_class authoring.pass_thru(default)`

### Parameters:

Parameter | Description
--------- | -----------
default|`string`<br><p>The default author for commits in the destination. This is used in squash mode workflows</p>


## whitelisted

Create an individual or team that contributes code.

`authoring_class authoring.whitelisted(default, whitelist)`

### Parameters:

Parameter | Description
--------- | -----------
default|`string`<br><p>The default author for commits in the destination. This is used in squash mode workflows or when users are not whitelisted.</p>
whitelist|`sequence of string`<br><p>List of white listed authors in the origin. The authors must be unique</p>



# authoring_class

The authors mapping between an origin and a destination


# authoring_mode

Mode used for author mapping from origin to destination


# change

A change metadata. Contains information like author, change message or detected labels


# destination

A repository which a source of truth can be copied to


# origin

A Origin represents a source control repository from which source is copied.


# transformation

A transformation to the workdir


# metadata

Core transformations for the change metadata

## squash_notes

Generate a message that includes a constant prefix text and a list of changes included in the squash change.

`transformation metadata.squash_notes(prefix='Copybara import of the project:\n\n', max=100, compact=True, oldest_first=False)`

### Parameters:

Parameter | Description
--------- | -----------
prefix|`string`<br><p>A prefix to be printed before the list of commits.</p>
max|`integer`<br><p>Max number of commits to include in the message. For the rest a comment like (and x more) will be included. By default 100 commits are included.</p>
compact|`boolean`<br><p>If compact is set, each change will be shown in just one line</p>
oldest_first|`boolean`<br><p>If set to true, the list shows the oldest changes first. Otherwise it shows the changes in descending order.</p>


## save_author

For a given change, store a copy of the author as a label with the name ORIGINAL_AUTHOR.

`transformation metadata.save_author(label='ORIGINAL_AUTHOR')`

### Parameters:

Parameter | Description
--------- | -----------
label|`string`<br><p>The label to use for storing the author</p>


## restore_author

For a given change, restore the author present in the ORIGINAL_AUTHOR label as the author of the change.

`transformation metadata.restore_author(label='ORIGINAL_AUTHOR')`

### Parameters:

Parameter | Description
--------- | -----------
label|`string`<br><p>The label to use for restoring the author</p>



# core

Core functionality for creating workflows, and basic transformations.

## glob

Glob returns a list of every file in the workdir that matches at least one pattern in include and does not match any of the patterns in exclude.

`glob glob(include, exclude=[])`

### Parameters:

Parameter | Description
--------- | -----------
include|`sequence of string`<br><p>The list of glob patterns to include</p>
exclude|`sequence of string`<br><p>The list of glob patterns to exclude</p>


## project

General configuration of the project. Like the name.

`core.project(name)`

### Parameters:

Parameter | Description
--------- | -----------
name|`string`<br><p>The name of the configuration.</p>


## reverse

Given a list of transformations, returns the list of transformations equivalent to undoing all the transformations

`sequence core.reverse(transformations)`

### Parameters:

Parameter | Description
--------- | -----------
transformations|`sequence of transformation`<br><p>The transformations to reverse</p>


## workflow

Defines a migration pipeline which can be invoked via the Copybara command.

`core.workflow(name, origin, destination, authoring, transformations=[], exclude_in_origin=N/A, exclude_in_destination=N/A, origin_files=glob(['**']), destination_files=glob(['**']), mode="SQUASH", include_changelist_notes=False, reversible_check=True for CHANGE_REQUEST mode. False otherwise, ask_for_confirmation=False)`

### Parameters:

Parameter | Description
--------- | -----------
name|`string`<br><p>The name of the workflow.</p>
origin|`origin`<br><p>Where to read the migration code from.</p>
destination|`destination`<br><p>Where to read the migration code from.</p>
authoring|`authoring_class`<br><p>The author mapping configuration from origin to destination.</p>
transformations|`sequence`<br><p>Where to read the migration code from.</p>
exclude_in_origin|`glob`<br><p>For compatibility purposes only. Use origin_files instead.</p>
exclude_in_destination|`glob`<br><p>For compatibility purposes only. Use detination_files instead.</p>
origin_files|`glob`<br><p>A glob relative to the workdir that will be read from the origin during the import. For example glob(["**.java"]), all java files, recursively, which excludes all other file types.</p>
destination_files|`glob`<br><p>A glob relative to the root of the destination repository that matches files that are part of the migration. Files NOT matching this glob will never be removed, even if the file does not exist in the source. For example glob(['**'], exclude = ['**/BUILD']) keeps all BUILD files in destination when the origin does not have any BUILD files. You can also use this to limit the migration to a subdirectory of the destination, e.g. glob(['java/src/**'], exclude = ['**/BUILD']) to only affect non-BUILD files in java/src.</p>
mode|`string`<br><p>Workflow mode. Currently we support three modes:<br><ul><li><b>SQUASH</b>: Create a single commit in the destination with new tree state.</li><li><b>ITERATIVE</b>: Import each origin change individually.</li><li><b>CHANGE_REQUEST</b>: Import an origin tree state diffed by a common parent in destination. This could be a GH Pull Request, a Gerrit Change, etc.</li></ul></p>
include_changelist_notes|`boolean`<br><p>Include a list of change list messages that were imported.**DEPRECATED**: This method is about to be removed.</p>
reversible_check|`boolean`<br><p>Indicates if the tool should try to to reverse all the transformations at the end to check that they are reversible.<br/>The default value is True for CHANGE_REQUEST mode. False otherwise</p>
ask_for_confirmation|`boolean`<br><p>Indicates that the tool should show the diff and require user's confirmation before making a change in the destination.</p>




**Command line flags:**

Name | Type | Description
---- | ----------- | -----------
--change_request_parent | *string* | Commit reference to be used as parent when importing a commit using CHANGE_REQUEST workflow mode. this shouldn't be needed in general as Copybara is able to detect the parent commit message.
--last-rev | *string* | Last revision that was migrated to the destination
--ignore-noop | *boolean* | Only warn about operations/transforms that didn't have any effect. For example: A transform that didn't modify any file, non-existent origin directories, etc.

## move

Moves files between directories and renames files

`move core.move(before, after, paths=glob(["**"]))`

### Parameters:

Parameter | Description
--------- | -----------
before|`string`<br><p>The name of the file or directory before moving. If this is the empty string and 'after' is a directory, then all files in the workdir will be moved to the sub directory specified by 'after', maintaining the directory tree.</p>
after|`string`<br><p>The name of the file or directory after moving. If this is the empty string and 'before' is a directory, then all files in 'before' will be moved to the repo root, maintaining the directory tree inside 'before'.</p>
paths|`glob`<br><p>A glob expression relative to 'before' if it represents a directory. Only files matching the expression will be moved. For example, glob(["**.java"]), matches all java files recursively inside 'before' folder. Defaults to match all the files recursively.</p>


## replace

Replace a text with another text using optional regex groups. This tranformer can be automatically reversed.

`replace core.replace(before, after, regex_groups={}, paths=glob(["**"]), first_only=False, multiline=False)`

### Parameters:

Parameter | Description
--------- | -----------
before|`string`<br><p>The text before the transformation. Can contain references to regex groups. For example "foo${x}text".<p>If '$' literal character needs to be match '$$' should be used. For example '$$FOO' would match the literal '$FOO'.</p></p>
after|`string`<br><p>The name of the file or directory after moving. If this is the empty string and 'before' is a directory, then all files in 'before' will be moved to the repo root, maintaining the directory tree inside 'before'.</p>
regex_groups|`dict`<br><p>A set of named regexes that can be used to match part of the replaced text. For example {"x": "[A-Za-z]+"}</p>
paths|`glob`<br><p>A glob expression relative to the workdir representing the files to apply the transformation. For example, glob(["**.java"]), matches all java files recursively. Defaults to match all the files recursively.</p>
first_only|`boolean`<br><p>If true, only replaces the first instance rather than all. In single line mode, replaces the first instance on each line. In multiline mode, replaces the first instance in each file.</p>
multiline|`boolean`<br><p>Whether to replace text that spans more than one line.</p>


## transform

Creates a transformation with a particular, manually-specified, reversal, where the forward version and reversed version of the transform are represented as lists of transforms. The is useful if a transformation does not automatically reverse, or if the automatic reversal does not work for some reason.

`transformation core.transform(transformations, reversal)`

### Parameters:

Parameter | Description
--------- | -----------
transformations|`sequence of transformation`<br><p>The list of transformations to run as a result of running this transformation.</p>
reversal|`sequence of transformation`<br><p>The list of transformations to run as a result of running this transformation in reverse.</p>



# folder

Module for dealing with local filesytem folders

## destination

A folder destination is a destination that puts the output in a folder

`destination folder.destination()`



**Command line flags:**

Name | Type | Description
---- | ----------- | -----------
--folder-dir | *string* | Local directory to put the output of the transformation


# git

Set of functions to define Git origins and destinations.



**Command line flags:**

Name | Type | Description
---- | ----------- | -----------
--git-committer-name | *string* | If set, overrides the committer name for the generated commits.
--git-committer-email | *string* | If set, overrides the committer e-mail for the generated commits.
--git-repo-storage | *string* | Location of the storage path for git repositories
--git-first-commit | *boolean* | Ignore that the fetch reference doesn't exist when pushing to destination

## origin

Defines a standard Git origin. For Git specific origins use: github_origin or gerrit_origin.

`gitOrigin git.origin(url, ref=None)`

### Parameters:

Parameter | Description
--------- | -----------
url|`string`<br><p>Indicates the URL of the git repository</p>
ref|`string`<br><p>Represents the default reference that will be used for reading the revision from the git repository. For example: 'master'</p>


## gerrit_origin

Defines a Git origin of type Gerrit.

`gitOrigin git.gerrit_origin(url, ref=None)`

### Parameters:

Parameter | Description
--------- | -----------
url|`string`<br><p>Indicates the URL of the git repository</p>
ref|`string`<br><p>Represents the default reference that will be used for reading the revision from the git repository. For example: 'master'</p>


## github_origin

Defines a Git origin of type Github.

`gitOrigin git.github_origin(url, ref=None)`

### Parameters:

Parameter | Description
--------- | -----------
url|`string`<br><p>Indicates the URL of the git repository</p>
ref|`string`<br><p>Represents the default reference that will be used for reading the revision from the git repository. For example: 'master'</p>


## destination

Creates a commit in a git repository using the transformed worktree

`gitDestination git.destination(url, fetch, push)`

### Parameters:

Parameter | Description
--------- | -----------
url|`string`<br><p>Indicates the URL to push to as well as the URL from which to get the parent commit</p>
fetch|`string`<br><p>Indicates the ref from which to get the parent commit</p>
push|`string`<br><p>Reference to use for pushing the change, for example 'master'</p>


## gerrit_destination

Creates a change in Gerrit using the transformed worktree. If this is used in iterative mode, then each commit pushed in a single Copybara invocation will have the correct commit parent. The reviews generated can then be easily done in the correct order without rebasing.

`gerritDestination git.gerrit_destination(url, fetch, push_to_refs_for='')`

### Parameters:

Parameter | Description
--------- | -----------
url|`string`<br><p>Indicates the URL to push to as well as the URL from which to get the parent commit</p>
fetch|`string`<br><p>Indicates the ref from which to get the parent commit</p>
push_to_refs_for|`string`<br><p>Review branch to push the change to, for example setting this to 'feature_x' causes the destination to push to 'refs/for/feature_x'. It defaults to 'fetch' value.</p>



