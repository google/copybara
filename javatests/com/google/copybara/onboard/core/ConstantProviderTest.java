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

import java.util.Optional;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ConstantProviderTest {

  public static final InputProviderResolver RESOLVER = new InputProviderResolver() {

    @Override
    public <T> Optional<T> resolve(Input<T> input) {
      throw new IllegalStateException("Shouldn't be called in this test!");
    }
  };
  private static final Input<String> INPUT = Input.create("InputProviderResolver",
      "just for test", null, String.class, s -> s);

  private static final Input<Integer> OTHER = Input.create("InputProviderResolverOther",
      "just for test", null, Integer.class, Integer::valueOf);

  @Test
  public void testSimple() throws CannotProvideException, InterruptedException {
    ConstantProvider provider = new ConstantProvider(INPUT, "hello");
    assertThat(provider.provides()).containsExactly(INPUT, DEFAULT_PRIORITY);
    assertThat(provider.resolve(INPUT, RESOLVER)).isEqualTo(Optional.of("hello"));
  }

  @Test
  public void testNoValue() throws CannotProvideException, InterruptedException {
    ConstantProvider provider = new ConstantProvider(INPUT, null);
    assertThat(provider.provides()).containsExactly(INPUT, DEFAULT_PRIORITY);
    assertThat(provider.resolve(INPUT, RESOLVER)).isEqualTo(Optional.empty());
  }

  @Test
  public void testNotFound() {
    ConstantProvider provider = new ConstantProvider(INPUT, null);
    assertThat(Assert.assertThrows(IllegalArgumentException.class,
        () -> provider.resolve(OTHER, RESOLVER))).hasMessageThat()
        .contains("This shouldn't happen");
  }
}
