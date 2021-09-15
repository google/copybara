/*
 * Copyright (C) 2021 Google Inc.
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

package com.google.copybara.onboard;

import com.google.common.collect.ImmutableSet;
import com.google.re2j.Pattern;
import java.util.Objects;

/** A template object for a core.workflow() git to git Copybara workflow */
final class GitToGitTemplate implements ConfigTemplate {

  public static final Pattern AUTHOR_PATTERN = Pattern.compile("(?P<name>[^<]+)<(?P<email>[^>]*)>");

  private final ImmutableSet<RequiredField> requiredFields =
      ImmutableSet.of(
          RequiredField.create(
              "origin_url",
              FieldClass.STRING,
              Location.NAMED,
              "Git URL to serve as origin repository.",
              Objects::nonNull),
          RequiredField.create(
              "destination_url",
              FieldClass.STRING,
              Location.NAMED,
              "Git URL to serve as destination repository",
              Objects::nonNull),
          RequiredField.create(
              "email",
              FieldClass.STRING,
              Location.NAMED,
              "Team email to be used for authoring",
              s -> AUTHOR_PATTERN.matcher(s).matches()));

  private final ImmutableSet<OptionalField> optionalFields =
      ImmutableSet.of(
          OptionalField.create(
              "name",
              FieldClass.STRING,
              Location.KEYWORD,
              "Name for the workflow",
              Objects::nonNull,
              "default"));

  @Override
  public ImmutableSet<RequiredField> getRequiredFields() {
    return requiredFields;
  }

  @Override
  public ImmutableSet<OptionalField> getOptionalFields() {
    return optionalFields;
  }

  @Override
  public boolean validate(String configInProgress) {
    return requiredFields.stream().noneMatch(x -> configInProgress.contains(x.name()));
  }

  @Override
  public String getTemplateString() {
    return "transformations = [\n"
        + "    # TODO: Insert your transformations here\n"
        + "]\n"
        + "\n"
        + "core.workflow(\n"
        + "    name = 'default',\n"
        + "    origin = git.origin(\n"
        + "    url = ::origin_url::), \n"
        + "    destination = git.destination(\n"
        + "    url = ::destination_url::),\n"
        + "    authoring = authoring.pass_thru(::email::),\n"
        + "::keyword_params::\n"
        + "    transformations = transformations,\n"
        + ")";
  }
}
