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
using Copybara.Util.Console;
using BuildozerCommand = Copybara.Buildozer.BuildozerOptions.BuildozerCommand;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Buildozer;

/// <summary>A transformation which runs one or more commands against a single target.</summary>
public sealed class BuildozerModify : IBuildozerTransformation
{
    private readonly BuildozerOptions _options;
    private readonly WorkflowOptions _workflowOptions;
    private readonly IReadOnlyList<Target> _targets;
    private readonly ImmutableArray<Command> _commands;

    internal BuildozerModify(
        BuildozerOptions options,
        WorkflowOptions workflowOptions,
        IReadOnlyList<Target> targets,
        IEnumerable<Command> commands)
    {
        _options = Preconditions.CheckNotNull(options);
        _workflowOptions = Preconditions.CheckNotNull(workflowOptions);
        _targets = Preconditions.CheckNotNull(targets);
        _commands = commands.ToImmutableArray();
    }

    public TransformationStatus Transform(TransformWork work)
    {
        Console console = work.GetConsole();
        try
        {
            _options.Run(console, work.GetCheckoutDir(), GetCommands());
            return TransformationStatus.Success();
        }
        catch (TargetNotFoundException e)
        {
            return TransformationStatus.Noop(e.Message);
        }
    }

    public ITransformation Reverse()
    {
        var reverseCommands = new List<Command>();
        for (int i = _commands.Length - 1; i >= 0; i--)
        {
            reverseCommands.Add(_commands[i].Reverse());
        }
        return new BuildozerModify(_options, _workflowOptions, _targets, reverseCommands);
    }

    public IEnumerable<BuildozerCommand> GetCommands()
    {
        var result = new List<BuildozerCommand>();
        foreach (Command command in _commands)
        {
            result.Add(new BuildozerCommand(Target.AsStringList(_targets), command.ToString()));
        }
        return result;
    }

    public bool CanJoin(ITransformation transformation) =>
        BuildozerBatch.IsBuildozer(transformation);

    public ITransformation Join(ITransformation next) =>
        BuildozerBatch.Join(_options, _workflowOptions, this, (IBuildozerTransformation)next);

    public string Describe() => "buildozer.modify [" + string.Join(", ", _targets) + "]";

    public override string ToString() =>
        $"BuildozerModify{{target=[{string.Join(", ", _targets)}]," +
        $" commands=[{string.Join(", ", _commands)}]}}";
}
