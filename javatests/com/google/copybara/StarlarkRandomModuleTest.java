/*
 * Copyright (C) 2024 Google LLC.
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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Mutability;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class StarlarkRandomModuleTest {
  private final StarlarkRandomModule randomModule = new StarlarkRandomModule();

  @Test
  public void testSampleStarlarkList_allElements() throws Exception {
    StarlarkList<String> list = StarlarkList.of(Mutability.IMMUTABLE, "foo", "bar", "baz");
    assertThat((Iterable<?>) randomModule.sampleStarlarkList(list, StarlarkInt.of(list.size())))
        .containsExactlyElementsIn(list);
  }

  @Test
  public void testSampleStarlarkList_twoElements() throws Exception {
    StarlarkList<String> list = StarlarkList.of(Mutability.IMMUTABLE, "foo", "bar", "baz");
    StarlarkList<?> sampled = randomModule.sampleStarlarkList(list, StarlarkInt.of(2));
    assertThat((Iterable<?>) list).containsAtLeastElementsIn(sampled);
    assertThat((Iterable<?>) sampled).hasSize(2);
  }

  @Test
  public void testSampleStarlarkList_outOfBounds() throws Exception {
    StarlarkList<String> list = StarlarkList.of(Mutability.IMMUTABLE, "foo", "bar", "baz");
    EvalException e =
        assertThrows(
            EvalException.class,
            () -> randomModule.sampleStarlarkList(list, StarlarkInt.of(list.size() + 1)));
    assertThat(e)
        .hasMessageThat()
        .contains("k is out of bounds. Must be >= 0 and <= 3. Current value: 4");
  }
}
