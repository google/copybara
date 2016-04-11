package com.google.copybara.localdir;

import com.google.copybara.Option;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

/**
 * Arguments for GitRepository
 */
@Parameters(separators = "=")
public final class LocalDestinationOptions implements Option {

  @Parameter(names = "--folder-dir",
      description = "Local directory to put the output of the transformation")
  String localFolder = null;
}
