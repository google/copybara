/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara.util.console;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.jimfs.Jimfs;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FileConsoleTest {

  private Path file;

  @Before
  public void setup() throws IOException {
    file = Jimfs.newFileSystem().getPath("/tmp/foo.txt");
    Files.createDirectories(file.getParent());
  }

  @Test
  public void testNewFile() throws IOException {
    runTest(file);
  }

  @Test
  public void testExistingFileIsTruncated() throws IOException {
    Files.write(file, "Previous content".getBytes(StandardCharsets.UTF_8));
    runTest(file);
  }

  private void runTest(Path file) throws IOException {
    TestingConsole delegate = new TestingConsole();
    try (FileConsole fileConsole = new FileConsole(delegate, file)) {
      fileConsole.startupMessage("v1");
      fileConsole.info("This is info");
      fileConsole.warn("This is warning");
      fileConsole.error("This is error");
      fileConsole.verbose("This is verbose");
      fileConsole.progress("This is progress");
    }

    List<String> lines = Files.readAllLines(file);
    assertThat(lines)
        .containsExactly(
            "INFO: Copybara source mover (Version: v1)",
            "INFO: This is info",
            "WARNING: This is warning",
            "ERROR: This is error",
            "VERBOSE: This is verbose",
            "PROGRESS: This is progress");

    delegate
        .assertThat()
        .matchesNext(MessageType.INFO, "Copybara source mover [(]Version: v1[)]")
        .matchesNext(MessageType.INFO, "This is info")
        .matchesNext(MessageType.WARNING, "This is warning")
        .matchesNext(MessageType.ERROR, "This is error")
        .matchesNext(MessageType.VERBOSE, "This is verbose")
        .matchesNext(MessageType.PROGRESS, "This is progress");
  }
}
