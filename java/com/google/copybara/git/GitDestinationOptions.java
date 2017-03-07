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
import com.google.copybara.Option;
import javax.annotation.Nullable;

/**
 * Arguments for {@link GitDestination}, {@link GitOrigin}, and other Git components.
 */
@Parameters(separators = "=")
public final class GitDestinationOptions implements Option {

  public static final String GIT_COMMITTER_NAME_FLAG = "--git-committer-name";
  public static final String GIT_COMMITTER_EMAIL_FLAG = "--git-committer-email";

  @Parameter(names = GIT_COMMITTER_NAME_FLAG,
      description = "If set, overrides the committer name for the generated commits in git"
          + " destination.")
  String committerName = "";

  @Parameter(names = GIT_COMMITTER_EMAIL_FLAG,
      description = "If set, overrides the committer e-mail for the generated commits in git"
          + " destination.")
  String committerEmail = "";

  @Parameter(names = "--git-destination-url",
      description = "If set, overrides the git destination URL.")
  String url = null;

  @Nullable
  @Parameter(names = "--git-destination-fetch",
      description = "If set, overrides the git destination fetch reference.")
  String fetch = null;

  @Nullable
  @Parameter(names = "--git-destination-push",
      description = "If set, overrides the git destination push reference.")
  String push = null;
}
