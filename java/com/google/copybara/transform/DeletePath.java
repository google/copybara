// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import com.google.common.base.Preconditions;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A transformation that deletes files and directories recursively
 */
public final class DeletePath implements Transformation {

  private static final Logger logger = Logger.getLogger(DeletePath.class.getName());
  private final Path path;

  private DeletePath(Path path) {
    this.path = path;
  }

  @Override
  public String toString() {
    return String.format("DeletePath{path: %s}", path);
  }

  @Override
  public void transform(Path workdir) throws IOException {
    Preconditions.checkState(path.startsWith(workdir),
        "Should not happen. Already checked on YAML code.");
    final AtomicBoolean deleted = new AtomicBoolean(false);
    Files.walkFileTree(workdir, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
        logger.log(Level.INFO, String.format("Rule %s: deleting file %s", this, file));
        if (file.startsWith(path)) {
          Files.delete(file);
          deleted.set(true);
        }
        return FileVisitResult.CONTINUE;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        logger.log(Level.INFO, String.format("Rule %s: deleting dir %s", this, dir));
        if (dir.startsWith(path)) {
          Files.delete(dir);
          deleted.set(true);
        }
        return FileVisitResult.CONTINUE;
      }
    });
    if (!deleted.get()) {
      //TODO(malcon): Better exception. Is this what we want? Better safe than sorry for now
      throw new IllegalStateException("Nothing was deleted!");
    }
  }

  public final static class Yaml implements Transformation.Yaml {

    private String path;

    public void setPath(String path) {
      this.path = path;
    }

    @Override
    public Transformation withOptions(Options options) {
      Path workdir = options.getOption(GeneralOptions.class).getWorkdir();

      Path realPath = workdir.resolve(ConfigValidationException.checkNotMissing(path, "path")).normalize();

      // We could validate in transform. But we prefer to validate early, before
      // running any transformation.
      if (!realPath.startsWith(workdir)) {
        throw new ConfigValidationException(
            "Only relative paths to workdir are allowed but 'path' is " + path);
      }
      return new DeletePath(realPath);
    }
  }
}
