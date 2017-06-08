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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

/**
 * Simple struct to provide a sidechannel for return values.
 */
public class StructuredOutput {

  private final List<SummaryLine> summaryLines = new ArrayList<>();

  /**
   * Appends a summary line to this structured output.
   */
  public void addSummaryLine(SummaryLine summaryLine) {
    summaryLines.add(summaryLine);
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
  public static abstract class SummaryLine {

    public static SummaryLine withTextOnly(String summary) {
      return new AutoValue_StructuredOutput_SummaryLine(
          checkNotNull(summary), /*originRef*/ null, /*destinationRef*/ null);
    }

    public static SummaryLine withDestinationRef(String summary, String destinationRef) {
      return new AutoValue_StructuredOutput_SummaryLine(
          checkNotNull(summary), /*originRef*/ null, checkNotNull(destinationRef));
    }

    /**
     * Returns the human-readable description of this line.
     */
    public abstract String getSummary();

    /**
     * Returns the origin reference that was affected.
     */
    @Nullable
    public abstract String getOriginRef();

    /**
     * Returns the destination reference that was affected.
     */
    @Nullable
    public abstract String getDestinationRef();
  }
}
