/*
 * Copyright (C) 2026 Google LLC.
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

package com.google.copybara.exception;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** An exception thrown when an Origin precondition is not met. */
public class MissingPreconditionException extends EmptyChangeException {

  private final List<String> refs;

  /**
   * @param message the error message
   * @param refs String representations of the refs to the origin revisions that failed to migrate.
   */
  public MissingPreconditionException(String message, List<String> refs) {
    super(message);
    this.refs = Collections.unmodifiableList(new ArrayList<>(refs));
  }

  /**
   * @param cause the cause of the exception
   * @param message the error message
   * @param refs String representations of the refs to the origin revisions that failed to migrate.
   */
  public MissingPreconditionException(Throwable cause, String message, List<String> refs) {
    super(cause, message);
    this.refs = Collections.unmodifiableList(new ArrayList<>(refs));
  }

  public List<String> getRefs() {
    return refs;
  }
}
