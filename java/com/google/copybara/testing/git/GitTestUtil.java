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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.Json;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.copybara.GeneralOptions;
import com.google.copybara.authoring.Author;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.FetchResult;
import com.google.copybara.git.GerritOptions;
import com.google.copybara.git.GitEnvironment;
import com.google.copybara.git.GitHubOptions;
import com.google.copybara.git.GitOptions;
import com.google.copybara.git.GitRepository;
import com.google.copybara.git.GitRepository.PushCmd;
import com.google.copybara.git.InvalidRefspecException;
import com.google.copybara.git.Refspec;
import com.google.copybara.testing.OptionsBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import org.mockito.stubbing.OngoingStubbing;

/** Common utilities for creating and working with git repos in test */
public class GitTestUtil {

  private static final Author DEFAULT_AUTHOR = new Author("Authorbara", "author@example.com");
  private static final Author COMMITER = new Author("Commit Bara", "commitbara@example.com");
  public static final MockRequestAssertion ALWAYS_TRUE =
      new MockRequestAssertion("Always true", any -> true);

  protected MockHttpTransport mockHttpTransport = null;

  private final OptionsBuilder optionsBuilder;

  public GitTestUtil(OptionsBuilder optionsBuilder) {
    this.optionsBuilder = checkNotNull(optionsBuilder);
  }

  public static LowLevelHttpRequest mockResponse(String responseContent) {
    return mockResponseWithStatus(responseContent, 200, ALWAYS_TRUE);
  }

  public static LowLevelHttpRequest mockResponseAndValidateRequest(
      String responseContent, String predicateText, Predicate<String> requestValidator) {
    return mockResponseWithStatus(responseContent, 200,
        new MockRequestAssertion(predicateText, requestValidator));
  }

  public static LowLevelHttpRequest mockResponseAndValidateRequest(
      String responseContent, MockRequestAssertion assertion) {
    return mockResponseWithStatus(responseContent, 200, assertion);
  }

  public static LowLevelHttpRequest mockResponseWithStatus(String responseContent, int status) {
    return mockResponseWithStatus(responseContent, status, ALWAYS_TRUE);
  }

  public static LowLevelHttpRequest mockResponseWithStatus(
      String responseContent, int status, MockRequestAssertion requestValidator) {
    return new MockLowLevelHttpRequest() {
      @Override
      public LowLevelHttpResponse execute() throws IOException {
        assertWithMessage(
                String.format(
                    "Request <%s> did not match predicate: '%s'",
                    this.getContentAsString(), requestValidator))
            .that(requestValidator.test(this.getContentAsString()))
            .isTrue();
        // Responses contain a IntputStream for content. Cannot be reused between for two
        // consecutive calls. We create new ones per call here.
        return new MockLowLevelHttpResponse()
            .setContentType(Json.MEDIA_TYPE)
            .setContent(responseContent)
            .setStatusCode(status);
      }
    };
  }

  public static LowLevelHttpRequest mockNotFoundResponse(String responseContent) {
    return mockResponseWithStatus(responseContent, 404);
  }

  public static LowLevelHttpRequest mockGitHubNotFound() {
    return mockResponseWithStatus(
        "{\n"
            + "\"message\" : \"Not Found\",\n"
            + "\"documentation_url\" : \"https://developer.github.com/v3\"\n"
            + "}", 404, ALWAYS_TRUE);
  }

  public static LowLevelHttpRequest mockGitHubUnauthorized() {
    return mockResponseWithStatus(
        "{\n"
            + "\"message\" : \"Not Found\",\n"
            + "\"documentation_url\" : \"https://developer.github.com/v3\"\n"
            + "}", 401, ALWAYS_TRUE);
  }

  public static LowLevelHttpRequest mockGitHubUnprocessable() {
    return mockResponseWithStatus(
        "{\n"
            + "\"message\" : \"Unauthorized\",\n"
            + "\"documentation_url\" : \"https://developer.github.com/v3\"\n"
            + "}", 422, ALWAYS_TRUE);
  }

  public void mockRemoteGitRepos() throws IOException {
    mockRemoteGitRepos(new Validator());
  }

  public void mockRemoteGitRepos(Validator validator) throws IOException {
    try {
      mockRemoteGitRepos(
          validator,
          // if a credentials repo was set, use it.
          /* credentialsRepo= */ (optionsBuilder.git.getCredentialHelperStorePath() != null)
              ? optionsBuilder.git.cachedBareRepoForUrl(
                  optionsBuilder.git.getCredentialHelperStorePath())
              : null);
    } catch (RepoException e) {
      throw new IOException(e);
    }
  }

  public void mockRemoteGitRepos(Validator validator, GitRepository credentialsRepo)
      throws IOException {
    assertWithMessage("mockRemoteGitRepos() method called more than once in this test")
        .that(mockHttpTransport)
        .isNull();
    mockHttpTransport =
        mock(
            MockHttpTransport.class,
            withSettings()
                .defaultAnswer(
                    invocation -> {
                      String method = (String) invocation.getArguments()[0];
                      String url = (String) invocation.getArguments()[1];
                      return mockResponseWithStatus(
                          "not used",
                          404,
                          new MockRequestAssertion("Always throw", content -> {
                            throw new AssertionError(
                                String.format(
                                    "Cannot find a programmed answer for: %s %s\n%s",
                                    method, url, content));
                          }));
                    }));

    optionsBuilder.git = new GitOptionsForTest(optionsBuilder.general, validator);
    optionsBuilder.github = mockGitHubOptions(credentialsRepo);
    optionsBuilder.gerrit = mockGerritOptions(credentialsRepo);
  }

  protected GerritOptions mockGerritOptions(GitRepository credentialsRepo) {
    return new GerritOptions(optionsBuilder.general, optionsBuilder.git) {
      @Override
      protected HttpTransport getHttpTransport() {
        return mockHttpTransport;
      }

      @Override
      protected GitRepository getCredentialsRepo() throws RepoException {
        return credentialsRepo != null ? credentialsRepo : super.getCredentialsRepo();
      }
    };
  }

  protected GitHubOptions mockGitHubOptions(GitRepository credentialsRepo) {
    return new GitHubOptions(optionsBuilder.general, optionsBuilder.git) {
      @Override
      protected HttpTransport newHttpTransport() {
        return mockHttpTransport;
      }

      @Override
      protected GitRepository getCredentialsRepo() throws RepoException {
        return credentialsRepo != null ? credentialsRepo : super.getCredentialsRepo();
      }
    };
  }

  public GitRepository mockRemoteRepo(String url) throws RepoException {
    // If this cast fails, it means you didn't call mockRemoteGitRepos first.
    return ((GitOptionsForTest) optionsBuilder.git)
        .mockRemoteRepo(url, getGitEnv().getEnvironment());
  }

  public MockHttpTransport httpTransport() {
    return Preconditions.checkNotNull(mockHttpTransport, "Call mockRemoteGitRepos() on setup");
  }

  public OngoingStubbing<LowLevelHttpRequest> mockApi(
      String method, String url, LowLevelHttpRequest request, LowLevelHttpRequest... rest) {
    OngoingStubbing<LowLevelHttpRequest> when;
    try {
      when = when(httpTransport().buildRequest(method, url)).thenReturn(request);
    } catch (IOException e) {
      // Cannot happen as we are not really calling the method.
      throw new AssertionError(e);
    }

    for (LowLevelHttpRequest httpRequest : rest) {
      when = when.thenReturn(httpRequest);
    }
    return when.thenReturn(request);
  }

  /**
   * Returns an environment that contains the System environment and a set of variables needed so
   * that test don't crash in environments where the author is not set
   *
   * <p>TODO(malcon, danielromero): Remove once all tests use GitTestUtil and internal extension.
   */
  public static GitEnvironment getGitEnv() {
    HashMap<String, String> values = new HashMap<>(System.getenv());
    values.put("GIT_AUTHOR_NAME", DEFAULT_AUTHOR.getName());
    values.put("GIT_AUTHOR_EMAIL", DEFAULT_AUTHOR.getEmail());
    values.put("GIT_COMMITTER_NAME", COMMITER.getName());
    values.put("GIT_COMMITTER_EMAIL", COMMITER.getEmail());
    return new GitEnvironment(values);
  }

  public static void createFakeGerritNodeDbMeta(GitRepository repo, int change, String changeId)
      throws RepoException, IOException, CannotResolveRevisionException {

    // Save head for restoring it later
    String head = repo.parseRef("HEAD");
    // Start a branch without history
    repo.simpleCommand("checkout", "--orphan", "meta_branch_" + change);

    Files.write(repo.getWorkTree().resolve("not_used.txt"), "".getBytes(UTF_8));
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
    Files.write(repo.getWorkTree().resolve("not_used.txt"), "a".getBytes(UTF_8));
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

  // fetch and push refs should be complete (refs/heads/master vs master). Our
  // internal implementation requires it.
  public static class CompleteRefValidator extends Validator {

    @Override
    public void validateFetch(String url, boolean prune, boolean force, Iterable<String> refspecs) {
      for (String refspec : refspecs) {
        try {
          assertThat(Refspec.create(getGitEnv(),
              Paths.get("/tmp"), refspec).getOrigin()).startsWith("refs/");
        } catch (InvalidRefspecException e) {
          throw new AssertionError(e);
        }
      }

    }

    @Override
    public void validatePush(PushCmd pushCmd) {
      for (Refspec refspec : pushCmd.getRefspecs()) {
        // Destination push refs should be complete (refs/heads/master vs master). Our
        // internal implementation requires it.
        assertThat(refspec.getDestination()).startsWith("refs/");
      }

    }
  }

  public static class Validator {

    public void validateFetch(String url, boolean prune, boolean force,
        Iterable<String> refspecs) {
      // Intended to be empty
    }

    public void validatePush(PushCmd pushCmd) {
      // Intended to be empty
    }
  }

  /**
   * Test version of {@link GitOptions} that allow us to fake a remote repository. Instead of
   * fetching from a remote uri it will use a local folder with repositories.
   */
  public static class GitOptionsForTest extends GitOptions {

    private final Path httpsRepos;
    private final Validator validator;
    private final Set<String> mappingPrefixes = Sets.newHashSet("https://");
    private final GeneralOptions generalOptions;
    @Nullable private String forcePushForRefspec;

    public GitOptionsForTest(GeneralOptions generalOptions, Validator validator)
        throws IOException {
      super(generalOptions);
      this.generalOptions = checkNotNull(generalOptions);
      this.validator = checkNotNull(validator);
      this.httpsRepos = Files.createTempDirectory("remote_git_mocks");
    }

    public GitRepository mockRemoteRepo(String url, Map<String, String> env) throws RepoException {
      GitRepository repo =
          GitRepository.newBareRepo(
              httpsRepos.resolve(url), new GitEnvironment(env), generalOptions.isVerbose(),
              DEFAULT_TIMEOUT, false);
      repo.init();
      return repo;
    }

    @Override
    public GitRepository createBareRepo(GeneralOptions generalOptions, Path path)
        throws RepoException {
      return initRepo(new RewriteUrlGitRepository(path, null, generalOptions, httpsRepos,
                                                  validator, mappingPrefixes, forcePushForRefspec));
    }

    /** Add additional prefixes that should be mapped for test. */
    @SuppressWarnings("unused")
    public GitOptionsForTest addPrefix(String prefix) {
      mappingPrefixes.add(prefix);
      return this;
    }

    @SuppressWarnings("unused")
    public GitOptionsForTest forcePushForRefspecPrefix(String forcePushForRefspec) {
      this.forcePushForRefspec = forcePushForRefspec;
      return this;
    }
  }

  public static class RewriteUrlGitRepository extends GitRepository {

    private final GeneralOptions generalOptions;
    private final Path httpsRepos;
    private final Validator validator;
    private final Set<String> mappingPrefixes;
    @Nullable
    private final String forcePushForRefspec;

    public RewriteUrlGitRepository(Path gitDir, Path workTree, GeneralOptions generalOptions,
        Path httpsRepos, Validator validator, Set<String> mappingPrefixes,
        @Nullable String forcePushForRefspec) {
      super(
          gitDir,
          workTree,
          generalOptions.isVerbose(),
          new GitEnvironment(generalOptions.getEnvironment()), generalOptions.repoTimeout, false);
      this.generalOptions = generalOptions;
      this.httpsRepos = httpsRepos;
      this.validator = validator;
      this.mappingPrefixes = mappingPrefixes;
      this.forcePushForRefspec = forcePushForRefspec;
    }

    @Override
    public FetchResult fetch(
        String url,
        boolean prune,
        boolean force,
        Iterable<String> refspecs,
        boolean partialFetch,
        Optional<Integer> depth)
        throws RepoException, ValidationException {
      validator.validateFetch(url, prune, force, refspecs);
      return super.fetch(mapUrl(url), prune, force, refspecs, partialFetch, Optional.empty());
    }

    @Override
    protected String runPush(PushCmd pushCmd) throws RepoException, ValidationException {
      validator.validatePush(pushCmd);
      if (pushCmd.getUrl() != null) {
        pushCmd = pushCmd.withRefspecs(mapUrl(pushCmd.getUrl()),
            pushCmd.getRefspecs());
      }
      ImmutableList.Builder<Refspec> newRefspec = ImmutableList.builder();
      for (Refspec refspec : pushCmd.getRefspecs()) {
        if (forcePushForRefspec != null
            && refspec.getDestination().startsWith(forcePushForRefspec)) {
          newRefspec.add(refspec.withAllowNoFastForward());
        } else {
          newRefspec.add(refspec);
        }
      }
      return super.runPush(pushCmd.withRefspecs(pushCmd.getUrl(), newRefspec.build()));
    }

    @Override
    public Map<String, String> lsRemote(
        String url, Collection<String> refs, int maxLogLines, Collection<String> flags)
        throws RepoException, ValidationException {
      return super.lsRemote(mapUrl(url), refs, maxLogLines, flags);
    }

    @Override
    @Nullable
    public String getPrimaryBranch(String url) throws RepoException {
      return super.getPrimaryBranch(mapUrl(url));
    }

    @Override
    public GitRepository withWorkTree(Path newWorkTree) {
      return new RewriteUrlGitRepository(getGitDir(), newWorkTree, generalOptions, httpsRepos,
          validator, mappingPrefixes, forcePushForRefspec);
    }

    private String mapUrl(String url) {
      for (String prefix : mappingPrefixes) {
        if (url.startsWith(prefix)) {
          Path repo = httpsRepos.resolve(url.replace(prefix, ""));
          assertWithMessage(repo.toString()).that(Files.isDirectory(repo)).isTrue();
          return "file:///" + repo;
        }
      }
      return url;
    }
  }

  /**
   * Write content to the path basePath + relativePath. Creating the parent directories if
   * necessary.
   */
  public static void writeFile(Path basePath, String relativePath, String content)
      throws IOException {
    Files.createDirectories(basePath.resolve(relativePath).getParent());
    Files.write(basePath.resolve(relativePath), content.getBytes(UTF_8));
  }

  public static byte[] getResource(String testfile) throws IOException {
    return Files.readAllBytes(
        Paths.get(System.getenv("TEST_SRCDIR"))
            .resolve(System.getenv("TEST_WORKSPACE"))
            .resolve(
                "java/com/google/copybara/git/github/api/testing")
            .resolve(testfile));
  }

  /**
   * Wrapper for predicate to allow readable test failures.
   */
  public static class MockRequestAssertion implements Predicate<String> {

    private final String text;
    private final Predicate<String> delegate;

    public static MockRequestAssertion contains(String expected) {
      return new MockRequestAssertion(
          String.format("Expected request to contain '%s'", expected), s -> s.contains(expected));
    }

    public static MockRequestAssertion equals(String expected) {
      return new MockRequestAssertion(
          String.format("Expected request to be equal to '%s'", expected), s -> s.equals(expected));
    }

    public static MockRequestAssertion and(MockRequestAssertion p1, MockRequestAssertion p2) {
      return new MockRequestAssertion(
          String.format("Expected request Satisfy:\n%s\nand\n%s", p1, p2),
          s -> p1.test(s) && p2.test(s));
    }

    MockRequestAssertion(Predicate<String> delegate) {
      this("Predicate text not given", delegate);
    }

    public MockRequestAssertion(String text, Predicate<String> delegate) {
      this.text = text;
      this.delegate = delegate;
    }

    @Override
    public boolean test(String s) {
      return delegate.test(s);
    }

    @Override
    public String toString() {
      return text;
    }
  }

  /** Class used to validate JSON object matches predicate */
  public static class JsonValidator<T> implements Predicate<String> {
    private boolean called;
    private final Class<T> clazz;
    private final Predicate<T> predicate;

    JsonValidator(Class<T> clazz, Predicate<T> predicate) {
      this.clazz = Preconditions.checkNotNull(clazz);
      this.predicate = Preconditions.checkNotNull(predicate);
    }

    @Override
    public boolean test(String s) {
      try {
        T requestObject = GsonFactory.getDefaultInstance().createJsonParser(s).parse(clazz);
        called = true;
        return predicate.test(requestObject);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    public boolean wasCalled() {
      return called;
    }
  }

  public static <T> JsonValidator<T> createValidator(Class<T> clazz, Predicate<T> predicate) {
    return new JsonValidator<>(clazz, predicate);
  }
}