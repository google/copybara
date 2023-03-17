/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.version;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import com.google.common.collect.ImmutableSet;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.testing.TestingConsole;
import java.util.Optional;
import javax.annotation.Nullable;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class LatestVersionSelectorTest {

  private OptionsBuilder options;
  private TestingConsole console;
  private SkylarkTestExecutor skylark;

  @Before
  public void setup() throws Exception {
    options = new OptionsBuilder();
    console = new TestingConsole();
    options = new OptionsBuilder()
        .setConsole(console)
        .setOutputRootToTmpDir();

    skylark = new SkylarkTestExecutor(options);
  }

  @Test
  public void simpleTest() throws ValidationException, RepoException {
    runTest("format = 'test'", ImmutableSet.of("a", "b", "test"), null, "test");
  }

  @Test
  public void semanticVersion() throws ValidationException, RepoException {
    runSemanticVersioning(ImmutableSet.of("v1.0.0", "v1.1.0", "v1.1.1", "v1.0.99"),
        null, "v1.1.1");
  }

  @Test
  public void semanticVersion_force() throws ValidationException, RepoException {
    options.general.setVersionSelectorUseCliRefForTest(false);
    ImmutableSet<String> versions = ImmutableSet.of("v1.0.0", "v1.1.0", "v1.1.1", "v1.0.99");
    runSemanticVersioning(versions, "test", "v1.1.1");

    options.general.setForceForTest(true);
    runSemanticVersioning(versions, "test", "test");
  }

  private void runSemanticVersioning(ImmutableSet<String> versionList, String requestedRef,
      String expected)
      throws ValidationException, RepoException {
    runTest(""
            + "format = 'v${n0}.${n1}.${n2}',"
            + "regex_groups = {"
            + "  'n0': '[0-9]+',"
            + "  'n1': '[0-9]+',"
            + "  'n2': '[0-9]+',"
            + "}",
        versionList,
        requestedRef, expected);
  }

  @Test
  public void versionMix() throws ValidationException, RepoException {
    runTest(""
            + "  format = 'Foo-${s1}-${n0}-${s2}',"
            + "  regex_groups = {"
            + "    's1' : '[a-c]',"
            + "    'n0' : '[0-9]+',"
            + "    's2' : '[a-z]+'"
            + "  }",
        ImmutableSet.of("99.99.99",
            "Foo-a-2-a",
            "Foo-b-1-b",
            "Foo-c-10-c",
            "Foo-c-10-d"),
        null, "Foo-c-10-d");
  }

  @Test
  public void testInvalidNumber() {
    assertThat(assertThrows(ValidationException.class, () -> runTest(""
            + "  format = '${n0}',"
            + "  regex_groups = {"
            + "    'n0' : 'v12',"
            + "  }",
        ImmutableSet.of("v12"), null, "not used")))
        .hasMessageThat()
        .contains("Invalid number");
  }

  @Test
  public void extraGroups() {
    ValidationException e = assertThrows(ValidationException.class, () -> runTest(
        "format = 'refs/tags/${n0}',"
            + " regex_groups = {'n0' : '20200609', 'n1' : 'OOOPS'}",
        ImmutableSet.of(), null,
        "not used"));
    assertThat(e)
        .hasMessageThat()
        .contains("Extra regex_groups not used in pattern: [n1]");
  }

  @Test
  public void testVersionSelector_customDate() throws Exception {
    runTest(""
            + "format = '${n0}.${n1}',"
            + "regex_groups = {"
            + "    'n0' : '20[0-9]{2}(0[1-9]|1[012])(0[1-9]|[12][0-9]|3[01])',"
            + "    'n1' : '[0-9]{1,3}'}",
        ImmutableSet.of(
            "20109999.999",
            "20100310.1",
            "20110310.1",
            "20110410.1",
            "20110411.10",
            "20110411.1"),
        null,
        "20110411.10");
  }

  @Test
  public void testVersionSelector_optionalGroups() throws Exception {
    runTest(
        ""
            + "format = '${n0}.${n1}${n2}',"
            + "regex_groups = {"
            + "    'n0' : '[0-9]+',"
            + "    'n1' : '[0-9]+', 'n2' : '(.[0-9]+)?'}",
        ImmutableSet.of("1.9", "1.1.1", "1.9.11", "1.5.3", "1.9.2", "1.9.12", "2.15."),
        null,
        "1.9.12");
  }

  private void runTest(String arguments, ImmutableSet<String> versionList,
      String requestedRef, @Nullable String expected) throws ValidationException, RepoException {
    VersionSelector v = skylark.eval("v", "v = core.latest_version(" + arguments + ")");
    Optional<String> selected = v.select(() -> versionList,
        requestedRef, console
    );
    assertThat(selected.orElse(null)).isEqualTo(expected);
  }
}
