/*
 * Copyright (C) 2024 Google LLC.
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
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.copybara.credentials.ConstantCredentialIssuer;
import com.google.copybara.credentials.CredentialIssuer;
import com.google.copybara.credentials.TtlSecret;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class CredentialFileHandlerTest {

  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Rule public final TestName name = new TestName();

  @Test
  public void testHappyCase() throws Exception {
    CredentialIssuer password = ConstantCredentialIssuer.createConstantSecret("password", "token");
    CredentialFileHandler underTest =
        new CredentialFileHandler(
            "github.com",
            "google/copybara",
            ConstantCredentialIssuer.createConstantOpenValue("bearer-token"),
            password);
    assertThat(underTest.getPassword()).isEqualTo("token");
    assertThat(underTest.getPassword()).isEqualTo("token");

    assertThat(underTest.getPassword()).isEqualTo("token");
  }

  @Test
  public void testExpiration() throws Exception {
    CredentialIssuer password = mock(CredentialIssuer.class);
    Clock mockClock = mock(Clock.class);
    Instant now = Instant.ofEpochSecond(10000);
    when(mockClock.instant()).thenReturn(now).thenReturn(now.plusSeconds(10));
    when(password.issue())
        .thenReturn(new TtlSecret("token", "Secret", now.plusSeconds(10), mockClock))
        .thenReturn(new TtlSecret("anotherToken", "Secret", now.plusSeconds(20), mockClock));

    CredentialFileHandler underTest =
        new CredentialFileHandler(
            "example.com",
            "google/copybara",
            ConstantCredentialIssuer.createConstantOpenValue("bearer-token"),
            password);
    assertThat(underTest.getPassword()).isEqualTo("token");
    assertThat(underTest.getPassword()).isEqualTo("anotherToken");
    verify(password, times(2)).issue();
  }

  @Test
  public void testGitCredential() throws Exception {

    Clock mockClock = mock(Clock.class);
    Instant now = Instant.ofEpochSecond(10000);
    when(mockClock.instant()).thenReturn(now);
    CredentialIssuer password1 = mock(CredentialIssuer.class);

    when(password1.issue())
        .thenReturn(
            new TtlSecret(
                "v1.myhexsecret",
                "password",
                now.plusSeconds(20),
                mockClock))
        .thenReturn(new TtlSecret("yetAnotherToken", "password1", now.plusSeconds(40), mockClock));
    CredentialIssuer password2 = mock(CredentialIssuer.class);

    when(password2.issue())
        .thenReturn(new TtlSecret("anotherToken", "Secret2", now.plusSeconds(60), mockClock));

    CredentialFileHandler underTest1 =
        new CredentialFileHandler(
            "github.com",
            "google/copybara",
            ConstantCredentialIssuer.createConstantOpenValue("x-access-token"),
            password1);
    CredentialFileHandler underTest2 =
        new CredentialFileHandler(
            "github.com",
            "copybara/google",
            ConstantCredentialIssuer.createConstantOpenValue("x-access-token"),
            password2);
    Path path = Files.createTempDirectory(name.getMethodName());
    Path file = path.resolve("creds");
    Path repoPath = path.resolve("repo");
    Path workPath = path.resolve("work");
    Files.createDirectories(workPath);
    Files.createDirectories(repoPath);
    GitRepository repo =
        GitRepository.newBareRepo(
                repoPath, getGitEnv(), /* verbose= */ true, DEFAULT_TIMEOUT, /* noVerify= */ false)
            .withWorkTree(workPath)
            .init();
    underTest1.install(repo, file);
    underTest2.install(repo, file);
    assertThat(repo.credentialFill("https://github.com/google/copybara").getUsername())
        .isEqualTo("x-access-token");
    assertThat(repo.credentialFill("https://github.com/copybara/google").getUsername())
        .isEqualTo("x-access-token");
    assertThat(repo.credentialFill("https://github.com/google/copybara").getPassword_BeCareful())
        .isEqualTo("v1.myhexsecret");
    assertThat(repo.credentialFill("https://github.com/copybara/google").getPassword_BeCareful())
        .isEqualTo("anotherToken");
    when(mockClock.instant()).thenReturn(now.plusSeconds(20));

    // Multiple calls
    underTest1.writeTokenToCredFile(file);
    underTest2.writeTokenToCredFile(file);
    assertThat(repo.credentialFill("https://github.com/google/copybara").getPassword_BeCareful())
        .isEqualTo("yetAnotherToken");
    assertThat(repo.credentialFill("https://github.com/copybara/google").getPassword_BeCareful())
        .isEqualTo("anotherToken");
    assertThat(new String(Files.readAllBytes(file), UTF_8))
        .isEqualTo(
            "https://x-access-token:yetAnotherToken@github.com/google/copybara\n"
                + "https://x-access-token:anotherToken@github.com/copybara/google\n");
    assertThat(underTest1.getScrubbedFileContentForDebug(file)).doesNotContain("Token");
    assertThat(underTest1.getScrubbedFileContentForDebug(file))
        .contains("x-access-token:<scrubbed>@");
  }
}
