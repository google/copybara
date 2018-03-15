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

import com.google.common.jimfs.Jimfs;
import com.google.copybara.exception.CannotResolveLabel;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PathBasedConfigFileTest {

  private FileSystem fs;

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  @Before
  public void setup() {
    fs = Jimfs.newFileSystem();
  }

  @Test
  public void testResolve() throws IOException, CannotResolveLabel {
    Path foo = Files.write(fs.getPath("/foo"), "foo".getBytes());
    Files.write(fs.getPath("/bar"), "bar".getBytes());
    Files.createDirectories(fs.getPath("/baz"));
    Files.write(fs.getPath("/baz/foo"), "bazfoo".getBytes());
    Files.write(fs.getPath("/baz/bar"), "bazbar".getBytes());

    ConfigFile fooConfig = new PathBasedConfigFile(foo, /*rootPath=*/null);
    assertThat(fooConfig.content()).isEqualTo("foo".getBytes());
    assertThat(fooConfig.path()).isEqualTo("/foo");
    assertThat(fooConfig.relativeToRoot()).isEqualTo(fooConfig.path());
    assertThat(fooConfig.resolve("bar").content()).isEqualTo("bar".getBytes());

    ConfigFile bazFooConfig = fooConfig.resolve("baz/foo");
    assertThat(bazFooConfig.content()).isEqualTo("bazfoo".getBytes());
    // Checks that the correct bar is resolved.
    assertThat(bazFooConfig.resolve("bar").content()).isEqualTo("bazbar".getBytes());
  }

  @Test
  public void testResolveAbsolute() throws IOException, CannotResolveLabel {
    Files.write(fs.getPath("/foo"), "foo".getBytes());
    Files.createDirectories(fs.getPath("/baz"));
    Files.write(fs.getPath("/baz/foo"), "bazfoo".getBytes());
    Files.write(fs.getPath("/baz/bar"), "bazbar".getBytes());

    ConfigFile fooConfig = new PathBasedConfigFile(fs.getPath("/foo"),
        /*rootPath=*/fs.getPath("/"));
    assertThat(fooConfig.relativeToRoot()).isEqualTo("foo");
    assertThat(fooConfig.content()).isEqualTo("foo".getBytes());
    assertThat(fooConfig.path()).isEqualTo("/foo");

    ConfigFile bazFooConfig = fooConfig.resolve("//baz/foo");
    assertThat(bazFooConfig.content()).isEqualTo("bazfoo".getBytes());
    assertThat(bazFooConfig.resolve("//baz/bar").content()).isEqualTo("bazbar".getBytes());
  }

  @Test
  public void testResolveFailure() throws IOException, CannotResolveLabel {
    ConfigFile fooConfig = new PathBasedConfigFile(
        Files.write(fs.getPath("/foo"), "foo".getBytes()),
        /*rootPath=*/null);
    thrown.expect(CannotResolveLabel.class);
    thrown.expectMessage("Cannot find 'bar'. '/bar' does not exist.");
    fooConfig.resolve("bar");
  }

  @Test
  public void testAbsoluteResolveFailsIfNoRoot() throws IOException, CannotResolveLabel {
    ConfigFile fooConfig = new PathBasedConfigFile(Files.write(fs.getPath("/foo"),
        "foo".getBytes()),/*rootPath=*/null);
    thrown.expect(CannotResolveLabel.class);
    thrown.expectMessage("Absolute paths are not allowed because the root config path"
        + " couldn't be automatically detected");
    fooConfig.resolve("//bar");
  }
}
