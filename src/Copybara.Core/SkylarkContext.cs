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

using Starlark.Eval;

namespace Copybara;

/// <summary>A context object that can be enhanced with Skylark information.</summary>
public interface ISkylarkContext<out T>
{
    /// <summary>Create a copy instance with Skylark function parameters.</summary>
    T WithParams(Dict @params);

    /// <summary>Performs tasks after Starlark code finishes.</summary>
    void OnFinish(object? result, object context);
}
