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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.ChangeMessage;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LabelFinder;
import com.google.copybara.TransformResult;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.git.GitDestination.MessageInfo;
import com.google.copybara.git.GitRepository.GitLogEntry;
import com.google.copybara.git.GitRepository.StatusCode;
import com.google.copybara.git.GitRepository.StatusFile;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.util.DirFactory;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import javax.annotation.Nullable;

/**
 * Integrate changes from a url present in the migrated change label.
 */

@SkylarkModule(name = "git_integrate",
    category = SkylarkModuleCategory.BUILTIN, documented = false, doc = "")
public class GitIntegrateChanges {

  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final String label;
  private final Strategy strategy;
  private final boolean ignoreErrors;
  private final boolean newGitIntegrate;

  GitIntegrateChanges(String label, Strategy strategy, boolean ignoreErrors,
      boolean newGitIntegrate) {
    this.label = Preconditions.checkNotNull(label);
    this.strategy = Preconditions.checkNotNull(strategy);
    this.ignoreErrors = ignoreErrors;
    this.newGitIntegrate = newGitIntegrate;
  }

  /**
   * Perform an integrate of changes for matching labels in the existing {@code repository} HEAD.
   *
   * @throws CannotIntegrateException if a change cannot be integrated due to a user error
   * @throws RepoException if a git related error happens during the integrate
   */
  void run(GitRepository repository, GeneralOptions generalOptions,
      MessageInfo messageInfo, Predicate<String> externalFileMatcher, TransformResult result,
      boolean ignoreIntegrationErrors) throws CannotIntegrateException, RepoException {
    try {
      doIntegrate(repository, generalOptions, externalFileMatcher, result, messageInfo);
    } catch (CannotIntegrateException e) {
      if (ignoreIntegrationErrors || ignoreErrors) {
        logger.atWarning().withCause(e).log("Cannot integrate changes");
        generalOptions.console().warnFmt("Cannot integrate changes: %s", e.getMessage());
      } else {
        throw e;
      }
    } catch (RepoException e) {
      if (ignoreIntegrationErrors || ignoreErrors) {
        logger.atSevere().withCause(e).log("Cannot integrate changes");
        generalOptions.console().warnFmt("Cannot integrate changes: %s", e.getMessage());
      } else {
        throw e;
      }
    }
  }

  private void doIntegrate(GitRepository repository, GeneralOptions generalOptions,
      Predicate<String> externalFiles, TransformResult result, MessageInfo messageInfo)
      throws CannotIntegrateException, RepoException {

    for (LabelFinder label : result.findAllLabels()) {
      if (!label.isLabel() || !this.label.equals(label.getName())) {
        continue;
      }
      if (label.getValue().isEmpty()) {
        throw new CannotIntegrateException("Found an empty value for label " + this.label);
      }
      try (ProfilerTask ignore = generalOptions.profiler().start("integrate",
          ImmutableMap.of("URL", label.getValue()))) {
        generalOptions.console().progress("Integrating change from " + label.getValue());
        IntegrateLabel integrateLabel = GitHubPRIntegrateLabel.parse(label.getValue(), repository,
            generalOptions);
        if (integrateLabel == null) {
          integrateLabel = GerritIntegrateLabel.parse(label.getValue(), repository,
              generalOptions);
          if (integrateLabel == null) {
            GitRevision gitRevision = GitRepoType.GIT.resolveRef(repository, /*repoUrl=*/null,
                                                                 label.getValue(), generalOptions,
                /*describeVersion=*/false);
            integrateLabel = IntegrateLabel.genericGitRevision(gitRevision);
          }
        }

        strategy.integrate(repository, integrateLabel, externalFiles, label,
            messageInfo, generalOptions.console(), generalOptions.getDirFactory(), newGitIntegrate);
      } catch (ValidationException e) {
        throw new CannotIntegrateException("Error resolving " + label.getValue(), e);
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
    FAKE_MERGE {
      @Override
      void integrate(GitRepository repository, IntegrateLabel integrateLabel,
          Predicate<String> externalFiles, LabelFinder rawLabelValue,
          MessageInfo messageInfo, Console console, DirFactory dirFactory, boolean newGitIntegrate)
          throws ValidationException, RepoException {
        GitLogEntry head = getHeadCommit(repository);

        String msg = integrateLabel.mergeMessage(messageInfo.labelsToAdd);
        // If there is already a merge, don't overwrite the merge but create a new one.
        // Otherwise amend the last commit as a merge.
        GitRevision commit = head.getParents().size() > 1
                             ? repository.commitTree(msg, head.getTree(),
            ImmutableList.of(head.getCommit(), integrateLabel.getRevision()))
                             : repository.commitTree(msg, head.getTree(),
                                 ImmutableList.<GitRevision>builder().addAll(head.getParents())
                                     .add(integrateLabel.getRevision()).build());
        repository.simpleCommand("update-ref", "HEAD", commit.getSha1());
      }
    },
    /**
     * An hybrid that includes the changes that don't match destination_files but fake-merges
     * the rest.
     */
    FAKE_MERGE_AND_INCLUDE_FILES {
      @Override
      void integrate(GitRepository repository, IntegrateLabel gitRevision,
          Predicate<String> externalFiles,
          LabelFinder rawLabelValue, MessageInfo messageInfo, Console console,
          DirFactory dirFactory, boolean newGitIntegrate)
          throws ValidationException, RepoException {
        // Fake merge first so that we have a commit and then amend that commit wit the external
        // files
        FAKE_MERGE.integrate(repository, gitRevision, externalFiles, rawLabelValue, messageInfo,
            console, dirFactory, newGitIntegrate);
        INCLUDE_FILES.integrate(repository, gitRevision, externalFiles, rawLabelValue, messageInfo,
            console, dirFactory, newGitIntegrate);
      }
    },
    /**
     * Include changes that don't match destination_files but don't create a merge commit.
     */
    INCLUDE_FILES {
      @Override
      void integrate(GitRepository repository, IntegrateLabel integrateLabel,
          Predicate<String> externalFiles, LabelFinder rawLabelValue, MessageInfo messageInfo,
          Console console, DirFactory dirFactory, boolean newGitIntegrate)
          throws ValidationException, RepoException {
        // Save HEAD commit before starting messing with the repo
        GitLogEntry head = getHeadCommit(repository);
        byte[] diff;
        if (newGitIntegrate) {
          diff = computeExternalDiff(repository, integrateLabel, externalFiles, head);
        } else {
          diff = repository.simpleCommandNoRedirectOutput("diff",
              head.getCommit().getSha1() + ".." + integrateLabel.getRevision().getSha1())
              .getStdoutBytes();
        }
        if (diff == null) {
          return;
        }
        try {
          // Apply the patch to the current branch.
          repository.apply(diff, /*index=*/true);
        } catch (RebaseConflictException e) {
          // Add more context information
          throw new CannotIntegrateException("Cannot apply the changes from " + integrateLabel, e);
        }

        List<String> toRevert = new ArrayList<>();
        for (StatusFile statusFile : repository.status()) {
          // Just in case the worktree is dirty
          if (statusFile.getIndexStatus() == StatusCode.UNMODIFIED) {
            continue;
          }
          if (statusFile.getIndexStatus() == StatusCode.COPIED) {
            revertIfInternal(toRevert, externalFiles, statusFile.getNewFileName());
          } else if (statusFile.getIndexStatus().equals(StatusCode.RENAMED)) {
            revertIfInternal(toRevert, externalFiles, statusFile.getFile());
            revertIfInternal(toRevert, externalFiles, statusFile.getNewFileName());
          } else {
            revertIfInternal(toRevert, externalFiles, statusFile.getFile());
          }
        }
        // Batch to prevent going over max arguments length.
        for (List<String> revertBatch : Lists.partition(toRevert, 20)) {
          ImmutableList.Builder<String> params = ImmutableList.<String>builder()
              .add("reset", "HEAD", "--");
          params.addAll(revertBatch);
          repository.simpleCommand(params.build().toArray(new String[0]));
        }
        ChangeMessage msg = ChangeMessage.parseAllAsLabels(head.getBody())
            .withRemovedLabelByNameAndValue(rawLabelValue.getName(), rawLabelValue.getValue());

        // Amend last commit with the external files and remove the integration label
        try {
          repository.commit(/*author=*/null, /*amend=*/true, /*timestamp=*/null, msg.toString());
        } catch (ValidationException ignore) {
          // This is expected. There might not be any external file
        }
        // Cleanup any non-comitted file
        repository.simpleCommand("reset", "--hard");
        repository.forceClean();
      }

      @Nullable
      private byte[] computeExternalDiff(GitRepository repository, IntegrateLabel integrateLabel,
          Predicate<String> externalFiles, GitLogEntry head)
          throws ValidationException, RepoException {
        String commonBaseline = findCommonBaseline(repository, integrateLabel, head);
        // Create a patch of the changes from common baseline..feature head.
        String diff = new String(repository.simpleCommandNoRedirectOutput("diff",
            commonBaseline + ".." + integrateLabel.getRevision().getSha1())
            .getStdoutBytes(), UTF_8);

        boolean include = true;
        // Filter the diff to the external files changed by the external change that weren't
        // migrated to the internal repository.
        StringBuilder filteredDiff = new StringBuilder();
        for (String line : Splitter.on("\n").split(diff)) {
          if (line.startsWith("diff ")) {
            List<String> diffHeader = Splitter.on(" ").splitToList(line);
            String path = diffHeader.get(3).substring(2);
            include = externalFiles.test(path);
          }
          if (include) {
            filteredDiff.append(line).append("\n");
          }
        }
        // Nothing to add
        if (filteredDiff.length() == 0) {
          return null;
        }
        return filteredDiff.toString().getBytes(UTF_8);
      }

      private String findCommonBaseline(GitRepository repository, IntegrateLabel integrateLabel,
          GitLogEntry head) throws ValidationException {
        GitRevision previousHead = Iterables.getFirst(head.getParents(), null);
        if (previousHead == null) {
          return head.getCommit().getSha1();
        }
        try {
          return repository.mergeBase(previousHead.getSha1(),
              integrateLabel.getRevision().getSha1());
        } catch (RepoException e) {
          logger.atWarning().log(
              "Cannot find common parent for previous head commit %s and integrate commit: %s.",
              previousHead, integrateLabel);
          return head.getCommit().getSha1();
        }
      }

      private void revertIfInternal(List<String> toRevert, Predicate<String> externalFiles,
          String file) {
        if (!externalFiles.test(file)) {
          toRevert.add(file);
        }
      }
    };

    private static GitLogEntry getHeadCommit(GitRepository repository) throws RepoException {
      return Iterables.getOnlyElement(repository.log("HEAD").withLimit(1).run());
    }

    void integrate(GitRepository repository, IntegrateLabel gitRevision,
        Predicate<String> externalFiles, LabelFinder rawLabelValue, MessageInfo messageInfo,
        Console console,
        DirFactory dirFactory, boolean newGitIntegrate)
        throws ValidationException, RepoException {
      throw new CannotIntegrateException(this + " integrate mode is still not supported");
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof GitIntegrateChanges)) {
      return false;
    }
    GitIntegrateChanges that = (GitIntegrateChanges) o;
    return ignoreErrors == that.ignoreErrors &&
        Objects.equals(label, that.label) &&
        strategy == that.strategy;
  }

  @Override
  public int hashCode() {
    return Objects.hash(label, strategy, ignoreErrors);
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
