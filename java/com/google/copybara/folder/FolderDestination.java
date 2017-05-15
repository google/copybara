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
import com.google.copybara.Destination;
import com.google.copybara.GeneralOptions;
import com.google.copybara.RepoException;
import com.google.copybara.Revision;
import com.google.copybara.TransformResult;
import com.google.copybara.ValidationException;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.FileUtil.CopySymlinkStrategy;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
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
  private final GeneralOptions generalOptions;
  private final FolderDestinationOptions folderDestinationOptions;

  FolderDestination(GeneralOptions generalOptions,
      FolderDestinationOptions folderDestinationOptions) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.folderDestinationOptions = Preconditions.checkNotNull(folderDestinationOptions);
  }

  @Override
  public Writer newWriter(Glob destinationFiles, boolean dryRun) {
    if (dryRun) {
      generalOptions.console().warn("--dry-run does not have any effect for folder.destination");
    }
    return new WriterImpl(destinationFiles);
  }

  private class WriterImpl implements Writer {

    final Glob destinationFiles;

    WriterImpl(Glob destinationFiles) {
      this.destinationFiles = destinationFiles;
    }

    @Nullable
    @Override
    public DestinationStatus getDestinationStatus(String labelName, @Nullable String groupId)
        throws RepoException {
      // Not supported
      return null;
    }

    @Override
    public boolean supportsStatus() {
      return false;
    }

    @Override
    public WriterResult write(TransformResult transformResult, Console console)
        throws ValidationException, RepoException, IOException {
      Path localFolder = getFolderPath(console);
      console.progress("FolderDestination: creating " + localFolder);
      try {
        Files.createDirectories(localFolder);
      } catch (FileAlreadyExistsException e) {
        // This exception message is particularly bad and we don't want to treat it as unhandled
        throw new RepoException("Cannot create '" + localFolder + "' because '" + e.getFile()
            + "' already exists and is not a directory");
      }
      console.progress("FolderDestination: deleting previous data from " + localFolder);

      FileUtil.deleteFilesRecursively(localFolder, destinationFiles.relativeTo(localFolder));

      console.progress("FolderDestination: Copying contents of the workdir to " + localFolder);
      FileUtil.copyFilesRecursively(transformResult.getPath(), localFolder,
          CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS);
      return WriterResult.OK;
    }
  }

  private Path getFolderPath(Console console) throws IOException {
    String localFolderOption = folderDestinationOptions.localFolder;
    Path localFolder;
    if (Strings.isNullOrEmpty(localFolderOption)) {
      localFolder = generalOptions.getTmpDirectoryFactory().newTempDirectory("folder-destination");
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
    return localFolder;
  }

  @Override
  public String getLabelNameWhenOrigin() {
    throw new UnsupportedOperationException(FOLDER_DESTINATION_NAME + " does not support labels");
  }
}
