// Copyright 2016 Google Inc. All Rights Reserved.
package com.google.copybara.folder;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.copybara.Core;
import com.google.copybara.Destination;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.RepoException;
import com.google.copybara.TransformResult;
import com.google.copybara.config.OptionsAwareModule;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.EvalException;
import java.io.IOException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.annotation.Nullable;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;

/**
 * Writes the output tree to a local destination. Any file that is not excluded in the configuration
 * gets deleted before writing the new files.
 */
public class FolderDestination implements Destination {

  private static final String FOLDER_DESTINATION_NAME = "!FolderDestination";

  private final Path localFolder;

  private FolderDestination(Path localFolder) {
    this.localFolder = Preconditions.checkNotNull(localFolder);
  }

  @Override
  public Writer newWriter() {
    return new WriterImpl();
  }

  private class WriterImpl implements Writer {
    @Override
    public WriterResult write(TransformResult transformResult, Console console)
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

      console.progress("FolderDestination: Copying contents of the workdir to " + localFolder);
      FileUtil.copyFilesRecursively(transformResult.getPath(), localFolder);
      return WriterResult.OK;
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

  @SkylarkModule(
      name = "folder",
      doc = "Module for dealing with local filesytem folders",
      category = SkylarkModuleCategory.BUILTIN)
  public static class Module implements OptionsAwareModule {

    private static final String DESTINATION_VAR = "destination";
    private static final DateTimeFormatter FOLDER_DATE_FORMAT =
        new DateTimeFormatterBuilder().appendPattern("yyyy_MM_dd_HH_mm_ss").toFormatter();

    private Options options;

    @SkylarkSignature(name = DESTINATION_VAR, returnType = Destination.class,
        doc = "A folder destination is a destination that puts the output in a folder",
        parameters = {
            @Param(name = "self", type = Module.class, doc = "this object"),
        },
        objectType = Module.class, useLocation = true, useEnvironment = true)
    @UsesFlags(FolderDestinationOptions.class)
    public static final BuiltinFunction destination = new BuiltinFunction(DESTINATION_VAR) {
      public Destination invoke(Module self, Location location, Environment env)
          throws EvalException {
        Path defaultRootPath =
            self.options.get(GeneralOptions.class).getHomeDir().resolve("copybara/out/");

        GeneralOptions generalOptions = self.options.get(GeneralOptions.class);
        String configName = Core.getProjectNameOrFail(env, location);
        // Lets assume we are in the same filesystem for now...
        FileSystem fs = generalOptions.getFileSystem();
        String localFolderOption = self.options.get(FolderDestinationOptions.class).localFolder;
        Path localFolder;
        if (Strings.isNullOrEmpty(localFolderOption)) {
          localFolder = defaultRootPath
              .resolve(configName.replaceAll("[^A-Za-z0-9]", ""))
              .resolve(DateTime.now().toString(FOLDER_DATE_FORMAT));
          generalOptions.console().info(
              String.format("Using folder '%s' in default root. Use --folder-dir to override.",
                  localFolder));
        } else {
          localFolder = fs.getPath(localFolderOption);
          if (!localFolder.isAbsolute()) {
            localFolder = generalOptions.getCwd().resolve(localFolder);
          }
        }
        return new FolderDestination(localFolder);
      }
    };

    @Override
    public void setOptions(Options options) {
      this.options = options;
    }
  }
}
