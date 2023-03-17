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

import com.google.copybara.exception.ValidationException;
import com.google.copybara.revision.Revision;
import java.util.Optional;
import java.util.function.Function;
import net.starlark.java.eval.StarlarkValue;

/** Takes a ref and resolves it to the repository */
public interface VersionResolver extends StarlarkValue {
  Revision resolve(String ref, Function<String, Optional<String>> assemblyStrategy)
      throws ValidationException;
}
