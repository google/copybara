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

package com.google.copybara.hg;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Strings;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Option;
import com.google.copybara.exception.RepoException;
import com.google.copybara.util.OriginUtil.CheckoutHook;
import java.nio.file.Path;

/**
 * Options for {@link HgOrigin}.
 */
@Parameters(separators = "=")
public class HgOriginOptions implements Option {

  @Parameter(names = "--hg-origin-checkout-hook",
      description = "A command to be executed when a checkout happens for a hg origin. Only"
        + " intended to run tools that update the repository to latest sources",
      hidden = true)
  String originCheckoutHook = null;

  void maybeRunCheckoutHook(Path checkoutDir, GeneralOptions generalOptions) throws RepoException {
    if (Strings.isNullOrEmpty(originCheckoutHook)) {
      return;
    }
    CheckoutHook checkoutHook = new CheckoutHook(originCheckoutHook, generalOptions, "hg.origin");
    checkoutHook.run(checkoutDir);
  }
}
