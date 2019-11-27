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

package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ChangeMessageTest {

  private SkylarkTestExecutor skylarkTestExecutor;

  @Before
  public void setUp() throws Exception {
    OptionsBuilder options = new OptionsBuilder();
    skylarkTestExecutor = new SkylarkTestExecutor(options);
  }

  @Test
  public void testEmptyMessageParser() {
    ChangeMessage msg = ChangeMessage.parseMessage("\n\nGitOrigin-RevId: 12345\n");
    assertThat(msg.getText()).isEqualTo("");
    assertThat(msg.getLabels()).hasSize(1);
    assertThat(msg.getLabels().get(0).getName()).isEqualTo("GitOrigin-RevId");
  }

  @Test
  public void testMessageParser() {
    ChangeMessage msg = ChangeMessage.parseMessage(""
        + "Test parse title message\n"
        + "\n"
        + "Fixes https://github.com/test");
    assertThat(msg.toString()).isEqualTo(""
        + "Test parse title message\n"
        + "\n"
        + "Fixes https://github.com/test\n");
    assertThat(msg.getLabels()).hasSize(1);
  }

  private static final String CHANGE_MESSAGE_SKYLARK =
      ""
          + "First line\\n"
          + "Second line\\n"
          + "\\n"
          + "GitOrigin-RevId: 12345\\n"
          + "Other-label: AA\\n"
          + "Other-label: BB\\n";

  @Test
  public void testParseMessageSkylark() throws Exception {
    String var = String.format("parse_message('%s')", CHANGE_MESSAGE_SKYLARK);

    ImmutableMap<String, Object> expectedFieldValues =
        ImmutableMap.<String, Object>builder()
            .put("first_line", "First line")
            .put("text", "First line\nSecond line")
            .put("label_values('GitOrigin-RevId')[0]", "12345")
            .put("label_values('Other-label')[0]", "AA")
            .put("label_values('Other-label')[1]", "BB")
            .build();
    skylarkTestExecutor.verifyFields(var, expectedFieldValues);
  }
}
