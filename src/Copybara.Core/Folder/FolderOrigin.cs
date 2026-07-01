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

using System.Collections.Immutable;
using Copybara.Authoring;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Revision;
using Copybara.Util;

namespace Copybara.Folder;

/// <summary>Use a folder as the input for the migration.</summary>
public class FolderOrigin : IOrigin<FolderRevision>
{
    private const string LabelName = "FolderOrigin-RevId";

    private const UnixFileMode FilePermissions = UnixFileMode.UserRead | UnixFileMode.UserWrite;

    private readonly string _fileSystemRoot;
    private readonly Author _author;
    private readonly string _message;
    private readonly string _cwd;
    private readonly FileUtil.CopySymlinkStrategy _copySymlinkStrategy;
    private readonly string? _version;

    internal FolderOrigin(
        string fileSystemRoot,
        Author author,
        string message,
        string cwd,
        FileUtil.CopySymlinkStrategy copySymlinkStrategy,
        string? version)
    {
        _fileSystemRoot = Preconditions.CheckNotNull(fileSystemRoot);
        _author = author;
        _message = message;
        _cwd = Preconditions.CheckNotNull(cwd);
        _copySymlinkStrategy = Preconditions.CheckNotNull(copySymlinkStrategy);
        _version = version;
    }

    public FolderRevision Resolve(string? reference)
    {
        ValidationException.CheckCondition(
            reference != null,
            "A path is expected as reference in the command line. Invoke copybara as:\n"
                + "    copybara copy.bara.sky workflow_name ORIGIN_FOLDER");
        string path = reference!;
        if (!Path.IsPathRooted(path))
        {
            path = Path.GetFullPath(Path.Combine(_cwd, path));
        }
        ValidationException.CheckCondition(
            Directory.Exists(path) || File.Exists(path), "%s folder doesn't exist", path);
        ValidationException.CheckCondition(Directory.Exists(path), "%s is not a folder", path);

        return new FolderRevision(path, DateTimeOffset.Now, _version);
    }

    public IOrigin<FolderRevision>.IReader<FolderRevision> NewReader(
        Glob originFiles, Authoring.Authoring authoring) =>
        new ReaderImpl(this, originFiles);

    public string GetLabelName() => LabelName;

    public string GetTypeName() => "folder.origin";

    public ImmutableListMultimap<string, string> Describe(Glob? originFiles)
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        builder.Put("type", GetTypeName());
        return builder.Build();
    }

    private sealed class ReaderImpl : IOrigin<FolderRevision>.IReader<FolderRevision>
    {
        private readonly FolderOrigin _origin;
        private readonly Glob _originFiles;

        public ReaderImpl(FolderOrigin origin, Glob originFiles)
        {
            _origin = origin;
            _originFiles = originFiles;
        }

        public void Checkout(FolderRevision reference, string checkoutDir)
        {
            try
            {
                FileUtil.CopyFilesRecursively(
                    reference.Path, checkoutDir, _origin._copySymlinkStrategy, _originFiles);
                FileUtil.AddPermissionsAllRecursively(checkoutDir, FilePermissions);
            }
            catch (SymlinkException e)
            {
                throw new ValidationException(
                    "Cannot copy files into the workdir: " + e.Message, e);
            }
            catch (IOException e)
            {
                throw new RepoException(
                    "Cannot copy files into the workdir:\n"
                        + $"  origin folder: {reference.Path}\n"
                        + $"  workdir: {checkoutDir}",
                    e);
            }
        }

        public Origin.ChangesResponse<FolderRevision> Changes(
            FolderRevision? fromRef, FolderRevision toRef)
        {
            // Ignore fromRef since a folder doesn't have history of changes
            return Origin.ChangesResponse<FolderRevision>.ForChanges(
                ImmutableArray.Create(Change(toRef)));
        }

        public bool SupportsHistory() => false;

        public Change<FolderRevision> Change(FolderRevision reference) =>
            new(
                reference,
                _origin._author,
                _origin._message,
                reference.ReadTimestamp() ?? DateTimeOffset.Now,
                ImmutableListMultimap<string, string>.Empty);

        public void VisitChanges(FolderRevision? start, IChangesVisitor visitor)
        {
            FolderRevision reference = start!;
            var change = new Change<IRevision>(
                reference,
                _origin._author,
                _origin._message,
                reference.ReadTimestamp() ?? DateTimeOffset.Now,
                ImmutableListMultimap<string, string>.Empty);
            visitor.Visit(change);
        }
    }
}
