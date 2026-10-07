/*
 * Copyright (C) 2017 Google Inc.
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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.ChangeMessage;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LabelFinder;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import java.util.Optional;

/**
 * A label value that describes what to integrate.
 */
public interface IntegrateLabel {

  /** Get the merge message */
  String mergeMessage(ImmutableList<LabelFinder> labelsToAdd) throws ValidationException;

  /**
   * Get the revision to integrate
   */
  GitRevision getRevision() throws RepoException, ValidationException;

  static IntegrateLabel genericGitRevision(
      GitRepository repository,
      String repoUrl,
      String rawLabelValue,
      GeneralOptions generalOptions,
      GitOptions gitOptions,
      boolean allowProtocolRef)
      throws RepoException, ValidationException {
    String rawLabel = rawLabelValue.trim();
    if (rawLabel.contains("://")) {
      ValidationException.checkCondition(
          allowProtocolRef,
          "Protocol markers (://) are not allowed in integrate ref '%s' unless"
              + " 'allow_protocol_ref' is set.",
          rawLabel);
      int spaceIdx = rawLabel.lastIndexOf(' ');
      String labelUrl = spaceIdx != -1 ? rawLabel.substring(0, spaceIdx) : rawLabel;
      String repoUrlPrefix = repoUrl.endsWith("/") ? repoUrl : repoUrl + "/";
      boolean matchesDestination = labelUrl.equals(repoUrl) || labelUrl.startsWith(repoUrlPrefix);
      if (!matchesDestination) {
        if (gitOptions.allowIntegrateRef.isEmpty()) {
          throw new ValidationException(
              String.format(
                  "Integrate URL in '%s' must match or have destination repository URL '%s' as a"
                      + " prefix.",
                  rawLabel, repoUrl));
        }
        ValidationException.checkCondition(
            gitOptions.allowIntegrateRef.stream().anyMatch(labelUrl::startsWith),
            "Integrate URL in '%s' does not match destination repository URL '%s' or any allowed"
                + " prefix (%s).",
            rawLabel,
            repoUrl,
            gitOptions.allowIntegrateRef);
      }
    }
    // would derive url from repo remote, but there is no guarantee a remote is configured.
    GitRevision gitRevision =
        GitRepoType.GIT.resolveRef(
            repository,
            repoUrl,
            rawLabel,
            generalOptions,
            /* describeVersion= */ false,
            /* partialFetch= */ false,
            Optional.empty(),
            /* recurseSubmodules= */ false);
    return genericGitRevision(gitRevision);
  }

  static IntegrateLabel genericGitRevision(GitRevision revision) {
    Preconditions.checkNotNull(revision);
    return new IntegrateLabel() {
      @Override
      public String mergeMessage(ImmutableList<LabelFinder> labelsToAdd)
          throws ValidationException {
        return IntegrateLabel.withLabels("Merge of " + revision.getHash(), labelsToAdd);
      }

      @Override
      public GitRevision getRevision() {
        return revision;
      }

      @Override
      public String toString() {
        return "Merge of " + revision.getHash();
      }
    };
  }

  static String withLabels(String msg, ImmutableList<LabelFinder> labelsToAdd)
      throws ValidationException {
    ChangeMessage result = ChangeMessage.parseMessage(msg);
    for (LabelFinder labelFinder : labelsToAdd) {
      result = result.withLabel(
          labelFinder.getName(), labelFinder.getSeparator(), labelFinder.getValue());
    }
    return result.toString();
  }
}
