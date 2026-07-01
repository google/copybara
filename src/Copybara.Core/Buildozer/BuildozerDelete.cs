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
using Copybara.Buildozer;
using Copybara.Common;
using Copybara.Exceptions;
using BuildozerCommand = Copybara.Buildozer.BuildozerOptions.BuildozerCommand;

namespace Copybara.Buildozer;

/// <summary>
/// A transformation which deletes build target and reverses to create the same target.
/// </summary>
public sealed class BuildozerDelete : IBuildozerTransformation
{
    private readonly BuildozerOptions _options;
    private readonly WorkflowOptions _workflowOptions;
    private readonly Target _target;
    private readonly BuildozerCreate? _recreateAs;

    internal BuildozerDelete(
        BuildozerOptions options,
        WorkflowOptions workflowOptions,
        Target target,
        BuildozerCreate? recreateAs)
    {
        _options = Preconditions.CheckNotNull(options);
        _workflowOptions = Preconditions.CheckNotNull(workflowOptions);
        _target = Preconditions.CheckNotNull(target);
        _recreateAs = recreateAs;
    }

    public TransformationStatus Transform(TransformWork work)
    {
        try
        {
            _options.Run(work.GetConsole(), work.GetCheckoutDir(), GetCommands());
            return TransformationStatus.Success();
        }
        catch (TargetNotFoundException e)
        {
            return TransformationStatus.Noop(e.Message);
        }
    }

    public string Describe() => "buildozer.delete " + _target;

    public bool CanJoin(ITransformation transformation) =>
        BuildozerBatch.IsBuildozer(transformation);

    public ITransformation Join(ITransformation next) =>
        BuildozerBatch.Join(_options, _workflowOptions, this, (IBuildozerTransformation)next);

    public IEnumerable<BuildozerCommand> GetCommands() =>
        ImmutableArray.Create(new BuildozerCommand(_target.ToString(), "delete"));

    public ITransformation Reverse()
    {
        if (_recreateAs == null)
        {
            throw new NonReversibleValidationException(
                "This buildozer.delete is not reversible. Please specify at least rule_type to make"
                + " it reversible.");
        }
        return _recreateAs;
    }

    public override string ToString() =>
        $"BuildozerDelete{{target={_target}, recreateAs={_recreateAs}}}";
}
