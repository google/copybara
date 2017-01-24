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

import static com.google.common.base.StandardSystemProperty.USER_HOME;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.copybara.folder.FolderDestinationOptions;
import com.google.copybara.folder.FolderModule;
import com.google.copybara.folder.FolderOriginOptions;
import com.google.copybara.git.GerritOptions;
import com.google.copybara.git.GitDestinationOptions;
import com.google.copybara.git.GitMirrorOptions;
import com.google.copybara.git.GitModule;
import com.google.copybara.git.GitOptions;
import com.google.copybara.modules.PatchModule;
import com.google.copybara.transform.metadata.MetadataModule;

/**
 * A supplier of modules and {@link Option}s for Copybara.
 */
public class ModuleSupplier {

  private static final ImmutableSet<Class<?>> BASIC_MODULES = ImmutableSet.of(
      FolderModule.class,
      GitModule.class,
      MetadataModule.class,
      PatchModule.class);

  private final String homeDir;

  /**
   * Creates a new instance using the {@code USER_HOME} as the home dir.
   */
  public ModuleSupplier() {
    this(USER_HOME.value());
  }

  /**
   * Creates a new instance with the given {@code homeDir}.
   */
  public ModuleSupplier(String homeDir) {
    this.homeDir = Preconditions.checkNotNull(homeDir);
  }

  /**
   * Returns the {@code set} of modules available.
   */
  public ImmutableSet<Class<?>> getModules() {
    return BASIC_MODULES;
  }

  /**
   * Returns a new list of {@link Option}s.
   */
  public ImmutableList<Option> newOptions() {
    return ImmutableList.of(
        new FolderDestinationOptions(),
        new FolderOriginOptions(),
        new GitOptions(homeDir),
        new GitDestinationOptions(),
        new GitMirrorOptions(),
        new GerritOptions(),
        new WorkflowOptions());
  }
}