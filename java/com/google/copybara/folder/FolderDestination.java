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
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.Destination;
import com.google.copybara.DestinationEffect;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Revision;
import com.google.copybara.TransformResult;
import com.google.copybara.WriterContext;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.FileUtil.CopySymlinkStrategy;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * Writes the output tree to a local destination. Any file that is not excluded in the configuration
 * gets deleted before writing the new files.
 */
public class FolderDestination implements Destination<Revision> {
  private final Logger logger = Logger.getLogger(this.getClass().getName());

  private static final String FOLDER_DESTINATION_NAME = "folder.destination";
  private static final String HISTORY_NOT_SUPPORTED =
      String.format(
          "History not supported in %s. Consider passing a ref as an argument, or using "
              + "--last-rev.", FOLDER_DESTINATION_NAME);

  private final GeneralOptions generalOptions;
  private final FolderDestinationOptions folderDestinationOptions;

  FolderDestination(GeneralOptions generalOptions,
      FolderDestinationOptions folderDestinationOptions) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.folderDestinationOptions = Preconditions.checkNotNull(folderDestinationOptions);
  }

  @Override
  public Writer<Revision> newWriter(WriterContext writerContext) {
    if (writerContext.isDryRun()) {
      generalOptions.console().warn("--dry-run does not have any effect for folder.destination");
    }
    return new WriterImpl();
  }

  private class WriterImpl implements Writer<Revision> {

    @Nullable
    @Override
    public DestinationStatus getDestinationStatus(Glob destinationFiles, String labelName)
        throws ValidationException {
      throw new ValidationException(HISTORY_NOT_SUPPORTED);
    }

    @Override
    public void visitChanges(Revision start, ChangesVisitor visitor)
        throws ValidationException {
      throw new ValidationException(HISTORY_NOT_SUPPORTED);
    }

    @Override
    public boolean supportsHistory() {
      return false;
    }

    @Override
    public ImmutableList<DestinationEffect> write(TransformResult transformResult,
        Glob destinationFiles, Console console)
        throws ValidationException, RepoException, IOException {
      Path localFolder = getFolderPath(console);
      console.progress("FolderDestination: creating " + localFolder);
      boolean exists = Files.exists(localFolder);
      try {
        Files.createDirectories(localFolder);
      } catch (FileAlreadyExistsException e) {
        // This exception message is particularly bad and we don't want to treat it as unhandled
        throw new RepoException("Cannot create '" + localFolder + "' because '" + e.getFile()
            + "' already exists and is not a directory");
      } catch (AccessDeniedException e) {
        throw new ValidationException("Path is not accessible: " + localFolder, e);
      } catch (FileSystemException e) {
        if (e.getMessage().contains("Read-only file system")
            || e.getMessage().contains("Operation not permitted")) {
          throw new ValidationException("Path is not accessible: " + localFolder, e);
        }
        throw e;
      }
      console.progress("FolderDestination: Deleting destination files in " + localFolder);
      FileUtil.deleteFilesRecursively(localFolder, destinationFiles.relativeTo(localFolder));

      console.progress("FolderDestination: Copying contents of the workdir to " + localFolder);
      FileUtil.copyFilesRecursively(transformResult.getPath(), localFolder,
          CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS);
      return ImmutableList.of(
          new DestinationEffect(
              exists ? DestinationEffect.Type.UPDATED : DestinationEffect.Type.CREATED,
              String.format("Folder '%s' contains the output files of the migration", localFolder),
              transformResult.getChanges().getCurrent(),
              new DestinationEffect.DestinationRef(
                  localFolder.toString(), "local_folder", localFolder.toString())));
    }
  }

  private Path getFolderPath(Console console) throws IOException {
    String localFolderOption = folderDestinationOptions.localFolder;
    Path localFolder;
    if (Strings.isNullOrEmpty(localFolderOption)) {
      localFolder = generalOptions.getDirFactory().newTempDir("folder-destination");
      String msg = String.format(
          "Using folder in default root (--folder-dir to override): %s",
          localFolder.toAbsolutePath());
      logger.log(Level.INFO, msg);
      console.info(msg);
    } else {
      // Lets assume we are in the same filesystem for now...
      localFolder = generalOptions.getFileSystem().getPath(localFolderOption);
      if (!localFolder.isAbsolute()) {
        localFolder = generalOptions.getCwd().resolve(localFolder);
      }
    }

    // Normalize for console and other stuff that might require normalized paths
    return localFolder.normalize();
  }

  @Override
  public String getLabelNameWhenOrigin() throws ValidationException {
    throw new ValidationException(FOLDER_DESTINATION_NAME + " does not support labels");
  }

  @Override
  public String getType() {
    return "folder.destination";
  }

  @Override
  public ImmutableSetMultimap<String, String> describe(Glob destinationFiles) {
    return ImmutableSetMultimap.of("type", getType());
  }

}
