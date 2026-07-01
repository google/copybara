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

using Copybara.Common;
using Copybara.Exceptions;
using Starlark.Eval;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara.Action;

/// <summary>
/// An implementation of <see cref="IAction"/> that delegates to a Starlark function. Port of
/// <c>com.google.copybara.action.StarlarkAction</c>.
/// </summary>
public sealed class StarlarkAction : IAction
{
    private readonly string _name;
    private readonly IStarlarkCallable _function;
    private readonly Dict _params;
    private readonly StarlarkThread.PrintHandler _printHandler;

    public StarlarkAction(
        string name,
        IStarlarkCallable function,
        Dict @params,
        StarlarkThread.PrintHandler printHandler)
    {
        _name = name;
        _function = Preconditions.CheckNotNull(function);
        _params = Preconditions.CheckNotNull(@params);
        _printHandler = Preconditions.CheckNotNull(printHandler);
    }

    public void Run<T>(ActionContext<T> context)
        where T : ISkylarkContext<T>
    {
        T actionContext = context.WithParams(_params);
        using var mu = Mutability.Create("dynamic_action");
        try
        {
            var thread = StarlarkThread.CreateTransient(mu, StarlarkSemantics.DEFAULT);
            thread.SetPrintHandler(_printHandler);
            object? result = StarlarkRt.Fastcall(
                thread, _function, new object?[] { actionContext }, Array.Empty<object?>());
            context.OnFinish(result, actionContext);
        }
        catch (EvalException e)
        {
            var cause = e.InnerException;
            string error = string.Format(
                "Error while executing the skylark transformation {0}: {1}.",
                _function.Name, e.Message);
            if (cause is RepoException)
            {
                throw new RepoException(error, cause);
            }
            throw new ValidationException(error, cause);
        }
    }

    public string GetName() => _name;

    public ImmutableListMultimap<string, string> Describe()
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        foreach (var paramKey in _params.Keys)
        {
            builder.Put(
                paramKey?.ToString() ?? "null",
                (paramKey is null ? null : _params.Get(paramKey))?.ToString() ?? "null");
        }
        return builder.Build();
    }

    public override string ToString() => $"StarlarkAction{{name={_function.Name}}}";
}
