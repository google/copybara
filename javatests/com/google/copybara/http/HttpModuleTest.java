/*
 * Copyright (C) 2023 Google LLC.
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

package com.google.copybara.http;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.http.HttpTransport;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.http.testing.MockHttpTester;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import java.net.URLEncoder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class HttpModuleTest {
  private SkylarkTestExecutor starlark;
  private MockHttpTester http;

  @Before
  public void setUp() {
    http = new MockHttpTester();
    OptionsBuilder optionsBuilder = new OptionsBuilder();
    optionsBuilder.http =
        new HttpOptions() {
          @Override
          public HttpTransport getTransport() {
            return http.getTransport();
          }
        };
    starlark = new SkylarkTestExecutor(optionsBuilder);
  }

  @Test
  public void testUrlEncode() throws ValidationException {
    String[] testCases = {"https://google.com?query=seach", "info@google.com", "key=value"};
    for (String testCase : testCases) {
      String res = starlark.eval("res", "res = http.url_encode(\"" + testCase + "\")");
      String encoded = URLEncoder.encode(testCase, UTF_8);
      assertThat(res).isEqualTo(encoded);
    }
  }
}
