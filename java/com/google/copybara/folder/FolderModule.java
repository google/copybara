package com.google.copybara.folder;

import com.google.common.base.Strings;
import com.google.copybara.Author;
import com.google.copybara.Core;
import com.google.copybara.Destination;
import com.google.copybara.GeneralOptions;
import com.google.copybara.Options;
import com.google.copybara.Origin;
import com.google.copybara.config.base.OptionsAwareModule;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.EvalException;
import java.nio.file.FileSystem;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Main module that groups all the functions related to folders.
 */
@SkylarkModule(
    name = "folder",
    doc = "Module for dealing with local filesytem folders",
    category = SkylarkModuleCategory.BUILTIN)
public class FolderModule implements OptionsAwareModule {

  private static final String DESTINATION_VAR = "destination";
  private static final DateTimeFormatter FOLDER_DATE_FORMATTER =
      DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss");

  private Options options;

  @SuppressWarnings("unused")
  @SkylarkSignature(name = DESTINATION_VAR, returnType = Destination.class,
      doc = "A folder destination is a destination that puts the output in a folder",
      parameters = {
          @Param(name = "self", type = FolderModule.class, doc = "this object"),
      },
      objectType = FolderModule.class, useLocation = true, useEnvironment = true)
  @UsesFlags(FolderDestinationOptions.class)
  public static final BuiltinFunction DESTINATION = new BuiltinFunction(DESTINATION_VAR) {
    @SuppressWarnings("unused")
    public Destination invoke(FolderModule self, Location location, Environment env)
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
            .resolve(LocalDateTime.now().format(FOLDER_DATE_FORMATTER));
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

  @SuppressWarnings("unused")
  @SkylarkSignature(name = "origin", returnType = FolderOrigin.class,
      doc = "A folder origin is a origin that uses a folder as input",
      parameters = {
          @Param(name = "self", type = FolderModule.class, doc = "this object"),
      },
      objectType = FolderModule.class, useLocation = true, useEnvironment = true)
  @UsesFlags(FolderOriginOptions.class)
  public static final BuiltinFunction ORIGIN = new BuiltinFunction("origin") {
    @SuppressWarnings("unused")
    public FolderOrigin invoke(FolderModule self, Location location, Environment env)
        throws EvalException {

      GeneralOptions generalOptions = self.options.get(GeneralOptions.class);
      // Lets assume we are in the same filesystem for now...
      FileSystem fs = generalOptions.getFileSystem();
      return new FolderOrigin(fs,
          Author.parse(location, self.options.get(FolderOriginOptions.class).author),
          self.options.get(FolderOriginOptions.class).message);
    }
  };

  @Override
  public void setOptions(Options options) {
    this.options = options;
  }
}
