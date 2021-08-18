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

import static com.google.copybara.util.FileUtil.resolveDirInCache;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.base.Preconditions;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Option;
import com.google.copybara.exception.RepoException;
import com.google.copybara.jcommander.GreaterThanZeroValidator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * Common arguments for {@link GitDestination}, {@link GitOrigin}, and other Git components.
 */
@Parameters(separators = "=")
public class GitOptions implements Option {

  private final GeneralOptions generalOptions;
  private String partialCacheFilePrefix;

  @Nullable
  public String getCredentialHelperStorePath() {
    return credentialHelperStorePath;
  }

  @Parameter(names = "--git-credential-helper-store-file",
      description = "Credentials store file to be used. See "
          + "https://git-scm.com/docs/git-credential-store")
  public String credentialHelperStorePath;

  @Parameter(names = "--nogit-credential-helper-store",
      description = "Disable using credentials store. See "
          + "https://git-scm.com/docs/git-credential-store")
  boolean noCredentialHelperStore = false;

  @Parameter(
      names = "--nogit-prompt",
      description =
          "Disable username/password prompt and fail if no credentials are found. This "
              + "flag sets the environment variable GIT_TERMINAL_PROMPT which is intended for"
              + " automated jobs running Git"
              + " https://git-scm.com/docs/git/2.3.0#git-emGITTERMINALPROMPTem")
  boolean noGitPrompt = false;

  @Parameter(names = "--git-visit-changes-page-size",
      description = "Size of the git log page used for visiting changes.", hidden = true,
      validateWith = GreaterThanZeroValidator.class)
  int visitChangePageSize = 200;

  @Parameter(names = "--git-tag-overwrite",
      description = "If set, copybara will force update existing git tag")
  boolean gitTagOverwrite = false;

  @Parameter(names = "--experiment-checkout-affected-files",
      description = "If set, copybara will only checkout affected files at git origin. "
          + "Note that this is experimental.")
  boolean experimentCheckoutAffectedFiles = false;

  @Parameter(names = "--git-no-verify", description =
      "Pass the '--no-verify' option to git pushes and commits to disable git commit hooks.")
  public boolean gitNoVerify = false;

  public GitOptions(GeneralOptions generalOptions) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
  }

  private GitOptions(GeneralOptions generalOptions, @Nullable String partialCacheFilePrefix) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.partialCacheFilePrefix = partialCacheFilePrefix;
  }

  public Path getRepoStorage() throws IOException {
    return generalOptions.getDirFactory().getCacheDir("git_repos");
  }

  public GitRepository cachedBareRepoForUrl(String url) throws RepoException {
    Preconditions.checkNotNull(url);
    try {
      return createBareRepo(generalOptions, resolveDirInCache(url, getRepoStorage()));
    } catch (IOException e) {
      throw new RepoException("Cannot create a cached repo for " + url, e);
    }
  }

  /**
   * Rewrite url for subodule fetch
   */
  public String rewriteSubmoduleUrl(String url) throws RepoException {
    return url;
  }

  /** Returns a {@link GitEnvironment} configured for the given options. */
  protected GitEnvironment getGitEnvironment(Map<String, String> env) {
    return new GitEnvironment(env, noGitPrompt);
  }

  /**
   * Create a new initialized repository in the location.
   *
   * <p>Can be overwritten to create custom GitRepository objects.
   */
  public GitRepository createBareRepo(GeneralOptions generalOptions, Path path)
      throws RepoException {
    GitRepository repo =
        GitRepository.newBareRepo(
            path, getGitEnvironment(generalOptions.getEnvironment()),
            generalOptions.isVerbose(), generalOptions.fetchTimeout, gitNoVerify);
    return initRepo(repo);
  }

  protected GitRepository initRepo(GitRepository repo) throws RepoException {
    repo.init();
    if (noCredentialHelperStore) {
      return repo;
    }
    String storePath = getCredentialHelperStorePath();
    String path = storePath == null ? "" : " --file=" + storePath;
    repo.withCredentialHelper("store" + path);
    repo.setLocalConfigField("fetch", "prune", "false");
    return repo;
  }

  public String getPartialCacheFilePrefix() {
    return partialCacheFilePrefix;
  }

  public GitOptions setPartialCacheFilePrefix(String partialCacheFilePrefix) {
    return new GitOptions(generalOptions, partialCacheFilePrefix);
  }

}
