/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.copybara.transform;

import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.TransformWork;
import com.google.copybara.Transformation;
import com.google.copybara.TransformationStatus;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.templatetoken.Token;
import com.google.copybara.templatetoken.Token.TokenType;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.Glob;
import com.google.re2j.Pattern;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Starlark;
import net.starlark.java.syntax.Location;

/** Transformation that moves (renames) or copies a single file or directory. */
public class CopyOrMove implements Transformation {

  private final RegexTemplateTokens before;
  private final RegexTemplateTokens after;
  private final Glob paths;
  private final ImmutableMap<String, Pattern> regexGroups;
  private final boolean overwrite;
  @Nullable private final Location location;
  private final WorkflowOptions workflowOptions;
  private final boolean isCopy;

  private CopyOrMove(
      RegexTemplateTokens before,
      RegexTemplateTokens after,
      Map<String, Pattern> regexGroups,
      WorkflowOptions workflowOptions,
      Glob paths,
      boolean overwrite,
      @Nullable Location location,
      boolean isCopy) {
    this.before = Preconditions.checkNotNull(before);
    this.after = Preconditions.checkNotNull(after);
    this.regexGroups = ImmutableMap.copyOf(regexGroups);
    this.paths = paths;
    this.overwrite = overwrite;
    this.location = location;
    this.workflowOptions = Preconditions.checkNotNull(workflowOptions);
    this.isCopy = isCopy;
  }

  public static CopyOrMove create(
      String before,
      String after,
      Map<String, String> regexGroups,
      WorkflowOptions workflowOptions,
      Glob paths,
      boolean overwrite,
      Location location,
      boolean isCopy)
      throws EvalException {
    Map<String, Pattern> parsedRegexGroups = Replace.parsePatterns(regexGroups);
    RegexTemplateTokens beforeTokens =
        new RegexTemplateTokens(validatePath(before), parsedRegexGroups, true, true, location);
    beforeTokens.validateUnused();
    RegexTemplateTokens afterTokens =
        new RegexTemplateTokens(validatePath(after), parsedRegexGroups, true, true, location);
    return new CopyOrMove(
        beforeTokens,
        afterTokens,
        parsedRegexGroups,
        workflowOptions,
        paths,
        overwrite,
        location,
        isCopy);
  }

  public static CopyOrMove createMove(
      String before,
      String after,
      Map<String, String> regexGroups,
      WorkflowOptions workflowOptions,
      Glob paths,
      boolean overwrite,
      Location location)
      throws EvalException {
    return create(
        before, after, regexGroups, workflowOptions, paths, overwrite, location, /*isCopy=*/ false);
  }

  public static CopyOrMove createCopy(
      String before,
      String after,
      Map<String, String> regexGroups,
      WorkflowOptions workflowOptions,
      Glob paths,
      boolean overwrite,
      Location location)
      throws EvalException {
    return create(
        before, after, regexGroups, workflowOptions, paths, overwrite, location, /*isCopy=*/ true);
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("before", before)
        .add("after", after)
        .add("regexGroups", regexGroups)
        .add("paths", paths)
        .add("overwrite", overwrite)
        .toString();
  }

  @Override
  public TransformationStatus transform(TransformWork work)
      throws IOException, ValidationException {
    work.getConsole().progress("Moving " + this.before);
    if (this.before.isLiteral()) {
      return transformNoRegex(work);
    } else {
      return transformWithRegex(work);
    }
  }

  private TransformationStatus transformNoRegex(TransformWork work)
      throws IOException, ValidationException {
    Path before = work.getCheckoutDir().resolve(this.before.toString()).normalize();
      if (!Files.exists(before)) {
        return TransformationStatus.noop(
          String.format("Error moving '%s'. It doesn't exist in the workdir", this.before));
      }
    Path after = work.getCheckoutDir().resolve(this.after.toString()).normalize();
      if (Files.isDirectory(after, LinkOption.NOFOLLOW_LINKS)
          && after.startsWith(before)) {
      // When moving from a parent dir to a sub-directory, make sure after doesn't already have
      // files in it - this is most likely a mistake.
      new VerifyDirIsEmptyVisitor(
              after,
              Files.isDirectory(before) && paths != Glob.ALL_FILES ? paths.relativeTo(after) : null)
          .walk();
      }
      createParentDirs(after);
      try {
        boolean beforeIsDir = Files.isDirectory(before);
        checkCondition(paths.equals(Glob.ALL_FILES) || beforeIsDir,
            "Cannot use user defined 'paths' filter when the 'before' is not a directory: "
                + paths);
      checkCondition(
          !this.after.isEmpty() || beforeIsDir,
          "Can only move a path to the root when the path is a folder. But '%s' is a "
              + "file. Use instead core.move('%s', '%s')",
          this.before,
          this.before,
          before.getFileName().toString());

        // Simple move of all the contents of a directory
        if (beforeIsDir && !isCopy && paths.equals(Glob.ALL_FILES)) {
          moveAllFilesInDir(before, after, work.getCheckoutDir());
          return TransformationStatus.success();
        }

        Files.walkFileTree(before,
            new CopyMoveVisitor(before, after, beforeIsDir ? paths.relativeTo(before) : null,
                overwrite, isCopy));

        // Delete 'before' folder if we moved all the files. We don't traverse to check emptyness
        // recursively but it should be good enough for now.
        if (beforeIsDir && !isCopy) {
          recursiveDeleteIfEmpty(before);
        }
      } catch (FileAlreadyExistsException e) {
        throw new ValidationException(
            String.format("Cannot move file to '%s' because it already exists", e.getFile()), e);
      }
    return TransformationStatus.success();
  }

  private TransformationStatus transformWithRegex(TransformWork work)
      throws IOException, ValidationException {

    // Optimize by only visiting files within rootPath
    Path rootPath = work.getCheckoutDir().resolve(getRoot(before)).normalize();
    if (!Files.isDirectory(rootPath)) {
      return TransformationStatus.noop(
          "Transformation '" + this + "' was a no-op because it didn't match any file");
    }
    try {
      boolean atLeastOneFileMatched =
          CopyMoveRegexVisitor.run(
              rootPath,
              before,
              after,
              paths.relativeTo(rootPath),
              work.getCheckoutDir(),
              overwrite,
              isCopy);
      if (!atLeastOneFileMatched) {
        return TransformationStatus.noop(
            "Transformation '" + this + "' was a no-op because it didn't match any file");
      }
    } catch (FileAlreadyExistsException e) {
      throw new ValidationException(
          String.format("Cannot move file to '%s' because it already exists", e.getFile()), e);
    }
    return TransformationStatus.success();
  }

  private static String getRoot(RegexTemplateTokens templateTokens) {
    List<Token> tokens = templateTokens.getTokens();
    if (tokens.isEmpty()) {
      return "";
    } else if (tokens.size() == 1) {
      Token token = tokens.get(0);
      return token.getType() == TokenType.LITERAL ? token.getValue() : "";
    }
    String prefix =
        tokens.stream()
            .findFirst()
            .map((t) -> t.getType() == TokenType.LITERAL ? t.getValue() : "")
            .orElse("");
    if (prefix.contains("/")) {
      return prefix.substring(0, prefix.lastIndexOf("/"));
    } else {
      return "";
    }
  }

  /** Traverse a directory files/folders recursively and delete any empty folder */
  private void recursiveDeleteIfEmpty(Path dir) throws IOException {
    Files.walkFileTree(dir, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
        return FileVisitResult.SKIP_SIBLINGS;
      }

      @Override
      public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
        try {
          Files.delete(dir);
        } catch (DirectoryNotEmptyException ignore) {
          // Folder not empty. Ignore.
        }
        return FileVisitResult.CONTINUE;
      }
    });
  }

  /**
   * Move all the files and directories inside {@code before} to {@code after}.
   *
   * <p>Instead of doing a direct move we use a temporary directory to support moving directories
   * from subdir to the root and viceversa.
   */
  private void moveAllFilesInDir(Path before, Path after, Path checkoutDir) throws IOException {
    List<Path> beforeFiles = listDirFiles(before);
    Path tmp = Files.createTempDirectory(checkoutDir, "core.move");
    for (Path file : beforeFiles) {
      Files.move(file, tmp.resolve(file.getFileName()));
    }

    if (!checkoutDir.equals(before)) {
      recursiveDeleteIfEmpty(before);
    }

    // If directory exists after the move to tmp, it can contain files. Move each file individually.
    if (!Files.exists(after)) {
      // Ensure parent exist and then rename tmp.
      Files.createDirectories(after.getParent());
      Files.move(tmp, after);
      return;
    }

    // Use our less-efficient move per file, We could try to be more clever here and do per
    // non-empty subfolder. But lot of stuff to deal with for good errors, covering all the
    // cases (symlink, dir, file transitions, etc.).
    Files.walkFileTree(tmp,
        new CopyMoveVisitor(tmp, after, /*pathMatcher=*/null, overwrite, /*isCopy=*/false));

    recursiveDeleteIfEmpty(tmp);
  }

  private List<Path> listDirFiles(Path before) throws IOException {
    try(Stream<Path> stream = Files.list(before)) {
      return stream.collect(Collectors.toList());
    }
  }

  @Override
  public Transformation reverse() throws NonReversibleValidationException {
    if (!this.before.isLiteral()) {
      throw new NonReversibleValidationException(
          "core."
              + (isCopy ? "copy" : "move")
              + "() with regex templating is not automatically reversible. Use core.transform to"
              + " define an explicit reverse");
    }
    if (overwrite) {
      throw new NonReversibleValidationException(
          "core."
              + (isCopy ? "copy" : "move")
              + "() with overwrite set is not"
              + " automatically reversible. Use core.transform to define an explicit reverse");
    }
    if (isCopy) {
      Path afterPath = Paths.get(after.toString());
      if (paths != Glob.ALL_FILES) {
        throw new NonReversibleValidationException(
            "core.copy not automatically" + " reversible when using 'paths'");
      } else if (after.isEmpty()
          || Paths.get(before.toString()).normalize().startsWith(afterPath)) {
        throw new NonReversibleValidationException(
            "core.copy not automatically" + " reversible when copying to a parent directory");
      }
      return new ExplicitReversal(
          new Remove(
              // After might be a directory or a file. Delete both
              Glob.createGlob(ImmutableList.of(after.toString(), afterPath + "/**")), location),
          this);
    }
    return new CopyOrMove(
        after,
        before,
        regexGroups,
        workflowOptions,
        paths,
        /*overwrite=*/ false,
        location,
        /*isCopy=*/ false);
  }

  private void createParentDirs(Path after) throws IOException, ValidationException {
    try {
      Files.createDirectories(after.getParent());
    } catch (FileAlreadyExistsException e) {
      // This exception message is particularly bad and we don't want to treat it as unhandled
      throw new ValidationException(String.format(
          "Cannot create '%s' because '%s' already exists and is not a directory",
          after.getParent(), e.getFile())
      );
    }
  }

  @Override
  public String describe() {
    return (isCopy ? "Copying " : "Moving ") + before;
  }

  private static String validatePath(String strPath) throws EvalException {
    try {
      return FileUtil.checkNormalizedRelative(strPath);
    } catch (IllegalArgumentException e) {
      throw Starlark.errorf("'%s' is not a valid path: %s", strPath, e.getMessage());
    }
  }

  @Override
  public Location location() {
    return location;
  }
}
