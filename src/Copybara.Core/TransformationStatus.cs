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

using Copybara.Common;
using Copybara.Exceptions;
using Starlark.Annot;
using Starlark.Eval;
using Console = Copybara.Util.Console.Console;

namespace Copybara;

/// <summary>The status of a Transformation that was just run. Either a 'success' or a 'no-op'.</summary>
[StarlarkBuiltin(
    TransformationStatus.StarlarkTypeName,
    Doc = "The status of a Transformation that was just run. Either a 'success' or a 'no-op'.")]
public sealed class TransformationStatus : IStarlarkValue
{
    public const string StarlarkTypeName = "transformation_status";

    private readonly bool _isSuccess;
    private readonly string? _message;

    private TransformationStatus(bool success, string? message)
    {
        _isSuccess = success;
        _message = message;
    }

    public static TransformationStatus Success() => new(true, null);

    public static TransformationStatus Noop(string message) => new(false, message);

    [StarlarkMethod(
        "is_success",
        Doc = "Whether this status has the value SUCCESS.",
        StructField = true)]
    public bool IsSuccess() => _isSuccess;

    [StarlarkMethod("is_noop", Doc = "Whether this status has the value NO-OP.", StructField = true)]
    public bool IsNoop() => !_isSuccess;

    public string GetMessage()
    {
        Preconditions.CheckState(IsNoop(), "Can only get message if the Transform was a no-op.");
        return _message!;
    }

    public void Warn(Console console)
    {
        Preconditions.CheckState(IsNoop(), "Can only warn if the Transform was a no-op.");
        console.Warn("NOOP: " + _message);
    }

    public void ThrowException(Console console, bool ignoreNoop)
    {
        Preconditions.CheckState(IsNoop(), "Can only throw if the Transform was a no-op.");
        if (ignoreNoop)
        {
            Warn(console);
            return;
        }
        throw new VoidOperationException(
            $"{_message}. Use --ignore-noop if you want to ignore this error");
    }

    public override bool Equals(object? o)
    {
        if (ReferenceEquals(this, o))
        {
            return true;
        }
        if (o is null || GetType() != o.GetType())
        {
            return false;
        }
        var that = (TransformationStatus)o;
        return string.Equals(_message, that._message) && _isSuccess == that._isSuccess;
    }

    public override int GetHashCode() => HashCode.Combine(_isSuccess, _message);

    public override string ToString() =>
        $"{StarlarkTypeName}(isSuccess={_isSuccess}, message={_message})";
}
