/*
 * Copyright (C) 2018 Google Inc.
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

using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Action;

/// <summary>
/// Represents the result returned by an <see cref="IAction"/>. Port of
/// <c>com.google.copybara.action.ActionResult</c>.
/// </summary>
[StarlarkBuiltin(
    "dynamic.action_result",
    Doc = "Result objects created by actions to tell Copybara what happened.")]
public sealed class ActionResult : IStarlarkPrintableValue
{
    private readonly Result _result;
    private readonly string? _msg;

    private ActionResult(Result result, string? msg)
    {
        _result = result;
        _msg = msg;
    }

    /// <summary>Result kind. The Starlark-facing name is the upstream-compatible upper snake case form.</summary>
    public enum Result
    {
        Success,
        Error,
        NoOp,
    }

    public static ActionResult SuccessResult() => new(Result.Success, msg: null);

    public static ActionResult ErrorResult(string msg) => new(Result.Error, msg);

    public static ActionResult NoopResult(string? msg) => new(Result.NoOp, msg);

    public Result GetResult() => _result;

    [StarlarkMethod(
        "result",
        Doc = "The result of this action",
        StructField = true)]
    public string GetResultForSkylark() => ResultName(_result);

    [StarlarkMethod(
        "msg",
        Doc = "The message associated with the result",
        StructField = true,
        AllowReturnNones = true)]
    public string? GetMsg() => _msg;

    /// <summary>Upstream-compatible name of the result (SUCCESS, ERROR, NO_OP).</summary>
    private static string ResultName(Result result) => result switch
    {
        Result.Success => "SUCCESS",
        Result.Error => "ERROR",
        Result.NoOp => "NO_OP",
        _ => result.ToString(),
    };

    public void Repr(Printer printer, StarlarkSemantics semantics) => printer.Append(ToString());

    public override string ToString() =>
        $"ActionResult{{result={ResultName(_result)}, msg={_msg ?? "null"}}}";
}
