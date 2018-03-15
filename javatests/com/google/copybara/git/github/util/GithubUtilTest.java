/*
 * Copyright (C) 2017 Google Inc.
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

package com.google.copybara.git.github.util;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.git.github.util.GithubUtil.getProjectNameFromUrl;
import static org.junit.Assert.fail;

import com.google.copybara.exception.ValidationException;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GithubUtilTest {

  @Test
  public void testGetPRojectNameFromUrl() throws Exception {
    assertThat(getProjectNameFromUrl("https://github.com/foo")).isEqualTo("foo");
    assertThat(getProjectNameFromUrl("https://github.com/foo/bar")).isEqualTo("foo/bar");
    assertThat(getProjectNameFromUrl("ssh://git@github.com/foo/bar.git")).isEqualTo("foo/bar");
    assertThat(getProjectNameFromUrl("git@github.com/foo/bar.git")).isEqualTo("foo/bar");
    assertThat(getProjectNameFromUrl("git@github.com:foo/bar.git")).isEqualTo("foo/bar");
    try {
      getProjectNameFromUrl("foo@bar:baz");
      fail();
    } catch (ValidationException e) {
      assertThat(e.getMessage()).contains("Cannot find project name");
    }
    try {
      getProjectNameFromUrl("file://some/local/dir");
      fail();
    } catch (ValidationException e) {
      assertThat(e.getMessage()).contains("Not a github url");
    }
  }
}
