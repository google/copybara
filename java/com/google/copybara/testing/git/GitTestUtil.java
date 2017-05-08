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

package com.google.copybara.testing.git;

import com.google.copybara.authoring.Author;
import java.util.HashMap;
import java.util.Map;

/**
 * Common utilities for creating and working with git repos in test
 */
public class GitTestUtil {
  static final Author DEFAULT_AUTHOR = new Author("Authorbara", "author@example.com");
  static final Author COMMITER = new Author("Commit Bara", "commitbara@example.com");

  /**
   * Returns an environment that contains the System environment and a set of variables
   * needed so that test don't crash in environments where the author is not set
   */
  public static Map<String, String> getGitEnv() {
    HashMap<String, String> values = new HashMap<>(System.getenv());
    values.put("GIT_AUTHOR_NAME", DEFAULT_AUTHOR.getName());
    values.put("GIT_AUTHOR_EMAIL", DEFAULT_AUTHOR.getEmail());
    values.put("GIT_COMMITTER_NAME", COMMITER.getName());
    values.put("GIT_COMMITTER_EMAIL", COMMITER.getEmail());
    return values;
  }
}
