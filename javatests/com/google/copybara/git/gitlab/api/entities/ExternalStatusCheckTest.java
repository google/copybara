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
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public class ExternalStatusCheckTest {
  private static final GsonFactory GSON_FACTORY = new GsonFactory();

  @Test
  public void testGsonParsing() throws Exception {
    String json =
"""
{
  "id": 1,
    "name": "test-service",
    "project_id": 13429,
    "external_url": "https://perflab.tycho.joonix.net/api/v4",
    "protected_branches": [],
    "hmac": false
}
""";
    ExternalStatusCheck underTest = GSON_FACTORY.fromString(json, ExternalStatusCheck.class);

    assertThat(underTest.getStatusCheckId()).isEqualTo(1);
    assertThat(underTest.getName()).isEqualTo("test-service");
    assertThat(underTest.getExternalUrl()).isEqualTo("https://perflab.tycho.joonix.net/api/v4");
    assertThat(underTest.getProtectedBranches()).isEmpty();
    assertThat(underTest.getHmac()).isEqualTo(false);
  }
}
