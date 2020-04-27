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

package com.google.copybara.buildozer;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.copybara.WorkflowOptions;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.doc.annotations.Example;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.ParamType;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.StarlarkBuiltin;
import com.google.devtools.build.lib.skylarkinterface.StarlarkDocumentationCategory;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.Location;
import com.google.devtools.build.lib.syntax.Sequence;
import com.google.devtools.build.lib.syntax.Starlark;
import com.google.devtools.build.lib.syntax.StarlarkThread;
import com.google.devtools.build.lib.syntax.StarlarkValue;
import java.util.ArrayList;
import java.util.List;

/** Skylark module for Buildozer-related functionality. */
@StarlarkBuiltin(
    name = "buildozer",
    doc =
        "Module for Buildozer-related functionality such as creating and modifying BUILD targets.",
    category = StarlarkDocumentationCategory.BUILTIN)
public final class BuildozerModule implements StarlarkValue {

  private final BuildozerOptions buildozerOptions;
  private final WorkflowOptions workflowOptions;

  public BuildozerModule(WorkflowOptions workflowOptions, BuildozerOptions buildozerOptions) {
    this.workflowOptions = Preconditions.checkNotNull(workflowOptions);
    this.buildozerOptions = Preconditions.checkNotNull(buildozerOptions);
  }

  private static ImmutableList<Target> getTargetList(Location location, Object arg)
      throws EvalException {
    if (arg instanceof String) {
      return ImmutableList.of(Target.fromConfig(location, (String) arg));
    } else {
      ImmutableList.Builder<Target> builder = ImmutableList.builder();
      for (String target : SkylarkUtil.convertStringList(arg, "target")) {
        builder.add(Target.fromConfig(location, target));
      }
      return builder.build();
    }
  }

  private static ImmutableList<Command> coerceCommandList(Location location, Iterable<?> commands)
      throws EvalException {
    ImmutableList.Builder<Command> wrappedCommands = new ImmutableList.Builder<>();
    for (Object command : commands) {
      if (command instanceof String) {
        wrappedCommands.add(Command.fromConfig(location, (String) command, /*reverse*/ null));
      } else if (command instanceof Command) {
        wrappedCommands.add((Command) command);
      } else {
        throw Starlark.errorf("Expected a string or buildozer.cmd, but got: %s", command);
      }
    }
    return wrappedCommands.build();
  }

  @SkylarkCallable(
      name = "create",
      doc =
          "A transformation which creates a new build target and populates its "
              + "attributes. This transform can reverse automatically to delete the target.",
      parameters = {
        @Param(
            name = "target",
            type = String.class,
            doc =
                "Target to create, including the package, e.g. 'foo:bar'. The package can be "
                    + "'.' for the root BUILD file.",
            named = true),
        @Param(
            name = "rule_type",
            type = String.class,
            doc = "Type of this rule, for instance, java_library.",
            named = true),
        @Param(
            name = "commands",
            type = Sequence.class,
            doc =
                "Commands to populate attributes of the target after creating it. Elements can"
                    + " be strings such as 'add deps :foo' or objects returned by buildozer.cmd.",
            defaultValue = "[]",
            named = true),
        @Param(
            name = "before",
            type = String.class,
            doc =
                "When supplied, causes this target to be created *before* the target named by"
                    + " 'before'",
            positional = false,
            defaultValue = "''",
            named = true),
        @Param(
            name = "after",
            type = String.class,
            doc =
                "When supplied, causes this target to be created *after* the target named by"
                    + " 'after'",
            positional = false,
            defaultValue = "''",
            named = true),
      },
      useStarlarkThread = true)
  public BuildozerCreate create(
      String target,
      String ruleType,
      Sequence<?> commands,
      String before,
      String after,
      StarlarkThread thread)
      throws EvalException {
    Location location = thread.getCallerLocation();
    List<String> commandStrings = new ArrayList<>();
    for (Object command : coerceCommandList(location, commands)) {
      commandStrings.add(command.toString());
    }
    return new BuildozerCreate(
        location,
        buildozerOptions,
        workflowOptions,
        Target.fromConfig(location, target),
        ruleType,
        new BuildozerCreate.RelativeTo(location, before, after),
        commandStrings);
  }

  private void mustOmitRecreateParam(Object expected, Object actual, String paramName)
      throws EvalException {
    if (!expected.equals(actual)) {
      throw Starlark.errorf(
          "Parameter '%s' is only used for reversible buildozer.delete transforms, but this"
              + " buildozer.delete is not reversible. Specify 'rule_type' argument to make it"
              + " reversible.",
          paramName);
    }
  }

  @SkylarkCallable(
      name = "delete",
      doc =
          "A transformation which is the opposite of creating a build target. When run normally,"
              + " it deletes a build target. When reversed, it creates and prepares one.",
      parameters = {
        @Param(
            name = "target",
            type = String.class,
            doc = "Target to create, including the package, e.g. 'foo:bar'",
            named = true),
        @Param(
            name = "rule_type",
            type = String.class,
            doc =
                "Type of this rule, for instance, java_library. Supplying this will cause this"
                    + " transformation to be reversible.",
            defaultValue = "''",
            named = true),
        @Param(
            name = "recreate_commands",
            type = Sequence.class,
            doc =
                "Commands to populate attributes of the target after creating it. Elements can"
                    + " be strings such as 'add deps :foo' or objects returned by buildozer.cmd.",
            positional = false,
            defaultValue = "[]",
            named = true),
        @Param(
            name = "before",
            type = String.class,
            doc =
                "When supplied with rule_type and the transformation is reversed, causes this"
                    + " target to be created *before* the target named by 'before'",
            positional = false,
            defaultValue = "''",
            named = true),
        @Param(
            name = "after",
            type = String.class,
            doc =
                "When supplied with rule_type and the transformation is reversed, causes this"
                    + " target to be created *after* the target named by 'after'",
            positional = false,
            defaultValue = "''",
            named = true),
      },
      useStarlarkThread = true)
  public BuildozerDelete delete(
      String targetString,
      String ruleType,
      Sequence<?> recreateCommands,
      String before,
      String after,
      StarlarkThread thread)
      throws EvalException {
    Location location = thread.getCallerLocation();
    List<String> commandStrings = new ArrayList<>();
    for (Object command : coerceCommandList(location, recreateCommands)) {
      commandStrings.add(command.toString());
    }
    BuildozerCreate recreateAs;
    Target target = Target.fromConfig(location, targetString);
    if (ruleType.isEmpty()) {
      recreateAs = null;
      mustOmitRecreateParam(ImmutableList.of(), recreateCommands, "recreate_commands");
      mustOmitRecreateParam("", before, "before");
      mustOmitRecreateParam("", after, "after");
    } else {
      recreateAs = new BuildozerCreate(
          location, buildozerOptions,
          workflowOptions,
          target,
          ruleType,
          new BuildozerCreate.RelativeTo(location, before, after),
          commandStrings);
    }
    return new BuildozerDelete(location, buildozerOptions, workflowOptions, target,
        recreateAs);
  }

  @SkylarkCallable(
      name = "modify",
      doc =
          "A transformation which runs one or more Buildozer commands against a single"
              + " target expression. See http://go/buildozer for details on supported commands and"
              + " target expression formats.",
      parameters = {
        @Param(
            name = "target",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = Sequence.class, generic1 = String.class)
            },
            doc = "Specifies the target(s) against which to apply the commands. Can be a list.",
            named = true),
        @Param(
            name = "commands",
            type = Sequence.class,
            doc =
                "Commands to apply to the target(s) specified. Elements can"
                    + " be strings such as 'add deps :foo' or objects returned by buildozer.cmd.",
            named = true),
      },
      useStarlarkThread = true)
  @Example(
      title = "Add a setting to one target",
      before = "Add \"config = ':foo'\" to foo/bar:baz:",
      code =
          "buildozer.modify(\n"
              + "    target = 'foo/bar:baz',\n"
              + "    commands = [\n"
              + "        buildozer.cmd('set config \":foo\"'),\n"
              + "    ],\n"
              + ")")
  @Example(
      title = "Add a setting to several targets",
      before = "Add \"config = ':foo'\" to foo/bar:baz and foo/bar:fooz:",
      code =
          "buildozer.modify(\n"
              + "    target = ['foo/bar:baz', 'foo/bar:fooz'],\n"
              + "    commands = [\n"
              + "        buildozer.cmd('set config \":foo\"'),\n"
              + "    ],\n"
              + ")")
  public BuildozerModify modify(Object target, Sequence<?> commands, StarlarkThread thread)
      throws EvalException {
    if (commands.isEmpty()) {
      throw Starlark.errorf("at least one element required in 'commands' argument");
    }
    Location location = thread.getCallerLocation();
    return new BuildozerModify(
        buildozerOptions, workflowOptions, getTargetList(location, target),
        coerceCommandList(location, commands));
  }

  @SkylarkCallable(
      name = "cmd",
      doc =
          "Creates a Buildozer command. You can specify the reversal with the 'reverse' "
              + "argument.",
      parameters = {
        @Param(
            name = "forward",
            type = String.class,
            doc = "Specifies the Buildozer command, e.g. 'replace deps :foo :bar'",
            named = true),
        @Param(
            name = "reverse",
            type = String.class,
            doc =
                "The reverse of the command. This is only required if the given command cannot be"
                    + " reversed automatically and the reversal of this command is required by"
                    + " some workflow or Copybara check. The following commands are automatically"
                    + " reversible:<br><ul><li>add</li><li>remove (when used to remove element"
                    + " from list i.e. 'remove srcs foo.cc'</li><li>replace</li></ul>",
            noneable = true,
            defaultValue = "None",
            named = true),
      },
      useStarlarkThread = true)
  public Command cmd(String forward, Object reverse, StarlarkThread thread) throws EvalException {
    return Command.fromConfig(
        thread.getCallerLocation(), forward, SkylarkUtil.convertOptionalString(reverse));
  }
}
