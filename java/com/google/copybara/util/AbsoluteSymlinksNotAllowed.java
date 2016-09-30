package com.google.copybara.util;

import java.io.IOException;
import java.nio.file.Path;

/**
 * An exception thrown then Copybara detects symlink to absolute paths or locations outside the
 * expected root path.
 */
public class AbsoluteSymlinksNotAllowed extends IOException {

  private final Path symlink;
  private final Path destinationFile;

  AbsoluteSymlinksNotAllowed(String msg, Path symlink, Path destinationFile) {
    this.symlink = symlink;
    this.destinationFile = destinationFile;
  }

  public Path getSymlink() {
    return symlink;
  }

  public Path getDestinationFile() {
    return destinationFile;
  }
}
