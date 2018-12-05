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
import static com.google.copybara.git.GitModule.DEFAULT_INTEGRATE_LABEL;
import static com.google.copybara.git.GitRepoType.GERRIT_CHANGE_DESCRIPTION_LABEL;
import static com.google.copybara.git.GitRepoType.GERRIT_CHANGE_ID_LABEL;
import static com.google.copybara.git.GitRepoType.GERRIT_CHANGE_NUMBER_LABEL;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;

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
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.testing.TestingConsole;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

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

  private GitOrigin origin;
  private Path remote;
  private OptionsBuilder options;
  private GitRepository repo;

  @Rule public final ExpectedException thrown = ExpectedException.none();

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

    createTestRepo(Files.createTempDirectory("remote"));

    String url = "file://" + remote.toFile().getAbsolutePath();

    origin =
        skylark.eval(
            "result",
            String.format("result = " + "git.gerrit_origin(" + "    url = '%s'," + ")", url));

    Files.write(remote.resolve("base.txt"), new byte[0]);
    repo.add().files("base.txt").run();

    git("commit", "-m", "baseline", "--date", commitTime);
    baseline = repo.parseRef("HEAD");
    Files.write(remote.resolve("test.txt"), "some content".getBytes());
    repo.add().files("test.txt").run();

    git("commit", "-m", "first change", "--date", commitTime);
    firstRevision =
        new GitRevision(
            repo,
            repo.parseRef("HEAD"),
            GitRepoType.gerritPatchSetAsReviewReference(1),
            "12345",
            ImmutableListMultimap.of(
                GERRIT_CHANGE_NUMBER_LABEL, "12345",
                GERRIT_CHANGE_ID_LABEL, CHANGE_ID,
                GERRIT_CHANGE_DESCRIPTION_LABEL, CHANGE_DESCRIPTION,
                DEFAULT_INTEGRATE_LABEL, "gerrit " + url + " 12345 Patch Set 1 " + CHANGE_ID), url);
    git("update-ref", "refs/changes/45/12345/1", firstRevision.getSha1());

    git("commit", "-m", "second change", "--date", commitTime, "--amend");
    secondRevision =
        new GitRevision(
            repo,
            repo.parseRef("HEAD"),
            GitRepoType.gerritPatchSetAsReviewReference(2),
            "12345",
            ImmutableListMultimap.of(
                GERRIT_CHANGE_NUMBER_LABEL, "12345",
                GERRIT_CHANGE_ID_LABEL, CHANGE_ID,
                GERRIT_CHANGE_DESCRIPTION_LABEL, CHANGE_DESCRIPTION,
                DEFAULT_INTEGRATE_LABEL, "gerrit " + url + " 12345 Patch Set 2 " + CHANGE_ID), url);
    git("update-ref", "refs/changes/45/12345/2", secondRevision.getSha1());

    git("commit", "-m", "third change", "--date", commitTime, "--amend");
    thirdRevision =
        new GitRevision(
            repo,
            repo.parseRef("HEAD"),
            GitRepoType.gerritPatchSetAsReviewReference(3),
            "12345",
            ImmutableListMultimap.of(
                GERRIT_CHANGE_NUMBER_LABEL, "12345",
                GERRIT_CHANGE_ID_LABEL, CHANGE_ID,
                GERRIT_CHANGE_DESCRIPTION_LABEL, CHANGE_DESCRIPTION,
                DEFAULT_INTEGRATE_LABEL, "gerrit " + url + " 12345 Patch Set 3 " + CHANGE_ID), url);
    git("update-ref", "refs/changes/45/12345/3", thirdRevision.getSha1());

    GitTestUtil.createFakeGerritNodeDbMeta(repo, 12345, CHANGE_ID);
  }

  @Test
  public void testEmptyUrl() {
    skylark.evalFails("git.gerrit_origin( url = '')", "Invalid empty field 'url'");
  }

  @Test
  public void testReferencesWithContext() throws RepoException, ValidationException {
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
    assertThat(origin.resolve("master").contextReference()).isEqualTo("master");
  }

  @Test
  public void testChanges() throws RepoException, ValidationException {
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
  public void testReferenceNotFound() throws RepoException, ValidationException {
    thrown.expect(CannotResolveRevisionException.class);
    thrown.expectMessage("Cannot find change number 54321");
    origin.resolve("54321");
  }

  @Test
  public void testReferencePatchSetNotFound() throws RepoException, ValidationException {
    thrown.expect(CannotResolveRevisionException.class);
    thrown.expectMessage("Cannot find patch set 42");
    origin.resolve("http://foo.com/#/c/12345/42");
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

  private void createTestRepo(Path folder) throws Exception {
    remote = folder;
    repo = GitRepository.newRepo(true, remote, GitTestUtil.getGitEnv(), DEFAULT_TIMEOUT).init();
  }

  private String git(String... params) throws RepoException {
    return repo.git(remote, params).getStdout();
  }
}
