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

package com.google.copybara;

import com.google.common.collect.ImmutableSetMultimap;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkPrinter;
import com.google.devtools.build.lib.skylarkinterface.SkylarkValue;

/**
 * A feedback API endpoint of an origin or destination.
 *
 * <p>Endpoints are symmetric, that is, they need to be able to act both as an origin and
 * destination of a feedback migration, which means that they need to support both read and write
 * operations on the API.
 */
@SkylarkModule(
    name = "api",
    doc = "A feedback API endpoint of an origin or destination.",
    category = SkylarkModuleCategory.TOP_LEVEL_TYPE,
    documented = false)
public interface Endpoint extends SkylarkValue {

  /**
   * To be used for core.workflow origin/destinations that don't want to provide an api for
   * giving feedback.
   */
  Endpoint NOOP_ENDPOINT = new Endpoint() {
    @Override
    public ImmutableSetMultimap<String, String> describe() {
      throw new IllegalStateException("Instance shouldn't be use for core.feedback");
    }

    @Override
    public void repr(SkylarkPrinter printer) {
      printer.append("noop_endpoint");
    }
  };

  @Override
  default void repr(SkylarkPrinter printer) {
    printer.append(toString());
  }

  /** Returns a key-value ist of the options the endpoint was instantiated with. */
  ImmutableSetMultimap<String, String> describe();
}
