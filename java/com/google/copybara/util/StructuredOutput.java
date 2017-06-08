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

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;

/**
 * Simple struct to provide a sidechannel for return values.
 */
public class StructuredOutput {

  private final StringBuilder summary = new StringBuilder();
  private final List<String> affectedRefs = new ArrayList<>();

  /**
   * Appends the message to the summary, adding a new line.
   */
  public void addSummaryLine(String msg) {
    summary.append(msg);
    summary.append('\n');
  }

  /**
   * Appends a reference affected by this execution, for whatever definition of "affected" that
   * the destination might choose.
   */
  public void addAffectedRef(String ref) {
    affectedRefs.add(ref);
  }

  /**
   * Returns the list of affected references by this execution.
   *
   * <p>Note that it's up to the caller to interpret the meaning of these references.
   */
  public List<String> getAffectedRefs() {
    return ImmutableList.copyOf(affectedRefs);
  }

  @Override
  public String toString() {
    return summary.toString();
  }
}
