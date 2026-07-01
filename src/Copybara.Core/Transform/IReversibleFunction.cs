/*
 * Copyright (C) 2019 Google Inc.
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

namespace Copybara.Transform;

/// <summary>A function that given an object can map to another object.</summary>
[StarlarkBuiltin(
    "mapping_function",
    Doc = "A function that given an object can map to another object")]
public interface IReversibleFunction<TIn, TOut> : IStarlarkValue
{
    /// <summary>Applies the function to the given input.</summary>
    TOut Apply(TIn input);

    /// <summary>Create a reverse of the function.</summary>
    /// <exception cref="NonReversibleValidationException">if the mapping is not reversible.</exception>
    IReversibleFunction<TOut, TIn> ReverseMapping();
}
