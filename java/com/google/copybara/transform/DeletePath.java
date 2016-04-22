// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.ReadablePathMatcher;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A transformation that deletes files and directories recursively
 */
public final class DeletePath implements Transformation {

  private static final Logger logger = Logger.getLogger(DeletePath.class.getName());
  private final PathMatcher pathMatcher;

  private DeletePath(PathMatcher pathMatcher) {
    this.pathMatcher = pathMatcher;
  }

  @Override
  public void transform(Path workdir) throws IOException {
    int result = FileUtil.deleteFilesRecursively(workdir, pathMatcher);
    logger.log(Level.INFO,
        String.format("Rule %s: deleted %s files under %s", this, result, workdir));
    if (result == 0) {
      String suffix = "";
      if (!pathMatcher.toString().endsWith("/**")) {
        suffix = " Did you mean '" + pathMatcher + "/**'?";
      }
      //TODO(malcon): Better exception.
      throw new IllegalStateException(
          String.format("Rule %s: Nothing was deleted.%s", this, suffix));
    }
  }

  @Override
  public String toString() {
    return "DeletePath{path=" + pathMatcher + "}";
  }

  public final static class Yaml implements Transformation.Yaml {

    private String path;

    public void setPath(String path) {
      this.path = path;
    }

    @Override
    public Transformation withOptions(Options options) throws ConfigValidationException {
      PathMatcher pathMatcher = ReadablePathMatcher.relativeGlob(
          options.get(GeneralOptions.class).getWorkdir(),
          ConfigValidationException.checkNotMissing(this.path, "path"));
      return new DeletePath(pathMatcher);
    }
  }
}
