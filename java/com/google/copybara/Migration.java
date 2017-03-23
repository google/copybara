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

import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.config.ConfigFile;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;

/**
 * A migration is a process that moves files at a particular revision from one/many systems to
 * one/many destinations.
 *
 * <p>For helping with the migration a working directory is provided to do any temporary file
 * operations.
 */
public interface Migration {

  /**
   * Run a migration for a source reference. If the source reference is not present the default
   * (if any) will be used.
   *
   * @param workdir a working directory for doing file operations if needed.
   * @param sourceRef the source revision to be migrated. If not present the default (if any) for
   * the migration will be used.
   * @throws RepoException if an error happens while accessing the repository
   * @throws IOException if any generic I/O error happen during the process
   * @throws ValidationException if during the execution an error attributable to miss
   * configuration
   * is detected.
   */
  void run(Path workdir, @Nullable String sourceRef)
      throws RepoException, IOException, ValidationException;

  default Info getInfo() throws RepoException, ValidationException {
    return Info.EMPTY;
  }

  /**
   * @return The migration's name.
   */
  String getName();


  /**
   * @return The migration's mode.
   */
  String getModeString();

  /**
   * @return The migration's main config file.
   */
  ConfigFile<?> getMainConfigFile();

  /**
   * Returns a multimap containing enough data to fingerprint the origin for validation purposes.
   */
  ImmutableSetMultimap<String, String> getOriginDescription();

  /**
   * Returns a multimap containing enough data to fingerprint the destination for validation
   * purposes.
   */
  ImmutableSetMultimap<String, String> getDestinationDescription();
}
