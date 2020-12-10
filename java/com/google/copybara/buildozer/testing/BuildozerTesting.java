/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara.buildozer.testing;

import com.google.copybara.testing.OptionsBuilder;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility methods for testing Buildozer.
 */
public final class BuildozerTesting {

  private BuildozerTesting() {}

  /**
   * Allows Buildozer to be executed for transformation instances constructed with the given {@code
   * options}.
   */
  public static void enable(OptionsBuilder options) throws IOException {
    Path runtime = Paths.get(System.getenv("TEST_SRCDIR"))
        .resolve(System.getenv("TEST_WORKSPACE"))
        .resolve("java/com/google/copybara/buildozer/testing");
    // Use data dependencies on the buildozer/buildifier binaries.
    File buildozer = runtime.resolve("buildozer").toFile();
    File buildifier = runtime.resolve("buildifier").toFile();
    options.buildozerBin = buildozer.getAbsolutePath();
    options.buildifier.buildifierBin = buildifier.getAbsolutePath();
  }
}
