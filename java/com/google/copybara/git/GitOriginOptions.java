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

package com.google.copybara.git;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Strings;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Option;
import com.google.copybara.exception.RepoException;
import com.google.copybara.util.OriginUtil.CheckoutHook;
import java.nio.file.Path;

/**
 * Options for {@link GitOrigin}.
 */
@Parameters(separators = "=")
public class GitOriginOptions implements Option {

  @Parameter(names = "--git-origin-checkout-hook",
      description = "A command to be executed when a checkout happens for a git origin."
          + " DON'T USE IT. The only intention is to run tools that gather dependencies"
          + " after the checkout.", hidden = true)
  String originCheckoutHook = null;

  @Parameter(names = "--git-origin-rebase-ref",
      description = "When importing a change from a Git origin ref, it will be rebased to this ref,"
          + " if set. A common use case: importing a Github PR, rebase it to the main branch "
          + "(usually 'master'). Note that, if the repo uses submodules, they won't be rebased.")
  String originRebaseRef = null;

  @Parameter(names = "--git-origin-describe-default", description = "The default for git describe"
      + "in git.*origin.", arity = 1, hidden = true)
  boolean gitDescribeDefault = true;

  @Parameter(names = "--nogit-origin-version-selector", description = "Disable the version selector"
      + " for the migration. Only useful for forcing a migration to the passed version in the CLI")
  boolean noGitVersionSelector = false;

  public boolean useGitVersionSelector() {
    return !noGitVersionSelector;
  }

  void maybeRunCheckoutHook(Path checkoutDir, GeneralOptions generalOptions) throws RepoException {
    if (Strings.isNullOrEmpty(originCheckoutHook)) {
      return;
    }
    CheckoutHook checkoutHook = new CheckoutHook(originCheckoutHook, generalOptions, "git.origin");
    checkoutHook.run(checkoutDir);
  }
}
