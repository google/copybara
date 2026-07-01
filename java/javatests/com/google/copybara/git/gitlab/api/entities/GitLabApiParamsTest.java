/*
 * Copyright (C) 2025 Google LLC.
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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableList;
import com.google.copybara.git.gitlab.api.entities.GitLabApiParams.Param;
import java.net.URLEncoder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitLabApiParamsTest {
  @Test
  public void getQueryString_singleParam() {
    GitLabApiParams underTest =
        new GitLabApiParamsImpl(ImmutableList.of(new Param("param", "value")));

    assertThat(underTest.getQueryString()).isEqualTo("param=value");
  }

  @Test
  public void getQueryString_handlesUrlEncodingSuccessfully() {
    GitLabApiParams underTest =
        new GitLabApiParamsImpl(ImmutableList.of(new Param("param&key\"", "with%weird@chars")));

    assertThat(underTest.getQueryString()).isEqualTo("param%26key%22=with%25weird%40chars");
  }

  @Test
  public void getQueryString_moreThanOneParam() {
    GitLabApiParams underTest =
        new GitLabApiParamsImpl(
            ImmutableList.of(new Param("param1", "value1"), new Param("param2", "value2")));

    assertThat(underTest.getQueryString()).isEqualTo("param1=value1&param2=value2");
  }

  @Test
  public void getQueryString_paramsWithSameNameOk() {
    GitLabApiParams underTest =
        new GitLabApiParamsImpl(
            ImmutableList.of(new Param("param1", "value1"), new Param("param1", "value2")));

    assertThat(underTest.getQueryString()).isEqualTo("param1=value1&param1=value2");
  }

  @Test
  public void getQueryString_paramWithNonStringValue() {
    ImmutableList<String> value = ImmutableList.of("value1", "value2");
    GitLabApiParams underTest =
        new GitLabApiParamsImpl(ImmutableList.of(new Param("param", value)));

    // Check to see we use the toString result for the query string.
    assertThat(underTest.getQueryString())
        .isEqualTo("param=" + URLEncoder.encode(value.toString(), UTF_8));
  }

  @Test
  public void getQueryString_mixedParamTypes() {
    ImmutableList<String> value = ImmutableList.of("value2");
    GitLabApiParams underTest =
        new GitLabApiParamsImpl(
            ImmutableList.of(new Param("param1", "value1"), new Param("param2", value)));

    // Check to see we use the toString result for the query string.
    assertThat(underTest.getQueryString())
        .isEqualTo("param1=value1&param2=" + URLEncoder.encode(value.toString(), UTF_8));
  }

  private record GitLabApiParamsImpl(ImmutableList<Param> params) implements GitLabApiParams {}
}
