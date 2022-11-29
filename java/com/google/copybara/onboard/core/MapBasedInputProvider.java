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
import java.util.Optional;

/**
 * An Input provider that uses a constant map as the source of values. Can be used for providing
 * values from CLI flags.
 */
public class MapBasedInputProvider implements InputProvider {

  private final ImmutableMap<String, String> map;
  private final int priority;

  public MapBasedInputProvider(ImmutableMap<String, String> map, int priority) {
    this.map = checkNotNull(map);
    this.priority = priority;
  }

  @Override
  public <T> Optional<T> resolve(Input<T> input, InputProviderResolver resolver)
      throws InterruptedException, CannotProvideException {
    for (String s : map.keySet()) {
      Input<?> ourInput = findInput(s);
      if (ourInput == input) {
        try {
          return Optional.of(input.convert(map.get(s), resolver));
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw e;
        } catch (Exception e) {
          // This could be console.error instead and return Optional.empty()
          // So the user can correct in the iterative mode.
          throw new CannotProvideException(
              String.format("Invalid value for %s(%s): %s",
                  input.description(), input.name(), e.getMessage()));
        }
      }
    }

    return Optional.empty();
  }

  @Override
  public ImmutableMap<Input<?>, Integer> provides() throws CannotProvideException {
    ImmutableMap.Builder<Input<?>, Integer> result = ImmutableMap.builder();
    for (String s : map.keySet()) {
      result.put(findInput(s), priority);
    }
    return result.build();
  }

  private Input<?> findInput(String s) throws CannotProvideException {
    Input<?> input = Input.registeredInputs().get(s);
    if (input == null) {
      throw new CannotProvideException(
          String.format("Invalid input type '%s'. Available inputs: %s",
              s, Input.registeredInputs()));
    }
    return input;
  }
}
