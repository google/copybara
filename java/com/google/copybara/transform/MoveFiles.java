// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.transform;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

/**
 * Moves and renames files
 */
public class MoveFiles implements Transformation {

  private final List<MoveElement> paths;
  private final WorkflowOptions workflowOptions;

  private MoveFiles(List<MoveElement> paths, WorkflowOptions workflowOptions) {
    this.paths = ImmutableList.copyOf(paths);
    this.workflowOptions = Preconditions.checkNotNull(workflowOptions);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("paths", paths)
        .toString();
  }

  @Override
  public void transform(Path workdir, Console console) throws IOException, ValidationException {
    for (MoveElement path : paths) {
      console.progress("Moving " + path.before);
      Path before = workdir.resolve(path.before);
      if (!Files.exists(before)) {
        workflowOptions.reportNoop(
            console,
            String.format("Error moving '%s'. It doesn't exist in the workdir", path.before));
        continue;
      }
      Path after = workdir.resolve(path.after);
      if (Files.isDirectory(after, LinkOption.NOFOLLOW_LINKS)
          && after.startsWith(before)) {
        // When moving from a parent dir to a sub-directory, make sure after doesn't already have
        // files in it - this is most likely a mistake.
        new VerifyDirIsEmptyVisitor(after).walk();
      }
      createParentDirs(after);
      try {
        Files.walkFileTree(before, new MovingVisitor(before, after));
      } catch (FileAlreadyExistsException e) {
        throw new ValidationException(
            String.format("Cannot move file to '%s' because it already exists", e.getFile()));
      }
    }
  }

  @Override
  public Transformation reverse() {
    ImmutableList.Builder<MoveElement> elements = ImmutableList.builder();
    for (MoveElement path : paths) {
      MoveElement newElement = new MoveElement();
      newElement.before = path.after;
      newElement.after = path.before;
      elements.add(newElement);
    }
    return new MoveFiles(elements.build().reverse(), workflowOptions);
  }

  private void createParentDirs(Path after) throws IOException, ValidationException {
    try {
      Files.createDirectories(after.getParent());
    } catch (FileAlreadyExistsException e) {
      // This exception message is particularly bad and we don't want to treat it as unhandled
      throw new ValidationException(String.format(
          "Cannot create '%s' because '%s' already exists and is not a directory",
          after.getParent(), e.getFile()));
    }
  }

  @Override
  public String describe() {
    return "Renaming " + paths.size() + " file(s)";
  }

  @SuppressWarnings("WeakerAccess")
  public static final class MoveElement {

    private static final Path basePath = FileSystems.getDefault().getPath("/workdir");
    private String before;
    private String after;

    @DocField(description = "The name of the file or directory before moving. If this is the empty"
        + " string and 'after' is a directory, then all files in the workdir will be moved to the"
        + " sub directory specified by 'after', maintaining the directory tree.")
    public void setBefore(String before) throws ConfigValidationException {
      this.before = validatePath(before);
    }

    @DocField(description = "The name of the file or directory after moving. If this is the empty"
        + " string and 'before' is a directory, then all files in 'before' will be moved to the"
        + " repo root, maintaining the directory tree inside 'before'.")
    public void setAfter(String after) throws ConfigValidationException {
      this.after = validatePath(after);
    }

    private String validatePath(String strPath) throws ConfigValidationException {
      Path resolved = basePath.resolve(strPath);
      Path normalized = resolved.normalize();

      if (!(resolved.toString().equals(normalized.toString()) && normalized.startsWith(basePath))) {
        throw new ConfigValidationException("'" + strPath + "' is not a relative path");
      }
      return strPath;
    }

    void checkRequiredFields() throws ConfigValidationException {
      ConfigValidationException.checkNotMissing(before, "before");
      ConfigValidationException.checkNotMissing(after, "after");
    }
  }
}
