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

package com.google.copybara.version;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.copybara.util.console.testing.TestingConsole;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class RequestedExactMatchSelectorTest {
  @Test
  public void testFound() throws Exception {
    RequestedExactMatchSelector selector = new RequestedExactMatchSelector();
    assertThat(selector.select(() -> ImmutableSet.of("one", "two"), "one",
        new TestingConsole()))
        .isEqualTo(Optional.of("one"));
  }

  @Test
  public void testNotFound() throws Exception {
    RequestedExactMatchSelector selector = new RequestedExactMatchSelector();
    assertThat(selector.select(() -> ImmutableSet.of("one", "two"), "three", new TestingConsole()))
        .isEmpty();
  }
}
