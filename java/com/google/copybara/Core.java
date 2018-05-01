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

package com.google.copybara;

import static com.google.copybara.config.GlobalMigrations.getGlobalMigrations;
import static com.google.copybara.config.SkylarkUtil.check;
import static com.google.copybara.config.SkylarkUtil.convertFromNoneable;
import static com.google.copybara.config.SkylarkUtil.stringToEnum;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.LabelsAwareModule;
import com.google.copybara.config.OptionsAwareModule;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.doc.annotations.Example;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.copybara.feedback.Action;
import com.google.copybara.feedback.Feedback;
import com.google.copybara.feedback.SkylarkAction;
import com.google.copybara.templatetoken.Parser;
import com.google.copybara.templatetoken.Token;
import com.google.copybara.templatetoken.Token.TokenType;
import com.google.copybara.transform.CopyOrMove;
import com.google.copybara.transform.ExplicitReversal;
import com.google.copybara.transform.Remove;
import com.google.copybara.transform.Replace;
import com.google.copybara.transform.Sequence;
import com.google.copybara.transform.SkylarkTransformation;
import com.google.copybara.transform.TodoReplace;
import com.google.copybara.transform.TodoReplace.Mode;
import com.google.copybara.transform.VerifyMatch;
import com.google.copybara.util.Glob;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.skylarkinterface.SkylarkSignature;
import com.google.devtools.build.lib.syntax.BaseFunction;
import com.google.devtools.build.lib.syntax.BuiltinFunction;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.Runtime;
import com.google.devtools.build.lib.syntax.Runtime.NoneType;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.SkylarkList.MutableList;
import com.google.devtools.build.lib.syntax.SkylarkList.Tuple;
import com.google.devtools.build.lib.syntax.Type;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Main configuration class for creating migrations.
 *
 * <p>This class is exposed in Skylark configuration as an instance variable
 * called "core". So users can use it as:
 * <pre>
 * core.workspace(
 *   name = "foo",
 *   ...
 * )
 * </pre>
 */
@SkylarkModule(
    name = Core.CORE_VAR,
    doc = "Core functionality for creating migrations, and basic transformations.",
    category = SkylarkModuleCategory.BUILTIN)
@UsesFlags(GeneralOptions.class)
public class Core implements OptionsAwareModule, LabelsAwareModule {

  public static final String CORE_VAR = "core";

  private GeneralOptions generalOptions;
  private WorkflowOptions workflowOptions;
  private ConfigFile<?> mainConfigFile;
  private Supplier<ImmutableMap<String, ? extends ConfigFile<?>>> allConfigFiles;
  private Supplier<Environment> dynamicEnvironment;

  @Override
  public void setOptions(Options options) {
    generalOptions = options.get(GeneralOptions.class);
    workflowOptions = options.get(WorkflowOptions.class);
  }

  @SuppressWarnings("unused")
  @SkylarkSignature(
      name = "glob",
      returnType = Glob.class,
      doc = "Glob returns a list of every file in the workdir that matches at least one"
          + " pattern in include and does not match any of the patterns in exclude.",
      parameters = {
          @Param(name = "include", type = SkylarkList.class,
              generic1 = String.class, doc = "The list of glob patterns to include"),
          @Param(name = "exclude", type = SkylarkList.class,
              generic1 = String.class, doc = "The list of glob patterns to exclude",
              defaultValue = "[]", named = true, positional = false),
      }, useLocation = true)
  @Example(title = "Simple usage",
      before = "Include all the files under a folder except for `internal` folder files:",
      code = "glob([\"foo/**\"], exclude = [\"foo/internal/**\"])")
  @Example(title = "Multiple folders",
      before = "Globs can have multiple inclusive rules:",
      code = "glob([\"foo/**\", \"bar/**\", \"baz/**.java\"])",
      after = "This will include all files inside `foo` and `bar` folders and Java files"
          + " inside `baz` folder.")
  @Example(title = "Multiple excludes",
      before = "Globs can have multiple exclusive rules:",
      code = "glob([\"foo/**\"], exclude = [\"foo/internal/**\", \"foo/confidential/**\" ])",
      after = "Include all the files of `foo` except the ones in `internal` and `confidential`"
          + " folders")
  @Example(title = "All BUILD files recursively",
      before = "Copybara uses Java globbing. The globbing is very similar to Bash one. This"
          + " means that recursive globbing for a filename is a bit more tricky:",
      code = "glob([\"BUILD\", \"**/BUILD\"])",
      after = "This is the correct way of matching all `BUILD` files recursively, including the"
          + " one in the root. `**/BUILD` would only match `BUILD` files in subdirectories.")
  @Example(title = "Matching multiple strings with one expression",
      before = "While two globs can be used for matching two directories, there is a more"
          + " compact approach:",
      code = "glob([\"{java,javatests}/**\"])",
      after = "This matches any file in `java` and `javatests` folders.")
  public static final BuiltinFunction GLOB = new BuiltinFunction("glob") {
    public Glob invoke(SkylarkList include, SkylarkList exclude, Location location)
        throws EvalException {
      List<String> includeStrings = Type.STRING_LIST.convert(include, "include");
      List<String> excludeStrings = Type.STRING_LIST.convert(exclude, "exclude");
      try {
        return Glob.createGlob(includeStrings, excludeStrings);
      } catch (IllegalArgumentException e) {
        throw new EvalException(location, String.format(
            "Cannot create a glob from: include='%s' and exclude='%s': %s",
            includeStrings, excludeStrings, e.getMessage()), e);
      }
    }
  };

  @SuppressWarnings("unused")
  @SkylarkSignature(name = "reverse", returnType = SkylarkList.class,
      doc = "Given a list of transformations, returns the list of transformations equivalent to"
          + " undoing all the transformations",
      parameters = {
          @Param(name = "self", type = Core.class, doc = "this object"),
          @Param(name = "transformations", type = SkylarkList.class,
              generic1 = Transformation.class, doc = "The transformations to reverse"),
      },
      objectType = Core.class, useLocation = true)
  public static final BuiltinFunction REVERSE =
      new BuiltinFunction("reverse") {
        @SuppressWarnings("unused")
        public SkylarkList<Transformation> invoke(Core self, SkylarkList<Transformation> transforms,
            Location location)
            throws EvalException {

          ImmutableList.Builder<Transformation> builder = ImmutableList.builder();
          for (Transformation t : transforms.getContents(Transformation.class, "transformations")) {
            try {
              builder.add(t.reverse());
            } catch (NonReversibleValidationException e) {
              throw new EvalException(location, e.getMessage());
            }
          }

          return SkylarkList.createImmutable(builder.build().reverse());
        }
      };

  private static final String CHECK_LAST_REV_STATE = "check_last_rev_state";

  @SuppressWarnings("unused")
  @SkylarkSignature(name = "workflow", returnType = NoneType.class,
      doc = "Defines a migration pipeline which can be invoked via the Copybara command.\n"
          + "\n"
          + "Implicit labels that can be used/exposed:\n"
          + "\n"
          + "  - " + TransformWork.COPYBARA_CONTEXT_REFERENCE_LABEL + ": Requested reference. For"
          + " example if copybara is invoked as `copybara copy.bara.sky workflow master`, the value"
          + " would be `master`.\n"
          + "  - " + TransformWork.COPYBARA_LAST_REV + ": Last reference that was migrated\n"
          + "  - " + TransformWork.COPYBARA_CURRENT_REV
          + ": The current reference being migrated\n"
          + "  - " + TransformWork.COPYBARA_CURRENT_MESSAGE
          + ": The current message at this point of the transformations\n"
          + "  - " + TransformWork.COPYBARA_CURRENT_MESSAGE_TITLE
          + ": The current message title (first line) at this point of the transformations\n",
      parameters = {
          @Param(name = "self", type = Core.class, doc = "this object", positional = false),
          @Param(name = "name", type = String.class,
              doc = "The name of the workflow.", positional = false),
          @Param(name = "origin", type = Origin.class,
              doc = "Where to read from the code to be migrated, before applying the "
                  + "transformations. This is usually a VCS like Git, but can also be a local "
                  + "folder or even a pending change in a code review system like Gerrit.",
              positional = false),
          @Param(name = "destination", type = Destination.class,
              doc = "Where to write to the code being migrated, after applying the "
                  + "transformations. This is usually a VCS like Git, but can also be a local "
                  + "folder or even a pending change in a code review system like Gerrit.",
              positional = false),
          @Param(name = "authoring", type = Authoring.class,
              doc = "The author mapping configuration from origin to destination.",
              positional = false),
          @Param(name = "transformations", type = SkylarkList.class,
              generic1 = Object.class,
              doc = "The transformations to be run for this workflow. They will run in sequence.",
              positional = false, defaultValue = "[]"),
          @Param(name = "origin_files", type = Glob.class,
              doc = "A glob relative to the workdir that will be read from the"
                  + " origin during the import. For example glob([\"**.java\"]), all java files,"
                  + " recursively, which excludes all other file types.",
              defaultValue = "glob(['**'])", positional = false),
          @Param(name = "destination_files", type = Glob.class,
              doc = "A glob relative to the root of the destination repository that matches"
                  + " files that are part of the migration. Files NOT matching this glob will never"
                  + " be removed, even if the file does not exist in the source. For example"
                  + " glob(['**'], exclude = ['**/BUILD']) keeps all BUILD files in destination"
                  + " when the origin does not have any BUILD files. You can also use this to limit"
                  + " the migration to a subdirectory of the destination,"
                  + " e.g. glob(['java/src/**'], exclude = ['**/BUILD']) to only affect non-BUILD"
                  + " files in java/src.",
              defaultValue = "glob(['**'])", positional = false),
          @Param(name = "mode", type = String.class, doc = ""
              + "Workflow mode. Currently we support three modes:<br>"
              + "<ul>"
              + "<li><b>'SQUASH'</b>: Create a single commit in the destination with new tree"
              + " state.</li>"
              + "<li><b>'ITERATIVE'</b>: Import each origin change individually.</li>"
              + "<li><b>'CHANGE_REQUEST'</b>: Import a pending change to the Source-of-Truth."
              + " This could be a GH Pull Request, a Gerrit Change, etc. The final intention should"
              + " be to submit the change.</li>"
              + "<li><b>'CHANGE_REQUEST_FROM_SOT'</b>: Import a pending change **from** the"
              + " Source-of-Truth. This mode is useful when, despite the pending change being"
              + " already in the SoT, the users want to review the code on a different system."
              + " The final intention should never be to submit in the destination, but just"
              + " review or test</li>"
              + "</ul>",
              defaultValue = "\"SQUASH\"", positional = false),
          @Param(name = "reversible_check", type = Boolean.class,
              doc = "Indicates if the tool should try to to reverse all the transformations"
                  + " at the end to check that they are reversible.<br/>The default value is"
                  + " True for 'CHANGE_REQUEST' mode. False otherwise",
              defaultValue = "True for 'CHANGE_REQUEST' mode. False otherwise",
              noneable = true, positional = false),
          @Param(name = CHECK_LAST_REV_STATE, type = Boolean.class,
              doc = "If set to true, Copybara will validate that the destination didn't change"
                  + " since last-rev import for destination_files. Note that this"
                  + " flag doesn't work for CHANGE_REQUEST mode.",
              defaultValue = "False", positional = false),
          @Param(name = "ask_for_confirmation", type = Boolean.class,
              doc = "Indicates that the tool should show the diff and require user's"
                  + " confirmation before making a change in the destination.",
              defaultValue = "False", positional = false),
          @Param(name = "dry_run", type = Boolean.class,
              doc = "Run the migration in dry-run mode. Some destination implementations might"
                  + " have some side effects (like creating a code review), but never submit to a"
                  + " main branch.",
              defaultValue = "False", positional = false),
          @Param(name = "after_migration", type = SkylarkList.class,
              doc = "Run a feedback workflow after one migration happens. STILL WIP",
              defaultValue = "[]", positional = false),
          @Param(name = "change_identity", type = String.class,
              doc = "By default, Copybara hashes several fields so that each change has an"
                  + " unique identifier that at the same time reuses the generated destination"
                  + " change. This allows to customize the identity hash generation so that"
                  + " the same identity is used in several workflows. At least"
                  + " ${copybara_config_path} has to be present. Current user is added to the"
                  + " hash automatically.<br><br>"
                  + "Available variables:"
                  + "<ul>"
                  + "  <li>${copybara_config_path}: Main config file path</li>"
                  + "  <li>${copybara_workflow_name}: The name of the workflow being run</li>"
                  + "  <li>${copybara_reference}: The requested reference. In general Copybara"
                  + " tries its best to give a repetable reference. For example Gerrit"
                  + " change number or change-id or GitHub Pull Request number. If it cannot"
                  + " find a context reference it uses the resolved revision.</li>"
                  + "  <li>${label:label_name}: A label present for the current change. Exposed in"
                  + " the message or not.</li>"
                  + "</ul>"
                  + "If any of the labels cannot be found it defaults to the default identity"
                  + " (The effect would be no reuse of destination change between workflows)",
              defaultValue = "None", noneable = true, positional = false),
          @Param(name = "set_rev_id", type = Boolean.class,
              doc = "Copybara adds labels like 'GitOrigin-RevId' in the destination in order to"
                  + " track what was the latest change imported. For certain workflows like"
                  + " `CHANGE_REQUEST` it not used and is purely informational. This field allows"
                  + " to disable it for that mode. Destinations might ignore the flag.",
              defaultValue = "True", positional = false),
          @Param(name = "smart_prune", type = Boolean.class,
              doc = "By default CHANGE_REQUEST workflows cannot restore scrubbed files. This"
                  + " flag does a best-effort approach in restoring the non-affected snippets. For"
                  + " now we only revert the non-affected files. This only works for CHANGE_REQUEST"
                  + " mode.",
              defaultValue = "False", positional = false),
      },
      objectType = Core.class, useLocation = true, useEnvironment = true)
  @UsesFlags({WorkflowOptions.class})
  public static final BuiltinFunction WORKFLOW = new BuiltinFunction("workflow",
      ImmutableList.of(
          MutableList.empty(),
          Glob.ALL_FILES,
          Glob.ALL_FILES,
          "SQUASH",
          Runtime.NONE,
          Boolean.FALSE,
          Boolean.FALSE,
          Boolean.FALSE,
          SkylarkList.createImmutable(ImmutableList.of()),
          Runtime.NONE,
          Boolean.TRUE,
          Boolean.FALSE
      )) {

    public NoneType invoke(Core self, String workflowName,
        Origin<Revision> origin, Destination<?> destination, Authoring authoring,
        SkylarkList<?> transformations,
        Glob originFiles,
        Glob destinationFiles,
        String modeStr,
        Object reversibleCheckObj,
        Boolean checkLastRevStateField,
        Boolean askForConfirmation,
        Boolean dryRunMode,
        SkylarkList<?> afterMigrations,
        Object changeIdentityObj,
        Boolean setRevId,
        Boolean smartPrune,
        Location location,
        Environment env)
        throws EvalException {
      WorkflowMode mode = stringToEnum(location, "mode", modeStr, WorkflowMode.class);

      Sequence sequenceTransform = Sequence.fromConfig(self.generalOptions.profiler(),
          self.workflowOptions.joinTransformations(),
          transformations, "transformations", self.dynamicEnvironment);
      Transformation reverseTransform = null;
      if (!self.generalOptions.isDisableReversibleCheck()
          && convertFromNoneable(reversibleCheckObj, mode == WorkflowMode.CHANGE_REQUEST)) {
        try {
          reverseTransform = sequenceTransform.reverse();
        } catch (NonReversibleValidationException e) {
          throw new EvalException(location, e.getMessage());
        }
      }

      ImmutableList<Token> changeIdentity = getChangeIdentity(changeIdentityObj, location);

      if (!setRevId) {
        check(location, mode == WorkflowMode.CHANGE_REQUEST, "Disabling RevId is only supported"
            + " for CHANGE_REQUEST mode.");
      }
      if (smartPrune) {
        check(location, mode == WorkflowMode.CHANGE_REQUEST, "smart_prune is only supported"
            + " for CHANGE_REQUEST mode.");
      }

      if (checkLastRevStateField) {
        check(location, mode != WorkflowMode.CHANGE_REQUEST,
            "%s is not compatible with %s", CHECK_LAST_REV_STATE, WorkflowMode.CHANGE_REQUEST);
      }

      Authoring resolvedAuthoring = authoring;
      Author defaultAuthorFlag = self.workflowOptions.getDefaultAuthorFlag();
      if (defaultAuthorFlag != null) {
        resolvedAuthoring = new Authoring(defaultAuthorFlag, authoring.getMode(),
            authoring.getWhitelist());
      }

      getGlobalMigrations(env).addMigration(location, workflowName, new Workflow<>(
          workflowName,
          origin,
          destination,
          resolvedAuthoring,
          sequenceTransform,
          self.workflowOptions.getLastRevision(),
          self.workflowOptions.isInitHistory(),
          self.generalOptions,
          originFiles,
          destinationFiles,
          mode,
          self.workflowOptions,
          reverseTransform,
          askForConfirmation,
          self.mainConfigFile,
          self.allConfigFiles,
          self.workflowOptions.dryRunMode || dryRunMode,
          checkLastRevStateField || self.workflowOptions.checkLastRevState,
          convertFeedbackActions(afterMigrations, location, self.dynamicEnvironment),
          changeIdentity,
          setRevId,
          smartPrune));
      return Runtime.NONE;
    }
  };

  private static ImmutableList<Token> getChangeIdentity(Object changeIdentityObj, Location location)
      throws EvalException {
    String changeIdentity = SkylarkUtil.convertFromNoneable(changeIdentityObj, null);

    if (changeIdentity == null) {
      return ImmutableList.of();
    }
    ImmutableList<Token> result = new Parser(location).parse(changeIdentity);
    boolean configVarFound = false;
    for (Token token : result) {
      if (token.getType() != TokenType.INTERPOLATION) {
        continue;
      }
      if (token.getValue().equals(Workflow.COPYBARA_CONFIG_PATH_IDENTITY_VAR)) {
        configVarFound = true;
        continue;
      }
      if (token.getValue().equals(Workflow.COPYBARA_WORKFLOW_NAME_IDENTITY_VAR)
          || token.getValue().equals(Workflow.COPYBARA_REFERENCE_IDENTITY_VAR)
          || token.getValue().startsWith(Workflow.COPYBARA_REFERENCE_LABEL_VAR)) {
        continue;
      }
      throw new EvalException(location,
          String.format("Unrecognized variable: %s", token.getValue()));
    }
    if (!configVarFound) {
      throw new EvalException(location,
          String.format("${%s} variable is required", Workflow.COPYBARA_CONFIG_PATH_IDENTITY_VAR));
    }
    return result;
  }

  @SuppressWarnings("unused")
  @SkylarkSignature(
      name = "move",
      returnType = Transformation.class,
      doc = "Moves files between directories and renames files",
      parameters = {
          @Param(name = "self", type = Core.class, doc = "this object"),
          @Param(name = "before", type = String.class, doc = ""
              + "The name of the file or directory before moving. If this is the empty"
              + " string and 'after' is a directory, then all files in the workdir will be moved to"
              + " the sub directory specified by 'after', maintaining the directory tree."),
          @Param(name = "after", type = String.class, doc = ""
              + "The name of the file or directory after moving. If this is the empty"
              + " string and 'before' is a directory, then all files in 'before' will be moved to"
              + " the repo root, maintaining the directory tree inside 'before'."),
          @Param(name = "paths", type = Glob.class,
              doc = "A glob expression relative to 'before' if it represents a directory."
                  + " Only files matching the expression will be moved. For example,"
                  + " glob([\"**.java\"]), matches all java files recursively inside"
                  + " 'before' folder. Defaults to match all the files recursively.",
              defaultValue = "glob([\"**\"])"),
          @Param(name = "overwrite",
              doc = "Overwrite destination files if they already exist. Note that this makes the"
                  + " transformation non-reversible, since there is no way to know if the file"
                  + " was overwritten or not in the reverse workflow.",
              type = Boolean.class, defaultValue = "False")
      },
      objectType = Core.class, useLocation = true)
  @Example(title = "Move a directory",
      before = "Move all the files in a directory to another directory:",
      code = "core.move(\"foo/bar_internal\", \"bar\")",
      after = "In this example, `foo/bar_internal/one` will be moved to `bar/one`.")
  @Example(title = "Move all the files to a subfolder",
      before = "Move all the files in the checkout dir into a directory called foo:",
      code = "core.move(\"\", \"foo\")",
      after = "In this example, `one` and `two/bar` will be moved to `foo/one` and `foo/two/bar`.")
  @Example(title = "Move a subfolder's content to the root",
      before = "Move the contents of a folder to the checkout root directory:",
      code = "core.move(\"foo\", \"\")",
      after = "In this example, `foo/bar` would be moved to `bar`.")
  public static final BuiltinFunction MOVE = new BuiltinFunction("move",
      ImmutableList.of(Glob.ALL_FILES, false)) {
    @SuppressWarnings("unused")
    public Transformation invoke(Core self, String before, String after, Glob paths,
        Boolean overwrite,
        Location location) throws EvalException {

      check(location, !Objects.equals(before, after),
          "Moving from the same folder to the same folder is a noop. Remove the"
              + " transformation.");

      return CopyOrMove.createMove(before, after, self.workflowOptions, paths, overwrite, location);
    }
  };

  @SuppressWarnings("unused")
  @SkylarkSignature(
      name = "copy",
      returnType = Transformation.class,
      doc = "Copy files between directories and renames files",
      parameters = {
          @Param(name = "self", type = Core.class, doc = "this object"),
          @Param(name = "before", type = String.class, doc = ""
              + "The name of the file or directory to copy. If this is the empty"
              + " string and 'after' is a directory, then all files in the workdir will be copied"
              + " to the sub directory specified by 'after', maintaining the directory tree."),
          @Param(name = "after", type = String.class, doc = ""
              + "The name of the file or directory destination. If this is the empty"
              + " string and 'before' is a directory, then all files in 'before' will be copied to"
              + " the repo root, maintaining the directory tree inside 'before'."),
          @Param(name = "paths", type = Glob.class,
              doc = "A glob expression relative to 'before' if it represents a directory."
                  + " Only files matching the expression will be copied. For example,"
                  + " glob([\"**.java\"]), matches all java files recursively inside"
                  + " 'before' folder. Defaults to match all the files recursively.",
              defaultValue = "glob([\"**\"])"),
          @Param(name = "overwrite",
              doc = "Overwrite destination files if they already exist. Note that this makes the"
                  + " transformation non-reversible, since there is no way to know if the file"
                  + " was overwritten or not in the reverse workflow.",
              type = Boolean.class, defaultValue = "False")
      },
      objectType = Core.class, useLocation = true)
  @Example(title = "Copy a directory",
      before = "Move all the files in a directory to another directory:",
      code = "core.copy(\"foo/bar_internal\", \"bar\")",
      after = "In this example, `foo/bar_internal/one` will be copied to `bar/one`.")
  @Example(title = "Copy with reversal",
      before = "Copy all static files to a 'static' folder and use remove for reverting the change",
      code = ""
          + "core.transform(\n"
          + "    [core.copy(\"foo\", \"foo/static\", paths = glob([\"**.css\",\"**.html\", ]))],\n"
          + "    reversal = [core.remove(glob(['foo/static/**.css', 'foo/static/**.html']))]\n"
          + ")")
  public static final BuiltinFunction COPY = new BuiltinFunction("copy",
      ImmutableList.of(Glob.ALL_FILES, false)) {
    @SuppressWarnings("unused")
    public Transformation invoke(Core self, String before, String after, Glob paths,
        Boolean overwrite,
        Location location) throws EvalException {
      check(location, !Objects.equals(before, after),
          "Copying from the same folder to the same folder is a noop. Remove the"
              + " transformation.");
      return CopyOrMove.createCopy(before, after, self.workflowOptions, paths, overwrite, location);
    }
  };

  @SuppressWarnings("unused")
  @SkylarkSignature(
      name = "remove",
      returnType = Remove.class,
      doc = "Remove files from the workdir. **This transformation is only mean to be used inside"
          + " core.transform for reversing core.copy like transforms**. For regular file filtering"
          + " use origin_files exclude mechanism.",
      parameters = {
          @Param(name = "self", type = Core.class, doc = "this object"),
          @Param(name = "paths", type = Glob.class, doc = "The files to be deleted"),
      },
      objectType = Core.class, useLocation = true)
  @Example(title = "Reverse a file copy",
      before = "Move all the files in a directory to another directory:",
      code = "core.transform(\n"
          + "    [core.copy(\"foo\", \"foo/public\")],\n"
          + "    reversal = [core.remove(glob([\"foo/public/**\"]))])",
      after = "In this example, `foo/bar_internal/one` will be moved to `bar/one`.")
  @Example(title = "Copy with reversal",
      before = "Copy all static files to a 'static' folder and use remove for reverting the change",
      code = ""
          + "core.transform(\n"
          + "    [core.copy(\"foo\", \"foo/static\", paths = glob([\"**.css\",\"**.html\", ]))],\n"
          + "    reversal = [core.remove(glob(['foo/static/**.css', 'foo/static/**.html']))]\n"
          + ")")
  public static final BuiltinFunction REMOVE = new BuiltinFunction("remove") {
    @SuppressWarnings("unused")
    public Remove invoke(Core self, Glob paths, Location location) throws EvalException {
      return new Remove(paths, self.workflowOptions, location);
    }
  };

  @SuppressWarnings("unused")
  @SkylarkSignature(
      name = "replace",
      returnType = Replace.class,
      doc = "Replace a text with another text using optional regex groups. This tranformer can be"
          + " automatically reversed.",
      parameters = {
          @Param(name = "self", type = Core.class, doc = "this object"),
          @Param(name = "before", type = String.class,
              doc = "The text before the transformation. Can contain references to regex groups."
                  + " For example \"foo${x}text\".<p>If '$' literal character needs to be matched, "
                  + "'`$$`' should be used. For example '`$$FOO`' would match the literal '$FOO'."),
          @Param(name = "after", type = String.class,
              doc = "The text after the transformation. It can also contain references to regex "
                  + "groups, like 'before' field."),
          @Param(name = "regex_groups", type = SkylarkDict.class,
              doc = "A set of named regexes that can be used to match part of the replaced text."
                  + "Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax."
                  + " For example {\"x\": \"[A-Za-z]+\"}", defaultValue = "{}"),
          @Param(name = "paths", type = Glob.class,
              doc = "A glob expression relative to the workdir representing the files to apply"
                  + " the transformation. For example, glob([\"**.java\"]), matches all java files"
                  + " recursively. Defaults to match all the files recursively.",
              defaultValue = "glob([\"**\"])"),
          @Param(name = "first_only", type = Boolean.class,
              doc = "If true, only replaces the first instance rather than all. In single line"
                  + " mode, replaces the first instance on each line. In multiline mode, replaces "
                  + "the first instance in each file.",
              defaultValue = "False"),
          @Param(name = "multiline", type = Boolean.class,
              doc = "Whether to replace text that spans more than one line.",
              defaultValue = "False"),
          @Param(name = "repeated_groups", type = Boolean.class,
              doc = "Allow to use a group multiple times. For example foo${repeated}/${repeated}."
                  + " Note that this mechanism doesn't use backtracking. In other words, the group"
                  + " instances are treated as different groups in regex construction and then a"
                  + " validation is done after that.",
              defaultValue = "False"),
          @Param(name = "ignore", type = SkylarkList.class,
              doc = "A set of regexes. Any text that matches any expression in this set, which"
                  + " might otherwise be transformed, will be ignored.",
              defaultValue = "[]"),
      },
      objectType = Core.class, useLocation = true)
  @Example(title = "Simple replacement",
      before = "Replaces the text \"internal\" with \"external\" in all java files",
      code = "core.replace(\n"
          + "    before = \"internal\",\n"
          + "    after = \"external\",\n"
          + "    paths = glob([\"**.java\"]),\n"
          + ")")
  @Example(title = "Replace using regex groups",
      before = "In this example we map some urls from the internal to the external version in"
          + " all the files of the project.",
      code = "core.replace(\n"
          + "        before = \"https://some_internal/url/${pkg}.html\",\n"
          + "        after = \"https://example.com/${pkg}.html\",\n"
          + "        regex_groups = {\n"
          + "            \"pkg\": \".*\",\n"
          + "        },\n"
          + "    )",
      after = "So a url like `https://some_internal/url/foo/bar.html` will be transformed to"
          + " `https://example.com/foo/bar.html`.")
  @Example(title = "Remove confidential blocks",
      before = "This example removes blocks of text/code that are confidential and thus shouldn't"
          + "be exported to a public repository.",
      code = "core.replace(\n"
          + "        before = \"${x}\",\n"
          + "        after = \"\",\n"
          + "        multiline = True,\n"
          + "        regex_groups = {\n"
          + "            \"x\": \"(?m)^.*BEGIN-INTERNAL[\\\\w\\\\W]*?END-INTERNAL.*$\\\\n\",\n"
          + "        },\n"
          + "    )",
      after = "This replace would transform a text file like:\n\n"
          + "```\n"
          + "This is\n"
          + "public\n"
          + " // BEGIN-INTERNAL\n"
          + " confidential\n"
          + " information\n"
          + " // END-INTERNAL\n"
          + "more public code\n"
          + " // BEGIN-INTERNAL\n"
          + " more confidential\n"
          + " information\n"
          + " // END-INTERNAL\n"
          + "```\n\n"
          + "Into:\n\n"
          + "```\n"
          + "This is\n"
          + "public\n"
          + "more public code\n"
          + "```\n\n")
  public static final BuiltinFunction REPLACE = new BuiltinFunction("replace",
      ImmutableList.of(
          SkylarkDict.empty(),
          Glob.ALL_FILES,
          false,
          false,
          false,
          Tuple.empty())) {
    public Replace invoke(Core self, String before, String after,
        SkylarkDict<String, String> regexes, Glob paths, Boolean firstOnly,
        Boolean multiline, Boolean repeatedGroups, SkylarkList<String> ignore,
        Location location) throws EvalException {
      return Replace.create(location,
          before,
          after,
          Type.STRING_DICT.convert(regexes, "regex_groups"),
          paths,
          firstOnly,
          multiline,
          repeatedGroups,
          Type.STRING_LIST.convert(ignore, "patterns_to_ignore"),
          self.workflowOptions);
    }
  };

  @SuppressWarnings("unused")
  @SkylarkSignature(
      name = "todo_replace",
      returnType = TodoReplace.class,
      doc = "Replace Google style TODOs. For example `TODO(username, othername)`.",
      parameters = {
          @Param(name = "self", type = Core.class, doc = "this object"),
          @Param(name = "tags", type = SkylarkList.class, generic1 = String.class,
              doc = "Prefix tag to look for", defaultValue = "['TODO', 'NOTE']"),
          @Param(name = "mapping", type = SkylarkDict.class,
              doc = "Mapping of users/strings", defaultValue = "{}"),
          @Param(name = "mode", type = String.class,
              doc = "Mode for the replace:"
                  + "<ul>"
                  + "<li>'MAP_OR_FAIL': Try to use the mapping and if not found fail.</li>"
                  + "<li>'MAP_OR_IGNORE': Try to use the mapping but ignore if no mapping found."
                  + "</li>"
                  + "<li>'MAP_OR_DEFAULT': Try to use the mapping and use the default if not found."
                  + "</li>"
                  + "<li>'SCRUB_NAMES': Scrub all names from TODOs. Transforms 'TODO(foo)' to "
                  + "'TODO'"
                  + "</li>"
                  + "<li>'USE_DEFAULT': Replace any TODO(foo, bar) with TODO(default_string)</li>"
                  + "</ul>", defaultValue = "'MAP_OR_IGNORE'"),
          @Param(name = "paths", type = Glob.class,
              doc = "A glob expression relative to the workdir representing the files to apply"
                  + " the transformation. For example, glob([\"**.java\"]), matches all java files"
                  + " recursively. Defaults to match all the files recursively.",
              defaultValue = "glob([\"**\"])"),
          @Param(name = "default", type = String.class,
              doc = "Default value if mapping not found. Only valid for 'MAP_OR_DEFAULT' or"
                  + " 'USE_DEFAULT' modes", noneable = true, defaultValue = "None"),
      },
      objectType = Core.class, useLocation = true)
  @Example(title = "Simple update",
      before = "Replace TODOs and NOTES for users in the mapping:",
      code = "core.todo_replace(\n"
          + "  mapping = {\n"
          + "    'test1' : 'external1',\n"
          + "    'test2' : 'external2'\n"
          + "  }\n"
          + ")",
      after = "Would replace texts like TODO(test1) or NOTE(test1, test2) with TODO(external1)"
          + " or NOTE(external1, external2)")
  @Example(title = "Scrubbing",
      before = "Remove text from inside TODOs",
      code = "core.todo_replace(\n"
          + "  mode = 'SCRUB_NAMES'\n"
          + ")",
      after = "Would replace texts like TODO(test1): foo or NOTE(test1, test2):foo with TODO:foo"
          + " and NOTE:foo")
  public static final BuiltinFunction TODO_REPLACE = new BuiltinFunction("todo_replace",
      ImmutableList.of(
          SkylarkList.createImmutable(ImmutableList.of("TODO", "NOTE")),
          SkylarkDict.empty(),
          TodoReplace.Mode.MAP_OR_IGNORE.toString(),
          Glob.ALL_FILES,
          Runtime.NONE)) {
    public TodoReplace invoke(Core self, SkylarkList<String> skyTags,
        SkylarkDict<String, String> skyMapping, String modeStr, Glob paths, Object skyDefault,
        Location location) throws EvalException {
      Mode mode = stringToEnum(location, "mode", modeStr, Mode.class);
      Map<String, String> mapping = Type.STRING_DICT.convert(skyMapping, "mapping");
      String defaultString = SkylarkUtil.convertFromNoneable(skyDefault, /*defaultValue=*/null);
      ImmutableList<String> tags = ImmutableList.copyOf(Type.STRING_LIST.convert(skyTags, "tags"));

      check(location, !tags.isEmpty(), "'tags' cannot be empty");
      if (mode == Mode.MAP_OR_DEFAULT || mode == Mode.USE_DEFAULT) {
        check(location, defaultString != null, "'default' needs to be set for mode '%s'", mode);
      } else {
        check(location, defaultString == null, "'default' cannot be used for mode '%s'", mode);
      }
      if (mode == Mode.USE_DEFAULT || mode == Mode.SCRUB_NAMES) {
        check(location, mapping.isEmpty(), "'mapping' cannot be used with mode %s", mode);
      }
      return new TodoReplace(location, paths, tags, mode, mapping, defaultString,
                             self.workflowOptions.parallelizer());
    }
  };

  @SuppressWarnings("unused")
  @SkylarkSignature(
      name = "verify_match",
      returnType = VerifyMatch.class,
      doc = "Verifies that a RegEx matches (or not matches) the specified files. Does not"
          + " transform anything, but will stop the workflow if it fails.",
      parameters = {
          @Param(name = "self", type = Core.class, doc = "this object"),
          @Param(name = "regex", type = String.class,
              doc = "The regex pattern to verify. To satisfy the validation, there has to be at"
                  + "least one (or no matches if verify_no_match) match in each of the files "
                  + "included in paths. The re2j pattern will be applied in multiline mode, i.e."
                  + " '^' refers to the beginning of a file and '$' to its end. "
                  + "Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax."),
          @Param(name = "paths", type = Glob.class,
              doc = "A glob expression relative to the workdir representing the files to apply"
                  + " the transformation. For example, glob([\"**.java\"]), matches all java files"
                  + " recursively. Defaults to match all the files recursively.",
              defaultValue = "glob([\"**\"])"),
          @Param(name = "verify_no_match", type = Boolean.class,
              doc = "If true, the transformation will verify that the RegEx does not match.",
              defaultValue = "False"),
      },
      objectType = Core.class, useLocation = true)
  public static final BuiltinFunction VERIFY_MATCH = new BuiltinFunction("verify_match",
      ImmutableList.of(
          Glob.ALL_FILES,
          false
      )) {
    public VerifyMatch invoke(Core self, String regex, Glob paths, Boolean verifyNoMatch,
        Location location) throws EvalException {
      return VerifyMatch.create(location,
                                regex,
                                paths,
                                verifyNoMatch,
                                self.workflowOptions.parallelizer());
    }
  };

  @SuppressWarnings("unused")
  @SkylarkSignature(
      name = "transform",
      returnType = Transformation.class,
      doc = "Groups some transformations in a transformation that can contain a particular,"
              + " manually-specified, reversal, where the forward version and reversed version"
              + " of the transform are represented as lists of transforms. The is useful if a"
              + " transformation does not automatically reverse, or if the automatic reversal"
              + " does not work for some reason."
              + "<br>"
              + "If reversal is not provided, the transform will try to compute the reverse of"
              + " the transformations list.",
      parameters = {
        @Param(name = "self", type = Core.class, doc = "this object"),
        @Param(
            name = "transformations",
            type = SkylarkList.class,
            generic1 = Transformation.class,
            doc =
                "The list of transformations to run as a result of running this"
                    + " transformation."),
        @Param(
            name = "reversal",
            type = SkylarkList.class,
            generic1 = Transformation.class,
            doc =
                "The list of transformations to run as a result of running this"
                    + " transformation in reverse.",
            named = true,
            positional = false,
            noneable = true,
            defaultValue = "The reverse of 'transformations'"),
        @Param(
            name = "ignore_noop",
            type = Boolean.class,
            doc = "In case a noop error happens in the group of transformations (Both forward and"
                  + " reverse), it will be ignored, but the rest of the transformations in the group"
                  + " will still be executed. If ignore_noop is not set,"
                  + " we will apply the closest parent's ignore_noop.",
            named = true,
            positional = false,
            noneable = true,
            defaultValue = "None"),
      },
      objectType = Core.class,
      useEnvironment = true)
  public static final BuiltinFunction TRANSFORM =
      new BuiltinFunction("transform", ImmutableList.of(Runtime.NONE, Runtime.NONE)) {
        @SuppressWarnings("unused")
        public Transformation invoke(
            Core self,
            SkylarkList<Transformation> transformations,
            Object reversal,
            Object ignoreNoop,
            Environment env)
            throws EvalException {
          Sequence forward =
              Sequence.fromConfig(
                  self.generalOptions.profiler(),
                  self.workflowOptions.joinTransformations(),
                  transformations,
                  "transformations",
                  self.dynamicEnvironment);
          SkylarkList<Transformation> reverseList = convertFromNoneable(reversal, null);
          Boolean updatedIgnoreNoop = convertFromNoneable(ignoreNoop, null);
          if (reverseList == null) {
            try {
              reverseList = SkylarkList.createImmutable(ImmutableList.of(forward.reverse()));
            } catch (NonReversibleValidationException e) {
              throw new EvalException(
                  location,
                  "transformations are not automatically reversible."
                      + " Use 'reversal' field to explicitly configure the reversal of the transform",
                  e);
            }
          }
          Sequence reverse =
              Sequence.fromConfig(
                  self.generalOptions.profiler(),
                  self.workflowOptions.joinTransformations(),
                  reverseList,
                  "reversal",
                  self.dynamicEnvironment);
          return new ExplicitReversal(
              forward, reverse, updatedIgnoreNoop);
        }
      };

  @SuppressWarnings("unused")
  @SkylarkSignature(
      name = "dynamic_transform",
      returnType = Transformation.class,
      doc = "Create a dynamic Skylark transformation. This should only be used by libraries"
          + " developers",
      parameters = {
          @Param(name = "self", type = Core.class, doc = "this object"),
          @Param(name = "impl", type = BaseFunction.class, doc = "The Skylark function to call"),
          @Param(name = "params", type = SkylarkDict.class,
              doc = "The parameters to the function. Will be available under ctx.params",
              defaultValue = "{}"),
      },
      objectType = Core.class, useEnvironment = true)
  @Example(title = "Create a dynamic transformation with parameter",
      before = "If you want to create a library that uses dynamic transformations, you probably"
          + " want to make them customizable. In order to do that, in your library.bara.sky, you"
          + " need to hide the dynamic transformation (prefix with '_' and instead expose a"
          + " function that creates the dynamic transformation with the param:",
      code = ""
          + "def _test_impl(ctx):\n"
          + "  ctx.set_message("
          + "ctx.message + ctx.params['name'] + str(ctx.params['number']) + '\\n')\n"
          + "\n"
          + "def test(name, number = 2):\n"
          + "  return core.dynamic_transform(impl = _test_impl,\n"
          + "                           params = { 'name': name, 'number': number})\n"
          + "\n"
          + "  ",
      testExistingVariable = "test",
      after = "After defining this function, you can use `test('example', 42)` as a transformation"
          + " in `core.workflow`.")
  public static final BuiltinFunction DYNAMIC_TRANSFORM = new BuiltinFunction("dynamic_transform") {
    @SuppressWarnings("unused")
    public Transformation invoke(Core self, BaseFunction impl, SkylarkDict<?, ?> params,
        Environment env) {
      return new SkylarkTransformation(impl, SkylarkDict.<Object, Object>copyOf(env, params),
          self.dynamicEnvironment);
    }
  };

  @SuppressWarnings("unused")
  @SkylarkSignature(
      name = "dynamic_feedback",
      returnType = Action.class,
      doc = "Create a dynamic Skylark feedback migration. This should only be used by libraries"
          + " developers",
      parameters = {
          @Param(name = "self", type = Core.class, doc = "this object"),
          @Param(name = "impl",
              type = BaseFunction.class,
              doc = "The Skylark function to call"),
          @Param(name = "params", type = SkylarkDict.class,
              doc = "The parameters to the function. Will be available under ctx.params",
              defaultValue = "{}"),
      },
      objectType = Core.class, useEnvironment = true, documented = false)
  public static final BuiltinFunction DYNAMIC_FEEDBACK = new BuiltinFunction("dynamic_feedback") {
    @SuppressWarnings("unused")
    public Action invoke(Core self, BaseFunction impl, SkylarkDict<?, ?> params,
        Environment env) {
      return new SkylarkAction(impl, SkylarkDict.<Object, Object>copyOf(env, params),
          self.dynamicEnvironment);
    }
  };

  @SuppressWarnings("unused")
  @SkylarkSignature(
    name = "feedback",
    returnType = NoneType.class,
    documented = false,
    doc =
        ""
            + "WIP: This is not implemented yet.\n"
            + "\n"
            + "Defines a migration of changes' metadata, that can be invoked via the Copybara "
            + "command in the same way as a regular workflow migrates the change itself.\n"
            + "\n"
            + "It is considered change metadata any information associated with a change (pending "
            + "or submitted) that is not core to the change itself. A few examples:\n"
            + "<ul>\n"
            + "  <li> Comments: Present in any code review system. Examples: Github PRs or Gerrit "
            + "    code reviews.</li>\n"
            + "  <li> Labels: Used in code review systems for approvals and/or CI results. "
            + "    Examples: Github labels, Gerrit code review labels.</li>\n"
            + "</ul>\n"
            + "For the purpose of this workflow, it is not considered metadata the commit message "
            + "in Git, or any of the contents of the file tree.\n"
            + "\n",
    parameters = {
      @Param(name = "self", type = Core.class, doc = "this object", positional = false),
      @Param(
        name = "name",
        type = String.class,
        doc = "The name of the feedback workflow.",
        positional = false
      ),
      @Param(
        name = "origin",
        type = Trigger.class,
        doc = "The trigger of a feedback migration.",
        positional = false
      ),
      @Param(
        name = "destination",
        type = Endpoint.class,
        doc =
            "Where to write change metadata to. This is usually a code review system like "
                + "Gerrit or Github PR.",
        positional = false
      ),
      @Param(
        name = "actions",
        type = SkylarkList.class,
        doc =
            ""
                + "A list of feedback actions to perform, with the following semantics:\n"
                + "  - There is no guarantee of the order of execution.\n"
                + "  - Actions need to be independent from each other.\n"
                + "  - Failure in one action might prevent other actions from executing.\n",
        defaultValue = "[]",
        positional = false
      ),
    },
    objectType = Core.class,
    useLocation = true,
    useEnvironment = true
  )
  @UsesFlags({FeedbackOptions.class})
  /*TODO(danielromero): Add default values*/
  public static final BuiltinFunction FEEDBACK =
      new BuiltinFunction("feedback") {
        public NoneType invoke(
            Core self,
            String workflowName,
            Trigger trigger,
            Endpoint destination,
            SkylarkList<?> feedbackActions,
            Location location,
            Environment env)
            throws EvalException {
          ImmutableList<Action> actions = convertFeedbackActions(feedbackActions, location,
              self.dynamicEnvironment);
          getGlobalMigrations(env)
              .addMigration(
                  location,
                  workflowName,
                  new Feedback(
                      workflowName,
                      self.mainConfigFile,
                      trigger,
                      destination,
                      actions,
                      self.generalOptions));
          return Runtime.NONE;
        }
      };

  @SuppressWarnings("unused")
  @SkylarkSignature(
      name = "parse_message",
      returnType = ChangeMessage.class,
      doc = "Returns a ChangeMessage parsed from a well formed string.",
      parameters = {
          @Param(name = "message", type = String.class, doc = "The contents of the change message"),
      }, useLocation = true)
  public static final BuiltinFunction PARSE_MESSAGE = new BuiltinFunction("parse_message") {
    public ChangeMessage invoke(String changeMessage, Location location)
        throws EvalException {
      try {
        return ChangeMessage.parseMessage(changeMessage);
      } catch (RuntimeException e) {
        throw new EvalException(location, String.format(
            "Cannot parse change message '%s': %s", changeMessage, e.getMessage()), e);
      }
    }
  };

  private static ImmutableList<Action> convertFeedbackActions(
      SkylarkList<?> feedbackActions, Location location, Supplier<Environment> env)
      throws EvalException {
    ImmutableList.Builder<Action> actions = ImmutableList.builder();
    for (Object action : feedbackActions) {
      if (action instanceof BaseFunction) {
        actions.add(new SkylarkAction((BaseFunction) action, SkylarkDict.empty(), env));
      } else if (action instanceof Action) {
        actions.add((Action) action);
      } else {
        throw new EvalException(
            location,
            String.format("Invalid feedback action '%s 'of type: %s", action, action.getClass()));
      }
    }
    return actions.build();
  }

  @Override
  public void setConfigFile(ConfigFile<?> mainConfigFile, ConfigFile<?> currentConfigFile) {
    this.mainConfigFile = mainConfigFile;
  }

  @Override
  public void setAllConfigResources(
      Supplier<ImmutableMap<String, ? extends ConfigFile<?>>> allConfigFiles) {
    this.allConfigFiles = allConfigFiles;
  }

  @Override
  public void setDynamicEnvironment(Supplier<Environment> dynamicEnvironment) {
    this.dynamicEnvironment = dynamicEnvironment;
  }
}
