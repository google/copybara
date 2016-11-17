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


import static com.google.common.base.Preconditions.checkNotNull;

import javax.annotation.Nullable;

/**
 * Reflective information about the migration in progress.
 */
public class MigrationInfo {
  @Nullable private final String originLabel;
  @Nullable private final ChangeVisitable<?> destinationVisitable;

  public MigrationInfo(String originLabel, ChangeVisitable<?> destinationVisitable) {
    this.originLabel = originLabel;
    this.destinationVisitable = destinationVisitable;
  }

  public String getOriginLabel() {
    return checkNotNull(originLabel);
  }

  @Nullable
  public ChangeVisitable<?> destinationVisitable() {
    return destinationVisitable;
  }
}
