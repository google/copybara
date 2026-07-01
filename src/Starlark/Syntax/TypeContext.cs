// Copyright 2026 The Bazel Authors. All rights reserved.
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

namespace Starlark.Syntax;

/// <summary>
/// A context for obtaining more detailed information about Starlark types.
///
/// <para>This is used to inject type information from the eval/ package into the syntax/ package,
/// e.g. the method APIs of StarlarkList.</para>
/// </summary>
public interface TypeContext
{
    /// <summary>
    /// Returns the type of the given field of a <c>list[T]</c> type, or null if no such field exists.
    /// </summary>
    StarlarkType? GetListFieldType(string name);

    /// <summary>
    /// Returns the type of the given field of a <c>dict[K, V]</c> type, or null if no such field
    /// exists.
    /// </summary>
    StarlarkType? GetDictFieldType(string name);

    /// <summary>
    /// Returns the type of the given field of a <c>set[T]</c> type, or null if no such field exists.
    /// </summary>
    StarlarkType? GetSetFieldType(string name);
}
