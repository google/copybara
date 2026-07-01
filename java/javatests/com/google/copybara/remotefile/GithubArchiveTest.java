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

package com.google.copybara.remotefile;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import java.time.Duration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GithubArchiveTest {
  private OptionsBuilder options;

  private MockHttpTransport httpTransport;
  private String expectedRequest = "";
  private String responseContent = "";
  private SkylarkTestExecutor skylark;

  @Before
  public void setup() throws IOException {
    httpTransport =
        new MockHttpTransport() {
          @Override
          public LowLevelHttpRequest buildRequest(String method, String url) {
            String requestString = method + " " + url;
            MockLowLevelHttpRequest request = new MockLowLevelHttpRequest();
            MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
            request.setResponse(response);
            response.setStatusCode(200);
            response.setContent(responseContent);
            if (!url.equals(expectedRequest)) {
              response.setStatusCode(404);
              response.setContent(
                  String.format("UNEXPECTED REQUEST (Returning 404) REQUEST: %s, expected: %s",
                      requestString, expectedRequest));
            }
            return request;
          }
        };
    RemoteFileOptions options = new RemoteFileOptions();
    options.transport = () -> new GclientHttpStreamFactory(httpTransport, Duration.ofSeconds(20));
    Console console = new TestingConsole();
    OptionsBuilder optionsBuilder = new OptionsBuilder().setConsole(console);
    optionsBuilder.remoteFile = options;
    skylark = new SkylarkTestExecutor(optionsBuilder);
  }

  @Test
  public void downloadTarballAndGetSha256() throws Exception {
    expectedRequest = "https://github.com/google/copybara/archive/"
        + "674ac754f91e64a0efb8087e59a176484bd534d1.tar.gz";
    responseContent = "Let's pretend this is a tarball.";
    String sha = skylark.eval("sha256",
        "sha256 = remotefiles.github_archive("
            + "project = 'google/copybara',"
            + "revision='674ac754f91e64a0efb8087e59a176484bd534d1').sha256()");
    assertThat(sha).isEqualTo("0bcd56de6e8c48b84cc1584b6ec169f122f0d9df088b1f47476ac2e11a4f9a4d");
  }

  @Test
  public void downloadZipAndGetSha256() throws Exception {
    expectedRequest = "https://github.com/google/copybara/archive/"
        + "674ac754f91e64a0efb8087e59a176484bd534d1.zip";
    responseContent = "Let's pretend this is a zip.";
    String sha = skylark.eval("sha256",
        "sha256 = remotefiles.github_archive("
            + "project = 'google/copybara',"
            + "revision='674ac754f91e64a0efb8087e59a176484bd534d1',"
            + "type='ZIP').sha256()");
    assertThat(sha).isEqualTo("047bbcf6e39c5a8fda2ae70238e74d0b0b81f94a7d907b27ebd32a6e61d6ea09");
  }

  @Test
  public void badFileType() throws Exception {
    skylark.evalFails(
        "remotefiles.github_archive("
            + "project = 'google/copybara',"
            + "revision='674ac754f91e64a0efb8087e59a176484bd534d1',"
            + "type='FOO')",
        "Unsupported archive type: 'FOO'. Supported values: \\[TARBALL, ZIP\\]");
  }

  @Test
  public void repoExceptionOnDownloadFailure() throws Exception {
    httpTransport =
        new MockHttpTransport() {
          @Override
          public LowLevelHttpRequest buildRequest(String method, String url) {
             MockLowLevelHttpRequest request = new MockLowLevelHttpRequest() {
                public LowLevelHttpResponse execute() throws IOException {
                  throw new IOException("OH NOES!");
                }
             };
            return request;
          }
        };
    RemoteFileOptions options = new RemoteFileOptions();
    options.transport = () -> new GclientHttpStreamFactory(httpTransport, Duration.ofSeconds(20));
    Console console = new TestingConsole();
    OptionsBuilder optionsBuilder = new OptionsBuilder().setConsole(console);
    optionsBuilder.remoteFile = options;
    skylark = new SkylarkTestExecutor(optionsBuilder);
    ValidationException e = assertThrows(ValidationException.class, () -> skylark.eval("sha256",
        "sha256 = remotefiles.github_archive("
            + "project = 'google/copybara',"
            + "revision='674ac754f91e64a0efb8087e59a176484bd534d1').sha256()"));
    assertThat(e).hasCauseThat().hasCauseThat().hasCauseThat().isInstanceOf(RepoException.class);
  }
}
