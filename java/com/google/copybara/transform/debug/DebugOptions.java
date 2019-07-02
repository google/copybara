/*
 * Copyright (C) 2019 Google Inc.
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

package com.google.copybara.transform.debug;

import com.beust.jcommander.Parameter;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Option;
import com.google.copybara.Transformation;
import com.google.copybara.util.Glob;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;

public class DebugOptions implements Option {

  @Parameter(names = "--debug-metadata-break",
      description = "Stop when message and/or author changes")
  public boolean debugMetadataBreak = false;

  @Parameter(names = "--debug-file-break",
      description = "Stop when file matching the glob changes")
  public String debugFileBreak = null;

  @Parameter(names = "--debug-transform-break",
      description = "Stop when transform description matches")
  public String debugTransformBreak = null;


  private final GeneralOptions generalOptions;

  public DebugOptions(GeneralOptions generalOptions) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
  }

  @Nullable
  Glob getDebugFileBreak() {
    return debugFileBreak != null ? Glob.createGlob(ImmutableList.of(debugFileBreak)) : null;
  }


  Pattern getDebugTransformBreak() {
    return debugTransformBreak != null ? Pattern.compile(debugTransformBreak) : null;
  }

  public Transformation transformWrapper(Transformation transformation) {
    if (!debuggerEnabled()) {
      return transformation;
    }
    return TransformDebug.withDebugger(transformation, this, generalOptions.getEnvironment());
  }

  private boolean debuggerEnabled() {
    return debugMetadataBreak
        || debugFileBreak != null
        || debugTransformBreak != null;
  }

  Path createDiffDirectory() throws IOException {
    return generalOptions.getDirFactory().newTempDir("debug");
  }
}
