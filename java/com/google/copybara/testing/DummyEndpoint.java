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

package com.google.copybara.testing;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.Endpoint;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkPrinter;
import com.google.devtools.build.lib.syntax.SkylarkList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A dummy endpoint for feedback mechanism
 */
@SkylarkModule(
    name = "dummy_endpoint",
    doc = "A dummy endpoint for feedback mechanism",
    category = SkylarkModuleCategory.BUILTIN)
public class DummyEndpoint implements Endpoint {

  public final List<String> messages = new ArrayList<>();

  @Override
  public ImmutableSetMultimap<String, String> describe() {
    return ImmutableSetMultimap.<String, String>builder()
        .put("type", "dummy_endpoint")
        .build();
  }

  @SkylarkCallable(name = "message", doc = "Add a new message",
      parameters = { @Param(name = "message", type = String.class) })
  public void add(String msg) {
    messages.add(msg);
  }

  public void addAll(String... msg) {
    messages.addAll(Arrays.asList(msg));
  }

  @Override
  public void repr(SkylarkPrinter printer) {
    printer.append("dummy");
  }

  @SkylarkCallable(name = "get_messages", doc = "Get the messages", structField = true)
  public SkylarkList<String> getMessages() {
    return SkylarkList.createImmutable(messages);
  }
}
