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

package com.google.copybara.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.copybara.testing.git.GitTestUtil.getGitEnv;
import static com.google.copybara.util.CommandRunner.DEFAULT_TIMEOUT;

import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.util.console.testing.TestingConsole;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class FuzzyClosestVersionSelectorTest {

  private OptionsBuilder options;

  private static final String SHA1 = "232f5fc9f3bed8e1b02bca5d10b2eca444e30f95";

  private String url;
  private FuzzyClosestVersionSelector underTest = new FuzzyClosestVersionSelector();

  private GitRepository repository;
  private Path workdir;
  private Path gitDir;
  private TestingConsole console = new TestingConsole();

  @Before
  public void setup() throws Exception {
    workdir = Files.createTempDirectory("workdir");
    gitDir = Files.createTempDirectory("gitdir");
    repository = GitRepository
        .newBareRepo(gitDir, getGitEnv(), /*verbose=*/true, DEFAULT_TIMEOUT, /*noVerify=*/ false)
        .withWorkTree(workdir);
    repository.init();
    options = new OptionsBuilder();
    url = "file://" + repository.getGitDir();
    Files.write(workdir.resolve("foo.txt"), new byte[]{});
    repository.add().files("foo.txt").run();
    repository.simpleCommand("commit", "foo.txt", "-m", "message");
  }


  @Test
  public void testProcess_sha1() throws Exception {
    repository.tag("1.0.0").run();
    assertThat(underTest.selectVersion(SHA1, repository, url, console)).isEqualTo(SHA1);
  }

  @Test
  public void testProcess_tagMatch() throws Exception {
    repository.tag("1.0.0").run();
    assertThat(underTest.selectVersion("1.0.0", repository, url, console)).isEqualTo("1.0.0");
  }

  @Test
  public void testProcess_tagPrefixMatch() throws Exception {
    repository.tag("release1.0.0").withAnnotatedTag("test").run();
    assertThat(underTest.selectVersion("1.0.0", repository, url, console)).isEqualTo("release1.0.0");
  }

  @Test
  public void testProcess_tagSuffixMatch() throws Exception {
    repository.tag("1.0.0v").run();
    assertThat(underTest.selectVersion("1.0.0w", repository, url, console))
        .isEqualTo("1.0.0v");
  }

  @Test
  public void testProcess_tagVersionMatch() throws Exception {
    repository.tag("release1-0_0").withAnnotatedTag("test").run();
    assertThat(underTest.selectVersion("1.0.0", repository, url, console))
        .isEqualTo("release1-0_0");
  }

  @Test
  public void testProcess_tagRCSuffixMatch() throws Exception {
    repository.tag("foo-1.0.0RC0").run();
    assertThat(underTest.selectVersion("doo-1.0.0RC0", repository, url, console))
        .isEqualTo("foo-1.0.0RC0");
  }

  @Test
  public void testProcess_tagRCSuffixMismatch() throws Exception {
    repository.tag("foo-1.0.0RC1").run();
    assertThat(underTest.selectVersion("doo-1.0.0RC0", repository, url, console))
        .isEqualTo("doo-1.0.0RC0");
  }
}
