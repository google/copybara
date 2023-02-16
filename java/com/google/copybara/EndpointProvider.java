/*
 * Copyright (C) 2020 Google Inc.
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
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.eval.StarlarkValue;

/** Wrapper class to prevent arbitrary instantiation of endpoints in starlark. */
@StarlarkBuiltin(
    name = "endpoint_provider",
    doc = "An handle for an origin or destination API in a feedback migration.",
    documented = false)
public class EndpointProvider<T extends Endpoint> implements StarlarkValue {
  final T endpoint;

  EndpointProvider(T endpoint) {
    this.endpoint = endpoint;
  }

  public T getEndpoint() {
    return endpoint;
  }

  // TODO(b/269526710): Remove method
  public ImmutableSetMultimap<String, String> describe() {
    return endpoint.describe();
  }

  /**
   * Wrap an Endpoint
   */
  public static <T extends Endpoint> EndpointProvider<T> wrap(T e) {
    return new EndpointProvider<>(e);
  }
}
