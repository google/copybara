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

import com.google.common.collect.Iterables;
import com.google.common.testing.TestLogHandler;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitCredential.UserPassword;
import com.google.copybara.testing.git.GitTestUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitCredentialTest {

  private Path repoGitDir;
  private GitCredential credential;
  private Path credentialsFile;
  private GitRepository repo;

  @Before
  public void setup() throws Exception {
    repoGitDir = Files.createTempDirectory("test_repo");
    credentialsFile = Files.createTempFile("credentials", "test");
    repo = newBareRepo(
        repoGitDir, getGitEnv(), /*verbose=*/true, DEFAULT_TIMEOUT, /*noVerify=*/ false)
        .init()
        .withCredentialHelper("store --file=" + credentialsFile);

    credential = new GitCredential("git", Duration.ofSeconds(5), GitTestUtil.getGitEnv());
  }

  @Test
  public void testSuccess() throws Exception {
    Files.write(credentialsFile, "https://user:SECRET@somehost.com".getBytes(UTF_8));

    TestLogHandler handler = new TestLogHandler();
    Logger.getGlobal().getParent().addHandler(handler);
    UserPassword result;
    try {
      result = credential.fill(repoGitDir, "https://somehost.com/foo/bar");
    } finally {
      Logger.getGlobal().getParent().removeHandler(handler);
    }

    assertThat(result.getUsername()).isEqualTo("user");
    assertThat(result.getPassword_BeCareful()).isEqualTo("SECRET");
    assertThat(result.toString()).doesNotContain("SECRET");
    assertThat(Iterables.transform(handler.getStoredLogRecords(), LogRecord::getMessage))
        .doesNotContain("SECRET");
  }

  @Test
  public void testSeveralPathsSuccess() throws Exception {
    Files.write(credentialsFile, ("https://user:SECRET@somehost.com/path1\n"
        + "https://user:TOPSECRET@somehost.com/path2").getBytes(UTF_8));
    repo.git(repo.getGitDir(), "config", "--local", "credential.useHttpPath", "true");

    assertThat(credential.fill(repoGitDir, "https://somehost.com/path1")
        .getPassword_BeCareful()).isEqualTo("SECRET");
    assertThat(credential.fill(repoGitDir, "https://somehost.com/path2")
        .getPassword_BeCareful()).isEqualTo("TOPSECRET");
  }

  @Test
  public void testNotFound() throws Exception  {
    ValidationException expected =
        assertThrows(
            ValidationException.class,
            () -> credential.fill(repoGitDir, "https://somehost.com/foo/bar"));
    assertThat(expected)
        .hasMessageThat()
        .contains("Interactive prompting of passwords for git is disabled");
  }

  @Test
  public void testNoProtocol() throws Exception  {
    ValidationException expected =
        assertThrows(
            ValidationException.class, () -> credential.fill(repoGitDir, "somehost.com/foo/bar"));
    assertThat(expected).hasMessageThat().contains("Cannot find the protocol");
  }
}
