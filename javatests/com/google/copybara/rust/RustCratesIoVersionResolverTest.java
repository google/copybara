/*
 * Copyright (C) 2023 Google Inc.
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

package com.google.copybara.rust;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.remotefile.HttpStreamFactory;
import com.google.copybara.remotefile.RemoteFileOptions;
import com.google.copybara.revision.Revision;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.version.VersionResolver;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class RustCratesIoVersionResolverTest {
  private SkylarkTestExecutor skylark;
  private Console console;
  private RemoteFileOptions remoteFileOptions;
  private OptionsBuilder optionsBuilder;
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock public HttpStreamFactory transport;

  @Before
  public void setup() throws Exception {
    remoteFileOptions = new RemoteFileOptions();
    console = new TestingConsole();
    optionsBuilder = new OptionsBuilder();
    optionsBuilder.setConsole(console);
    skylark = new SkylarkTestExecutor(optionsBuilder);
    setupMockTransport();
  }

  public void setupMockTransport() throws Exception {
    JsonObject v1 = new JsonObject();
    v1.add("name", new JsonPrimitive("example"));
    v1.add("vers", new JsonPrimitive("0.2.0"));
    String content =
        ImmutableList.of(v1).stream().map(JsonElement::toString).collect(Collectors.joining("\n"));

    setUpMockTransportForSkylarkExecutor(
        ImmutableMap.of(
            "https://raw.githubusercontent.com/rust-lang/crates.io-index/master/ex/am/example",
            content));
  }

  private void setUpMockTransportForSkylarkExecutor(Map<String, String> urlToContent)
      throws IOException {
    for (Map.Entry<String, String> pair : urlToContent.entrySet()) {
      when(transport.open(new URL(pair.getKey())))
          .thenReturn(new ByteArrayInputStream(pair.getValue().getBytes(UTF_8)));
    }
    remoteFileOptions.transport = () -> transport;
    optionsBuilder.remoteFile = remoteFileOptions;
    skylark = new SkylarkTestExecutor(optionsBuilder);
  }

  @Test
  public void testRustCrateIoVersionResolver() throws Exception {
    VersionResolver versionResolver =
        skylark.eval(
            "version_resolver",
            "version_resolver = rust.crates_io_version_resolver(crate='example')");
    Revision rev =
        versionResolver.resolve(
            "0.2.0",
            (version) ->
                Optional.of(
                    String.format("https://crates.io/api/v1/crates/example/%s/download", version)));
    assertThat(rev.getUrl()).isEqualTo("https://crates.io/api/v1/crates/example/0.2.0/download");
  }

  @Test
  public void testFailedToAsembleUrlTemplate() throws Exception {
    VersionResolver versionResolver =
        skylark.eval(
            "version_resolver",
            "version_resolver = rust.crates_io_version_resolver(crate='example')");

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> versionResolver.resolve("0.2.0", (version) -> Optional.empty()));
    assertThat(exception.getMessage())
        .contains("Failed to assemble url template with provided assembly strategy.");
    assertThat(exception.getMessage())
        .contains("Provided ref = '0.2.0' and resolved version = '0.2.0'");
  }
}
