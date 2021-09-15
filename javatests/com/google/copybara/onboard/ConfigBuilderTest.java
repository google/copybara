/*
 * Copyright (C) 2021 Google Inc.
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

package com.google.copybara.onboard;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.copybara.config.Config;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ConfigBuilderTest {

  private SkylarkTestExecutor skylark;
  private static final String GIT_TO_GIT_CONFIG =
      "transformations = [\n"
          + "    # TODO: Insert your transformations here\n"
          + "]\n"
          + "\n"
          + "core.workflow(\n"
          + "    name = 'default',\n"
          + "    origin = git.origin(\n"
          + "    url = 'github.com/origin'), \n"
          + "    destination = git.destination(\n"
          + "    url = 'github.com/destination'),\n"
          + "    authoring = authoring.pass_thru('Name <foo@bar.com>'),\n"
          + "    mode='SQUASH',\n"
          + "    transformations = transformations,\n"
          + ")";

  @Before
  public void setUp() throws Exception {
    skylark = new SkylarkTestExecutor(new OptionsBuilder());
  }

  @Test
  public void testGitToGitTemplate() throws Exception {
    ConfigBuilder underTest = new ConfigBuilder(new GitToGitTemplate());

    underTest.setNamedStringParameter("origin_url", "github.com/origin");
    underTest.setNamedStringParameter("destination_url", "github.com/destination");
    underTest.setNamedStringParameter("email", "Name <foo@bar.com>");
    underTest.addStringKeywordParameter("mode", "SQUASH");

    String builtConfig = underTest.build();
    assertThat(builtConfig).isEqualTo(GIT_TO_GIT_CONFIG);
    assertThat(underTest.isValid()).isTrue();

    // Verify that config is valid
    Config cfg = skylark.loadConfig(builtConfig);
    assertThat(cfg.getMigrations()).containsKey("default");
  }

  @Test
  public void testMissingRequiredField() {
    ConfigBuilder underTest = new ConfigBuilder(new GitToGitTemplate());
    underTest.setNamedStringParameter("origin_url", "github.com/origin");
    underTest.setNamedStringParameter("destination_url", "github.com/destination");

    // no email parameter is set
    assertThrows(IllegalStateException.class, underTest::build);
  }
}
