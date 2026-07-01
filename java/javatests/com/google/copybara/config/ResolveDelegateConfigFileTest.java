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

package com.google.copybara.config;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;

import com.google.common.jimfs.Jimfs;
import com.google.copybara.exception.CannotResolveLabel;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ResolveDelegateConfigFileTest {

  private FileSystem fs;

  @Before
  public void setUp() {
    fs = Jimfs.newFileSystem();
  }

  @Test
  public void testFirstConfigResolves() throws Exception {
    Path pathToFoo = Files.writeString(fs.getPath("/foo"), "foo");
    Files.writeString(fs.getPath("/bar"), "bar");

    Files.createDirectories(fs.getPath("/baz"));
    Files.writeString(fs.getPath("/baz/foo"), "bazfoo");
    Files.writeString(fs.getPath("/baz/bar"), "bazbar");

    ConfigFile fooConfig =
        new PathBasedConfigFile(pathToFoo, /*rootPath=*/ null, /*identifierPrefix=*/ null);

    ConfigFile bazFooConfig = fooConfig.resolve("baz/foo");
    ResolveDelegateConfigFile resolveDelegateConfigFile =
        new ResolveDelegateConfigFile(fooConfig, bazFooConfig);

    assertThat(resolveDelegateConfigFile.resolve("bar").readContent()).isEqualTo("bar");
  }

  @Test
  public void testResolveNeedsSecondConfigFile() throws Exception {
    Path pathToFoo = Files.write(fs.getPath("/foo"), "foo".getBytes(UTF_8));
    Files.createDirectories(fs.getPath("/baz"));
    Files.writeString(fs.getPath("/baz/foo"), "bazfoo");
    Files.writeString(fs.getPath("/baz/bar"), "bazbar");

    ConfigFile fooConfig =
        new PathBasedConfigFile(pathToFoo, /*rootPath=*/ null, /*identifierPrefix=*/ null);

    ConfigFile bazFooConfig = fooConfig.resolve("baz/foo");
    ResolveDelegateConfigFile resolveDelegateConfigFile =
        new ResolveDelegateConfigFile(fooConfig, bazFooConfig);

    // fooConfig doesn't have bar -- we need to resolve to the second config to get bazbar
    assertThat(resolveDelegateConfigFile.resolve("bar").readContent()).isEqualTo("bazbar");
  }

  @Test
  public void testNeitherConfigCanResolve() throws Exception {
    Path pathToFoo = Files.write(fs.getPath("/foo"), "foo".getBytes(UTF_8));
    Files.createDirectories(fs.getPath("/baz"));
    Files.writeString(fs.getPath("/baz/foo"), "bazfoo");

    ConfigFile fooConfig =
        new PathBasedConfigFile(pathToFoo, /*rootPath=*/ null, /*identifierPrefix=*/ null);

    ConfigFile bazFooConfig = fooConfig.resolve("baz/foo");

    ResolveDelegateConfigFile resolveDelegateConfigFile =
        new ResolveDelegateConfigFile(fooConfig, bazFooConfig);
    CannotResolveLabel thrown =
        assertThrows(CannotResolveLabel.class, () -> resolveDelegateConfigFile.resolve("bar"));

    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "Could not resolve main config or second config to path 'bar'. Main config path is"
                + " '/foo', second config path is '/baz/foo'");
  }
}
