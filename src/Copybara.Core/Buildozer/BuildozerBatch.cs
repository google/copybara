/*
 * Copyright (C) 2018 Google Inc.
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
using BuildozerCommand = Copybara.Buildozer.BuildozerOptions.BuildozerCommand;

namespace Copybara.Buildozer;

/// <summary>A transformation that runs many buildozer transformation in batch.</summary>
public sealed class BuildozerBatch : IBuildozerTransformation
{
    private readonly BuildozerOptions _options;
    private readonly WorkflowOptions _workflowOptions;
    private readonly ImmutableArray<IBuildozerTransformation> _transformations;

    private BuildozerBatch(
        BuildozerOptions options,
        WorkflowOptions workflowOptions,
        IEnumerable<IBuildozerTransformation> transformations)
    {
        _options = Preconditions.CheckNotNull(options);
        _workflowOptions = Preconditions.CheckNotNull(workflowOptions);
        _transformations = transformations.ToImmutableArray();
    }

    public TransformationStatus Transform(TransformWork work)
    {
        var commands = new List<BuildozerCommand>();
        foreach (IBuildozerTransformation transformation in _transformations)
        {
            transformation.BeforeRun(work);
            commands.AddRange(transformation.GetCommands());
        }
        try
        {
            _options.Run(work.GetConsole(), work.GetCheckoutDir(), commands);
            return TransformationStatus.Success();
        }
        catch (TargetNotFoundException e)
        {
            return TransformationStatus.Noop(e.Message);
        }
    }

    public ITransformation Reverse() =>
        throw new InvalidOperationException(
            "Reverse should never be called for join transformations");

    public string Describe() =>
        "buildozer batch of " + _transformations.Length + " buildozer transformations";

    public bool CanJoin(ITransformation transformation) => IsBuildozer(transformation);

    internal static bool IsBuildozer(ITransformation transformation) =>
        transformation is IBuildozerTransformation;

    public ITransformation Join(ITransformation next) =>
        Join(_options, _workflowOptions, this, (IBuildozerTransformation)next);

    internal static BuildozerBatch Join(
        BuildozerOptions buildozerOptions,
        WorkflowOptions workflowOptions,
        IBuildozerTransformation current,
        IBuildozerTransformation next)
    {
        var transformationBuilder = ImmutableArray.CreateBuilder<IBuildozerTransformation>();
        if (current is BuildozerBatch currentBatch)
        {
            transformationBuilder.AddRange(currentBatch._transformations);
        }
        else
        {
            transformationBuilder.Add(current);
        }
        if (next is BuildozerBatch nextBatch)
        {
            transformationBuilder.AddRange(nextBatch._transformations);
        }
        else
        {
            transformationBuilder.Add(next);
        }
        return new BuildozerBatch(
            buildozerOptions, workflowOptions, transformationBuilder.ToImmutable());
    }

    internal static BuildozerBatch JoinAll(
        BuildozerOptions buildozerOptions,
        WorkflowOptions workflowOptions,
        IEnumerable<IBuildozerTransformation> transformations)
    {
        var transformationBuilder = ImmutableArray.CreateBuilder<IBuildozerTransformation>();
        foreach (IBuildozerTransformation transformation in transformations)
        {
            if (transformation is BuildozerBatch batch)
            {
                transformationBuilder.AddRange(batch._transformations);
            }
            else
            {
                transformationBuilder.Add(transformation);
            }
        }
        return new BuildozerBatch(
            buildozerOptions, workflowOptions, transformationBuilder.ToImmutable());
    }

    public void BeforeRun(TransformWork work)
    {
        foreach (IBuildozerTransformation transformation in _transformations)
        {
            transformation.BeforeRun(work);
        }
    }

    public IEnumerable<BuildozerCommand> GetCommands()
    {
        var result = new List<BuildozerCommand>();
        foreach (IBuildozerTransformation transformation in _transformations)
        {
            result.AddRange(transformation.GetCommands());
        }
        return result;
    }
}
