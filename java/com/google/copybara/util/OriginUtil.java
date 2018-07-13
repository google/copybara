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

package com.google.copybara.util;

import com.google.common.collect.ImmutableCollection;
import com.google.common.collect.ImmutableSet;

/**
 * Utility methods for managing origins
 */
public class OriginUtil {

  private OriginUtil() {}

  /**
   * Checks if the given {@code changedFiles} are or are descendants of the {@code roots}.
   */
  public static boolean affectsRoots(ImmutableSet<String> roots,
      ImmutableCollection<String> changedFiles) {
    if (changedFiles == null || Glob.isEmptyRoot(roots)) {
      return true;
    }
    // This is O(changes * files * roots) in the worse case. roots shouldn't be big and
    // files shouldn't be big for 99% of the changes.
    for (String file : changedFiles) {
      for (String root : roots) {
        if (file.equals(root) || file.startsWith(root + "/")) {
          return true;
        }
      }
    }
    return false;
  }
}
