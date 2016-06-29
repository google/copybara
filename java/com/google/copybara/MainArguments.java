// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

/**
 * Arguments which are unnamed (i.e. positional) or must be evaluated inside {@link Main}.
 */
@Parameters(separators = "=")
public final class MainArguments {

  @Parameter(description = "CONFIG_PATH [WORKFLOW_NAME [SOURCE_REF]]")
  List<String> unnamed = new ArrayList<>();

  @Parameter(names = "--help", help = true, description = "Shows this help text")
  boolean help;

  @Parameter(names = "--version", description = "Shows the version of the binary")
  boolean version;

  @Parameter(names = "--work-dir", description = "Directory where all the transformations"
      + " will be performed. By default a temporary directory.")
  String baseWorkdir;

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

  /**
   * Returns the base working directory. This method should not be accessed directly by any other
   * class but Main.
   */
  public Path getBaseWorkdir(FileSystem fs) throws IOException {
    Path workdirPath;

    if (baseWorkdir == null) {
      // This is equivalent to Files.createTempDirectory(String.. but
      // works for any filesystem
      Path tmpDir = fs.getPath(System.getProperty("java.io.tmpdir"));
      // This is only needed if using a fs for testing.
      Files.createDirectories(tmpDir);
      workdirPath = Files.createTempDirectory(tmpDir, "workdir");
    } else {
      workdirPath = fs.getPath(baseWorkdir).normalize();
    }
    if (Files.exists(workdirPath) && !Files.isDirectory(workdirPath)) {
      // Better being safe
      throw new IOException(
          "'" + workdirPath + "' exists and is not a directory");
    }
    if (!isDirEmpty(workdirPath)) {
      System.err.println("WARNING: " + workdirPath + " is not empty");
    }
    return workdirPath;
  }

  private static boolean isDirEmpty(final Path directory) throws IOException {
    try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
      return !dirStream.iterator().hasNext();
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
