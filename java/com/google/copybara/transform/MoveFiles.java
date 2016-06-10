package com.google.copybara.transform;

import com.google.common.collect.ImmutableList;
import com.google.copybara.EnvironmentException;
import com.google.copybara.Options;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.config.NonReversibleValidationException;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.doc.annotations.DocField;
import com.google.copybara.util.console.Console;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Moves and renames files
 */
public class MoveFiles implements Transformation {

  private final List<MoveElement> paths;

  private MoveFiles(List<MoveElement> paths) {
    this.paths = ImmutableList.copyOf(paths);
  }

  @Override
  public void transform(Path workdir, Console console) throws IOException, ValidationException {
    for (MoveElement path : paths) {
      console.progress("Moving " + path.before);
      Path before = workdir.resolve(path.before);
      if (!Files.exists(before)) {
        // TODO(malcon): We should have an error type for user issues that we discover
        // during execution.
        throw new ValidationException(
            String.format("Error moving '%s'. It doesn't exist in the workdir", path.before));
      }
      Path after = workdir.resolve(path.after);
      createParentDirs(after);
      try {
        Files.move(before, after, LinkOption.NOFOLLOW_LINKS, StandardCopyOption.ATOMIC_MOVE);
      } catch (FileAlreadyExistsException e) {
        throw new ValidationException(
            String
                .format("Cannot move '%s' because it already exists in the workdir", e.getFile()));
      }
    }
  }

  @Override
  public Transformation reverse() {
    //TODO(malcon): Make MoveFiles reversible
    throw new UnsupportedOperationException("MoveFiles not reversible");
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

  @DocElement(yamlName = "!MoveFiles",
      description = "Moves files between directories and renames files",
      elementKind = Transformation.class)
  public static class Yaml implements Transformation.Yaml {

    private List<MoveElement> paths = new ArrayList<>();

    @DocField(description = "Paths to rename/move. Use \"before:\" and \"after:\""
        + " field names for each element", listType = MoveElement.class)
    public void setPaths(List<MoveElement> paths) throws ConfigValidationException {
      if (!this.paths.isEmpty()) {
        throw new ConfigValidationException("'paths' already set: "+ this.paths );
      }
      this.paths = paths;
    }

    @Override
    public MoveFiles withOptions(Options options)
        throws ConfigValidationException, EnvironmentException {
      if (paths.isEmpty()) {
        throw new ConfigValidationException(
            "'paths' attribute is required and cannot be empty. At least one file"
                + " movement/rename is needed.");
      }
      return new MoveFiles(paths);
    }

    @Override
    public void checkReversible() throws ConfigValidationException {
      throw new NonReversibleValidationException(this);
    }
  }

  @SuppressWarnings("WeakerAccess")
  public static final class MoveElement {

    private static final Path basePath = FileSystems.getDefault().getPath("/workdir");
    private String before;
    private String after;

    public void setBefore(String before) throws ConfigValidationException {
      this.before = validatePath(before);
    }

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
  }
}
