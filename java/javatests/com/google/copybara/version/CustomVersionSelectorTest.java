/*
 * Copyright (C) 2025 Google LLC
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
import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import com.google.copybara.util.console.testing.TestingConsole;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class CustomVersionSelectorTest {
  private SkylarkTestExecutor starlarkExecutor;

  private final OptionsBuilder optionsBuilder = new OptionsBuilder();

  private final TestingConsole testingConsole = new TestingConsole();

  @Before
  public void setup() throws Exception {
    starlarkExecutor = new SkylarkTestExecutor(optionsBuilder);
  }

  @Test
  public void select_invalidComparatorDefinition_throwsException() throws Exception {
    ValidationException e =
        assertThrows(
            ValidationException.class,
            () ->
                starlarkExecutor.eval(
                    "selector",
                    """
                    def _custom_version_selector(a, b):
                        return -5

                    selector = core.custom_version_selector(comparator = _custom_version_selector)
                    """));

    assertThat(e)
        .hasMessageThat()
        .contains("The comparator must take two strings arguments named 'left' and 'right'");
  }

  @Test
  public void select_emptyVersionList_returnsEmpty() throws Exception {
    VersionSelector selector =
        starlarkExecutor.eval(
            "selector",
            """
            def _custom_version_selector(left, right):
                left_components = [int(x) for x in left.split(".")]
                right_components = [int(x) for x in right.split(".")]
                for x, y in zip(left_components, right_components):
                    if x < y:
                        return -1
                    elif x > y:
                        return 1
                    else:
                      continue
                return 0

            selector = core.custom_version_selector(comparator = _custom_version_selector)
            """);
    VersionList versionList = new VersionList.SetVersionList(ImmutableSet.of());
    Optional<String> result = selector.select(versionList, "requestedRef", testingConsole);

    assertThat(result).isEmpty();
  }

  @Test
  public void select_noVersionsMatchRegex_returnsEmpty() throws Exception {
    VersionSelector selector =
        starlarkExecutor.eval(
            "selector",
            """
            def _custom_version_selector(left, right):
                return 0

            selector = core.custom_version_selector(
                comparator = _custom_version_selector,
                regex_filter = r'[^\\s\\S]'
            )
            """);
    VersionList versionList = new VersionList.SetVersionList(ImmutableSet.of("alpha", "beta"));

    Optional<String> result = selector.select(versionList, "requestedRef", testingConsole);

    assertThat(result).isEmpty();
  }

  @Test
  public void select_simpleSemVer_returnsLatest() throws Exception {
    VersionSelector selector =
        starlarkExecutor.eval(
            "selector",
            """
            def _custom_version_selector(left, right):
                left_components = [int(x) for x in left[1:].split(".")]
                right_components = [int(x) for x in right[1:].split(".")]
                for x, y in zip(left_components, right_components):
                    if x < y:
                        return -1
                    elif x > y:
                        return 1
                    else:
                      continue
                return 0

            selector = core.custom_version_selector(_custom_version_selector)
            """);
    VersionList versionList =
        new VersionList.SetVersionList(ImmutableSet.of("v1.5.5", "v1.9.2", "v1.35.1", "v1.19.3"));

    Optional<String> result = selector.select(versionList, "requestedRef", testingConsole);

    assertThat(result).hasValue("v1.35.1");
  }

  @Test
  public void select_semVerWithPreVersions_returnsLatest() throws Exception {
    VersionSelector selector =
        starlarkExecutor.eval(
            "selector",
            """
            def _compare_pre_versions(left, right):
              left_components = left.split('.')
              right_components = right.split('.')

              for i in range(min(len(left_components), len(right_components))):
                if i >= len(left_components):
                  return 1
                if i >= len(right_components):
                  return -1
                if left_components[i].isdigit() and right_components[i].isdigit():
                  if int(left_components[i]) < int(right_components[i]):
                    return -1
                  elif int(left_components[i]) > int(right_components[i]):
                    return 1
                  else:
                    continue
                if left_components[i] < right_components[i]:
                  return -1
                elif left_components[i] > right_components[i]:
                  return 1
              return 0

            def _compare_base_versions(left, right):
              left_components = [int(x) for x in left.split(".")]
              right_components = [int(x) for x in right.split(".")]
              for x, y in zip(left_components, right_components):
                  if int(x) < int(y):
                      return -1
                  elif int(x) > int(y):
                      return 1
                  else:
                    continue
              return 0

            def _custom_version_selector(left, right):
              # remove the 'v' prefix
              left = left[left.find('v') + 1:]
              right = right[right.find('v') + 1:]

              left_parts  = left.split('-')
              right_parts = right.split('-')

              core.console.info("left parts: " + str(left_parts))
              core.console.info("right parts: " + str(right_parts))

              if _compare_base_versions(left_parts[0], right_parts[0]) != 0:
                  return _compare_base_versions(left_parts[0], right_parts[0])
              else: # means the base versions are the same
                  return _compare_pre_versions(left_parts[1], right_parts[1])

            selector = core.custom_version_selector(comparator = _custom_version_selector)
            """);
    VersionList versionList =
        new VersionList.SetVersionList(
            ImmutableSet.of(
                "v1.19.1-rc.3", "v1.17.1-alpha", "v1.19.1-rc.12", "v1.19.1-rc.2", "v1.19.1-alpha"));

    Optional<String> result = selector.select(versionList, "requestedRef", testingConsole);

    assertThat(result).hasValue("v1.19.1-rc.12");
  }

  @Test
  public void select_calVer_returnsLatest() throws Exception {
    VersionSelector selector =
        starlarkExecutor.eval(
            "selector",
            """
            def _custom_version_selector(left, right):
                left_components = [int(x) for x in left.split(".")]
                right_components = [int(x) for x in right.split(".")]
                for x, y in zip(left_components, right_components):
                    if x < y:
                        return -1
                    elif x > y:
                        return 1
                    else:
                      continue
                return 0

            selector = core.custom_version_selector(
                comparator = _custom_version_selector,
                regex_filter = r'[0-9]+\\.[0-9]+\\.[0-9]+'
            )
            """);
    VersionList versionList =
        new VersionList.SetVersionList(
            ImmutableSet.of("2024.1.1", "2024.12.2", "2024.5.7", "IGNORE_THIS"));

    Optional<String> result = selector.select(versionList, "requestedRef", testingConsole);

    assertThat(result).hasValue("2024.12.2");
  }
}
