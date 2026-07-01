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

using Copybara.Exceptions;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Git;

/// <summary>
/// The result returned by git merge when used in Starlark. For example in git.mirror dynamic
/// actions. Port of <c>com.google.copybara.git.MergeResult</c>.
/// </summary>
[StarlarkBuiltin(
    "git_merge_result",
    Doc = "The result returned by git merge when used in Starlark. For example in git.mirror"
        + " dynamic actions.")]
public sealed class MergeResult : IStarlarkValue
{
    private readonly bool _error;
    private readonly string? _errorMsg;

    private MergeResult(bool error, string? errorMsg)
    {
        _error = error;
        _errorMsg = errorMsg;
    }

    /// <summary>Create a merge result that was successful.</summary>
    public static MergeResult Success() => new(false, null);

    /// <summary>Create a merge result that failed, normally due to a conflict.</summary>
    public static MergeResult Error(string errorMsg) => new(true, errorMsg);

    [StarlarkMethod(
        "error",
        Doc = "True if the merge execution resulted in an error. False otherwise",
        StructField = true)]
    public bool IsError() => _error;

    [StarlarkMethod(
        "error_msg",
        Doc = "Error message from git if the merge resulted in a conflict/error. Users must check"
            + " error field before accessing this field.",
        StructField = true)]
    public string? GetErrorMsg()
    {
        ValidationException.CheckCondition(
            _error,
            "Access to error_msg is forbidden for merges that don't result in an error");
        return _errorMsg;
    }
}
