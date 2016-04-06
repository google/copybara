package com.google.copybara.localdir;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * Arguments for GitRepository
 */
@Parameters(separators = "=")
public final class LocalDestinationOptions {

  @Parameter(names = "--folder-dir",
      description = "Local directory to put the output of the transformation")
  String localFolder = null;
}
