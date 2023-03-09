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

package com.google.copybara.http.auth;

import java.io.IOException;

/** provider for auth credentials */
public interface KeySource {
  String get() throws IOException;

  /**
   * Signifies the key source was unable to locate the key
   * it is attempting to get.
   * Extends IOException to work more easily with the
   * http client interceptor type signature.
   */
  class KeyNotFoundException extends IOException {
    public KeyNotFoundException(String message) {
      super(message);
    }
  }
}
