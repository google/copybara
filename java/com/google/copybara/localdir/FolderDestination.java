package com.google.copybara.localdir;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.Destination;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.RepoException;
import com.google.copybara.config.ConfigValidationException;
import com.google.copybara.util.CommandUtil;
import com.google.copybara.util.FileUtil;
import com.google.devtools.build.lib.shell.Command;
import com.google.devtools.build.lib.shell.CommandException;
import com.google.devtools.build.lib.shell.ShellUtils;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.util.List;

/**
 * Writes the output tree to a local destination. Any file that is not excluded in the configuration
 * gets deleted before writing the new files.
 */
public class FolderDestination implements Destination {

  private final PathMatcher excludeFromDeletion;
  private final Path localFolder;
  private final boolean verbose;

  private FolderDestination(PathMatcher excludeFromDeletion, Path localFolder, boolean verbose) {
    this.excludeFromDeletion = excludeFromDeletion;
    this.localFolder = localFolder;
    this.verbose = verbose;
  }

  @Override
  public void process(Path workdir) throws RepoException, IOException {
    Files.createDirectories(localFolder);
    FileUtil.deleteFilesRecursively(localFolder, FileUtil.notPathMatcher(excludeFromDeletion));
    try {
      // Life is too short to implement a recursive copy in Java... Let's wait until
      // we need Windows support. This also should be faster than copy and we don't need the
      // workdir after the destination is executed.
      CommandUtil.executeCommand(
          new Command(new String[]{"/bin/sh", "-cxv",
              "cp -aR * " + ShellUtils.shellEscape(localFolder.toString())},
              ImmutableMap.<String, String>of(), workdir.toFile()), verbose);
    } catch (CommandException e) {
      throw new RepoException("Cannot copy contents of " + workdir + " to " + localFolder, e);
    }
  }

  public static class Yaml implements Destination.Yaml {

    List<String> excludePathsForDeletion = ImmutableList.of();

    public void setExcludePathsForDeletion(List<String> excludePathsForDeletion) {
      this.excludePathsForDeletion = excludePathsForDeletion;
    }

    @Override
    public Destination withOptions(Options options) {
      ImmutableList.Builder<PathMatcher> pathMatchers = ImmutableList.builder();
      GeneralOptions generalOptions = options.getOption(GeneralOptions.class);
      // Lets assume we are in the same filesystem for now...
      FileSystem fs = generalOptions.getWorkdir().getFileSystem();
      String localFolderOption = options.getOption(LocalDestinationOptions.class).localFolder;
      if (Strings.isNullOrEmpty(localFolderOption)) {
        throw new ConfigValidationException(
            "--folder-dir is required with FolderDestination destination");
      }
      Path localFolder = fs.getPath(localFolderOption);
      if (!localFolder.isAbsolute()) {
        localFolder = fs.getPath(System.getProperty("user.dir")).resolve(localFolder);
      }
      for (String path : excludePathsForDeletion) {
        pathMatchers.add(FileUtil.relativeGlob(localFolder, path));
      }
      return new FolderDestination(FileUtil.anyPathMatcher(pathMatchers.build()), localFolder,
          generalOptions.isVerbose());
    }
  }
}
