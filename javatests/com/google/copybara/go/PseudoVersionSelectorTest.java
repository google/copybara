/*
 * Copyright (C) 2023 Google LLC
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

package com.google.copybara.go;

import static com.google.common.truth.Truth8.assertThat;

import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class PseudoVersionSelectorTest {

  private final PseudoVersionSelector underTest = new PseudoVersionSelector();

  @Test
  public void testProcess_golangPseudo() throws Exception {
    assertThat(underTest.select(null, "v0.0.0-20190604041725-da78bae5fc95", null))
        .hasValue("da78bae5fc95");
  }

  @Test
  public void testProcess_golangPseudoNoV() throws Exception {
    assertThat(underTest.select(null, "0.0.0-20190604041725-da78bae5fc95", null))
        .hasValue("da78bae5fc95");
  }

  @Test
  public void testProcess_golangPseudoSuffix() throws Exception {
    assertThat(underTest.select(null, "v0.0.0-20190604041725-da78bae5fc95+incompatible", null))
        .hasValue("da78bae5fc95");
  }

  @Test
  public void testProcess_golangPseudoRc() throws Exception {
    assertThat(underTest.select(null, "v0.0.0-0.20190604041725-da78bae5fc95", null))
        .hasValue("da78bae5fc95");
  }

  @Test
  public void testProcess_noPV() throws Exception {
    assertThat(underTest.select(null, "v0.0.0", null)).isEmpty();
  }
}
