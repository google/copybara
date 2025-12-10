<!-- Generated reference here -->
## Table of Contents


  - [archive](#archive)
    - [archive.create](#archivecreate)
    - [archive.extract](#archiveextract)
  - [author](#author)
  - [authoring](#authoring)
    - [authoring.allowed](#authoringallowed)
    - [authoring.overwrite](#authoringoverwrite)
    - [authoring.pass_thru](#authoringpass_thru)
  - [authoring_class](#authoring_class)
  - [buildozer](#buildozer)
    - [buildozer.batch](#buildozerbatch)
    - [buildozer.cmd](#buildozercmd)
    - [buildozer.create](#buildozercreate)
    - [buildozer.delete](#buildozerdelete)
    - [buildozer.modify](#buildozermodify)
    - [buildozer.print](#buildozerprint)
  - [change](#change)
  - [ChangeMessage](#changemessage)
    - [message.label_values](#messagelabel_values)
  - [Changes](#changes)
  - [checker](#checker)
  - [Command](#command)
  - [compression](#compression)
    - [compression.unzip_path](#compressionunzip_path)
  - [console](#console)
    - [console.error](#consoleerror)
    - [console.info](#consoleinfo)
    - [console.progress](#consoleprogress)
    - [console.verbose](#consoleverbose)
    - [console.warn](#consolewarn)
  - [core](#core)
    - [core.action](#coreaction)
    - [core.action_migration](#coreaction_migration)
    - [core.autopatch_config](#coreautopatch_config)
    - [core.convert_encoding](#coreconvert_encoding)
    - [core.copy](#corecopy)
    - [core.custom_version_selector](#corecustom_version_selector)
    - [core.dynamic_feedback](#coredynamic_feedback)
    - [core.dynamic_transform](#coredynamic_transform)
    - [core.fail_with_noop](#corefail_with_noop)
    - [core.feedback](#corefeedback)
    - [core.filter_replace](#corefilter_replace)
    - [core.format](#coreformat)
    - [core.latest_version](#corelatest_version)
    - [core.merge_import_config](#coremerge_import_config)
    - [core.move](#coremove)
    - [core.remove](#coreremove)
    - [core.rename](#corerename)
    - [core.replace](#corereplace)
    - [core.replace_mapper](#corereplace_mapper)
    - [core.reverse](#corereverse)
    - [core.todo_replace](#coretodo_replace)
    - [core.transform](#coretransform)
    - [core.verify_match](#coreverify_match)
    - [core.workflow](#coreworkflow)
  - [core.autopatch_config](#coreautopatch_config)
  - [credentials](#credentials)
    - [credentials.static_secret](#credentialsstatic_secret)
    - [credentials.static_value](#credentialsstatic_value)
    - [credentials.toml_key_source](#credentialstoml_key_source)
    - [credentials.username_password](#credentialsusername_password)
  - [datetime](#datetime)
    - [datetime.fromtimestamp](#datetimefromtimestamp)
    - [datetime.now](#datetimenow)
  - [description_checker](#description_checker)
  - [destination](#destination)
  - [destination_effect](#destination_effect)
  - [destination_reader](#destination_reader)
    - [destination_reader.copy_destination_files](#destination_readercopy_destination_files)
    - [destination_reader.file_exists](#destination_readerfile_exists)
    - [destination_reader.read_file](#destination_readerread_file)
  - [destination_ref](#destination_ref)
  - [dict](#dict)
    - [dict.clear](#dictclear)
    - [dict.get](#dictget)
    - [dict.items](#dictitems)
    - [dict.keys](#dictkeys)
    - [dict.pop](#dictpop)
    - [dict.popitem](#dictpopitem)
    - [dict.setdefault](#dictsetdefault)
    - [dict.update](#dictupdate)
    - [dict.values](#dictvalues)
  - [dynamic.action_result](#dynamicaction_result)
  - [endpoint](#endpoint)
    - [endpoint.new_destination_ref](#endpointnew_destination_ref)
    - [endpoint.new_origin_ref](#endpointnew_origin_ref)
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
    - [feedback.revision_context.fill_template](#feedbackrevision_contextfill_template)
  - [filter_replace](#filter_replace)
  - [float](#float)
  - [folder](#folder)
    - [folder.destination](#folderdestination)
    - [folder.origin](#folderorigin)
  - [format](#format)
    - [format.buildifier](#formatbuildifier)
  - [function](#function)
  - [gerrit_api_obj](#gerrit_api_obj)
    - [gerrit_api_obj.abandon_change](#gerrit_api_objabandon_change)
    - [gerrit_api_obj.delete_vote](#gerrit_api_objdelete_vote)
    - [gerrit_api_obj.get_actions](#gerrit_api_objget_actions)
    - [gerrit_api_obj.get_change](#gerrit_api_objget_change)
    - [gerrit_api_obj.list_changes](#gerrit_api_objlist_changes)
    - [gerrit_api_obj.new_destination_ref](#gerrit_api_objnew_destination_ref)
    - [gerrit_api_obj.new_origin_ref](#gerrit_api_objnew_origin_ref)
    - [gerrit_api_obj.post_review](#gerrit_api_objpost_review)
    - [gerrit_api_obj.submit_change](#gerrit_api_objsubmit_change)
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
  - [github_api_combined_status_obj](#github_api_combined_status_obj)
  - [github_api_commit_author_obj](#github_api_commit_author_obj)
  - [github_api_commit_obj](#github_api_commit_obj)
  - [github_api_github_commit_obj](#github_api_github_commit_obj)
  - [github_api_issue_comment_obj](#github_api_issue_comment_obj)
  - [github_api_obj](#github_api_obj)
    - [github_api_obj.add_label](#github_api_objadd_label)
    - [github_api_obj.create_issue](#github_api_objcreate_issue)
    - [github_api_obj.create_release](#github_api_objcreate_release)
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
    - [github_api_obj.new_release_request](#github_api_objnew_release_request)
    - [github_api_obj.post_issue_comment](#github_api_objpost_issue_comment)
    - [github_api_obj.update_pull_request](#github_api_objupdate_pull_request)
    - [github_api_obj.update_reference](#github_api_objupdate_reference)
  - [github_api_pull_request_comment_obj](#github_api_pull_request_comment_obj)
  - [github_api_pull_request_obj](#github_api_pull_request_obj)
  - [github_api_ref_obj](#github_api_ref_obj)
  - [github_api_revision_obj](#github_api_revision_obj)
  - [github_api_status_obj](#github_api_status_obj)
  - [github_api_user_obj](#github_api_user_obj)
  - [github_app_obj](#github_app_obj)
  - [github_check_run_obj](#github_check_run_obj)
  - [github_check_runs_obj](#github_check_runs_obj)
  - [github_check_suite_obj](#github_check_suite_obj)
  - [github_check_suites_response_obj](#github_check_suites_response_obj)
  - [github_create_release_obj](#github_create_release_obj)
    - [github_create_release_obj.set_draft](#github_create_release_objset_draft)
    - [github_create_release_obj.set_generate_release_notes](#github_create_release_objset_generate_release_notes)
    - [github_create_release_obj.set_latest](#github_create_release_objset_latest)
    - [github_create_release_obj.set_prerelease](#github_create_release_objset_prerelease)
    - [github_create_release_obj.with_body](#github_create_release_objwith_body)
    - [github_create_release_obj.with_commitish](#github_create_release_objwith_commitish)
    - [github_create_release_obj.with_name](#github_create_release_objwith_name)
  - [github_release_obj](#github_release_obj)
  - [glob](#glob)
  - [Globals](#globals)
    - [abs](#abs)
    - [all](#all)
    - [any](#any)
    - [bool](#bool)
    - [dict](#dict)
    - [dir](#dir)
    - [enumerate](#enumerate)
    - [fail](#fail)
    - [float](#float)
    - [getattr](#getattr)
    - [glob](#glob)
    - [hasattr](#hasattr)
    - [hash](#hash)
    - [int](#int)
    - [len](#len)
    - [list](#list)
    - [max](#max)
    - [min](#min)
    - [new_author](#new_author)
    - [parse_message](#parse_message)
    - [print](#print)
    - [range](#range)
    - [repr](#repr)
    - [reversed](#reversed)
    - [set](#set)
    - [sorted](#sorted)
    - [str](#str)
    - [tuple](#tuple)
    - [type](#type)
    - [zip](#zip)
  - [go](#go)
    - [go.go_proxy_resolver](#gogo_proxy_resolver)
    - [go.go_proxy_version_list](#gogo_proxy_version_list)
  - [goproxy_version_list](#goproxy_version_list)
    - [goproxy_version_list.get_info](#goproxy_version_listget_info)
  - [hashing](#hashing)
    - [hashing.path_md5_sum](#hashingpath_md5_sum)
    - [hashing.path_sha256_sum](#hashingpath_sha256_sum)
    - [hashing.str_sha256_sum](#hashingstr_sha256_sum)
  - [hg](#hg)
    - [hg.origin](#hgorigin)
  - [html](#html)
    - [html.xpath](#htmlxpath)
  - [html_element](#html_element)
    - [html_element.attr](#html_elementattr)
  - [http](#http)
    - [http.bearer_auth](#httpbearer_auth)
    - [http.endpoint](#httpendpoint)
    - [http.host](#httphost)
    - [http.json](#httpjson)
    - [http.multipart_form](#httpmultipart_form)
    - [http.multipart_form_file](#httpmultipart_form_file)
    - [http.multipart_form_text](#httpmultipart_form_text)
    - [http.trigger](#httptrigger)
    - [http.url_encode](#httpurl_encode)
    - [http.urlencoded_form](#httpurlencoded_form)
    - [http.username_password_auth](#httpusername_password_auth)
  - [http_endpoint](#http_endpoint)
    - [http_endpoint.delete](#http_endpointdelete)
    - [http_endpoint.followRedirects](#http_endpointfollowredirects)
    - [http_endpoint.get](#http_endpointget)
    - [http_endpoint.new_destination_ref](#http_endpointnew_destination_ref)
    - [http_endpoint.new_origin_ref](#http_endpointnew_origin_ref)
    - [http_endpoint.post](#http_endpointpost)
  - [http_response](#http_response)
    - [http_response.code](#http_responsecode)
    - [http_response.contents_string](#http_responsecontents_string)
    - [http_response.download](#http_responsedownload)
    - [http_response.header](#http_responseheader)
    - [http_response.status](#http_responsestatus)
  - [int](#int)
  - [Issue](#issue)
  - [list](#list)
    - [list.append](#listappend)
    - [list.clear](#listclear)
    - [list.extend](#listextend)
    - [list.index](#listindex)
    - [list.insert](#listinsert)
    - [list.pop](#listpop)
    - [list.remove](#listremove)
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
  - [origin](#origin)
  - [origin_ref](#origin_ref)
  - [output_obj](#output_obj)
  - [patch](#patch)
    - [patch.apply](#patchapply)
    - [patch.quilt_apply](#patchquilt_apply)
  - [Path](#path)
    - [path.exists](#pathexists)
    - [path.read_symlink](#pathread_symlink)
    - [path.relativize](#pathrelativize)
    - [path.remove](#pathremove)
    - [path.resolve](#pathresolve)
    - [path.resolve_sibling](#pathresolve_sibling)
    - [path.rmdir](#pathrmdir)
  - [PathAttributes](#pathattributes)
  - [python](#python)
    - [python.parse_metadata](#pythonparse_metadata)
  - [random](#random)
    - [random.sample](#randomsample)
  - [re2](#re2)
    - [re2.compile](#re2compile)
    - [re2.quote](#re2quote)
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
    - [remotefiles.origin](#remotefilesorigin)
  - [rust_version_requirement](#rust_version_requirement)
    - [rust_version_requirement.fulfills](#rust_version_requirementfulfills)
  - [set](#set)
    - [set.add](#setadd)
    - [set.clear](#setclear)
    - [set.difference](#setdifference)
    - [set.difference_update](#setdifference_update)
    - [set.discard](#setdiscard)
    - [set.intersection](#setintersection)
    - [set.intersection_update](#setintersection_update)
    - [set.isdisjoint](#setisdisjoint)
    - [set.issubset](#setissubset)
    - [set.issuperset](#setissuperset)
    - [set.pop](#setpop)
    - [set.remove](#setremove)
    - [set.symmetric_difference](#setsymmetric_difference)
    - [set.symmetric_difference_update](#setsymmetric_difference_update)
    - [set.union](#setunion)
    - [set.update](#setupdate)
  - [SetReviewInput](#setreviewinput)
  - [StarlarkDateTime](#starlarkdatetime)
    - [StarlarkDateTime.in_epoch_seconds](#starlarkdatetimein_epoch_seconds)
    - [StarlarkDateTime.strftime](#starlarkdatetimestrftime)
  - [string](#string)
    - [string.capitalize](#stringcapitalize)
    - [string.count](#stringcount)
    - [string.elems](#stringelems)
    - [string.endswith](#stringendswith)
    - [string.find](#stringfind)
    - [string.format](#stringformat)
    - [string.index](#stringindex)
    - [string.isalnum](#stringisalnum)
    - [string.isalpha](#stringisalpha)
    - [string.isdigit](#stringisdigit)
    - [string.islower](#stringislower)
    - [string.isspace](#stringisspace)
    - [string.istitle](#stringistitle)
    - [string.isupper](#stringisupper)
    - [string.join](#stringjoin)
    - [string.lower](#stringlower)
    - [string.lstrip](#stringlstrip)
    - [string.partition](#stringpartition)
    - [string.removeprefix](#stringremoveprefix)
    - [string.removesuffix](#stringremovesuffix)
    - [string.replace](#stringreplace)
    - [string.rfind](#stringrfind)
    - [string.rindex](#stringrindex)
    - [string.rpartition](#stringrpartition)
    - [string.rsplit](#stringrsplit)
    - [string.rstrip](#stringrstrip)
    - [string.split](#stringsplit)
    - [string.splitlines](#stringsplitlines)
    - [string.startswith](#stringstartswith)
    - [string.strip](#stringstrip)
    - [string.title](#stringtitle)
    - [string.upper](#stringupper)
  - [struct](#struct)
    - [struct](#struct)
  - [time_delta](#time_delta)
    - [time_delta.total_seconds](#time_deltatotal_seconds)
  - [toml](#toml)
    - [toml.parse](#tomlparse)
  - [TomlContent](#tomlcontent)
    - [TomlContent.get](#tomlcontentget)
    - [TomlContent.get_or_default](#tomlcontentget_or_default)
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
    - [ctx.fill_template](#ctxfill_template)
    - [ctx.find_all_labels](#ctxfind_all_labels)
    - [ctx.find_label](#ctxfind_label)
    - [ctx.list](#ctxlist)
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
  - [tuple](#tuple)
  - [VersionSelector](#versionselector)
  - [xml](#xml)
    - [xml.xpath](#xmlxpath)
  - [copybara_flags](#copybara_flags)



## archive

Functions to work with archives.

<a id="archive.create" aria-hidden="true"></a>
### archive.create

Creates an archive, possibly compressed, from a list of files.

<code>archive.create(<a href=#archive.create.archive>archive</a>, <a href=#archive.create.files>files</a>=None)</code>


<h4 id="parameters.archive.create">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=archive.create.archive href=#archive.create.archive>archive</span> | <code><a href="#path">Path</a></code><br><p>Expected path of the generated archive file.</p>
<span id=archive.create.files href=#archive.create.files>files</span> | <code><a href="#glob">glob</a></code> or <code>NoneType</code><br><p>An optional glob to describe the list of file paths that are to be included in the archive. If not specified, all files under the current working directory will be included. Note, the original file path in the filesystem will be preserved when archiving it.</p>

<a id="archive.extract" aria-hidden="true"></a>
### archive.extract

Extract the contents of the archive to a path.

<code>archive.extract(<a href=#archive.extract.archive>archive</a>, <a href=#archive.extract.type>type</a>="AUTO", <a href=#archive.extract.destination_folder>destination_folder</a>=None, <a href=#archive.extract.paths>paths</a>=None)</code>


<h4 id="parameters.archive.extract">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=archive.extract.archive href=#archive.extract.archive>archive</span> | <code><a href="#path">Path</a></code><br><p>The path to the archive file.</p>
<span id=archive.extract.type href=#archive.extract.type>type</span> | <code><a href="#string">string</a></code><br><p>The archive type. Supported types: AUTO, JAR, ZIP, TAR, TAR_GZ, TAR_XZ, and TAR_BZ2. AUTO will try to infer the archive type automatically.</p>
<span id=archive.extract.destination_folder href=#archive.extract.destination_folder>destination_folder</span> | <code><a href="#path">Path</a></code> or <code>NoneType</code><br><p>The path to extract the archive to. This defaults to the directory where the archive is located.</p>
<span id=archive.extract.paths href=#archive.extract.paths>paths</span> | <code><a href="#glob">glob</a></code> or <code>NoneType</code><br><p>An optional glob that is used to filter the files extracted from the archive.</p>



## author

Represents the author of a change


<h4 id="fields.author">Fields:</h4>

Name | Description
---- | -----------
email | <code><a href="#string">string</a></code><br><p>The email of the author</p>
name | <code><a href="#string">string</a></code><br><p>The name of the author</p>


<h4 id="returned_by.author">Returned By:</h4>

<ul><li><a href="#new_author">new_author</a></li></ul>
<h4 id="consumed_by.author">Consumed By:</h4>

<ul><li><a href="#ctx.set_author">ctx.set_author</a></li></ul>



## authoring

The authors mapping between an origin and a destination

<a id="authoring.allowed" aria-hidden="true"></a>
### authoring.allowed

Create a list for an individual or team contributing code.

<code><a href="#authoring_class">authoring_class</a></code> <code>authoring.allowed(<a href=#authoring.allowed.default>default</a>, <a href=#authoring.allowed.allowlist>allowlist</a>)</code>


<h4 id="parameters.authoring.allowed">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=authoring.allowed.default href=#authoring.allowed.default>default</span> | <code><a href="#string">string</a></code><br><p>The default author for commits in the destination. This is used in squash mode workflows or when users are not on the list.</p>
<span id=authoring.allowed.allowlist href=#authoring.allowed.allowlist>allowlist</span> | <code>sequence of <a href="#string">string</a></code><br><p>List of  authors in the origin that are allowed to contribute code. The authors must be unique</p>


<h4 id="example.authoring.allowed">Examples:</h4>


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

<code><a href="#authoring_class">authoring_class</a></code> <code>authoring.overwrite(<a href=#authoring.overwrite.default>default</a>)</code>


<h4 id="parameters.authoring.overwrite">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=authoring.overwrite.default href=#authoring.overwrite.default>default</span> | <code><a href="#string">string</a></code><br><p>The default author for commits in the destination</p>


<h4 id="example.authoring.overwrite">Example:</h4>


##### Overwrite usage example:

Create an authoring object that will overwrite any origin author with noreply@foobar.com mail.

```python
authoring.overwrite("Foo Bar <noreply@foobar.com>")
```


<a id="authoring.pass_thru" aria-hidden="true"></a>
### authoring.pass_thru

Use the origin author as the author in the destination, no filtering.

<code><a href="#authoring_class">authoring_class</a></code> <code>authoring.pass_thru(<a href=#authoring.pass_thru.default>default</a>)</code>


<h4 id="parameters.authoring.pass_thru">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=authoring.pass_thru.default href=#authoring.pass_thru.default>default</span> | <code><a href="#string">string</a></code><br><p>The default author for commits in the destination. This is used in squash mode workflows or if author cannot be determined.</p>


<h4 id="example.authoring.pass_thru">Example:</h4>


##### Pass thru usage example:



```python
authoring.pass_thru(default = "Foo Bar <noreply@foobar.com>")
```




## authoring_class

The authors mapping between an origin and a destination


<h4 id="returned_by.authoring_class">Returned By:</h4>

<ul><li><a href="#authoring.allowed">authoring.allowed</a></li><li><a href="#authoring.overwrite">authoring.overwrite</a></li><li><a href="#authoring.pass_thru">authoring.pass_thru</a></li></ul>
<h4 id="consumed_by.authoring_class">Consumed By:</h4>

<ul><li><a href="#core.workflow">core.workflow</a></li></ul>



## buildozer

Module for Buildozer-related functionality such as creating and modifying BUILD targets.

<a id="buildozer.batch" aria-hidden="true"></a>
### buildozer.batch

Combines a list of buildozer transforms into a single batch transformation.

<code><a href="#transformation">transformation</a></code> <code>buildozer.batch(<a href=#buildozer.batch.transforms>transforms</a>)</code>


<h4 id="parameters.buildozer.batch">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=buildozer.batch.transforms href=#buildozer.batch.transforms>transforms</span> | <code>sequence of <a href="#transformation">transformation</a></code><br><p>The list of buildozer transforms to combine.</p>

<a id="buildozer.cmd" aria-hidden="true"></a>
### buildozer.cmd

Creates a Buildozer command. You can specify the reversal with the 'reverse' argument.

<code><a href="#command">Command</a></code> <code>buildozer.cmd(<a href=#buildozer.cmd.forward>forward</a>, <a href=#buildozer.cmd.reverse>reverse</a>=None)</code>


<h4 id="parameters.buildozer.cmd">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=buildozer.cmd.forward href=#buildozer.cmd.forward>forward</span> | <code><a href="#string">string</a></code><br><p>Specifies the Buildozer command, e.g. 'replace deps :foo :bar'</p>
<span id=buildozer.cmd.reverse href=#buildozer.cmd.reverse>reverse</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>The reverse of the command. This is only required if the given command cannot be reversed automatically and the reversal of this command is required by some workflow or Copybara check. The following commands are automatically reversible:<br><ul><li>add</li><li>remove (when used to remove element from list i.e. 'remove srcs foo.cc'</li><li>replace</li></ul></p>

<a id="buildozer.create" aria-hidden="true"></a>
### buildozer.create

A transformation which creates a new build target and populates its attributes. This transform can reverse automatically to delete the target.

<code><a href="#transformation">transformation</a></code> <code>buildozer.create(<a href=#buildozer.create.target>target</a>, <a href=#buildozer.create.rule_type>rule_type</a>, <a href=#buildozer.create.commands>commands</a>=[], <a href=#buildozer.create.before>before</a>='', <a href=#buildozer.create.after>after</a>='')</code>


<h4 id="parameters.buildozer.create">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=buildozer.create.target href=#buildozer.create.target>target</span> | <code><a href="#string">string</a></code><br><p>Target to create, including the package, e.g. 'foo:bar'. The package can be '.' for the root BUILD file.</p>
<span id=buildozer.create.rule_type href=#buildozer.create.rule_type>rule_type</span> | <code><a href="#string">string</a></code><br><p>Type of this rule, for instance, java_library.</p>
<span id=buildozer.create.commands href=#buildozer.create.commands>commands</span> | <code>sequence of <a href="#string">string</a></code> or <code>sequence of <a href="#command">Command</a></code><br><p>Commands to populate attributes of the target after creating it. Elements can be strings such as 'add deps :foo' or objects returned by buildozer.cmd.</p>
<span id=buildozer.create.before href=#buildozer.create.before>before</span> | <code><a href="#string">string</a></code><br><p>When supplied, causes this target to be created *before* the target named by 'before'</p>
<span id=buildozer.create.after href=#buildozer.create.after>after</span> | <code><a href="#string">string</a></code><br><p>When supplied, causes this target to be created *after* the target named by 'after'</p>

<a id="buildozer.delete" aria-hidden="true"></a>
### buildozer.delete

A transformation which is the opposite of creating a build target. When run normally, it deletes a build target. When reversed, it creates and prepares one.

<code><a href="#transformation">transformation</a></code> <code>buildozer.delete(<a href=#buildozer.delete.target>target</a>, <a href=#buildozer.delete.rule_type>rule_type</a>='', <a href=#buildozer.delete.recreate_commands>recreate_commands</a>=[], <a href=#buildozer.delete.before>before</a>='', <a href=#buildozer.delete.after>after</a>='')</code>


<h4 id="parameters.buildozer.delete">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=buildozer.delete.target href=#buildozer.delete.target>target</span> | <code><a href="#string">string</a></code><br><p>Target to delete, including the package, e.g. 'foo:bar'</p>
<span id=buildozer.delete.rule_type href=#buildozer.delete.rule_type>rule_type</span> | <code><a href="#string">string</a></code><br><p>Type of this rule, for instance, java_library. Supplying this will cause this transformation to be reversible.</p>
<span id=buildozer.delete.recreate_commands href=#buildozer.delete.recreate_commands>recreate_commands</span> | <code>sequence of <a href="#string">string</a></code> or <code>sequence of <a href="#command">Command</a></code><br><p>Commands to populate attributes of the target after creating it. Elements can be strings such as 'add deps :foo' or objects returned by buildozer.cmd.</p>
<span id=buildozer.delete.before href=#buildozer.delete.before>before</span> | <code><a href="#string">string</a></code><br><p>When supplied with rule_type and the transformation is reversed, causes this target to be created *before* the target named by 'before'</p>
<span id=buildozer.delete.after href=#buildozer.delete.after>after</span> | <code><a href="#string">string</a></code><br><p>When supplied with rule_type and the transformation is reversed, causes this target to be created *after* the target named by 'after'</p>

<a id="buildozer.modify" aria-hidden="true"></a>
### buildozer.modify

A transformation which runs one or more Buildozer commands against a single target expression. See http://go/buildozer for details on supported commands and target expression formats.

<code><a href="#transformation">transformation</a></code> <code>buildozer.modify(<a href=#buildozer.modify.target>target</a>, <a href=#buildozer.modify.commands>commands</a>)</code>


<h4 id="parameters.buildozer.modify">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=buildozer.modify.target href=#buildozer.modify.target>target</span> | <code><a href="#string">string</a></code> or <code>sequence of <a href="#string">string</a></code><br><p>Specifies the target(s) against which to apply the commands. Can be a list.</p>
<span id=buildozer.modify.commands href=#buildozer.modify.commands>commands</span> | <code>sequence of <a href="#string">string</a></code> or <code>sequence of <a href="#command">Command</a></code><br><p>Commands to apply to the target(s) specified. Elements can be strings such as 'add deps :foo' or objects returned by buildozer.cmd.</p>


<h4 id="example.buildozer.modify">Examples:</h4>


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


<a id="buildozer.print" aria-hidden="true"></a>
### buildozer.print

Executes a buildozer print command and returns the output. This is designed to be used in the context of a transform

<code><a href="#string">string</a></code> <code>buildozer.print(<a href=#buildozer.print.ctx>ctx</a>, <a href=#buildozer.print.attr>attr</a>, <a href=#buildozer.print.target>target</a>)</code>


<h4 id="parameters.buildozer.print">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=buildozer.print.ctx href=#buildozer.print.ctx>ctx</span> | <code><a href="#transformwork">TransformWork</a></code><br><p>The TransformWork object</p>
<span id=buildozer.print.attr href=#buildozer.print.attr>attr</span> | <code><a href="#string">string</a></code><br><p>The attribute from the target rule to print.</p>
<span id=buildozer.print.target href=#buildozer.print.target>target</span> | <code><a href="#string">string</a></code><br><p>The target to print from.</p>



## change

A change metadata. Contains information like author, change message or detected labels


<h4 id="fields.change">Fields:</h4>

Name | Description
---- | -----------
author | <code><a href="#author">author</a></code><br><p>The author of the change</p>
date_time_iso_offset | <code><a href="#string">string</a></code><br><p>Return a ISO offset date time. Example:  2011-12-03T10:15:30+01:00'</p>
first_line_message | <code><a href="#string">string</a></code><br><p>The message of the change</p>
labels | <code>dict[<a href="#string">string</a>, <a href="#string">string</a>]</code><br><p>A dictionary with the labels detected for the change. If the label is present multiple times it returns the last value. Note that this is a heuristic and it could include things that are not labels.</p>
labels_all_values | <code>dict[<a href="#string">string</a>, list of string]</code><br><p>A dictionary with the labels detected for the change. Note that the value is a collection of the values for each time the label was found. Use 'labels' instead if you are only interested in the last value. Note that this is a heuristic and it could include things that are not labels.</p>
merge | <code><a href="#bool">bool</a></code><br><p>Returns true if the change represents a merge</p>
message | <code><a href="#string">string</a></code><br><p>The message of the change</p>
original_author | <code><a href="#author">author</a></code><br><p>The author of the change before any mapping</p>
ref | <code><a href="#string">string</a></code><br><p>Origin reference ref</p>



## ChangeMessage

Represents a well formed parsed change message with its associated labels.


<h4 id="fields.ChangeMessage">Fields:</h4>

Name | Description
---- | -----------
first_line | <code><a href="#string">string</a></code><br><p>First line of this message</p>
text | <code><a href="#string">string</a></code><br><p>The text description this message, not including the labels.</p>


<h4 id="returned_by.ChangeMessage">Returned By:</h4>

<ul><li><a href="#parse_message">parse_message</a></li></ul>

<a id="message.label_values" aria-hidden="true"></a>
### message.label_values

Returns a list of values associated with the label name.

<code>list of string</code> <code>message.label_values(<a href=#message.label_values.label_name>label_name</a>)</code>


<h4 id="parameters.message.label_values">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=message.label_values.label_name href=#message.label_values.label_name>label_name</span> | <code><a href="#string">string</a></code><br><p>The label name.</p>



## Changes

Data about the set of changes that are being migrated. Each change includes information like: original author, change message, labels, etc. You receive this as a field in TransformWork object for user defined transformations


<h4 id="fields.Changes">Fields:</h4>

Name | Description
---- | -----------
current | <code>list of change</code><br><p>List of changes that will be migrated</p>
migrated | <code>list of change</code><br><p>List of changes that where migrated in previous Copybara executions or if using ITERATIVE mode in previous iterations of this workflow.</p>



## checker

A checker to be run on arbitrary data and files


<h4 id="consumed_by.checker">Consumed By:</h4>

<ul><li><a href="#git.destination">git.destination</a></li><li><a href="#git.gerrit_api">git.gerrit_api</a></li><li><a href="#git.gerrit_destination">git.gerrit_destination</a></li><li><a href="#git.gerrit_origin">git.gerrit_origin</a></li><li><a href="#git.gerrit_trigger">git.gerrit_trigger</a></li><li><a href="#git.github_api">git.github_api</a></li><li><a href="#git.github_destination">git.github_destination</a></li><li><a href="#git.github_pr_destination">git.github_pr_destination</a></li><li><a href="#git.github_pr_origin">git.github_pr_origin</a></li><li><a href="#git.github_trigger">git.github_trigger</a></li><li><a href="#git.mirror">git.mirror</a></li><li><a href="#http.endpoint">http.endpoint</a></li><li><a href="#http.trigger">http.trigger</a></li></ul>



## Command

Buildozer command type


<h4 id="returned_by.Command">Returned By:</h4>

<ul><li><a href="#buildozer.cmd">buildozer.cmd</a></li></ul>
<h4 id="consumed_by.Command">Consumed By:</h4>

<ul><li><a href="#buildozer.create">buildozer.create</a></li><li><a href="#buildozer.delete">buildozer.delete</a></li><li><a href="#buildozer.modify">buildozer.modify</a></li></ul>



## compression

DEPRECATED. Use the `archive` module.
Module for compression related starlark utilities

<a id="compression.unzip_path" aria-hidden="true"></a>
### compression.unzip_path

DEPRECATED: Use `archive.extract` instead.
Unzip the zipped source CheckoutPath and unzip it to the destination CheckoutPath

<code>compression.unzip_path(<a href=#compression.unzip_path.source_path>source_path</a>, <a href=#compression.unzip_path.destination_path>destination_path</a>, <a href=#compression.unzip_path.filter>filter</a>=None)</code>


<h4 id="parameters.compression.unzip_path">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=compression.unzip_path.source_path href=#compression.unzip_path.source_path>source_path</span> | <code><a href="#path">Path</a></code><br><p>the zipped file source</p>
<span id=compression.unzip_path.destination_path href=#compression.unzip_path.destination_path>destination_path</span> | <code><a href="#path">Path</a></code><br><p>the path to unzip to</p>
<span id=compression.unzip_path.filter href=#compression.unzip_path.filter>filter</span> | <code><a href="#glob">glob</a></code> or <code>list of string</code> or <code>NoneType</code><br><p>A glob relative to the archive root that will restrict what files <br>from the archive should be extracted.</p>



## console

A console that can be used in skylark transformations to print info, warning or error messages.

<a id="console.error" aria-hidden="true"></a>
### console.error

Show an error in the log. Note that this will stop Copybara execution.

<code>console.error(<a href=#console.error.message>message</a>)</code>


<h4 id="parameters.console.error">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=console.error.message href=#console.error.message>message</span> | <code><a href="#string">string</a></code><br><p>message to log</p>

<a id="console.info" aria-hidden="true"></a>
### console.info

Show an info message in the console

<code>console.info(<a href=#console.info.message>message</a>)</code>


<h4 id="parameters.console.info">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=console.info.message href=#console.info.message>message</span> | <code><a href="#string">string</a></code><br><p>message to log</p>

<a id="console.progress" aria-hidden="true"></a>
### console.progress

Show a progress message in the console

<code>console.progress(<a href=#console.progress.message>message</a>)</code>


<h4 id="parameters.console.progress">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=console.progress.message href=#console.progress.message>message</span> | <code><a href="#string">string</a></code><br><p>message to log</p>

<a id="console.verbose" aria-hidden="true"></a>
### console.verbose

Show an info message in the console if verbose logging is enabled.

<code>console.verbose(<a href=#console.verbose.message>message</a>)</code>


<h4 id="parameters.console.verbose">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=console.verbose.message href=#console.verbose.message>message</span> | <code><a href="#string">string</a></code><br><p>message to log</p>

<a id="console.warn" aria-hidden="true"></a>
### console.warn

Show a warning in the console

<code>console.warn(<a href=#console.warn.message>message</a>)</code>


<h4 id="parameters.console.warn">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=console.warn.message href=#console.warn.message>message</span> | <code><a href="#string">string</a></code><br><p>message to log</p>



## core

Core functionality for creating migrations, and basic transformations.


<h4 id="fields.core">Fields:</h4>

Name | Description
---- | -----------
console | <code><a href="#console">console</a></code><br><p>Returns a handle to the console object.</p>
main_config_path | <code><a href="#string">string</a></code><br><p>Location of the config file. This is subject to change</p>



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
<span style="white-space: nowrap;">`--force, --force-update`</span> | *boolean* | Force the migration even if Copybara cannot find in the destination a change that is an ancestor of the one(s) being migrated. This should be used with care, as it could lose changes when migrating a previous/conflicting change.
<span style="white-space: nowrap;">`--info-list-only`</span> | *boolean* | When set, the INFO command will print a list of workflows defined in the file.
<span style="white-space: nowrap;">`--labels`</span> | *immutableMap* | Additional flags. Can be accessed in feedback and mirror context objects via the `cli_labels` field. In `core.workflow`, they are accessible as labels, but with names uppercased and prefixed with FLAG_ to avoid name clashes with existing labels. I.e. `--labels=label1:value1` will define a label FLAG_LABEL1Format: --labels=flag1:value1,flag2:value2 Or: --labels flag1:value1,flag2:value2 
<span style="white-space: nowrap;">`--noansi`</span> | *boolean* | Don't use ANSI output for messages
<span style="white-space: nowrap;">`--nocleanup`</span> | *boolean* | Cleanup the output directories. This includes the workdir, scratch clones of Git repos, etc. By default is set to false and directories will be cleaned prior to the execution. If set to true, the previous run output will not be cleaned up. Keep in mind that running in this mode will lead to an ever increasing disk usage.
<span style="white-space: nowrap;">`--noprompt`</span> | *boolean* | Don't prompt, this will answer all prompts with 'yes'
<span style="white-space: nowrap;">`--output-limit`</span> | *int* | Limit the output in the console to a number of records. Each subcommand might use this flag differently. Defaults to 0, which shows all the output.
<span style="white-space: nowrap;">`--output-root`</span> | *string* | The root directory where to generate output files. If not set, ~/copybara/out is used by default. Use with care, Copybara might remove files inside this root if necessary.
<span style="white-space: nowrap;">`--patch-bin`</span> | *string* | Path for GNU Patch command
<span style="white-space: nowrap;">`--repo-timeout`</span> | *duration* | Repository operation timeout duration.  Example values: 30s, 20m, 1h, etc.
<span style="white-space: nowrap;">`--squash`</span> | *boolean* | Override workflow's mode with 'SQUASH'. This is useful mainly for workflows that use 'ITERATIVE' mode, when we want to run a single export with 'SQUASH', maybe to fix an issue. Always use --dry-run before, to test your changes locally.
<span style="white-space: nowrap;">`--validate-starlark`</span> | *string* | Starlark should be validated prior to execution, but this might break legacy configs. Options are LOOSE, STRICT
<span style="white-space: nowrap;">`--version-selector-use-cli-ref`</span> | *boolean* | If command line ref is to used with a version selector, pass this flag to tell copybara to use it.
<span style="white-space: nowrap;">`-v, --verbose`</span> | *boolean* | Verbose output.

<a id="core.action" aria-hidden="true"></a>
### core.action

Create a dynamic Skylark action. This should only be used by libraries developers. Actions are Starlark functions that receive a context, perform some side effect and return a result (success, error or noop).

<code>dynamic.action</code> <code>core.action(<a href=#core.action.impl>impl</a>, <a href=#core.action.params>params</a>={})</code>


<h4 id="parameters.core.action">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.action.impl href=#core.action.impl>impl</span> | <code>callable</code><br><p>The Skylark function to call</p>
<span id=core.action.params href=#core.action.params>params</span> | <code><a href="#dict">dict</a></code><br><p>The parameters to the function. Will be available under ctx.params</p>

<a id="core.action_migration" aria-hidden="true"></a>
### core.action_migration

Defines a migration that is more flexible/less-opinionated migration than `core.workflow`. Most of the users should not use this migration and instead use `core.workflow` for moving code. In particular `core.workflow` provides many helping functionality like version handling, ITERATIVE/SQUASH/CHANGE_REQUEST modes, --read-config-from-change dynamic config, etc.

These are the features that raw_migration provides:<ul>
<li>Support for migrations that don't move source code (similar to feedback)</li>
<li>Support for migrations that talk to more than one origin/destination endpoits (Feature still in progress)</li>
<li>Custom management of versioning: For example moving non-linear/multiple  versions (Instead of `core.workflow`, that moves source code in relation to the previous migrated code and is able to only track one branch).</li>
</ul>


<code>core.action_migration(<a href=#core.action_migration.name>name</a>, <a href=#core.action_migration.origin>origin</a>, <a href=#core.action_migration.endpoints>endpoints</a>, <a href=#core.action_migration.action>action</a>, <a href=#core.action_migration.description>description</a>=None, <a href=#core.action_migration.filesystem>filesystem</a>=False)</code>


<h4 id="parameters.core.action_migration">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.action_migration.name href=#core.action_migration.name>name</span> | <code><a href="#string">string</a></code><br><p>The name of the migration.</p>
<span id=core.action_migration.origin href=#core.action_migration.origin>origin</span> | <code>trigger</code><br><p>The trigger endpoint of the migration. Accessible as `ctx.origin`</p>
<span id=core.action_migration.endpoints href=#core.action_migration.endpoints>endpoints</span> | <code>structure</code><br><p>One or more endpoints that the migration will have access for read and/or write with one being named 'destination'. This is a field that should be defined as:<br>```<br>  endpoint = struct(<br>     destination = foo.foo_api(...configuration...),<br>     some_endpoint = baz.baz_api(...configuration...),<br>  )<br>```<br>Then they will be accessible in the action as `ctx.destination` and `ctx.endpoints.some_endpoint`</p>
<span id=core.action_migration.action href=#core.action_migration.action>action</span> | <code>unknown</code><br><p>The action to execute when the migration is triggered.<br></p>
<span id=core.action_migration.description href=#core.action_migration.description>description</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>A description of what this workflow achieves</p>
<span id=core.action_migration.filesystem href=#core.action_migration.filesystem>filesystem</span> | <code><a href="#bool">bool</a></code><br><p>If true, the migration provide access to the filesystem to the endpoints</p>

<a id="core.autopatch_config" aria-hidden="true"></a>
### core.autopatch_config

Describes in the configuration for automatic patch file generation

<code><a href="#coreautopatch_config">core.autopatch_config</a></code> <code>core.autopatch_config(<a href=#core.autopatch_config.header>header</a>=None, <a href=#core.autopatch_config.suffix>suffix</a>='.patch', <a href=#core.autopatch_config.directory_prefix>directory_prefix</a>='', <a href=#core.autopatch_config.directory>directory</a>='AUTOPATCHES', <a href=#core.autopatch_config.strip_file_names_and_line_numbers>strip_file_names_and_line_numbers</a>=False, <a href=#core.autopatch_config.strip_file_names>strip_file_names</a>=False, <a href=#core.autopatch_config.strip_line_numbers>strip_line_numbers</a>=False, <a href=#core.autopatch_config.paths>paths</a>=None)</code>


<h4 id="parameters.core.autopatch_config">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.autopatch_config.header href=#core.autopatch_config.header>header</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>A string to include at the beginning of each patch file</p>
<span id=core.autopatch_config.suffix href=#core.autopatch_config.suffix>suffix</span> | <code><a href="#string">string</a></code><br><p>Suffix to use when saving patch files</p>
<span id=core.autopatch_config.directory_prefix href=#core.autopatch_config.directory_prefix>directory_prefix</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Directory prefix used to relativize filenames when writing patch files. E.g. if filename is third_party/foo/bar/bar.go and we want to write third_party/foo/AUTOPATCHES/bar/bar.go, the value for this field would be 'third_party/foo'</p>
<span id=core.autopatch_config.directory href=#core.autopatch_config.directory>directory</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Directory in which to save the patch files.</p>
<span id=core.autopatch_config.strip_file_names_and_line_numbers href=#core.autopatch_config.strip_file_names_and_line_numbers>strip_file_names_and_line_numbers</span> | <code><a href="#bool">bool</a></code><br><p>When true, strip filenames and line numbers from patch files</p>
<span id=core.autopatch_config.strip_file_names href=#core.autopatch_config.strip_file_names>strip_file_names</span> | <code><a href="#bool">bool</a></code><br><p>When true, strip filenames from patch files</p>
<span id=core.autopatch_config.strip_line_numbers href=#core.autopatch_config.strip_line_numbers>strip_line_numbers</span> | <code><a href="#bool">bool</a></code><br><p>When true, strip line numbers from patch files</p>
<span id=core.autopatch_config.paths href=#core.autopatch_config.paths>paths</span> | <code><a href="#glob">glob</a></code> or <code>list of string</code> or <code>NoneType</code><br><p>Only create patch files that match glob. Default is to match all files</p>

<a id="core.convert_encoding" aria-hidden="true"></a>
### core.convert_encoding

Change the encoding for a set of files

<code><a href="#transformation">transformation</a></code> <code>core.convert_encoding(<a href=#core.convert_encoding.before>before</a>, <a href=#core.convert_encoding.after>after</a>, <a href=#core.convert_encoding.paths>paths</a>)</code>


<h4 id="parameters.core.convert_encoding">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.convert_encoding.before href=#core.convert_encoding.before>before</span> | <code><a href="#string">string</a></code><br><p>The expected encoding of the files before transformation. Charset should be in the format expected by https://docs.oracle.com/javase/8/docs/api/java/nio/charset/Charset.html</p>
<span id=core.convert_encoding.after href=#core.convert_encoding.after>after</span> | <code><a href="#string">string</a></code><br><p>The encoding to convert to. Same format as 'before'</p>
<span id=core.convert_encoding.paths href=#core.convert_encoding.paths>paths</span> | <code><a href="#glob">glob</a></code><br><p>The files to be deleted</p>


<h4 id="example.core.convert_encoding">Example:</h4>


##### ISO-8859-1 to UTF-8:

Convert some files from ISO-8859-1 to UTF-8

```python
core.convert_encoding(
    before = 'ISO-8859-1',
    after = 'UTF-8',
    paths = glob(["foo/*.txt"]),
)
```

In this example, `foo/one.txt` encoding will be changed from ISO-8859-1 to UTF-8.


<a id="core.copy" aria-hidden="true"></a>
### core.copy

Copy files between directories and renames files

<code><a href="#transformation">transformation</a></code> <code>core.copy(<a href=#core.copy.before>before</a>, <a href=#core.copy.after>after</a>, <a href=#core.copy.paths>paths</a>=glob(["**"]), <a href=#core.copy.overwrite>overwrite</a>=False, <a href=#core.copy.regex_groups>regex_groups</a>={})</code>


<h4 id="parameters.core.copy">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.copy.before href=#core.copy.before>before</span> | <code><a href="#string">string</a></code><br><p>The name of the file or directory to copy. If this is the empty string and 'after' is a directory, then all files in the workdir will be copied to the sub directory specified by 'after', maintaining the directory tree.</p>
<span id=core.copy.after href=#core.copy.after>after</span> | <code><a href="#string">string</a></code><br><p>The name of the file or directory destination. If this is the empty string and 'before' is a directory, then all files in 'before' will be copied to the repo root, maintaining the directory tree inside 'before'.</p>
<span id=core.copy.paths href=#core.copy.paths>paths</span> | <code><a href="#glob">glob</a></code> or <code>list of string</code> or <code>NoneType</code><br><p>A glob expression relative to 'before' if it represents a directory. Only files matching the expression will be copied. For example, glob(["**.java"]), matches all java files recursively inside 'before' folder. Defaults to match all the files recursively.</p>
<span id=core.copy.overwrite href=#core.copy.overwrite>overwrite</span> | <code><a href="#bool">bool</a></code><br><p>Overwrite destination files if they already exist. Note that this makes the transformation non-reversible, since there is no way to know if the file was overwritten or not in the reverse workflow.</p>
<span id=core.copy.regex_groups href=#core.copy.regex_groups>regex_groups</span> | <code><a href="#dict">dict</a></code><br><p>A set of named regexes that can be used to match part of the file name. Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax. For example {"x": "[A-Za-z]+"}</p>


<h4 id="example.core.copy">Examples:</h4>


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


<a id="core.custom_version_selector" aria-hidden="true"></a>
### core.custom_version_selector

This is experimental: Custom version selector, users are able to define their own sorting comparator and filter candidates by regex. The custom version selector will choose the greatest version from the candidates that match the filter regex.

<code><a href="#versionselector">VersionSelector</a></code> <code>core.custom_version_selector(<a href=#core.custom_version_selector.comparator>comparator</a>, <a href=#core.custom_version_selector.regex_filter>regex_filter</a>=None)</code>


<h4 id="parameters.core.custom_version_selector">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.custom_version_selector.comparator href=#core.custom_version_selector.comparator>comparator</span> | <code>callable</code><br><p>A callable comparator of two strings as a callable. The comparator should take two strings arguments named 'left' and 'right' and return -1, 0, or 1.</p>
<span id=core.custom_version_selector.regex_filter href=#core.custom_version_selector.regex_filter>regex_filter</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Only versions that match this regex will be considered.</p>


<h4 id="example.core.custom_version_selector">Example:</h4>


##### Create a simple custom version selector:

To define a simple custom version selector, define a callable that takes two strings and compares them.

```python
core.custom_version_selector(
    comparator = lambda left, right: -1 if left < right else 1 if left > right else 0,
    regex_filter = r'.*'
)

```


<a id="core.dynamic_feedback" aria-hidden="true"></a>
### core.dynamic_feedback

Create a dynamic Skylark feedback migration. This should only be used by libraries developers

<code>dynamic.action</code> <code>core.dynamic_feedback(<a href=#core.dynamic_feedback.impl>impl</a>, <a href=#core.dynamic_feedback.params>params</a>={})</code>


<h4 id="parameters.core.dynamic_feedback">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.dynamic_feedback.impl href=#core.dynamic_feedback.impl>impl</span> | <code>callable</code><br><p>The Skylark function to call</p>
<span id=core.dynamic_feedback.params href=#core.dynamic_feedback.params>params</span> | <code><a href="#dict">dict</a></code><br><p>The parameters to the function. Will be available under ctx.params</p>

<a id="core.dynamic_transform" aria-hidden="true"></a>
### core.dynamic_transform

Create a dynamic Skylark transformation. This should only be used by libraries developers

<code><a href="#transformation">transformation</a></code> <code>core.dynamic_transform(<a href=#core.dynamic_transform.impl>impl</a>, <a href=#core.dynamic_transform.params>params</a>={})</code>


<h4 id="parameters.core.dynamic_transform">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.dynamic_transform.impl href=#core.dynamic_transform.impl>impl</span> | <code>callable</code><br><p>The Skylark function to call</p>
<span id=core.dynamic_transform.params href=#core.dynamic_transform.params>params</span> | <code><a href="#dict">dict</a></code><br><p>The parameters to the function. Will be available under ctx.params</p>


<h4 id="example.core.dynamic_transform">Examples:</h4>


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

<code>dynamic.action</code> <code>core.fail_with_noop(<a href=#core.fail_with_noop.msg>msg</a>)</code>


<h4 id="parameters.core.fail_with_noop">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.fail_with_noop.msg href=#core.fail_with_noop.msg>msg</span> | <code><a href="#string">string</a></code><br><p>The noop message</p>

<a id="core.feedback" aria-hidden="true"></a>
### core.feedback

Defines a migration of changes' metadata, that can be invoked via the Copybara command in the same way as a regular workflow migrates the change itself.

It is considered change metadata any information associated with a change (pending or submitted) that is not core to the change itself. A few examples:
<ul>
<li> Comments: Present in any code review system. Examples: GitHub PRs or Gerrit     code reviews.</li>
<li> Labels: Used in code review systems for approvals and/or CI results.     Examples: GitHub labels, Gerrit code review labels.</li>
</ul>
For the purpose of this workflow, it is not considered metadata the commit message in Git, or any of the contents of the file tree.



<code>core.feedback(<a href=#core.feedback.name>name</a>, <a href=#core.feedback.origin>origin</a>, <a href=#core.feedback.destination>destination</a>, <a href=#core.feedback.actions>actions</a>=[], <a href=#core.feedback.action>action</a>=None, <a href=#core.feedback.description>description</a>=None)</code>


<h4 id="parameters.core.feedback">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.feedback.name href=#core.feedback.name>name</span> | <code><a href="#string">string</a></code><br><p>The name of the feedback workflow.</p>
<span id=core.feedback.origin href=#core.feedback.origin>origin</span> | <code>trigger</code><br><p>The trigger of a feedback migration.</p>
<span id=core.feedback.destination href=#core.feedback.destination>destination</span> | <code>endpoint_provider</code><br><p>Where to write change metadata to. This is usually a code review system like Gerrit or GitHub PR.</p>
<span id=core.feedback.action href=#core.feedback.action>action</span> | <code>unknown</code><br><p>An action to execute when the migration is triggered</p>
<span id=core.feedback.description href=#core.feedback.description>description</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>A description of what this workflow achieves</p>

<a id="core.filter_replace" aria-hidden="true"></a>
### core.filter_replace

Applies an initial filtering to find a substring to be replaced and then applies a `mapping` of replaces for the matched text.

<code><a href="#filter_replace">filter_replace</a></code> <code>core.filter_replace(<a href=#core.filter_replace.regex>regex</a>, <a href=#core.filter_replace.mapping>mapping</a>={}, <a href=#core.filter_replace.group>group</a>=Whole text, <a href=#core.filter_replace.paths>paths</a>=glob(["**"]), <a href=#core.filter_replace.reverse>reverse</a>=regex)</code>


<h4 id="parameters.core.filter_replace">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.filter_replace.regex href=#core.filter_replace.regex>regex</span> | <code><a href="#string">string</a></code><br><p>A re2 regex to match a substring of the file</p>
<span id=core.filter_replace.mapping href=#core.filter_replace.mapping>mapping</span> | <code>unknown</code><br><p>A mapping function like core.replace_mapper or a dict with mapping values.</p>
<span id=core.filter_replace.group href=#core.filter_replace.group>group</span> | <code><a href="#int">int</a></code> or <code>NoneType</code><br><p>Extract a regex group from the matching text and pass this as parameter to the mapping instead of the whole matching text.</p>
<span id=core.filter_replace.paths href=#core.filter_replace.paths>paths</span> | <code><a href="#glob">glob</a></code> or <code>list of string</code> or <code>NoneType</code><br><p>A glob expression relative to the workdir representing the files to apply the transformation. For example, glob(["**.java"]), matches all java files recursively. Defaults to match all the files recursively.</p>
<span id=core.filter_replace.reverse href=#core.filter_replace.reverse>reverse</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>A re2 regex used as reverse transformation</p>


<h4 id="example.core.filter_replace">Examples:</h4>


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

<code><a href="#string">string</a></code> <code>core.format(<a href=#core.format.format>format</a>, <a href=#core.format.args>args</a>)</code>


<h4 id="parameters.core.format">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.format.format href=#core.format.format>format</span> | <code><a href="#string">string</a></code><br><p>The format string</p>
<span id=core.format.args href=#core.format.args>args</span> | <code>sequence</code><br><p>The arguments to format</p>

<a id="core.latest_version" aria-hidden="true"></a>
### core.latest_version

Selects the latest version that matches the format.  Using --force in the CLI will force to use the reference passed as argument instead.

<code><a href="#versionselector">VersionSelector</a></code> <code>core.latest_version(<a href=#core.latest_version.format>format</a>, <a href=#core.latest_version.regex_groups>regex_groups</a>={})</code>


<h4 id="parameters.core.latest_version">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.latest_version.format href=#core.latest_version.format>format</span> | <code><a href="#string">string</a></code><br><p>The format of the version. If using it for git, it has to use the completerefspec (e.g. 'refs/tags/${n0}.${n1}.${n2}')</p>
<span id=core.latest_version.regex_groups href=#core.latest_version.regex_groups>regex_groups</span> | <code><a href="#dict">dict</a></code><br><p>A set of named regexes that can be used to match part of the versions. Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax. Use the following nomenclature n0, n1, n2 for the version part (will use numeric sorting) or s0, s1, s2 (alphabetic sorting). Note that there can be mixed but the numbers cannot be repeated. In other words n0, s1, n2 is valid but not n0, s0, n1. n0 has more priority than n1. If there are fields where order is not important, use s(N+1) where N ist he latest sorted field. Example {"n0": "[0-9]+", "s1": "[a-z]+"}</p>


<h4 id="example.core.latest_version">Examples:</h4>


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


<a id="core.merge_import_config" aria-hidden="true"></a>
### core.merge_import_config

Describes which paths merge_import mode should be applied

<code><a href="#coremerge_import_config">core.merge_import_config</a></code> <code>core.merge_import_config(<a href=#core.merge_import_config.package_path>package_path</a>, <a href=#core.merge_import_config.paths>paths</a>=None, <a href=#core.merge_import_config.use_consistency_file>use_consistency_file</a>=False, <a href=#core.merge_import_config.merge_strategy>merge_strategy</a>='DIFF3')</code>


<h4 id="parameters.core.merge_import_config">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.merge_import_config.package_path href=#core.merge_import_config.package_path>package_path</span> | <code><a href="#string">string</a></code><br><p>Package location (ex. 'google3/third_party/java/foo').</p>
<span id=core.merge_import_config.paths href=#core.merge_import_config.paths>paths</span> | <code><a href="#glob">glob</a></code> or <code>list of string</code> or <code>NoneType</code><br><p>Glob of paths to apply merge_import mode, relative to package_path</p>
<span id=core.merge_import_config.use_consistency_file href=#core.merge_import_config.use_consistency_file>use_consistency_file</span> | <code><a href="#bool">bool</a></code><br><p>When merging, if a consistency file exists, use it to construct the center of the 3-way merge. This can result in a more accurate merge in some cases, such as when the config file has changed since the last import.</p>
<span id=core.merge_import_config.merge_strategy href=#core.merge_import_config.merge_strategy>merge_strategy</span> | <code><a href="#string">string</a></code><br><p>The strategy to use for merging files. DIFF3 shells out to diff3 with the -m flag to perform a 3-way merge. PATCH_MERGE creates a patch file by diffing the baseline and destination files, and then applies the patch to the origin file.</p>

<a id="core.move" aria-hidden="true"></a>
### core.move

Moves files between directories and renames files

<code><a href="#transformation">transformation</a></code> <code>core.move(<a href=#core.move.before>before</a>, <a href=#core.move.after>after</a>, <a href=#core.move.paths>paths</a>=glob(["**"]), <a href=#core.move.overwrite>overwrite</a>=False, <a href=#core.move.regex_groups>regex_groups</a>={})</code>


<h4 id="parameters.core.move">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.move.before href=#core.move.before>before</span> | <code><a href="#string">string</a></code><br><p>The name of the file or directory before moving. If this is the empty string and 'after' is a directory, then all files in the workdir will be moved to the sub directory specified by 'after', maintaining the directory tree.</p>
<span id=core.move.after href=#core.move.after>after</span> | <code><a href="#string">string</a></code><br><p>The name of the file or directory after moving. If this is the empty string and 'before' is a directory, then all files in 'before' will be moved to the repo root, maintaining the directory tree inside 'before'.</p>
<span id=core.move.paths href=#core.move.paths>paths</span> | <code><a href="#glob">glob</a></code> or <code>list of string</code> or <code>NoneType</code><br><p>A glob expression relative to 'before' if it represents a directory. Only files matching the expression will be moved. For example, glob(["**.java"]), matches all java files recursively inside 'before' folder. Defaults to match all the files recursively.</p>
<span id=core.move.overwrite href=#core.move.overwrite>overwrite</span> | <code><a href="#bool">bool</a></code><br><p>Overwrite destination files if they already exist. Note that this makes the transformation non-reversible, since there is no way to know if the file was overwritten or not in the reverse workflow.</p>
<span id=core.move.regex_groups href=#core.move.regex_groups>regex_groups</span> | <code><a href="#dict">dict</a></code><br><p>A set of named regexes that can be used to match part of the file name. Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax. For example {"x": "[A-Za-z]+"}</p>


<h4 id="example.core.move">Examples:</h4>


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

<code><a href="#transformation">transformation</a></code> <code>core.remove(<a href=#core.remove.paths>paths</a>)</code>


<h4 id="parameters.core.remove">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.remove.paths href=#core.remove.paths>paths</span> | <code><a href="#glob">glob</a></code><br><p>The files to be deleted</p>


<h4 id="example.core.remove">Examples:</h4>


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


<a id="core.rename" aria-hidden="true"></a>
### core.rename

A transformation for renaming several filenames in the working directory. This is a simplified version of core.move() for just renaming filenames without needing to use regex_groups. Note that it doesn't rename directories, only regular files.

<code><a href="#transformation">transformation</a></code> <code>core.rename(<a href=#core.rename.before>before</a>, <a href=#core.rename.after>after</a>, <a href=#core.rename.paths>paths</a>=glob(["**"]), <a href=#core.rename.overwrite>overwrite</a>=False, <a href=#core.rename.suffix>suffix</a>=False)</code>


<h4 id="parameters.core.rename">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.rename.before href=#core.rename.before>before</span> | <code><a href="#string">string</a></code><br><p>The filepath or suffix to change</p>
<span id=core.rename.after href=#core.rename.after>after</span> | <code><a href="#string">string</a></code><br><p>A filepath or suffix to use as replacement</p>
<span id=core.rename.paths href=#core.rename.paths>paths</span> | <code><a href="#glob">glob</a></code> or <code>list of string</code> or <code>NoneType</code><br><p>A glob expression relative to 'before' if it represents a directory. Only files matching the expression will be renamed. For example, glob(["**.java"]), matches all java files recursively inside 'before' folder. Defaults to match all the files recursively. Note that if reversible transformation is needed, the glob should match the filenames too in that case (or alternatively use an explicit reversal by using `core.transformation()`.</p>
<span id=core.rename.overwrite href=#core.rename.overwrite>overwrite</span> | <code><a href="#bool">bool</a></code><br><p>Overwrite destination files if they already exist. Note that this makes the transformation non-reversible, since there is no way to know if the file was overwritten or not in the reverse workflow.</p>
<span id=core.rename.suffix href=#core.rename.suffix>suffix</span> | <code><a href="#bool">bool</a></code><br><p>By default before/after match whole path segments. e.g. before = "FOO" wouldn't match `example/barFOO`. Sometimes only part of the path name needs to be replaced, e.g. renaming extensions. When `suffix` is set to true, it will match partial parts of the path string.</p>


<h4 id="example.core.rename">Examples:</h4>


##### Rename files:

Rename all FOO files:

```python
core.rename("FOO", "FOO.txt")
```

In this example, any `FOO` in any directory will be renamed to `FOO.txt`.


##### Rename extension:

Rename *.md files to *.txt files:

```python
core.rename(".md", ".txt", suffix = True)
```

In this example, `foo/bar.md` will be renamed to `foo/bar.txt`.


##### Rename files only in certain paths:

Renaming files in certain paths:

```python
core.rename("/FOO", "/FOO.txt", paths = glob(['dir1/**', 'dir2/**']))
```

In this example, `dir1/FOO` will be renamed to `dir1/FOO.txt`. Note that FOO files outside `dir1` and `dir2` won't be renamed


<a id="core.replace" aria-hidden="true"></a>
### core.replace

Replace a text with another text using optional regex groups. This transformation can be automatically reversed.

<code><a href="#transformation">transformation</a></code> <code>core.replace(<a href=#core.replace.before>before</a>, <a href=#core.replace.after>after</a>, <a href=#core.replace.regex_groups>regex_groups</a>={}, <a href=#core.replace.paths>paths</a>=glob(["**"]), <a href=#core.replace.first_only>first_only</a>=False, <a href=#core.replace.multiline>multiline</a>=False, <a href=#core.replace.repeated_groups>repeated_groups</a>=False, <a href=#core.replace.ignore>ignore</a>=[])</code>


<h4 id="parameters.core.replace">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.replace.before href=#core.replace.before>before</span> | <code><a href="#string">string</a></code><br><p>The text before the transformation. Can contain references to regex groups. For example "foo${x}text".<p>`before` can only contain 1 reference to each unique `regex_group`. If you require multiple references to the same `regex_group`, add `repeated_groups: True`.<p>If '$' literal character needs to be matched, '`$$`' should be used. For example '`$$FOO`' would match the literal '$FOO'. [Note this argument is a string. If you want to match a regular expression it must be encoded as a regex_group.]</p>
<span id=core.replace.after href=#core.replace.after>after</span> | <code><a href="#string">string</a></code><br><p>The text after the transformation. It can also contain references to regex groups, like 'before' field.</p>
<span id=core.replace.regex_groups href=#core.replace.regex_groups>regex_groups</span> | <code><a href="#dict">dict</a></code><br><p>A set of named regexes that can be used to match part of the replaced text.Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax. For example {"x": "[A-Za-z]+"}</p>
<span id=core.replace.paths href=#core.replace.paths>paths</span> | <code><a href="#glob">glob</a></code> or <code>list of string</code> or <code>NoneType</code><br><p>A glob expression relative to the workdir representing the files to apply the transformation. For example, glob(["**.java"]), matches all java files recursively. Defaults to match all the files recursively.</p>
<span id=core.replace.first_only href=#core.replace.first_only>first_only</span> | <code><a href="#bool">bool</a></code><br><p>If true, only replaces the first instance rather than all. In single line mode, replaces the first instance on each line. In multiline mode, replaces the first instance in each file.</p>
<span id=core.replace.multiline href=#core.replace.multiline>multiline</span> | <code><a href="#bool">bool</a></code><br><p>Whether to replace text that spans more than one line.</p>
<span id=core.replace.repeated_groups href=#core.replace.repeated_groups>repeated_groups</span> | <code><a href="#bool">bool</a></code><br><p>Allow to use a group multiple times. For example foo${repeated}/${repeated}. Note that this won't match "fooX/Y". This mechanism doesn't use backtracking. In other words, the group instances are treated as different groups in regex construction and then a validation is done after that.</p>
<span id=core.replace.ignore href=#core.replace.ignore>ignore</span> | <code>sequence</code><br><p>A set of regexes. If the entire content of any line (or file, if `multiline` is enabled) matches any expression in this set, then Copybara will not apply this transformation to any text there. Because `ignore` is matched against the entire line (or entire file under `multiline`), not just the parts that match `before`, the `ignore` regex can refer to text outside the span that would be replaced.</p>


<h4 id="example.core.replace">Examples:</h4>


##### Simple replacement:

Replaces the text "internal" with "external" in all java files

```python
core.replace(
    before = "internal",
    after = "external",
    paths = glob(["**.java"]),
)
```


##### Simple replacement in a specific file:

Replaces the text "internal" with "external" in all java files

```python
core.replace(
    before = "internal",
    after = "external",
    paths = ['foo/bar.txt'],
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


##### Replace with ignore:

This example replaces go links that shouldn't be in a public repository with `(broken link)`, but ignores any lines that contain `bazelbuild/rules_go/`, to avoid replacing file paths present in the text.

```python
core.replace(
        before = "${x}",
        after = "(broken link)",
        regex_groups = {
            "x": "(go|goto)/[-/_#a-zA-Z0-9?]*(.md|)",
        },
        ignore = [".*bazelbuild/rules_go/.*"],
    )
```

This replace would transform a text file like:

```
public code
go/copybara ... public code
public code ... go/copybara
go/copybara ... foo/bazelbuild/rules_go/bar
foo/bazelbuild/rules_go/baz ... go/copybara
```

Into:

```
public code
(broken link) ... public code
public code ... (broken link)
go/copybara ... foo/bazelbuild/rules_go/bar
foo/bazelbuild/rules_go/baz ... go/copybara
```

Note that the `go/copybara` links on lines that matched the ignore regex were not replaced. The transformation ignored these lines entirely.


<a id="core.replace_mapper" aria-hidden="true"></a>
### core.replace_mapper

A mapping function that applies a list of replaces until one replaces the text (Unless `all = True` is used). This should be used with core.filter_replace or other transformations that accept text mapping as parameter.

<code><a href="#mapping_function">mapping_function</a></code> <code>core.replace_mapper(<a href=#core.replace_mapper.mapping>mapping</a>, <a href=#core.replace_mapper.all>all</a>=False)</code>


<h4 id="parameters.core.replace_mapper">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.replace_mapper.mapping href=#core.replace_mapper.mapping>mapping</span> | <code>sequence of <a href="#transformation">transformation</a></code><br><p>The list of core.replace transformations</p>
<span id=core.replace_mapper.all href=#core.replace_mapper.all>all</span> | <code><a href="#bool">bool</a></code><br><p>Run all the mappings despite a replace happens.</p>

<a id="core.reverse" aria-hidden="true"></a>
### core.reverse

Given a list of transformations, returns the list of transformations equivalent to undoing all the transformations

<code>list of transformation</code> <code>core.reverse(<a href=#core.reverse.transformations>transformations</a>)</code>


<h4 id="parameters.core.reverse">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.reverse.transformations href=#core.reverse.transformations>transformations</span> | <code>sequence of <a href="#transformation">transformation</a></code><br><p>The transformations to reverse</p>

<a id="core.todo_replace" aria-hidden="true"></a>
### core.todo_replace

Replace Google style TODOs. For example `TODO(username, othername)`.

<code><a href="#transformation">transformation</a></code> <code>core.todo_replace(<a href=#core.todo_replace.tags>tags</a>=['TODO', 'NOTE'], <a href=#core.todo_replace.mapping>mapping</a>={}, <a href=#core.todo_replace.mode>mode</a>='MAP_OR_IGNORE', <a href=#core.todo_replace.paths>paths</a>=glob(["**"]), <a href=#core.todo_replace.default>default</a>=None, <a href=#core.todo_replace.ignore>ignore</a>=None)</code>


<h4 id="parameters.core.todo_replace">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.todo_replace.tags href=#core.todo_replace.tags>tags</span> | <code>sequence of <a href="#string">string</a></code><br><p>Prefix tag to look for</p>
<span id=core.todo_replace.mapping href=#core.todo_replace.mapping>mapping</span> | <code><a href="#dict">dict</a></code><br><p>Mapping of users/strings</p>
<span id=core.todo_replace.mode href=#core.todo_replace.mode>mode</span> | <code><a href="#string">string</a></code><br><p>Mode for the replace:<ul><li>'MAP_OR_FAIL': Try to use the mapping and if not found fail.</li><li>'MAP_OR_IGNORE': Try to use the mapping but ignore if no mapping found.</li><li>'MAP_OR_DEFAULT': Try to use the mapping and use the default if not found.</li><li>'SCRUB_NAMES': Scrub all names from TODOs. Transforms 'TODO(foo)' to 'TODO'</li><li>'USE_DEFAULT': Replace any TODO(foo, bar) with TODO(default_string)</li></ul></p>
<span id=core.todo_replace.paths href=#core.todo_replace.paths>paths</span> | <code><a href="#glob">glob</a></code> or <code>list of string</code> or <code>NoneType</code><br><p>A glob expression relative to the workdir representing the files to apply the transformation. For example, glob(["**.java"]), matches all java files recursively. Defaults to match all the files recursively.</p>
<span id=core.todo_replace.default href=#core.todo_replace.default>default</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Default value if mapping not found. Only valid for 'MAP_OR_DEFAULT' or 'USE_DEFAULT' modes</p>
<span id=core.todo_replace.ignore href=#core.todo_replace.ignore>ignore</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>If set, elements within TODO (with usernames) that match the regex will be ignored. For example ignore = "foo" would ignore "foo" in "TODO(foo,bar)" but not "bar".</p>


<h4 id="example.core.todo_replace">Examples:</h4>


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

<code><a href="#transformation">transformation</a></code> <code>core.transform(<a href=#core.transform.transformations>transformations</a>, <a href=#core.transform.reversal>reversal</a>=The reverse of 'transformations', <a href=#core.transform.name>name</a>=None, <a href=#core.transform.ignore_noop>ignore_noop</a>=None, <a href=#core.transform.noop_behavior>noop_behavior</a>=NOOP_IF_ANY_NOOP)</code>


<h4 id="parameters.core.transform">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.transform.transformations href=#core.transform.transformations>transformations</span> | <code>sequence of <a href="#transformation">transformation</a></code><br><p>The list of transformations to run as a result of running this transformation.</p>
<span id=core.transform.reversal href=#core.transform.reversal>reversal</span> | <code>sequence of <a href="#transformation">transformation</a></code> or <code>NoneType</code><br><p>The list of transformations to run as a result of running this transformation in reverse.</p>
<span id=core.transform.name href=#core.transform.name>name</span> | <code>unknown</code><br><p>Optional string identifier to name this transform. This can be used for better output readability or with the --skip-transforms flag.</p>
<span id=core.transform.ignore_noop href=#core.transform.ignore_noop>ignore_noop</span> | <code><a href="#bool">bool</a></code> or <code>NoneType</code><br><p>WARNING: Deprecated. Use `noop_behavior` instead.<br>In case a noop error happens in the group of transformations (Both forward and reverse), it will be ignored, but the rest of the transformations in the group will still be executed. If ignore_noop is not set, we will apply the closest parent's ignore_noop.</p>
<span id=core.transform.noop_behavior href=#core.transform.noop_behavior>noop_behavior</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>How to handle no-op transformations:<br><ul> <li><b>'IGNORE_NOOP'</b>: Any no-ops among the wrapped transformations are ignored.</li> <li><b>'NOOP_IF_ANY_NOOP'</b>: Throws an exception as soon as a single wrapped transformation is a no-op.</li> <li><b>'NOOP_IF_ALL_NOOP'</b>: Ignores no-ops from the wrapped transformations unless they all no-op, in which case an exception is thrown.</li></ul></p>

<a id="core.verify_match" aria-hidden="true"></a>
### core.verify_match

Verifies that a RegEx matches (or not matches) the specified files. Does not transform anything, but will stop the workflow if it fails.

<code><a href="#transformation">transformation</a></code> <code>core.verify_match(<a href=#core.verify_match.regex>regex</a>, <a href=#core.verify_match.paths>paths</a>=glob(["**"]), <a href=#core.verify_match.verify_no_match>verify_no_match</a>=False, <a href=#core.verify_match.also_on_reversal>also_on_reversal</a>=False, <a href=#core.verify_match.failure_message>failure_message</a>=None)</code>


<h4 id="parameters.core.verify_match">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.verify_match.regex href=#core.verify_match.regex>regex</span> | <code><a href="#string">string</a></code><br><p>The regex pattern to verify. To satisfy the validation, there has to be atleast one (or no matches if verify_no_match) match in each of the files included in paths. The re2j pattern will be applied in multiline mode, i.e. '^' refers to the beginning of a file and '$' to its end. Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax.</p>
<span id=core.verify_match.paths href=#core.verify_match.paths>paths</span> | <code><a href="#glob">glob</a></code> or <code>list of string</code> or <code>NoneType</code><br><p>A glob expression relative to the workdir representing the files to apply the transformation. For example, glob(["**.java"]), matches all java files recursively. Defaults to match all the files recursively.</p>
<span id=core.verify_match.verify_no_match href=#core.verify_match.verify_no_match>verify_no_match</span> | <code><a href="#bool">bool</a></code><br><p>If true, the transformation will verify that the RegEx does not match.</p>
<span id=core.verify_match.also_on_reversal href=#core.verify_match.also_on_reversal>also_on_reversal</span> | <code><a href="#bool">bool</a></code><br><p>If true, the check will also apply on the reversal. The default behavior is to not verify the pattern on reversal.</p>
<span id=core.verify_match.failure_message href=#core.verify_match.failure_message>failure_message</span> | <code>unknown</code><br><p>Optional string that will be included in the failure message.</p>

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


<code>core.workflow(<a href=#core.workflow.name>name</a>, <a href=#core.workflow.origin>origin</a>, <a href=#core.workflow.destination>destination</a>, <a href=#core.workflow.authoring>authoring</a>, <a href=#core.workflow.transformations>transformations</a>=[], <a href=#core.workflow.origin_files>origin_files</a>=glob(["**"]), <a href=#core.workflow.destination_files>destination_files</a>=glob(["**"]), <a href=#core.workflow.mode>mode</a>="SQUASH", <a href=#core.workflow.reversible_check>reversible_check</a>=True for 'CHANGE_REQUEST' mode. False otherwise, <a href=#core.workflow.check_last_rev_state>check_last_rev_state</a>=False, <a href=#core.workflow.ask_for_confirmation>ask_for_confirmation</a>=False, <a href=#core.workflow.dry_run>dry_run</a>=False, <a href=#core.workflow.after_migration>after_migration</a>=[], <a href=#core.workflow.after_workflow>after_workflow</a>=[], <a href=#core.workflow.change_identity>change_identity</a>=None, <a href=#core.workflow.set_rev_id>set_rev_id</a>=True, <a href=#core.workflow.smart_prune>smart_prune</a>=False, <a href=#core.workflow.merge_import>merge_import</a>=None, <a href=#core.workflow.autopatch_config>autopatch_config</a>=None, <a href=#core.workflow.after_merge_transformations>after_merge_transformations</a>=[], <a href=#core.workflow.migrate_noop_changes>migrate_noop_changes</a>=False, <a href=#core.workflow.experimental_custom_rev_id>experimental_custom_rev_id</a>=None, <a href=#core.workflow.custom_rev_id>custom_rev_id</a>=None, <a href=#core.workflow.description>description</a>=None, <a href=#core.workflow.checkout>checkout</a>=True, <a href=#core.workflow.reversible_check_ignore_files>reversible_check_ignore_files</a>=None, <a href=#core.workflow.consistency_file_path>consistency_file_path</a>=None)</code>


<h4 id="parameters.core.workflow">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=core.workflow.name href=#core.workflow.name>name</span> | <code><a href="#string">string</a></code><br><p>The name of the workflow.</p>
<span id=core.workflow.origin href=#core.workflow.origin>origin</span> | <code><a href="#origin">origin</a></code><br><p>Where to read from the code to be migrated, before applying the transformations. This is usually a VCS like Git, but can also be a local folder or even a pending change in a code review system like Gerrit.</p>
<span id=core.workflow.destination href=#core.workflow.destination>destination</span> | <code><a href="#destination">destination</a></code><br><p>Where to write to the code being migrated, after applying the transformations. This is usually a VCS like Git, but can also be a local folder or even a pending change in a code review system like Gerrit.</p>
<span id=core.workflow.authoring href=#core.workflow.authoring>authoring</span> | <code><a href="#authoring_class">authoring_class</a></code><br><p>The author mapping configuration from origin to destination.</p>
<span id=core.workflow.transformations href=#core.workflow.transformations>transformations</span> | <code>sequence</code><br><p>The transformations to be run for this workflow. They will run in sequence.</p>
<span id=core.workflow.origin_files href=#core.workflow.origin_files>origin_files</span> | <code><a href="#glob">glob</a></code> or <code>list of string</code> or <code>NoneType</code><br><p>A glob or list of files relative to the workdir that will be read from the origin during the import. For example glob(["**.java"]), all java files, recursively, which excludes all other file types, or ['foo.java'] for a specific file.</p>
<span id=core.workflow.destination_files href=#core.workflow.destination_files>destination_files</span> | <code><a href="#glob">glob</a></code> or <code>list of string</code> or <code>NoneType</code><br><p>A glob relative to the root of the destination repository that matches files that are part of the migration. Files NOT matching this glob will never be removed, even if the file does not exist in the source. For example glob(['**'], exclude = ['**/BUILD']) keeps all BUILD files in destination when the origin does not have any BUILD files. You can also use this to limit the migration to a subdirectory of the destination, e.g. glob(['java/src/**'], exclude = ['**/BUILD']) to only affect non-BUILD files in java/src.</p>
<span id=core.workflow.mode href=#core.workflow.mode>mode</span> | <code><a href="#string">string</a></code><br><p>Workflow mode. Currently we support four modes:<br><ul><li><b>'SQUASH'</b>: Create a single commit in the destination with new tree state.</li><li><b>'ITERATIVE'</b>: Import each origin change individually.</li><li><b>'CHANGE_REQUEST'</b>: Import a pending change to the Source-of-Truth. This could be a GH Pull Request, a Gerrit Change, etc. The final intention should be to submit the change in the SoT (destination in this case).</li><li><b>'CHANGE_REQUEST_FROM_SOT'</b>: Import a pending change **from** the Source-of-Truth. This mode is useful when, despite the pending change being already in the SoT, the users want to review the code on a different system. The final intention should never be to submit in the destination, but just review or test</li></ul></p>
<span id=core.workflow.reversible_check href=#core.workflow.reversible_check>reversible_check</span> | <code><a href="#bool">bool</a></code> or <code>NoneType</code><br><p>Indicates if the tool should try to to reverse all the transformations at the end to check that they are reversible.<br/>The default value is True for 'CHANGE_REQUEST' mode. False otherwise</p>
<span id=core.workflow.check_last_rev_state href=#core.workflow.check_last_rev_state>check_last_rev_state</span> | <code><a href="#bool">bool</a></code><br><p>If set to true, Copybara will validate that the destination didn't change since last-rev import for destination_files. Note that this flag doesn't work for CHANGE_REQUEST mode.</p>
<span id=core.workflow.ask_for_confirmation href=#core.workflow.ask_for_confirmation>ask_for_confirmation</span> | <code><a href="#bool">bool</a></code><br><p>Indicates that the tool should show the diff and require user's confirmation before making a change in the destination.</p>
<span id=core.workflow.dry_run href=#core.workflow.dry_run>dry_run</span> | <code><a href="#bool">bool</a></code><br><p>Run the migration in dry-run mode. Some destination implementations might have some side effects (like creating a code review), but never submit to a main branch.</p>
<span id=core.workflow.after_migration href=#core.workflow.after_migration>after_migration</span> | <code>sequence</code><br><p>Run a feedback workflow after one migration happens. This runs once per change in `ITERATIVE` mode and only once for `SQUASH`.</p>
<span id=core.workflow.after_workflow href=#core.workflow.after_workflow>after_workflow</span> | <code>sequence</code><br><p>Run a feedback workflow after all the changes for this workflow run are migrated. Prefer `after_migration` as it is executed per change (in ITERATIVE mode). Tasks in this hook shouldn't be critical to execute. These actions shouldn't record effects (They'll be ignored).</p>
<span id=core.workflow.change_identity href=#core.workflow.change_identity>change_identity</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>By default, Copybara hashes several fields so that each change has an unique identifier that at the same time reuses the generated destination change. This allows to customize the identity hash generation so that the same identity is used in several workflows. At least ${copybara_config_path} has to be present. Current user is added to the hash automatically.<br><br>Available variables:<ul> <li>${copybara_config_path}: Main config file path</li> <li>${copybara_workflow_name}: The name of the workflow being run</li> <li>${copybara_reference}: The requested reference. In general Copybara tries its best to give a repetable reference. For example Gerrit change number or change-id or GitHub Pull Request number. If it cannot find a context reference it uses the resolved revision.</li> <li>${label:label_name}: A label present for the current change. Exposed in the message or not.</li></ul>If any of the labels cannot be found it defaults to the default identity (The effect would be no reuse of destination change between workflows)</p>
<span id=core.workflow.set_rev_id href=#core.workflow.set_rev_id>set_rev_id</span> | <code><a href="#bool">bool</a></code><br><p>Copybara adds labels like 'GitOrigin-RevId' in the destination in order to track what was the latest change imported. For `CHANGE_REQUEST` workflows it is not used and is purely informational. This field allows to disable it for that mode. Destinations might ignore the flag.</p>
<span id=core.workflow.smart_prune href=#core.workflow.smart_prune>smart_prune</span> | <code><a href="#bool">bool</a></code><br><p>By default CHANGE_REQUEST workflows cannot restore scrubbed files. This flag does a best-effort approach in restoring the non-affected snippets. For now we only revert the non-affected files. This only works for CHANGE_REQUEST mode.</p>
<span id=core.workflow.merge_import href=#core.workflow.merge_import>merge_import</span> | <code><a href="#bool">bool</a></code> or <code><a href="#coremerge_import_config">core.merge_import_config</a></code> or <code>NoneType</code><br><p>A migration mode that shells out to a diffing tool (default is diff3) to merge all files. The inputs to the diffing tool are (1) origin file (2) baseline file (3) destination file. This can be used to perpetuate destination-only changes in non source of truth repositories.</p>
<span id=core.workflow.autopatch_config href=#core.workflow.autopatch_config>autopatch_config</span> | <code><a href="#coreautopatch_config">core.autopatch_config</a></code> or <code>NoneType</code><br><p>Configuration that describes the setting for automatic patch file generation</p>
<span id=core.workflow.after_merge_transformations href=#core.workflow.after_merge_transformations>after_merge_transformations</span> | <code>sequence</code><br><p>Perform these transformations after merge_import, but before Copybara writes to the destination. Ex: any BUILD file generations that rely on the results of merge_import</p>
<span id=core.workflow.migrate_noop_changes href=#core.workflow.migrate_noop_changes>migrate_noop_changes</span> | <code><a href="#bool">bool</a></code><br><p>By default, Copybara tries to only migrate changes that affect origin_files or config files. This flag allows to include all the changes. Note that it might generate more empty changes errors. In `ITERATIVE` mode it might fail if some transformation is validating the message (Like has to contain 'PUBLIC' and the change doesn't contain it because it is internal).</p>
<span id=core.workflow.experimental_custom_rev_id href=#core.workflow.experimental_custom_rev_id>experimental_custom_rev_id</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>DEPRECATED(Remove by 2024/01/01: Use . Use this label name instead of the one provided by the origin.</p>
<span id=core.workflow.custom_rev_id href=#core.workflow.custom_rev_id>custom_rev_id</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>If the destination uses labels to mark the last change migrated, use this label name instead of the one provided by the origin. This allows to to have two migrations to the same destination without the other migration changes interfering this migration. I can also serve to clearly state where the change is coming from.</p>
<span id=core.workflow.description href=#core.workflow.description>description</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>A description of what this workflow achieves</p>
<span id=core.workflow.checkout href=#core.workflow.checkout>checkout</span> | <code><a href="#bool">bool</a></code><br><p>Allows disabling the checkout. The usage of this feature is rare. This could be used to update a file of your own repo when a dependant repo version changes and you are not interested on the files of the dependant repo, just the new version.</p>
<span id=core.workflow.reversible_check_ignore_files href=#core.workflow.reversible_check_ignore_files>reversible_check_ignore_files</span> | <code><a href="#glob">glob</a></code> or <code>list of string</code> or <code>NoneType</code><br><p>Ignore the files matching the glob in the reversible check</p>
<span id=core.workflow.consistency_file_path href=#core.workflow.consistency_file_path>consistency_file_path</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Under development. Must end with .bara.consistency</p>



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
<span style="white-space: nowrap;">`--disable-consistency-merge-import`</span> | *boolean* | If merge import is set to use consistency in the config, disable it for this run. This uses an import baseline instead. A new consistency file will still be generated.
<span style="white-space: nowrap;">`--force-author`</span> | *author* | Force the author to this. Note that this only changes the author before the transformations happen, you can still use the transformations to alter it.
<span style="white-space: nowrap;">`--force-message`</span> | *string* | Force the change description to this. Note that this only changes the message before the transformations happen, you can still use the transformations to alter it.
<span style="white-space: nowrap;">`--ignore-noop`</span> | *boolean* | Only warn about operations/transforms that didn't have any effect. For example: A transform that didn't modify any file, non-existent origin directories, etc.
<span style="white-space: nowrap;">`--import-noop-changes`</span> | *boolean* | By default Copybara will only try to migrate changes that could affect the destination. Ignoring changes that only affect excluded files in origin_files. This flag disables that behavior and runs for all the changes.
<span style="white-space: nowrap;">`--info-include-versions`</span> | *boolean* | Include upstream versions in the info command output.
<span style="white-space: nowrap;">`--init-history`</span> | *boolean* | Import all the changes from the beginning of the history up to the resolved ref. For 'ITERATIVE' workflows this will import individual changes since the first one. For 'SQUASH' it will import the squashed change up to the resolved ref. WARNING: Use with care, this flag should be used only for the very first run of Copybara for a workflow.
<span style="white-space: nowrap;">`--iterative-limit-changes`</span> | *int* | Import just a number of changes instead of all the pending ones
<span style="white-space: nowrap;">`--last-rev`</span> | *string* | Last revision that was migrated to the destination
<span style="white-space: nowrap;">`--nosmart-prune`</span> | *boolean* | Disable smart prunning
<span style="white-space: nowrap;">`--notransformation-join`</span> | *boolean* | By default Copybara tries to join certain transformations in one so that it is more efficient. This disables the feature.
<span style="white-space: nowrap;">`--read-config-from-change`</span> | *boolean* | For each imported origin change, load the workflow's origin_files, destination_files and transformations from the config version of that change. The rest of the fields (more importantly, origin and destination) cannot change and the version from the first config will be used.
<span style="white-space: nowrap;">`--read-config-from-change-disable`</span> | *boolean* | --read-config-from-change is a arity 0 flag, this flag overrides it to override it being enabled.
<span style="white-space: nowrap;">`--same-version`</span> | *boolean* | Re-import the last version imported. This is useful for example to check that a refactor in a copy.bara.sky file doesn't introduce accidental changes.
<span style="white-space: nowrap;">`--skip-transforms`</span> | *list* | List of transform names that should be skipped.
<span style="white-space: nowrap;">`--squash-skip-history`</span> | *boolean* | Avoid exposing the history of changes that are being migrated. This is useful when we want to migrate a new repository but we don't want to expose all the change history to metadata.squash_notes.
<span style="white-space: nowrap;">`--threads`</span> | *int* | Number of threads to use when running transformations that change lot of files
<span style="white-space: nowrap;">`--threads-for-merge-import`</span> | *int* | Number of threads to use for executing the diff tool for the merge import mode.
<span style="white-space: nowrap;">`--threads-min-size`</span> | *int* | Minimum size of the lists to process to run them in parallel
<span style="white-space: nowrap;">`--to-folder`</span> | *boolean* | Sometimes a user wants to test what the outcome would be for a workflow without changing the configuration or adding an auxiliary testing workflow. This flag allows to change an existing workflow to use folder.destination
<span style="white-space: nowrap;">`--workflow-identity-user`</span> | *string* | Use a custom string as a user for computing change identity



## core.autopatch_config

The configuration that describes automatic patch file generation


<h4 id="returned_by.core.autopatch_config">Returned By:</h4>

<ul><li><a href="#core.autopatch_config">core.autopatch_config</a></li></ul>
<h4 id="consumed_by.core.autopatch_config">Consumed By:</h4>

<ul><li><a href="#core.workflow">core.workflow</a></li></ul>



## credentials

Module for working with credentials.

<a id="credentials.static_secret" aria-hidden="true"></a>
### credentials.static_secret

Holder for secrets that can be in plaintext within the config.

<code>CredentialIssuer</code> <code>credentials.static_secret(<a href=#credentials.static_secret.name>name</a>, <a href=#credentials.static_secret.secret>secret</a>)</code>


<h4 id="parameters.credentials.static_secret">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=credentials.static_secret.name href=#credentials.static_secret.name>name</span> | <code><a href="#string">string</a></code><br><p>A name for this secret.</p>
<span id=credentials.static_secret.secret href=#credentials.static_secret.secret>secret</span> | <code><a href="#string">string</a></code><br><p>The secret value.</p>

<a id="credentials.static_value" aria-hidden="true"></a>
### credentials.static_value

Holder for credentials that are safe to read/log (e.g. 'x-access-token') .

<code>CredentialIssuer</code> <code>credentials.static_value(<a href=#credentials.static_value.value>value</a>)</code>


<h4 id="parameters.credentials.static_value">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=credentials.static_value.value href=#credentials.static_value.value>value</span> | <code><a href="#string">string</a></code><br><p>The open value.</p>

<a id="credentials.toml_key_source" aria-hidden="true"></a>
### credentials.toml_key_source

Supply an authentication credential from the file pointed to by the --http-credential-file flag.

<code>CredentialIssuer</code> <code>credentials.toml_key_source(<a href=#credentials.toml_key_source.dot_path>dot_path</a>)</code>


<h4 id="parameters.credentials.toml_key_source">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=credentials.toml_key_source.dot_path href=#credentials.toml_key_source.dot_path>dot_path</span> | <code><a href="#string">string</a></code><br><p>Dot path to the data field containing the credential.</p>

<a id="credentials.username_password" aria-hidden="true"></a>
### credentials.username_password

A pair of username and password credential issuers.

<code>UsernamePasswordIssuer</code> <code>credentials.username_password(<a href=#credentials.username_password.username>username</a>, <a href=#credentials.username_password.password>password</a>)</code>


<h4 id="parameters.credentials.username_password">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=credentials.username_password.username href=#credentials.username_password.username>username</span> | <code>CredentialIssuer</code><br><p>Username credential.</p>
<span id=credentials.username_password.password href=#credentials.username_password.password>password</span> | <code>CredentialIssuer</code><br><p>Password credential.</p>



## datetime

Module for datetime manipulation.

<a id="datetime.fromtimestamp" aria-hidden="true"></a>
### datetime.fromtimestamp

Returns a starlark_datetime object representation of the epoch time. The object is timezone aware.

<code><a href="#starlarkdatetime">StarlarkDateTime</a></code> <code>datetime.fromtimestamp(<a href=#datetime.fromtimestamp.timestamp>timestamp</a>=0, <a href=#datetime.fromtimestamp.tz>tz</a>='America/Los_Angeles')</code>


<h4 id="parameters.datetime.fromtimestamp">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=datetime.fromtimestamp.timestamp href=#datetime.fromtimestamp.timestamp>timestamp</span> | <code><a href="#int">int</a></code><br><p>Epoch time in seconds.</p>
<span id=datetime.fromtimestamp.tz href=#datetime.fromtimestamp.tz>tz</span> | <code><a href="#string">string</a></code><br><p>The timezone. E.g. America/New_York, Asia/Tokyo, Europe/Rome, etc.</p>

<a id="datetime.now" aria-hidden="true"></a>
### datetime.now

Returns a starlark_datetime object. The object is timezone aware.

<code><a href="#starlarkdatetime">StarlarkDateTime</a></code> <code>datetime.now(<a href=#datetime.now.tz>tz</a>='America/Los_Angeles')</code>


<h4 id="parameters.datetime.now">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=datetime.now.tz href=#datetime.now.tz>tz</span> | <code><a href="#string">string</a></code><br><p>The timezone. E.g. America/New_York, Asia/Tokyo, Europe/Rome</p>



## description_checker

A checker to be run on change descriptions



## destination

A repository which a source of truth can be copied to


<h4 id="returned_by.destination">Returned By:</h4>

<ul><li><a href="#folder.destination">folder.destination</a></li><li><a href="#git.destination">git.destination</a></li><li><a href="#git.gerrit_destination">git.gerrit_destination</a></li><li><a href="#git.github_destination">git.github_destination</a></li><li><a href="#git.github_pr_destination">git.github_pr_destination</a></li></ul>
<h4 id="consumed_by.destination">Consumed By:</h4>

<ul><li><a href="#core.workflow">core.workflow</a></li></ul>



## destination_effect

Represents an effect that happened in the destination due to a single migration


<h4 id="fields.destination_effect">Fields:</h4>

Name | Description
---- | -----------
destination_ref | <code><a href="#destination_ref">destination_ref</a></code><br><p>Destination reference updated/created. Might be null if there was no effect. Might be set even if the type is error (For example a synchronous presubmit test failed but a review was created).</p>
errors | <code>list of string</code><br><p>List of errors that happened during the migration</p>
origin_refs | <code>list of origin_ref</code><br><p>List of origin changes that were included in this migration</p>
summary | <code><a href="#string">string</a></code><br><p>Textual summary of what happened. Users of this class should not try to parse this field.</p>
type | <code><a href="#string">string</a></code><br><p>Return the type of effect that happened: CREATED, UPDATED, NOOP, INSUFFICIENT_APPROVALS or ERROR</p>



## destination_reader

Handle to read from the destination


<h4 id="returned_by.destination_reader">Returned By:</h4>

<ul><li><a href="#ctx.destination_reader">ctx.destination_reader</a></li></ul>

<a id="destination_reader.copy_destination_files" aria-hidden="true"></a>
### destination_reader.copy_destination_files

Copy files from the destination into the workdir.

<code>destination_reader.copy_destination_files(<a href=#destination_reader.copy_destination_files.glob>glob</a>, <a href=#destination_reader.copy_destination_files.path>path</a>=None)</code>


<h4 id="parameters.destination_reader.copy_destination_files">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=destination_reader.copy_destination_files.glob href=#destination_reader.copy_destination_files.glob>glob</span> | <code><a href="#glob">glob</a></code> or <code>list of string</code> or <code>NoneType</code><br><p>Files to copy to the workdir, potentially overwriting files checked out from the origin.</p>
<span id=destination_reader.copy_destination_files.path href=#destination_reader.copy_destination_files.path>path</span> | <code><a href="#path">Path</a></code> or <code>NoneType</code><br><p>Optional path to copy the files to</p>


<h4 id="example.destination_reader.copy_destination_files">Example:</h4>


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

<code><a href="#bool">bool</a></code> <code>destination_reader.file_exists(<a href=#destination_reader.file_exists.path>path</a>)</code>


<h4 id="parameters.destination_reader.file_exists">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=destination_reader.file_exists.path href=#destination_reader.file_exists.path>path</span> | <code><a href="#string">string</a></code><br><p>Path to the file.</p>

<a id="destination_reader.read_file" aria-hidden="true"></a>
### destination_reader.read_file

Read a file from the destination.

<code><a href="#string">string</a></code> <code>destination_reader.read_file(<a href=#destination_reader.read_file.path>path</a>)</code>


<h4 id="parameters.destination_reader.read_file">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=destination_reader.read_file.path href=#destination_reader.read_file.path>path</span> | <code><a href="#string">string</a></code><br><p>Path to the file.</p>


<h4 id="example.destination_reader.read_file">Example:</h4>


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


<h4 id="fields.destination_ref">Fields:</h4>

Name | Description
---- | -----------
id | <code><a href="#string">string</a></code><br><p>Destination reference id</p>
type | <code><a href="#string">string</a></code><br><p>Type of reference created. Each destination defines its own and guarantees to be more stable than urls/ids</p>
url | <code><a href="#string">string</a></code><br><p>Url, if any, of the destination change</p>


<h4 id="returned_by.destination_ref">Returned By:</h4>

<ul><li><a href="#endpoint.new_destination_ref">endpoint.new_destination_ref</a></li><li><a href="#gerrit_api_obj.new_destination_ref">gerrit_api_obj.new_destination_ref</a></li><li><a href="#github_api_obj.new_destination_ref">github_api_obj.new_destination_ref</a></li><li><a href="#http_endpoint.new_destination_ref">http_endpoint.new_destination_ref</a></li></ul>
<h4 id="consumed_by.destination_ref">Consumed By:</h4>

<ul><li><a href="#feedback.context.record_effect">feedback.context.record_effect</a></li><li><a href="#feedback.finish_hook_context.record_effect">feedback.finish_hook_context.record_effect</a></li><li><a href="#git.mirrorContext.record_effect">git.mirrorContext.record_effect</a></li></ul>



## dict

dict is a built-in type representing an associative mapping or <i>dictionary</i>. A dictionary supports indexing using <code>d[k]</code> and key membership testing using <code>k in d</code>; both operations take constant time. Unfrozen dictionaries are mutable, and may be updated by assigning to <code>d[k]</code> or by calling certain methods. Dictionaries are iterable; iteration yields the sequence of keys in insertion order. Iteration order is unaffected by updating the value associated with an existing key, but is affected by removing then reinserting a key.
<pre>d = {0: "x", 2: "z", 1: "y"}
[k for k in d]  # [0, 2, 1]
d.pop(2)
d[0], d[2] = "a", "b"
0 in d, "a" in d  # (True, False)
[(k, v) for k, v in d.items()]  # [(0, "a"), (1, "y"), (2, "b")]
</pre>
<p>There are four ways to construct a dictionary:
<ol>
<li>A dictionary expression <code>{k: v, ...}</code> yields a new dictionary with the specified key/value entries, inserted in the order they appear in the expression. Evaluation fails if any two key expressions yield the same value.</li><li>A dictionary comprehension <code>{k: v for vars in seq}</code> yields a new dictionary into which each key/value pair is inserted in loop iteration order. Duplicates are permitted: the first insertion of a given key determines its position in the sequence, and the last determines its associated value.
<pre class="language-python">
{k: v for k, v in (("a", 0), ("b", 1), ("a", 2))}  # {"a": 2, "b": 1}
{i: 2*i for i in range(3)}  # {0: 0, 1: 2, 2: 4}
</pre></li><li>A call to the built-in <a href="#dict">dict</a> function returns a dictionary containing the specified entries, which are inserted in argument order, positional arguments before named. As with comprehensions, duplicate keys are permitted.</li><li>The union expression <code>x | y</code> yields a new dictionary by combining two existing dictionaries. If the two dictionaries have a key <code>k</code> in common, the right hand side dictionary's value of the key (in other words, <code>y[k]</code>) wins. The <code>|=</code> variant of the union operator modifies a dictionary in-place. Example:<br><pre class=language-python>d = {"foo": "FOO", "bar": "BAR"} | {"foo": "FOO2", "baz": "BAZ"}
# d == {"foo": "FOO2", "bar": "BAR", "baz": "BAZ"}
d = {"a": 1, "b": 2}
d |= {"b": 3, "c": 4}
# d == {"a": 1, "b": 3, "c": 4}</pre></li></ol>


<h4 id="returned_by.dict">Returned By:</h4>

<ul><li><a href="#dict">dict</a></li></ul>
<h4 id="consumed_by.dict">Consumed By:</h4>

<ul><li><a href="#core.action">core.action</a></li><li><a href="#core.copy">core.copy</a></li><li><a href="#core.dynamic_feedback">core.dynamic_feedback</a></li><li><a href="#core.dynamic_transform">core.dynamic_transform</a></li><li><a href="#core.latest_version">core.latest_version</a></li><li><a href="#core.move">core.move</a></li><li><a href="#core.replace">core.replace</a></li><li><a href="#core.todo_replace">core.todo_replace</a></li><li><a href="#dict.update">dict.update</a></li><li><a href="#git.latest_version">git.latest_version</a></li><li><a href="#git.review_input">git.review_input</a></li><li><a href="#dict">dict</a></li><li><a href="#http.endpoint">http.endpoint</a></li><li><a href="#http.trigger">http.trigger</a></li><li><a href="#http.urlencoded_form">http.urlencoded_form</a></li><li><a href="#http_endpoint.delete">http_endpoint.delete</a></li><li><a href="#http_endpoint.get">http_endpoint.get</a></li><li><a href="#http_endpoint.post">http_endpoint.post</a></li><li><a href="#metadata.map_author">metadata.map_author</a></li><li><a href="#metadata.map_references">metadata.map_references</a></li><li><a href="#string.format">string.format</a></li><li><a href="#struct">struct</a></li></ul>

<a id="dict.clear" aria-hidden="true"></a>
### dict.clear

Remove all items from the dictionary.

<code>dict.clear()</code>

<a id="dict.get" aria-hidden="true"></a>
### dict.get

Returns the value for <code>key</code> if <code>key</code> is in the dictionary, else <code>default</code>. If <code>default</code> is not given, it defaults to <code>None</code>, so that this method never throws an error.

<code>unknown</code> <code>dict.get(<a href=#dict.get.key>key</a>, <a href=#dict.get.default>default</a>=None)</code>


<h4 id="parameters.dict.get">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=dict.get.key href=#dict.get.key>key</span> | <code>unknown</code><br><p>The key to look for.</p>
<span id=dict.get.default href=#dict.get.default>default</span> | <code>unknown</code><br><p>The default value to use (instead of None) if the key is not found.</p>

<a id="dict.items" aria-hidden="true"></a>
### dict.items

Returns the list of key-value tuples:<pre class="language-python">{2: "a", 4: "b", 1: "c"}.items() == [(2, "a"), (4, "b"), (1, "c")]</pre>


<code>sequence</code> <code>dict.items()</code>

<a id="dict.keys" aria-hidden="true"></a>
### dict.keys

Returns the list of keys:<pre class="language-python">{2: "a", 4: "b", 1: "c"}.keys() == [2, 4, 1]</pre>


<code>sequence</code> <code>dict.keys()</code>

<a id="dict.pop" aria-hidden="true"></a>
### dict.pop

Removes a <code>key</code> from the dict, and returns the associated value. If no entry with that key was found, remove nothing and return the specified <code>default</code> value; if no default value was specified, fail instead.

<code>unknown</code> <code>dict.pop(<a href=#dict.pop.key>key</a>, <a href=#dict.pop.default>default</a>=unbound)</code>


<h4 id="parameters.dict.pop">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=dict.pop.key href=#dict.pop.key>key</span> | <code>unknown</code><br><p>The key.</p>
<span id=dict.pop.default href=#dict.pop.default>default</span> | <code>unknown</code><br><p>a default value if the key is absent.</p>

<a id="dict.popitem" aria-hidden="true"></a>
### dict.popitem

Remove and return the first <code>(key, value)</code> pair from the dictionary. <code>popitem</code> is useful to destructively iterate over a dictionary, as often used in set algorithms. If the dictionary is empty, the <code>popitem</code> call fails.

<code><a href="#tuple">tuple</a></code> <code>dict.popitem()</code>

<a id="dict.setdefault" aria-hidden="true"></a>
### dict.setdefault

If <code>key</code> is in the dictionary, return its value. If not, insert key with a value of <code>default</code> and return <code>default</code>. <code>default</code> defaults to <code>None</code>.

<code>?</code> <code>dict.setdefault(<a href=#dict.setdefault.key>key</a>, <a href=#dict.setdefault.default>default</a>=None)</code>


<h4 id="parameters.dict.setdefault">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=dict.setdefault.key href=#dict.setdefault.key>key</span> | <code>?</code><br><p>The key.</p>
<span id=dict.setdefault.default href=#dict.setdefault.default>default</span> | <code>?</code><br><p>a default value if the key is absent.</p>

<a id="dict.update" aria-hidden="true"></a>
### dict.update

Updates the dictionary first with the optional positional argument, <code>pairs</code>,  then with the optional keyword arguments
If the positional argument is present, it must be a dict, iterable, or None.
If it is a dict, then its key/value pairs are inserted into this dict. If it is an iterable, it must provide a sequence of pairs (or other iterables of length 2), each of which is treated as a key/value pair to be inserted.
Each keyword argument <code>name=value</code> causes the name/value pair to be inserted into this dict.

<code>dict.update(<a href=#dict.update.pairs>pairs</a>=[], <a href=#dict.update.kwargs>kwargs</a>)</code>


<h4 id="parameters.dict.update">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=dict.update.pairs href=#dict.update.pairs>pairs</span> | <code>unknown</code><br><p>Either a dictionary or a list of entries. Entries must be tuples or lists with exactly two elements: key, value.</p>
<span id=dict.update.kwargs href=#dict.update.kwargs>kwargs</span> | <code><a href="#dict">dict</a></code><br><p>Dictionary of additional entries.</p>

<a id="dict.values" aria-hidden="true"></a>
### dict.values

Returns the list of values:<pre class="language-python">{2: "a", 4: "b", 1: "c"}.values() == ["a", "b", "c"]</pre>


<code>sequence</code> <code>dict.values()</code>



## dynamic.action_result

Result objects created by actions to tell Copybara what happened.


<h4 id="fields.dynamic.action_result">Fields:</h4>

Name | Description
---- | -----------
msg | <code><a href="#string">string</a></code><br><p>The message associated with the result</p>
result | <code><a href="#string">string</a></code><br><p>The result of this action</p>


<h4 id="returned_by.dynamic.action_result">Returned By:</h4>

<ul><li><a href="#feedback.context.error">feedback.context.error</a></li><li><a href="#feedback.context.noop">feedback.context.noop</a></li><li><a href="#feedback.context.success">feedback.context.success</a></li><li><a href="#feedback.finish_hook_context.error">feedback.finish_hook_context.error</a></li><li><a href="#feedback.finish_hook_context.noop">feedback.finish_hook_context.noop</a></li><li><a href="#feedback.finish_hook_context.success">feedback.finish_hook_context.success</a></li><li><a href="#git.mirrorContext.error">git.mirrorContext.error</a></li><li><a href="#git.mirrorContext.noop">git.mirrorContext.noop</a></li><li><a href="#git.mirrorContext.success">git.mirrorContext.success</a></li></ul>



## endpoint

An origin or destination API in a feedback migration.


<h4 id="fields.endpoint">Fields:</h4>

Name | Description
---- | -----------
url | <code><a href="#string">string</a></code><br><p>Return the URL of this endpoint.</p>


<h4 id="returned_by.endpoint">Returned By:</h4>

<ul><li><a href="#ctx.destination_api">ctx.destination_api</a></li><li><a href="#ctx.origin_api">ctx.origin_api</a></li></ul>

<a id="endpoint.new_destination_ref" aria-hidden="true"></a>
### endpoint.new_destination_ref

Creates a new destination reference out of this endpoint.

<code><a href="#destination_ref">destination_ref</a></code> <code>endpoint.new_destination_ref(<a href=#endpoint.new_destination_ref.ref>ref</a>, <a href=#endpoint.new_destination_ref.type>type</a>, <a href=#endpoint.new_destination_ref.url>url</a>=None)</code>


<h4 id="parameters.endpoint.new_destination_ref">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=endpoint.new_destination_ref.ref href=#endpoint.new_destination_ref.ref>ref</span> | <code><a href="#string">string</a></code><br><p>The reference.</p>
<span id=endpoint.new_destination_ref.type href=#endpoint.new_destination_ref.type>type</span> | <code><a href="#string">string</a></code><br><p>The type of this reference.</p>
<span id=endpoint.new_destination_ref.url href=#endpoint.new_destination_ref.url>url</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>The url associated with this reference, if any.</p>

<a id="endpoint.new_origin_ref" aria-hidden="true"></a>
### endpoint.new_origin_ref

Creates a new origin reference out of this endpoint.

<code><a href="#origin_ref">origin_ref</a></code> <code>endpoint.new_origin_ref(<a href=#endpoint.new_origin_ref.ref>ref</a>)</code>


<h4 id="parameters.endpoint.new_origin_ref">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=endpoint.new_origin_ref.ref href=#endpoint.new_origin_ref.ref>ref</span> | <code><a href="#string">string</a></code><br><p>The reference.</p>



## feedback.context

Gives access to the feedback migration information and utilities. This context is a concrete implementation for feedback migrations.


<h4 id="fields.feedback.context">Fields:</h4>

Name | Description
---- | -----------
action_name | <code><a href="#string">string</a></code><br><p>The name of the current action.</p>
cli_labels | <code>dict[<a href="#string">string</a>, <a href="#string">string</a>]</code><br><p>Access labels that a user passes through flag '--labels'. For example: --labels=foo:value1,bar:value2. Then it can access in this way:cli_labels['foo'].</p>
console | <code>Console</code><br><p>Get an instance of the console to report errors or warnings</p>
destination | <code><a href="#endpoint">endpoint</a></code><br><p>An object representing the destination. Can be used to query or modify the destination state</p>
endpoints | <code>structure</code><br><p>An object that gives access to the API of the configured endpoints</p>
feedback_name | <code><a href="#string">string</a></code><br><p>DEPRECATED: The name of the Feedback migration calling this action. Use migration_name instead.</p>
fs | <code>action.filesystem</code><br><p>If a migration of type `core.action_migration` sets `filesystem = True`, it gives access to the underlying migration filesystem to manipulate files.</p>
migration_name | <code><a href="#string">string</a></code><br><p>The name of the migration calling this action.</p>
origin | <code><a href="#endpoint">endpoint</a></code><br><p>An object representing the origin. Can be used to query about the ref or modifying the origin state</p>
params | <code><a href="#dict">dict</a></code><br><p>Parameters for the function if created with core.action</p>
refs | <code>list of string</code><br><p>A list containing string representations of the entities that triggered the event</p>

<a id="feedback.context.error" aria-hidden="true"></a>
### feedback.context.error

Returns an error action result.

<code><a href="#dynamicaction_result">dynamic.action_result</a></code> <code>feedback.context.error(<a href=#feedback.context.error.msg>msg</a>)</code>


<h4 id="parameters.feedback.context.error">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=feedback.context.error.msg href=#feedback.context.error.msg>msg</span> | <code><a href="#string">string</a></code><br><p>The error message</p>

<a id="feedback.context.noop" aria-hidden="true"></a>
### feedback.context.noop

Returns a no op action result with an optional message.

<code><a href="#dynamicaction_result">dynamic.action_result</a></code> <code>feedback.context.noop(<a href=#feedback.context.noop.msg>msg</a>=None)</code>


<h4 id="parameters.feedback.context.noop">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=feedback.context.noop.msg href=#feedback.context.noop.msg>msg</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>The no op message</p>

<a id="feedback.context.record_effect" aria-hidden="true"></a>
### feedback.context.record_effect

Records an effect of the current action.

<code>feedback.context.record_effect(<a href=#feedback.context.record_effect.summary>summary</a>, <a href=#feedback.context.record_effect.origin_refs>origin_refs</a>, <a href=#feedback.context.record_effect.destination_ref>destination_ref</a>, <a href=#feedback.context.record_effect.errors>errors</a>=[], <a href=#feedback.context.record_effect.type>type</a>="UPDATED")</code>


<h4 id="parameters.feedback.context.record_effect">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=feedback.context.record_effect.summary href=#feedback.context.record_effect.summary>summary</span> | <code><a href="#string">string</a></code><br><p>The summary of this effect</p>
<span id=feedback.context.record_effect.origin_refs href=#feedback.context.record_effect.origin_refs>origin_refs</span> | <code>sequence of <a href="#origin_ref">origin_ref</a></code><br><p>The origin refs</p>
<span id=feedback.context.record_effect.destination_ref href=#feedback.context.record_effect.destination_ref>destination_ref</span> | <code><a href="#destination_ref">destination_ref</a></code><br><p>The destination ref</p>
<span id=feedback.context.record_effect.errors href=#feedback.context.record_effect.errors>errors</span> | <code>sequence of <a href="#string">string</a></code><br><p>An optional list of errors</p>
<span id=feedback.context.record_effect.type href=#feedback.context.record_effect.type>type</span> | <code><a href="#string">string</a></code><br><p>The type of migration effect:<br><ul><li><b>'CREATED'</b>: A new review or change was created.</li><li><b>'UPDATED'</b>: An existing review or change was updated.</li><li><b>'NOOP'</b>: The change was a noop.</li><li><b>'NOOP_AGAINST_PENDING_CHANGE'</b>: The change was a noop, relativeto an existing pending change.</li><li><b>'INSUFFICIENT_APPROVALS'</b>: The effect couldn't happen because the change doesn't have enough approvals.</li><li><b>'ERROR'</b>: A user attributable error happened that prevented the destination from creating/updating the change.</li><li><b>'STARTED'</b>: The initial effect of a migration that depends on a previous one. This allows to have 'dependant' migrations defined by users.<br>An example of this: a workflow migrates code from a Gerrit review to a GitHub PR, and a feedback migration migrates the test results from a CI in GitHub back to the Gerrit change.<br>This effect would be created on the former one.</li></ul></p>

<a id="feedback.context.success" aria-hidden="true"></a>
### feedback.context.success

Returns a successful action result.

<code><a href="#dynamicaction_result">dynamic.action_result</a></code> <code>feedback.context.success()</code>



## feedback.finish_hook_context

Gives access to the feedback migration information and utilities. This context is a concrete implementation for 'after_migration' hooks.


<h4 id="fields.feedback.finish_hook_context">Fields:</h4>

Name | Description
---- | -----------
action_name | <code><a href="#string">string</a></code><br><p>The name of the current action.</p>
cli_labels | <code>dict[<a href="#string">string</a>, <a href="#string">string</a>]</code><br><p>Access labels that a user passes through flag '--labels'. For example: --labels=foo:value1,bar:value2. Then it can access in this way:cli_labels['foo'].</p>
console | <code>Console</code><br><p>Get an instance of the console to report errors or warnings</p>
destination | <code><a href="#endpoint">endpoint</a></code><br><p>An object representing the destination. Can be used to query or modify the destination state</p>
effects | <code>list of destination_effect</code><br><p>The list of effects that happened in the destination</p>
origin | <code><a href="#endpoint">endpoint</a></code><br><p>An object representing the origin. Can be used to query about the ref or modifying the origin state</p>
params | <code><a href="#dict">dict</a></code><br><p>Parameters for the function if created with core.action</p>
revision | <code><a href="#feedbackrevision_context">feedback.revision_context</a></code><br><p>Get the requested/resolved revision</p>

<a id="feedback.finish_hook_context.error" aria-hidden="true"></a>
### feedback.finish_hook_context.error

Returns an error action result.

<code><a href="#dynamicaction_result">dynamic.action_result</a></code> <code>feedback.finish_hook_context.error(<a href=#feedback.finish_hook_context.error.msg>msg</a>)</code>


<h4 id="parameters.feedback.finish_hook_context.error">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=feedback.finish_hook_context.error.msg href=#feedback.finish_hook_context.error.msg>msg</span> | <code><a href="#string">string</a></code><br><p>The error message</p>

<a id="feedback.finish_hook_context.noop" aria-hidden="true"></a>
### feedback.finish_hook_context.noop

Returns a no op action result with an optional message.

<code><a href="#dynamicaction_result">dynamic.action_result</a></code> <code>feedback.finish_hook_context.noop(<a href=#feedback.finish_hook_context.noop.msg>msg</a>=None)</code>


<h4 id="parameters.feedback.finish_hook_context.noop">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=feedback.finish_hook_context.noop.msg href=#feedback.finish_hook_context.noop.msg>msg</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>The no op message</p>

<a id="feedback.finish_hook_context.record_effect" aria-hidden="true"></a>
### feedback.finish_hook_context.record_effect

Records an effect of the current action.

<code>feedback.finish_hook_context.record_effect(<a href=#feedback.finish_hook_context.record_effect.summary>summary</a>, <a href=#feedback.finish_hook_context.record_effect.origin_refs>origin_refs</a>, <a href=#feedback.finish_hook_context.record_effect.destination_ref>destination_ref</a>, <a href=#feedback.finish_hook_context.record_effect.errors>errors</a>=[], <a href=#feedback.finish_hook_context.record_effect.type>type</a>="UPDATED")</code>


<h4 id="parameters.feedback.finish_hook_context.record_effect">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=feedback.finish_hook_context.record_effect.summary href=#feedback.finish_hook_context.record_effect.summary>summary</span> | <code><a href="#string">string</a></code><br><p>The summary of this effect</p>
<span id=feedback.finish_hook_context.record_effect.origin_refs href=#feedback.finish_hook_context.record_effect.origin_refs>origin_refs</span> | <code>sequence of <a href="#origin_ref">origin_ref</a></code><br><p>The origin refs</p>
<span id=feedback.finish_hook_context.record_effect.destination_ref href=#feedback.finish_hook_context.record_effect.destination_ref>destination_ref</span> | <code><a href="#destination_ref">destination_ref</a></code><br><p>The destination ref</p>
<span id=feedback.finish_hook_context.record_effect.errors href=#feedback.finish_hook_context.record_effect.errors>errors</span> | <code>sequence of <a href="#string">string</a></code><br><p>An optional list of errors</p>
<span id=feedback.finish_hook_context.record_effect.type href=#feedback.finish_hook_context.record_effect.type>type</span> | <code><a href="#string">string</a></code><br><p>The type of migration effect:<br><ul><li><b>'CREATED'</b>: A new review or change was created.</li><li><b>'UPDATED'</b>: An existing review or change was updated.</li><li><b>'NOOP'</b>: The change was a noop.</li><li><b>'NOOP_AGAINST_PENDING_CHANGE'</b>: The change was a noop, relativeto an existing pending change.</li><li><b>'INSUFFICIENT_APPROVALS'</b>: The effect couldn't happen because the change doesn't have enough approvals.</li><li><b>'ERROR'</b>: A user attributable error happened that prevented the destination from creating/updating the change.</li><li><b>'STARTED'</b>: The initial effect of a migration that depends on a previous one. This allows to have 'dependant' migrations defined by users.<br>An example of this: a workflow migrates code from a Gerrit review to a GitHub PR, and a feedback migration migrates the test results from a CI in GitHub back to the Gerrit change.<br>This effect would be created on the former one.</li></ul></p>

<a id="feedback.finish_hook_context.success" aria-hidden="true"></a>
### feedback.finish_hook_context.success

Returns a successful action result.

<code><a href="#dynamicaction_result">dynamic.action_result</a></code> <code>feedback.finish_hook_context.success()</code>



## feedback.revision_context

Information about the revision request/resolved for the migration


<h4 id="fields.feedback.revision_context">Fields:</h4>

Name | Description
---- | -----------
labels | <code>dict[<a href="#string">string</a>, list of string]</code><br><p>A dictionary with the labels detected for the requested/resolved revision.</p>

<a id="feedback.revision_context.fill_template" aria-hidden="true"></a>
### feedback.revision_context.fill_template

Replaces variables in templates with the values from this revision.

<code><a href="#string">string</a></code> <code>feedback.revision_context.fill_template(<a href=#feedback.revision_context.fill_template.template>template</a>)</code>


<h4 id="parameters.feedback.revision_context.fill_template">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=feedback.revision_context.fill_template.template href=#feedback.revision_context.fill_template.template>template</span> | <code><a href="#string">string</a></code><br><p>The template to use</p>


<h4 id="example.feedback.revision_context.fill_template">Example:</h4>


##### Use the SHA1 in a string:

Create a custom transformation which is successful.

```python
filled_template = revision.fill_template('Current Revision: ${GIT_SHORT_SHA1}')
```

filled_template will contain (for example) 'Current Revision: abcdef12'




## filter_replace

A core.filter_replace transformation


<h4 id="returned_by.filter_replace">Returned By:</h4>

<ul><li><a href="#core.filter_replace">core.filter_replace</a></li></ul>



## float

The type of floating-point numbers in Starlark.


<h4 id="returned_by.float">Returned By:</h4>

<ul><li><a href="#float">float</a></li></ul>
<h4 id="consumed_by.float">Consumed By:</h4>

<ul><li><a href="#abs">abs</a></li><li><a href="#float">float</a></li><li><a href="#int">int</a></li></ul>



## folder

Module for dealing with local filesystem folders

<a id="folder.destination" aria-hidden="true"></a>
### folder.destination

A folder destination is a destination that puts the output in a folder. It can be used both for testing or real production migrations.Given that folder destination does not support a lot of the features of real VCS, there are some limitations on how to use it:<ul><li>It requires passing a ref as an argument, as there is no way of calculating previous migrated changes. Alternatively, --last-rev can be used, which could migrate N changes.</li><li>Most likely, the workflow should use 'SQUASH' mode, as history is not supported.</li><li>If 'ITERATIVE' mode is used, a new temp directory will be created for each change migrated.</li></ul>

<code><a href="#destination">destination</a></code> <code>folder.destination()</code>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--folder-dir`</span> | *string* | Local directory to write the output of the migration to. If the directory exists, all files will be deleted. By default Copybara will generate a temporary directory, so you shouldn't need this.

<a id="folder.origin" aria-hidden="true"></a>
### folder.origin

A folder origin is a origin that uses a folder as input. The folder is specified via the source_ref argument.

<code><a href="#origin">origin</a></code> <code>folder.origin(<a href=#folder.origin.materialize_outside_symlinks>materialize_outside_symlinks</a>=False)</code>


<h4 id="parameters.folder.origin">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=folder.origin.materialize_outside_symlinks href=#folder.origin.materialize_outside_symlinks>materialize_outside_symlinks</span> | <code><a href="#bool">bool</a></code><br><p>By default folder.origin will refuse any symlink in the migration folder that is an absolute symlink or that refers to a file outside of the folder. If this flag is set, it will materialize those symlinks as regular files in the checkout directory.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--folder-origin-author`</span> | *string* | Deprecated. Please use '--force-author'. Author of the change being migrated from folder.origin()
<span style="white-space: nowrap;">`--folder-origin-ignore-invalid-symlinks`</span> | *boolean* | If an invalid symlink is found, ignore it instead of failing
<span style="white-space: nowrap;">`--folder-origin-message`</span> | *string* | Deprecated. Please use '--force-message'. Message of the change being migrated from folder.origin()
<span style="white-space: nowrap;">`--folder-origin-version`</span> | *string* | The version string associated with the change migrated from folder.origin(). If not specified, the default will be the folder path.



## format

Module for formatting the code to Google's style/guidelines

<a id="format.buildifier" aria-hidden="true"></a>
### format.buildifier

Formats the BUILD files using buildifier.

<code><a href="#transformation">transformation</a></code> <code>format.buildifier(<a href=#format.buildifier.paths>paths</a>=glob(["**.bzl", "**/BUILD", "BUILD"]), <a href=#format.buildifier.type>type</a>='auto', <a href=#format.buildifier.lint>lint</a>="OFF", <a href=#format.buildifier.lint_warnings>lint_warnings</a>=[])</code>


<h4 id="parameters.format.buildifier">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=format.buildifier.paths href=#format.buildifier.paths>paths</span> | <code><a href="#glob">glob</a></code> or <code>list of string</code> or <code>NoneType</code><br><p>Paths of the files to format relative to the workdir.</p>
<span id=format.buildifier.type href=#format.buildifier.type>type</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>The type of the files. Can be 'auto', 'bzl', 'build' or 'workspace'. Note that this is not recommended to be set and might break in the future. The default is 'auto'. This mode formats as BUILD files "BUILD", "BUILD.bazel", "WORKSPACE" and "WORKSPACE.bazel" files. The rest as bzl files. Prefer to use those names for BUILD files instead of setting this flag.</p>
<span id=format.buildifier.lint href=#format.buildifier.lint>lint</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>If buildifier --lint should be used. This fixes several common issues. Note that this transformation is difficult to revert. For example if it removes a load statement because is not used after removing a rule, then the reverse workflow needs to add back the load statement (core.replace or similar).  Possible values: `OFF`, `FIX`. Default is `OFF`</p>
<span id=format.buildifier.lint_warnings href=#format.buildifier.lint_warnings>lint_warnings</span> | <code>sequence of <a href="#string">string</a></code><br><p>Warnings used in the lint mode. Default is buildifier default</p>


<h4 id="example.format.buildifier">Examples:</h4>


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



## function

The type of functions declared in Starlark.



## gerrit_api_obj

Gerrit API endpoint implementation for feedback migrations and after migration hooks.


<h4 id="fields.gerrit_api_obj">Fields:</h4>

Name | Description
---- | -----------
url | <code><a href="#string">string</a></code><br><p>Return the URL of this endpoint.</p>

<a id="gerrit_api_obj.abandon_change" aria-hidden="true"></a>
### gerrit_api_obj.abandon_change

Abandon a Gerrit change.

<code><a href="#gerritapichangeinfo">gerritapi.ChangeInfo</a></code> <code>gerrit_api_obj.abandon_change(<a href=#gerrit_api_obj.abandon_change.change_id>change_id</a>)</code>


<h4 id="parameters.gerrit_api_obj.abandon_change">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=gerrit_api_obj.abandon_change.change_id href=#gerrit_api_obj.abandon_change.change_id>change_id</span> | <code><a href="#string">string</a></code><br><p>The Gerrit change id.</p>

<a id="gerrit_api_obj.delete_vote" aria-hidden="true"></a>
### gerrit_api_obj.delete_vote

Delete a label vote from an account owner on a Gerrit change.


<code>gerrit_api_obj.delete_vote(<a href=#gerrit_api_obj.delete_vote.change_id>change_id</a>, <a href=#gerrit_api_obj.delete_vote.account_id>account_id</a>, <a href=#gerrit_api_obj.delete_vote.label_id>label_id</a>)</code>


<h4 id="parameters.gerrit_api_obj.delete_vote">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=gerrit_api_obj.delete_vote.change_id href=#gerrit_api_obj.delete_vote.change_id>change_id</span> | <code><a href="#string">string</a></code><br><p>The Gerrit change id.</p>
<span id=gerrit_api_obj.delete_vote.account_id href=#gerrit_api_obj.delete_vote.account_id>account_id</span> | <code><a href="#string">string</a></code><br><p>The account owner who votes on label_id. Use 'me' or 'self' if the account owner makes this api call</p>
<span id=gerrit_api_obj.delete_vote.label_id href=#gerrit_api_obj.delete_vote.label_id>label_id</span> | <code><a href="#string">string</a></code><br><p>The name of the label.</p>

<a id="gerrit_api_obj.get_actions" aria-hidden="true"></a>
### gerrit_api_obj.get_actions

Retrieve the actions of a Gerrit change.

<code>dict[<a href="#string">string</a>, <a href="#gerritapigetactioninfo">gerritapi.getActionInfo</a>]</code> <code>gerrit_api_obj.get_actions(<a href=#gerrit_api_obj.get_actions.id>id</a>, <a href=#gerrit_api_obj.get_actions.revision>revision</a>)</code>


<h4 id="parameters.gerrit_api_obj.get_actions">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=gerrit_api_obj.get_actions.id href=#gerrit_api_obj.get_actions.id>id</span> | <code><a href="#string">string</a></code><br><p>The change id or change number.</p>
<span id=gerrit_api_obj.get_actions.revision href=#gerrit_api_obj.get_actions.revision>revision</span> | <code><a href="#string">string</a></code><br><p>The revision of the change.</p>

<a id="gerrit_api_obj.get_change" aria-hidden="true"></a>
### gerrit_api_obj.get_change

Retrieve a Gerrit change.

<code><a href="#gerritapichangeinfo">gerritapi.ChangeInfo</a></code> <code>gerrit_api_obj.get_change(<a href=#gerrit_api_obj.get_change.id>id</a>, <a href=#gerrit_api_obj.get_change.include_results>include_results</a>=['LABELS'])</code>


<h4 id="parameters.gerrit_api_obj.get_change">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=gerrit_api_obj.get_change.id href=#gerrit_api_obj.get_change.id>id</span> | <code><a href="#string">string</a></code><br><p>The change id or change number.</p>
<span id=gerrit_api_obj.get_change.include_results href=#gerrit_api_obj.get_change.include_results>include_results</span> | <code>sequence of <a href="#string">string</a></code><br><p>What to include in the response. See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#query-options</p>

<a id="gerrit_api_obj.list_changes" aria-hidden="true"></a>
### gerrit_api_obj.list_changes

Get changes from Gerrit based on a query. See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#list-changes.


<code>list of gerritapi.ChangeInfo</code> <code>gerrit_api_obj.list_changes(<a href=#gerrit_api_obj.list_changes.query>query</a>, <a href=#gerrit_api_obj.list_changes.include_results>include_results</a>=[])</code>


<h4 id="parameters.gerrit_api_obj.list_changes">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=gerrit_api_obj.list_changes.query href=#gerrit_api_obj.list_changes.query>query</span> | <code><a href="#string">string</a></code><br><p>The query string to list changes by. See https://gerrit-review.googlesource.com/Documentation/user-search.html#_basic_change_search.</p>
<span id=gerrit_api_obj.list_changes.include_results href=#gerrit_api_obj.list_changes.include_results>include_results</span> | <code>sequence of <a href="#string">string</a></code><br><p>What to include in the response. See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#query-options</p>

<a id="gerrit_api_obj.new_destination_ref" aria-hidden="true"></a>
### gerrit_api_obj.new_destination_ref

Creates a new destination reference out of this endpoint.

<code><a href="#destination_ref">destination_ref</a></code> <code>gerrit_api_obj.new_destination_ref(<a href=#gerrit_api_obj.new_destination_ref.ref>ref</a>, <a href=#gerrit_api_obj.new_destination_ref.type>type</a>, <a href=#gerrit_api_obj.new_destination_ref.url>url</a>=None)</code>


<h4 id="parameters.gerrit_api_obj.new_destination_ref">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=gerrit_api_obj.new_destination_ref.ref href=#gerrit_api_obj.new_destination_ref.ref>ref</span> | <code><a href="#string">string</a></code><br><p>The reference.</p>
<span id=gerrit_api_obj.new_destination_ref.type href=#gerrit_api_obj.new_destination_ref.type>type</span> | <code><a href="#string">string</a></code><br><p>The type of this reference.</p>
<span id=gerrit_api_obj.new_destination_ref.url href=#gerrit_api_obj.new_destination_ref.url>url</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>The url associated with this reference, if any.</p>

<a id="gerrit_api_obj.new_origin_ref" aria-hidden="true"></a>
### gerrit_api_obj.new_origin_ref

Creates a new origin reference out of this endpoint.

<code><a href="#origin_ref">origin_ref</a></code> <code>gerrit_api_obj.new_origin_ref(<a href=#gerrit_api_obj.new_origin_ref.ref>ref</a>)</code>


<h4 id="parameters.gerrit_api_obj.new_origin_ref">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=gerrit_api_obj.new_origin_ref.ref href=#gerrit_api_obj.new_origin_ref.ref>ref</span> | <code><a href="#string">string</a></code><br><p>The reference.</p>

<a id="gerrit_api_obj.post_review" aria-hidden="true"></a>
### gerrit_api_obj.post_review

Post a review to a Gerrit change for a particular revision. The review will be authored by the user running the tool, or the role account if running in the service.


<code><a href="#gerritapireviewresult">gerritapi.ReviewResult</a></code> <code>gerrit_api_obj.post_review(<a href=#gerrit_api_obj.post_review.change_id>change_id</a>, <a href=#gerrit_api_obj.post_review.revision_id>revision_id</a>, <a href=#gerrit_api_obj.post_review.review_input>review_input</a>)</code>


<h4 id="parameters.gerrit_api_obj.post_review">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=gerrit_api_obj.post_review.change_id href=#gerrit_api_obj.post_review.change_id>change_id</span> | <code><a href="#string">string</a></code><br><p>The Gerrit change id.</p>
<span id=gerrit_api_obj.post_review.revision_id href=#gerrit_api_obj.post_review.revision_id>revision_id</span> | <code><a href="#string">string</a></code><br><p>The revision for which the comment will be posted.</p>
<span id=gerrit_api_obj.post_review.review_input href=#gerrit_api_obj.post_review.review_input>review_input</span> | <code><a href="#setreviewinput">SetReviewInput</a></code><br><p>The review to post to Gerrit.</p>

<a id="gerrit_api_obj.submit_change" aria-hidden="true"></a>
### gerrit_api_obj.submit_change

Submit a Gerrit change

<code><a href="#gerritapichangeinfo">gerritapi.ChangeInfo</a></code> <code>gerrit_api_obj.submit_change(<a href=#gerrit_api_obj.submit_change.change_id>change_id</a>)</code>


<h4 id="parameters.gerrit_api_obj.submit_change">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=gerrit_api_obj.submit_change.change_id href=#gerrit_api_obj.submit_change.change_id>change_id</span> | <code><a href="#string">string</a></code><br><p>The Gerrit change id.</p>



## gerritapi.AccountInfo

Gerrit account information.


<h4 id="fields.gerritapi.AccountInfo">Fields:</h4>

Name | Description
---- | -----------
account_id | <code><a href="#string">string</a></code><br><p>The numeric ID of the account.</p>
email | <code><a href="#string">string</a></code><br><p>The email address the user prefers to be contacted through.<br>Only set if detailed account information is requested.<br>See option DETAILED_ACCOUNTS for change queries<br>and options DETAILS and ALL_EMAILS for account queries.</p>
name | <code><a href="#string">string</a></code><br><p>The full name of the user.<br>Only set if detailed account information is requested.<br>See option DETAILED_ACCOUNTS for change queries<br>and option DETAILS for account queries.</p>
secondary_emails | <code>list of string</code><br><p>A list of the secondary email addresses of the user.<br>Only set for account queries when the ALL_EMAILS option or the suggest parameter is set.<br>Secondary emails are only included if the calling user has the Modify Account, and hence is allowed to see secondary emails of other users.</p>
username | <code><a href="#string">string</a></code><br><p>The username of the user.<br>Only set if detailed account information is requested.<br>See option DETAILED_ACCOUNTS for change queries<br>and option DETAILS for account queries.</p>



## gerritapi.ApprovalInfo

Gerrit approval information.


<h4 id="fields.gerritapi.ApprovalInfo">Fields:</h4>

Name | Description
---- | -----------
account_id | <code><a href="#string">string</a></code><br><p>The numeric ID of the account.</p>
date | <code><a href="#string">string</a></code><br><p>The time and date describing when the approval was made.</p>
email | <code><a href="#string">string</a></code><br><p>The email address the user prefers to be contacted through.<br>Only set if detailed account information is requested.<br>See option DETAILED_ACCOUNTS for change queries<br>and options DETAILS and ALL_EMAILS for account queries.</p>
name | <code><a href="#string">string</a></code><br><p>The full name of the user.<br>Only set if detailed account information is requested.<br>See option DETAILED_ACCOUNTS for change queries<br>and option DETAILS for account queries.</p>
secondary_emails | <code>list of string</code><br><p>A list of the secondary email addresses of the user.<br>Only set for account queries when the ALL_EMAILS option or the suggest parameter is set.<br>Secondary emails are only included if the calling user has the Modify Account, and hence is allowed to see secondary emails of other users.</p>
username | <code><a href="#string">string</a></code><br><p>The username of the user.<br>Only set if detailed account information is requested.<br>See option DETAILED_ACCOUNTS for change queries<br>and option DETAILS for account queries.</p>
value | <code><a href="#int">int</a></code><br><p>The vote that the user has given for the label. If present and zero, the user is permitted to vote on the label. If absent, the user is not permitted to vote on that label.</p>



## gerritapi.ChangeInfo

Gerrit change information.


<h4 id="fields.gerritapi.ChangeInfo">Fields:</h4>

Name | Description
---- | -----------
branch | <code><a href="#string">string</a></code><br><p>The name of the target branch.<br>The refs/heads/ prefix is omitted.</p>
change_id | <code><a href="#string">string</a></code><br><p>The Change-Id of the change.</p>
created | <code><a href="#string">string</a></code><br><p>The timestamp of when the change was created.</p>
current_revision | <code><a href="#string">string</a></code><br><p>The commit ID of the current patch set of this change.<br>Only set if the current revision is requested or if all revisions are requested.</p>
id | <code><a href="#string">string</a></code><br><p>The ID of the change in the format "`<project>~<branch>~<Change-Id>`", where 'project', 'branch' and 'Change-Id' are URL encoded. For 'branch' the refs/heads/ prefix is omitted.</p>
labels | <code>dict[<a href="#string">string</a>, <a href="#gerritapilabelinfo">gerritapi.LabelInfo</a>]</code><br><p>The labels of the change as a map that maps the label names to LabelInfo entries.<br>Only set if labels or detailed labels are requested.</p>
messages | <code>list of gerritapi.ChangeMessageInfo</code><br><p>Messages associated with the change as a list of ChangeMessageInfo entities.<br>Only set if messages are requested.</p>
number | <code><a href="#string">string</a></code><br><p>The legacy numeric ID of the change.</p>
owner | <code><a href="#gerritapiaccountinfo">gerritapi.AccountInfo</a></code><br><p>The owner of the change as an AccountInfo entity.</p>
project | <code><a href="#string">string</a></code><br><p>The name of the project.</p>
revisions | <code>dict[<a href="#string">string</a>, <a href="#gerritapirevisioninfo">gerritapi.RevisionInfo</a>]</code><br><p>All patch sets of this change as a map that maps the commit ID of the patch set to a RevisionInfo entity.<br>Only set if the current revision is requested (in which case it will only contain a key for the current revision) or if all revisions are requested.</p>
status | <code><a href="#string">string</a></code><br><p>The status of the change (NEW, MERGED, ABANDONED).</p>
subject | <code><a href="#string">string</a></code><br><p>The subject of the change (header line of the commit message).</p>
submit_requirements | <code>list of SubmitRequirementResultInfo</code><br><p>A list of the evaluated submit requirements for the change.</p>
submittable | <code><a href="#bool">bool</a></code><br><p>Whether the change has been approved by the project submit rules. Only set if requested via additional field SUBMITTABLE.</p>
submitted | <code><a href="#string">string</a></code><br><p>The timestamp of when the change was submitted.</p>
topic | <code><a href="#string">string</a></code><br><p>The topic to which this change belongs.</p>
triplet_id | <code><a href="#string">string</a></code><br><p>The ID of the change in the format "'<project>~<branch>~<Change-Id>'", where 'project' and 'branch' are URL encoded. For 'branch' the refs/heads/ prefix is omitted.</p>
updated | <code><a href="#string">string</a></code><br><p>The timestamp of when the change was last updated.</p>
work_in_progress | <code><a href="#bool">bool</a></code><br><p>Whether the change is marked as "Work in progress".</p>


<h4 id="returned_by.gerritapi.ChangeInfo">Returned By:</h4>

<ul><li><a href="#gerrit_api_obj.abandon_change">gerrit_api_obj.abandon_change</a></li><li><a href="#gerrit_api_obj.get_change">gerrit_api_obj.get_change</a></li><li><a href="#gerrit_api_obj.submit_change">gerrit_api_obj.submit_change</a></li></ul>



## gerritapi.ChangeMessageInfo

Gerrit change message information.


<h4 id="fields.gerritapi.ChangeMessageInfo">Fields:</h4>

Name | Description
---- | -----------
author | <code><a href="#gerritapiaccountinfo">gerritapi.AccountInfo</a></code><br><p>Author of the message as an AccountInfo entity.<br>Unset if written by the Gerrit system.</p>
date | <code><a href="#string">string</a></code><br><p>The timestamp of when this identity was constructed.</p>
id | <code><a href="#string">string</a></code><br><p>The ID of the message.</p>
message | <code><a href="#string">string</a></code><br><p>The text left by the user.</p>
real_author | <code><a href="#gerritapiaccountinfo">gerritapi.AccountInfo</a></code><br><p>Real author of the message as an AccountInfo entity.<br>Set if the message was posted on behalf of another user.</p>
revision_number | <code><a href="#int">int</a></code><br><p>Which patchset (if any) generated this message.</p>
tag | <code><a href="#string">string</a></code><br><p>Value of the tag field from ReviewInput set while posting the review. NOTE: To apply different tags on on different votes/comments multiple invocations of the REST call are required.</p>



## gerritapi.ChangesQuery

Input for listing Gerrit changes. See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#list-changes



## gerritapi.CommitInfo

Gerrit commit information.


<h4 id="fields.gerritapi.CommitInfo">Fields:</h4>

Name | Description
---- | -----------
author | <code><a href="#gerritapigitpersoninfo">gerritapi.GitPersonInfo</a></code><br><p>The author of the commit as a GitPersonInfo entity.</p>
commit | <code><a href="#string">string</a></code><br><p>The commit ID. Not set if included in a RevisionInfo entity that is contained in a map which has the commit ID as key.</p>
committer | <code><a href="#gerritapigitpersoninfo">gerritapi.GitPersonInfo</a></code><br><p>The committer of the commit as a GitPersonInfo entity.</p>
message | <code><a href="#string">string</a></code><br><p>The commit message.</p>
parents | <code>list of gerritapi.ParentCommitInfo</code><br><p>The parent commits of this commit as a list of CommitInfo entities. In each parent only the commit and subject fields are populated.</p>
subject | <code><a href="#string">string</a></code><br><p>The subject of the commit (header line of the commit message).</p>



## gerritapi.getActionInfo

Gerrit actions information.


<h4 id="fields.gerritapi.getActionInfo">Fields:</h4>

Name | Description
---- | -----------
enabled | <code><a href="#bool">bool</a></code><br><p>If true the action is permitted at this time and the caller is likely allowed to execute it.</p>
label | <code><a href="#string">string</a></code><br><p>Short title to display to a user describing the action</p>


<h4 id="returned_by.gerritapi.getActionInfo">Returned By:</h4>

<ul><li><a href="#gerrit_api_obj.get_actions">gerrit_api_obj.get_actions</a></li></ul>



## gerritapi.GitPersonInfo

Git person information.


<h4 id="fields.gerritapi.GitPersonInfo">Fields:</h4>

Name | Description
---- | -----------
date | <code><a href="#string">string</a></code><br><p>The timestamp of when this identity was constructed.</p>
email | <code><a href="#string">string</a></code><br><p>The email address of the author/committer.</p>
name | <code><a href="#string">string</a></code><br><p>The name of the author/committer.</p>



## gerritapi.LabelInfo

Gerrit label information.


<h4 id="fields.gerritapi.LabelInfo">Fields:</h4>

Name | Description
---- | -----------
all | <code>list of gerritapi.ApprovalInfo</code><br><p>List of all approvals for this label as a list of ApprovalInfo entities. Items in this list may not represent actual votes cast by users; if a user votes on any label, a corresponding ApprovalInfo will appear in this list for all labels.</p>
approved | <code><a href="#gerritapiaccountinfo">gerritapi.AccountInfo</a></code><br><p>One user who approved this label on the change (voted the maximum value) as an AccountInfo entity.</p>
blocking | <code><a href="#bool">bool</a></code><br><p>If true, the label blocks submit operation. If not set, the default is false.</p>
default_value | <code><a href="#int">int</a></code><br><p>The default voting value for the label. This value may be outside the range specified in permitted_labels.</p>
disliked | <code><a href="#gerritapiaccountinfo">gerritapi.AccountInfo</a></code><br><p>One user who disliked this label on the change (voted negatively, but not the minimum value) as an AccountInfo entity.</p>
recommended | <code><a href="#gerritapiaccountinfo">gerritapi.AccountInfo</a></code><br><p>One user who recommended this label on the change (voted positively, but not the maximum value) as an AccountInfo entity.</p>
rejected | <code><a href="#gerritapiaccountinfo">gerritapi.AccountInfo</a></code><br><p>One user who rejected this label on the change (voted the minimum value) as an AccountInfo entity.</p>
value | <code><a href="#int">int</a></code><br><p>The voting value of the user who recommended/disliked this label on the change if it is not `"+1"`/`"-1"`.</p>
values | <code>dict[<a href="#string">string</a>, <a href="#string">string</a>]</code><br><p>A map of all values that are allowed for this label. The map maps the values (`"-2"`, `"-1"`, `"0"`, `"+1"`, `"+2"`) to the value descriptions.</p>



## gerritapi.ParentCommitInfo

Gerrit parent commit information.


<h4 id="fields.gerritapi.ParentCommitInfo">Fields:</h4>

Name | Description
---- | -----------
commit | <code><a href="#string">string</a></code><br><p>The commit ID. Not set if included in a RevisionInfo entity that is contained in a map which has the commit ID as key.</p>
subject | <code><a href="#string">string</a></code><br><p>The subject of the commit (header line of the commit message).</p>



## gerritapi.ReviewResult

Gerrit review result.


<h4 id="fields.gerritapi.ReviewResult">Fields:</h4>

Name | Description
---- | -----------
labels | <code>dict[<a href="#string">string</a>, <a href="#int">int</a>]</code><br><p>Map of labels to values after the review was posted.</p>
ready | <code><a href="#bool">bool</a></code><br><p>If true, the change was moved from WIP to ready for review as a result of this action. Not set if false.</p>


<h4 id="returned_by.gerritapi.ReviewResult">Returned By:</h4>

<ul><li><a href="#gerrit_api_obj.post_review">gerrit_api_obj.post_review</a></li></ul>



## gerritapi.RevisionInfo

Gerrit revision information.


<h4 id="fields.gerritapi.RevisionInfo">Fields:</h4>

Name | Description
---- | -----------
commit | <code><a href="#gerritapicommitinfo">gerritapi.CommitInfo</a></code><br><p>The commit of the patch set as CommitInfo entity.</p>
created | <code><a href="#string">string</a></code><br><p>The timestamp of when the patch set was created.</p>
kind | <code><a href="#string">string</a></code><br><p>The change kind. Valid values are REWORK, TRIVIAL_REBASE, MERGE_FIRST_PARENT_UPDATE, NO_CODE_CHANGE, and NO_CHANGE.</p>
patchset_number | <code><a href="#int">int</a></code><br><p>The patch set number, or edit if the patch set is an edit.</p>
ref | <code><a href="#string">string</a></code><br><p>The Git reference for the patch set.</p>
uploader | <code><a href="#gerritapiaccountinfo">gerritapi.AccountInfo</a></code><br><p>The uploader of the patch set as an AccountInfo entity.</p>



## gerritapi.SubmitRequirementExpressionInfo

Result of evaluating submit requirement expression


<h4 id="fields.gerritapi.SubmitRequirementExpressionInfo">Fields:</h4>

Name | Description
---- | -----------
expression | <code><a href="#string">string</a></code><br><p>The submit requirement expression as a string.</p>
fulfilled | <code><a href="#bool">bool</a></code><br><p>If true, this submit requirement result was created from a legacy SubmitRecord. Otherwise, it was created by evaluating a submit requirement.</p>
status | <code><a href="#string">string</a></code><br><p>The status of the submit requirement evaluation.</p>



## git

Set of functions to define Git origins and destinations.



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--allowed-git-push-options`</span> | *list* | This is a flag used to allowlist push options sent to git servers. E.g. copybara copy.bara.sky --git-push-option="foo,bar" would make copybara validate push so that the only push options (if there are any) used are 'foo' and 'bar'. If this flag is unset, it will skip push options validation. Set to "" to allow no push options.
<span style="white-space: nowrap;">`--experiment-checkout-affected-files`</span> | *boolean* | If set, copybara will only checkout affected files at git origin. Note that this is experimental.
<span style="white-space: nowrap;">`--git-credential-helper-store-file`</span> | *string* | Credentials store file to be used. See https://git-scm.com/docs/git-credential-store
<span style="white-space: nowrap;">`--git-http-follow-redirects`</span> | *string* | Whether git should follow HTTP redirects. For a list of valid options, please see https://git-scm.com/docs/git-config#Documentation/git-config.txt-httpfollowRedirects
<span style="white-space: nowrap;">`--git-ls-remote-limit`</span> | *integer* | Limit the number of ls-remote rows is visible to Copybara.
<span style="white-space: nowrap;">`--git-no-verify`</span> | *boolean* | Pass the '--no-verify' option to git pushes and commits to disable git commit hooks.
<span style="white-space: nowrap;">`--git-origin-fetch-depth`</span> | *integer* | Use a shallow clone of the specified depth for git.origin. If set, only the n most recent changes' tree states are imported with older changes omitted.
<span style="white-space: nowrap;">`--git-push-option`</span> | *list* | This is a repeatable flag used to set git push level flags to send to git servers. E.g. copybara copy.bara.sky --git-push-option foo --git-push-option bar would make git operations done by copybara under the hood use the --push-option flags: git push -push-option=foo -push-option=bar ...
<span style="white-space: nowrap;">`--git-tag-overwrite`</span> | *boolean* | If set, copybara will force update existing git tag
<span style="white-space: nowrap;">`--nogit-credential-helper-store`</span> | *boolean* | Disable using credentials store. See https://git-scm.com/docs/git-credential-store
<span style="white-space: nowrap;">`--nogit-prompt`</span> | *boolean* | Disable username/password prompt and fail if no credentials are found. This flag sets the environment variable GIT_TERMINAL_PROMPT which is intended for automated jobs running Git https://git-scm.com/docs/git/2.3.0#git-emGITTERMINALPROMPTem

<a id="git.destination" aria-hidden="true"></a>
### git.destination

Creates a commit in a git repository using the transformed worktree.<br><br>For GitHub use git.github_destination. For creating Pull Requests in GitHub, use git.github_pr_destination. For creating a Gerrit change use git.gerrit_destination.<br><br>Given that Copybara doesn't ask for user/password in the console when doing the push to remote repos, you have to use ssh protocol, have the credentials cached or use a credential manager.

<code><a href="#destination">destination</a></code> <code>git.destination(<a href=#git.destination.url>url</a>, <a href=#git.destination.push>push</a>='master', <a href=#git.destination.tag_name>tag_name</a>=None, <a href=#git.destination.tag_msg>tag_msg</a>=None, <a href=#git.destination.fetch>fetch</a>=None, <a href=#git.destination.partial_fetch>partial_fetch</a>=False, <a href=#git.destination.integrates>integrates</a>=None, <a href=#git.destination.primary_branch_migration>primary_branch_migration</a>=False, <a href=#git.destination.checker>checker</a>=None, <a href=#git.destination.credentials>credentials</a>=None)</code>


<h4 id="parameters.git.destination">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.destination.url href=#git.destination.url>url</span> | <code><a href="#string">string</a></code><br><p>Indicates the URL to push to as well as the URL from which to get the parent commit</p>
<span id=git.destination.push href=#git.destination.push>push</span> | <code><a href="#string">string</a></code><br><p>Reference to use for pushing the change, for example 'main'.</p>
<span id=git.destination.tag_name href=#git.destination.tag_name>tag_name</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>A template string that refers to a tag name. If tag_name exists, overwrite this tag only if flag git-tag-overwrite is set. Note that tag creation is best-effort and migration will succeed even if the tag cannot be created. Usage: Users can use a string or a string with a label. For instance ${label}_tag_name. And the value of label must be in changes' label list. Otherwise, tag won't be created.</p>
<span id=git.destination.tag_msg href=#git.destination.tag_msg>tag_msg</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>A template string that refers to the commit msg of a tag. If set, we will create an annotated tag when tag_name is set. Usage: Users can use a string or a string with a label. For instance ${label}_message. And the value of label must be in changes' label list. Otherwise, tag will be created with sha1's commit msg.</p>
<span id=git.destination.fetch href=#git.destination.fetch>fetch</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Indicates the ref from which to get the parent commit. Defaults to push value if None</p>
<span id=git.destination.partial_fetch href=#git.destination.partial_fetch>partial_fetch</span> | <code><a href="#bool">bool</a></code><br><p>This is an experimental feature that only works for certain origin globs.</p>
<span id=git.destination.integrates href=#git.destination.integrates>integrates</span> | <code>sequence of git_integrate</code> or <code>NoneType</code><br><p>Integrate changes from a url present in the migrated change label. Defaults to a semi-fake merge if COPYBARA_INTEGRATE_REVIEW label is present in the message</p>
<span id=git.destination.primary_branch_migration href=#git.destination.primary_branch_migration>primary_branch_migration</span> | <code><a href="#bool">bool</a></code><br><p>When enabled, copybara will ignore the 'push' and 'fetch' params if either is 'master' or 'main' and instead try to establish the default git branch. If this fails, it will fall back to the param's declared value.<br>This is intended to help migrating to the new standard of using 'main' without breaking users relying on the legacy default.</p>
<span id=git.destination.checker href=#git.destination.checker>checker</span> | <code><a href="#checker">checker</a></code> or <code>NoneType</code><br><p>A checker that can check leaks or other checks in the commit created. </p>
<span id=git.destination.credentials href=#git.destination.credentials>credentials</span> | <code>UsernamePasswordIssuer</code> or <code>NoneType</code><br><p>EXPERIMENTAL: Read credentials from config file to access the Git Repo. This expects a 'credentials.username_password' specifying the username to use for the remote git host and a password or token. This is gated by the '--use-credentials-from-config' flag</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--git-committer-email`</span> | *string* | If set, overrides the committer e-mail for the generated commits in git destination.
<span style="white-space: nowrap;">`--git-committer-name`</span> | *string* | If set, overrides the committer name for the generated commits in git destination.
<span style="white-space: nowrap;">`--git-destination-fetch`</span> | *string* | If set, overrides the git destination fetch reference.
<span style="white-space: nowrap;">`--git-destination-fetch-depth`</span> | *integer* | Use a shallow clone of the specified depth for git.destination
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

<code>endpoint_provider</code> <code>git.gerrit_api(<a href=#git.gerrit_api.url>url</a>, <a href=#git.gerrit_api.checker>checker</a>=None, <a href=#git.gerrit_api.allow_submit>allow_submit</a>=False)</code>


<h4 id="parameters.git.gerrit_api">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.gerrit_api.url href=#git.gerrit_api.url>url</span> | <code><a href="#string">string</a></code><br><p>Indicates the Gerrit repo URL.</p>
<span id=git.gerrit_api.checker href=#git.gerrit_api.checker>checker</span> | <code><a href="#checker">checker</a></code> or <code>NoneType</code><br><p>A checker for the Gerrit API transport.</p>
<span id=git.gerrit_api.allow_submit href=#git.gerrit_api.allow_submit>allow_submit</span> | <code><a href="#bool">bool</a></code><br><p>Enable the submit_change method</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--force-gerrit-submit`</span> | *boolean* | Override the gerrit submit setting that is set in the config. This also flips the submit bit.
<span style="white-space: nowrap;">`--gerrit-change-id`</span> | *string* | ChangeId to use in the generated commit message. Use this flag if you want to reuse the same Gerrit review for an export.
<span style="white-space: nowrap;">`--gerrit-new-change`</span> | *boolean* | Create a new change instead of trying to reuse an existing one.
<span style="white-space: nowrap;">`--gerrit-topic`</span> | *string* | Gerrit topic to use

<a id="git.gerrit_destination" aria-hidden="true"></a>
### git.gerrit_destination

Creates a change in Gerrit using the transformed worktree. If this is used in iterative mode, then each commit pushed in a single Copybara invocation will have the correct commit parent. The reviews generated can then be easily done in the correct order without rebasing.

<code><a href="#destination">destination</a></code> <code>git.gerrit_destination(<a href=#git.gerrit_destination.url>url</a>, <a href=#git.gerrit_destination.fetch>fetch</a>, <a href=#git.gerrit_destination.push_to_refs_for>push_to_refs_for</a>=fetch value, <a href=#git.gerrit_destination.submit>submit</a>=False, <a href=#git.gerrit_destination.partial_fetch>partial_fetch</a>=False, <a href=#git.gerrit_destination.notify>notify</a>=None, <a href=#git.gerrit_destination.change_id_policy>change_id_policy</a>='FAIL_IF_PRESENT', <a href=#git.gerrit_destination.allow_empty_diff_patchset>allow_empty_diff_patchset</a>=True, <a href=#git.gerrit_destination.reviewers>reviewers</a>=[], <a href=#git.gerrit_destination.cc>cc</a>=[], <a href=#git.gerrit_destination.labels>labels</a>=[], <a href=#git.gerrit_destination.api_checker>api_checker</a>=None, <a href=#git.gerrit_destination.integrates>integrates</a>=None, <a href=#git.gerrit_destination.topic>topic</a>=None, <a href=#git.gerrit_destination.gerrit_submit>gerrit_submit</a>=False, <a href=#git.gerrit_destination.primary_branch_migration>primary_branch_migration</a>=False, <a href=#git.gerrit_destination.checker>checker</a>=None, <a href=#git.gerrit_destination.credentials>credentials</a>=None)</code>


<h4 id="parameters.git.gerrit_destination">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.gerrit_destination.url href=#git.gerrit_destination.url>url</span> | <code><a href="#string">string</a></code><br><p>Indicates the URL to push to as well as the URL from which to get the parent commit</p>
<span id=git.gerrit_destination.fetch href=#git.gerrit_destination.fetch>fetch</span> | <code><a href="#string">string</a></code><br><p>Indicates the ref from which to get the parent commit</p>
<span id=git.gerrit_destination.push_to_refs_for href=#git.gerrit_destination.push_to_refs_for>push_to_refs_for</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Review branch to push the change to, for example setting this to 'feature_x' causes the destination to push to 'refs/for/feature_x'. It defaults to 'fetch' value.</p>
<span id=git.gerrit_destination.submit href=#git.gerrit_destination.submit>submit</span> | <code><a href="#bool">bool</a></code><br><p>If true, skip the push thru Gerrit refs/for/branch and directly push to branch. This is effectively a git.destination that sets a Change-Id</p>
<span id=git.gerrit_destination.partial_fetch href=#git.gerrit_destination.partial_fetch>partial_fetch</span> | <code><a href="#bool">bool</a></code><br><p>This is an experimental feature that only works for certain origin globs.</p>
<span id=git.gerrit_destination.notify href=#git.gerrit_destination.notify>notify</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Type of Gerrit notify option (https://gerrit-review.googlesource.com/Documentation/user-upload.html#notify). Sends notifications by default.</p>
<span id=git.gerrit_destination.change_id_policy href=#git.gerrit_destination.change_id_policy>change_id_policy</span> | <code><a href="#string">string</a></code><br><p>What to do in the presence or absent of Change-Id in message:<ul> <li>`'REQUIRE'`: Require that the change_id is present in the message as a valid label</li> <li>`'FAIL_IF_PRESENT'`: Fail if found in message</li> <li>`'REUSE'`: Reuse if present. Otherwise generate a new one</li> <li>`'REPLACE'`: Replace with a new one if found</li></ul></p>
<span id=git.gerrit_destination.allow_empty_diff_patchset href=#git.gerrit_destination.allow_empty_diff_patchset>allow_empty_diff_patchset</span> | <code><a href="#bool">bool</a></code><br><p>By default Copybara will upload a new PatchSet to Gerrit without checking the previous one. If this set to false, Copybara will download current PatchSet and check the diff against the new diff.</p>
<span id=git.gerrit_destination.reviewers href=#git.gerrit_destination.reviewers>reviewers</span> | <code>sequence</code><br><p>The list of the reviewers to add. Each element in the list is: an email (e.g. `"foo@example.com"` or label (e.g.  `"${SOME_GERRIT_REVIEWER}`). These assume that users have already registered on the Gerrit host and has access to the repos.</p>
<span id=git.gerrit_destination.cc href=#git.gerrit_destination.cc>cc</span> | <code>sequence</code><br><p>The list of the email addresses or users that will be CCed in the review. Can use labels as the `reviewers` field.</p>
<span id=git.gerrit_destination.labels href=#git.gerrit_destination.labels>labels</span> | <code>sequence</code><br><p>The list of labels to be pushed with the change. The format is the label along with the associated value. For example: Run-Presubmit+1</p>
<span id=git.gerrit_destination.api_checker href=#git.gerrit_destination.api_checker>api_checker</span> | <code><a href="#checker">checker</a></code> or <code>NoneType</code><br><p>A checker for the Gerrit API endpoint provided for after_migration hooks. This field is not required if the workflow hooks don't use the origin/destination endpoints.</p>
<span id=git.gerrit_destination.integrates href=#git.gerrit_destination.integrates>integrates</span> | <code>sequence of git_integrate</code> or <code>NoneType</code><br><p>Integrate changes from a url present in the migrated change label. Defaults to a semi-fake merge if COPYBARA_INTEGRATE_REVIEW label is present in the message</p>
<span id=git.gerrit_destination.topic href=#git.gerrit_destination.topic>topic</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Sets the topic of the Gerrit change created.<br><br>By default it sets no topic. This field accepts a template with labels. For example: `"topic_${CONTEXT_REFERENCE}"`</p>
<span id=git.gerrit_destination.gerrit_submit href=#git.gerrit_destination.gerrit_submit>gerrit_submit</span> | <code><a href="#bool">bool</a></code><br><p>By default, Copybara uses git commit/push to the main branch when submit = True.  If this flag is enabled, it will update the Gerrit change with the latest commit and submit using Gerrit.</p>
<span id=git.gerrit_destination.primary_branch_migration href=#git.gerrit_destination.primary_branch_migration>primary_branch_migration</span> | <code><a href="#bool">bool</a></code><br><p>When enabled, copybara will ignore the 'push_to_refs_for' and 'fetch' params if either is 'master' or 'main' and instead try to establish the default git branch. If this fails, it will fall back to the param's declared value.<br>This is intended to help migrating to the new standard of using 'main' without breaking users relying on the legacy default.</p>
<span id=git.gerrit_destination.checker href=#git.gerrit_destination.checker>checker</span> | <code><a href="#checker">checker</a></code> or <code>NoneType</code><br><p>A checker that validates the commit files & message. If `api_checker` is not set, it will also be used for checking API calls. If only `api_checker`is used, that checker will only apply to API calls.</p>
<span id=git.gerrit_destination.credentials href=#git.gerrit_destination.credentials>credentials</span> | <code>UsernamePasswordIssuer</code> or <code>NoneType</code><br><p>EXPERIMENTAL: Read credentials from config file to access the Git Repo. This expects a 'credentials.username_password' specifying the username to use for the remote git host and a password or token. This is gated by the '--use-credentials-from-config' flag</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--force-gerrit-submit`</span> | *boolean* | Override the gerrit submit setting that is set in the config. This also flips the submit bit.
<span style="white-space: nowrap;">`--gerrit-change-id`</span> | *string* | ChangeId to use in the generated commit message. Use this flag if you want to reuse the same Gerrit review for an export.
<span style="white-space: nowrap;">`--gerrit-new-change`</span> | *boolean* | Create a new change instead of trying to reuse an existing one.
<span style="white-space: nowrap;">`--gerrit-topic`</span> | *string* | Gerrit topic to use
<span style="white-space: nowrap;">`--git-committer-email`</span> | *string* | If set, overrides the committer e-mail for the generated commits in git destination.
<span style="white-space: nowrap;">`--git-committer-name`</span> | *string* | If set, overrides the committer name for the generated commits in git destination.
<span style="white-space: nowrap;">`--git-destination-fetch`</span> | *string* | If set, overrides the git destination fetch reference.
<span style="white-space: nowrap;">`--git-destination-fetch-depth`</span> | *integer* | Use a shallow clone of the specified depth for git.destination
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


<code><a href="#origin">origin</a></code> <code>git.gerrit_origin(<a href=#git.gerrit_origin.url>url</a>, <a href=#git.gerrit_origin.ref>ref</a>=None, <a href=#git.gerrit_origin.submodules>submodules</a>='NO', <a href=#git.gerrit_origin.excluded_submodules>excluded_submodules</a>=[], <a href=#git.gerrit_origin.first_parent>first_parent</a>=True, <a href=#git.gerrit_origin.partial_fetch>partial_fetch</a>=False, <a href=#git.gerrit_origin.api_checker>api_checker</a>=None, <a href=#git.gerrit_origin.patch>patch</a>=None, <a href=#git.gerrit_origin.branch>branch</a>=None, <a href=#git.gerrit_origin.describe_version>describe_version</a>=None, <a href=#git.gerrit_origin.ignore_gerrit_noop>ignore_gerrit_noop</a>=False, <a href=#git.gerrit_origin.primary_branch_migration>primary_branch_migration</a>=False, <a href=#git.gerrit_origin.import_wip_changes>import_wip_changes</a>=True)</code>


<h4 id="parameters.git.gerrit_origin">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.gerrit_origin.url href=#git.gerrit_origin.url>url</span> | <code><a href="#string">string</a></code><br><p>Indicates the URL of the git repository</p>
<span id=git.gerrit_origin.ref href=#git.gerrit_origin.ref>ref</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>DEPRECATED. Use git.origin for submitted branches.</p>
<span id=git.gerrit_origin.submodules href=#git.gerrit_origin.submodules>submodules</span> | <code><a href="#string">string</a></code><br><p>Download submodules. Valid values: NO, YES, RECURSIVE.</p>
<span id=git.gerrit_origin.excluded_submodules href=#git.gerrit_origin.excluded_submodules>excluded_submodules</span> | <code>sequence of <a href="#string">string</a></code><br><p>A list of names (not paths, e.g. "foo" is the submodule name if [submodule "foo"] appears in the .gitmodules file) of submodules that will not be download even if 'submodules' is set to YES or RECURSIVE. </p>
<span id=git.gerrit_origin.first_parent href=#git.gerrit_origin.first_parent>first_parent</span> | <code><a href="#bool">bool</a></code><br><p>If true, it only uses the first parent when looking for changes. Note that when disabled in ITERATIVE mode, it will try to do a migration for each change of the merged branch.</p>
<span id=git.gerrit_origin.partial_fetch href=#git.gerrit_origin.partial_fetch>partial_fetch</span> | <code><a href="#bool">bool</a></code><br><p>If true, partially fetch git repository by only fetching affected files.</p>
<span id=git.gerrit_origin.api_checker href=#git.gerrit_origin.api_checker>api_checker</span> | <code><a href="#checker">checker</a></code> or <code>NoneType</code><br><p>A checker for the Gerrit API endpoint provided for after_migration hooks. This field is not required if the workflow hooks don't use the origin/destination endpoints.</p>
<span id=git.gerrit_origin.patch href=#git.gerrit_origin.patch>patch</span> | <code><a href="#transformation">transformation</a></code> or <code>NoneType</code><br><p>Patch the checkout dir. The difference with `patch.apply` transformation is that here we can apply it using three-way</p>
<span id=git.gerrit_origin.branch href=#git.gerrit_origin.branch>branch</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Limit the import to changes that are for this branch. By default imports everything.</p>
<span id=git.gerrit_origin.describe_version href=#git.gerrit_origin.describe_version>describe_version</span> | <code><a href="#bool">bool</a></code> or <code>NoneType</code><br><p>Download tags and use 'git describe' to create four labels with a meaningful version identifier:<br><br>  - `GIT_DESCRIBE_CHANGE_VERSION`: The version for the change or changes being migrated. The value changes per change in `ITERATIVE` mode and will be the latest migrated change in `SQUASH` (In other words, doesn't include excluded changes). this is normally what users want to use.<br> - `GIT_DESCRIBE_REQUESTED_VERSION`: `git describe` for the requested/head version. Constant in `ITERATIVE` mode and includes filtered changes.<br>  -`GIT_DESCRIBE_FIRST_PARENT`: `git describe` for the first parent version.<br>  -`GIT_SEQUENTIAL_REVISION_NUMBER`: The sequential number of the commit. Falls back to the SHA1 if not applicable.<br></p>
<span id=git.gerrit_origin.ignore_gerrit_noop href=#git.gerrit_origin.ignore_gerrit_noop>ignore_gerrit_noop</span> | <code><a href="#bool">bool</a></code><br><p>Option to not migrate Gerrit changes that do not change origin_files</p>
<span id=git.gerrit_origin.primary_branch_migration href=#git.gerrit_origin.primary_branch_migration>primary_branch_migration</span> | <code><a href="#bool">bool</a></code><br><p>When enabled, copybara will ignore the 'ref' param if it is 'master' or 'main' and instead try to establish the default git branch. If this fails, it will fall back to the 'ref' param.<br>This is intended to help migrating to the new standard of using 'main' without breaking users relying on the legacy default.</p>
<span id=git.gerrit_origin.import_wip_changes href=#git.gerrit_origin.import_wip_changes>import_wip_changes</span> | <code><a href="#bool">bool</a></code><br><p>When set to true, Copybara will migrate changes marked as Work in Progress (WIP).</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--force-gerrit-submit`</span> | *boolean* | Override the gerrit submit setting that is set in the config. This also flips the submit bit.
<span style="white-space: nowrap;">`--gerrit-change-id`</span> | *string* | ChangeId to use in the generated commit message. Use this flag if you want to reuse the same Gerrit review for an export.
<span style="white-space: nowrap;">`--gerrit-new-change`</span> | *boolean* | Create a new change instead of trying to reuse an existing one.
<span style="white-space: nowrap;">`--gerrit-topic`</span> | *string* | Gerrit topic to use
<span style="white-space: nowrap;">`--git-fuzzy-last-rev`</span> | *boolean* | By default Copybara will try to migrate the revision listed as the version in the metadata file from github. This flag tells Copybara to first find the git tag which most closely matches the metadata version, and use that for the migration.
<span style="white-space: nowrap;">`--git-origin-log-batch`</span> | *int* | Read the origin git log in batches of n commits. Might be needed for large migrations resulting in git logs of more than 1 GB.
<span style="white-space: nowrap;">`--git-origin-non-linear-history`</span> | *boolean* | Read the full git log and skip changes before the from ref rather than using a log path.
<span style="white-space: nowrap;">`--git-origin-rebase-ref`</span> | *string* | When importing a change from a Git origin ref, it will be rebased to this ref, if set. A common use case: importing a Github PR, rebase it to the main branch (usually 'master'). Note that, if the repo uses submodules, they won't be rebased.
<span style="white-space: nowrap;">`--nogit-origin-version-selector`</span> | *boolean* | Disable the version selector for the migration. Only useful for forcing a migration to the passed version in the CLI

<a id="git.gerrit_trigger" aria-hidden="true"></a>
### git.gerrit_trigger

Defines a feedback trigger based on updates on a Gerrit change.

<code>trigger</code> <code>git.gerrit_trigger(<a href=#git.gerrit_trigger.url>url</a>, <a href=#git.gerrit_trigger.checker>checker</a>=None, <a href=#git.gerrit_trigger.events>events</a>=[], <a href=#git.gerrit_trigger.allow_submit>allow_submit</a>=False)</code>


<h4 id="parameters.git.gerrit_trigger">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.gerrit_trigger.url href=#git.gerrit_trigger.url>url</span> | <code><a href="#string">string</a></code><br><p>Indicates the Gerrit repo URL.</p>
<span id=git.gerrit_trigger.checker href=#git.gerrit_trigger.checker>checker</span> | <code><a href="#checker">checker</a></code> or <code>NoneType</code><br><p>A checker for the Gerrit API transport provided by this trigger.</p>
<span id=git.gerrit_trigger.events href=#git.gerrit_trigger.events>events</span> | <code>sequence of <a href="#string">string</a></code> or <code>dict of sequence</code> or <code>NoneType</code><br><p>Types of events to monitor. Optional. Can either be a list of event types or a dict of event types to particular events of that type, e.g. `['LABELS']` or `{'LABELS': 'my_label_name'}`.<br>Valid values for event types are: `'LABELS'`, `'SUBMIT_REQUIREMENTS'`</p>
<span id=git.gerrit_trigger.allow_submit href=#git.gerrit_trigger.allow_submit>allow_submit</span> | <code><a href="#bool">bool</a></code><br><p>Enable the submit_change method in the endpoint provided</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--force-gerrit-submit`</span> | *boolean* | Override the gerrit submit setting that is set in the config. This also flips the submit bit.
<span style="white-space: nowrap;">`--gerrit-change-id`</span> | *string* | ChangeId to use in the generated commit message. Use this flag if you want to reuse the same Gerrit review for an export.
<span style="white-space: nowrap;">`--gerrit-new-change`</span> | *boolean* | Create a new change instead of trying to reuse an existing one.
<span style="white-space: nowrap;">`--gerrit-topic`</span> | *string* | Gerrit topic to use

<a id="git.github_api" aria-hidden="true"></a>
### git.github_api

Defines a feedback API endpoint for GitHub, that exposes relevant GitHub API operations.

<code>endpoint_provider</code> <code>git.github_api(<a href=#git.github_api.url>url</a>, <a href=#git.github_api.checker>checker</a>=None, <a href=#git.github_api.credentials>credentials</a>=None)</code>


<h4 id="parameters.git.github_api">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.github_api.url href=#git.github_api.url>url</span> | <code><a href="#string">string</a></code><br><p>Indicates the GitHub repo URL.</p>
<span id=git.github_api.checker href=#git.github_api.checker>checker</span> | <code><a href="#checker">checker</a></code> or <code>NoneType</code><br><p>A checker for the GitHub API transport.</p>
<span id=git.github_api.credentials href=#git.github_api.credentials>credentials</span> | <code>UsernamePasswordIssuer</code> or <code>NoneType</code><br><p>EXPERIMENTAL: Read credentials from config file to access the Git Repo. This expects a 'credentials.username_password' specifying the username to use for the remote git host and a password or token. This is gated by the '--use-credentials-from-config' flag</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--allstar-app-ids`</span> | *list* | Flag used to set AllStar GitHub app id aliases. See https://github.com/ossf/allstar.
<span style="white-space: nowrap;">`--github-api-bearer-auth`</span> | *boolean* | If using a token for GitHub access, bearer auth might be required
<span style="white-space: nowrap;">`--github-destination-delete-pr-branch`</span> | *boolean* | Overwrite git.github_destination delete_pr_branch field
<span style="white-space: nowrap;">`--gql-commit-history-override`</span> | *list* | Flag used to target GraphQL params 'first' arguments in the event the defaults are over or underusing the api ratelimit. The flag value should be semicolon separated. This should be rarely used for repos that don't fit well in our defaults. E.g. '50;5;5' represent 50 commits, 5 PRs for each commit, 5 reviews per PR

<a id="git.github_destination" aria-hidden="true"></a>
### git.github_destination

Creates a commit in a GitHub repository branch (for example master). For creating PullRequest use git.github_pr_destination.

<code><a href="#destination">destination</a></code> <code>git.github_destination(<a href=#git.github_destination.url>url</a>, <a href=#git.github_destination.push>push</a>='master', <a href=#git.github_destination.fetch>fetch</a>=None, <a href=#git.github_destination.pr_branch_to_update>pr_branch_to_update</a>=None, <a href=#git.github_destination.partial_fetch>partial_fetch</a>=False, <a href=#git.github_destination.delete_pr_branch>delete_pr_branch</a>=False, <a href=#git.github_destination.integrates>integrates</a>=None, <a href=#git.github_destination.api_checker>api_checker</a>=None, <a href=#git.github_destination.primary_branch_migration>primary_branch_migration</a>=False, <a href=#git.github_destination.tag_name>tag_name</a>=None, <a href=#git.github_destination.tag_msg>tag_msg</a>=None, <a href=#git.github_destination.checker>checker</a>=None, <a href=#git.github_destination.credentials>credentials</a>=None, <a href=#git.github_destination.push_to_fork>push_to_fork</a>=False, <a href=#git.github_destination.github_host_name>github_host_name</a>='github.com')</code>


<h4 id="parameters.git.github_destination">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.github_destination.url href=#git.github_destination.url>url</span> | <code><a href="#string">string</a></code><br><p>Indicates the URL to push to as well as the URL from which to get the parent commit</p>
<span id=git.github_destination.push href=#git.github_destination.push>push</span> | <code><a href="#string">string</a></code><br><p>Reference to use for pushing the change, for example 'main'.</p>
<span id=git.github_destination.fetch href=#git.github_destination.fetch>fetch</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Indicates the ref from which to get the parent commit. Defaults to push value if None</p>
<span id=git.github_destination.pr_branch_to_update href=#git.github_destination.pr_branch_to_update>pr_branch_to_update</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>A template string that refers to a pull request branch in the same repository will be updated to current commit of this push branch only if pr_branch_to_update exists. The reason behind this field is that presubmiting changes creates and leaves a pull request open. By using this, we can automerge/close this type of pull requests. As a result, users will see this pr_branch_to_update as merged to this push branch. Usage: Users can use a string or a string with a label. For instance ${label}_pr_branch_name. And the value of label must be in changes' label list. Otherwise, nothing will happen.</p>
<span id=git.github_destination.partial_fetch href=#git.github_destination.partial_fetch>partial_fetch</span> | <code><a href="#bool">bool</a></code><br><p>This is an experimental feature that only works for certain origin globs.</p>
<span id=git.github_destination.delete_pr_branch href=#git.github_destination.delete_pr_branch>delete_pr_branch</span> | <code><a href="#bool">bool</a></code> or <code>NoneType</code><br><p>When `pr_branch_to_update` is enabled, it will delete the branch reference after the push to the branch and main branch (i.e master) happens. This allows to cleanup temporary branches created for testing.</p>
<span id=git.github_destination.integrates href=#git.github_destination.integrates>integrates</span> | <code>sequence of git_integrate</code> or <code>NoneType</code><br><p>Integrate changes from a url present in the migrated change label. Defaults to a semi-fake merge if COPYBARA_INTEGRATE_REVIEW label is present in the message</p>
<span id=git.github_destination.api_checker href=#git.github_destination.api_checker>api_checker</span> | <code><a href="#checker">checker</a></code> or <code>NoneType</code><br><p>A checker for the Gerrit API endpoint provided for after_migration hooks. This field is not required if the workflow hooks don't use the origin/destination endpoints.</p>
<span id=git.github_destination.primary_branch_migration href=#git.github_destination.primary_branch_migration>primary_branch_migration</span> | <code><a href="#bool">bool</a></code><br><p>When enabled, copybara will ignore the 'push' and 'fetch' params if either is 'master' or 'main' and instead try to establish the default git branch. If this fails, it will fall back to the param's declared value.<br>This is intended to help migrating to the new standard of using 'main' without breaking users relying on the legacy default.</p>
<span id=git.github_destination.tag_name href=#git.github_destination.tag_name>tag_name</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>A template string that specifies to a tag name. If the tag already exists, copybara will only overwrite it if the --git-tag-overwrite flag is set.<br>Note that tag creation is best-effort and the migration will succeed even if the tag cannot be created. Usage: Users can use a string or a string with a label. For instance ${label}_tag_name. And the value of label must be in changes' label list. Otherwise, tag won't be created.</p>
<span id=git.github_destination.tag_msg href=#git.github_destination.tag_msg>tag_msg</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>A template string that refers to the commit msg for a tag. If set, copybara willcreate an annotated tag with this custom message<br>Usage: Labels in the string will be resolved. E.g. .${label}_message.By default, the tag will be created with the labeled commit's message.</p>
<span id=git.github_destination.checker href=#git.github_destination.checker>checker</span> | <code><a href="#checker">checker</a></code> or <code>NoneType</code><br><p>A checker that validates the commit files & message. If `api_checker` is not set, it will also be used for checking API calls. If only `api_checker`is used, that checker will only apply to API calls.</p>
<span id=git.github_destination.credentials href=#git.github_destination.credentials>credentials</span> | <code>UsernamePasswordIssuer</code> or <code>NoneType</code><br><p>EXPERIMENTAL: Read credentials from config file to access the Git Repo. This expects a 'credentials.username_password' specifying the username to use for the remote git host and a password or token. This is gated by the '--use-credentials-from-config' flag</p>
<span id=git.github_destination.github_host_name href=#git.github_destination.github_host_name>github_host_name</span> | <code><a href="#string">string</a></code><br><p>**EXPERIMENTAL feature.** The host name of the GitHub repository, used to construct the URL. Required for GitHub Enterprise.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--allstar-app-ids`</span> | *list* | Flag used to set AllStar GitHub app id aliases. See https://github.com/ossf/allstar.
<span style="white-space: nowrap;">`--git-committer-email`</span> | *string* | If set, overrides the committer e-mail for the generated commits in git destination.
<span style="white-space: nowrap;">`--git-committer-name`</span> | *string* | If set, overrides the committer name for the generated commits in git destination.
<span style="white-space: nowrap;">`--git-destination-fetch`</span> | *string* | If set, overrides the git destination fetch reference.
<span style="white-space: nowrap;">`--git-destination-fetch-depth`</span> | *integer* | Use a shallow clone of the specified depth for git.destination
<span style="white-space: nowrap;">`--git-destination-ignore-integration-errors`</span> | *boolean* | If an integration error occurs, ignore it and continue without the integrate
<span style="white-space: nowrap;">`--git-destination-last-rev-first-parent`</span> | *boolean* | Use git --first-parent flag when looking for last-rev in previous commits
<span style="white-space: nowrap;">`--git-destination-non-fast-forward`</span> | *boolean* | Allow non-fast-forward pushes to the destination. We only allow this when used with different push != fetch references.
<span style="white-space: nowrap;">`--git-destination-path`</span> | *string* | If set, the tool will use this directory for the local repository. Note that if the directory exists it needs to be a git repository. Copybara will revert any staged/unstaged changes. For example, you can override destination url with a local non-bare repo (or existing empty folder) with this flag.
<span style="white-space: nowrap;">`--git-destination-push`</span> | *string* | If set, overrides the git destination push reference.
<span style="white-space: nowrap;">`--git-destination-url`</span> | *string* | If set, overrides the git destination URL.
<span style="white-space: nowrap;">`--git-skip-checker`</span> | *boolean* | If true and git.destination has a configured checker, it will not be used in the migration.
<span style="white-space: nowrap;">`--github-api-bearer-auth`</span> | *boolean* | If using a token for GitHub access, bearer auth might be required
<span style="white-space: nowrap;">`--github-destination-delete-pr-branch`</span> | *boolean* | Overwrite git.github_destination delete_pr_branch field
<span style="white-space: nowrap;">`--gql-commit-history-override`</span> | *list* | Flag used to target GraphQL params 'first' arguments in the event the defaults are over or underusing the api ratelimit. The flag value should be semicolon separated. This should be rarely used for repos that don't fit well in our defaults. E.g. '50;5;5' represent 50 commits, 5 PRs for each commit, 5 reviews per PR
<span style="white-space: nowrap;">`--nogit-destination-rebase`</span> | *boolean* | Don't rebase the change automatically for workflows CHANGE_REQUEST mode

<a id="git.github_origin" aria-hidden="true"></a>
### git.github_origin

Defines a Git origin for a Github repository. This origin should be used for public branches. Use github_pr_origin for importing Pull Requests.

<code><a href="#origin">origin</a></code> <code>git.github_origin(<a href=#git.github_origin.url>url</a>, <a href=#git.github_origin.ref>ref</a>=None, <a href=#git.github_origin.submodules>submodules</a>='NO', <a href=#git.github_origin.excluded_submodules>excluded_submodules</a>=[], <a href=#git.github_origin.first_parent>first_parent</a>=True, <a href=#git.github_origin.partial_fetch>partial_fetch</a>=False, <a href=#git.github_origin.patch>patch</a>=None, <a href=#git.github_origin.describe_version>describe_version</a>=None, <a href=#git.github_origin.version_selector>version_selector</a>=None, <a href=#git.github_origin.primary_branch_migration>primary_branch_migration</a>=False, <a href=#git.github_origin.enable_lfs>enable_lfs</a>=False, <a href=#git.github_origin.credentials>credentials</a>=None, <a href=#git.github_origin.repo_id>repo_id</a>=None, <a href=#git.github_origin.github_host_name>github_host_name</a>='github.com')</code>


<h4 id="parameters.git.github_origin">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.github_origin.url href=#git.github_origin.url>url</span> | <code><a href="#string">string</a></code><br><p>Indicates the URL of the git repository</p>
<span id=git.github_origin.ref href=#git.github_origin.ref>ref</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Represents the default reference that will be used for reading the revision from the git repository. For example: 'master'</p>
<span id=git.github_origin.submodules href=#git.github_origin.submodules>submodules</span> | <code><a href="#string">string</a></code><br><p>Download submodules. Valid values: NO, YES, RECURSIVE.</p>
<span id=git.github_origin.excluded_submodules href=#git.github_origin.excluded_submodules>excluded_submodules</span> | <code>sequence of <a href="#string">string</a></code><br><p>A list of names (not paths, e.g. "foo" is the submodule name if [submodule "foo"] appears in the .gitmodules file) of submodules that will not be download even if 'submodules' is set to YES or RECURSIVE. </p>
<span id=git.github_origin.first_parent href=#git.github_origin.first_parent>first_parent</span> | <code><a href="#bool">bool</a></code><br><p>If true, it only uses the first parent when looking for changes. Note that when disabled in ITERATIVE mode, it will try to do a migration for each change of the merged branch.</p>
<span id=git.github_origin.partial_fetch href=#git.github_origin.partial_fetch>partial_fetch</span> | <code><a href="#bool">bool</a></code><br><p>If true, partially fetch git repository by only fetching affected files.</p>
<span id=git.github_origin.patch href=#git.github_origin.patch>patch</span> | <code><a href="#transformation">transformation</a></code> or <code>NoneType</code><br><p>Patch the checkout dir. The difference with `patch.apply` transformation is that here we can apply it using three-way</p>
<span id=git.github_origin.describe_version href=#git.github_origin.describe_version>describe_version</span> | <code><a href="#bool">bool</a></code> or <code>NoneType</code><br><p>Download tags and use 'git describe' to create four labels with a meaningful version identifier:<br><br>  - `GIT_DESCRIBE_CHANGE_VERSION`: The version for the change or changes being migrated. The value changes per change in `ITERATIVE` mode and will be the latest migrated change in `SQUASH` (In other words, doesn't include excluded changes). this is normally what users want to use.<br> - `GIT_DESCRIBE_REQUESTED_VERSION`: `git describe` for the requested/head version. Constant in `ITERATIVE` mode and includes filtered changes.<br>  -`GIT_DESCRIBE_FIRST_PARENT`: `git describe` for the first parent version.<br>  -`GIT_SEQUENTIAL_REVISION_NUMBER`: The sequential number of the commit. Falls back to the SHA1 if not applicable.<br></p>
<span id=git.github_origin.version_selector href=#git.github_origin.version_selector>version_selector</span> | <code><a href="#versionselector">VersionSelector</a></code> or <code>NoneType</code><br><p>Select a custom version (tag)to migrate instead of 'ref'. Version selector is expected to match the whole refspec (e.g. 'refs/heads/${n1}')</p>
<span id=git.github_origin.primary_branch_migration href=#git.github_origin.primary_branch_migration>primary_branch_migration</span> | <code><a href="#bool">bool</a></code><br><p>When enabled, copybara will ignore the 'ref' param if it is 'master' or 'main' and instead try to establish the default git branch. If this fails, it will fall back to the 'ref' param.<br>This is intended to help migrating to the new standard of using 'main' without breaking users relying on the legacy default.</p>
<span id=git.github_origin.enable_lfs href=#git.github_origin.enable_lfs>enable_lfs</span> | <code><a href="#bool">bool</a></code><br><p>If true, Large File Storage support is enabled for the origin.</p>
<span id=git.github_origin.credentials href=#git.github_origin.credentials>credentials</span> | <code>UsernamePasswordIssuer</code> or <code>NoneType</code><br><p>EXPERIMENTAL: Read credentials from config file to access the Git Repo. This expects a 'credentials.username_password' specifying the username to use for the remote git host and a password or token. This is gated by the '--use-credentials-from-config' flag</p>
<span id=git.github_origin.repo_id href=#git.github_origin.repo_id>repo_id</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>The repo id of the github repository, used as a stable reference to the repo for validation.</p>
<span id=git.github_origin.github_host_name href=#git.github_origin.github_host_name>github_host_name</span> | <code><a href="#string">string</a></code><br><p>**EXPERIMENTAL feature** The github host name of the repository.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--allstar-app-ids`</span> | *list* | Flag used to set AllStar GitHub app id aliases. See https://github.com/ossf/allstar.
<span style="white-space: nowrap;">`--git-fuzzy-last-rev`</span> | *boolean* | By default Copybara will try to migrate the revision listed as the version in the metadata file from github. This flag tells Copybara to first find the git tag which most closely matches the metadata version, and use that for the migration.
<span style="white-space: nowrap;">`--git-origin-log-batch`</span> | *int* | Read the origin git log in batches of n commits. Might be needed for large migrations resulting in git logs of more than 1 GB.
<span style="white-space: nowrap;">`--git-origin-non-linear-history`</span> | *boolean* | Read the full git log and skip changes before the from ref rather than using a log path.
<span style="white-space: nowrap;">`--git-origin-rebase-ref`</span> | *string* | When importing a change from a Git origin ref, it will be rebased to this ref, if set. A common use case: importing a Github PR, rebase it to the main branch (usually 'master'). Note that, if the repo uses submodules, they won't be rebased.
<span style="white-space: nowrap;">`--github-api-bearer-auth`</span> | *boolean* | If using a token for GitHub access, bearer auth might be required
<span style="white-space: nowrap;">`--github-destination-delete-pr-branch`</span> | *boolean* | Overwrite git.github_destination delete_pr_branch field
<span style="white-space: nowrap;">`--gql-commit-history-override`</span> | *list* | Flag used to target GraphQL params 'first' arguments in the event the defaults are over or underusing the api ratelimit. The flag value should be semicolon separated. This should be rarely used for repos that don't fit well in our defaults. E.g. '50;5;5' represent 50 commits, 5 PRs for each commit, 5 reviews per PR
<span style="white-space: nowrap;">`--nogit-origin-version-selector`</span> | *boolean* | Disable the version selector for the migration. Only useful for forcing a migration to the passed version in the CLI

<a id="git.github_pr_destination" aria-hidden="true"></a>
### git.github_pr_destination

Creates changes in a new pull request in the destination.

<code><a href="#destination">destination</a></code> <code>git.github_pr_destination(<a href=#git.github_pr_destination.url>url</a>, <a href=#git.github_pr_destination.destination_ref>destination_ref</a>='master', <a href=#git.github_pr_destination.pr_branch>pr_branch</a>=None, <a href=#git.github_pr_destination.partial_fetch>partial_fetch</a>=False, <a href=#git.github_pr_destination.allow_empty_diff>allow_empty_diff</a>=True, <a href=#git.github_pr_destination.allow_empty_diff_merge_statuses>allow_empty_diff_merge_statuses</a>=[], <a href=#git.github_pr_destination.allow_empty_diff_check_suites_to_conclusion>allow_empty_diff_check_suites_to_conclusion</a>={}, <a href=#git.github_pr_destination.title>title</a>=None, <a href=#git.github_pr_destination.body>body</a>=None, <a href=#git.github_pr_destination.assignees>assignees</a>=[], <a href=#git.github_pr_destination.integrates>integrates</a>=None, <a href=#git.github_pr_destination.api_checker>api_checker</a>=None, <a href=#git.github_pr_destination.update_description>update_description</a>=False, <a href=#git.github_pr_destination.primary_branch_migration>primary_branch_migration</a>=False, <a href=#git.github_pr_destination.checker>checker</a>=None, <a href=#git.github_pr_destination.draft>draft</a>=False, <a href=#git.github_pr_destination.credentials>credentials</a>=None, <a href=#git.github_pr_destination.github_host_name>github_host_name</a>='github.com')</code>


<h4 id="parameters.git.github_pr_destination">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.github_pr_destination.url href=#git.github_pr_destination.url>url</span> | <code><a href="#string">string</a></code><br><p>Url of the GitHub project. For example "https://github.com/google/copybara'"</p>
<span id=git.github_pr_destination.destination_ref href=#git.github_pr_destination.destination_ref>destination_ref</span> | <code><a href="#string">string</a></code><br><p>Destination reference for the change.</p>
<span id=git.github_pr_destination.pr_branch href=#git.github_pr_destination.pr_branch>pr_branch</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Customize the pull request branch. The token ${CONTEXT_REFERENCE} will be replaced with the corresponding stable reference (head, PR number, Gerrit change number, etc.).</p>
<span id=git.github_pr_destination.partial_fetch href=#git.github_pr_destination.partial_fetch>partial_fetch</span> | <code><a href="#bool">bool</a></code><br><p>This is an experimental feature that only works for certain origin globs.</p>
<span id=git.github_pr_destination.allow_empty_diff href=#git.github_pr_destination.allow_empty_diff>allow_empty_diff</span> | <code><a href="#bool">bool</a></code><br><p>By default, copybara migrates changes without checking existing PRs. If set, copybara will skip pushing a change to an existing PR only if the git three of the pending migrating change is the same as the existing PR.</p>
<span id=git.github_pr_destination.allow_empty_diff_merge_statuses href=#git.github_pr_destination.allow_empty_diff_merge_statuses>allow_empty_diff_merge_statuses</span> | <code>sequence of <a href="#string">string</a></code><br><p>**EXPERIMENTAL feature.** By default, if `allow_empty_diff = False` is set, Copybara skips uploading the change if the tree hasn't changed and it can be merged. When this list is set with values from https://docs.github.com/en/github-ae@latest/graphql/reference/enums#mergestatestatus, it will still upload for the configured statuses. For example, if a user sets it to `['DIRTY', 'UNSTABLE', 'UNKNOWN']` (the recommended set to use), it wouldn't skip upload if test failed in GitHub for previous export, or if the change cannot be merged. **Note that this field is experimental and is subject to change by GitHub without notice**. Please consult Copybara team before using this field.</p>
<span id=git.github_pr_destination.allow_empty_diff_check_suites_to_conclusion href=#git.github_pr_destination.allow_empty_diff_check_suites_to_conclusion>allow_empty_diff_check_suites_to_conclusion</span> | <code>dict of string</code><br><p>**EXPERIMENTAL feature.** By default, if `allow_empty_diff = False` is set, Copybara skips uploading the change if the tree hasn't changed and it can be merged.<br><br>This field allows to configure Check suit slugs and conclusions for those check suites where an upload needs to happen despite no code changes. For example this can be used to upload if tests are failing. A Very common usage would be `{"github-actions" :   ["none", "failure", "timed_out", "cancelled"]}`: This would upload changes when Checks are in progress, has failed, timeout or being cancelled. `github-actions` check suit slug name is the default name for checks run by GitHub actions where the suit is not given a name.</p>
<span id=git.github_pr_destination.title href=#git.github_pr_destination.title>title</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>When creating (or updating if `update_description` is set) a pull request, use this title. By default it uses the change first line. This field accepts a template with labels. For example: `"Change ${CONTEXT_REFERENCE}"`</p>
<span id=git.github_pr_destination.body href=#git.github_pr_destination.body>body</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>When creating (or updating if `update_description` is set) a pull request, use this body. By default it uses the change summary. This field accepts a template with labels. For example: `"Change ${CONTEXT_REFERENCE}"`</p>
<span id=git.github_pr_destination.assignees href=#git.github_pr_destination.assignees>assignees</span> | <code>sequence of <a href="#string">string</a></code><br><p>The assignees to set when creating a new pull request. The maximum number of assignees is 10 and the assignees must be GitHub usernames or a label that can be resolved to a GitHub username. For example: `assignees = ["github-repo-owner1", "${YOUR_LABEL}"]`</p>
<span id=git.github_pr_destination.integrates href=#git.github_pr_destination.integrates>integrates</span> | <code>sequence of git_integrate</code> or <code>NoneType</code><br><p>Integrate changes from a url present in the migrated change label. Defaults to a semi-fake merge if COPYBARA_INTEGRATE_REVIEW label is present in the message</p>
<span id=git.github_pr_destination.api_checker href=#git.github_pr_destination.api_checker>api_checker</span> | <code><a href="#checker">checker</a></code> or <code>NoneType</code><br><p>A checker for the GitHub API endpoint provided for after_migration hooks. This field is not required if the workflow hooks don't use the origin/destination endpoints.</p>
<span id=git.github_pr_destination.update_description href=#git.github_pr_destination.update_description>update_description</span> | <code><a href="#bool">bool</a></code><br><p>By default, Copybara only set the title and body of the PR when creating the PR. If this field is set to true, it will update those fields for every update.</p>
<span id=git.github_pr_destination.primary_branch_migration href=#git.github_pr_destination.primary_branch_migration>primary_branch_migration</span> | <code><a href="#bool">bool</a></code><br><p>When enabled, copybara will ignore the 'desination_ref' param if it is 'master' or 'main' and instead try to establish the default git branch. If this fails, it will fall back to the param's declared value.<br>This is intended to help migrating to the new standard of using 'main' without breaking users relying on the legacy default.</p>
<span id=git.github_pr_destination.checker href=#git.github_pr_destination.checker>checker</span> | <code><a href="#checker">checker</a></code> or <code>NoneType</code><br><p>A checker that validates the commit files & message. If `api_checker` is not set, it will also be used for checking API calls. If only `api_checker`is used, that checker will only apply to API calls.</p>
<span id=git.github_pr_destination.draft href=#git.github_pr_destination.draft>draft</span> | <code><a href="#bool">bool</a></code><br><p>Flag create pull request as draft or not.</p>
<span id=git.github_pr_destination.credentials href=#git.github_pr_destination.credentials>credentials</span> | <code>UsernamePasswordIssuer</code> or <code>NoneType</code><br><p>EXPERIMENTAL: Read credentials from config file to access the Git Repo. This expects a 'credentials.username_password' specifying the username to use for the remote git host and a password or token. This is gated by the '--use-credentials-from-config' flag</p>
<span id=git.github_pr_destination.github_host_name href=#git.github_pr_destination.github_host_name>github_host_name</span> | <code><a href="#string">string</a></code><br><p>**EXPERIMENTAL feature** The GitHub host name to use for the migration.</p>


<h4 id="example.git.github_pr_destination">Examples:</h4>


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
<span style="white-space: nowrap;">`--allstar-app-ids`</span> | *list* | Flag used to set AllStar GitHub app id aliases. See https://github.com/ossf/allstar.
<span style="white-space: nowrap;">`--git-committer-email`</span> | *string* | If set, overrides the committer e-mail for the generated commits in git destination.
<span style="white-space: nowrap;">`--git-committer-name`</span> | *string* | If set, overrides the committer name for the generated commits in git destination.
<span style="white-space: nowrap;">`--git-destination-fetch`</span> | *string* | If set, overrides the git destination fetch reference.
<span style="white-space: nowrap;">`--git-destination-fetch-depth`</span> | *integer* | Use a shallow clone of the specified depth for git.destination
<span style="white-space: nowrap;">`--git-destination-ignore-integration-errors`</span> | *boolean* | If an integration error occurs, ignore it and continue without the integrate
<span style="white-space: nowrap;">`--git-destination-last-rev-first-parent`</span> | *boolean* | Use git --first-parent flag when looking for last-rev in previous commits
<span style="white-space: nowrap;">`--git-destination-non-fast-forward`</span> | *boolean* | Allow non-fast-forward pushes to the destination. We only allow this when used with different push != fetch references.
<span style="white-space: nowrap;">`--git-destination-path`</span> | *string* | If set, the tool will use this directory for the local repository. Note that if the directory exists it needs to be a git repository. Copybara will revert any staged/unstaged changes. For example, you can override destination url with a local non-bare repo (or existing empty folder) with this flag.
<span style="white-space: nowrap;">`--git-destination-push`</span> | *string* | If set, overrides the git destination push reference.
<span style="white-space: nowrap;">`--git-destination-url`</span> | *string* | If set, overrides the git destination URL.
<span style="white-space: nowrap;">`--git-skip-checker`</span> | *boolean* | If true and git.destination has a configured checker, it will not be used in the migration.
<span style="white-space: nowrap;">`--github-api-bearer-auth`</span> | *boolean* | If using a token for GitHub access, bearer auth might be required
<span style="white-space: nowrap;">`--github-destination-delete-pr-branch`</span> | *boolean* | Overwrite git.github_destination delete_pr_branch field
<span style="white-space: nowrap;">`--github-destination-pr-branch`</span> | *string* | If set, uses this branch for creating the pull request instead of using a generated one
<span style="white-space: nowrap;">`--github-destination-pr-create`</span> | *boolean* | If the pull request should be created
<span style="white-space: nowrap;">`--gql-commit-history-override`</span> | *list* | Flag used to target GraphQL params 'first' arguments in the event the defaults are over or underusing the api ratelimit. The flag value should be semicolon separated. This should be rarely used for repos that don't fit well in our defaults. E.g. '50;5;5' represent 50 commits, 5 PRs for each commit, 5 reviews per PR
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


<code><a href="#origin">origin</a></code> <code>git.github_pr_origin(<a href=#git.github_pr_origin.url>url</a>, <a href=#git.github_pr_origin.use_merge>use_merge</a>=False, <a href=#git.github_pr_origin.required_labels>required_labels</a>=[], <a href=#git.github_pr_origin.required_status_context_names>required_status_context_names</a>=[], <a href=#git.github_pr_origin.required_check_runs>required_check_runs</a>=[], <a href=#git.github_pr_origin.retryable_labels>retryable_labels</a>=[], <a href=#git.github_pr_origin.submodules>submodules</a>='NO', <a href=#git.github_pr_origin.excluded_submodules>excluded_submodules</a>=[], <a href=#git.github_pr_origin.baseline_from_branch>baseline_from_branch</a>=False, <a href=#git.github_pr_origin.first_parent>first_parent</a>=True, <a href=#git.github_pr_origin.partial_fetch>partial_fetch</a>=False, <a href=#git.github_pr_origin.state>state</a>='OPEN', <a href=#git.github_pr_origin.review_state>review_state</a>=None, <a href=#git.github_pr_origin.review_approvers>review_approvers</a>=["COLLABORATOR", "MEMBER", "OWNER"], <a href=#git.github_pr_origin.api_checker>api_checker</a>=None, <a href=#git.github_pr_origin.patch>patch</a>=None, <a href=#git.github_pr_origin.branch>branch</a>=None, <a href=#git.github_pr_origin.describe_version>describe_version</a>=None, <a href=#git.github_pr_origin.credentials>credentials</a>=None, <a href=#git.github_pr_origin.github_host_name>github_host_name</a>='github.com')</code>


<h4 id="parameters.git.github_pr_origin">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.github_pr_origin.url href=#git.github_pr_origin.url>url</span> | <code><a href="#string">string</a></code><br><p>Indicates the URL of the GitHub repository</p>
<span id=git.github_pr_origin.use_merge href=#git.github_pr_origin.use_merge>use_merge</span> | <code><a href="#bool">bool</a></code><br><p>If the content for refs/pull/&lt;ID&gt;/merge should be used instead of the PR head. The GitOrigin-RevId still will be the one from refs/pull/&lt;ID&gt;/head revision.</p>
<span id=git.github_pr_origin.required_labels href=#git.github_pr_origin.required_labels>required_labels</span> | <code>sequence of <a href="#string">string</a></code><br><p>Required labels to import the PR. All the labels need to be present in order to migrate the Pull Request.</p>
<span id=git.github_pr_origin.required_status_context_names href=#git.github_pr_origin.required_status_context_names>required_status_context_names</span> | <code>sequence of <a href="#string">string</a></code><br><p>A list of names of services which must all mark the PR with 'success' before it can be imported.<br><br>See https://docs.github.com/en/rest/reference/repos#statuses</p>
<span id=git.github_pr_origin.required_check_runs href=#git.github_pr_origin.required_check_runs>required_check_runs</span> | <code>sequence of <a href="#string">string</a></code><br><p>A list of check runs which must all have a value of 'success' in order to import the PR.<br><br>See https://docs.github.com/en/rest/guides/getting-started-with-the-checks-api</p>
<span id=git.github_pr_origin.retryable_labels href=#git.github_pr_origin.retryable_labels>retryable_labels</span> | <code>sequence of <a href="#string">string</a></code><br><p>Required labels to import the PR that should be retried. This parameter must be a subset of required_labels.</p>
<span id=git.github_pr_origin.submodules href=#git.github_pr_origin.submodules>submodules</span> | <code><a href="#string">string</a></code><br><p>Download submodules. Valid values: NO, YES, RECURSIVE.</p>
<span id=git.github_pr_origin.excluded_submodules href=#git.github_pr_origin.excluded_submodules>excluded_submodules</span> | <code>sequence of <a href="#string">string</a></code><br><p>A list of names (not paths, e.g. "foo" is the submodule name if [submodule "foo"] appears in the .gitmodules file) of submodules that will not be download even if 'submodules' is set to YES or RECURSIVE. </p>
<span id=git.github_pr_origin.baseline_from_branch href=#git.github_pr_origin.baseline_from_branch>baseline_from_branch</span> | <code><a href="#bool">bool</a></code><br><p>WARNING: Use this field only for github -> git CHANGE_REQUEST workflows.<br>When the field is set to true for CHANGE_REQUEST workflows it will find the baseline comparing the Pull Request with the base branch instead of looking for the *-RevId label in the commit message.</p>
<span id=git.github_pr_origin.first_parent href=#git.github_pr_origin.first_parent>first_parent</span> | <code><a href="#bool">bool</a></code><br><p>If true, it only uses the first parent when looking for changes. Note that when disabled in ITERATIVE mode, it will try to do a migration for each change of the merged branch.</p>
<span id=git.github_pr_origin.partial_fetch href=#git.github_pr_origin.partial_fetch>partial_fetch</span> | <code><a href="#bool">bool</a></code><br><p>This is an experimental feature that only works for certain origin globs.</p>
<span id=git.github_pr_origin.state href=#git.github_pr_origin.state>state</span> | <code><a href="#string">string</a></code><br><p>Only migrate Pull Request with that state. Possible values: `'OPEN'`, `'CLOSED'` or `'ALL'`. Default 'OPEN'</p>
<span id=git.github_pr_origin.review_state href=#git.github_pr_origin.review_state>review_state</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Required state of the reviews associated with the Pull Request Possible values:<br><br>-  `ANY`: No review or approval required.<br>-  `HAS_REVIEWERS`: A reviewer interacted with the change, e.g. commented.<br>-  `ANY_COMMIT_APPROVED`: At least one commit in the PR was approved.<br>-  `HEAD_COMMIT_APPROVED`: The current head commit was approved.<br><br> Default is `None` which has no requirement.<br>This field is required if the user wants `GITHUB_PR_REVIEWER_APPROVER` and `GITHUB_PR_REVIEWER_OTHER` labels populated</p>
<span id=git.github_pr_origin.review_approvers href=#git.github_pr_origin.review_approvers>review_approvers</span> | <code>sequence of <a href="#string">string</a></code> or <code>NoneType</code><br><p>The set of reviewer types that are considered for approvals. In order to have any effect, `review_state` needs to be set. GITHUB_PR_REVIEWER_APPROVER` will be populated for these types. See the valid types here: https://developer.github.com/v4/enum/commentauthorassociation/</p>
<span id=git.github_pr_origin.api_checker href=#git.github_pr_origin.api_checker>api_checker</span> | <code><a href="#checker">checker</a></code> or <code>NoneType</code><br><p>A checker for the GitHub API endpoint provided for after_migration hooks. This field is not required if the workflow hooks don't use the origin/destination endpoints.</p>
<span id=git.github_pr_origin.patch href=#git.github_pr_origin.patch>patch</span> | <code><a href="#transformation">transformation</a></code> or <code>NoneType</code><br><p>Patch the checkout dir. The difference with `patch.apply` transformation is that here we can apply it using three-way</p>
<span id=git.github_pr_origin.branch href=#git.github_pr_origin.branch>branch</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>If set, it will only migrate pull requests for this base branch</p>
<span id=git.github_pr_origin.describe_version href=#git.github_pr_origin.describe_version>describe_version</span> | <code><a href="#bool">bool</a></code> or <code>NoneType</code><br><p>Download tags and use 'git describe' to create four labels with a meaningful version identifier:<br><br>  - `GIT_DESCRIBE_CHANGE_VERSION`: The version for the change or changes being migrated. The value changes per change in `ITERATIVE` mode and will be the latest migrated change in `SQUASH` (In other words, doesn't include excluded changes). this is normally what users want to use.<br> - `GIT_DESCRIBE_REQUESTED_VERSION`: `git describe` for the requested/head version. Constant in `ITERATIVE` mode and includes filtered changes.<br>  -`GIT_DESCRIBE_FIRST_PARENT`: `git describe` for the first parent version.<br>  -`GIT_SEQUENTIAL_REVISION_NUMBER`: The sequential number of the commit. Falls back to the SHA1 if not applicable.<br></p>
<span id=git.github_pr_origin.credentials href=#git.github_pr_origin.credentials>credentials</span> | <code>UsernamePasswordIssuer</code> or <code>NoneType</code><br><p>EXPERIMENTAL: Read credentials from config file to access the Git Repo. This expects a 'credentials.username_password' specifying the username to use for the remote git host and a password or token. This is gated by the '--use-credentials-from-config' flag</p>
<span id=git.github_pr_origin.github_host_name href=#git.github_pr_origin.github_host_name>github_host_name</span> | <code><a href="#string">string</a></code><br><p>**EXPERIMENTAL feature.** The host name of the GitHub repository, used to construct the URL. Required for GitHub Enterprise.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--allstar-app-ids`</span> | *list* | Flag used to set AllStar GitHub app id aliases. See https://github.com/ossf/allstar.
<span style="white-space: nowrap;">`--git-fuzzy-last-rev`</span> | *boolean* | By default Copybara will try to migrate the revision listed as the version in the metadata file from github. This flag tells Copybara to first find the git tag which most closely matches the metadata version, and use that for the migration.
<span style="white-space: nowrap;">`--git-origin-log-batch`</span> | *int* | Read the origin git log in batches of n commits. Might be needed for large migrations resulting in git logs of more than 1 GB.
<span style="white-space: nowrap;">`--git-origin-non-linear-history`</span> | *boolean* | Read the full git log and skip changes before the from ref rather than using a log path.
<span style="white-space: nowrap;">`--git-origin-rebase-ref`</span> | *string* | When importing a change from a Git origin ref, it will be rebased to this ref, if set. A common use case: importing a Github PR, rebase it to the main branch (usually 'master'). Note that, if the repo uses submodules, they won't be rebased.
<span style="white-space: nowrap;">`--github-api-bearer-auth`</span> | *boolean* | If using a token for GitHub access, bearer auth might be required
<span style="white-space: nowrap;">`--github-destination-delete-pr-branch`</span> | *boolean* | Overwrite git.github_destination delete_pr_branch field
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
<span style="white-space: nowrap;">`--gql-commit-history-override`</span> | *list* | Flag used to target GraphQL params 'first' arguments in the event the defaults are over or underusing the api ratelimit. The flag value should be semicolon separated. This should be rarely used for repos that don't fit well in our defaults. E.g. '50;5;5' represent 50 commits, 5 PRs for each commit, 5 reviews per PR
<span style="white-space: nowrap;">`--nogit-origin-version-selector`</span> | *boolean* | Disable the version selector for the migration. Only useful for forcing a migration to the passed version in the CLI

<a id="git.github_trigger" aria-hidden="true"></a>
### git.github_trigger

Defines a feedback trigger based on updates on a GitHub PR.

<code>trigger</code> <code>git.github_trigger(<a href=#git.github_trigger.url>url</a>, <a href=#git.github_trigger.checker>checker</a>=None, <a href=#git.github_trigger.events>events</a>=[], <a href=#git.github_trigger.credentials>credentials</a>=None)</code>


<h4 id="parameters.git.github_trigger">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.github_trigger.url href=#git.github_trigger.url>url</span> | <code><a href="#string">string</a></code><br><p>Indicates the GitHub repo URL.</p>
<span id=git.github_trigger.checker href=#git.github_trigger.checker>checker</span> | <code><a href="#checker">checker</a></code> or <code>NoneType</code><br><p>A checker for the GitHub API transport provided by this trigger.</p>
<span id=git.github_trigger.events href=#git.github_trigger.events>events</span> | <code>sequence of <a href="#string">string</a></code> or <code>dict of sequence</code><br><p>Types of events to subscribe. Can  either be a list of event types or a dict of event types to particular events of that type, e.g. `['CHECK_RUNS']` or `{'CHECK_RUNS': 'my_check_run_name'}`.<br>Valid values for event types are: `'ISSUES'`, `'ISSUE_COMMENT'`, `'PULL_REQUEST'`,  `'PULL_REQUEST_REVIEW_COMMENT'`, `'PUSH'`, `'STATUS'`, `'CHECK_RUNS'`</p>
<span id=git.github_trigger.credentials href=#git.github_trigger.credentials>credentials</span> | <code>UsernamePasswordIssuer</code> or <code>NoneType</code><br><p>EXPERIMENTAL: Read credentials from config file to access the Git Repo. This expects a 'credentials.username_password' specifying the username to use for the remote git host and a password or token. This is gated by the '--use-credentials-from-config' flag</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--allstar-app-ids`</span> | *list* | Flag used to set AllStar GitHub app id aliases. See https://github.com/ossf/allstar.
<span style="white-space: nowrap;">`--github-api-bearer-auth`</span> | *boolean* | If using a token for GitHub access, bearer auth might be required
<span style="white-space: nowrap;">`--github-destination-delete-pr-branch`</span> | *boolean* | Overwrite git.github_destination delete_pr_branch field
<span style="white-space: nowrap;">`--gql-commit-history-override`</span> | *list* | Flag used to target GraphQL params 'first' arguments in the event the defaults are over or underusing the api ratelimit. The flag value should be semicolon separated. This should be rarely used for repos that don't fit well in our defaults. E.g. '50;5;5' represent 50 commits, 5 PRs for each commit, 5 reviews per PR

<a id="git.integrate" aria-hidden="true"></a>
### git.integrate

Integrate changes from a url present in the migrated change label.

<code>git_integrate</code> <code>git.integrate(<a href=#git.integrate.label>label</a>="COPYBARA_INTEGRATE_REVIEW", <a href=#git.integrate.strategy>strategy</a>="FAKE_MERGE_AND_INCLUDE_FILES", <a href=#git.integrate.ignore_errors>ignore_errors</a>=True)</code>


<h4 id="parameters.git.integrate">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.integrate.label href=#git.integrate.label>label</span> | <code><a href="#string">string</a></code><br><p>The migration label that will contain the url to the change to integrate.</p>
<span id=git.integrate.strategy href=#git.integrate.strategy>strategy</span> | <code><a href="#string">string</a></code><br><p>How to integrate the change:<br><ul> <li><b>'FAKE_MERGE'</b>: Add the url revision/reference as parent of the migration change but ignore all the files from the url. The commit message will be a standard merge one but will include the corresponding RevId label</li> <li><b>'FAKE_MERGE_AND_INCLUDE_FILES'</b>: Same as 'FAKE_MERGE' but any change to files that doesn't match destination_files will be included as part of the merge commit. So it will be a semi fake merge: Fake for destination_files but merge for non destination files.</li> <li><b>'INCLUDE_FILES'</b>: Same as 'FAKE_MERGE_AND_INCLUDE_FILES' but it it doesn't create a merge but only include changes not matching destination_files</li></ul></p>
<span id=git.integrate.ignore_errors href=#git.integrate.ignore_errors>ignore_errors</span> | <code><a href="#bool">bool</a></code><br><p>If we should ignore integrate errors and continue the migration without the integrate</p>


<h4 id="example.git.integrate">Example:</h4>


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

<code><a href="#versionselector">VersionSelector</a></code> <code>git.latest_version(<a href=#git.latest_version.refspec_format>refspec_format</a>="refs/tags/${n0}.${n1}.${n2}", <a href=#git.latest_version.refspec_groups>refspec_groups</a>={'n0' : '[0-9]+', 'n1' : '[0-9]+', 'n2' : '[0-9]+'})</code>


<h4 id="parameters.git.latest_version">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.latest_version.refspec_format href=#git.latest_version.refspec_format>refspec_format</span> | <code><a href="#string">string</a></code><br><p>The format of the branch/tag</p>
<span id=git.latest_version.refspec_groups href=#git.latest_version.refspec_groups>refspec_groups</span> | <code><a href="#dict">dict</a></code><br><p>A set of named regexes that can be used to match part of the versions. Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax. Use the following nomenclature n0, n1, n2 for the version part (will use numeric sorting) or s0, s1, s2 (alphabetic sorting). Note that there can be mixed but the numbers cannot be repeated. In other words n0, s1, n2 is valid but not n0, s0, n1. n0 has more priority than n1. If there are fields where order is not important, use s(N+1) where N ist he latest sorted field. Example {"n0": "[0-9]+", "s1": "[a-z]+"}</p>

<a id="git.mirror" aria-hidden="true"></a>
### git.mirror

Mirror git references between repositories

<code>git.mirror(<a href=#git.mirror.name>name</a>, <a href=#git.mirror.origin>origin</a>, <a href=#git.mirror.destination>destination</a>, <a href=#git.mirror.refspecs>refspecs</a>=['refs/heads/*'], <a href=#git.mirror.prune>prune</a>=False, <a href=#git.mirror.partial_fetch>partial_fetch</a>=False, <a href=#git.mirror.description>description</a>=None, <a href=#git.mirror.action>action</a>=None, <a href=#git.mirror.origin_checker>origin_checker</a>=None, <a href=#git.mirror.destination_checker>destination_checker</a>=None, <a href=#git.mirror.origin_credentials>origin_credentials</a>=None, <a href=#git.mirror.destination_credentials>destination_credentials</a>=None)</code>


<h4 id="parameters.git.mirror">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.mirror.name href=#git.mirror.name>name</span> | <code><a href="#string">string</a></code><br><p>Migration name</p>
<span id=git.mirror.origin href=#git.mirror.origin>origin</span> | <code><a href="#string">string</a></code><br><p>Indicates the URL of the origin git repository</p>
<span id=git.mirror.destination href=#git.mirror.destination>destination</span> | <code><a href="#string">string</a></code><br><p>Indicates the URL of the destination git repository</p>
<span id=git.mirror.refspecs href=#git.mirror.refspecs>refspecs</span> | <code>sequence of <a href="#string">string</a></code><br><p>Represents a list of git refspecs to mirror between origin and destination. For example 'refs/heads/*:refs/remotes/origin/*' will mirror any reference inside refs/heads to refs/remotes/origin.</p>
<span id=git.mirror.prune href=#git.mirror.prune>prune</span> | <code><a href="#bool">bool</a></code><br><p>Remove remote refs that don't have a origin counterpart. Prune is ignored if actions are used (Action is in charge of doing the pruning)</p>
<span id=git.mirror.partial_fetch href=#git.mirror.partial_fetch>partial_fetch</span> | <code><a href="#bool">bool</a></code><br><p>This is an experimental feature that only works for certain origin globs.</p>
<span id=git.mirror.description href=#git.mirror.description>description</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>A description of what this migration achieves</p>
<span id=git.mirror.action href=#git.mirror.action>action</span> | <code>unknown</code><br><p>An action to execute when the migration is triggered. Actions can fetch, push, rebase, merge, etc. Only fetches/pushes for the declared refspec are allowed.</p>
<span id=git.mirror.origin_checker href=#git.mirror.origin_checker>origin_checker</span> | <code><a href="#checker">checker</a></code> or <code>NoneType</code><br><p>Checker for applicable gerrit or github apis that can be inferred from the origin url. You can omit this if there no intention to use aforementioned APIs.</p>
<span id=git.mirror.destination_checker href=#git.mirror.destination_checker>destination_checker</span> | <code><a href="#checker">checker</a></code> or <code>NoneType</code><br><p>Checker for applicable gerrit or github apis that can be inferred from the destination url. You can omit this if there no intention to use aforementioned APIs.</p>
<span id=git.mirror.origin_credentials href=#git.mirror.origin_credentials>origin_credentials</span> | <code>UsernamePasswordIssuer</code> or <code>NoneType</code><br><p>EXPERIMENTAL: Read credentials from config file to access the Git Repo. This expects a 'credentials.username_password' specifying the username to use for the remote git host and a password or token. This is gated by the '--use-credentials-from-config' flag</p>
<span id=git.mirror.destination_credentials href=#git.mirror.destination_credentials>destination_credentials</span> | <code>UsernamePasswordIssuer</code> or <code>NoneType</code><br><p>EXPERIMENTAL: Read credentials from config file to access the Git Repo. This expects a 'credentials.username_password' specifying the username to use for the remote git host and a password or token. This is gated by the '--use-credentials-from-config' flag</p>

<a id="git.origin" aria-hidden="true"></a>
### git.origin

Defines a standard Git origin. For Git specific origins use: `github_origin` or `gerrit_origin`.<br><br>All the origins in this module accept several string formats as reference (When copybara is called in the form of `copybara config workflow reference`):<br><ul><li>**Branch name:** For example `master`</li><li>**An arbitrary reference:** `refs/changes/20/50820/1`</li><li>**A SHA-1:** Note that it has to be reachable from the default refspec</li><li>**A Git repository URL and reference:** `http://github.com/foo master`</li><li>**A GitHub pull request URL:** `https://github.com/some_project/pull/1784`</li></ul><br>So for example, Copybara can be invoked for a `git.origin` in the CLI as:<br>`copybara copy.bara.sky my_workflow https://github.com/some_project/pull/1784`<br>This will use the pull request as the origin URL and reference.

<code><a href="#origin">origin</a></code> <code>git.origin(<a href=#git.origin.url>url</a>, <a href=#git.origin.ref>ref</a>=None, <a href=#git.origin.submodules>submodules</a>='NO', <a href=#git.origin.excluded_submodules>excluded_submodules</a>=[], <a href=#git.origin.include_branch_commit_logs>include_branch_commit_logs</a>=False, <a href=#git.origin.first_parent>first_parent</a>=True, <a href=#git.origin.partial_fetch>partial_fetch</a>=False, <a href=#git.origin.patch>patch</a>=None, <a href=#git.origin.describe_version>describe_version</a>=None, <a href=#git.origin.version_selector>version_selector</a>=None, <a href=#git.origin.primary_branch_migration>primary_branch_migration</a>=False, <a href=#git.origin.credentials>credentials</a>=None, <a href=#git.origin.repo_id>repo_id</a>=None)</code>


<h4 id="parameters.git.origin">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.origin.url href=#git.origin.url>url</span> | <code><a href="#string">string</a></code><br><p>Indicates the URL of the git repository</p>
<span id=git.origin.ref href=#git.origin.ref>ref</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Represents the default reference that will be used for reading the revision from the git repository. For example: 'master'</p>
<span id=git.origin.submodules href=#git.origin.submodules>submodules</span> | <code><a href="#string">string</a></code><br><p>Download submodules. Valid values: NO, YES, RECURSIVE.</p>
<span id=git.origin.excluded_submodules href=#git.origin.excluded_submodules>excluded_submodules</span> | <code>sequence of <a href="#string">string</a></code><br><p>A list of names (not paths, e.g. "foo" is the submodule name if [submodule "foo"] appears in the .gitmodules file) of submodules that will not be download even if 'submodules' is set to YES or RECURSIVE. </p>
<span id=git.origin.include_branch_commit_logs href=#git.origin.include_branch_commit_logs>include_branch_commit_logs</span> | <code><a href="#bool">bool</a></code><br><p>Whether to include raw logs of branch commits in the migrated change message.WARNING: This field is deprecated in favor of 'first_parent' one. This setting *only* affects merge commits.</p>
<span id=git.origin.first_parent href=#git.origin.first_parent>first_parent</span> | <code><a href="#bool">bool</a></code><br><p>If true, it only uses the first parent when looking for changes. Note that when disabled in ITERATIVE mode, it will try to do a migration for each change of the merged branch.</p>
<span id=git.origin.partial_fetch href=#git.origin.partial_fetch>partial_fetch</span> | <code><a href="#bool">bool</a></code><br><p>If true, partially fetch git repository by only fetching affected files.</p>
<span id=git.origin.patch href=#git.origin.patch>patch</span> | <code><a href="#transformation">transformation</a></code> or <code>NoneType</code><br><p>Patch the checkout dir. The difference with `patch.apply` transformation is that here we can apply it using three-way</p>
<span id=git.origin.describe_version href=#git.origin.describe_version>describe_version</span> | <code><a href="#bool">bool</a></code> or <code>NoneType</code><br><p>Download tags and use 'git describe' to create four labels with a meaningful version identifier:<br><br>  - `GIT_DESCRIBE_CHANGE_VERSION`: The version for the change or changes being migrated. The value changes per change in `ITERATIVE` mode and will be the latest migrated change in `SQUASH` (In other words, doesn't include excluded changes). this is normally what users want to use.<br> - `GIT_DESCRIBE_REQUESTED_VERSION`: `git describe` for the requested/head version. Constant in `ITERATIVE` mode and includes filtered changes.<br>  -`GIT_DESCRIBE_FIRST_PARENT`: `git describe` for the first parent version.<br>  -`GIT_SEQUENTIAL_REVISION_NUMBER`: The sequential number of the commit. Falls back to the SHA1 if not applicable.<br></p>
<span id=git.origin.version_selector href=#git.origin.version_selector>version_selector</span> | <code><a href="#versionselector">VersionSelector</a></code> or <code>NoneType</code><br><p>Select a custom version (tag)to migrate instead of 'ref'. Version selector is expected to match the whole refspec (e.g. 'refs/heads/${n1}')</p>
<span id=git.origin.primary_branch_migration href=#git.origin.primary_branch_migration>primary_branch_migration</span> | <code><a href="#bool">bool</a></code><br><p>When enabled, copybara will ignore the 'ref' param if it is 'master' or 'main' and instead try to establish the default git branch. If this fails, it will fall back to the 'ref' param.<br>This is intended to help migrating to the new standard of using 'main' without breaking users relying on the legacy default.</p>
<span id=git.origin.credentials href=#git.origin.credentials>credentials</span> | <code>UsernamePasswordIssuer</code> or <code>NoneType</code><br><p>EXPERIMENTAL: Read credentials from config file to access the Git Repo. This expects a 'credentials.username_password' specifying the username to use for the remote git host and a password or token. This is gated by the '--use-credentials-from-config' flag</p>
<span id=git.origin.repo_id href=#git.origin.repo_id>repo_id</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>(Experimental) The repo id of the git repository, used as a stable reference to the repo for validation.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--git-fuzzy-last-rev`</span> | *boolean* | By default Copybara will try to migrate the revision listed as the version in the metadata file from github. This flag tells Copybara to first find the git tag which most closely matches the metadata version, and use that for the migration.
<span style="white-space: nowrap;">`--git-origin-log-batch`</span> | *int* | Read the origin git log in batches of n commits. Might be needed for large migrations resulting in git logs of more than 1 GB.
<span style="white-space: nowrap;">`--git-origin-non-linear-history`</span> | *boolean* | Read the full git log and skip changes before the from ref rather than using a log path.
<span style="white-space: nowrap;">`--git-origin-rebase-ref`</span> | *string* | When importing a change from a Git origin ref, it will be rebased to this ref, if set. A common use case: importing a Github PR, rebase it to the main branch (usually 'master'). Note that, if the repo uses submodules, they won't be rebased.
<span style="white-space: nowrap;">`--nogit-origin-version-selector`</span> | *boolean* | Disable the version selector for the migration. Only useful for forcing a migration to the passed version in the CLI

<a id="git.review_input" aria-hidden="true"></a>
### git.review_input

Creates a review to be posted on Gerrit.

<code><a href="#setreviewinput">SetReviewInput</a></code> <code>git.review_input(<a href=#git.review_input.labels>labels</a>={}, <a href=#git.review_input.message>message</a>=None, <a href=#git.review_input.tag>tag</a>=None, <a href=#git.review_input.notify>notify</a>='ALL')</code>


<h4 id="parameters.git.review_input">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.review_input.labels href=#git.review_input.labels>labels</span> | <code><a href="#dict">dict</a></code><br><p>The labels to post.</p>
<span id=git.review_input.message href=#git.review_input.message>message</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>The message to be added as review comment.</p>
<span id=git.review_input.tag href=#git.review_input.tag>tag</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Tag to be applied to the review, for instance 'autogenerated:copybara'.</p>
<span id=git.review_input.notify href=#git.review_input.notify>notify</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Notify setting, defaults to 'ALL'</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--force-gerrit-submit`</span> | *boolean* | Override the gerrit submit setting that is set in the config. This also flips the submit bit.
<span style="white-space: nowrap;">`--gerrit-change-id`</span> | *string* | ChangeId to use in the generated commit message. Use this flag if you want to reuse the same Gerrit review for an export.
<span style="white-space: nowrap;">`--gerrit-new-change`</span> | *boolean* | Create a new change instead of trying to reuse an existing one.
<span style="white-space: nowrap;">`--gerrit-topic`</span> | *string* | Gerrit topic to use



## git.mirrorContext

Expose methods to `git.mirror` actions to perform operations over git repositories


<h4 id="fields.git.mirrorContext">Fields:</h4>

Name | Description
---- | -----------
action_name | <code><a href="#string">string</a></code><br><p>The name of the current action.</p>
cli_labels | <code>dict[<a href="#string">string</a>, <a href="#string">string</a>]</code><br><p>Access labels that a user passes through flag '--labels'. For example: --labels=foo:value1,bar:value2. Then it can access in this way:cli_labels['foo'].</p>
console | <code>Console</code><br><p>Get an instance of the console to report errors or warnings</p>
destination_api | <code><a href="#endpoint">endpoint</a></code><br><p>Returns a handle to platform specific api, inferred from the destination url when possible.</p>
origin_api | <code><a href="#endpoint">endpoint</a></code><br><p>Returns a handle to platform specific api, inferred from the origin url when possible.</p>
params | <code><a href="#dict">dict</a></code><br><p>Parameters for the function if created with core.action</p>
refs | <code>sequence</code><br><p>A list containing string representations of the entities that triggered the event</p>

<a id="git.mirrorContext.cherry_pick" aria-hidden="true"></a>
### git.mirrorContext.cherry_pick

Cherry-pick one or more commits to a branch

<code><a href="#git_merge_result">git_merge_result</a></code> <code>git.mirrorContext.cherry_pick(<a href=#git.mirrorContext.cherry_pick.branch>branch</a>, <a href=#git.mirrorContext.cherry_pick.commits>commits</a>, <a href=#git.mirrorContext.cherry_pick.add_commit_origin_info>add_commit_origin_info</a>=True, <a href=#git.mirrorContext.cherry_pick.merge_parent_number>merge_parent_number</a>=None, <a href=#git.mirrorContext.cherry_pick.allow_empty>allow_empty</a>=False, <a href=#git.mirrorContext.cherry_pick.fast_forward>fast_forward</a>=False)</code>


<h4 id="parameters.git.mirrorContext.cherry_pick">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.mirrorContext.cherry_pick.branch href=#git.mirrorContext.cherry_pick.branch>branch</span> | <code><a href="#string">string</a></code><br><p></p>
<span id=git.mirrorContext.cherry_pick.commits href=#git.mirrorContext.cherry_pick.commits>commits</span> | <code>sequence of <a href="#string">string</a></code><br><p>Commits to cherry-pick. An expression like foo..bar can be used to cherry-pick several commits. Note that 'HEAD' will refer to the `branch` HEAD, since cherry-pick requires a checkout of the branch before cherry-picking.</p>
<span id=git.mirrorContext.cherry_pick.add_commit_origin_info href=#git.mirrorContext.cherry_pick.add_commit_origin_info>add_commit_origin_info</span> | <code><a href="#bool">bool</a></code><br><p>Add information about the origin of the commit (sha-1) to the message of the newcommit</p>
<span id=git.mirrorContext.cherry_pick.merge_parent_number href=#git.mirrorContext.cherry_pick.merge_parent_number>merge_parent_number</span> | <code>unknown</code><br><p>Specify the parent number for cherry-picking merge commits</p>
<span id=git.mirrorContext.cherry_pick.allow_empty href=#git.mirrorContext.cherry_pick.allow_empty>allow_empty</span> | <code><a href="#bool">bool</a></code><br><p>Allow empty commits (noop commits)</p>
<span id=git.mirrorContext.cherry_pick.fast_forward href=#git.mirrorContext.cherry_pick.fast_forward>fast_forward</span> | <code><a href="#bool">bool</a></code><br><p>Fast-forward commits if possible</p>

<a id="git.mirrorContext.create_branch" aria-hidden="true"></a>
### git.mirrorContext.create_branch

Merge one or more commits into a local branch.

<code>git.mirrorContext.create_branch(<a href=#git.mirrorContext.create_branch.name>name</a>, <a href=#git.mirrorContext.create_branch.starting_point>starting_point</a>=None)</code>


<h4 id="parameters.git.mirrorContext.create_branch">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.mirrorContext.create_branch.name href=#git.mirrorContext.create_branch.name>name</span> | <code><a href="#string">string</a></code><br><p></p>
<span id=git.mirrorContext.create_branch.starting_point href=#git.mirrorContext.create_branch.starting_point>starting_point</span> | <code>unknown</code><br><p></p>

<a id="git.mirrorContext.destination_fetch" aria-hidden="true"></a>
### git.mirrorContext.destination_fetch

Fetch from the destination a list of refspecs. Note that fetch happens without pruning.

<code><a href="#bool">bool</a></code> <code>git.mirrorContext.destination_fetch(<a href=#git.mirrorContext.destination_fetch.refspec>refspec</a>, <a href=#git.mirrorContext.destination_fetch.prune>prune</a>=True, <a href=#git.mirrorContext.destination_fetch.depth>depth</a>=None, <a href=#git.mirrorContext.destination_fetch.partial_fetch>partial_fetch</a>=False)</code>


<h4 id="parameters.git.mirrorContext.destination_fetch">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.mirrorContext.destination_fetch.refspec href=#git.mirrorContext.destination_fetch.refspec>refspec</span> | <code>sequence of <a href="#string">string</a></code><br><p></p>
<span id=git.mirrorContext.destination_fetch.prune href=#git.mirrorContext.destination_fetch.prune>prune</span> | <code><a href="#bool">bool</a></code><br><p></p>
<span id=git.mirrorContext.destination_fetch.depth href=#git.mirrorContext.destination_fetch.depth>depth</span> | <code><a href="#int">int</a></code> or <code>NoneType</code><br><p>Sets number of commits to fetch. Setting to None (the default) means no limit to that number.</p>
<span id=git.mirrorContext.destination_fetch.partial_fetch href=#git.mirrorContext.destination_fetch.partial_fetch>partial_fetch</span> | <code><a href="#bool">bool</a></code><br><p>If true, partially fetch only the minimum needed (e.g. don't fetch blobs if not used)</p>

<a id="git.mirrorContext.destination_push" aria-hidden="true"></a>
### git.mirrorContext.destination_push

Push to the destination a list of refspecs.

<code>git.mirrorContext.destination_push(<a href=#git.mirrorContext.destination_push.refspec>refspec</a>, <a href=#git.mirrorContext.destination_push.prune>prune</a>=False, <a href=#git.mirrorContext.destination_push.push_options>push_options</a>=[])</code>


<h4 id="parameters.git.mirrorContext.destination_push">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.mirrorContext.destination_push.refspec href=#git.mirrorContext.destination_push.refspec>refspec</span> | <code>sequence of <a href="#string">string</a></code><br><p></p>
<span id=git.mirrorContext.destination_push.prune href=#git.mirrorContext.destination_push.prune>prune</span> | <code><a href="#bool">bool</a></code><br><p></p>
<span id=git.mirrorContext.destination_push.push_options href=#git.mirrorContext.destination_push.push_options>push_options</span> | <code>sequence of <a href="#string">string</a></code><br><p>Additional push options to use with destination push</p>

<a id="git.mirrorContext.error" aria-hidden="true"></a>
### git.mirrorContext.error

Returns an error action result.

<code><a href="#dynamicaction_result">dynamic.action_result</a></code> <code>git.mirrorContext.error(<a href=#git.mirrorContext.error.msg>msg</a>)</code>


<h4 id="parameters.git.mirrorContext.error">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.mirrorContext.error.msg href=#git.mirrorContext.error.msg>msg</span> | <code><a href="#string">string</a></code><br><p>The error message</p>

<a id="git.mirrorContext.merge" aria-hidden="true"></a>
### git.mirrorContext.merge

Merge one or more commits into a local branch.

<code><a href="#git_merge_result">git_merge_result</a></code> <code>git.mirrorContext.merge(<a href=#git.mirrorContext.merge.branch>branch</a>, <a href=#git.mirrorContext.merge.commits>commits</a>, <a href=#git.mirrorContext.merge.msg>msg</a>=None, <a href=#git.mirrorContext.merge.fast_forward>fast_forward</a>="FF")</code>


<h4 id="parameters.git.mirrorContext.merge">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.mirrorContext.merge.branch href=#git.mirrorContext.merge.branch>branch</span> | <code><a href="#string">string</a></code><br><p></p>
<span id=git.mirrorContext.merge.commits href=#git.mirrorContext.merge.commits>commits</span> | <code>sequence of <a href="#string">string</a></code><br><p></p>
<span id=git.mirrorContext.merge.msg href=#git.mirrorContext.merge.msg>msg</span> | <code>unknown</code><br><p></p>
<span id=git.mirrorContext.merge.fast_forward href=#git.mirrorContext.merge.fast_forward>fast_forward</span> | <code><a href="#string">string</a></code><br><p>Valid values are FF (default), NO_FF, FF_ONLY.</p>

<a id="git.mirrorContext.noop" aria-hidden="true"></a>
### git.mirrorContext.noop

Returns a no op action result with an optional message.

<code><a href="#dynamicaction_result">dynamic.action_result</a></code> <code>git.mirrorContext.noop(<a href=#git.mirrorContext.noop.msg>msg</a>=None)</code>


<h4 id="parameters.git.mirrorContext.noop">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.mirrorContext.noop.msg href=#git.mirrorContext.noop.msg>msg</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>The no op message</p>

<a id="git.mirrorContext.origin_fetch" aria-hidden="true"></a>
### git.mirrorContext.origin_fetch

Fetch from the origin a list of refspecs. Note that fetch happens without pruning.

<code><a href="#bool">bool</a></code> <code>git.mirrorContext.origin_fetch(<a href=#git.mirrorContext.origin_fetch.refspec>refspec</a>, <a href=#git.mirrorContext.origin_fetch.prune>prune</a>=True, <a href=#git.mirrorContext.origin_fetch.depth>depth</a>=None, <a href=#git.mirrorContext.origin_fetch.partial_fetch>partial_fetch</a>=False)</code>


<h4 id="parameters.git.mirrorContext.origin_fetch">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.mirrorContext.origin_fetch.refspec href=#git.mirrorContext.origin_fetch.refspec>refspec</span> | <code>sequence of <a href="#string">string</a></code><br><p></p>
<span id=git.mirrorContext.origin_fetch.prune href=#git.mirrorContext.origin_fetch.prune>prune</span> | <code><a href="#bool">bool</a></code><br><p></p>
<span id=git.mirrorContext.origin_fetch.depth href=#git.mirrorContext.origin_fetch.depth>depth</span> | <code><a href="#int">int</a></code> or <code>NoneType</code><br><p>Sets number of commits to fetch. Setting to None (the default) means no limit to that number.</p>
<span id=git.mirrorContext.origin_fetch.partial_fetch href=#git.mirrorContext.origin_fetch.partial_fetch>partial_fetch</span> | <code><a href="#bool">bool</a></code><br><p>If true, partially fetch only the minimum needed (e.g. don't fetch blobs if not used)</p>

<a id="git.mirrorContext.rebase" aria-hidden="true"></a>
### git.mirrorContext.rebase

Rebase one or more commits into a local branch.

<code><a href="#git_merge_result">git_merge_result</a></code> <code>git.mirrorContext.rebase(<a href=#git.mirrorContext.rebase.upstream>upstream</a>, <a href=#git.mirrorContext.rebase.branch>branch</a>, <a href=#git.mirrorContext.rebase.newBase>newBase</a>=None, <a href=#git.mirrorContext.rebase.conflict_advice>conflict_advice</a>=None)</code>


<h4 id="parameters.git.mirrorContext.rebase">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.mirrorContext.rebase.upstream href=#git.mirrorContext.rebase.upstream>upstream</span> | <code><a href="#string">string</a></code><br><p>upstream branch with new changes</p>
<span id=git.mirrorContext.rebase.branch href=#git.mirrorContext.rebase.branch>branch</span> | <code><a href="#string">string</a></code><br><p>Current branch with specific commits that we want to rebase in top of the new `upstream` changes</p>
<span id=git.mirrorContext.rebase.newBase href=#git.mirrorContext.rebase.newBase>newBase</span> | <code>unknown</code><br><p>Move the rebased changes to a new branch (--into parameter in git rebase)</p>
<span id=git.mirrorContext.rebase.conflict_advice href=#git.mirrorContext.rebase.conflict_advice>conflict_advice</span> | <code>unknown</code><br><p>Additional information on how to solve the issue in case if conflict</p>

<a id="git.mirrorContext.record_effect" aria-hidden="true"></a>
### git.mirrorContext.record_effect

Records an effect of the current action.

<code>git.mirrorContext.record_effect(<a href=#git.mirrorContext.record_effect.summary>summary</a>, <a href=#git.mirrorContext.record_effect.origin_refs>origin_refs</a>, <a href=#git.mirrorContext.record_effect.destination_ref>destination_ref</a>, <a href=#git.mirrorContext.record_effect.errors>errors</a>=[], <a href=#git.mirrorContext.record_effect.type>type</a>="UPDATED")</code>


<h4 id="parameters.git.mirrorContext.record_effect">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.mirrorContext.record_effect.summary href=#git.mirrorContext.record_effect.summary>summary</span> | <code><a href="#string">string</a></code><br><p>The summary of this effect</p>
<span id=git.mirrorContext.record_effect.origin_refs href=#git.mirrorContext.record_effect.origin_refs>origin_refs</span> | <code>sequence of <a href="#origin_ref">origin_ref</a></code><br><p>The origin refs</p>
<span id=git.mirrorContext.record_effect.destination_ref href=#git.mirrorContext.record_effect.destination_ref>destination_ref</span> | <code><a href="#destination_ref">destination_ref</a></code><br><p>The destination ref</p>
<span id=git.mirrorContext.record_effect.errors href=#git.mirrorContext.record_effect.errors>errors</span> | <code>sequence of <a href="#string">string</a></code><br><p>An optional list of errors</p>
<span id=git.mirrorContext.record_effect.type href=#git.mirrorContext.record_effect.type>type</span> | <code><a href="#string">string</a></code><br><p>The type of migration effect:<br><ul><li><b>'CREATED'</b>: A new review or change was created.</li><li><b>'UPDATED'</b>: An existing review or change was updated.</li><li><b>'NOOP'</b>: The change was a noop.</li><li><b>'NOOP_AGAINST_PENDING_CHANGE'</b>: The change was a noop, relativeto an existing pending change.</li><li><b>'INSUFFICIENT_APPROVALS'</b>: The effect couldn't happen because the change doesn't have enough approvals.</li><li><b>'ERROR'</b>: A user attributable error happened that prevented the destination from creating/updating the change.</li><li><b>'STARTED'</b>: The initial effect of a migration that depends on a previous one. This allows to have 'dependant' migrations defined by users.<br>An example of this: a workflow migrates code from a Gerrit review to a GitHub PR, and a feedback migration migrates the test results from a CI in GitHub back to the Gerrit change.<br>This effect would be created on the former one.</li></ul></p>

<a id="git.mirrorContext.references" aria-hidden="true"></a>
### git.mirrorContext.references

Return a map of reference -> sha-1 for local references matching the refspec or all if no refspec is passed.

<code>dict[<a href="#string">string</a>, <a href="#string">string</a>]</code> <code>git.mirrorContext.references(<a href=#git.mirrorContext.references.refspec>refspec</a>=[])</code>


<h4 id="parameters.git.mirrorContext.references">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=git.mirrorContext.references.refspec href=#git.mirrorContext.references.refspec>refspec</span> | <code>sequence of <a href="#string">string</a></code><br><p></p>

<a id="git.mirrorContext.success" aria-hidden="true"></a>
### git.mirrorContext.success

Returns a successful action result.

<code><a href="#dynamicaction_result">dynamic.action_result</a></code> <code>git.mirrorContext.success()</code>



## git_merge_result

The result returned by git merge when used in Starlark. For example in git.mirror dynamic actions.


<h4 id="fields.git_merge_result">Fields:</h4>

Name | Description
---- | -----------
error | <code><a href="#bool">bool</a></code><br><p>True if the merge execution resulted in an error. False otherwise</p>
error_msg | <code><a href="#string">string</a></code><br><p>Error message from git if the merge resulted in a conflict/error. Users must check error field before accessing this field.</p>


<h4 id="returned_by.git_merge_result">Returned By:</h4>

<ul><li><a href="#git.mirrorContext.cherry_pick">git.mirrorContext.cherry_pick</a></li><li><a href="#git.mirrorContext.merge">git.mirrorContext.merge</a></li><li><a href="#git.mirrorContext.rebase">git.mirrorContext.rebase</a></li></ul>



## github_api_combined_status_obj

Combined Information about a commit status as defined in https://developer.github.com/v3/repos/statuses. This is a subset of the available fields in GitHub


<h4 id="fields.github_api_combined_status_obj">Fields:</h4>

Name | Description
---- | -----------
sha | <code><a href="#string">string</a></code><br><p>The SHA-1 of the commit</p>
state | <code><a href="#string">string</a></code><br><p>The overall state of all statuses for a commit: success, failure, pending or error</p>
statuses | <code>list of github_api_status_obj</code><br><p>List of statuses for the commit</p>
total_count | <code><a href="#int">int</a></code><br><p>Total number of statuses</p>


<h4 id="returned_by.github_api_combined_status_obj">Returned By:</h4>

<ul><li><a href="#github_api_obj.get_combined_status">github_api_obj.get_combined_status</a></li></ul>



## github_api_commit_author_obj

Author/Committer for commit field for GitHub commit information https://developer.github.com/v3/git/commits/#get-a-commit. This is a subset of the available fields in GitHub


<h4 id="fields.github_api_commit_author_obj">Fields:</h4>

Name | Description
---- | -----------
date | <code><a href="#string">string</a></code><br><p>Date of the commit</p>
email | <code><a href="#string">string</a></code><br><p>Email of the author/committer</p>
name | <code><a href="#string">string</a></code><br><p>Name of the author/committer</p>



## github_api_commit_obj

Commit field for GitHub commit information https://developer.github.com/v3/git/commits/#get-a-commit. This is a subset of the available fields in GitHub


<h4 id="fields.github_api_commit_obj">Fields:</h4>

Name | Description
---- | -----------
author | <code><a href="#github_api_commit_author_obj">github_api_commit_author_obj</a></code><br><p>Author of the commit</p>
committer | <code><a href="#github_api_commit_author_obj">github_api_commit_author_obj</a></code><br><p>Committer of the commit</p>
message | <code><a href="#string">string</a></code><br><p>Message of the commit</p>



## github_api_github_commit_obj

Information about a commit as defined in https://developer.github.com/v3/git/commits/#get-a-commit. This is a subset of the available fields in GitHub


<h4 id="fields.github_api_github_commit_obj">Fields:</h4>

Name | Description
---- | -----------
author | <code><a href="#github_api_user_obj">github_api_user_obj</a></code><br><p>GitHub information about the author of the change</p>
commit | <code><a href="#github_api_commit_obj">github_api_commit_obj</a></code><br><p>Information about the commit, like the message or git commit author/committer</p>
committer | <code><a href="#github_api_user_obj">github_api_user_obj</a></code><br><p>GitHub information about the committer of the change</p>
html_url | <code><a href="#string">string</a></code><br><p>GitHub url for the commit</p>
sha | <code><a href="#string">string</a></code><br><p>SHA of the commit</p>


<h4 id="returned_by.github_api_github_commit_obj">Returned By:</h4>

<ul><li><a href="#github_api_obj.get_commit">github_api_obj.get_commit</a></li></ul>



## github_api_issue_comment_obj

Information about an issue comment as defined in https://docs.github.com/en/rest/issues/comments. This is a subset of the available fields in GitHub


<h4 id="fields.github_api_issue_comment_obj">Fields:</h4>

Name | Description
---- | -----------
body | <code><a href="#string">string</a></code><br><p>Body of the comment</p>
id | <code>long</code><br><p>Comment identifier</p>
user | <code><a href="#github_api_user_obj">github_api_user_obj</a></code><br><p>Comment user</p>



## github_api_obj

GitHub API endpoint implementation for feedback migrations and after migration hooks.


<h4 id="fields.github_api_obj">Fields:</h4>

Name | Description
---- | -----------
url | <code><a href="#string">string</a></code><br><p>Return the URL of this endpoint.</p>

<a id="github_api_obj.add_label" aria-hidden="true"></a>
### github_api_obj.add_label

Add labels to a PR/issue

<code>github_api_obj.add_label(<a href=#github_api_obj.add_label.number>number</a>, <a href=#github_api_obj.add_label.labels>labels</a>)</code>


<h4 id="parameters.github_api_obj.add_label">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_api_obj.add_label.number href=#github_api_obj.add_label.number>number</span> | <code><a href="#int">int</a></code><br><p>Pull Request number</p>
<span id=github_api_obj.add_label.labels href=#github_api_obj.add_label.labels>labels</span> | <code>sequence of <a href="#string">string</a></code><br><p>List of labels to add.</p>

<a id="github_api_obj.create_issue" aria-hidden="true"></a>
### github_api_obj.create_issue

Create a new issue.

<code><a href="#issue">Issue</a></code> <code>github_api_obj.create_issue(<a href=#github_api_obj.create_issue.title>title</a>, <a href=#github_api_obj.create_issue.body>body</a>, <a href=#github_api_obj.create_issue.assignees>assignees</a>)</code>


<h4 id="parameters.github_api_obj.create_issue">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_api_obj.create_issue.title href=#github_api_obj.create_issue.title>title</span> | <code><a href="#string">string</a></code><br><p>Title of the issue</p>
<span id=github_api_obj.create_issue.body href=#github_api_obj.create_issue.body>body</span> | <code><a href="#string">string</a></code><br><p>Body of the issue.</p>
<span id=github_api_obj.create_issue.assignees href=#github_api_obj.create_issue.assignees>assignees</span> | <code>sequence</code><br><p>GitHub users to whom the issue will be assigned.</p>

<a id="github_api_obj.create_release" aria-hidden="true"></a>
### github_api_obj.create_release

Create a new GitHub release.

<code><a href="#github_release_obj">github_release_obj</a></code> <code>github_api_obj.create_release(<a href=#github_api_obj.create_release.request>request</a>)</code>


<h4 id="parameters.github_api_obj.create_release">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_api_obj.create_release.request href=#github_api_obj.create_release.request>request</span> | <code><a href="#github_create_release_obj">github_create_release_obj</a></code><br><p>The populated release object. See new_release_request.</p>

<a id="github_api_obj.create_status" aria-hidden="true"></a>
### github_api_obj.create_status

Create or update a status for a commit. Returns the status created.

<code><a href="#github_api_status_obj">github_api_status_obj</a></code> <code>github_api_obj.create_status(<a href=#github_api_obj.create_status.sha>sha</a>, <a href=#github_api_obj.create_status.state>state</a>, <a href=#github_api_obj.create_status.context>context</a>, <a href=#github_api_obj.create_status.description>description</a>, <a href=#github_api_obj.create_status.target_url>target_url</a>=None)</code>


<h4 id="parameters.github_api_obj.create_status">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_api_obj.create_status.sha href=#github_api_obj.create_status.sha>sha</span> | <code><a href="#string">string</a></code><br><p>The SHA-1 for which we want to create or update the status</p>
<span id=github_api_obj.create_status.state href=#github_api_obj.create_status.state>state</span> | <code><a href="#string">string</a></code><br><p>The state of the commit status: 'success', 'error', 'pending' or 'failure'</p>
<span id=github_api_obj.create_status.context href=#github_api_obj.create_status.context>context</span> | <code><a href="#string">string</a></code><br><p>The context for the commit status. Use a value like 'copybara/import_successful' or similar</p>
<span id=github_api_obj.create_status.description href=#github_api_obj.create_status.description>description</span> | <code><a href="#string">string</a></code><br><p>Description about what happened</p>
<span id=github_api_obj.create_status.target_url href=#github_api_obj.create_status.target_url>target_url</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Url with expanded information about the event</p>

<a id="github_api_obj.delete_reference" aria-hidden="true"></a>
### github_api_obj.delete_reference

Delete a reference.

<code>github_api_obj.delete_reference(<a href=#github_api_obj.delete_reference.ref>ref</a>)</code>


<h4 id="parameters.github_api_obj.delete_reference">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_api_obj.delete_reference.ref href=#github_api_obj.delete_reference.ref>ref</span> | <code><a href="#string">string</a></code><br><p>The name of the reference.</p>

<a id="github_api_obj.get_authenticated_user" aria-hidden="true"></a>
### github_api_obj.get_authenticated_user

Get autenticated user info, return null if not found

<code><a href="#github_api_user_obj">github_api_user_obj</a></code> <code>github_api_obj.get_authenticated_user()</code>

<a id="github_api_obj.get_check_runs" aria-hidden="true"></a>
### github_api_obj.get_check_runs

Get the list of check runs for a sha. https://developer.github.com/v3/checks/runs/#check-runs

<code>list of github_check_run_obj</code> <code>github_api_obj.get_check_runs(<a href=#github_api_obj.get_check_runs.sha>sha</a>)</code>


<h4 id="parameters.github_api_obj.get_check_runs">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_api_obj.get_check_runs.sha href=#github_api_obj.get_check_runs.sha>sha</span> | <code><a href="#string">string</a></code><br><p>The SHA-1 for which we want to get the check runs</p>

<a id="github_api_obj.get_combined_status" aria-hidden="true"></a>
### github_api_obj.get_combined_status

Get the combined status for a commit. Returns None if not found.

<code><a href="#github_api_combined_status_obj">github_api_combined_status_obj</a></code> <code>github_api_obj.get_combined_status(<a href=#github_api_obj.get_combined_status.ref>ref</a>)</code>


<h4 id="parameters.github_api_obj.get_combined_status">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_api_obj.get_combined_status.ref href=#github_api_obj.get_combined_status.ref>ref</span> | <code><a href="#string">string</a></code><br><p>The SHA-1 or ref for which we want to get the combined status</p>

<a id="github_api_obj.get_commit" aria-hidden="true"></a>
### github_api_obj.get_commit

Get information for a commit in GitHub. Returns None if not found.

<code><a href="#github_api_github_commit_obj">github_api_github_commit_obj</a></code> <code>github_api_obj.get_commit(<a href=#github_api_obj.get_commit.ref>ref</a>)</code>


<h4 id="parameters.github_api_obj.get_commit">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_api_obj.get_commit.ref href=#github_api_obj.get_commit.ref>ref</span> | <code><a href="#string">string</a></code><br><p>The SHA-1 for which we want to get the combined status</p>

<a id="github_api_obj.get_pull_request_comment" aria-hidden="true"></a>
### github_api_obj.get_pull_request_comment

Get a pull request comment

<code><a href="#github_api_pull_request_comment_obj">github_api_pull_request_comment_obj</a></code> <code>github_api_obj.get_pull_request_comment(<a href=#github_api_obj.get_pull_request_comment.comment_id>comment_id</a>)</code>


<h4 id="parameters.github_api_obj.get_pull_request_comment">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_api_obj.get_pull_request_comment.comment_id href=#github_api_obj.get_pull_request_comment.comment_id>comment_id</span> | <code><a href="#string">string</a></code><br><p>Comment identifier</p>

<a id="github_api_obj.get_pull_request_comments" aria-hidden="true"></a>
### github_api_obj.get_pull_request_comments

Get all pull request comments

<code>list of github_api_pull_request_comment_obj</code> <code>github_api_obj.get_pull_request_comments(<a href=#github_api_obj.get_pull_request_comments.number>number</a>)</code>


<h4 id="parameters.github_api_obj.get_pull_request_comments">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_api_obj.get_pull_request_comments.number href=#github_api_obj.get_pull_request_comments.number>number</span> | <code><a href="#int">int</a></code><br><p>Pull Request number</p>

<a id="github_api_obj.get_pull_requests" aria-hidden="true"></a>
### github_api_obj.get_pull_requests

Get Pull Requests for a repo

<code>list of github_api_pull_request_obj</code> <code>github_api_obj.get_pull_requests(<a href=#github_api_obj.get_pull_requests.head_prefix>head_prefix</a>=None, <a href=#github_api_obj.get_pull_requests.base_prefix>base_prefix</a>=None, <a href=#github_api_obj.get_pull_requests.state>state</a>="OPEN", <a href=#github_api_obj.get_pull_requests.sort>sort</a>="CREATED", <a href=#github_api_obj.get_pull_requests.direction>direction</a>="ASC")</code>


<h4 id="parameters.github_api_obj.get_pull_requests">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_api_obj.get_pull_requests.head_prefix href=#github_api_obj.get_pull_requests.head_prefix>head_prefix</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Only return PRs wher the branch name has head_prefix</p>
<span id=github_api_obj.get_pull_requests.base_prefix href=#github_api_obj.get_pull_requests.base_prefix>base_prefix</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Only return PRs where the destination branch name has base_prefix</p>
<span id=github_api_obj.get_pull_requests.state href=#github_api_obj.get_pull_requests.state>state</span> | <code><a href="#string">string</a></code><br><p>State of the Pull Request. Can be `"OPEN"`, `"CLOSED"` or `"ALL"`</p>
<span id=github_api_obj.get_pull_requests.sort href=#github_api_obj.get_pull_requests.sort>sort</span> | <code><a href="#string">string</a></code><br><p>Sort filter for retrieving the Pull Requests. Can be `"CREATED"`, `"UPDATED"` or `"POPULARITY"`</p>
<span id=github_api_obj.get_pull_requests.direction href=#github_api_obj.get_pull_requests.direction>direction</span> | <code><a href="#string">string</a></code><br><p>Direction of the filter. Can be `"ASC"` or `"DESC"`</p>

<a id="github_api_obj.get_reference" aria-hidden="true"></a>
### github_api_obj.get_reference

Get a reference SHA-1 from GitHub. Returns None if not found.

<code><a href="#github_api_ref_obj">github_api_ref_obj</a></code> <code>github_api_obj.get_reference(<a href=#github_api_obj.get_reference.ref>ref</a>)</code>


<h4 id="parameters.github_api_obj.get_reference">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_api_obj.get_reference.ref href=#github_api_obj.get_reference.ref>ref</span> | <code><a href="#string">string</a></code><br><p>The name of the reference. For example: "refs/heads/branchName".</p>

<a id="github_api_obj.get_references" aria-hidden="true"></a>
### github_api_obj.get_references

Get all the reference SHA-1s from GitHub. Note that Copybara only returns a maximum number of 500.

<code>list of github_api_ref_obj</code> <code>github_api_obj.get_references()</code>

<a id="github_api_obj.list_issue_comments" aria-hidden="true"></a>
### github_api_obj.list_issue_comments

Lists comments for an issue

<code>list of github_api_issue_comment_obj</code> <code>github_api_obj.list_issue_comments(<a href=#github_api_obj.list_issue_comments.number>number</a>)</code>


<h4 id="parameters.github_api_obj.list_issue_comments">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_api_obj.list_issue_comments.number href=#github_api_obj.list_issue_comments.number>number</span> | <code><a href="#int">int</a></code><br><p>Issue or Pull Request number</p>

<a id="github_api_obj.new_destination_ref" aria-hidden="true"></a>
### github_api_obj.new_destination_ref

Creates a new destination reference out of this endpoint.

<code><a href="#destination_ref">destination_ref</a></code> <code>github_api_obj.new_destination_ref(<a href=#github_api_obj.new_destination_ref.ref>ref</a>, <a href=#github_api_obj.new_destination_ref.type>type</a>, <a href=#github_api_obj.new_destination_ref.url>url</a>=None)</code>


<h4 id="parameters.github_api_obj.new_destination_ref">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_api_obj.new_destination_ref.ref href=#github_api_obj.new_destination_ref.ref>ref</span> | <code><a href="#string">string</a></code><br><p>The reference.</p>
<span id=github_api_obj.new_destination_ref.type href=#github_api_obj.new_destination_ref.type>type</span> | <code><a href="#string">string</a></code><br><p>The type of this reference.</p>
<span id=github_api_obj.new_destination_ref.url href=#github_api_obj.new_destination_ref.url>url</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>The url associated with this reference, if any.</p>

<a id="github_api_obj.new_origin_ref" aria-hidden="true"></a>
### github_api_obj.new_origin_ref

Creates a new origin reference out of this endpoint.

<code><a href="#origin_ref">origin_ref</a></code> <code>github_api_obj.new_origin_ref(<a href=#github_api_obj.new_origin_ref.ref>ref</a>)</code>


<h4 id="parameters.github_api_obj.new_origin_ref">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_api_obj.new_origin_ref.ref href=#github_api_obj.new_origin_ref.ref>ref</span> | <code><a href="#string">string</a></code><br><p>The reference.</p>

<a id="github_api_obj.new_release_request" aria-hidden="true"></a>
### github_api_obj.new_release_request

Create a handle for creating a new release.

<code><a href="#github_create_release_obj">github_create_release_obj</a></code> <code>github_api_obj.new_release_request(<a href=#github_api_obj.new_release_request.tag_name>tag_name</a>)</code>


<h4 id="parameters.github_api_obj.new_release_request">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_api_obj.new_release_request.tag_name href=#github_api_obj.new_release_request.tag_name>tag_name</span> | <code><a href="#string">string</a></code><br><p>The git tag to use for the release.</p>


<h4 id="example.github_api_obj.new_release_request">Example:</h4>


##### Create a new release request.:

After uploading a new commit

```python
endpoint.new_release_request(tag_name='v1.0.2').with_name('1.0.2')
```


<a id="github_api_obj.post_issue_comment" aria-hidden="true"></a>
### github_api_obj.post_issue_comment

Post a comment on a issue.

<code>github_api_obj.post_issue_comment(<a href=#github_api_obj.post_issue_comment.number>number</a>, <a href=#github_api_obj.post_issue_comment.comment>comment</a>)</code>


<h4 id="parameters.github_api_obj.post_issue_comment">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_api_obj.post_issue_comment.number href=#github_api_obj.post_issue_comment.number>number</span> | <code><a href="#int">int</a></code><br><p>Issue or Pull Request number</p>
<span id=github_api_obj.post_issue_comment.comment href=#github_api_obj.post_issue_comment.comment>comment</span> | <code><a href="#string">string</a></code><br><p>Comment body to post.</p>

<a id="github_api_obj.update_pull_request" aria-hidden="true"></a>
### github_api_obj.update_pull_request

Update Pull Requests for a repo. Returns None if not found

<code><a href="#github_api_pull_request_obj">github_api_pull_request_obj</a></code> <code>github_api_obj.update_pull_request(<a href=#github_api_obj.update_pull_request.number>number</a>, <a href=#github_api_obj.update_pull_request.title>title</a>=None, <a href=#github_api_obj.update_pull_request.body>body</a>=None, <a href=#github_api_obj.update_pull_request.state>state</a>=None)</code>


<h4 id="parameters.github_api_obj.update_pull_request">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_api_obj.update_pull_request.number href=#github_api_obj.update_pull_request.number>number</span> | <code><a href="#int">int</a></code><br><p>Pull Request number</p>
<span id=github_api_obj.update_pull_request.title href=#github_api_obj.update_pull_request.title>title</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>New Pull Request title</p>
<span id=github_api_obj.update_pull_request.body href=#github_api_obj.update_pull_request.body>body</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>New Pull Request body</p>
<span id=github_api_obj.update_pull_request.state href=#github_api_obj.update_pull_request.state>state</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>State of the Pull Request. Can be `"OPEN"`, `"CLOSED"`</p>

<a id="github_api_obj.update_reference" aria-hidden="true"></a>
### github_api_obj.update_reference

Update a reference to point to a new commit. Returns the info of the reference.

<code><a href="#github_api_ref_obj">github_api_ref_obj</a></code> <code>github_api_obj.update_reference(<a href=#github_api_obj.update_reference.ref>ref</a>, <a href=#github_api_obj.update_reference.sha>sha</a>, <a href=#github_api_obj.update_reference.force>force</a>)</code>


<h4 id="parameters.github_api_obj.update_reference">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_api_obj.update_reference.ref href=#github_api_obj.update_reference.ref>ref</span> | <code><a href="#string">string</a></code><br><p>The name of the reference.</p>
<span id=github_api_obj.update_reference.sha href=#github_api_obj.update_reference.sha>sha</span> | <code><a href="#string">string</a></code><br><p>The id for the commit status.</p>
<span id=github_api_obj.update_reference.force href=#github_api_obj.update_reference.force>force</span> | <code><a href="#bool">bool</a></code><br><p>Indicates whether to force the update or to make sure the update is a fast-forward update. Leaving this out or setting it to false will make sure you're not overwriting work. Default: false</p>



## github_api_pull_request_comment_obj

Information about a pull request comment as defined in https://developer.github.com/v3/pulls/comments/. This is a subset of the available fields in GitHub


<h4 id="fields.github_api_pull_request_comment_obj">Fields:</h4>

Name | Description
---- | -----------
body | <code><a href="#string">string</a></code><br><p>Body of the comment</p>
diff_hunk | <code><a href="#string">string</a></code><br><p>The diff hunk where the comment was posted</p>
id | <code><a href="#string">string</a></code><br><p>Comment identifier</p>
original_position | <code><a href="#int">int</a></code><br><p>Original position of the comment</p>
path | <code><a href="#string">string</a></code><br><p>The file path</p>
position | <code><a href="#int">int</a></code><br><p>Position of the comment</p>
user | <code><a href="#github_api_user_obj">github_api_user_obj</a></code><br><p>The user who posted the comment</p>


<h4 id="returned_by.github_api_pull_request_comment_obj">Returned By:</h4>

<ul><li><a href="#github_api_obj.get_pull_request_comment">github_api_obj.get_pull_request_comment</a></li></ul>



## github_api_pull_request_obj

Information about a pull request as defined in https://docs.github.com/en/rest/reference/pulls. This is a subset of the available fields in GitHub


<h4 id="fields.github_api_pull_request_obj">Fields:</h4>

Name | Description
---- | -----------
assignee | <code><a href="#github_api_user_obj">github_api_user_obj</a></code><br><p>Pull Request assignee</p>
base | <code><a href="#github_api_revision_obj">github_api_revision_obj</a></code><br><p>Information about base</p>
body | <code><a href="#string">string</a></code><br><p>Pull Request body</p>
commits | <code><a href="#int">int</a></code><br><p>Number of commits in the PR</p>
draft | <code><a href="#bool">bool</a></code><br><p>Whether pull request is a draft</p>
head | <code><a href="#github_api_revision_obj">github_api_revision_obj</a></code><br><p>Information about head</p>
merged | <code><a href="#bool">bool</a></code><br><p>Whether pull request has been merged</p>
number | <code><a href="#int">int</a></code><br><p>Pull Request number</p>
state | <code><a href="#string">string</a></code><br><p>Pull Request state</p>
title | <code><a href="#string">string</a></code><br><p>Pull Request title</p>
user | <code><a href="#github_api_user_obj">github_api_user_obj</a></code><br><p>Pull Request owner</p>


<h4 id="returned_by.github_api_pull_request_obj">Returned By:</h4>

<ul><li><a href="#github_api_obj.update_pull_request">github_api_obj.update_pull_request</a></li></ul>



## github_api_ref_obj

Information about a commit status as defined in https://developer.github.com/v3/repos/statuses. This is a subset of the available fields in GitHub


<h4 id="fields.github_api_ref_obj">Fields:</h4>

Name | Description
---- | -----------
ref | <code><a href="#string">string</a></code><br><p>The name of the reference</p>
sha | <code><a href="#string">string</a></code><br><p>The sha of the reference</p>
url | <code><a href="#string">string</a></code><br><p>The url of the reference</p>


<h4 id="returned_by.github_api_ref_obj">Returned By:</h4>

<ul><li><a href="#github_api_obj.get_reference">github_api_obj.get_reference</a></li><li><a href="#github_api_obj.update_reference">github_api_obj.update_reference</a></li></ul>



## github_api_revision_obj

Information about a GitHub revision (Used in Pull Request and other entities)


<h4 id="fields.github_api_revision_obj">Fields:</h4>

Name | Description
---- | -----------
label | <code><a href="#string">string</a></code><br><p>Label for the revision</p>
ref | <code><a href="#string">string</a></code><br><p>Reference</p>
repo | <code>Repository</code><br><p>Repository</p>
sha | <code><a href="#string">string</a></code><br><p>SHA of the reference</p>



## github_api_status_obj

Information about a commit status as defined in https://developer.github.com/v3/repos/statuses. This is a subset of the available fields in GitHub


<h4 id="fields.github_api_status_obj">Fields:</h4>

Name | Description
---- | -----------
context | <code><a href="#string">string</a></code><br><p>Context of the commit status. This is a relatively stable id</p>
description | <code><a href="#string">string</a></code><br><p>Description of the commit status. Can be None.</p>
state | <code><a href="#string">string</a></code><br><p>The state of the commit status: success, failure, pending or error</p>
target_url | <code><a href="#string">string</a></code><br><p>Get the target url of the commit status. Can be None.</p>


<h4 id="returned_by.github_api_status_obj">Returned By:</h4>

<ul><li><a href="#github_api_obj.create_status">github_api_obj.create_status</a></li></ul>



## github_api_user_obj

An object representing a GitHub user


<h4 id="fields.github_api_user_obj">Fields:</h4>

Name | Description
---- | -----------
login | <code><a href="#string">string</a></code><br><p>Login of the user</p>


<h4 id="returned_by.github_api_user_obj">Returned By:</h4>

<ul><li><a href="#github_api_obj.get_authenticated_user">github_api_obj.get_authenticated_user</a></li></ul>



## github_app_obj

Detail about a GitHub App.


<h4 id="fields.github_app_obj">Fields:</h4>

Name | Description
---- | -----------
id | <code><a href="#int">int</a></code><br><p>The GitHub App's Id</p>
name | <code><a href="#string">string</a></code><br><p>The GitHub App's name</p>
slug | <code><a href="#string">string</a></code><br><p>The url-friendly name of the GitHub App.</p>



## github_check_run_obj

Detail about a check run as defined in https://developer.github.com/v3/checks/runs/#create-a-check-run


<h4 id="fields.github_check_run_obj">Fields:</h4>

Name | Description
---- | -----------
app | <code><a href="#github_app_obj">github_app_obj</a></code><br><p>The detail of a GitHub App, such as id, slug, and name</p>
conclusion | <code><a href="#string">string</a></code><br><p>The final conclusion of the check. Can be one of success, failure, neutral, cancelled, timed_out, or action_required.</p>
detail_url | <code><a href="#string">string</a></code><br><p>The URL of the integrator's site that has the full details of the check.</p>
name | <code><a href="#string">string</a></code><br><p>The name of the check</p>
output | <code><a href="#output_obj">output_obj</a></code><br><p>The description of a GitHub App's run, including title, summary, text.</p>
pulls | <code>list of PullRequest</code><br><p>Pull requests associated with this check_run ('number' only)</p>
sha | <code><a href="#string">string</a></code><br><p>The SHA-1 the check run is based on</p>
status | <code><a href="#string">string</a></code><br><p>The current status of the check run. Can be one of queued, in_progress, or completed.</p>



## github_check_runs_obj

List check runs for a specific ref https://developer.github.com/v3/checks/runs/#list-check-runs-for-a-specific-ref


<h4 id="fields.github_check_runs_obj">Fields:</h4>

Name | Description
---- | -----------
check_runs | <code>list of github_check_run_obj</code><br><p>The list of the detail for each check run.</p>
total_count | <code><a href="#int">int</a></code><br><p>The total count of check runs.</p>



## github_check_suite_obj

Detail about a check run as defined in https://developer.github.com/v3/checks/runs/#create-a-check-run


<h4 id="fields.github_check_suite_obj">Fields:</h4>

Name | Description
---- | -----------
app | <code><a href="#github_app_obj">github_app_obj</a></code><br><p>The detail of a GitHub App, such as id, slug, and name</p>
conclusion | <code><a href="#string">string</a></code><br><p>The final conclusion of the check. Can be one of success, failure, neutral, cancelled, timed_out, or action_required.</p>
id | <code><a href="#int">int</a></code><br><p>Check suite identifier</p>
sha | <code><a href="#string">string</a></code><br><p>The SHA-1 the check run is based on</p>
status | <code><a href="#string">string</a></code><br><p>The current status of the check run. Can be one of queued, in_progress, pending, or completed.</p>



## github_check_suites_response_obj

Detail about a check run as defined in https://docs.github.com/en/rest/checks/suites?apiVersion=2022-11-28#list-check-suites-for-a-git-reference



## github_create_release_obj

GitHub API value type for release params. See https://docs.github.com/en/rest/releases/releases?apiVersion=2022-11-28#create-a-release


<h4 id="returned_by.github_create_release_obj">Returned By:</h4>

<ul><li><a href="#github_api_obj.new_release_request">github_api_obj.new_release_request</a></li><li><a href="#github_create_release_obj.set_draft">github_create_release_obj.set_draft</a></li><li><a href="#github_create_release_obj.set_generate_release_notes">github_create_release_obj.set_generate_release_notes</a></li><li><a href="#github_create_release_obj.set_latest">github_create_release_obj.set_latest</a></li><li><a href="#github_create_release_obj.set_prerelease">github_create_release_obj.set_prerelease</a></li><li><a href="#github_create_release_obj.with_body">github_create_release_obj.with_body</a></li><li><a href="#github_create_release_obj.with_commitish">github_create_release_obj.with_commitish</a></li><li><a href="#github_create_release_obj.with_name">github_create_release_obj.with_name</a></li></ul>
<h4 id="consumed_by.github_create_release_obj">Consumed By:</h4>

<ul><li><a href="#github_api_obj.create_release">github_api_obj.create_release</a></li></ul>

<a id="github_create_release_obj.set_draft" aria-hidden="true"></a>
### github_create_release_obj.set_draft

Is this a draft release?

<code><a href="#github_create_release_obj">github_create_release_obj</a></code> <code>github_create_release_obj.set_draft(<a href=#github_create_release_obj.set_draft.draft>draft</a>)</code>


<h4 id="parameters.github_create_release_obj.set_draft">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_create_release_obj.set_draft.draft href=#github_create_release_obj.set_draft.draft>draft</span> | <code><a href="#bool">bool</a></code><br><p>Mark release as draft?</p>

<a id="github_create_release_obj.set_generate_release_notes" aria-hidden="true"></a>
### github_create_release_obj.set_generate_release_notes

Generate release notes?

<code><a href="#github_create_release_obj">github_create_release_obj</a></code> <code>github_create_release_obj.set_generate_release_notes(<a href=#github_create_release_obj.set_generate_release_notes.generate_notes>generate_notes</a>)</code>


<h4 id="parameters.github_create_release_obj.set_generate_release_notes">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_create_release_obj.set_generate_release_notes.generate_notes href=#github_create_release_obj.set_generate_release_notes.generate_notes>generate_notes</span> | <code><a href="#bool">bool</a></code><br><p>Generate notes?</p>

<a id="github_create_release_obj.set_latest" aria-hidden="true"></a>
### github_create_release_obj.set_latest

Is this the latest release?

<code><a href="#github_create_release_obj">github_create_release_obj</a></code> <code>github_create_release_obj.set_latest(<a href=#github_create_release_obj.set_latest.make_latest>make_latest</a>)</code>


<h4 id="parameters.github_create_release_obj.set_latest">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_create_release_obj.set_latest.make_latest href=#github_create_release_obj.set_latest.make_latest>make_latest</span> | <code><a href="#bool">bool</a></code><br><p>Mark release as latest?</p>

<a id="github_create_release_obj.set_prerelease" aria-hidden="true"></a>
### github_create_release_obj.set_prerelease

Is this a prerelease?

<code><a href="#github_create_release_obj">github_create_release_obj</a></code> <code>github_create_release_obj.set_prerelease(<a href=#github_create_release_obj.set_prerelease.prerelease>prerelease</a>)</code>


<h4 id="parameters.github_create_release_obj.set_prerelease">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_create_release_obj.set_prerelease.prerelease href=#github_create_release_obj.set_prerelease.prerelease>prerelease</span> | <code><a href="#bool">bool</a></code><br><p>Mark release as prerelease?</p>

<a id="github_create_release_obj.with_body" aria-hidden="true"></a>
### github_create_release_obj.with_body

Set the body for the release.

<code><a href="#github_create_release_obj">github_create_release_obj</a></code> <code>github_create_release_obj.with_body(<a href=#github_create_release_obj.with_body.body>body</a>)</code>


<h4 id="parameters.github_create_release_obj.with_body">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_create_release_obj.with_body.body href=#github_create_release_obj.with_body.body>body</span> | <code><a href="#string">string</a></code><br><p>Body for the release</p>

<a id="github_create_release_obj.with_commitish" aria-hidden="true"></a>
### github_create_release_obj.with_commitish

Set the commitish to be used for the release. Defaults to HEAD

<code><a href="#github_create_release_obj">github_create_release_obj</a></code> <code>github_create_release_obj.with_commitish(<a href=#github_create_release_obj.with_commitish.commitish>commitish</a>)</code>


<h4 id="parameters.github_create_release_obj.with_commitish">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_create_release_obj.with_commitish.commitish href=#github_create_release_obj.with_commitish.commitish>commitish</span> | <code><a href="#string">string</a></code><br><p>Commitish for the release</p>

<a id="github_create_release_obj.with_name" aria-hidden="true"></a>
### github_create_release_obj.with_name

Set the name for the release.

<code><a href="#github_create_release_obj">github_create_release_obj</a></code> <code>github_create_release_obj.with_name(<a href=#github_create_release_obj.with_name.name>name</a>)</code>


<h4 id="parameters.github_create_release_obj.with_name">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=github_create_release_obj.with_name.name href=#github_create_release_obj.with_name.name>name</span> | <code><a href="#string">string</a></code><br><p>Name for the release</p>



## github_release_obj

GitHub API value type for a release. See https://docs.github.com/en/rest/releases/releases?apiVersion=2022-11-28#create-a-release


<h4 id="fields.github_release_obj">Fields:</h4>

Name | Description
---- | -----------
id | <code><a href="#int">int</a></code><br><p>Release id</p>
tarball | <code><a href="#string">string</a></code><br><p>Tarball Url</p>
zip | <code><a href="#string">string</a></code><br><p>Zip Url</p>


<h4 id="returned_by.github_release_obj">Returned By:</h4>

<ul><li><a href="#github_api_obj.create_release">github_api_obj.create_release</a></li></ul>



## glob

A glob represents a set of relative filepaths in the Copybara workdir. Most consumers will also accept a list of fully qualified (no wildcards) file names instead.


<h4 id="returned_by.glob">Returned By:</h4>

<ul><li><a href="#glob">glob</a></li></ul>
<h4 id="consumed_by.glob">Consumed By:</h4>

<ul><li><a href="#archive.create">archive.create</a></li><li><a href="#archive.extract">archive.extract</a></li><li><a href="#compression.unzip_path">compression.unzip_path</a></li><li><a href="#core.autopatch_config">core.autopatch_config</a></li><li><a href="#core.convert_encoding">core.convert_encoding</a></li><li><a href="#core.copy">core.copy</a></li><li><a href="#core.filter_replace">core.filter_replace</a></li><li><a href="#core.merge_import_config">core.merge_import_config</a></li><li><a href="#core.move">core.move</a></li><li><a href="#core.remove">core.remove</a></li><li><a href="#core.rename">core.rename</a></li><li><a href="#core.replace">core.replace</a></li><li><a href="#core.todo_replace">core.todo_replace</a></li><li><a href="#core.verify_match">core.verify_match</a></li><li><a href="#core.workflow">core.workflow</a></li><li><a href="#destination_reader.copy_destination_files">destination_reader.copy_destination_files</a></li><li><a href="#format.buildifier">format.buildifier</a></li><li><a href="#ctx.list">ctx.list</a></li><li><a href="#ctx.run">ctx.run</a></li></ul>



## Globals

Global functions available in Copybara

<a id="abs" aria-hidden="true"></a>
### abs

Returns the absolute value of a number (a non-negative number with the same magnitude).<pre class="language-python">abs(-2.3) == 2.3</pre>

<code>unknown</code> <code>abs(<a href=#abs.x>x</a>)</code>


<h4 id="parameters.abs">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=abs.x href=#abs.x>x</span> | <code><a href="#int">int</a></code> or <code><a href="#float">float</a></code><br><p>A number (int or float)</p>

<a id="all" aria-hidden="true"></a>
### all

Returns true if all elements evaluate to True or if the collection is empty. Elements are converted to boolean using the <a href="#bool">bool</a> function.<pre class="language-python">all(["hello", 3, True]) == True
all([-1, 0, 1]) == False</pre>

<code><a href="#bool">bool</a></code> <code>all(<a href=#all.elements>elements</a>)</code>


<h4 id="parameters.all">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=all.elements href=#all.elements>elements</span> | <code>sequence</code><br><p>A collection of elements.</p>

<a id="any" aria-hidden="true"></a>
### any

Returns true if at least one element evaluates to True. Elements are converted to boolean using the <a href="#bool">bool</a> function.<pre class="language-python">any([-1, 0, 1]) == True
any([False, 0, ""]) == False</pre>

<code><a href="#bool">bool</a></code> <code>any(<a href=#any.elements>elements</a>)</code>


<h4 id="parameters.any">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=any.elements href=#any.elements>elements</span> | <code>sequence</code><br><p>A collection of elements.</p>

<a id="bool" aria-hidden="true"></a>
### bool

Constructor for the bool type. It returns <code>False</code> if the object is <code>None</code>, <code>False</code>, an empty string (<code>""</code>), the number <code>0</code>, or an empty collection (e.g. <code>()</code>, <code>[]</code>). Otherwise, it returns <code>True</code>.

<code><a href="#bool">bool</a></code> <code>bool(<a href=#bool.x>x</a>=False)</code>


<h4 id="parameters.bool">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=bool.x href=#bool.x>x</span> | <code>unknown</code><br><p>The variable to convert.</p>

<a id="dict" aria-hidden="true"></a>
### dict

Creates a <a href="../core/dict.html">dictionary</a> from an optional positional argument and an optional set of keyword arguments. In the case where the same key is given multiple times, the last value will be used. Entries supplied via keyword arguments are considered to come after entries supplied via the positional argument.

<code><a href="#dict">dict</a></code> <code>dict(<a href=#dict.pairs>pairs</a>=[], <a href=#dict.kwargs>kwargs</a>)</code>


<h4 id="parameters.dict">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=dict.pairs href=#dict.pairs>pairs</span> | <code>unknown</code><br><p>A dict, or an iterable whose elements are each of length 2 (key, value).</p>
<span id=dict.kwargs href=#dict.kwargs>kwargs</span> | <code><a href="#dict">dict</a></code><br><p>Dictionary of additional entries.</p>

<a id="dir" aria-hidden="true"></a>
### dir

Returns a list of strings: the names of the attributes and methods of the parameter object.

<code>list of string</code> <code>dir(<a href=#dir.x>x</a>)</code>


<h4 id="parameters.dir">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=dir.x href=#dir.x>x</span> | <code>unknown</code><br><p>The object to check.</p>

<a id="enumerate" aria-hidden="true"></a>
### enumerate

Returns a list of pairs (two-element tuples), with the index (int) and the item from the input sequence.
<pre class="language-python">enumerate([24, 21, 84]) == [(0, 24), (1, 21), (2, 84)]</pre>


<code>sequence</code> <code>enumerate(<a href=#enumerate.list>list</a>, <a href=#enumerate.start>start</a>=0)</code>


<h4 id="parameters.enumerate">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=enumerate.list href=#enumerate.list>list</span> | <code>unknown</code><br><p>input sequence.</p>
<span id=enumerate.start href=#enumerate.start>start</span> | <code><a href="#int">int</a></code><br><p>start index.</p>

<a id="fail" aria-hidden="true"></a>
### fail

Causes execution to fail with an error.

<code>fail(<a href=#fail.msg>msg</a>=None, <a href=#fail.attr>attr</a>=None, <a href=#fail.sep>sep</a>=" ", <a href=#fail.stack_trace>stack_trace</a>=True, <a href=#fail.args>args</a>)</code>


<h4 id="parameters.fail">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=fail.msg href=#fail.msg>msg</span> | <code>unknown</code><br><p>Deprecated: use positional arguments instead. This argument acts like an implicit leading positional argument.</p>
<span id=fail.attr href=#fail.attr>attr</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Deprecated. Causes an optional prefix containing this string to be added to the error message.</p>
<span id=fail.sep href=#fail.sep>sep</span> | <code><a href="#string">string</a></code><br><p>The separator string between the objects, default is space (" ").</p>
<span id=fail.stack_trace href=#fail.stack_trace>stack_trace</span> | <code><a href="#bool">bool</a></code><br><p>If False stack trace is elided from failure for friendlier user messages</p>
<span id=fail.args href=#fail.args>args</span> | <code><a href="#list">list</a></code><br><p>A list of values, formatted with debugPrint (which is equivalent to str by default) and joined with sep (defaults to " "), that appear in the error message.</p>

<a id="float" aria-hidden="true"></a>
### float

Returns x as a float value. <ul><li>If <code>x</code> is already a float, <code>float</code> returns it unchanged.</li><li>If <code>x</code> is a bool, <code>float</code> returns 1.0 for True and 0.0 for False.</li><li>If <code>x</code> is an int, <code>float</code> returns the nearest finite floating-point value to x, or an error if the magnitude is too large.</li><li>If <code>x</code> is a string, it must be a valid floating-point literal, or be equal (ignoring case) to <code>NaN</code>, <code>Inf</code>, or <code>Infinity</code>, optionally preceded by a <code>+</code> or <code>-</code> sign.</li></ul>Any other value causes an error. With no argument, <code>float()</code> returns 0.0.

<code><a href="#float">float</a></code> <code>float(<a href=#float.x>x</a>=unbound)</code>


<h4 id="parameters.float">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=float.x href=#float.x>x</span> | <code><a href="#string">string</a></code> or <code><a href="#bool">bool</a></code> or <code><a href="#int">int</a></code> or <code><a href="#float">float</a></code><br><p>The value to convert.</p>

<a id="getattr" aria-hidden="true"></a>
### getattr

Returns the struct's field of the given name if it exists. If not, it either returns <code>default</code> (if specified) or raises an error. <code>getattr(x, "foobar")</code> is equivalent to <code>x.foobar</code>.<pre class="language-python">getattr(ctx.attr, "myattr")
getattr(ctx.attr, "myattr", "mydefault")</pre>

<code>unknown</code> <code>getattr(<a href=#getattr.x>x</a>, <a href=#getattr.name>name</a>, <a href=#getattr.default>default</a>=unbound)</code>


<h4 id="parameters.getattr">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=getattr.x href=#getattr.x>x</span> | <code>unknown</code><br><p>The struct whose attribute is accessed.</p>
<span id=getattr.name href=#getattr.name>name</span> | <code><a href="#string">string</a></code><br><p>The name of the struct attribute.</p>
<span id=getattr.default href=#getattr.default>default</span> | <code>unknown</code><br><p>The default value to return in case the struct doesn't have an attribute of the given name.</p>

<a id="glob" aria-hidden="true"></a>
### glob

Returns an object which matches every file in the workdir that matches at least one pattern in include and does not match any of the patterns in exclude.

<code><a href="#glob">glob</a></code> <code>glob(<a href=#glob.include>include</a>, <a href=#glob.exclude>exclude</a>=[])</code>


<h4 id="parameters.glob">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=glob.include href=#glob.include>include</span> | <code>sequence of <a href="#string">string</a></code><br><p>The list of glob patterns to include</p>
<span id=glob.exclude href=#glob.exclude>exclude</span> | <code>sequence of <a href="#string">string</a></code><br><p>The list of glob patterns to exclude</p>


<h4 id="example.glob">Examples:</h4>


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


<a id="hasattr" aria-hidden="true"></a>
### hasattr

Returns True if the object <code>x</code> has an attribute or method of the given <code>name</code>, otherwise False. Example:<br><pre class="language-python">hasattr(ctx.attr, "myattr")</pre>

<code><a href="#bool">bool</a></code> <code>hasattr(<a href=#hasattr.x>x</a>, <a href=#hasattr.name>name</a>)</code>


<h4 id="parameters.hasattr">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=hasattr.x href=#hasattr.x>x</span> | <code>unknown</code><br><p>The object to check.</p>
<span id=hasattr.name href=#hasattr.name>name</span> | <code><a href="#string">string</a></code><br><p>The name of the attribute.</p>

<a id="hash" aria-hidden="true"></a>
### hash

Return a hash value for a string. This is computed deterministically using the same algorithm as Java's <code>String.hashCode()</code>, namely: <pre class="language-python">s[0] * (31^(n-1)) + s[1] * (31^(n-2)) + ... + s[n-1]</pre> Hashing of values besides strings is not currently supported.

<code><a href="#int">int</a></code> <code>hash(<a href=#hash.value>value</a>)</code>


<h4 id="parameters.hash">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=hash.value href=#hash.value>value</span> | <code><a href="#string">string</a></code><br><p>String value to hash.</p>

<a id="int" aria-hidden="true"></a>
### int

Returns x as an int value.<ul><li>If <code>x</code> is already an int, <code>int</code> returns it unchanged.</li><li>If <code>x</code> is a bool, <code>int</code> returns 1 for True and 0 for False.</li><li>If <code>x</code> is a string, it must have the format     <code>&lt;sign&gt;&lt;prefix&gt;&lt;digits&gt;</code>.     <code>&lt;sign&gt;</code> is either <code>"+"</code>, <code>"-"</code>,     or empty (interpreted as positive). <code>&lt;digits&gt;</code> are a     sequence of digits from 0 up to <code>base</code> - 1, where the letters a-z     (or equivalently, A-Z) are used as digits for 10-35. In the case where     <code>base</code> is 2/8/16, <code>&lt;prefix&gt;</code> is optional and may     be 0b/0o/0x (or equivalently, 0B/0O/0X) respectively; if the     <code>base</code> is any other value besides these bases or the special value     0, the prefix must be empty. In the case where <code>base</code> is 0, the     string is interpreted as an integer literal, in the sense that one of the     bases 2/8/10/16 is chosen depending on which prefix if any is used. If     <code>base</code> is 0, no prefix is used, and there is more than one digit,     the leading digit cannot be 0; this is to avoid confusion between octal and     decimal. The magnitude of the number represented by the string must be within     the allowed range for the int type.</li><li>If <code>x</code> is a float, <code>int</code> returns the integer value of    the float, rounding towards zero. It is an error if x is non-finite (NaN or    infinity).</li></ul>This function fails if <code>x</code> is any other type, or if the value is a string not satisfying the above format. Unlike Python's <code>int</code> function, this function does not allow zero arguments, and does not allow extraneous whitespace for string arguments.<p>Examples:<pre class="language-python">int("123") == 123
int("-123") == -123
int("+123") == 123
int("FF", 16) == 255
int("0xFF", 16) == 255
int("10", 0) == 10
int("-0x10", 0) == -16
int("-0x10", 0) == -16
int("123.456") == 123
</pre>

<code><a href="#int">int</a></code> <code>int(<a href=#int.x>x</a>, <a href=#int.base>base</a>=unbound)</code>


<h4 id="parameters.int">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=int.x href=#int.x>x</span> | <code><a href="#string">string</a></code> or <code><a href="#bool">bool</a></code> or <code><a href="#int">int</a></code> or <code><a href="#float">float</a></code><br><p>The string to convert.</p>
<span id=int.base href=#int.base>base</span> | <code><a href="#int">int</a></code><br><p>The base used to interpret a string value; defaults to 10. Must be between 2 and 36 (inclusive), or 0 to detect the base as if <code>x</code> were an integer literal. This parameter must not be supplied if the value is not a string.</p>

<a id="len" aria-hidden="true"></a>
### len

Returns the length of a string, sequence (such as a list or tuple), dict, set, or other iterable.

<code><a href="#int">int</a></code> <code>len(<a href=#len.x>x</a>)</code>


<h4 id="parameters.len">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=len.x href=#len.x>x</span> | <code>iterable</code> or <code><a href="#string">string</a></code><br><p>The value whose length to report.</p>

<a id="list" aria-hidden="true"></a>
### list

Returns a new list with the same elements as the given iterable value.<pre class="language-python">list([1, 2]) == [1, 2]
list((2, 3, 2)) == [2, 3, 2]
list({5: "a", 2: "b", 4: "c"}) == [5, 2, 4]</pre>

<code>sequence</code> <code>list(<a href=#list.x>x</a>=[])</code>


<h4 id="parameters.list">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=list.x href=#list.x>x</span> | <code>sequence</code><br><p>The object to convert.</p>

<a id="max" aria-hidden="true"></a>
### max

Returns the largest one of all given arguments. If only one positional argument is provided, it must be a non-empty iterable.It is an error if elements are not comparable (for example int with string), or if no arguments are given.<pre class="language-python">
max(2, 5, 4) == 5
max([5, 6, 3]) == 6
max("two", "three", "four", key = len) =="three"  # the longest
max([1, -1, -2, 2], key = abs) == -2  # the first encountered with maximal key value
</pre>

<code>unknown</code> <code>max(<a href=#max.key>key</a>=None, <a href=#max.args>args</a>)</code>


<h4 id="parameters.max">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=max.key href=#max.key>key</span> | <code>callable</code> or <code>NoneType</code><br><p>An optional function applied to each element before comparison.</p>
<span id=max.args href=#max.args>args</span> | <code><a href="#list">list</a></code><br><p>The elements to be checked.</p>

<a id="min" aria-hidden="true"></a>
### min

Returns the smallest one of all given arguments. If only one positional argument is provided, it must be a non-empty iterable. It is an error if elements are not comparable (for example int with string), or if no arguments are given.<pre class="language-python">
min(2, 5, 4) == 2
min([5, 6, 3]) == 3
min("six", "three", "four", key = len) == "six"  # the shortest
min([2, -2, -1, 1], key = abs) == -1  # the first encountered with minimal key value
</pre>

<code>unknown</code> <code>min(<a href=#min.key>key</a>=None, <a href=#min.args>args</a>)</code>


<h4 id="parameters.min">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=min.key href=#min.key>key</span> | <code>callable</code> or <code>NoneType</code><br><p>An optional function applied to each element before comparison.</p>
<span id=min.args href=#min.args>args</span> | <code><a href="#list">list</a></code><br><p>The elements to be checked.</p>

<a id="new_author" aria-hidden="true"></a>
### new_author

Create a new author from a string with the form 'name <foo@bar.com>'

<code><a href="#author">author</a></code> <code>new_author(<a href=#new_author.author_string>author_string</a>)</code>


<h4 id="parameters.new_author">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=new_author.author_string href=#new_author.author_string>author_string</span> | <code><a href="#string">string</a></code><br><p>A string representation of the author with the form 'name <foo@bar.com>'</p>


<h4 id="example.new_author">Example:</h4>


##### Create a new author:



```python
new_author('Foo Bar <foobar@myorg.com>')
```


<a id="parse_message" aria-hidden="true"></a>
### parse_message

Returns a ChangeMessage parsed from a well formed string.

<code><a href="#changemessage">ChangeMessage</a></code> <code>parse_message(<a href=#parse_message.message>message</a>)</code>


<h4 id="parameters.parse_message">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=parse_message.message href=#parse_message.message>message</span> | <code><a href="#string">string</a></code><br><p>The contents of the change message</p>

<a id="print" aria-hidden="true"></a>
### print

Prints <code>args</code> as debug output. It will be prefixed with the string <code>"DEBUG"</code> and the location (file and line number) of this call. The exact way in which the arguments are converted to strings is unspecified and may change at any time. In particular, it may be different from (and more detailed than) the formatting done by <a href='#str'><code>str()</code></a> and <a href='#repr'><code>repr()</code></a>.<p>Using <code>print</code> in production code is discouraged due to the spam it creates for users. For deprecations, prefer a hard error using <a href="#fail"><code>fail()</code></a> whenever possible.

<code>print(<a href=#print.sep>sep</a>=" ", <a href=#print.args>args</a>)</code>


<h4 id="parameters.print">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=print.sep href=#print.sep>sep</span> | <code><a href="#string">string</a></code><br><p>The separator string between the objects, default is space (" ").</p>
<span id=print.args href=#print.args>args</span> | <code><a href="#list">list</a></code><br><p>The objects to print.</p>

<a id="range" aria-hidden="true"></a>
### range

Creates a list where items go from <code>start</code> to <code>stop</code>, using a <code>step</code> increment. If a single argument is provided, items will range from 0 to that element.<pre class="language-python">range(4) == [0, 1, 2, 3]
range(3, 9, 2) == [3, 5, 7]
range(3, 0, -1) == [3, 2, 1]</pre>

<code>list of int</code> <code>range(<a href=#range.start_or_stop>start_or_stop</a>, <a href=#range.stop>stop</a>=unbound, <a href=#range.step>step</a>=1)</code>


<h4 id="parameters.range">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=range.start_or_stop href=#range.start_or_stop>start_or_stop</span> | <code><a href="#int">int</a></code><br><p>Value of the start element if stop is provided, otherwise value of stop and the actual start is 0</p>
<span id=range.stop href=#range.stop>stop</span> | <code><a href="#int">int</a></code><br><p>optional index of the first item <i>not</i> to be included in the resulting list; generation of the list stops before <code>stop</code> is reached.</p>
<span id=range.step href=#range.step>step</span> | <code><a href="#int">int</a></code><br><p>The increment (default is 1). It may be negative.</p>

<a id="repr" aria-hidden="true"></a>
### repr

Converts any object to a string representation. This is useful for debugging.<br><pre class="language-python">repr("ab") == '"ab"'</pre>

<code><a href="#string">string</a></code> <code>repr(<a href=#repr.x>x</a>)</code>


<h4 id="parameters.repr">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=repr.x href=#repr.x>x</span> | <code>unknown</code><br><p>The object to convert.</p>

<a id="reversed" aria-hidden="true"></a>
### reversed

Returns a new, unfrozen list that contains the elements of the original iterable sequence in reversed order.<pre class="language-python">reversed([3, 5, 4]) == [4, 5, 3]</pre>

<code>sequence</code> <code>reversed(<a href=#reversed.sequence>sequence</a>)</code>


<h4 id="parameters.reversed">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=reversed.sequence href=#reversed.sequence>sequence</span> | <code>sequence</code><br><p>The iterable sequence (e.g. list) to be reversed.</p>

<a id="set" aria-hidden="true"></a>
### set

Creates a new <a href="#set-2">set</a> containing the unique elements of a given
iterable, preserving iteration order.

<p>If called with no argument, <code>set()</code> returns a new empty set.

<p>For example,
<pre class=language-python>
set()                          # an empty set
set([3, 1, 1, 2])              # set([3, 1, 2]), a set of three elements
set({"k1": "v1", "k2": "v2"})  # set(["k1", "k2"]), a set of two elements
</pre>


<code>sequence</code> <code>set(<a href=#set.elements>elements</a>=[])</code>


<h4 id="parameters.set">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=set.elements href=#set.elements>elements</span> | <code>sequence</code><br><p>An iterable of hashable values.</p>

<a id="sorted" aria-hidden="true"></a>
### sorted

Returns a new sorted list containing all the elements of the supplied iterable sequence. An error may occur if any pair of elements x, y may not be compared using x < y. The elements are sorted into ascending order, unless the reverse argument is True, in which case the order is descending.
 Sorting is stable: elements that compare equal retain their original relative order.
<pre class="language-python">
sorted([3, 5, 4]) == [3, 4, 5]
sorted([3, 5, 4], reverse = True) == [5, 4, 3]
sorted(["two", "three", "four"], key = len) == ["two", "four", "three"]  # sort by length
</pre>

<code>sequence</code> <code>sorted(<a href=#sorted.iterable>iterable</a>, <a href=#sorted.key>key</a>=None, <a href=#sorted.reverse>reverse</a>=False)</code>


<h4 id="parameters.sorted">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=sorted.iterable href=#sorted.iterable>iterable</span> | <code>sequence</code><br><p>The iterable sequence to sort.</p>
<span id=sorted.key href=#sorted.key>key</span> | <code>callable</code> or <code>NoneType</code><br><p>An optional function applied to each element before comparison.</p>
<span id=sorted.reverse href=#sorted.reverse>reverse</span> | <code><a href="#bool">bool</a></code><br><p>Return results in descending order.</p>

<a id="str" aria-hidden="true"></a>
### str

Converts any object to string. This is useful for debugging.<pre class="language-python">str("ab") == "ab"
str(8) == "8"</pre>

<code><a href="#string">string</a></code> <code>str(<a href=#str.x>x</a>)</code>


<h4 id="parameters.str">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=str.x href=#str.x>x</span> | <code>unknown</code><br><p>The object to convert.</p>

<a id="tuple" aria-hidden="true"></a>
### tuple

Returns a tuple with the same elements as the given iterable value.<pre class="language-python">tuple([1, 2]) == (1, 2)
tuple((2, 3, 2)) == (2, 3, 2)
tuple({5: "a", 2: "b", 4: "c"}) == (5, 2, 4)</pre>

<code><a href="#tuple">tuple</a></code> <code>tuple(<a href=#tuple.x>x</a>=())</code>


<h4 id="parameters.tuple">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=tuple.x href=#tuple.x>x</span> | <code>sequence</code><br><p>The object to convert.</p>

<a id="type" aria-hidden="true"></a>
### type

Returns the type name of its argument. This is useful for debugging and type-checking. Examples:<pre class="language-python">type(2) == "int"
type([1]) == "list"
type(struct(a = 2)) == "struct"</pre>This function might change in the future. To write Python-compatible code and be future-proof, use it only to compare return values: <pre class="language-python">if type(x) == type([]):  # if x is a list</pre>

<code><a href="#string">string</a></code> <code>type(<a href=#type.x>x</a>)</code>


<h4 id="parameters.type">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=type.x href=#type.x>x</span> | <code>unknown</code><br><p>The object to check type of.</p>

<a id="zip" aria-hidden="true"></a>
### zip

Returns a <code>list</code> of <code>tuple</code>s, where the i-th tuple contains the i-th element from each of the argument sequences or iterables. The list has the size of the shortest input. With a single iterable argument, it returns a list of 1-tuples. With no arguments, it returns an empty list. Examples:<pre class="language-python">zip()  # == []
zip([1, 2])  # == [(1,), (2,)]
zip([1, 2], [3, 4])  # == [(1, 3), (2, 4)]
zip([1, 2], [3, 4, 5])  # == [(1, 3), (2, 4)]</pre>

<code>sequence</code> <code>zip(<a href=#zip.args>args</a>)</code>


<h4 id="parameters.zip">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=zip.args href=#zip.args>args</span> | <code><a href="#list">list</a></code><br><p>lists to zip.</p>



## go

Module for Go related starlark operations

<a id="go.go_proxy_resolver" aria-hidden="true"></a>
### go.go_proxy_resolver

Go resolver that knows what to do with command line passed refs.

<code>VersionResolver</code> <code>go.go_proxy_resolver(<a href=#go.go_proxy_resolver.module>module</a>, <a href=#go.go_proxy_resolver.auth>auth</a>=None)</code>


<h4 id="parameters.go.go_proxy_resolver">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=go.go_proxy_resolver.module href=#go.go_proxy_resolver.module>module</span> | <code><a href="#string">string</a></code><br><p>The go module path name. e.g. github.com/google/gopacket. This will automatically normalize uppercase characters to '!{your_uppercase_character}' to escape them.</p>
<span id=go.go_proxy_resolver.auth href=#go.go_proxy_resolver.auth>auth</span> | <code>AuthInterceptor</code> or <code>NoneType</code><br><p>Optional, an interceptor for providing credentials.</p>

<a id="go.go_proxy_version_list" aria-hidden="true"></a>
### go.go_proxy_version_list

Returns go proxy version list object

<code><a href="#goproxy_version_list">goproxy_version_list</a></code> <code>go.go_proxy_version_list(<a href=#go.go_proxy_version_list.module>module</a>, <a href=#go.go_proxy_version_list.ref>ref</a>=None, <a href=#go.go_proxy_version_list.auth>auth</a>=None)</code>


<h4 id="parameters.go.go_proxy_version_list">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=go.go_proxy_version_list.module href=#go.go_proxy_version_list.module>module</span> | <code><a href="#string">string</a></code><br><p>The go module path name. e.g. github.com/google/gopacket. This will automatically normalize uppercase characters to '!{your_uppercase_character}' to escape them.</p>
<span id=go.go_proxy_version_list.ref href=#go.go_proxy_version_list.ref>ref</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>This parameter is primarily used to track versions at specific branches and revisions. If a value is supplied, the returned version list will attempt to extract version data from ${ref}.info found with go proxy at the /@v/${ref}.info endpoint. You can leave off the .info suffix.</p>
<span id=go.go_proxy_version_list.auth href=#go.go_proxy_version_list.auth>auth</span> | <code>AuthInterceptor</code> or <code>NoneType</code><br><p>Optional, an interceptor for providing credentials.</p>


<h4 id="example.go.go_proxy_version_list">Example:</h4>


##### Create a version list for a given go package:

Example of how create a version list for github.com/google/gopacket

```python
go.go_proxy_version_list(
        module='github.com/google/gopacket'
)
```




## goproxy_version_list

Fetch versions from goproxy


<h4 id="returned_by.goproxy_version_list">Returned By:</h4>

<ul><li><a href="#go.go_proxy_version_list">go.go_proxy_version_list</a></li></ul>

<a id="goproxy_version_list.get_info" aria-hidden="true"></a>
### goproxy_version_list.get_info

Return the results of an info query. An object is only returned if a ref was specified.

<code>GoVersionObject</code> <code>goproxy_version_list.get_info(<a href=#goproxy_version_list.get_info.ref>ref</a>=None)</code>


<h4 id="parameters.goproxy_version_list.get_info">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=goproxy_version_list.get_info.ref href=#goproxy_version_list.get_info.ref>ref</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>The reference to query for. This is optional, and the default will be the latest version, or the ref if passed into this object during creation.</p>



## hashing

utilities for hashing

<a id="hashing.path_md5_sum" aria-hidden="true"></a>
### hashing.path_md5_sum

Return the md5 hash of a file at a checkout path. Do not use unless working with legacy systems that require MD5.
WARNING: do not use unless working with legacy systems that require MD5

<code><a href="#string">string</a></code> <code>hashing.path_md5_sum(<a href=#hashing.path_md5_sum.path>path</a>)</code>


<h4 id="parameters.hashing.path_md5_sum">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=hashing.path_md5_sum.path href=#hashing.path_md5_sum.path>path</span> | <code><a href="#path">Path</a></code><br><p>checkout path pointing to a file to be hashed</p>

<a id="hashing.path_sha256_sum" aria-hidden="true"></a>
### hashing.path_sha256_sum

Return the sha256 hash of a file at a checkout path

<code><a href="#string">string</a></code> <code>hashing.path_sha256_sum(<a href=#hashing.path_sha256_sum.path>path</a>)</code>


<h4 id="parameters.hashing.path_sha256_sum">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=hashing.path_sha256_sum.path href=#hashing.path_sha256_sum.path>path</span> | <code><a href="#path">Path</a></code><br><p>checkout path pointing to a file to be hashed</p>

<a id="hashing.str_sha256_sum" aria-hidden="true"></a>
### hashing.str_sha256_sum

Return the hash of a list of objects based on the algorithm specified

<code><a href="#string">string</a></code> <code>hashing.str_sha256_sum(<a href=#hashing.str_sha256_sum.input>input</a>)</code>


<h4 id="parameters.hashing.str_sha256_sum">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=hashing.str_sha256_sum.input href=#hashing.str_sha256_sum.input>input</span> | <code>sequence of <a href="#string">string</a></code> or <code><a href="#string">string</a></code><br><p>One or more string inputs to hash.</p>



## hg

Set of functions to define Mercurial (Hg) origins and destinations.

<a id="hg.origin" aria-hidden="true"></a>
### hg.origin

<b>EXPERIMENTAL:</b> Defines a standard Mercurial (Hg) origin.

<code><a href="#origin">origin</a></code> <code>hg.origin(<a href=#hg.origin.url>url</a>, <a href=#hg.origin.ref>ref</a>="default")</code>


<h4 id="parameters.hg.origin">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=hg.origin.url href=#hg.origin.url>url</span> | <code><a href="#string">string</a></code><br><p>Indicates the URL of the Hg repository</p>
<span id=hg.origin.ref href=#hg.origin.ref>ref</span> | <code><a href="#string">string</a></code><br><p>Represents the default reference that will be used to read a revision from the repository. The reference defaults to `default`, the most recent revision on the default branch. References can be in a variety of formats:<br><ul> <li> A global identifier for a revision. Example: f4e0e692208520203de05557244e573e981f6c72</li><li> A bookmark in the repository.</li><li> A branch in the repository, which returns the tip of that branch. Example: default</li><li> A tag in the repository. Example: tip</li></ul></p>



## html

Set of functions to work with HTML in copybara

<a id="html.xpath" aria-hidden="true"></a>
### html.xpath

Run an xpath expression on HTML content to select elements. This only supports a subset of xpath expressions.

<code>list of html_element</code> <code>html.xpath(<a href=#html.xpath.content>content</a>, <a href=#html.xpath.expression>expression</a>)</code>


<h4 id="parameters.html.xpath">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=html.xpath.content href=#html.xpath.content>content</span> | <code><a href="#string">string</a></code><br><p>The HTML content</p>
<span id=html.xpath.expression href=#html.xpath.expression>expression</span> | <code><a href="#string">string</a></code><br><p>XPath expression to select elements</p>



## html_element

A HTML element.

<a id="html_element.attr" aria-hidden="true"></a>
### html_element.attr

Get an attribute value by key

<code><a href="#string">string</a></code> <code>html_element.attr(<a href=#html_element.attr.key>key</a>)</code>


<h4 id="parameters.html_element.attr">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=html_element.attr.key href=#html_element.attr.key>key</span> | <code><a href="#string">string</a></code><br><p>the (case-sensitive) attribute key</p>



## http

Module for working with http endpoints.

<a id="http.bearer_auth" aria-hidden="true"></a>
### http.bearer_auth

Authentication via a bearer token.

<code>BearerInterceptor</code> <code>http.bearer_auth(<a href=#http.bearer_auth.creds>creds</a>)</code>


<h4 id="parameters.http.bearer_auth">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=http.bearer_auth.creds href=#http.bearer_auth.creds>creds</span> | <code>CredentialIssuer</code><br><p>The token credentials.</p>

<a id="http.endpoint" aria-hidden="true"></a>
### http.endpoint

Endpoint that executes any sort of http request. Currently restrictedto requests to specific hosts.

<code>endpoint_provider</code> <code>http.endpoint(<a href=#http.endpoint.host>host</a>='', <a href=#http.endpoint.checker>checker</a>=None, <a href=#http.endpoint.hosts>hosts</a>=[], <a href=#http.endpoint.issuers>issuers</a>={})</code>


<h4 id="parameters.http.endpoint">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=http.endpoint.host href=#http.endpoint.host>host</span> | <code><a href="#string">string</a></code><br><p>DEPRECATED. A single host to allow HTTP traffic to.</p>
<span id=http.endpoint.checker href=#http.endpoint.checker>checker</span> | <code><a href="#checker">checker</a></code> or <code>NoneType</code><br><p>A checker that will check calls made by the endpoint</p>
<span id=http.endpoint.hosts href=#http.endpoint.hosts>hosts</span> | <code>sequence</code><br><p>A list of hosts to allow HTTP traffic to.</p>
<span id=http.endpoint.issuers href=#http.endpoint.issuers>issuers</span> | <code><a href="#dict">dict</a></code> or <code>NoneType</code><br><p>A dictionaty of credential issuers.</p>

<a id="http.host" aria-hidden="true"></a>
### http.host

Wraps a host and potentially credentials for http auth.

<code>HostCredential</code> <code>http.host(<a href=#http.host.host>host</a>, <a href=#http.host.auth>auth</a>=None)</code>


<h4 id="parameters.http.host">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=http.host.host href=#http.host.host>host</span> | <code><a href="#string">string</a></code><br><p>The host to be contacted.</p>
<span id=http.host.auth href=#http.host.auth>auth</span> | <code>AuthInterceptor</code> or <code>UsernamePasswordIssuer</code> or <code>NoneType</code><br><p>Optional, an interceptor for providing credentials. Also accepts a username_password.</p>

<a id="http.json" aria-hidden="true"></a>
### http.json

Creates a JSON HTTP body.

<code>HttpEndpointJsonContent</code> <code>http.json(<a href=#http.json.body>body</a>={})</code>


<h4 id="parameters.http.json">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=http.json.body href=#http.json.body>body</span> | <code>unknown</code><br><p>HTTP body object, property name will be used as key and value as value.</p>

<a id="http.multipart_form" aria-hidden="true"></a>
### http.multipart_form

Creates a multipart form http body.

<code>HttpEndpointMultipartFormContent</code> <code>http.multipart_form(<a href=#http.multipart_form.parts>parts</a>=[])</code>


<h4 id="parameters.http.multipart_form">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=http.multipart_form.parts href=#http.multipart_form.parts>parts</span> | <code>sequence</code><br><p>A list of form parts</p>

<a id="http.multipart_form_file" aria-hidden="true"></a>
### http.multipart_form_file

Create a file part for a multipart form payload.

<code>HttpEndpointFormPart</code> <code>http.multipart_form_file(<a href=#http.multipart_form_file.name>name</a>, <a href=#http.multipart_form_file.path>path</a>, <a href=#http.multipart_form_file.content_type>content_type</a>="application/octet-stream", <a href=#http.multipart_form_file.filename>filename</a>=None)</code>


<h4 id="parameters.http.multipart_form_file">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=http.multipart_form_file.name href=#http.multipart_form_file.name>name</span> | <code><a href="#string">string</a></code><br><p>The name of the form field.</p>
<span id=http.multipart_form_file.path href=#http.multipart_form_file.path>path</span> | <code><a href="#path">Path</a></code><br><p>The checkout path pointing to the file to use as the field value.</p>
<span id=http.multipart_form_file.content_type href=#http.multipart_form_file.content_type>content_type</span> | <code><a href="#string">string</a></code><br><p>Content type header value for the form part. Defaults to application/octet-stream. <br>https://www.w3.org/Protocols/rfc1341/4_Content-Type.html</p>
<span id=http.multipart_form_file.filename href=#http.multipart_form_file.filename>filename</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>The filename that will be sent along with the data. Defaults to the filename of the path parameter. Sets the filename parameter in the content disposition header. <br>https://www.w3.org/Protocols/HTTP/Issues/content-disposition.txt</p>

<a id="http.multipart_form_text" aria-hidden="true"></a>
### http.multipart_form_text

Create a text/plain part for a multipart form payload

<code>HttpEndpointFormPart</code> <code>http.multipart_form_text(<a href=#http.multipart_form_text.name>name</a>, <a href=#http.multipart_form_text.text>text</a>)</code>


<h4 id="parameters.http.multipart_form_text">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=http.multipart_form_text.name href=#http.multipart_form_text.name>name</span> | <code><a href="#string">string</a></code><br><p>The name of the form field.</p>
<span id=http.multipart_form_text.text href=#http.multipart_form_text.text>text</span> | <code><a href="#string">string</a></code><br><p>The form value of the field</p>

<a id="http.trigger" aria-hidden="true"></a>
### http.trigger

Trigger for http endpoint

<code>trigger</code> <code>http.trigger(<a href=#http.trigger.hosts>hosts</a>=[], <a href=#http.trigger.issuers>issuers</a>={}, <a href=#http.trigger.checker>checker</a>=None)</code>


<h4 id="parameters.http.trigger">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=http.trigger.hosts href=#http.trigger.hosts>hosts</span> | <code>sequence</code><br><p>A list of hosts to allow HTTP traffic to.</p>
<span id=http.trigger.issuers href=#http.trigger.issuers>issuers</span> | <code><a href="#dict">dict</a></code> or <code>NoneType</code><br><p>A dictionary of credential issuers.</p>
<span id=http.trigger.checker href=#http.trigger.checker>checker</span> | <code><a href="#checker">checker</a></code> or <code>NoneType</code><br><p>A checker that will check calls made by the endpoint</p>

<a id="http.url_encode" aria-hidden="true"></a>
### http.url_encode

URL-encode the input string

<code><a href="#string">string</a></code> <code>http.url_encode(<a href=#http.url_encode.input>input</a>)</code>


<h4 id="parameters.http.url_encode">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=http.url_encode.input href=#http.url_encode.input>input</span> | <code><a href="#string">string</a></code><br><p>The string to be encoded.</p>

<a id="http.urlencoded_form" aria-hidden="true"></a>
### http.urlencoded_form

Creates a url-encoded form HTTP body.

<code>HttpEndpointUrlEncodedFormContent</code> <code>http.urlencoded_form(<a href=#http.urlencoded_form.body>body</a>={})</code>


<h4 id="parameters.http.urlencoded_form">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=http.urlencoded_form.body href=#http.urlencoded_form.body>body</span> | <code><a href="#dict">dict</a></code><br><p>HTTP body object, property name will be used as key and value as value.</p>

<a id="http.username_password_auth" aria-hidden="true"></a>
### http.username_password_auth

Authentication via username and password.

<code>UsernamePasswordInterceptor</code> <code>http.username_password_auth(<a href=#http.username_password_auth.creds>creds</a>)</code>


<h4 id="parameters.http.username_password_auth">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=http.username_password_auth.creds href=#http.username_password_auth.creds>creds</span> | <code>UsernamePasswordIssuer</code><br><p>The username and password credentials.</p>



## http_endpoint

Calls via HTTP.


<h4 id="fields.http_endpoint">Fields:</h4>

Name | Description
---- | -----------
url | <code><a href="#string">string</a></code><br><p>Return the URL of this endpoint.</p>

<a id="http_endpoint.delete" aria-hidden="true"></a>
### http_endpoint.delete

Execute a delete request

<code><a href="#http_response">http_response</a></code> <code>http_endpoint.delete(<a href=#http_endpoint.delete.url>url</a>, <a href=#http_endpoint.delete.headers>headers</a>={}, <a href=#http_endpoint.delete.auth>auth</a>=False)</code>


<h4 id="parameters.http_endpoint.delete">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=http_endpoint.delete.url href=#http_endpoint.delete.url>url</span> | <code><a href="#string">string</a></code><br><p></p>
<span id=http_endpoint.delete.headers href=#http_endpoint.delete.headers>headers</span> | <code><a href="#dict">dict</a></code><br><p>dict of http headers for the request</p>
<span id=http_endpoint.delete.auth href=#http_endpoint.delete.auth>auth</span> | <code><a href="#bool">bool</a></code><br><p></p>

<a id="http_endpoint.followRedirects" aria-hidden="true"></a>
### http_endpoint.followRedirects

Sets whether to follow redirects automatically

<code>http_endpoint.followRedirects(<a href=#http_endpoint.followRedirects.followRedirects>followRedirects</a>)</code>


<h4 id="parameters.http_endpoint.followRedirects">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=http_endpoint.followRedirects.followRedirects href=#http_endpoint.followRedirects.followRedirects>followRedirects</span> | <code><a href="#bool">bool</a></code><br><p>Whether to follow redirects automatically</p>

<a id="http_endpoint.get" aria-hidden="true"></a>
### http_endpoint.get

Execute a get request

<code><a href="#http_response">http_response</a></code> <code>http_endpoint.get(<a href=#http_endpoint.get.url>url</a>, <a href=#http_endpoint.get.headers>headers</a>={}, <a href=#http_endpoint.get.auth>auth</a>=False)</code>


<h4 id="parameters.http_endpoint.get">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=http_endpoint.get.url href=#http_endpoint.get.url>url</span> | <code><a href="#string">string</a></code><br><p></p>
<span id=http_endpoint.get.headers href=#http_endpoint.get.headers>headers</span> | <code><a href="#dict">dict</a></code><br><p>dict of http headers for the request</p>
<span id=http_endpoint.get.auth href=#http_endpoint.get.auth>auth</span> | <code><a href="#bool">bool</a></code><br><p></p>

<a id="http_endpoint.new_destination_ref" aria-hidden="true"></a>
### http_endpoint.new_destination_ref

Creates a new destination reference out of this endpoint.

<code><a href="#destination_ref">destination_ref</a></code> <code>http_endpoint.new_destination_ref(<a href=#http_endpoint.new_destination_ref.ref>ref</a>, <a href=#http_endpoint.new_destination_ref.type>type</a>, <a href=#http_endpoint.new_destination_ref.url>url</a>=None)</code>


<h4 id="parameters.http_endpoint.new_destination_ref">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=http_endpoint.new_destination_ref.ref href=#http_endpoint.new_destination_ref.ref>ref</span> | <code><a href="#string">string</a></code><br><p>The reference.</p>
<span id=http_endpoint.new_destination_ref.type href=#http_endpoint.new_destination_ref.type>type</span> | <code><a href="#string">string</a></code><br><p>The type of this reference.</p>
<span id=http_endpoint.new_destination_ref.url href=#http_endpoint.new_destination_ref.url>url</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>The url associated with this reference, if any.</p>

<a id="http_endpoint.new_origin_ref" aria-hidden="true"></a>
### http_endpoint.new_origin_ref

Creates a new origin reference out of this endpoint.

<code><a href="#origin_ref">origin_ref</a></code> <code>http_endpoint.new_origin_ref(<a href=#http_endpoint.new_origin_ref.ref>ref</a>)</code>


<h4 id="parameters.http_endpoint.new_origin_ref">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=http_endpoint.new_origin_ref.ref href=#http_endpoint.new_origin_ref.ref>ref</span> | <code><a href="#string">string</a></code><br><p>The reference.</p>

<a id="http_endpoint.post" aria-hidden="true"></a>
### http_endpoint.post

Execute a post request

<code><a href="#http_response">http_response</a></code> <code>http_endpoint.post(<a href=#http_endpoint.post.url>url</a>, <a href=#http_endpoint.post.headers>headers</a>={}, <a href=#http_endpoint.post.content>content</a>=None, <a href=#http_endpoint.post.auth>auth</a>=False)</code>


<h4 id="parameters.http_endpoint.post">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=http_endpoint.post.url href=#http_endpoint.post.url>url</span> | <code><a href="#string">string</a></code><br><p></p>
<span id=http_endpoint.post.headers href=#http_endpoint.post.headers>headers</span> | <code><a href="#dict">dict</a></code><br><p>dict of http headers for the request</p>
<span id=http_endpoint.post.content href=#http_endpoint.post.content>content</span> | <code>HttpEndpointBody</code> or <code>NoneType</code><br><p></p>
<span id=http_endpoint.post.auth href=#http_endpoint.post.auth>auth</span> | <code><a href="#bool">bool</a></code><br><p></p>



## http_response

A http response.


<h4 id="returned_by.http_response">Returned By:</h4>

<ul><li><a href="#http_endpoint.delete">http_endpoint.delete</a></li><li><a href="#http_endpoint.get">http_endpoint.get</a></li><li><a href="#http_endpoint.post">http_endpoint.post</a></li></ul>

<a id="http_response.code" aria-hidden="true"></a>
### http_response.code

http status code

<code><a href="#int">int</a></code> <code>http_response.code()</code>

<a id="http_response.contents_string" aria-hidden="true"></a>
### http_response.contents_string

response contents as string

<code><a href="#string">string</a></code> <code>http_response.contents_string()</code>

<a id="http_response.download" aria-hidden="true"></a>
### http_response.download

Writes the content of the HTTP response into the given destination path

<code>http_response.download(<a href=#http_response.download.path>path</a>)</code>


<h4 id="parameters.http_response.download">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=http_response.download.path href=#http_response.download.path>path</span> | <code><a href="#path">Path</a></code><br><p>The destination Path</p>

<a id="http_response.header" aria-hidden="true"></a>
### http_response.header

Returns the value of the response header specified by the field name

<code>list of string</code> <code>http_response.header(<a href=#http_response.header.key>key</a>)</code>


<h4 id="parameters.http_response.header">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=http_response.header.key href=#http_response.header.key>key</span> | <code><a href="#string">string</a></code><br><p></p>

<a id="http_response.status" aria-hidden="true"></a>
### http_response.status

http status message

<code><a href="#string">string</a></code> <code>http_response.status()</code>



## int

The type of integers in Starlark. Starlark integers may be of any magnitude; arithmetic is exact. Examples of integer expressions:<br><pre class="language-python">153
0x2A  # hexadecimal literal
0o54  # octal literal
23 * 2 + 5
100 / -7
100 % -7  # -5 (unlike in some other languages)
int("18")
</pre>


<h4 id="returned_by.int">Returned By:</h4>

<ul><li><a href="#hash">hash</a></li><li><a href="#int">int</a></li><li><a href="#len">len</a></li><li><a href="#http_response.code">http_response.code</a></li><li><a href="#list.index">list.index</a></li><li><a href="#re2_matcher.end">re2_matcher.end</a></li><li><a href="#re2_matcher.group_count">re2_matcher.group_count</a></li><li><a href="#re2_matcher.start">re2_matcher.start</a></li><li><a href="#string.count">string.count</a></li><li><a href="#string.find">string.find</a></li><li><a href="#string.index">string.index</a></li><li><a href="#string.rfind">string.rfind</a></li><li><a href="#string.rindex">string.rindex</a></li></ul>
<h4 id="consumed_by.int">Consumed By:</h4>

<ul><li><a href="#core.filter_replace">core.filter_replace</a></li><li><a href="#datetime.fromtimestamp">datetime.fromtimestamp</a></li><li><a href="#git.mirrorContext.destination_fetch">git.mirrorContext.destination_fetch</a></li><li><a href="#git.mirrorContext.origin_fetch">git.mirrorContext.origin_fetch</a></li><li><a href="#github_api_obj.add_label">github_api_obj.add_label</a></li><li><a href="#github_api_obj.get_pull_request_comments">github_api_obj.get_pull_request_comments</a></li><li><a href="#github_api_obj.list_issue_comments">github_api_obj.list_issue_comments</a></li><li><a href="#github_api_obj.post_issue_comment">github_api_obj.post_issue_comment</a></li><li><a href="#github_api_obj.update_pull_request">github_api_obj.update_pull_request</a></li><li><a href="#abs">abs</a></li><li><a href="#enumerate">enumerate</a></li><li><a href="#float">float</a></li><li><a href="#int">int</a></li><li><a href="#range">range</a></li><li><a href="#list.index">list.index</a></li><li><a href="#list.insert">list.insert</a></li><li><a href="#list.pop">list.pop</a></li><li><a href="#metadata.squash_notes">metadata.squash_notes</a></li><li><a href="#patch.apply">patch.apply</a></li><li><a href="#random.sample">random.sample</a></li><li><a href="#re2_matcher.end">re2_matcher.end</a></li><li><a href="#re2_matcher.find">re2_matcher.find</a></li><li><a href="#re2_matcher.group">re2_matcher.group</a></li><li><a href="#re2_matcher.start">re2_matcher.start</a></li><li><a href="#string.count">string.count</a></li><li><a href="#string.endswith">string.endswith</a></li><li><a href="#string.find">string.find</a></li><li><a href="#string.index">string.index</a></li><li><a href="#string.replace">string.replace</a></li><li><a href="#string.rfind">string.rfind</a></li><li><a href="#string.rindex">string.rindex</a></li><li><a href="#string.rsplit">string.rsplit</a></li><li><a href="#string.split">string.split</a></li><li><a href="#string.startswith">string.startswith</a></li></ul>



## Issue

Github issue object


<h4 id="fields.Issue">Fields:</h4>

Name | Description
---- | -----------
assignee | <code><a href="#github_api_user_obj">github_api_user_obj</a></code><br><p>Pull Request assignee</p>
body | <code><a href="#string">string</a></code><br><p>Pull Request body</p>
number | <code><a href="#int">int</a></code><br><p>Pull Request number</p>
state | <code><a href="#string">string</a></code><br><p>Pull Request state</p>
title | <code><a href="#string">string</a></code><br><p>Pull Request title</p>
user | <code><a href="#github_api_user_obj">github_api_user_obj</a></code><br><p>Pull Request owner</p>


<h4 id="returned_by.Issue">Returned By:</h4>

<ul><li><a href="#github_api_obj.create_issue">github_api_obj.create_issue</a></li></ul>



## list

The built-in list type. Example list expressions:<br><pre class=language-python>x = [1, 2, 3]</pre>Accessing elements is possible using indexing (starts from <code>0</code>):<br><pre class=language-python>e = x[1]   # e == 2</pre>Lists support the <code>+</code> operator to concatenate two lists. Example:<br><pre class=language-python>x = [1, 2] + [3, 4]   # x == [1, 2, 3, 4]
x = ["a", "b"]
x += ["c"]            # x == ["a", "b", "c"]</pre>Similar to strings, lists support slice operations:<pre class=language-python>['a', 'b', 'c', 'd'][1:3]   # ['b', 'c']
['a', 'b', 'c', 'd'][::2]  # ['a', 'c']
['a', 'b', 'c', 'd'][3:0:-1]  # ['d', 'c', 'b']</pre>Lists are mutable, as in Python.


<h4 id="consumed_by.list">Consumed By:</h4>

<ul><li><a href="#fail">fail</a></li><li><a href="#max">max</a></li><li><a href="#min">min</a></li><li><a href="#print">print</a></li><li><a href="#zip">zip</a></li><li><a href="#random.sample">random.sample</a></li><li><a href="#set.difference">set.difference</a></li><li><a href="#set.difference_update">set.difference_update</a></li><li><a href="#set.intersection">set.intersection</a></li><li><a href="#set.intersection_update">set.intersection_update</a></li><li><a href="#set.union">set.union</a></li><li><a href="#set.update">set.update</a></li><li><a href="#string.format">string.format</a></li></ul>

<a id="list.append" aria-hidden="true"></a>
### list.append

Adds an item to the end of the list.

<code>list.append(<a href=#list.append.item>item</a>)</code>


<h4 id="parameters.list.append">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=list.append.item href=#list.append.item>item</span> | <code>?</code><br><p>Item to add at the end.</p>

<a id="list.clear" aria-hidden="true"></a>
### list.clear

Removes all the elements of the list.

<code>list.clear()</code>

<a id="list.extend" aria-hidden="true"></a>
### list.extend

Adds all items to the end of the list.

<code>list.extend(<a href=#list.extend.items>items</a>)</code>


<h4 id="parameters.list.extend">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=list.extend.items href=#list.extend.items>items</span> | <code>list of ?</code><br><p>Items to add at the end.</p>

<a id="list.index" aria-hidden="true"></a>
### list.index

Returns the index in the list of the first item whose value is x. It is an error if there is no such item.

<code><a href="#int">int</a></code> <code>list.index(<a href=#list.index.x>x</a>, <a href=#list.index.start>start</a>=unbound, <a href=#list.index.end>end</a>=unbound)</code>


<h4 id="parameters.list.index">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=list.index.x href=#list.index.x>x</span> | <code>?</code><br><p>The object to search.</p>
<span id=list.index.start href=#list.index.start>start</span> | <code><a href="#int">int</a></code><br><p>The start index of the list portion to inspect.</p>
<span id=list.index.end href=#list.index.end>end</span> | <code><a href="#int">int</a></code><br><p>The end index of the list portion to inspect.</p>

<a id="list.insert" aria-hidden="true"></a>
### list.insert

Inserts an item at a given position.

<code>list.insert(<a href=#list.insert.index>index</a>, <a href=#list.insert.item>item</a>)</code>


<h4 id="parameters.list.insert">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=list.insert.index href=#list.insert.index>index</span> | <code><a href="#int">int</a></code><br><p>The index of the given position.</p>
<span id=list.insert.item href=#list.insert.item>item</span> | <code>?</code><br><p>The item.</p>

<a id="list.pop" aria-hidden="true"></a>
### list.pop

Removes the item at the given position in the list, and returns it. If no <code>index</code> is specified, it removes and returns the last item in the list.

<code>?</code> <code>list.pop(<a href=#list.pop.i>i</a>=-1)</code>


<h4 id="parameters.list.pop">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=list.pop.i href=#list.pop.i>i</span> | <code><a href="#int">int</a></code><br><p>The index of the item.</p>

<a id="list.remove" aria-hidden="true"></a>
### list.remove

Removes the first item from the list whose value is x. It is an error if there is no such item.

<code>list.remove(<a href=#list.remove.x>x</a>)</code>


<h4 id="parameters.list.remove">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=list.remove.x href=#list.remove.x>x</span> | <code>?</code><br><p>The object to remove.</p>



## mapping_function

A function that given an object can map to another object


<h4 id="returned_by.mapping_function">Returned By:</h4>

<ul><li><a href="#core.replace_mapper">core.replace_mapper</a></li></ul>



## metadata

Core transformations for the change metadata

<a id="metadata.add_header" aria-hidden="true"></a>
### metadata.add_header

Adds a header line to the commit message. Any variable present in the message in the form of ${LABEL_NAME} will be replaced by the corresponding label in the message. Note that this requires that the label is already in the message or in any of the changes being imported. The label in the message takes priority over the ones in the list of original messages of changes imported.


<code><a href="#transformation">transformation</a></code> <code>metadata.add_header(<a href=#metadata.add_header.text>text</a>, <a href=#metadata.add_header.ignore_label_not_found>ignore_label_not_found</a>=False, <a href=#metadata.add_header.new_line>new_line</a>=True)</code>


<h4 id="parameters.metadata.add_header">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=metadata.add_header.text href=#metadata.add_header.text>text</span> | <code><a href="#string">string</a></code><br><p>The header text to include in the message. For example '[Import of foo ${LABEL}]'. This would construct a message resolving ${LABEL} to the corresponding label.</p>
<span id=metadata.add_header.ignore_label_not_found href=#metadata.add_header.ignore_label_not_found>ignore_label_not_found</span> | <code><a href="#bool">bool</a></code><br><p>If a label used in the template is not found, ignore the error and don't add the header. By default it will stop the migration and fail.</p>
<span id=metadata.add_header.new_line href=#metadata.add_header.new_line>new_line</span> | <code><a href="#bool">bool</a></code><br><p>If a new line should be added between the header and the original message. This allows to create messages like `HEADER: ORIGINAL_MESSAGE`</p>


<h4 id="example.metadata.add_header">Examples:</h4>


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

<code><a href="#transformation">transformation</a></code> <code>metadata.expose_label(<a href=#metadata.expose_label.name>name</a>, <a href=#metadata.expose_label.new_name>new_name</a>=label, <a href=#metadata.expose_label.separator>separator</a>="=", <a href=#metadata.expose_label.ignore_label_not_found>ignore_label_not_found</a>=True, <a href=#metadata.expose_label.all>all</a>=False, <a href=#metadata.expose_label.concat_separator>concat_separator</a>=None)</code>


<h4 id="parameters.metadata.expose_label">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=metadata.expose_label.name href=#metadata.expose_label.name>name</span> | <code><a href="#string">string</a></code><br><p>The label to search</p>
<span id=metadata.expose_label.new_name href=#metadata.expose_label.new_name>new_name</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>The name to use in the message</p>
<span id=metadata.expose_label.separator href=#metadata.expose_label.separator>separator</span> | <code><a href="#string">string</a></code><br><p>The separator to use when adding the label to the message</p>
<span id=metadata.expose_label.ignore_label_not_found href=#metadata.expose_label.ignore_label_not_found>ignore_label_not_found</span> | <code><a href="#bool">bool</a></code><br><p>If a label is not found, ignore the error and continue.</p>
<span id=metadata.expose_label.all href=#metadata.expose_label.all>all</span> | <code><a href="#bool">bool</a></code><br><p>By default Copybara tries to find the most relevant instance of the label. First looking into the message and then looking into the changes in order. If this field is true it exposes all the matches instead.</p>
<span id=metadata.expose_label.concat_separator href=#metadata.expose_label.concat_separator>concat_separator</span> | <code>unknown</code><br><p>If all is set, copybara will expose multiple values in one per line. If a separator is specified, it will concat the values instead.</p>


<h4 id="example.metadata.expose_label">Examples:</h4>


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

<code><a href="#transformation">transformation</a></code> <code>metadata.map_author(<a href=#metadata.map_author.authors>authors</a>, <a href=#metadata.map_author.reversible>reversible</a>=False, <a href=#metadata.map_author.noop_reverse>noop_reverse</a>=False, <a href=#metadata.map_author.fail_if_not_found>fail_if_not_found</a>=False, <a href=#metadata.map_author.reverse_fail_if_not_found>reverse_fail_if_not_found</a>=False, <a href=#metadata.map_author.map_all_changes>map_all_changes</a>=False)</code>


<h4 id="parameters.metadata.map_author">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=metadata.map_author.authors href=#metadata.map_author.authors>authors</span> | <code><a href="#dict">dict</a></code><br><p>The author mapping. Keys can be in the form of 'Your Name', 'some@mail' or 'Your Name &lt;some@mail&gt;'. The mapping applies heuristics to know which field to use in the mapping. The value has to be always in the form of 'Your Name &lt;some@mail&gt;'</p>
<span id=metadata.map_author.reversible href=#metadata.map_author.reversible>reversible</span> | <code><a href="#bool">bool</a></code><br><p>If the transform is automatically reversible. Workflows using the reverse of this transform will be able to automatically map values to keys.</p>
<span id=metadata.map_author.noop_reverse href=#metadata.map_author.noop_reverse>noop_reverse</span> | <code><a href="#bool">bool</a></code><br><p>If true, the reversal of the transformation doesn't do anything. This is useful to avoid having to write `core.transformation(metadata.map_author(...), reversal = [])`.</p>
<span id=metadata.map_author.fail_if_not_found href=#metadata.map_author.fail_if_not_found>fail_if_not_found</span> | <code><a href="#bool">bool</a></code><br><p>Fail if a mapping cannot be found. Helps discovering early authors that should be in the map</p>
<span id=metadata.map_author.reverse_fail_if_not_found href=#metadata.map_author.reverse_fail_if_not_found>reverse_fail_if_not_found</span> | <code><a href="#bool">bool</a></code><br><p>Same as fail_if_not_found but when the transform is used in a inverse workflow.</p>
<span id=metadata.map_author.map_all_changes href=#metadata.map_author.map_all_changes>map_all_changes</span> | <code><a href="#bool">bool</a></code><br><p>If all changes being migrated should be mapped. Useful for getting a mapped metadata.squash_notes. By default we only map the current author.</p>


<h4 id="example.metadata.map_author">Example:</h4>


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

<code><a href="#transformation">transformation</a></code> <code>metadata.map_references(<a href=#metadata.map_references.before>before</a>, <a href=#metadata.map_references.after>after</a>, <a href=#metadata.map_references.regex_groups>regex_groups</a>={}, <a href=#metadata.map_references.additional_import_labels>additional_import_labels</a>=[])</code>


<h4 id="parameters.metadata.map_references">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=metadata.map_references.before href=#metadata.map_references.before>before</span> | <code><a href="#string">string</a></code><br><p>Template for origin references in the change message. Use a '${reference}' token to capture the actual references. E.g. if the origin uses links like 'http://changes?1234', the template would be 'http://changes?${reference}', with reference_regex = '[0-9]+'</p>
<span id=metadata.map_references.after href=#metadata.map_references.after>after</span> | <code><a href="#string">string</a></code><br><p>Format for destination references in the change message. Use a '${reference}' token to represent the destination reference.  E.g. if the destination uses links like 'http://changes?1234', the template would be 'http://changes?${reference}', with reference_regex = '[0-9]+'</p>
<span id=metadata.map_references.regex_groups href=#metadata.map_references.regex_groups>regex_groups</span> | <code><a href="#dict">dict</a></code><br><p>Regexes for the ${reference} token's content. Requires one 'before_ref' entry matching the ${reference} token's content on the before side. Optionally accepts one 'after_ref' used for validation. Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax.</p>
<span id=metadata.map_references.additional_import_labels href=#metadata.map_references.additional_import_labels>additional_import_labels</span> | <code>sequence of <a href="#string">string</a></code><br><p>Meant to be used when migrating from another tool: Per default, copybara will only recognize the labels defined in the workflow's endpoints. The tool will use these additional labels to find labels created by other invocations and tools.</p>


<h4 id="example.metadata.map_references">Example:</h4>


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

<code><a href="#transformation">transformation</a></code> <code>metadata.remove_label(<a href=#metadata.remove_label.name>name</a>)</code>


<h4 id="parameters.metadata.remove_label">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=metadata.remove_label.name href=#metadata.remove_label.name>name</span> | <code><a href="#string">string</a></code><br><p>The label name</p>


<h4 id="example.metadata.remove_label">Example:</h4>


##### Remove a label:

Remove Change-Id label from the message:

```python
metadata.remove_label('Change-Id')
```


<a id="metadata.replace_message" aria-hidden="true"></a>
### metadata.replace_message

Replace the change message with a template text. Any variable present in the message in the form of ${LABEL_NAME} will be replaced by the corresponding label in the message. Note that this requires that the label is already in the message or in any of the changes being imported. The label in the message takes priority over the ones in the list of original messages of changes imported.


<code><a href="#transformation">transformation</a></code> <code>metadata.replace_message(<a href=#metadata.replace_message.text>text</a>, <a href=#metadata.replace_message.ignore_label_not_found>ignore_label_not_found</a>=False)</code>


<h4 id="parameters.metadata.replace_message">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=metadata.replace_message.text href=#metadata.replace_message.text>text</span> | <code><a href="#string">string</a></code><br><p>The template text to use for the message. For example '[Import of foo ${LABEL}]'. This would construct a message resolving ${LABEL} to the corresponding label.</p>
<span id=metadata.replace_message.ignore_label_not_found href=#metadata.replace_message.ignore_label_not_found>ignore_label_not_found</span> | <code><a href="#bool">bool</a></code><br><p>If a label used in the template is not found, ignore the error and don't add the header. By default it will stop the migration and fail.</p>


<h4 id="example.metadata.replace_message">Example:</h4>


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

<code><a href="#transformation">transformation</a></code> <code>metadata.restore_author(<a href=#metadata.restore_author.label>label</a>='ORIGINAL_AUTHOR', <a href=#metadata.restore_author.separator>separator</a>="=", <a href=#metadata.restore_author.search_all_changes>search_all_changes</a>=False)</code>


<h4 id="parameters.metadata.restore_author">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=metadata.restore_author.label href=#metadata.restore_author.label>label</span> | <code><a href="#string">string</a></code><br><p>The label to use for restoring the author</p>
<span id=metadata.restore_author.separator href=#metadata.restore_author.separator>separator</span> | <code><a href="#string">string</a></code><br><p>The separator to use between the label and the value</p>
<span id=metadata.restore_author.search_all_changes href=#metadata.restore_author.search_all_changes>search_all_changes</span> | <code><a href="#bool">bool</a></code><br><p>By default Copybara only looks in the last current change for the author label. This allows to do the search in all current changes (Only makes sense for SQUASH/CHANGE_REQUEST).</p>

<a id="metadata.save_author" aria-hidden="true"></a>
### metadata.save_author

For a given change, store a copy of the author as a label with the name ORIGINAL_AUTHOR.

<code><a href="#transformation">transformation</a></code> <code>metadata.save_author(<a href=#metadata.save_author.label>label</a>='ORIGINAL_AUTHOR', <a href=#metadata.save_author.separator>separator</a>="=")</code>


<h4 id="parameters.metadata.save_author">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=metadata.save_author.label href=#metadata.save_author.label>label</span> | <code><a href="#string">string</a></code><br><p>The label to use for storing the author</p>
<span id=metadata.save_author.separator href=#metadata.save_author.separator>separator</span> | <code><a href="#string">string</a></code><br><p>The separator to use between the label and the value</p>

<a id="metadata.scrubber" aria-hidden="true"></a>
### metadata.scrubber

Removes part of the change message using a regex

<code><a href="#transformation">transformation</a></code> <code>metadata.scrubber(<a href=#metadata.scrubber.regex>regex</a>, <a href=#metadata.scrubber.msg_if_no_match>msg_if_no_match</a>=None, <a href=#metadata.scrubber.fail_if_no_match>fail_if_no_match</a>=False, <a href=#metadata.scrubber.replacement>replacement</a>='')</code>


<h4 id="parameters.metadata.scrubber">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=metadata.scrubber.regex href=#metadata.scrubber.regex>regex</span> | <code><a href="#string">string</a></code><br><p>Any text matching the regex will be removed. Note that the regex is runs in multiline mode.</p>
<span id=metadata.scrubber.msg_if_no_match href=#metadata.scrubber.msg_if_no_match>msg_if_no_match</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>If set, Copybara will use this text when the scrubbing regex doesn't match.</p>
<span id=metadata.scrubber.fail_if_no_match href=#metadata.scrubber.fail_if_no_match>fail_if_no_match</span> | <code><a href="#bool">bool</a></code><br><p>If set, msg_if_no_match must be None and then fail if the scrubbing regex doesn't match. </p>
<span id=metadata.scrubber.replacement href=#metadata.scrubber.replacement>replacement</span> | <code><a href="#string">string</a></code><br><p>Text replacement for the matching substrings. References to regex group numbers can be used in the form of $1, $2, etc.</p>


<h4 id="example.metadata.scrubber">Examples:</h4>


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

<code><a href="#transformation">transformation</a></code> <code>metadata.squash_notes(<a href=#metadata.squash_notes.prefix>prefix</a>='Copybara import of the project:\n\n', <a href=#metadata.squash_notes.max>max</a>=100, <a href=#metadata.squash_notes.compact>compact</a>=True, <a href=#metadata.squash_notes.show_ref>show_ref</a>=True, <a href=#metadata.squash_notes.show_author>show_author</a>=True, <a href=#metadata.squash_notes.show_description>show_description</a>=True, <a href=#metadata.squash_notes.oldest_first>oldest_first</a>=False, <a href=#metadata.squash_notes.use_merge>use_merge</a>=True)</code>


<h4 id="parameters.metadata.squash_notes">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=metadata.squash_notes.prefix href=#metadata.squash_notes.prefix>prefix</span> | <code><a href="#string">string</a></code><br><p>A prefix to be printed before the list of commits.</p>
<span id=metadata.squash_notes.max href=#metadata.squash_notes.max>max</span> | <code><a href="#int">int</a></code><br><p>Max number of commits to include in the message. For the rest a comment like (and x more) will be included. By default 100 commits are included.</p>
<span id=metadata.squash_notes.compact href=#metadata.squash_notes.compact>compact</span> | <code><a href="#bool">bool</a></code><br><p>If compact is set, each change will be shown in just one line</p>
<span id=metadata.squash_notes.show_ref href=#metadata.squash_notes.show_ref>show_ref</span> | <code><a href="#bool">bool</a></code><br><p>If each change reference should be present in the notes</p>
<span id=metadata.squash_notes.show_author href=#metadata.squash_notes.show_author>show_author</span> | <code><a href="#bool">bool</a></code><br><p>If each change author should be present in the notes</p>
<span id=metadata.squash_notes.show_description href=#metadata.squash_notes.show_description>show_description</span> | <code><a href="#bool">bool</a></code><br><p>If each change description should be present in the notes</p>
<span id=metadata.squash_notes.oldest_first href=#metadata.squash_notes.oldest_first>oldest_first</span> | <code><a href="#bool">bool</a></code><br><p>If set to true, the list shows the oldest changes first. Otherwise it shows the changes in descending order.</p>
<span id=metadata.squash_notes.use_merge href=#metadata.squash_notes.use_merge>use_merge</span> | <code><a href="#bool">bool</a></code><br><p>If true then merge changes are included in the squash notes</p>


<h4 id="example.metadata.squash_notes">Examples:</h4>


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

<code><a href="#transformation">transformation</a></code> <code>metadata.use_last_change(<a href=#metadata.use_last_change.author>author</a>=True, <a href=#metadata.use_last_change.message>message</a>=True, <a href=#metadata.use_last_change.default_message>default_message</a>=None, <a href=#metadata.use_last_change.use_merge>use_merge</a>=True)</code>


<h4 id="parameters.metadata.use_last_change">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=metadata.use_last_change.author href=#metadata.use_last_change.author>author</span> | <code><a href="#bool">bool</a></code><br><p>Replace author with the last change author (Could still be the default author if not on the allowlist or using `authoring.overwrite`.)</p>
<span id=metadata.use_last_change.message href=#metadata.use_last_change.message>message</span> | <code><a href="#bool">bool</a></code><br><p>Replace message with last change message.</p>
<span id=metadata.use_last_change.default_message href=#metadata.use_last_change.default_message>default_message</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>Replace message with last change message.</p>
<span id=metadata.use_last_change.use_merge href=#metadata.use_last_change.use_merge>use_merge</span> | <code><a href="#bool">bool</a></code><br><p>If true then merge changes are taken into account for looking for the last change.</p>

<a id="metadata.verify_match" aria-hidden="true"></a>
### metadata.verify_match

Verifies that a RegEx matches (or not matches) the change message. Does not transform anything, but will stop the workflow if it fails.

<code><a href="#transformation">transformation</a></code> <code>metadata.verify_match(<a href=#metadata.verify_match.regex>regex</a>, <a href=#metadata.verify_match.verify_no_match>verify_no_match</a>=False)</code>


<h4 id="parameters.metadata.verify_match">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=metadata.verify_match.regex href=#metadata.verify_match.regex>regex</span> | <code><a href="#string">string</a></code><br><p>The regex pattern to verify. The re2j pattern will be applied in multiline mode, i.e. '^' refers to the beginning of a file and '$' to its end.</p>
<span id=metadata.verify_match.verify_no_match href=#metadata.verify_match.verify_no_match>verify_no_match</span> | <code><a href="#bool">bool</a></code><br><p>If true, the transformation will verify that the RegEx does not match.</p>


<h4 id="example.metadata.verify_match">Example:</h4>


##### Check that a text is present in the change description:

Check that the change message contains a text enclosed in &lt;public&gt;&lt;/public&gt;:

```python
metadata.verify_match("<public>(.|\n)*</public>")
```




## origin

A Origin represents a source control repository from which source is copied.


<h4 id="returned_by.origin">Returned By:</h4>

<ul><li><a href="#folder.origin">folder.origin</a></li><li><a href="#git.gerrit_origin">git.gerrit_origin</a></li><li><a href="#git.github_origin">git.github_origin</a></li><li><a href="#git.github_pr_origin">git.github_pr_origin</a></li><li><a href="#git.origin">git.origin</a></li><li><a href="#hg.origin">hg.origin</a></li><li><a href="#remotefiles.origin">remotefiles.origin</a></li></ul>
<h4 id="consumed_by.origin">Consumed By:</h4>

<ul><li><a href="#core.workflow">core.workflow</a></li></ul>



## origin_ref

Reference to the change/review in the origin.


<h4 id="fields.origin_ref">Fields:</h4>

Name | Description
---- | -----------
ref | <code><a href="#string">string</a></code><br><p>Origin reference ref</p>


<h4 id="returned_by.origin_ref">Returned By:</h4>

<ul><li><a href="#endpoint.new_origin_ref">endpoint.new_origin_ref</a></li><li><a href="#gerrit_api_obj.new_origin_ref">gerrit_api_obj.new_origin_ref</a></li><li><a href="#github_api_obj.new_origin_ref">github_api_obj.new_origin_ref</a></li><li><a href="#http_endpoint.new_origin_ref">http_endpoint.new_origin_ref</a></li></ul>
<h4 id="consumed_by.origin_ref">Consumed By:</h4>

<ul><li><a href="#feedback.context.record_effect">feedback.context.record_effect</a></li><li><a href="#feedback.finish_hook_context.record_effect">feedback.finish_hook_context.record_effect</a></li><li><a href="#git.mirrorContext.record_effect">git.mirrorContext.record_effect</a></li></ul>



## output_obj

Descriptive details about the run.


<h4 id="fields.output_obj">Fields:</h4>

Name | Description
---- | -----------
summary | <code><a href="#string">string</a></code><br><p>The summary of the check run.</p>
text | <code><a href="#string">string</a></code><br><p>The details of the check run.</p>
title | <code><a href="#string">string</a></code><br><p>The title of the check run.</p>



## patch

Module for applying patches.

<a id="patch.apply" aria-hidden="true"></a>
### patch.apply

A transformation that applies the given patch files. If a path does not exist in a patch, it will be ignored.

<code><a href="#transformation">transformation</a></code> <code>patch.apply(<a href=#patch.apply.patches>patches</a>=[], <a href=#patch.apply.excluded_patch_paths>excluded_patch_paths</a>=[], <a href=#patch.apply.series>series</a>=None, <a href=#patch.apply.strip>strip</a>=1, <a href=#patch.apply.directory>directory</a>='')</code>


<h4 id="parameters.patch.apply">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=patch.apply.patches href=#patch.apply.patches>patches</span> | <code>sequence of <a href="#string">string</a></code><br><p>The list of patchfiles to apply, relative to the current config file. The files will be applied relative to the checkout dir and the leading path component will be stripped (-p1).<br><br>If `series` is also specified, these patches will be applied before those ones.<br><br>**This field doesn't accept a glob.**</p>
<span id=patch.apply.excluded_patch_paths href=#patch.apply.excluded_patch_paths>excluded_patch_paths</span> | <code>sequence of <a href="#string">string</a></code><br><p>The list of paths to exclude from each of the patches. Each of the paths will be excluded from all the patches. Note that these are not workdir paths, but paths relative to the patch itself. If not empty, the patch will be applied using 'git apply' instead of GNU Patch.</p>
<span id=patch.apply.series href=#patch.apply.series>series</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>A file which contains a list of patches to apply. The patch files to apply are interpreted relative to this file and must be written one per line. The patches listed in this file will be applied relative to the checkout dir and the leading path component will be stripped (via the `-p1` flag).<br><br>You can generate a file which matches this format by running 'find . -name *.patch &#124; sort > series'.<br><br>If `patches` is also specified, those patches will be applied before these ones.</p>
<span id=patch.apply.strip href=#patch.apply.strip>strip</span> | <code><a href="#int">int</a></code><br><p>Number of segments to strip. (This sets the `-pX` flag, for example `-p0`, `-p1`, etc.) By default it uses `-p1`.</p>
<span id=patch.apply.directory href=#patch.apply.directory>directory</span> | <code><a href="#string">string</a></code><br><p>Path relative to the working directory from which to apply patches. This supports patches that specify relative paths in their file diffs but use a different relative path base than the working directory. (This sets the `-d` flag, for example `-d sub/dir/`). By default, it uses the current directory.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--patch-skip-version-check`</span> | *boolean* | Skip checking the version of patch and assume it is fine
<span style="white-space: nowrap;">`--patch-use-git-apply`</span> | *boolean* | Don't use GNU Patch and instead use 'git apply'
<span style="white-space: nowrap;">`--quilt-bin`</span> | *string* | Path to quilt command

<a id="patch.quilt_apply" aria-hidden="true"></a>
### patch.quilt_apply

A transformation that applies and updates patch files using Quilt. Compared to `patch.apply`, this transformation supports updating the content of patch files if they can be successfully applied with fuzz. The patch files must be included in the destination_files glob in order to get updated. Underneath, Copybara runs `quilt import; quilt push; quilt refresh` for each patch file in the `series` file in order. Currently, all patch files and the `series` file must reside in a "patches" sub-directory under the root directory containing the migrated code. This means it has the limitation that the migrated code itself cannot contain a directory with the name "patches".

<code><a href="#transformation">transformation</a></code> <code>patch.quilt_apply(<a href=#patch.quilt_apply.series>series</a>)</code>


<h4 id="parameters.patch.quilt_apply">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=patch.quilt_apply.series href=#patch.quilt_apply.series>series</span> | <code><a href="#string">string</a></code><br><p>A file which contains a list of patches to apply. It is similar to the `series` parameter in `patch.apply` transformation, and is required for Quilt. Patches listed in this file will be applied relative to the checkout dir, and the leading path component is stripped via the `-p1` flag. Currently this file should be the `patches/series` file in the root directory of the migrated code.</p>


<h4 id="example.patch.quilt_apply">Example:</h4>


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
<span style="white-space: nowrap;">`--patch-skip-version-check`</span> | *boolean* | Skip checking the version of patch and assume it is fine
<span style="white-space: nowrap;">`--patch-use-git-apply`</span> | *boolean* | Don't use GNU Patch and instead use 'git apply'
<span style="white-space: nowrap;">`--quilt-bin`</span> | *string* | Path to quilt command



## Path

Represents a path in the checkout directory


<h4 id="fields.Path">Fields:</h4>

Name | Description
---- | -----------
attr | <code><a href="#pathattributes">PathAttributes</a></code><br><p>Get the file attributes, for example size.</p>
name | <code><a href="#string">string</a></code><br><p>Filename of the path. For foo/bar/baz.txt it would be baz.txt</p>
parent | <code>unknown</code><br><p>Get the parent path</p>
path | <code><a href="#string">string</a></code><br><p>Full path relative to the checkout directory</p>


<h4 id="returned_by.Path">Returned By:</h4>

<ul><li><a href="#path.read_symlink">path.read_symlink</a></li><li><a href="#path.relativize">path.relativize</a></li><li><a href="#path.resolve">path.resolve</a></li><li><a href="#path.resolve_sibling">path.resolve_sibling</a></li><li><a href="#ctx.new_path">ctx.new_path</a></li></ul>
<h4 id="consumed_by.Path">Consumed By:</h4>

<ul><li><a href="#archive.create">archive.create</a></li><li><a href="#archive.extract">archive.extract</a></li><li><a href="#compression.unzip_path">compression.unzip_path</a></li><li><a href="#destination_reader.copy_destination_files">destination_reader.copy_destination_files</a></li><li><a href="#hashing.path_md5_sum">hashing.path_md5_sum</a></li><li><a href="#hashing.path_sha256_sum">hashing.path_sha256_sum</a></li><li><a href="#http.multipart_form_file">http.multipart_form_file</a></li><li><a href="#http_response.download">http_response.download</a></li><li><a href="#path.relativize">path.relativize</a></li><li><a href="#path.resolve">path.resolve</a></li><li><a href="#path.resolve_sibling">path.resolve_sibling</a></li><li><a href="#python.parse_metadata">python.parse_metadata</a></li><li><a href="#ctx.create_symlink">ctx.create_symlink</a></li><li><a href="#ctx.read_path">ctx.read_path</a></li><li><a href="#ctx.set_executable">ctx.set_executable</a></li><li><a href="#ctx.write_path">ctx.write_path</a></li></ul>

<a id="path.exists" aria-hidden="true"></a>
### path.exists

Check whether a file, directory or symlink exists at this path

<code><a href="#bool">bool</a></code> <code>path.exists()</code>

<a id="path.read_symlink" aria-hidden="true"></a>
### path.read_symlink

Read the symlink

<code><a href="#path">Path</a></code> <code>path.read_symlink()</code>

<a id="path.relativize" aria-hidden="true"></a>
### path.relativize

Constructs a relative path between this path and a given path. For example:<br>    path('a/b').relativize('a/b/c/d')<br>returns 'c/d'

<code><a href="#path">Path</a></code> <code>path.relativize(<a href=#path.relativize.other>other</a>)</code>


<h4 id="parameters.path.relativize">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=path.relativize.other href=#path.relativize.other>other</span> | <code><a href="#path">Path</a></code><br><p>The path to relativize against this path</p>

<a id="path.remove" aria-hidden="true"></a>
### path.remove

Delete self

<code>path.remove()</code>

<a id="path.resolve" aria-hidden="true"></a>
### path.resolve

Resolve the given path against this path.

<code><a href="#path">Path</a></code> <code>path.resolve(<a href=#path.resolve.child>child</a>)</code>


<h4 id="parameters.path.resolve">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=path.resolve.child href=#path.resolve.child>child</span> | <code><a href="#string">string</a></code> or <code><a href="#path">Path</a></code><br><p>Resolve the given path against this path. The parameter can be a string or a Path.</p>

<a id="path.resolve_sibling" aria-hidden="true"></a>
### path.resolve_sibling

Resolve the given path against this path.

<code><a href="#path">Path</a></code> <code>path.resolve_sibling(<a href=#path.resolve_sibling.other>other</a>)</code>


<h4 id="parameters.path.resolve_sibling">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=path.resolve_sibling.other href=#path.resolve_sibling.other>other</span> | <code><a href="#string">string</a></code> or <code><a href="#path">Path</a></code><br><p>Resolve the given path against this path. The parameter can be a string or a Path.</p>

<a id="path.rmdir" aria-hidden="true"></a>
### path.rmdir

Delete all files in a directory. If recursive is true, delete descendants of all files in directory

<code>path.rmdir(<a href=#path.rmdir.recursive>recursive</a>=False)</code>


<h4 id="parameters.path.rmdir">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=path.rmdir.recursive href=#path.rmdir.recursive>recursive</span> | <code><a href="#bool">bool</a></code><br><p>When true, delete descendants of self and of siblings</p>



## PathAttributes

Represents a path attributes like size.


<h4 id="fields.PathAttributes">Fields:</h4>

Name | Description
---- | -----------
size | <code><a href="#int">int</a></code><br><p>The size of the file. Throws an error if file size > 2GB.</p>
symlink | <code><a href="#bool">bool</a></code><br><p>Returns true if it is a symlink</p>



## python

utilities for interacting with the pypi package manager

<a id="python.parse_metadata" aria-hidden="true"></a>
### python.parse_metadata

Extract the metadata from a python METADATA file into a dictionary. Returns a list of key value tuples.

<code>list of tuple</code> <code>python.parse_metadata(<a href=#python.parse_metadata.path>path</a>)</code>


<h4 id="parameters.python.parse_metadata">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=python.parse_metadata.path href=#python.parse_metadata.path>path</span> | <code><a href="#path">Path</a></code><br><p>path relative to workdir root of the .whl file</p>



## random

A module for randomization-related functions.

<a id="random.sample" aria-hidden="true"></a>
### random.sample

Returns a list of k unique elements randomly sampled from the list.

<code>sequence</code> <code>random.sample(<a href=#random.sample.population>population</a>, <a href=#random.sample.k>k</a>)</code>


<h4 id="parameters.random.sample">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=random.sample.population href=#random.sample.population>population</span> | <code><a href="#list">list</a></code><br><p>The list to sample from.</p>
<span id=random.sample.k href=#random.sample.k>k</span> | <code><a href="#int">int</a></code><br><p>The number of elements to sample from the population list.</p>



## re2

Set of functions to work with regexes in Copybara.

<a id="re2.compile" aria-hidden="true"></a>
### re2.compile

Create a regex pattern

<code><a href="#re2_pattern">re2_pattern</a></code> <code>re2.compile(<a href=#re2.compile.regex>regex</a>)</code>


<h4 id="parameters.re2.compile">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=re2.compile.regex href=#re2.compile.regex>regex</span> | <code><a href="#string">string</a></code><br><p></p>


<h4 id="example.re2.compile">Example:</h4>


##### Simple regex:

Patterns need to be compiled before using them:

```python
re2.compile("a(.*)b").matches('accccb')
```


<a id="re2.quote" aria-hidden="true"></a>
### re2.quote

Quote a string to be matched literally if used within a regex pattern

<code><a href="#string">string</a></code> <code>re2.quote(<a href=#re2.quote.string>string</a>)</code>


<h4 id="parameters.re2.quote">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=re2.quote.string href=#re2.quote.string>string</span> | <code><a href="#string">string</a></code><br><p></p>



## re2_matcher

A RE2 regex pattern matcher object to perform regexes in Starlark


<h4 id="returned_by.re2_matcher">Returned By:</h4>

<ul><li><a href="#re2_pattern.matcher">re2_pattern.matcher</a></li></ul>

<a id="re2_matcher.end" aria-hidden="true"></a>
### re2_matcher.end

Return the end position of a matching group

<code><a href="#int">int</a></code> <code>re2_matcher.end(<a href=#re2_matcher.end.group>group</a>=0)</code>


<h4 id="parameters.re2_matcher.end">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=re2_matcher.end.group href=#re2_matcher.end.group>group</span> | <code><a href="#int">int</a></code> or <code><a href="#string">string</a></code><br><p></p>

<a id="re2_matcher.find" aria-hidden="true"></a>
### re2_matcher.find

Return true if the string matches the regex pattern.

<code><a href="#bool">bool</a></code> <code>re2_matcher.find(<a href=#re2_matcher.find.start>start</a>=None)</code>


<h4 id="parameters.re2_matcher.find">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=re2_matcher.find.start href=#re2_matcher.find.start>start</span> | <code><a href="#int">int</a></code> or <code>NoneType</code><br><p>The input position where the search begins</p>

<a id="re2_matcher.group" aria-hidden="true"></a>
### re2_matcher.group

Return a matching group

<code><a href="#string">string</a></code> <code>re2_matcher.group(<a href=#re2_matcher.group.group>group</a>=0)</code>


<h4 id="parameters.re2_matcher.group">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=re2_matcher.group.group href=#re2_matcher.group.group>group</span> | <code><a href="#int">int</a></code> or <code><a href="#string">string</a></code><br><p></p>

<a id="re2_matcher.group_count" aria-hidden="true"></a>
### re2_matcher.group_count

Return the number of groups found for a match

<code><a href="#int">int</a></code> <code>re2_matcher.group_count()</code>

<a id="re2_matcher.matches" aria-hidden="true"></a>
### re2_matcher.matches

Return true if the string matches the regex pattern.

<code><a href="#bool">bool</a></code> <code>re2_matcher.matches()</code>

<a id="re2_matcher.replace_all" aria-hidden="true"></a>
### re2_matcher.replace_all

Replace all instances matching the regex

<code><a href="#string">string</a></code> <code>re2_matcher.replace_all(<a href=#re2_matcher.replace_all.replacement>replacement</a>=0)</code>


<h4 id="parameters.re2_matcher.replace_all">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=re2_matcher.replace_all.replacement href=#re2_matcher.replace_all.replacement>replacement</span> | <code><a href="#string">string</a></code><br><p></p>

<a id="re2_matcher.replace_first" aria-hidden="true"></a>
### re2_matcher.replace_first

Replace the first instance matching the regex

<code><a href="#string">string</a></code> <code>re2_matcher.replace_first(<a href=#re2_matcher.replace_first.replacement>replacement</a>=0)</code>


<h4 id="parameters.re2_matcher.replace_first">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=re2_matcher.replace_first.replacement href=#re2_matcher.replace_first.replacement>replacement</span> | <code><a href="#string">string</a></code><br><p></p>

<a id="re2_matcher.start" aria-hidden="true"></a>
### re2_matcher.start

Return the start position of a matching group

<code><a href="#int">int</a></code> <code>re2_matcher.start(<a href=#re2_matcher.start.group>group</a>=0)</code>


<h4 id="parameters.re2_matcher.start">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=re2_matcher.start.group href=#re2_matcher.start.group>group</span> | <code><a href="#int">int</a></code> or <code><a href="#string">string</a></code><br><p></p>



## re2_pattern

A RE2 regex pattern object to perform regexes in Starlark


<h4 id="returned_by.re2_pattern">Returned By:</h4>

<ul><li><a href="#re2.compile">re2.compile</a></li></ul>

<a id="re2_pattern.matcher" aria-hidden="true"></a>
### re2_pattern.matcher

Return a Matcher for the given input.

<code><a href="#re2_matcher">re2_matcher</a></code> <code>re2_pattern.matcher(<a href=#re2_pattern.matcher.input>input</a>)</code>


<h4 id="parameters.re2_pattern.matcher">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=re2_pattern.matcher.input href=#re2_pattern.matcher.input>input</span> | <code><a href="#string">string</a></code><br><p></p>

<a id="re2_pattern.matches" aria-hidden="true"></a>
### re2_pattern.matches

Return true if the string matches the regex pattern

<code><a href="#bool">bool</a></code> <code>re2_pattern.matches(<a href=#re2_pattern.matches.input>input</a>)</code>


<h4 id="parameters.re2_pattern.matches">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=re2_pattern.matches.input href=#re2_pattern.matches.input>input</span> | <code><a href="#string">string</a></code><br><p></p>



## remotefiles

Functions to access remote files not in either repo.



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--remote-http-files-connection-timeout`</span> | *duration* | Timeout for the fetch operation, e.g. 30s.  Example values: 30s, 20m, 1h, etc.

<a id="remotefiles.origin" aria-hidden="true"></a>
### remotefiles.origin

Defines a remote file origin.

<code><a href="#origin">origin</a></code> <code>remotefiles.origin(<a href=#remotefiles.origin.author>author</a>='Copybara <noreply@copybara.io>', <a href=#remotefiles.origin.message>message</a>='Placeholder message', <a href=#remotefiles.origin.unpack_method>unpack_method</a>='AS_IS', <a href=#remotefiles.origin.archive_source>archive_source</a>='', <a href=#remotefiles.origin.version_list>version_list</a>=None, <a href=#remotefiles.origin.origin_version_selector>origin_version_selector</a>=None, <a href=#remotefiles.origin.version_resolver>version_resolver</a>=None, <a href=#remotefiles.origin.auth>auth</a>=None)</code>


<h4 id="parameters.remotefiles.origin">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=remotefiles.origin.author href=#remotefiles.origin.author>author</span> | <code><a href="#string">string</a></code><br><p>Author to attribute the change to</p>
<span id=remotefiles.origin.message href=#remotefiles.origin.message>message</span> | <code><a href="#string">string</a></code><br><p>Message to attach to the change</p>
<span id=remotefiles.origin.unpack_method href=#remotefiles.origin.unpack_method>unpack_method</span> | <code><a href="#string">string</a></code><br><p>The method by which to unpack the remote file. Currently 'ZIP', 'TAR', 'TAR_GZ', 'TAR_XZ', 'TAR_BZ2', and 'AS_IS' are supported.</p>
<span id=remotefiles.origin.archive_source href=#remotefiles.origin.archive_source>archive_source</span> | <code><a href="#string">string</a></code><br><p>Template or literal URL to download archive from. Optionally you can use ${VERSION} in your URL string as placeholder for later resolved versions during origin checkout. E.g. 'https://proxy.golang.org/mymodule/@v/${VERSION}.zip'</p>
<span id=remotefiles.origin.version_list href=#remotefiles.origin.version_list>version_list</span> | <code>VersionList</code> or <code>NoneType</code><br><p>Version list to select versions on. Omit to create a versionless origin.</p>
<span id=remotefiles.origin.origin_version_selector href=#remotefiles.origin.origin_version_selector>origin_version_selector</span> | <code><a href="#versionselector">VersionSelector</a></code> or <code>NoneType</code><br><p>Version selector used to select on version_list. Omit to create a versionless origin.</p>
<span id=remotefiles.origin.version_resolver href=#remotefiles.origin.version_resolver>version_resolver</span> | <code>VersionResolver</code> or <code>NoneType</code><br><p>Version resolvers are used to resolve refs to specific versions. Primarily used when command line refs are provided and accompanied by the '--force' or '--version-selector-use-cli-ref' flag.</p>
<span id=remotefiles.origin.auth href=#remotefiles.origin.auth>auth</span> | <code>AuthInterceptor</code> or <code>NoneType</code><br><p>Optional, an interceptor for providing credentials.</p>



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--remote-http-files-connection-timeout`</span> | *duration* | Timeout for the fetch operation, e.g. 30s.  Example values: 30s, 20m, 1h, etc.



## rust_version_requirement

Represents a Cargo version requirement.

<a id="rust_version_requirement.fulfills" aria-hidden="true"></a>
### rust_version_requirement.fulfills

Given a semantic version string, returns true if the version fulfills this version requirement.

<code><a href="#bool">bool</a></code> <code>rust_version_requirement.fulfills(<a href=#rust_version_requirement.fulfills.fulfills>fulfills</a>)</code>


<h4 id="parameters.rust_version_requirement.fulfills">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=rust_version_requirement.fulfills.fulfills href=#rust_version_requirement.fulfills.fulfills>fulfills</span> | <code><a href="#string">string</a></code><br><p>The version requirement</p>



## set

The built-in set type. A set is a mutable collection of unique values &ndash; the set's
<em>elements</em>. The <a href="../globals/all#type">type name</a> of a set is <code>"set"</code>.

<p>Sets provide constant-time operations to insert, remove, or check for the presence of a value.
Sets are implemented using a hash table, and therefore, just like keys of a
<a href="../dict">dictionary</a>, elements of a set must be hashable. A value may be used as an
element of a set if and only if it may be used as a key of a dictionary.

<p>Sets may be constructed using the <a href="../globals/all#set"><code>set()</code></a> built-in
function, which returns a new set containing the unique elements of its optional argument, which
must be an iterable. Calling <code>set()</code> without an argument constructs an empty set. Sets
have no literal syntax.

<p>The <code>in</code> and <code>not in</code> operations check whether a value is (or is not) in a
set:

<pre class=language-python>
s = set(["a", "b", "c"])
"a" in s  # True
"z" in s  # False
</pre>

<p>A set is iterable, and thus may be used as the operand of a <code>for</code> loop, a list
comprehension, and the various built-in functions that operate on iterables. Its length can be
retrieved using the <a href="../globals/all#len"><code>len()</code></a> built-in function, and the
order of iteration is the order in which elements were first added to the set:

<pre class=language-python>
s = set(["z", "y", "z", "y"])
len(s)       # prints 2
s.add("x")
len(s)       # prints 3
for e in s:
    print e  # prints "z", "y", "x"
</pre>

<p>A set used in Boolean context is true if and only if it is non-empty.

<pre class=language-python>
s = set()
"non-empty" if s else "empty"  # "empty"
t = set(["x", "y"])
"non-empty" if t else "empty"  # "non-empty"
</pre>

<p>Sets may be compared for equality or inequality using <code>==</code> and <code>!=</code>. A set
<code>s</code> is equal to <code>t</code> if and only if <code>t</code> is a set containing the same
elements; iteration order is not significant. In particular, a set is <em>not</em> equal to the list
of its elements. Sets are not ordered with respect to other sets, and an attempt to compare two sets
using <code>&lt;</code>, <code>&lt;=</code>, <code>&gt;</code>, <code>&gt;=</code>, or to sort a
sequence of sets, will fail.

<pre class=language-python>
set() == set()              # True
set() != []                 # True
set([1, 2]) == set([2, 1])  # True
set([1, 2]) != [1, 2]       # True
</pre>

<p>The <code>|</code> operation on two sets returns the union of the two sets: a set containing the
elements found in either one or both of the original sets.

<pre class=language-python>
set([1, 2]) | set([3, 2])  # set([1, 2, 3])
</pre>

<p>The <code>&amp;</code> operation on two sets returns the intersection of the two sets: a set
containing only the elements found in both of the original sets.

<pre class=language-python>
set([1, 2]) &amp; set([2, 3])  # set([2])
set([1, 2]) &amp; set([3, 4])  # set()
</pre>

<p>The <code>-</code> operation on two sets returns the difference of the two sets: a set containing
the elements found in the left-hand side set but not the right-hand side set.

<pre class=language-python>
set([1, 2]) - set([2, 3])  # set([1])
set([1, 2]) - set([3, 4])  # set([1, 2])
</pre>

<p>The <code>^</code> operation on two sets returns the symmetric difference of the two sets: a set
containing the elements found in exactly one of the two original sets, but not in both.

<pre class=language-python>
set([1, 2]) ^ set([2, 3])  # set([1, 3])
set([1, 2]) ^ set([3, 4])  # set([1, 2, 3, 4])
</pre>

<p>In each of the above operations, the elements of the resulting set retain their order from the
two operand sets, with all elements that were drawn from the left-hand side ordered before any
element that was only present in the right-hand side.

<p>The corresponding augmented assignments, <code>|=</code>, <code>&amp;=</code>, <code>-=</code>,
and <code>^=</code>, modify the left-hand set in place.

<pre class=language-python>
s = set([1, 2])
s |= set([2, 3, 4])     # s now equals set([1, 2, 3, 4])
s &amp;= set([0, 1, 2, 3])  # s now equals set([1, 2, 3])
s -= set([0, 1])        # s now equals set([2, 3])
s ^= set([3, 4])        # s now equals set([2, 4])
</pre>

<p>Like all mutable values in Starlark, a set can be frozen, and once frozen, all subsequent
operations that attempt to update it will fail.


<a id="set.add" aria-hidden="true"></a>
### set.add

Adds an element to the set.

<p>It is permissible to <code>add</code> a value already present in the set; this leaves the set
unchanged.

<p>If you need to add multiple elements to a set, see <a href="#update"><code>update</code></a> or
the <code>|=</code> augmented assignment operation.


<code>set.add(<a href=#set.add.element>element</a>)</code>


<h4 id="parameters.set.add">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=set.add.element href=#set.add.element>element</span> | <code>?</code><br><p>Element to add.</p>

<a id="set.clear" aria-hidden="true"></a>
### set.clear

Removes all the elements of the set.

<code>set.clear()</code>

<a id="set.difference" aria-hidden="true"></a>
### set.difference

Returns a new mutable set containing the difference of this set with others.

<p>If <code>s</code> and <code>t</code> are sets, <code>s.difference(t)</code> is equivalent to
<code>s - t</code>; however, note that the <code>-</code> operation requires both sides to be sets,
while the <code>difference</code> method also accepts sequences and dicts.

<p>It is permissible to call <code>difference</code> without any arguments; this returns a copy of
the set.

<p>For example,
<pre class=language-python>
set([1, 2, 3]).difference([2])             # set([1, 3])
set([1, 2, 3]).difference([0, 1], [3, 4])  # set([2])
</pre>


<code>sequence</code> <code>set.difference(<a href=#set.difference.others>others</a>)</code>


<h4 id="parameters.set.difference">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=set.difference.others href=#set.difference.others>others</span> | <code><a href="#list">list</a></code><br><p>Collections of hashable elements.</p>

<a id="set.difference_update" aria-hidden="true"></a>
### set.difference_update

Removes any elements found in any others from this set.

<p>If <code>s</code> and <code>t</code> are sets, <code>s.difference_update(t)</code> is equivalent
to <code>s -= t</code>; however, note that the <code>-=</code> augmented assignment requires both
sides to be sets, while the <code>difference_update</code> method also accepts sequences and dicts.

<p>It is permissible to call <code>difference_update</code> without any arguments; this leaves the
set unchanged.

<p>For example,
<pre class=language-python>
s = set([1, 2, 3, 4])
s.difference_update([2])             # None; s is set([1, 3, 4])
s.difference_update([0, 1], [4, 5])  # None; s is set([3])
</pre>


<code>set.difference_update(<a href=#set.difference_update.others>others</a>)</code>


<h4 id="parameters.set.difference_update">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=set.difference_update.others href=#set.difference_update.others>others</span> | <code><a href="#list">list</a></code><br><p>Collections of hashable elements.</p>

<a id="set.discard" aria-hidden="true"></a>
### set.discard

Removes an element from the set if it is present.

<p>It is permissible to <code>discard</code> a value not present in the set; this leaves the set
unchanged. If you want to fail on an attempt to remove a non-present element, use
<a href="#remove"><code>remove</code></a> instead. If you need to remove multiple elements from a
set, see <a href="#difference_update"><code>difference_update</code></a> or the <code>-=</code>
augmented assignment operation.

<p>For example,
<pre class=language-python>
s = set(["x", "y"])
s.discard("y")  # None; s == set(["x"])
s.discard("y")  # None; s == set(["x"])
</pre>


<code>set.discard(<a href=#set.discard.element>element</a>)</code>


<h4 id="parameters.set.discard">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=set.discard.element href=#set.discard.element>element</span> | <code>?</code><br><p>Element to discard. Must be hashable.</p>

<a id="set.intersection" aria-hidden="true"></a>
### set.intersection

Returns a new mutable set containing the intersection of this set with others.

<p>If <code>s</code> and <code>t</code> are sets, <code>s.intersection(t)</code> is equivalent to
<code>s &amp; t</code>; however, note that the <code>&amp;</code> operation requires both sides to
be sets, while the <code>intersection</code> method also accepts sequences and dicts.

<p>It is permissible to call <code>intersection</code> without any arguments; this returns a copy of
the set.

<p>For example,
<pre class=language-python>
set([1, 2]).intersection([2, 3])             # set([2])
set([1, 2, 3]).intersection([0, 1], [1, 2])  # set([1])
</pre>


<code>sequence</code> <code>set.intersection(<a href=#set.intersection.others>others</a>)</code>


<h4 id="parameters.set.intersection">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=set.intersection.others href=#set.intersection.others>others</span> | <code><a href="#list">list</a></code><br><p>Collections of hashable elements.</p>

<a id="set.intersection_update" aria-hidden="true"></a>
### set.intersection_update

Removes any elements not found in all others from this set.

<p>If <code>s</code> and <code>t</code> are sets, <code>s.intersection_update(t)</code> is
equivalent to <code>s &amp;= t</code>; however, note that the <code>&amp;=</code> augmented
assignment requires both sides to be sets, while the <code>intersection_update</code> method also
accepts sequences and dicts.

<p>It is permissible to call <code>intersection_update</code> without any arguments; this leaves the
set unchanged.

<p>For example,
<pre class=language-python>
s = set([1, 2, 3, 4])
s.intersection_update([0, 1, 2])       # None; s is set([1, 2])
s.intersection_update([0, 1], [1, 2])  # None; s is set([1])
</pre>


<code>set.intersection_update(<a href=#set.intersection_update.others>others</a>)</code>


<h4 id="parameters.set.intersection_update">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=set.intersection_update.others href=#set.intersection_update.others>others</span> | <code><a href="#list">list</a></code><br><p>Collections of hashable elements.</p>

<a id="set.isdisjoint" aria-hidden="true"></a>
### set.isdisjoint

Returns true if this set has no elements in common with another.

<p>For example,
<pre class=language-python>
set([1, 2]).isdisjoint([3, 4])  # True
set().isdisjoint(set())         # True
set([1, 2]).isdisjoint([2, 3])  # False
</pre>


<code><a href="#bool">bool</a></code> <code>set.isdisjoint(<a href=#set.isdisjoint.other>other</a>)</code>


<h4 id="parameters.set.isdisjoint">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=set.isdisjoint.other href=#set.isdisjoint.other>other</span> | <code>unknown</code><br><p>A collection of hashable elements.</p>

<a id="set.issubset" aria-hidden="true"></a>
### set.issubset

Returns true of this set is a subset of another.

<p>Note that a set is always considered to be a subset of itself.

<p>For example,
<pre class=language-python>
set([1, 2]).issubset([1, 2, 3])  # True
set([1, 2]).issubset([1, 2])     # True
set([1, 2]).issubset([2, 3])     # False
</pre>


<code><a href="#bool">bool</a></code> <code>set.issubset(<a href=#set.issubset.other>other</a>)</code>


<h4 id="parameters.set.issubset">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=set.issubset.other href=#set.issubset.other>other</span> | <code>unknown</code><br><p>A collection of hashable elements.</p>

<a id="set.issuperset" aria-hidden="true"></a>
### set.issuperset

Returns true of this set is a superset of another.

<p>Note that a set is always considered to be a superset of itself.

<p>For example,
<pre class=language-python>
set([1, 2, 3]).issuperset([1, 2])     # True
set([1, 2, 3]).issuperset([1, 2, 3])  # True
set([1, 2, 3]).issuperset([2, 3, 4])  # False
</pre>


<code><a href="#bool">bool</a></code> <code>set.issuperset(<a href=#set.issuperset.other>other</a>)</code>


<h4 id="parameters.set.issuperset">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=set.issuperset.other href=#set.issuperset.other>other</span> | <code>unknown</code><br><p>A collection of hashable elements.</p>

<a id="set.pop" aria-hidden="true"></a>
### set.pop

Removes and returns the first element of the set (in iteration order, which is the order in which
elements were first added to the set).

<p>Fails if the set is empty.

<p>For example,
<pre class=language-python>
s = set([3, 1, 2])
s.pop()  # 3; s == set([1, 2])
s.pop()  # 1; s == set([2])
s.pop()  # 2; s == set()
s.pop()  # error: empty set
</pre>


<code>?</code> <code>set.pop()</code>

<a id="set.remove" aria-hidden="true"></a>
### set.remove

Removes an element, which must be present in the set, from the set.

<p><code>remove</code> fails if the element was not present in the set. If you don't want to fail on
an attempt to remove a non-present element, use <a href="#discard"><code>discard</code></a> instead.
If you need to remove multiple elements from a set, see
<a href="#difference_update"><code>difference_update</code></a> or the <code>-=</code> augmented
assignment operation.


<code>set.remove(<a href=#set.remove.element>element</a>)</code>


<h4 id="parameters.set.remove">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=set.remove.element href=#set.remove.element>element</span> | <code>?</code><br><p>Element to remove. Must be an element of the set (and hashable).</p>

<a id="set.symmetric_difference" aria-hidden="true"></a>
### set.symmetric_difference

Returns a new mutable set containing the symmetric difference of this set with another collection of
hashable elements.

<p>If <code>s</code> and <code>t</code> are sets, <code>s.symmetric_difference(t)</code> is
equivalent to <code>s ^ t</code>; however, note that the <code>^</code> operation requires both
sides to be sets, while the <code>symmetric_difference</code> method also accepts a sequence or a
dict.

<p>For example,
<pre class=language-python>
set([1, 2]).symmetric_difference([2, 3])  # set([1, 3])
</pre>


<code>sequence</code> <code>set.symmetric_difference(<a href=#set.symmetric_difference.other>other</a>)</code>


<h4 id="parameters.set.symmetric_difference">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=set.symmetric_difference.other href=#set.symmetric_difference.other>other</span> | <code>unknown</code><br><p>A collection of hashable elements.</p>

<a id="set.symmetric_difference_update" aria-hidden="true"></a>
### set.symmetric_difference_update

Returns a new mutable set containing the symmetric difference of this set with another collection of
hashable elements.

<p>If <code>s</code> and <code>t</code> are sets, <code>s.symmetric_difference_update(t)</code> is
equivalent to `s ^= t<code>; however, note that the </code>^=` augmented assignment requires both
sides to be sets, while the <code>symmetric_difference_update</code> method also accepts a sequence
or a dict.

<p>For example,
<pre class=language-python>
s = set([1, 2])
s.symmetric_difference_update([2, 3])  # None; s == set([1, 3])
</pre>


<code>set.symmetric_difference_update(<a href=#set.symmetric_difference_update.other>other</a>)</code>


<h4 id="parameters.set.symmetric_difference_update">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=set.symmetric_difference_update.other href=#set.symmetric_difference_update.other>other</span> | <code>unknown</code><br><p>A collection of hashable elements.</p>

<a id="set.union" aria-hidden="true"></a>
### set.union

Returns a new mutable set containing the union of this set with others.

<p>If <code>s</code> and <code>t</code> are sets, <code>s.union(t)</code> is equivalent to
<code>s | t</code>; however, note that the <code>|</code> operation requires both sides to be sets,
while the <code>union</code> method also accepts sequences and dicts.

<p>It is permissible to call <code>union</code> without any arguments; this returns a copy of the
set.

<p>For example,
<pre class=language-python>
set([1, 2]).union([2, 3])                    # set([1, 2, 3])
set([1, 2]).union([2, 3], {3: "a", 4: "b"})  # set([1, 2, 3, 4])
</pre>


<code>sequence</code> <code>set.union(<a href=#set.union.others>others</a>)</code>


<h4 id="parameters.set.union">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=set.union.others href=#set.union.others>others</span> | <code><a href="#list">list</a></code><br><p>Collections of hashable elements.</p>

<a id="set.update" aria-hidden="true"></a>
### set.update

Adds the elements found in others to this set.

<p>For example,
<pre class=language-python>
s = set()
s.update([1, 2])          # None; s is set([1, 2])
s.update([2, 3], [3, 4])  # None; s is set([1, 2, 3, 4])
</pre>

<p>If <code>s</code> and <code>t</code> are sets, <code>s.update(t)</code> is equivalent to
<code>s |= t</code>; however, note that the <code>|=</code> augmented assignment requires both sides
to be sets, while the <code>update</code> method also accepts sequences and dicts.

<p>It is permissible to call <code>update</code> without any arguments; this leaves the set
unchanged.


<code>set.update(<a href=#set.update.others>others</a>)</code>


<h4 id="parameters.set.update">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=set.update.others href=#set.update.others>others</span> | <code><a href="#list">list</a></code><br><p>Collections of hashable elements.</p>



## SetReviewInput

Input for posting a review to Gerrit. See https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#review-input


<h4 id="returned_by.SetReviewInput">Returned By:</h4>

<ul><li><a href="#git.review_input">git.review_input</a></li></ul>
<h4 id="consumed_by.SetReviewInput">Consumed By:</h4>

<ul><li><a href="#gerrit_api_obj.post_review">gerrit_api_obj.post_review</a></li></ul>



## StarlarkDateTime

Starlark datetime object


<h4 id="returned_by.StarlarkDateTime">Returned By:</h4>

<ul><li><a href="#datetime.fromtimestamp">datetime.fromtimestamp</a></li><li><a href="#datetime.now">datetime.now</a></li></ul>

<a id="StarlarkDateTime.in_epoch_seconds" aria-hidden="true"></a>
### StarlarkDateTime.in_epoch_seconds

Returns the time in epoch seconds for the starlark_datetime instance

<code>long</code> <code>StarlarkDateTime.in_epoch_seconds()</code>

<a id="StarlarkDateTime.strftime" aria-hidden="true"></a>
### StarlarkDateTime.strftime

Returns a string representation of the StarlarkDateTime object with your chosen formatting

<code><a href="#string">string</a></code> <code>StarlarkDateTime.strftime(<a href=#StarlarkDateTime.strftime.format>format</a>)</code>


<h4 id="parameters.StarlarkDateTime.strftime">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=StarlarkDateTime.strftime.format href=#StarlarkDateTime.strftime.format>format</span> | <code><a href="#string">string</a></code><br><p>Format string used to present StarlarkDateTime object. See https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html for patterns.</p>



## string

A language built-in type to support strings. Examples of string literals:<br><pre class="language-python">a = 'abc\ndef'
b = "ab'cd"
c = """multiline string"""

# Strings support slicing (negative index starts from the end):
x = "hello"[2:4]  # "ll"
y = "hello"[1:-1]  # "ell"
z = "hello"[:4]  # "hell"
# Slice steps can be used, too:
s = "hello"[::2] # "hlo"
t = "hello"[3:0:-1] # "lle"
</pre>Strings are not directly iterable, use the <code>.elems()</code> method to iterate over their characters. Examples:<br><pre class="language-python">"bc" in "abcd"   # evaluates to True
x = [c for c in "abc".elems()]  # x == ["a", "b", "c"]</pre>
Implicit concatenation of strings is not allowed; use the <code>+</code> operator instead. Comparison operators perform a lexicographical comparison; use <code>==</code> to test for equality.


<h4 id="returned_by.string">Returned By:</h4>

<ul><li><a href="#buildozer.print">buildozer.print</a></li><li><a href="#core.format">core.format</a></li><li><a href="#destination_reader.read_file">destination_reader.read_file</a></li><li><a href="#feedback.revision_context.fill_template">feedback.revision_context.fill_template</a></li><li><a href="#gerrit_api_obj.get_actions">gerrit_api_obj.get_actions</a></li><li><a href="#git.mirrorContext.references">git.mirrorContext.references</a></li><li><a href="#repr">repr</a></li><li><a href="#str">str</a></li><li><a href="#type">type</a></li><li><a href="#hashing.path_md5_sum">hashing.path_md5_sum</a></li><li><a href="#hashing.path_sha256_sum">hashing.path_sha256_sum</a></li><li><a href="#hashing.str_sha256_sum">hashing.str_sha256_sum</a></li><li><a href="#html_element.attr">html_element.attr</a></li><li><a href="#http.url_encode">http.url_encode</a></li><li><a href="#http_response.contents_string">http_response.contents_string</a></li><li><a href="#http_response.status">http_response.status</a></li><li><a href="#re2.quote">re2.quote</a></li><li><a href="#re2_matcher.group">re2_matcher.group</a></li><li><a href="#re2_matcher.replace_all">re2_matcher.replace_all</a></li><li><a href="#re2_matcher.replace_first">re2_matcher.replace_first</a></li><li><a href="#StarlarkDateTime.strftime">StarlarkDateTime.strftime</a></li><li><a href="#string.capitalize">string.capitalize</a></li><li><a href="#string.format">string.format</a></li><li><a href="#string.join">string.join</a></li><li><a href="#string.lower">string.lower</a></li><li><a href="#string.lstrip">string.lstrip</a></li><li><a href="#string.removeprefix">string.removeprefix</a></li><li><a href="#string.removesuffix">string.removesuffix</a></li><li><a href="#string.replace">string.replace</a></li><li><a href="#string.rstrip">string.rstrip</a></li><li><a href="#string.strip">string.strip</a></li><li><a href="#string.title">string.title</a></li><li><a href="#string.upper">string.upper</a></li><li><a href="#ctx.fill_template">ctx.fill_template</a></li><li><a href="#ctx.find_label">ctx.find_label</a></li><li><a href="#ctx.now_as_string">ctx.now_as_string</a></li><li><a href="#ctx.read_path">ctx.read_path</a></li></ul>
<h4 id="consumed_by.string">Consumed By:</h4>

<ul><li><a href="#archive.extract">archive.extract</a></li><li><a href="#authoring.allowed">authoring.allowed</a></li><li><a href="#authoring.overwrite">authoring.overwrite</a></li><li><a href="#authoring.pass_thru">authoring.pass_thru</a></li><li><a href="#buildozer.cmd">buildozer.cmd</a></li><li><a href="#buildozer.create">buildozer.create</a></li><li><a href="#buildozer.delete">buildozer.delete</a></li><li><a href="#buildozer.modify">buildozer.modify</a></li><li><a href="#buildozer.print">buildozer.print</a></li><li><a href="#message.label_values">message.label_values</a></li><li><a href="#console.error">console.error</a></li><li><a href="#console.info">console.info</a></li><li><a href="#console.progress">console.progress</a></li><li><a href="#console.verbose">console.verbose</a></li><li><a href="#console.warn">console.warn</a></li><li><a href="#core.action_migration">core.action_migration</a></li><li><a href="#core.autopatch_config">core.autopatch_config</a></li><li><a href="#core.convert_encoding">core.convert_encoding</a></li><li><a href="#core.copy">core.copy</a></li><li><a href="#core.custom_version_selector">core.custom_version_selector</a></li><li><a href="#core.fail_with_noop">core.fail_with_noop</a></li><li><a href="#core.feedback">core.feedback</a></li><li><a href="#core.filter_replace">core.filter_replace</a></li><li><a href="#core.format">core.format</a></li><li><a href="#core.latest_version">core.latest_version</a></li><li><a href="#core.merge_import_config">core.merge_import_config</a></li><li><a href="#core.move">core.move</a></li><li><a href="#core.rename">core.rename</a></li><li><a href="#core.replace">core.replace</a></li><li><a href="#core.todo_replace">core.todo_replace</a></li><li><a href="#core.transform">core.transform</a></li><li><a href="#core.verify_match">core.verify_match</a></li><li><a href="#core.workflow">core.workflow</a></li><li><a href="#credentials.static_secret">credentials.static_secret</a></li><li><a href="#credentials.static_value">credentials.static_value</a></li><li><a href="#credentials.toml_key_source">credentials.toml_key_source</a></li><li><a href="#datetime.fromtimestamp">datetime.fromtimestamp</a></li><li><a href="#datetime.now">datetime.now</a></li><li><a href="#destination_reader.file_exists">destination_reader.file_exists</a></li><li><a href="#destination_reader.read_file">destination_reader.read_file</a></li><li><a href="#endpoint.new_destination_ref">endpoint.new_destination_ref</a></li><li><a href="#endpoint.new_origin_ref">endpoint.new_origin_ref</a></li><li><a href="#feedback.context.error">feedback.context.error</a></li><li><a href="#feedback.context.noop">feedback.context.noop</a></li><li><a href="#feedback.context.record_effect">feedback.context.record_effect</a></li><li><a href="#feedback.finish_hook_context.error">feedback.finish_hook_context.error</a></li><li><a href="#feedback.finish_hook_context.noop">feedback.finish_hook_context.noop</a></li><li><a href="#feedback.finish_hook_context.record_effect">feedback.finish_hook_context.record_effect</a></li><li><a href="#feedback.revision_context.fill_template">feedback.revision_context.fill_template</a></li><li><a href="#format.buildifier">format.buildifier</a></li><li><a href="#gerrit_api_obj.abandon_change">gerrit_api_obj.abandon_change</a></li><li><a href="#gerrit_api_obj.delete_vote">gerrit_api_obj.delete_vote</a></li><li><a href="#gerrit_api_obj.get_actions">gerrit_api_obj.get_actions</a></li><li><a href="#gerrit_api_obj.get_change">gerrit_api_obj.get_change</a></li><li><a href="#gerrit_api_obj.list_changes">gerrit_api_obj.list_changes</a></li><li><a href="#gerrit_api_obj.new_destination_ref">gerrit_api_obj.new_destination_ref</a></li><li><a href="#gerrit_api_obj.new_origin_ref">gerrit_api_obj.new_origin_ref</a></li><li><a href="#gerrit_api_obj.post_review">gerrit_api_obj.post_review</a></li><li><a href="#gerrit_api_obj.submit_change">gerrit_api_obj.submit_change</a></li><li><a href="#git.destination">git.destination</a></li><li><a href="#git.gerrit_api">git.gerrit_api</a></li><li><a href="#git.gerrit_destination">git.gerrit_destination</a></li><li><a href="#git.gerrit_origin">git.gerrit_origin</a></li><li><a href="#git.gerrit_trigger">git.gerrit_trigger</a></li><li><a href="#git.github_api">git.github_api</a></li><li><a href="#git.github_destination">git.github_destination</a></li><li><a href="#git.github_origin">git.github_origin</a></li><li><a href="#git.github_pr_destination">git.github_pr_destination</a></li><li><a href="#git.github_pr_origin">git.github_pr_origin</a></li><li><a href="#git.github_trigger">git.github_trigger</a></li><li><a href="#git.integrate">git.integrate</a></li><li><a href="#git.latest_version">git.latest_version</a></li><li><a href="#git.mirror">git.mirror</a></li><li><a href="#git.origin">git.origin</a></li><li><a href="#git.review_input">git.review_input</a></li><li><a href="#git.mirrorContext.cherry_pick">git.mirrorContext.cherry_pick</a></li><li><a href="#git.mirrorContext.create_branch">git.mirrorContext.create_branch</a></li><li><a href="#git.mirrorContext.destination_fetch">git.mirrorContext.destination_fetch</a></li><li><a href="#git.mirrorContext.destination_push">git.mirrorContext.destination_push</a></li><li><a href="#git.mirrorContext.error">git.mirrorContext.error</a></li><li><a href="#git.mirrorContext.merge">git.mirrorContext.merge</a></li><li><a href="#git.mirrorContext.noop">git.mirrorContext.noop</a></li><li><a href="#git.mirrorContext.origin_fetch">git.mirrorContext.origin_fetch</a></li><li><a href="#git.mirrorContext.rebase">git.mirrorContext.rebase</a></li><li><a href="#git.mirrorContext.record_effect">git.mirrorContext.record_effect</a></li><li><a href="#git.mirrorContext.references">git.mirrorContext.references</a></li><li><a href="#github_api_obj.add_label">github_api_obj.add_label</a></li><li><a href="#github_api_obj.create_issue">github_api_obj.create_issue</a></li><li><a href="#github_api_obj.create_status">github_api_obj.create_status</a></li><li><a href="#github_api_obj.delete_reference">github_api_obj.delete_reference</a></li><li><a href="#github_api_obj.get_check_runs">github_api_obj.get_check_runs</a></li><li><a href="#github_api_obj.get_combined_status">github_api_obj.get_combined_status</a></li><li><a href="#github_api_obj.get_commit">github_api_obj.get_commit</a></li><li><a href="#github_api_obj.get_pull_request_comment">github_api_obj.get_pull_request_comment</a></li><li><a href="#github_api_obj.get_pull_requests">github_api_obj.get_pull_requests</a></li><li><a href="#github_api_obj.get_reference">github_api_obj.get_reference</a></li><li><a href="#github_api_obj.new_destination_ref">github_api_obj.new_destination_ref</a></li><li><a href="#github_api_obj.new_origin_ref">github_api_obj.new_origin_ref</a></li><li><a href="#github_api_obj.new_release_request">github_api_obj.new_release_request</a></li><li><a href="#github_api_obj.post_issue_comment">github_api_obj.post_issue_comment</a></li><li><a href="#github_api_obj.update_pull_request">github_api_obj.update_pull_request</a></li><li><a href="#github_api_obj.update_reference">github_api_obj.update_reference</a></li><li><a href="#github_create_release_obj.with_body">github_create_release_obj.with_body</a></li><li><a href="#github_create_release_obj.with_commitish">github_create_release_obj.with_commitish</a></li><li><a href="#github_create_release_obj.with_name">github_create_release_obj.with_name</a></li><li><a href="#fail">fail</a></li><li><a href="#float">float</a></li><li><a href="#getattr">getattr</a></li><li><a href="#glob">glob</a></li><li><a href="#hasattr">hasattr</a></li><li><a href="#hash">hash</a></li><li><a href="#int">int</a></li><li><a href="#len">len</a></li><li><a href="#new_author">new_author</a></li><li><a href="#parse_message">parse_message</a></li><li><a href="#print">print</a></li><li><a href="#go.go_proxy_resolver">go.go_proxy_resolver</a></li><li><a href="#go.go_proxy_version_list">go.go_proxy_version_list</a></li><li><a href="#goproxy_version_list.get_info">goproxy_version_list.get_info</a></li><li><a href="#hashing.str_sha256_sum">hashing.str_sha256_sum</a></li><li><a href="#hg.origin">hg.origin</a></li><li><a href="#html.xpath">html.xpath</a></li><li><a href="#html_element.attr">html_element.attr</a></li><li><a href="#http.endpoint">http.endpoint</a></li><li><a href="#http.host">http.host</a></li><li><a href="#http.multipart_form_file">http.multipart_form_file</a></li><li><a href="#http.multipart_form_text">http.multipart_form_text</a></li><li><a href="#http.url_encode">http.url_encode</a></li><li><a href="#http_endpoint.delete">http_endpoint.delete</a></li><li><a href="#http_endpoint.get">http_endpoint.get</a></li><li><a href="#http_endpoint.new_destination_ref">http_endpoint.new_destination_ref</a></li><li><a href="#http_endpoint.new_origin_ref">http_endpoint.new_origin_ref</a></li><li><a href="#http_endpoint.post">http_endpoint.post</a></li><li><a href="#http_response.header">http_response.header</a></li><li><a href="#metadata.add_header">metadata.add_header</a></li><li><a href="#metadata.expose_label">metadata.expose_label</a></li><li><a href="#metadata.map_references">metadata.map_references</a></li><li><a href="#metadata.remove_label">metadata.remove_label</a></li><li><a href="#metadata.replace_message">metadata.replace_message</a></li><li><a href="#metadata.restore_author">metadata.restore_author</a></li><li><a href="#metadata.save_author">metadata.save_author</a></li><li><a href="#metadata.scrubber">metadata.scrubber</a></li><li><a href="#metadata.squash_notes">metadata.squash_notes</a></li><li><a href="#metadata.use_last_change">metadata.use_last_change</a></li><li><a href="#metadata.verify_match">metadata.verify_match</a></li><li><a href="#patch.apply">patch.apply</a></li><li><a href="#patch.quilt_apply">patch.quilt_apply</a></li><li><a href="#path.resolve">path.resolve</a></li><li><a href="#path.resolve_sibling">path.resolve_sibling</a></li><li><a href="#re2.compile">re2.compile</a></li><li><a href="#re2.quote">re2.quote</a></li><li><a href="#re2_matcher.end">re2_matcher.end</a></li><li><a href="#re2_matcher.group">re2_matcher.group</a></li><li><a href="#re2_matcher.replace_all">re2_matcher.replace_all</a></li><li><a href="#re2_matcher.replace_first">re2_matcher.replace_first</a></li><li><a href="#re2_matcher.start">re2_matcher.start</a></li><li><a href="#re2_pattern.matcher">re2_pattern.matcher</a></li><li><a href="#re2_pattern.matches">re2_pattern.matches</a></li><li><a href="#remotefiles.origin">remotefiles.origin</a></li><li><a href="#rust_version_requirement.fulfills">rust_version_requirement.fulfills</a></li><li><a href="#StarlarkDateTime.strftime">StarlarkDateTime.strftime</a></li><li><a href="#string.capitalize">string.capitalize</a></li><li><a href="#string.count">string.count</a></li><li><a href="#string.elems">string.elems</a></li><li><a href="#string.endswith">string.endswith</a></li><li><a href="#string.find">string.find</a></li><li><a href="#string.format">string.format</a></li><li><a href="#string.index">string.index</a></li><li><a href="#string.isalnum">string.isalnum</a></li><li><a href="#string.isalpha">string.isalpha</a></li><li><a href="#string.isdigit">string.isdigit</a></li><li><a href="#string.islower">string.islower</a></li><li><a href="#string.isspace">string.isspace</a></li><li><a href="#string.istitle">string.istitle</a></li><li><a href="#string.isupper">string.isupper</a></li><li><a href="#string.join">string.join</a></li><li><a href="#string.lower">string.lower</a></li><li><a href="#string.lstrip">string.lstrip</a></li><li><a href="#string.partition">string.partition</a></li><li><a href="#string.removeprefix">string.removeprefix</a></li><li><a href="#string.removesuffix">string.removesuffix</a></li><li><a href="#string.replace">string.replace</a></li><li><a href="#string.rfind">string.rfind</a></li><li><a href="#string.rindex">string.rindex</a></li><li><a href="#string.rpartition">string.rpartition</a></li><li><a href="#string.rsplit">string.rsplit</a></li><li><a href="#string.rstrip">string.rstrip</a></li><li><a href="#string.split">string.split</a></li><li><a href="#string.splitlines">string.splitlines</a></li><li><a href="#string.startswith">string.startswith</a></li><li><a href="#string.strip">string.strip</a></li><li><a href="#string.title">string.title</a></li><li><a href="#string.upper">string.upper</a></li><li><a href="#toml.parse">toml.parse</a></li><li><a href="#TomlContent.get">TomlContent.get</a></li><li><a href="#TomlContent.get_or_default">TomlContent.get_or_default</a></li><li><a href="#ctx.add_label">ctx.add_label</a></li><li><a href="#ctx.add_or_replace_label">ctx.add_or_replace_label</a></li><li><a href="#ctx.add_text_before_labels">ctx.add_text_before_labels</a></li><li><a href="#ctx.fill_template">ctx.fill_template</a></li><li><a href="#ctx.find_all_labels">ctx.find_all_labels</a></li><li><a href="#ctx.find_label">ctx.find_label</a></li><li><a href="#ctx.new_path">ctx.new_path</a></li><li><a href="#ctx.noop">ctx.noop</a></li><li><a href="#ctx.now_as_string">ctx.now_as_string</a></li><li><a href="#ctx.remove_label">ctx.remove_label</a></li><li><a href="#ctx.replace_label">ctx.replace_label</a></li><li><a href="#ctx.set_message">ctx.set_message</a></li><li><a href="#ctx.write_path">ctx.write_path</a></li><li><a href="#xml.xpath">xml.xpath</a></li></ul>

<a id="string.capitalize" aria-hidden="true"></a>
### string.capitalize

Returns a copy of the string with its first character (if any) capitalized and the rest lowercased. This method does not support non-ascii characters. 

<code><a href="#string">string</a></code> <code>string.capitalize(<a href=#string.capitalize.self>self</a>)</code>


<h4 id="parameters.string.capitalize">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.capitalize.self href=#string.capitalize.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>

<a id="string.count" aria-hidden="true"></a>
### string.count

Returns the number of (non-overlapping) occurrences of substring <code>sub</code> in string, optionally restricting to <code>[start:end]</code>, <code>start</code> being inclusive and <code>end</code> being exclusive.

<code><a href="#int">int</a></code> <code>string.count(<a href=#string.count.self>self</a>, <a href=#string.count.sub>sub</a>, <a href=#string.count.start>start</a>=0, <a href=#string.count.end>end</a>=None)</code>


<h4 id="parameters.string.count">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.count.self href=#string.count.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>
<span id=string.count.sub href=#string.count.sub>sub</span> | <code><a href="#string">string</a></code><br><p>The substring to count.</p>
<span id=string.count.start href=#string.count.start>start</span> | <code><a href="#int">int</a></code> or <code>NoneType</code><br><p>Restrict to search from this position.</p>
<span id=string.count.end href=#string.count.end>end</span> | <code><a href="#int">int</a></code> or <code>NoneType</code><br><p>optional position before which to restrict to search.</p>

<a id="string.elems" aria-hidden="true"></a>
### string.elems

Returns an iterable value containing successive 1-element substrings of the string. Equivalent to <code>[s[i] for i in range(len(s))]</code>, except that the returned value might not be a list.

<code>list of string</code> <code>string.elems(<a href=#string.elems.self>self</a>)</code>


<h4 id="parameters.string.elems">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.elems.self href=#string.elems.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>

<a id="string.endswith" aria-hidden="true"></a>
### string.endswith

Returns True if the string ends with <code>sub</code>, otherwise False, optionally restricting to <code>[start:end]</code>, <code>start</code> being inclusive and <code>end</code> being exclusive.

<code><a href="#bool">bool</a></code> <code>string.endswith(<a href=#string.endswith.self>self</a>, <a href=#string.endswith.sub>sub</a>, <a href=#string.endswith.start>start</a>=0, <a href=#string.endswith.end>end</a>=None)</code>


<h4 id="parameters.string.endswith">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.endswith.self href=#string.endswith.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>
<span id=string.endswith.sub href=#string.endswith.sub>sub</span> | <code><a href="#string">string</a></code> or <code>tuple of string</code><br><p>The suffix (or tuple of alternative suffixes) to match.</p>
<span id=string.endswith.start href=#string.endswith.start>start</span> | <code><a href="#int">int</a></code> or <code>NoneType</code><br><p>Test beginning at this position.</p>
<span id=string.endswith.end href=#string.endswith.end>end</span> | <code><a href="#int">int</a></code> or <code>NoneType</code><br><p>optional position at which to stop comparing.</p>

<a id="string.find" aria-hidden="true"></a>
### string.find

Returns the first index where <code>sub</code> is found, or -1 if no such index exists, optionally restricting to <code>[start:end]</code>, <code>start</code> being inclusive and <code>end</code> being exclusive.

<code><a href="#int">int</a></code> <code>string.find(<a href=#string.find.self>self</a>, <a href=#string.find.sub>sub</a>, <a href=#string.find.start>start</a>=0, <a href=#string.find.end>end</a>=None)</code>


<h4 id="parameters.string.find">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.find.self href=#string.find.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>
<span id=string.find.sub href=#string.find.sub>sub</span> | <code><a href="#string">string</a></code><br><p>The substring to find.</p>
<span id=string.find.start href=#string.find.start>start</span> | <code><a href="#int">int</a></code> or <code>NoneType</code><br><p>Restrict to search from this position.</p>
<span id=string.find.end href=#string.find.end>end</span> | <code><a href="#int">int</a></code> or <code>NoneType</code><br><p>optional position before which to restrict to search.</p>

<a id="string.format" aria-hidden="true"></a>
### string.format

Perform string interpolation. Format strings contain replacement fields surrounded by curly braces <code>&#123;&#125;</code>. Anything that is not contained in braces is considered literal text, which is copied unchanged to the output.If you need to include a brace character in the literal text, it can be escaped by doubling: <code>&#123;&#123;</code> and <code>&#125;&#125;</code>A replacement field can be either a name, a number, or empty. Values are converted to strings using the <a href="#str">str</a> function.<pre class="language-python"># Access in order:
"&#123;&#125; < &#123;&#125;".format(4, 5) == "4 < 5"
# Access by position:
"{1}, {0}".format(2, 1) == "1, 2"
# Access by name:
"x{key}x".format(key = 2) == "x2x"</pre>


<code><a href="#string">string</a></code> <code>string.format(<a href=#string.format.self>self</a>, <a href=#string.format.kwargs>kwargs</a>, <a href=#string.format.args>args</a>)</code>


<h4 id="parameters.string.format">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.format.self href=#string.format.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>
<span id=string.format.kwargs href=#string.format.kwargs>kwargs</span> | <code><a href="#dict">dict</a></code><br><p>Dictionary of arguments.</p>
<span id=string.format.args href=#string.format.args>args</span> | <code><a href="#list">list</a></code><br><p>List of arguments.</p>

<a id="string.index" aria-hidden="true"></a>
### string.index

Returns the first index where <code>sub</code> is found, or raises an error if no such  index exists, optionally restricting to <code>[start:end]</code><code>start</code> being inclusive and <code>end</code> being exclusive.

<code><a href="#int">int</a></code> <code>string.index(<a href=#string.index.self>self</a>, <a href=#string.index.sub>sub</a>, <a href=#string.index.start>start</a>=0, <a href=#string.index.end>end</a>=None)</code>


<h4 id="parameters.string.index">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.index.self href=#string.index.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>
<span id=string.index.sub href=#string.index.sub>sub</span> | <code><a href="#string">string</a></code><br><p>The substring to find.</p>
<span id=string.index.start href=#string.index.start>start</span> | <code><a href="#int">int</a></code> or <code>NoneType</code><br><p>Restrict to search from this position.</p>
<span id=string.index.end href=#string.index.end>end</span> | <code><a href="#int">int</a></code> or <code>NoneType</code><br><p>optional position before which to restrict to search.</p>

<a id="string.isalnum" aria-hidden="true"></a>
### string.isalnum

Returns True if all characters in the string are alphanumeric ([a-zA-Z0-9]) and there is at least one character.

<code><a href="#bool">bool</a></code> <code>string.isalnum(<a href=#string.isalnum.self>self</a>)</code>


<h4 id="parameters.string.isalnum">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.isalnum.self href=#string.isalnum.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>

<a id="string.isalpha" aria-hidden="true"></a>
### string.isalpha

Returns True if all characters in the string are alphabetic ([a-zA-Z]) and there is at least one character.

<code><a href="#bool">bool</a></code> <code>string.isalpha(<a href=#string.isalpha.self>self</a>)</code>


<h4 id="parameters.string.isalpha">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.isalpha.self href=#string.isalpha.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>

<a id="string.isdigit" aria-hidden="true"></a>
### string.isdigit

Returns True if all characters in the string are digits ([0-9]) and there is at least one character.

<code><a href="#bool">bool</a></code> <code>string.isdigit(<a href=#string.isdigit.self>self</a>)</code>


<h4 id="parameters.string.isdigit">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.isdigit.self href=#string.isdigit.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>

<a id="string.islower" aria-hidden="true"></a>
### string.islower

Returns True if all cased characters in the string are lowercase and there is at least one character.

<code><a href="#bool">bool</a></code> <code>string.islower(<a href=#string.islower.self>self</a>)</code>


<h4 id="parameters.string.islower">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.islower.self href=#string.islower.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>

<a id="string.isspace" aria-hidden="true"></a>
### string.isspace

Returns True if all characters are white space characters and the string contains at least one character.

<code><a href="#bool">bool</a></code> <code>string.isspace(<a href=#string.isspace.self>self</a>)</code>


<h4 id="parameters.string.isspace">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.isspace.self href=#string.isspace.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>

<a id="string.istitle" aria-hidden="true"></a>
### string.istitle

Returns True if the string is in title case and it contains at least one character. This means that every uppercase character must follow an uncased one (e.g. whitespace) and every lowercase character must follow a cased one (e.g. uppercase or lowercase).

<code><a href="#bool">bool</a></code> <code>string.istitle(<a href=#string.istitle.self>self</a>)</code>


<h4 id="parameters.string.istitle">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.istitle.self href=#string.istitle.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>

<a id="string.isupper" aria-hidden="true"></a>
### string.isupper

Returns True if all cased characters in the string are uppercase and there is at least one character.

<code><a href="#bool">bool</a></code> <code>string.isupper(<a href=#string.isupper.self>self</a>)</code>


<h4 id="parameters.string.isupper">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.isupper.self href=#string.isupper.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>

<a id="string.join" aria-hidden="true"></a>
### string.join

Returns a string in which the string elements of the argument have been joined by this string as a separator. Example:<br><pre class="language-python">"|".join(["a", "b", "c"]) == "a|b|c"</pre>

<code><a href="#string">string</a></code> <code>string.join(<a href=#string.join.self>self</a>, <a href=#string.join.elements>elements</a>)</code>


<h4 id="parameters.string.join">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.join.self href=#string.join.self>self</span> | <code><a href="#string">string</a></code><br><p></p>
<span id=string.join.elements href=#string.join.elements>elements</span> | <code>iterable of string</code><br><p>The objects to join.</p>

<a id="string.lower" aria-hidden="true"></a>
### string.lower

Returns the lower case version of this string.

<code><a href="#string">string</a></code> <code>string.lower(<a href=#string.lower.self>self</a>)</code>


<h4 id="parameters.string.lower">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.lower.self href=#string.lower.self>self</span> | <code><a href="#string">string</a></code><br><p></p>

<a id="string.lstrip" aria-hidden="true"></a>
### string.lstrip

Returns a copy of the string where leading characters that appear in <code>chars</code> are removed. Note that <code>chars</code> is not a prefix: all combinations of its value are removed:<pre class="language-python">"abcba".lstrip("ba") == "cba"</pre>

<code><a href="#string">string</a></code> <code>string.lstrip(<a href=#string.lstrip.self>self</a>, <a href=#string.lstrip.chars>chars</a>=None)</code>


<h4 id="parameters.string.lstrip">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.lstrip.self href=#string.lstrip.self>self</span> | <code><a href="#string">string</a></code><br><p></p>
<span id=string.lstrip.chars href=#string.lstrip.chars>chars</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>The characters to remove, or all whitespace if None.</p>

<a id="string.partition" aria-hidden="true"></a>
### string.partition

Splits the input string at the first occurrence of the separator <code>sep</code> and returns the resulting partition as a three-element tuple of the form (before, separator, after). If the input string does not contain the separator, partition returns (self, '', '').

<code><a href="#tuple">tuple</a></code> <code>string.partition(<a href=#string.partition.self>self</a>, <a href=#string.partition.sep>sep</a>)</code>


<h4 id="parameters.string.partition">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.partition.self href=#string.partition.self>self</span> | <code><a href="#string">string</a></code><br><p></p>
<span id=string.partition.sep href=#string.partition.sep>sep</span> | <code><a href="#string">string</a></code><br><p>The string to split on.</p>

<a id="string.removeprefix" aria-hidden="true"></a>
### string.removeprefix

If the string starts with <code>prefix</code>, returns a new string with the prefix removed. Otherwise, returns the string.

<code><a href="#string">string</a></code> <code>string.removeprefix(<a href=#string.removeprefix.self>self</a>, <a href=#string.removeprefix.prefix>prefix</a>)</code>


<h4 id="parameters.string.removeprefix">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.removeprefix.self href=#string.removeprefix.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>
<span id=string.removeprefix.prefix href=#string.removeprefix.prefix>prefix</span> | <code><a href="#string">string</a></code><br><p>The prefix to remove if present.</p>

<a id="string.removesuffix" aria-hidden="true"></a>
### string.removesuffix

If the string ends with <code>suffix</code>, returns a new string with the suffix removed. Otherwise, returns the string.

<code><a href="#string">string</a></code> <code>string.removesuffix(<a href=#string.removesuffix.self>self</a>, <a href=#string.removesuffix.suffix>suffix</a>)</code>


<h4 id="parameters.string.removesuffix">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.removesuffix.self href=#string.removesuffix.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>
<span id=string.removesuffix.suffix href=#string.removesuffix.suffix>suffix</span> | <code><a href="#string">string</a></code><br><p>The suffix to remove if present.</p>

<a id="string.replace" aria-hidden="true"></a>
### string.replace

Returns a copy of the string in which the occurrences of <code>old</code> have been replaced with <code>new</code>, optionally restricting the number of replacements to <code>count</code>.

<code><a href="#string">string</a></code> <code>string.replace(<a href=#string.replace.self>self</a>, <a href=#string.replace.old>old</a>, <a href=#string.replace.new>new</a>, <a href=#string.replace.count>count</a>=-1)</code>


<h4 id="parameters.string.replace">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.replace.self href=#string.replace.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>
<span id=string.replace.old href=#string.replace.old>old</span> | <code><a href="#string">string</a></code><br><p>The string to be replaced.</p>
<span id=string.replace.new href=#string.replace.new>new</span> | <code><a href="#string">string</a></code><br><p>The string to replace with.</p>
<span id=string.replace.count href=#string.replace.count>count</span> | <code><a href="#int">int</a></code><br><p>The maximum number of replacements. If omitted, or if the value is negative, there is no limit.</p>

<a id="string.rfind" aria-hidden="true"></a>
### string.rfind

Returns the last index where <code>sub</code> is found, or -1 if no such index exists, optionally restricting to <code>[start:end]</code>, <code>start</code> being inclusive and <code>end</code> being exclusive.

<code><a href="#int">int</a></code> <code>string.rfind(<a href=#string.rfind.self>self</a>, <a href=#string.rfind.sub>sub</a>, <a href=#string.rfind.start>start</a>=0, <a href=#string.rfind.end>end</a>=None)</code>


<h4 id="parameters.string.rfind">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.rfind.self href=#string.rfind.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>
<span id=string.rfind.sub href=#string.rfind.sub>sub</span> | <code><a href="#string">string</a></code><br><p>The substring to find.</p>
<span id=string.rfind.start href=#string.rfind.start>start</span> | <code><a href="#int">int</a></code> or <code>NoneType</code><br><p>Restrict to search from this position.</p>
<span id=string.rfind.end href=#string.rfind.end>end</span> | <code><a href="#int">int</a></code> or <code>NoneType</code><br><p>optional position before which to restrict to search.</p>

<a id="string.rindex" aria-hidden="true"></a>
### string.rindex

Returns the last index where <code>sub</code> is found, or raises an error if no such index exists, optionally restricting to <code>[start:end]</code>, <code>start</code> being inclusive and <code>end</code> being exclusive.

<code><a href="#int">int</a></code> <code>string.rindex(<a href=#string.rindex.self>self</a>, <a href=#string.rindex.sub>sub</a>, <a href=#string.rindex.start>start</a>=0, <a href=#string.rindex.end>end</a>=None)</code>


<h4 id="parameters.string.rindex">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.rindex.self href=#string.rindex.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>
<span id=string.rindex.sub href=#string.rindex.sub>sub</span> | <code><a href="#string">string</a></code><br><p>The substring to find.</p>
<span id=string.rindex.start href=#string.rindex.start>start</span> | <code><a href="#int">int</a></code> or <code>NoneType</code><br><p>Restrict to search from this position.</p>
<span id=string.rindex.end href=#string.rindex.end>end</span> | <code><a href="#int">int</a></code> or <code>NoneType</code><br><p>optional position before which to restrict to search.</p>

<a id="string.rpartition" aria-hidden="true"></a>
### string.rpartition

Splits the input string at the last occurrence of the separator <code>sep</code> and returns the resulting partition as a three-element tuple of the form (before, separator, after). If the input string does not contain the separator, rpartition returns ('', '', self).

<code><a href="#tuple">tuple</a></code> <code>string.rpartition(<a href=#string.rpartition.self>self</a>, <a href=#string.rpartition.sep>sep</a>)</code>


<h4 id="parameters.string.rpartition">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.rpartition.self href=#string.rpartition.self>self</span> | <code><a href="#string">string</a></code><br><p></p>
<span id=string.rpartition.sep href=#string.rpartition.sep>sep</span> | <code><a href="#string">string</a></code><br><p>The string to split on.</p>

<a id="string.rsplit" aria-hidden="true"></a>
### string.rsplit

Returns a list of all the words in the string, using <code>sep</code> as the separator, optionally limiting the number of splits to <code>maxsplit</code>. Except for splitting from the right, this method behaves like split().

<code>list of string</code> <code>string.rsplit(<a href=#string.rsplit.self>self</a>, <a href=#string.rsplit.sep>sep</a>, <a href=#string.rsplit.maxsplit>maxsplit</a>=unbound)</code>


<h4 id="parameters.string.rsplit">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.rsplit.self href=#string.rsplit.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>
<span id=string.rsplit.sep href=#string.rsplit.sep>sep</span> | <code><a href="#string">string</a></code><br><p>The string to split on.</p>
<span id=string.rsplit.maxsplit href=#string.rsplit.maxsplit>maxsplit</span> | <code><a href="#int">int</a></code><br><p>The maximum number of splits.</p>

<a id="string.rstrip" aria-hidden="true"></a>
### string.rstrip

Returns a copy of the string where trailing characters that appear in <code>chars</code> are removed. Note that <code>chars</code> is not a suffix: all combinations of its value are removed:<pre class="language-python">"abcbaa".rstrip("ab") == "abc"</pre>

<code><a href="#string">string</a></code> <code>string.rstrip(<a href=#string.rstrip.self>self</a>, <a href=#string.rstrip.chars>chars</a>=None)</code>


<h4 id="parameters.string.rstrip">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.rstrip.self href=#string.rstrip.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>
<span id=string.rstrip.chars href=#string.rstrip.chars>chars</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>The characters to remove, or all whitespace if None.</p>

<a id="string.split" aria-hidden="true"></a>
### string.split

Returns a list of all the words in the string, using <code>sep</code> as the separator, optionally limiting the number of splits to <code>maxsplit</code>.

<code>list of string</code> <code>string.split(<a href=#string.split.self>self</a>, <a href=#string.split.sep>sep</a>, <a href=#string.split.maxsplit>maxsplit</a>=unbound)</code>


<h4 id="parameters.string.split">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.split.self href=#string.split.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>
<span id=string.split.sep href=#string.split.sep>sep</span> | <code><a href="#string">string</a></code><br><p>The string to split on.</p>
<span id=string.split.maxsplit href=#string.split.maxsplit>maxsplit</span> | <code><a href="#int">int</a></code><br><p>The maximum number of splits.</p>

<a id="string.splitlines" aria-hidden="true"></a>
### string.splitlines

Splits the string at line boundaries ('\n', '\r\n', '\r') and returns the result as a new mutable list.

<code>list of string</code> <code>string.splitlines(<a href=#string.splitlines.self>self</a>, <a href=#string.splitlines.keepends>keepends</a>=False)</code>


<h4 id="parameters.string.splitlines">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.splitlines.self href=#string.splitlines.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>
<span id=string.splitlines.keepends href=#string.splitlines.keepends>keepends</span> | <code><a href="#bool">bool</a></code><br><p>Whether the line breaks should be included in the resulting list.</p>

<a id="string.startswith" aria-hidden="true"></a>
### string.startswith

Returns True if the string starts with <code>sub</code>, otherwise False, optionally restricting to <code>[start:end]</code>, <code>start</code> being inclusive and <code>end</code> being exclusive.

<code><a href="#bool">bool</a></code> <code>string.startswith(<a href=#string.startswith.self>self</a>, <a href=#string.startswith.sub>sub</a>, <a href=#string.startswith.start>start</a>=0, <a href=#string.startswith.end>end</a>=None)</code>


<h4 id="parameters.string.startswith">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.startswith.self href=#string.startswith.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>
<span id=string.startswith.sub href=#string.startswith.sub>sub</span> | <code><a href="#string">string</a></code> or <code>tuple of string</code><br><p>The prefix (or tuple of alternative prefixes) to match.</p>
<span id=string.startswith.start href=#string.startswith.start>start</span> | <code><a href="#int">int</a></code> or <code>NoneType</code><br><p>Test beginning at this position.</p>
<span id=string.startswith.end href=#string.startswith.end>end</span> | <code><a href="#int">int</a></code> or <code>NoneType</code><br><p>Stop comparing at this position.</p>

<a id="string.strip" aria-hidden="true"></a>
### string.strip

Returns a copy of the string where leading or trailing characters that appear in <code>chars</code> are removed. Note that <code>chars</code> is neither a prefix nor a suffix: all combinations of its value are removed:<pre class="language-python">"aabcbcbaa".strip("ab") == "cbc"</pre>

<code><a href="#string">string</a></code> <code>string.strip(<a href=#string.strip.self>self</a>, <a href=#string.strip.chars>chars</a>=None)</code>


<h4 id="parameters.string.strip">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.strip.self href=#string.strip.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>
<span id=string.strip.chars href=#string.strip.chars>chars</span> | <code><a href="#string">string</a></code> or <code>NoneType</code><br><p>The characters to remove, or all whitespace if None.</p>

<a id="string.title" aria-hidden="true"></a>
### string.title

Converts the input string into title case, i.e. every word starts with an uppercase letter while the remaining letters are lowercase. In this context, a word means strictly a sequence of letters. This method does not support supplementary Unicode characters.

<code><a href="#string">string</a></code> <code>string.title(<a href=#string.title.self>self</a>)</code>


<h4 id="parameters.string.title">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.title.self href=#string.title.self>self</span> | <code><a href="#string">string</a></code><br><p>This string.</p>

<a id="string.upper" aria-hidden="true"></a>
### string.upper

Returns the upper case version of this string.

<code><a href="#string">string</a></code> <code>string.upper(<a href=#string.upper.self>self</a>)</code>


<h4 id="parameters.string.upper">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=string.upper.self href=#string.upper.self>self</span> | <code><a href="#string">string</a></code><br><p></p>



## struct

Immutable struct type.

<a id="struct" aria-hidden="true"></a>
### struct

Creates a new immutable struct. Structs with the same keys/values are equal. The struct's keys and values are passed in as keyword arguments.

<code>StructImpl</code> <code>struct(<a href=#struct.kwargs>kwargs</a>)</code>


<h4 id="parameters.struct">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=struct.kwargs href=#struct.kwargs>kwargs</span> | <code><a href="#dict">dict</a></code><br><p>Dictionary of Args.</p>


<h4 id="example.struct">Example:</h4>


##### Create a struct:

Structs are immutable objects to group values.

```python
my_struct = struct(foo='bar')
x = my_struct.foo
```




## time_delta

A time delta.

<a id="time_delta.total_seconds" aria-hidden="true"></a>
### time_delta.total_seconds

Total number of seconds in a timedelta object.

<code>long</code> <code>time_delta.total_seconds()</code>



## toml

Module for parsing TOML in Copybara.

<a id="toml.parse" aria-hidden="true"></a>
### toml.parse

Parse the TOML content. Returns a toml object.

<code><a href="#tomlcontent">TomlContent</a></code> <code>toml.parse(<a href=#toml.parse.content>content</a>)</code>


<h4 id="parameters.toml.parse">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=toml.parse.content href=#toml.parse.content>content</span> | <code><a href="#string">string</a></code><br><p>TOML content to be parsed</p>


<h4 id="example.toml.parse">Example:</h4>


##### Parsing a TOML string:

To parse a TOML string, pass the string into the parser.

```python
toml.parse("foo = 42")
```




## TomlContent

Object containing parsed TOML values.


<h4 id="returned_by.TomlContent">Returned By:</h4>

<ul><li><a href="#toml.parse">toml.parse</a></li></ul>

<a id="TomlContent.get" aria-hidden="true"></a>
### TomlContent.get

Retrieve the value from the parsed TOML for the given key. If the key is not defined, this will return None.

<code>unknown</code> <code>TomlContent.get(<a href=#TomlContent.get.key>key</a>)</code>


<h4 id="parameters.TomlContent.get">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=TomlContent.get.key href=#TomlContent.get.key>key</span> | <code><a href="#string">string</a></code><br><p>The dotted key expression</p>


<h4 id="example.TomlContent.get">Example:</h4>


##### Get the value for a key:

Pass in the name of the key. This will return the value.

```python
TomlContent.get("foo")
```


<a id="TomlContent.get_or_default" aria-hidden="true"></a>
### TomlContent.get_or_default

Retrieve the value from the parsed TOML for the given key. If the key is not defined, this will return the default value.

<code>unknown</code> <code>TomlContent.get_or_default(<a href=#TomlContent.get_or_default.key>key</a>, <a href=#TomlContent.get_or_default.default>default</a>)</code>


<h4 id="parameters.TomlContent.get_or_default">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=TomlContent.get_or_default.key href=#TomlContent.get_or_default.key>key</span> | <code><a href="#string">string</a></code><br><p>The dotted key expression</p>
<span id=TomlContent.get_or_default.default href=#TomlContent.get_or_default.default>default</span> | <code>unknown</code><br><p>The default value to return if the key isn't found.</p>


<h4 id="example.TomlContent.get_or_default">Example:</h4>


##### Get the value for a key, with a default value:

Pass in the name of the key. This will return the value.

```python
TomlContent.get_or_default("foo", "bar")
```




## transformation

A single operation which modifies the source checked out from the origin, prior to writing it to the destination. Transformations can also be used to perform validations or checks.<br/><br/>Many common transformations are provided by the built-in libraries, such as <a href='#core'><code>core</code></a>.<br/><br/>Custom transformations can be defined in Starlark code via <a href='#core.dynamic_transform'><code>core.dynamic_transform</code></a>.


<h4 id="returned_by.transformation">Returned By:</h4>

<ul><li><a href="#buildozer.batch">buildozer.batch</a></li><li><a href="#buildozer.create">buildozer.create</a></li><li><a href="#buildozer.delete">buildozer.delete</a></li><li><a href="#buildozer.modify">buildozer.modify</a></li><li><a href="#core.convert_encoding">core.convert_encoding</a></li><li><a href="#core.copy">core.copy</a></li><li><a href="#core.dynamic_transform">core.dynamic_transform</a></li><li><a href="#core.move">core.move</a></li><li><a href="#core.remove">core.remove</a></li><li><a href="#core.rename">core.rename</a></li><li><a href="#core.replace">core.replace</a></li><li><a href="#core.todo_replace">core.todo_replace</a></li><li><a href="#core.transform">core.transform</a></li><li><a href="#core.verify_match">core.verify_match</a></li><li><a href="#format.buildifier">format.buildifier</a></li><li><a href="#metadata.add_header">metadata.add_header</a></li><li><a href="#metadata.expose_label">metadata.expose_label</a></li><li><a href="#metadata.map_author">metadata.map_author</a></li><li><a href="#metadata.map_references">metadata.map_references</a></li><li><a href="#metadata.remove_label">metadata.remove_label</a></li><li><a href="#metadata.replace_message">metadata.replace_message</a></li><li><a href="#metadata.restore_author">metadata.restore_author</a></li><li><a href="#metadata.save_author">metadata.save_author</a></li><li><a href="#metadata.scrubber">metadata.scrubber</a></li><li><a href="#metadata.squash_notes">metadata.squash_notes</a></li><li><a href="#metadata.use_last_change">metadata.use_last_change</a></li><li><a href="#metadata.verify_match">metadata.verify_match</a></li><li><a href="#patch.apply">patch.apply</a></li><li><a href="#patch.quilt_apply">patch.quilt_apply</a></li></ul>
<h4 id="consumed_by.transformation">Consumed By:</h4>

<ul><li><a href="#buildozer.batch">buildozer.batch</a></li><li><a href="#core.replace_mapper">core.replace_mapper</a></li><li><a href="#core.reverse">core.reverse</a></li><li><a href="#core.transform">core.transform</a></li><li><a href="#git.gerrit_origin">git.gerrit_origin</a></li><li><a href="#git.github_origin">git.github_origin</a></li><li><a href="#git.github_pr_origin">git.github_pr_origin</a></li><li><a href="#git.origin">git.origin</a></li><li><a href="#ctx.run">ctx.run</a></li></ul>



## transformation_status

The status of a Transformation that was just run. Either a 'success' or a 'no-op'.


<h4 id="fields.transformation_status">Fields:</h4>

Name | Description
---- | -----------
is_noop | <code><a href="#bool">bool</a></code><br><p>Whether this status has the value NO-OP.</p>
is_success | <code><a href="#bool">bool</a></code><br><p>Whether this status has the value SUCCESS.</p>


<h4 id="returned_by.transformation_status">Returned By:</h4>

<ul><li><a href="#ctx.noop">ctx.noop</a></li><li><a href="#ctx.success">ctx.success</a></li></ul>



## TransformWork

Data about the set of changes that are being migrated. It includes information about changes like: the author to be used for commit, change message, etc. You receive a TransformWork object as an argument when defining a <a href='#core.dynamic_transform'><code>dynamic transform</code></a>.


<h4 id="fields.TransformWork">Fields:</h4>

Name | Description
---- | -----------
author | <code><a href="#author">author</a></code><br><p>Author to be used in the change</p>
changes | <code><a href="#changes">Changes</a></code><br><p>List of changes that will be migrated</p>
console | <code>Console</code><br><p>Get an instance of the console to report errors or warnings</p>
message | <code><a href="#string">string</a></code><br><p>Message to be used in the change</p>
params | <code><a href="#dict">dict</a></code><br><p>Parameters for the function if created with core.dynamic_transform</p>


<h4 id="consumed_by.TransformWork">Consumed By:</h4>

<ul><li><a href="#buildozer.print">buildozer.print</a></li></ul>

<a id="ctx.add_label" aria-hidden="true"></a>
### ctx.add_label

Add a label to the end of the description

<code>ctx.add_label(<a href=#ctx.add_label.label>label</a>, <a href=#ctx.add_label.value>value</a>, <a href=#ctx.add_label.separator>separator</a>="=", <a href=#ctx.add_label.hidden>hidden</a>=False)</code>


<h4 id="parameters.ctx.add_label">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=ctx.add_label.label href=#ctx.add_label.label>label</span> | <code><a href="#string">string</a></code><br><p>The label to add</p>
<span id=ctx.add_label.value href=#ctx.add_label.value>value</span> | <code><a href="#string">string</a></code><br><p>The new value for the label</p>
<span id=ctx.add_label.separator href=#ctx.add_label.separator>separator</span> | <code><a href="#string">string</a></code><br><p>The separator to use for the label</p>
<span id=ctx.add_label.hidden href=#ctx.add_label.hidden>hidden</span> | <code><a href="#bool">bool</a></code><br><p>Don't show the label in the message but only keep it internally</p>

<a id="ctx.add_or_replace_label" aria-hidden="true"></a>
### ctx.add_or_replace_label

Replace an existing label or add it to the end of the description

<code>ctx.add_or_replace_label(<a href=#ctx.add_or_replace_label.label>label</a>, <a href=#ctx.add_or_replace_label.value>value</a>, <a href=#ctx.add_or_replace_label.separator>separator</a>="=")</code>


<h4 id="parameters.ctx.add_or_replace_label">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=ctx.add_or_replace_label.label href=#ctx.add_or_replace_label.label>label</span> | <code><a href="#string">string</a></code><br><p>The label to add/replace</p>
<span id=ctx.add_or_replace_label.value href=#ctx.add_or_replace_label.value>value</span> | <code><a href="#string">string</a></code><br><p>The new value for the label</p>
<span id=ctx.add_or_replace_label.separator href=#ctx.add_or_replace_label.separator>separator</span> | <code><a href="#string">string</a></code><br><p>The separator to use for the label</p>

<a id="ctx.add_text_before_labels" aria-hidden="true"></a>
### ctx.add_text_before_labels

Add a text to the description before the labels paragraph

<code>ctx.add_text_before_labels(<a href=#ctx.add_text_before_labels.text>text</a>)</code>


<h4 id="parameters.ctx.add_text_before_labels">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=ctx.add_text_before_labels.text href=#ctx.add_text_before_labels.text>text</span> | <code><a href="#string">string</a></code><br><p></p>

<a id="ctx.create_symlink" aria-hidden="true"></a>
### ctx.create_symlink

Create a symlink

<code>ctx.create_symlink(<a href=#ctx.create_symlink.link>link</a>, <a href=#ctx.create_symlink.target>target</a>)</code>


<h4 id="parameters.ctx.create_symlink">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=ctx.create_symlink.link href=#ctx.create_symlink.link>link</span> | <code><a href="#path">Path</a></code><br><p>The link path</p>
<span id=ctx.create_symlink.target href=#ctx.create_symlink.target>target</span> | <code><a href="#path">Path</a></code><br><p>The target path</p>

<a id="ctx.destination_api" aria-hidden="true"></a>
### ctx.destination_api

Returns an api handle for the destination repository. Methods available depend on the destination type. Use with extreme caution, as external calls can make workflow non-deterministic and possibly irreversible. Can have side effects in dry-runmode.

<code><a href="#endpoint">endpoint</a></code> <code>ctx.destination_api()</code>

<a id="ctx.destination_info" aria-hidden="true"></a>
### ctx.destination_info

Returns an object to store additional configuration and data for the destination. An object is only returned if supported by the destination.

<code>DestinationInfo</code> <code>ctx.destination_info()</code>

<a id="ctx.destination_reader" aria-hidden="true"></a>
### ctx.destination_reader

Returns a handle to read files from the destination, if supported by the destination.

<code><a href="#destination_reader">destination_reader</a></code> <code>ctx.destination_reader()</code>

<a id="ctx.fill_template" aria-hidden="true"></a>
### ctx.fill_template

Replaces variables in templates with the values from this revision.

<code><a href="#string">string</a></code> <code>ctx.fill_template(<a href=#ctx.fill_template.template>template</a>)</code>


<h4 id="parameters.ctx.fill_template">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=ctx.fill_template.template href=#ctx.fill_template.template>template</span> | <code><a href="#string">string</a></code><br><p>The template to use</p>


<h4 id="example.ctx.fill_template">Example:</h4>


##### Use the SHA1 in a string:

Create a custom transformation which is successful.

```python
filled_template = ctx.fill_template('Current Revision: ${GIT_SHORT_SHA1}')
```

filled_template will contain (for example) 'Current Revision: abcdef12'


<a id="ctx.find_all_labels" aria-hidden="true"></a>
### ctx.find_all_labels

Tries to find all the values for a label. First it looks at the generated message (that is, labels that might have been added by previous transformations), then it looks in all the commit messages being imported and finally in the resolved reference passed in the CLI.

<code>list of string</code> <code>ctx.find_all_labels(<a href=#ctx.find_all_labels.label>label</a>)</code>


<h4 id="parameters.ctx.find_all_labels">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=ctx.find_all_labels.label href=#ctx.find_all_labels.label>label</span> | <code><a href="#string">string</a></code><br><p>The label to find</p>

<a id="ctx.find_label" aria-hidden="true"></a>
### ctx.find_label

Tries to find a label. First it looks at the generated message (that is, labels that might have been added by previous transformations), then it looks in all the commit messages being imported and finally in the resolved reference passed in the CLI. Returns the first such label value found this way.

<code><a href="#string">string</a></code> <code>ctx.find_label(<a href=#ctx.find_label.label>label</a>)</code>


<h4 id="parameters.ctx.find_label">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=ctx.find_label.label href=#ctx.find_label.label>label</span> | <code><a href="#string">string</a></code><br><p>The label to find</p>

<a id="ctx.list" aria-hidden="true"></a>
### ctx.list

List files in the checkout/work directory that matches a glob

<code>list of Path</code> <code>ctx.list(<a href=#ctx.list.paths>paths</a>)</code>


<h4 id="parameters.ctx.list">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=ctx.list.paths href=#ctx.list.paths>paths</span> | <code><a href="#glob">glob</a></code><br><p>A glob representing the paths to list</p>

<a id="ctx.new_path" aria-hidden="true"></a>
### ctx.new_path

Create a new path

<code><a href="#path">Path</a></code> <code>ctx.new_path(<a href=#ctx.new_path.path>path</a>)</code>


<h4 id="parameters.ctx.new_path">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=ctx.new_path.path href=#ctx.new_path.path>path</span> | <code><a href="#string">string</a></code><br><p>The string representing the path, relative to the checkout root directory</p>

<a id="ctx.noop" aria-hidden="true"></a>
### ctx.noop

The status returned by a no-op Transformation

<code><a href="#transformation_status">transformation_status</a></code> <code>ctx.noop(<a href=#ctx.noop.message>message</a>)</code>


<h4 id="parameters.ctx.noop">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=ctx.noop.message href=#ctx.noop.message>message</span> | <code><a href="#string">string</a></code><br><p></p>


<h4 id="example.ctx.noop">Example:</h4>


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

<code><a href="#string">string</a></code> <code>ctx.now_as_string(<a href=#ctx.now_as_string.format>format</a>="yyyy-MM-dd", <a href=#ctx.now_as_string.zone>zone</a>="UTC")</code>


<h4 id="parameters.ctx.now_as_string">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=ctx.now_as_string.format href=#ctx.now_as_string.format>format</span> | <code><a href="#string">string</a></code><br><p>The format to use. See: https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html for details.</p>
<span id=ctx.now_as_string.zone href=#ctx.now_as_string.zone>zone</span> | <code><a href="#string">string</a></code><br><p>The timezone id to use. See https://docs.oracle.com/javase/8/docs/api/java/time/ZoneId.html. By default UTC </p>

<a id="ctx.origin_api" aria-hidden="true"></a>
### ctx.origin_api

Returns an api handle for the origin repository. Methods available depend on the origin type. Use with extreme caution, as external calls can make workflow non-deterministic and possibly irreversible. Can have side effects in dry-runmode.

<code><a href="#endpoint">endpoint</a></code> <code>ctx.origin_api()</code>

<a id="ctx.read_path" aria-hidden="true"></a>
### ctx.read_path

Read the content of path as UTF-8

<code><a href="#string">string</a></code> <code>ctx.read_path(<a href=#ctx.read_path.path>path</a>)</code>


<h4 id="parameters.ctx.read_path">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=ctx.read_path.path href=#ctx.read_path.path>path</span> | <code><a href="#path">Path</a></code><br><p>The Path to read from</p>

<a id="ctx.remove_label" aria-hidden="true"></a>
### ctx.remove_label

Remove a label from the message if present

<code>ctx.remove_label(<a href=#ctx.remove_label.label>label</a>, <a href=#ctx.remove_label.whole_message>whole_message</a>=False)</code>


<h4 id="parameters.ctx.remove_label">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=ctx.remove_label.label href=#ctx.remove_label.label>label</span> | <code><a href="#string">string</a></code><br><p>The label to delete</p>
<span id=ctx.remove_label.whole_message href=#ctx.remove_label.whole_message>whole_message</span> | <code><a href="#bool">bool</a></code><br><p>By default Copybara only looks in the last paragraph for labels. This flagmake it replace labels in the whole message.</p>

<a id="ctx.replace_label" aria-hidden="true"></a>
### ctx.replace_label

Replace a label if it exist in the message

<code>ctx.replace_label(<a href=#ctx.replace_label.label>label</a>, <a href=#ctx.replace_label.value>value</a>, <a href=#ctx.replace_label.separator>separator</a>="=", <a href=#ctx.replace_label.whole_message>whole_message</a>=False)</code>


<h4 id="parameters.ctx.replace_label">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=ctx.replace_label.label href=#ctx.replace_label.label>label</span> | <code><a href="#string">string</a></code><br><p>The label to replace</p>
<span id=ctx.replace_label.value href=#ctx.replace_label.value>value</span> | <code><a href="#string">string</a></code><br><p>The new value for the label</p>
<span id=ctx.replace_label.separator href=#ctx.replace_label.separator>separator</span> | <code><a href="#string">string</a></code><br><p>The separator to use for the label</p>
<span id=ctx.replace_label.whole_message href=#ctx.replace_label.whole_message>whole_message</span> | <code><a href="#bool">bool</a></code><br><p>By default Copybara only looks in the last paragraph for labels. This flagmake it replace labels in the whole message.</p>

<a id="ctx.run" aria-hidden="true"></a>
### ctx.run

Run a glob or a transform. For example:<br><code>files = ctx.run(glob(['**.java']))</code><br>or<br><code>ctx.run(core.move("foo", "bar"))</code><br>or<br>

<code>unknown</code> <code>ctx.run(<a href=#ctx.run.runnable>runnable</a>)</code>


<h4 id="parameters.ctx.run">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=ctx.run.runnable href=#ctx.run.runnable>runnable</span> | <code><a href="#glob">glob</a></code> or <code><a href="#transformation">transformation</a></code><br><p>When `runnable` is a `glob`, returns a list of files in the workdir which it matches.</br></br>When `runnable` is a `transformation`, runs it in the workdir.</p>

<a id="ctx.set_author" aria-hidden="true"></a>
### ctx.set_author

Update the author to be used in the change

<code>ctx.set_author(<a href=#ctx.set_author.author>author</a>)</code>


<h4 id="parameters.ctx.set_author">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=ctx.set_author.author href=#ctx.set_author.author>author</span> | <code><a href="#author">author</a></code><br><p></p>

<a id="ctx.set_executable" aria-hidden="true"></a>
### ctx.set_executable

Set the executable permission of a file

<code>ctx.set_executable(<a href=#ctx.set_executable.path>path</a>, <a href=#ctx.set_executable.value>value</a>)</code>


<h4 id="parameters.ctx.set_executable">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=ctx.set_executable.path href=#ctx.set_executable.path>path</span> | <code><a href="#path">Path</a></code><br><p>The Path to set the executable permission of</p>
<span id=ctx.set_executable.value href=#ctx.set_executable.value>value</span> | <code><a href="#bool">bool</a></code><br><p>Whether or not the file should be executable</p>

<a id="ctx.set_message" aria-hidden="true"></a>
### ctx.set_message

Update the message to be used in the change

<code>ctx.set_message(<a href=#ctx.set_message.message>message</a>)</code>


<h4 id="parameters.ctx.set_message">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=ctx.set_message.message href=#ctx.set_message.message>message</span> | <code><a href="#string">string</a></code><br><p></p>

<a id="ctx.success" aria-hidden="true"></a>
### ctx.success

The status returned by a successful Transformation

<code><a href="#transformation_status">transformation_status</a></code> <code>ctx.success()</code>


<h4 id="example.ctx.success">Example:</h4>


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

<code>ctx.write_path(<a href=#ctx.write_path.path>path</a>, <a href=#ctx.write_path.content>content</a>)</code>


<h4 id="parameters.ctx.write_path">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=ctx.write_path.path href=#ctx.write_path.path>path</span> | <code><a href="#path">Path</a></code><br><p>The Path to write to</p>
<span id=ctx.write_path.content href=#ctx.write_path.content>content</span> | <code><a href="#string">string</a></code><br><p>The content of the file</p>



## tuple

The built-in tuple type. Example tuple expressions:<br><pre class=language-python>x = (1, 2, 3)</pre>Accessing elements is possible using indexing (starts from <code>0</code>):<br><pre class=language-python>e = x[1]   # e == 2</pre>Lists support the <code>+</code> operator to concatenate two tuples. Example:<br><pre class=language-python>x = (1, 2) + (3, 4)   # x == (1, 2, 3, 4)
x = ("a", "b")
x += ("c",)            # x == ("a", "b", "c")</pre>Similar to lists, tuples support slice operations:<pre class=language-python>('a', 'b', 'c', 'd')[1:3]   # ('b', 'c')
('a', 'b', 'c', 'd')[::2]  # ('a', 'c')
('a', 'b', 'c', 'd')[3:0:-1]  # ('d', 'c', 'b')</pre>Tuples are immutable, therefore <code>x[1] = "a"</code> is not supported.


<h4 id="returned_by.tuple">Returned By:</h4>

<ul><li><a href="#dict.popitem">dict.popitem</a></li><li><a href="#tuple">tuple</a></li><li><a href="#string.partition">string.partition</a></li><li><a href="#string.rpartition">string.rpartition</a></li></ul>



## VersionSelector

Select a version from a list of versions


<h4 id="returned_by.VersionSelector">Returned By:</h4>

<ul><li><a href="#core.custom_version_selector">core.custom_version_selector</a></li><li><a href="#core.latest_version">core.latest_version</a></li><li><a href="#git.latest_version">git.latest_version</a></li></ul>
<h4 id="consumed_by.VersionSelector">Consumed By:</h4>

<ul><li><a href="#git.github_origin">git.github_origin</a></li><li><a href="#git.origin">git.origin</a></li><li><a href="#remotefiles.origin">remotefiles.origin</a></li></ul>



## xml

Set of functions to work with XML in Copybara.

<a id="xml.xpath" aria-hidden="true"></a>
### xml.xpath

Run an xpath expression

<code>unknown</code> <code>xml.xpath(<a href=#xml.xpath.content>content</a>, <a href=#xml.xpath.expression>expression</a>, <a href=#xml.xpath.type>type</a>)</code>


<h4 id="parameters.xml.xpath">Parameters:</h4>

Parameter | Description
--------- | -----------
<span id=xml.xpath.content href=#xml.xpath.content>content</span> | <code><a href="#string">string</a></code><br><p>The XML content</p>
<span id=xml.xpath.expression href=#xml.xpath.expression>expression</span> | <code><a href="#string">string</a></code><br><p>XPath expression</p>
<span id=xml.xpath.type href=#xml.xpath.type>type</span> | <code><a href="#string">string</a></code><br><p>The type of the return value, see http://www.w3.org/TR/xpathfor more details. For now we support STRING, BOOLEAN & NUMBER.</p>



## copybara_flags

All flag options available to the Copybara CLI.



**Command line flags:**

Name | Type | Description
---- | ---- | -----------
<span style="white-space: nowrap;">`--allow-empty-diff`</span> | *boolean* | If set to false, Copybara will not write to the destination if the exact same change is already pending in the destination. Currently only supported for `git.github_pr_destination` and `git.gerrit_destination`.
<span style="white-space: nowrap;">`--allowed-git-push-options`</span> | *list* | This is a flag used to allowlist push options sent to git servers. E.g. copybara copy.bara.sky --git-push-option="foo,bar" would make copybara validate push so that the only push options (if there are any) used are 'foo' and 'bar'. If this flag is unset, it will skip push options validation. Set to "" to allow no push options.
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
<span style="white-space: nowrap;">`--experiment-checkout-affected-files`</span> | *boolean* | If set, copybara will only checkout affected files at git origin. Note that this is experimental.
<span style="white-space: nowrap;">`--force, --force-update`</span> | *boolean* | Force the migration even if Copybara cannot find in the destination a change that is an ancestor of the one(s) being migrated. This should be used with care, as it could lose changes when migrating a previous/conflicting change.
<span style="white-space: nowrap;">`--git-credential-helper-store-file`</span> | *string* | Credentials store file to be used. See https://git-scm.com/docs/git-credential-store
<span style="white-space: nowrap;">`--git-http-follow-redirects`</span> | *string* | Whether git should follow HTTP redirects. For a list of valid options, please see https://git-scm.com/docs/git-config#Documentation/git-config.txt-httpfollowRedirects
<span style="white-space: nowrap;">`--git-ls-remote-limit`</span> | *integer* | Limit the number of ls-remote rows is visible to Copybara.
<span style="white-space: nowrap;">`--git-no-verify`</span> | *boolean* | Pass the '--no-verify' option to git pushes and commits to disable git commit hooks.
<span style="white-space: nowrap;">`--git-origin-fetch-depth`</span> | *integer* | Use a shallow clone of the specified depth for git.origin. If set, only the n most recent changes' tree states are imported with older changes omitted.
<span style="white-space: nowrap;">`--git-push-option`</span> | *list* | This is a repeatable flag used to set git push level flags to send to git servers. E.g. copybara copy.bara.sky --git-push-option foo --git-push-option bar would make git operations done by copybara under the hood use the --push-option flags: git push -push-option=foo -push-option=bar ...
<span style="white-space: nowrap;">`--git-tag-overwrite`</span> | *boolean* | If set, copybara will force update existing git tag
<span style="white-space: nowrap;">`--info-list-only`</span> | *boolean* | When set, the INFO command will print a list of workflows defined in the file.
<span style="white-space: nowrap;">`--labels`</span> | *immutableMap* | Additional flags. Can be accessed in feedback and mirror context objects via the `cli_labels` field. In `core.workflow`, they are accessible as labels, but with names uppercased and prefixed with FLAG_ to avoid name clashes with existing labels. I.e. `--labels=label1:value1` will define a label FLAG_LABEL1Format: --labels=flag1:value1,flag2:value2 Or: --labels flag1:value1,flag2:value2 
<span style="white-space: nowrap;">`--noansi`</span> | *boolean* | Don't use ANSI output for messages
<span style="white-space: nowrap;">`--nocleanup`</span> | *boolean* | Cleanup the output directories. This includes the workdir, scratch clones of Git repos, etc. By default is set to false and directories will be cleaned prior to the execution. If set to true, the previous run output will not be cleaned up. Keep in mind that running in this mode will lead to an ever increasing disk usage.
<span style="white-space: nowrap;">`--nogit-credential-helper-store`</span> | *boolean* | Disable using credentials store. See https://git-scm.com/docs/git-credential-store
<span style="white-space: nowrap;">`--nogit-prompt`</span> | *boolean* | Disable username/password prompt and fail if no credentials are found. This flag sets the environment variable GIT_TERMINAL_PROMPT which is intended for automated jobs running Git https://git-scm.com/docs/git/2.3.0#git-emGITTERMINALPROMPTem
<span style="white-space: nowrap;">`--noprompt`</span> | *boolean* | Don't prompt, this will answer all prompts with 'yes'
<span style="white-space: nowrap;">`--output-limit`</span> | *int* | Limit the output in the console to a number of records. Each subcommand might use this flag differently. Defaults to 0, which shows all the output.
<span style="white-space: nowrap;">`--output-root`</span> | *string* | The root directory where to generate output files. If not set, ~/copybara/out is used by default. Use with care, Copybara might remove files inside this root if necessary.
<span style="white-space: nowrap;">`--patch-bin`</span> | *string* | Path for GNU Patch command
<span style="white-space: nowrap;">`--remote-http-files-connection-timeout`</span> | *duration* | Timeout for the fetch operation, e.g. 30s.  Example values: 30s, 20m, 1h, etc.
<span style="white-space: nowrap;">`--repo-timeout`</span> | *duration* | Repository operation timeout duration.  Example values: 30s, 20m, 1h, etc.
<span style="white-space: nowrap;">`--squash`</span> | *boolean* | Override workflow's mode with 'SQUASH'. This is useful mainly for workflows that use 'ITERATIVE' mode, when we want to run a single export with 'SQUASH', maybe to fix an issue. Always use --dry-run before, to test your changes locally.
<span style="white-space: nowrap;">`--validate-starlark`</span> | *string* | Starlark should be validated prior to execution, but this might break legacy configs. Options are LOOSE, STRICT
<span style="white-space: nowrap;">`--version-selector-use-cli-ref`</span> | *boolean* | If command line ref is to used with a version selector, pass this flag to tell copybara to use it.
<span style="white-space: nowrap;">`-v, --verbose`</span> | *boolean* | Verbose output.


