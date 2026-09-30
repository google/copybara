/*
 * Copyright (C) 2026 Google LLC
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

package com.google.copybara.onboard.ai;

import com.google.common.collect.ImmutableList;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.AuthorParser;
import com.google.copybara.authoring.InvalidAuthorException;
import com.google.copybara.onboard.core.CannotConvertException;
import com.google.copybara.onboard.core.Converter;
import com.google.copybara.onboard.core.Input;

/**
 * {@link Input}s used by {@code ai_onboard}.
 *
 * <p>Names use an {@code ai_} prefix because {@link Input} names are global and must not clash with
 * {@code com.google.copybara.onboard.Inputs}.
 */
public final class AiInputs {

  private AiInputs() {}

  private static final Converter<String> NON_BLANK =
      (value, resolver) -> {
        if (value.isBlank()) {
          throw new CannotConvertException("A value is required.");
        }
        return value;
      };

  private static final Converter<String> ANY = (value, resolver) -> value;

  public static final Input<String> ORIGIN_PATH =
      Input.create(
          "ai_origin_path",
          "Where does the code you want to migrate live",
          null,
          String.class,
          NON_BLANK);

  public static final Input<String> DESTINATION_URL =
      Input.create(
          "ai_destination_url",
          "What is the URL of the destination repository",
          null,
          String.class,
          NON_BLANK);

  public static final Input<String> DESTINATION_BRANCH =
      Input.create(
          "ai_destination_branch", "Which destination branch", "main", String.class, NON_BLANK);

  public static final Input<Author> DEFAULT_AUTHOR =
      Input.create(
          "ai_default_author",
          "Who should be the default author (e.g. 'Name <email@example.com>')",
          null,
          Author.class,
          (value, resolver) -> {
            try {
              return AuthorParser.parse(value);
            } catch (InvalidAuthorException e) {
              throw new CannotConvertException(
                  "Invalid author. Format \"Name <email@example.com>\": " + e.getMessage());
            }
          });

  public static final Input<String> ORIGIN_FILES =
      Input.create(
          "ai_origin_files",
          "Which origin files should be migrated (e.g. 'everything except tests')",
          "everything",
          String.class,
          ANY);

  public static final Input<String> DESTINATION_FILES =
      Input.create(
          "ai_destination_files",
          "Which destination files should Copybara manage (e.g. 'everything except .github')",
          "everything",
          String.class,
          ANY);

  public static final Input<String> TRANSFORMATIONS =
      Input.create(
          "ai_transformations",
          "Describe any transformations (e.g. 'move the package to the repository root')",
          "none",
          String.class,
          ANY);

  public static final Input<String> OTHER_REQUESTS =
      Input.create(
          "ai_other_requests", "Anything else Copybara should do", "none", String.class, ANY);

  public static ImmutableList<Input<?>> all() {
    return ImmutableList.of(
        ORIGIN_PATH,
        DESTINATION_URL,
        DESTINATION_BRANCH,
        DEFAULT_AUTHOR,
        ORIGIN_FILES,
        DESTINATION_FILES,
        TRANSFORMATIONS,
        OTHER_REQUESTS);
  }
}
