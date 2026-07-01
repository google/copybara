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

using Copybara.Util;

namespace Copybara.TreeState;

/// <summary>
/// Utilities for dealing with <see cref="TreeState"/> objects. Port of
/// <c>com.google.copybara.treestate.TreeStateUtil</c>.
/// </summary>
public static class TreeStateUtil
{
    /// <summary>Filter a collection of <see cref="TreeState.FileState"/>s using an <see cref="IPathMatcher"/>.</summary>
    public static List<TreeState.FileState> Filter(
        IPathMatcher pathMatcher, IEnumerable<TreeState.FileState> files) =>
        files.Where(fileState => pathMatcher.Matches(fileState.GetPath())).ToList();
}
