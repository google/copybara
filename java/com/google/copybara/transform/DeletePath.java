// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.util.FileUtil;

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
  private final String description;

  private DeletePath(PathMatcher pathMatcher, String description) {
    this.pathMatcher = pathMatcher;
    this.description = description;
  }

  @Override
  public void transform(Path workdir) throws IOException {
    int result = FileUtil.deleteFilesRecursively(workdir, pathMatcher);
    logger.log(Level.INFO,
        String.format("Rule %s: deleted %s files under %s", this, result, workdir));
    if (result == 0) {
      String suffix = "";
      if (!description.endsWith("/**")) {
        suffix = " Did you mean '" + description + "/**'?";
      }
      //TODO(malcon): Better exception.
      throw new IllegalStateException(
          String.format("Rule %s: Nothing was deleted.%s", this, suffix));
    }
  }

  @Override
  public String toString() {
    return "DeletePath{path=" + description + "}";
  }

  public final static class Yaml implements Transformation.Yaml {

    private String path;

    public void setPath(String path) {
      this.path = path;
    }

    @Override
    public Transformation withOptions(Options options) {
      PathMatcher pathMatcher = FileUtil.relativeGlob(
          options.getOption(GeneralOptions.class).getWorkdir(),
          ConfigValidationException.checkNotMissing(this.path, "path"));
      return new DeletePath(pathMatcher, path);
    }
  }
}
