/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara.templatetoken;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.copybara.templatetoken.LabelTemplate.LabelNotFoundException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LabelTemplateTest {

  @Test
  public void testTemplateNoVars() throws LabelNotFoundException {
    assertThat(new LabelTemplate("foobar").resolve(e -> e.equals("FOO") ? "VAL" : null))
        .isEqualTo("foobar");
  }

  @Test
  public void testWithVars() throws LabelNotFoundException {
    assertThat(new LabelTemplate("Foo${LABEL}\n${LABEL}\n\nAnd ${OTHER}")
        .resolve(e -> e.equals("LABEL") ? "VAL" : e.equals("OTHER") ? "VAL2" : null))
        .isEqualTo("FooVAL\nVAL\n\nAnd VAL2");
  }

  @Test
  public void testNotFound() {
    LabelNotFoundException exception =
        assertThrows(
            LabelNotFoundException.class,
            () ->
                new LabelTemplate("Foo${LABEL}${OTHER}")
                    .resolve(e -> e.equals("LABEL") ? "VAL" : null));
    assertThat(exception.getLabel()).isEqualTo("OTHER");
  }
}
