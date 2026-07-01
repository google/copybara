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
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableMap;
import com.google.copybara.exception.CannotResolveLabel;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MapConfigFileTest {
  @Test
  public void testResolve() throws IOException, CannotResolveLabel {
    ImmutableMap<String, byte[]> map = ImmutableMap.of(
        "/foo", "foo".getBytes(UTF_8),
        "/bar", "bar".getBytes(UTF_8),
        "/baz/foo", "bazfoo".getBytes(UTF_8),
        "/baz/bar", "bazbar".getBytes(UTF_8));
    ConfigFile fooConfig = new MapConfigFile(map, "/foo");
    assertThat(fooConfig.readContent()).isEqualTo("foo");
    assertThat(fooConfig.path()).isEqualTo("/foo");
    assertThat(fooConfig.resolve("bar").readContent()).isEqualTo("bar");

    ConfigFile bazFooConfig = fooConfig.resolve("baz/foo");
    assertThat(bazFooConfig.readContent()).isEqualTo("bazfoo");

    // Checks that the correct bar is resolved.
    assertThat(bazFooConfig.resolve("bar").readContent()).isEqualTo("bazbar");
  }

  @Test
  public void testResolveAbsolute() throws IOException, CannotResolveLabel {
    ImmutableMap<String, byte[]> map = ImmutableMap.of(
        "foo", "foo".getBytes(UTF_8),
        "bar", "bar".getBytes(UTF_8),
        "baz/foo", "bazfoo".getBytes(UTF_8),
        "baz/bar", "bazbar".getBytes(UTF_8));

    ConfigFile fooConfig  = new MapConfigFile(map, "foo");
    assertThat(fooConfig.readContent()).isEqualTo("foo");
    assertThat(fooConfig.path()).isEqualTo("foo");

    ConfigFile bazFooConfig = fooConfig.resolve("//baz/foo");
    assertThat(bazFooConfig.readContent()).isEqualTo("bazfoo");
    assertThat(bazFooConfig.resolve("//baz/bar").readContent()).isEqualTo("bazbar");
  }

  @Test
  public void testResolveFailure() throws CannotResolveLabel {
    ImmutableMap<String, byte[]> map = ImmutableMap.of("/foo", "foo".getBytes(UTF_8));
    ConfigFile fooConfig =  new MapConfigFile(ImmutableMap.copyOf(map), "/foo");
    CannotResolveLabel thrown =
        assertThrows(CannotResolveLabel.class, () -> fooConfig.resolve("bar"));
    assertThat(thrown).hasMessageThat().contains("Cannot resolve '/bar': does not exist.");
  }
}
