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
import static java.util.stream.Collectors.toMap;

import com.google.copybara.git.gitlab.api.entities.GitLabApiParams.Param;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ListProjectMergeRequestParamsTest {
  @Test
  public void noValuesSet() {
    ListProjectMergeRequestParams underTest = ListProjectMergeRequestParams.getDefaultInstance();

    assertThat(underTest.params()).isEmpty();
  }

  @Test
  public void setsSourceBranchSuccessfully() {
    ListProjectMergeRequestParams underTest =
        new ListProjectMergeRequestParams(Optional.of("my_branch"));

    assertThat(underTest.params().stream().collect(toMap(Param::key, Param::value)))
        .containsExactly("source_branch", "my_branch");
  }
}
