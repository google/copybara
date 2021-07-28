/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.copybara;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.git.GitTestUtil.writeFile;

import com.google.common.collect.ImmutableList;
import com.google.copybara.config.Config;
import com.google.copybara.config.ConfigValidator;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitEnvironment;
import com.google.copybara.git.GitRepository;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.RecordsProcessCallDestination;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.testing.TestingEventMonitor;
import com.google.copybara.testing.git.GitTestUtil;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.StarlarkMode;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class MigrateCmdTest {

  private static final String COMMIT_TIME = "2037-02-16T13:00:00Z";

  private SkylarkTestExecutor skylark;
  private TestingConsole console;

  private OptionsBuilder optionsBuilder;
  private TestingEventMonitor eventMonitor;
  private Path temp;
  private Path remote;
  private GitRepository repo;
  private String url;
  private String primaryBranch;
  private Path outPut;

  @Before
  public void setUp() throws Exception {
    console = new TestingConsole();
    temp = Files.createTempDirectory("temp");
    optionsBuilder = new OptionsBuilder();
    optionsBuilder.setConsole(console).setOutputRootToTmpDir();
    optionsBuilder.setForce(true);
    Path userHomeForTest = Files.createTempDirectory("home");
    optionsBuilder.setEnvironment(GitTestUtil.getGitEnv().getEnvironment());
    optionsBuilder.setHomeDir(userHomeForTest.toString());
    eventMonitor = new TestingEventMonitor();
    optionsBuilder.general.enableEventMonitor("just testing", eventMonitor);
    outPut = optionsBuilder.general.getOutputRoot();
    optionsBuilder.general.starlarkMode = StarlarkMode.STRICT.name();
    remote = Files.createTempDirectory("remote");
    repo =
        GitRepository.newRepo(
            /*verbose*/ true, remote, new GitEnvironment(optionsBuilder.general.getEnvironment()))
            .init();
    primaryBranch = repo.getPrimaryBranch();
    Files.createDirectories(remote.resolve("include"));
    Files.write(remote.resolve("include/fileA.txt"), new byte[0]);
    git(remote, "add", "include/fileA.txt");
    git(remote, "commit", "-m", "not include");
    optionsBuilder.setLastRevision(repo.parseRef("HEAD"));
    git(remote, "checkout", primaryBranch);

    Files.write(remote.resolve("include/mainline-file.txt"), new byte[0]);
    git(remote, "add", "include/mainline-file.txt");
    git(remote, "commit", "-m", "message_a!");

    optionsBuilder.general.dryRunMode = true;
    url = "file://" + remote.toFile().getAbsolutePath();

    writeFile(remote, "test.txt", "some content");
    writeFile(remote, "testA.txt", "some content");
    repo.add().files("test.txt", "testA.txt").run();
    git(remote, "commit", "-m", "first file", "--date", COMMIT_TIME);

    optionsBuilder.setForce(true);
    RecordsProcessCallDestination destination = new RecordsProcessCallDestination();
    optionsBuilder.testingOptions.destination = destination;
    skylark = new SkylarkTestExecutor(optionsBuilder);
  }
  @Test
  public void updateGitCacheRepoPrefixForPartialFetchOfOrigin() throws Exception {
    getMigrateCmd(getConfigInfo(/*partialFetch=*/"True"))
        .run(new CommandEnv(temp,
            optionsBuilder.build(),
            ImmutableList.of(temp.resolve("copy.bara.sky").toString(), "default")));

    assertThat(
            Files.exists(
                FileUtil.resolveDirInCache(
                    "copy.bara.sky:default" + url, outPut.resolve("cache").resolve("git_repos"))))
        .isTrue();
  }

  @Test
  public void checkGitCacheRepoNameForNonPartialFetch() throws Exception {
    getMigrateCmd(getConfigInfo(/*partialFetch=*/"False"))
        .run(new CommandEnv(temp,
            optionsBuilder.build(),
            ImmutableList.of(temp.resolve("copy.bara.sky").toString(), "default")));

    assertThat(
            Files.exists(
                FileUtil.resolveDirInCache(url, outPut.resolve("cache").resolve("git_repos"))))
        .isTrue();
  }

  private String git(Path dir, String... params) throws RepoException {
    return repo.git(dir, params).getStdout();
  }

  private String getConfigInfo(String partialFetch) {
    return
        "core.workflow(\n"
            + "    name = 'default',\n"
            + "    origin = git.origin(\n"
            + "         url = '" + url + "',\n"
            + "         partial_fetch = " + partialFetch + ",\n"
            + "         ref = '" + primaryBranch + "',\n"
            + "    ),\n"
            + "    origin_files = glob(['include/mainline-file.txt']),\n"
            + "    destination = testing.destination(),\n"
            + "    mode = 'ITERATIVE',\n"
            + "    authoring = authoring.pass_thru('example <example@example.com>'),\n"
            + ")";

  }

  private MigrateCmd getMigrateCmd(String configInfo) {
    ModuleSet moduleSet = skylark.createModuleSet();
    return
        new MigrateCmd(
            new ConfigValidator() {},
            migration -> {},
            (configPath, sourceRef) ->
                new ConfigLoader(
                    moduleSet,
                    skylark.createConfigFile("copy.bara.sky", configInfo),
                    optionsBuilder.general.getStarlarkMode()) {
                  @Override
                  protected Config doLoadForRevision(Console console, Revision revision)
                      throws ValidationException {
                    try {
                      return skylark.loadConfig(configPath);
                    } catch (IOException e) {
                      throw new AssertionError("Should not fail", e);
                    }
                  }
                },
            moduleSet);
  }
}
