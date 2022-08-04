/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.git.version;

import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitRepository;
import com.google.copybara.git.Refspec;
import com.google.copybara.version.VersionList;

/**
 *  A {@link VersionList} that uses a git ls-remote to list versions from a remote Git repository.
 */
public class RefspecVersionList implements VersionList {

  private final GitRepository repo;
  private final ImmutableCollection<Refspec> refspecs;
  private final String url;

  public RefspecVersionList(GitRepository repo, ImmutableCollection<Refspec> refspecs, String url) {
    this.repo = repo;
    this.refspecs = refspecs;
    this.url = url;
  }

  @Override
  public ImmutableSet<String> list() throws ValidationException, RepoException {
    return ImmutableSet.copyOf(
        repo.lsRemote(
            url,
            ImmutableList.copyOf(
                refspecs.stream().map(Refspec::getOrigin).collect(toImmutableSet()))).keySet());
  }

  /** A {@link RefspecVersionList} for listing git tags */
  public static class TagVersionList extends RefspecVersionList {

    public TagVersionList(GitRepository repo, String url) {
      super(repo, tagBranchRefspec(repo, "tags"), url);
    }

    @Override
    public ImmutableSet<String> list() throws ValidationException, RepoException {
      return super.list().stream()
          .map(s -> s.substring("refs/tags/".length()))
          .collect(toImmutableSet());
    }
  }

  /** A {@link RefspecVersionList} for listing git branches */
  public static class BranchVersionList extends RefspecVersionList {

    public BranchVersionList(GitRepository repo, String url) {
      super(repo, tagBranchRefspec(repo, "heads"), url);
    }

    @Override
    public ImmutableSet<String> list() throws ValidationException, RepoException {
      return super.list().stream()
          .map(s -> s.substring("refs/heads/".length()))
          .collect(toImmutableSet());
    }
  }

  private static ImmutableList<Refspec> tagBranchRefspec(GitRepository repo, String type) {
    try {
      return ImmutableList.of(repo.createRefSpec("refs/" + type + "/*"));
    } catch (ValidationException e) {
      throw new IllegalStateException("Unexpected error constructing"
          + " refspec from constant. This shouldn't happen. Fill a"
          + " Copybara bug", e);
    }
  }
}
