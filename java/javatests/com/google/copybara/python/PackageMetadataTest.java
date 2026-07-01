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

package com.google.copybara.python;

import static com.google.common.truth.Truth.assertThat;

import com.google.copybara.exception.ValidationException;
import com.google.copybara.testing.OptionsBuilder;
import com.google.copybara.testing.SkylarkTestExecutor;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.Tuple;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class PackageMetadataTest {
  // this string comes from the 'env' argument in the BUILD file
  private static final String DATA_DIR = "DATA_DIR";
  private static final String TEST_FILENAME = "TESTMETADATA";

  private static final StarlarkList<Tuple> EXPECTED_METADATA =
      StarlarkList.immutableOf(
          Tuple.of("Name", "example-package-copybara"),
          Tuple.of("Version", "0.0.1"),
          Tuple.of("Metadata-Version", "2.1"),
          Tuple.of("Author-email", "Example Author <author@example.com>"),
          Tuple.of("Classifier", "Programming Language :: Python :: 3"),
          Tuple.of("Classifier", "Operating System :: OS Independent"),
          Tuple.of("Classifier", "License :: OSI Approved :: Apache Software License"),
          Tuple.of("Summary", "A small example package"),
          Tuple.of("Project-URL", "Bug Tracker, https://github.com/pypa/sampleproject/issues"),
          Tuple.of("Project-URL", "Homepage, https://github.com/pypa/sampleproject"),
          Tuple.of("License-File", "LICENSE"),
          Tuple.of("Description-Content-Type", "text/markdown"),
          Tuple.of("Requires-Python", ">=3.7"));

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();
  Path checkoutDirectory;
  private SkylarkTestExecutor starlark;

  @Before
  public void setup() {
    checkoutDirectory = tempFolder.getRoot().toPath();
    OptionsBuilder optionsBuilder = new OptionsBuilder();
    optionsBuilder.testingOptions.checkoutDirectory = checkoutDirectory;
    starlark = new SkylarkTestExecutor(optionsBuilder);
  }

  @Test
  public void testGetMetadata() throws ValidationException, IOException {
    // find the metadata file in runfiles
    // https://bazel.build/extending/rules#runfiles_location
    Path runfilesPath = Path.of("..").toAbsolutePath().normalize();
    Path wheelPath = runfilesPath.resolve(System.getenv(DATA_DIR));

    // copy metadata to checkout dir
    Path checkoutDir = tempFolder.getRoot().toPath();
    Path metadataPath = wheelPath.resolve(TEST_FILENAME);
    Path metadataCheckoutPath = checkoutDir.resolve(TEST_FILENAME);
    Files.copy(metadataPath, metadataCheckoutPath);

    Sequence<Tuple> metadata =
        starlark.eval(
            "metadata",
            String.format("" + "wheel_path = testing.get_checkout(\"%s\")\n", TEST_FILENAME)
                + "metadata = python.parse_metadata(wheel_path)\n");

    assertThat(metadata).containsExactlyElementsIn(EXPECTED_METADATA);
  }
}
