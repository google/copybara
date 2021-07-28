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

import static com.google.copybara.util.FileUtil.resolveDirInCache;

import com.beust.jcommander.Parameters;
import com.google.common.base.Preconditions;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Option;
import com.google.copybara.exception.RepoException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Common arguments for Hg components
 */
@Parameters(separators = "=")
public class HgOptions implements Option {

  private static final String HGDIR_PATH = ".hg";

  private final GeneralOptions generalOptions;

  /**
   * Depth of hg changes to visit at a time. For example, if depth is set to 2, visit the start
   * change and at most 2 of its next descendants if they exist.
   */
  int visitChangeDepth = 200;

  public HgOptions(GeneralOptions generalOptions) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
  }

  public final HgRepository cachedBareRepoForUrl(String url)
      throws RepoException {
    Preconditions.checkNotNull(url);
    try {
      return createBareRepo(url, getRepoStorage());
    } catch (IOException e) {
      throw new RepoException("Cannot create a cached repo for " + url, e);
    }
  }

  /**
   * Returns an initialized repository in the {@code path} location. If an initialized
   * repository already exists in the location, returns that repository.
   */
  private HgRepository createBareRepo(String url, Path path)
      throws RepoException {
    Path repoPath = resolveDirInCache(url, path);
    Path hgDir = repoPath.resolve(HGDIR_PATH);

    HgRepository repo =
        new HgRepository(hgDir, generalOptions.isVerbose(), generalOptions.fetchTimeout);
    if (Files.notExists(hgDir)) {
      repo.init();
    }

    repo.cleanUpdate("null");
    return repo;
  }

  private Path getRepoStorage() throws IOException {
    return generalOptions.getDirFactory().getCacheDir("hg_repos");
  }
}
