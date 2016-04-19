// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import java.io.IOException;

/**
 * Indicates that a transformation being applied to the current migration target does not
 * round-trip when applied in reverse.
 */
public class NotRoundtrippableException extends IOException {
  public NotRoundtrippableException(String message) {
    super(message);
  }
}
