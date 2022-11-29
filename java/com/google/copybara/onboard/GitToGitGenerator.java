/*
 * Copyright (C) 2022 Google Inc.
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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.onboard.core.CannotProvideException;
import com.google.copybara.onboard.core.InputProviderResolver;
import com.google.copybara.onboard.core.template.Field;
import com.google.copybara.onboard.core.template.TemplateConfigGenerator;
import java.util.Optional;

/** A template object for a core.workflow() git to git Copybara workflow */
public final class GitToGitGenerator extends TemplateConfigGenerator {

  private static final String TEMPLATE = ""
      + "core.workflow(\n"
      + "    name = '::name::',\n"
      + "    origin = git.origin(\n"
      + "        url = \"::origin_url::\",\n"
      + "    ), \n"
      + "    destination = git.destination(\n"
      + "        url = \"::destination_url::\",\n"
      + "    ),\n"
      + "    authoring = authoring.pass_thru(\"::email::\"),\n"
      + "    ::keyword_params::\n"
      + "    transformations = [\n"
      + "        # TODO: Insert your transformations here\n"
      + "    ],\n"
      + ")\n";

  public GitToGitGenerator() {
    super(TEMPLATE);
  }

  @Override
  protected ImmutableMap<Field, Object> resolve(InputProviderResolver resolver)
      throws InterruptedException, CannotProvideException {
    ImmutableMap.Builder<Field, Object> result = ImmutableMap.builder();

    result.put(Field.required("origin_url"), resolver.resolve(Inputs.GIT_ORIGIN_URL).get());
    result.put(Field.required("destination_url"),
        resolver.resolve(Inputs.GIT_DESTINATION_URL).get());
    result.put(Field.required("email"), resolver.resolve(Inputs.DEFAULT_AUTHOR).get());

    Optional<String> name = resolver.resolveOptional(Inputs.MIGRATION_NAME);
    result.put(Field.required("name"), name.isPresent() && !Strings.isNullOrEmpty(name.get())
        ? name.get()
        : "default");

    return result.build();
  }

  @Override
  public String name() {
    return "git_to_git";
  }

  @Override
  public String toString() {
    return name();
  }
}
