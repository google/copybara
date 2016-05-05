package com.google.copybara;

import com.google.common.base.Preconditions;

import com.beust.jcommander.Parameter;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nullable;

/**
 * General options available for all the program classes.
 */
public final class GeneralOptions implements Option {

  private final Path workdir;
  private final boolean verbose;
  @Nullable
  private final String lastRevision;

  public GeneralOptions(Path workdir, boolean verbose, @Nullable String lastRevision) {
    this.workdir = Preconditions.checkNotNull(workdir);
    this.verbose = verbose;
    this.lastRevision = lastRevision;
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
    return workdir;
  }

  private static boolean isDirEmpty(final Path directory) throws IOException {
    try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(directory)) {
      return !dirStream.iterator().hasNext();
    }
  }

  public static final class Args {
    @Parameter(names = "-v", description = "Verbose output.")
    boolean verbose;

    @Parameter(names = "--work-dir", description = "Directory where all the transformations"
        + " will be performed. By default a temporary directory.")
    String workdir;

    @Parameter(names = "--last-rev", description = "Last revision that was migrated to the destination")
    String lastRevision;

    /**
     * This method should be called after the options have been set but before are used by any class.
     */
    public GeneralOptions init(FileSystem fs) throws IOException {
      Path workdirPath;

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

      return new GeneralOptions(workdirPath, verbose, lastRevision);
    }
  }
}
