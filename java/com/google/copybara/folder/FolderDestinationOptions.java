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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.annotations.VisibleForTesting;
import com.google.copybara.Option;

/**
 * Arguments for FolderDestination
 */
@Parameters(separators = "=")
public final class FolderDestinationOptions implements Option {

  @Parameter(names = "--folder-dir",
      description = "Local directory to write the output of the migration to. If the directory "
          + "exists, all files will be deleted. By default Copybara will generate a temporary "
          + "directory, so you shouldn't need this.")
  @VisibleForTesting
  public String localFolder = null;
}
