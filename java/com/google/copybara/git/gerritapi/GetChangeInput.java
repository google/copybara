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
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSet;
import java.util.stream.Collectors;

/**
 * An object that represents the input parameters for get change:
 *
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#get-change
 */
public class GetChangeInput {
  private final ImmutableSet<IncludeResult> include;

  public GetChangeInput() {
    this(ImmutableSet.of());
  }

  public GetChangeInput(ImmutableSet<IncludeResult> include) {
    this.include = include;
  }

  String asUrlParams() {
    return Joiner.on("&").join(include.stream().map(i -> "o=" + i).collect(Collectors.toList()));
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    GetChangeInput that = (GetChangeInput) o;
    return Objects.equal(include, that.include);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(include);
  }
}
