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

using System.Collections.Immutable;

using Copybara.Onboard.Core.Template;

namespace Copybara.Onboard.Core;

/// <summary>
/// Represents an object that can be used for resolving <see cref="Input{T}"/> objects and can be
/// used by <see cref="IInputProvider"/>s to resolve <see cref="Input{T}"/>s recursively.
/// </summary>
public interface IInputProviderResolver
{
    /// <summary>
    /// Given an <see cref="Input{T}"/>, resolve to the corresponding value.
    /// </summary>
    /// <exception cref="System.Threading.ThreadInterruptedException">if user cancels the request
    /// (e.g. Ctrl-C on the console).</exception>
    /// <exception cref="CannotProvideException">if there is a failure during the resolution.</exception>
    T Resolve<T>(Input<T> input)
        where T : class;

    /// <summary>
    /// Resolve an input that might not have a value but that it is optional. Returns <c>null</c>
    /// when the input cannot be provided.
    /// </summary>
    public T? ResolveOptional<T>(Input<T> input)
        where T : class
    {
        try
        {
            return Resolve(input);
        }
        catch (CannotProvideException)
        {
            return null;
        }
    }

    /// <summary>Config generators registered in the system.</summary>
    public IReadOnlyDictionary<string, IConfigGenerator> GetGenerators() =>
        ImmutableDictionary<string, IConfigGenerator>.Empty;

    /// <summary>Given a Starlark string, convert it to its corresponding object.</summary>
    /// <exception cref="CannotConvertException"/>
    public T ParseStarlark<T>(string starlark)
        where T : class =>
        throw new CannotConvertException("Parsing Starlark not supported");
}
