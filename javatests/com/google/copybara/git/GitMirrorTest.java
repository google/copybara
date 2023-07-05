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
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
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
            .setEnvironment(new GitEnvironment(System.getenv()).getEnvironment())
            .setOutputRootToTmpDir()
            .setWorkdirToRealTempDir()
            .setConsole(console);

    options.gitDestination.committerEmail = "copybara@example.com";
    options.gitDestination.committerName = "Copy Bara";

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
    originRepo.branch("other").run();
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
        path, new GitEnvironment(getGitEnv().getEnvironment()), options.general.isVerbose(),
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
                new GitEnvironment(getGitEnv().getEnvironment()))
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
        + "   ctx.console.info('Hello, this is action1 ' + str(ctx.refs))\n"
        + "   return ctx.success()\n"
        + "\n"
        + "def a2(ctx):\n"
        + "   ctx.console.info('Hello, this is action2')\n"
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
  public void testSingleAction() throws Exception {
    String cfg = ""
        + "def a1(ctx):\n"
        + "   ctx.console.info('Hello, this is action1 ' + str(ctx.refs))\n"
        + "   return ctx.success()\n"
        + "\n"
        + "git.mirror("
        + "    name = 'default',"
        + "    origin = 'file://" + originRepo.getGitDir().toAbsolutePath() + "',"
        + "    destination = 'file://" + destRepo.getGitDir().toAbsolutePath() + "',"
        + "    action = a1"
        + ")\n"
        + "";

    Migration migration = loadMigration(cfg, "default");
    migration.run(workdir, ImmutableList.of("my_ref"));
    console.assertThat().onceInLog(MessageType.INFO,
        "Hello, this is action1 \\[\"my_ref\"\\]");
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
        + "   ctx.console.info('Hello, this is action1')\n"
        + "   return ctx.error('Something bad happened')\n"
        + "\n"
        + "def a2(ctx):\n"
        + "   ctx.console.info('Hello, this is action2')\n"
        + "   return ctx.error('Another thing bad happened')\n"
        + "\n"
        + "def a3(ctx):\n"
        + "   ctx.console.info('Hello, this is action3')\n"
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
    Migration mirror = mergeInit("FF", /* partialFetch= */ false);

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
  @TestParameters({
    "{partialFetch: true, expectedConfigMatcher: \"(remote..*.partialclonefilter blob:none((.|\\n"
        + ")*))\"}",
    "{partialFetch: false, expectedConfigMatcher: \"^$\"}"
  })
  public void testMirrorWithPartialFetch_toggleOnOff(
      boolean partialFetch, String expectedConfigMatcher)
      throws IOException, RepoException, ValidationException {
    Migration mirror = mergeInit("NO_FF", /* partialFetch= */ partialFetch);

    GitLogEntry originChange = repoChange(originRepo, "testfile", "test content", "commit");

    mirror.run(workdir, ImmutableList.of());

    GitLogEntry merge = lastChange(destRepo, primaryBranch);

    assertThat(
            options
                .git
                .cachedBareRepoForUrl(
                    "file://" + originRepo.getGitDir().toAbsolutePath().toString())
                .gitAllowNonZeroExit(
                    new byte[] {},
                    ImmutableList.of("config", "--get-regexp", "remote..*.partialclonefilter"),
                    Duration.ofMinutes(15))
                .getStdout())
        .matches(expectedConfigMatcher);
  }

  @Test
  public void testSuccessfulMergeWithoutFastForwarding()
      throws IOException, RepoException, ValidationException {
    Migration mirror = mergeInit("NO_FF", /* partialFetch= */ false);

    GitLogEntry originChange = repoChange(originRepo, "testfile", "test content", "commit");

    mirror.run(workdir, ImmutableList.of());

    GitLogEntry merge = lastChange(destRepo, primaryBranch);

    // merge has more than 1 parent from not fast forwarding
    assertThat(merge.getParents().size()).isGreaterThan(1);
    assertThat(destRepo.simpleCommand("log").getStdout()).contains("Merge branch");

    // expecting the destination repo to have a merge commit that the origin does not
    assertThat(lastChange(destRepo, primaryBranch).getCommit())
        .isNotEqualTo(lastChange(originRepo, primaryBranch).getCommit());
  }

  @Test
  @TestParameters({"{fastForwardOption: \"FF\"}", "{fastForwardOption: \"FF_ONLY\"}"})
  public void testSuccessfulMergeWithFastForwarding(String fastForwardOption)
      throws IOException, RepoException, ValidationException {
    Migration mirror = mergeInit(fastForwardOption, /* partialFetch= */ false);
    GitRevision firstCommit = lastChange(destRepo, primaryBranch).getCommit();
    GitLogEntry originChange = repoChange(originRepo, "testfile", "test content", "commit");

    mirror.run(workdir, ImmutableList.of());

    GitLogEntry merge = lastChange(destRepo, primaryBranch);

    assertThat(merge.getParents()).containsExactly(firstCommit);

    // OSS branch is updated with origin version.
    assertThat(lastChange(destRepo, "oss").getCommit()).isEqualTo(originChange.getCommit());
  }

  @Test
  public void testRebase() throws Exception {
    options.gitDestination.committerEmail = "internal_system@example.com";
    options.gitDestination.committerName = "Internal System";

    String cfg =
        ""
            + "def _rebase(ctx):\n"
            + "     ctx.destination_fetch(refspec = ['refs/heads/" + primaryBranch + "'])\n"
            + "     exist = ctx.destination_fetch(refspec = ['refs/heads/internal'])\n"
            + "     ctx.origin_fetch(refspec = ['refs/heads/" + primaryBranch + "'])\n"
            + "     if exist:\n"
            + "         result = ctx.rebase(branch = 'internal',"
            + "               upstream = '" + primaryBranch + "')\n"
            + "         if result.error:\n"
            + "             return ctx.error('Conflict rebasing change')\n"
            + "     else:\n"
            + "         ctx.create_branch('internal', starting_point = '" + primaryBranch + "')\n"
            + "     ctx.destination_push(refspec = ['+refs/heads/internal'])\n"
            + "     ctx.destination_push(refspec = ['refs/heads/" + primaryBranch + "'])\n"
            + "     return ctx.success()\n"
            + "\n"
            + "git.mirror("
            + "    name = 'default',"
            + "    origin = 'file://" + originRepo.getGitDir().toAbsolutePath() + "',"
            + "    destination = 'file://" + destRepo.getGitDir().toAbsolutePath() + "',"
            + "    refspecs = ['refs/heads/*:refs/heads/*'],"
            + "    actions = [_rebase],"
            + ")";
    Migration mirror1 = loadMigration(cfg, "default");
    mirror1.run(workdir, ImmutableList.of());
    GitLogEntry originChange = repoChange(originRepo, "some_other_file", "Content", "new change");
    GitRepository dest = destRepo.withWorkTree(Files.createTempDirectory("dest"));
    dest.forceCheckout("internal");
    GitLogEntry destChange = repoChange(dest, "some_file", "Content", "destination only");
    mirror1.run(workdir, ImmutableList.of());

    ImmutableList<GitLogEntry> log = destRepo.log("internal").run();
    assertThat(log.get(0).getBody()).contains(destChange.getBody());
    assertThat(log.get(0).getCommitter()).isEqualTo(options.gitDestination.getCommitter());
    assertThat(log.get(1).getCommit()).isEqualTo(originChange.getCommit());
  }

  @Test
  public void testReferences() throws Exception {
    String cfg =
        ""
            + "def test(ctx):\n"
            + "     ctx.origin_fetch(refspec = ['refs/heads/*:refs/heads/origin/*'])\n"
            + "     for k in ctx.references().keys():\n"
            + "         ctx.console.info('REF: ' + k)\n"
            + "     for k in ctx.references(['refs/heads/origin/fo*']).keys():\n"
            + "         ctx.console.info('REFSPEC: ' + k)\n"
            + "     return ctx.success()\n"
            + "\n"
            + "git.mirror("
            + "    name = 'default',"
            + "    origin = 'file://" + originRepo.getGitDir().toAbsolutePath() + "',"
            + "    destination = 'file://" + destRepo.getGitDir().toAbsolutePath() + "',"
            + "    actions = [test],"
            + ")";
    Migration mirror1 = loadMigration(cfg, "default");
    originRepo.branch("foo1").run();
    originRepo.branch("foo2").run();
    mirror1.run(workdir, ImmutableList.of());

    console.assertThat().onceInLog(MessageType.INFO, "REF: refs/heads/origin/" + primaryBranch);
    console.assertThat().onceInLog(MessageType.INFO, "REF: refs/heads/origin/foo1");
    console.assertThat().onceInLog(MessageType.INFO, "REF: refs/heads/origin/foo2");
    console.assertThat().onceInLog(MessageType.INFO, "REF: refs/heads/origin/other");

    console.assertThat().onceInLog(MessageType.INFO, "REFSPEC: refs/heads/origin/foo1");
    console.assertThat().onceInLog(MessageType.INFO, "REFSPEC: refs/heads/origin/foo2");
    console
        .assertThat()
        .timesInLog(0, MessageType.INFO, "REFSPEC: refs/heads/origin/" + primaryBranch);
    console
        .assertThat()
        .timesInLog(0, MessageType.INFO, "REFSPEC: refs/heads/origin/other");
  }

  @Test
  public void testCreateBranch() throws Exception {
    String cfg =
        ""
            + "def test(ctx):\n"
            + "     ctx.origin_fetch(refspec = ['refs/heads/*:refs/heads/*'])\n"
            + "     ctx.create_branch('create_head')\n"
            + "     ctx.create_branch('create_old', 'HEAD~1')\n"
            + "     for k,v in ctx.references(['refs/heads/create*']).items():\n"
            + "         ctx.console.info('REF: ' + k + ':' + v)\n"
            + "     return ctx.success()\n"
            + "\n"
            + "git.mirror("
            + "    name = 'default',"
            + "    origin = 'file://" + originRepo.getGitDir().toAbsolutePath() + "',"
            + "    destination = 'file://" + destRepo.getGitDir().toAbsolutePath() + "',"
            + "    actions = [test],"
            + ")";
    Migration mirror1 = loadMigration(cfg, "default");
    GitLogEntry one = repoChange(originRepo, "some_other_file", "one", "new change");
    GitLogEntry two = repoChange(originRepo, "some_other_file", "two", "new change");

    mirror1.run(workdir, ImmutableList.of());

    console.assertThat().onceInLog(MessageType.INFO,
        "REF: refs/heads/create_old:" + one.getCommit().getSha1());
    console.assertThat().onceInLog(MessageType.INFO,
        "REF: refs/heads/create_head:" + two.getCommit().getSha1());
  }

  @Test
  public void testDefaultMirrorWithPushOptions() throws Exception {
    // default mirror when user doesn't set actions
    final Migration mirror = createMirrorObj();
    options.git.gitPushOptions = ImmutableList.of("example_push_option");
    RepoException expectedException =
        assertThrows(RepoException.class, () -> mirror.run(workdir, ImmutableList.of()));
    assertThat(expectedException).hasMessageThat().contains("--push-option=example_push_option");
    // expected, this folder doesn't support push options
    assertThat(expectedException)
        .hasMessageThat()
        .contains("the receiving end does not support push options");
  }

  @Test
  public void testPushWithAllowedPushOptionsButUnsupportedAtDestination() throws Exception {
    String cfg =
        "def test(ctx):\n"
            + "     ctx.origin_fetch(refspec = ['refs/heads/*:refs/heads/*'])\n"
            + "     ctx.destination_push(['refs/heads/*:refs/heads/*'], push_options ="
            + " ['example_push_option'])\n"
            + "     return ctx.success()\n"
            + "\n"
            + "git.mirror(    name = 'default',    origin = 'file://"
            + originRepo.getGitDir().toAbsolutePath()
            + "',"
            + "    destination = 'file://"
            + destRepo.getGitDir().toAbsolutePath()
            + "',"
            + "    actions = [test],"
            + ")";
    Migration mirror = loadMigration(cfg, "default");
    GitLogEntry one = repoChange(originRepo, "some_other_file", "one", "new change");
    RepoException expectedException =
        assertThrows(RepoException.class, () -> mirror.run(workdir, ImmutableList.of()));
    assertThat(expectedException).hasMessageThat().contains("--push-option=example_push_option");
    // expected, this folder doesn't support push options
    assertThat(expectedException)
        .hasMessageThat()
        .contains("the receiving end does not support push options");
  }

  @Test
  public void testMirrorWithApiHandles() throws Exception {
    String cfg =
        "def test(ctx):\n"
            + "    origin_api = ctx.origin_api\n"
            + "    destination_api = ctx.destination_api\n"
            + "    if type(origin_api) != 'gerrit_api_obj':\n"
            + "        fail('this was not supposed to happen: expected gerrit_api_obj, but got ' +"
            + " type(origin_api))\n"
            + "    if type(destination_api) != 'github_api_obj':\n"
            + "        fail('this was not supposed to happen: expected github_api_obj, but got ' +"
            + " type(destination_api))\n"
            + "    return ctx.success()\n"
            + "\n"
            + "git.mirror(\n"
            + "    name = 'default',\n"
            + "    origin = 'https://copybara.googlesource.com/copybara/',\n"
            + "    destination = 'https://github.com/google/copybara',\n"
            + "    actions = [test],\n"
            + ")";
    Migration mirror = loadMigration(cfg, "default");
    mirror.run(workdir, ImmutableList.of());
  }

  @Test
  public void testMirrorWithApiHandles_noInferredApiHandle() throws Exception {
    String cfg =
        "def test(ctx):\n"
            + "    origin_api = ctx.origin_api\n"
            + "    destination_api = ctx.destination_api\n"
            + "    if type(origin_api) == 'NoneType' and type(destination_api) == 'NoneType':\n"
            + "        return ctx.success()\n"
            + "    fail('this was not supposed to happen: expected NoneType, but got ' +"
            + " type(origin_api) + ' and ' + type(destination_api))\n"
            + "\n"
            + "git.mirror(\n"
            + "    name = 'default',\n"
            + "    origin = 'not.github.or.gerrit.com',\n"
            + "    destination = 'foo.bar.baz.com',\n"
            + "    actions = [test],\n"
            + ")";
    Migration mirror = loadMigration(cfg, "default");
    mirror.run(workdir, ImmutableList.of());
  }

  @Test
  public void testCherrypick() throws Exception {
    String primaryBranch = originRepo.getPrimaryBranch();
    String cfg =
        ""
            + "def test(ctx):\n"
            + "     ctx.origin_fetch(refspec = ['refs/heads/*:refs/heads/*'])\n"
            + "     ctx.create_branch('my_branch', starting_point = 'HEAD~3')\n"
            + "     ctx.cherry_pick('my_branch', ['HEAD.." + primaryBranch + "'])\n"
            + "     ctx.destination_push(['refs/heads/*:refs/heads/*'])\n"
            + "     return ctx.success()\n"
            + "\n"
            + "git.mirror("
            + "    name = 'default',"
            + "    origin = 'file://" + originRepo.getGitDir().toAbsolutePath() + "',"
            + "    destination = 'file://" + destRepo.getGitDir().toAbsolutePath() + "',"
            + "    actions = [test],"
            + ")";
    Migration mirror1 = loadMigration(cfg, "default");
    GitLogEntry one = repoChange(originRepo, "some_other_file", "1", "one");
    GitLogEntry two = repoChange(originRepo, "some_other_file", "2", "two");
    GitLogEntry three = repoChange(originRepo, "some_other_file", "3", "three");
    GitLogEntry four = repoChange(originRepo, "some_other_file", "4", "four");

    mirror1.run(workdir, ImmutableList.of());

    ImmutableList<GitLogEntry> logCp = destRepo.log("my_branch").run();
    ImmutableList<GitLogEntry> log = destRepo.log(primaryBranch).run();

    assertThat(logCp.get(0).getBody()).containsMatch("four(.|\n)*"
        + "cherry picked from commit " + four.getCommit().getSha1());
    assertThat(logCp.get(0).getCommit().getSha1()).isNotEqualTo(four.getCommit().getSha1());

    assertThat(logCp.get(1).getBody()).containsMatch("three(.|\n)*"
        + "cherry picked from commit " + three.getCommit().getSha1());
    assertThat(logCp.get(1).getCommit().getSha1()).isNotEqualTo(three.getCommit().getSha1());

    assertThat(logCp.get(2).getBody()).containsMatch("two(.|\n)*"
        + "cherry picked from commit " + two.getCommit().getSha1());
    assertThat(logCp.get(2).getCommit().getSha1()).isNotEqualTo(two.getCommit().getSha1());

    assertThat(logCp.get(3).getBody()).containsMatch("one");
    assertThat(logCp.get(3).getCommit().getSha1()).isEqualTo(one.getCommit().getSha1());
  }

  @Test
  @TestParameters({"{fastForwardOption: \"FF\"}", "{fastForwardOption: \"FF_ONLY\"}"})
  public void testMergeConflict(String fastForwardOption) throws Exception {
    Migration mirror = mergeInit(fastForwardOption, /* partialFetch= */ false);

    GitLogEntry destChange = repoChange(destRepo, "some_file", "Hello", "destination only");
    GitLogEntry originChange = repoChange(originRepo, "some_file", "Bye", "new change");

    assertThat((assertThrows(ValidationException.class, new ThrowingRunnable() {
      @Override
      public void run() throws Throwable {
        mirror.run(workdir, ImmutableList.of());
      }
    }))).hasMessageThat().contains("Conflict merging refs/heads/" + primaryBranch);
  }

  private Migration mergeInit(String fastForwardOption, boolean partialFetch)
      throws IOException, ValidationException, RepoException {
    String partialFetchString = partialFetch ? "True" : "False";
    String cfg =
        ""
            + ("def _merger(ctx):\n"
                + "     ctx.console.info('Hello this is mirror!')\n"
                + "     branch = ctx.params['branch']\n"
                + "     oss_branch = ctx.params['oss_branch']\n"
                + "     ctx.origin_fetch("
                + String.format("partial_fetch=%s", partialFetchString)
                + ", refspec = [branch +"
                + " ':refs/heads/copybara/origin_fetch'])\n"
                + "     exist = ctx.destination_fetch("
                + String.format("partial_fetch=%s", partialFetchString)
                + ", refspec = [branch +"
                + " ':refs/heads/copybara/destination_fetch'])\n"
                + "     if exist:\n"
                + "         result = ctx.merge(branch = 'copybara/destination_fetch',           "
                + "              commits = ['refs/heads/copybara/origin_fetch'],"
                + "fast_forward = "
                + String.format("'%s'", fastForwardOption)
                + ")\n"
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
    String origPrimary =
        originRepo.git(originRepo.getGitDir(), "show-ref", primaryBranch, "-s").getStdout();
    assertThat(destRepo.git(destRepo.getGitDir(), "show-ref", primaryBranch, "-s").getStdout())
        .isEqualTo(origPrimary);
    assertThat(destRepo.git(destRepo.getGitDir(), "show-ref", "oss", "-s").getStdout())
        .isEqualTo(origPrimary);
    return mirror;
  }

  private GitLogEntry repoChange(GitRepository repo, String path, String content, String msg)
      throws IOException, RepoException {
    GitRepository withWorkdir =
        repo.getWorkTree() != null ? repo
                                   : repo.withWorkTree(Files.createTempDirectory("test"));

    GitTestUtil.writeFile(withWorkdir.getWorkTree(), path, content);
    withWorkdir.add().all().run();
    withWorkdir.simpleCommand("commit", "-m", msg);
    return lastChange(withWorkdir, "HEAD");
  }

  private GitLogEntry lastChange(GitRepository withWorkdir, String ref) throws RepoException {
    return withWorkdir.log(ref).withLimit(1).run().get(0);
  }
}
