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

package com.google.copybara.hg;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.copybara.ChangeMessage;
import com.google.copybara.Destination;
import com.google.copybara.DestinationEffect;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LabelFinder;
import com.google.copybara.TransformResult;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.util.FileUtil;
import com.google.copybara.util.FileUtil.CopySymlinkStrategy;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import javax.annotation.Nullable;

/**
 * A Mercurial (Hg) repository destination.
 */
public class HgDestination implements Destination<HgRevision> {
  private static final String ORIGIN_LABEL_SEPARATOR = ": ";
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static class MessageInfo {
    final ImmutableList<LabelFinder> labelsToAdd;

    MessageInfo(ImmutableList<LabelFinder> labelsToAdd) {
      this.labelsToAdd = checkNotNull(labelsToAdd);
    }
  }

  private final String repoUrl;
  private final String fetch;
  private final String push;
  private final GeneralOptions generalOptions;

  HgDestination(String repoUrl, String fetch, String push, GeneralOptions generalOptions) {
    this.repoUrl = repoUrl;
    this.fetch = fetch;
    this.push = push;
    this.generalOptions = generalOptions;
  }

  @Override
  public Writer<HgRevision> newWriter (Glob destinationFiles, boolean dryRun,
      @Nullable String groupId, @Nullable Writer<HgRevision> oldWriter) {
    return new WriterImpl(repoUrl, fetch, push, generalOptions);
  }

  @Override
  public String getLabelNameWhenOrigin() {
    return HgRepository.HG_ORIGIN_REV_ID;
  }

  static class WriterImpl implements Writer<HgRevision> {
    private final String repoUrl;
    private final String remoteFetch;
    private final String remotePush;
    private final GeneralOptions generalOptions;
    private final boolean force;

    WriterImpl(String repoUrl, String remoteFetch, String remotePush,
        GeneralOptions generalOptions) {
      this.repoUrl = checkNotNull(repoUrl);
      this.remoteFetch = checkNotNull(remoteFetch);
      this.remotePush = checkNotNull(remotePush);
      this.generalOptions = generalOptions;
      this.force = generalOptions.isForced();
    }

    @Nullable
    @Override
    public DestinationStatus getDestinationStatus(String labelName)
        throws RepoException, ValidationException {
      throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public boolean supportsHistory() {
      throw new UnsupportedOperationException("Not implemented yet");
    }

    @Nullable
    private void pullFromRemote(Console console, HgRepository repo, String repoUrl,
        String reference) throws RepoException, ValidationException {
      try (ProfilerTask ignore =
          generalOptions.profiler().start("hg_destination_pull")) {
        console.progressFmt("Hg Destination: Pulling: %s from %s", reference, repoUrl);
        repo.pullFromRef(repoUrl, reference);
      } catch (CannotResolveRevisionException e) {
        String warning = String.format("Hg Destination: '%s' doesn't exist in '%s'",
            reference, repoUrl);
        if (!force) {
          throw new ValidationException(
              "%s. Use %s flag if you want to push anyway", warning, GeneralOptions.FORCE);
        }
        console.warn(warning);
      }
    }

    /**
     * Returns the message for a change with any labels, if set
     */
    public static ChangeMessage getChangeMessage(
        TransformResult transformResult, String originLabelSeparator) {
      MessageInfo messageInfo = new MessageInfo(transformResult.isSetRevId()
          ? ImmutableList.of(new LabelFinder(
              transformResult.getCurrentRevision().getLabelName() + originLabelSeparator
                  + transformResult.getCurrentRevision().asString()))
          : ImmutableList.of());
      ChangeMessage msg = ChangeMessage.parseMessage(transformResult.getSummary());
      for (LabelFinder label : messageInfo.labelsToAdd) {
        msg = msg.withNewOrReplacedLabel(label.getName(), label.getSeparator(), label.getValue());
      }
      return msg;
    }

    /**
     * Writes the changes in {@param transformResult} to the destination repository.
     */
    @Override
    public ImmutableList<DestinationEffect> write(TransformResult transformResult, Console console)
        throws ValidationException, RepoException, IOException {
      logger.atInfo().log("Exporting from %s to: %s", transformResult.getPath(), this);

      Path scratchRepoPath = generalOptions.getDirFactory().newTempDir("hg_scratch_repo");

      // Cannot specify a working tree for a hg repo, so copy workdir to a temporary repository
      FileUtil.copyFilesRecursively(transformResult.getPath(), scratchRepoPath,
          CopySymlinkStrategy.FAIL_OUTSIDE_SYMLINKS);
      HgRepository scratchRepo = new HgRepository(scratchRepoPath, true).init();

      console.progress("Hg Destination: Pulling from " + remoteFetch);
      pullFromRemote(console, scratchRepo, repoUrl, remoteFetch);
      scratchRepo.hg(scratchRepoPath, "update");

      console.progress("Hg Destination: Adding all files");
      scratchRepo.hg(scratchRepoPath, "add");

      //TODO(jlliu): include/exclude destinationFiles

      console.progress("Hg Destination: Creating a local commit");

      ChangeMessage msg = getChangeMessage(transformResult, ORIGIN_LABEL_SEPARATOR);
      String date = transformResult.getTimestamp().format(DateTimeFormatter.RFC_1123_DATE_TIME);
      scratchRepo.hg(scratchRepoPath, "commit", "--user",
          transformResult.getAuthor().toString(), "--date", date,
          "-m", msg.toString());

      console.progress(String.format("Hg Destination: Pushing to %s %s", repoUrl, remotePush));
      scratchRepo.hg(scratchRepoPath, "push", "--rev", remotePush, repoUrl);

      String tip = scratchRepo.identify("tip").getGlobalId();

      return ImmutableList.of(
          new DestinationEffect(
              DestinationEffect.Type.CREATED,
              String.format("Created revision %s", tip),
              transformResult.getChanges().getCurrent(),
              new DestinationEffect.DestinationRef(
                  tip, "commit", repoUrl)));
    }

    @Override
    public void visitChanges(@Nullable HgRevision start, ChangesVisitor visitor)
      throws RepoException, ValidationException {
      throw new UnsupportedOperationException("Not implemented yet");
    }
  }

  /**
   * Builds a new {@link HgDestination}
   */
  static HgDestination newHgDestination(String url, String fetch, String push,
      GeneralOptions generalOptions) {
    return new HgDestination(url, fetch, push, generalOptions);
  }
}
