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
package com.google.copybara.util;

import java.util.Objects;

/**
 * SinglePatch represents the difference between what exists in the destination files and the output
 * of an import created by Copybara. This will only be different when using merge import mode,
 * because otherwise the destination files will be overwritten and there will be no difference.
 */
public class SinglePatch {
  private static final String header = ""
      + "# This file is generated by Copybara.\n"
      + "# Do not edit.\n";

  byte[] toBytes() {
    return header.getBytes();
  }

  public static SinglePatch fromBytes(byte[] bytes) {
    return new SinglePatch();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    return obj instanceof SinglePatch;
  }

  @Override
  public int hashCode() {
    return Objects.hash();
  }

}