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

package com.google.copybara.git;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.Endpoint;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;

/**
 * Gerrit endpoint implementation for feedback migrations.
 */
@SkylarkModule(
    name = "gerrit_api_obj",
    category = SkylarkModuleCategory.BUILTIN,
    documented = false,
    doc = "Gerrit API endpoint implementation for feedback migrations."
)
public class GerritEndpoint implements Endpoint {

  private final GerritOptions gerritOptions;
  private final String url;

  GerritEndpoint(GerritOptions gerritOptions, String url) {
    this.gerritOptions = Preconditions.checkNotNull(gerritOptions);
    this.url = Preconditions.checkNotNull(url);
  }

  @Override
  public ImmutableSetMultimap<String, String> describe() {
    return ImmutableSetMultimap.of("type", "gerrit_api", "url", url);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("gerritOptions", gerritOptions)
        .add("url", url)
        .toString();
  }
}
