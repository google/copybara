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

package com.google.copybara.git.gitlab.util;

import com.google.common.base.Strings;
import com.google.copybara.exception.ValidationException;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;

import java.net.URI;

import static com.google.copybara.exception.ValidationException.checkCondition;

public class GitLabHost {

  public String getProjectNameFromUrl(String url) throws ValidationException {
    checkCondition(!Strings.isNullOrEmpty(url), "Empty url");

    String gitProtocolPrefix = "git@";
    int separator = url.indexOf(":");
    if (url.startsWith(gitProtocolPrefix) && separator > 0) {
      return url.substring(gitProtocolPrefix.length(), separator + 1).replaceAll("([.]git|/)$", "");
    }

    URI uri;
    try {
      uri = URI.create(url);
    } catch (IllegalArgumentException e) {
      throw new ValidationException("Cannot find project name from url " + url);
    }
    if (uri.getScheme() == null) {
      uri = URI.create("notimportant://" + url);
    }

    String name = uri.getPath().replaceAll("^/", "").replaceAll("([.]git|/)$", "");
    Matcher firstTwo = Pattern.compile("^([^/]+/[^/]+).*$").matcher(name);
    if (firstTwo.matches()) {
      name = firstTwo.group(1);
    }

    checkCondition(!Strings.isNullOrEmpty(name), "Cannot find project name from url %s", url);
    return name;
  }
}
