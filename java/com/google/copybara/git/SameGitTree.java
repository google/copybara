/*
 * Copyright (C) 2020 Google Inc.
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

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.GeneralOptions;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.profiler.Profiler.ProfilerTask;

/** A  class comparing git tree of a repo's head sha1 with any sha1 */
public class SameGitTree {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GitRepository  repo;
  private final String repoUrl;
  private final GeneralOptions generalOptions;
  private final boolean partialFetch;

  public SameGitTree(GitRepository repo, String repoUrl, GeneralOptions generalOptions,
      boolean partialFetch) {
    this.repo = repo;
    this.repoUrl = repoUrl;
    this.generalOptions = generalOptions;
    this.partialFetch = partialFetch;
  }

  private String saveOldHead() throws RepoException, CannotResolveRevisionException {
    GitRevision gitRevision = repo.getHeadRef();
    return gitRevision.contextReference() != null
        ? gitRevision.contextReference()
        : gitRevision.getSha1();
  }

  /** Compare git tree of repo's head with the parameter sha1
   *
   * It will save the current head at the repo, and fetch the sha1.
   * Then compare the git three of them.
   *
   * In the end, regardless of the checking status, the repo will be force set to previous head.
   */
  public boolean hasSameTree(String sha1) throws RepoException, CannotResolveRevisionException {
    String oldHead = saveOldHead();
    try (ProfilerTask ignore2 = generalOptions.profiler().start("fetch_remote_sha1")) {
      repo.fetch(repoUrl, /*prune=*/ false, /*force=*/ true,
          ImmutableList.of(sha1), partialFetch);
      return repo.hasSameTree(sha1);
    } catch (RepoException | ValidationException e) {
      logger.atWarning().withCause(e).log(
          "Cannot compare git tree of head %s with sha1 %s.", oldHead, sha1);
      generalOptions.console().warnFmt(
          "Cannot compare git tree of head %s with sha1 %s.", oldHead, sha1);
    } finally {
      repo.forceCheckout(oldHead);
    }
    return false;
  }

}
