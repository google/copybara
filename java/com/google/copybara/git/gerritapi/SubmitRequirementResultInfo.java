/*
 * Copyright (C) 2022 Google Inc.
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

package com.google.copybara.git.gerritapi;

import com.google.api.client.util.Key;
import com.google.common.base.MoreObjects;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/** Result of evaluating a submit requirement on a change. */
public class SubmitRequirementResultInfo implements StarlarkValue {

  @Key private String name;
  @Key private String status;

  @Key("submittability_expression_result")
  private SubmitRequirementExpressionInfo submittabilityExpressionResult;

  @Key("is_legacy")
  private boolean isLegacy;

  @StarlarkMethod(name = "name", doc = "The submit requirement name.", structField = true)
  public String getName() {
    return name;
  }

  public SubmitRequirementResultStatus getStatus() {
    return SubmitRequirementResultStatus.valueOf(status);
  }

  @StarlarkMethod(
      name = "status",
      doc = "The status of the submit requirement evaluation.",
      structField = true)
  public String getStatusAsString() {
    return status;
  }

  @StarlarkMethod(
      name = "is_legacy",
      doc =
          "If true, this submit requirement result was created from a legacy SubmitRecord."
              + " Otherwise, it was created by evaluating a submit requirement.",
      structField = true)
  public boolean getisLegacy() {
    return isLegacy;
  }

  @StarlarkMethod(
      name = "submittability_expression_result",
      doc =
          "A SubmitRequirementExpressionInfo containing the result of evaluating the"
              + " submittabilityexpression. If the submit requirement does not apply, the status"
              + " field of the result will be set to NOT_EVALUATED.",
      structField = true)
  public SubmitRequirementExpressionInfo getSubmittabilityExpressionResult() {
    return submittabilityExpressionResult;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("name", name)
        .add("status", status)
        .add("submittabilityExpressionResult", submittabilityExpressionResult)
        .add("isLegacy", isLegacy)
        .toString();
  }
}
