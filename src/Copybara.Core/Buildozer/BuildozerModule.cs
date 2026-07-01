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

using System.Collections;
using System.Collections.Immutable;
using Copybara.Buildozer;
using Copybara.Common;
using Copybara.Config;
using Copybara.Exceptions;
using Starlark.Annot;
using Starlark.Eval;
using StarlarkRt = Starlark.Eval.Starlark;
using Sequence = Starlark.Eval.Sequence;

namespace Copybara.Buildozer;

/// <summary>Skylark module for Buildozer-related functionality.</summary>
[StarlarkBuiltin(
    "buildozer",
    Doc =
        "Module for Buildozer-related functionality such as creating and modifying BUILD targets.")]
public sealed class BuildozerModule : IStarlarkValue
{
    private readonly BuildozerOptions _buildozerOptions;
    private readonly WorkflowOptions _workflowOptions;
    private readonly BuildozerPrintExecutor _buildozerPrintExecutor;

    public BuildozerModule(
        WorkflowOptions workflowOptions,
        BuildozerOptions buildozerOptions,
        GeneralOptions generalOptions)
    {
        _workflowOptions = Preconditions.CheckNotNull(workflowOptions);
        _buildozerOptions = Preconditions.CheckNotNull(buildozerOptions);
        _buildozerPrintExecutor =
            BuildozerPrintExecutor.Create(
                buildozerOptions, Preconditions.CheckNotNull(generalOptions).GetConsole());
    }

    private static ImmutableArray<Target> GetTargetList(object arg)
    {
        if (arg is string s)
        {
            return ImmutableArray.Create(Target.FromConfig(s));
        }

        var builder = ImmutableArray.CreateBuilder<Target>();
        foreach (string target in SkylarkUtil.ConvertStringList(arg, "target"))
        {
            builder.Add(Target.FromConfig(target));
        }
        return builder.ToImmutable();
    }

    private static ImmutableArray<Command> CoerceCommandList(IEnumerable commands)
    {
        var wrappedCommands = ImmutableArray.CreateBuilder<Command>();
        foreach (object? command in commands)
        {
            if (command is string s)
            {
                wrappedCommands.Add(Command.FromConfig(s, null));
            }
            else if (command is Command c)
            {
                wrappedCommands.Add(c);
            }
            else
            {
                throw StarlarkRt.Errorf(
                    "Expected a string or buildozer.cmd, but got: {0}", command);
            }
        }
        return wrappedCommands.ToImmutable();
    }

    [StarlarkMethod(
        "create",
        Doc =
            "A transformation which creates a new build target and populates its "
            + "attributes. This transform can reverse automatically to delete the target.")]
    public BuildozerCreate Create(
        [Param(
            Name = "target",
            Doc =
                "Target to create, including the package, e.g. 'foo:bar'. The package can be "
                + "'.' for the root BUILD file.",
            Named = true)]
        string target,
        [Param(
            Name = "rule_type",
            Doc = "Type of this rule, for instance, java_library.",
            Named = true)]
        string ruleType,
        [Param(
            Name = "commands",
            Doc =
                "Commands to populate attributes of the target after creating it. Elements can"
                + " be strings such as 'add deps :foo' or objects returned by buildozer.cmd.",
            DefaultValue = "[]",
            AllowedTypes = new[] { typeof(Sequence) },
            Named = true)]
        object commands,
        [Param(
            Name = "before",
            Doc =
                "When supplied, causes this target to be created *before* the target named by"
                + " 'before'",
            Positional = false,
            DefaultValue = "''",
            Named = true)]
        string before,
        [Param(
            Name = "after",
            Doc =
                "When supplied, causes this target to be created *after* the target named by"
                + " 'after'",
            Positional = false,
            DefaultValue = "''",
            Named = true)]
        string after)
    {
        var commandStrings = new List<string>();
        foreach (Command command in CoerceCommandList((IEnumerable)commands))
        {
            commandStrings.Add(command.ToString());
        }
        return new BuildozerCreate(
            _buildozerOptions,
            _workflowOptions,
            Target.FromConfig(target),
            ruleType,
            new BuildozerCreate.RelativeTo(before, after),
            commandStrings);
    }

    private static void MustOmitRecreateParam(object expected, object actual, string paramName)
    {
        if (!expected.Equals(actual))
        {
            throw StarlarkRt.Errorf(
                "Parameter '{0}' is only used for reversible buildozer.delete transforms, but this"
                + " buildozer.delete is not reversible. Specify 'rule_type' argument to make it"
                + " reversible.",
                paramName);
        }
    }

    [StarlarkMethod(
        "delete",
        Doc =
            "A transformation which is the opposite of creating a build target. When run normally,"
            + " it deletes a build target. When reversed, it creates and prepares one.")]
    public BuildozerDelete Delete(
        [Param(
            Name = "target",
            Doc = "Target to delete, including the package, e.g. 'foo:bar'",
            Named = true)]
        string targetString,
        [Param(
            Name = "rule_type",
            Doc =
                "Type of this rule, for instance, java_library. Supplying this will cause this"
                + " transformation to be reversible.",
            DefaultValue = "''",
            Named = true)]
        string ruleType,
        [Param(
            Name = "recreate_commands",
            Doc =
                "Commands to populate attributes of the target after creating it. Elements can"
                + " be strings such as 'add deps :foo' or objects returned by buildozer.cmd.",
            Positional = false,
            DefaultValue = "[]",
            AllowedTypes = new[] { typeof(Sequence) },
            Named = true)]
        object recreateCommands,
        [Param(
            Name = "before",
            Doc =
                "When supplied with rule_type and the transformation is reversed, causes this"
                + " target to be created *before* the target named by 'before'",
            Positional = false,
            DefaultValue = "''",
            Named = true)]
        string before,
        [Param(
            Name = "after",
            Doc =
                "When supplied with rule_type and the transformation is reversed, causes this"
                + " target to be created *after* the target named by 'after'",
            Positional = false,
            DefaultValue = "''",
            Named = true)]
        string after)
    {
        var commandStrings = new List<string>();
        foreach (Command command in CoerceCommandList((IEnumerable)recreateCommands))
        {
            commandStrings.Add(command.ToString());
        }
        BuildozerCreate? recreateAs;
        Target target = Target.FromConfig(targetString);
        if (ruleType.Length == 0)
        {
            recreateAs = null;
            MustOmitRecreateParam(
                (object)ImmutableArray<object?>.Empty, ToListForCompare(recreateCommands),
                "recreate_commands");
            MustOmitRecreateParam("", before, "before");
            MustOmitRecreateParam("", after, "after");
        }
        else
        {
            recreateAs =
                new BuildozerCreate(
                    _buildozerOptions,
                    _workflowOptions,
                    target,
                    ruleType,
                    new BuildozerCreate.RelativeTo(before, after),
                    commandStrings);
        }
        return new BuildozerDelete(_buildozerOptions, _workflowOptions, target, recreateAs);
    }

    // Emulates Java's check that the (empty) default list argument was left untouched.
    private static object ToListForCompare(object recreateCommands)
    {
        int count = 0;
        foreach (object? unused in (IEnumerable)recreateCommands)
        {
            count++;
        }
        return count == 0 ? (object)ImmutableArray<object?>.Empty : recreateCommands;
    }

    [StarlarkMethod(
        "modify",
        Doc =
            "A transformation which runs one or more Buildozer commands against a single"
            + " target expression. See http://go/buildozer for details on supported commands and"
            + " target expression formats.")]
    public BuildozerModify Modify(
        [Param(
            Name = "target",
            AllowedTypes = new[] { typeof(string), typeof(Sequence) },
            Doc = "Specifies the target(s) against which to apply the commands. Can be a list.",
            Named = true)]
        object target,
        [Param(
            Name = "commands",
            Doc =
                "Commands to apply to the target(s) specified. Elements can"
                + " be strings such as 'add deps :foo' or objects returned by buildozer.cmd.",
            AllowedTypes = new[] { typeof(Sequence) },
            Named = true)]
        object commands)
    {
        var commandList = CoerceCommandList((IEnumerable)commands);
        if (commandList.Length == 0)
        {
            throw StarlarkRt.Errorf("at least one element required in 'commands' argument");
        }
        return new BuildozerModify(
            _buildozerOptions, _workflowOptions, GetTargetList(target), commandList);
    }

    [StarlarkMethod(
        "cmd",
        Doc =
            "Creates a Buildozer command. You can specify the reversal with the 'reverse' "
            + "argument.")]
    public Command Cmd(
        [Param(
            Name = "forward",
            Doc = "Specifies the Buildozer command, e.g. 'replace deps :foo :bar'",
            Named = true)]
        string forward,
        [Param(
            Name = "reverse",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) },
            Doc =
                "The reverse of the command. This is only required if the given command cannot be"
                + " reversed automatically and the reversal of this command is required by"
                + " some workflow or Copybara check. The following commands are automatically"
                + " reversible:<br><ul><li>add</li><li>remove (when used to remove element"
                + " from list i.e. 'remove srcs foo.cc'</li><li>replace</li></ul>",
            DefaultValue = "None",
            Named = true)]
        object? reverse)
    {
        return Command.FromConfig(forward, SkylarkUtil.ConvertOptionalString(reverse));
    }

    [StarlarkMethod(
        "print",
        Doc =
            "Executes a buildozer print command and returns the output. This is designed to be used"
            + " in the context of a transform")]
    public string Print(
        [Param(
            Name = "ctx",
            Doc = "The TransformWork object",
            AllowedTypes = new[] { typeof(TransformWork) },
            Named = true)]
        TransformWork ctx,
        [Param(Name = "attr", Doc = "The attribute from the target rule to print.", Named = true)]
        string attr,
        [Param(Name = "target", Doc = "The target to print from.", Named = true)]
        string target)
    {
        return _buildozerPrintExecutor.Run(ctx.GetCheckoutDir(), attr, target);
    }

    [StarlarkMethod(
        "batch",
        Doc = "Combines a list of buildozer transforms into a single batch transformation.")]
    public BuildozerBatch Batch(
        [Param(
            Name = "transforms",
            Doc = "The list of buildozer transforms to combine.",
            AllowedTypes = new[] { typeof(Sequence) },
            Named = true)]
        object transforms)
    {
        var builder = ImmutableArray.CreateBuilder<IBuildozerTransformation>();
        foreach (object? transform in (IEnumerable)transforms)
        {
            if (transform is IBuildozerTransformation t)
            {
                builder.Add(t);
            }
            else
            {
                throw StarlarkRt.Errorf(
                    "Expected a buildozer transform, but got: {0}",
                    transform?.GetType().Name ?? "null");
            }
        }
        return BuildozerBatch.JoinAll(_buildozerOptions, _workflowOptions, builder.ToImmutable());
    }
}
