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

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.testing.TestingConsole;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GithubTarballTest {
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
    options.transport = () -> httpTransport;
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
        "sha256 = remotefiles.github_tarball("
            + "project = 'google/copybara',"
            + "revision='674ac754f91e64a0efb8087e59a176484bd534d1').sha256()");
    assertThat(sha).isEqualTo("0bcd56de6e8c48b84cc1584b6ec169f122f0d9df088b1f47476ac2e11a4f9a4d");
  }

  @Test
  public void downloadTarballAndGetContent() throws Exception {
    expectedRequest = "https://github.com/google/copybara/archive/"
        + "674ac754f91e64a0efb8087e59a176484bd534d1.tar.gz";
    responseContent = "Let's pretend this is a tarball.";

    String content = skylark.eval("contents",
        "contents = remotefiles.github_tarball("
            + "project = 'google/copybara',"
            + "revision='674ac754f91e64a0efb8087e59a176484bd534d1').contents()");
    assertThat(content).isEqualTo("Let's pretend this is a tarball.");
  }
}
