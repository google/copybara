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

package com.google.copybara.onboard;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.copybara.onboard.core.AskInputProvider.Mode;
import com.google.copybara.onboard.core.CannotProvideException;
import com.google.copybara.onboard.core.InputProviderResolverImpl;
import com.google.copybara.util.console.testing.TestingConsole;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class GitToGitGeneratorTest {

  @Test
  public void testSimple() throws CannotProvideException, InterruptedException {
    GitToGitGenerator gitToGitGenerator = new GitToGitGenerator();
    TestingConsole console = new TestingConsole();

    console.respondWithString("http://example.com/origin");
    console.respondWithString("http://example.com/destination");
    console.respondWithString("author <author@example.com>");
    console.respondWithString("my_name");

    String config =
        gitToGitGenerator.generate(
            InputProviderResolverImpl.create(
                ImmutableSet.of(),
                (s, r) -> {
                  throw new IllegalStateException();
                },
                Mode.AUTO,
                console));
    assertThat(config)
        .isEqualTo(
"""
core.workflow(
    name = 'my_name',
    origin = git.origin(
        url = "http://example.com/origin",
    ),\s
    destination = git.destination(
        url = "http://example.com/destination",
    ),
    authoring = authoring.pass_thru("author <author@example.com>"),

    transformations = [
        # TODO: Insert your transformations here
    ],
)
""");
  }
}
