/*
 * Copyright (C) 2023 Google Inc.
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
using Starlark.Syntax;

namespace Copybara.Transform;

/// <summary>Transformation for doing rename of files.</summary>
public class Rename : ITransformation
{
    private readonly string _before;
    private readonly string _after;
    private readonly Glob _paths;
    private readonly bool _overwrite;
    private readonly bool _suffix;
    private readonly Location _location;

    public Rename(
        string before, string after, Glob paths, bool overwrite, bool suffix, Location location)
    {
        _before = before;
        _after = after;
        _paths = paths;
        _overwrite = overwrite;
        _suffix = suffix;
        _location = location;
    }

    public TransformationStatus Transform(TransformWork work)
    {
        bool noop = true;
        foreach (CheckoutPath p in work.List(_paths).Cast<CheckoutPath>())
        {
            string file = PathOps.Resolve(p.GetCheckoutDir(), p.GetPath());
            if (!File.Exists(file))
            {
                continue;
            }
            string destination;
            if (_suffix)
            {
                if (!file.EndsWith(_before, StringComparison.Ordinal))
                {
                    continue;
                }
                destination = PathOps.Normalize(ReplaceFirst(file, _before, _after));
            }
            else
            {
                if (!PathEndsWith(file, _before))
                {
                    continue;
                }
                destination = PathOps.Normalize(ReplaceFirst(file, _before, _after));
            }
            ValidationException.CheckCondition(
                PathOps.StartsWith(destination, p.GetCheckoutDir()),
                "Destination file for " + destination + " is out of the checkout directory");
            noop = false;
            var destParent = PathOps.GetParent(destination);
            if (destParent != null)
            {
                Directory.CreateDirectory(destParent);
            }
            if (_overwrite && File.Exists(destination))
            {
                File.Delete(destination);
            }
            File.Move(file, destination);
        }
        if (noop)
        {
            return TransformationStatus.Noop($"Couldn't find any file to rename with '{_before}'");
        }
        return TransformationStatus.Success();
    }

    // Mirrors String.replace(before, after): replaces all occurrences of the literal 'before'.
    private static string ReplaceFirst(string value, string before, string after) =>
        value.Replace(before, after);

    // Mirrors java.nio.file.Path.endsWith: true if the path ends with the given path segments.
    private static bool PathEndsWith(string path, string suffix)
    {
        string normPath = path.Replace('\\', '/').TrimEnd('/');
        string normSuffix = suffix.Replace('\\', '/').TrimEnd('/');
        if (normPath == normSuffix)
        {
            return true;
        }
        return normPath.EndsWith("/" + normSuffix, StringComparison.Ordinal);
    }

    public ITransformation Reverse()
    {
        if (_overwrite)
        {
            throw new NonReversibleValidationException(
                "core.rename() with overwrite set is not automatically reversible. Use"
                    + " core.transform to define an explicit reverse");
        }

        return new ExplicitReversal(
            new Rename(_after, _before, _paths, _overwrite, _suffix, _location), this);
    }

    public string Describe() => "Renaming " + _before;

    public Location Location() => _location;
}
