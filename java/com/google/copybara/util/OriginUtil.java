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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.GeneralOptions;
import com.google.copybara.exception.RepoException;
import com.google.copybara.shell.Command;
import com.google.copybara.shell.CommandException;
import java.nio.file.Path;

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

  /**
   * A {@link CheckoutHook} executes a script on a checkout directory.
   */
  public static class CheckoutHook {

    private final String checkoutHook;
    private final GeneralOptions generalOptions;
    private final String originType;

    public CheckoutHook(String checkoutHook, GeneralOptions generalOptions, String originType) {
      this.checkoutHook = Preconditions.checkNotNull(checkoutHook);
      this.generalOptions = Preconditions.checkNotNull(generalOptions);
      this.originType = Preconditions.checkNotNull(originType);
    }

    public void run(Path checkoutDir) throws RepoException {
      try {
        Command cmd =
            new Command(
                new String[] {checkoutHook}, generalOptions.getEnvironment(), checkoutDir.toFile());
        CommandOutputWithStatus result = new CommandRunner(cmd)
            .withVerbose(generalOptions.isVerbose())
            .execute();
        logLines(generalOptions.console(), getPrefix("Stdout"), result.getStdout());
        logLines(generalOptions.console(), getPrefix("Stderr"), result.getStderr());
      } catch (BadExitStatusWithOutputException e) {
        logLines(generalOptions.console(), getPrefix("Stdout"), e.getOutput().getStdout());
        logLines(generalOptions.console(), getPrefix("Sderr"), e.getOutput().getStderr());
        throw new RepoException("Error executing the checkout hook: " + checkoutHook, e);
      } catch (CommandException e) {
        throw new RepoException("Error executing the checkout hook: " + checkoutHook, e);
      }
    }

    private String getPrefix(String channel) {
      return String.format("%s hook (%s): ", channel, originType);
    }
  }
}
