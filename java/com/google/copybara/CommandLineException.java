// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

/**
 * An exception due to a command line error
 */
class CommandLineException extends Exception {

  CommandLineException(String message) {
    super(message);
  }
}
