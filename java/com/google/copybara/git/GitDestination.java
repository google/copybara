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

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.copybara.GeneralOptions.FORCE;
import static com.google.copybara.exception.ValidationException.checkCondition;
import static com.google.copybara.git.LazyGitRepository.memoized;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.copybara.Change;
import com.google.copybara.ChangeMessage;
import com.google.copybara.Destination;
import com.google.copybara.DestinationEffect;
import com.google.copybara.GeneralOptions;
import com.google.copybara.LabelFinder;
import com.google.copybara.Revision;
import com.google.copybara.TransformResult;
import com.google.copybara.exception.CannotResolveRevisionException;
import com.google.copybara.exception.ChangeRejectedException;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.profiler.Profiler.ProfilerTask;
import com.google.copybara.util.DiffUtil;
import com.google.copybara.util.Glob;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * A Git repository destination.
 */
public final class GitDestination implements Destination<GitRevision> {

  private static final String ORIGIN_LABEL_SEPARATOR = ": ";

  static class MessageInfo {

    final boolean newPush;
    final ImmutableList<LabelFinder> labelsToAdd;

    MessageInfo(ImmutableList<LabelFinder> labelsToAdd, boolean newPush) {
      this.labelsToAdd = checkNotNull(labelsToAdd);
      this.newPush = newPush;
    }
  }

  interface CommitGenerator {

    /** Generates a commit message based on the uncommitted index stored in the given repository. */
    MessageInfo message(TransformResult transformResult) throws RepoException, ValidationException;
  }

  static final class DefaultCommitGenerator implements CommitGenerator {

    @Override
    public MessageInfo message(TransformResult transformResult) {
      Revision rev = transformResult.getCurrentRevision();
      ImmutableList<LabelFinder> labels = transformResult.isSetRevId()
          ? ImmutableList.of(
              new LabelFinder(rev.getLabelName() + ORIGIN_LABEL_SEPARATOR + rev.asString()))
          : ImmutableList.of();
      return new MessageInfo(labels, /*newPush*/ true);
    }
  }

  private static final Logger logger = Logger.getLogger(GitDestination.class.getName());

  private final String repoUrl;
  private final String fetch;
  private final String push;
  private final GitDestinationOptions destinationOptions;
  private final GitOptions gitOptions;
  private final GeneralOptions generalOptions;
  // Whether the skip_push flag is set in copy.bara.sky
  private final boolean skipPush;

  private final Iterable<GitIntegrateChanges> integrates;
  // Whether skip_push is set, either by command line or copy.bara.sky
  private final boolean effectiveSkipPush;
  private final CommitGenerator commitGenerator;
  private final ProcessPushOutput processPushOutput;
  private final LazyGitRepository localRepo;

  GitDestination(
      String repoUrl,
      String fetch,
      String push,
      GitDestinationOptions destinationOptions,
      GitOptions gitOptions,
      GeneralOptions generalOptions,
      boolean skipPush,
      CommitGenerator commitGenerator,
      ProcessPushOutput processPushOutput,
      Iterable<GitIntegrateChanges> integrates) {
    this.repoUrl = checkNotNull(repoUrl);
    this.fetch = checkNotNull(fetch);
    this.push = checkNotNull(push);
    this.destinationOptions = checkNotNull(destinationOptions);
    this.gitOptions = Preconditions.checkNotNull(gitOptions);
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.skipPush = skipPush;
    this.integrates = Preconditions.checkNotNull(integrates);
    this.effectiveSkipPush = skipPush || destinationOptions.skipPush;
    this.commitGenerator = checkNotNull(commitGenerator);
    this.processPushOutput = checkNotNull(processPushOutput);
    this.localRepo = memoized(ignored -> destinationOptions.localGitRepo(repoUrl));
  }

  /**
   * Throws an exception if the user.email or user.name Git configuration settings are not set. This
   * helps ensure that the committer field of generated commits is correct.
   */
  private static void verifyUserInfoConfigured(GitRepository repo)
      throws RepoException, ValidationException {
    String output = repo.simpleCommand("config", "-l").getStdout();
    boolean nameConfigured = false;
    boolean emailConfigured = false;
    for (String line : output.split("\n")) {
      if (line.startsWith("user.name=")) {
        nameConfigured = true;
      } else if (line.startsWith("user.email=")) {
        emailConfigured = true;
      }
    }
    checkCondition(nameConfigured && emailConfigured,
        "'user.name' and/or 'user.email' are not configured. Please run "
            + "`git config --global SETTING VALUE` to set them");
  }

  @Override
  public Writer<GitRevision> newWriter(Glob destinationFiles, boolean dryRun,
      @Nullable String groupId, @Nullable Writer<GitRevision> oldWriter) {
    WriterImpl gitOldWriter = (WriterImpl) oldWriter;

    boolean effectiveSkipPush = GitDestination.this.effectiveSkipPush || dryRun;

    WriterState state;
    if (oldWriter != null && gitOldWriter.skipPush == effectiveSkipPush) {
      state = ((WriterImpl) oldWriter).state;
    } else {
      state = new WriterState(localRepo,
          destinationOptions.localRepoPath != null
              ? push // This is nicer for the user
              : "copybara/push-" + UUID.randomUUID() + (dryRun ? "-dryrun" : ""));
    }

    return new WriterImpl<>(destinationFiles, effectiveSkipPush, repoUrl, fetch, push,
                            generalOptions, commitGenerator, processPushOutput,
                            state, destinationOptions.nonFastForwardPush, integrates,
                            destinationOptions.lastRevFirstParent, destinationOptions.ignoreIntegrationErrors,
                            destinationOptions.localRepoPath, destinationOptions.committerName,
                            destinationOptions.committerEmail, destinationOptions.rebaseWhenBaseline(),
                            gitOptions.visitChangePageSize);
  }

  /**
   * State to be maintained between writer instances.
   */
  public static class WriterState {

    boolean alreadyFetched;
    boolean firstWrite = true;
    final LazyGitRepository localRepo;
    final String localBranch;

    WriterState(LazyGitRepository localRepo, String localBranch) {
      this.localRepo = localRepo;
      this.localBranch = localBranch;
    }
  }

  /**
   * A writer for git.*destination destinations. Note that this is not a public interface and
   * shouldn't be used directly.
   */
  public static class WriterImpl<S extends WriterState> implements Writer<GitRevision> {

    private final Glob destinationFiles;
    final boolean skipPush;
    private final String repoUrl;
    private final String remoteFetch;
    private final String remotePush;
    private final boolean force;
    // Only use this console when you don't receive one as a parameter.
    private final Console baseConsole;
    private final GeneralOptions generalOptions;
    private final CommitGenerator commitGenerator;
    private final ProcessPushOutput processPushOutput;
    final S state;
    // We could get it from destinationOptions but this is in preparation of a GH PR destination.
    private final boolean nonFastForwardPush;
    private final Iterable<GitIntegrateChanges> integrates;
    private final boolean lastRevFirstParent;
    private final boolean ignoreIntegrationErrors;
    private final String localRepoPath;
    private final String committerName;
    private final String committerEmail;
    private final boolean rebase;
    private final int visitChangePageSize;

    /**
     * Create a new git.destination writer
     */
    public WriterImpl(Glob destinationFiles, boolean skipPush, String repoUrl, String remoteFetch,
        String remotePush, GeneralOptions generalOptions, CommitGenerator commitGenerator,
        ProcessPushOutput processPushOutput, S state, boolean nonFastForwardPush,
        Iterable<GitIntegrateChanges> integrates, boolean lastRevFirstParent,
        boolean ignoreIntegrationErrors, String localRepoPath, String committerName,
        String committerEmail, boolean rebase, int visitChangePageSize) {
      this.destinationFiles = checkNotNull(destinationFiles);
      this.skipPush = skipPush;
      this.repoUrl = checkNotNull(repoUrl);
      this.remoteFetch = checkNotNull(remoteFetch);
      this.remotePush = checkNotNull(remotePush);
      this.force = generalOptions.isForced();
      this.baseConsole = checkNotNull(generalOptions.console());
      this.generalOptions = generalOptions;
      this.commitGenerator = checkNotNull(commitGenerator);
      this.processPushOutput = checkNotNull(processPushOutput);
      this.state = checkNotNull(state);
      this.nonFastForwardPush = nonFastForwardPush;
      this.integrates = Preconditions.checkNotNull(integrates);
      this.lastRevFirstParent = lastRevFirstParent;
      this.ignoreIntegrationErrors = ignoreIntegrationErrors;
      this.localRepoPath = localRepoPath;
      this.committerName = committerName;
      this.committerEmail = committerEmail;
      this.rebase = rebase;
      this.visitChangePageSize = visitChangePageSize;
    }

    @Override
    public void visitChanges(@Nullable GitRevision start, ChangesVisitor visitor)
        throws RepoException, CannotResolveRevisionException {
      GitRepository repository = getRepository(baseConsole);
      try {
        fetchIfNeeded(repository, baseConsole);
      } catch (ValidationException e) {
        throw new CannotResolveRevisionException(
            "Cannot visit changes because fetch failed. Does the destination branch exist?", e);
      }
      GitRevision startRef = getLocalBranchRevision(repository);
      if (startRef == null) {
        return;
      }
      ChangeReader.Builder queryChanges =
          ChangeReader.Builder.forDestination(repository, baseConsole)
              .setVerbose(generalOptions.isVerbose());

      GitVisitorUtil.visitChanges(
          start == null ? startRef : start,
          visitor,
          queryChanges,
          generalOptions,
          "destination",
          visitChangePageSize);
    }

    /**
     * Do a fetch iff we haven't done one already. Prevents doing unnecessary fetches.
     */
    private void fetchIfNeeded(GitRepository repo, Console console)
        throws RepoException, ValidationException {
      if (!state.alreadyFetched) {
        GitRevision revision = fetchFromRemote(console, repo, repoUrl, remoteFetch);
        if (revision != null) {
          repo.simpleCommand("branch", state.localBranch, revision.getSha1());
        }
        state.alreadyFetched = true;
      }
    }

    @Nullable
    @Override
    public DestinationStatus getDestinationStatus(String labelName)
        throws RepoException {
      GitRepository repo = getRepository(baseConsole);
      try {
        fetchIfNeeded(repo, baseConsole);
      } catch (ValidationException e) {
        return null;
      }
      GitRevision startRef = getLocalBranchRevision(repo);
      if (startRef == null) {
        return null;
      }

      PathMatcher pathMatcher = destinationFiles.relativeTo(Paths.get(""));
      DestinationStatusVisitor visitor = new DestinationStatusVisitor(pathMatcher, labelName);
      ChangeReader.Builder changeReader =
          ChangeReader.Builder.forDestination(repo, baseConsole)
              .setVerbose(generalOptions.isVerbose())
              .setFirstParent(lastRevFirstParent)
              .grep("^" + labelName + ORIGIN_LABEL_SEPARATOR)
              .setRoots(destinationFiles.roots());
      try {
        // Using same visitChangePageSize for now
        GitVisitorUtil.visitChanges(
            startRef,
            visitor,
            changeReader,
            generalOptions,
            "get_destination_status",
            visitChangePageSize);
      } catch (CannotResolveRevisionException e) {
        // TODO: handle
        return null;
      }
      return visitor.getDestinationStatus();
    }

    /**
     * A visitor that computes the {@link DestinationStatus} matching the actual files affected by
     * the changes with the destination files glob.
     */
    private static class DestinationStatusVisitor implements ChangesVisitor {

      private final PathMatcher pathMatcher;
      private final String labelName;

      private DestinationStatus destinationStatus = null;

      DestinationStatusVisitor(PathMatcher pathMatcher, String labelName) {
        this.pathMatcher = pathMatcher;
        this.labelName = labelName;
      }

      @Override
      public VisitResult visit(Change<? extends Revision> change) {
        ImmutableSet<String> changeFiles = change.getChangeFiles();
        if (changeFiles != null) {
          if (change.getLabels().containsKey(labelName)) {
            for (String file : changeFiles) {
              if (pathMatcher.matches(Paths.get('/' + file))) {
                String lastRev = Iterables.getLast(change.getLabels().get(labelName));
                destinationStatus = new DestinationStatus(lastRev, ImmutableList.of());
                return VisitResult.TERMINATE;
              }
            }
          }
        }
        return VisitResult.CONTINUE;
      }

      @Nullable
      DestinationStatus getDestinationStatus() {
        return destinationStatus;
      }
    }

    @Nullable
    private GitRevision getLocalBranchRevision(GitRepository gitRepository) throws RepoException {
      try {
        return gitRepository.resolveReference(state.localBranch);
      } catch (CannotResolveRevisionException e) {
        if (force) {
          return null;
        }
        throw new RepoException(String.format("Could not find %s in %s and '%s' was not used",
            remoteFetch, repoUrl, GeneralOptions.FORCE));
      }
    }

    @Override
    public boolean supportsHistory() {
      return true;
    }


    @Override
    public ImmutableList<DestinationEffect> write(TransformResult transformResult, Console console)
        throws ValidationException, RepoException, IOException {
      logger.log(Level.INFO, "Exporting from " + transformResult.getPath() + " to: " + this);
      String baseline = transformResult.getBaseline();

      GitRepository scratchClone = getRepository(console);

      fetchIfNeeded(scratchClone, console);

      console.progress("Git Destination: Checking out " + remoteFetch);

      GitRevision localBranchRevision = getLocalBranchRevision(scratchClone);
      updateLocalBranchToBaseline(scratchClone, baseline);

      if (state.firstWrite) {
        String reference = baseline != null ? baseline : state.localBranch;
        configForPush(getRepository(console), repoUrl, remotePush);
        if (!force && localBranchRevision == null) {
          throw new RepoException(String.format(
              "Cannot checkout '%s' from '%s'. Use '%s' if the destination is a new git repo or"
                  + " you don't care about the destination current status", reference,
              repoUrl,
              GeneralOptions.FORCE));
        }
        if (localBranchRevision != null) {
          scratchClone.simpleCommand("checkout", "-f", "-q", reference);
        } else {
          // Configure the commit to go to local branch instead of master.
          scratchClone.simpleCommand("symbolic-ref", "HEAD", getCompleteRef(state.localBranch));
        }
        state.firstWrite = false;
      } else if (!skipPush) {
        // Should be a no-op, but an iterative migration could take several minutes between
        // migrations so lets fetch the latest first.
        fetchFromRemote(console, scratchClone, repoUrl, remoteFetch);
      }

      PathMatcher pathMatcher = destinationFiles.relativeTo(scratchClone.getWorkTree());
      // Get the submodules before we stage them for deletion with
      // repo.simpleCommand(add --all)
      AddExcludedFilesToIndex excludedAdder =
          new AddExcludedFilesToIndex(scratchClone, pathMatcher);
      excludedAdder.findSubmodules(console);

      GitRepository alternate = scratchClone.withWorkTree(transformResult.getPath());

      console.progress("Git Destination: Adding all files");
      alternate.add().force().all().run();

      console.progress("Git Destination: Excluding files");
      excludedAdder.add();

      console.progress("Git Destination: Creating a local commit");
      MessageInfo messageInfo = commitGenerator.message(transformResult);

      ChangeMessage msg = ChangeMessage.parseMessage(transformResult.getSummary());
      for (LabelFinder label : messageInfo.labelsToAdd) {
        msg.addOrReplaceLabel(label.getName(), label.getSeparator(), label.getValue());
      }

      String commitMessage = msg.toString();
      alternate.commit(
          transformResult.getAuthor().toString(),
          transformResult.getTimestamp(),
          commitMessage);

      for (GitIntegrateChanges integrate : integrates) {
        integrate.run(alternate, generalOptions, messageInfo,
            path -> !pathMatcher.matches(scratchClone.getWorkTree().resolve(path)),
            transformResult, ignoreIntegrationErrors);
      }

      if (baseline != null && rebase) {
        // Our current implementation (That we should change) leaves unstaged files in the
        // work-tree. This is fine for commit/push but not for rebase, since rebase could fail
        // and needs to create a conflict resolution work-tree.
        alternate.simpleCommand("reset", "--hard");
        alternate.rebase(localBranchRevision.getSha1());
      }

      if (localRepoPath != null) {

        // If the user provided a directory for the local repo we don't want to leave changes
        // in the checkout dir. Remove tracked changes:
        scratchClone.simpleCommand("reset", "--hard");
        // ...and untracked ones:
        scratchClone.simpleCommand("clean", "-f");
        scratchClone.simpleCommand("checkout", state.localBranch);
      }

      if (transformResult.isAskForConfirmation()) {
        // The git repo contains the staged changes at this point. Git diff writes to Stdout
        console.info(DiffUtil.colorize(
            console, scratchClone.simpleCommand("show", "HEAD").getStdout()));
        if (!console.promptConfirmation(
            String.format("Proceed with push to %s %s?", repoUrl, remotePush))) {
          console.warn("Migration aborted by user.");
          throw new ChangeRejectedException(
              "User aborted execution: did not confirm diff changes.");
        }
      }
      GitRevision head = scratchClone.resolveReference("HEAD");
      if (skipPush) {
        console.infoFmt(
            "Git Destination: skipped push to remote. Check the local commits at %s",
            scratchClone.getGitDir());
        return ImmutableList.of(
            new DestinationEffect(
                DestinationEffect.Type.CREATED,
                String.format(
                    "Dry run commit '%s' created locally at %s", head, scratchClone.getGitDir()),
                transformResult.getChanges().getCurrent(),
                new DestinationEffect.DestinationRef(head.getSha1(), "commit", /*url=*/ null),
                ImmutableList.of()));
      }

      console.progress(String.format("Git Destination: Pushing to %s %s", repoUrl, remotePush));
      checkCondition(!nonFastForwardPush
          || !Objects.equals(remoteFetch, remotePush), "non fast-forward push is only"
          + " allowed when fetch != push");

      String serverResponse = generalOptions.repoTask(
          "push",
          () -> scratchClone.push()
              .withRefspecs(repoUrl, ImmutableList.of(scratchClone.createRefSpec(
                  (nonFastForwardPush ? "+" : "") + "HEAD:" + getCompleteRef(remotePush))))
              .run()
      );
      return processPushOutput.process(
          serverResponse,
          messageInfo.newPush,
          alternate,
          transformResult.getChanges().getCurrent());
    }

    /**
     * Get the local {@link GitRepository} associated with the writer.
     *
     * Note that this is not a public interface and is subjec to change.
     */
    public GitRepository getRepository(Console console) throws RepoException {
      return state.localRepo.get(console);
    }

    private void updateLocalBranchToBaseline(GitRepository repo, String baseline)
        throws RepoException {
      if (baseline != null && !repo.refExists(baseline)) {
        throw new RepoException("Cannot find baseline '" + baseline
            + (getLocalBranchRevision(repo) != null
               ? "' from fetch reference '" + remoteFetch + "'"
               : "' and fetch reference '" + remoteFetch + "' itself")
            + " in " + repoUrl + ".");
      } else if (baseline != null) {
        // Update the local branch to use the baseline
        repo.simpleCommand("update-ref", state.localBranch, baseline);
      }
    }

    @Nullable
    private GitRevision fetchFromRemote(Console console, GitRepository repo, String repoUrl,
        String fetch) throws RepoException, ValidationException {
      String completeFetchRef = getCompleteRef(fetch);
      try (ProfilerTask ignore = generalOptions.profiler().start("destination_fetch")){
        console.progress("Git Destination: Fetching: " + repoUrl + " " + completeFetchRef);
        return repo.fetchSingleRef(repoUrl, completeFetchRef);
      } catch (CannotResolveRevisionException e) {
        String warning = String.format("Git Destination: '%s' doesn't exist in '%s'",
            completeFetchRef, repoUrl);
        if (!force) {
          throw new ValidationException(
              "%s. Use %s flag if you want to push anyway", warning, FORCE);
        }
        console.warn(warning);
      }
      return null;
    }

    private String getCompleteRef(String fetch) {
      // Assume that it is a branch. Doesn't work for tags. But we don't update tags (For now).
      return fetch.startsWith("refs/") ? fetch : "refs/heads/" + fetch;
    }

    private GitRepository configForPush(GitRepository repo, String repoUrl, String push)
        throws RepoException, ValidationException {

      if (localRepoPath != null) {
        // Configure the local repo to allow pushing to the ref manually outside of Copybara
        repo.simpleCommand("config", "remote.copybara_remote.url", repoUrl);
        repo.simpleCommand("config", "remote.copybara_remote.push",
            state.localBranch + ":" + push);
        repo.simpleCommand("config", "branch." + state.localBranch
            + ".remote", "copybara_remote");
      }
      if (!Strings.isNullOrEmpty(committerName)) {
        repo.simpleCommand("config", "user.name", committerName);
      }
      if (!Strings.isNullOrEmpty(committerEmail)) {
        repo.simpleCommand("config", "user.email", committerEmail);
      }
      verifyUserInfoConfigured(repo);

      return repo;
    }

  }

  @VisibleForTesting
  String getFetch() {
    return fetch;
  }

  @VisibleForTesting
  String getPush() {
    return push;
  }

  @Override
  public String getLabelNameWhenOrigin() {
    return GitRepository.GIT_ORIGIN_REV_ID;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("repoUrl", repoUrl)
        .add("fetch", fetch)
        .add("push", push)
        .add("skip_push", skipPush)
        .toString();
  }

  /**
   * Process the server response from the push command
   */
  interface ProcessPushOutput {

    /**
     * @param output - the message for the commit
     * @param newPush - true if is the first time we are pushing to the origin ref
     * @param alternateRepo - The alternate repo used for staging commits, if any
     * @param current
     */
    ImmutableList<DestinationEffect> process(
        String output,
        boolean newPush,
        GitRepository alternateRepo,
        List<? extends Change<?>> current);
  }

  static class ProcessPushStructuredOutput implements ProcessPushOutput {

    @Override
    public ImmutableList<DestinationEffect> process(
        String output,
        boolean newPush,
        GitRepository alternateRepo,
        List<? extends Change<?>> current) {
      try {
        String sha1 = alternateRepo.parseRef("HEAD");

        return ImmutableList.of(
            new DestinationEffect(
                DestinationEffect.Type.CREATED,
                String.format("Created revision %s", sha1),
                current,
                new DestinationEffect.DestinationRef(sha1, "commit", /*url=*/ null),
                ImmutableList.of()));

      } catch (RepoException | CannotResolveRevisionException e) {
        // We should always be able to resolve HEAD. Otherwise we have something really wrong.
        throw new RuntimeException("Internal bug. Please fill a bug", e);
      }
    }
  }

  /**
   * Not a public API. It is subject to change.
   */
  public LazyGitRepository getLocalRepo() {
    return localRepo;
  }

  @Override
  public String getType() {
    return "git.destination";
  }

  @Override
  public ImmutableSetMultimap<String, String> describe(Glob originFiles) {
    ImmutableSetMultimap.Builder<String, String> builder =
        new ImmutableSetMultimap.Builder<String, String>()
            .put("type", getType())
            .put("url", repoUrl)
            .put("fetch", fetch)
            .put("push", push);
    if (skipPush) {
      builder.put("skip_push", "" + skipPush);
    }
    return builder.build();
  }

}
