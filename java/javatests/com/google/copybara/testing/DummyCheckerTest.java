/*
 * Copyright (C) 2019 Google Inc.
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

package com.google.copybara.testing;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.jimfs.Jimfs;
import com.google.copybara.checks.CheckerException;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class DummyCheckerTest {

  private TestingConsole console = new TestingConsole();
  private DummyChecker checker;
  private Path testDir;

  @Before
  public void setUp() throws Exception {
    FileSystem fs = Jimfs.newFileSystem();
    testDir = fs.getPath("/testDir");
    Files.createDirectories(testDir);
    checker = new DummyChecker(ImmutableSet.of("bad", "DANGER"));
  }

  @Test
  public void testDoCheck_fields() throws CheckerException {
    checker.doCheck(ImmutableMap.of("field1", "good"), console);

    verifyInvalid(ImmutableMap.of("field1", "ba d", "field2", "BBBADDD"),
        "Bad word 'bad' found: field 'field2'");
    verifyInvalid(ImmutableMap.of("field1", "BADDANGER"),
        "Bad word 'bad' found: field 'field1'");
  }

  @Test
  public void testDoCheck_file() throws CheckerException, IOException {
    Path testFile = testDir.resolve("test.txt");
    Files.write(testFile, "Good contents\nThis is a good file\n".getBytes(StandardCharsets.UTF_8));

    checker.doCheck(testFile, console);

    Files.write(
        testFile, "Bad contents\nThis is a dangerous file\n".getBytes(StandardCharsets.UTF_8));
    verifyInvalid(testFile, "Bad word 'bad' found: /testDir/test.txt:1");

    Files.write(
        testFile, "First line\nThis is a dangerous file\n".getBytes(StandardCharsets.UTF_8));
    verifyInvalid(testFile, "Bad word 'danger' found: /testDir/test.txt:2");
  }

  private void verifyInvalid(ImmutableMap<String, String> fields, String expectedError) {
    try {
      checker.doCheck(fields, console);
      fail();
    } catch (CheckerException expected) {
      assertThat(expected).hasMessageThat().contains(expectedError);
    }
  }

  private void verifyInvalid(Path file, String expectedError) throws IOException {
    try {
      checker.doCheck(file, console);
      fail();
    } catch (CheckerException expected) {
      assertThat(expected).hasMessageThat().contains(expectedError);
    }
  }
}
