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

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.onboard.core.InputProvider.DEFAULT_PRIORITY;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CachedInputProviderTest {

  public static final InputProviderResolver RESOLVER = new InputProviderResolver() {

    @Override
    public <T> Optional<T> resolve(Input<T> input) {
      throw new IllegalStateException("Shouldn't be called in this test!");
    }
  };
  private static final Input<String> INPUT = Input.create("CachedInputProviderTest",
      "just for test", null, String.class, (s, resolver) -> s);

  @Test
  public void testSimple() throws CannotProvideException, InterruptedException {
    String[] val = {"42"};
    CachedInputProvider provider = new CachedInputProvider(
        new InputProvider() {
          @SuppressWarnings("unchecked")
          @Override
          public <T> Optional<T> resolve(Input<T> input, InputProviderResolver db) {
            return (Optional<T>) Optional.of(val[0]);
          }

          @Override
          public ImmutableMap<Input<?>, Integer> provides() throws CannotProvideException {
            return defaultPriority(ImmutableSet.of(INPUT));
          }
        }
    );
    assertThat(provider.provides()).containsExactly(INPUT, DEFAULT_PRIORITY);
    assertThat(provider.resolve(INPUT, RESOLVER)).isEqualTo(Optional.of("42"));
    val[0] = "other";
    assertThat(provider.resolve(INPUT, RESOLVER)).isEqualTo(Optional.of("42"));
  }

}
