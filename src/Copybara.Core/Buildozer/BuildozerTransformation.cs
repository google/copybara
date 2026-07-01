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

using Copybara.Buildozer;

namespace Copybara.Buildozer;

/// <summary>
/// Common interface implemented by all buildozer transformations.
///
/// <para>Used by <see cref="BuildozerBatch"/> to batch multiple invocations in one buildozer cli
/// call.</para>
/// </summary>
public interface IBuildozerTransformation : ITransformation
{
    /// <summary>Actions to run before calling buildozer. For example creating files.</summary>
    void BeforeRun(TransformWork work)
    {
    }

    /// <summary>List of commands to execute.</summary>
    IEnumerable<BuildozerOptions.BuildozerCommand> GetCommands();
}
