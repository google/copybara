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
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Arguments for a command that expects the CLI arguments be like: <code>config_file [workflow
 * [source_ref]]</code>
 */
public final class ConfigFileArgs {

  private final String configPath;
  @Nullable
  private final String workflowName;
  private final ImmutableList<String> sourceRefs;

  ConfigFileArgs(String configPath, @Nullable String workflowName) {
    this(configPath, workflowName, ImmutableList.of());
  }

  ConfigFileArgs(String configPath, @Nullable String workflowName, List<String> sourceRefs) {
    this.configPath = Preconditions.checkNotNull(configPath);
    this.workflowName = workflowName;
    this.sourceRefs = ImmutableList.copyOf(sourceRefs);
  }

  public String getConfigPath() {
    return configPath;
  }

  public String getWorkflowName() {
    return workflowName == null ? "default" : workflowName;
  }

  public boolean hasWorkflowName() {
    return workflowName != null;
  }

  /**
   * Returns the first sourceRef from the command arguments, or null if no source ref was provided.
   *
   * <p>This method is provided for convenience, for subocmmands that only care about the first
   * source_ref.
   */
  @Nullable
  public String getSourceRef() {
    return Iterables.getFirst(sourceRefs, /*default*/ null);
  }

  public ImmutableList<String> getSourceRefs() {
    return sourceRefs;
  }
}
