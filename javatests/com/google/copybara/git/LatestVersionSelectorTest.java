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
import static com.google.copybara.testing.git.GitTestUtil.writeFile;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsProcessCallDestination;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.testing.TestingConsole;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(JUnit4.class)
public class LatestVersionSelectorTest {

  private static final String COMMIT_TIME = "2037-02-16T13:00:00Z";
  private String url;
  private Path remote;
  private OptionsBuilder options;
  private GitRepository repo;
  private SkylarkTestExecutor skylark;
  private String cliReference;

  @Before
  public void setup() throws Exception {
    options = new OptionsBuilder();
    TestingConsole console = new TestingConsole();
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

    url = "file://" + remote.toFile().getAbsolutePath();

    writeFile(remote, "test.txt", "some content");
    repo.add().files("test.txt").run();
    git("commit", "-m", "first file", "--date", COMMIT_TIME);
    cliReference = "foo";
  }

  @Test
  public void testVersionSelector() throws Exception {
    createTags("foo", "1.0.0", "1.1.9", "1.9.1", "1.21.1");
    checkTags("1.21.1");
  }

  @Test
  public void testVersionSelector_notActive() throws Exception {
    options.gitOrigin.noGitVersionSelector = true;
    createTags("foo", "1.0.0", "1.1.9", "1.9.1", "1.21.1");
    checkTags("foo");
  }

  @Test
  public void testVersionSelector_notActiveBecauseOfForce() throws Exception {
    options.general.setForceForTest(true);
    createTags("foo", "1.0.0", "1.1.9", "1.9.1", "1.21.1");
    checkTags("foo");
  }

  @Test
  public void testVersionSelector_forcePrefix() throws Exception {
    cliReference = "force:1.0.0";
    createTags("foo", "1.0.0", "1.1.9", "1.9.1", "1.21.1");
    checkTags("1.0.0");
  }

  @Test
  public void testVersionSelector_custom() throws Exception {
    createTags("99.99.99",
        "Foo-a-2-a",
        "Foo-b-1-b",
        "Foo-c-10-c",
        "Foo-c-10-d");
    checkTagsCustomSelector(
        "refspec_format = 'refs/tags/Foo-${s1}-${n0}-${s2}',"
            + " refspec_groups = {'s1' : '[a-c]', 'n0' : '[0-9]+', 's2' : '[a-z]+'}",
        "Foo-c-10-d",
        "refs/tags/Foo-*");
  }

  @Test
  public void testVersionSelector_branch() throws Exception {
    for (String b : ImmutableList.of("vAlpha1", "vBeta1", "vCharly10", "vCharly2")) {
      git("branch", b);
    }

    checkTagsCustomSelector(
        "refspec_format = 'refs/heads/v${s0}${n1}',"
            + " refspec_groups = {'s0' : '[a-zA-Z]+', 'n1' : '[0-9]+'}",
        "vCharly10",
        "refs/heads/v*");
  }

  @Test
  public void testVersionSelector_extraGroups() {
    ValidationException e = assertThrows(ValidationException.class, () -> checkTagsCustomSelector(
        "refspec_format = 'refs/tags/${n0}',"
            + " refspec_groups = {'n0' : '20200609', 'n1' : 'OOOPS'}",
        "20200609",
        "refs/tags/*"));
    assertThat(e)
        .hasMessageThat()
        .contains("Extra refspec_groups not used in pattern: [n1]");
  }

  @Test
  public void testVersionSelector_customDate() throws Exception {
    createTags(
        "20109999.999",
        "20100310.1",
        "20110310.1",
        "20110410.1",
        "20110411.10",
        "20110411.1");
    checkTagsCustomSelector(""
            + "refspec_format = 'refs/tags/${n0}.${n1}',"
            + "refspec_groups = {"
            + "    'n0' : '20[0-9]{2}(0[1-9]|1[012])(0[1-9]|[12][0-9]|3[01])',"
            + "    'n1' : '[0-9]{1,3}'}",
        "20110411.10", "refs/tags/*");
  }

  @Test
  public void testVersionSelector_noMatch() throws Exception {
    createTags("1.0", "1.1", "1.2");
    ValidationException e = assertThrows(ValidationException.class, () -> checkTags(null));
    assertThat(e)
        .hasMessageThat()
        .contains("didn't match any version for 'refs/tags/([0-9]+)\\.([0-9]+)\\.([0-9]+)'");
  }

  private void checkTags(String expectedResult) throws Exception {
    checkTagsCustomSelector("", expectedResult, "refs/tags/*");
  }

  private void checkTagsCustomSelector(String fields, String expectedResult, String expectedRefspec)
      throws RepoException, com.google.copybara.exception.ValidationException {

    GitOrigin origin = skylark.eval("result", "result = "
        + "git.origin(\n"
        + "    url = '" + url + "',\n"
        + "    version_selector = git.latest_version(" + fields + "),\n"
        + ")");

    GitRevision version = origin.resolve(/*reference=*/cliReference);
    assertThat(version.contextReference()).isEqualTo(expectedResult);
    assertThat(version.contextReference()).isEqualTo(expectedResult);
    assertThat(origin.describe(Glob.ALL_FILES).get("refspec")).containsExactly(expectedRefspec);
  }

  @Test
  public void testResolve() throws Exception {
    createTags("1.0.0");
    options.testingOptions.destination = new RecordsProcessCallDestination();
    options.general.setForceForTest(true);
    Path workdir = Files.createTempDirectory("workdir");
    //noinspection unchecked
    String cfg = ""
        + "core.workflow("
        + "   name = 'default',"
        + "   origin = git.origin("
        + "     url = '" + url + "',\n"
        + "     version_selector = git.latest_version(),\n"
        + "   ),"
        + "   authoring = authoring.overwrite('Foo <foo@example.com>'),"
        + "   destination = testing.destination(),"
        + ")";

    skylark.loadConfig(cfg).getMigration("default")
        .run(workdir, ImmutableList.of());

    assertThat(options.testingOptions.destination.processed).hasSize(1);
    assertThat(options.testingOptions.destination.processed.get(0).getWorkdir())
    .containsExactly("test.txt", "some content");

    writeFile(remote, "test.txt", "some content2");
    repo.add().files("test.txt").run();
    git("commit", "-m", "second change", "--date", COMMIT_TIME);

    createTags("foo", "1.1.0");
    options.general.setForceForTest(false);
    skylark.loadConfig(cfg).getMigration("default")
        .run(workdir, ImmutableList.of());

    assertThat(options.testingOptions.destination.processed.get(1).getWorkdir())
        .containsExactly("test.txt", "some content2");
  }

  private void createTags(String... repoTags) throws RepoException {
    for (String tag : repoTags) {
      git("tag", tag);
    }
  }

  private void createTestRepo(Path folder) throws Exception {
    remote = folder;
    repo =
        GitRepository.newRepo(
            /*verbose*/ true, remote, new GitEnvironment(options.general.getEnvironment()))
            .init();
  }

  private void git(String... params) throws RepoException {
    repo.git(remote, params).getStdout();
  }
}
