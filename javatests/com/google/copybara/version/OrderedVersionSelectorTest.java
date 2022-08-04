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
import static com.google.common.truth.Truth8.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.templatetoken.Token;
import com.google.copybara.util.console.Console;
import com.google.copybara.util.console.testing.TestingConsole;
import com.google.copybara.version.VersionSelector.SearchPattern;
import java.util.Optional;
import javax.annotation.Nullable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class OrderedVersionSelectorTest {

  public static final VersionList LIST = () -> ImmutableSet.of("one", "two");

  @Test
  public void testEmpty() throws ValidationException, RepoException {
    OrderedVersionSelector selector = new OrderedVersionSelector(ImmutableList.of());
    assertThat(selector.select(LIST, "one", new TestingConsole())).isEmpty();
    assertThat(selector.searchPatterns()).isEmpty();
  }

  @Test
  public void testSimple() throws ValidationException, RepoException {
    OrderedVersionSelector selector = new OrderedVersionSelector(ImmutableList.of(
        (versionList, requestedRef, console) -> Optional.of("three"),
        (versionList, requestedRef, console) -> Optional.of("forth")
    ));
    assertThat(selector.select(LIST, "one", new TestingConsole())).hasValue("three");
  }

  @Test
  public void testFirstEmpty() throws ValidationException, RepoException {
    OrderedVersionSelector selector = new OrderedVersionSelector(ImmutableList.of(
        (versionList, requestedRef, console) -> Optional.empty(),
        (versionList, requestedRef, console) -> Optional.of("forth")
    ));
    assertThat(selector.select(LIST, "one", new TestingConsole())).hasValue("forth");
  }

  @Test
  public void testSearchPatterns() throws ValidationException, RepoException {
    SearchPattern foo = new SearchPattern(ImmutableList.of(Token.literal("foo")));
    SearchPattern bar = new SearchPattern(ImmutableList.of(Token.literal("bar")));

    OrderedVersionSelector selector = new OrderedVersionSelector(ImmutableList.of(
        new VersionSelector() {
          @Override
          public Optional<String> select(VersionList versionList, @Nullable String requestedRef,
              Console console) {
            return Optional.empty();
          }

          @Override
          public ImmutableSet<SearchPattern> searchPatterns() {
            return ImmutableSet.of(foo);
          }
        },
        new VersionSelector() {
          @Override
          public Optional<String> select(VersionList versionList, @Nullable String requestedRef,
              Console console) {
            return Optional.empty();
          }

          @Override
          public ImmutableSet<SearchPattern> searchPatterns() {
            return ImmutableSet.of(bar);
          }
        }
    ));
    assertThat(selector.searchPatterns()).containsExactly(foo, bar);
  }
}
