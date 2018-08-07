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

package com.google.copybara.util;

import static com.google.copybara.testing.FileSubjects.assertThatPath;
import static com.google.copybara.util.OriginUtil.runCheckoutHook;
import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OriginUtilTest {

  private Path workDir;
  private OptionsBuilder options;

  @Before
  public void setup() throws Exception {
    workDir = Files.createTempDirectory("workDir");
    options = new OptionsBuilder();
  }

  private void verifyHookThrowsValidationException(String checkoutHook) throws RepoException {
    try {
      runCheckoutHook(workDir, checkoutHook, options.general.getEnvironment(),
          options.general.isVerbose(), options.general.console(), "some.origin");
      fail("Should have thrown exception");
    } catch (ValidationException expected) {
      assertThat(expected.getMessage()).contains("Invalid checkout hook path");
    }
  }

  @Test
  public void testInvalidCheckoutHookPath() throws Exception {
    String checkoutHook = "../";
    verifyHookThrowsValidationException(checkoutHook);

    checkoutHook = "./";
    verifyHookThrowsValidationException(checkoutHook);

    checkoutHook = "/hook.sh";
    verifyHookThrowsValidationException(checkoutHook);
  }

  @Test
  public void testCheckoutHookWithExitError() throws Exception {
    Path checkoutHook = Files.createTempFile(workDir, "script", "script");
    Files.write(checkoutHook, "exit 42".getBytes(UTF_8));

    Files.setPosixFilePermissions(checkoutHook, ImmutableSet.<PosixFilePermission>builder()
        .addAll(Files.getPosixFilePermissions(checkoutHook))
        .add(PosixFilePermission.OWNER_EXECUTE).build());

    try {
      runCheckoutHook(workDir, checkoutHook.toFile().getName(), options.general.getEnvironment(),
          options.general.isVerbose(), options.general.console(), "some.origin");
      fail("Should have thrown exception");
    } catch (RepoException expected) {
      assertThat(expected.getMessage()).contains("Error executing the checkout hook");
      assertEquals("Process exited with status 42",
          Throwables.getRootCause(expected).getMessage());
    }
  }

  @Test
  public void testCheckoutHook() throws Exception {
    Path checkoutHook = Files.createTempFile(workDir, "script", "script");
    Files.write(checkoutHook, "touch hook.txt".getBytes(UTF_8));

    Files.setPosixFilePermissions(checkoutHook, ImmutableSet.<PosixFilePermission>builder()
        .addAll(Files.getPosixFilePermissions(checkoutHook))
        .add(PosixFilePermission.OWNER_EXECUTE).build());

    runCheckoutHook(workDir, checkoutHook.toFile().getName(), options.general.getEnvironment(),
        options.general.isVerbose(), options.general.console(), "some.origin");

    assertThatPath(workDir).containsFile("hook.txt", "");
  }

}
