package com.google.copybara;

import com.google.common.base.MoreObjects;
import com.google.copybara.Origin.Reference;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * A migration is a process that moves files at a particular revision from one/many systems to
 * one/many destinations.
 *
 * <p>For helping with the migration a working directory is provided to do any temporary file
 * operations.
 */
public interface Migration {

  /**
   * Run a migration for a source reference. If the source reference is not present the default
   * (if any) will be used.
   *
   * @param workdir a working directory for doing file operations if needed.
   * @param sourceRef the source revision to be migrated. If not present the default (if any) for
   * the migration will be used.
   * @throws RepoException if an error happens while accessing the repository
   * @throws IOException if any generic I/O error happen during the process
   * @throws ValidationException if during the execution an error attributable to miss
   * configuration
   * is detected.
   */
  void run(Path workdir, @Nullable String sourceRef)
      throws RepoException, IOException, ValidationException;

  default Info getInfo() throws RepoException, ValidationException {
    return Info.EMPTY;
  }

  class Info<R extends Reference> {
    private static final Info EMPTY = new Info<>(/*lastMigratedRef=*/null);

    // TODO(copybara-team): Convert this into a list of migration references
    @Nullable
    private final Reference lastMigratedRef;

    Info(@Nullable R lastMigratedRef) {
      this.lastMigratedRef = lastMigratedRef;
    }

    @Nullable
    Reference getLastMigratedRef() {
      return lastMigratedRef;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("lastMigratedRef", lastMigratedRef)
          .toString();
    }
  }
}
