/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.copybara.hashing;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.io.MoreFiles;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import java.io.IOException;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class HashingModuleTest {
  @Rule public TemporaryFolder folder = new TemporaryFolder();

  private SkylarkTestExecutor starlark;
  Path checkoutDirectory;

  @Before
  public void setup() {
    checkoutDirectory = folder.getRoot().toPath();
    OptionsBuilder optionsBuilder = new OptionsBuilder();
    optionsBuilder.testingOptions.checkoutDirectory = checkoutDirectory;
    starlark = new SkylarkTestExecutor(optionsBuilder);
  }

  @Test
  public void testMd5Sum() throws IOException, ValidationException {
    String testContents = "asdf";
    // generated using `echo -n "asdf" | md5sum`
    String expectedHash = "912ec803b2ce49e4a541068d495ab570";

    Path testFile = checkoutDirectory.resolve("testfile.txt");
    MoreFiles.asByteSink(testFile).asCharSink(UTF_8).write(testContents);

    String hash =
        starlark.eval(
            "hash",
            """
            checkout_path = testing.get_checkout("testfile.txt")
            hash = hashing.path_md5_sum(checkout_path)""");
    assertThat(hash).isEqualTo(expectedHash);
  }

  @Test
  public void testSha256Sum() throws IOException, ValidationException {
    String testContents = "asdf";
    // generated using `echo -n "asdf" | sha256sum`
    String expectedHash = "f0e4c2f76c58916ec258f246851bea091d14d4247a2fc3e18694461b1816e13b";

    Path testFile = checkoutDirectory.resolve("testfile.txt");
    MoreFiles.asByteSink(testFile).asCharSink(UTF_8).write(testContents);

    String hash =
        starlark.eval(
            "hash",
            """
            checkout_path = testing.get_checkout("testfile.txt")
            hash = hashing.path_sha256_sum(checkout_path)""");
    assertThat(hash).isEqualTo(expectedHash);
  }

  @Test
  public void testHash() throws IOException, ValidationException {
    // generated using `echo -n "myhashinputmyotherinput" | sha256sum`
    String expectedHash = "40e57c2a84ee4d541e5d50a8458645bce4a5fb257161de085ff432439a3ea81c";
    String hash =
        starlark.eval(
            "hash", "hash = hashing.str_sha256_sum(input=['myhashinput', 'myotherinput'])");
    assertThat(hash).isEqualTo(expectedHash);
  }
}
