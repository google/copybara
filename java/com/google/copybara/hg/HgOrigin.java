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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.io.MoreFiles;
import com.google.copybara.Change;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * A class for manipulating Hg repositories
 */
public class HgOrigin implements Origin<HgRevision> {

  private final String repoUrl;
  private final String branch;
  private final HgOptions hgOptions;
  private final GeneralOptions generalOptions;

  HgOrigin(GeneralOptions generalOptions, HgOptions hgOptions, String repoUrl, String branch) {
    this.generalOptions = generalOptions;
    this.hgOptions = hgOptions;
    this.repoUrl = CharMatcher.is('/').trimTrailingFrom(checkNotNull(repoUrl));
    this.branch = Preconditions.checkNotNull(branch);
  }

  @VisibleForTesting
  public HgRepository getRepository() throws RepoException, ValidationException {
    return hgOptions.cachedBareRepoForUrl(repoUrl);
  }

  /**
   * Resolves a hg changeset reference to a revision. Pulls revision into repo.
   */
  @Override
  public HgRevision resolve(String reference) throws RepoException, ValidationException {
    HgRepository repo = getRepository();
    repo.pullFromRef(repoUrl, reference);
    return repo.identify(reference);
  }

  static class ReaderImpl implements Reader<HgRevision> {

    private final String repoUrl;
    private final HgOptions hgOptions;

    ReaderImpl(String repoUrl, HgOptions hgOptions) {
      this.repoUrl = checkNotNull(repoUrl);
      this.hgOptions = hgOptions;
    }

    protected HgRepository getRepository() throws RepoException, ValidationException {
      return hgOptions.cachedBareRepoForUrl(repoUrl);
    }

    @Override
    public void checkout(HgRevision revision, Path workDir)
        throws RepoException, ValidationException {
      HgRepository repo = getRepository();
      String revId = revision.getGlobalId();
      repo.pullFromRef(repoUrl, revId);
      repo.cleanUpdate(revId);
      try {
        repo.archive(workDir.toString()); // update the working directory
      }
      catch (RepoException e) {
        if (e.getMessage().contains("abort: no files match the archive pattern")) {
          // if checked out empty working directory, then empty the archive
          try {
            MoreFiles.deleteDirectoryContents(workDir);
          }
          catch (IOException io) {
            throw new RepoException(
                String.format("Could not update working directory: %s", io.getMessage()));
          }
        }
        else {
          throw new RepoException(e.getMessage());
        }
      }
    }

    @Override
    public ChangesResponse<HgRevision> changes(@Nullable HgRevision fromRef, HgRevision toRef) {
      throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Change<HgRevision> change(HgRevision ref) {
      throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void visitChanges(HgRevision start, ChangesVisitor visitor) {
      throw new UnsupportedOperationException("Not implemented yet");
    }
  }

  @Override
  public Reader<HgRevision> newReader(Glob originFiles, Authoring authoring) {
    return new ReaderImpl(repoUrl, hgOptions);
  }

  @Override
  public String getLabelName() {
    return String.format("HgOrigin{url = %s}", repoUrl);
  }

  /**
   * Builds a new {@link HgOrigin}
   */
  static HgOrigin newHgOrigin(Options options, String url, String branch) {
    return new HgOrigin(options.get(GeneralOptions.class), options.get(HgOptions.class), url,
        branch);
  }
}
