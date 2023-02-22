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

package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.starlark.java.eval.EvalException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CheckoutPathTest {

  Path checkoutDir;

  @Before
  public void setUp() throws Exception {
    checkoutDir = Files.createTempDirectory("dir");
  }

  @Test
  public void testRemoveSelf() throws Exception {
    Path toDelete = Path.of("file/to/delete");
    Path absoluteDir = checkoutDir.resolve(toDelete);
    Files.createDirectories(absoluteDir.getParent());
    Files.writeString(absoluteDir, "I will be deleted");
    CheckoutPath underTest = new CheckoutPath(toDelete, checkoutDir);

    underTest.remove();

    assertThat(Files.exists(absoluteDir)).isFalse();
  }

  @Test
  public void testRemoveDirNonrecursive() throws Exception {
    Path emptyDir = Path.of("parent/dir");
    Path emptyDirAbsolute = checkoutDir.resolve(emptyDir);
    Files.createDirectories(emptyDirAbsolute);
    CheckoutPath underTest = new CheckoutPath(emptyDirAbsolute, checkoutDir);

    underTest.rmDir(false);

    assertThat(Files.exists(emptyDirAbsolute)).isFalse();
  }

  @Test
  public void testRemoveDirRecursive() throws Exception {
    Path dir = Path.of("parent/dir");
    Path toDelete = dir.resolve("to_delete.txt");
    Path toDeleteAbsolute = checkoutDir.resolve(toDelete);
    Files.createDirectories(toDeleteAbsolute.getParent());
    Files.writeString(toDeleteAbsolute, "I will be deleted");
    Path sibling = dir.resolve("sibling.txt");
    Path siblingAbsolute = checkoutDir.resolve(sibling);
    Files.writeString(siblingAbsolute, "I will also be deleted");
    Path willRemain = dir.resolve("remain/remain.txt");
    Path willRemainAbsolute = checkoutDir.resolve(willRemain);
    Files.createDirectories(willRemainAbsolute.getParent());
    Files.writeString(willRemainAbsolute, "I will not be deleted");
    CheckoutPath underTest = new CheckoutPath(toDelete.getParent(), checkoutDir);

    underTest.rmDir(true);

    assertThat(Files.exists(toDeleteAbsolute)).isFalse();
    assertThat(Files.exists(siblingAbsolute)).isFalse();
    assertThat(Files.exists(willRemainAbsolute)).isFalse();
  }

  @Test
  public void testCreateWithCheckoutDirBlocksEscapePaths() throws IOException {
    Path testRoot = Files.createTempDirectory("escapeTest");

    // create a file that shouldn't be accessed
    Path privilegedFile = testRoot.resolve("secret.txt");
    Files.writeString(privilegedFile, "treasure");

    // create a checkout directory
    Path checkoutDirPath = testRoot.resolve("checkout");
    Files.createDirectories(checkoutDirPath);

    // create a path from the checkout directory to the protected file
    Path escapePath = Path.of("../secret.txt");

    assertThrows(
        EvalException.class, () -> CheckoutPath.createWithCheckoutDir(escapePath, checkoutDirPath));
  }
}
