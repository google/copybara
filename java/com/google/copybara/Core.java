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

import static com.google.copybara.Workflow.COPYBARA_CONFIG_PATH_IDENTITY_VAR;
import static com.google.copybara.config.GlobalMigrations.getGlobalMigrations;
import static com.google.copybara.config.SkylarkUtil.check;
import static com.google.copybara.config.SkylarkUtil.convertFromNoneable;
import static com.google.copybara.config.SkylarkUtil.stringToEnum;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.authoring.Author;
import com.google.copybara.authoring.Authoring;
import com.google.copybara.config.ConfigFile;
import com.google.copybara.config.LabelsAwareModule;
import com.google.copybara.config.Migration;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.doc.annotations.DocDefault;
import com.google.copybara.doc.annotations.Example;
import com.google.copybara.doc.annotations.UsesFlags;
import com.google.copybara.exception.EmptyChangeException;
import com.google.copybara.feedback.Action;
import com.google.copybara.feedback.Feedback;
import com.google.copybara.feedback.SkylarkAction;
import com.google.copybara.templatetoken.Parser;
import com.google.copybara.templatetoken.Token;
import com.google.copybara.templatetoken.Token.TokenType;
import com.google.copybara.transform.CopyOrMove;
import com.google.copybara.transform.ExplicitReversal;
import com.google.copybara.transform.FilterReplace;
import com.google.copybara.transform.Remove;
import com.google.copybara.transform.Replace;
import com.google.copybara.transform.ReplaceMapper;
import com.google.copybara.transform.ReversibleFunction;
import com.google.copybara.transform.Sequence;
import com.google.copybara.transform.SkylarkTransformation;
import com.google.copybara.transform.TodoReplace;
import com.google.copybara.transform.TodoReplace.Mode;
import com.google.copybara.transform.VerifyMatch;
import com.google.copybara.transform.debug.DebugOptions;
import com.google.copybara.util.Glob;
import com.google.devtools.build.lib.events.Location;
import com.google.devtools.build.lib.skylarkinterface.Param;
import com.google.devtools.build.lib.skylarkinterface.SkylarkCallable;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModule;
import com.google.devtools.build.lib.skylarkinterface.SkylarkModuleCategory;
import com.google.devtools.build.lib.syntax.BaseFunction;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.Runtime;
import com.google.devtools.build.lib.syntax.Runtime.NoneType;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import com.google.devtools.build.lib.syntax.SkylarkList;
import com.google.devtools.build.lib.syntax.StarlarkThread;
import com.google.re2j.Pattern;
import java.util.IllegalFormatException;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Main configuration class for creating migrations.
 *
 * <p>This class is exposed in Skylark configuration as an instance variable called "core". So users
 * can use it as:
 * <pre>
 * core.workspace(
 *   name = "foo",
 *   ...
 * )
 * </pre>
 */
@SkylarkModule(
    name = "core",
    doc = "Core functionality for creating migrations, and basic transformations.",
    category = SkylarkModuleCategory.BUILTIN)
    @UsesFlags({GeneralOptions.class, DebugOptions.class})
public class Core implements LabelsAwareModule {

  // Restrict for label ids like 'BAZEL_REV_ID'. More strict than our current revId.
  private static final Pattern CUSTOM_REVID_FORMAT = Pattern.compile("[A-Z][A-Z_0-9]{1,30}_REV_ID");
  private static final String CHECK_LAST_REV_STATE = "check_last_rev_state";
  private final GeneralOptions generalOptions;
  private final WorkflowOptions workflowOptions;
  private final DebugOptions debugOptions;
  private ConfigFile mainConfigFile;
  private Supplier<ImmutableMap<String, ConfigFile>> allConfigFiles;
  private Supplier<StarlarkThread> dynamicStarlarkThread;

  public Core(
      GeneralOptions generalOptions, WorkflowOptions workflowOptions, DebugOptions debugOptions) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.workflowOptions = Preconditions.checkNotNull(workflowOptions);
    this.debugOptions = Preconditions.checkNotNull(debugOptions);
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "reverse",
      doc =
          "Given a list of transformations, returns the list of transformations equivalent to"
              + " undoing all the transformations",
      parameters = {
        @Param(
            name = "transformations",
            named = true,
            type = SkylarkList.class,
            generic1 = Transformation.class,
            doc = "The transformations to reverse"),
      },
      useLocation = true)
  public SkylarkList<Transformation> reverse(
      SkylarkList<?> transforms, // <Transformation> or <BaseFunction>
      Location location)
      throws EvalException {

    ImmutableList.Builder<Transformation> builder = ImmutableList.builder();
    for (Object t : transforms.getContents(Object.class, "transformations")) {
      try {
        if (t instanceof BaseFunction) {
          builder.add(
              new SkylarkTransformation(
                      (BaseFunction) t, SkylarkDict.empty(), dynamicStarlarkThread)
                  .reverse());
        } else if (t instanceof Transformation) {
          builder.add(((Transformation) t).reverse());
        } else {
          throw new EvalException(
              location, "Expected type 'transformation' or function, but found: " + t);
        }
      } catch (NonReversibleValidationException e) {
        throw new EvalException(location, e.getMessage());
      }
    }

    return SkylarkList.createImmutable(builder.build().reverse());
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "workflow",
      doc =
          "Defines a migration pipeline which can be invoked via the Copybara command.\n"
              + "\n"
              + "Implicit labels that can be used/exposed:\n"
              + "\n"
              + "  - "
              + TransformWork.COPYBARA_CONTEXT_REFERENCE_LABEL
              + ": Requested reference. For example if copybara is invoked as `copybara"
              + " copy.bara.sky workflow master`, the value would be `master`.\n"
              + "  - "
              + TransformWork.COPYBARA_LAST_REV
              + ": Last reference that was migrated\n"
              + "  - "
              + TransformWork.COPYBARA_CURRENT_REV
              + ": The current reference being migrated\n"
              + "  - "
              + TransformWork.COPYBARA_CURRENT_MESSAGE
              + ": The current message at this point of the transformations\n"
              + "  - "
              + TransformWork.COPYBARA_CURRENT_MESSAGE_TITLE
              + ": The current message title (first line) at this point of the transformations\n"
              + "  - "
              + TransformWork.COPYBARA_AUTHOR
              + ": The author of the change\n",
      parameters = {
        @Param(
            name = "name",
            named = true,
            type = String.class,
            doc = "The name of the workflow.",
            positional = false),
        @Param(
            name = "origin",
            named = true,
            type = Origin.class,
            doc =
                "Where to read from the code to be migrated, before applying the "
                    + "transformations. This is usually a VCS like Git, but can also be a local "
                    + "folder or even a pending change in a code review system like Gerrit.",
            positional = false),
        @Param(
            name = "destination",
            named = true,
            type = Destination.class,
            doc =
                "Where to write to the code being migrated, after applying the "
                    + "transformations. This is usually a VCS like Git, but can also be a local "
                    + "folder or even a pending change in a code review system like Gerrit.",
            positional = false),
        @Param(
            name = "authoring",
            named = true,
            type = Authoring.class,
            doc = "The author mapping configuration from origin to destination.",
            positional = false),
        @Param(
            name = "transformations",
            named = true,
            type = SkylarkList.class,
            doc = "The transformations to be run for this workflow. They will run in sequence.",
            positional = false,
            defaultValue = "[]"),
        @Param(
            name = "origin_files",
            named = true,
            type = Glob.class,
            doc =
                "A glob relative to the workdir that will be read from the"
                    + " origin during the import. For example glob([\"**.java\"]), all java files,"
                    + " recursively, which excludes all other file types.",
            defaultValue = "None",
            noneable = true,
            positional = false),
        @Param(
            name = "destination_files",
            named = true,
            type = Glob.class,
            doc =
                "A glob relative to the root of the destination repository that matches files that"
                    + " are part of the migration. Files NOT matching this glob will never be"
                    + " removed, even if the file does not exist in the source. For example"
                    + " glob(['**'], exclude = ['**/BUILD']) keeps all BUILD files in destination"
                    + " when the origin does not have any BUILD files. You can also use this to"
                    + " limit the migration to a subdirectory of the destination, e.g."
                    + " glob(['java/src/**'], exclude = ['**/BUILD']) to only affect non-BUILD"
                    + " files in java/src.",
            defaultValue = "None",
            noneable = true,
            positional = false),
        @Param(
            name = "mode",
            named = true,
            type = String.class,
            doc =
                "Workflow mode. Currently we support four modes:<br><ul><li><b>'SQUASH'</b>:"
                    + " Create a single commit in the destination with new tree"
                    + " state.</li><li><b>'ITERATIVE'</b>: Import each origin change"
                    + " individually.</li><li><b>'CHANGE_REQUEST'</b>: Import a pending change to"
                    + " the Source-of-Truth. This could be a GH Pull Request, a Gerrit Change,"
                    + " etc. The final intention should be to submit the change in the SoT"
                    + " (destination in this case).</li><li><b>'CHANGE_REQUEST_FROM_SOT'</b>:"
                    + " Import a pending change **from** the Source-of-Truth. This mode is useful"
                    + " when, despite the pending change being already in the SoT, the users want"
                    + " to review the code on a different system. The final intention should never"
                    + " be to submit in the destination, but just review or test</li></ul>",
            defaultValue = "\"SQUASH\"",
            positional = false),
        @Param(
            name = "reversible_check",
            named = true,
            type = Boolean.class,
            doc =
                "Indicates if the tool should try to to reverse all the transformations"
                    + " at the end to check that they are reversible.<br/>The default value is"
                    + " True for 'CHANGE_REQUEST' mode. False otherwise",
            defaultValue = "None",
            noneable = true,
            positional = false),
        @Param(
            name = CHECK_LAST_REV_STATE,
            named = true,
            type = Boolean.class,
            doc =
                "If set to true, Copybara will validate that the destination didn't change"
                    + " since last-rev import for destination_files. Note that this"
                    + " flag doesn't work for CHANGE_REQUEST mode.",
            defaultValue = "None",
            noneable = true,
            positional = false),
        @Param(
            name = "ask_for_confirmation",
            named = true,
            type = Boolean.class,
            doc =
                "Indicates that the tool should show the diff and require user's"
                    + " confirmation before making a change in the destination.",
            defaultValue = "False",
            positional = false),
        @Param(
            name = "dry_run",
            named = true,
            type = Boolean.class,
            doc =
                "Run the migration in dry-run mode. Some destination implementations might"
                    + " have some side effects (like creating a code review), but never submit to a"
                    + " main branch.",
            defaultValue = "False",
            positional = false),
        @Param(
            name = "after_migration",
            named = true,
            type = SkylarkList.class,
            doc =
                "Run a feedback workflow after one migration happens. This runs once per"
                    + " change in `ITERATIVE` mode and only once for `SQUASH`.",
            defaultValue = "[]",
            positional = false),
        @Param(
            name = "after_workflow",
            named = true,
            type = SkylarkList.class,
            doc =
                "Run a feedback workflow after all the changes for this workflow run are migrated."
                    + " Prefer `after_migration` as it is executed per change (in ITERATIVE mode)."
                    + " Tasks in this hook shouldn't be critical to execute. These actions"
                    + " shouldn't record effects (They'll be ignored).",
            defaultValue = "[]",
            positional = false),
        @Param(
            name = "change_identity",
            named = true,
            type = String.class,
            doc =
                "By default, Copybara hashes several fields so that each change has an unique"
                    + " identifier that at the same time reuses the generated destination change."
                    + " This allows to customize the identity hash generation so that the same"
                    + " identity is used in several workflows. At least ${copybara_config_path}"
                    + " has to be present. Current user is added to the hash"
                    + " automatically.<br><br>Available variables:<ul> "
                    + " <li>${copybara_config_path}: Main config file path</li> "
                    + " <li>${copybara_workflow_name}: The name of the workflow being run</li> "
                    + " <li>${copybara_reference}: The requested reference. In general Copybara"
                    + " tries its best to give a repetable reference. For example Gerrit change"
                    + " number or change-id or GitHub Pull Request number. If it cannot find a"
                    + " context reference it uses the resolved revision.</li> "
                    + " <li>${label:label_name}: A label present for the current change. Exposed"
                    + " in the message or not.</li></ul>If any of the labels cannot be found it"
                    + " defaults to the default identity (The effect would be no reuse of"
                    + " destination change between workflows)",
            defaultValue = "None",
            noneable = true,
            positional = false),
        @Param(
            name = "set_rev_id",
            named = true,
            type = Boolean.class,
            doc =
                "Copybara adds labels like 'GitOrigin-RevId' in the destination in order to"
                    + " track what was the latest change imported. For certain workflows like"
                    + " `CHANGE_REQUEST` it not used and is purely informational. This field allows"
                    + " to disable it for that mode. Destinations might ignore the flag.",
            defaultValue = "True",
            positional = false),
        @Param(
            name = "smart_prune",
            named = true,
            type = Boolean.class,
            doc =
                "By default CHANGE_REQUEST workflows cannot restore scrubbed files. This flag does"
                    + " a best-effort approach in restoring the non-affected snippets. For now we"
                    + " only revert the non-affected files. This only works for CHANGE_REQUEST"
                    + " mode.",
            defaultValue = "False",
            positional = false),
        @Param(
            name = "migrate_noop_changes",
            named = true,
            type = Boolean.class,
            doc =
                "By default, Copybara tries to only migrate changes that affect origin_files or"
                    + " config files. This flag allows to include all the changes. Note that it"
                    + " might generate more empty changes errors. In `ITERATIVE` mode it might"
                    + " fail if some transformation is validating the message (Like has to contain"
                    + " 'PUBLIC' and the change doesn't contain it because it is internal).",
            defaultValue = "False",
            positional = false),
        @Param(
            name = "experimental_custom_rev_id",
            named = true,
            type = String.class,
            doc =
                "Use this label name instead of the one provided by the origin. This is subject"
                    + " to change and there is no guarantee.",
            defaultValue = "None",
            positional = false,
            noneable = true),
        @Param(
            name = "description",
            type = String.class,
            named = true,
            noneable = true,
            positional = false,
            doc = "A description of what this workflow achieves",
            defaultValue = "None"),
        @Param(
            name = "checkout",
            type = Boolean.class,
            named = true,
            positional = false,
            doc =
                "Allows disabling the checkout. The usage of this feature is rare. This could"
                    + " be used to update a file of your own repo when a dependant repo version"
                    + " changes and you are not interested on the files of the dependant repo, just"
                    + " the new version.",
            defaultValue = "True"),
      },
      useLocation = true,
      useStarlarkThread = true)
  @UsesFlags({WorkflowOptions.class})
  @DocDefault(field = "origin_files", value = "glob([\"**\"])")
  @DocDefault(field = "destination_files", value = "glob([\"**\"])")
  @DocDefault(field = CHECK_LAST_REV_STATE, value = "True for CHANGE_REQUEST")
  @DocDefault(field = "reversible_check", value = "True for 'CHANGE_REQUEST' mode. False otherwise")
  public void workflow(
      String workflowName,
      Origin<?> origin, // <Revision>
      Destination<?> destination,
      Authoring authoring,
      SkylarkList<?> transformations,
      Object originFiles,
      Object destinationFiles,
      String modeStr,
      Object reversibleCheckObj,
      Object checkLastRevStateField,
      Boolean askForConfirmation,
      Boolean dryRunMode,
      SkylarkList<?> afterMigrations,
      SkylarkList<?> afterAllMigrations,
      Object changeIdentityObj,
      Boolean setRevId,
      Boolean smartPrune,
      Boolean migrateNoopChanges,
      Object customRevIdField,
      Object description,
      Boolean checkout,
      Location location,
      StarlarkThread thread)
      throws EvalException {
    WorkflowMode mode = stringToEnum(location, "mode", modeStr, WorkflowMode.class);

    Sequence sequenceTransform =
        Sequence.fromConfig(
            generalOptions.profiler(),
            workflowOptions.joinTransformations(),
            transformations,
            "transformations",
            dynamicStarlarkThread,
            debugOptions::transformWrapper);
    Transformation reverseTransform = null;
    if (!generalOptions.isDisableReversibleCheck()
        && convertFromNoneable(reversibleCheckObj, mode == WorkflowMode.CHANGE_REQUEST)) {
      try {
        reverseTransform = sequenceTransform.reverse();
      } catch (NonReversibleValidationException e) {
        throw new EvalException(location, e.getMessage());
      }
    }

    ImmutableList<Token> changeIdentity = getChangeIdentity(changeIdentityObj, location);

    String customRevId = convertFromNoneable(customRevIdField, null);
    check(location,
        customRevId == null || CUSTOM_REVID_FORMAT.matches(customRevId),
        "Invalid experimental_custom_rev_id format. Format: %s", CUSTOM_REVID_FORMAT.pattern());

    if (setRevId) {
      check(location, mode != WorkflowMode.CHANGE_REQUEST || customRevId == null,
          "experimental_custom_rev_id is not allowed to be used in CHANGE_REQUEST mode if"
              + " set_rev_id is set to true. experimental_custom_rev_id is used for looking"
              + " for the baseline in the origin. No revId is stored in the destination.");
    } else {
      check(location, mode == WorkflowMode.CHANGE_REQUEST, "'set_rev_id = False' is only supported"
          + " for CHANGE_REQUEST mode.");
    }
    if (smartPrune) {
      check(location, mode == WorkflowMode.CHANGE_REQUEST, "'smart_prune = True' is only supported"
          + " for CHANGE_REQUEST mode.");
    }

    boolean checkLastRevState = convertFromNoneable(checkLastRevStateField, false);
    if (checkLastRevState) {
      check(location, mode != WorkflowMode.CHANGE_REQUEST,
          "%s is not compatible with %s", CHECK_LAST_REV_STATE, WorkflowMode.CHANGE_REQUEST);
    }

    Authoring resolvedAuthoring = authoring;
    Author defaultAuthorFlag = workflowOptions.getDefaultAuthorFlag();
    if (defaultAuthorFlag != null) {
      resolvedAuthoring = new Authoring(defaultAuthorFlag, authoring.getMode(),
          authoring.getWhitelist());
    }

    WorkflowMode effectiveMode = generalOptions.squash ? WorkflowMode.SQUASH : mode;
    Workflow<Revision, ?> workflow =
        new Workflow<>(
            workflowName,
            convertFromNoneable(description, null),
            (Origin<Revision>) origin,
            destination,
            resolvedAuthoring,
            sequenceTransform,
            workflowOptions.getLastRevision(),
            workflowOptions.isInitHistory(),
            generalOptions,
            convertFromNoneable(originFiles, Glob.ALL_FILES),
            convertFromNoneable(destinationFiles, Glob.ALL_FILES),
            effectiveMode,
            workflowOptions,
            reverseTransform,
            askForConfirmation,
            mainConfigFile,
            allConfigFiles,
            dryRunMode,
            checkLastRevState || workflowOptions.checkLastRevState,
            convertFeedbackActions(afterMigrations, location, dynamicStarlarkThread),
            convertFeedbackActions(afterAllMigrations, location, dynamicStarlarkThread),
            changeIdentity,
            setRevId,
            smartPrune,
            workflowOptions.migrateNoopChanges || migrateNoopChanges,
            customRevId,
            checkout);
    registerGlobalMigration(workflowName, workflow, location, thread);
  }

  private static ImmutableList<Token> getChangeIdentity(Object changeIdentityObj, Location location)
      throws EvalException {
    String changeIdentity = convertFromNoneable(changeIdentityObj, null);

    if (changeIdentity == null) {
      return ImmutableList.of();
    }
    ImmutableList<Token> result = new Parser(location).parse(changeIdentity);
    boolean configVarFound = false;
    for (Token token : result) {
      if (token.getType() != TokenType.INTERPOLATION) {
        continue;
      }
      if (token.getValue().equals(COPYBARA_CONFIG_PATH_IDENTITY_VAR)) {
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
    check(
        location, configVarFound, "${%s} variable is required", COPYBARA_CONFIG_PATH_IDENTITY_VAR);
    return result;
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "move",

      doc = "Moves files between directories and renames files",
      parameters = {
          @Param(name = "before", named = true, type = String.class, doc = ""
              + "The name of the file or directory before moving. If this is the empty"
              + " string and 'after' is a directory, then all files in the workdir will be moved to"
              + " the sub directory specified by 'after', maintaining the directory tree."),
          @Param(name = "after", named = true, type = String.class, doc = ""
              + "The name of the file or directory after moving. If this is the empty"
              + " string and 'before' is a directory, then all files in 'before' will be moved to"
              + " the repo root, maintaining the directory tree inside 'before'."),
          @Param(name = "paths", named = true, type = Glob.class,
              doc = "A glob expression relative to 'before' if it represents a directory."
                  + " Only files matching the expression will be moved. For example,"
                  + " glob([\"**.java\"]), matches all java files recursively inside"
                  + " 'before' folder. Defaults to match all the files recursively.",
              defaultValue = "None", noneable = true),
          @Param(name = "overwrite", named = true,
              doc = "Overwrite destination files if they already exist. Note that this makes the"
                  + " transformation non-reversible, since there is no way to know if the file"
                  + " was overwritten or not in the reverse workflow.",
              type = Boolean.class, defaultValue = "False")
      },
      useLocation = true)
  @DocDefault(field = "paths", value = "glob([\"**\"])")
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
  public Transformation move(String before, String after, Object paths, Boolean overwrite,
      Location location) throws EvalException {

    check(location, !Objects.equals(before, after),
        "Moving from the same folder to the same folder is a noop. Remove the"
            + " transformation.");

    return CopyOrMove.createMove(before, after, workflowOptions,
        convertFromNoneable(paths, Glob.ALL_FILES), overwrite, location);
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "copy",

      doc = "Copy files between directories and renames files",
      parameters = {
          @Param(name = "before", named = true, type = String.class, doc = ""
              + "The name of the file or directory to copy. If this is the empty"
              + " string and 'after' is a directory, then all files in the workdir will be copied"
              + " to the sub directory specified by 'after', maintaining the directory tree."),
          @Param(name = "after", named = true, type = String.class, doc = ""
              + "The name of the file or directory destination. If this is the empty"
              + " string and 'before' is a directory, then all files in 'before' will be copied to"
              + " the repo root, maintaining the directory tree inside 'before'."),
          @Param(name = "paths", named = true, type = Glob.class,
              doc = "A glob expression relative to 'before' if it represents a directory."
                  + " Only files matching the expression will be copied. For example,"
                  + " glob([\"**.java\"]), matches all java files recursively inside"
                  + " 'before' folder. Defaults to match all the files recursively.",
              defaultValue = "None", noneable = true),
          @Param(name = "overwrite", named = true,
              doc = "Overwrite destination files if they already exist. Note that this makes the"
                  + " transformation non-reversible, since there is no way to know if the file"
                  + " was overwritten or not in the reverse workflow.",
              type = Boolean.class, defaultValue = "False")
      },
      useLocation = true)
  @DocDefault(field = "paths", value = "glob([\"**\"])")
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
  public Transformation copy(String before, String after, Object paths, Boolean overwrite,
      Location location) throws EvalException {
    check(location, !Objects.equals(before, after),
        "Copying from the same folder to the same folder is a noop. Remove the"
            + " transformation.");
    return CopyOrMove.createCopy(before, after, workflowOptions,
        convertFromNoneable(paths, Glob.ALL_FILES), overwrite, location);
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "remove",

      doc = "Remove files from the workdir. **This transformation is only meant to be used inside"
          + " core.transform for reversing core.copy like transforms**. For regular file filtering"
          + " use origin_files exclude mechanism.",
      parameters = {
          @Param(name = "paths", named = true, type = Glob.class, doc = "The files to be deleted"),
      },
      useLocation = true)
  @Example(title = "Reverse a file copy",
      before = "Move all the files in a directory to another directory:",
      code = "core.transform(\n"
          + "    [core.copy(\"foo\", \"foo/public\")],\n"
          + "    reversal = [core.remove(glob([\"foo/public/**\"]))])",
      after = "In this example, `foo/one` will be moved to `foo/public/one`.")
  @Example(title = "Copy with reversal",
      before = "Copy all static files to a 'static' folder and use remove for reverting the change",
      code = ""
          + "core.transform(\n"
          + "    [core.copy(\"foo\", \"foo/static\", paths = glob([\"**.css\",\"**.html\", ]))],\n"
          + "    reversal = [core.remove(glob(['foo/static/**.css', 'foo/static/**.html']))]\n"
          + ")")
  public Remove remove(Glob paths, Location location) {
    return new Remove(paths, workflowOptions, location);
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "replace",
      doc =
          "Replace a text with another text using optional regex groups. This tranformer can be"
              + " automatically reversed.",
      parameters = {
        @Param(
            name = "before",
            named = true,
            type = String.class,
            doc =
                "The text before the transformation. Can contain references to regex groups. For"
                    + " example \"foo${x}text\".<p>`before` can only contain 1 reference to each"
                    + " unique `regex_group`. If you require multiple references to the same"
                    + " `regex_group`, add `repeated_groups: True`.<p>If '$' literal character"
                    + " needs to be matched, '`$$`' should be used. For example '`$$FOO`' would"
                    + " match the literal '$FOO'."),
        @Param(
            name = "after",
            named = true,
            type = String.class,
            doc =
                "The text after the transformation. It can also contain references to regex "
                    + "groups, like 'before' field."),
        @Param(
            name = "regex_groups",
            named = true,
            type = SkylarkDict.class,
            doc =
                "A set of named regexes that can be used to match part of the replaced text."
                    + "Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax."
                    + " For example {\"x\": \"[A-Za-z]+\"}",
            defaultValue = "{}"),
        @Param(
            name = "paths",
            named = true,
            type = Glob.class,
            doc =
                "A glob expression relative to the workdir representing the files to apply the"
                    + " transformation. For example, glob([\"**.java\"]), matches all java files"
                    + " recursively. Defaults to match all the files recursively.",
            defaultValue = "None",
            noneable = true),
        @Param(
            name = "first_only",
            named = true,
            type = Boolean.class,
            doc =
                "If true, only replaces the first instance rather than all. In single line mode,"
                    + " replaces the first instance on each line. In multiline mode, replaces the"
                    + " first instance in each file.",
            defaultValue = "False"),
        @Param(
            name = "multiline",
            named = true,
            type = Boolean.class,
            doc = "Whether to replace text that spans more than one line.",
            defaultValue = "False"),
        @Param(
            name = "repeated_groups",
            named = true,
            type = Boolean.class,
            doc =
                "Allow to use a group multiple times. For example foo${repeated}/${repeated}. Note"
                    + " that this mechanism doesn't use backtracking. In other words, the group"
                    + " instances are treated as different groups in regex construction and then a"
                    + " validation is done after that.",
            defaultValue = "False"),
        @Param(
            name = "ignore",
            named = true,
            type = SkylarkList.class,
            doc =
                "A set of regexes. Any text that matches any expression in this set, which"
                    + " might otherwise be transformed, will be ignored.",
            defaultValue = "[]"),
      },
      useLocation = true)
  @DocDefault(field = "paths", value = "glob([\"**\"])")
  @Example(
      title = "Simple replacement",
      before = "Replaces the text \"internal\" with \"external\" in all java files",
      code =
          "core.replace(\n"
              + "    before = \"internal\",\n"
              + "    after = \"external\",\n"
              + "    paths = glob([\"**.java\"]),\n"
              + ")")
  @Example(
      title = "Append some text at the end of files",
      before = "",
      code =
          "core.replace(\n"
              + "   before = '${end}',\n"
              + "   after  = 'Text to be added at the end',\n"
              + "   multiline = True,\n"
              + "   regex_groups = { 'end' : '\\z'},\n"
              + ")")
  @Example(
      title = "Append some text at the end of files reversible",
      before = "Same as the above example but make the transformation reversible",
      code =
          "core.transform([\n"
              + "    core.replace(\n"
              + "       before = '${end}',\n"
              + "       after  = 'some append',\n"
              + "       multiline = True,\n"
              + "       regex_groups = { 'end' : '\\z'},\n"
              + "    )\n"
              + "],\n"
              + "reversal = [\n"
              + "    core.replace(\n"
              + "       before = 'some append${end}',\n"
              + "       after = '',\n"
              + "       multiline = True,\n"
              + "       regex_groups = { 'end' : '\\z'},\n"
              + "    )"
              + "])")
  @Example(
      title = "Replace using regex groups",
      before =
          "In this example we map some urls from the internal to the external version in"
              + " all the files of the project.",
      code =
          "core.replace(\n"
              + "        before = \"https://some_internal/url/${pkg}.html\",\n"
              + "        after = \"https://example.com/${pkg}.html\",\n"
              + "        regex_groups = {\n"
              + "            \"pkg\": \".*\",\n"
              + "        },\n"
              + "    )",
      after =
          "So a url like `https://some_internal/url/foo/bar.html` will be transformed to"
              + " `https://example.com/foo/bar.html`.")
  @Example(
      title = "Remove confidential blocks",
      before =
          "This example removes blocks of text/code that are confidential and thus shouldn't"
              + "be exported to a public repository.",
      code =
          "core.replace(\n"
              + "        before = \"${x}\",\n"
              + "        after = \"\",\n"
              + "        multiline = True,\n"
              + "        regex_groups = {\n"
              + "            \"x\": \"(?m)^.*BEGIN-INTERNAL[\\\\w\\\\W]*?END-INTERNAL.*$\\\\n\",\n"
              + "        },\n"
              + "    )",
      after =
          "This replace would transform a text file like:\n\n"
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
  public Replace replace(
      String before,
      String after,
      SkylarkDict<?, ?> regexes, // <String, String>
      Object paths,
      Boolean firstOnly,
      Boolean multiline,
      Boolean repeatedGroups,
      SkylarkList<?> ignore, // <String>
      Location location)
      throws EvalException {
    return Replace.create(
        location,
        before,
        after,
        SkylarkUtil.convertStringMap(regexes, "regex_groups"),
        convertFromNoneable(paths, Glob.ALL_FILES),
        firstOnly,
        multiline,
        repeatedGroups,
        SkylarkUtil.convertStringList(ignore, "patterns_to_ignore"),
        workflowOptions);
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "todo_replace",
      doc = "Replace Google style TODOs. For example `TODO(username, othername)`.",
      parameters = {
        @Param(
            name = "tags",
            named = true,
            type = SkylarkList.class,
            generic1 = String.class,
            doc = "Prefix tag to look for",
            defaultValue = "['TODO', 'NOTE']"),
        @Param(
            name = "mapping",
            named = true,
            type = SkylarkDict.class,
            doc = "Mapping of users/strings",
            defaultValue = "{}"),
        @Param(
            name = "mode",
            named = true,
            type = String.class,
            doc =
                "Mode for the replace:<ul><li>'MAP_OR_FAIL': Try to use the mapping and if not"
                    + " found fail.</li><li>'MAP_OR_IGNORE': Try to use the mapping but ignore if"
                    + " no mapping found.</li><li>'MAP_OR_DEFAULT': Try to use the mapping and use"
                    + " the default if not found.</li><li>'SCRUB_NAMES': Scrub all names from"
                    + " TODOs. Transforms 'TODO(foo)' to 'TODO'</li><li>'USE_DEFAULT': Replace any"
                    + " TODO(foo, bar) with TODO(default_string)</li></ul>",
            defaultValue = "'MAP_OR_IGNORE'"),
        @Param(
            name = "paths",
            named = true,
            type = Glob.class,
            doc =
                "A glob expression relative to the workdir representing the files to apply the"
                    + " transformation. For example, glob([\"**.java\"]), matches all java files"
                    + " recursively. Defaults to match all the files recursively.",
            defaultValue = "None",
            noneable = true),
        @Param(
            name = "default",
            named = true,
            type = String.class,
            doc =
                "Default value if mapping not found. Only valid for 'MAP_OR_DEFAULT' or"
                    + " 'USE_DEFAULT' modes",
            noneable = true,
            defaultValue = "None"),
      },
      useLocation = true)
  @DocDefault(field = "paths", value = "glob([\"**\"])")
  @Example(
      title = "Simple update",
      before = "Replace TODOs and NOTES for users in the mapping:",
      code =
          "core.todo_replace(\n"
              + "  mapping = {\n"
              + "    'test1' : 'external1',\n"
              + "    'test2' : 'external2'\n"
              + "  }\n"
              + ")",
      after =
          "Would replace texts like TODO(test1) or NOTE(test1, test2) with TODO(external1)"
              + " or NOTE(external1, external2)")
  @Example(
      title = "Scrubbing",
      before = "Remove text from inside TODOs",
      code = "core.todo_replace(\n" + "  mode = 'SCRUB_NAMES'\n" + ")",
      after =
          "Would replace texts like TODO(test1): foo or NOTE(test1, test2):foo with TODO:foo"
              + " and NOTE:foo")
  public TodoReplace todoReplace(
      SkylarkList<?> skyTags, // <String>
      SkylarkDict<?, ?> skyMapping, // <String, String>
      String modeStr,
      Object paths,
      Object skyDefault,
      Location location)
      throws EvalException {
    Mode mode = stringToEnum(location, "mode", modeStr, Mode.class);
    Map<String, String> mapping = SkylarkUtil.convertStringMap(skyMapping, "mapping");
    String defaultString = convertFromNoneable(skyDefault, /*defaultValue=*/null);
    ImmutableList<String> tags =
        ImmutableList.copyOf(SkylarkUtil.convertStringList(skyTags, "tags"));

    check(location, !tags.isEmpty(), "'tags' cannot be empty");
    if (mode == Mode.MAP_OR_DEFAULT || mode == Mode.USE_DEFAULT) {
      check(location, defaultString != null, "'default' needs to be set for mode '%s'", mode);
    } else {
      check(location, defaultString == null, "'default' cannot be used for mode '%s'", mode);
    }
    if (mode == Mode.USE_DEFAULT || mode == Mode.SCRUB_NAMES) {
      check(location, mapping.isEmpty(), "'mapping' cannot be used with mode %s", mode);
    }
    return new TodoReplace(location, convertFromNoneable(paths, Glob.ALL_FILES), tags, mode,
        mapping, defaultString, workflowOptions.parallelizer());
  }

  public static final String TODO_FILTER_REPLACE_EXAMPLE = ""
      + "core.filter_replace(\n"
      + "    regex = 'TODO\\((.*?)\\)',\n"
      + "    group = 1,\n"
      + "        mapping = core.replace_mapper([\n"
      + "            core.replace(\n"
      + "                before = '${p}foo${s}',\n"
      + "                after = '${p}fooz${s}',\n"
      + "                regex_groups = { 'p': '.*', 's': '.*'}\n"
      + "            ),\n"
      + "            core.replace(\n"
      + "                before = '${p}baz${s}',\n"
      + "                after = '${p}bazz${s}',\n"
      + "                regex_groups = { 'p': '.*', 's': '.*'}\n"
      + "            ),\n"
      + "        ],\n"
      + "        all = True\n"
      + "    )\n"
      + ")\n";

  public static final String SIMPLE_FILTER_REPLACE_EXAMPLE = ""
      + "core.filter_replace(\n"
      + "    regex = 'a.*',\n"
      + "    mapping = {\n"
      + "        'afoo': 'abar',\n"
      + "        'abaz': 'abam'\n"
      + "    }\n"
      + ")\n";

  @SuppressWarnings({"unused", "unchecked"})
  @SkylarkCallable(
      name = "filter_replace",

      doc = "Applies an initial filtering to find a substring to be replaced and then applies"
          + "a `mapping` of replaces for the matched text.",
      parameters = {
          @Param(name = "regex", named = true, type = String.class,
              doc = "A re2 regex to match a substring of the file"),
          @Param(name = "mapping", named = true,
              doc = "A mapping function like core.replace_mapper or a dict with mapping values.",
              defaultValue = "{}"),
          @Param(name = "group", named = true, type = Integer.class,
              doc = "Extract a regex group from the matching text and pass this as parameter to"
                  + " the mapping instead of the whole matching text.",
              noneable = true, defaultValue = "None"),
          @Param(name = "paths", named = true, type = Glob.class,
              doc = "A glob expression relative to the workdir representing the files to apply"
                  + " the transformation. For example, glob([\"**.java\"]), matches all java files"
                  + " recursively. Defaults to match all the files recursively.",
              defaultValue = "None", noneable = true),
          @Param(name = "reverse", named = true, type = String.class,
              doc = "A re2 regex used as reverse transformation",
              defaultValue = "None", noneable = true),
      },
      useLocation = true)
  @DocDefault(field = "paths", value = "glob([\"**\"])")
  @DocDefault(field = "reverse", value = "`regex`")
  @DocDefault(field = "group", value = "Whole text")
  @Example(title = "Simple replace with mapping",
      before = "Simplest mapping",
      code = SIMPLE_FILTER_REPLACE_EXAMPLE)
  @Example(title = "TODO replace",
      before = "This replace is similar to what it can be achieved with core.todo_replace:",
      code = TODO_FILTER_REPLACE_EXAMPLE)
  public FilterReplace filterReplace(String regex,
      Object mapping, Object group, Object paths, Object reverse, Location location)
      throws EvalException {
    ReversibleFunction<String, String> func = getMappingFunction(mapping, location);

    String afterPattern = convertFromNoneable(reverse, regex);
    int numGroup = convertFromNoneable(group, 0);
    Pattern before = Pattern.compile(regex);
    check(location, numGroup <= before.groupCount(),
        "group idx is greater than the number of groups defined in '%s'. Regex has %s groups",
        before.pattern(), before.groupCount());
    Pattern after = Pattern.compile(afterPattern);
    check(location, numGroup <= after.groupCount(),
        "reverse_group idx is greater than the number of groups defined in '%s'."
            + " Regex has %s groups",
        after.pattern(), after.groupCount());
    return new FilterReplace(
        workflowOptions,
        before,
        after,
        numGroup,
        numGroup,
        func,
        convertFromNoneable(paths, Glob.ALL_FILES), location);
  }

  @SuppressWarnings("unchecked")
  public static ReversibleFunction<String, String> getMappingFunction(Object mapping,
      Location location)
      throws EvalException {
    if (mapping instanceof SkylarkDict) {
      ImmutableMap<String, String> map = ImmutableMap.copyOf(
          SkylarkDict.castSkylarkDictOrNoneToDict(mapping, String.class, String.class, "mapping"));
      check(location, !map.isEmpty(), "Empty mapping is not allowed."
          + " Remove the transformation instead");
      return new MapMapper(map, location);
    }
    check(location, mapping instanceof ReversibleFunction, "mapping has to be instance of"
        + " map or a reversible function");
    return  (ReversibleFunction<String, String>) mapping;
  }

  @SkylarkCallable(
      name = "replace_mapper",
      doc =
          "A mapping function that applies a list of replaces until one replaces the text"
              + " (Unless `all = True` is used). This should be used with core.filter_replace or"
              + " other transformations that accept text mapping as parameter.",
      parameters = {
        @Param(
            name = "mapping",
            type = SkylarkList.class,
            generic1 = Transformation.class,
            named = true,
            doc = "The list of core.replace transformations"),
        @Param(
            name = "all",
            type = Boolean.class,
            named = true,
            positional = false,
            doc = "Run all the mappings despite a replace happens.",
            defaultValue = "False"),
      },
      useLocation = true)
  public ReplaceMapper mapImports(
      SkylarkList<?> mapping, // <Transformation>
      Boolean all,
      Location location)
      throws EvalException {
    check(location, !mapping.isEmpty(), "Empty mapping is not allowed");
    ImmutableList.Builder<Replace> replaces = ImmutableList.builder();
    for (Transformation t : mapping.getContents(Transformation.class, "mapping")) {
      check(location, t instanceof Replace,
          "Only core.replace can be used as mapping, but got: " + t.describe());
      Replace replace = (Replace) t;
      check(location, replace.getPaths().equals(Glob.ALL_FILES), "core.replace cannot use"
          + " 'paths' inside core.replace_mapper");
      replaces.add(replace);
    }
    return new ReplaceMapper(replaces.build(), all);
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "verify_match",

      doc = "Verifies that a RegEx matches (or not matches) the specified files. Does not"
          + " transform anything, but will stop the workflow if it fails.",
      parameters = {
          @Param(name = "regex", named = true, type = String.class,
              doc = "The regex pattern to verify. To satisfy the validation, there has to be at"
                  + "least one (or no matches if verify_no_match) match in each of the files "
                  + "included in paths. The re2j pattern will be applied in multiline mode, i.e."
                  + " '^' refers to the beginning of a file and '$' to its end. "
                  + "Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax."),
          @Param(name = "paths", named = true, type = Glob.class,
              doc = "A glob expression relative to the workdir representing the files to apply"
                  + " the transformation. For example, glob([\"**.java\"]), matches all java files"
                  + " recursively. Defaults to match all the files recursively.",
              defaultValue = "None", noneable = true),
          @Param(name = "verify_no_match", named = true, type = Boolean.class,
              doc = "If true, the transformation will verify that the RegEx does not match.",
              defaultValue = "False"),
      }, useLocation = true)
  @DocDefault(field = "paths", value = "glob([\"**\"])")
  public VerifyMatch verifyMatch(String regex, Object paths, Boolean verifyNoMatch,
      Location location)
      throws EvalException {
    return VerifyMatch.create(location,
        regex,
        convertFromNoneable(paths, Glob.ALL_FILES),
        verifyNoMatch,
        workflowOptions.parallelizer());
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "transform",
      doc =
          "Groups some transformations in a transformation that can contain a particular,"
              + " manually-specified, reversal, where the forward version and reversed version"
              + " of the transform are represented as lists of transforms. The is useful if a"
              + " transformation does not automatically reverse, or if the automatic reversal"
              + " does not work for some reason."
              + "<br>"
              + "If reversal is not provided, the transform will try to compute the reverse of"
              + " the transformations list.",
      parameters = {
        @Param(
            name = "transformations",
            type = SkylarkList.class,
            generic1 = Transformation.class,
            named = true,
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
            defaultValue = "None"),
        @Param(
            name = "ignore_noop",
            type = Boolean.class,
            doc =
                "In case a noop error happens in the group of transformations (Both forward and"
                    + " reverse), it will be ignored, but the rest of the transformations in the"
                    + " group will still be executed. If ignore_noop is not set,"
                    + " we will apply the closest parent's ignore_noop.",
            named = true,
            positional = false,
            noneable = true,
            defaultValue = "None"),
      },
      useLocation = true,
      useStarlarkThread = true)
  @DocDefault(field = "reversal", value = "The reverse of 'transformations'")
  public Transformation transform(
      SkylarkList<?> transformations, // <Transformation>
      Object reversal,
      Object ignoreNoop,
      Location location,
      StarlarkThread thread)
      throws EvalException {
    Sequence forward =
        Sequence.fromConfig(
            generalOptions.profiler(),
            workflowOptions.joinTransformations(),
            transformations,
            "transformations",
            dynamicStarlarkThread,
            debugOptions::transformWrapper);
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
            generalOptions.profiler(),
            workflowOptions.joinTransformations(),
            reverseList,
            "reversal",
            dynamicStarlarkThread,
            debugOptions::transformWrapper);
    return new ExplicitReversal(
        forward, reverse, updatedIgnoreNoop);
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "dynamic_transform",
      doc =
          "Create a dynamic Skylark transformation. This should only be used by libraries"
              + " developers",
      parameters = {
        @Param(
            name = "impl",
            named = true,
            type = BaseFunction.class,
            doc = "The Skylark function to call"),
        @Param(
            name = "params",
            named = true,
            type = SkylarkDict.class,
            doc = "The parameters to the function. Will be available under ctx.params",
            defaultValue = "{}"),
      },
      useStarlarkThread = true)
  @Example(
      title = "Create a dynamic transformation with parameter",
      before =
          "If you want to create a library that uses dynamic transformations, you probably want to"
              + " make them customizable. In order to do that, in your library.bara.sky, you need"
              + " to hide the dynamic transformation (prefix with '_' and instead expose a"
              + " function that creates the dynamic transformation with the param:",
      code =
          ""
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
      after =
          "After defining this function, you can use `test('example', 42)` as a transformation"
              + " in `core.workflow`.")
  public Transformation dynamic_transform(
      BaseFunction impl, SkylarkDict<?, ?> params, StarlarkThread thread) {
    return new SkylarkTransformation(
        impl, SkylarkDict.<Object, Object>copyOf(thread, params), dynamicStarlarkThread);
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "dynamic_feedback",
      doc =
          "Create a dynamic Skylark feedback migration. This should only be used by libraries"
              + " developers",
      parameters = {
        @Param(
            name = "impl",
            named = true,
            type = BaseFunction.class,
            doc = "The Skylark function to call"),
        @Param(
            name = "params",
            named = true,
            type = SkylarkDict.class,
            doc = "The parameters to the function. Will be available under ctx.params",
            defaultValue = "{}"),
      },
      useStarlarkThread = true)
  public Action dynamicFeedback(
      BaseFunction impl, SkylarkDict<?, ?> params, StarlarkThread thread) {
    return new SkylarkAction(
        impl, SkylarkDict.<Object, Object>copyOf(thread, params), dynamicStarlarkThread);
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "fail_with_noop",

      doc = "If invoked, it will fail the current migration as a noop",
      parameters = {
          @Param(name = "msg", named = true, type = String.class, doc = "The noop message"),
      },
      useLocation = true)
  public Action failWithNoop(String msg, Location location) throws EmptyChangeException {
    // Add an internal EvalException to know the location of the error.
    throw new EmptyChangeException(new EvalException(location, msg), msg);
  }

  @SkylarkCallable(name = "main_config_path",
      doc = "Location of the config file. This is subject to change",
      structField = true)
  public String getMainConfigFile() {
    return mainConfigFile.getIdentifier();
  }

  @SuppressWarnings("unused")
  @SkylarkCallable(
      name = "feedback",
      doc =
          "Defines a migration of changes' metadata, that can be invoked via the Copybara command"
              + " in the same way as a regular workflow migrates the change itself.\n"
              + "\n"
              + "It is considered change metadata any information associated with a change"
              + " (pending or submitted) that is not core to the change itself. A few examples:\n"
              + "<ul>\n"
              + "  <li> Comments: Present in any code review system. Examples: Github PRs or"
              + " Gerrit     code reviews.</li>\n"
              + "  <li> Labels: Used in code review systems for approvals and/or CI results.    "
              + " Examples: Github labels, Gerrit code review labels.</li>\n"
              + "</ul>\n"
              + "For the purpose of this workflow, it is not considered metadata the commit"
              + " message in Git, or any of the contents of the file tree.\n"
              + "\n",
      parameters = {
        @Param(
            name = "name",
            type = String.class,
            doc = "The name of the feedback workflow.",
            positional = false,
            named = true),
        @Param(
            name = "origin",
            type = Trigger.class,
            doc = "The trigger of a feedback migration.",
            positional = false,
            named = true),
        @Param(
            name = "destination",
            type = Endpoint.class,
            doc =
                "Where to write change metadata to. This is usually a code review system like "
                    + "Gerrit or GitHub PR.",
            positional = false,
            named = true),
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
            positional = false,
            named = true),
        @Param(
            name = "description",
            type = String.class,
            named = true,
            noneable = true,
            positional = false,
            doc = "A description of what this workflow achieves",
            defaultValue = "None"),
      },
      useLocation = true,
      useStarlarkThread = true)
  @UsesFlags({FeedbackOptions.class})
  /*TODO(danielromero): Add default values*/
  public NoneType feedback(
      String workflowName,
      Trigger trigger,
      Endpoint destination,
      SkylarkList<?> feedbackActions,
      Object description,
      Location location,
      StarlarkThread thread)
      throws EvalException {
    ImmutableList<Action> actions =
        convertFeedbackActions(feedbackActions, location, dynamicStarlarkThread);
    Feedback migration =
        new Feedback(
            workflowName,
            convertFromNoneable(description, null),
            mainConfigFile,
            trigger,
            destination,
            actions,
            generalOptions);
    registerGlobalMigration(workflowName, migration, location, thread);
    return Runtime.NONE;
  }

  /** Registers a {@link Migration} in the global registry. */
  protected void registerGlobalMigration(
      String name, Migration migration, Location location, StarlarkThread thread)
      throws EvalException {
    getGlobalMigrations(thread).addMigration(location, name, migration);
  }

  @SkylarkCallable(
      name = "format",
      doc = "Formats a String using Java format patterns.",
      parameters = {
        @Param(name = "format", type = String.class, named = true, doc = "The format string"),
        @Param(
            name = "args",
            type = SkylarkList.class,
            named = true,
            doc = "The arguments to format"),
      },
      useLocation = true)
  public String format(String format, SkylarkList<?> args, Location location) throws EvalException {
    try {
      return String.format(format, args.toArray(new Object[0]));
    } catch (IllegalFormatException e) {
      throw new EvalException(location, "Invalid format: " + format, e);
    }
  }

  private static ImmutableList<Action> convertFeedbackActions(
      SkylarkList<?> feedbackActions, Location location, Supplier<StarlarkThread> thread)
      throws EvalException {
    ImmutableList.Builder<Action> actions = ImmutableList.builder();
    for (Object action : feedbackActions) {
      if (action instanceof BaseFunction) {
        actions.add(new SkylarkAction((BaseFunction) action, SkylarkDict.empty(), thread));
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
  public void setConfigFile(ConfigFile mainConfigFile, ConfigFile currentConfigFile) {
    this.mainConfigFile = mainConfigFile;
  }

  @Override
  public void setAllConfigResources(
      Supplier<ImmutableMap<String, ConfigFile>> allConfigFiles) {
    this.allConfigFiles = allConfigFiles;
  }

  @Override
  public void setDynamicEnvironment(Supplier<StarlarkThread> dynamicStarlarkThread) {
    this.dynamicStarlarkThread = dynamicStarlarkThread;
  }
}
