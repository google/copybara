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

package com.google.copybara.util;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Simple struct to provide a sidechannel for return values.
 *
 * @deprecated use DestinationEffect and events.
 */
@Deprecated
public class StructuredOutput {
  @Nullable private SummaryLine.Builder currentLine = null;

  private final List<SummaryLine> summaryLines = new ArrayList<>();

  /**
   * Appends the current builder to the summary, resetting the builder.
   */
  public void appendSummaryLine() {
    if (currentLine == null) {
      return;
    }
    summaryLines.add(currentLine.build());
    currentLine = null;
  }

  /**
   * Returns a reference to the builder for the latest entry to amend.
   */
  public SummaryLine.Builder getCurrentSummaryLineBuilder() {
    if (currentLine == null) {
      currentLine = new AutoValue_StructuredOutput_SummaryLine.Builder();
    }
    return currentLine;
  }

  /**
   * Returns the list of summary lines for this execution.
   *
   * <p>Note that it's up to the caller to interpret the meaning of the references on each line.
   */
  public List<SummaryLine> getSummaryLines() {
    return ImmutableList.copyOf(summaryLines);
  }

  @Override
  public String toString() {
    StringBuilder summary = new StringBuilder();
    for (SummaryLine summaryLine : summaryLines) {
      summary.append(summaryLine);
      summary.append('\n');
    }
    return summary.toString();
  }

  /**
   * Represents one summary item of the results produced by this migration.
   */
  @AutoValue
  public abstract static class SummaryLine {

    /**
     * Returns the human-readable description of this line.
     */
    @Nullable
    public abstract String getSummary();

    /**
     * Returns the origin references that were affected.
     */
    @Nullable
    public abstract ImmutableList<String> getOriginRefs();

    /**
     * Returns the destination reference that was affected.
     */
    @Nullable
    public abstract String getDestinationRef();

    /**
     * Returns the available to migrate information.
     */
    @Nullable
    public abstract AvailableToMigrate getAvailableToMigrate();


    /**
     * Builder to allow having one mutable instance during the workflow.
     */
    @AutoValue.Builder
    public abstract static class Builder {
      public abstract Builder setSummary(String summary);
      public abstract Builder setDestinationRef(String destinationRef);
      public abstract Builder setOriginRefs(ImmutableList<String> originRefs);
      public abstract Builder setAvailableToMigrate(AvailableToMigrate availableToMigrate);

      abstract SummaryLine build();
    }
  }

  /**
   * Represents the changes in the origin that are available to migrate.
   */
  @AutoValue
  public abstract static class AvailableToMigrate {

    public static AvailableToMigrate create(ImmutableList<String> revisions) {
      return new AutoValue_StructuredOutput_AvailableToMigrate(revisions);
    }

    /**
     * Returns the list of revisions available to migrate.
     */
    @Nullable
    public abstract ImmutableList<String> getRevisions();

  }
}
