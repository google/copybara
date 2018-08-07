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

import static com.google.copybara.util.console.Consoles.logLines;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import com.google.copybara.util.console.Console;
import java.nio.file.Path;
import java.util.Map;

/**
 * Utility methods for managing origins
 */
public class OriginUtil {

  private OriginUtil() {}

  /**
   * Checks if the given {@code changedFiles} are or are descendants of the {@code roots}.
   */
  public static boolean affectsRoots(ImmutableSet<String> roots,
      ImmutableCollection<String> changedFiles) {
    if (changedFiles == null || Glob.isEmptyRoot(roots)) {
      return true;
    }
    // This is O(changes * files * roots) in the worse case. roots shouldn't be big and
    // files shouldn't be big for 99% of the changes.
    for (String file : changedFiles) {
      for (String root : roots) {
        if (file.equals(root) || file.startsWith(root + "/")) {
          return true;
        }
      }
    }
    return false;
  }

  public static void runCheckoutHook(Path workDir, String checkoutHook,
      Map<String, String> environment, boolean isVerbose, Console console, String originType)
      throws RepoException, ValidationException {
    try {
      checkoutHook = FileUtil.checkNormalizedRelative(checkoutHook);
    } catch (IllegalArgumentException e) {
      throw new ValidationException(
          String.format("Invalid checkout hook path: %s", e.getMessage()));
    }
    try {
      Command cmd = new Command(new String[]{
          workDir.resolve(checkoutHook).toAbsolutePath().toString()}, environment,
          workDir.toFile());
      CommandOutputWithStatus result = new CommandRunner(cmd)
          .withVerbose(isVerbose)
          .execute();
      logLines(
          console, String.format("%s hook (Stdout): ", originType), result.getStdout());
      logLines(
          console, String.format("%s hook (Stderr): ", originType), result.getStderr());
    } catch (BadExitStatusWithOutputException e) {
      logLines(console,
          String.format("%s hook (Stdout): ", originType), e.getOutput().getStdout());
      logLines(console,
          String.format("%s hook (Stderr): ", originType), e.getOutput().getStderr());
      throw new RepoException(
          "Error executing the checkout hook: " + checkoutHook, e);
    } catch (CommandException e) {
      throw new RepoException(
          "Error executing the checkout hook: " + checkoutHook, e);
    }
  }
}
