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
import com.google.copybara.exception.CannotResolveLabel;
import java.io.IOException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MapConfigFileTest {
  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Test
  public void testResolve() throws IOException, CannotResolveLabel {
    ImmutableMap<String, byte[]> map = ImmutableMap.of(
        "/foo", "foo".getBytes(UTF_8),
        "/bar", "bar".getBytes(UTF_8),
        "/baz/foo", "bazfoo".getBytes(UTF_8),
        "/baz/bar", "bazbar".getBytes(UTF_8));
    ConfigFile<?> fooConfig = new MapConfigFile(map, "/foo");
    assertThat(new String(fooConfig.content(), UTF_8)).isEqualTo("foo");
    assertThat(fooConfig.path()).isEqualTo("/foo");
    assertThat(new String(fooConfig.resolve("bar").content(), UTF_8)).isEqualTo("bar");

    ConfigFile<?> bazFooConfig = fooConfig.resolve("baz/foo");
    assertThat(new String(bazFooConfig.content(), UTF_8)).isEqualTo("bazfoo");

    // Checks that the correct bar is resolved.
    assertThat(new String(bazFooConfig.resolve("bar").content(), UTF_8)).isEqualTo("bazbar");
  }

  @Test
  public void testResolveAbsolute() throws IOException, CannotResolveLabel {
    ImmutableMap<String, byte[]> map = ImmutableMap.of(
        "foo", "foo".getBytes(UTF_8),
        "bar", "bar".getBytes(UTF_8),
        "baz/foo", "bazfoo".getBytes(UTF_8),
        "baz/bar", "bazbar".getBytes(UTF_8));

    ConfigFile<?> fooConfig  = new MapConfigFile(map, "foo");
    assertThat(new String(fooConfig.content(), UTF_8)).isEqualTo("foo");
    assertThat(fooConfig.path()).isEqualTo("foo");

    ConfigFile<?> bazFooConfig = fooConfig.resolve("//baz/foo");
    assertThat(new String(bazFooConfig.content(), UTF_8)).isEqualTo("bazfoo");
    assertThat(new String(bazFooConfig.resolve("//baz/bar").content(), UTF_8)).isEqualTo("bazbar");
  }

  @Test
  public void testResolveFailure() throws IOException, CannotResolveLabel {
    ImmutableMap<String, byte[]> map = ImmutableMap.of("/foo", "foo".getBytes(UTF_8));
    ConfigFile<?> fooConfig =  new MapConfigFile(ImmutableMap.copyOf(map), "/foo");
    thrown.expect(CannotResolveLabel.class);
    thrown.expectMessage("Cannot resolve '/bar': does not exist.");
    fooConfig.resolve("bar");
  }
}
