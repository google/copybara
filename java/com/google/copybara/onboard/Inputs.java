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

import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.AuthorParser;
import com.google.copybara.authoring.InvalidAuthorException;
import com.google.copybara.onboard.core.CannotConvertException;
import com.google.copybara.onboard.core.Converter;
import com.google.copybara.onboard.core.Input;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Standard {@link Input}s that can be used by config generators.
 */
public class Inputs {

  private static final Converter<URL> URL_CONVERTER = (s, resolver) -> {
    try {
      return new URL(s);
    } catch (MalformedURLException e) {
      throw new CannotConvertException("Invalid url " + s + ": " + e);
    }

  };
  public static final Input<URL> GIT_ORIGIN_URL = Input.create(
      "git_origin_url", "Git URL to serve as origin repository",
      null, URL.class, URL_CONVERTER);

  public static final Input<URL> GIT_DESTINATION_URL = Input.create(
      "git_destination_url", "Git URL to serve as origin repository",
      null, URL.class, URL_CONVERTER);

  public static final Input<Author> DEFAULT_AUTHOR = Input.create(
      "default_author", "Default author for changes",
      null, Author.class, (value, resolver) -> {
        try {
          return AuthorParser.parse(value);
        } catch (InvalidAuthorException e) {
          throw new CannotConvertException(
              "Invalid author. Format \"foo <foo@example.com>\": " + e.getMessage());
        }
      });
  
  public static final Input<String> MIGRATION_NAME = Input.create(
      "migration_name", "Migration name",
      null, String.class, (s, resolver) -> s);
}
