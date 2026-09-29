/*
 * Copyright (C) 2026 Google LLC.
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

package com.google.copybara.jj;

import static com.google.common.truth.Truth.assertThat;

import com.beust.jcommander.JCommander;
import com.google.copybara.exception.RepoException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.util.console.testing.TestingConsole;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class JjOptionsTest {

  @Rule public final TemporaryFolder tempFolder = new TemporaryFolder();

  private OptionsBuilder options;
  private JjOptions jjOptions;
  private TestingConsole console;
  private Path workdir;

  @Before
  public void setUp() throws Exception {
    workdir = tempFolder.newFolder("workdir").toPath();
    console = new TestingConsole();
    options = new OptionsBuilder().setHomeDir(tempFolder.newFolder("home").toString());
    jjOptions = new JjOptions(options.general);
    jjOptions.jjBin = JjRepositoryTest.setUpJjBin();
  }

  @Test
  public void jjBinFlag_parsedByJCommander_updatesJjBin() {
    JCommander jCommander = new JCommander(jjOptions);

    jCommander.parse("--jj-bin=/custom/path/to/jj");

    assertThat(jjOptions.jjBin).isEqualTo("/custom/path/to/jj");
  }

  @Test
  public void committerFlags_parsedByJCommander_updateCommitterFields() {
    JCommander jCommander = new JCommander(jjOptions);

    jCommander.parse(
        "--jj-committer-name=Custom Name", "--jj-committer-email=custom@example.com");

    assertThat(jjOptions.committerName).isEqualTo("Custom Name");
    assertThat(jjOptions.committerEmail).isEqualTo("custom@example.com");
  }

  @Test
  public void createRepo_withCommitterOptions_configuresRepoUserAndEmail() throws Exception {
    jjOptions.committerName = "Bara Kopi";
    jjOptions.committerEmail = "bara@kopi.io";

    JjRepository repo = jjOptions.createRepo(workdir, console);

    assertThat(getRepoConfig(repo, "user.name")).isEqualTo("Bara Kopi");
    assertThat(getRepoConfig(repo, "user.email")).isEqualTo("bara@kopi.io");
    assertThat(getRepoScopedConfig(repo)).contains("user.name");
    assertThat(getRepoScopedConfig(repo)).contains("user.email");
  }

  @Test
  public void createRepo_withoutCommitterOptions_doesNotSetRepoUserAndEmail() throws Exception {
    JjRepository repo = jjOptions.createRepo(workdir, console);

    String repoConfig = getRepoScopedConfig(repo);
    assertThat(repoConfig).doesNotContain("user.name");
    assertThat(repoConfig).doesNotContain("user.email");
  }

  @Test
  public void createRepo_initializesNonColocatedRepo() throws Exception {
    JjRepository repo = jjOptions.createRepo(workdir, console);

    assertThat(repo.getRepoDir().toString()).isEqualTo(workdir.resolve("jj_repo").toString());
    assertThat(Files.isDirectory(repo.getRepoDir().resolve(".jj/repo"))).isTrue();
    assertThat(Files.exists(repo.getRepoDir().resolve(".git"))).isFalse();
  }

  private static String getRepoConfig(JjRepository repo, String name) throws RepoException {
    return repo.simpleCommand("--no-pager", "--color=never", "config", "get", name)
        .getStdout()
        .trim();
  }

  private static String getRepoScopedConfig(JjRepository repo) throws RepoException {
    return repo.simpleCommand("--no-pager", "--color=never", "config", "list", "--repo")
        .getStdout();
  }
}
