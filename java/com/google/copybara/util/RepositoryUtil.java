/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara.util;

import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.copybara.exception.ValidationException;

/**
 * Utilities for Repositories.
 */
public class RepositoryUtil {

  /**
   * Verify that a repo URL is not plain HTTP
   */
  public static String validateNotHttp(String url) throws ValidationException {
    checkCondition(!url.startsWith("http://"),
        "URL '%s' is not valid - should be using https.", url);
    return url;
  }

  private RepositoryUtil() {
  }
}
