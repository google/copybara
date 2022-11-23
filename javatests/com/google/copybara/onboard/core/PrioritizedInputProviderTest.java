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

import com.google.common.collect.ImmutableList;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PrioritizedInputProviderTest {
  public static final InputProviderResolver RESOLVER = new InputProviderResolver() {

    @Override
    public <T> Optional<T> resolve(Input<T> input) {
      throw new IllegalStateException("Shouldn't be called in this test!");
    }
  };

  @SuppressWarnings("rawtypes")
  private static final Input<String> INPUT =
      Input.create("PrioritizedInputProviderTest",
      "just for test", null, String.class,
          s -> s);

  @Test
  public void testSimple() throws CannotProvideException, InterruptedException {
    InputProvider provider = new PrioritizedInputProvider(INPUT,
        ImmutableList.of(
            new ConstantProvider(INPUT, "10", 10),
            new ConstantProvider(INPUT, "20", 20),
            new ConstantProvider(INPUT, null, 30)
        ));

    assertThat(provider.provides()).containsExactly(INPUT, 30);
    assertThat(provider.resolve(INPUT, RESOLVER)).isEqualTo(Optional.of("20"));
  }

  @Test
  public void testNone() throws CannotProvideException, InterruptedException {
    InputProvider provider = new PrioritizedInputProvider(INPUT,
        ImmutableList.of(
            new ConstantProvider(INPUT, null, 10)
        ));

    assertThat(provider.provides()).containsExactly(INPUT, 10);
    assertThat(provider.resolve(INPUT, RESOLVER)).isEqualTo(Optional.empty());
  }
}
