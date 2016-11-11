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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.Change;
import com.google.copybara.Origin;
import com.google.copybara.RepoException;
import com.google.copybara.ValidationException;
import com.google.copybara.util.AbsoluteSymlinksNotAllowed;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.FileUtil.CopySymlinkStrategy;
import com.google.copybara.util.Glob;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import javax.annotation.Nullable;

/**
 * Use a folder as the input for the migration.
 */
public class FolderOrigin implements Origin<FolderReference> {

  private static final String LABEL_NAME = "FolderOrigin-RevId";
  private final FileSystem fs;
  private final Author author;
  private final String message;
  private final CopySymlinkStrategy copySymlinkStrategy;

  FolderOrigin(FileSystem fs, Author author, String message, boolean materializeOutsideSymlinks) {
    this.fs = Preconditions.checkNotNull(fs);
    this.author = author;
    this.message = message;
    this.copySymlinkStrategy = materializeOutsideSymlinks
        ? CopySymlinkStrategy.MATERIALIZE_OUTSIDE_SYMLINKS
        : CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS;
  }

  @Override
  public FolderReference resolve(@Nullable String reference) throws RepoException {
    if (reference == null) {
      throw new RepoException(""
          + "A path is expected as reference in the command line. Invoke copybara as:\n"
          + "    copybara copy.bara.sky workflow_name ORIGIN_FOLDER");
    }
    Path path = fs.getPath(reference);
    if (!Files.exists(path)) {
      throw new RepoException(path + " folder doesn't exist");
    } else if (!Files.isDirectory(path)) {
      throw new RepoException(path + " is not a folder");
    } else if (!Files.isReadable(path) || !Files.isExecutable(path)) {
      throw new RepoException(path + " is not readable/executable");
    }

    return new FolderReference(path, Instant.now(), LABEL_NAME);
  }

  @Override
  public Reader<FolderReference> newReader(Glob originFiles, Authoring authoring)
      throws ValidationException {
    return new Reader<FolderReference>() {
      @Override
      public void checkout(FolderReference ref, Path workdir) throws RepoException {
        try {
          FileUtil.copyFilesRecursively(ref.path, workdir, copySymlinkStrategy);
        } catch (AbsoluteSymlinksNotAllowed e) {
          throw new RepoException(String.format("Cannot copy files into the workdir: Some symlinks"
              + " refer to locations outside of the folder and 'materialize_outside_symlinks'"
              + " config option was not used:\n"
              + "  %s -> %s\n", e.getSymlink(), e.getDestinationFile()));
        } catch (IOException e) {
          throw new RepoException(String.format("Cannot copy files into the workdir:\n"
                      + "  origin folder: %s\n"
                      + "  workdir: %s",
                  ref.path, workdir), e);
        }
      }

      @Override
      public ImmutableList<Change<FolderReference>> changes(@Nullable FolderReference fromRef,
          FolderReference toRef) throws RepoException {
        // Ignore fromRef since a folder doesn't have history of changes
        return ImmutableList.of(change(toRef));
      }

      @Override
      public boolean supportsHistory() {
        return false;
      }

      @Override
      public Change<FolderReference> change(FolderReference ref) throws RepoException {
        ZonedDateTime time = ZonedDateTime.ofInstant(
            Preconditions.checkNotNull(ref.readTimestamp()), ZoneId.systemDefault());
        return new Change<>(ref, author, message, time, ImmutableMap.of());
      }

      @Override
      public void visitChanges(FolderReference start, ChangesVisitor visitor)
          throws RepoException {
        visitor.visit(change(start));
      }
    };
  }

  @Override
  public String getLabelName() {
    return LABEL_NAME;
  }
}
