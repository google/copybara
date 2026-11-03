/*
 * Copyright (C) 2026 Google LLC
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

package com.google.copybara.onboard.ai;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.copybara.testing.FakeLlmClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class LlmClientTest {

  @Test
  public void generateJson_unsupportedByDefault() {
    LlmClient client = prompt -> "text";

    LlmException e = assertThrows(LlmException.class, () -> client.generateJson("prompt", "{}"));

    assertThat(e).hasMessageThat().contains("doesn't support structured output");
  }

  @Test
  public void fakeLlmClient_generateJson_recordsPromptAndSchema() throws Exception {
    FakeLlmClient client = new FakeLlmClient().respondWith("{\"ok\": true}");

    assertThat(client.generateJson("the prompt", "{\"type\": \"object\"}"))
        .isEqualTo("{\"ok\": true}");
    assertThat(client.getPrompts()).containsExactly("the prompt");
    assertThat(client.getSchemas()).containsExactly("{\"type\": \"object\"}");
  }
}
