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
import java.util.Collection;
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

  public static void createFakeGerritNodeDbMeta(GitRepository repo, int change, String changeId)
      throws RepoException, IOException, CannotResolveRevisionException {

    // Save head for restoring it later
    String head = repo.parseRef("HEAD");
    // Start a branch without history
    repo.simpleCommand("checkout", "--orphan", "meta_branch_" + change);

    Files.write(repo.getWorkTree().resolve("not_used.txt"), "".getBytes());
    repo.add().files("not_used.txt").run();

    repo.simpleCommand("commit", "-m", ""
        + "Create change\n"
        + "\n"
        + "Uploaded patch set 1.\n"
        + "\n"
        + "Patch-set: 1\n"
        + "Change-id: " + changeId + "\n"
        + "Subject: GerritDestination: Sample review message\n"
        + "Branch: refs/heads/master\n"
        + "Commit: 7d15cf91ee118e68b9784a7e7e2bba7a30ad8e59\n"
        + "Groups: 7d15cf91ee118e68b9784a7e7e2bba7a30ad8e59");
    Files.write(repo.getWorkTree().resolve("not_used.txt"), "a".getBytes());
    repo.add().files("not_used.txt").run();

    repo.simpleCommand("commit", "-m", ""
        + "Create patch set 2\n"
        + "\n"
        + "Uploaded patch set 2.\n"
        + "\n"
        + "Patch-set: 2\n"
        + "Subject: GerritDestination: Sample review message\n"
        + "Commit: 2223378c91bb1c403c404d792d95b91dbc0472d9\n"
        + "Groups: 2223378c91bb1c403c404d792d95b91dbc0472d9");

    // Create the meta reference

    String metaRef = String.format("refs/changes/%02d/%d/meta", change % 100, change);
    repo.simpleCommand("update-ref", metaRef, repo.parseRef("meta_branch_" + change));

    // Restore head
    repo.simpleCommand("update-ref", "HEAD", head);
  }

  public static class Validator {

    public void validateFetch(String url, boolean prune, boolean force,
        Iterable<String> refspecs) {
      // Intended to be empty
    }
  }

  public static class TestGitOptions extends GitOptions {

    private final Path httpsRepos;
    private final Validator validator;

    public TestGitOptions(Path httpsRepos, Supplier<GeneralOptions> generalOptionsSupplier) {
      this(httpsRepos, generalOptionsSupplier, new Validator());
    }

    public TestGitOptions(Path httpsRepos, Supplier<GeneralOptions> generalOptionsSupplier,
        Validator validator) {
      super(generalOptionsSupplier);
      this.httpsRepos = Preconditions.checkNotNull(httpsRepos);
      this.validator = Preconditions.checkNotNull(validator);
    }

    @Override
    protected GitRepository createBareRepo(GeneralOptions generalOptions, Path path)
        throws RepoException {
      return initRepo(new RewriteUrlGitRepository(path, null, generalOptions, httpsRepos,
          validator));
    }
  }

  private static class RewriteUrlGitRepository extends GitRepository {

    private final GeneralOptions generalOptions;
    private final Path httpsRepos;
    private Validator validator;

    RewriteUrlGitRepository(Path gitDir, Path workTree, GeneralOptions generalOptions,
        Path httpsRepos, Validator validator) {
      super(gitDir, workTree, generalOptions.isVerbose(), generalOptions.getEnvironment());
      this.generalOptions = generalOptions;
      this.httpsRepos = httpsRepos;
      this.validator = validator;
    }

    @Override
    public FetchResult fetch(String url, boolean prune, boolean force,
        Iterable<String> refspecs) throws RepoException, CannotResolveRevisionException {
      validator.validateFetch(url, prune, force, refspecs);
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
    public Map<String, String> lsRemote(String url, Collection<String> refs) throws RepoException {
      return super.lsRemote(mapUrl(url), refs);
    }

    @Override
    public GitRepository withWorkTree(Path newWorkTree) {
      return new RewriteUrlGitRepository(getGitDir(), newWorkTree, generalOptions, httpsRepos,
          validator);
    }

    private String mapUrl(String url) {
      if (!url.startsWith("https://")) {
        return url;
      }
      Path repo = httpsRepos.resolve(url.replaceAll("https://", ""));
      assertWithMessage(repo.toString()).that(Files.isDirectory(repo)).isTrue();
      return "file:///" + repo.toString();
    }
  }
}
