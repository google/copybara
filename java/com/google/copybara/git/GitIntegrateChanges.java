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

package com.google.copybara.git;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.CannotResolveRevisionException;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LabelFinder;
import com.google.copybara.RepoException;
import com.google.copybara.TransformResult;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Integrate changes from a url present in the migrated change label.
 */

@SkylarkModule(name = "git_integrate",
    category = SkylarkModuleCategory.BUILTIN, documented = false, doc = "")
public class GitIntegrateChanges {

  private static final Logger logger = Logger.getLogger(GitDestination.class.getName());

  private final String label;
  private final Strategy strategy;
  private final boolean ignoreErrors;

  GitIntegrateChanges(String label, Strategy strategy, boolean ignoreErrors) {
    this.label = Preconditions.checkNotNull(label);
    this.strategy = Preconditions.checkNotNull(strategy);
    this.ignoreErrors = ignoreErrors;
  }

  /**
   * Perform an integrate of changes for matching labels.
   *
   * @throws CannotIntegrateException if a change cannot be integrated due to a user error
   * @throws RepoException if a git related error happens during the integrate
   */
  void integrate(GitRepository repository, GeneralOptions generalOptions,
      GitDestinationOptions gitDestinationOptions, TransformResult result)
      throws CannotIntegrateException, RepoException {
    try {
      doIntegrate(repository, generalOptions, result);
    } catch (CannotIntegrateException e) {
      if (gitDestinationOptions.ignoreIntegrationErrors || ignoreErrors) {
        logger.log(Level.WARNING, "Cannot integrate changes", e);
      } else {
        throw e;
      }
    } catch (RepoException e) {
      if (gitDestinationOptions.ignoreIntegrationErrors || ignoreErrors) {
        logger.log(Level.SEVERE, "Cannot integrate changes", e);
      } else {
        throw e;
      }
    }
  }

  private void doIntegrate(GitRepository repository, GeneralOptions generalOptions,
      TransformResult result) throws CannotIntegrateException, RepoException {

    for (LabelFinder label : result.findAllLabels()) {
      if (!label.isLabel() || !this.label.equals(label.getName())) {
        continue;
      }
      if (label.getValue().isEmpty()) {
        throw new CannotIntegrateException("Found an empty value for label %s", this.label);
      }
      try (ProfilerTask ignore = generalOptions.profiler().start("integrate",
          ImmutableMap.of("URL", label.getValue()))) {
        GitRevision gitRevision = GitRepoType.GIT.resolveRef(repository, /*repoUrl=*/null,
            label.getValue(), generalOptions);
        strategy.integrate(repository, gitRevision);
      } catch (CannotResolveRevisionException e) {
        throw new CannotIntegrateException(e, "Error resolving %s", label.getValue());
      }
    }
  }

  /**
   * What should we do when we find a change to be integrated
   */
  public enum Strategy {
    /**
     * A simple git fake-merge: Ignore any content from the change url.
     */
    FAKE_MERGE,
    /**
     * An hybrid that includes the changes that don't match destination_files but fake-merges
     * the rest.
     */
    FAKE_MERGE_AND_INCLUDE_FILES,
    /**
     * Include changes that don't match destination_files but don't create a merge commit.
     */
    INCLUDE_FILES;

    void integrate(GitRepository repository, GitRevision gitRevision)
        throws CannotIntegrateException, RepoException {
      throw new CannotIntegrateException(this + " integrate mode is still not supported");
    }
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("label", label)
        .add("strategy", strategy)
        .add("ignoreErrors", ignoreErrors)
        .toString();
  }
}
