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
import com.google.copybara.git.gitlab.api.GitLabApiTransport;
import com.google.copybara.http.auth.BearerInterceptor;
import com.google.copybara.util.console.testing.TestingConsole;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitLabOptionsTest {
  private TestingConsole console;

  @Before
  public void setup() {
    console = new TestingConsole();
  }

  @Test
  public void testHttpTransportSupplier_notNull() {
    GitLabOptions underTest = new GitLabOptions();

    assertThat(underTest.getHttpTransportSupplier()).isNotNull();
    assertThat(underTest.getHttpTransportSupplier().get()).isNotNull();
  }

  @Test
  public void testHttpTransportSupplier_memoization() {
    GitLabOptions underTest = new GitLabOptions();

    HttpTransport transport1 = underTest.getHttpTransportSupplier().get();
    HttpTransport transport2 = underTest.getHttpTransportSupplier().get();

    assertThat(transport1).isNotNull();
    assertThat(transport1).isSameInstanceAs(transport2);
  }

  @Test
  public void testGetApiTransport() {
    GitLabOptions gitLabOptions = new GitLabOptions();

    GitLabApiTransport result =
        GitLabOptions.getApiTransport(
            "http://gitlab.com/google/copybara",
            gitLabOptions.getHttpTransportSupplier().get(),
            console,
            new BearerInterceptor(ConstantCredentialIssuer.createConstantSecret("test", "test")));

    assertThat(result).isNotNull();
  }
}
