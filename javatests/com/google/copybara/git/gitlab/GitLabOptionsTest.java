/*
 * Copyright (C) 2025 Google LLC
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

package com.google.copybara.git.gitlab;

import static com.google.common.truth.Truth.assertThat;

import com.google.api.client.http.HttpTransport;
import com.google.copybara.credentials.ConstantCredentialIssuer;
import com.google.copybara.credentials.CredentialModule.UsernamePasswordIssuer;
import com.google.copybara.git.CredentialFileHandler;
import com.google.copybara.git.gitlab.api.GitLabApi;
import com.google.copybara.git.gitlab.api.GitLabApiTransport;
import com.google.copybara.http.auth.BearerInterceptor;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.util.console.testing.TestingConsole;
import java.net.URI;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mockito;

@RunWith(JUnit4.class)
public class GitLabOptionsTest {
  private TestingConsole console;
  private final OptionsBuilder optionsBuilder = new OptionsBuilder();
  private GitLabOptions underTest;

  @Before
  public void setup() {
    console = new TestingConsole();
    underTest = new GitLabOptions();
  }

  @Test
  public void testHttpTransportSupplier_notNull() {
    assertThat(underTest.getHttpTransportSupplier()).isNotNull();
    assertThat(underTest.getHttpTransportSupplier().get()).isNotNull();
  }

  @Test
  public void testHttpTransportSupplier_memoization() {
    HttpTransport transport1 = underTest.getHttpTransportSupplier().get();
    HttpTransport transport2 = underTest.getHttpTransportSupplier().get();

    assertThat(transport1).isNotNull();
    assertThat(transport1).isSameInstanceAs(transport2);
  }

  @Test
  public void testGetGitLabApi_notNull() {
    GitLabApiTransport gitLabApiTransport = Mockito.mock(GitLabApiTransport.class);

    assertThat(underTest.getGitLabApi(gitLabApiTransport)).isNotNull();
  }

  @Test
  public void setGitLabApiSupplier() {
    GitLabApi gitLabApi = Mockito.mock(GitLabApi.class);

    underTest.setGitLabApiSupplier(unused -> gitLabApi);

    assertThat(underTest.getGitLabApi(Mockito.mock(GitLabApiTransport.class)))
        .isSameInstanceAs(gitLabApi);
  }

  @Test
  public void setCredentialFileHandlerSupplier() {
    CredentialFileHandler credentialFileHandler = Mockito.mock(CredentialFileHandler.class);
    underTest.setCredentialFileHandlerSupplier((unused, ignored) -> credentialFileHandler);

    assertThat(
            underTest.getCredentialFileHandler(
                URI.create("https://capy.com/bara"), Mockito.mock(UsernamePasswordIssuer.class)))
        .isSameInstanceAs(credentialFileHandler);
  }

  @Test
  public void testGetApiTransport() {
    GitLabApiTransport result =
        GitLabOptions.getApiTransport(
            "http://gitlab.com/google/copybara",
            underTest.getHttpTransportSupplier().get(),
            console,
            Optional.of(
                new BearerInterceptor(
                    ConstantCredentialIssuer.createConstantSecret("test", "test"))));

    assertThat(result).isNotNull();
  }
}
