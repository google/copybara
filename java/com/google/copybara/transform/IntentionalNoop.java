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

package com.google.copybara.transform;

import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;

/**
 * A transformation which does nothing. This is a no-op which is not considered an error (unlike a
 * core.move which has no matching files for the 'before' path), so it does not generate a warning.
 */
public enum IntentionalNoop implements Transformation {
  INSTANCE;

  @Override
  public TransformationStatus transform(TransformWork work) {
    return TransformationStatus.success();
  }

  @Override
  public Transformation reverse() {
    return this;
  }

  @Override
  public String describe() {
    return "no-op";
  }
}
