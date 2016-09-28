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
import static org.junit.Assert.fail;

import com.google.copybara.Migration;
import com.google.copybara.RepoException;
import com.google.copybara.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
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
  private TestingConsole console;
  private SkylarkTestExecutor skylark;
  private GitRepository originRepo;
  private GitRepository destRepo;
  private Path workdir;

  @Before
  public void setup() throws Exception {
    workdir = Files.createTempDirectory("workdir");
    options = new OptionsBuilder();
    options.setWorkdirToRealTempDir();
    originRepo = GitRepository.initScratchRepo(/*verbose=*/true,
        options.general.getEnvironment());
    destRepo = GitRepository.bareRepo(Files.createTempDirectory("destinationFolder"),
        options.general.getEnvironment(), options.general.isVerbose());

    Path reposDir = Files.createTempDirectory("repos_repo");
    options.git.repoStorage = reposDir.toString();

    destRepo.initGitDir();
    console = new TestingConsole();
    options.setConsole(console);

    skylark = new SkylarkTestExecutor(options, GitModule.class);

    Files.write(originRepo.getWorkTree().resolve("test.txt"), "some content".getBytes());
    originRepo.git(originRepo.getWorkTree(), "add", "test.txt");
    originRepo.git(originRepo.getWorkTree(), "commit", "-m", "first file");
    originRepo.git(originRepo.getWorkTree(), "branch", "other");
  }

  @Test
  public void testMirror() throws Exception {
    Migration mirror = skylark.loadConfig(""
        + "core.project('foo')\n"
        + "git.mirror("
        + "    name = 'default',"
        + "    origin = 'file://" + originRepo.getGitDir().toAbsolutePath() + "',"
        + "    destination = 'file://" + destRepo.getGitDir().toAbsolutePath() + "',"
        + ")").getActiveMigration();
    mirror.run(workdir, /*sourceRef=*/null);
    String orig = originRepo.git(originRepo.getGitDir(), "show-ref").getStdout();
    String dest = destRepo.git(destRepo.getGitDir(), "show-ref").getStdout();
    assertThat(dest).isEqualTo(orig);
  }

  @Test
  public void testMirrorCustomRefspec() throws Exception {
    Migration mirror = skylark.loadConfig(""
        + "core.project('foo')\n"
        + "git.mirror("
        + "    name = 'default',"
        + "    origin = 'file://" + originRepo.getGitDir().toAbsolutePath() + "',"
        + "    destination = 'file://" + destRepo.getGitDir().toAbsolutePath() + "',"
        + "    refspecs = ["
        + "        'refs/heads/master:refs/heads/origin_master'"
        + "    ]"
        + ")").getActiveMigration();
    mirror.run(workdir, /*sourceRef=*/null);
    String origMaster = originRepo.git(originRepo.getGitDir(), "show-ref", "master", "-s")
        .getStdout();
    String destMaster = destRepo.git(destRepo.getGitDir(), "show-ref", "origin_master", "-s")
        .getStdout();
    assertThat(destMaster).isEqualTo(origMaster);
    checkRefDoesntExist("refs/heads/master");
    checkRefDoesntExist("refs/heads/other");
  }

  private void checkRefDoesntExist(String ref) throws RepoException {
    assertThat(destRepo.git(destRepo.getGitDir(), "show-ref").getStdout())
        .doesNotContain(" " + ref + "\n");
  }

  @Test
  public void testMirrorConflict() throws Exception {
    Migration mirror = prepareForConflict();
    try {
      mirror.run(workdir, /*sourceRef=*/null);
      fail();
    } catch (RepoException e) {
      assertThat(e.getMessage()).contains("[rejected]");
    }
  }

  @Test
  public void testMirrorNoConflictIfForce() throws Exception {
    options.gitMirrorOptions.forcePush = true;
    Migration mirror = prepareForConflict();
    mirror.run(workdir, /*sourceRef=*/null);
  }

  private Migration prepareForConflict() throws IOException, ValidationException, RepoException {
    Migration mirror = skylark.loadConfig(""
        + "core.project('foo')\n"
        + "git.mirror("
        + "    name = 'default',"
        + "    origin = 'file://" + originRepo.getGitDir().toAbsolutePath() + "',"
        + "    destination = 'file://" + destRepo.getGitDir().toAbsolutePath() + "',"
        + ")").getActiveMigration();
    GitRepository other = GitRepository.initScratchRepo(/*verbose=*/true,
        options.general.getEnvironment());
    Files.write(other.getWorkTree().resolve("test2.txt"), "some content".getBytes());
    other.git(other.getWorkTree(), "add", "test2.txt");
    other.git(other.getWorkTree(), "commit", "-m", "another file");
    other.git(other.getWorkTree(), "branch", "other");
    other.git(other.getWorkTree(), "push", "file://" + destRepo.getGitDir(), "+refs/*:refs/*");
    return mirror;
  }
}
