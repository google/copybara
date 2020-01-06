/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.copybara.config;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class CapturingConfigFileTest {
  private final MapConfigFile baseWrap =
      new MapConfigFile(
          ImmutableMap.of(
              "/foo", "foo".getBytes(UTF_8),
              "/bar", "bar".getBytes(UTF_8),
              "/baz/foo", "bazfoo".getBytes(UTF_8),
              "/baz/bar", "bazbar".getBytes(UTF_8)),
          "/foo");

  @Test
  public void testResolve() throws Exception {
    CapturingConfigFile capture = new CapturingConfigFile(baseWrap);
    assertThat(content(capture)).isEqualTo("foo");
    assertThat(capture.path()).isEqualTo("/foo");
    assertThat(content(capture.resolve("bar"))).isEqualTo("bar");
    ConfigFile bazFooConfig = capture.resolve("baz/foo");
    assertThat(content(bazFooConfig)).isEqualTo("bazfoo");
    assertThat(content(bazFooConfig.resolve("bar"))).isEqualTo("bazbar");
  }

  @Test
  public void relativeToRootIsRelative() {
    Path root = Paths.get("/foo/bar");
    CapturingConfigFile cfg = new CapturingConfigFile(
        new PathBasedConfigFile(root.resolve("baz"), root, /*identifierPrefix=*/null));
    assertThat(cfg.getIdentifier()).isEqualTo("baz");
  }

  @Test
  public void testBuildMap() throws Exception {
    CapturingConfigFile capture = new CapturingConfigFile(baseWrap);
    ConfigFile child = capture.resolve("bar");
    child.resolve("baz/foo"); // grandChild
    child.resolve("bar"); // repeat/self
    Map<String, ConfigFile> deps = capture.getAllLoadedFiles();
    assertThat(content(deps.get("/foo"))).isEqualTo("foo");
    assertThat(content(deps.get("/baz/foo"))).isEqualTo("bazfoo");
    assertThat(content(deps.get("/bar"))).isEqualTo("bar");
    assertThat(deps).hasSize(3);
  }

  private String content(ConfigFile file) throws Exception {
    return file.readContent();
  }
}
