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

import com.beust.jcommander.Parameter;
import com.beust.jcommander.Parameters;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Suppliers;
import com.google.common.collect.Lists;
import com.google.copybara.authoring.Author;
import com.google.copybara.exception.VoidOperationException;
import com.google.copybara.jcommander.GreaterThanZeroListValidator;
import com.google.copybara.util.console.Console;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.syntax.EvalException;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import javax.annotation.Nullable;

/**
 * Arguments for {@link Workflow} components.
 */
@Parameters(separators = "=")
public class WorkflowOptions implements Option {

  static final String CHANGE_REQUEST_PARENT_FLAG = "--change_request_parent";
  static final String READ_CONFIG_FROM_CHANGE = "--read-config-from-change";
  protected static final String CHANGE_REQUEST_FROM_SOT_LIMIT_FLAG = "--change-request-from-sot-limit";

  @Parameter(names = CHANGE_REQUEST_PARENT_FLAG,
      description = "Commit revision to be used as parent when importing a commit using"
          + " CHANGE_REQUEST workflow mode. this shouldn't be needed in general as Copybara is able"
          + " to detect the parent commit message.")
  public String changeBaseline = "";

  /**
   * Public so that it can be used programmatically.
   */
  @Parameter(names = "--last-rev",
      description = "Last revision that was migrated to the destination")
  public String lastRevision;

  static final String INIT_HISTORY_FLAG = "--init-history";

  @Parameter(names = INIT_HISTORY_FLAG,
      description = "Import all the changes from the beginning of the history up to the resolved"
          + " ref. For 'ITERATIVE' workflows this will import individual changes since the first "
          + "one. For 'SQUASH' it will import the squashed change up to the resolved ref. "
          + "WARNING: Use with care, this flag should be used only for the very first run of "
          + "Copybara for a workflow.")
  public boolean initHistory = false;

  @Parameter(names = "--iterative-limit-changes",
      description = "Import just a number of changes instead of all the pending ones")
  public int iterativeLimitChanges = Integer.MAX_VALUE;

  @Parameter(names = "--ignore-noop",
      description = "Only warn about operations/transforms that didn't have any effect."
          + " For example: A transform that didn't modify any file, non-existent origin"
          + " directories, etc.")
  public boolean ignoreNoop = false;

  @Parameter(
    names = "--squash-skip-history",
    description =
        "Avoid exposing the history of changes that are being migrated. This is"
            + " useful when we want to migrate a new repository but we don't want to expose all"
            + " the change history to metadata.squash_notes."
  )
  public boolean squashSkipHistory = false;

  @Parameter(names = {"--import-noop-changes"},
      description = "By default Copybara will only try to migrate changes that could affect the"
          + " destination. Ignoring changes that only affect excluded files in origin_files. This"
          + " flag disables that behavior and runs for all the changes.")
  public boolean migrateNoopChanges = false;

  @Parameter(names = {"--workflow-identity-user"},
      description = "Use a custom string as a user for computing change identity")
  @Nullable
  public String workflowIdentityUser = null;

  public static final String CHECK_LAST_REV_STATE = "--check-last-rev-state";

  @Parameter(names = CHECK_LAST_REV_STATE,
      description = "If enabled, Copybara will validate that the destination didn't change"
          + " since last-rev import for destination_files. Note that this"
          + " flag doesn't work for CHANGE_REQUEST mode.")
  public boolean checkLastRevState = false;

  @Parameter(names = "--threads",
      description = "Number of threads to use when running transformations that change lot of files")
  public int threads = 1;

  @Parameter(names = CHANGE_REQUEST_FROM_SOT_LIMIT_FLAG,
      description = "Number of origin baseline changes to use for trying to match one in the"
          + " destination. It can be used if the are many parent changes in the origin that are a"
          + " no-op in the destination")
  public int changeRequestFromSotLimit = 10;

  @Parameter(names = "--threads-min-size",
      description = "Minimum size of the lists to process to run them in parallel")
  public int threadsMinSize = 100;

  @Parameter(names = "--notransformation-join",
      description = "By default Copybara tries to join certain transformations in one so that it"
          + " is more efficient. This disables the feature.")
  public boolean noTransformationJoin = false;

  @Parameter(
      names = READ_CONFIG_FROM_CHANGE,
      description = "For each imported origin change, load the configuration from that change.")
  boolean readConfigFromChange = false;

  @Parameter(names = "--nosmart-prune",
      description = "Disable smart prunning")
  boolean noSmartPrune = false;

  public boolean canUseSmartPrune() {
    return !noSmartPrune;
  }

  @Parameter(names = "--change-request-from-sot-retry",
      description = "Number of retries and delay between retries when we cannot find the baseline"
          + " in the destination for CHANGE_REQUEST_FROM_SOT. For example '10,30,60' will retry"
          + " three times. The first retry will be delayed 10s, the second one 30s and the third"
          + " one 60s", validateWith = GreaterThanZeroListValidator.class)
  public List<Integer> changeRequestFromSotRetry= Lists.newArrayList();


  @Parameter(names = "--default-author",
      description = "Use this author as default instead of the one in the config file."
          + "Format should be 'Foo Bar <foobar@example.com>'")
  String defaultAuthor = null;

  @Nullable
  public Author getDefaultAuthorFlag() throws EvalException {
    if (defaultAuthor == null) {
      return null;
    }
    return Author.parse(Location.BUILTIN, defaultAuthor);
  }

  public boolean isReadConfigFromChange() {
    return readConfigFromChange;
  }

  private final Supplier<LocalParallelizer> parallelizerSupplier =
      Suppliers.memoize(() -> new LocalParallelizer(threads, threadsMinSize));

  public LocalParallelizer parallelizer() {
    return parallelizerSupplier.get();
  }

  public boolean joinTransformations() {
    return !noTransformationJoin;
  }

  /**
   * Reports that some operation is a no-op. This will either throw an exception or report the
   * incident to the console, depending on the options.
   */
  public void reportNoop(Console console, String message, boolean currentIgnoreNoop)
      throws VoidOperationException {
    if (ignoreNoop) {
      console.warn("NOOP: " + message);
    } else if(currentIgnoreNoop){
      if(console.isVerbose()){
        console.warn("NOOP: " + message);
      }
    } else {
      throw new VoidOperationException(
          String.format("%s. Use --ignore-noop if you want to ignore this error", message));
    }
  }

  public WorkflowOptions() {}

  @VisibleForTesting
  public WorkflowOptions(String changeBaseline, String lastRevision, boolean checkLastRevState) {
    this.changeBaseline = changeBaseline;
    this.lastRevision = lastRevision;
    this.checkLastRevState = checkLastRevState;
  }

  public String getLastRevision() {
    return lastRevision;
  }

  public boolean isInitHistory() {
    return initHistory;
  }

  public String getChangeBaseline() {
    return changeBaseline;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof WorkflowOptions)) {
      return false;
    }
    WorkflowOptions that = (WorkflowOptions) o;
    return Objects.equals(changeBaseline, that.changeBaseline)
        && Objects.equals(lastRevision, that.lastRevision);
  }

  @Override
  public int hashCode() {
    return Objects.hash(changeBaseline, lastRevision);
  }
}
