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
using Copybara.Common;
using Copybara.Effect;
using Copybara.Exceptions;
using Copybara.Revision;
using Copybara.Util;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Folder;

/// <summary>
/// Writes the output tree to a local destination. Any file that is not excluded in the configuration
/// gets deleted before writing the new files.
/// </summary>
public class FolderDestination : IDestination<IRevision>
{
    private static readonly ILogger Logger = NullLogger.Instance;

    private const string FolderDestinationName = "folder.destination";

    private static readonly string HistoryNotSupported =
        $"History not supported in {FolderDestinationName}. Consider passing a ref as an argument, "
            + "or using --last-rev.";

    private readonly GeneralOptions _generalOptions;
    private readonly FolderDestinationOptions _folderDestinationOptions;

    internal FolderDestination(
        GeneralOptions generalOptions, FolderDestinationOptions folderDestinationOptions)
    {
        _generalOptions = Preconditions.CheckNotNull(generalOptions);
        _folderDestinationOptions = Preconditions.CheckNotNull(folderDestinationOptions);
    }

    public IDestination<IRevision>.IWriter<IRevision> NewWriter(WriterContext writerContext)
    {
        if (writerContext.IsDryRun())
        {
            _generalOptions.GetConsole().Warn(
                "--dry-run does not have any effect for folder.destination");
        }
        return new WriterImpl(this);
    }

    private sealed class WriterImpl : IDestination<IRevision>.IWriter<IRevision>
    {
        private readonly FolderDestination _destination;

        public WriterImpl(FolderDestination destination) => _destination = destination;

        public DestinationStatus? GetDestinationStatus(Glob destinationFiles, string labelName) =>
            throw new ValidationException(HistoryNotSupported);

        public void VisitChanges(IRevision? start, IChangesVisitor visitor) =>
            throw new ValidationException(HistoryNotSupported);

        public DestinationReader GetDestinationReader(
            Console console, Origin.Baseline<IRevision>? baseline, string workdir) =>
            GetDestinationReader(console, (string?)"", workdir);

        public DestinationReader GetDestinationReader(
            Console console, string? baseline, string workdir)
        {
            try
            {
                return new FolderDestinationReader(_destination.GetFolderPath(console), workdir);
            }
            catch (IOException e)
            {
                throw new RepoException("Failed to initialize destination reader.", e);
            }
        }

        public bool SupportsHistory() => false;

        public IReadOnlyList<DestinationEffect> Write(
            TransformResult transformResult, Glob destinationFiles, Console console)
        {
            string localFolder = _destination.GetFolderPath(console);
            return WriteToFolder(transformResult, destinationFiles, console, localFolder);
        }
    }

    public static IReadOnlyList<DestinationEffect> WriteToFolder(
        TransformResult transformResult, Glob destinationFiles, Console console, string localFolder)
    {
        console.Progress("FolderDestination: creating " + localFolder);
        bool exists = Directory.Exists(localFolder) || File.Exists(localFolder);
        try
        {
            if (File.Exists(localFolder) && !Directory.Exists(localFolder))
            {
                // Mirrors Java Files.createDirectories throwing FileAlreadyExistsException when a
                // non-directory already exists at the path.
                throw new RepoException(
                    $"Cannot create '{localFolder}' because '{localFolder}' already exists and is "
                        + "not a directory");
            }
            Directory.CreateDirectory(localFolder);
        }
        catch (UnauthorizedAccessException e)
        {
            throw new ValidationException("Path is not accessible: " + localFolder, e);
        }
        catch (IOException e)
            when (e.Message.Contains("Read-only file system")
                || e.Message.Contains("Operation not permitted"))
        {
            throw new ValidationException("Path is not accessible: " + localFolder, e);
        }

        console.Progress("FolderDestination: Deleting destination files in " + localFolder);
        int numDeletedFiles = FileUtil.DeleteFilesRecursively(localFolder, destinationFiles);
        console.Info(
            $"FolderDestination: Deleted {numDeletedFiles} existing destination files in {localFolder}");

        console.Progress("FolderDestination: Copying contents of the workdir to " + localFolder);
        FileUtil.CopyFilesRecursively(
            transformResult.GetPath(),
            localFolder,
            FileUtil.CopySymlinkStrategy.FailOutsideSymlinks);
        return ImmutableArray.Create(
            new DestinationEffect(
                exists ? DestinationEffect.EffectType.UPDATED : DestinationEffect.EffectType.CREATED,
                $"Folder '{localFolder}' contains the output files of the migration",
                transformResult.GetChanges().GetCurrent().Cast<OriginRef>().ToList(),
                new DestinationEffect.DestinationRef(localFolder, "local_folder", localFolder)));
    }

    private string GetFolderPath(Console console)
    {
        string? localFolderOption = _folderDestinationOptions.LocalFolder;
        string localFolder;
        if (string.IsNullOrEmpty(localFolderOption))
        {
            localFolder = _generalOptions.GetDirFactory().NewTempDir("folder-destination");
            string msg =
                "Using folder in default root (--folder-dir to override): "
                    + Path.GetFullPath(localFolder);
            Logger.LogInformation("{Message}", msg);
            console.Info(msg);
        }
        else
        {
            // Lets assume we are in the same filesystem for now...
            localFolder = localFolderOption;
            if (!Path.IsPathRooted(localFolder))
            {
                localFolder = Path.Combine(_generalOptions.GetCwd(), localFolder);
            }
        }

        // Normalize for console and other stuff that might require normalized paths
        return Path.GetFullPath(localFolder);
    }

    public string GetLabelNameWhenOrigin() =>
        throw new ValidationException(FolderDestinationName + " does not support labels");

    public string GetTypeName() => "folder.destination";

    public ImmutableListMultimap<string, string> Describe(Glob? originFiles)
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        builder.Put("type", GetTypeName());
        return builder.Build();
    }
}
