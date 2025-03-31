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

// import com.google.api.client.json.JsonParser;
import com.google.api.client.json.JsonToken;
import com.google.api.client.json.gson.GsonFactory;
import com.google.copybara.git.gitlab.api.entities.MergeRequest.DetailedMergeStatus;
import com.google.copybara.git.gitlab.api.entities.MergeRequest.State;
import com.google.gson.JsonParser;
import com.google.gson.JsonObject;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SetExternalStatusCheckResponseTest {
  private static final GsonFactory GSON_FACTORY = new GsonFactory();

  @Test
  public void gsonConvertToJson() throws Exception {
    SetExternalStatusCheckResponse underTest =
        new SetExternalStatusCheckResponse(
            1,
            new MergeRequest(
                1,
                1,
                "shaValue",
                "title",
                "description",
                DetailedMergeStatus.DRAFT_STATUS,
                "sourceBranch",
                "webUrl",
                State.OPENED),
            new ExternalStatusCheck(1, "name", 1, "url", ImmutableList.of(), false));

    String json = GSON_FACTORY.toString(underTest);
    JsonObject parsedJson = JsonParser.parseString(json).getAsJsonObject();
    assertThat(parsedJson.get("id").getAsInt()).isEqualTo(1);

    JsonObject mergeRequest = parsedJson.getAsJsonObject("merge_request");
    assertThat(mergeRequest.get("id").getAsInt()).isEqualTo(1);
    assertThat(mergeRequest.get("iid").getAsInt()).isEqualTo(1);
    assertThat(mergeRequest.get("sha").getAsString()).isEqualTo("shaValue");
    assertThat(mergeRequest.get("title").getAsString()).isEqualTo("title");
    assertThat(mergeRequest.get("description").getAsString()).isEqualTo("description");
    assertThat(mergeRequest.get("detailed_merge_status").getAsString()).isEqualTo("draft_status");
    assertThat(mergeRequest.get("source_branch").getAsString()).contains("sourceBranch");
    assertThat(mergeRequest.get("web_url").getAsString()).isEqualTo("webUrl");
    assertThat(mergeRequest.get("state").getAsString()).isEqualTo("opened");

    JsonObject externalStatusCheck = parsedJson.getAsJsonObject("external_status_check");
    assertThat(externalStatusCheck.get("id").getAsInt()).isEqualTo(1);
    assertThat(externalStatusCheck.get("name").getAsString()).isEqualTo("name");
    assertThat(externalStatusCheck.get("project_id").getAsInt()).isEqualTo(1);
    assertThat(externalStatusCheck.get("external_url").getAsString()).isEqualTo("url");
    assertThat(externalStatusCheck.get("protected_branches").getAsJsonArray().size()).isEqualTo(0);
    assertThat(externalStatusCheck.get("hmac").getAsBoolean()).isEqualTo(false);
  }
}
