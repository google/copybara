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
import com.google.copybara.GeneralOptions;
import com.google.copybara.Option;

/**
 * Arguments for git.mirror migration.
 */
@Parameters(separators = "=")
public class GitMirrorOptions implements Option {

  public GitMirrorOptions() {
  }

  // TODO(malcon): Remove once internal references removed.
  @Deprecated
  public GitMirrorOptions(GeneralOptions ignore1, GitOptions ignore2) {
  }

  // TODO(malcon): Deprecate and use --force instead
  @Parameter(names = "--git-mirror-force",
      description = "Force push even if it is not fast-forward")
  boolean forcePush = false;
}
