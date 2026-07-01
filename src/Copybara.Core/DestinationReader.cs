/*
 * Copyright (C) 2020 Google LLC
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

using Copybara.Exceptions;
using Copybara.Util;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara;

/// <summary>An api handle to read files from the destination, rather than just the origin.</summary>
[StarlarkBuiltin("destination_reader", Doc = "Handle to read from the destination")]
public abstract class DestinationReader : IStarlarkValue
{
    public static readonly DestinationReader NotImplemented = new NotImplementedReader();

    public static readonly DestinationReader NoopDestinationReader = new NoopReader();

    [StarlarkMethod("read_file", Doc = "Read a file from the destination.")]
    public abstract string ReadFile(
        [Param(Name = "path", Named = true, Doc = "Path to the file.")] string path);

    [StarlarkMethod(
        "copy_destination_files",
        Doc = "Copy files from the destination into the workdir.")]
    // TODO(joshgoldman): refactor this out in favor of directory-specific version
    public abstract void CopyDestinationFiles(
        [Param(
            Name = "glob",
            Named = true,
            AllowedTypes = new[] { typeof(Glob), typeof(StarlarkList), typeof(NoneType) },
            Doc = "Files to copy to the workdir, potentially overwriting files checked out from the"
                + " origin.")]
        object glob,
        [Param(
            Name = "path",
            Named = true,
            Doc = "Optional path to copy the files to",
            AllowedTypes = new[] { typeof(CheckoutPath), typeof(NoneType) },
            DefaultValue = "None")]
        object path);

    /// <summary>
    /// Similar to <see cref="CopyDestinationFiles"/> but specifies a destination directory (instead
    /// of using the default working directory workdir).
    /// </summary>
    public abstract void CopyDestinationFilesToDirectory(Glob glob, string directory);

    [StarlarkMethod(
        "file_exists",
        Doc = "Checks whether a given file exists in the destination.")]
    public abstract bool Exists(
        [Param(Name = "path", Named = true, Doc = "Path to the file.")] string path);

    /// <summary>Fetch the destination version at which this file was last modified.</summary>
    public virtual string? LastModified(string path) =>
        throw new NotSupportedException(
            "Last modified is not implemented in this destination reader.");

    /// <summary>
    /// Returns true if this implementation supports <see cref="GetHash"/>.
    ///
    /// <para>If this returns false, hashes will be computed by reading the files from the local
    /// filesystem instead. An implementation should only provide getHash() if it can compute the
    /// hashes in a more efficient way.</para>
    /// </summary>
    public virtual bool SupportsGetHash() => false;

    /// <summary>Obtain the hash of the destination file at this path.</summary>
    public virtual string GetHash(string path) =>
        throw new NotSupportedException("Get hash is not implemented in this destination reader.");

    private sealed class NotImplementedReader : DestinationReader
    {
        public override string ReadFile(string path) =>
            throw new RepoException("Reading files is not implemented by this destination");

        public override void CopyDestinationFiles(object glob, object path) =>
            throw new RepoException("Reading files is not implemented by this destination");

        public override void CopyDestinationFilesToDirectory(Glob glob, string directory) =>
            throw new RepoException("Reading files is not implemented by this destination");

        public override bool Exists(string path) => false;
    }

    private sealed class NoopReader : DestinationReader
    {
        public override string ReadFile(string path) => "";

        public override void CopyDestinationFiles(object glob, object path) { }

        public override void CopyDestinationFilesToDirectory(Glob glob, string directory) { }

        public override bool Exists(string path) => false;
    }
}
