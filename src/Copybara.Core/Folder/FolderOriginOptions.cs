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

namespace Copybara.Folder;

/// <summary>Arguments for FolderOrigin.</summary>
public sealed class FolderOriginOptions : IOption
{
    [Flag(
        "--folder-origin-author",
        "Deprecated. Please use '--force-author'."
            + " Author of the change being migrated from folder.origin()")]
    public string Author { get; set; } = "Copybara <noreply@copybara.io>";

    [Flag(
        "--folder-origin-message",
        "Deprecated. Please use '--force-message'. Message of the change being migrated"
            + " from folder.origin()")]
    public string Message { get; set; } = "Copybara code migration";

    [Flag(
        "--folder-origin-version",
        "The version string associated with the change migrated from folder.origin(). If not"
            + " specified, the default will be the folder path.")]
    public string? Version { get; set; }

    [Flag(
        "--folder-origin-ignore-invalid-symlinks",
        "DEPRECATED - equivalent to folder.origin(outside_symlinks_mode='IGNORE',"
            + " broken_symlinks_mode='IGNORE')",
        Arity = 1)]
    public bool? IgnoreInvalidSymlinks { get; set; }
}
