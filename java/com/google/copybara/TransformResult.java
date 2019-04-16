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

package com.google.copybara;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.authoring.Author;
import com.google.copybara.exception.RepoException;
import com.google.copybara.util.DiffUtil.DiffFile;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.function.Function;
import javax.annotation.CheckReturnValue;
import javax.annotation.Nullable;

/**
 * Represents the final result of a transformation, including metadata and actual code to be
 * migrated.
 */
public final class TransformResult {
  private final Path path;
  private final Author author;
  private final ZonedDateTime timestamp;
  private final String summary;
  @Nullable
  private final String baseline;
  private final boolean askForConfirmation;
  private final Revision currentRevision;
  private final Revision requestedRevision;
  @Nullable
  private final String changeIdentity;
  private final String workflowName;
  @Nullable private final String rawSourceRef;
  private final Changes changes;
  private final boolean setRevId;
  @Nullable private final ImmutableList<DiffFile> affectedFilesForSmartPrune;
  private final Function<String, Collection<String>> labelFinder;
  private final String revIdLabel;
  private final boolean confirmedInOrigin;

  private static ZonedDateTime readTimestampOrCurrentTime(Revision originRef) throws RepoException {
    ZonedDateTime refTimestamp = originRef.readTimestamp();
    return (refTimestamp != null) ? refTimestamp : ZonedDateTime.now(ZoneId.systemDefault());
  }

  public TransformResult(
      Path path,
      Revision currentRevision,
      Author author,
      String summary,
      Revision requestedRevision,
      String workflowName,
      Changes changes,
      @Nullable String rawSourceRef,
      boolean setRevId,
      Function<String, Collection<String>> labelFinder,
      String revIdLabel)
      throws RepoException {
    this(
        path,
        currentRevision,
        author,
        readTimestampOrCurrentTime(currentRevision),
        summary,
        /*baseline=*/ null,
        /*askForConfirmation=*/ false,
        requestedRevision,
        /*changeIdentity=*/ null,
        workflowName,
        changes,
        rawSourceRef,
        setRevId,
        /*affectedFilesForSmartPrune=*/ null,
        labelFinder,
        revIdLabel,
        /*confirmedInOrigin=*/ false);
  }

  private TransformResult(
      Path path,
      Revision currentRevision,
      Author author,
      ZonedDateTime timestamp,
      String summary,
      @Nullable String baseline,
      boolean askForConfirmation,
      Revision requestedRevision,
      @Nullable String changeIdentity,
      String workflowName,
      Changes changes,
      @Nullable String rawSourceRef,
      boolean setRevId,
      @Nullable ImmutableList<DiffFile> affectedFilesForSmartPrune,
      Function<String, Collection<String>> labelFinder,
      String revIdLabel,
      boolean confirmedInOrigin) {
    this.path = Preconditions.checkNotNull(path);
    this.currentRevision = Preconditions.checkNotNull(currentRevision);
    this.author = Preconditions.checkNotNull(author);
    this.timestamp = timestamp;
    this.summary = Preconditions.checkNotNull(summary);
    this.baseline = baseline;
    this.askForConfirmation = askForConfirmation;
    this.requestedRevision = Preconditions.checkNotNull(requestedRevision);
    this.changeIdentity = changeIdentity;
    this.workflowName = Preconditions.checkNotNull(workflowName);
    this.changes = Preconditions.checkNotNull(changes);
    this.rawSourceRef = rawSourceRef;
    this.setRevId = setRevId;
    this.affectedFilesForSmartPrune = affectedFilesForSmartPrune;
    this.labelFinder = Preconditions.checkNotNull(labelFinder);
    this.revIdLabel = Preconditions.checkNotNull(revIdLabel);
    this.confirmedInOrigin = confirmedInOrigin;
  }

  @CheckReturnValue
  public TransformResult withBaseline(String newBaseline) {
    Preconditions.checkNotNull(newBaseline);
    return new TransformResult(
        this.path,
        this.currentRevision,
        this.author,
        this.timestamp,
        this.summary,
        newBaseline,
        this.askForConfirmation,
        this.requestedRevision,
        this.changeIdentity,
        this.workflowName,
        this.changes,
        this.rawSourceRef,
        this.setRevId,
        this.affectedFilesForSmartPrune,
        this.labelFinder,
        this.revIdLabel,
        this.confirmedInOrigin);
  }

  /**
   * Used internally
   */
  @SuppressWarnings("unused")
  @CheckReturnValue
  public TransformResult withSummary(String summary) {
    return new TransformResult(
        this.path,
        this.currentRevision,
        this.author,
        this.timestamp,
        summary,
        this.baseline,
        this.askForConfirmation,
        this.requestedRevision,
        this.changeIdentity,
        this.workflowName,
        this.changes,
        this.rawSourceRef,
        this.setRevId,
        this.affectedFilesForSmartPrune,
        this.labelFinder,
        this.revIdLabel,
        this.confirmedInOrigin);
  }

  @CheckReturnValue
  public TransformResult withIdentity(String changeIdentity) {
    return new TransformResult(
        this.path,
        this.currentRevision,
        this.author,
        this.timestamp,
        this.summary,
        this.baseline,
        this.askForConfirmation,
        this.requestedRevision,
        changeIdentity,
        this.workflowName,
        this.changes,
        this.rawSourceRef,
        setRevId,
        this.affectedFilesForSmartPrune,
        this.labelFinder,
        this.revIdLabel,
        this.confirmedInOrigin);
  }

  @CheckReturnValue
  public TransformResult withAskForConfirmation(boolean askForConfirmation) {
    return new TransformResult(
        this.path,
        this.currentRevision,
        this.author,
        this.timestamp,
        this.summary,
        this.baseline,
        askForConfirmation,
        this.requestedRevision,
        this.changeIdentity,
        this.workflowName,
        this.changes,
        this.rawSourceRef,
        this.setRevId,
        this.affectedFilesForSmartPrune,
        this.labelFinder,
        this.revIdLabel,
        this.confirmedInOrigin);
  }

  @CheckReturnValue
  public TransformResult withChanges(Changes changes) {
    return new TransformResult(
        this.path,
        this.currentRevision,
        this.author,
        this.timestamp,
        this.summary,
        this.baseline,
        this.askForConfirmation,
        this.requestedRevision,
        this.changeIdentity,
        this.workflowName,
        changes,
        this.rawSourceRef,
        this.setRevId,
        this.affectedFilesForSmartPrune,
        this.labelFinder,
        this.revIdLabel,
        this.confirmedInOrigin);
  }

  @CheckReturnValue
  public TransformResult withSetRevId(boolean setRevId) {
    return new TransformResult(
        this.path,
        this.currentRevision,
        this.author,
        this.timestamp,
        this.summary,
        this.baseline,
        this.askForConfirmation,
        this.requestedRevision,
        this.changeIdentity,
        this.workflowName,
        this.changes,
        this.rawSourceRef,
        setRevId,
        this.affectedFilesForSmartPrune,
        this.labelFinder,
        this.revIdLabel,
        this.confirmedInOrigin);
  }

  @CheckReturnValue
  public TransformResult withAffectedFilesForSmartPrune(
      ImmutableList<DiffFile> affectedFilesForSmartPrune) {
    Preconditions.checkNotNull(affectedFilesForSmartPrune);
    return new TransformResult(
        this.path,
        this.currentRevision,
        this.author,
        this.timestamp,
        this.summary,
        this.baseline,
        this.askForConfirmation,
        this.requestedRevision,
        this.changeIdentity,
        this.workflowName,
        this.changes,
        this.rawSourceRef,
        this.setRevId,
        affectedFilesForSmartPrune,
        this.labelFinder,
        this.revIdLabel,
        this.confirmedInOrigin);
  }

  @CheckReturnValue
  public TransformResult withLabelFinder(Function<String, Collection<String>> labelMapper) {
    return new TransformResult(
        this.path,
        this.currentRevision,
        this.author,
        this.timestamp,
        this.summary,
        this.baseline,
        this.askForConfirmation,
        this.requestedRevision,
        this.changeIdentity,
        this.workflowName,
        this.changes,
        this.rawSourceRef,
        this.setRevId,
        this.affectedFilesForSmartPrune,
        Preconditions.checkNotNull(labelMapper),
        this.revIdLabel,
        this.confirmedInOrigin);
  }

  @CheckReturnValue
  public TransformResult withDiffInOrigin(boolean diffInOrigin) {
    return new TransformResult(
        this.path,
        this.currentRevision,
        this.author,
        this.timestamp,
        this.summary,
        this.baseline,
        this.askForConfirmation,
        this.requestedRevision,
        this.changeIdentity,
        this.workflowName,
        this.changes,
        this.rawSourceRef,
        this.setRevId,
        this.affectedFilesForSmartPrune,
        this.labelFinder,
        this.revIdLabel,
        diffInOrigin);
  }

  /**
   * Directory containing the tree of files to put in destination.
   */
  public Path getPath() {
    return path;
  }

  /**
   * The current revision being migrated. In ITERATIVE mode this would change per
   * migration.
   */
  public Revision getCurrentRevision() {
    return currentRevision;
  }

  /**
   * The revision that the user asked to migrate to. For example in ITERATIVE mode this would be
   * always the same revision for one Copybara invocation.
   *
   * <p>This revision might contain {@link Revision#contextReference()}, labels or other metadata
   * associated with it.
   */
  public Revision getRequestedRevision() {
    return requestedRevision;
  }

  /**
   * An stable identifier that represents an entity (code review for example) in the origin for
   * this particular change. For example a workflow location + Gerrit change number.
   */
  @Nullable
  public String getChangeIdentity() {
    return changeIdentity;
  }

  /**
   * Destination author to be used.
   */
  public Author getAuthor() {
    return author;
  }

  /**
   * The {@link ZonedDateTime} when the code was submitted to the origin repository.
   */
  public ZonedDateTime getTimestamp() {
    return timestamp;
  }

  /**
   * A description of the migrated changes to include in the destination's change description. The
   * destination may add more boilerplate text or metadata.
   */
  public String getSummary() {
    return summary;
  }

  /**
   * Destination baseline to be used for updating the code in the destination. If null, the
   * destination can assume head baseline.
   *
   * <p>Destinations supporting non-null baselines are expected to do the equivalent of:
   * <ul>
   *    <li>Sync to that baseline</li>
   *    <li>Apply/patch the changes on that revision</li>
   *    <li>Sync to head and auto-merge conflicts if possible</li>
   * </ul>
   */
  @Nullable
  public String getBaseline() {
    return baseline;
  }

  /**
   * If the destination should ask for confirmation. Some destinations might chose to ignore this
   * flag either because it doesn't apply to them or because the always ask for confirmation in
   * certain circumstances.
   *
   * <p>But in general, any destination that could do accidental damage to a repository should
   * not ignore when the value is true.
   */
  public boolean isAskForConfirmation() {
    return askForConfirmation;
  }

  /**
   * If true, the destination will not ask for confirmation. Instead, it will show the diff.
   *
   */
  public boolean isConfirmedInOrigin() {
    return confirmedInOrigin;
  }

  /**
   * The workflow name for the migration. Together with the config path it uniquely identifies a
   * workflow.
   */
  public String getWorkflowName() {
    return workflowName;
  }

  /**
   * Get all the labels from the message.
   */
  public ImmutableList<LabelFinder> findAllLabels() {
    return ChangeMessage.parseMessage(summary).getLabels();
  }

  /**
   * Data about the set of changes that are being migrated.
   */
  public Changes getChanges() {
    return changes;
  }

  /**
   * Reference as requested in the CLI if any
   */
  @Nullable
  public String getRawSourceRef() {
    return rawSourceRef;
  }

  /**
   * If RevId should be recorded in the destination
   */
  public boolean isSetRevId() {
    return setRevId;
  }

  /**
   * The label to use for storing the current migration state
   */
  public String getRevIdLabel() {
    return revIdLabel;
  }

  /**
   * It not null, the subset of files that Workflow smart_prune detected as really changed.
   *
   * <p>Destinations might ignore this field if they don't want to prune the list of affected
   * files.
   */
  @Nullable
  public ImmutableList<DiffFile> getAffectedFilesForSmartPrune() {
    return affectedFilesForSmartPrune;
  }

  /**
   * A function that returns all the label values that match a name.
   */
  public Function<String, Collection<String>> getLabelFinder() {
    return labelFinder;
  }
}
