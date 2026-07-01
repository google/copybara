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
using Starlark.Eval;
using StarlarkRt = Starlark.Eval.Starlark;
using BuildozerCommand = Copybara.Buildozer.BuildozerOptions.BuildozerCommand;

namespace Copybara.Buildozer;

/// <summary>
/// A transformation which creates a new build target and reverses to delete the same target.
/// </summary>
public sealed class BuildozerCreate : IBuildozerTransformation
{
    private readonly BuildozerOptions _options;
    private readonly WorkflowOptions _workflowOptions;
    private readonly Target _target;
    private readonly string _ruleType;
    private readonly RelativeTo _relativeTo;
    private readonly ImmutableArray<string> _commands;

    internal sealed class RelativeTo
    {
        internal readonly string Args;

        private static void ValidateTargetName(string targetName)
        {
            if (targetName.Contains(':'))
            {
                throw StarlarkRt.Errorf(
                    "unexpected : in target name (did you include the package by mistake?) - '{0}'",
                    targetName);
            }
        }

        internal RelativeTo(string before, string after)
        {
            if (before.Length != 0 && after.Length != 0)
            {
                throw new EvalException(
                    "cannot specify both 'before' and 'after' in the target create arguments");
            }

            if (before.Length != 0)
            {
                ValidateTargetName(before);
                Args = "before " + before;
            }
            else if (after.Length != 0)
            {
                ValidateTargetName(after);
                Args = "after " + after;
            }
            else
            {
                Args = "";
            }
        }

        public override string ToString() => Args;
    }

    internal BuildozerCreate(
        BuildozerOptions options,
        WorkflowOptions workflowOptions,
        Target target,
        string ruleType,
        RelativeTo relativeTo,
        IEnumerable<string> commands)
    {
        _options = Preconditions.CheckNotNull(options);
        _workflowOptions = Preconditions.CheckNotNull(workflowOptions);
        _target = Preconditions.CheckNotNull(target);
        _ruleType = Preconditions.CheckNotNull(ruleType);
        _relativeTo = Preconditions.CheckNotNull(relativeTo);
        _commands = commands.ToImmutableArray();
    }

    public TransformationStatus Transform(TransformWork work)
    {
        BeforeRun(work);
        try
        {
            _options.Run(work.GetConsole(), work.GetCheckoutDir(), GetCommands());
            return TransformationStatus.Success();
        }
        catch (TargetNotFoundException e)
        {
            // This should not happen for creation. If it happens, it is due to a file error.
            throw new ValidationException(e.Message);
        }
    }

    public void BeforeRun(TransformWork work)
    {
        string buildFilePath = Path.Combine(work.GetCheckoutDir(), GetTargetBuildFile());
        if (!File.Exists(buildFilePath))
        {
            // Alert the user that the package to contain this target doesn't have a BUILD file, since
            // this may be a configuration error.
            work.GetConsole().Info(
                $"BUILD file to contain {_target} doesn't exist. Creating now.");
            string? parent = Path.GetDirectoryName(buildFilePath);
            if (parent != null)
            {
                Directory.CreateDirectory(parent);
            }
            File.WriteAllBytes(buildFilePath, Array.Empty<byte>());
        }
    }

    private string GetTargetBuildFile()
    {
        string pkg = _target.GetPackage();
        // pkg can be empty (e.g. ":foo"), which should create targets in the workdir root, i.e.
        // ./BUILD
        return pkg + (pkg.Length == 0 ? "." : "") + "/BUILD";
    }

    public IEnumerable<BuildozerCommand> GetCommands()
    {
        var result = new List<BuildozerCommand>
        {
            new(
                ImmutableArray.Create(GetTargetBuildFile()),
                $"new {_ruleType} {_target.GetName()} {_relativeTo.Args}"),
        };
        foreach (string command in _commands)
        {
            result.Add(new BuildozerCommand(ImmutableArray.Create(_target.ToString()), command));
        }
        return result;
    }

    public bool CanJoin(ITransformation transformation) =>
        BuildozerBatch.IsBuildozer(transformation);

    public ITransformation Join(ITransformation next) =>
        BuildozerBatch.Join(_options, _workflowOptions, this, (IBuildozerTransformation)next);

    public string Describe() => "buildozer.create " + _target;

    public ITransformation Reverse() =>
        new BuildozerDelete(_options, _workflowOptions, _target, this);

    public override string ToString() =>
        $"BuildozerCreate{{target={_target}, ruleType={_ruleType}, relativeTo={_relativeTo}," +
        $" commands=[{string.Join(", ", _commands)}]}}";
}
