/*
 * Copyright (C) 2019 Google Inc.
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

package com.google.copybara.transform;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.truth.Truth;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CoreTest {

  private OptionsBuilder options;
  private SkylarkTestExecutor skylark;

  @Before
  public void setup() {
    options = new OptionsBuilder();
    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void testGetconfig() throws Exception {
    assertThat(skylark.<String>evalWithConfigFilePath("p", "p = core.main_config_path",
        "some/random/path/copy.bara.sky")).isEqualTo("some/random/path/copy.bara.sky");
  }
}
