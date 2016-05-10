// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Arguments which are unnamed (i.e. positional) or must be evaluated inside {@link Main}.
 */
@Parameters(separators = "=")
final class MainArguments {

  @Parameter(description = "CONFIG_PATH [WORKFLOW_NAME [SOURCE_REF]]")
  List<String> unnamed = new ArrayList<>();

  @Parameter(names = "--help", help = true, description = "Shows this help text")
  boolean help;

  String getConfigPath() {
    return unnamed.get(0);
  }

  String getWorkflowName() {
    if (unnamed.size() >= 2) {
      return unnamed.get(1);
    } else {
      return "default";
    }
  }

  @Nullable
  String getSourceRef() {
    if (unnamed.size() >= 3) {
      return unnamed.get(2);
    } else {
      return null;
    }
  }

  void validateUnnamedArgs() throws CommandLineException {
    if (unnamed.size() < 1) {
      throw new CommandLineException("Expected at least a configuration file.");
    } else if (unnamed.size() > 3) {
      throw new CommandLineException("Expect at most three arguments.");
    }
  }
}
