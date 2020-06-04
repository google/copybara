/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.copybara.jcommander;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableMap;
import com.google.copybara.ModuleSupplier;
import com.google.copybara.Options;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.util.console.testing.TestingConsole;

import com.beust.jcommander.JCommander;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;

@RunWith(JUnit4.class)
public class GlobConverterTest {

  @Test
  public void testGlob() throws IOException {
    PathMatcher matcher = parseGlob("some/test1/**,some/test2/**,-some/test2/exclude/**");

    assertThat(matcher.matches(Paths.get("/some/test1/file"))).isTrue();
    assertThat(matcher.matches(Paths.get("/some/test2/file"))).isTrue();
    assertThat(matcher.matches(Paths.get("/some/test2/include/file"))).isTrue();
    assertThat(matcher.matches(Paths.get("/some/test2/exclude/file"))).isFalse();
  }

  private PathMatcher parseGlob(String value) throws IOException {
    ImmutableMap<String, String> envWithHome =
        ImmutableMap.of("HOME", Files.createTempDirectory("foo").toString());

    ModuleSupplier moduleSupplier = new ModuleSupplier(envWithHome, FileSystems.getDefault(),
        new TestingConsole());
    Options options = moduleSupplier.create().getOptions();
    JCommander jCommander = new JCommander(options.getAll());
    jCommander.parse("--read-config-from-head-paths", value);
    return options.get(WorkflowOptions.class).readConfigFromChangePaths
        .relativeTo(Paths.get("/"));
  }
}
