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

package com.google.copybara;

import static com.google.common.truth.Truth.assertWithMessage;
import static java.util.stream.Collectors.joining;

import com.google.copybara.testing.ReferenceTestUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ReferenceTest {

  private ReferenceTestUtil util;
  private String ref;

  @Before
  public void setUp() throws Exception {
    Path runfilesPath =
        Path.of("..").toAbsolutePath().normalize().resolve(System.getenv("REFERENCE_DIR"));
    ref = Files.readString(runfilesPath);
    util = new ReferenceTestUtil(ref);
  }

  @Test
  public void documentedClassesPresent() throws Exception {
    String msg =
        util.getMissingTopLevelEntries().stream()
            .map(
                c ->
                    String.format(
                        "Class '%s' sets StarlarkBuiltin but is not present in the reference. If"
                            + " this is intentional, set documented to false. Otherwise, make sure"
                            + " the defining BUILD rule has the"
                            + " '//third_party/java_src/copybara/java/com/google/copybara/doc"
                            + ":document' plugin.",
                        c.getCanonicalName()))
            .collect(joining("\n\n"));
    assertWithMessage(msg).that(msg).isEmpty();
  }

  @Test
  public void documentedMethodsPresent() throws Exception {
    String msg =
        util.getMissingMethods().stream()
            .map(
                m ->
                    String.format(
                        "Method '%s' in '%s' sets StarlarkMethod but is not present in the"
                            + " reference. If this is intentional, set 'documented' to false and"
                            + " make sure the class is documented by setting StarlarkBuiltin or"
                            + " Library.",
                        m.name(), m.definingClass().getCanonicalName()))
            .collect(joining("\n\n"));
    assertWithMessage(msg).that(msg).isEmpty();
  }
}
