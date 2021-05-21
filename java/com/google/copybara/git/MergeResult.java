/*
 * Copyright (C) 2021 Google Inc.
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

import com.google.copybara.exception.ValidationException;

import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

@StarlarkBuiltin(
    name = "git_merge_result",
    doc = "The result returned by git merge when used in Starlark. For example in git.mirror"
        + " dynamic actions.")
public class MergeResult implements StarlarkValue {

  private final boolean error;
  private final String errorMsg;

  private MergeResult(boolean error, String errorMsg) {
    this.error = error;
    this.errorMsg = errorMsg;
  }

  /**
   * Create a merge result that was succesful.
   */
  public static MergeResult success(){
    return new MergeResult(false, null);
  }

  /**
   * Create a merge result that failed, normally due to a conflict.
   */
  public static MergeResult error(String errorMsg) {
    return new MergeResult(true, errorMsg);
  }

  @StarlarkMethod(
      name = "error",
      doc = "True if the merge execution resulted in an error. False otherwise",
      structField = true
  )
  public boolean isError() {
    return error;
  }

  @StarlarkMethod(
      name = "error_msg",
      doc = "Error message from git if the merge resulted in a conflict/error. Users must check"
          + " error field before accessing this field.",
      structField = true
  )
  public String getErrorMsg() throws ValidationException {
    ValidationException.checkCondition(error, "Access to error_msg is forbidden for merges"
        + " that don't result in an error");
    return errorMsg;
  }
}
