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
import javax.annotation.Nullable;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/**
 * Result of evaluating a single submit requirement expression. This API entity is populated from
 * {@link com.google.gerrit.entities.SubmitRequirementExpressionResult}.
 */
@SuppressWarnings("unused")
@StarlarkBuiltin(
    name = "gerritapi.SubmitRequirementExpressionInfo",
    doc = "Result of evaluating submit requirement expression")
public class SubmitRequirementExpressionInfo implements StarlarkValue {

  @Key private String expression;
  @Key private String status;
  @Key private boolean fulfilled;

  public SubmitRequirementExpressionInfo() {}

  @StarlarkMethod(
      name = "expression",
      doc = "The submit requirement expression as a string.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getExpression() {
    return expression;
  }

  public SubmitRequirementExpressionStatus getStatus() {
    return SubmitRequirementExpressionStatus.valueOf(status);
  }

  @StarlarkMethod(
      name = "status",
      doc = "The status of the submit requirement evaluation.",
      structField = true,
      allowReturnNones = true)
  @Nullable
  public String getStatusAsString() {
    return status;
  }

  @StarlarkMethod(
      name = "fulfilled",
      doc =
          "If true, this submit requirement result was created from a legacy SubmitRecord."
              + " Otherwise, it was created by evaluating a submit requirement.",
      structField = true)
  public boolean getFulfilled() {
    return fulfilled;
  }
}
