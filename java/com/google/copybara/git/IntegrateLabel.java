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
import com.google.copybara.LabelFinder;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;

/**
 * A label value that describes what to integrate.
 */
public interface IntegrateLabel {

  /**
   * Get the merge message
   */
  String mergeMessage(ImmutableList<LabelFinder> labelsToAdd);

  /**
   * Get the revision to integrate
   */
  GitRevision getRevision() throws RepoException, ValidationException;

  static IntegrateLabel genericGitRevision(GitRevision revision) {
    Preconditions.checkNotNull(revision);
    return new IntegrateLabel() {
      @Override
      public String mergeMessage(ImmutableList<LabelFinder> labelsToAdd) {
        return IntegrateLabel.withLabels("Merge of " + revision.getSha1(), labelsToAdd);
      }

      @Override
      public GitRevision getRevision() {
        return revision;
      }
    };
  }

  static String withLabels(String msg, ImmutableList<LabelFinder> labelsToAdd) {
    ChangeMessage result = ChangeMessage.parseMessage(msg);
    for (LabelFinder labelFinder : labelsToAdd) {
      result = result.withLabel(
          labelFinder.getName(), labelFinder.getSeparator(), labelFinder.getValue());
    }
    return result.toString();
  }
}
