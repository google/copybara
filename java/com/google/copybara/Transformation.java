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

package com.google.copybara;

import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import java.io.IOException;

/**
 * Interface implemented by all source code transformations.
 */
@SkylarkModule(
    name = "transformation",
    doc = "A transformation to the workdir",
    category = SkylarkModuleCategory.TOP_LEVEL_TYPE)
public interface Transformation {

  /**
   * Transforms the files inside the checkout dir specified by {@code work}.
   *
   * @throws IOException if an error occur during the access to the files
   * @throws ValidationException if an error attributable to the user happened
   */
  void transform(TransformWork work, Console console) throws IOException, ValidationException;

  /**
   * Returns a transformation which runs this transformation in reverse.
   *
   * @throws NonReversibleValidationException if the transform is not reversible
   */
  Transformation reverse() throws NonReversibleValidationException;

  /**
   * Return a high level description of what the transform is doing. Note that this should not be
   * {@link #toString()} method but something more user friendly.
   */
  String describe();
}
