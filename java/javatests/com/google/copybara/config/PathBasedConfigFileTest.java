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

import com.google.common.jimfs.Jimfs;
import com.google.copybara.exception.CannotResolveLabel;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PathBasedConfigFileTest {

  private FileSystem fs;

  @Before
  public void setup() {
    fs = Jimfs.newFileSystem();
  }

  @Test
  public void testResolve() throws IOException, CannotResolveLabel {
    Path foo = Files.write(fs.getPath("/foo"), "foo".getBytes(UTF_8));
    Files.write(fs.getPath("/bar"), "bar".getBytes(UTF_8));
    Files.createDirectories(fs.getPath("/baz"));
    Files.write(fs.getPath("/baz/foo"), "bazfoo".getBytes(UTF_8));
    Files.write(fs.getPath("/baz/bar"), "bazbar".getBytes(UTF_8));

    ConfigFile fooConfig = new PathBasedConfigFile(foo, /*rootPath=*/null,
        /*identifierPrefix=*/null);
    assertThat(fooConfig.readContent()).isEqualTo("foo");
    assertThat(fooConfig.path()).isEqualTo("/foo");
    assertThat(fooConfig.getIdentifier()).isEqualTo(fooConfig.path());
    assertThat(fooConfig.resolve("bar").readContent()).isEqualTo("bar");

    ConfigFile bazFooConfig = fooConfig.resolve("baz/foo");
    assertThat(bazFooConfig.readContent()).isEqualTo("bazfoo");
    // Checks that the correct bar is resolved.
    assertThat(bazFooConfig.resolve("bar").readContent()).isEqualTo("bazbar");
  }

  @Test
  public void testResolveWithIdentity() throws IOException {
    Path root = fs.getPath("foo/bar/baz").toAbsolutePath();
    Files.createDirectories(root);
    Path foo = Files.write(root.resolve("file.txt"), "foo".getBytes(UTF_8));

    ConfigFile fooConfig = new PathBasedConfigFile(foo, root, "PREFIX");
    assertThat(fooConfig.getIdentifier()).isEqualTo("PREFIX/file.txt");
  }

  @Test
  public void testResolveAbsolute() throws IOException, CannotResolveLabel {
    Files.write(fs.getPath("/foo"), "foo".getBytes(UTF_8));
    Files.createDirectories(fs.getPath("/baz"));
    Files.write(fs.getPath("/baz/foo"), "bazfoo".getBytes(UTF_8));
    Files.write(fs.getPath("/baz/bar"), "bazbar".getBytes(UTF_8));

    ConfigFile fooConfig = new PathBasedConfigFile(fs.getPath("/foo"),
        /*rootPath=*/fs.getPath("/"), /*identifierPrefix=*/null);
    assertThat(fooConfig.getIdentifier()).isEqualTo("foo");
    assertThat(fooConfig.readContent()).isEqualTo("foo");
    assertThat(fooConfig.path()).isEqualTo("/foo");

    ConfigFile bazFooConfig = fooConfig.resolve("//baz/foo");
    assertThat(bazFooConfig.readContent()).isEqualTo("bazfoo");
    assertThat(bazFooConfig.resolve("//baz/bar").readContent()).isEqualTo("bazbar");
  }

  @Test
  public void testResolveFailure() throws IOException, CannotResolveLabel {
    ConfigFile fooConfig = new PathBasedConfigFile(
        Files.write(fs.getPath("/foo"), "foo".getBytes(UTF_8)),
        /*rootPath=*/null, /*identifierPrefix=*/null);
    CannotResolveLabel thrown =
        assertThrows(CannotResolveLabel.class, () -> fooConfig.resolve("bar"));
    assertThat(thrown).hasMessageThat().contains("Cannot find 'bar'. '/bar' does not exist.");
  }

  @Test
  public void testAbsoluteResolveFailsIfNoRoot() throws IOException, CannotResolveLabel {
    ConfigFile fooConfig = new PathBasedConfigFile(Files.write(fs.getPath("/foo"),
        "foo".getBytes(UTF_8)),/*rootPath=*/null, /*identifierPrefix=*/null);
    CannotResolveLabel thrown =
        assertThrows(CannotResolveLabel.class, () -> fooConfig.resolve("//bar"));
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "Absolute paths are not allowed because the root config path"
                + " couldn't be automatically detected");
  }
}
