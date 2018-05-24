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

package com.google.copybara.testing;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.checks.Checker;
import com.google.copybara.checks.CheckerException;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map.Entry;

/**
 * A dummy checker for tests
 *
 * TODO(danielromero): Promote to a real transform that uses regex
 */
@SkylarkModule(
    name = "dummy_checker",
    doc = "A dummy checker for tests",
    category = SkylarkModuleCategory.BUILTIN)
public class DummyChecker implements Checker {

  private final ImmutableSet<String> badWords;

  public DummyChecker(ImmutableSet<String> badWords) {
    this.badWords = Preconditions.checkNotNull(badWords);
  }

  @Override
  public void doCheck(ImmutableMap<String, String> fields) throws CheckerException, IOException {
    for (Entry<String, String> entry : fields.entrySet()) {
      if (badWords.stream().anyMatch(s -> entry.getValue().contains(s))) {
        throw new CheckerException("Bad word found!");
      }
    }
  }

  @Override
  public void doCheck(Path target) throws CheckerException, IOException {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
