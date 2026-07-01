/*
 * Copyright (C) 2019 Google Inc.
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

using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Util;
using Starlark.Syntax;
using Copybara.Util.Console;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Transform.Debug;

/// <summary>
/// A transformation that delegates to another transformation and allows debugging its execution.
/// </summary>
public sealed class TransformDebug : ITransformation
{
    private const string CopybaraMetadataFakeFile =
        " Copybara metadata(Author, description, etc.)";

    private readonly ITransformation _delegate;
    private readonly DebugOptions _debugOptions;
    private readonly IReadOnlyDictionary<string, string> _environment;

    private TransformDebug(
        ITransformation delegateTransform,
        DebugOptions debugOptions,
        IReadOnlyDictionary<string, string> environment)
    {
        _delegate = Preconditions.CheckNotNull(delegateTransform);
        _debugOptions = Preconditions.CheckNotNull(debugOptions);
        _environment = Preconditions.CheckNotNull(environment);
    }

    public TransformationStatus Transform(TransformWork work)
    {
        Console console = work.GetConsole();
        bool fileDebug = _debugOptions.GetDebugFileBreak() != null;
        bool metadataDebug = _debugOptions.DebugMetadataBreak;

        Regex? debugTransformBreak = _debugOptions.GetDebugTransformBreak();
        bool transformMatch =
            debugTransformBreak != null && debugTransformBreak.IsMatch(_delegate.Describe());

        if (!fileDebug && !metadataDebug && !transformMatch)
        {
            // Nothing to debug!
            return _delegate.Transform(work);
        }

        var before = ReadState(work, fileDebug || transformMatch, work.GetTreeState());
        TransformationStatus status = _delegate.Transform(work);
        work.ValidateTreeStateCache();
        var after = ReadState(work, fileDebug || transformMatch, work.GetTreeState());

        var difference = new MapDifference(before, after);

        bool stop = transformMatch;
        if (fileDebug)
        {
            IPathMatcher debugFileBreak = _debugOptions.GetDebugFileBreak()!.RelativeTo("/");
            foreach (string path in difference.OnlyOnLeft.Keys
                         .Concat(difference.OnlyOnRight.Keys)
                         .Concat(difference.Differing.Keys))
            {
                if (path.Equals(CopybaraMetadataFakeFile))
                {
                    continue;
                }
                if (debugFileBreak.Matches("/" + path))
                {
                    stop = true;
                    console.InfoFmt("File '{0}' change matched. Stopping", path);
                    break;
                }
            }
        }
        else if (metadataDebug
            && !ByteArraysEqual(
                before.GetValueOrDefault(CopybaraMetadataFakeFile),
                after.GetValueOrDefault(CopybaraMetadataFakeFile)))
        {
            stop = true;
            console.InfoFmt("Message, author and/or labels changed");
        }

        if (!stop)
        {
            return status;
        }
        if (!transformMatch)
        {
            // Stopped because of file/metadata change. Show the diff directly.
            ShowDiff(console, difference);
        }
        while (true)
        {
            string answer = console.Ask(
                "Debugger stopped after '" + _delegate.Describe() + "' "
                    + console.Colorize(AnsiColor.Purple, _delegate.Location().ToString()) + ".\n"
                    + "      Current file state can be checked at " + work.GetCheckoutDir() + "\n"
                    + "Diff (d), Continue (c), Stop (s): ",
                "d",
                input => input is "d" or "c" or "s");

            switch (answer)
            {
                case "d":
                    ShowDiff(console, difference);
                    break;
                case "c":
                    return status;
                case "s":
                    throw new ValidationException("Stopped by user");
            }
        }
    }

    private void ShowDiff(Console console, MapDifference difference)
    {
        if (difference.AreEqual())
        {
            console.Info("No changes detected");
            return;
        }
        string debug = _debugOptions.CreateDiffDirectory();
        FileUtil.DeleteRecursively(debug);
        Directory.CreateDirectory(debug);
        string beforePath = Path.Combine(debug, "before");
        Directory.CreateDirectory(beforePath);
        string afterPath = Path.Combine(debug, "after");
        Directory.CreateDirectory(afterPath);

        foreach (var entry in difference.OnlyOnLeft)
        {
            WriteFile(beforePath, entry.Key, entry.Value);
        }
        foreach (var entry in difference.OnlyOnRight)
        {
            WriteFile(afterPath, entry.Key, entry.Value);
        }
        foreach (var entry in difference.Differing)
        {
            WriteFile(beforePath, entry.Key, entry.Value.Left);
            WriteFile(afterPath, entry.Key, entry.Value.Right);
        }

        try
        {
            console.Info(
                DiffUtil.Colorize(
                    console,
                    Encoding.UTF8.GetString(
                        DiffUtil.Diff(beforePath, afterPath, verbose: false, _environment))));
        }
        catch (InsideGitDirException e)
        {
            throw new ValidationException(
                "Cannot debug if temporary directory is inside a git directory", e);
        }
    }

    private static void WriteFile(string basePath, string path, byte[] content)
    {
        string target = Path.Combine(basePath, path);
        string? parent = Path.GetDirectoryName(target);
        if (parent != null)
        {
            Directory.CreateDirectory(parent);
        }
        File.WriteAllBytes(target, content);
    }

    private static SortedDictionary<string, byte[]> ReadState(
        TransformWork work, bool filesNeeded, TreeState.TreeState treeState)
    {
        var result = new SortedDictionary<string, byte[]>(StringComparer.Ordinal)
        {
            [CopybaraMetadataFakeFile] = Encoding.UTF8.GetBytes(work.GetMetadata().ToString()),
        };

        if (filesNeeded)
        {
            var files = treeState.Find(Glob.AllFiles.RelativeTo(work.GetCheckoutDir()));
            foreach (var beforeFile in files)
            {
                string filePath = beforeFile.GetPath();
                // Ignore symlinks.
                var info = new FileInfo(filePath);
                if (info.LinkTarget != null)
                {
                    continue;
                }
                string relative = Path.GetRelativePath(work.GetCheckoutDir(), filePath);
                byte[] bytes = File.ReadAllBytes(filePath);
                result[relative] = bytes.Length > 100_000
                    ? Encoding.UTF8.GetBytes(
                        "File too big. Hash: " + Convert.ToHexString(SHA256.HashData(bytes)))
                    : bytes;
            }
        }
        return result;
    }

    /// <summary>Returns the inner transformation.</summary>
    public ITransformation GetDelegate() => _delegate;

    public ITransformation Reverse() =>
        new TransformDebug(_delegate.Reverse(), _debugOptions, _environment);

    public string Describe() => _delegate.Describe();

    public bool CanJoin(ITransformation transformation) => false;

    public ITransformation Join(ITransformation next) =>
        throw new InvalidOperationException(
            $"Debugger doesn't support join!: delegate = {_delegate}, next = {next}");

    public Location Location() => _delegate.Location();

    internal static ITransformation WithDebugger(
        ITransformation t,
        DebugOptions debugOptions,
        IReadOnlyDictionary<string, string> environment) =>
        t is TransformDebug ? t : new TransformDebug(t, debugOptions, environment);

    private static bool ByteArraysEqual(byte[]? one, byte[]? other)
    {
        if (ReferenceEquals(one, other))
        {
            return true;
        }
        if (one == null || other == null)
        {
            return false;
        }
        return one.AsSpan().SequenceEqual(other);
    }

    /// <summary>
    /// A minimal replacement for Guava's <c>MapDifference</c> over byte-array-valued maps.
    /// </summary>
    private sealed class MapDifference
    {
        public SortedDictionary<string, byte[]> OnlyOnLeft { get; } = new(StringComparer.Ordinal);
        public SortedDictionary<string, byte[]> OnlyOnRight { get; } = new(StringComparer.Ordinal);

        public SortedDictionary<string, (byte[] Left, byte[] Right)> Differing { get; } =
            new(StringComparer.Ordinal);

        public MapDifference(
            SortedDictionary<string, byte[]> left, SortedDictionary<string, byte[]> right)
        {
            foreach (var entry in left)
            {
                if (!right.TryGetValue(entry.Key, out var rightValue))
                {
                    OnlyOnLeft[entry.Key] = entry.Value;
                }
                else if (!ByteArraysEqual(entry.Value, rightValue))
                {
                    Differing[entry.Key] = (entry.Value, rightValue);
                }
            }
            foreach (var entry in right)
            {
                if (!left.ContainsKey(entry.Key))
                {
                    OnlyOnRight[entry.Key] = entry.Value;
                }
            }
        }

        public bool AreEqual() =>
            OnlyOnLeft.Count == 0 && OnlyOnRight.Count == 0 && Differing.Count == 0;
    }
}
