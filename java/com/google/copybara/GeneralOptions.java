package com.google.copybara;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;

import com.beust.jcommander.Parameter;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * General options available for all the program classes.
 */
public final class GeneralOptions {

  private final FileSystem fs;
  private Path workdirPath;

  @Parameter(names = "-v", description = "Verbose output.")
  boolean verbose;

  @Parameter(names = "--work-dir", description = "Directory where all the transformations"
      + " will be performed. By default a temporary directory.")
  String workdir;

  public GeneralOptions() {
    fs = FileSystems.getDefault();
  }

  @VisibleForTesting
  public GeneralOptions(FileSystem fs) {
    this.fs = fs;
  }

  /**
   * This method should be called after the options have been set but before are used by any class.
   */
  public void init() throws IOException {
    if (workdir == null) {
      // This is equivalent to Files.createTempDirectory(String.. but
      // works for any filesystem
      Path tmpDir = fs.getPath(System.getProperty("java.io.tmpdir"));
      // This is only needed if using a fs for testing.
      Files.createDirectories(tmpDir);
      workdirPath = Files.createTempDirectory(tmpDir, "workdir");
    } else {
      workdirPath = fs.getPath(workdir).normalize();
    }
    if (Files.exists(workdirPath) && !Files.isDirectory(workdirPath)) {
      // Better being safe
      throw new IOException(
          "'" + workdirPath + "' exists and is not a directory");
    }
    if (!isDirEmpty(workdirPath)) {
      System.err.println("WARNING: " + workdirPath + " is not empty");
    }
  }

  public boolean isVerbose() {
    return verbose;
  }

  /**
   * Returns the workdir to be used by the repositories, checkers and transformations.
   *
   * <p> The path is already normalized.
   */
  public Path getWorkdir() {
    return Preconditions.checkNotNull(workdirPath, "init method not called");
  }

  private static boolean isDirEmpty(final Path directory) throws IOException {
    try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
      return !dirStream.iterator().hasNext();
    }
  }
}
