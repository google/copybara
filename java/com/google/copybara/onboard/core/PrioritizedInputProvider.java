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
import java.util.Collection;
import java.util.Optional;
import java.util.TreeSet;

/**
 * Given an {@link Input} and a {@code Collection} of {@link InputProvider}s for that Input,
 * it creates a InputProvider that calls the delegate InputProviders in priority order.
 */
public class PrioritizedInputProvider implements InputProvider {

  private final Input<?> input;
  private final TreeSet<PrioritizedEntry> providers = new TreeSet<>();

  public PrioritizedInputProvider(Input<?> input, Collection<InputProvider> providers) {
    this.input = input;
    for (InputProvider provider : providers) {
      Integer priority = provider.provides().get(input);
      this.providers.add(new PrioritizedEntry(provider,
          checkNotNull(priority, "Provider %s doesn't provide %s", provider, input)));
    }
  }

  @Override
  public <T> Optional<T> resolve(Input<T> input, InputProviderResolver db)
      throws InterruptedException, CannotProvideException {
    for (PrioritizedEntry p : providers) {
      Optional<T> result = p.provider.resolve(input, db);
      if (result.isPresent()) {
        return result;
      }
    }
    return Optional.empty();
  }

  @Override
  public ImmutableMap<Input<?>, Integer> provides() {
    // Doesn't matter much but just in case we wrap this in other provider in
    // the future.
    return ImmutableMap.of(input, providers.iterator().next().priority);
  }

  private static final class PrioritizedEntry
      implements Comparable<PrioritizedEntry> {

    private final InputProvider provider;
    private final int priority;

    public PrioritizedEntry(InputProvider provider, int priority) {
      this.provider = provider;
      this.priority = priority;
    }

    @Override
    public int compareTo(PrioritizedEntry o) {
      // reversed so highest priority is the biggest number.
      return Integer.compare(o.priority, priority);
    }
  }

}
