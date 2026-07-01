/*
 * Copyright (C) 2020 Google Inc.
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

namespace Copybara.Format;

/// <summary>Specifies how Buildifier is executed.</summary>
public class BuildifierOptions : IOption
{
    [Flag(
        "--buildifier-bin",
        "Binary to use for buildifier (Default is /usr/bin/buildifier)",
        Hidden = true)]
    public string BuildifierBin { get; set; } = "/usr/bin/buildifier";

    [Flag("--buildifier-batch-size", "Process files in batches this size")]
    public int BatchSize { get; set; } = 200;
}
