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

package com.google.copybara;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.buildozer.BuildozerModule;
import com.google.copybara.buildozer.BuildozerOptions;
import com.google.copybara.folder.FolderDestinationOptions;
import com.google.copybara.folder.FolderModule;
import com.google.copybara.folder.FolderOriginOptions;
import com.google.copybara.format.BuildifierOptions;
import com.google.copybara.format.FormatModule;
import com.google.copybara.git.GerritOptions;
import com.google.copybara.git.GitDestinationOptions;
import com.google.copybara.git.GitHubDestinationOptions;
import com.google.copybara.git.GitHubOptions;
import com.google.copybara.git.GitHubPrOriginOptions;
import com.google.copybara.git.GitMirrorOptions;
import com.google.copybara.git.GitModule;
import com.google.copybara.git.GitOptions;
import com.google.copybara.git.GitOriginOptions;
import com.google.copybara.go.GoModule;
import com.google.copybara.hg.HgModule;
import com.google.copybara.hg.HgOptions;
import com.google.copybara.hg.HgOriginOptions;
import com.google.copybara.onboard.GeneratorOptions;
import com.google.copybara.re2.Re2Module;
import com.google.copybara.remotefile.RemoteFileModule;
import com.google.copybara.remotefile.RemoteFileOptions;
import com.google.copybara.toml.TomlModule;
import com.google.copybara.transform.debug.DebugOptions;
import com.google.copybara.transform.metadata.MetadataModule;
import com.google.copybara.transform.patch.PatchModule;
import com.google.copybara.transform.patch.PatchingOptions;
import com.google.copybara.util.console.Console;
import com.google.copybara.xml.XmlModule;
import java.nio.file.FileSystem;
import java.util.Map;
import java.util.function.Function;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.lib.json.Json;

/**
 * A supplier of modules and {@link Option}s for Copybara.
 */
public class ModuleSupplier {

  private static final ImmutableSet<Class<?>> BASIC_MODULES = ImmutableSet.of(
      CoreGlobal.class);
  private final Map<String, String> environment;
  private final FileSystem fileSystem;
  private final Console console;

  public ModuleSupplier(Map<String, String> environment, FileSystem fileSystem,
      Console console) {
    this.environment = Preconditions.checkNotNull(environment);
    this.fileSystem = Preconditions.checkNotNull(fileSystem);
    this.console = Preconditions.checkNotNull(console);
  }

  /**
   * Returns the {@code set} of modules available.
   * TODO(malcon): Remove once no more static modules exist.
   */
  protected ImmutableSet<Class<?>> getStaticModules() {
    return BASIC_MODULES;
  }

  /**
   * Get non-static modules available
   */
  public ImmutableSet<Object> getModules(Options options) {
    GeneralOptions general = options.get(GeneralOptions.class);
    FolderModule folderModule = new FolderModule(
        options.get(FolderOriginOptions.class),
        options.get(FolderDestinationOptions.class),
        general);
    return ImmutableSet.of(
        new Core(general, options.get(WorkflowOptions.class), options.get(DebugOptions.class),
                 folderModule),
        new GitModule(options), new HgModule(options),
        folderModule,
        new FormatModule(
            options.get(WorkflowOptions.class), options.get(BuildifierOptions.class), general),
        new BuildozerModule(
            options.get(WorkflowOptions.class), options.get(BuildozerOptions.class)),
        new PatchModule(options.get(PatchingOptions.class)),
        new MetadataModule(),
        new Authoring.Module(),
        new RemoteFileModule(options),
        new Re2Module(),
        new TomlModule(),
        new XmlModule(),
        new StructModule(),
        new StarlarkDateTimeModule(),
        new GoModule(options.get(RemoteFileOptions.class)),
        Json.INSTANCE);
  }

  /** Returns a new list of {@link Option}s. */
  protected Options newOptions() {
    GeneralOptions generalOptions = new GeneralOptions(environment, fileSystem, console);
    GitOptions gitOptions = new GitOptions(generalOptions);
    GitDestinationOptions gitDestinationOptions =
        new GitDestinationOptions(generalOptions, gitOptions);
    BuildifierOptions buildifierOptions = new BuildifierOptions();
    WorkflowOptions workflowOptions = new WorkflowOptions();
    return new Options(ImmutableList.of(
        generalOptions,
        buildifierOptions,
        new BuildozerOptions(generalOptions, buildifierOptions, workflowOptions),
        new FolderDestinationOptions(),
        new FolderOriginOptions(),
        gitOptions,
        new GitOriginOptions(),
        new GitHubPrOriginOptions(),
        gitDestinationOptions,
        new GitHubOptions(generalOptions, gitOptions),
        new GitHubDestinationOptions(),
        new GerritOptions(generalOptions, gitOptions),
        new GitMirrorOptions(),
        new HgOptions(generalOptions),
        new HgOriginOptions(),
        new PatchingOptions(generalOptions),
        workflowOptions,
        new RemoteFileOptions(),
        new DebugOptions(generalOptions),
        new GeneratorOptions()));
  }

  /**
   * A ModuleSet contains the collection of modules and flags for one Skylark copy.bara.sky
   * evaluation/execution.
   */
  public final ModuleSet create() {
    Options options = newOptions();
    return createWithOptions(options);
  }

  public final ModuleSet createWithOptions(Options options) {
    return new ModuleSet(options, getStaticModules(), modulesToVariableMap(options));
  }

  private ImmutableMap<String, Object> modulesToVariableMap(Options options) {
    return getModules(options).stream()
        .collect(ImmutableMap.toImmutableMap(
            this::findClosestStarlarkBuiltinName,
            Function.identity()));
  }

  private String findClosestStarlarkBuiltinName(Object o) {
    Class<?> cls = o.getClass();
    while (cls != null && cls != Object.class) {
      StarlarkBuiltin annotation = cls.getAnnotation(StarlarkBuiltin.class);
      if (annotation != null) {
        return annotation.name();
      }
      cls = cls.getSuperclass();
    }
    throw new IllegalStateException("Cannot find @StarlarkBuiltin for " + o.getClass());
  }
}
