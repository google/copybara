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

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LabelFinder;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;

import javax.annotation.Nullable;

/**
 * Integrate label for Gerrit changes
 *
 * <p>Returns a string like:
 *
 * <ul>
 *   <li>"Gerrit https://example.com/project 1271"</li>
 *   </li>"Gerrit https://example.com/project 1271 5"</li>
 *   </li>"Gerrit https://example.com/project 1271 ChangeId"</li>
 *   </li>"Gerrit https://example.com/project 1271 5 ChangeId"</li>
 *   </ul>
 * Where both the PatchSet and ChangeId are optional.
 */
class GerritIntegrateLabel implements IntegrateLabel {

  private static final Pattern LABEL_PATTERN = Pattern.compile("gerrit ([^ ]+)"
      + " ([0-9]+)(?: Patch Set ([0-9]+))?(?: (I[a-f0-9]+))?");

  private final GitRepository repository;
  private final GeneralOptions generalOptions;
  private final String url;
  private final int changeNumber;
  @Nullable
  private Integer patchSet;
  @Nullable
  private final String changeId;

  GerritIntegrateLabel(GitRepository repository, GeneralOptions generalOptions,
      String url,
      int changeNumber, @Nullable Integer patchSet,
      @Nullable String changeId) {
    this.repository = Preconditions.checkNotNull(repository);
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.url = Preconditions.checkNotNull(url);
    this.changeNumber = changeNumber;
    this.patchSet = patchSet;
    this.changeId = changeId;
  }

  @Nullable
  static GerritIntegrateLabel parse(String str, GitRepository repository,
      GeneralOptions generalOptions) {
    Matcher matcher = LABEL_PATTERN.matcher(str);
    return matcher.matches()
           ? new GerritIntegrateLabel(repository, generalOptions,
                                      matcher.group(1),
                                      Integer.parseInt(matcher.group(2)),
                                      (matcher.group(3) == null
                                       ? null
                                       : Integer.parseInt(matcher.group(3))),
                                      matcher.group(4))
           : null;
  }

  @Override
  public String toString() {
    return String.format("gerrit %s %d%s%s", url, changeNumber,
                         patchSet != null ? " Patch Set " + patchSet : "",
                         changeId != null ? " " + changeId : "");
  }

  @Override
  public String mergeMessage(ImmutableList<LabelFinder> labelsToAdd) {
    if (changeId != null) {
      labelsToAdd = ImmutableList.<LabelFinder>builder().addAll(labelsToAdd)
          .add(new LabelFinder("Change-Id: " + changeId)).build();
    }
    return IntegrateLabel.withLabels("Merge Gerrit change " + changeNumber
            + (patchSet == null ? "" : " Patch Set " + patchSet),
        labelsToAdd);
  }

  @Override
  public GitRevision getRevision() throws RepoException, ValidationException {
    int latestPatchSet = GerritChange.getGerritPatchSets(repository, url, changeNumber)
        .lastEntry().getKey();

    if (patchSet == null) {
      patchSet = latestPatchSet;
    } else if (latestPatchSet > patchSet) {
      generalOptions.console().warnFmt(
          "Change %s has more patch sets after Patch Set %s. Latest is Patch Set %s."
              + " Not all changes might be migrated", changeNumber, patchSet,
          latestPatchSet);
    }

    return GitRepoType.GERRIT.resolveRef(repository, url,
        String.format("refs/changes/%02d/%d", changeNumber % 100, changeNumber) + "/" + patchSet,
        generalOptions, /*describeVersion=*/false, /*partialFetch=*/ false);
  }
}
