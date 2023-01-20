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

package com.google.copybara.go;

import static com.google.common.truth.Truth.assertThat;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertThrows;
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
import com.google.copybara.version.VersionList;
import com.google.copybara.version.VersionResolver;
import com.google.testing.junit.testparameterinjector.TestParameterInjector;
import com.google.testing.junit.testparameterinjector.TestParameters;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Map;
import java.util.Optional;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(TestParameterInjector.class)
public class GoProxyVersioningTest {
  private SkylarkTestExecutor skylark;
  private Console console;
  private RemoteFileOptions options;
  private OptionsBuilder optionsBuilder;
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock public HttpStreamFactory transport;

  public GoProxyVersioningTest() {}

  private void setUpMockTransportForSkylarkExecutor(Map<String, String> urlToContent)
      throws IOException {
    for (Map.Entry<String, String> pair : urlToContent.entrySet()) {
      when(transport.open(new URL(pair.getKey())))
          .thenReturn(new ByteArrayInputStream(pair.getValue().getBytes(UTF_8)));
    }
    options.transport = () -> transport;
    optionsBuilder.remoteFile = options;
    skylark = new SkylarkTestExecutor(optionsBuilder);
  }

  @Before
  public void setup() throws Exception {
    console = new TestingConsole();
    options = new RemoteFileOptions();
    optionsBuilder = new OptionsBuilder();
    optionsBuilder.setConsole(console);
    skylark = new SkylarkTestExecutor(optionsBuilder);
  }

  @Test
  @TestParameters({"{content: \"v1.0.0\"}", "{content: \"v.1.1.0\nv1.2.0\nv1.3.0\"}"})
  public void testGoProxyVersionList_validListResponse(String content) throws Exception {
    setUpMockTransportForSkylarkExecutor(
        ImmutableMap.of("https://proxy.golang.org/github.com/google/example/@v/list", content));
    VersionList versionList =
        skylark.eval(
            "version_list",
            "version_list = go.go_proxy_version_list(module='github.com/google/example')");
    assertThat(versionList.list()).containsExactlyElementsIn(content.split("\n"));
  }

  @Test
  @TestParameters({"{content: \"v.1.1.0\nv1.2.0\nv1.3.0\", module: \"github.com/foo/Bar\"}"})
  public void testGoProxyVersionList_uppercaseModuleName(String content, String module)
      throws Exception {
    setUpMockTransportForSkylarkExecutor(
        ImmutableMap.of("https://proxy.golang.org/github.com/foo/!bar/@v/list", content));
    VersionList versionList =
        skylark.eval(
            "version_list",
            String.format("version_list = go.go_proxy_version_list(module='%s')", module));
    assertThat(versionList.list()).containsExactlyElementsIn(content.split("\n"));
  }

  @Test
  public void testGoProxyVersionList_noVersionListAnywhere() throws Exception {
    setUpMockTransportForSkylarkExecutor(
        ImmutableMap.of(
            "https://proxy.golang.org/github.com/google/example/@v/list",
            "",
            "https://proxy.golang.org/github.com/google/example/@latest",
            ""));
    VersionList versionList =
        skylark.eval(
            "version_list",
            "version_list = go.go_proxy_version_list(module='github.com/google/example')");
    ValidationException expected = assertThrows(ValidationException.class, versionList::list);
    assertThat(expected)
        .hasCauseThat()
        .hasMessageThat()
        .contains(
            "Failed to query proxy.golang.org for version list at"
                + " https://proxy.golang.org/github.com/google/example/@latest");
  }

  @Test
  @TestParameters({
    "{listVersionContent: \"\", latestVersionContent:"
        + " '{\"Version\":\"v0.0.0-20220508222113-6edffad2e616\",\"Time\":\"2022-05-08T22:21:13Z\"}'}"
  })
  public void testGoProxyVersionList_useFallBackForNoVersionListContent(
      String listVersionContent, String latestVersionContent) throws Exception {
    setUpMockTransportForSkylarkExecutor(
        ImmutableMap.of(
            "https://proxy.golang.org/github.com/google/example/@v/list",
            listVersionContent,
            "https://proxy.golang.org/github.com/google/example/@latest",
            latestVersionContent));
    VersionList versionList =
        skylark.eval(
            "version_list",
            "version_list = go.go_proxy_version_list(module='github.com/google/example')");
    assertThat(versionList.list()).containsExactly("v0.0.0-20220508222113-6edffad2e616");
  }

  @Test
  public void testGoProxyVersionList_useDotInfoData() throws Exception {
    setUpMockTransportForSkylarkExecutor(
        ImmutableMap.of(
            "https://proxy.golang.org/github.com/google/example/@v/list",
            "",
            "https://proxy.golang.org/github.com/google/example/@latest",
            "",
            "https://proxy.golang.org/github.com/google/example/@v/main.info",
            "{\"Version\":\"v0.5.9\",\"Time\":\"2022-10-02T22:41:56Z\",\"Origin\":{\"VCS\":\"git\""
                + ",\"URL\":\"https://github.com/google/example\",\"Ref\":\"refs/tags/v0.5.9\","
                + "\"Hash\":\"a97318bf6562f1ed2632c5f985db51b1ac5bdcd0\"}}"));
    VersionList versionList =
        skylark.eval(
            "version_list",
            "version_list = go.go_proxy_version_list(module='github.com/google/example',"
                + " ref='main')");
    assertThat(versionList.list()).containsExactly("v0.5.9");
  }

  @Test
  public void testGoProxyVersionResolver_loadListWithRespectToRef() throws Exception {
    setUpMockTransportForSkylarkExecutor(
        ImmutableMap.of(
            "https://proxy.golang.org/github.com/google/example/@v/list",
            "",
            "https://proxy.golang.org/github.com/google/example/@latest",
            "",
            "https://proxy.golang.org/github.com/google/example/@v/legacy_branch.info",
            "{\"Version\":\"v0.5.9\",\"Time\":\"2022-10-02T22:41:56Z\",\"Origin\":{\"VCS\":\"git\""
                + ",\"URL\":\"https://github.com/google/example\",\"Ref\":\"refs/tags/v0.5.9\","
                + "\"Hash\":\"a97318bf6562f1ed2632c5f985db51b1ac5bdcd0\"}}"));
    VersionResolver versionResolver =
        skylark.eval(
            "version_resolver",
            "version_resolver = go.go_proxy_resolver(module='github.com/google/example')");
    Revision revision =
        versionResolver.resolve(
            "legacy_branch",
            (version) ->
                Optional.of(
                    String.format(
                        "https://proxy.golang.org/github.com/google/example/@v/%s.zip", version)));
    assertThat(revision.getUrl())
        .isEqualTo("https://proxy.golang.org/github.com/google/example/@v/v0.5.9.zip");
  }
}
