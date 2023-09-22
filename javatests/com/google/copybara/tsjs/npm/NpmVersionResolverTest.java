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

package com.google.copybara.tsjs.npm;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

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
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class NpmVersionResolverTest {
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
    JsonObject listResponse = new JsonObject();
    JsonObject versions = new JsonObject();
    listResponse.add("versions", versions);
    JsonObject v1 = new JsonObject();
    v1.add("version", new JsonPrimitive("1.0.0"));
    versions.add("1.0.0", v1);

    JsonObject v2 = new JsonObject();
    v2.add("version", new JsonPrimitive("2.1.0"));
    JsonObject dist = new JsonObject();
    dist.add(
        "tarball",
        new JsonPrimitive("https://registry.npmjs.com/@scope/package/-/package-2.1.0.tgz"));
    v2.add("dist", dist);
    versions.add("2.1.0", v2);

    JsonObject v3 = new JsonObject();
    v3.add("version", new JsonPrimitive("3.1.1"));
    versions.add("3.1.1", v3);

    String content = listResponse.toString();

    setUpMockTransportForSkylarkExecutor(
        ImmutableMap.of("https://registry.npmjs.com/@scope/package/", content));
  }

  private void setUpMockTransportForSkylarkExecutor(Map<String, String> urlToContent)
      throws IOException {
    when(transport.open(any(URL.class))).thenReturn(new ByteArrayInputStream(new byte[0]));
    for (Map.Entry<String, String> pair : urlToContent.entrySet()) {
      when(transport.open(new URL(pair.getKey())))
          .thenReturn(new ByteArrayInputStream(pair.getValue().getBytes(UTF_8)));
    }
    remoteFileOptions.transport = () -> transport;
    optionsBuilder.remoteFile = remoteFileOptions;
    skylark = new SkylarkTestExecutor(optionsBuilder);
  }

  @Test
  public void testNpmVersionResolver() throws Exception {
    VersionResolver versionResolver =
        skylark.eval(
            "version_resolver",
            "version_resolver = npm.npm_resolver(package_name = '@scope/package')");
    Revision rev =
        versionResolver.resolve(
            "2.1.0",
            (version) ->
                Optional.of(
                    String.format(
                        "https://registry.npmjs.com/@scope/package/-/package-%s.tgz", version)));
    assertThat(rev.getUrl())
        .isEqualTo("https://registry.npmjs.com/@scope/package/-/package-2.1.0.tgz");
  }

  @Test
  public void testFailedToAssembleUrlTemplate() throws Exception {
    VersionResolver versionResolver =
        skylark.eval(
            "version_resolver",
            "version_resolver = npm.npm_resolver(package_name = '@scope/package')");

    ValidationException exception =
        assertThrows(
            ValidationException.class,
            () -> versionResolver.resolve("0.2.0", (version) -> Optional.empty()));
    assertThat(exception.getMessage())
        .contains("Could not locate version with ref '0.2.0' as a version.");
  }
}
