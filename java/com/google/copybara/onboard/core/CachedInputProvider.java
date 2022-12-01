/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.onboard.core;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * A simple {@code DataProvider} that caches the request to avoid calling populators or asking the
 * user for the same value several times.
 */
public class CachedInputProvider implements InputProvider {

  private final Map<Input<?>, Object> values = new HashMap<>();
  private final InputProvider provider;

  public CachedInputProvider(InputProvider provider) {
    this.provider = checkNotNull(provider);
  }

  @Override
  public <T> Optional<T> resolve(Input<T> input, InputProviderResolver db)
      throws InterruptedException, CannotProvideException {
    @SuppressWarnings("unchecked")
    T v = (T) values.get(input);
    if (v != null) {
      return Optional.of(v);
    }
    Optional<T> t = provider.resolve(input, db);
    t.ifPresent(val -> values.put(input, val));
    return t;
  }

  @Override
  public ImmutableMap<Input<?>, Integer> provides() throws CannotProvideException {
    return provider.provides();
  }

  @Override
  public String toString() {
    return "Cached(" + provider + ')';
  }
}
