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

using Starlark.Eval;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara.Transform;

/// <summary>
/// This class consists exclusively of static methods that operate on or return
/// <see cref="ITransformation"/>s.
/// </summary>
public static class Transformations
{
    /// <summary>
    /// Cast a Starlark callable to a <see cref="ITransformation"/>. If the input is already a
    /// <see cref="ITransformation"/>, it is returned unchanged.
    ///
    /// <para>Many functions in Copybara's Starlark API require <see cref="ITransformation"/>s as
    /// input. In nearly all cases, the user may instead choose to provide an ordinary Starlark
    /// function. This utility method converts those functions into objects with the necessary type.
    /// </para>
    /// </summary>
    /// <param name="element">the object to cast to a Transformation.</param>
    /// <param name="description">
    /// the name of the field for which this object was provided by the user as a parameter, used for
    /// error messages.
    /// </param>
    /// <param name="printHandler">
    /// the <see cref="StarlarkThread.PrintHandler"/> to use for the thread which runs this Starlark
    /// function.
    /// </param>
    public static ITransformation ToTransformation(
        object? element, string description, StarlarkThread.PrintHandler printHandler)
    {
        if (element is IStarlarkCallable callable)
        {
            return new SkylarkTransformation(callable, Dict.Empty(), printHandler);
        }
        if (element is ITransformation transformation)
        {
            return transformation;
        }
        throw StarlarkRt.Errorf(
            "for '{0}' element, got {1}, want function or transformation",
            description, StarlarkRt.Type(element));
    }
}
