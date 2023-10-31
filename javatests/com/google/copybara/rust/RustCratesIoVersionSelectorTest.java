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
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.copybara.remotefile.HttpStreamFactory;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.version.VersionList;
import com.google.copybara.version.VersionSelector;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

@RunWith(JUnit4.class)
public class RustCratesIoVersionSelectorTest {
  private SkylarkTestExecutor skylark;
  private Console console;
  @Rule public final MockitoRule mocks = MockitoJUnit.rule();
  @Mock public HttpStreamFactory transport;

  @Before
  public void setup() throws Exception {
    console = new TestingConsole();
    OptionsBuilder optionsBuilder = new OptionsBuilder();
    optionsBuilder.setConsole(console);
    skylark = new SkylarkTestExecutor(optionsBuilder);
  }

  @Test
  public void testSimple() throws Exception {
    VersionSelector versionSelector =
        skylark.eval(
            "version_selector",
            "version_selector = rust.crates_io_version_selector(requirement='1.2')");
    VersionList versionList =
        new VersionList.SetVersionList(
            ImmutableSet.of("0.2.3", "1.2.3-alpha.1", "1.2.3", "1.2.4", "2.0.0"));
    assertThat(versionSelector.select(versionList, null, console)).hasValue("1.2.4");
  }

  @Test
  public void testZeroMajorVersion() throws Exception {
    VersionSelector versionSelector =
        skylark.eval(
            "version_selector",
            "version_selector = rust.crates_io_version_selector(requirement='0.5')");
    VersionList versionList =
        new VersionList.SetVersionList(ImmutableSet.of("0.2.3", "0.5.0", "0.5.9", "1.2.3"));
    assertThat(versionSelector.select(versionList, null, console)).hasValue("0.5.9");
  }

  @Test
  public void testPreReleaseRequirement() throws Exception {
    VersionSelector versionSelector =
        skylark.eval(
            "version_selector",
            "version_selector = rust.crates_io_version_selector(requirement='=1.2.3-alpha.1')");
    VersionList versionList =
        new VersionList.SetVersionList(
            ImmutableSet.of("0.2.3", "1.2.2", "1.2.3-alpha.1", "1.2.3", "1.2.4"));
    assertThat(versionSelector.select(versionList, null, console)).hasValue("1.2.3-alpha.1");
  }

  @Test
  public void testToString() throws Exception {
    VersionSelector versionSelector =
        skylark.eval(
            "version_selector",
            "version_selector = rust.crates_io_version_selector(requirement='~1.2.3')");
    assertThat(versionSelector.toString())
        .isEqualTo("rust.crates_io_version_selector(requirement = \"~1.2.3\")");
  }
}
