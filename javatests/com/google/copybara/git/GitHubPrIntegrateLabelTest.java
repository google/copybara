/*
 * Copyright (C) 2022 Google Inc.
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
package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.copybara.GeneralOptions;
import com.google.copybara.util.console.Console;
import java.nio.file.FileSystems;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class GitHubPrIntegrateLabelTest {

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock GitRepository repo;
  @Mock Console console;

  @Test
  public void testParseLabel() {
    GeneralOptions options =
        new GeneralOptions(ImmutableMap.of(), FileSystems.getDefault(), console);

    assertThat(GitHubPrIntegrateLabel.parse(
        "https://github.com/foo/bar/pull/18"
            + " from copybarrista:main dbb8386719596088dbf7513fad87559b2ff796cc   ", repo, options))
        .isNotNull();
  }
}
