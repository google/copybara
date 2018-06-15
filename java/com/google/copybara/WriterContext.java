/*
 * Copyright (C) 2018 Google Inc.
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

import com.google.common.annotations.VisibleForTesting;
import com.google.copybara.Destination.Writer;
import com.google.copybara.util.Glob;
import javax.annotation.Nullable;
import com.google.common.base.Preconditions;

/**
 * Writer context which includes workflowName, destinationFiles, dryRun, revision, and oldWriter of
 * a migration.
 */
public class WriterContext<D extends Revision> {

  private final String workflowName;
  private final String workflowIdentityUser;
  private final Glob destinationFiles;
  private final boolean dryRun;
  private final Revision originalRevision;
  @Nullable private final Writer<D> oldWriter;

  @VisibleForTesting
  public WriterContext(
      String workflowName,
      @Nullable String workflowIdentityUser,
      Glob destinationFiles,
      boolean dryRun,
      Revision originalRevision,
      @Nullable Writer<D> oldWriter) {

    this.workflowName = Preconditions.checkNotNull(workflowName);
    this.workflowIdentityUser = workflowIdentityUser != null
        ? workflowIdentityUser
        : System.getProperty("user.name");
    this.destinationFiles = Preconditions.checkNotNull(destinationFiles);
    this.dryRun = dryRun;
    this.originalRevision = Preconditions.checkNotNull(originalRevision);
    this.oldWriter = oldWriter;
  }

  public Revision getOriginalRevision() {
    return originalRevision;
  }

  public String getWorkflowIdentityUser() {
    return workflowIdentityUser;
  }

  public String getWorkflowName() {
    return workflowName;
  }

  public boolean isDryRun() {
    return dryRun;
  }

  public Glob getDestinationFiles() {
    return destinationFiles;
  }

  public Writer<D> getOldWriter() {
    return oldWriter;
  }
}
