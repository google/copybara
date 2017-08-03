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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.CannotResolveRevisionException;
import com.google.copybara.Change;
import com.google.copybara.Origin.Reader;
import com.google.copybara.RepoException;
import com.google.copybara.ValidationException;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.authoring.Authoring.AuthoringMappingMode;
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
          ImmutableSet.<String>of());
  private GitOrigin origin;
  private Path remote;
  private OptionsBuilder options;
  private GitRepository repo;

  @Rule public final ExpectedException thrown = ExpectedException.none();

  private GitRevision firstRevision;
  private GitRevision secondRevision;
  private GitRevision thirdRevision;

  @Before
  public void setup() throws Exception {
    options = new OptionsBuilder();
    TestingConsole console = new TestingConsole();
    options = new OptionsBuilder().setConsole(console);

    Path reposDir = Files.createTempDirectory("repos_repo");
    options.git.repoStorage = reposDir.toString();

    SkylarkTestExecutor skylark = new SkylarkTestExecutor(options, GitModule.class);
    // Pass custom HOME directory so that we run an hermetic test and we
    // can add custom configuration to $HOME/.gitconfig.
    Path userHomeForTest = Files.createTempDirectory("home");
    options.setEnvironment(GitTestUtil.getGitEnv());
    options.setHomeDir(userHomeForTest.toString());

    createTestRepo(Files.createTempDirectory("remote"));

    String url = "file://" + remote.toFile().getAbsolutePath();

    origin =
        skylark.eval(
            "result",
            String.format("result = " + "git.gerrit_origin(" + "    url = '%s'," + ")", url));

    Files.write(remote.resolve("test.txt"), "some content".getBytes());
    repo.add().files("test.txt").run();

    git("commit", "-m", "first change", "--date", commitTime);
    firstRevision =
        new GitRevision(
            repo,
            repo.parseRef("HEAD"),
            GitRepoType.GERRIT.gerritPatchSetAsReviewReference(1),
            "12345",
            ImmutableMap.of(GitRepoType.GERRIT_CHANGE_NUMBER_LABEL, "12345"));
    git("update-ref", "refs/changes/45/12345/1", firstRevision.getSha1());

    git("commit", "-m", "second change", "--date", commitTime, "--amend");
    secondRevision =
        new GitRevision(
            repo,
            repo.parseRef("HEAD"),
            GitRepoType.GERRIT.gerritPatchSetAsReviewReference(2),
            "12345",
            ImmutableMap.of(GitRepoType.GERRIT_CHANGE_NUMBER_LABEL, "12345"));
    git("update-ref", "refs/changes/45/12345/2", secondRevision.getSha1());

    git("commit", "-m", "third change", "--date", commitTime, "--amend");
    thirdRevision =
        new GitRevision(
            repo,
            repo.parseRef("HEAD"),
            GitRepoType.GERRIT.gerritPatchSetAsReviewReference(3),
            "12345",
            ImmutableMap.of(GitRepoType.GERRIT_CHANGE_NUMBER_LABEL, "12345"));
    git("update-ref", "refs/changes/45/12345/3", thirdRevision.getSha1());
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
    assertThat(res.getRevision().contextReference()).isEqualTo(secondRevision.contextReference());
    assertThat(res.getRevision().getReviewReference())
        .isEqualTo(secondRevision.getReviewReference());

    ImmutableList<Change<GitRevision>> changes = reader.changes(
            origin.resolve("http://foo.com/#/c/12345/1"),
            origin.resolve("http://foo.com/#/c/12345/3"));

    // Each ref is conceptually a rebase. Size is not really important for this test.
    assertThat(changes).hasSize(1);
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

  private void validateSameGitRevision(GitRevision resolved, GitRevision expected) {
    assertThat(resolved.asString()).isEqualTo(expected.asString());
    assertThat(resolved.getSha1()).isEqualTo(expected.getSha1());
    assertThat(resolved.getReviewReference()).isEqualTo(expected.getReviewReference());
    assertThat(resolved.contextReference()).isEqualTo(expected.contextReference());
    assertThat(resolved.associatedLabels()).isEqualTo(expected.associatedLabels());
  }

  private void createTestRepo(Path folder) throws Exception {
    remote = folder;
    repo =
        GitRepository.newRepo(true, remote, options.general.getEnvironment()).init(
        );
  }

  private String git(String... params) throws RepoException {
    return repo.git(remote, params).getStdout();
  }
}
