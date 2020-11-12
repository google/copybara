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
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/** Wrapper class to prevent arbitrary instantiation of endpoints in starlark. */
@StarlarkBuiltin(
    name = "endpoint_provider",
    doc = "An handle for an origin or destination API in a feedback migration.")
public class EndpointProvider<T extends Endpoint> implements StarlarkValue, Endpoint {
  final T endpoint;

  EndpointProvider(T endpoint) {
    this.endpoint = endpoint;
  }

  public T getEndpoint() {
    return endpoint;
  }

  @Override
  public ImmutableSetMultimap<String, String> describe() {
    return endpoint.describe();
  }

  /**
   * Wrap an Endpoint
   */
  public static <T extends Endpoint> EndpointProvider<T> wrap(T e) {
    return new EndpointProvider<>(e);
  }

  @Override
  @StarlarkMethod(
      name = "url",
      doc = "Return the URL of this endpoint, if any.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getUrl() {
    return endpoint.getUrl();
  }

}
