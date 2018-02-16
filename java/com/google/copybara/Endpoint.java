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
import com.google.devtools.build.lib.skylarkinterface.SkylarkPrinter;
import com.google.devtools.build.lib.skylarkinterface.SkylarkValue;

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

  /** Returns a key-value ist of the options the endpoint was instantiated with. */
  ImmutableSetMultimap<String, String> describe();
}
