/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.copybara.http;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.Endpoint;
import com.google.copybara.Trigger;

/** HttpTrigger helps working with http origins. */
public class HttpTrigger implements Trigger {

  private final Endpoint endpoint;

  public HttpTrigger(Endpoint endpoint) {
    this.endpoint = Preconditions.checkNotNull(endpoint);
  }

  @Override
  public Endpoint getEndpoint() {
    return endpoint;
  }

  @Override
  public ImmutableSetMultimap<String, String> describe() {
    return endpoint.describe();
  }
}
