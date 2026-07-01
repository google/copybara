// Copyright 2022 The Bazel Authors. All rights reserved.
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

namespace Starlark.Eval;

/// <summary>
/// Wrapper on a predeclared value that controls its accessibility to Starlark based on a semantic
/// flag and/or the Module's client data. Port of <c>net.starlark.java.eval.GuardedValue</c>.
/// </summary>
public interface IGuardedValue
{
    /// <summary>Returns an error describing an attempt to access this guard's protected object.</summary>
    string GetErrorFromAttemptingAccess(string name);

    /// <summary>Returns this guard's underlying object.</summary>
    object GetObject();

    /// <summary>Returns true if the underlying object is accessible under the given semantics.</summary>
    bool IsObjectAccessibleUsingSemantics(StarlarkSemantics semantics, object? clientData);
}
