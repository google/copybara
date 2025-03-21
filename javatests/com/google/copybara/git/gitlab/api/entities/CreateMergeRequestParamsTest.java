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
public class CreateMergeRequestParamsTest {
  private static final GsonFactory GSON_FACTORY = new GsonFactory();

  @Test
  public void gsonConvertToJson() throws Exception {
    CreateMergeRequestParams underTest =
        new CreateMergeRequestParams(
            /* projectId= */ 12345,
            "sourceBranchValue",
            "targetBranchValue",
            "titleValue",
            "descriptionValue",
            /* assigneeIds= */ ImmutableList.of(1, 2, 3, 4, 5));

    String json = GSON_FACTORY.toString(underTest);

    JsonParser jsonParser = GSON_FACTORY.createJsonParser(json);
    jsonParser.skipToKey("id");
    assertThat(jsonParser.getIntValue()).isEqualTo(12345);
    jsonParser = GSON_FACTORY.createJsonParser(json);
    jsonParser.skipToKey("source_branch");
    assertThat(jsonParser.getText()).isEqualTo("sourceBranchValue");
    jsonParser = GSON_FACTORY.createJsonParser(json);
    jsonParser.skipToKey("target_branch");
    assertThat(jsonParser.getText()).isEqualTo("targetBranchValue");
    jsonParser = GSON_FACTORY.createJsonParser(json);
    jsonParser.skipToKey("title");
    assertThat(jsonParser.getText()).isEqualTo("titleValue");
    jsonParser = GSON_FACTORY.createJsonParser(json);
    jsonParser.skipToKey("description");
    assertThat(jsonParser.getText()).isEqualTo("descriptionValue");
    jsonParser = GSON_FACTORY.createJsonParser(json);
    jsonParser.skipToKey("assignee_ids");
    Collection<Integer> assigneeIds = jsonParser.parseArray(ArrayList.class, Integer.class);
    assertThat(assigneeIds).containsExactly(1, 2, 3, 4, 5).inOrder();
  }

  @Test
  public void gsonConvertToJson_nullableValuesWithNullAreNotPresent() throws Exception {
    CreateMergeRequestParams underTest =
        new CreateMergeRequestParams(
            /* projectId= */ 12345,
            /* sourceBranch= */ null,
            /* targetBranch= */ null,
            /* title= */ null,
            /* description= */ null,
            /* assigneeIds= */ ImmutableList.of(1, 2, 3, 4, 5));

    String json = GSON_FACTORY.toString(underTest);

    JsonParser jsonParser = GSON_FACTORY.createJsonParser(json);
    // We look for the END_OBJECT to ensure that the key is not present.
    jsonParser.skipToKey("source_branch");
    assertThat(jsonParser.getCurrentToken()).isEqualTo(JsonToken.END_OBJECT);
    jsonParser = GSON_FACTORY.createJsonParser(json);
    jsonParser.skipToKey("target_branch");
    assertThat(jsonParser.getCurrentToken()).isEqualTo(JsonToken.END_OBJECT);
    jsonParser = GSON_FACTORY.createJsonParser(json);
    jsonParser.skipToKey("title");
    assertThat(jsonParser.getCurrentToken()).isEqualTo(JsonToken.END_OBJECT);
    jsonParser = GSON_FACTORY.createJsonParser(json);
    jsonParser.skipToKey("description");
    assertThat(jsonParser.getCurrentToken()).isEqualTo(JsonToken.END_OBJECT);
  }
}
