package com.google.copybara;

import com.beust.jcommander.Parameter;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * General options available for all the program classes.
 */
public final class GeneralOptions {
  @Parameter(names = "-v", description = "Verbose output.")
  boolean verbose;

  @Parameter(names = "--work-dir", description = "Directory where all the transformations"
      + " will be performed. By default a temporary directory.")
  String workdir;

  public boolean isVerbose() {
    return verbose;
  }

  public Path getWorkdir() throws IOException {
    if (workdir == null) {
      workdir = Files.createTempDirectory("workdir").toString();
    }
    return FileSystems.getDefault().getPath(workdir);
  }

}
