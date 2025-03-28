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

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Value;
import com.google.copybara.git.gitlab.api.entities.MergeRequest.DetailedMergeStatus;
import com.google.copybara.git.gitlab.api.entities.MergeRequest.State;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class MergeRequestTest {
  private static final GsonFactory GSON_FACTORY = new GsonFactory();

  @Test
  public void testGsonParsing() throws Exception {
    String json =
"""
{
  "id": 12345,
  "iid": 98765,
  "detailed_merge_status": "mergeable",
  "source_branch": "capybara",
  "sha": "90993f8bffdf33e7a238838a56403f113cefdcbd",
  "state": "opened",
  "web_url": "https://gitlab.com/google/copybara/-/merge_requests/1"
}
""";
    MergeRequest underTest = GSON_FACTORY.fromString(json, MergeRequest.class);

    assertThat(underTest.getId()).isEqualTo(12345);
    assertThat(underTest.getIid()).isEqualTo(98765);
    assertThat(underTest.getDetailedMergeStatus()).isEqualTo(DetailedMergeStatus.MERGEABLE);
    assertThat(underTest.getSourceBranch()).isEqualTo("capybara");
    assertThat(underTest.getSha()).isEqualTo("90993f8bffdf33e7a238838a56403f113cefdcbd");
    assertThat(underTest.getState()).isEqualTo(MergeRequest.State.OPENED);
    assertThat(underTest.getWebUrl())
        .isEqualTo("https://gitlab.com/google/copybara/-/merge_requests/1");
  }

  @Test
  public void detailedMergeRequestField_gsonParsingWorksSuccessfully(
      @TestParameter DetailedMergeStatus detailedMergeStatus) throws Exception {
    String gsonValueName =
        DetailedMergeStatus.class
            .getField(detailedMergeStatus.name())
            .getAnnotationsByType(Value.class)[0]
            .value();
    String json =
        String.format(
"""
{"detailed_merge_status": "%s"}
""",
            gsonValueName);

    MergeRequest underTest = GSON_FACTORY.fromString(json, MergeRequest.class);

    assertThat(underTest.getDetailedMergeStatus()).isEqualTo(detailedMergeStatus);
  }

  @Test
  public void stateField_gsonParsingWorksSuccessfully(@TestParameter State state) throws Exception {
    String gsonValueName =
        State.class.getField(state.name()).getAnnotationsByType(Value.class)[0].value();
    String json =
        String.format(
"""
{"state": "%s"}
""",
            gsonValueName);

    MergeRequest underTest = GSON_FACTORY.fromString(json, MergeRequest.class);

    assertThat(underTest.getState()).isEqualTo(state);
  }
}
