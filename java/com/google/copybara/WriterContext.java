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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import javax.annotation.Nullable;

/**
 * Writer context which includes all the information for creating a writer
 */
public class WriterContext {

  private final String workflowName;
  private final String workflowIdentityUser;
  private final boolean dryRun;
  private final Revision originalRevision;
  private final ImmutableSet<String> roots;

  public WriterContext(String workflowName,
      @Nullable String workflowIdentityUser,
      boolean dryRun,
      Revision originalRevision,
      ImmutableSet<String> roots) {
    this.workflowName = Preconditions.checkNotNull(workflowName);
    this.workflowIdentityUser = workflowIdentityUser != null
        ? workflowIdentityUser
        : System.getProperty("user.name");
    this.dryRun = dryRun;
    this.originalRevision = Preconditions.checkNotNull(originalRevision);
    this.roots = Preconditions.checkNotNull(roots);
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

  public ImmutableSet<String> getRoots() {
    return roots;
  }
}
