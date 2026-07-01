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
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using Copybara.Common;
using Microsoft.Extensions.Logging;
using Microsoft.Extensions.Logging.Abstractions;

namespace Copybara.Util;

/// <summary>
/// Utility methods for files. Port of <c>com.google.copybara.util.FileUtil</c>.
///
/// <para>Java's <c>java.nio.file.Path</c> is represented here as a <c>string</c> and matching uses
/// <c>/</c>-separated, normalized paths (see <see cref="IPathMatcher"/>).</para>
/// </summary>
public static class FileUtil
{
    private static readonly ILogger Logger = NullLogger.Instance;

    private static readonly IPathMatcher AllFilesMatcher = new AllFilesPathMatcher();

    // Anchored so that (like Java's Matcher.matches) the whole string must match.
    private static readonly Regex Relativism = new(@"\A(.*/)?[.][.]?(/.*)?\z", RegexOptions.Singleline);

    /// <summary>
    /// Checks that the given path is relative and does not contain any <c>.</c> or <c>..</c>
    /// components.
    /// </summary>
    /// <returns>the <paramref name="path"/> passed.</returns>
    public static string CheckNormalizedRelative(string path)
    {
        string normalized = PathNormalizer.Normalize(path);
        Preconditions.CheckArgument(
            !Relativism.IsMatch(normalized),
            "path has unexpected . or .. components: {0}", path);
        Preconditions.CheckArgument(
            !normalized.StartsWith('/'),
            "path must be relative, but it starts with /: {0}", path);
        return path;
    }

    /// <summary>How to handle symlinks in Copybara.</summary>
    public enum SymlinkMode
    {
        /// <summary>Copy the symlink as-is.</summary>
        CopyAsIs,

        /// <summary>Materialize the symlink (copy the target's contents).</summary>
        Materialize,

        /// <summary>Ignore the symlink.</summary>
        Ignore,

        /// <summary>Fail the operation.</summary>
        Fail,
    }

    /// <summary>Strategy for handling symlinks. Port of <c>FileUtil.CopySymlinkStrategy</c>.</summary>
    public sealed class CopySymlinkStrategy
    {
        public static readonly CopySymlinkStrategy FailOutsideSymlinks =
            new(SymlinkMode.CopyAsIs, SymlinkMode.Fail, SymlinkMode.Fail);

        public static readonly CopySymlinkStrategy MaterializeOutsideSymlinks =
            new(SymlinkMode.CopyAsIs, SymlinkMode.Materialize, SymlinkMode.Fail);

        public static readonly CopySymlinkStrategy IgnoreInvalidSymlinks =
            new(SymlinkMode.CopyAsIs, SymlinkMode.CopyAsIs, SymlinkMode.Ignore);

        private readonly SymlinkMode _inside;
        private readonly SymlinkMode _outside;
        private readonly SymlinkMode _broken;

        public CopySymlinkStrategy(SymlinkMode inside, SymlinkMode outside, SymlinkMode broken)
        {
            _inside = inside;
            _outside = outside;
            _broken = broken;
            Preconditions.CheckArgument(
                broken != SymlinkMode.Materialize,
                "MATERIALIZE is not a valid mode for broken symlinks");
        }

        public SymlinkMode GetSymlinkMode(ResolvedSymlink resolvedSymlink) =>
            resolvedSymlink.TargetLocationValue switch
            {
                ResolvedSymlink.TargetLocation.Inside => _inside,
                ResolvedSymlink.TargetLocation.Outside => _outside,
                ResolvedSymlink.TargetLocation.Broken => _broken,
                _ => throw new ArgumentOutOfRangeException(),
            };
    }

    /// <summary>Additional checks to run while copying files.</summary>
    public interface ICopyVisitorValidator
    {
        void Validate(string from);
    }

    /// <summary>
    /// Represents the regular file/directory that a symlink points to. Port of
    /// <c>FileUtil.ResolvedSymlink</c>.
    /// </summary>
    public sealed class ResolvedSymlink
    {
        public enum TargetLocation
        {
            Inside,
            Outside,
            Broken,
        }

        public ResolvedSymlink(string regularFile, TargetLocation targetLocation)
        {
            RegularFile = Preconditions.CheckNotNull(regularFile);
            TargetLocationValue = targetLocation;
        }

        public string RegularFile { get; }

        public TargetLocation TargetLocationValue { get; }

        public string GetRegularFile() => RegularFile;

        public TargetLocation GetTargetLocation() => TargetLocationValue;
    }

    public static void CopyFilesRecursively(
        string from, string to, CopySymlinkStrategy symlinkStrategy) =>
        CopyFilesRecursively(from, to, symlinkStrategy, Glob.AllFiles);

    /// <summary>
    /// Copies files from <paramref name="from"/> to <paramref name="to"/>. Fails if a destination
    /// file already exists. File attributes are copied where the platform supports it.
    /// </summary>
    public static void CopyFilesRecursively(
        string from, string to, CopySymlinkStrategy symlinkStrategy, Glob glob) =>
        CopyFilesRecursively(from, to, symlinkStrategy, glob, null);

    public static void CopyFilesRecursively(
        string from,
        string to,
        CopySymlinkStrategy symlinkStrategy,
        Glob glob,
        ICopyVisitorValidator? validator)
    {
        Preconditions.CheckArgument(Directory.Exists(from), "{0} (from) is not a directory", from);
        Preconditions.CheckArgument(Directory.Exists(to), "{0} (to) is not a directory", to);

        string fromFull = Path.GetFullPath(from);
        string toFull = Path.GetFullPath(to);
        IPathMatcher destMatcher = glob.RelativeTo(toFull);

        foreach (var root in glob.Roots())
        {
            string rootElement = string.IsNullOrEmpty(root) ? from : Path.Combine(from, root);
            if (!Directory.Exists(rootElement) && !File.Exists(rootElement))
            {
                continue;
            }
            foreach (var file in EnumerateFilesAndSymlinks(rootElement))
            {
                string relativeToFrom = GetRelative(fromFull, file);
                string destFile = NormalizeFull(Path.Combine(toFull, relativeToFrom));
                if (!destMatcher.Matches(destFile))
                {
                    continue;
                }
                validator?.Validate(file);
                string? parent = Path.GetDirectoryName(destFile);
                if (parent != null)
                {
                    Directory.CreateDirectory(parent);
                }

                var info = new FileInfo(file);
                bool symlink = (info.Attributes & FileAttributes.ReparsePoint) != 0;
                if (symlink)
                {
                    string? target = info.LinkTarget;
                    var mode = symlinkStrategy.GetSymlinkMode(
                        ResolveSymlink(glob.RelativeTo(fromFull), file));
                    switch (mode)
                    {
                        case SymlinkMode.Fail:
                            throw new SymlinkException(
                                $"Symlink '{file}' points to '{target}'");
                        case SymlinkMode.Ignore:
                            continue;
                        case SymlinkMode.CopyAsIs:
                            if (target != null)
                            {
                                File.CreateSymbolicLink(destFile, target);
                            }
                            continue;
                        case SymlinkMode.Materialize:
                            File.Copy(file, destFile, overwrite: false);
                            continue;
                    }
                }
                File.Copy(file, destFile, overwrite: false);
            }
        }
    }

    /// <summary>
    /// Adds the given permissions to the matching files under the given path. On non-POSIX platforms
    /// this is a best-effort operation using file attributes.
    /// </summary>
    public static void AddPermissionsRecursively(
        string path, UnixFileMode permissionsToAdd, IPathMatcher pathMatcher)
    {
        foreach (var file in EnumerateFilesAndSymlinks(path))
        {
            var info = new FileInfo(file);
            bool symlink = (info.Attributes & FileAttributes.ReparsePoint) != 0;
            if (!symlink && pathMatcher.Matches(NormalizeFull(file)))
            {
                AddPermissions(file, permissionsToAdd);
            }
        }
    }

    /// <summary>Adds the given permissions to all the files under the given path.</summary>
    public static void AddPermissionsAllRecursively(string path, UnixFileMode permissionsToAdd) =>
        AddPermissionsRecursively(path, permissionsToAdd, AllFilesMatcher);

    /// <summary>
    /// Deletes the files that match the <see cref="IPathMatcher"/>. Directories themselves are not
    /// removed, only the files inside them.
    /// </summary>
    public static int DeleteFilesRecursively(string path, IPathMatcher pathMatcher)
    {
        int counter = 0;
        string root = NormalizeFull(path);
        foreach (var file in EnumerateFilesAndSymlinks(root))
        {
            if (pathMatcher.Matches(NormalizeFull(file)))
            {
                File.Delete(file);
                counter++;
            }
        }
        return counter;
    }

    /// <summary>Deletes the files that match the <see cref="Glob"/>.</summary>
    public static int DeleteFilesRecursively(string path, Glob glob)
    {
        int counter = 0;
        string full = Path.GetFullPath(path);
        foreach (var root in glob.Roots())
        {
            string rootPath = string.IsNullOrEmpty(root) ? path : Path.Combine(path, root);
            if (Directory.Exists(rootPath) || File.Exists(rootPath))
            {
                counter += DeleteFilesRecursively(rootPath, glob.RelativeTo(full));
            }
        }
        return counter;
    }

    /// <summary>Delete all the contents of a path recursively.</summary>
    public static void DeleteRecursively(string path)
    {
        if (Directory.Exists(path))
        {
            Directory.Delete(path, recursive: true);
        }
        else if (File.Exists(path))
        {
            File.Delete(path);
        }
    }

    /// <summary>
    /// A <see cref="IPathMatcher"/> that returns true if any of the delegate matchers returns true.
    /// </summary>
    public static IPathMatcher AnyPathMatcher(IReadOnlyList<IPathMatcher> pathMatchers) =>
        new AnyPathMatcherImpl(pathMatchers.ToImmutableArray());

    /// <summary>Returns a <see cref="IPathMatcher"/> that negates <paramref name="pathMatcher"/>.</summary>
    public static IPathMatcher NotPathMatcher(IPathMatcher pathMatcher) =>
        new NotPathMatcherImpl(pathMatcher);

    /// <summary>
    /// Resolves <paramref name="symlink"/> recursively until it finds a regular file or directory,
    /// checking that all intermediate paths jumps are under <paramref name="matcher"/>.
    /// </summary>
    public static ResolvedSymlink ResolveSymlink(IPathMatcher matcher, string symlink)
    {
        string path = NormalizeFull(symlink);
        Preconditions.CheckArgument(matcher.Matches(path), "{0} doesn't match {1}", path, matcher);

        var visited = new HashSet<string>();
        while (IsSymbolicLink(path))
        {
            if (!visited.Add(path))
            {
                throw new IOException("Symlink cycle detected:\n  " + string.Join("\n  ", visited));
            }
            if (visited.Count > 50)
            {
                throw new IOException("Symlink chain too long:\n  " + string.Join("\n  ", visited));
            }
            string? target = new FileInfo(path).LinkTarget;
            if (target == null)
            {
                break;
            }
            string newPath;
            if (!Path.IsPathRooted(target))
            {
                string? dir = Path.GetDirectoryName(path);
                newPath = NormalizeFull(Path.Combine(dir ?? "", target));
            }
            else
            {
                newPath = NormalizeFull(target);
            }
            if (!matcher.Matches(newPath))
            {
                if (!Directory.Exists(newPath)
                    || !matcher.Matches(newPath + "/copybara_random_path.txt"))
                {
                    bool broken = !File.Exists(newPath) && !Directory.Exists(newPath);
                    return new ResolvedSymlink(
                        newPath,
                        broken
                            ? ResolvedSymlink.TargetLocation.Broken
                            : ResolvedSymlink.TargetLocation.Outside);
                }
            }
            path = newPath;
        }
        bool notExists = !File.Exists(path) && !Directory.Exists(path);
        return new ResolvedSymlink(
            path,
            notExists ? ResolvedSymlink.TargetLocation.Broken : ResolvedSymlink.TargetLocation.Inside);
    }

    /// <summary>
    /// Tries to add the given permissions. On POSIX filesystems this augments the existing mode; on
    /// others it best-effort sets read/write/execute for the owner.
    /// </summary>
    public static void AddPermissions(string path, UnixFileMode permissionsToAdd)
    {
        if (!OperatingSystem.IsWindows())
        {
            UnixFileMode current = File.GetUnixFileMode(path);
            File.SetUnixFileMode(path, current | permissionsToAdd);
        }
        else
        {
            // On Windows only the read-only attribute is meaningful. If write permission is requested,
            // clear the read-only flag.
            if ((permissionsToAdd & UnixFileMode.UserWrite) != 0)
            {
                var attrs = File.GetAttributes(path);
                if ((attrs & FileAttributes.ReadOnly) != 0)
                {
                    File.SetAttributes(path, attrs & ~FileAttributes.ReadOnly);
                }
            }
        }
    }

    private const int RepoFolderNameLimit = 100;

    public static string ResolveDirInCache(string url, string repoStorage)
    {
        string escapedUrl = PercentEscape(url);

        // Avoid "Filename too long" errors.
        if (escapedUrl.Length > RepoFolderNameLimit + 40)
        {
            string tail = escapedUrl.Substring(RepoFolderNameLimit - 1);
            byte[] hash = SHA1.HashData(Encoding.UTF8.GetBytes(tail));
            escapedUrl =
                escapedUrl.Substring(0, RepoFolderNameLimit - 1)
                + "_"
                + Convert.ToHexStringLower(hash);
        }
        return Path.Combine(repoStorage, escapedUrl);
    }

    // Mirrors Guava PercentEscaper with safeChars "-_" and plusForSpace=true.
    private static string PercentEscape(string s)
    {
        var sb = new StringBuilder();
        foreach (char c in s)
        {
            if (char.IsLetterOrDigit(c) || c == '-' || c == '_')
            {
                sb.Append(c);
            }
            else if (c == ' ')
            {
                sb.Append('+');
            }
            else
            {
                foreach (byte b in Encoding.UTF8.GetBytes(c.ToString()))
                {
                    sb.Append('%');
                    sb.Append("0123456789ABCDEF"[(b >> 4) & 0xF]);
                    sb.Append("0123456789ABCDEF"[b & 0xF]);
                }
            }
        }
        return sb.ToString();
    }

    private static bool IsSymbolicLink(string path)
    {
        try
        {
            var info = new FileInfo(path);
            return (info.Attributes & FileAttributes.ReparsePoint) != 0;
        }
        catch (IOException)
        {
            return false;
        }
    }

    private static IEnumerable<string> EnumerateFilesAndSymlinks(string root)
    {
        if (File.Exists(root) &&
            !Directory.Exists(root))
        {
            yield return root;
            yield break;
        }
        if (!Directory.Exists(root))
        {
            yield break;
        }
        foreach (var f in Directory.EnumerateFiles(root, "*", SearchOption.AllDirectories))
        {
            yield return f;
        }
    }

    private static string NormalizeFull(string path) =>
        PathNormalizer.Normalize(Path.GetFullPath(path));

    private static string GetRelative(string from, string file) =>
        PathNormalizer.Normalize(Path.GetRelativePath(from, file));

    private sealed class AllFilesPathMatcher : IPathMatcher
    {
        public bool Matches(string path) => true;

        public override string ToString() => "**";
    }

    private sealed class AnyPathMatcherImpl : IPathMatcher, IEquatable<AnyPathMatcherImpl>
    {
        private readonly ImmutableArray<IPathMatcher> _pathMatchers;

        public AnyPathMatcherImpl(ImmutableArray<IPathMatcher> pathMatchers) =>
            _pathMatchers = pathMatchers;

        public bool Matches(string path)
        {
            foreach (var pathMatcher in _pathMatchers)
            {
                if (pathMatcher.Matches(path))
                {
                    return true;
                }
            }
            return false;
        }

        public bool Equals(AnyPathMatcherImpl? other) =>
            other is not null && _pathMatchers.SequenceEqual(other._pathMatchers);

        public override bool Equals(object? obj) => Equals(obj as AnyPathMatcherImpl);

        public override int GetHashCode()
        {
            var hc = new HashCode();
            foreach (var m in _pathMatchers)
            {
                hc.Add(m);
            }
            return hc.ToHashCode();
        }

        public override string ToString() => "anyOf[" + string.Join(", ", _pathMatchers) + "]";
    }

    private sealed class NotPathMatcherImpl : IPathMatcher
    {
        private readonly IPathMatcher _pathMatcher;

        public NotPathMatcherImpl(IPathMatcher pathMatcher) => _pathMatcher = pathMatcher;

        public bool Matches(string path) => !_pathMatcher.Matches(path);

        public override string ToString() => "not(" + _pathMatcher + ")";
    }
}
