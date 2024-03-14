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
import com.google.common.collect.Iterables;
import com.google.copybara.exception.RepoException;
import com.google.copybara.remotefile.HttpStreamFactory;
import com.google.copybara.remotefile.RemoteFileOptions;
import com.google.copybara.rust.RustRegistryVersionObject.Deps;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.version.VersionList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
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
public class RustCratesIoVersionListTest {
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
  public void testRustCrateIoVersionList_validListResponse() throws Exception {
    JsonObject v1 = new JsonObject();
    v1.add("name", new JsonPrimitive("example"));
    v1.add("vers", new JsonPrimitive("0.1.0"));
    JsonObject v2 = new JsonObject();
    v2.add("name", new JsonPrimitive("example"));
    v2.add("vers", new JsonPrimitive("0.2.0"));
    JsonObject v3 = new JsonObject();
    v3.add("name", new JsonPrimitive("example"));
    v3.add("vers", new JsonPrimitive("0.3.0"));
    String content =
        ImmutableList.of(v1, v2, v3).stream()
            .map(JsonElement::toString)
            .collect(Collectors.joining("\n"));

    setUpMockTransportForSkylarkExecutor(
        ImmutableMap.of("https://index.crates.io/ex/am/example", content));
    VersionList versionList =
        skylark.eval("version_list", "version_list = rust.crates_io_version_list(crate='example')");
    assertThat(versionList.list())
        .containsExactlyElementsIn(ImmutableList.of("0.1.0", "0.2.0", "0.3.0"));
  }

  @Test
  public void testRustCrateIoVersionList_ignorePreReleaseVersions() throws Exception {
    JsonObject v1 = new JsonObject();
    v1.add("name", new JsonPrimitive("example"));
    v1.add("vers", new JsonPrimitive("0.1.0"));
    JsonObject v2 = new JsonObject();
    v2.add("name", new JsonPrimitive("example"));
    v2.add("vers", new JsonPrimitive("0.1.1-alpha"));
    JsonObject v3 = new JsonObject();
    v3.add("name", new JsonPrimitive("example"));
    v3.add("vers", new JsonPrimitive("0.1.1-beta.1"));
    JsonObject v4 = new JsonObject();
    v4.add("name", new JsonPrimitive("example"));
    v4.add("vers", new JsonPrimitive("0.1.2"));
    String content =
        ImmutableList.of(v1, v2, v3, v4).stream()
            .map(JsonElement::toString)
            .collect(Collectors.joining("\n"));

    setUpMockTransportForSkylarkExecutor(
        ImmutableMap.of("https://index.crates.io/ex/am/example", content));
    VersionList versionList =
        skylark.eval(
            "version_list",
            "version_list = rust.crates_io_version_list(crate='example',"
                + " match_pre_release_versions = False)");
    assertThat(versionList.list()).containsExactlyElementsIn(ImmutableList.of("0.1.0", "0.1.2"));
  }

  @Test
  public void testRustCrateIoVersionList_allowPreReleaseVersions() throws Exception {
    JsonObject v1 = new JsonObject();
    v1.add("name", new JsonPrimitive("example"));
    v1.add("vers", new JsonPrimitive("0.1.0"));
    JsonObject v2 = new JsonObject();
    v2.add("name", new JsonPrimitive("example"));
    v2.add("vers", new JsonPrimitive("0.1.1-alpha"));
    JsonObject v3 = new JsonObject();
    v3.add("name", new JsonPrimitive("example"));
    v3.add("vers", new JsonPrimitive("0.1.1-beta.1"));
    JsonObject v4 = new JsonObject();
    v4.add("name", new JsonPrimitive("example"));
    v4.add("vers", new JsonPrimitive("0.1.2"));
    String content =
        ImmutableList.of(v1, v2, v3, v4).stream()
            .map(JsonElement::toString)
            .collect(Collectors.joining("\n"));

    setUpMockTransportForSkylarkExecutor(
        ImmutableMap.of("https://index.crates.io/ex/am/example", content));
    VersionList versionList =
        skylark.eval(
            "version_list",
            "version_list = rust.crates_io_version_list(crate='example',"
                + " match_pre_release_versions = True)");
    assertThat(versionList.list())
        .containsExactlyElementsIn(
            ImmutableList.of("0.1.0", "0.1.1-alpha", "0.1.1-beta.1", "0.1.2"));
  }

  @Test
  public void testRustCrateIoVersionList_shortCrateName() throws Exception {
    JsonObject crate1 = new JsonObject();
    crate1.add("name", new JsonPrimitive("a"));
    crate1.add("vers", new JsonPrimitive("1.2.3"));
    JsonObject crate2 = new JsonObject();
    crate2.add("name", new JsonPrimitive("abc"));
    crate2.add("vers", new JsonPrimitive("4.5.6"));

    setUpMockTransportForSkylarkExecutor(
        ImmutableMap.of(
            "https://index.crates.io/1/a",
            crate1.toString(),
            "https://index.crates.io/3/a/abc",
            crate2.toString()));
    VersionList versionListCrate1 =
        skylark.eval("version_list", "version_list = rust.crates_io_version_list(crate='a')");
    VersionList versionListCrate2 =
        skylark.eval("version_list", "version_list = rust.crates_io_version_list(crate='abc')");
    assertThat(versionListCrate1.list()).containsExactlyElementsIn(ImmutableList.of("1.2.3"));
    assertThat(versionListCrate2.list()).containsExactlyElementsIn(ImmutableList.of("4.5.6"));
  }

  @Test
  public void testRustCrateIoVersionList_badJson() throws Exception {
    setUpMockTransportForSkylarkExecutor(
        ImmutableMap.of("https://index.crates.io/ex/am/example", "foo"));
    VersionList versionList =
        skylark.eval("version_list", "version_list = rust.crates_io_version_list(crate='example')");
    RepoException expected = assertThrows(RepoException.class, versionList::list);
    assertThat(expected)
        .hasMessageThat()
        .contains(
            "Failed to query crates.io-index for version list at"
                + " https://index.crates.io/ex/am/example");
  }

  @Test
  public void testRustCrateIoVersionList_withDeps() throws Exception {
    JsonObject crate = new JsonObject();
    JsonObject dep = new JsonObject();
    crate.add("name", new JsonPrimitive("example"));
    crate.add("vers", new JsonPrimitive("1.2.3"));
    dep.add("name", new JsonPrimitive("example_dep"));
    JsonArray deps = new JsonArray();
    deps.add(dep);
    crate.add("deps", deps);

    setUpMockTransportForSkylarkExecutor(
        ImmutableMap.of("https://index.crates.io/ex/am/example", crate.toString()));
    VersionList versionList =
        skylark.eval("version_list", "version_list = rust.crates_io_version_list(crate='example')");
    RustRegistryVersionObject versionObject =
        Iterables.getOnlyElement(((RustCratesIoVersionList) versionList).getVersionList());
    assertThat(versionObject.getName()).isEqualTo("example");
    assertThat(versionObject.getVers()).isEqualTo("1.2.3");
    List<Deps> resultDeps = versionObject.getDeps();
    assertThat(resultDeps).hasSize(1);
    assertThat(Iterables.getOnlyElement(resultDeps).getName()).isEqualTo("example_dep");
  }

  @Test
  public void testRustCrateIoVersionList_withFeatures() throws Exception {
    JsonObject crate = new JsonObject();
    crate.add("name", new JsonPrimitive("example"));
    crate.add("vers", new JsonPrimitive("1.2.3"));
    JsonObject features = new JsonObject();
    JsonArray additionalFeatures = new JsonArray();
    additionalFeatures.add("feature2");
    features.add("example-feature", additionalFeatures);
    crate.add("features", features);

    setUpMockTransportForSkylarkExecutor(
        ImmutableMap.of("https://index.crates.io/ex/am/example", crate.toString()));
    VersionList versionList =
        skylark.eval("version_list", "version_list = rust.crates_io_version_list(crate='example')");
    RustRegistryVersionObject versionObject =
        Iterables.getOnlyElement(((RustCratesIoVersionList) versionList).getVersionList());
    assertThat(versionObject.getName()).isEqualTo("example");
    assertThat(versionObject.getVers()).isEqualTo("1.2.3");
    Map<String, List<String>> resultFeatures = versionObject.getFeatures();
    assertThat(resultFeatures).containsKey("example-feature");
    assertThat(resultFeatures.get("example-feature").get(0)).isEqualTo("feature2");
  }

  @Test
  public void testRustCrateIoVersionList_cratesWithUpperCaseName() throws Exception {
    JsonObject v1 = new JsonObject();
    v1.add("name", new JsonPrimitive("Example"));
    v1.add("vers", new JsonPrimitive("0.1.0"));
    String content =
        ImmutableList.of(v1).stream().map(JsonElement::toString).collect(Collectors.joining("\n"));

    setUpMockTransportForSkylarkExecutor(
        ImmutableMap.of("https://index.crates.io/ex/am/example", content));
    // The crate name starts with an uppercase letter, but when we look up in the index, we use the
    // lowercase spelling.
    VersionList versionList =
        skylark.eval("version_list", "version_list = rust.crates_io_version_list(crate='Example')");
    assertThat(versionList.list()).containsExactlyElementsIn(ImmutableList.of("0.1.0"));
  }
}
