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
import static com.google.copybara.git.GitRepository.newBareRepo;
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.testing.FakeTicker;
import com.google.copybara.config.Migration;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitCredential.UserPassword;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.testing.profiler.RecordingListener;
import com.google.copybara.testing.profiler.RecordingListener.EventType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitMirrorTest {

  @Rule
  public final ExpectedException thrown = ExpectedException.none();

  private OptionsBuilder options;
  private SkylarkTestExecutor skylark;
  private GitRepository originRepo;
  private GitRepository destRepo;
  private Path workdir;

  @Before
  public void setup() throws Exception {
    workdir = Files.createTempDirectory("workdir");
    options =
        new OptionsBuilder()
            .setEnvironment(GitTestUtil.getGitEnv().getEnvironment())
            .setOutputRootToTmpDir()
            .setWorkdirToRealTempDir()
            .setConsole(new TestingConsole());
    originRepo = newBareRepo(Files.createTempDirectory("gitdir"), getGitEnv(),
        /*verbose=*/true, DEFAULT_TIMEOUT)
        .withWorkTree(Files.createTempDirectory("worktree"));
    originRepo.init();
    destRepo = bareRepo(Files.createTempDirectory("destinationFolder"));
    destRepo.init();

    skylark = new SkylarkTestExecutor(options);

    Files.write(originRepo.getWorkTree().resolve("test.txt"), "some content".getBytes());
    originRepo.add().files("test.txt").run();
    originRepo.simpleCommand("commit", "-m", "first file");
    originRepo.simpleCommand("branch", "other");
  }

  @Test
  public void testMirror() throws Exception {
    RecordingListener recordingCallback = new RecordingListener();
    Profiler profiler = new Profiler(new FakeTicker());
    profiler.init(ImmutableList.of(recordingCallback));
    options.general.withProfiler(profiler);

    Migration mirror = createMirrorObj();
    mirror.run(workdir, ImmutableList.of());
    String orig = originRepo.git(originRepo.getGitDir(), "show-ref").getStdout();
    String dest = destRepo.git(destRepo.getGitDir(), "show-ref").getStdout();
    assertThat(dest).isEqualTo(orig);

    recordingCallback
        .assertMatchesNext(EventType.START, "//copybara")
        .assertMatchesNext(EventType.START, "//copybara/run/default")
        .assertMatchesNext(EventType.START, "//copybara/run/default/fetch")
        .assertMatchesNext(EventType.END, "//copybara/run/default/fetch")
        .assertMatchesNext(EventType.START, "//copybara/run/default/push")
        .assertMatchesNext(EventType.END, "//copybara/run/default/push")
        .assertMatchesNext(EventType.END, "//copybara/run/default");
  }

  @Test
  public void testMirrorDryRun() throws Exception {
    Migration mirror = createMirrorObj();
    mirror.run(workdir, ImmutableList.of());
    String orig = originRepo.git(originRepo.getGitDir(), "show-ref").getStdout();
    String dest = destRepo.git(destRepo.getGitDir(), "show-ref").getStdout();
    assertThat(dest).isEqualTo(orig);

    options.general.dryRunMode = true;

    mirror = createMirrorObj();

    String destOld = destRepo.git(destRepo.getGitDir(), "show-ref").getStdout();

    Files.write(originRepo.getWorkTree().resolve("test.txt"), "updated content".getBytes());
    originRepo.add().files("test.txt").run();
    originRepo.simpleCommand("commit", "-m", "first file");

    mirror.run(workdir, ImmutableList.of());
    orig = originRepo.git(originRepo.getGitDir(), "show-ref").getStdout();
    dest = destRepo.git(destRepo.getGitDir(), "show-ref").getStdout();
    assertThat(dest).isNotEqualTo(orig);
    assertThat(dest).isEqualTo(destOld);
  }

  private Migration createMirrorObj() throws IOException, ValidationException {
    return loadMigration(String.format(""
            + "git.mirror("
            + "    name = 'default',"
            + "    origin = 'file://%s',"
            + "    destination = 'file://%s')",
        originRepo.getGitDir().toAbsolutePath(), destRepo.getGitDir().toAbsolutePath()),
        "default");
  }

  /**
   * Regression that test that if we use a local repo cache we prune when fetching.
   *
   * <p>'other' should never be present in destRepo since at the time of the migration it was
   * not present in the origin and destRepo was empty.
   */
  @Test
  public void testMirrorDeletedOrigin() throws Exception {
    GitRepository destRepo1 = bareRepo(Files.createTempDirectory("dest1")).init();

    String cfgContent = ""
        + "git.mirror("
        + "    name = 'one',"
        + "    origin = 'file://" + originRepo.getGitDir().toAbsolutePath() + "',"
        + "    destination = 'file://" + destRepo1.getGitDir().toAbsolutePath() + "',"
        + ")\n"
        + "git.mirror("
        + "    name = 'two',"
        + "    origin = 'file://" + originRepo.getGitDir().toAbsolutePath() + "',"
        + "    destination = 'file://" + destRepo.getGitDir().toAbsolutePath() + "',"
        + ")";

    loadMigration(cfgContent, "one").run(workdir, ImmutableList.of());
    originRepo.simpleCommand("branch", "-D", "other");
    Mirror mirror = (Mirror) loadMigration(cfgContent, "two");

    assertThat(mirror.getOriginDescription().get("ref")).containsExactly("refs/heads/*");
    assertThat(mirror.getDestinationDescription().get("ref")).containsExactly("refs/heads/*");

    mirror.run(workdir, ImmutableList.of());

    checkRefDoesntExist("refs/heads/other");
  }

  @Test
  public void testMirrorDescription() throws Exception {
    String cfgContent = ""
        + "git.mirror("
        + "    name = 'one',"
        + "    description = 'Do foo with bar',"
        + "    origin = 'https://example.com/foo',"
        + "    destination = 'https://example.com/bar',"
        + ")";

    assertThat(loadMigration(cfgContent, "one").getDescription())
        .isEqualTo("Do foo with bar");
  }

  private Migration loadMigration(String cfgContent, String name)
      throws IOException, ValidationException {
    return skylark.loadConfig(cfgContent).getMigration(name);
  }

  @Test
  public void testMirrorNoPrune() throws Exception {
    GitRepository destRepo1 = bareRepo(Files.createTempDirectory("dest1"));
    destRepo1.init();

    String cfg = ""
        + "git.mirror("
        + "    name = 'default',"
        + "    origin = 'file://" + originRepo.getGitDir().toAbsolutePath() + "',"
        + "    destination = 'file://" + destRepo.getGitDir().toAbsolutePath() + "',"
        + ")\n"
        + "";

    String other = originRepo.git(originRepo.getGitDir(), "show-ref", "refs/heads/other",
        "-s").getStdout();
    Migration migration = loadMigration(cfg, "default");
    migration.run(workdir, ImmutableList.of());
    originRepo.simpleCommand("branch", "-D", "other");
    migration.run(workdir, ImmutableList.of());
    assertThat(destRepo.git(destRepo.getGitDir(), "show-ref", "refs/heads/other", "-s").getStdout())
        .isEqualTo(other);
  }

  @Test
  public void testMirrorPrune() throws Exception {
    GitRepository destRepo1 = bareRepo(Files.createTempDirectory("dest1"));
    destRepo1.init();

    String cfg = ""
        + "git.mirror("
        + "    name = 'default',"
        + "    origin = 'file://" + originRepo.getGitDir().toAbsolutePath() + "',"
        + "    destination = 'file://" + destRepo.getGitDir().toAbsolutePath() + "',"
        + "    prune = True,"
        + ")\n"
        + "";

    Migration migration = loadMigration(cfg, "default");
    migration.run(workdir, ImmutableList.of());
    originRepo.simpleCommand("branch", "-D", "other");
    migration.run(workdir, ImmutableList.of());

    checkRefDoesntExist("refs/heads/other");
  }

  private GitRepository bareRepo(Path path) {
    return newBareRepo(
        path, new GitEnvironment(options.general.getEnvironment()), options.general.isVerbose(),
        DEFAULT_TIMEOUT);
  }

  @Test
  public void testMirrorCustomRefspec() throws Exception {
    String cfg = ""
        + "git.mirror("
        + "    name = 'default',"
        + "    origin = 'file://" + originRepo.getGitDir().toAbsolutePath() + "',"
        + "    destination = 'file://" + destRepo.getGitDir().toAbsolutePath() + "',"
        + "    refspecs = ["
        + "        'refs/heads/master:refs/heads/origin_master'"
        + "    ]"
        + ")";
    Migration mirror = loadMigration(cfg, "default");
    mirror.run(workdir, ImmutableList.of());
    String origMaster = originRepo.git(originRepo.getGitDir(), "show-ref", "master", "-s")
        .getStdout();
    String destMaster = destRepo.git(destRepo.getGitDir(), "show-ref", "origin_master", "-s")
        .getStdout();
    assertThat(destMaster).isEqualTo(origMaster);
    checkRefDoesntExist("refs/heads/master");
    checkRefDoesntExist("refs/heads/other");
  }

  @Test
  public void testMirrorCredentials() throws Exception {
    Path credentialsFile = Files.createTempFile("credentials", "test");
    Files.write(credentialsFile, "https://user:SECRET@somehost.com".getBytes(UTF_8));
    options.git.credentialHelperStorePath = credentialsFile.toString();

    String cfg = ""
        + "git.mirror("
        + "    name = 'default',"
        + "    origin = 'file://" + originRepo.getGitDir().toAbsolutePath() + "',"
        + "    destination = 'file://" + destRepo.getGitDir().toAbsolutePath() + "',"
        + ")\n"
        + "";

    GitRepository repository = ((Mirror) loadMigration(cfg, "default")).getLocalRepo();

    UserPassword result = repository
        .credentialFill("https://somehost.com/foo/bar");

    assertThat(result.getUsername()).isEqualTo("user");
    assertThat(result.getPassword_BeCareful()).isEqualTo("SECRET");
  }

  private void checkRefDoesntExist(String ref) throws RepoException {
    assertThat(destRepo.git(destRepo.getGitDir(), "show-ref").getStdout())
        .doesNotContain(" " + ref + "\n");
  }

  @Test
  public void testMirrorConflict() throws Exception {
    Migration mirror = prepareForConflict();
    try {
      mirror.run(workdir, ImmutableList.of());
      fail();
    } catch (RepoException e) {
      assertThat(e.getMessage()).contains("[rejected]");
    }
  }

  @Test
  public void testMirrorNoConflictIfForce() throws Exception {
    options.gitMirrorOptions.forcePush = true;
    Migration mirror = prepareForConflict();
    mirror.run(workdir, ImmutableList.of());
  }

  private Migration prepareForConflict() throws IOException, ValidationException, RepoException {
    String cfg = ""
        + "git.mirror("
        + "    name = 'default',"
        + "    origin = 'file://" + originRepo.getGitDir().toAbsolutePath() + "',"
        + "    destination = 'file://" + destRepo.getGitDir().toAbsolutePath() + "',"
        + ")";
    Path otherRepoPath = Files.createTempDirectory("other_repo");
    GitRepository other =
        GitRepository.newRepo(
                /*verbose*/ true,
                otherRepoPath,
                new GitEnvironment(options.general.getEnvironment()))
            .init();
    Files.write(other.getWorkTree().resolve("test2.txt"), "some content".getBytes());
    other.add().files("test2.txt").run();
    other.git(other.getWorkTree(), "commit", "-m", "another file");
    other.git(other.getWorkTree(), "branch", "other");
    other.push()
        .withRefspecs("file://" + destRepo.getGitDir(),
            ImmutableList.of(other.createRefSpec("+refs/*:refs/*")))
        .run();
    return loadMigration(cfg, "default");
  }
}
