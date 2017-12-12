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

import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.RepoException;
import com.google.copybara.ValidationException;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.authoring.Authoring.AuthoringMappingMode;
import com.google.copybara.testing.FileSubjects;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Map.Entry;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitOriginSubmodulesTest {

  private static final String GITMODULES = ".gitmodules";
  private Path checkoutDir;
  private final Authoring authoring = new Authoring(new Author("foo", "default@example.com"),
      AuthoringMappingMode.PASS_THRU, ImmutableSet.of());

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  private SkylarkTestExecutor skylark;

  @Before
  public void setup() throws Exception {
    OptionsBuilder options = new OptionsBuilder().setConsole(new TestingConsole());

    Path reposDir = Files.createTempDirectory("repos_repo");
    options.git.repoStorage = reposDir.toString();

    skylark = new SkylarkTestExecutor(options, GitModule.class);
    // Pass custom HOME directory so that we run an hermetic test and we
    // can add custom configuration to $HOME/.gitconfig.
    Path userHomeForTest = Files.createTempDirectory("home");
    options.setHomeDir(userHomeForTest.toString());

    checkoutDir = Files.createTempDirectory("checkout");
  }

  private GitOrigin origin(String url, String master) throws ValidationException {
    return skylark.eval("result",
        String.format("result = git.origin(\n"
            + "    url = '%s',\n"
            + "    ref = '%s',\n"
            + "    submodules = 'RECURSIVE',\n"
            + ")", url, master));
  }

  /**
   * Test basic cases: Absolute urls, sibling relative url, default (master) branch and an
   * specific branch.
   */
  @Test
  public void testBasicCases() throws Exception {
    Path base = Files.createTempDirectory("base");
    createRepoWithFoo(base, "r1");
    GitRepository r2 = createRepoWithFoo(base, "r2");
    // Build a relative url submodule
    r2.simpleCommand("submodule", "add", "-f", "--name", "r1", "--reference",
        r2.getWorkTree().toString(), "../r1");
    commit(r2, "adding r1 submodule");
    r2.simpleCommand("branch", "for_submodule");
    // This commit shouldn't be read, since it is in master and r3 depends on 'for_submodule' branch:
    commitAdd(r2, ImmutableMap.of("foo", "2"));

    GitRepository r3 = createRepoWithFoo(base, "r3");
    // Build an absolute url submodule
    r3.simpleCommand("submodule", "add", "--branch", "for_submodule",
        "file://" + r2.getWorkTree(), "r2");
    commit(r3, "adding r2 submodule");

    GitOrigin origin = origin("file://" + r3.getGitDir(), "master");
    GitRevision master = origin.resolve("master");
    origin.newReader(Glob.ALL_FILES, authoring).checkout(master, checkoutDir);

    FileSubjects.assertThatPath(checkoutDir)
        .containsFiles(GITMODULES, "r2/" + GITMODULES)
        .containsFile("foo", "1")
        .containsFile("r2/foo", "1")
        .containsFile("r2/r1/foo", "1")
        .containsNoMoreFiles();
  }

  private GitRepository createRepoWithFoo(Path base, String name)
      throws IOException, RepoException, ValidationException {
    Files.createDirectories(base.resolve(name));
    GitRepository r1 = GitRepository.newRepo(false, base.resolve(name), getGitEnv()).init(
    );
    commitAdd(r1, ImmutableMap.of("foo", "1"));
    return r1;
  }

  /**
   * Test that we can refer to HEAD as '.' in the branch field
   */
  @Test
  public void testDotBranch() throws Exception {
    Path base = Files.createTempDirectory("base");
    GitRepository r1 = createRepoWithFoo(base, "r1");
    GitRepository r2 = createRepoWithFoo(base, "r2");
    // Build a relative url submodule
    r2.simpleCommand("submodule", "add", "-f", "--branch", "master", "--name", "r1",
        "file://" + r1.getWorkTree());
    Path moduleCfg = r2.getWorkTree().resolve(GITMODULES);
    // Replace master with '.'. This is a valid branch reference but I haven't found a way of
    // adding it with the submodule command.
    Files.write(moduleCfg, new String(Files.readAllBytes(moduleCfg)).replace("master", ".")
        .getBytes());
    commit(r2, "adding r1 submodule");

    GitOrigin origin = origin("file://" + r2.getGitDir(), "master");
    GitRevision master = origin.resolve("master");
    origin.newReader(Glob.ALL_FILES, authoring).checkout(master, checkoutDir);

    FileSubjects.assertThatPath(checkoutDir)
        .containsFiles(GITMODULES)
        .containsFile("foo", "1")
        .containsFile("r1/foo", "1")
        .containsNoMoreFiles();
  }

  /**
   * Test that even if submodules config are tracking a moving ref (master, etc.), each
   * commit is associated with an specific SHA-1.
   */
  @Test
  public void testFixedSha1PerCommit() throws Exception {
    Path base = Files.createTempDirectory("base");
    GitRepository r1 = createRepoWithFoo(base, "r1");
    GitRepository r2 = createRepoWithFoo(base, "r2");

    r2.simpleCommand("submodule", "add", "--branch", "master", "--name", "r1",
        "file://" + r1.getWorkTree());
    commit(r2, "adding r1 submodule");
    GitRevision r2FirstSha1 = r2.showRef().get("refs/heads/master");

    addFile(r1, "bar", "bar");
    addFile(r1, "foo", "foo");
    commit(r1, "bar change");

    r2.simpleCommand("submodule", "update", "--remote");
    r2.add().all().run();
    commit(r2, "updating r1 submodule");

    GitRevision r2SecondSha1 = r2.showRef().get("refs/heads/master");

    GitOrigin origin = origin("file://" + r2.getGitDir(), "refs/heads/master");
    origin.resolve(r2FirstSha1.getSha1());
    origin.newReader(Glob.ALL_FILES, authoring).checkout(r2FirstSha1, checkoutDir);

    FileSubjects.assertThatPath(checkoutDir)
        .containsFiles(GITMODULES)
        .containsFile("foo", "1")
        .containsFile("r1/foo", "1")
        .containsNoMoreFiles();

    origin.resolve(r2SecondSha1.getSha1());
    origin.newReader(Glob.ALL_FILES, authoring).checkout(r2SecondSha1, checkoutDir);

    FileSubjects.assertThatPath(checkoutDir)
        .containsFiles(GITMODULES)
        .containsFile("foo", "1")
        .containsFile("r1/foo", "foo")
        .containsFile("r1/bar", "bar")
        .containsNoMoreFiles();
  }

  private void commitAdd(GitRepository repo, Map<String, String> files)
      throws IOException, RepoException, ValidationException {
    for (Entry<String, String> e : files.entrySet()) {
      addFile(repo, e.getKey(), e.getValue());
    }
    commit(repo, "message");
  }

  private void addFile(GitRepository repo, String name, String content)
      throws IOException, RepoException {
    Files.write(repo.getWorkTree().resolve(name), content.getBytes(UTF_8));
    repo.add().files(name).run();
  }

  private void commit(GitRepository repo, String message)
      throws RepoException, ValidationException {
    repo.commit("foo <foobar@example.com>", ZonedDateTime.now(ZoneId.systemDefault()), message);
  }
}
