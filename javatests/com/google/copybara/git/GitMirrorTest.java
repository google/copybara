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
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.common.testing.FakeTicker;
import com.google.copybara.config.Migration;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitCredential.UserPassword;
import com.google.copybara.git.GitRepository.GitLogEntry;
import com.google.copybara.profiler.Profiler;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.testing.profiler.RecordingListener;
import com.google.copybara.testing.profiler.RecordingListener.EventType;
import com.google.copybara.util.console.Message.MessageType;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitMirrorTest {

  public static final String NATIVE_MIRROR_IN_STARLARK_FUNC = "def _native_mirror(ctx):\n"
      + "     ctx.console.info('Hello this is mirror!')\n"
      + "     for o,d in ctx.params['refspec'].items():\n"
      + "         ctx.origin_fetch(refspec = [o])\n"
      + "         ctx.destination_push(refspec = [o + ':' + d], prune = ctx.params['prune'])\n"
      + "         return ctx.success()\n"
      + "\n"
      + "def native_mirror(refspec = {'refs/heads/*': 'refs/heads/*'}, prune = False):\n"
      + "    return core.action(impl = _native_mirror,"
      + "         params = {'refspec': refspec, 'prune' : prune})\n"
      + "\n";
  private OptionsBuilder options;
  private SkylarkTestExecutor skylark;
  private GitRepository originRepo;
  private GitRepository destRepo;
  private Path workdir;
  private String primaryBranch;
  private TestingConsole console;

  @Before
  public void setup() throws Exception {
    workdir = Files.createTempDirectory("workdir");
    console = new TestingConsole();
    options =
        new OptionsBuilder()
            .setEnvironment(GitTestUtil.getGitEnv().getEnvironment())
            .setOutputRootToTmpDir()
            .setWorkdirToRealTempDir()
            .setConsole(console);
    originRepo = newBareRepo(Files.createTempDirectory("gitdir"), getGitEnv(),
        /*verbose=*/true, DEFAULT_TIMEOUT, /*noVerify=*/ false)
        .withWorkTree(Files.createTempDirectory("worktree"));
    originRepo.init();
    destRepo = bareRepo(Files.createTempDirectory("destinationFolder"));
    destRepo.init();

    skylark = new SkylarkTestExecutor(options);

    Files.write(originRepo.getWorkTree().resolve("test.txt"), "some content".getBytes(UTF_8));
    originRepo.add().files("test.txt").run();
    originRepo.simpleCommand("commit", "-m", "first file");
    originRepo.simpleCommand("branch", "other");
    primaryBranch = originRepo.getPrimaryBranch();
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

    Files.write(originRepo.getWorkTree().resolve("test.txt"), "updated content".getBytes(UTF_8));
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
        DEFAULT_TIMEOUT, /*noVerify=*/ false);
  }

  @Test
  public void testMirrorCustomRefspec() throws Exception {
    String cfg = ""
        + "git.mirror("
        + "    name = 'default',"
        + "    origin = 'file://" + originRepo.getGitDir().toAbsolutePath() + "',"
        + "    destination = 'file://" + destRepo.getGitDir().toAbsolutePath() + "',"
        + "    refspecs = ["
        + String.format("        'refs/heads/%s:refs/heads/origin_primary'", primaryBranch)
        + "    ]"
        + ")";
    Migration mirror = loadMigration(cfg, "default");
    mirror.run(workdir, ImmutableList.of());
    String origPrimary = originRepo.git(originRepo.getGitDir(), "show-ref", primaryBranch, "-s")
        .getStdout();
    String dest = destRepo.git(destRepo.getGitDir(), "show-ref", "origin_primary", "-s")
        .getStdout();
    assertThat(dest).isEqualTo(origPrimary);
    checkRefDoesntExist("refs/heads/" + primaryBranch);
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
    ValidationException e =
        assertThrows(ValidationException.class, () -> mirror.run(workdir, ImmutableList.of()));
    assertThat(e).hasMessageThat().matches(".*Failed to push to .*"
        + "because local/origin history is behind destination.*");
  }

  @Test
  public void testMirrorNoConflictIfGitMirrorForce() throws Exception {
    options.gitMirrorOptions.forcePush = true;
    Migration mirror = prepareForConflict();
    mirror.run(workdir, ImmutableList.of());
  }

  @Test
  public void testMirrorNoConflictIfForce() throws Exception {
    options.setForce(true);
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
    Files.write(other.getWorkTree().resolve("test2.txt"), "some content".getBytes(UTF_8));
    other.add().files("test2.txt").run();
    other.git(other.getWorkTree(), "commit", "-m", "another file");
    other.git(other.getWorkTree(), "branch", "other");
    other.push()
        .withRefspecs("file://" + destRepo.getGitDir(),
            ImmutableList.of(other.createRefSpec("+refs/*:refs/*")))
        .run();
    return loadMigration(cfg, "default");
  }

  @Test
  public void testInvalidMigrationName() {
    skylark.evalFails(
        ""
            + "git.mirror(\n"
            + "    name = 'foo| bad;name',\n"
            + "    origin = 'file://"
            + originRepo.getGitDir().toAbsolutePath()
            + "',"
            + "    destination = 'file://"
            + destRepo.getGitDir().toAbsolutePath()
            + "',"
            + ")\n",
        ".*Migration name 'foo[|] bad;name' doesn't conform to expected pattern.*");
  }

  @Test
  public void testActionsCode() throws Exception {
    String cfg = ""
        + "def a1(ctx):\n"
        + "   ctx.console.info(\'Hello, this is action1 \' + str(ctx.refs))\n"
        + "   return ctx.success()\n"
        + "\n"
        + "def a2(ctx):\n"
        + "   ctx.console.info(\'Hello, this is action2\')\n"
        + "   return ctx.success()\n"
        + "\n"
        + "git.mirror("
        + "    name = 'default',"
        + "    origin = 'file://" + originRepo.getGitDir().toAbsolutePath() + "',"
        + "    destination = 'file://" + destRepo.getGitDir().toAbsolutePath() + "',"
        + "    actions = [a1, a2]"
        + ")\n"
        + "";

    Migration migration = loadMigration(cfg, "default");
    migration.run(workdir, ImmutableList.of("my_ref"));
    console.assertThat().onceInLog(MessageType.INFO,
        "Hello, this is action1 \\[\"my_ref\"\\]");
    console.assertThat().onceInLog(MessageType.INFO, "Hello, this is action2");
  }

  @Test
  public void testActionFailure() throws Exception {
    ValidationException ve = checkActionFailure();
    assertThat(ve).hasMessageThat().contains("Something bad happened");
    assertThat(ve).hasMessageThat().doesNotContain("Another thing bad happened");
    console.assertThat().onceInLog(MessageType.INFO, "Hello, this is action1");
    console.assertThat().timesInLog(0, MessageType.INFO, "Hello, this is action2");
    console.assertThat().timesInLog(0, MessageType.INFO, "Hello, this is action3");
  }

  @Test
  public void testActionFailureWithForce() throws Exception {
    options.setForce(true);
    ValidationException ve = checkActionFailure();
    assertThat(ve).hasMessageThat().contains("Something bad happened");
    assertThat(ve).hasMessageThat().contains("Another thing bad happened");
    console.assertThat().onceInLog(MessageType.INFO, "Hello, this is action1");
    console.assertThat().onceInLog(MessageType.INFO, "Hello, this is action2");
    console.assertThat().onceInLog(MessageType.INFO, "Hello, this is action3");
  }

  private ValidationException checkActionFailure() throws IOException, ValidationException {
    String cfg = ""
        + "def a1(ctx):\n"
        + "   ctx.console.info(\'Hello, this is action1\')\n"
        + "   return ctx.error('Something bad happened')\n"
        + "\n"
        + "def a2(ctx):\n"
        + "   ctx.console.info(\'Hello, this is action2\')\n"
        + "   return ctx.error('Another thing bad happened')\n"
        + "\n"
        + "def a3(ctx):\n"
        + "   ctx.console.info(\'Hello, this is action3\')\n"
        + "   return ctx.success()\n"
        + "\n"
        + "git.mirror("
        + "    name = 'default',"
        + "    origin = 'file://" + originRepo.getGitDir().toAbsolutePath() + "',"
        + "    destination = 'file://" + destRepo.getGitDir().toAbsolutePath() + "',"
        + "    actions = [a1, a2, a3]"
        + ")\n"
        + "";

    Migration migration = loadMigration(cfg, "default");
    return assertThrows(ValidationException.class,
        () -> migration.run(workdir, ImmutableList.of()));
  }

  /** Starlark version of our native git.mirror implementation */
  @Test
  public void testDefaultMirrorInStarlark() throws Exception {
    String cfg = ""
        + NATIVE_MIRROR_IN_STARLARK_FUNC
        + "git.mirror("
        + "    name = 'default',"
        + "    origin = 'file://" + originRepo.getGitDir().toAbsolutePath() + "',"
        + "    destination = 'file://" + destRepo.getGitDir().toAbsolutePath() + "',"
        + "    refspecs = ["
        + String.format("       'refs/heads/%s:refs/heads/origin_primary'", primaryBranch)
        + "    ],"
        + "    actions = [native_mirror(refspec = {"
        + String.format("       'refs/heads/%s':'refs/heads/origin_primary'", primaryBranch)
        + "})],"
        + ")";
    Migration mirror = loadMigration(cfg, "default");
    mirror.run(workdir, ImmutableList.of());
    String origPrimary = originRepo.git(originRepo.getGitDir(), "show-ref", primaryBranch, "-s")
        .getStdout();
    String dest = destRepo.git(destRepo.getGitDir(), "show-ref", "origin_primary", "-s")
        .getStdout();
    assertThat(dest).isEqualTo(origPrimary);
    checkRefDoesntExist("refs/heads/" + primaryBranch);
    checkRefDoesntExist("refs/heads/other");
  }

  @Test
  public void testDefaultMirrorInStarlark_invalid_origin() throws Exception {
    String cfg = ""
        + NATIVE_MIRROR_IN_STARLARK_FUNC
        + "git.mirror("
        + "    name = 'default',"
        + "    origin = 'file://" + originRepo.getGitDir().toAbsolutePath() + "',"
        + "    destination = 'file://" + destRepo.getGitDir().toAbsolutePath() + "',"
        + "    refspecs = ["
        + String.format("       'refs/heads/%s:refs/heads/origin_primary'", primaryBranch)
        + "    ],"
        + "    actions = [native_mirror(refspec = {"
        + "       'refs/heads/INVALID':'refs/heads/origin_primary'"
        + "})],"
        + ")";
    Migration mirror = loadMigration(cfg, "default");
    ValidationException ve = assertThrows(ValidationException.class,
        () -> mirror.run(workdir, ImmutableList.of()));
    assertThat(ve).hasMessageThat().contains(
        "Action tried to fetch from origin one or more refspec not covered by git.mirror");
  }

  @Test
  public void testDefaultMirrorInStarlark_invalid_destination() throws Exception {
    String cfg = ""
        + NATIVE_MIRROR_IN_STARLARK_FUNC
        + "git.mirror("
        + "    name = 'default',"
        + "    origin = 'file://" + originRepo.getGitDir().toAbsolutePath() + "',"
        + "    destination = 'file://" + destRepo.getGitDir().toAbsolutePath() + "',"
        + "    refspecs = ["
        + String.format("       'refs/heads/%s:refs/heads/origin_primary'", primaryBranch)
        + "    ],"
        + "    actions = [native_mirror(refspec = {"
        + String.format("       'refs/heads/%s':'refs/heads/INVALID'", primaryBranch)
        + "})],"
        + ")";
    Migration mirror = loadMigration(cfg, "default");
    ValidationException ve = assertThrows(ValidationException.class,
        () -> mirror.run(workdir, ImmutableList.of()));
    assertThat(ve).hasMessageThat().contains(
        "Action tried to push to destination one or more refspec not covered by git.mirror");
  }

  @Test
  public void testMerge() throws Exception {
    Migration mirror = mergeInit();

    GitLogEntry destChange = repoChange(destRepo, "some_file", "Content", "destination only");
    GitLogEntry originChange = repoChange(originRepo, "some_other_file", "Content", "new change");

    mirror.run(workdir, ImmutableList.of());

    GitLogEntry merge = lastChange(destRepo, primaryBranch);

    // It is a merge
    assertThat(merge.getParents())
        .containsExactly(destChange.getCommit(), originChange.getCommit());

    // OSS branch is updated with origin version.
    assertThat(lastChange(destRepo, "oss").getCommit()).isEqualTo(originChange.getCommit());

  }

  @Test
  public void testMergeConflict() throws Exception {
    Migration mirror = mergeInit();

    GitLogEntry destChange = repoChange(destRepo, "some_file", "Hello", "destination only");
    GitLogEntry originChange = repoChange(originRepo, "some_file", "Bye", "new change");

    assertThat((assertThrows(ValidationException.class, new ThrowingRunnable() {
      @Override
      public void run() throws Throwable {
        mirror.run(workdir, ImmutableList.of());
      }
    }))).hasMessageThat().contains("Conflict merging refs/heads/" + primaryBranch);
  }

  private Migration mergeInit() throws IOException, ValidationException, RepoException {
    String cfg =
        ""
            + ("def _merger(ctx):\n"
                + "     ctx.console.info('Hello this is mirror!')\n"
                + "     branch = ctx.params['branch']\n"
                + "     oss_branch = ctx.params['oss_branch']\n"
                + "     ctx.origin_fetch(refspec = [branch +"
                + " ':refs/heads/copybara/origin_fetch'])\n"
                + "     exist = ctx.destination_fetch(refspec = [branch +"
                + " ':refs/heads/copybara/destination_fetch'])\n"
                + "     if exist:\n"
                + "         result = ctx.merge(branch = 'copybara/destination_fetch',           "
                + "              commits = ['refs/heads/copybara/origin_fetch'])\n"
                + "         if result.error:\n"
                + "             return ctx.error('Conflict merging ' + branch + ' into"
                + " destination: ' + result.error_msg)\n"
                + "         ctx.destination_push(refspec ="
                + " ['refs/heads/copybara/destination_fetch:' + branch])\n"
                + "     else:\n"
                + "         ctx.destination_push(refspec = ['refs/heads/copybara/origin_fetch:'"
                + " + branch])\n"
                + "     if oss_branch:\n"
                + "         ctx.destination_push(refspec = ['refs/heads/copybara/origin_fetch:'"
                + " + oss_branch])\n"
                + "     return ctx.success()\n"
                + "\n"
                + "def merger(branch, oss_branch = None):\n"
                + "    return core.action(impl = _merger,         params = {'branch': branch,"
                + " 'oss_branch' : oss_branch})\n"
                + "\n")
            + "git.mirror("
            + "    name = 'default',"
            + "    origin = 'file://"
            + originRepo.getGitDir().toAbsolutePath()
            + "',"
            + "    destination = 'file://"
            + destRepo.getGitDir().toAbsolutePath()
            + "',"
            + "    refspecs = ["
            + String.format(
                ""
                    + "       'refs/heads/%s:refs/heads/%s',"
                    + "       'refs/heads/%s:refs/heads/oss'",
                primaryBranch, primaryBranch, primaryBranch)
            + "    ],"
            + "    actions = [merger("
            + String.format("'refs/heads/%s'", primaryBranch)
            + ", oss_branch = 'refs/heads/oss')],"
            + ")";
    Migration mirror = loadMigration(cfg, "default");
    mirror.run(workdir, ImmutableList.of());
    String origPrimary = originRepo.git(originRepo.getGitDir(), "show-ref", primaryBranch, "-s")
        .getStdout();
    assertThat(destRepo.git(destRepo.getGitDir(), "show-ref", primaryBranch, "-s")
        .getStdout()).isEqualTo(origPrimary);
    assertThat(destRepo.git(destRepo.getGitDir(), "show-ref", "oss", "-s")
        .getStdout()).isEqualTo(origPrimary);
    return mirror;
  }

  private GitLogEntry repoChange(GitRepository repo, String path, String content, String msg)
      throws IOException, RepoException {
    GitRepository withWorkdir = repo.withWorkTree(Files.createTempDirectory("test"));

    GitTestUtil.writeFile(withWorkdir.getWorkTree(), path, content);
    withWorkdir.add().all().run();
    withWorkdir.simpleCommand("commit", "-m", msg);
    return lastChange(withWorkdir, "HEAD");
  }

  private GitLogEntry lastChange(GitRepository withWorkdir, String ref) throws RepoException {
    return withWorkdir.log(ref).withLimit(1).run().get(0);
  }
}
