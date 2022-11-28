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
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/**
 * An object that represents the input parameters for a submit requirement:
 *
 * <p>https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#submit-requirement-input
 */
public class SubmitRequirementInput implements StarlarkValue {
  /** Submit requirement name. */
  @Key private String name;

  /**
   * Query expression that can be evaluated on any change. If evaluated to true on a change, the
   * submit requirement is fulfilled and not blocking change submission.
   */
  @Key("submittability_expression")
  String submittabilityExpression;

  @StarlarkMethod(name = "name", doc = "The submit requirement name.", structField = true)
  public String getName() {
    return name;
  }

  @StarlarkMethod(
      name = "submittability_expression",
      doc =
          "Query expression that can be evaluated on any change. If evaluated to true on a change,"
              + " the submit requirement is fulfilled and not blocking change submission.",
      structField = true)
  public String getSubmittabilityExpression() {
    return submittabilityExpression;
  }

  public SubmitRequirementInput(String name, String submittabilityExpression) {
    this.name = name;
    this.submittabilityExpression = submittabilityExpression;
  }
}
