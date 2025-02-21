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
package com.google.copybara.remotefile;

import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.copybara.exception.ValidationException;
import com.google.copybara.remotefile.extractutil.ExtractType;

/**
 * Remote file types supported by Copybara archive import Must be kept in the same ordinal order as
 * ExtractType
 */
public enum RemoteFileType {
  JAR,
  ZIP,
  TAR,
  TAR_GZ,
  TAR_XZ,
  AS_IS;

  public static ExtractType toExtractType(RemoteFileType type) throws ValidationException {
    checkCondition(type != AS_IS, "Cannot convert an as is file type to an extract type");
    return ExtractType.values()[type.ordinal()];
  }
}
