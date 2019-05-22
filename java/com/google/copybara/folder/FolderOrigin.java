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

package com.google.copybara.folder;

import static com.google.copybara.exception.ValidationException.checkCondition;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.Change;
import com.google.copybara.Origin;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.AbsoluteSymlinksNotAllowed;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.FileUtil.CopySymlinkStrategy;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import javax.annotation.Nullable;

/**
 * Use a folder as the input for the migration.
 */
public class FolderOrigin implements Origin<FolderRevision> {

  private static final String LABEL_NAME = "FolderOrigin-RevId";
  private static final ImmutableSet<PosixFilePermission> FILE_PERMISSIONS =
      ImmutableSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

  private final FileSystem fs;
  private final Author author;
  private final String message;
  private final Path cwd;
  private final CopySymlinkStrategy copySymlinkStrategy;

  FolderOrigin(FileSystem fs, Author author, String message, Path cwd,
      boolean materializeOutsideSymlinks, boolean ignoreInvalidSymlinks) {
    this.fs = Preconditions.checkNotNull(fs);
    this.author = author;
    this.message = message;
    this.cwd = Preconditions.checkNotNull(cwd);
    this.copySymlinkStrategy = ignoreInvalidSymlinks
            ? CopySymlinkStrategy.IGNORE_INVALID_SYMLINKS
            : materializeOutsideSymlinks
                ? CopySymlinkStrategy.MATERIALIZE_OUTSIDE_SYMLINKS
                : CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS;
  }

  @Override
  public FolderRevision resolve(@Nullable String reference) throws ValidationException {
    checkCondition(reference != null, ""
        + "A path is expected as reference in the command line. Invoke copybara as:\n"
        + "    copybara copy.bara.sky workflow_name ORIGIN_FOLDER");
    Path path = fs.getPath(reference);
    if (!path.isAbsolute()) {
      path = cwd.resolve(path);
    }
    checkCondition(Files.exists(path), "%s folder doesn't exist", path);
    checkCondition(Files.isDirectory(path), "%s is not a folder", path);
    checkCondition(Files.isReadable(path) && Files.isExecutable(path), "%s is not readable/executable", path);

    return new FolderRevision(path, ZonedDateTime.now(ZoneId.systemDefault()));
  }

  @Override
  public Reader<FolderRevision> newReader(Glob originFiles, Authoring authoring)
      throws ValidationException {
    return new Reader<FolderRevision>() {
      @Override
      public void checkout(FolderRevision ref, Path workdir)
          throws RepoException, ValidationException {
        try {
          FileUtil.copyFilesRecursively(ref.path, workdir, copySymlinkStrategy, originFiles);
          FileUtil.addPermissionsAllRecursively(workdir, FILE_PERMISSIONS);
        } catch (AbsoluteSymlinksNotAllowed e) {
          throw new ValidationException(
              String.format("Cannot copy files into the workdir: Some"
                        + " symlinks refer to locations outside of the folder and"
                        + " 'materialize_outside_symlinks' config option was not used:\n"
                        + "  %s -> %s\n", e.getSymlink(), e.getDestinationFile()));
        } catch (IOException e) {
          throw new RepoException(
              String.format(
                  "Cannot copy files into the workdir:\n"
                      + "  origin folder: %s\n"
                      + "  workdir: %s",
                  ref.path, workdir),
              e);
        }
      }

      @Override
      public ChangesResponse<FolderRevision> changes(
          @Nullable FolderRevision fromRef, FolderRevision toRef) throws RepoException {
        // Ignore fromRef since a folder doesn't have history of changes
        return ChangesResponse.forChanges(ImmutableList.of(change(toRef)));
      }

      @Override
      public boolean supportsHistory() {
        return false;
      }

      @Override
      public Change<FolderRevision> change(FolderRevision ref) throws RepoException {
        return new Change<>(ref, author, message, ref.readTimestamp(), ImmutableListMultimap.of());
      }

      @Override
      public void visitChanges(FolderRevision start, ChangesVisitor visitor) throws RepoException {
        visitor.visit(change(start));
      }
    };
  }

  @Override
  public String getLabelName() {
    return LABEL_NAME;
  }

  @Override
  public String getType() {
    return "folder.origin";
  }

  @Override
  public ImmutableSetMultimap<String, String> describe(Glob destinationFiles) {
    return ImmutableSetMultimap.of("type", getType());
  }
}
