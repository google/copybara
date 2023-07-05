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

import com.google.common.base.Joiner;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.Endpoint;
import com.google.copybara.LazyResourceLoader;
import com.google.copybara.Trigger;
import com.google.copybara.git.gerritapi.GerritApi;
import com.google.copybara.util.console.Console;
import java.util.stream.Collectors;

/** A  feedback trigger based on updates on a Gerrit change.
 */
public class GerritTrigger implements Trigger {

  private final LazyResourceLoader<GerritApi> apiSupplier;
  private final String url;
  private final ImmutableSet<GerritEventTrigger> events;
  private final Console console;
  private final boolean allowSubmitChange;
  
  GerritTrigger(
      LazyResourceLoader<GerritApi> apiSupplier,
      String url,
      ImmutableSet<GerritEventTrigger> events,
      Console console,
      boolean allowSubmitChange) {
    this.apiSupplier = Preconditions.checkNotNull(apiSupplier);
    this.url = Preconditions.checkNotNull(url);
    this.events = Preconditions.checkNotNull(events);
    this.console = console;
    this.allowSubmitChange = allowSubmitChange;
  }

  @Override
  public Endpoint getEndpoint() {
    return new GerritEndpoint(apiSupplier, url, console, allowSubmitChange);
  }

  @Override
  public ImmutableSetMultimap<String, String> describe() {
    ImmutableSetMultimap.Builder<String, String> builder = ImmutableSetMultimap.builder();
    builder.put("type", "gerrit_trigger")
        .put("url", url)
        .put("gerritSubmit", "" + allowSubmitChange)
        .putAll("events",
        events.stream().map(s -> s.type().name()).collect(Collectors.toList()));

    for (GerritEventTrigger trigger : events) {
      if (trigger.subtypes().isEmpty()) {
        continue;
      }
      builder.putAll(
          String.format("SUBTYPES_%s", trigger.type().name()),
          trigger.subtypes());
    }
    return builder.build();
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("url", url)
        .add("event_types", Joiner.on(",").join(events))
        .toString();
  }
}
