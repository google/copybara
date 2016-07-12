package com.google.copybara.folder;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.Destination;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.RepoException;
import com.google.copybara.TransformResult;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.doc.annotations.DocElement;
import com.google.copybara.util.CommandUtil;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.shell.Command;
import com.google.devtools.build.lib.shell.CommandException;
import com.google.devtools.build.lib.shell.ShellUtils;

import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.annotation.Nullable;

/**
 * Writes the output tree to a local destination. Any file that is not excluded in the configuration
 * gets deleted before writing the new files.
 */
public class FolderDestination implements Destination {

  private static final String FOLDER_DESTINATION_NAME = "!FolderDestination";

  private final Path localFolder;
  private final boolean verbose;

  private FolderDestination(Path localFolder, boolean verbose) {
    this.localFolder = Preconditions.checkNotNull(localFolder);
    this.verbose = verbose;
  }

  @Override
  public void process(TransformResult transformResult, Console console)
      throws RepoException, IOException {
    console.progress("FolderDestination: creating " + localFolder);
    try {
      Files.createDirectories(localFolder);
    } catch (FileAlreadyExistsException e) {
      // This exception message is particularly bad and we don't want to treat it as unhandled
      throw new RepoException("Cannot create '" + localFolder + "' because '" + e.getFile()
          + "' already exists and is not a directory");
    }
    console.progress("FolderDestination: deleting previous data from " + localFolder);

    FileUtil.deleteFilesRecursively(localFolder,
        FileUtil.notPathMatcher(
            transformResult.getExcludedDestinationPaths().relativeTo(localFolder)));

    console.progress(
        "FolderDestination: Copying contents of the workdir to " + localFolder);
    try {
      // Life is too short to implement a recursive copy in Java... Let's wait until
      // we need Windows support. This also should be faster than copy and we don't need the
      // workdir after the destination is executed.
      CommandUtil.executeCommand(
          new Command(new String[]{"/bin/sh", "-cxv",
              "cp -aR * " + ShellUtils.shellEscape(localFolder.toString())},
              ImmutableMap.<String, String>of(), transformResult.getPath().toFile()), verbose);
    } catch (CommandException e) {
      throw new RepoException(
          "Cannot copy contents of " + transformResult.getPath() + " to " + localFolder,
          e);
    }
  }

  @Nullable
  @Override
  public String getPreviousRef(String labelName) {
    // Not supported
    return null;
  }

  @Override
  public String getLabelNameWhenOrigin() {
    throw new UnsupportedOperationException(FOLDER_DESTINATION_NAME + " does not support labels");
  }

  @DocElement(yamlName = FOLDER_DESTINATION_NAME, elementKind = Destination.class,
      description = "A folder destination is a destination that puts the output in a folder.",
      flags = FolderDestinationOptions.class)
  public static class Yaml implements Destination.Yaml {

    @Override
    public Destination withOptions(Options options, String configName, boolean askConfirmation)
        throws ConfigValidationException {
      GeneralOptions generalOptions = options.get(GeneralOptions.class);
      generalOptions.console()
          .warn("Field 'askConfirmation' is ignored in FolderDestination.");
      // Lets assume we are in the same filesystem for now...
      FileSystem fs = generalOptions.getFileSystem();
      String localFolderOption = options.get(FolderDestinationOptions.class).localFolder;
      if (Strings.isNullOrEmpty(localFolderOption)) {
        throw new ConfigValidationException(
            "--folder-dir is required with FolderDestination destination");
      }
      Path localFolder = fs.getPath(localFolderOption);
      if (!localFolder.isAbsolute()) {
        localFolder = fs.getPath(System.getProperty("user.dir")).resolve(localFolder);
      }

      return new FolderDestination(localFolder, generalOptions.isVerbose());
    }

  }
}
