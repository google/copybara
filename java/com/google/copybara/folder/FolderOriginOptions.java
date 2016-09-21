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
public final class FolderOriginOptions implements Option {

  @Parameter(names = "--folder-origin-author",
      description = "Author of the change being migrated from folder.origin()")
  @VisibleForTesting
  public String author = "Copybara <noreply@copybara.io>";

  @Parameter(names = "--folder-origin-message",
      description = "Message of the change being migrated from folder.origin()")
  @VisibleForTesting
  public String message = "Copybara code migration";
}
