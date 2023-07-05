/*
 * Copyright (C) 2023 Google Inc.
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
package com.google.copybara.git.github.api;

import com.google.api.client.util.Key;
import com.google.errorprone.annotations.Keep;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/**
 * Input for creating a release
 * https://docs.github.com/en/rest/releases/releases?apiVersion=2022-11-28#create-a-release
 */
@StarlarkBuiltin(
    name = "github_create_release_obj",
    doc = "GitHub API value type for release params. See "
        + "https://docs.github.com/en/rest/releases/releases?apiVersion=2022-11-28#create-a-release"
)
public class CreateReleaseRequest implements StarlarkValue  {
  @Keep @Key("tag_name") private String tagName;
  @Keep @Key private String body;
  @Keep @Key private String name;
  @Keep @Key("target_commitish") private String targetCommitish;
  @Keep @Key("prerelease") private Boolean preRelease;


  @Keep  @Key private Boolean draft;
  @Keep  @Key("make_latest") private Boolean makeLatest;
  @Keep  @Key("generate_release_notes") private Boolean generateReleaseNotes;


  public CreateReleaseRequest(String tagName) {
    this.tagName = tagName;
  }

  public CreateReleaseRequest() {
    // just for reflection.
  }

  @StarlarkMethod(
      name = "with_body",
      doc = "Set the body for the release.",
      parameters = {@Param(name = "body", doc = "Body for the release")})
  public CreateReleaseRequest withBody(String body) {
    this.body = body;
    return this;
  }

  @StarlarkMethod(
      name = "with_name",
      doc = "Set the name for the release.",
      parameters = {@Param(name = "name", doc = "Name for the release")})
  public CreateReleaseRequest withName(String name) {
    this.name = name;
    return this;
  }

  @StarlarkMethod(
      name = "with_commitish",
      doc = "Set the commitish to be used for the release. Defaults to HEAD",
      parameters = {@Param(name = "commitish", doc = "Commitish for the release")})
  public CreateReleaseRequest withCommitish(String targetCommitish) {
    this.targetCommitish = targetCommitish;
    return this;
  }

  @StarlarkMethod(
      name = "set_draft",
      doc = "Is this a draft release?",
      parameters = {@Param(name = "draft", doc = "Mark release as draft?")})
  public CreateReleaseRequest withDraft(Boolean draft) {
    this.draft = draft;
    return this;
  }

  @StarlarkMethod(
      name = "set_latest",
      doc = "Is this the latest release?",
      parameters = {@Param(name = "make_latest", doc = "Mark release as latest?")})
  public CreateReleaseRequest withMakeLatest(Boolean makeLatest) {
    this.makeLatest = makeLatest;
    return this;
  }

  @StarlarkMethod(
      name = "set_prerelease",
      doc = "Is this a prerelease?",
      parameters = {@Param(name = "prerelease", doc = "Mark release as prerelease?")})
  public CreateReleaseRequest withPreRelease(Boolean preRelease) {
    this.preRelease = preRelease;
    return this;
  }

  @StarlarkMethod(
      name = "set_generate_release_notes",
      doc = "Generate release notes?",
      parameters = {@Param(name = "generate_notes", doc = "Generate notes?")})
  public CreateReleaseRequest withGenerateReleaseNotes(Boolean generateReleaseNotes) {
    this.generateReleaseNotes = generateReleaseNotes;
    return this;
  }

  public String getTagName() {
    return tagName;
  }

  public String getBody() {
    return body;
  }

  public String getName() {
    return name;
  }

  public String getTargetCommitish() {
    return targetCommitish;
  }
}
