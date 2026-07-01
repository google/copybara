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
using Copybara.Config;
using Copybara.Exceptions;
using Copybara.Util;
using Starlark.Syntax;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Transform.Patch;

/// <summary>
/// Transformation for applying patch file during a workflow. Instantiated by
/// <see cref="PatchModule"/>.
/// </summary>
public class PatchTransformation : ITransformation
{
    private readonly ImmutableArray<ConfigFile> _patches;
    private readonly ImmutableArray<string> _excludedPaths;
    private readonly bool _reverse;
    private readonly PatchingOptions _options;
    private readonly int _strip;
    private readonly string _directory;
    private readonly Location _location;

    internal PatchTransformation(
        ImmutableArray<ConfigFile> patches,
        ImmutableArray<string> excludedPaths,
        PatchingOptions options,
        bool reverse,
        int strip,
        string directory,
        Location location)
    {
        _patches = patches;
        _excludedPaths = excludedPaths;
        _reverse = reverse;
        _options = options;
        _strip = strip;
        _directory = directory;
        _location = Preconditions.CheckNotNull(location);
    }

    /// <exception cref="ValidationException"/>
    public TransformationStatus Transform(TransformWork work)
    {
        try
        {
            Patch(work.GetConsole(), work.GetCheckoutDir(), gitDir: null);
        }
        catch (InsideGitDirException e)
        {
            throw new ValidationException(string.Format(
                "Cannot use patch.apply because Copybara temporary directory ({0}) is inside a git"
                    + " directory ({1}). Please remove the git repository or use {2} flag.",
                e.Path, e.GitDirPath, GeneralOptions.OutputRootFlag));
        }
        return TransformationStatus.Success();
    }

    /// <exception cref="ValidationException"/>
    /// <exception cref="InsideGitDirException"/>
    public void Patch(Console console, string checkoutDir, string? gitDir)
    {
        for (int i = 0; i < _patches.Length; i++)
        {
            ConfigFile patch = _patches[i];
            try
            {
                console.InfoFmt("Applying patch {0}/{1}: '{2}'.", i + 1, _patches.Length, patch.Path());
                _options.Patch(
                    PathOps.Resolve(checkoutDir, _directory),
                    patch.ReadContentBytes(),
                    _excludedPaths,
                    _strip,
                    _reverse,
                    gitDir);
            }
            catch (IOException ioException)
            {
                string msg = string.Format(
                    "Error applying patch {0}: {1}", patch.GetIdentifier(), ioException.Message);
                console.Error(msg);
                throw new ValidationException(msg, ioException);
            }
        }
    }

    public ITransformation Reverse()
    {
        return new PatchTransformation(
            ImmutableArray.CreateRange(_patches.Reverse()),
            _excludedPaths,
            _options,
            !_reverse,
            _strip,
            _directory,
            _location);
    }

    public string Describe() =>
        "Patch.apply: " + string.Join(", ", _patches.Select(p => p.Path()));

    public Location Location() => _location;
}
