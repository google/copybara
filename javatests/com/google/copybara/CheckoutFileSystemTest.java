/*
 * Copyright (C) 2026 Google LLC
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

package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import net.starlark.java.eval.EvalException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CheckoutFileSystemTest {

  private Path checkoutDir;
  private CheckoutFileSystem fs;

  @Before
  public void setUp() throws Exception {
    checkoutDir = Files.createTempDirectory("checkout");
    fs = new CheckoutFileSystem(checkoutDir);
  }

  @Test
  public void writePath_directorySymlinkEscape_throwsEvalException() throws Exception {
    Path outsideDir = Files.createTempDirectory("outside");
    Files.createSymbolicLink(checkoutDir.resolve("pwn"), outsideDir);

    CheckoutPath path = fs.newPath("pwn/nested/hit.txt");
    EvalException expected = assertThrows(EvalException.class, () -> fs.writePath(path, "ESCAPED"));

    assertThat(expected).hasMessageThat().contains("is not inside the checkout directory");
    assertThat(Files.exists(outsideDir.resolve("nested/hit.txt"))).isFalse();
  }

  @Test
  public void readPath_directorySymlinkEscape_throwsEvalException() throws Exception {
    Path outsideDir = Files.createTempDirectory("outside");
    Files.writeString(outsideDir.resolve("secret.txt"), "SECRET");
    Files.createSymbolicLink(checkoutDir.resolve("pwn"), outsideDir);

    CheckoutPath path = fs.newPath("pwn/secret.txt");
    EvalException expected = assertThrows(EvalException.class, () -> fs.readPath(path));

    assertThat(expected).hasMessageThat().contains("is not inside the checkout directory");
  }

  @Test
  public void setExecutable_directorySymlinkEscape_throwsEvalException() throws Exception {
    Path outsideDir = Files.createTempDirectory("outside");
    Path outsideScript = Files.writeString(outsideDir.resolve("script.sh"), "#!/bin/sh\n");
    outsideScript.toFile().setExecutable(false);
    Files.createSymbolicLink(checkoutDir.resolve("pwn"), outsideDir);

    CheckoutPath path = fs.newPath("pwn/script.sh");
    EvalException expected = assertThrows(EvalException.class, () -> fs.setExecutable(path, true));

    assertThat(expected).hasMessageThat().contains("is not inside the checkout directory");
    assertThat(Files.isExecutable(outsideScript)).isFalse();
  }

  @Test
  public void createSymlink_directorySymlinkEscape_throwsEvalException() throws Exception {
    Path outsideDir = Files.createTempDirectory("outside");
    Files.createSymbolicLink(checkoutDir.resolve("pwn"), outsideDir);
    Files.writeString(checkoutDir.resolve("inside.txt"), "INSIDE");

    CheckoutPath link = fs.newPath("pwn/link.txt");
    CheckoutPath target = fs.newPath("inside.txt");
    EvalException expected =
        assertThrows(EvalException.class, () -> fs.createSymlink(link, target));

    assertThat(expected).hasMessageThat().contains("is not inside the checkout directory");
    assertThat(Files.exists(outsideDir.resolve("link.txt"))).isFalse();
  }

  @Test
  public void writePath_danglingLeafSymlinkOutside_throwsEvalException() throws Exception {
    Path outsideDir = Files.createTempDirectory("outside");
    Path nonexistentTarget = outsideDir.resolve("new_target.txt");
    Files.createSymbolicLink(checkoutDir.resolve("dangling"), nonexistentTarget);

    CheckoutPath path = fs.newPath("dangling");
    assertThrows(EvalException.class, () -> fs.writePath(path, "ESCAPED"));

    assertThat(Files.exists(nonexistentTarget)).isFalse();
  }

  @Test
  public void writeAndReadPath_internalDirectorySymlink_succeeds() throws Exception {
    Path realSubdir = Files.createDirectories(checkoutDir.resolve("real_dir"));
    Files.createSymbolicLink(checkoutDir.resolve("link_dir"), Path.of("real_dir"));

    fs.writePath(fs.newPath("link_dir/nested/ok.txt"), "INSIDE");

    assertThat(fs.readPath(fs.newPath("link_dir/nested/ok.txt"))).isEqualTo("INSIDE");
    assertThat(Files.readString(realSubdir.resolve("nested/ok.txt"))).isEqualTo("INSIDE");
  }
}
