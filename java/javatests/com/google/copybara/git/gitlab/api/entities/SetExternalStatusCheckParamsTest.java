/*
 * Copyright (C) 2025 Google LLC
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

package com.google.copybara.git.gitlab.api.entities;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.client.json.JsonParser;
import com.google.api.client.json.JsonToken;
import com.google.api.client.json.gson.GsonFactory;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SetExternalStatusCheckParamsTest {
  private static final GsonFactory GSON_FACTORY = new GsonFactory();

  @Test
  public void gsonConvertToJson() throws Exception {
    SetExternalStatusCheckParams underTest =
        new SetExternalStatusCheckParams(
            /* projectId= */ 12345,
            /* mergeRequestIid= */ 12345,
            "shaValue",
            /* externalStatusCheckId= */ 12345,
            "passed");

    String json = GSON_FACTORY.toString(underTest);

    JsonParser jsonParser = GSON_FACTORY.createJsonParser(json);
    jsonParser.skipToKey("id");
    assertThat(jsonParser.getIntValue()).isEqualTo(12345);
    jsonParser = GSON_FACTORY.createJsonParser(json);
    jsonParser.skipToKey("merge_request_iid");
    assertThat(jsonParser.getIntValue()).isEqualTo(12345);
    jsonParser = GSON_FACTORY.createJsonParser(json);
    jsonParser.skipToKey("sha");
    assertThat(jsonParser.getText()).isEqualTo("shaValue");
    jsonParser = GSON_FACTORY.createJsonParser(json);
    jsonParser.skipToKey("external_status_check_id");
    assertThat(jsonParser.getIntValue()).isEqualTo(12345);
    jsonParser = GSON_FACTORY.createJsonParser(json);
    jsonParser.skipToKey("status");
    assertThat(jsonParser.getText()).isEqualTo("passed");
  }
}
