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

using System.Text;
using Copybara.Common;
using Copybara.Exceptions;
using Starlark.Eval;
using Starlark.Syntax;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara.Transform;

/// <summary>A transformation that uses a Skylark function to transform the code.</summary>
public class SkylarkTransformation : ITransformation
{
    private readonly IStarlarkCallable _function;
    private readonly Dict _params;
    private readonly StarlarkThread.PrintHandler _printHandler;

    public SkylarkTransformation(
        IStarlarkCallable function, Dict @params, StarlarkThread.PrintHandler printHandler)
    {
        _function = Preconditions.CheckNotNull(function);
        _params = Preconditions.CheckNotNull(@params);
        _printHandler = Preconditions.CheckNotNull(printHandler);
    }

    public TransformationStatus Transform(TransformWork work)
    {
        var skylarkConsole = new SkylarkConsole(work.GetConsole());
        TransformWork skylarkWork = work.WithConsole(skylarkConsole).WithParams(_params);
        TransformationStatus status = TransformationStatus.Success();
        using (var mu = Mutability.Create("dynamic_transform"))
        {
            StarlarkThread thread = StarlarkThread.CreateTransient(mu, StarlarkSemantics.DEFAULT);
            thread.SetPrintHandler(_printHandler);
            try
            {
                object? result = StarlarkRt.Call(
                    thread,
                    _function,
                    new object?[] { skylarkWork },
                    new Dictionary<string, object?>());
                result = ReferenceEquals(result, StarlarkRt.None)
                    ? TransformationStatus.Success()
                    : result;
                ValidationException.CheckCondition(
                    result is TransformationStatus,
                    "Dynamic transforms functions should return nothing or objects of type {0}, but"
                        + " '{1}' returned: {2}",
                    TransformationStatus.StarlarkTypeName,
                    Describe(),
                    result!);
                status = (TransformationStatus)result!;
            }
            catch (EvalException e)
            {
                switch (e.InnerException)
                {
                    case EmptyChangeException ece:
                        throw ece;
                    case RepoException re:
                        throw new RepoException(
                            $"Error while executing the skylark transformation {Describe()}:"
                                + $" {e.Message}",
                            re);
                    default:
                        throw new ValidationException(
                            $"Error while executing the skylark transformation {Describe()}:"
                                + $" {e.Message}",
                            e);
                }
            }
            finally
            {
                work.UpdateFrom(skylarkWork);
            }
        }

        ValidationException.CheckCondition(
            skylarkConsole.GetErrorCount() == 0,
            "{0} error(s) while executing {1}",
            skylarkConsole.GetErrorCount(),
            Describe());
        return status;
    }

    public ITransformation Reverse() => new ExplicitReversal(IntentionalNoop.Instance, this);

    public string Describe() => _function.Name;

    public override string ToString()
    {
        string camelCaseName = LowerUnderscoreToUpperCamel(Describe());
        if (camelCaseName.EndsWith("Impl", StringComparison.Ordinal))
        {
            camelCaseName = camelCaseName.Substring(0, camelCaseName.Length - 4);
        }
        var builder = new StringBuilder(camelCaseName).Append('{');
        bool first = true;
        foreach (var e in _params.Entries)
        {
            if (!first)
            {
                builder.Append(", ");
            }
            first = false;
            builder.Append(e.Key).Append('=').Append(e.Value);
        }
        return builder.Append('}').ToString();
    }

    public Location Location() => _function.Location;

    // Port of Guava CaseFormat.LOWER_UNDERSCORE.to(UPPER_CAMEL, ...).
    private static string LowerUnderscoreToUpperCamel(string value)
    {
        var sb = new StringBuilder(value.Length);
        bool upperNext = true;
        foreach (char c in value)
        {
            if (c == '_')
            {
                upperNext = true;
                continue;
            }
            sb.Append(upperNext ? char.ToUpperInvariant(c) : c);
            upperNext = false;
        }
        return sb.ToString();
    }
}
