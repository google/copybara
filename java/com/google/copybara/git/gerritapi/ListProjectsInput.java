/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.copybara.git.gerritapi;

import com.google.common.base.Joiner;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * A request for List Projects Gerrit API call
 */
public class ListProjectsInput {

  @Nullable private String branch;
  private boolean description;
  private int limit = -1;
  private int skip = -1;
  @Nullable private String prefix;
  @Nullable private String regex;

  public ListProjectsInput() {
  }

  private ListProjectsInput(@Nullable String branch, boolean description, int limit,
      int skip, @Nullable String prefix, @Nullable String regex) {
    this.branch = branch;
    this.description = description;
    this.limit = limit;
    this.prefix = prefix;
    this.regex = regex;
    this.skip = skip;
  }

  public ListProjectsInput withBranch(String branch) {
    return new ListProjectsInput(branch, description, limit, skip, prefix, regex);
  }

  public ListProjectsInput withLimit(int limit) {
    return new ListProjectsInput(branch, description, limit, skip, prefix, regex);
  }

  public ListProjectsInput withSkip(int skip) {
    return new ListProjectsInput(branch, description, limit, skip, prefix, regex);
  }

  public ListProjectsInput withPrefix(String prefix) {
    return new ListProjectsInput(branch, description, limit , skip, prefix, regex);
  }

  public ListProjectsInput withRegex(String regex) {
    return new ListProjectsInput(branch, description, limit , skip, prefix, regex);
  }

  public ListProjectsInput withDescription() {
    return new ListProjectsInput(branch, /*description=*/true, limit, skip, prefix, regex);
  }

  public String asUrlParams() {
    List<String> args = new ArrayList<>();
    if (branch != null) {
      args.add("b=" + branch);
    }
    if (description) {
      args.add("d");
    }
    if (limit != -1) {
      args.add("n=" + limit);
    }
    if (skip != -1) {
      args.add("S=" + skip);
    }
    if (prefix != null) {
      args.add("p=" + prefix);
    }
    if (regex != null) {
      args.add("r=" + regex);
    }

    return Joiner.on('&').join(args);
  }
}
