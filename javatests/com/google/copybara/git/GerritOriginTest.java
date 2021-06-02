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

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.git.GerritChange.GERRIT_OWNER_EMAIL_LABEL;
import static com.google.copybara.git.GitModule.DEFAULT_INTEGRATE_LABEL;
import static com.google.copybara.testing.git.GitTestUtil.mockResponse;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.when;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.Change;
import com.google.copybara.Origin.Reader;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.authoring.Authoring.AuthoringMappingMode;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.stubbing.Answer;

@RunWith(JUnit4.class)
public class GerritOriginTest {

  private static final String commitTime = "2037-02-16T13:00:00Z";
  private static final Authoring AUTHORING =
      new Authoring(
          new Author("foo bar", "baz@bar.com"),
          AuthoringMappingMode.OVERWRITE,
          ImmutableSet.of());
  private static final String CHANGE_ID = "Id5287e977c0d840a6d84eb2c3c1841036c411890";
  private static final String CHANGE_DESCRIPTION = "Create patch set 2\n"
      + "\n"
      + "Uploaded patch set 2.";
  public static final String REPO_URL = "localhost:33333/foo/bar";

  private GitOrigin origin;
  private Path remote;
  private OptionsBuilder options;
  private GitRepository repo;
  private GitTestUtil gitUtil;
  private GitRevision firstRevision;
  private GitRevision secondRevision;
  private GitRevision thirdRevision;
  private String baseline;
  private SkylarkTestExecutor skylark;
  private TestingConsole console;

  @Before
  public void setup() throws Exception {
    options = new OptionsBuilder();
    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console)
        .setOutputRootToTmpDir();

    skylark = new SkylarkTestExecutor(options);
    // Pass custom HOME directory so that we run an hermetic test and we
    // can add custom configuration to $HOME/.gitconfig.
    Path userHomeForTest = Files.createTempDirectory("home");
    options.setEnvironment(GitTestUtil.getGitEnv().getEnvironment());
    options.setHomeDir(userHomeForTest.toString());

    gitUtil = new GitTestUtil(options);
    gitUtil.mockRemoteGitRepos();

    createTestRepo();

    String url = "https://" + REPO_URL;

    origin =
        skylark.eval(
            "result",
            String.format("result = " + "git.gerrit_origin(" + "    url = '%s'," + ")", url));

    Files.write(remote.resolve("base.txt"), new byte[0]);
    repo.add().files("base.txt").run();

    git("commit", "-m", "baseline", "--date", commitTime);
    baseline = repo.parseRef("HEAD");
    Files.write(remote.resolve("test.txt"), "some content".getBytes(UTF_8));
    repo.add().files("test.txt").run();

    git("commit", "-m", "first change", "--date", commitTime);
    firstRevision =
        new GitRevision(
            repo,
            repo.parseRef("HEAD"),
            GerritChange.gerritPatchSetAsReviewReference(1),
            "12345",
            ImmutableListMultimap.<String, String>builder()
                .put(GerritChange.GERRIT_CHANGE_NUMBER_LABEL, "12345")
                .put(GerritChange.GERRIT_CHANGE_ID_LABEL, CHANGE_ID)
                .put(GerritChange.GERRIT_COMPLETE_CHANGE_ID_LABEL, "my_branch-12345")
                .put(GerritChange.GERRIT_CHANGE_BRANCH, "my_branch")
                .put(GERRIT_OWNER_EMAIL_LABEL, "the_owner@example.com")
                .put("GERRIT_REVIEWER_EMAIL", "foo@example.com")
                .put("GERRIT_REVIEWER_EMAIL", "bar@example.com")
                .put("GERRIT_CC_EMAIL", "baz@example.com")
                .put(GerritChange.GERRIT_CHANGE_DESCRIPTION_LABEL, CHANGE_DESCRIPTION)
                .put(DEFAULT_INTEGRATE_LABEL, "gerrit " + url + " 12345 Patch Set 1 " + CHANGE_ID)
                .put(
                    GitRepository.GIT_DESCRIBE_REQUESTED_VERSION,
                    repo.parseRef("HEAD").substring(0, 7))
                .put(GitRepository.GIT_DESCRIBE_FIRST_PARENT, repo.parseRef("HEAD").substring(0, 7))
                .put(GitRepository.GIT_DESCRIBE_ABBREV, "")
                .build(),
            url);
    git("update-ref", "refs/changes/45/12345/1", firstRevision.getSha1());

    git("commit", "-m", "second change", "--date", commitTime, "--amend");

    Files.write(remote.resolve("foo.md"), "some content".getBytes(UTF_8));
    repo.add().files("foo.md").run();

    secondRevision =
        new GitRevision(
            repo,
            repo.parseRef("HEAD"),
            GerritChange.gerritPatchSetAsReviewReference(2),
            "12345",
            ImmutableListMultimap.<String, String>builder()
                .put(GerritChange.GERRIT_CHANGE_NUMBER_LABEL, "12345")
                .put(GerritChange.GERRIT_CHANGE_ID_LABEL, CHANGE_ID)
                .put(GerritChange.GERRIT_COMPLETE_CHANGE_ID_LABEL, "my_branch-12345")
                .put(GerritChange.GERRIT_CHANGE_BRANCH, "my_branch")
                .put(GERRIT_OWNER_EMAIL_LABEL, "the_owner@example.com")
                .put("GERRIT_REVIEWER_EMAIL", "foo@example.com")
                .put("GERRIT_REVIEWER_EMAIL", "bar@example.com")
                .put("GERRIT_CC_EMAIL", "baz@example.com")
                .put(GerritChange.GERRIT_CHANGE_DESCRIPTION_LABEL, CHANGE_DESCRIPTION)
                .put(
                    GitRepository.GIT_DESCRIBE_REQUESTED_VERSION,
                    repo.parseRef("HEAD").substring(0, 7))
                .put(GitRepository.GIT_DESCRIBE_FIRST_PARENT, repo.parseRef("HEAD").substring(0, 7))
                .put(GitRepository.GIT_DESCRIBE_ABBREV, "")
                .put(DEFAULT_INTEGRATE_LABEL, "gerrit " + url + " 12345 Patch Set 2 " + CHANGE_ID)
                .build(),
            url);
    git("update-ref", "refs/changes/45/12345/2", secondRevision.getSha1());

    git("commit", "-m", "third change", "--date", commitTime, "--amend");
    thirdRevision =
        new GitRevision(
            repo,
            repo.parseRef("HEAD"),
            GerritChange.gerritPatchSetAsReviewReference(3),
            "12345",
            ImmutableListMultimap.<String, String>builder()
                .put(GerritChange.GERRIT_CHANGE_NUMBER_LABEL, "12345")
                .put(GerritChange.GERRIT_COMPLETE_CHANGE_ID_LABEL, "my_branch-12345")
                .put(GerritChange.GERRIT_CHANGE_BRANCH, "my_branch")
                .put(GERRIT_OWNER_EMAIL_LABEL, "the_owner@example.com")
                .put("GERRIT_REVIEWER_EMAIL", "foo@example.com")
                .put("GERRIT_REVIEWER_EMAIL", "bar@example.com")
                .put("GERRIT_CC_EMAIL", "baz@example.com")
                .put(GerritChange.GERRIT_CHANGE_ID_LABEL, CHANGE_ID)
                .put(GerritChange.GERRIT_CHANGE_DESCRIPTION_LABEL, CHANGE_DESCRIPTION)
                .put(
                    GitRepository.GIT_DESCRIBE_REQUESTED_VERSION,
                    repo.parseRef("HEAD").substring(0, 7))
                .put(GitRepository.GIT_DESCRIBE_FIRST_PARENT, repo.parseRef("HEAD").substring(0, 7))
                .put(GitRepository.GIT_DESCRIBE_ABBREV, "")
                .put(DEFAULT_INTEGRATE_LABEL, "gerrit " + url + " 12345 Patch Set 3 " + CHANGE_ID)
                .build(),
            url);
    git("update-ref", "refs/changes/45/12345/3", thirdRevision.getSha1());

    GitTestUtil.createFakeGerritNodeDbMeta(repo, 12345, CHANGE_ID);
  }

  @Test
  public void testEmptyUrl() {
    skylark.evalFails("git.gerrit_origin( url = '')", "Invalid empty field 'url'");
  }

  @Test
  public void testReferencesWithContext() throws Exception {
    mockChange(12345);
    validateSameGitRevision(origin.resolve("12345"), thirdRevision);
    validateSameGitRevision(origin.resolve("http://foo.com/#/c/12345"), thirdRevision);
    validateSameGitRevision(origin.resolve("http://foo.com/c/12345"), thirdRevision);
    validateSameGitRevision(origin.resolve("http://foo.com/12345"), thirdRevision);
    validateSameGitRevision(origin.resolve("https://foo.com/12345"), thirdRevision);
    validateSameGitRevision(origin.resolve("http://foo.com/#/c/12345/2"), secondRevision);
    validateSameGitRevision(origin.resolve("https://foo.com/#/c/12345/"), thirdRevision);

    validateSameGitRevision(origin.resolve("refs/changes/45/12345/1"), firstRevision);
    validateSameGitRevision(origin.resolve("refs/changes/45/12345/2"), secondRevision);
    validateSameGitRevision(origin.resolve("refs/changes/45/12345/3"), thirdRevision);

    // Test resolving from GitOrigin-RevId string:
    GitRevision resolved = origin.resolve(thirdRevision.asString());
    assertThat(resolved.getSha1()).isEqualTo(thirdRevision.getSha1());
    assertThat(resolved.getReviewReference()).isEqualTo(thirdRevision.getReviewReference());

    resolved = origin.resolve(thirdRevision.getSha1());
    assertThat(resolved.getSha1()).isEqualTo(thirdRevision.getSha1());
    assertThat(resolved.getReviewReference()).isNull();

    // Doesn't have any context as we passed a SHA-1
    assertThat(origin.resolve(firstRevision.asString()).contextReference()).isNull();
    // The context reference defaults to regular git one
    assertThat(origin.resolve(origin.getRepository().getPrimaryBranch()).contextReference())
        .isEqualTo(origin.getRepository().getPrimaryBranch());
  }

  private void mockChange(int changeNumber) throws IOException {
    when(gitUtil
        .httpTransport()
        .buildRequest(eq("GET"),
            startsWith(
                "https://localhost:33333/changes/" + changeNumber
                    + "?o=DETAILED_ACCOUNTS&o=DETAILED_LABELS")))
        .then(
            (Answer<LowLevelHttpRequest>)
                invocation -> {
                  String change = changeIdFromRequest((String) invocation.getArguments()[1]);
                  return mockResponse(
                      "{"
                          + "  id : \"my_branch-" + changeNumber + "\","
                          + "  change_id : \"" + change + "\","
                          + "  status : \"NEW\","
                          + "  branch : \"my_branch\","
                          + "  owner : { email : \"the_owner@example.com\"},"
                          + "  \"reviewers\": {\n"
                          + "    \"REVIEWER\": [\n"
                          + "      {\n"
                          + "        \"_account_id\": 1,\n"
                          + "        \"email\": \"foo@example.com\"\n"
                          + "      },\n"
                          + "      {\n"
                          + "        \"_account_id\": 2,\n"
                          + "        \"email\": \"bar@example.com\"\n"
                          + "      },\n"
                          + "    ],\n"
                          + "    \"CC\": [\n"
                          + "      {\n"
                          + "        \"_account_id\": 3,\n"
                          + "        \"email\": \"baz@example.com\"\n"
                          + "      }\n"
                          + "    ]\n"
                          + "   }"
                          + "}");
                });
  }

  @Test
  public void testBranchFiltering() throws Exception {
    mockChange(12345);
    GerritOrigin origin = skylark.eval("g", "g = git.gerrit_origin("
        + "  url = 'https://" + REPO_URL + "',"
        + "  branch = 'master')");

    EmptyChangeException e =
        assertThrows(EmptyChangeException.class, () -> origin.resolve("12345"));
    assertThat(e).hasMessageThat().contains("Skipping import of change 12345 for branch my_branch");
    // But this should work, as the last-rev needs to be resolved:
    origin.resolve(firstRevision.getSha1());
  }

  @Test
  public void testChanges() throws Exception {
    mockChange(12345);
    Reader<GitRevision> reader = origin.newReader(Glob.ALL_FILES, AUTHORING);
    Change<GitRevision> res = reader.change(origin.resolve("http://foo.com/#/c/12345/2"));
    assertThat(res.getRevision()).isEqualTo(secondRevision);
    assertThat(res.getRevision().getUrl()).isNotNull();
    assertThat(res.getRevision().contextReference()).isEqualTo(secondRevision.contextReference());
    assertThat(res.getRevision().getReviewReference())
        .isEqualTo(secondRevision.getReviewReference());

    ImmutableList<Change<GitRevision>> changes = reader.changes(
        origin.resolve("http://foo.com/#/c/12345/1"),
        origin.resolve("http://foo.com/#/c/12345/3")).getChanges();

    assertThat(changes.get(0).getRevision().getUrl()).isNotNull();
    // Each ref is conceptually a rebase. Size is not really important for this test.
    assertThat(changes).hasSize(1);

    assertThat(reader.findBaselinesWithoutLabel(origin.resolve("12345"), /*limit=*/ 1).get(0)
        .getSha1())
        .isEqualTo(baseline);
  }

  @Test
  public void testIgnoreGerritNoop() throws Exception {
    mockChange(12345);
    GerritOrigin origin =
        skylark.eval(
            "g",
            "g = git.gerrit_origin("
                + "  url = 'https://"
                + REPO_URL
                + "',"
                + "  branch = 'my_branch',"
                + "  ignore_gerrit_noop = True)");
    Reader<GitRevision> reader =
        origin.newReader(Glob.createGlob(ImmutableList.of("depot/foo/bar")), AUTHORING);
    assertThat(reader
        .changes(
            origin.resolve("http://foo.com/#/c/12345/1"),
            origin.resolve("http://foo.com/#/c/12345/2")).isEmpty()).isTrue();
  }

  @Test
  public void testOpFollowedByNoop() throws Exception {
    mockChange(12345);
    GerritOrigin origin =
        skylark.eval(
            "g",
            "g = git.gerrit_origin("
                + "  url = 'https://"
                + REPO_URL
                + "',"
                + "  branch = 'my_branch',"
                + "  ignore_gerrit_noop = True)");
    Reader<GitRevision> reader =
        origin.newReader(Glob.createGlob(ImmutableList.of("**.md")), AUTHORING);
    // noop should return empty
    assertThat(reader
        .changes(
            origin.resolve("http://foo.com/#/c/12345/1"),
            origin.resolve("http://foo.com/#/c/12345/2")).isEmpty()).isTrue();
    // noop -> op should not return empty
    assertThat(reader
        .changes(
            origin.resolve("http://foo.com/#/c/12345/1"),
            origin.resolve("http://foo.com/#/c/12345/3")).isEmpty()).isFalse();
  }

  @Test
  public void testReferenceNotFound() throws RepoException, ValidationException {
    CannotResolveRevisionException thrown =
        assertThrows(CannotResolveRevisionException.class, () -> origin.resolve("54321"));
    assertThat(thrown).hasMessageThat().contains("Cannot find change number 54321");
  }

  @Test
  public void testReferencePatchSetNotFound() throws RepoException, ValidationException {
    CannotResolveRevisionException thrown =
        assertThrows(
            CannotResolveRevisionException.class,
            () -> origin.resolve("http://foo.com/#/c/12345/42"));
    assertThat(thrown).hasMessageThat().contains("Cannot find patch set 42");
  }

  @Test
  public void testDescribe() throws RepoException, ValidationException {
    ImmutableMultimap<String, String> actual = origin.describe(Glob.ALL_FILES);
    assertThat(actual.get("type")).containsExactly("git.origin");
    assertThat(actual.get("repoType")).containsExactly("GERRIT");
  }

  @Test
  public void testFeedbackEndpoint() throws ValidationException {
    GerritOrigin origin = skylark.eval("g", "g = git.gerrit_origin(url = 'rpc://some/host')");
    Reader<GitRevision> reader = origin.newReader(Glob.ALL_FILES, AUTHORING);
    // We already have enough coverage of this class plus core wiring of endpoints.
    assertThat(reader.getFeedbackEndPoint(console)).isInstanceOf(GerritEndpoint.class);
  }

  private void validateSameGitRevision(GitRevision resolved, GitRevision expected) {
    assertThat(resolved.asString()).isEqualTo(expected.asString());
    assertThat(resolved.getSha1()).isEqualTo(expected.getSha1());
    assertThat(resolved.getReviewReference()).isEqualTo(expected.getReviewReference());
    assertThat(resolved.contextReference()).isEqualTo(expected.contextReference());
    assertThat(resolved.associatedLabels()).isEqualTo(expected.associatedLabels());
    assertThat(resolved.getUrl()).isEqualTo(expected.getUrl());
  }

  private void createTestRepo() throws Exception {
    repo = gitUtil.mockRemoteRepo(REPO_URL).withWorkTree(
        Files.createTempDirectory("remote"));
    remote = repo.getWorkTree();
  }

  private String git(String... params) throws RepoException {
    return repo.simpleCommand(params).getStdout();
  }

  private String changeIdFromRequest(String url) {
    return url.replaceAll(".*(I[a-z0-9]{40}).*", "$1");
  }
}
