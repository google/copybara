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

package com.google.copybara.exception;

/**
 * An exception thrown by destinations when they detect that there is no change to submit, because
 * the same change is already pending in the destination and `allow_empty_diff=False` was present in
 * the config.
 */
public class RedundantChangeException extends EmptyChangeException {

  private final String pendingRevision;

  public RedundantChangeException(String message, String pendingRevision) {
    super(message);
    this.pendingRevision = pendingRevision;
  }

  public RedundantChangeException(Throwable cause, String message, String pendingRevision) {
    super(cause, message);
    this.pendingRevision = pendingRevision;
  }

  /** Return a ref (e.g. SHA1) of the pending change that makes the current workflow redundant. */
  public String getPendingRevision() {
    return pendingRevision;
  }
}
