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
using System.Text.RegularExpressions;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.TemplateToken;
using Copybara.Util;
using Starlark.Syntax;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara.Transform;

/// <summary>Transformation that moves (renames) or copies a single file or directory.</summary>
public class CopyOrMove : ITransformation
{
    private readonly RegexTemplateTokens _before;
    private readonly RegexTemplateTokens _after;
    private readonly Glob _paths;
    private readonly ImmutableDictionary<string, Regex> _regexGroups;
    private readonly bool _overwrite;
    private readonly Location _location;
    private readonly bool _isCopy;

    private CopyOrMove(
        RegexTemplateTokens before,
        RegexTemplateTokens after,
        IReadOnlyDictionary<string, Regex> regexGroups,
        Glob paths,
        bool overwrite,
        Location location,
        bool isCopy)
    {
        _before = Preconditions.CheckNotNull(before);
        _after = Preconditions.CheckNotNull(after);
        _regexGroups = regexGroups.ToImmutableDictionary();
        _paths = paths;
        _overwrite = overwrite;
        _location = location;
        _isCopy = isCopy;
    }

    public static CopyOrMove Create(
        string before,
        string after,
        IReadOnlyDictionary<string, string> regexGroups,
        Glob paths,
        bool overwrite,
        Location location,
        bool isCopy)
    {
        var parsedRegexGroups = Replace.ParsePatterns(regexGroups);
        var beforeTokens = new RegexTemplateTokens(
            ValidatePath(before), parsedRegexGroups, repeatedGroups: true, matchExactly: true, location);
        beforeTokens.ValidateUnused();
        var afterTokens = new RegexTemplateTokens(
            ValidatePath(after), parsedRegexGroups, repeatedGroups: true, matchExactly: true, location);
        return new CopyOrMove(
            beforeTokens, afterTokens, parsedRegexGroups, paths, overwrite, location, isCopy);
    }

    public static CopyOrMove CreateMove(
        string before,
        string after,
        IReadOnlyDictionary<string, string> regexGroups,
        Glob paths,
        bool overwrite,
        Location location) =>
        Create(before, after, regexGroups, paths, overwrite, location, isCopy: false);

    public static CopyOrMove CreateCopy(
        string before,
        string after,
        IReadOnlyDictionary<string, string> regexGroups,
        Glob paths,
        bool overwrite,
        Location location) =>
        Create(before, after, regexGroups, paths, overwrite, location, isCopy: true);

    public override string ToString() =>
        $"CopyOrMove{{before={_before}, after={_after},"
        + $" regexGroups=[{string.Join(", ", _regexGroups.Keys)}], paths={_paths},"
        + $" overwrite={_overwrite}}}";

    public TransformationStatus Transform(TransformWork work)
    {
        work.GetConsole().Progress("Moving " + _before);
        return _before.IsLiteral() ? TransformNoRegex(work) : TransformWithRegex(work);
    }

    private TransformationStatus TransformNoRegex(TransformWork work)
    {
        string before = PathOps.Normalize(PathOps.Resolve(work.GetCheckoutDir(), _before.ToString()));
        if (!File.Exists(before) && !Directory.Exists(before))
        {
            return TransformationStatus.Noop(
                $"Error moving '{_before}'. It doesn't exist in the workdir");
        }
        string after = PathOps.Normalize(PathOps.Resolve(work.GetCheckoutDir(), _after.ToString()));
        if (Directory.Exists(after) && PathOps.StartsWith(after, before))
        {
            // When moving from a parent dir to a sub-directory, make sure after doesn't already
            // have files in it - this is most likely a mistake.
            new VerifyDirIsEmptyVisitor(
                    after,
                    Directory.Exists(before) && !_paths.Equals(Glob.AllFiles)
                        ? _paths.RelativeTo(after)
                        : null)
                .Walk();
        }
        CreateParentDirs(after);

        bool beforeIsDir = Directory.Exists(before);
        ValidationException.CheckCondition(
            _paths.Equals(Glob.AllFiles) || beforeIsDir,
            "Cannot use user defined 'paths' filter when the 'before' is not a directory: " + _paths);
        ValidationException.CheckCondition(
            !_after.IsEmpty() || beforeIsDir,
            "Can only move a path to the root when the path is a folder. But '{0}' is a file. Use"
                + " instead core.move('{1}', '{2}')",
            _before, _before, PathOps.GetFileName(before));

        // Simple move of all the contents of a directory.
        if (beforeIsDir && !_isCopy && _paths.Equals(Glob.AllFiles))
        {
            MoveAllFilesInDir(before, after, work.GetCheckoutDir());
            return TransformationStatus.Success();
        }

        new CopyMoveVisitor(
                before, after, beforeIsDir ? _paths.RelativeTo(before) : null, _overwrite, _isCopy)
            .Walk();

        // Delete 'before' folder if we moved all the files.
        if (beforeIsDir && !_isCopy)
        {
            RecursiveDeleteIfEmpty(before);
        }
        return TransformationStatus.Success();
    }

    private TransformationStatus TransformWithRegex(TransformWork work)
    {
        // Optimize by only visiting files within rootPath.
        string rootPath = PathOps.Normalize(PathOps.Resolve(work.GetCheckoutDir(), GetRoot(_before)));
        if (!Directory.Exists(rootPath))
        {
            return TransformationStatus.Noop(
                "Transformation '" + this + "' was a no-op because it didn't match any file");
        }
        bool atLeastOneFileMatched = CopyMoveRegexVisitor.Run(
            rootPath, _before, _after, _paths.RelativeTo(rootPath), work.GetCheckoutDir(),
            _overwrite, _isCopy);
        if (!atLeastOneFileMatched)
        {
            return TransformationStatus.Noop(
                "Transformation '" + this + "' was a no-op because it didn't match any file");
        }
        return TransformationStatus.Success();
    }

    private static string GetRoot(RegexTemplateTokens templateTokens)
    {
        var tokens = templateTokens.GetTokens();
        if (tokens.Count == 0)
        {
            return "";
        }
        if (tokens.Count == 1)
        {
            Token token = tokens[0];
            return token.GetTokenType() == TokenType.Literal ? token.GetValue() : "";
        }
        Token first = tokens[0];
        string prefix = first.GetTokenType() == TokenType.Literal ? first.GetValue() : "";
        if (prefix.Contains('/'))
        {
            return prefix.Substring(0, prefix.LastIndexOf('/'));
        }
        return "";
    }

    /// <summary>Traverse a directory files/folders recursively and delete any empty folder.</summary>
    private static void RecursiveDeleteIfEmpty(string dir)
    {
        if (!Directory.Exists(dir))
        {
            return;
        }
        foreach (var sub in Directory.EnumerateDirectories(dir))
        {
            RecursiveDeleteIfEmpty(sub);
        }
        if (!Directory.EnumerateFileSystemEntries(dir).Any())
        {
            try
            {
                Directory.Delete(dir);
            }
            catch (IOException)
            {
                // Folder not empty. Ignore.
            }
        }
    }

    /// <summary>
    /// Move all the files and directories inside <paramref name="before"/> to
    /// <paramref name="after"/>.
    /// </summary>
    private void MoveAllFilesInDir(string before, string after, string checkoutDir)
    {
        var beforeFiles = ListDirFiles(before);
        string tmp = Directory.CreateTempSubdirectory("core.move").FullName;
        // Ensure tmp is inside the checkout so relative moves behave; mirror createTempDirectory in
        // checkoutDir by moving into a subdir of checkoutDir.
        string tmpInCheckout = PathOps.Resolve(checkoutDir, "core.move" + Guid.NewGuid().ToString("N"));
        Directory.CreateDirectory(tmpInCheckout);
        Directory.Delete(tmp);
        tmp = tmpInCheckout;
        foreach (string file in beforeFiles)
        {
            string name = PathOps.GetFileName(file);
            string target = PathOps.Resolve(tmp, name);
            if (Directory.Exists(file))
            {
                Directory.Move(file, target);
            }
            else
            {
                File.Move(file, target);
            }
        }

        if (!PathOps.Normalize(checkoutDir).Equals(PathOps.Normalize(before)))
        {
            RecursiveDeleteIfEmpty(before);
        }

        // If directory exists after the move to tmp, it can contain files.
        if (!Directory.Exists(after))
        {
            var afterParent = PathOps.GetParent(after);
            if (afterParent != null)
            {
                Directory.CreateDirectory(afterParent);
            }
            Directory.Move(tmp, after);
            return;
        }

        // Use our less-efficient move per file.
        new CopyMoveVisitor(tmp, after, pathMatcher: null, _overwrite, isCopy: false).Walk();

        RecursiveDeleteIfEmpty(tmp);
    }

    private static List<string> ListDirFiles(string before) =>
        Directory.EnumerateFileSystemEntries(before).ToList();

    public ITransformation Reverse()
    {
        if (!_before.IsLiteral())
        {
            throw new NonReversibleValidationException(
                "core." + (_isCopy ? "copy" : "move")
                    + "() with regex templating is not automatically reversible. Use core.transform"
                    + " to define an explicit reverse");
        }
        if (_overwrite)
        {
            throw new NonReversibleValidationException(
                "core." + (_isCopy ? "copy" : "move")
                    + "() with overwrite set is not automatically reversible. Use core.transform to"
                    + " define an explicit reverse");
        }
        if (_isCopy)
        {
            string afterPath = PathOps.Normalize(_after.ToString());
            if (!_paths.Equals(Glob.AllFiles))
            {
                throw new NonReversibleValidationException(
                    "core.copy not automatically reversible when using 'paths'");
            }
            if (_after.IsEmpty() ||
                PathOps.StartsWith(PathOps.Normalize(_before.ToString()), afterPath))
            {
                throw new NonReversibleValidationException(
                    "core.copy not automatically reversible when copying to a parent directory");
            }
            return new ExplicitReversal(
                new Remove(
                    // After might be a directory or a file. Delete both.
                    Glob.CreateGlob(ImmutableArray.Create(_after.ToString(), afterPath + "/**")),
                    _location),
                this);
        }
        return new CopyOrMove(
            _after, _before, _regexGroups, _paths, overwrite: false, _location, isCopy: false);
    }

    private static void CreateParentDirs(string after)
    {
        try
        {
            var parent = PathOps.GetParent(after);
            if (parent != null)
            {
                Directory.CreateDirectory(parent);
            }
        }
        catch (IOException e)
        {
            throw new ValidationException(
                $"Cannot create '{PathOps.GetParent(after)}' because a path component already exists"
                    + $" and is not a directory: {e.Message}");
        }
    }

    public string Describe() => (_isCopy ? "Copying " : "Moving ") + _before;

    private static string ValidatePath(string strPath)
    {
        try
        {
            return FileUtil.CheckNormalizedRelative(strPath);
        }
        catch (ArgumentException e)
        {
            throw StarlarkRt.Errorf("'{0}' is not a valid path: {1}", strPath, e.Message);
        }
    }

    public Location Location() => _location;
}
