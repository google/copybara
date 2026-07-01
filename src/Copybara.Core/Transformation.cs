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

using Starlark.Annot;
using Starlark.Eval;
using Starlark.Syntax;

namespace Copybara;

/// <summary>Interface implemented by all source code transformations.</summary>
[StarlarkBuiltin(
    "transformation",
    Doc =
        "A single operation which modifies the source checked out from the origin, prior to writing"
        + " it to the destination. Transformations can also be used to perform validations or"
        + " checks.<br/><br/>Many common transformations are provided by the built-in"
        + " libraries, such as <a href='#core'><code>core</code></a>.<br/><br/>Custom"
        + " transformations can be defined in Starlark code via <a"
        + " href='#core.dynamic_transform'><code>core.dynamic_transform</code></a>.")]
public interface ITransformation : IStarlarkValue
{
    /// <summary>Transforms the files inside the checkout dir specified by <paramref name="work"/>.</summary>
    TransformationStatus Transform(TransformWork work);

    /// <summary>
    /// Returns a transformation which runs this transformation in reverse.
    /// </summary>
    /// <exception cref="Copybara.Exceptions.NonReversibleValidationException">
    /// if the transform is not reversible.
    /// </exception>
    ITransformation Reverse();

    /// <summary>
    /// Return a high level description of what the transform is doing. Note that this should not be
    /// the <see cref="object.ToString"/> method but something more user friendly.
    /// </summary>
    string Describe();

    /// <summary>Starlark location of the transformation.</summary>
    Location Location() => Starlark.Syntax.Location.BUILTIN;

    bool CanJoin(ITransformation transformation) => false;

    ITransformation Join(ITransformation next) =>
        throw new InvalidOperationException($"Unexpected join call for {this} and {next}");
}
