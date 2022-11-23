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

import static com.google.common.base.Verify.verify;

import com.google.common.base.Joiner;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.util.console.Console;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * This class is in charge of delegating to the proper input provider to resolve Inputs recursively.
 *
 * The {@link InputProvider} uses an internal {@link ImmutableSet<Input>} to detect loops.
 */
public final class InputProviderResolverImpl implements InputProviderResolver {

  private final ImmutableSet<String> loopDetector;
  private final ImmutableMap<Input<?>, InputProvider> inputProviders;

  public static InputProviderResolver create(Collection<InputProvider> providers,
      AskInputProvider.Mode askMode, Console console) {
    HashMultimap<Input<?>, InputProvider> map = HashMultimap.create();
    for (InputProvider provider : providers) {
      for (Input<?> provides : provider.provides().keySet()) {
        map.put(provides, provider);
      }
    }
    ImmutableMap.Builder<Input<?>, InputProvider> builder = ImmutableMap.builder();

    for (Entry<Input<?>, Collection<InputProvider>> entry : map.asMap().entrySet()) {
      builder.put(entry.getKey(),
          // Cache any result
          new CachedInputProvider(
              // Ask user for input depending on the mode
              new AskInputProvider(
                  // Resolver in priority order
                  new PrioritizedInputProvider(entry.getKey(), entry.getValue()),
                  askMode, console)));
    }
    return new InputProviderResolverImpl(builder.build(), ImmutableSet.of());
  }

  private InputProviderResolverImpl(ImmutableMap<Input<?>, InputProvider> inputProviders,
      ImmutableSet<String> loopDetector) {
    this.loopDetector = loopDetector;
    this.inputProviders = inputProviders;
  }

  /**
   * Resolve the value for an {@link Input} object.
   */
  @Override
  public <T> Optional<T> resolve(Input<T> input)
      throws InterruptedException, CannotProvideException {
    if (loopDetector.contains(input.name())) {
      throw new IllegalStateException("Loop detected trying to resolver input: "
          + Joiner.on(" -> ").join(loopDetector) + " -> *" + input.name());
    }
    InputProvider inputProvider = inputProviders.get(input);
    if (inputProvider == null) {
      // No provider means that we cannot resolve the Input
      return Optional.empty();
    }

    verify(inputProvider.provides().containsKey(input),
        "Something went wrong, InputProvider %s doesn't provide %s",
        inputProvider, input);

    return inputProvider.resolve(input, new InputProviderResolverImpl(inputProviders,
        ImmutableSet.<String>builder().addAll(loopDetector).add(input.name()).build()));
  }
}
