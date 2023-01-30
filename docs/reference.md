<!-- Generated reference here -->
## Table of Contents


  - [author](#author)
  - [authoring](#authoring)
    - [authoring.allowed](#authoringallowed)
    - [authoring.overwrite](#authoringoverwrite)
    - [authoring.pass_thru](#authoringpass_thru)
  - [authoring_class](#authoring_class)
  - [buildozer](#buildozer)
    - [buildozer.cmd](#buildozercmd)
    - [buildozer.create](#buildozercreate)
    - [buildozer.delete](#buildozerdelete)
    - [buildozer.modify](#buildozermodify)
  - [change](#change)
  - [ChangeMessage](#changemessage)
    - [message.label_values](#messagelabel_values)
  - [Changes](#changes)
  - [Console](#console)
    - [console.error](#consoleerror)
    - [console.info](#consoleinfo)
    - [console.progress](#consoleprogress)
    - [console.verbose](#consoleverbose)
    - [console.warn](#consolewarn)
  - [core](#core)
    - [core.action](#coreaction)
    - [core.autopatch_config](#coreautopatch_config)
    - [core.copy](#corecopy)
    - [core.dynamic_feedback](#coredynamic_feedback)
    - [core.dynamic_transform](#coredynamic_transform)
    - [core.fail_with_noop](#corefail_with_noop)
    - [core.feedback](#corefeedback)
    - [core.filter_replace](#corefilter_replace)
    - [core.format](#coreformat)
    - [core.latest_version](#corelatest_version)
    - [core.move](#coremove)
    - [core.remove](#coreremove)
    - [core.replace](#corereplace)
    - [core.replace_mapper](#corereplace_mapper)
    - [core.reverse](#corereverse)
    - [core.todo_replace](#coretodo_replace)
    - [core.transform](#coretransform)
    - [core.verify_match](#coreverify_match)
    - [core.workflow](#coreworkflow)
  - [core.autopatch_config](#coreautopatch_config)
  - [destination_effect](#destination_effect)
  - [destination_reader](#destination_reader)
    - [destination_reader.copy_destination_files](#destination_readercopy_destination_files)
    - [destination_reader.file_exists](#destination_readerfile_exists)
    - [destination_reader.read_file](#destination_readerread_file)
  - [destination_ref](#destination_ref)
  - [endpoint](#endpoint)
    - [endpoint.new_destination_ref](#endpointnew_destination_ref)
    - [endpoint.new_origin_ref](#endpointnew_origin_ref)
  - [endpoint_provider](#endpoint_provider)
    - [endpoint_provider.new_destination_ref](#endpoint_providernew_destination_ref)
    - [endpoint_provider.new_origin_ref](#endpoint_providernew_origin_ref)
  - [feedback.context](#feedbackcontext)
    - [feedback.context.error](#feedbackcontexterror)
    - [feedback.context.noop](#feedbackcontextnoop)
    - [feedback.context.record_effect](#feedbackcontextrecord_effect)
    - [feedback.context.success](#feedbackcontextsuccess)
  - [feedback.finish_hook_context](#feedbackfinish_hook_context)
    - [feedback.finish_hook_context.error](#feedbackfinish_hook_contexterror)
    - [feedback.finish_hook_context.noop](#feedbackfinish_hook_contextnoop)
    - [feedback.finish_hook_context.record_effect](#feedbackfinish_hook_contextrecord_effect)
    - [feedback.finish_hook_context.success](#feedbackfinish_hook_contextsuccess)
  - [feedback.revision_context](#feedbackrevision_context)
  - [filter_replace](#filter_replace)
  - [folder](#folder)
    - [folder.destination](#folderdestination)
    - [folder.origin](#folderorigin)
  - [format](#format)
    - [format.buildifier](#formatbuildifier)
  - [gerrit_api_obj](#gerrit_api_obj)
    - [gerrit_api_obj.delete_vote](#gerrit_api_objdelete_vote)
    - [gerrit_api_obj.get_actions](#gerrit_api_objget_actions)
    - [gerrit_api_obj.get_change](#gerrit_api_objget_change)
    - [gerrit_api_obj.list_changes](#gerrit_api_objlist_changes)
    - [gerrit_api_obj.new_destination_ref](#gerrit_api_objnew_destination_ref)
    - [gerrit_api_obj.new_origin_ref](#gerrit_api_objnew_origin_ref)
    - [gerrit_api_obj.post_review](#gerrit_api_objpost_review)
  - [gerritapi.AccountInfo](#gerritapiaccountinfo)
  - [gerritapi.ApprovalInfo](#gerritapiapprovalinfo)
  - [gerritapi.ChangeInfo](#gerritapichangeinfo)
  - [gerritapi.ChangeMessageInfo](#gerritapichangemessageinfo)
  - [gerritapi.ChangesQuery](#gerritapichangesquery)
  - [gerritapi.CommitInfo](#gerritapicommitinfo)
  - [gerritapi.getActionInfo](#gerritapigetactioninfo)
  - [gerritapi.GitPersonInfo](#gerritapigitpersoninfo)
  - [gerritapi.LabelInfo](#gerritapilabelinfo)
  - [gerritapi.ParentCommitInfo](#gerritapiparentcommitinfo)
  - [gerritapi.ReviewResult](#gerritapireviewresult)
  - [gerritapi.RevisionInfo](#gerritapirevisioninfo)
  - [gerritapi.SubmitRequirementExpressionInfo](#gerritapisubmitrequirementexpressioninfo)
  - [git](#git)
    - [git.destination](#gitdestination)
    - [git.gerrit_api](#gitgerrit_api)
    - [git.gerrit_destination](#gitgerrit_destination)
    - [git.gerrit_origin](#gitgerrit_origin)
    - [git.gerrit_trigger](#gitgerrit_trigger)
    - [git.github_api](#gitgithub_api)
    - [git.github_destination](#gitgithub_destination)
    - [git.github_origin](#gitgithub_origin)
    - [git.github_pr_destination](#gitgithub_pr_destination)
    - [git.github_pr_origin](#gitgithub_pr_origin)
    - [git.github_trigger](#gitgithub_trigger)
    - [git.integrate](#gitintegrate)
    - [git.latest_version](#gitlatest_version)
    - [git.mirror](#gitmirror)
    - [git.origin](#gitorigin)
    - [git.review_input](#gitreview_input)
  - [git.mirrorContext](#gitmirrorcontext)
    - [git.mirrorContext.cherry_pick](#gitmirrorcontextcherry_pick)
    - [git.mirrorContext.create_branch](#gitmirrorcontextcreate_branch)
    - [git.mirrorContext.destination_fetch](#gitmirrorcontextdestination_fetch)
    - [git.mirrorContext.destination_push](#gitmirrorcontextdestination_push)
    - [git.mirrorContext.error](#gitmirrorcontexterror)
    - [git.mirrorContext.merge](#gitmirrorcontextmerge)
    - [git.mirrorContext.noop](#gitmirrorcontextnoop)
    - [git.mirrorContext.origin_fetch](#gitmirrorcontextorigin_fetch)
    - [git.mirrorContext.rebase](#gitmirrorcontextrebase)
    - [git.mirrorContext.record_effect](#gitmirrorcontextrecord_effect)
    - [git.mirrorContext.references](#gitmirrorcontextreferences)
    - [git.mirrorContext.success](#gitmirrorcontextsuccess)
  - [git_merge_result](#git_merge_result)
  - [github_api_obj](#github_api_obj)
    - [github_api_obj.add_label](#github_api_objadd_label)
    - [github_api_obj.create_issue](#github_api_objcreate_issue)
    - [github_api_obj.create_status](#github_api_objcreate_status)
    - [github_api_obj.delete_reference](#github_api_objdelete_reference)
    - [github_api_obj.get_authenticated_user](#github_api_objget_authenticated_user)
    - [github_api_obj.get_check_runs](#github_api_objget_check_runs)
    - [github_api_obj.get_combined_status](#github_api_objget_combined_status)
    - [github_api_obj.get_commit](#github_api_objget_commit)
    - [github_api_obj.get_pull_request_comment](#github_api_objget_pull_request_comment)
    - [github_api_obj.get_pull_request_comments](#github_api_objget_pull_request_comments)
    - [github_api_obj.get_pull_requests](#github_api_objget_pull_requests)
    - [github_api_obj.get_reference](#github_api_objget_reference)
    - [github_api_obj.get_references](#github_api_objget_references)
    - [github_api_obj.list_issue_comments](#github_api_objlist_issue_comments)
    - [github_api_obj.new_destination_ref](#github_api_objnew_destination_ref)
    - [github_api_obj.new_origin_ref](#github_api_objnew_origin_ref)
    - [github_api_obj.post_issue_comment](#github_api_objpost_issue_comment)
    - [github_api_obj.update_pull_request](#github_api_objupdate_pull_request)
    - [github_api_obj.update_reference](#github_api_objupdate_reference)
  - [glob](#glob)
  - [Globals](#globals)
    - [glob](#glob)
    - [new_author](#new_author)
    - [parse_message](#parse_message)
  - [go](#go)
    - [go.go_proxy_resolver](#gogo_proxy_resolver)
    - [go.go_proxy_version_list](#gogo_proxy_version_list)
  - [hg](#hg)
    - [hg.origin](#hgorigin)
  - [mapping_function](#mapping_function)
  - [metadata](#metadata)
    - [metadata.add_header](#metadataadd_header)
    - [metadata.expose_label](#metadataexpose_label)
    - [metadata.map_author](#metadatamap_author)
    - [metadata.map_references](#metadatamap_references)
    - [metadata.remove_label](#metadataremove_label)
    - [metadata.replace_message](#metadatareplace_message)
    - [metadata.restore_author](#metadatarestore_author)
    - [metadata.save_author](#metadatasave_author)
    - [metadata.scrubber](#metadatascrubber)
    - [metadata.squash_notes](#metadatasquash_notes)
    - [metadata.use_last_change](#metadatause_last_change)
    - [metadata.verify_match](#metadataverify_match)
  - [origin_ref](#origin_ref)
  - [patch](#patch)
    - [patch.apply](#patchapply)
    - [patch.quilt_apply](#patchquilt_apply)
  - [Path](#path)
    - [path.read_symlink](#pathread_symlink)
    - [path.relativize](#pathrelativize)
    - [path.resolve](#pathresolve)
    - [path.resolve_sibling](#pathresolve_sibling)
  - [PathAttributes](#pathattributes)
  - [re2](#re2)
    - [re2.compile](#re2compile)
  - [re2_matcher](#re2_matcher)
    - [re2_matcher.end](#re2_matcherend)
    - [re2_matcher.find](#re2_matcherfind)
    - [re2_matcher.group](#re2_matchergroup)
    - [re2_matcher.group_count](#re2_matchergroup_count)
    - [re2_matcher.matches](#re2_matchermatches)
    - [re2_matcher.replace_all](#re2_matcherreplace_all)
    - [re2_matcher.replace_first](#re2_matcherreplace_first)
    - [re2_matcher.start](#re2_matcherstart)
  - [re2_pattern](#re2_pattern)
    - [re2_pattern.matcher](#re2_patternmatcher)
    - [re2_pattern.matches](#re2_patternmatches)
  - [remotefiles](#remotefiles)
    - [remotefiles.github_archive](#remotefilesgithub_archive)
    - [remotefiles.origin](#remotefilesorigin)
  - [SetReviewInput](#setreviewinput)
  - [struct](#struct)
    - [struct](#struct)
  - [toml](#toml)
    - [toml.parse](#tomlparse)
  - [TomlContent](#tomlcontent)
    - [TomlContent.get](#tomlcontentget)
  - [transformation](#transformation)
  - [transformation_status](#transformation_status)
  - [TransformWork](#transformwork)
    - [ctx.add_label](#ctxadd_label)
    - [ctx.add_or_replace_label](#ctxadd_or_replace_label)
    - [ctx.add_text_before_labels](#ctxadd_text_before_labels)
    - [ctx.create_symlink](#ctxcreate_symlink)
    - [ctx.destination_api](#ctxdestination_api)
    - [ctx.destination_info](#ctxdestination_info)
    - [ctx.destination_reader](#ctxdestination_reader)
    - [ctx.find_all_labels](#ctxfind_all_labels)
    - [ctx.find_label](#ctxfind_label)
    - [ctx.new_path](#ctxnew_path)
    - [ctx.noop](#ctxnoop)
    - [ctx.now_as_string](#ctxnow_as_string)
    - [ctx.origin_api](#ctxorigin_api)
    - [ctx.read_path](#ctxread_path)
    - [ctx.remove_label](#ctxremove_label)
    - [ctx.replace_label](#ctxreplace_label)
    - [ctx.run](#ctxrun)
    - [ctx.set_author](#ctxset_author)
    - [ctx.set_executable](#ctxset_executable)
    - [ctx.set_message](#ctxset_message)
    - [ctx.success](#ctxsuccess)
    - [ctx.write_path](#ctxwrite_path)
  - [xml](#xml)
    - [xml.xpath](#xmlxpath)



## author

Represents the author of a change


#### Fields:

Name | Description
---- | -----------
email | `string`<br><p>The email of the author</p>
name | `string`<br><p>The name of the author</p>



## authoring

The authors mapping between an origin and a destination

<a id="authoring.allowed" aria-hidden="true"></a>
### authoring.allowed

Create a list for an individual or team contributing code.

[`authoring_class`](#authoring_class) `authoring.allowed(default, allowlist)`


#### Parameters:

Parameter | Description
--------- | -----------
default | `string`<br><p>The default author for commits in the destination. This is used in squash mode workflows or when users are not on the list.</p>
allowlist | `sequence of string`<br><p>List of  authors in the origin that are allowed to contribute code. The authors must be unique</p>


#### Examples:


##### Only pass thru allowed users:



```python
authoring.allowed(
    default = "Foo Bar <noreply@foobar.com>",
    allowlist = [
       "someuser@myorg.com",
       "other@myorg.com",
       "another@myorg.com",
    ],
)
```


##### Only pass thru allowed LDAPs/usernames:

Some repositories are not based on email but use LDAPs/usernames. This is also supported since it is up to the origin how to check whether two authors are the same.

```python
authoring.allowed(
    default = "Foo Bar <noreply@foobar.com>",
    allowlist = [
       "someuser",
       "other",
       "another",
    ],
)
```


<a id="authoring.overwrite" aria-hidden="true"></a>
### authoring.overwrite

Use the default author for all the submits in the destination. Note that some destinations might choose to ignore this author and use the current user running the tool (In other words they don't allow impersonation).

[`authoring_class`](#authoring_class) `authoring.overwrite(default)`


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

Use the origin author as the author in the destination, no filtering.

[`authoring_class`](#authoring_class) `authoring.pass_thru(default)`


#### Parameters:

Parameter | Description
--------- | -----------
default | `string`<br><p>The default author for commits in the destination. This is used in squash mode workflows or if author cannot be determined.</p>


#### Example:


##### Pass thru usage example:



```python
authoring.pass_thru(default = "Foo Bar <noreply@foobar.com>")
```




## authoring_class

The authors mapping between an origin and a destination



## buildozer

Module for Buildozer-related functionality such as creating and modifying BUILD targets.

<a id="buildozer.cmd" aria-hidden="true"></a>
### buildozer.cmd

Creates a Buildozer command. You can specify the reversal with the 'reverse' argument.

`Command` `buildozer.cmd(forward, reverse=None)`


#### Parameters:

Parameter | Description
--------- | -----------
forward | `string`<br><p>Specifies the Buildozer command, e.g. 'replace deps :foo :bar'</p>
reverse | `string` or `NoneType`<br><p>The reverse of the command. This is only required if the given command cannot be reversed automatically and the reversal of this command is required by some workflow or Copybara check. The following commands are automatically reversible:<br><ul><li>add</li><li>remove (when used to remove element from list i.e. 'remove srcs foo.cc'</li><li>replace</li></ul></p>

<a id="buildozer.create" aria-hidden="true"></a>
### buildozer.create

A transformation which creates a new build target and populates its attributes. This transform can reverse automatically to delete the target.

[`transformation`](#transformation) `buildozer.create(target, rule_type, commands=[], before='', after='')`


#### Parameters:

Parameter | Description
--------- | -----------
target | `string`<br><p>Target to create, including the package, e.g. 'foo:bar'. The package can be '.' for the root BUILD file.</p>
rule_type | `string`<br><p>Type of this rule, for instance, java_library.</p>
commands | `sequence of string` or `sequence of Command`<br><p>Commands to populate attributes of the target after creating it. Elements can be strings such as 'add deps :foo' or objects returned by buildozer.cmd.</p>
before | `string`<br><p>When supplied, causes this target to be created *before* the target named by 'before'</p>
after | `string`<br><p>When supplied, causes this target to be created *after* the target named by 'after'</p>

<a id="buildozer.delete" aria-hidden="true"></a>
### buildozer.delete

A transformation which is the opposite of creating a build target. When run normally, it deletes a build target. When reversed, it creates and prepares one.

[`transformation`](#transformation) `buildozer.delete(target, rule_type='', recreate_commands=[], before='', after='')`


#### Parameters:

Parameter | Description
--------- | -----------
target | `string`<br><p>Target to delete, including the package, e.g. 'foo:bar'</p>
rule_type | `string`<br><p>Type of this rule, for instance, java_library. Supplying this will cause this transformation to be reversible.</p>
recreate_commands | `sequence of string` or `sequence of Command`<br><p>Commands to populate attributes of the target after creating it. Elements can be strings such as 'add deps :foo' or objects returned by buildozer.cmd.</p>
before | `string`<br><p>When supplied with rule_type and the transformation is reversed, causes this target to be created *before* the target named by 'before'</p>
after | `string`<br><p>When supplied with rule_type and the transformation is reversed, causes this target to be created *after* the target named by 'after'</p>

<a id="buildozer.modify" aria-hidden="true"></a>
### buildozer.modify

A transformation which runs one or more Buildozer commands against a single target expression. See http://go/buildozer for details on supported commands and target expression formats.

[`transformation`](#transformation) `buildozer.modify(target, commands)`


#### Parameters:

Parameter | Description
--------- | -----------
target | `string` or `sequence of string`<br><p>Specifies the target(s) against which to apply the commands. Can be a list.</p>
commands | `sequence of string` or `sequence of Command`<br><p>Commands to apply to the target(s) specified. Elements can be strings such as 'add deps :foo' or objects returned by buildozer.cmd.</p>


#### Examples:


##### Add a setting to one target:

Add "config = ':foo'" to foo/bar:baz:

```python
buildozer.modify(
    target = 'foo/bar:baz',
    commands = [
        buildozer.cmd('set config ":foo"'),
    ],
)
```


##### Add a setting to several targets:

Add "config = ':foo'" to foo/bar:baz and foo/bar:fooz:

```python
buildozer.modify(
    target = ['foo/bar:baz', 'foo/bar:fooz'],
    commands = [
        buildozer.cmd('set config ":foo"'),
    ],
)
```




## change

A change metadata. Contains information like author, change message or detected labels


#### Fields:

Name | Description
---- | -----------
author | [`author`](#author)<br><p>The author of the change</p>
date_time_iso_offset | `string`<br><p>Return a ISO offset date time. Example:  2011-12-03T10:15:30+01:00'</p>
first_line_message | `string`<br><p>The message of the change</p>
labels | `dict[string, string]`<br><p>A dictionary with the labels detected for the change. If the label is present multiple times it returns the last value. Note that this is a heuristic and it could include things that are not labels.</p>
labels_all_values | `dict[string, sequence of string]`<br><p>A dictionary with the labels detected for the change. Note that the value is a collection of the values for each time the label was found. Use 'labels' instead if you are only interested in the last value. Note that this is a heuristic and it could include things that are not labels.</p>
merge | `bool`<br><p>Returns true if the change represents a merge</p>
message | `string`<br><p>The message of the change</p>
original_author | [`author`](#author)<br><p>The author of the change before any mapping</p>
ref | `string`<br><p>Origin reference ref</p>



## ChangeMessage

Represents a well formed parsed change message with its associated labels.


#### Fields:

Name | Description
---- | -----------
first_line | `string`<br><p>First line of this message</p>
text | `string`<br><p>The text description this message, not including the labels.</p>

<a id="message.label_values" aria-hidden="true"></a>
### message.label_values

Returns a list of values associated with the label name.

`sequence of string` `message.label_values(label_name)`


#### Parameters:

Parameter | Description
--------- | -----------
label_name | `string`<br><p>The label name.</p>



## Changes

Data about the set of changes that are being migrated. Each change includes information like: original author, change message, labels, etc. You receive this as a field in TransformWork object for user defined transformations


#### Fields:

Name | Description
---- | -----------
current | `sequence of `[`change`](#change)<br><p>List of changes that will be migrated</p>
migrated | `sequence of `[`change`](#change)<br><p>List of changes that where migrated in previous Copybara executions or if using ITERATIVE mode in previous iterations of this workflow.</p>



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


#### Fields:

Name | Description
---- | -----------
console | [`Console`](#console)<br><p>Returns a handle to the console object.</p>
main_config_path | `string`<br><p>Location of the config file. This is subject to change</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--allow-empty-diff`</span> | *boolean* | If set to false, Copybara will not write to the destination if the exact same change is already pending in the destination. Currently only supported for `git.github_pr_destination` and `git.gerrit_destination`.
<span style="white-space: nowrap;">`--commands-timeout`</span> | *duration* | Commands timeout.  Example values: 30s, 20m, 1h, etc.
<span style="white-space: nowrap;">`--config-root`</span> | *string* | Configuration root path to be used for resolving absolute config labels like '//foo/bar'
<span style="white-space: nowrap;">`--console-file-flush-interval`</span> | *duration* | How often Copybara should flush the console to the output file. (10s, 1m, etc.)If set to 0s, console will be flushed only at the end.  Example values: 30s, 20m, 1h, etc.
<span style="white-space: nowrap;">`--console-file-path`</span> | *string* | If set, write the console output also to the given file path.
<span style="white-space: nowrap;">`--debug-file-break`</span> | *string* | Stop when file matching the glob changes
<span style="white-space: nowrap;">`--debug-metadata-break`</span> | *boolean* | Stop when message and/or author changes
<span style="white-space: nowrap;">`--debug-transform-break`</span> | *string* | Stop when transform description matches
<span style="white-space: nowrap;">`--diff-bin`</span> | *string* | Command line diff tool bin used in merge import. Defaults to diff3, but users can pass in their own diffing tools (along with requisite arg reordering)
<span style="white-space: nowrap;">`--disable-reversible-check`</span> | *boolean* | If set, all workflows will be executed without reversible_check, overriding the  workflow config and the normal behavior for CHANGE_REQUEST mode.
<span style="white-space: nowrap;">`--dry-run`</span> | *boolean* | Run the migration in dry-run mode. Some destination implementations might have some side effects (like creating a code review), but never submit to a main branch.
<span style="white-space: nowrap;">`--event-monitor`</span> | *list* | Eventmonitors to enable. These must be in the list of available monitors.
<span style="white-space: nowrap;">`--force`</span> | *boolean* | Force the migration even if Copybara cannot find in the destination a change that is an ancestor of the one(s) being migrated. This should be used with care, as it could lose changes when migrating a previous/conflicting change.
<span style="white-space: nowrap;">`--info-list-only`</span> | *boolean* | When set, the INFO command will print a list of workflows defined in the file.
<span style="white-space: nowrap;">`--noansi`</span> | *boolean* | Don't use ANSI output for messages
<span style="white-space: nowrap;">`--nocleanup`</span> | *boolean* | Cleanup the output directories. This includes the workdir, scratch clones of Git repos, etc. By default is set to false and directories will be cleaned prior to the execution. If set to true, the previous run output will not be cleaned up. Keep in mind that running in this mode will lead to an ever increasing disk usage.
<span style="white-space: nowrap;">`--output-limit`</span> | *int* | Limit the output in the console to a number of records. Each subcommand might use this flag differently. Defaults to 0, which shows all the output.
<span style="white-space: nowrap;">`--output-root`</span> | *string* | The root directory where to generate output files. If not set, ~/copybara/out is used by default. Use with care, Copybara might remove files inside this root if necessary.
<span style="white-space: nowrap;">`--repo-timeout`</span> | *duration* | Repository operation timeout duration.  Example values: 30s, 20m, 1h, etc.
<span style="white-space: nowrap;">`--squash`</span> | *boolean* | Override workflow's mode with 'SQUASH'. This is useful mainly for workflows that use 'ITERATIVE' mode, when we want to run a single export with 'SQUASH', maybe to fix an issue. Always use --dry-run before, to test your changes locally.
<span style="white-space: nowrap;">`--validate-starlark`</span> | *string* | Starlark should be validated prior to execution, but this might break legacy configs. Options are LOOSE, STRICT
<span style="white-space: nowrap;">`--version-selector-use-cli-ref`</span> | *boolean* | If command line ref is to used with a version selector, pass this flag to tell copybara to use it.
<span style="white-space: nowrap;">`-v, --verbose`</span> | *boolean* | Verbose output.

<a id="core.action" aria-hidden="true"></a>
### core.action

Create a dynamic Skylark action. This should only be used by libraries developers. Actions are Starlark functions that receive a context, perform some side effect and return a result (success, error or noop).

`dynamic.action` `core.action(impl, params={})`


#### Parameters:

Parameter | Description
--------- | -----------
impl | `callable`<br><p>The Skylark function to call</p>
params | `dict`<br><p>The parameters to the function. Will be available under ctx.params</p>

<a id="core.autopatch_config" aria-hidden="true"></a>
### core.autopatch_config

Describes in the configuration for automatic patch file generation

[`core.autopatch_config`](#coreautopatch_config) `core.autopatch_config(header=None, suffix='.patch', directory_prefix='None', directory='AUTOPATCHES', strip_file_names_and_line_numbers=False)`


#### Parameters:

Parameter | Description
--------- | -----------
header | `string` or `NoneType`<br><p>A string to include at the beginning of each patch file</p>
suffix | `string`<br><p>Suffix to use when saving patch files</p>
directory_prefix | `string` or `NoneType`<br><p>Directory prefix used to relativize filenames when writing patch files. E.g. if filename is third_party/foo/bar/bar.go and we want to write third_party/foo/PATCHES/bar/bar.go, the value for this field would be 'third_party/foo'</p>
directory | `string` or `NoneType`<br><p>Directory in which to save the patch files.</p>
strip_file_names_and_line_numbers | `bool`<br><p>When true, strip filenames and line numbers from patch files</p>

<a id="core.copy" aria-hidden="true"></a>
### core.copy

Copy files between directories and renames files

[`transformation`](#transformation) `core.copy(before, after, paths=glob(["**"]), overwrite=False, regex_groups={})`


#### Parameters:

Parameter | Description
--------- | -----------
before | `string`<br><p>The name of the file or directory to copy. If this is the empty string and 'after' is a directory, then all files in the workdir will be copied to the sub directory specified by 'after', maintaining the directory tree.</p>
after | `string`<br><p>The name of the file or directory destination. If this is the empty string and 'before' is a directory, then all files in 'before' will be copied to the repo root, maintaining the directory tree inside 'before'.</p>
paths | [`glob`](#glob) or `NoneType`<br><p>A glob expression relative to 'before' if it represents a directory. Only files matching the expression will be copied. For example, glob(["**.java"]), matches all java files recursively inside 'before' folder. Defaults to match all the files recursively.</p>
overwrite | `bool`<br><p>Overwrite destination files if they already exist. Note that this makes the transformation non-reversible, since there is no way to know if the file was overwritten or not in the reverse workflow.</p>
regex_groups | `dict`<br><p>A set of named regexes that can be used to match part of the file name. Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax. For example {"x": "[A-Za-z]+"}</p>


#### Examples:


##### Copy a directory:

Move all the files in a directory to another directory:

```python
core.copy("foo/bar_internal", "bar")
```

In this example, `foo/bar_internal/one` will be copied to `bar/one`.


##### Copy using Regex:

Change a file extension:

```python
core.copy(before = 'foo/${x}.txt', after = 'foo/${x}.md', regex_groups = { 'x': '.*'})
```

In this example, `foo/bar/README.txt` will be copied to `foo/bar/README.md`.


##### Copy with reversal:

Copy all static files to a 'static' folder and use remove for reverting the change

```python
core.transform(
    [core.copy("foo", "foo/static", paths = glob(["**.css","**.html", ]))],
    reversal = [core.remove(glob(['foo/static/**.css', 'foo/static/**.html']))]
)
```


<a id="core.dynamic_feedback" aria-hidden="true"></a>
### core.dynamic_feedback

Create a dynamic Skylark feedback migration. This should only be used by libraries developers

`dynamic.action` `core.dynamic_feedback(impl, params={})`


#### Parameters:

Parameter | Description
--------- | -----------
impl | `callable`<br><p>The Skylark function to call</p>
params | `dict`<br><p>The parameters to the function. Will be available under ctx.params</p>

<a id="core.dynamic_transform" aria-hidden="true"></a>
### core.dynamic_transform

Create a dynamic Skylark transformation. This should only be used by libraries developers

[`transformation`](#transformation) `core.dynamic_transform(impl, params={})`


#### Parameters:

Parameter | Description
--------- | -----------
impl | `callable`<br><p>The Skylark function to call</p>
params | `dict`<br><p>The parameters to the function. Will be available under ctx.params</p>


#### Examples:


##### Create a dynamic transformation without parameters:

To define a simple dynamic transformation, you don't even need to use `core.dynamic_transform`. The following transformation sets the change's message to uppercase.

```python
def test(ctx):
  ctx.set_message(ctx.message.upper())
```

After defining this function, you can use `test` as a transformation in `core.workflow`.


##### Create a dynamic transformation with parameters:

If you want to create a library that uses dynamic transformations, you probably want to make them customizable. In order to do that, in your library.bara.sky, you need to hide the dynamic transformation (prefix with '\_') and instead expose a function that creates the dynamic transformation with the param:

```python
def _test_impl(ctx):
  ctx.set_message(ctx.message + ctx.params['name'] + str(ctx.params['number']) + '\n')

def test(name, number = 2):
  return core.dynamic_transform(impl = _test_impl,
                           params = { 'name': name, 'number': number})
```

After defining this function, you can use `test('example', 42)` as a transformation in `core.workflow`.


<a id="core.fail_with_noop" aria-hidden="true"></a>
### core.fail_with_noop

If invoked, it will fail the current migration as a noop

`dynamic.action` `core.fail_with_noop(msg)`


#### Parameters:

Parameter | Description
--------- | -----------
msg | `string`<br><p>The noop message</p>

<a id="core.feedback" aria-hidden="true"></a>
### core.feedback

Defines a migration of changes' metadata, that can be invoked via the Copybara command in the same way as a regular workflow migrates the change itself.

It is considered change metadata any information associated with a change (pending or submitted) that is not core to the change itself. A few examples:
<ul>
  <li> Comments: Present in any code review system. Examples: GitHub PRs or Gerrit     code reviews.</li>
  <li> Labels: Used in code review systems for approvals and/or CI results.     Examples: GitHub labels, Gerrit code review labels.</li>
</ul>
For the purpose of this workflow, it is not considered metadata the commit message in Git, or any of the contents of the file tree.



`core.feedback(name, origin, destination, actions=[], description=None)`


#### Parameters:

Parameter | Description
--------- | -----------
name | `string`<br><p>The name of the feedback workflow.</p>
origin | `trigger`<br><p>The trigger of a feedback migration.</p>
destination | [`endpoint_provider`](#endpoint_provider)<br><p>Where to write change metadata to. This is usually a code review system like Gerrit or GitHub PR.</p>
actions | `sequence`<br><p>A list of feedback actions to perform, with the following semantics:<br>  - There is no guarantee of the order of execution.<br>  - Actions need to be independent from each other.<br>  - Failure in one action might prevent other actions from executing.<br></p>
description | `string` or `NoneType`<br><p>A description of what this workflow achieves</p>

<a id="core.filter_replace" aria-hidden="true"></a>
### core.filter_replace

Applies an initial filtering to find a substring to be replaced and then applies a `mapping` of replaces for the matched text.

[`filter_replace`](#filter_replace) `core.filter_replace(regex, mapping={}, group=Whole text, paths=glob(["**"]), reverse=regex)`


#### Parameters:

Parameter | Description
--------- | -----------
regex | `string`<br><p>A re2 regex to match a substring of the file</p>
mapping | `unknown`<br><p>A mapping function like core.replace_mapper or a dict with mapping values.</p>
group | `int` or `NoneType`<br><p>Extract a regex group from the matching text and pass this as parameter to the mapping instead of the whole matching text.</p>
paths | [`glob`](#glob) or `NoneType`<br><p>A glob expression relative to the workdir representing the files to apply the transformation. For example, glob(["**.java"]), matches all java files recursively. Defaults to match all the files recursively.</p>
reverse | `string` or `NoneType`<br><p>A re2 regex used as reverse transformation</p>


#### Examples:


##### Simple replace with mapping:

Simplest mapping

```python
core.filter_replace(
    regex = 'a.*',
    mapping = {
        'afoo': 'abar',
        'abaz': 'abam'
    }
)
```


##### TODO replace:

This replace is similar to what it can be achieved with core.todo_replace:

```python
core.filter_replace(
    regex = 'TODO\\((.*?)\\)',
    group = 1,
        mapping = core.replace_mapper([
            core.replace(
                before = '${p}foo${s}',
                after = '${p}fooz${s}',
                regex_groups = { 'p': '.*', 's': '.*'}
            ),
            core.replace(
                before = '${p}baz${s}',
                after = '${p}bazz${s}',
                regex_groups = { 'p': '.*', 's': '.*'}
            ),
        ],
        all = True
    )
)
```


<a id="core.format" aria-hidden="true"></a>
### core.format

Formats a String using Java's <a href='https://docs.oracle.com/javase/8/docs/api/java/lang/String.html#format-java.lang.String-java.lang.Object...-'><code>String.format</code></a>.

`string` `core.format(format, args)`


#### Parameters:

Parameter | Description
--------- | -----------
format | `string`<br><p>The format string</p>
args | `sequence`<br><p>The arguments to format</p>

<a id="core.latest_version" aria-hidden="true"></a>
### core.latest_version

Selects the latest version that matches the format.  Using --force in the CLI will force to use the reference passed as argument instead.

`VersionSelector` `core.latest_version(format, regex_groups={})`


#### Parameters:

Parameter | Description
--------- | -----------
format | `string`<br><p>The format of the version. If using it for git, it has to use the completerefspec (e.g. 'refs/tags/${n0}.${n1}.${n2}')</p>
regex_groups | `dict`<br><p>A set of named regexes that can be used to match part of the versions. Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax. Use the following nomenclature n0, n1, n2 for the version part (will use numeric sorting) or s0, s1, s2 (alphabetic sorting). Note that there can be mixed but the numbers cannot be repeated. In other words n0, s1, n2 is valid but not n0, s0, n1. n0 has more priority than n1. If there are fields where order is not important, use s(N+1) where N ist he latest sorted field. Example {"n0": "[0-9]+", "s1": "[a-z]+"}</p>


#### Examples:


##### Version selector for Git tags:

Example of how to match tags that follow semantic versioning

```python
core.latest_version(
    format = "refs/tags/${n0}.${n1}.${n2}",    regex_groups = {
        'n0': '[0-9]+',        'n1': '[0-9]+',        'n2': '[0-9]+',    })
```


##### Version selector for Git tags with mixed version semantics with X.Y.Z and X.Y tagging:

Edge case example: we allow a '.' literal prefix for numeric regex groups.

```python
core.latest_version(
    format = "refs/tags/${n0}.${n1}${n2}",    regex_groups = {
        'n0': '[0-9]+',        'n1': '[0-9]+',        'n2': '(.[0-9]+)?',    })
```


<a id="core.move" aria-hidden="true"></a>
### core.move

Moves files between directories and renames files

[`transformation`](#transformation) `core.move(before, after, paths=glob(["**"]), overwrite=False, regex_groups={})`


#### Parameters:

Parameter | Description
--------- | -----------
before | `string`<br><p>The name of the file or directory before moving. If this is the empty string and 'after' is a directory, then all files in the workdir will be moved to the sub directory specified by 'after', maintaining the directory tree.</p>
after | `string`<br><p>The name of the file or directory after moving. If this is the empty string and 'before' is a directory, then all files in 'before' will be moved to the repo root, maintaining the directory tree inside 'before'.</p>
paths | [`glob`](#glob) or `NoneType`<br><p>A glob expression relative to 'before' if it represents a directory. Only files matching the expression will be moved. For example, glob(["**.java"]), matches all java files recursively inside 'before' folder. Defaults to match all the files recursively.</p>
overwrite | `bool`<br><p>Overwrite destination files if they already exist. Note that this makes the transformation non-reversible, since there is no way to know if the file was overwritten or not in the reverse workflow.</p>
regex_groups | `dict`<br><p>A set of named regexes that can be used to match part of the file name. Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax. For example {"x": "[A-Za-z]+"}</p>


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


##### Move using Regex:

Change a file extension:

```python
core.move(before = 'foo/${x}.txt', after = 'foo/${x}.md', regex_groups = { 'x': '.*'})
```

In this example, `foo/bar/README.txt` will be moved to `foo/bar/README.md`.


<a id="core.remove" aria-hidden="true"></a>
### core.remove

Remove files from the workdir. **This transformation is only meant to be used inside core.transform for reversing core.copy like transforms**. For regular file filtering use origin_files exclude mechanism.

[`transformation`](#transformation) `core.remove(paths)`


#### Parameters:

Parameter | Description
--------- | -----------
paths | [`glob`](#glob)<br><p>The files to be deleted</p>


#### Examples:


##### Reverse a file copy:

Move all the files in a directory to another directory:

```python
core.transform(
    [core.copy("foo", "foo/public")],
    reversal = [core.remove(glob(["foo/public/**"]))])
```

In this example, `foo/one` will be moved to `foo/public/one`.


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

Replace a text with another text using optional regex groups. This transformation can be automatically reversed.

[`transformation`](#transformation) `core.replace(before, after, regex_groups={}, paths=glob(["**"]), first_only=False, multiline=False, repeated_groups=False, ignore=[])`


#### Parameters:

Parameter | Description
--------- | -----------
before | `string`<br><p>The text before the transformation. Can contain references to regex groups. For example "foo${x}text".<p>`before` can only contain 1 reference to each unique `regex_group`. If you require multiple references to the same `regex_group`, add `repeated_groups: True`.<p>If '$' literal character needs to be matched, '`$$`' should be used. For example '`$$FOO`' would match the literal '$FOO'. [Note this argument is a string. If you want to match a regular expression it must be encoded as a regex_group.]</p>
after | `string`<br><p>The text after the transformation. It can also contain references to regex groups, like 'before' field.</p>
regex_groups | `dict`<br><p>A set of named regexes that can be used to match part of the replaced text.Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax. For example {"x": "[A-Za-z]+"}</p>
paths | [`glob`](#glob) or `NoneType`<br><p>A glob expression relative to the workdir representing the files to apply the transformation. For example, glob(["**.java"]), matches all java files recursively. Defaults to match all the files recursively.</p>
first_only | `bool`<br><p>If true, only replaces the first instance rather than all. In single line mode, replaces the first instance on each line. In multiline mode, replaces the first instance in each file.</p>
multiline | `bool`<br><p>Whether to replace text that spans more than one line.</p>
repeated_groups | `bool`<br><p>Allow to use a group multiple times. For example foo${repeated}/${repeated}. Note that this won't match "fooX/Y". This mechanism doesn't use backtracking. In other words, the group instances are treated as different groups in regex construction and then a validation is done after that.</p>
ignore | `sequence`<br><p>A set of regexes. Any line that matches any expression in this set, which might otherwise be transformed, will be ignored.</p>


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


##### Append some text at the end of files:



```python
core.replace(
   before = '${end}',
   after  = 'Text to be added at the end',
   multiline = True,
   regex_groups = { 'end' : '\\z'},
)
```


##### Append some text at the end of files reversible:

Same as the above example but make the transformation reversible

```python
core.transform([
    core.replace(
       before = '${end}',
       after  = 'some append',
       multiline = True,
       regex_groups = { 'end' : r'\z'},
    )
],
reversal = [
    core.replace(
       before = 'some append${end}',
       after = '',
       multiline = True,
       regex_groups = { 'end' : r'\z'},
    )])
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




<a id="core.replace_mapper" aria-hidden="true"></a>
### core.replace_mapper

A mapping function that applies a list of replaces until one replaces the text (Unless `all = True` is used). This should be used with core.filter_replace or other transformations that accept text mapping as parameter.

[`mapping_function`](#mapping_function) `core.replace_mapper(mapping, all=False)`


#### Parameters:

Parameter | Description
--------- | -----------
mapping | `sequence of `[`transformation`](#transformation)<br><p>The list of core.replace transformations</p>
all | `bool`<br><p>Run all the mappings despite a replace happens.</p>

<a id="core.reverse" aria-hidden="true"></a>
### core.reverse

Given a list of transformations, returns the list of transformations equivalent to undoing all the transformations

`sequence of `[`transformation`](#transformation) `core.reverse(transformations)`


#### Parameters:

Parameter | Description
--------- | -----------
transformations | `sequence of `[`transformation`](#transformation)<br><p>The transformations to reverse</p>

<a id="core.todo_replace" aria-hidden="true"></a>
### core.todo_replace

Replace Google style TODOs. For example `TODO(username, othername)`.

[`transformation`](#transformation) `core.todo_replace(tags=['TODO', 'NOTE'], mapping={}, mode='MAP_OR_IGNORE', paths=glob(["**"]), default=None, ignore=None)`


#### Parameters:

Parameter | Description
--------- | -----------
tags | `sequence of string`<br><p>Prefix tag to look for</p>
mapping | `dict`<br><p>Mapping of users/strings</p>
mode | `string`<br><p>Mode for the replace:<ul><li>'MAP_OR_FAIL': Try to use the mapping and if not found fail.</li><li>'MAP_OR_IGNORE': Try to use the mapping but ignore if no mapping found.</li><li>'MAP_OR_DEFAULT': Try to use the mapping and use the default if not found.</li><li>'SCRUB_NAMES': Scrub all names from TODOs. Transforms 'TODO(foo)' to 'TODO'</li><li>'USE_DEFAULT': Replace any TODO(foo, bar) with TODO(default_string)</li></ul></p>
paths | [`glob`](#glob) or `NoneType`<br><p>A glob expression relative to the workdir representing the files to apply the transformation. For example, glob(["**.java"]), matches all java files recursively. Defaults to match all the files recursively.</p>
default | `string` or `NoneType`<br><p>Default value if mapping not found. Only valid for 'MAP_OR_DEFAULT' or 'USE_DEFAULT' modes</p>
ignore | `string` or `NoneType`<br><p>If set, elements within TODO (with usernames) that match the regex will be ignored. For example ignore = "foo" would ignore "foo" in "TODO(foo,bar)" but not "bar".</p>


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


##### Ignoring Regex Patterns:

Ignore regEx inside TODOs when scrubbing/mapping

```python
core.todo_replace(
  mapping = { 'aaa' : 'foo'},
  ignore = 'b/.*'
)
```

Would replace texts like TODO(b/123, aaa) with TODO(b/123, foo)


<a id="core.transform" aria-hidden="true"></a>
### core.transform

Groups some transformations in a transformation that can contain a particular, manually-specified, reversal, where the forward version and reversed version of the transform are represented as lists of transforms. The is useful if a transformation does not automatically reverse, or if the automatic reversal does not work for some reason.<br>If reversal is not provided, the transform will try to compute the reverse of the transformations list.

[`transformation`](#transformation) `core.transform(transformations, reversal=The reverse of 'transformations', ignore_noop=None, noop_behavior=NOOP_IF_ANY_NOOP)`


#### Parameters:

Parameter | Description
--------- | -----------
transformations | `sequence of `[`transformation`](#transformation)<br><p>The list of transformations to run as a result of running this transformation.</p>
reversal | `sequence of `[`transformation`](#transformation) or `NoneType`<br><p>The list of transformations to run as a result of running this transformation in reverse.</p>
ignore_noop | `bool` or `NoneType`<br><p>WARNING: Deprecated. Use `noop_behavior` instead.<br>In case a noop error happens in the group of transformations (Both forward and reverse), it will be ignored, but the rest of the transformations in the group will still be executed. If ignore_noop is not set, we will apply the closest parent's ignore_noop.</p>
noop_behavior | `string` or `NoneType`<br><p>How to handle no-op transformations:<br><ul> <li><b>'IGNORE_NOOP'</b>: Any no-ops among the wrapped transformations are ignored.</li> <li><b>'NOOP_IF_ANY_NOOP'</b>: Throws an exception as soon as a single wrapped transformation is a no-op.</li> <li><b>'NOOP_IF_ALL_NOOP'</b>: Ignores no-ops from the wrapped transformations unless they all no-op, in which case an exception is thrown.</li></ul></p>

<a id="core.verify_match" aria-hidden="true"></a>
### core.verify_match

Verifies that a RegEx matches (or not matches) the specified files. Does not transform anything, but will stop the workflow if it fails.

[`transformation`](#transformation) `core.verify_match(regex, paths=glob(["**"]), verify_no_match=False, also_on_reversal=False, failure_message=None)`


#### Parameters:

Parameter | Description
--------- | -----------
regex | `string`<br><p>The regex pattern to verify. To satisfy the validation, there has to be atleast one (or no matches if verify_no_match) match in each of the files included in paths. The re2j pattern will be applied in multiline mode, i.e. '^' refers to the beginning of a file and '$' to its end. Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax.</p>
paths | [`glob`](#glob) or `NoneType`<br><p>A glob expression relative to the workdir representing the files to apply the transformation. For example, glob(["**.java"]), matches all java files recursively. Defaults to match all the files recursively.</p>
verify_no_match | `bool`<br><p>If true, the transformation will verify that the RegEx does not match.</p>
also_on_reversal | `bool`<br><p>If true, the check will also apply on the reversal. The default behavior is to not verify the pattern on reversal.</p>
failure_message | `unknown`<br><p>Optional string that will be included in the failure message.</p>

<a id="core.workflow" aria-hidden="true"></a>
### core.workflow

Defines a migration pipeline which can be invoked via the Copybara command.

Implicit labels that can be used/exposed:

  - COPYBARA_CONTEXT_REFERENCE: Requested reference. For example if copybara is invoked as `copybara copy.bara.sky workflow master`, the value would be `master`.
  - COPYBARA_LAST_REV: Last reference that was migrated
  - COPYBARA_CURRENT_REV: The current reference being migrated
  - COPYBARA_CURRENT_REV_DATE_TIME: Date & time for the current reference being migrated in ISO format (Example: "2011-12-03T10:15:30+01:00")
  - COPYBARA_CURRENT_MESSAGE: The current message at this point of the transformations
  - COPYBARA_CURRENT_MESSAGE_TITLE: The current message title (first line) at this point of the transformations
  - COPYBARA_AUTHOR: The author of the change


`core.workflow(name, origin, destination, authoring, transformations=[], origin_files=glob(["**"]), destination_files=glob(["**"]), mode="SQUASH", reversible_check=True for 'CHANGE_REQUEST' mode. False otherwise, check_last_rev_state=False, ask_for_confirmation=False, dry_run=False, after_migration=[], after_workflow=[], change_identity=None, set_rev_id=True, smart_prune=False, merge_import=False, autopatch_config=None, migrate_noop_changes=False, experimental_custom_rev_id=None, description=None, checkout=True, reversible_check_ignore_files=None)`


#### Parameters:

Parameter | Description
--------- | -----------
name | `string`<br><p>The name of the workflow.</p>
origin | `origin`<br><p>Where to read from the code to be migrated, before applying the transformations. This is usually a VCS like Git, but can also be a local folder or even a pending change in a code review system like Gerrit.</p>
destination | `destination`<br><p>Where to write to the code being migrated, after applying the transformations. This is usually a VCS like Git, but can also be a local folder or even a pending change in a code review system like Gerrit.</p>
authoring | [`authoring_class`](#authoring_class)<br><p>The author mapping configuration from origin to destination.</p>
transformations | `sequence`<br><p>The transformations to be run for this workflow. They will run in sequence.</p>
origin_files | [`glob`](#glob) or `NoneType`<br><p>A glob relative to the workdir that will be read from the origin during the import. For example glob(["**.java"]), all java files, recursively, which excludes all other file types.</p>
destination_files | [`glob`](#glob) or `NoneType`<br><p>A glob relative to the root of the destination repository that matches files that are part of the migration. Files NOT matching this glob will never be removed, even if the file does not exist in the source. For example glob(['**'], exclude = ['**/BUILD']) keeps all BUILD files in destination when the origin does not have any BUILD files. You can also use this to limit the migration to a subdirectory of the destination, e.g. glob(['java/src/**'], exclude = ['**/BUILD']) to only affect non-BUILD files in java/src.</p>
mode | `string`<br><p>Workflow mode. Currently we support four modes:<br><ul><li><b>'SQUASH'</b>: Create a single commit in the destination with new tree state.</li><li><b>'ITERATIVE'</b>: Import each origin change individually.</li><li><b>'CHANGE_REQUEST'</b>: Import a pending change to the Source-of-Truth. This could be a GH Pull Request, a Gerrit Change, etc. The final intention should be to submit the change in the SoT (destination in this case).</li><li><b>'CHANGE_REQUEST_FROM_SOT'</b>: Import a pending change **from** the Source-of-Truth. This mode is useful when, despite the pending change being already in the SoT, the users want to review the code on a different system. The final intention should never be to submit in the destination, but just review or test</li></ul></p>
reversible_check | `bool` or `NoneType`<br><p>Indicates if the tool should try to to reverse all the transformations at the end to check that they are reversible.<br/>The default value is True for 'CHANGE_REQUEST' mode. False otherwise</p>
check_last_rev_state | `bool`<br><p>If set to true, Copybara will validate that the destination didn't change since last-rev import for destination_files. Note that this flag doesn't work for CHANGE_REQUEST mode.</p>
ask_for_confirmation | `bool`<br><p>Indicates that the tool should show the diff and require user's confirmation before making a change in the destination.</p>
dry_run | `bool`<br><p>Run the migration in dry-run mode. Some destination implementations might have some side effects (like creating a code review), but never submit to a main branch.</p>
after_migration | `sequence`<br><p>Run a feedback workflow after one migration happens. This runs once per change in `ITERATIVE` mode and only once for `SQUASH`.</p>
after_workflow | `sequence`<br><p>Run a feedback workflow after all the changes for this workflow run are migrated. Prefer `after_migration` as it is executed per change (in ITERATIVE mode). Tasks in this hook shouldn't be critical to execute. These actions shouldn't record effects (They'll be ignored).</p>
change_identity | `string` or `NoneType`<br><p>By default, Copybara hashes several fields so that each change has an unique identifier that at the same time reuses the generated destination change. This allows to customize the identity hash generation so that the same identity is used in several workflows. At least ${copybara_config_path} has to be present. Current user is added to the hash automatically.<br><br>Available variables:<ul>  <li>${copybara_config_path}: Main config file path</li>  <li>${copybara_workflow_name}: The name of the workflow being run</li>  <li>${copybara_reference}: The requested reference. In general Copybara tries its best to give a repetable reference. For example Gerrit change number or change-id or GitHub Pull Request number. If it cannot find a context reference it uses the resolved revision.</li>  <li>${label:label_name}: A label present for the current change. Exposed in the message or not.</li></ul>If any of the labels cannot be found it defaults to the default identity (The effect would be no reuse of destination change between workflows)</p>
set_rev_id | `bool`<br><p>Copybara adds labels like 'GitOrigin-RevId' in the destination in order to track what was the latest change imported. For `CHANGE_REQUEST` workflows it is not used and is purely informational. This field allows to disable it for that mode. Destinations might ignore the flag.</p>
smart_prune | `bool`<br><p>By default CHANGE_REQUEST workflows cannot restore scrubbed files. This flag does a best-effort approach in restoring the non-affected snippets. For now we only revert the non-affected files. This only works for CHANGE_REQUEST mode.</p>
merge_import | `bool`<br><p>A migration mode that shells out to a diffing tool (default is diff3) to merge all files. The inputs to the diffing tool are (1) origin file (2) baseline file (3) destination file. This can be used to perpetuate destination-only changes in non source of truth repositories.</p>
autopatch_config | [`core.autopatch_config`](#coreautopatch_config) or `NoneType`<br><p>Configuration that describes the setting for automatic patch file generation</p>
migrate_noop_changes | `bool`<br><p>By default, Copybara tries to only migrate changes that affect origin_files or config files. This flag allows to include all the changes. Note that it might generate more empty changes errors. In `ITERATIVE` mode it might fail if some transformation is validating the message (Like has to contain 'PUBLIC' and the change doesn't contain it because it is internal).</p>
experimental_custom_rev_id | `string` or `NoneType`<br><p>Use this label name instead of the one provided by the origin. This is subject to change and there is no guarantee.</p>
description | `string` or `NoneType`<br><p>A description of what this workflow achieves</p>
checkout | `bool`<br><p>Allows disabling the checkout. The usage of this feature is rare. This could be used to update a file of your own repo when a dependant repo version changes and you are not interested on the files of the dependant repo, just the new version.</p>
reversible_check_ignore_files | [`glob`](#glob) or `NoneType`<br><p>Ignore the files matching the glob in the reversible check</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--baseline-for-merge-import`</span> | *string* | Origin baseline to use for merge import. This overrides any inferred origin baseline
<span style="white-space: nowrap;">`--change-request-from-sot-limit`</span> | *int* | Number of origin baseline changes to use for trying to match one in the destination. It can be used if the are many parent changes in the origin that are a no-op in the destination
<span style="white-space: nowrap;">`--change-request-from-sot-retry`</span> | *list* | Number of retries and delay between retries when we cannot find the baseline in the destination for CHANGE_REQUEST_FROM_SOT. For example '10,30,60' will retry three times. The first retry will be delayed 10s, the second one 30s and the third one 60s
<span style="white-space: nowrap;">`--change-request-parent, --change_request_parent`</span> | *string* | Commit revision to be used as parent when importing a commit using CHANGE_REQUEST workflow mode. This shouldn't be needed in general as Copybara is able to detect the parent commit message.
<span style="white-space: nowrap;">`--check-last-rev-state`</span> | *boolean* | If enabled, Copybara will validate that the destination didn't change since last-rev import for destination_files. Note that this flag doesn't work for CHANGE_REQUEST mode.
<span style="white-space: nowrap;">`--default-author`</span> | *string* | Use this author as default instead of the one in the config file.Format should be 'Foo Bar <foobar@example.com>'
<span style="white-space: nowrap;">`--diff-in-origin`</span> | *boolean* | When this flag is enabled, copybara will show different changes between last Revision and current revision in origin instead of in destination. NOTE: it Only works for SQUASH and ITERATIVE
<span style="white-space: nowrap;">`--force-author`</span> | *author* | Force the author to this. Note that this only changes the author before the transformations happen, you can still use the transformations to alter it.
<span style="white-space: nowrap;">`--force-message`</span> | *string* | Force the change description to this. Note that this only changes the message before the transformations happen, you can still use the transformations to alter it.
<span style="white-space: nowrap;">`--ignore-noop`</span> | *boolean* | Only warn about operations/transforms that didn't have any effect. For example: A transform that didn't modify any file, non-existent origin directories, etc.
<span style="white-space: nowrap;">`--import-noop-changes`</span> | *boolean* | By default Copybara will only try to migrate changes that could affect the destination. Ignoring changes that only affect excluded files in origin_files. This flag disables that behavior and runs for all the changes.
<span style="white-space: nowrap;">`--init-history`</span> | *boolean* | Import all the changes from the beginning of the history up to the resolved ref. For 'ITERATIVE' workflows this will import individual changes since the first one. For 'SQUASH' it will import the squashed change up to the resolved ref. WARNING: Use with care, this flag should be used only for the very first run of Copybara for a workflow.
<span style="white-space: nowrap;">`--iterative-limit-changes`</span> | *int* | Import just a number of changes instead of all the pending ones
<span style="white-space: nowrap;">`--last-rev`</span> | *string* | Last revision that was migrated to the destination
<span style="white-space: nowrap;">`--nosmart-prune`</span> | *boolean* | Disable smart prunning
<span style="white-space: nowrap;">`--notransformation-join`</span> | *boolean* | By default Copybara tries to join certain transformations in one so that it is more efficient. This disables the feature.
<span style="white-space: nowrap;">`--read-config-from-change`</span> | *boolean* | For each imported origin change, load the workflow's origin_files, destination_files and transformations from the config version of that change. The rest of the fields (more importantly, origin and destination) cannot change and the version from the first config will be used.
<span style="white-space: nowrap;">`--read-config-from-change-disable`</span> | *boolean* | --read-config-from-change is a arity 0 flag, this flag overrides it to override it being enabled.
<span style="white-space: nowrap;">`--same-version`</span> | *boolean* | Re-import the last version imported. This is useful for example to check that a refactor in a copy.bara.sky file doesn't introduce accidental changes.
<span style="white-space: nowrap;">`--squash-skip-history`</span> | *boolean* | Avoid exposing the history of changes that are being migrated. This is useful when we want to migrate a new repository but we don't want to expose all the change history to metadata.squash_notes.
<span style="white-space: nowrap;">`--threads`</span> | *int* | Number of threads to use when running transformations that change lot of files
<span style="white-space: nowrap;">`--threads-for-merge-import`</span> | *int* | Number of threads to use for executing the diff tool for the merge import mode.
<span style="white-space: nowrap;">`--threads-min-size`</span> | *int* | Minimum size of the lists to process to run them in parallel
<span style="white-space: nowrap;">`--to-folder`</span> | *boolean* | Sometimes a user wants to test what the outcome would be for a workflow without changing the configuration or adding an auxiliary testing workflow. This flags allowsto change an existing workflow to use folder.destination
<span style="white-space: nowrap;">`--workflow-identity-user`</span> | *string* | Use a custom string as a user for computing change identity



## core.autopatch_config

The configuration that describes automatic patch file generation



## destination_effect

Represents an effect that happened in the destination due to a single migration


#### Fields:

Name | Description
---- | -----------
destination_ref | [`destination_ref`](#destination_ref)<br><p>Destination reference updated/created. Might be null if there was no effect. Might be set even if the type is error (For example a synchronous presubmit test failed but a review was created).</p>
errors | `sequence of string`<br><p>List of errors that happened during the migration</p>
origin_refs | `sequence of `[`origin_ref`](#origin_ref)<br><p>List of origin changes that were included in this migration</p>
summary | `string`<br><p>Textual summary of what happened. Users of this class should not try to parse this field.</p>
type | `string`<br><p>Return the type of effect that happened: CREATED, UPDATED, NOOP, INSUFFICIENT_APPROVALS or ERROR</p>



## destination_reader

Handle to read from the destination

<a id="destination_reader.copy_destination_files" aria-hidden="true"></a>
### destination_reader.copy_destination_files

Copy files from the destination into the workdir.

`destination_reader.copy_destination_files(glob, path=None)`


#### Parameters:

Parameter | Description
--------- | -----------
glob | [`glob`](#glob)<br><p>Files to copy to the workdir, potentially overwriting files checked out from the origin.</p>
path | [`Path`](#path) or `NoneType`<br><p>Optional path to copy the files to</p>


#### Example:


##### Copy files from the destination's baseline:

This can be added to the transformations of your core.workflow:

```python
def _copy_destination_file(ctx):
   content = ctx.destination_reader().copy_destination_files(glob(include = ['path/to/**']))

transforms = [core.dynamic_transform(_copy_destination_file)]

```

Would copy all files in path/to/ from the destination baseline to the copybara workdir. The files do not have to be covered by origin_files nor destination_files, but will cause errors if they are not covered by destination_files and not moved or deleted.


<a id="destination_reader.file_exists" aria-hidden="true"></a>
### destination_reader.file_exists

Checks whether a given file exists in the destination.

`bool` `destination_reader.file_exists(path)`


#### Parameters:

Parameter | Description
--------- | -----------
path | `string`<br><p>Path to the file.</p>

<a id="destination_reader.read_file" aria-hidden="true"></a>
### destination_reader.read_file

Read a file from the destination.

`string` `destination_reader.read_file(path)`


#### Parameters:

Parameter | Description
--------- | -----------
path | `string`<br><p>Path to the file.</p>


#### Example:


##### Read a file from the destination's baseline:

This can be added to the transformations of your core.workflow:

```python
def _read_destination_file(ctx):
    content = ctx.destination_reader().read_file(path = 'path/to/my_file.txt')
    ctx.console.info(content)

    transforms = [core.dynamic_transform(_read_destination_file)]

```

Would print out the content of path/to/my_file.txt in the destination. The file does not have to be covered by origin_files nor destination_files.




## destination_ref

Reference to the change/review created/updated on the destination.


#### Fields:

Name | Description
---- | -----------
id | `string`<br><p>Destination reference id</p>
type | `string`<br><p>Type of reference created. Each destination defines its own and guarantees to be more stable than urls/ids</p>
url | `string`<br><p>Url, if any, of the destination change</p>



## endpoint

An origin or destination API in a feedback migration.


#### Fields:

Name | Description
---- | -----------
url | `string`<br><p>Return the URL of this endpoint.</p>

<a id="endpoint.new_destination_ref" aria-hidden="true"></a>
### endpoint.new_destination_ref

Creates a new destination reference out of this endpoint.

[`destination_ref`](#destination_ref) `endpoint.new_destination_ref(ref, type, url=None)`


#### Parameters:

Parameter | Description
--------- | -----------
ref | `string`<br><p>The reference.</p>
type | `string`<br><p>The type of this reference.</p>
url | `string` or `NoneType`<br><p>The url associated with this reference, if any.</p>

<a id="endpoint.new_origin_ref" aria-hidden="true"></a>
### endpoint.new_origin_ref

Creates a new origin reference out of this endpoint.

[`origin_ref`](#origin_ref) `endpoint.new_origin_ref(ref)`


#### Parameters:

Parameter | Description
--------- | -----------
ref | `string`<br><p>The reference.</p>



## endpoint_provider

An handle for an origin or destination API in a feedback migration.


#### Fields:

Name | Description
---- | -----------
url | `string`<br><p>Return the URL of this endpoint, if any.</p>

<a id="endpoint_provider.new_destination_ref" aria-hidden="true"></a>
### endpoint_provider.new_destination_ref

Creates a new destination reference out of this endpoint.

[`destination_ref`](#destination_ref) `endpoint_provider.new_destination_ref(ref, type, url=None)`


#### Parameters:

Parameter | Description
--------- | -----------
ref | `string`<br><p>The reference.</p>
type | `string`<br><p>The type of this reference.</p>
url | `string` or `NoneType`<br><p>The url associated with this reference, if any.</p>

<a id="endpoint_provider.new_origin_ref" aria-hidden="true"></a>
### endpoint_provider.new_origin_ref

Creates a new origin reference out of this endpoint.

[`origin_ref`](#origin_ref) `endpoint_provider.new_origin_ref(ref)`


#### Parameters:

Parameter | Description
--------- | -----------
ref | `string`<br><p>The reference.</p>



## feedback.context

Gives access to the feedback migration information and utilities. This context is a concrete implementation for feedback migrations.


#### Fields:

Name | Description
---- | -----------
action_name | `string`<br><p>The name of the current action.</p>
cli_labels | `dict[string, string]`<br><p>Access labels that a user passes through flag '--labels'. For example: --labels=foo:value1,bar:value2. Then it can access in this way:cli_labels['foo'].</p>
console | [`Console`](#console)<br><p>Get an instance of the console to report errors or warnings</p>
destination | [`endpoint`](#endpoint)<br><p>An object representing the destination. Can be used to query or modify the destination state</p>
feedback_name | `string`<br><p>The name of the Feedback migration calling this action.</p>
origin | [`endpoint`](#endpoint)<br><p>An object representing the origin. Can be used to query about the ref or modifying the origin state</p>
params | `dict`<br><p>Parameters for the function if created with core.action</p>
refs | `sequence of string`<br><p>A list containing string representations of the entities that triggered the event</p>

<a id="feedback.context.error" aria-hidden="true"></a>
### feedback.context.error

Returns an error action result.

`dynamic.action_result` `feedback.context.error(msg)`


#### Parameters:

Parameter | Description
--------- | -----------
msg | `string`<br><p>The error message</p>

<a id="feedback.context.noop" aria-hidden="true"></a>
### feedback.context.noop

Returns a no op action result with an optional message.

`dynamic.action_result` `feedback.context.noop(msg=None)`


#### Parameters:

Parameter | Description
--------- | -----------
msg | `string` or `NoneType`<br><p>The no op message</p>

<a id="feedback.context.record_effect" aria-hidden="true"></a>
### feedback.context.record_effect

Records an effect of the current action.

`feedback.context.record_effect(summary, origin_refs, destination_ref, errors=[], type="UPDATED")`


#### Parameters:

Parameter | Description
--------- | -----------
summary | `string`<br><p>The summary of this effect</p>
origin_refs | `sequence of `[`origin_ref`](#origin_ref)<br><p>The origin refs</p>
destination_ref | [`destination_ref`](#destination_ref)<br><p>The destination ref</p>
errors | `sequence of string`<br><p>An optional list of errors</p>
type | `string`<br><p>The type of migration effect:<br><ul><li><b>'CREATED'</b>: A new review or change was created.</li><li><b>'UPDATED'</b>: An existing review or change was updated.</li><li><b>'NOOP'</b>: The change was a noop.</li><li><b>'NOOP_AGAINST_PENDING_CHANGE'</b>: The change was a noop, relativeto an existing pending change.</li><li><b>'INSUFFICIENT_APPROVALS'</b>: The effect couldn't happen because the change doesn't have enough approvals.</li><li><b>'ERROR'</b>: A user attributable error happened that prevented the destination from creating/updating the change. <li><b>'STARTED'</b>: The initial effect of a migration that depends on a previous one. This allows to have 'dependant' migrations defined by users.<br>An example of this: a workflow migrates code from a Gerrit review to a GitHub PR, and a feedback migration migrates the test results from a CI in GitHub back to the Gerrit change.<br>This effect would be created on the former one.</li></ul></p>

<a id="feedback.context.success" aria-hidden="true"></a>
### feedback.context.success

Returns a successful action result.

`dynamic.action_result` `feedback.context.success()`



## feedback.finish_hook_context

Gives access to the feedback migration information and utilities. This context is a concrete implementation for 'after_migration' hooks.


#### Fields:

Name | Description
---- | -----------
action_name | `string`<br><p>The name of the current action.</p>
cli_labels | `dict[string, string]`<br><p>Access labels that a user passes through flag '--labels'. For example: --labels=foo:value1,bar:value2. Then it can access in this way:cli_labels['foo'].</p>
console | [`Console`](#console)<br><p>Get an instance of the console to report errors or warnings</p>
destination | [`endpoint`](#endpoint)<br><p>An object representing the destination. Can be used to query or modify the destination state</p>
effects | `sequence of `[`destination_effect`](#destination_effect)<br><p>The list of effects that happened in the destination</p>
origin | [`endpoint`](#endpoint)<br><p>An object representing the origin. Can be used to query about the ref or modifying the origin state</p>
params | `dict`<br><p>Parameters for the function if created with core.action</p>
revision | [`feedback.revision_context`](#feedbackrevision_context)<br><p>Get the requested/resolved revision</p>

<a id="feedback.finish_hook_context.error" aria-hidden="true"></a>
### feedback.finish_hook_context.error

Returns an error action result.

`dynamic.action_result` `feedback.finish_hook_context.error(msg)`


#### Parameters:

Parameter | Description
--------- | -----------
msg | `string`<br><p>The error message</p>

<a id="feedback.finish_hook_context.noop" aria-hidden="true"></a>
### feedback.finish_hook_context.noop

Returns a no op action result with an optional message.

`dynamic.action_result` `feedback.finish_hook_context.noop(msg=None)`


#### Parameters:

Parameter | Description
--------- | -----------
msg | `string` or `NoneType`<br><p>The no op message</p>

<a id="feedback.finish_hook_context.record_effect" aria-hidden="true"></a>
### feedback.finish_hook_context.record_effect

Records an effect of the current action.

`feedback.finish_hook_context.record_effect(summary, origin_refs, destination_ref, errors=[], type="UPDATED")`


#### Parameters:

Parameter | Description
--------- | -----------
summary | `string`<br><p>The summary of this effect</p>
origin_refs | `sequence of `[`origin_ref`](#origin_ref)<br><p>The origin refs</p>
destination_ref | [`destination_ref`](#destination_ref)<br><p>The destination ref</p>
errors | `sequence of string`<br><p>An optional list of errors</p>
type | `string`<br><p>The type of migration effect:<br><ul><li><b>'CREATED'</b>: A new review or change was created.</li><li><b>'UPDATED'</b>: An existing review or change was updated.</li><li><b>'NOOP'</b>: The change was a noop.</li><li><b>'NOOP_AGAINST_PENDING_CHANGE'</b>: The change was a noop, relativeto an existing pending change.</li><li><b>'INSUFFICIENT_APPROVALS'</b>: The effect couldn't happen because the change doesn't have enough approvals.</li><li><b>'ERROR'</b>: A user attributable error happened that prevented the destination from creating/updating the change. <li><b>'STARTED'</b>: The initial effect of a migration that depends on a previous one. This allows to have 'dependant' migrations defined by users.<br>An example of this: a workflow migrates code from a Gerrit review to a GitHub PR, and a feedback migration migrates the test results from a CI in GitHub back to the Gerrit change.<br>This effect would be created on the former one.</li></ul></p>

<a id="feedback.finish_hook_context.success" aria-hidden="true"></a>
### feedback.finish_hook_context.success

Returns a successful action result.

`dynamic.action_result` `feedback.finish_hook_context.success()`



## feedback.revision_context

Information about the revision request/resolved for the migration


#### Fields:

Name | Description
---- | -----------
labels | `dict[string, sequence of string]`<br><p>A dictionary with the labels detected for the requested/resolved revision.</p>



## filter_replace

A core.filter_replace transformation



## folder

Module for dealing with local filesystem folders

<a id="folder.destination" aria-hidden="true"></a>
### folder.destination

A folder destination is a destination that puts the output in a folder. It can be used both for testing or real production migrations.Given that folder destination does not support a lot of the features of real VCS, there are some limitations on how to use it:<ul><li>It requires passing a ref as an argument, as there is no way of calculating previous migrated changes. Alternatively, --last-rev can be used, which could migrate N changes.<li>Most likely, the workflow should use 'SQUASH' mode, as history is not supported.<li>If 'ITERATIVE' mode is used, a new temp directory will be created for each change migrated.</ul>

`destination` `folder.destination()`



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--folder-dir`</span> | *string* | Local directory to write the output of the migration to. If the directory exists, all files will be deleted. By default Copybara will generate a temporary directory, so you shouldn't need this.

<a id="folder.origin" aria-hidden="true"></a>
### folder.origin

A folder origin is a origin that uses a folder as input. The folder is specified via the source_ref argument.

`origin` `folder.origin(materialize_outside_symlinks=False)`


#### Parameters:

Parameter | Description
--------- | -----------
materialize_outside_symlinks | `bool`<br><p>By default folder.origin will refuse any symlink in the migration folder that is an absolute symlink or that refers to a file outside of the folder. If this flag is set, it will materialize those symlinks as regular files in the checkout directory.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--folder-origin-author`</span> | *string* | Deprecated. Please use '--force-author'. Author of the change being migrated from folder.origin()
<span style="white-space: nowrap;">`--folder-origin-ignore-invalid-symlinks`</span> | *boolean* | If an invalid symlink is found, ignore it instead of failing
<span style="white-space: nowrap;">`--folder-origin-message`</span> | *string* | Deprecated. Please use '--force-message'. Message of the change being migrated from folder.origin()



## format

Module for formatting the code to Google's style/guidelines

<a id="format.buildifier" aria-hidden="true"></a>
### format.buildifier

Formats the BUILD files using buildifier.

[`transformation`](#transformation) `format.buildifier(paths=glob(["**.bzl", "**/BUILD", "BUILD"]), type='auto', lint="OFF", lint_warnings=[])`


#### Parameters:

Parameter | Description
--------- | -----------
paths | [`glob`](#glob) or `NoneType`<br><p>Paths of the files to format relative to the workdir.</p>
type | `string` or `NoneType`<br><p>The type of the files. Can be 'auto', 'bzl', 'build' or 'workspace'. Note that this is not recommended to be set and might break in the future. The default is 'auto'. This mode formats as BUILD files "BUILD", "BUILD.bazel", "WORKSPACE" and "WORKSPACE.bazel" files. The rest as bzl files. Prefer to use those names for BUILD files instead of setting this flag.</p>
lint | `string` or `NoneType`<br><p>If buildifier --lint should be used. This fixes several common issues. Note that this transformation is difficult to revert. For example if it removes a load statement because is not used after removing a rule, then the reverse workflow needs to add back the load statement (core.replace or similar).  Possible values: `OFF`, `FIX`. Default is `OFF`</p>
lint_warnings | `sequence of string`<br><p>Warnings used in the lint mode. Default is buildifier default`</p>


#### Examples:


##### Default usage:

The default parameters formats all BUILD and bzl files in the checkout directory:

```python
format.buildifier()
```


##### Enable lint:

Enable lint for buildifier

```python
format.buildifier(lint = "FIX")
```


##### Using globs:

Globs can be used to match only certain files:

```python
format.buildifier(
    paths = glob(["foo/BUILD", "foo/**/BUILD"], exclude = ["foo/bar/BUILD"])
)
```

Formats all the BUILD files inside `foo` except for `foo/bar/BUILD`




**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--buildifier-batch-size`</span> | *int* | Process files in batches this size



## gerrit_api_obj

Gerrit API endpoint implementation for feedback migrations and after migration hooks.


#### Fields:

Name | Description
---- | -----------
url | `string`<br><p>Return the URL of this endpoint.</p>

<a id="gerrit_api_obj.delete_vote" aria-hidden="true"></a>
### gerrit_api_obj.delete_vote

Delete a label vote from an account owner on a Gerrit change.


`gerrit_api_obj.delete_vote(change_id, account_id, label_id)`


#### Parameters:

Parameter | Description
--------- | -----------
change_id | `string`<br><p>The Gerrit change id.</p>
account_id | `string`<br><p>The account owner who votes on label_id. Use 'me' or 'self' if the account owner makes this api call</p>
label_id | `string`<br><p>The name of the label.</p>

<a id="gerrit_api_obj.get_actions" aria-hidden="true"></a>
### gerrit_api_obj.get_actions

Retrieve the actions of a Gerrit change.

`dict[string, gerritapi.getActionInfo]` `gerrit_api_obj.get_actions(id, revision)`


#### Parameters:

Parameter | Description
--------- | -----------
id | `string`<br><p>The change id or change number.</p>
revision | `string`<br><p>The revision of the change.</p>

<a id="gerrit_api_obj.get_change" aria-hidden="true"></a>
### gerrit_api_obj.get_change

Retrieve a Gerrit change.

[`gerritapi.ChangeInfo`](#gerritapichangeinfo) `gerrit_api_obj.get_change(id, include_results=['LABELS'])`


#### Parameters:

Parameter | Description
--------- | -----------
id | `string`<br><p>The change id or change number.</p>
include_results | `sequence of string`<br><p>What to include in the response. See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#query-options</p>

<a id="gerrit_api_obj.list_changes" aria-hidden="true"></a>
### gerrit_api_obj.list_changes

Get changes from Gerrit based on a query. See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#list-changes.


`sequence of `[`gerritapi.ChangeInfo`](#gerritapichangeinfo) `gerrit_api_obj.list_changes(query, include_results=[])`


#### Parameters:

Parameter | Description
--------- | -----------
query | `string`<br><p>The query string to list changes by. See https://gerrit-review.googlesource.com/Documentation/user-search.html#_basic_change_search.</p>
include_results | `sequence of string`<br><p>What to include in the response. See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#query-options</p>

<a id="gerrit_api_obj.new_destination_ref" aria-hidden="true"></a>
### gerrit_api_obj.new_destination_ref

Creates a new destination reference out of this endpoint.

[`destination_ref`](#destination_ref) `gerrit_api_obj.new_destination_ref(ref, type, url=None)`


#### Parameters:

Parameter | Description
--------- | -----------
ref | `string`<br><p>The reference.</p>
type | `string`<br><p>The type of this reference.</p>
url | `string` or `NoneType`<br><p>The url associated with this reference, if any.</p>

<a id="gerrit_api_obj.new_origin_ref" aria-hidden="true"></a>
### gerrit_api_obj.new_origin_ref

Creates a new origin reference out of this endpoint.

[`origin_ref`](#origin_ref) `gerrit_api_obj.new_origin_ref(ref)`


#### Parameters:

Parameter | Description
--------- | -----------
ref | `string`<br><p>The reference.</p>

<a id="gerrit_api_obj.post_review" aria-hidden="true"></a>
### gerrit_api_obj.post_review

Post a review to a Gerrit change for a particular revision. The review will be authored by the user running the tool, or the role account if running in the service.


[`gerritapi.ReviewResult`](#gerritapireviewresult) `gerrit_api_obj.post_review(change_id, revision_id, review_input)`


#### Parameters:

Parameter | Description
--------- | -----------
change_id | `string`<br><p>The Gerrit change id.</p>
revision_id | `string`<br><p>The revision for which the comment will be posted.</p>
review_input | [`SetReviewInput`](#setreviewinput)<br><p>The review to post to Gerrit.</p>



## gerritapi.AccountInfo

Gerrit account information.


#### Fields:

Name | Description
---- | -----------
account_id | `string`<br><p>The numeric ID of the account.</p>
email | `string`<br><p>The email address the user prefers to be contacted through.<br>Only set if detailed account information is requested.<br>See option DETAILED_ACCOUNTS for change queries<br>and options DETAILS and ALL_EMAILS for account queries.</p>
name | `string`<br><p>The full name of the user.<br>Only set if detailed account information is requested.<br>See option DETAILED_ACCOUNTS for change queries<br>and option DETAILS for account queries.</p>
secondary_emails | `sequence of string`<br><p>A list of the secondary email addresses of the user.<br>Only set for account queries when the ALL_EMAILS option or the suggest parameter is set.<br>Secondary emails are only included if the calling user has the Modify Account, and hence is allowed to see secondary emails of other users.</p>
username | `string`<br><p>The username of the user.<br>Only set if detailed account information is requested.<br>See option DETAILED_ACCOUNTS for change queries<br>and option DETAILS for account queries.</p>



## gerritapi.ApprovalInfo

Gerrit approval information.


#### Fields:

Name | Description
---- | -----------
account_id | `string`<br><p>The numeric ID of the account.</p>
date | `string`<br><p>The time and date describing when the approval was made.</p>
email | `string`<br><p>The email address the user prefers to be contacted through.<br>Only set if detailed account information is requested.<br>See option DETAILED_ACCOUNTS for change queries<br>and options DETAILS and ALL_EMAILS for account queries.</p>
name | `string`<br><p>The full name of the user.<br>Only set if detailed account information is requested.<br>See option DETAILED_ACCOUNTS for change queries<br>and option DETAILS for account queries.</p>
secondary_emails | `sequence of string`<br><p>A list of the secondary email addresses of the user.<br>Only set for account queries when the ALL_EMAILS option or the suggest parameter is set.<br>Secondary emails are only included if the calling user has the Modify Account, and hence is allowed to see secondary emails of other users.</p>
username | `string`<br><p>The username of the user.<br>Only set if detailed account information is requested.<br>See option DETAILED_ACCOUNTS for change queries<br>and option DETAILS for account queries.</p>
value | `int`<br><p>The vote that the user has given for the label. If present and zero, the user is permitted to vote on the label. If absent, the user is not permitted to vote on that label.</p>



## gerritapi.ChangeInfo

Gerrit change information.


#### Fields:

Name | Description
---- | -----------
branch | `string`<br><p>The name of the target branch.<br>The refs/heads/ prefix is omitted.</p>
change_id | `string`<br><p>The Change-Id of the change.</p>
created | `string`<br><p>The timestamp of when the change was created.</p>
current_revision | `string`<br><p>The commit ID of the current patch set of this change.<br>Only set if the current revision is requested or if all revisions are requested.</p>
id | `string`<br><p>The ID of the change in the format "`<project>~<branch>~<Change-Id>`", where 'project', 'branch' and 'Change-Id' are URL encoded. For 'branch' the refs/heads/ prefix is omitted.</p>
labels | `dict[string, gerritapi.LabelInfo]`<br><p>The labels of the change as a map that maps the label names to LabelInfo entries.<br>Only set if labels or detailed labels are requested.</p>
messages | `sequence of `[`gerritapi.ChangeMessageInfo`](#gerritapichangemessageinfo)<br><p>Messages associated with the change as a list of ChangeMessageInfo entities.<br>Only set if messages are requested.</p>
number | `string`<br><p>The legacy numeric ID of the change.</p>
owner | [`gerritapi.AccountInfo`](#gerritapiaccountinfo)<br><p>The owner of the change as an AccountInfo entity.</p>
project | `string`<br><p>The name of the project.</p>
revisions | `dict[string, gerritapi.RevisionInfo]`<br><p>All patch sets of this change as a map that maps the commit ID of the patch set to a RevisionInfo entity.<br>Only set if the current revision is requested (in which case it will only contain a key for the current revision) or if all revisions are requested.</p>
status | `string`<br><p>The status of the change (NEW, MERGED, ABANDONED).</p>
subject | `string`<br><p>The subject of the change (header line of the commit message).</p>
submit_requirements | `sequence of SubmitRequirementResultInfo`<br><p>A list of the evaluated submit requirements for the change.</p>
submittable | `bool`<br><p>Whether the change has been approved by the project submit rules. Only set if requested via additional field SUBMITTABLE.</p>
submitted | `string`<br><p>The timestamp of when the change was submitted.</p>
topic | `string`<br><p>The topic to which this change belongs.</p>
updated | `string`<br><p>The timestamp of when the change was last updated.</p>



## gerritapi.ChangeMessageInfo

Gerrit change message information.


#### Fields:

Name | Description
---- | -----------
author | [`gerritapi.AccountInfo`](#gerritapiaccountinfo)<br><p>Author of the message as an AccountInfo entity.<br>Unset if written by the Gerrit system.</p>
date | `string`<br><p>The timestamp of when this identity was constructed.</p>
id | `string`<br><p>The ID of the message.</p>
message | `string`<br><p>The text left by the user.</p>
real_author | [`gerritapi.AccountInfo`](#gerritapiaccountinfo)<br><p>Real author of the message as an AccountInfo entity.<br>Set if the message was posted on behalf of another user.</p>
revision_number | `int`<br><p>Which patchset (if any) generated this message.</p>
tag | `string`<br><p>Value of the tag field from ReviewInput set while posting the review. NOTE: To apply different tags on on different votes/comments multiple invocations of the REST call are required.</p>



## gerritapi.ChangesQuery

Input for listing Gerrit changes. See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#list-changes



## gerritapi.CommitInfo

Gerrit commit information.


#### Fields:

Name | Description
---- | -----------
author | [`gerritapi.GitPersonInfo`](#gerritapigitpersoninfo)<br><p>The author of the commit as a GitPersonInfo entity.</p>
commit | `string`<br><p>The commit ID. Not set if included in a RevisionInfo entity that is contained in a map which has the commit ID as key.</p>
committer | [`gerritapi.GitPersonInfo`](#gerritapigitpersoninfo)<br><p>The committer of the commit as a GitPersonInfo entity.</p>
message | `string`<br><p>The commit message.</p>
parents | `sequence of `[`gerritapi.ParentCommitInfo`](#gerritapiparentcommitinfo)<br><p>The parent commits of this commit as a list of CommitInfo entities. In each parent only the commit and subject fields are populated.</p>
subject | `string`<br><p>The subject of the commit (header line of the commit message).</p>



## gerritapi.getActionInfo

Gerrit actions information.


#### Fields:

Name | Description
---- | -----------
enabled | `bool`<br><p>If true the action is permitted at this time and the caller is likely allowed to execute it.</p>
label | `string`<br><p>Short title to display to a user describing the action</p>



## gerritapi.GitPersonInfo

Git person information.


#### Fields:

Name | Description
---- | -----------
date | `string`<br><p>The timestamp of when this identity was constructed.</p>
email | `string`<br><p>The email address of the author/committer.</p>
name | `string`<br><p>The name of the author/committer.</p>



## gerritapi.LabelInfo

Gerrit label information.


#### Fields:

Name | Description
---- | -----------
all | `sequence of `[`gerritapi.ApprovalInfo`](#gerritapiapprovalinfo)<br><p>List of all approvals for this label as a list of ApprovalInfo entities. Items in this list may not represent actual votes cast by users; if a user votes on any label, a corresponding ApprovalInfo will appear in this list for all labels.</p>
approved | [`gerritapi.AccountInfo`](#gerritapiaccountinfo)<br><p>One user who approved this label on the change (voted the maximum value) as an AccountInfo entity.</p>
blocking | `bool`<br><p>If true, the label blocks submit operation. If not set, the default is false.</p>
default_value | `int`<br><p>The default voting value for the label. This value may be outside the range specified in permitted_labels.</p>
disliked | [`gerritapi.AccountInfo`](#gerritapiaccountinfo)<br><p>One user who disliked this label on the change (voted negatively, but not the minimum value) as an AccountInfo entity.</p>
recommended | [`gerritapi.AccountInfo`](#gerritapiaccountinfo)<br><p>One user who recommended this label on the change (voted positively, but not the maximum value) as an AccountInfo entity.</p>
rejected | [`gerritapi.AccountInfo`](#gerritapiaccountinfo)<br><p>One user who rejected this label on the change (voted the minimum value) as an AccountInfo entity.</p>
value | `int`<br><p>The voting value of the user who recommended/disliked this label on the change if it is not `"+1"`/`"-1"`.</p>
values | `dict[string, string]`<br><p>A map of all values that are allowed for this label. The map maps the values (`"-2"`, `"-1"`, `"0"`, `"+1"`, `"+2"`) to the value descriptions.</p>



## gerritapi.ParentCommitInfo

Gerrit parent commit information.


#### Fields:

Name | Description
---- | -----------
commit | `string`<br><p>The commit ID. Not set if included in a RevisionInfo entity that is contained in a map which has the commit ID as key.</p>
subject | `string`<br><p>The subject of the commit (header line of the commit message).</p>



## gerritapi.ReviewResult

Gerrit review result.


#### Fields:

Name | Description
---- | -----------
labels | `dict[string, int]`<br><p>Map of labels to values after the review was posted.</p>
ready | `bool`<br><p>If true, the change was moved from WIP to ready for review as a result of this action. Not set if false.</p>



## gerritapi.RevisionInfo

Gerrit revision information.


#### Fields:

Name | Description
---- | -----------
commit | [`gerritapi.CommitInfo`](#gerritapicommitinfo)<br><p>The commit of the patch set as CommitInfo entity.</p>
created | `string`<br><p>The timestamp of when the patch set was created.</p>
kind | `string`<br><p>The change kind. Valid values are REWORK, TRIVIAL_REBASE, MERGE_FIRST_PARENT_UPDATE, NO_CODE_CHANGE, and NO_CHANGE.</p>
patchset_number | `int`<br><p>The patch set number, or edit if the patch set is an edit.</p>
ref | `string`<br><p>The Git reference for the patch set.</p>
uploader | [`gerritapi.AccountInfo`](#gerritapiaccountinfo)<br><p>The uploader of the patch set as an AccountInfo entity.</p>



## gerritapi.SubmitRequirementExpressionInfo

Result of evaluating submit requirement expression


#### Fields:

Name | Description
---- | -----------
expression | `string`<br><p>The submit requirement expression as a string.</p>
fulfilled | `bool`<br><p>If true, this submit requirement result was created from a legacy SubmitRecord. Otherwise, it was created by evaluating a submit requirement.</p>
status | `string`<br><p>The status of the submit requirement evaluation.</p>



## git

Set of functions to define Git origins and destinations.



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--experiment-checkout-affected-files`</span> | *boolean* | If set, copybara will only checkout affected files at git origin. Note that this is experimental.
<span style="white-space: nowrap;">`--git-credential-helper-store-file`</span> | *string* | Credentials store file to be used. See https://git-scm.com/docs/git-credential-store
<span style="white-space: nowrap;">`--git-no-verify`</span> | *boolean* | Pass the '--no-verify' option to git pushes and commits to disable git commit hooks.
<span style="white-space: nowrap;">`--git-tag-overwrite`</span> | *boolean* | If set, copybara will force update existing git tag
<span style="white-space: nowrap;">`--nogit-credential-helper-store`</span> | *boolean* | Disable using credentials store. See https://git-scm.com/docs/git-credential-store
<span style="white-space: nowrap;">`--nogit-prompt`</span> | *boolean* | Disable username/password prompt and fail if no credentials are found. This flag sets the environment variable GIT_TERMINAL_PROMPT which is intended for automated jobs running Git https://git-scm.com/docs/git/2.3.0#git-emGITTERMINALPROMPTem

<a id="git.destination" aria-hidden="true"></a>
### git.destination

Creates a commit in a git repository using the transformed worktree.<br><br>For GitHub use git.github_destination. For creating Pull Requests in GitHub, use git.github_pr_destination. For creating a Gerrit change use git.gerrit_destination.<br><br>Given that Copybara doesn't ask for user/password in the console when doing the push to remote repos, you have to use ssh protocol, have the credentials cached or use a credential manager.

`destination` `git.destination(url, push='master', tag_name=None, tag_msg=None, fetch=None, partial_fetch=False, integrates=None, primary_branch_migration=False, checker=None)`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Indicates the URL to push to as well as the URL from which to get the parent commit</p>
push | `string`<br><p>Reference to use for pushing the change, for example 'main'.</p>
tag_name | `string` or `NoneType`<br><p>A template string that refers to a tag name. If tag_name exists, overwrite this tag only if flag git-tag-overwrite is set. Note that tag creation is best-effort and migration will succeed even if the tag cannot be created. Usage: Users can use a string or a string with a label. For instance ${label}_tag_name. And the value of label must be in changes' label list. Otherwise, tag won't be created.</p>
tag_msg | `string` or `NoneType`<br><p>A template string that refers to the commit msg of a tag. If set, we will create an annotated tag when tag_name is set. Usage: Users can use a string or a string with a label. For instance ${label}_message. And the value of label must be in changes' label list. Otherwise, tag will be created with sha1's commit msg.</p>
fetch | `string` or `NoneType`<br><p>Indicates the ref from which to get the parent commit. Defaults to push value if None</p>
partial_fetch | `bool`<br><p>This is an experimental feature that only works for certain origin globs.</p>
integrates | `sequence of git_integrate` or `NoneType`<br><p>Integrate changes from a url present in the migrated change label. Defaults to a semi-fake merge if COPYBARA_INTEGRATE_REVIEW label is present in the message</p>
primary_branch_migration | `bool`<br><p>When enabled, copybara will ignore the 'push' and 'fetch' params if either is 'master' or 'main' and instead try to establish the default git branch. If this fails, it will fall back to the param's declared value.<br>This is intended to help migrating to the new standard of using 'main' without breaking users relying on the legacy default.</p>
checker | `checker` or `NoneType`<br><p>A checker that can check leaks or other checks in the commit created. </p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--git-committer-email`</span> | *string* | If set, overrides the committer e-mail for the generated commits in git destination.
<span style="white-space: nowrap;">`--git-committer-name`</span> | *string* | If set, overrides the committer name for the generated commits in git destination.
<span style="white-space: nowrap;">`--git-destination-fetch`</span> | *string* | If set, overrides the git destination fetch reference.
<span style="white-space: nowrap;">`--git-destination-ignore-integration-errors`</span> | *boolean* | If an integration error occurs, ignore it and continue without the integrate
<span style="white-space: nowrap;">`--git-destination-last-rev-first-parent`</span> | *boolean* | Use git --first-parent flag when looking for last-rev in previous commits
<span style="white-space: nowrap;">`--git-destination-non-fast-forward`</span> | *boolean* | Allow non-fast-forward pushes to the destination. We only allow this when used with different push != fetch references.
<span style="white-space: nowrap;">`--git-destination-path`</span> | *string* | If set, the tool will use this directory for the local repository. Note that if the directory exists it needs to be a git repository. Copybara will revert any staged/unstaged changes. For example, you can override destination url with a local non-bare repo (or existing empty folder) with this flag.
<span style="white-space: nowrap;">`--git-destination-push`</span> | *string* | If set, overrides the git destination push reference.
<span style="white-space: nowrap;">`--git-destination-url`</span> | *string* | If set, overrides the git destination URL.
<span style="white-space: nowrap;">`--git-skip-checker`</span> | *boolean* | If true and git.destination has a configured checker, it will not be used in the migration.
<span style="white-space: nowrap;">`--nogit-destination-rebase`</span> | *boolean* | Don't rebase the change automatically for workflows CHANGE_REQUEST mode

<a id="git.gerrit_api" aria-hidden="true"></a>
### git.gerrit_api

Defines a feedback API endpoint for Gerrit, that exposes relevant Gerrit API operations.

[`endpoint_provider`](#endpoint_provider) `git.gerrit_api(url, checker=None)`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Indicates the Gerrit repo URL.</p>
checker | `checker` or `NoneType`<br><p>A checker for the Gerrit API transport.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--gerrit-change-id`</span> | *string* | ChangeId to use in the generated commit message. Use this flag if you want to reuse the same Gerrit review for an export.
<span style="white-space: nowrap;">`--gerrit-new-change`</span> | *boolean* | Create a new change instead of trying to reuse an existing one.
<span style="white-space: nowrap;">`--gerrit-topic`</span> | *string* | Gerrit topic to use

<a id="git.gerrit_destination" aria-hidden="true"></a>
### git.gerrit_destination

Creates a change in Gerrit using the transformed worktree. If this is used in iterative mode, then each commit pushed in a single Copybara invocation will have the correct commit parent. The reviews generated can then be easily done in the correct order without rebasing.

`destination` `git.gerrit_destination(url, fetch, push_to_refs_for=fetch value, submit=False, partial_fetch=False, notify=None, change_id_policy='FAIL_IF_PRESENT', allow_empty_diff_patchset=True, reviewers=[], cc=[], labels=[], api_checker=None, integrates=None, topic=None, gerrit_submit=False, primary_branch_migration=False, checker=None)`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Indicates the URL to push to as well as the URL from which to get the parent commit</p>
fetch | `string`<br><p>Indicates the ref from which to get the parent commit</p>
push_to_refs_for | `string` or `NoneType`<br><p>Review branch to push the change to, for example setting this to 'feature_x' causes the destination to push to 'refs/for/feature_x'. It defaults to 'fetch' value.</p>
submit | `bool`<br><p>If true, skip the push thru Gerrit refs/for/branch and directly push to branch. This is effectively a git.destination that sets a Change-Id</p>
partial_fetch | `bool`<br><p>This is an experimental feature that only works for certain origin globs.</p>
notify | `string` or `NoneType`<br><p>Type of Gerrit notify option (https://gerrit-review.googlesource.com/Documentation/user-upload.html#notify). Sends notifications by default.</p>
change_id_policy | `string`<br><p>What to do in the presence or absent of Change-Id in message:<ul>  <li>`'REQUIRE'`: Require that the change_id is present in the message as a valid label</li>  <li>`'FAIL_IF_PRESENT'`: Fail if found in message</li>  <li>`'REUSE'`: Reuse if present. Otherwise generate a new one</li>  <li>`'REPLACE'`: Replace with a new one if found</li></ul></p>
allow_empty_diff_patchset | `bool`<br><p>By default Copybara will upload a new PatchSet to Gerrit without checking the previous one. If this set to false, Copybara will download current PatchSet and check the diff against the new diff.</p>
reviewers | `sequence`<br><p>The list of the reviewers will be added to gerrit change reviewer listThe element in the list is: an email, for example: "foo@example.com" or label for example: ${SOME_GERRIT_REVIEWER}. These are under the condition of assuming that users have registered to gerrit repos</p>
cc | `sequence`<br><p>The list of the email addresses or users that will be CCed in the review. Can use labels as the `reviewers` field.</p>
labels | `sequence`<br><p>The list of labels to be pushed with the change. The format is the label along with the associated value. For example: Run-Presubmit+1</p>
api_checker | `checker` or `NoneType`<br><p>A checker for the Gerrit API endpoint provided for after_migration hooks. This field is not required if the workflow hooks don't use the origin/destination endpoints.</p>
integrates | `sequence of git_integrate` or `NoneType`<br><p>Integrate changes from a url present in the migrated change label. Defaults to a semi-fake merge if COPYBARA_INTEGRATE_REVIEW label is present in the message</p>
topic | `string` or `NoneType`<br><p>Sets the topic of the Gerrit change created.<br><br>By default it sets no topic. This field accepts a template with labels. For example: `"topic_${CONTEXT_REFERENCE}"`</p>
gerrit_submit | `bool`<br><p>By default, Copybara uses git commit/push to the main branch when submit = True.  If this flag is enabled, it will update the Gerrit change with the latest commit and submit using Gerrit.</p>
primary_branch_migration | `bool`<br><p>When enabled, copybara will ignore the 'push_to_refs_for' and 'fetch' params if either is 'master' or 'main' and instead try to establish the default git branch. If this fails, it will fall back to the param's declared value.<br>This is intended to help migrating to the new standard of using 'main' without breaking users relying on the legacy default.</p>
checker | `checker` or `NoneType`<br><p>A checker that validates the commit files & message. If `api_checker` is not set, it will also be used for checking API calls. If only `api_checker`is used, that checker will only apply to API calls.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--git-committer-email`</span> | *string* | If set, overrides the committer e-mail for the generated commits in git destination.
<span style="white-space: nowrap;">`--git-committer-name`</span> | *string* | If set, overrides the committer name for the generated commits in git destination.
<span style="white-space: nowrap;">`--git-destination-fetch`</span> | *string* | If set, overrides the git destination fetch reference.
<span style="white-space: nowrap;">`--git-destination-ignore-integration-errors`</span> | *boolean* | If an integration error occurs, ignore it and continue without the integrate
<span style="white-space: nowrap;">`--git-destination-last-rev-first-parent`</span> | *boolean* | Use git --first-parent flag when looking for last-rev in previous commits
<span style="white-space: nowrap;">`--git-destination-non-fast-forward`</span> | *boolean* | Allow non-fast-forward pushes to the destination. We only allow this when used with different push != fetch references.
<span style="white-space: nowrap;">`--git-destination-path`</span> | *string* | If set, the tool will use this directory for the local repository. Note that if the directory exists it needs to be a git repository. Copybara will revert any staged/unstaged changes. For example, you can override destination url with a local non-bare repo (or existing empty folder) with this flag.
<span style="white-space: nowrap;">`--git-destination-push`</span> | *string* | If set, overrides the git destination push reference.
<span style="white-space: nowrap;">`--git-destination-url`</span> | *string* | If set, overrides the git destination URL.
<span style="white-space: nowrap;">`--git-skip-checker`</span> | *boolean* | If true and git.destination has a configured checker, it will not be used in the migration.
<span style="white-space: nowrap;">`--nogit-destination-rebase`</span> | *boolean* | Don't rebase the change automatically for workflows CHANGE_REQUEST mode

<a id="git.gerrit_origin" aria-hidden="true"></a>
### git.gerrit_origin

Defines a Git origin for Gerrit reviews.

Implicit labels that can be used/exposed:

  - GERRIT_CHANGE_NUMBER: The change number for the Gerrit review.
  - GERRIT_CHANGE_ID: The change id for the Gerrit review.
  - GERRIT_CHANGE_DESCRIPTION: The description of the Gerrit review.
  - COPYBARA_INTEGRATE_REVIEW: A label that when exposed, can be used to integrate automatically in the reverse workflow.
  - GERRIT_CHANGE_BRANCH: The destination branch for thechange
  - GERRIT_CHANGE_TOPIC: The change topic
  - GERRIT_COMPLETE_CHANGE_ID: Complete Change-Id with project, branch and Change-Id
  - GERRIT_OWNER_EMAIL: Owner email
  - GERRIT_REVIEWER_EMAIL: Multiple value field with the email of the reviewers
  - GERRIT_CC_EMAIL: Multiple value field with the email of the people/groups in cc


`origin` `git.gerrit_origin(url, ref=None, submodules='NO', excluded_submodules=[], first_parent=True, partial_fetch=False, api_checker=None, patch=None, branch=None, describe_version=None, ignore_gerrit_noop=False, primary_branch_migration=False)`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Indicates the URL of the git repository</p>
ref | `string` or `NoneType`<br><p>DEPRECATED. Use git.origin for submitted branches.</p>
submodules | `string`<br><p>Download submodules. Valid values: NO, YES, RECURSIVE.</p>
excluded_submodules | `sequence of string`<br><p>A list of names (not paths, e.g. "foo" is the submodule name if [submodule "foo"] appears in the .gitmodules file) of submodules that will not be download even if 'submodules' is set to YES or RECURSIVE. </p>
first_parent | `bool`<br><p>If true, it only uses the first parent when looking for changes. Note that when disabled in ITERATIVE mode, it will try to do a migration for each change of the merged branch.</p>
partial_fetch | `bool`<br><p>If true, partially fetch git repository by only fetching affected files..</p>
api_checker | `checker` or `NoneType`<br><p>A checker for the Gerrit API endpoint provided for after_migration hooks. This field is not required if the workflow hooks don't use the origin/destination endpoints.</p>
patch | [`transformation`](#transformation) or `NoneType`<br><p>Patch the checkout dir. The difference with `patch.apply` transformation is that here we can apply it using three-way</p>
branch | `string` or `NoneType`<br><p>Limit the import to changes that are for this branch. By default imports everything.</p>
describe_version | `bool` or `NoneType`<br><p>Download tags and use 'git describe' to create four labels with a meaningful version identifier:<br><br>  - `GIT_DESCRIBE_CHANGE_VERSION`: The version for the change or changes being migrated. The value changes per change in `ITERATIVE` mode and will be the latest migrated change in `SQUASH` (In other words, doesn't include excluded changes). this is normally what users want to use.<br> - `GIT_DESCRIBE_REQUESTED_VERSION`: `git describe` for the requested/head version. Constant in `ITERATIVE` mode and includes filtered changes.<br>  -`GIT_DESCRIBE_FIRST_PARENT`: `git describe` for the first parent version.<br>  -`GIT_SEQUENTIAL_REVISION_NUMBER`: The sequential number of the commit. Falls back to the SHA1 if not applicable.<br></p>
ignore_gerrit_noop | `bool`<br><p>Option to not migrate Gerrit changes that do not change origin_files</p>
primary_branch_migration | `bool`<br><p>When enabled, copybara will ignore the 'ref' param if it is 'master' or 'main' and instead try to establish the default git branch. If this fails, it will fall back to the 'ref' param.<br>This is intended to help migrating to the new standard of using 'main' without breaking users relying on the legacy default.</p>

<a id="git.gerrit_trigger" aria-hidden="true"></a>
### git.gerrit_trigger

Defines a feedback trigger based on updates on a Gerrit change.

`trigger` `git.gerrit_trigger(url, checker=None, events=[])`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Indicates the Gerrit repo URL.</p>
checker | `checker` or `NoneType`<br><p>A checker for the Gerrit API transport provided by this trigger.</p>
events | `sequence of string` or `dict of sequence` or `NoneType`<br><p>Types of events to monitor. Optional. Can either be a list of event types or a dict of event types to particular events of that type, e.g. `['LABELS']` or `{'LABELS': 'my_label_name'}`.<br>Valid values for event types are: `'LABELS'`, `'SUBMIT_REQUIREMENTS'`</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--gerrit-change-id`</span> | *string* | ChangeId to use in the generated commit message. Use this flag if you want to reuse the same Gerrit review for an export.
<span style="white-space: nowrap;">`--gerrit-new-change`</span> | *boolean* | Create a new change instead of trying to reuse an existing one.
<span style="white-space: nowrap;">`--gerrit-topic`</span> | *string* | Gerrit topic to use

<a id="git.github_api" aria-hidden="true"></a>
### git.github_api

Defines a feedback API endpoint for GitHub, that exposes relevant GitHub API operations.

[`endpoint_provider`](#endpoint_provider) `git.github_api(url, checker=None)`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Indicates the GitHub repo URL.</p>
checker | `checker` or `NoneType`<br><p>A checker for the GitHub API transport.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--github-destination-delete-pr-branch`</span> | *boolean* | Overwrite git.github_destination delete_pr_branch field
<span style="white-space: nowrap;">`--gql-commit-history-override`</span> | *list* | Flag used to target GraphQL params 'first' arguments in the event the defaults are over or underusing the api ratelimit. This should be rarely used for repos that don't fit well in our defaults. E.g. 50,5,5 represent 50 commits, 5 PRs for each commit, 5 reviews per PR

<a id="git.github_destination" aria-hidden="true"></a>
### git.github_destination

Creates a commit in a GitHub repository branch (for example master). For creating PullRequest use git.github_pr_destination.

`destination` `git.github_destination(url, push='master', fetch=None, pr_branch_to_update=None, partial_fetch=False, delete_pr_branch=False, integrates=None, api_checker=None, primary_branch_migration=False, tag_name=None, tag_msg=None, checker=None)`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Indicates the URL to push to as well as the URL from which to get the parent commit</p>
push | `string`<br><p>Reference to use for pushing the change, for example 'main'.</p>
fetch | `string` or `NoneType`<br><p>Indicates the ref from which to get the parent commit. Defaults to push value if None</p>
pr_branch_to_update | `string` or `NoneType`<br><p>A template string that refers to a pull request branch in the same repository will be updated to current commit of this push branch only if pr_branch_to_update exists. The reason behind this field is that presubmiting changes creates and leaves a pull request open. By using this, we can automerge/close this type of pull requests. As a result, users will see this pr_branch_to_update as merged to this push branch. Usage: Users can use a string or a string with a label. For instance ${label}_pr_branch_name. And the value of label must be in changes' label list. Otherwise, nothing will happen.</p>
partial_fetch | `bool`<br><p>This is an experimental feature that only works for certain origin globs.</p>
delete_pr_branch | `bool` or `NoneType`<br><p>When `pr_branch_to_update` is enabled, it will delete the branch reference after the push to the branch and main branch (i.e master) happens. This allows to cleanup temporary branches created for testing.</p>
integrates | `sequence of git_integrate` or `NoneType`<br><p>Integrate changes from a url present in the migrated change label. Defaults to a semi-fake merge if COPYBARA_INTEGRATE_REVIEW label is present in the message</p>
api_checker | `checker` or `NoneType`<br><p>A checker for the Gerrit API endpoint provided for after_migration hooks. This field is not required if the workflow hooks don't use the origin/destination endpoints.</p>
primary_branch_migration | `bool`<br><p>When enabled, copybara will ignore the 'push' and 'fetch' params if either is 'master' or 'main' and instead try to establish the default git branch. If this fails, it will fall back to the param's declared value.<br>This is intended to help migrating to the new standard of using 'main' without breaking users relying on the legacy default.</p>
tag_name | `string` or `NoneType`<br><p>A template string that specifies to a tag name. If the tag already exists, copybara will only overwrite it if the --git-tag-overwrite flag is set.<br>Note that tag creation is best-effort and the migration will succeed even if the tag cannot be created. Usage: Users can use a string or a string with a label. For instance ${label}_tag_name. And the value of label must be in changes' label list. Otherwise, tag won't be created.</p>
tag_msg | `string` or `NoneType`<br><p>A template string that refers to the commit msg for a tag. If set, copybara willcreate an annotated tag with this custom message<br>Usage: Labels in the string will be resolved. E.g. .${label}_message.By default, the tag will be created with the labeled commit's message.</p>
checker | `checker` or `NoneType`<br><p>A checker that validates the commit files & message. If `api_checker` is not set, it will also be used for checking API calls. If only `api_checker`is used, that checker will only apply to API calls.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--git-committer-email`</span> | *string* | If set, overrides the committer e-mail for the generated commits in git destination.
<span style="white-space: nowrap;">`--git-committer-name`</span> | *string* | If set, overrides the committer name for the generated commits in git destination.
<span style="white-space: nowrap;">`--git-destination-fetch`</span> | *string* | If set, overrides the git destination fetch reference.
<span style="white-space: nowrap;">`--git-destination-ignore-integration-errors`</span> | *boolean* | If an integration error occurs, ignore it and continue without the integrate
<span style="white-space: nowrap;">`--git-destination-last-rev-first-parent`</span> | *boolean* | Use git --first-parent flag when looking for last-rev in previous commits
<span style="white-space: nowrap;">`--git-destination-non-fast-forward`</span> | *boolean* | Allow non-fast-forward pushes to the destination. We only allow this when used with different push != fetch references.
<span style="white-space: nowrap;">`--git-destination-path`</span> | *string* | If set, the tool will use this directory for the local repository. Note that if the directory exists it needs to be a git repository. Copybara will revert any staged/unstaged changes. For example, you can override destination url with a local non-bare repo (or existing empty folder) with this flag.
<span style="white-space: nowrap;">`--git-destination-push`</span> | *string* | If set, overrides the git destination push reference.
<span style="white-space: nowrap;">`--git-destination-url`</span> | *string* | If set, overrides the git destination URL.
<span style="white-space: nowrap;">`--git-skip-checker`</span> | *boolean* | If true and git.destination has a configured checker, it will not be used in the migration.
<span style="white-space: nowrap;">`--nogit-destination-rebase`</span> | *boolean* | Don't rebase the change automatically for workflows CHANGE_REQUEST mode

<a id="git.github_origin" aria-hidden="true"></a>
### git.github_origin

Defines a Git origin for a Github repository. This origin should be used for public branches. Use github_pr_origin for importing Pull Requests.

`origin` `git.github_origin(url, ref=None, submodules='NO', excluded_submodules=[], first_parent=True, partial_fetch=False, patch=None, describe_version=None, version_selector=None, primary_branch_migration=False)`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Indicates the URL of the git repository</p>
ref | `string` or `NoneType`<br><p>Represents the default reference that will be used for reading the revision from the git repository. For example: 'master'</p>
submodules | `string`<br><p>Download submodules. Valid values: NO, YES, RECURSIVE.</p>
excluded_submodules | `sequence of string`<br><p>A list of names (not paths, e.g. "foo" is the submodule name if [submodule "foo"] appears in the .gitmodules file) of submodules that will not be download even if 'submodules' is set to YES or RECURSIVE. </p>
first_parent | `bool`<br><p>If true, it only uses the first parent when looking for changes. Note that when disabled in ITERATIVE mode, it will try to do a migration for each change of the merged branch.</p>
partial_fetch | `bool`<br><p>If true, partially fetch git repository by only fetching affected files.</p>
patch | [`transformation`](#transformation) or `NoneType`<br><p>Patch the checkout dir. The difference with `patch.apply` transformation is that here we can apply it using three-way</p>
describe_version | `bool` or `NoneType`<br><p>Download tags and use 'git describe' to create four labels with a meaningful version identifier:<br><br>  - `GIT_DESCRIBE_CHANGE_VERSION`: The version for the change or changes being migrated. The value changes per change in `ITERATIVE` mode and will be the latest migrated change in `SQUASH` (In other words, doesn't include excluded changes). this is normally what users want to use.<br> - `GIT_DESCRIBE_REQUESTED_VERSION`: `git describe` for the requested/head version. Constant in `ITERATIVE` mode and includes filtered changes.<br>  -`GIT_DESCRIBE_FIRST_PARENT`: `git describe` for the first parent version.<br>  -`GIT_SEQUENTIAL_REVISION_NUMBER`: The sequential number of the commit. Falls back to the SHA1 if not applicable.<br></p>
version_selector | `VersionSelector` or `NoneType`<br><p>Select a custom version (tag)to migrate instead of 'ref'. Version selector is expected to match the whole refspec (e.g. 'refs/heads/${n1}')</p>
primary_branch_migration | `bool`<br><p>When enabled, copybara will ignore the 'ref' param if it is 'master' or 'main' and instead try to establish the default git branch. If this fails, it will fall back to the 'ref' param.<br>This is intended to help migrating to the new standard of using 'main' without breaking users relying on the legacy default.</p>

<a id="git.github_pr_destination" aria-hidden="true"></a>
### git.github_pr_destination

Creates changes in a new pull request in the destination.

`destination` `git.github_pr_destination(url, destination_ref='master', pr_branch=None, partial_fetch=False, allow_empty_diff=True, allow_empty_diff_merge_statuses=[], title=None, body=None, integrates=None, api_checker=None, update_description=False, primary_branch_migration=False, checker=None, draft=False)`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Url of the GitHub project. For example "https://github.com/google/copybara'"</p>
destination_ref | `string`<br><p>Destination reference for the change.</p>
pr_branch | `string` or `NoneType`<br><p>Customize the pull request branch. Any variable present in the message in the form of ${CONTEXT_REFERENCE} will be replaced by the corresponding stable reference (head, PR number, Gerrit change number, etc.).</p>
partial_fetch | `bool`<br><p>This is an experimental feature that only works for certain origin globs.</p>
allow_empty_diff | `bool`<br><p>By default, copybara migrates changes without checking existing PRs. If set, copybara will skip pushing a change to an existing PR only if the git three of the pending migrating change is the same as the existing PR.</p>
allow_empty_diff_merge_statuses | `sequence of string`<br><p>**EXPERIMENTAL feature.** By default, if `allow_empty_diff = False` is set, Copybara skips uploading the change if the tree hasn't changed and it can be merged. When this list is set with values from https://docs.github.com/en/github-ae@latest/graphql/reference/enums#mergestatestatus, it will still upload for the configured statuses. For example, if a user sets it to `['DIRTY', 'UNSTABLE', 'UNKNOWN']` (the recommended set to use), it wouldn't skip upload if test failed in GitHub for previous export, or if the change cannot be merged. **Note that this field is experimental and is subject to change by GitHub without notice**. Please consult Copybara team before using this field.</p>
title | `string` or `NoneType`<br><p>When creating (or updating if `update_description` is set) a pull request, use this title. By default it uses the change first line. This field accepts a template with labels. For example: `"Change ${CONTEXT_REFERENCE}"`</p>
body | `string` or `NoneType`<br><p>When creating (or updating if `update_description` is set) a pull request, use this body. By default it uses the change summary. This field accepts a template with labels. For example: `"Change ${CONTEXT_REFERENCE}"`</p>
integrates | `sequence of git_integrate` or `NoneType`<br><p>Integrate changes from a url present in the migrated change label. Defaults to a semi-fake merge if COPYBARA_INTEGRATE_REVIEW label is present in the message</p>
api_checker | `checker` or `NoneType`<br><p>A checker for the GitHub API endpoint provided for after_migration hooks. This field is not required if the workflow hooks don't use the origin/destination endpoints.</p>
update_description | `bool`<br><p>By default, Copybara only set the title and body of the PR when creating the PR. If this field is set to true, it will update those fields for every update.</p>
primary_branch_migration | `bool`<br><p>When enabled, copybara will ignore the 'desination_ref' param if it is 'master' or 'main' and instead try to establish the default git branch. If this fails, it will fall back to the param's declared value.<br>This is intended to help migrating to the new standard of using 'main' without breaking users relying on the legacy default.</p>
checker | `checker` or `NoneType`<br><p>A checker that validates the commit files & message. If `api_checker` is not set, it will also be used for checking API calls. If only `api_checker`is used, that checker will only apply to API calls.</p>
draft | `bool`<br><p>Flag create pull request as draft or not.</p>


#### Examples:


##### Common usage:

Create a branch by using copybara's computerIdentity algorithm:

```python
git.github_pr_destination(
        url = "https://github.com/google/copybara",
        destination_ref = "master",
    )
```


##### Using pr_branch with label:

Customize pr_branch with context reference:

```python
git.github_pr_destination(
        url = "https://github.com/google/copybara",
         destination_ref = "master",
         pr_branch = 'test_${CONTEXT_REFERENCE}',
    )
```


##### Using pr_branch with constant string:

Customize pr_branch with a constant string:

```python
git.github_pr_destination(
        url = "https://github.com/google/copybara",
        destination_ref = "master",
        pr_branch = 'test_my_branch',
    )
```




**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--git-committer-email`</span> | *string* | If set, overrides the committer e-mail for the generated commits in git destination.
<span style="white-space: nowrap;">`--git-committer-name`</span> | *string* | If set, overrides the committer name for the generated commits in git destination.
<span style="white-space: nowrap;">`--git-destination-fetch`</span> | *string* | If set, overrides the git destination fetch reference.
<span style="white-space: nowrap;">`--git-destination-ignore-integration-errors`</span> | *boolean* | If an integration error occurs, ignore it and continue without the integrate
<span style="white-space: nowrap;">`--git-destination-last-rev-first-parent`</span> | *boolean* | Use git --first-parent flag when looking for last-rev in previous commits
<span style="white-space: nowrap;">`--git-destination-non-fast-forward`</span> | *boolean* | Allow non-fast-forward pushes to the destination. We only allow this when used with different push != fetch references.
<span style="white-space: nowrap;">`--git-destination-path`</span> | *string* | If set, the tool will use this directory for the local repository. Note that if the directory exists it needs to be a git repository. Copybara will revert any staged/unstaged changes. For example, you can override destination url with a local non-bare repo (or existing empty folder) with this flag.
<span style="white-space: nowrap;">`--git-destination-push`</span> | *string* | If set, overrides the git destination push reference.
<span style="white-space: nowrap;">`--git-destination-url`</span> | *string* | If set, overrides the git destination URL.
<span style="white-space: nowrap;">`--git-skip-checker`</span> | *boolean* | If true and git.destination has a configured checker, it will not be used in the migration.
<span style="white-space: nowrap;">`--github-destination-pr-branch`</span> | *string* | If set, uses this branch for creating the pull request instead of using a generated one
<span style="white-space: nowrap;">`--github-destination-pr-create`</span> | *boolean* | If the pull request should be created
<span style="white-space: nowrap;">`--nogit-destination-rebase`</span> | *boolean* | Don't rebase the change automatically for workflows CHANGE_REQUEST mode

<a id="git.github_pr_origin" aria-hidden="true"></a>
### git.github_pr_origin

Defines a Git origin for Github pull requests.

Implicit labels that can be used/exposed:

  - GITHUB_PR_NUMBER: The pull request number if the reference passed was in the form of `https://github.com/project/pull/123`,  `refs/pull/123/head` or `refs/pull/123/master`.
  - COPYBARA_INTEGRATE_REVIEW: A label that when exposed, can be used to integrate automatically in the reverse workflow.
  - GITHUB_BASE_BRANCH: The name of the branch which serves as the base for the Pull Request.
  - GITHUB_BASE_BRANCH_SHA1: The SHA-1 of the commit used as baseline. Generally, the baseline commit is the point of divergence between the PR's 'base' and 'head' branches. When `use_merge = True` is specified, the baseline is instead the tip of the PR's base branch.
  - GITHUB_PR_USE_MERGE: Equal to 'true' if the workflow is importing a GitHub PR 'merge' commit and 'false' when importing a GitHub PR 'head' commit.
  - GITHUB_PR_TITLE: Title of the Pull Request.
  - GITHUB_PR_BODY: Body of the Pull Request.
  - GITHUB_PR_URL: GitHub url of the Pull Request.
  - GITHUB_PR_HEAD_SHA: The SHA-1 of the head commit of the pull request.
  - GITHUB_PR_USER: The login of the author the pull request.
  - GITHUB_PR_ASSIGNEE: A repeated label with the login of the assigned users.
  - GITHUB_PR_REVIEWER_APPROVER: A repeated label with the login of users that have participated in the review and that can approve the import. Only populated if `review_state` field is set. Every reviewers type matching `review_approvers` will be added to this list.
  - GITHUB_PR_REVIEWER_OTHER: A repeated label with the login of users that have participated in the review but cannot approve the import. Only populated if `review_state` field is set.


`origin` `git.github_pr_origin(url, use_merge=False, required_labels=[], required_status_context_names=[], required_check_runs=[], retryable_labels=[], submodules='NO', excluded_submodules=[], baseline_from_branch=False, first_parent=True, partial_fetch=False, state='OPEN', review_state=None, review_approvers=["COLLABORATOR", "MEMBER", "OWNER"], api_checker=None, patch=None, branch=None, describe_version=None)`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Indicates the URL of the GitHub repository</p>
use_merge | `bool`<br><p>If the content for refs/pull/&lt;ID&gt;/merge should be used instead of the PR head. The GitOrigin-RevId still will be the one from refs/pull/&lt;ID&gt;/head revision.</p>
required_labels | `sequence of string`<br><p>Required labels to import the PR. All the labels need to be present in order to migrate the Pull Request.</p>
required_status_context_names | `sequence of string`<br><p>A list of names of services which must all mark the PR with 'success' before it can be imported.<br><br>See https://docs.github.com/en/rest/reference/repos#statuses</p>
required_check_runs | `sequence of string`<br><p>A list of check runs which must all have a value of 'success' in order to import the PR.<br><br>See https://docs.github.com/en/rest/guides/getting-started-with-the-checks-api</p>
retryable_labels | `sequence of string`<br><p>Required labels to import the PR that should be retried. This parameter must be a subset of required_labels.</p>
submodules | `string`<br><p>Download submodules. Valid values: NO, YES, RECURSIVE.</p>
excluded_submodules | `sequence of string`<br><p>A list of names (not paths, e.g. "foo" is the submodule name if [submodule "foo"] appears in the .gitmodules file) of submodules that will not be download even if 'submodules' is set to YES or RECURSIVE. </p>
baseline_from_branch | `bool`<br><p>WARNING: Use this field only for github -> git CHANGE_REQUEST workflows.<br>When the field is set to true for CHANGE_REQUEST workflows it will find the baseline comparing the Pull Request with the base branch instead of looking for the *-RevId label in the commit message.</p>
first_parent | `bool`<br><p>If true, it only uses the first parent when looking for changes. Note that when disabled in ITERATIVE mode, it will try to do a migration for each change of the merged branch.</p>
partial_fetch | `bool`<br><p>This is an experimental feature that only works for certain origin globs.</p>
state | `string`<br><p>Only migrate Pull Request with that state. Possible values: `'OPEN'`, `'CLOSED'` or `'ALL'`. Default 'OPEN'</p>
review_state | `string` or `NoneType`<br><p>Required state of the reviews associated with the Pull Request Possible values: `'HEAD_COMMIT_APPROVED'`, `'ANY_COMMIT_APPROVED'`, `'HAS_REVIEWERS'` or `'ANY'`. Default `None`. This field is required if the user wants `GITHUB_PR_REVIEWER_APPROVER` and `GITHUB_PR_REVIEWER_OTHER` labels populated</p>
review_approvers | `sequence of string` or `NoneType`<br><p>The set of reviewer types that are considered for approvals. In order to have any effect, `review_state` needs to be set. GITHUB_PR_REVIEWER_APPROVER` will be populated for these types. See the valid types here: https://developer.github.com/v4/enum/commentauthorassociation/</p>
api_checker | `checker` or `NoneType`<br><p>A checker for the GitHub API endpoint provided for after_migration hooks. This field is not required if the workflow hooks don't use the origin/destination endpoints.</p>
patch | [`transformation`](#transformation) or `NoneType`<br><p>Patch the checkout dir. The difference with `patch.apply` transformation is that here we can apply it using three-way</p>
branch | `string` or `NoneType`<br><p>If set, it will only migrate pull requests for this base branch</p>
describe_version | `bool` or `NoneType`<br><p>Download tags and use 'git describe' to create four labels with a meaningful version identifier:<br><br>  - `GIT_DESCRIBE_CHANGE_VERSION`: The version for the change or changes being migrated. The value changes per change in `ITERATIVE` mode and will be the latest migrated change in `SQUASH` (In other words, doesn't include excluded changes). this is normally what users want to use.<br> - `GIT_DESCRIBE_REQUESTED_VERSION`: `git describe` for the requested/head version. Constant in `ITERATIVE` mode and includes filtered changes.<br>  -`GIT_DESCRIBE_FIRST_PARENT`: `git describe` for the first parent version.<br>  -`GIT_SEQUENTIAL_REVISION_NUMBER`: The sequential number of the commit. Falls back to the SHA1 if not applicable.<br></p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--github-force-import`</span> | *boolean* | Force import regardless of the state of the PR
<span style="white-space: nowrap;">`--github-pr-merge`</span> | *boolean* | Override merge bit from config
<span style="white-space: nowrap;">`--github-required-check-run`</span> | *list* | Required check runs in the Pull Request to be imported by github_pr_origin
<span style="white-space: nowrap;">`--github-required-label`</span> | *list* | Required labels in the Pull Request to be imported by github_pr_origin
<span style="white-space: nowrap;">`--github-required-status-context-name`</span> | *list* | Required status context names in the Pull Request to be imported by github_pr_origin
<span style="white-space: nowrap;">`--github-retryable-label`</span> | *list* | Required labels in the Pull Request that should be retryed to be imported by github_pr_origin
<span style="white-space: nowrap;">`--github-skip-required-check-runs`</span> | *boolean* | Skip checking check runs for importing Pull Requests. Note that this is dangerous as it might import an unsafe PR.
<span style="white-space: nowrap;">`--github-skip-required-labels`</span> | *boolean* | Skip checking labels for importing Pull Requests. Note that this is dangerous as it might import an unsafe PR.
<span style="white-space: nowrap;">`--github-skip-required-status-context-names`</span> | *boolean* | Skip checking status context names for importing Pull Requests. Note that this is dangerous as it might import an unsafe PR.
<span style="white-space: nowrap;">`--github-use-repo`</span> | *string* | Use a different git repository instead

<a id="git.github_trigger" aria-hidden="true"></a>
### git.github_trigger

Defines a feedback trigger based on updates on a GitHub PR.

`trigger` `git.github_trigger(url, checker=None, events=[])`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Indicates the GitHub repo URL.</p>
checker | `checker` or `NoneType`<br><p>A checker for the GitHub API transport provided by this trigger.</p>
events | `sequence of string` or `dict of sequence`<br><p>Types of events to subscribe. Can  either be a list of event types or a dict of event types to particular events of that type, e.g. `['CHECK_RUNS']` or `{'CHECK_RUNS': 'my_check_run_name'}`.<br>Valid values for event types are: `'ISSUES'`, `'ISSUE_COMMENT'`, `'PULL_REQUEST'`,  `'PULL_REQUEST_REVIEW_COMMENT'`, `'PUSH'`, `'STATUS'`, `'CHECK_RUNS'`</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--github-destination-delete-pr-branch`</span> | *boolean* | Overwrite git.github_destination delete_pr_branch field
<span style="white-space: nowrap;">`--gql-commit-history-override`</span> | *list* | Flag used to target GraphQL params 'first' arguments in the event the defaults are over or underusing the api ratelimit. This should be rarely used for repos that don't fit well in our defaults. E.g. 50,5,5 represent 50 commits, 5 PRs for each commit, 5 reviews per PR

<a id="git.integrate" aria-hidden="true"></a>
### git.integrate

Integrate changes from a url present in the migrated change label.

`git_integrate` `git.integrate(label="COPYBARA_INTEGRATE_REVIEW", strategy="FAKE_MERGE_AND_INCLUDE_FILES", ignore_errors=True)`


#### Parameters:

Parameter | Description
--------- | -----------
label | `string`<br><p>The migration label that will contain the url to the change to integrate.</p>
strategy | `string`<br><p>How to integrate the change:<br><ul> <li><b>'FAKE_MERGE'</b>: Add the url revision/reference as parent of the migration change but ignore all the files from the url. The commit message will be a standard merge one but will include the corresponding RevId label</li> <li><b>'FAKE_MERGE_AND_INCLUDE_FILES'</b>: Same as 'FAKE_MERGE' but any change to files that doesn't match destination_files will be included as part of the merge commit. So it will be a semi fake merge: Fake for destination_files but merge for non destination files.</li> <li><b>'INCLUDE_FILES'</b>: Same as 'FAKE_MERGE_AND_INCLUDE_FILES' but it it doesn't create a merge but only include changes not matching destination_files</li></ul></p>
ignore_errors | `bool`<br><p>If we should ignore integrate errors and continue the migration without the integrate</p>


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


<a id="git.latest_version" aria-hidden="true"></a>
### git.latest_version

DEPRECATED: Use core.latest_version.

Customize what version of the available branches and tags to pick. By default it ignores the reference passed as parameter. Using --force in the CLI will force to use the reference passed as argument instead.

`VersionSelector` `git.latest_version(refspec_format="refs/tags/${n0}.${n1}.${n2}", refspec_groups={'n0' : '[0-9]+', 'n1' : '[0-9]+', 'n2' : '[0-9]+'})`


#### Parameters:

Parameter | Description
--------- | -----------
refspec_format | `string`<br><p>The format of the branch/tag</p>
refspec_groups | `dict`<br><p>A set of named regexes that can be used to match part of the versions. Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax. Use the following nomenclature n0, n1, n2 for the version part (will use numeric sorting) or s0, s1, s2 (alphabetic sorting). Note that there can be mixed but the numbers cannot be repeated. In other words n0, s1, n2 is valid but not n0, s0, n1. n0 has more priority than n1. If there are fields where order is not important, use s(N+1) where N ist he latest sorted field. Example {"n0": "[0-9]+", "s1": "[a-z]+"}</p>

<a id="git.mirror" aria-hidden="true"></a>
### git.mirror

Mirror git references between repositories

`git.mirror(name, origin, destination, refspecs=['refs/heads/*'], prune=False, partial_fetch=False, description=None, actions=[])`


#### Parameters:

Parameter | Description
--------- | -----------
name | `string`<br><p>Migration name</p>
origin | `string`<br><p>Indicates the URL of the origin git repository</p>
destination | `string`<br><p>Indicates the URL of the destination git repository</p>
refspecs | `sequence of string`<br><p>Represents a list of git refspecs to mirror between origin and destination. For example 'refs/heads/*:refs/remotes/origin/*' will mirror any reference inside refs/heads to refs/remotes/origin.</p>
prune | `bool`<br><p>Remove remote refs that don't have a origin counterpart. Prune is ignored if actions are used (Action is in charge of doing the pruning)</p>
partial_fetch | `bool`<br><p>This is an experimental feature that only works for certain origin globs.</p>
description | `string` or `NoneType`<br><p>A description of what this migration achieves</p>
actions | `sequence`<br><p>Experimental feature. A list of mirror actions to perform, with the following semantics:<br>  - There is no guarantee of the order of execution.<br>  - Actions need to be independent from each other.<br>  - Failure in one action might prevent other actions from executing. --force can be used to continue for 'user' errors like non-fast-forward errors.<br><br>Actions will be in charge of doing the fetch, push, rebases, merges,etc.Only fetches/pushes for the declared refspec are allowed</p>

<a id="git.origin" aria-hidden="true"></a>
### git.origin

Defines a standard Git origin. For Git specific origins use: `github_origin` or `gerrit_origin`.<br><br>All the origins in this module accept several string formats as reference (When copybara is called in the form of `copybara config workflow reference`):<br><ul><li>**Branch name:** For example `master`</li><li>**An arbitrary reference:** `refs/changes/20/50820/1`</li><li>**A SHA-1:** Note that it has to be reachable from the default refspec</li><li>**A Git repository URL and reference:** `http://github.com/foo master`</li><li>**A GitHub pull request URL:** `https://github.com/some_project/pull/1784`</li></ul><br>So for example, Copybara can be invoked for a `git.origin` in the CLI as:<br>`copybara copy.bara.sky my_workflow https://github.com/some_project/pull/1784`<br>This will use the pull request as the origin URL and reference.

`origin` `git.origin(url, ref=None, submodules='NO', excluded_submodules=[], include_branch_commit_logs=False, first_parent=True, partial_fetch=False, patch=None, describe_version=None, version_selector=None, primary_branch_migration=False)`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Indicates the URL of the git repository</p>
ref | `string` or `NoneType`<br><p>Represents the default reference that will be used for reading the revision from the git repository. For example: 'master'</p>
submodules | `string`<br><p>Download submodules. Valid values: NO, YES, RECURSIVE.</p>
excluded_submodules | `sequence of string`<br><p>A list of names (not paths, e.g. "foo" is the submodule name if [submodule "foo"] appears in the .gitmodules file) of submodules that will not be download even if 'submodules' is set to YES or RECURSIVE. </p>
include_branch_commit_logs | `bool`<br><p>Whether to include raw logs of branch commits in the migrated change message.WARNING: This field is deprecated in favor of 'first_parent' one. This setting *only* affects merge commits.</p>
first_parent | `bool`<br><p>If true, it only uses the first parent when looking for changes. Note that when disabled in ITERATIVE mode, it will try to do a migration for each change of the merged branch.</p>
partial_fetch | `bool`<br><p>If true, partially fetch git repository by only fetching affected files.</p>
patch | [`transformation`](#transformation) or `NoneType`<br><p>Patch the checkout dir. The difference with `patch.apply` transformation is that here we can apply it using three-way</p>
describe_version | `bool` or `NoneType`<br><p>Download tags and use 'git describe' to create four labels with a meaningful version identifier:<br><br>  - `GIT_DESCRIBE_CHANGE_VERSION`: The version for the change or changes being migrated. The value changes per change in `ITERATIVE` mode and will be the latest migrated change in `SQUASH` (In other words, doesn't include excluded changes). this is normally what users want to use.<br> - `GIT_DESCRIBE_REQUESTED_VERSION`: `git describe` for the requested/head version. Constant in `ITERATIVE` mode and includes filtered changes.<br>  -`GIT_DESCRIBE_FIRST_PARENT`: `git describe` for the first parent version.<br>  -`GIT_SEQUENTIAL_REVISION_NUMBER`: The sequential number of the commit. Falls back to the SHA1 if not applicable.<br></p>
version_selector | `VersionSelector` or `NoneType`<br><p>Select a custom version (tag)to migrate instead of 'ref'. Version selector is expected to match the whole refspec (e.g. 'refs/heads/${n1}')</p>
primary_branch_migration | `bool`<br><p>When enabled, copybara will ignore the 'ref' param if it is 'master' or 'main' and instead try to establish the default git branch. If this fails, it will fall back to the 'ref' param.<br>This is intended to help migrating to the new standard of using 'main' without breaking users relying on the legacy default.</p>

<a id="git.review_input" aria-hidden="true"></a>
### git.review_input

Creates a review to be posted on Gerrit.

[`SetReviewInput`](#setreviewinput) `git.review_input(labels={}, message=None, tag=None)`


#### Parameters:

Parameter | Description
--------- | -----------
labels | `dict`<br><p>The labels to post.</p>
message | `string` or `NoneType`<br><p>The message to be added as review comment.</p>
tag | `string` or `NoneType`<br><p>Tag to be applied to the review, for instance 'autogenerated:copybara'.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--gerrit-change-id`</span> | *string* | ChangeId to use in the generated commit message. Use this flag if you want to reuse the same Gerrit review for an export.
<span style="white-space: nowrap;">`--gerrit-new-change`</span> | *boolean* | Create a new change instead of trying to reuse an existing one.
<span style="white-space: nowrap;">`--gerrit-topic`</span> | *string* | Gerrit topic to use



## git.mirrorContext

Expose methods to `git.mirror` actions to perform operations over git repositories


#### Fields:

Name | Description
---- | -----------
action_name | `string`<br><p>The name of the current action.</p>
cli_labels | `dict[string, string]`<br><p>Access labels that a user passes through flag '--labels'. For example: --labels=foo:value1,bar:value2. Then it can access in this way:cli_labels['foo'].</p>
console | [`Console`](#console)<br><p>Get an instance of the console to report errors or warnings</p>
params | `dict`<br><p>Parameters for the function if created with core.action</p>
refs | `sequence`<br><p>A list containing string representations of the entities that triggered the event</p>

<a id="git.mirrorContext.cherry_pick" aria-hidden="true"></a>
### git.mirrorContext.cherry_pick

Cherry-pick one or more commits to a branch

[`git_merge_result`](#git_merge_result) `git.mirrorContext.cherry_pick(branch, commits, add_commit_origin_info=True, merge_parent_number=None, allow_empty=False, fast_forward=False)`


#### Parameters:

Parameter | Description
--------- | -----------
branch | `string`<br><p></p>
commits | `sequence of string`<br><p>Commits to cherry-pick. An expression like foo..bar can be used to cherry-pick several commits. Note that 'HEAD' will refer to the `branch` HEAD, since cherry-pick requires a checkout of the branch before cherry-picking.</p>
add_commit_origin_info | `bool`<br><p>Add information about the origin of the commit (sha-1) to the message of the newcommit</p>
merge_parent_number | `unknown`<br><p>Specify the parent number for cherry-picking merge commits</p>
allow_empty | `bool`<br><p>Allow empty commits (noop commits)</p>
fast_forward | `bool`<br><p>Fast-forward commits if possible</p>

<a id="git.mirrorContext.create_branch" aria-hidden="true"></a>
### git.mirrorContext.create_branch

Merge one or more commits into a local branch.

`git.mirrorContext.create_branch(name, starting_point=None)`


#### Parameters:

Parameter | Description
--------- | -----------
name | `string`<br><p></p>
starting_point | `unknown`<br><p></p>

<a id="git.mirrorContext.destination_fetch" aria-hidden="true"></a>
### git.mirrorContext.destination_fetch

Fetch from the destination a list of refspecs. Note that fetch happens without pruning.

`bool` `git.mirrorContext.destination_fetch(refspec, prune=True, depth=None, partial_fetch=False)`


#### Parameters:

Parameter | Description
--------- | -----------
refspec | `sequence of string`<br><p></p>
prune | `bool`<br><p></p>
depth | `int` or `NoneType`<br><p>Sets number of commits to fetch. Setting to None (the default) means no limit to that number.</p>
partial_fetch | `bool`<br><p>If true, partially fetch only the minimum needed (e.g. don't fetch blobs if not used)</p>

<a id="git.mirrorContext.destination_push" aria-hidden="true"></a>
### git.mirrorContext.destination_push

Push to the destination a list of refspecs.

`git.mirrorContext.destination_push(refspec, prune=False)`


#### Parameters:

Parameter | Description
--------- | -----------
refspec | `sequence of string`<br><p></p>
prune | `bool`<br><p></p>

<a id="git.mirrorContext.error" aria-hidden="true"></a>
### git.mirrorContext.error

Returns an error action result.

`dynamic.action_result` `git.mirrorContext.error(msg)`


#### Parameters:

Parameter | Description
--------- | -----------
msg | `string`<br><p>The error message</p>

<a id="git.mirrorContext.merge" aria-hidden="true"></a>
### git.mirrorContext.merge

Merge one or more commits into a local branch.

[`git_merge_result`](#git_merge_result) `git.mirrorContext.merge(branch, commits, msg=None, fast_forward="FF")`


#### Parameters:

Parameter | Description
--------- | -----------
branch | `string`<br><p></p>
commits | `sequence of string`<br><p></p>
msg | `unknown`<br><p></p>
fast_forward | `string`<br><p>Valid values are FF (default), NO_FF, FF_ONLY.</p>

<a id="git.mirrorContext.noop" aria-hidden="true"></a>
### git.mirrorContext.noop

Returns a no op action result with an optional message.

`dynamic.action_result` `git.mirrorContext.noop(msg=None)`


#### Parameters:

Parameter | Description
--------- | -----------
msg | `string` or `NoneType`<br><p>The no op message</p>

<a id="git.mirrorContext.origin_fetch" aria-hidden="true"></a>
### git.mirrorContext.origin_fetch

Fetch from the origin a list of refspecs. Note that fetch happens without pruning.

`bool` `git.mirrorContext.origin_fetch(refspec, prune=True, depth=None, partial_fetch=False)`


#### Parameters:

Parameter | Description
--------- | -----------
refspec | `sequence of string`<br><p></p>
prune | `bool`<br><p></p>
depth | `int` or `NoneType`<br><p>Sets number of commits to fetch. Setting to None (the default) means no limit to that number.</p>
partial_fetch | `bool`<br><p>If true, partially fetch only the minimum needed (e.g. don't fetch blobs if not used)</p>

<a id="git.mirrorContext.rebase" aria-hidden="true"></a>
### git.mirrorContext.rebase

Rebase one or more commits into a local branch.

[`git_merge_result`](#git_merge_result) `git.mirrorContext.rebase(upstream, branch, newBase=None, conflict_advice=None)`


#### Parameters:

Parameter | Description
--------- | -----------
upstream | `string`<br><p>upstream branch with new changes</p>
branch | `string`<br><p>Current branch with specific commits that we want to rebase in top of the new `upstream` changes</p>
newBase | `unknown`<br><p>Move the rebased changes to a new branch (--into parameter in git rebase)</p>
conflict_advice | `unknown`<br><p>Additional information on how to solve the issue in case if conflict</p>

<a id="git.mirrorContext.record_effect" aria-hidden="true"></a>
### git.mirrorContext.record_effect

Records an effect of the current action.

`git.mirrorContext.record_effect(summary, origin_refs, destination_ref, errors=[], type="UPDATED")`


#### Parameters:

Parameter | Description
--------- | -----------
summary | `string`<br><p>The summary of this effect</p>
origin_refs | `sequence of `[`origin_ref`](#origin_ref)<br><p>The origin refs</p>
destination_ref | [`destination_ref`](#destination_ref)<br><p>The destination ref</p>
errors | `sequence of string`<br><p>An optional list of errors</p>
type | `string`<br><p>The type of migration effect:<br><ul><li><b>'CREATED'</b>: A new review or change was created.</li><li><b>'UPDATED'</b>: An existing review or change was updated.</li><li><b>'NOOP'</b>: The change was a noop.</li><li><b>'NOOP_AGAINST_PENDING_CHANGE'</b>: The change was a noop, relativeto an existing pending change.</li><li><b>'INSUFFICIENT_APPROVALS'</b>: The effect couldn't happen because the change doesn't have enough approvals.</li><li><b>'ERROR'</b>: A user attributable error happened that prevented the destination from creating/updating the change. <li><b>'STARTED'</b>: The initial effect of a migration that depends on a previous one. This allows to have 'dependant' migrations defined by users.<br>An example of this: a workflow migrates code from a Gerrit review to a GitHub PR, and a feedback migration migrates the test results from a CI in GitHub back to the Gerrit change.<br>This effect would be created on the former one.</li></ul></p>

<a id="git.mirrorContext.references" aria-hidden="true"></a>
### git.mirrorContext.references

Return a map of reference -> sha-1 for local references matching the refspec or all if no refspec is passed.

`dict[string, string]` `git.mirrorContext.references(refspec=[])`


#### Parameters:

Parameter | Description
--------- | -----------
refspec | `sequence of string`<br><p></p>

<a id="git.mirrorContext.success" aria-hidden="true"></a>
### git.mirrorContext.success

Returns a successful action result.

`dynamic.action_result` `git.mirrorContext.success()`



## git_merge_result

The result returned by git merge when used in Starlark. For example in git.mirror dynamic actions.


#### Fields:

Name | Description
---- | -----------
error | `bool`<br><p>True if the merge execution resulted in an error. False otherwise</p>
error_msg | `string`<br><p>Error message from git if the merge resulted in a conflict/error. Users must check error field before accessing this field.</p>



## github_api_obj

GitHub API endpoint implementation for feedback migrations and after migration hooks.


#### Fields:

Name | Description
---- | -----------
url | `string`<br><p>Return the URL of this endpoint.</p>

<a id="github_api_obj.add_label" aria-hidden="true"></a>
### github_api_obj.add_label

Add labels to a PR/issue

`github_api_obj.add_label(number, labels)`


#### Parameters:

Parameter | Description
--------- | -----------
number | `int`<br><p>Pull Request number</p>
labels | `sequence of string`<br><p>List of labels to add.</p>

<a id="github_api_obj.create_issue" aria-hidden="true"></a>
### github_api_obj.create_issue

Create a new issue.

`Issue` `github_api_obj.create_issue(title, body, assignees)`


#### Parameters:

Parameter | Description
--------- | -----------
title | `string`<br><p>Title of the issue</p>
body | `string`<br><p>Body of the issue.</p>
assignees | `sequence`<br><p>GitHub users to whom the issue will be assigned.</p>

<a id="github_api_obj.create_status" aria-hidden="true"></a>
### github_api_obj.create_status

Create or update a status for a commit. Returns the status created.

`github_api_status_obj` `github_api_obj.create_status(sha, state, context, description, target_url=None)`


#### Parameters:

Parameter | Description
--------- | -----------
sha | `string`<br><p>The SHA-1 for which we want to create or update the status</p>
state | `string`<br><p>The state of the commit status: 'success', 'error', 'pending' or 'failure'</p>
context | `string`<br><p>The context for the commit status. Use a value like 'copybara/import_successful' or similar</p>
description | `string`<br><p>Description about what happened</p>
target_url | `string` or `NoneType`<br><p>Url with expanded information about the event</p>

<a id="github_api_obj.delete_reference" aria-hidden="true"></a>
### github_api_obj.delete_reference

Delete a reference.

`github_api_obj.delete_reference(ref)`


#### Parameters:

Parameter | Description
--------- | -----------
ref | `string`<br><p>The name of the reference.</p>

<a id="github_api_obj.get_authenticated_user" aria-hidden="true"></a>
### github_api_obj.get_authenticated_user

Get autenticated user info, return null if not found

`github_api_user_obj` `github_api_obj.get_authenticated_user()`

<a id="github_api_obj.get_check_runs" aria-hidden="true"></a>
### github_api_obj.get_check_runs

Get the list of check runs for a sha. https://developer.github.com/v3/checks/runs/#check-runs

`sequence of github_check_run_obj` `github_api_obj.get_check_runs(sha)`


#### Parameters:

Parameter | Description
--------- | -----------
sha | `string`<br><p>The SHA-1 for which we want to get the check runs</p>

<a id="github_api_obj.get_combined_status" aria-hidden="true"></a>
### github_api_obj.get_combined_status

Get the combined status for a commit. Returns None if not found.

`github_api_combined_status_obj` `github_api_obj.get_combined_status(ref)`


#### Parameters:

Parameter | Description
--------- | -----------
ref | `string`<br><p>The SHA-1 or ref for which we want to get the combined status</p>

<a id="github_api_obj.get_commit" aria-hidden="true"></a>
### github_api_obj.get_commit

Get information for a commit in GitHub. Returns None if not found.

`github_api_github_commit_obj` `github_api_obj.get_commit(ref)`


#### Parameters:

Parameter | Description
--------- | -----------
ref | `string`<br><p>The SHA-1 for which we want to get the combined status</p>

<a id="github_api_obj.get_pull_request_comment" aria-hidden="true"></a>
### github_api_obj.get_pull_request_comment

Get a pull request comment

`github_api_pull_request_comment_obj` `github_api_obj.get_pull_request_comment(comment_id)`


#### Parameters:

Parameter | Description
--------- | -----------
comment_id | `string`<br><p>Comment identifier</p>

<a id="github_api_obj.get_pull_request_comments" aria-hidden="true"></a>
### github_api_obj.get_pull_request_comments

Get all pull request comments

`sequence of github_api_pull_request_comment_obj` `github_api_obj.get_pull_request_comments(number)`


#### Parameters:

Parameter | Description
--------- | -----------
number | `int`<br><p>Pull Request number</p>

<a id="github_api_obj.get_pull_requests" aria-hidden="true"></a>
### github_api_obj.get_pull_requests

Get Pull Requests for a repo

`sequence of github_api_pull_request_obj` `github_api_obj.get_pull_requests(head_prefix=None, base_prefix=None, state="OPEN", sort="CREATED", direction="ASC")`


#### Parameters:

Parameter | Description
--------- | -----------
head_prefix | `string` or `NoneType`<br><p>Only return PRs wher the branch name has head_prefix</p>
base_prefix | `string` or `NoneType`<br><p>Only return PRs where the destination branch name has base_prefix</p>
state | `string`<br><p>State of the Pull Request. Can be `"OPEN"`, `"CLOSED"` or `"ALL"`</p>
sort | `string`<br><p>Sort filter for retrieving the Pull Requests. Can be `"CREATED"`, `"UPDATED"` or `"POPULARITY"`</p>
direction | `string`<br><p>Direction of the filter. Can be `"ASC"` or `"DESC"`</p>

<a id="github_api_obj.get_reference" aria-hidden="true"></a>
### github_api_obj.get_reference

Get a reference SHA-1 from GitHub. Returns None if not found.

`github_api_ref_obj` `github_api_obj.get_reference(ref)`


#### Parameters:

Parameter | Description
--------- | -----------
ref | `string`<br><p>The name of the reference. For example: "refs/heads/branchName".</p>

<a id="github_api_obj.get_references" aria-hidden="true"></a>
### github_api_obj.get_references

Get all the reference SHA-1s from GitHub. Note that Copybara only returns a maximum number of 500.

`sequence of github_api_ref_obj` `github_api_obj.get_references()`

<a id="github_api_obj.list_issue_comments" aria-hidden="true"></a>
### github_api_obj.list_issue_comments

Lists comments for an issue

`sequence of github_api_issue_comment_obj` `github_api_obj.list_issue_comments(number)`


#### Parameters:

Parameter | Description
--------- | -----------
number | `int`<br><p>Issue or Pull Request number</p>

<a id="github_api_obj.new_destination_ref" aria-hidden="true"></a>
### github_api_obj.new_destination_ref

Creates a new destination reference out of this endpoint.

[`destination_ref`](#destination_ref) `github_api_obj.new_destination_ref(ref, type, url=None)`


#### Parameters:

Parameter | Description
--------- | -----------
ref | `string`<br><p>The reference.</p>
type | `string`<br><p>The type of this reference.</p>
url | `string` or `NoneType`<br><p>The url associated with this reference, if any.</p>

<a id="github_api_obj.new_origin_ref" aria-hidden="true"></a>
### github_api_obj.new_origin_ref

Creates a new origin reference out of this endpoint.

[`origin_ref`](#origin_ref) `github_api_obj.new_origin_ref(ref)`


#### Parameters:

Parameter | Description
--------- | -----------
ref | `string`<br><p>The reference.</p>

<a id="github_api_obj.post_issue_comment" aria-hidden="true"></a>
### github_api_obj.post_issue_comment

Post a comment on a issue.

`github_api_obj.post_issue_comment(number, comment)`


#### Parameters:

Parameter | Description
--------- | -----------
number | `int`<br><p>Issue or Pull Request number</p>
comment | `string`<br><p>Comment body to post.</p>

<a id="github_api_obj.update_pull_request" aria-hidden="true"></a>
### github_api_obj.update_pull_request

Update Pull Requests for a repo. Returns None if not found

`github_api_pull_request_obj` `github_api_obj.update_pull_request(number, title=None, body=None, state=None)`


#### Parameters:

Parameter | Description
--------- | -----------
number | `int`<br><p>Pull Request number</p>
title | `string` or `NoneType`<br><p>New Pull Request title</p>
body | `string` or `NoneType`<br><p>New Pull Request body</p>
state | `string` or `NoneType`<br><p>State of the Pull Request. Can be `"OPEN"`, `"CLOSED"`</p>

<a id="github_api_obj.update_reference" aria-hidden="true"></a>
### github_api_obj.update_reference

Update a reference to point to a new commit. Returns the info of the reference.

`github_api_ref_obj` `github_api_obj.update_reference(ref, sha, force)`


#### Parameters:

Parameter | Description
--------- | -----------
ref | `string`<br><p>The name of the reference.</p>
sha | `string`<br><p>The id for the commit status.</p>
force | `bool`<br><p>Indicates whether to force the update or to make sure the update is a fast-forward update. Leaving this out or setting it to false will make sure you're not overwriting work. Default: false</p>



## glob

A glob represents a set of relative filepaths in the Copybara workdir.



## Globals

Global functions available in Copybara

<a id="glob" aria-hidden="true"></a>
### glob

Returns an object which matches every file in the workdir that matches at least one pattern in include and does not match any of the patterns in exclude.

[`glob`](#glob) `glob(include, exclude=[])`


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


##### Glob union:

This is useful when you want to exclude a broad subset of files but you want to still include some of those files.

```python
glob(["folder/**"], exclude = ["folder/**.excluded"]) + glob(['folder/includeme.excluded'])
```

This matches all the files in `folder`, excludes all files in that folder that ends with `.excluded` but keeps `folder/includeme.excluded`<br><br>`+` operator for globs is equivalent to `OR` operation.


##### Glob difference:

This is another way to exclude a broad subset of files, but still include some of those files.

```python
glob(["folder/**"]) - glob(["folder/**.excluded"], exclude=["folder/includeme.excluded"])
```

This matches the same file as in the previous example.<br><br>`-` operator for globs is equivalent to a set difference operation.


<a id="new_author" aria-hidden="true"></a>
### new_author

Create a new author from a string with the form 'name <foo@bar.com>'

[`author`](#author) `new_author(author_string)`


#### Parameters:

Parameter | Description
--------- | -----------
author_string | `string`<br><p>A string representation of the author with the form 'name <foo@bar.com>'</p>


#### Example:


##### Create a new author:



```python
new_author('Foo Bar <foobar@myorg.com>')
```


<a id="parse_message" aria-hidden="true"></a>
### parse_message

Returns a ChangeMessage parsed from a well formed string.

[`ChangeMessage`](#changemessage) `parse_message(message)`


#### Parameters:

Parameter | Description
--------- | -----------
message | `string`<br><p>The contents of the change message</p>



## go

Module for Go related starlark operations

<a id="go.go_proxy_resolver" aria-hidden="true"></a>
### go.go_proxy_resolver

Go resolver that knows what to do with command line passed refs.

`VersionResolver` `go.go_proxy_resolver(module)`


#### Parameters:

Parameter | Description
--------- | -----------
module | `string`<br><p>The go module path name. e.g. github.com/google/gopacket. This will automatically normalize uppercase characters to '!{your_uppercase_character}' to escape them.</p>

<a id="go.go_proxy_version_list" aria-hidden="true"></a>
### go.go_proxy_version_list

Returns go proxy version list object

`GoProxyVersionList` `go.go_proxy_version_list(module, ref=None)`


#### Parameters:

Parameter | Description
--------- | -----------
module | `string`<br><p>The go module path name. e.g. github.com/google/gopacket. This will automatically normalize uppercase characters to '!{your_uppercase_character}' to escape them.</p>
ref | `string` or `NoneType`<br><p>This parameter is primarily used to track versions at specific branches and revisions. If a value is supplied, the returned version list will attempt to extract version data from ${ref}.info found with go proxy at the /@v/${ref}.info endpoint. You can leave off the .info suffix.</p>


#### Example:


##### Create a version list for a given go package:

Example of how create a version list for github.com/google/gopacket

```python
go.go_proxy_version_list(
        module='github.com/google/gopacket'
)
```




## hg

Set of functions to define Mercurial (Hg) origins and destinations.

<a id="hg.origin" aria-hidden="true"></a>
### hg.origin

<b>EXPERIMENTAL:</b> Defines a standard Mercurial (Hg) origin.

`origin` `hg.origin(url, ref="default")`


#### Parameters:

Parameter | Description
--------- | -----------
url | `string`<br><p>Indicates the URL of the Hg repository</p>
ref | `string`<br><p>Represents the default reference that will be used to read a revision from the repository. The reference defaults to `default`, the most recent revision on the default branch. References can be in a variety of formats:<br><ul> <li> A global identifier for a revision. Example: f4e0e692208520203de05557244e573e981f6c72</li><li> A bookmark in the repository.</li><li> A branch in the repository, which returns the tip of that branch. Example: default</li><li> A tag in the repository. Example: tip</li></ul></p>



## mapping_function

A function that given an object can map to another object



## metadata

Core transformations for the change metadata

<a id="metadata.add_header" aria-hidden="true"></a>
### metadata.add_header

Adds a header line to the commit message. Any variable present in the message in the form of ${LABEL_NAME} will be replaced by the corresponding label in the message. Note that this requires that the label is already in the message or in any of the changes being imported. The label in the message takes priority over the ones in the list of original messages of changes imported.


[`transformation`](#transformation) `metadata.add_header(text, ignore_label_not_found=False, new_line=True)`


#### Parameters:

Parameter | Description
--------- | -----------
text | `string`<br><p>The header text to include in the message. For example '[Import of foo ${LABEL}]'. This would construct a message resolving ${LABEL} to the corresponding label.</p>
ignore_label_not_found | `bool`<br><p>If a label used in the template is not found, ignore the error and don't add the header. By default it will stop the migration and fail.</p>
new_line | `bool`<br><p>If a new line should be added between the header and the original message. This allows to create messages like `HEADER: ORIGINAL_MESSAGE`</p>


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

[`transformation`](#transformation) `metadata.expose_label(name, new_name=label, separator="=", ignore_label_not_found=True, all=False, concat_separator=None)`


#### Parameters:

Parameter | Description
--------- | -----------
name | `string`<br><p>The label to search</p>
new_name | `string` or `NoneType`<br><p>The name to use in the message</p>
separator | `string`<br><p>The separator to use when adding the label to the message</p>
ignore_label_not_found | `bool`<br><p>If a label is not found, ignore the error and continue.</p>
all | `bool`<br><p>By default Copybara tries to find the most relevant instance of the label. First looking into the message and then looking into the changes in order. If this field is true it exposes all the matches instead.</p>
concat_separator | `unknown`<br><p>If all is set, copybara will expose multiple values in one per line. If a separator is specified, it will concat the values instead.</p>


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


##### Expose multiple labels, concatenating the values :

Expose all instances of a label in all the changes (SQUASH for example)

```python
metadata.expose_label('REVIEW_URL', all = True, concat_separator=',')
```

This would add a `REVIEW_URL: value1,value2` label to the message.


<a id="metadata.map_author" aria-hidden="true"></a>
### metadata.map_author

Map the author name and mail to another author. The mapping can be done by both name and mail or only using any of the two.

[`transformation`](#transformation) `metadata.map_author(authors, reversible=False, noop_reverse=False, fail_if_not_found=False, reverse_fail_if_not_found=False, map_all_changes=False)`


#### Parameters:

Parameter | Description
--------- | -----------
authors | `dict`<br><p>The author mapping. Keys can be in the form of 'Your Name', 'some@mail' or 'Your Name &lt;some@mail&gt;'. The mapping applies heuristics to know which field to use in the mapping. The value has to be always in the form of 'Your Name &lt;some@mail&gt;'</p>
reversible | `bool`<br><p>If the transform is automatically reversible. Workflows using the reverse of this transform will be able to automatically map values to keys.</p>
noop_reverse | `bool`<br><p>If true, the reversal of the transformation doesn't do anything. This is useful to avoid having to write `core.transformation(metadata.map_author(...), reversal = [])`.</p>
fail_if_not_found | `bool`<br><p>Fail if a mapping cannot be found. Helps discovering early authors that should be in the map</p>
reverse_fail_if_not_found | `bool`<br><p>Same as fail_if_not_found but when the transform is used in a inverse workflow.</p>
map_all_changes | `bool`<br><p>If all changes being migrated should be mapped. Useful for getting a mapped metadata.squash_notes. By default we only map the current author.</p>


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

[`transformation`](#transformation) `metadata.map_references(before, after, regex_groups={}, additional_import_labels=[])`


#### Parameters:

Parameter | Description
--------- | -----------
before | `string`<br><p>Template for origin references in the change message. Use a '${reference}' token to capture the actual references. E.g. if the origin uses links like 'http://changes?1234', the template would be 'http://changes?${reference}', with reference_regex = '[0-9]+'</p>
after | `string`<br><p>Format for destination references in the change message. Use a '${reference}' token to represent the destination reference.  E.g. if the destination uses links like 'http://changes?1234', the template would be 'http://changes?${reference}', with reference_regex = '[0-9]+'</p>
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
)
```

This would be translated into 'Fixes bug introduced in destination/123456', provided that a change with the proper label was found - the message remains unchanged otherwise.


<a id="metadata.remove_label" aria-hidden="true"></a>
### metadata.remove_label

Remove a label from the message

[`transformation`](#transformation) `metadata.remove_label(name)`


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


[`transformation`](#transformation) `metadata.replace_message(text, ignore_label_not_found=False)`


#### Parameters:

Parameter | Description
--------- | -----------
text | `string`<br><p>The template text to use for the message. For example '[Import of foo ${LABEL}]'. This would construct a message resolving ${LABEL} to the corresponding label.</p>
ignore_label_not_found | `bool`<br><p>If a label used in the template is not found, ignore the error and don't add the header. By default it will stop the migration and fail.</p>


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

[`transformation`](#transformation) `metadata.restore_author(label='ORIGINAL_AUTHOR', separator="=", search_all_changes=False)`


#### Parameters:

Parameter | Description
--------- | -----------
label | `string`<br><p>The label to use for restoring the author</p>
separator | `string`<br><p>The separator to use between the label and the value</p>
search_all_changes | `bool`<br><p>By default Copybara only looks in the last current change for the author label. This allows to do the search in all current changes (Only makes sense for SQUASH/CHANGE_REQUEST).</p>

<a id="metadata.save_author" aria-hidden="true"></a>
### metadata.save_author

For a given change, store a copy of the author as a label with the name ORIGINAL_AUTHOR.

[`transformation`](#transformation) `metadata.save_author(label='ORIGINAL_AUTHOR', separator="=")`


#### Parameters:

Parameter | Description
--------- | -----------
label | `string`<br><p>The label to use for storing the author</p>
separator | `string`<br><p>The separator to use between the label and the value</p>

<a id="metadata.scrubber" aria-hidden="true"></a>
### metadata.scrubber

Removes part of the change message using a regex

[`transformation`](#transformation) `metadata.scrubber(regex, msg_if_no_match=None, fail_if_no_match=False, replacement='')`


#### Parameters:

Parameter | Description
--------- | -----------
regex | `string`<br><p>Any text matching the regex will be removed. Note that the regex is runs in multiline mode.</p>
msg_if_no_match | `string` or `NoneType`<br><p>If set, Copybara will use this text when the scrubbing regex doesn't match.</p>
fail_if_no_match | `bool`<br><p>If set, msg_if_no_match must be None and then fail if the scrubbing regex doesn't match. </p>
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

The previous example is prone to leak confidential information since a developer could easily forget to include the CONFIDENTIAL label. A different approach for this is to scrub everything by default except what is explicitly allowed. For example, the following scrubber would remove anything not enclosed in &lt;public&gt;&lt;/public&gt; tags:


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




##### Use default msg when the scrubbing regex doesn't match:

Assign msg_if_no_match a default msg. For example:


```python
metadata.scrubber('^(?:\n|.)*<public>((?:\n|.)*)</public>(?:\n|.)*$', msg_if_no_match = 'Internal Change.', replacement = '$1')
```

So a message like:

```
this
is
very confidential
This is not public msg.

and this is a secret too
```

would be transformed into:

```
Internal Change.
```




##### Fail if the scrubbing regex doesn't match:

Set fail_if_no_match to true

```python
metadata.scrubber('^(?:\n|.)*<public>((?:\n|.)*)</public>(?:\n|.)*$', fail_if_no_match = True, replacement = '$1')
```

So a message like:

```
this
is
very confidential
but this is not public

and this is a secret too

```

This would fail. Error msg:

```
Scrubber regex: '^(?:\n|.)*<public>((?:\n|.)*)</public>(?:\n|.)*$' didn't match for description: this
is
very confidential
but this is not public

and this is a secret too
```




<a id="metadata.squash_notes" aria-hidden="true"></a>
### metadata.squash_notes

Generate a message that includes a constant prefix text and a list of changes included in the squash change.

[`transformation`](#transformation) `metadata.squash_notes(prefix='Copybara import of the project:\n\n', max=100, compact=True, show_ref=True, show_author=True, show_description=True, oldest_first=False, use_merge=True)`


#### Parameters:

Parameter | Description
--------- | -----------
prefix | `string`<br><p>A prefix to be printed before the list of commits.</p>
max | `int`<br><p>Max number of commits to include in the message. For the rest a comment like (and x more) will be included. By default 100 commits are included.</p>
compact | `bool`<br><p>If compact is set, each change will be shown in just one line</p>
show_ref | `bool`<br><p>If each change reference should be present in the notes</p>
show_author | `bool`<br><p>If each change author should be present in the notes</p>
show_description | `bool`<br><p>If each change description should be present in the notes</p>
oldest_first | `bool`<br><p>If set to true, the list shows the oldest changes first. Otherwise it shows the changes in descending order.</p>
use_merge | `bool`<br><p>If true then merge changes are included in the squash notes</p>


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

[`transformation`](#transformation) `metadata.use_last_change(author=True, message=True, default_message=None, use_merge=True)`


#### Parameters:

Parameter | Description
--------- | -----------
author | `bool`<br><p>Replace author with the last change author (Could still be the default author if not on the allowlist or using `authoring.overwrite`.)</p>
message | `bool`<br><p>Replace message with last change message.</p>
default_message | `string` or `NoneType`<br><p>Replace message with last change message.</p>
use_merge | `bool`<br><p>If true then merge changes are taken into account for looking for the last change.</p>

<a id="metadata.verify_match" aria-hidden="true"></a>
### metadata.verify_match

Verifies that a RegEx matches (or not matches) the change message. Does not transform anything, but will stop the workflow if it fails.

[`transformation`](#transformation) `metadata.verify_match(regex, verify_no_match=False)`


#### Parameters:

Parameter | Description
--------- | -----------
regex | `string`<br><p>The regex pattern to verify. The re2j pattern will be applied in multiline mode, i.e. '^' refers to the beginning of a file and '$' to its end.</p>
verify_no_match | `bool`<br><p>If true, the transformation will verify that the RegEx does not match.</p>


#### Example:


##### Check that a text is present in the change description:

Check that the change message contains a text enclosed in &lt;public&gt;&lt;/public&gt;:

```python
metadata.verify_match("<public>(.|\n)*</public>")
```




## origin_ref

Reference to the change/review in the origin.


#### Fields:

Name | Description
---- | -----------
ref | `string`<br><p>Origin reference ref</p>



## patch

Module for applying patches.

<a id="patch.apply" aria-hidden="true"></a>
### patch.apply

A transformation that applies the given patch files. If a path does not exist in a patch, it will be ignored.

[`transformation`](#transformation) `patch.apply(patches=[], excluded_patch_paths=[], series=None, strip=1)`


#### Parameters:

Parameter | Description
--------- | -----------
patches | `sequence of string`<br><p>The list of patchfiles to apply, relative to the current config file. The files will be applied relative to the checkout dir and the leading path component will be stripped (-p1).<br><br>If `series` is also specified, these patches will be applied before those ones.<br><br>**This field doesn't accept a glob.**</p>
excluded_patch_paths | `sequence of string`<br><p>The list of paths to exclude from each of the patches. Each of the paths will be excluded from all the patches. Note that these are not workdir paths, but paths relative to the patch itself. If not empty, the patch will be applied using 'git apply' instead of GNU Patch.</p>
series | `string` or `NoneType`<br><p>A file which contains a list of patches to apply. The patch files to apply are interpreted relative to this file and must be written one per line. The patches listed in this file will be applied relative to the checkout dir and the leading path component will be stripped (via the `-p1` flag).<br><br>You can generate a file which matches this format by running 'find . -name *.patch &#124; sort > series'.<br><br>If `patches` is also specified, those patches will be applied before these ones.</p>
strip | `int`<br><p>Number of segments to strip. (This sets the `-pX` flag, for example `-p0`, `-p1`, etc.) By default it uses `-p1`.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--patch-bin`</span> | *string* | Path for GNU Patch command
<span style="white-space: nowrap;">`--patch-skip-version-check`</span> | *boolean* | Skip checking the version of patch and assume it is fine
<span style="white-space: nowrap;">`--patch-use-git-apply`</span> | *boolean* | Don't use GNU Patch and instead use 'git apply'
<span style="white-space: nowrap;">`--quilt-bin`</span> | *string* | Path to quilt command

<a id="patch.quilt_apply" aria-hidden="true"></a>
### patch.quilt_apply

A transformation that applies and updates patch files using Quilt. Compared to `patch.apply`, this transformation supports updating the content of patch files if they can be successfully applied with fuzz. The patch files must be included in the destination_files glob in order to get updated. Underneath, Copybara runs `quilt import; quilt push; quilt refresh` for each patch file in the `series` file in order. Currently, all patch files and the `series` file must reside in a "patches" sub-directory under the root directory containing the migrated code. This means it has the limitation that the migrated code itself cannot contain a directory with the name "patches".

[`transformation`](#transformation) `patch.quilt_apply(series)`


#### Parameters:

Parameter | Description
--------- | -----------
series | `string`<br><p>A file which contains a list of patches to apply. It is similar to the `series` parameter in `patch.apply` transformation, and is required for Quilt. Patches listed in this file will be applied relative to the checkout dir, and the leading path component is stripped via the `-p1` flag. Currently this file should be the `patches/series` file in the root directory of the migrated code.</p>


#### Example:


##### Workflow to apply and update patches:

Suppose the destination repository's directory structure looks like:
```
source_root/BUILD
source_root/copy.bara.sky
source_root/migrated_file1
source_root/migrated_file2
source_root/patches/series
source_root/patches/patch1.patch
```
Then the transformations in `source_root/copy.bara.sky` should look like:

```python
[
    patch.quilt_apply(series = "patches/series"),
    core.move("", "source_root"),
]
```

In this example, `patch1.patch` is applied to `migrated_file1` and/or `migrated_file2`. `patch1.patch` itself will be updated during the migration if it is applied with fuzz.




**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--patch-bin`</span> | *string* | Path for GNU Patch command
<span style="white-space: nowrap;">`--patch-skip-version-check`</span> | *boolean* | Skip checking the version of patch and assume it is fine
<span style="white-space: nowrap;">`--patch-use-git-apply`</span> | *boolean* | Don't use GNU Patch and instead use 'git apply'
<span style="white-space: nowrap;">`--quilt-bin`</span> | *string* | Path to quilt command



## Path

Represents a path in the checkout directory


#### Fields:

Name | Description
---- | -----------
attr | [`PathAttributes`](#pathattributes)<br><p>Get the file attributes, for example size.</p>
name | `string`<br><p>Filename of the path. For foo/bar/baz.txt it would be baz.txt</p>
parent | `unknown`<br><p>Get the parent path</p>
path | `string`<br><p>Full path relative to the checkout directory</p>

<a id="path.read_symlink" aria-hidden="true"></a>
### path.read_symlink

Read the symlink

[`Path`](#path) `path.read_symlink()`

<a id="path.relativize" aria-hidden="true"></a>
### path.relativize

Constructs a relative path between this path and a given path. For example:<br>    path('a/b').relativize('a/b/c/d')<br>returns 'c/d'

[`Path`](#path) `path.relativize(other)`


#### Parameters:

Parameter | Description
--------- | -----------
other | [`Path`](#path)<br><p>The path to relativize against this path</p>

<a id="path.resolve" aria-hidden="true"></a>
### path.resolve

Resolve the given path against this path.

[`Path`](#path) `path.resolve(child)`


#### Parameters:

Parameter | Description
--------- | -----------
child | `string` or [`Path`](#path)<br><p>Resolve the given path against this path. The parameter can be a string or a Path.</p>

<a id="path.resolve_sibling" aria-hidden="true"></a>
### path.resolve_sibling

Resolve the given path against this path.

[`Path`](#path) `path.resolve_sibling(other)`


#### Parameters:

Parameter | Description
--------- | -----------
other | `string` or [`Path`](#path)<br><p>Resolve the given path against this path. The parameter can be a string or a Path.</p>



## PathAttributes

Represents a path attributes like size.


#### Fields:

Name | Description
---- | -----------
size | `int`<br><p>The size of the file. Throws an error if file size > 2GB.</p>
symlink | `bool`<br><p>Returns true if it is a symlink</p>



## re2

Set of functions to work with regexes in Copybara.

<a id="re2.compile" aria-hidden="true"></a>
### re2.compile

Create a regex pattern

[`re2_pattern`](#re2_pattern) `re2.compile(regex)`


#### Parameters:

Parameter | Description
--------- | -----------
regex | `string`<br><p></p>


#### Example:


##### Simple regex:

Patterns need to be compiled before using them:

```python
re2.compile("a(.*)b").matches('accccb')
```




## re2_matcher

A RE2 regex pattern matcher object to perform regexes in Starlark

<a id="re2_matcher.end" aria-hidden="true"></a>
### re2_matcher.end

Return the end position of a matching group

`int` `re2_matcher.end(group=0)`


#### Parameters:

Parameter | Description
--------- | -----------
group | `int` or `string`<br><p></p>

<a id="re2_matcher.find" aria-hidden="true"></a>
### re2_matcher.find

Return true if the string matches the regex pattern.

`bool` `re2_matcher.find(start=None)`


#### Parameters:

Parameter | Description
--------- | -----------
start | `int` or `NoneType`<br><p>The input position where the search begins</p>

<a id="re2_matcher.group" aria-hidden="true"></a>
### re2_matcher.group

Return a matching group

`string` `re2_matcher.group(group=0)`


#### Parameters:

Parameter | Description
--------- | -----------
group | `int` or `string`<br><p></p>

<a id="re2_matcher.group_count" aria-hidden="true"></a>
### re2_matcher.group_count

Return the number of groups found for a match

`int` `re2_matcher.group_count()`

<a id="re2_matcher.matches" aria-hidden="true"></a>
### re2_matcher.matches

Return true if the string matches the regex pattern.

`bool` `re2_matcher.matches()`

<a id="re2_matcher.replace_all" aria-hidden="true"></a>
### re2_matcher.replace_all

Replace all instances matching the regex

`string` `re2_matcher.replace_all(replacement=0)`


#### Parameters:

Parameter | Description
--------- | -----------
replacement | `string`<br><p></p>

<a id="re2_matcher.replace_first" aria-hidden="true"></a>
### re2_matcher.replace_first

Replace the first instance matching the regex

`string` `re2_matcher.replace_first(replacement=0)`


#### Parameters:

Parameter | Description
--------- | -----------
replacement | `string`<br><p></p>

<a id="re2_matcher.start" aria-hidden="true"></a>
### re2_matcher.start

Return the start position of a matching group

`int` `re2_matcher.start(group=0)`


#### Parameters:

Parameter | Description
--------- | -----------
group | `int` or `string`<br><p></p>



## re2_pattern

A RE2 regex pattern object to perform regexes in Starlark

<a id="re2_pattern.matcher" aria-hidden="true"></a>
### re2_pattern.matcher

Return a Matcher for the given input.

[`re2_matcher`](#re2_matcher) `re2_pattern.matcher(input)`


#### Parameters:

Parameter | Description
--------- | -----------
input | `string`<br><p></p>

<a id="re2_pattern.matches" aria-hidden="true"></a>
### re2_pattern.matches

Return true if the string matches the regex pattern

`bool` `re2_pattern.matches(input)`


#### Parameters:

Parameter | Description
--------- | -----------
input | `string`<br><p></p>



## remotefiles

Functions to access remote files not in either repo.



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--remote-http-files-connection-timeout`</span> | *duration* | Timeout for the fetch operation, e.g. 30s.  Example values: 30s, 20m, 1h, etc.

<a id="remotefiles.github_archive" aria-hidden="true"></a>
### remotefiles.github_archive

A tarball for a specific SHA1 on GitHub. Experimental.

`remote_http_file.GitHubArchive` `remotefiles.github_archive(project=[], revision=[], type='TARBALL')`


#### Parameters:

Parameter | Description
--------- | -----------
project | `string`<br><p>The GitHub project from which to load the file, e.g. google/copybara</p>
revision | `string`<br><p>The revision to download from the project, typically a commit SHA1.</p>
type | `string`<br><p>Archive type to download, options are 'TARBALL' or 'ZIP'.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--remote-http-files-connection-timeout`</span> | *duration* | Timeout for the fetch operation, e.g. 30s.  Example values: 30s, 20m, 1h, etc.

<a id="remotefiles.origin" aria-hidden="true"></a>
### remotefiles.origin

Defines a remote file origin. This is a WIP and experimental. Do not use. 

`origin` `remotefiles.origin(author='Copybara <noreply@copybara.io>', message='Placeholder message', unpack_method='AS_IS', archive_source='', version_list=None, origin_version_selector=None, version_resolver=None, version_selector=None, base_url=None)`


#### Parameters:

Parameter | Description
--------- | -----------
author | `string`<br><p>Author to attribute the change to</p>
message | `string`<br><p>Message to attach to the change</p>
unpack_method | `string`<br><p>The method by which to unpack the remote file. Currently 'ZIP', 'TAR', and 'AS_IS' are supported.</p>
archive_source | `string`<br><p>Template or literal URL to download archive from. Optionally you can use ${VERSION} in your URL string as placeholder for later resolved versions during origin checkout. E.g. 'https://proxy.golang.org/mymodule/@v/${VERSION}.zip'</p>
version_list | `VersionList` or `NoneType`<br><p>Version list to select versions on. Omit to create a versionless origin.</p>
origin_version_selector | `VersionSelector` or `NoneType`<br><p>Version selector used to select on version_list. Omit to create a versionless origin.</p>
version_resolver | `VersionResolver` or `NoneType`<br><p>Version resolvers are used to resolve refs to specific versions. Primarily used when command line refs are provided and accompanied by the '--force' or '--version-selector-use-cli-ref' flag.</p>
version_selector | `unknown`<br><p>Object that contains version selecting logic. DEPRECATED.</p>
base_url | `unknown`<br><p>base URL to construct the full URL. DEPRECATED.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--remote-http-files-connection-timeout`</span> | *duration* | Timeout for the fetch operation, e.g. 30s.  Example values: 30s, 20m, 1h, etc.



## SetReviewInput

Input for posting a review to Gerrit. See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#review-input



## struct

Immutable struct type.

<a id="struct" aria-hidden="true"></a>
### struct

Creates a new immutable struct. Structs with the same keys/values are equal. The struct's keys and values are passed in as keyword arguments.

`StructImpl` `struct()`


#### Example:


##### Create a struct:

Structs are immutable objects to group values.

```python
my_struct = struct(foo='bar')
x = my_struct.foo
```




## toml

Module for parsing TOML in Copybara.

<a id="toml.parse" aria-hidden="true"></a>
### toml.parse

Parse the TOML content. Returns a toml object.

[`TomlContent`](#tomlcontent) `toml.parse(content)`


#### Parameters:

Parameter | Description
--------- | -----------
content | `string`<br><p>TOML content to be parsed</p>


#### Example:


##### Parsing a TOML string:

To parse a TOML string, pass the string into the parser.

```python
toml.parse("foo = 42")
```




## TomlContent

Object containing parsed TOML values.

<a id="TomlContent.get" aria-hidden="true"></a>
### TomlContent.get

Retrieve the value from the parsed TOML for the given key. If the key is not defined, this will return None.

`unknown` `TomlContent.get(key)`


#### Parameters:

Parameter | Description
--------- | -----------
key | `string`<br><p>The dotted key expression</p>


#### Example:


##### Get the value for a key:

Pass in the name of the key. This will return the value.

```python
TomlContent.get("foo")
```




## transformation

A single operation which modifies the source checked out from the origin, prior to writing it to the destination. Transformations can also be used to perform validations or checks.<br/><br/>Many common transformations are provided by the built-in libraries, such as <a href='#core'><code>core</code></a>.<br/><br/>Custom transformations can be defined in Starlark code via <a href='#core.dynamic_transform'><code>core.dynamic_transform</code></a>.



## transformation_status

The status of a Transformation that was just run. Either a 'success' or a 'no-op'.


#### Fields:

Name | Description
---- | -----------
is_noop | `bool`<br><p>Whether this status has the value NO-OP.</p>
is_success | `bool`<br><p>Whether this status has the value SUCCESS.</p>



## TransformWork

Data about the set of changes that are being migrated. It includes information about changes like: the author to be used for commit, change message, etc. You receive a TransformWork object as an argument when defining a <a href='#core.dynamic_transform'><code>dynamic transform</code></a>.


#### Fields:

Name | Description
---- | -----------
author | [`author`](#author)<br><p>Author to be used in the change</p>
changes | [`Changes`](#changes)<br><p>List of changes that will be migrated</p>
console | [`Console`](#console)<br><p>Get an instance of the console to report errors or warnings</p>
message | `string`<br><p>Message to be used in the change</p>
params | `dict`<br><p>Parameters for the function if created with core.dynamic_transform</p>

<a id="ctx.add_label" aria-hidden="true"></a>
### ctx.add_label

Add a label to the end of the description

`ctx.add_label(label, value, separator="=", hidden=False)`


#### Parameters:

Parameter | Description
--------- | -----------
label | `string`<br><p>The label to add</p>
value | `string`<br><p>The new value for the label</p>
separator | `string`<br><p>The separator to use for the label</p>
hidden | `bool`<br><p>Don't show the label in the message but only keep it internally</p>

<a id="ctx.add_or_replace_label" aria-hidden="true"></a>
### ctx.add_or_replace_label

Replace an existing label or add it to the end of the description

`ctx.add_or_replace_label(label, value, separator="=")`


#### Parameters:

Parameter | Description
--------- | -----------
label | `string`<br><p>The label to add/replace</p>
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

<a id="ctx.create_symlink" aria-hidden="true"></a>
### ctx.create_symlink

Create a symlink

`ctx.create_symlink(link, target)`


#### Parameters:

Parameter | Description
--------- | -----------
link | [`Path`](#path)<br><p>The link path</p>
target | [`Path`](#path)<br><p>The target path</p>

<a id="ctx.destination_api" aria-hidden="true"></a>
### ctx.destination_api

Returns an api handle for the destination repository. Methods available depend on the destination type. Use with extreme caution, as external calls can make workflow non-deterministic and possibly irreversible. Can have side effects in dry-runmode.

[`endpoint`](#endpoint) `ctx.destination_api()`

<a id="ctx.destination_info" aria-hidden="true"></a>
### ctx.destination_info

Returns an object to store additional configuration and data for the destination. An object is only returned if supported by the destination.

`DestinationInfo` `ctx.destination_info()`

<a id="ctx.destination_reader" aria-hidden="true"></a>
### ctx.destination_reader

Returns a handle to read files from the destination, if supported by the destination.

[`destination_reader`](#destination_reader) `ctx.destination_reader()`

<a id="ctx.find_all_labels" aria-hidden="true"></a>
### ctx.find_all_labels

Tries to find all the values for a label. First it looks at the generated message (that is, labels that might have been added by previous transformations), then it looks in all the commit messages being imported and finally in the resolved reference passed in the CLI.

`sequence of string` `ctx.find_all_labels(label)`


#### Parameters:

Parameter | Description
--------- | -----------
label | `string`<br><p>The label to find</p>

<a id="ctx.find_label" aria-hidden="true"></a>
### ctx.find_label

Tries to find a label. First it looks at the generated message (that is, labels that might have been added by previous transformations), then it looks in all the commit messages being imported and finally in the resolved reference passed in the CLI. Returns the first such label value found this way.

`string` `ctx.find_label(label)`


#### Parameters:

Parameter | Description
--------- | -----------
label | `string`<br><p>The label to find</p>

<a id="ctx.new_path" aria-hidden="true"></a>
### ctx.new_path

Create a new path

[`Path`](#path) `ctx.new_path(path)`


#### Parameters:

Parameter | Description
--------- | -----------
path | `string`<br><p>The string representing the path, relative to the checkout root directory</p>

<a id="ctx.noop" aria-hidden="true"></a>
### ctx.noop

The status returned by a no-op Transformation

[`transformation_status`](#transformation_status) `ctx.noop(message)`


#### Parameters:

Parameter | Description
--------- | -----------
message | `string`<br><p></p>


#### Example:


##### Define a dynamic transformation:

Create a custom transformation which fails.

```python
def my_transform(ctx):
  # do some stuff
  return ctx.noop('Error! The transform didn\'t do anything.')
```


<a id="ctx.now_as_string" aria-hidden="true"></a>
### ctx.now_as_string

Get current date as a string

`string` `ctx.now_as_string(format="yyyy-MM-dd", zone="UTC")`


#### Parameters:

Parameter | Description
--------- | -----------
format | `string`<br><p>The format to use. See: https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html for details.</p>
zone | `string`<br><p>The timezone id to use. See https://docs.oracle.com/javase/8/docs/api/java/time/ZoneId.html. By default UTC </p>

<a id="ctx.origin_api" aria-hidden="true"></a>
### ctx.origin_api

Returns an api handle for the origin repository. Methods available depend on the origin type. Use with extreme caution, as external calls can make workflow non-deterministic and possibly irreversible. Can have side effects in dry-runmode.

[`endpoint`](#endpoint) `ctx.origin_api()`

<a id="ctx.read_path" aria-hidden="true"></a>
### ctx.read_path

Read the content of path as UTF-8

`string` `ctx.read_path(path)`


#### Parameters:

Parameter | Description
--------- | -----------
path | [`Path`](#path)<br><p>The Path to read from</p>

<a id="ctx.remove_label" aria-hidden="true"></a>
### ctx.remove_label

Remove a label from the message if present

`ctx.remove_label(label, whole_message=False)`


#### Parameters:

Parameter | Description
--------- | -----------
label | `string`<br><p>The label to delete</p>
whole_message | `bool`<br><p>By default Copybara only looks in the last paragraph for labels. This flagmake it replace labels in the whole message.</p>

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
whole_message | `bool`<br><p>By default Copybara only looks in the last paragraph for labels. This flagmake it replace labels in the whole message.</p>

<a id="ctx.run" aria-hidden="true"></a>
### ctx.run

Run a glob or a transform. For example:<br><code>files = ctx.run(glob(['**.java']))</code><br>or<br><code>ctx.run(core.move("foo", "bar"))</code><br>or<br>

`unknown` `ctx.run(runnable)`


#### Parameters:

Parameter | Description
--------- | -----------
runnable | [`glob`](#glob) or [`transformation`](#transformation)<br><p>When `runnable` is a `glob`, returns a list of files in the workdir which it matches.</br></br>When `runnable` is a `transformation`, runs it in the workdir.</p>

<a id="ctx.set_author" aria-hidden="true"></a>
### ctx.set_author

Update the author to be used in the change

`ctx.set_author(author)`


#### Parameters:

Parameter | Description
--------- | -----------
author | [`author`](#author)<br><p></p>

<a id="ctx.set_executable" aria-hidden="true"></a>
### ctx.set_executable

Set the executable permission of a file

`ctx.set_executable(path, value)`


#### Parameters:

Parameter | Description
--------- | -----------
path | [`Path`](#path)<br><p>The Path to set the executable permission of</p>
value | `bool`<br><p>Whether or not the file should be executable</p>

<a id="ctx.set_message" aria-hidden="true"></a>
### ctx.set_message

Update the message to be used in the change

`ctx.set_message(message)`


#### Parameters:

Parameter | Description
--------- | -----------
message | `string`<br><p></p>

<a id="ctx.success" aria-hidden="true"></a>
### ctx.success

The status returned by a successful Transformation

[`transformation_status`](#transformation_status) `ctx.success()`


#### Example:


##### Define a dynamic transformation:

Create a custom transformation which is successful.

```python
def my_transform(ctx):
  # do some stuff
  return ctx.success()
```

For compatibility reasons, returning nothing is the same as returning success.


<a id="ctx.write_path" aria-hidden="true"></a>
### ctx.write_path

Write an arbitrary string to a path (UTF-8 will be used)

`ctx.write_path(path, content)`


#### Parameters:

Parameter | Description
--------- | -----------
path | [`Path`](#path)<br><p>The Path to write to</p>
content | `string`<br><p>The content of the file</p>



## xml

Set of functions to work with XML in Copybara.

<a id="xml.xpath" aria-hidden="true"></a>
### xml.xpath

Run an xpath expression

`unknown` `xml.xpath(content, expression, type)`


#### Parameters:

Parameter | Description
--------- | -----------
content | `string`<br><p>The XML content</p>
expression | `string`<br><p>XPath expression</p>
type | `string`<br><p>The type of the return value, see http://www.w3.org/TR/xpathfor more details. For now we support STRING, BOOLEAN & NUMBER.</p>


