/*
 * Copyright (C) 2017 Google Inc.
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
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.copybara.Destination.DestinationStatus;
import com.google.copybara.Destination.Writer;
import com.google.copybara.RepoException;
import com.google.copybara.ValidationException;
import com.google.copybara.git.GitRepository.GitLogEntry;
import com.google.copybara.git.github_api.GithubApi;
import com.google.copybara.testing.DummyRevision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.OptionsBuilder.GithubMockHttpTransport;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TransformResults;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.testing.git.GitTestUtil.TestGitOptions;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Map.Entry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GithubPrDestinationTest {

  private Path repoGitDir;
  private OptionsBuilder options;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;

  @Rule
  public final ExpectedException thrown = ExpectedException.none();
  private Path workdir;
  private Path localHub;
  private String expectedProject = "foo";
  private GithubMockHttpTransport githubMockHttpTransport;

  @Before
  public void setup() throws Exception {
    repoGitDir = Files.createTempDirectory("GithubPrDestinationTest-repoGitDir");
    workdir = Files.createTempDirectory("workdir");
    localHub = Files.createTempDirectory("localHub");

    git("init", "--bare", repoGitDir.toString());
    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console)
        .setOutputRootToTmpDir();
    options.git = new TestGitOptions(localHub, () -> GithubPrDestinationTest.this.options.general);

    options.github = new GithubOptions(() -> options.general, options.git) {
      @Override
      public GithubApi getApi(String project) throws RepoException {
        assertThat(project).isEqualTo(expectedProject);
        return super.getApi(project);
      }

      @Override
      protected HttpTransport getHttpTransport() {
        return githubMockHttpTransport;
      }
    };
    Path credentialsFile = Files.createTempFile("credentials", "test");
    Files.write(credentialsFile, "https://user:SECRET@github.com".getBytes(UTF_8));
    options.git.credentialHelperStorePath = credentialsFile.toString();

    options.gitDestination = new GitDestinationOptions(() -> options.general, options.git);
    options.gitDestination.committerEmail = "commiter@email";
    options.gitDestination.committerName = "Bara Kopi";
    skylark = new SkylarkTestExecutor(options, GitModule.class);
  }

  @Test
  public void testWrite() throws ValidationException, IOException, RepoException {
    checkWrite("feature");
  }

  @Test
  public void testWrite_destinationPrBranchFlag()
      throws ValidationException, IOException, RepoException {
    options.githubDestination.destinationPrBranch = "feature";
    checkWrite(/*groupId=*/null);
  }

  @Test
  public void testWrite_noGroupId()
      throws ValidationException, IOException, RepoException {
    thrown.expect(ValidationException.class);
    thrown.expectMessage("git.github_pr_destination is incompatible with the current origin");
    checkWrite(/*groupId=*/null);
  }

  private void checkWrite(String groupId)
      throws ValidationException, RepoException, IOException {
    githubMockHttpTransport = new GithubMockHttpTransport() {

      @Override
      protected byte[] getContent(String method, String url, MockLowLevelHttpRequest request)
          throws IOException {
        boolean isPulls = "https://api.github.com/repos/foo/pulls".equals(url);
        if ("GET".equals(method) && isPulls) {
          return "[]".getBytes(UTF_8);
        } else if ("POST".equals(method) && isPulls) {
          assertThat(request.getContentAsString())
              .isEqualTo("{\"base\":\"master\",\"body\":\"test summary\",\"head\":\"feature\",\"title\":\"test summary\"}");
          return ("{\n"
              + "  \"id\": 1,\n"
              + "  \"number\": 12345,\n"
              + "  \"state\": \"open\",\n"
              + "  \"title\": \"test summary\",\n"
              + "  \"body\": \"test summary\""
              + "}").getBytes();
        }
        fail(method + " " + url);
        throw new IllegalStateException();
      }
    };
    GithubPrDestination d = skylark.eval("r", "r = git.github_pr_destination("
        + "    url = 'https://github.com/foo'"
        + ")");

    Writer<GitRevision> writer = d.newWriter(Glob.ALL_FILES, /*dryRun=*/false, groupId,
        /*oldWriter=*/null);

    GitRepository remote = localHubRepo("foo");
    addFiles(remote, null, "first change", ImmutableMap.<String, String>builder()
        .put("foo.txt", "").build());

    Files.write(this.workdir.resolve("test.txt"), "some content".getBytes());
    writer.write(TransformResults.of(this.workdir, new DummyRevision("one")), console);
    Files.write(this.workdir.resolve("test.txt"), "other content".getBytes());
    writer.write(TransformResults.of(this.workdir, new DummyRevision("two")), console);

    // Use a new writer that shares the old state
    writer = d.newWriter(Glob.ALL_FILES, /*dryRun=*/false, groupId,
        /*oldWriter=*/writer);

    Files.write(this.workdir.resolve("test.txt"), "and content".getBytes());
    writer.write(TransformResults.of(this.workdir, new DummyRevision("three")), console);

    console.assertThat().timesInLog(1, MessageType.INFO,
        "Pull Request https://github.com/foo/pull/12345 created using branch 'feature'.");

    assertThat(remote.refExists("feature")).isTrue();
    assertThat(Iterables.transform(remote.log("feature").run(), GitLogEntry::getBody))
        .containsExactly("first change\n",
            "test summary\n"
                + "\n"
                + "DummyOrigin-RevId: one\n",
            "test summary\n"
                + "\n"
                + "DummyOrigin-RevId: two\n",
            "test summary\n"
                + "\n"
                + "DummyOrigin-RevId: three\n");

    // If we don't keep writer state (same as a new migration). We do a rebase of
    // all the changes.
    writer = d.newWriter(Glob.ALL_FILES, /*dryRun=*/false, groupId,
        /*oldWriter=*/null);

    Files.write(this.workdir.resolve("test.txt"), "and content".getBytes());
    writer.write(TransformResults.of(this.workdir, new DummyRevision("four")), console);

    assertThat(Iterables.transform(remote.log("feature").run(), GitLogEntry::getBody))
        .containsExactly("first change\n",
            "test summary\n"
                + "\n"
                + "DummyOrigin-RevId: four\n");
  }

  @Test
  public void testFindProject() throws ValidationException, IOException, RepoException {
    checkFindProject("https://github.com/foo", "foo");
    checkFindProject("https://github.com/foo/bar", "foo/bar");
    checkFindProject("https://github.com/foo.git", "foo");
    checkFindProject("https://github.com/foo/", "foo");
    checkFindProject("git+https://github.com/foo", "foo");
    checkFindProject("git@github.com/foo", "foo");
    thrown.expect(ValidationException.class);
    thrown.expectMessage("Cannot find project name from url https://github.com");
    checkFindProject("https://github.com", "foo");
  }

  private void checkFindProject(String url, final String project) throws ValidationException {
    GithubPrDestination d = skylark.eval("r", "r = git.github_pr_destination("
        + "    url = '" + url + "',"
        + "    destination_ref = 'other',"
        + ")");

    assertThat(d.getProjectName()).isEqualTo(project);
  }

  @Test
  public void testWriteNoMaster() throws ValidationException, IOException, RepoException {
    githubMockHttpTransport = new GithubMockHttpTransport() {

      @Override
      protected byte[] getContent(String method, String url, MockLowLevelHttpRequest request)
          throws IOException {
        boolean isPulls = "https://api.github.com/repos/foo/pulls".equals(url);
        if ("GET".equals(method) && isPulls) {
          return "[]".getBytes(UTF_8);
        } else if ("POST".equals(method) && isPulls) {
          assertThat(request.getContentAsString())
              .isEqualTo("{\"base\":\"other\",\"body\":\"test summary\",\"head\":\"feature\",\"title\":\"test summary\"}");
          return ("{\n"
              + "  \"id\": 1,\n"
              + "  \"number\": 12345,\n"
              + "  \"state\": \"open\",\n"
              + "  \"title\": \"test summary\",\n"
              + "  \"body\": \"test summary\""
              + "}").getBytes();
        }
        fail(method + " " + url);
        throw new IllegalStateException();
      }
    };
    GithubPrDestination d = skylark.eval("r", "r = git.github_pr_destination("
        + "    url = 'https://github.com/foo',"
        + "    destination_ref = 'other',"
        + ")");

    Writer<GitRevision> writer = d.newWriter(Glob.ALL_FILES, /*dryRun=*/false, "feature",
        /*oldWriter=*/null);

    GitRepository remote = localHubRepo("foo");
    addFiles(remote, "master", "first change", ImmutableMap.<String, String>builder()
        .put("foo.txt", "").build());

    addFiles(remote, "other", "second change", ImmutableMap.<String, String>builder()
        .put("foo.txt", "test").build());

    Files.write(this.workdir.resolve("test.txt"), "some content".getBytes());
    writer.write(TransformResults.of(this.workdir, new DummyRevision("one")), console);

    assertThat(remote.refExists("feature")).isTrue();
    assertThat(Iterables.transform(remote.log("feature").run(), GitLogEntry::getBody))
        .containsExactly("first change\n", "second change\n",
            "test summary\n"
                + "\n"
                + "DummyOrigin-RevId: one\n");
  }

  @Test
  public void testDestinationStatus() throws ValidationException, IOException, RepoException {
    options.githubDestination.createPullRequest = false;
    githubMockHttpTransport = GitTestUtil.NO_GITHUB_API_CALLS;
    GithubPrDestination d = skylark.eval("r", "r = git.github_pr_destination("
        + "    url = 'https://github.com/foo'"
        + ")");

    Writer<GitRevision> writer = d.newWriter(Glob.ALL_FILES, /*dryRun=*/false, "feature",
        /*oldWriter=*/null);

    GitRepository remote = localHubRepo("foo");
    addFiles(remote, "master", "first change\n\nDummyOrigin-RevId: baseline",
        ImmutableMap.<String, String>builder()
            .put("foo.txt", "").build());

    DestinationStatus status = writer.getDestinationStatus("DummyOrigin-RevId");

    assertThat(status.getBaseline()).isEqualTo("baseline");
    assertThat(status.getPendingChanges()).isEmpty();

    Files.write(this.workdir.resolve("test.txt"), "some content".getBytes());
    writer.write(TransformResults.of(this.workdir, new DummyRevision("one")), console);

    // New writer since after changes it keeps state internally for ITERATIVE mode
    status = d.newWriter(Glob.ALL_FILES, /*dryRun=*/false, "feature",/*oldWriter=*/null)
        .getDestinationStatus("DummyOrigin-RevId");

    assertThat(status.getBaseline()).isEqualTo("baseline");
    // Not supported for now as we rewrite the whole branch history.
    assertThat(status.getPendingChanges()).isEmpty();
  }

  private void addFiles(GitRepository remote, String branch, String msg, Map<String, String> files)
      throws IOException, RepoException {
    Path temp = Files.createTempDirectory("temp");
    GitRepository tmpRepo = remote.withWorkTree(temp);
    if (branch != null) {
      if (tmpRepo.refExists(branch)) {
        tmpRepo.simpleCommand("checkout", branch);
      } else if (!branch.equals("master")) {
        tmpRepo.simpleCommand("branch", branch);
        tmpRepo.simpleCommand("checkout", branch);
      }
    }

    for (Entry<String, String> entry : files.entrySet()) {
      Path file = temp.resolve(entry.getKey());
      Files.createDirectories(file.getParent());
      Files.write(file, entry.getValue().getBytes(UTF_8));
    }

    tmpRepo.add().all().run();
    tmpRepo.simpleCommand("commit", "-m", msg);
  }

  private GitRepository localHubRepo(String name) throws RepoException {
    GitRepository repo = GitRepository.newBareRepo(localHub.resolve("github.com/" + name),
        getGitEnv(),
        options.general.isVerbose());
    repo.init();
    return repo;
  }

  private String git(String... argv) throws RepoException {
    return repo()
        .git(repoGitDir, argv)
        .getStdout();
  }

  private GitRepository repo() {
    return repoForPath(repoGitDir);
  }

  private GitRepository repoForPath(Path path) {
    return GitRepository.newBareRepo(path, getGitEnv(),  /*verbose=*/true);
  }

}
