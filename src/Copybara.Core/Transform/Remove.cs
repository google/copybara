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

using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Util;
using Starlark.Syntax;

namespace Copybara.Transform;

/// <summary>
/// We might promote this to a Skylark transform. But because we already have origin_files, that
/// works better with reversible workflows, this is a bad idea except for explicit reversals of
/// core.copy.
/// </summary>
public class Remove : ITransformation
{
    private readonly Glob _glob;
    private readonly Location _location;

    public Remove(Glob glob, Location location)
    {
        _glob = Preconditions.CheckNotNull(glob);
        _location = location;
    }

    public TransformationStatus Transform(TransformWork work)
    {
        // TODO(malcon): Fix ConfigValidator and move this logic there.
        ValidationException.CheckCondition(
            work.IsInsideExplicitTransform(),
            "core.remove() is only mean to be used inside core.transform for reversing"
                + " transformations like core.copy(). Please use origin_files exclude for"
                + " filtering out files.");

        int numDeletes =
            FileUtil.DeleteFilesRecursively(work.GetCheckoutDir(), _glob.RelativeTo(work.GetCheckoutDir()));
        if (numDeletes == 0)
        {
            return TransformationStatus.Noop(_glob + " didn't delete any file");
        }
        return TransformationStatus.Success();
    }

    public ITransformation Reverse() =>
        throw new NonReversibleValidationException("core.remove is not reversible");

    public string Describe() => "Removing " + _glob;

    public Location Location() => _location;
}
