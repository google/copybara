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

package com.google.copybara.testing.git;

import static com.google.common.truth.Truth.assertWithMessage;
import static org.junit.Assert.fail;

import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.common.base.Preconditions;
import com.google.copybara.CannotResolveRevisionException;
import com.google.copybara.GeneralOptions;
import com.google.copybara.RepoException;
import com.google.copybara.authoring.Author;
import com.google.copybara.git.FetchResult;
import com.google.copybara.git.GitOptions;
import com.google.copybara.git.GitRepository;
import com.google.copybara.testing.OptionsBuilder.GithubMockHttpTransport;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Common utilities for creating and working with git repos in test
 */
public class GitTestUtil {
  static final Author DEFAULT_AUTHOR = new Author("Authorbara", "author@example.com");
  static final Author COMMITER = new Author("Commit Bara", "commitbara@example.com");
  public static final GithubMockHttpTransport NO_GITHUB_API_CALLS = new GithubMockHttpTransport() {
    @Override
    protected byte[] getContent(String method, String url, MockLowLevelHttpRequest request)
        throws IOException {
      fail();
      throw new IllegalStateException();
    }
  };

  /**
   * Returns an environment that contains the System environment and a set of variables
   * needed so that test don't crash in environments where the author is not set
   */
  public static Map<String, String> getGitEnv() {
    HashMap<String, String> values = new HashMap<>(System.getenv());
    values.put("GIT_AUTHOR_NAME", DEFAULT_AUTHOR.getName());
    values.put("GIT_AUTHOR_EMAIL", DEFAULT_AUTHOR.getEmail());
    values.put("GIT_COMMITTER_NAME", COMMITER.getName());
    values.put("GIT_COMMITTER_EMAIL", COMMITER.getEmail());
    return values;
  }

  public static class TestGitOptions extends GitOptions {

    private final Path localHub;

    public TestGitOptions(Path localHub, Supplier<GeneralOptions> generalOptionsSupplier) {
      super(generalOptionsSupplier);
      this.localHub = Preconditions.checkNotNull(localHub);
    }

    @Override
    protected GitRepository createBareRepo(GeneralOptions generalOptions, Path path)
        throws RepoException {
      return initRepo(new RewriteUrlGitRepository(path, null, generalOptions, localHub));
    }
  }

  private static class RewriteUrlGitRepository extends GitRepository {

    private final GeneralOptions generalOptions;
    private final Path localHub;

    RewriteUrlGitRepository(Path gitDir, Path workTree, GeneralOptions generalOptions,
        Path localHub) {
      super(gitDir, workTree, generalOptions.isVerbose(), generalOptions.getEnvironment());
      this.generalOptions = generalOptions;
      this.localHub = localHub;
    }

    @Override
    protected FetchResult fetch(String url, boolean prune, boolean force,
        Iterable<String> refspecs) throws RepoException, CannotResolveRevisionException {
      return super.fetch(mapUrl(url), prune, force, refspecs);
    }

    @Override
    protected String runPush(PushCmd pushCmd) throws RepoException {
      if (pushCmd.getUrl() != null) {
        pushCmd = pushCmd.withRefspecs(mapUrl(pushCmd.getUrl()),
            pushCmd.getRefspecs());
      }
      return super.runPush(pushCmd);
    }

    @Override
    public GitRepository withWorkTree(Path newWorkTree) {
      return new RewriteUrlGitRepository(getGitDir(), newWorkTree, generalOptions, localHub);
    }

    private String mapUrl(String url) {
      if (url.startsWith("file://")) {
        return url;
      }
      Path repo = localHub.resolve(url.replaceAll(".*github.com/", ""));
      assertWithMessage(repo.toString()).that(Files.isDirectory(repo)).isTrue();
      return "file:///" + repo.toString();
    }
  }
}
