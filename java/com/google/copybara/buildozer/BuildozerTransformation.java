/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.copybara.buildozer;

import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.buildozer.BuildozerOptions.BuildozerCommand;
import java.io.IOException;

/**
 * Common interface implemented by all buildozer transformations.
 *
 * Used by {@link BuildozerBatch} to batch multiple invocations in one buildozer cli call.
 */
public interface BuildozerTransformation extends Transformation {

  /**
   * Actions to run before calling buildozer. For example creating files.
   */
  default void beforeRun(TransformWork work) throws ValidationException, IOException {}

  /**
   * List of commands to execute
   */
  Iterable<BuildozerCommand> getCommands();
}
