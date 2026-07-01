// Copyright 2018 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

using Starlark.Syntax;

namespace Starlark.Eval;

/// <summary>
/// Implemented by all Starlark values that may be called like a function. Port of
/// <c>net.starlark.java.eval.StarlarkCallable</c>.
/// </summary>
public interface IStarlarkCallable : IStarlarkValue
{
    /// <summary>The "convenient" implementation of function calling.</summary>
    object? Call(StarlarkThread thread, Tuple args, Dict kwargs) =>
        throw Starlark.Errorf("function {0} not implemented", Name);

    /// <summary>
    /// The "fast" implementation of function calling. The default forwards to <see cref="Call"/>
    /// after rejecting duplicate named arguments. <paramref name="named"/> is a flat array of
    /// alternating name/value pairs.
    /// </summary>
    object? Fastcall(StarlarkThread thread, object?[] positional, object?[] named)
    {
        var kwargs = new Dict.Builder();
        var seen = new HashSet<string>();
        for (int i = 0; i < named.Length; i += 2)
        {
            string key = (string)named[i]!;
            if (!seen.Add(key))
            {
                throw Starlark.Errorf("{0} got multiple values for parameter '{1}'", this, key);
            }
            kwargs.Put(key, named[i + 1]);
        }
        return Call(thread, Tuple.Of(positional), kwargs.Build(thread.Mutability));
    }

    /// <summary>Returns the form this callable value should take in a stack trace.</summary>
    string Name { get; }

    /// <summary>Returns the location of the definition, or BUILTIN if not defined in Starlark.</summary>
    Location Location => Location.BUILTIN;
}
