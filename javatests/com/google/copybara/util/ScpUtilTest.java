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

package com.google.copybara.util;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.util.ScpUtil.ScpUrl;
import com.google.testing.junit.testparameterinjector.TestParameter;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(TestParameterInjector.class)
public final class ScpUtilTest {

  @Test
  public void parseScpUrl_valid(
      @TestParameter({
            "user@host:path",
            "host:path",
            "user@host:/path",
            "host:/path",
            "user@host:path/with/slashes",
            "host:path/with/slashes",
            "user@host:/",
            "host:/"
          })
          String url) {
    Optional<ScpUrl> parsed = ScpUtil.parseScpUrl(url);
    assertThat(parsed).isPresent();
  }

  @Test
  public void parseScpUrl_invalid(
      @TestParameter({"host://path", "host", "user@host", "http://host/path"}) String url) {
    Optional<ScpUrl> parsed = ScpUtil.parseScpUrl(url);
    assertThat(parsed).isEmpty();
  }

  @Test
  public void parseScpUrl_specifics() {
    Optional<ScpUrl> parsed = ScpUtil.parseScpUrl("user@host:path");
    assertThat(parsed).isPresent();
    assertThat(parsed.get().user()).isEqualTo("user");
    assertThat(parsed.get().host()).isEqualTo("host");
    assertThat(parsed.get().path()).isEqualTo("path");

    parsed = ScpUtil.parseScpUrl("host:path");
    assertThat(parsed).isPresent();
    assertThat(parsed.get().user()).isNull();
    assertThat(parsed.get().host()).isEqualTo("host");
    assertThat(parsed.get().path()).isEqualTo("path");
  }
}
