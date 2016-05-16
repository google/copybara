package com.google.copybara;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.copybara.util.console.Console;

import com.beust.jcommander.Parameter;

import java.io.IOException;
import java.nio.file.FileSystem;

import javax.annotation.Nullable;

/**
 * General options available for all the program classes.
 */
public final class GeneralOptions implements Option {

  public static final String NOANSI = "--noansi";
  private final FileSystem fileSystem;
  private final boolean verbose;
  @Nullable
  private final String lastRevision;
  private final Console console;

  @VisibleForTesting
  public GeneralOptions(FileSystem fileSystem, boolean verbose, @Nullable String lastRevision,
      Console console) {
    this.console = console;
    this.fileSystem = Preconditions.checkNotNull(fileSystem);
    this.verbose = verbose;
    this.lastRevision = lastRevision;
  }

  public boolean isVerbose() {
    return verbose;
  }

  public Console console() {
    return console;
  }

  @Nullable
  public String getLastRevision() {
    return lastRevision;
  }

  public FileSystem getFileSystem() {
    return fileSystem;
  }

  public static final class Args {
    @Parameter(names = "-v", description = "Verbose output.")
    boolean verbose;

    @Parameter(names = "--last-rev", description = "Last revision that was migrated to the destination")
    String lastRevision;

    // We don't use JCommander for parsing this flag but we do it manually since
    // the parsing could fail and we need to report errors using one console
    @SuppressWarnings("unused")
    @Parameter(names = NOANSI, description = "Don't use ANSI output for messages")
    boolean noansi = false;

    /**
     * This method should be called after the options have been set but before are used by any class.
     */
    public GeneralOptions init(FileSystem fileSystem, Console console) throws IOException {
      return new GeneralOptions(fileSystem, verbose, lastRevision, console);
    }
  }
}
