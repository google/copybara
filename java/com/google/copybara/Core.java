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
import static com.google.copybara.exception.ValidationException.checkCondition;
import static com.google.copybara.transform.Transformations.toTransformation;
import static com.google.copybara.version.LatestVersionSelector.VersionElementType.ALPHABETIC;
import static com.google.copybara.version.LatestVersionSelector.VersionElementType.NUMERIC;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.action.Action;
import com.google.copybara.action.StarlarkAction;
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
import com.google.copybara.exception.NonReversibleValidationException;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.feedback.Feedback;
import com.google.copybara.folder.FolderModule;
import com.google.copybara.revision.Revision;
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
import com.google.copybara.transform.SkylarkConsole;
import com.google.copybara.transform.SkylarkTransformation;
import com.google.copybara.transform.TodoReplace;
import com.google.copybara.transform.TodoReplace.Mode;
import com.google.copybara.transform.VerifyMatch;
import com.google.copybara.transform.debug.DebugOptions;
import com.google.copybara.util.Glob;
import com.google.copybara.version.LatestVersionSelector;
import com.google.copybara.version.LatestVersionSelector.VersionElementType;
import com.google.copybara.version.OrderedVersionSelector;
import com.google.copybara.version.RequestedVersionSelector;
import com.google.copybara.version.VersionSelector;
import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.util.IllegalFormatException;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.Module;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.Starlark;
import net.starlark.java.eval.StarlarkCallable;
import net.starlark.java.eval.StarlarkInt;
import net.starlark.java.eval.StarlarkList;
import net.starlark.java.eval.StarlarkThread;
import net.starlark.java.eval.StarlarkThread.CallStackEntry;
import net.starlark.java.eval.StarlarkValue;

/**
 * Main configuration class for creating migrations.
 *
 * <p>This class is exposed in Skylark configuration as an instance variable called "core". So users
 * can use it as:
 *
 * <pre>
 * core.workspace(
 *   name = "foo",
 *   ...
 * )
 * </pre>
 */
@StarlarkBuiltin(
    name = "core",
    doc = "Core functionality for creating migrations, and basic transformations.")
@UsesFlags({GeneralOptions.class, DebugOptions.class})
public class Core implements LabelsAwareModule, StarlarkValue {

  // Restrict for label ids like 'BAZEL_REV_ID'. More strict than our current revId.
  private static final Pattern CUSTOM_REVID_FORMAT = Pattern.compile("[A-Z][A-Z_0-9]{1,30}_REV_ID");
  private static final String CHECK_LAST_REV_STATE = "check_last_rev_state";
  private final GeneralOptions generalOptions;
  private final WorkflowOptions workflowOptions;
  private final DebugOptions debugOptions;
  private FolderModule folderModule;
  private ConfigFile mainConfigFile;
  private Supplier<ImmutableMap<String, ConfigFile>> allConfigFiles;
  private StarlarkThread.PrintHandler printHandler;
  @Nullable private SkylarkConsole console;

  public Core(
      GeneralOptions generalOptions, WorkflowOptions workflowOptions, DebugOptions debugOptions,
  FolderModule folderModule) {
    this.generalOptions = Preconditions.checkNotNull(generalOptions);
    this.workflowOptions = Preconditions.checkNotNull(workflowOptions);
    this.debugOptions = Preconditions.checkNotNull(debugOptions);
    this.folderModule = Preconditions.checkNotNull(folderModule);
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "reverse",
      doc =
          "Given a list of transformations, returns the list of transformations equivalent to"
              + " undoing all the transformations",
      parameters = {
        @Param(
            name = "transformations",
            named = true,
            allowedTypes = {
              @ParamType(
                  type = net.starlark.java.eval.Sequence.class,
                  generic1 = Transformation.class), // (or callable)
            },
            doc = "The transformations to reverse"),
      })
  public net.starlark.java.eval.Sequence<Transformation> reverse(
      net.starlark.java.eval.Sequence<?> transforms // <Transformation> or <StarlarkCallable>
      ) throws EvalException {

    ImmutableList.Builder<Transformation> builder = ImmutableList.builder();
    for (Object t : transforms) {
      try {
        builder.add(toTransformation(t, "transformations", printHandler).reverse());
      } catch (NonReversibleValidationException e) {
        throw Starlark.errorf("%s", e.getMessage());
      }
    }

    return StarlarkList.immutableCopyOf(builder.build().reverse());
  }

  @SuppressWarnings({"unused", "unchecked"})
  @StarlarkMethod(
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
              + TransformWork.COPYBARA_CURRENT_REV_DATE_TIME
              + ": Date & time for the current reference being migrated in ISO format"
              + " (Example: \"2011-12-03T10:15:30+01:00\")\n"
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
        @Param(name = "name", named = true, doc = "The name of the workflow.", positional = false),
        @Param(
            name = "origin",
            named = true,
            doc =
                "Where to read from the code to be migrated, before applying the "
                    + "transformations. This is usually a VCS like Git, but can also be a local "
                    + "folder or even a pending change in a code review system like Gerrit.",
            positional = false),
        @Param(
            name = "destination",
            named = true,
            doc =
                "Where to write to the code being migrated, after applying the "
                    + "transformations. This is usually a VCS like Git, but can also be a local "
                    + "folder or even a pending change in a code review system like Gerrit.",
            positional = false),
        @Param(
            name = "authoring",
            named = true,
            doc = "The author mapping configuration from origin to destination.",
            positional = false),
        @Param(
            name = "transformations",
            named = true,
            doc = "The transformations to be run for this workflow. They will run in sequence.",
            positional = false,
            defaultValue = "[]"),
        @Param(
            name = "origin_files",
            named = true,
            allowedTypes = {
              @ParamType(type = Glob.class),
              @ParamType(type = NoneType.class),
            },
            doc =
                "A glob relative to the workdir that will be read from the"
                    + " origin during the import. For example glob([\"**.java\"]), all java files,"
                    + " recursively, which excludes all other file types.",
            defaultValue = "None",
            positional = false),
        @Param(
            name = "destination_files",
            named = true,
            allowedTypes = {
              @ParamType(type = Glob.class),
              @ParamType(type = NoneType.class),
            },
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
            positional = false),
        @Param(
            name = "mode",
            named = true,
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
            allowedTypes = {
              @ParamType(type = Boolean.class),
              @ParamType(type = NoneType.class),
            },
            doc =
                "Indicates if the tool should try to to reverse all the transformations"
                    + " at the end to check that they are reversible.<br/>The default value is"
                    + " True for 'CHANGE_REQUEST' mode. False otherwise",
            defaultValue = "None",
            positional = false),
        @Param(
            name = CHECK_LAST_REV_STATE,
            named = true,
            doc =
                "If set to true, Copybara will validate that the destination didn't change"
                    + " since last-rev import for destination_files. Note that this"
                    + " flag doesn't work for CHANGE_REQUEST mode.",
            defaultValue = "False",
            positional = false),
        @Param(
            name = "ask_for_confirmation",
            named = true,
            doc =
                "Indicates that the tool should show the diff and require user's"
                    + " confirmation before making a change in the destination.",
            defaultValue = "False",
            positional = false),
        @Param(
            name = "dry_run",
            named = true,
            doc =
                "Run the migration in dry-run mode. Some destination implementations might"
                    + " have some side effects (like creating a code review), but never submit to a"
                    + " main branch.",
            defaultValue = "False",
            positional = false),
        @Param(
            name = "after_migration",
            named = true,
            doc =
                "Run a feedback workflow after one migration happens. This runs once per"
                    + " change in `ITERATIVE` mode and only once for `SQUASH`.",
            defaultValue = "[]",
            positional = false),
        @Param(
            name = "after_workflow",
            named = true,
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
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
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
            positional = false),
        @Param(
            name = "set_rev_id",
            named = true,
            doc =
                "Copybara adds labels like 'GitOrigin-RevId' in the destination in order to"
                    + " track what was the latest change imported. For `CHANGE_REQUEST` "
                    + "workflows it is not used and is purely informational. This field "
                    + "allows to disable it for that mode. Destinations might ignore the flag.",
            defaultValue = "True",
            positional = false),
        // TODO: deprecate this in favor of merge_import param, which will take enum and bool
        @Param(
            name = "smart_prune",
            named = true,
            doc =
                "By default CHANGE_REQUEST workflows cannot restore scrubbed files. This flag does"
                    + " a best-effort approach in restoring the non-affected snippets. For now we"
                    + " only revert the non-affected files. This only works for CHANGE_REQUEST"
                    + " mode.",
            defaultValue = "False",
            positional = false),
        @Param(
            name = "merge_import",
            named = true,
            doc =
                "A migration mode that shells out to a diffing tool (default is diff3) to merge all"
                    + " files. The inputs to the diffing tool are (1) origin file (2) baseline file"
                    + " (3) destination file. This can be used to perpetuate destination-only"
                    + " changes in non source of truth repositories.",
            defaultValue = "False",
            positional = false),
        @Param(
            name = "autopatch_config",
            doc = "Configuration that describes the setting for automatic patch file generation",
            allowedTypes = {
              @ParamType(type = AutoPatchfileConfiguration.class),
              @ParamType(type = NoneType.class),
            },
            positional = false,
            named = true,
            defaultValue = "None"),
        @Param(
            name = "migrate_noop_changes",
            named = true,
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
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            doc =
                "Use this label name instead of the one provided by the origin. This is subject"
                    + " to change and there is no guarantee.",
            defaultValue = "None",
            positional = false),
        @Param(
            name = "description",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            positional = false,
            doc = "A description of what this workflow achieves",
            defaultValue = "None"),
        @Param(
            name = "checkout",
            named = true,
            positional = false,
            doc =
                "Allows disabling the checkout. The usage of this feature is rare. This could"
                    + " be used to update a file of your own repo when a dependant repo version"
                    + " changes and you are not interested on the files of the dependant repo, just"
                    + " the new version.",
            defaultValue = "True"),
        @Param(
            name = "reversible_check_ignore_files",
            named = true,
            allowedTypes = {
              @ParamType(type = Glob.class),
              @ParamType(type = NoneType.class),
            },
            doc = "Ignore the files matching the glob in the reversible check",
            defaultValue = "None",
            positional = false),
      },
      useStarlarkThread = true)
  @UsesFlags({WorkflowOptions.class})
  @DocDefault(field = "origin_files", value = "glob([\"**\"])")
  @DocDefault(field = "destination_files", value = "glob([\"**\"])")
  @DocDefault(field = "reversible_check", value = "True for 'CHANGE_REQUEST' mode. False otherwise")
  @DocDefault(field = "reversible_check_ignore_files", value = "None")
  public void workflow(
      String workflowName,
      Origin<?> origin, // <Revision>, but skylark allows only ?
      Destination<?> destination,
      Authoring authoring,
      net.starlark.java.eval.Sequence<?> transformations,
      Object originFiles,
      Object destinationFiles,
      String modeStr,
      Object reversibleCheckObj,
      boolean checkLastRevState,
      Boolean askForConfirmation,
      Boolean dryRunMode,
      net.starlark.java.eval.Sequence<?> afterMigrations,
      net.starlark.java.eval.Sequence<?> afterAllMigrations,
      Object changeIdentityObj,
      Boolean setRevId,
      Boolean smartPrune,
      Boolean mergeImport,
      Object autoPatchFileConfigurationObj,
      Boolean migrateNoopChanges,
      Object customRevIdField,
      Object description,
      Boolean checkout,
      Object reversibleCheckIgnoreFiles,
      StarlarkThread thread)
      throws EvalException {
    WorkflowMode mode = stringToEnum("mode", modeStr, WorkflowMode.class);

    // Overwrite destination for testing workflow locally
    if (workflowOptions.toFolder) {
      destination = folderModule.destination();
    }

    Sequence sequenceTransform =
        Sequence.fromConfig(
            generalOptions.profiler(),
            workflowOptions,
            transformations,
            "transformations",
            printHandler,
            debugOptions::transformWrapper,
            Sequence.NoopBehavior.NOOP_IF_ANY_NOOP);
    Transformation reverseTransform = null;
    if (!generalOptions.isDisableReversibleCheck()
        && convertFromNoneable(reversibleCheckObj, mode == WorkflowMode.CHANGE_REQUEST)) {
      try {
        reverseTransform = sequenceTransform.reverse();
      } catch (NonReversibleValidationException e) {
        throw Starlark.errorf("%s", e.getMessage());
      }
    }

    ImmutableList<Token> changeIdentity = getChangeIdentity(changeIdentityObj);

    String customRevId = convertFromNoneable(customRevIdField, null);
    check(
        customRevId == null || CUSTOM_REVID_FORMAT.matches(customRevId),
        "Invalid experimental_custom_rev_id format. Format: %s",
        CUSTOM_REVID_FORMAT.pattern());

    if (setRevId) {
      check(
          mode != WorkflowMode.CHANGE_REQUEST || customRevId == null,
          "experimental_custom_rev_id is not allowed to be used in CHANGE_REQUEST mode if"
              + " set_rev_id is set to true. experimental_custom_rev_id is used for looking"
              + " for the baseline in the origin. No revId is stored in the destination.");
    } else {
      check(
          mode == WorkflowMode.CHANGE_REQUEST || mode == WorkflowMode.CHANGE_REQUEST_FROM_SOT,
          "'set_rev_id = False' is only supported"
              + " for CHANGE_REQUEST and CHANGE_REQUEST_FROM_SOT mode.");
    }
    if (smartPrune) {
      check(
          mode == WorkflowMode.CHANGE_REQUEST,
          "'smart_prune = True' is only supported" + " for CHANGE_REQUEST mode.");
    }

    if (checkLastRevState) {
      check(
          mode != WorkflowMode.CHANGE_REQUEST,
          "%s is not compatible with %s",
          CHECK_LAST_REV_STATE,
          WorkflowMode.CHANGE_REQUEST);
    }

    Authoring resolvedAuthoring = authoring;
    Author defaultAuthorFlag = workflowOptions.getDefaultAuthorFlag();
    if (defaultAuthorFlag != null) {
      resolvedAuthoring = new Authoring(defaultAuthorFlag, authoring.getMode(),
          authoring.getAllowlist());
    }

    AutoPatchfileConfiguration autoPatchfileConfiguration =
        convertFromNoneable(autoPatchFileConfigurationObj, null);

    WorkflowMode effectiveMode =
        generalOptions.squash || workflowOptions.importSameVersion ? WorkflowMode.SQUASH : mode;
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
            convertFromNoneable(reversibleCheckIgnoreFiles, null),
            askForConfirmation,
            mainConfigFile,
            allConfigFiles,
            dryRunMode,
            checkLastRevState || workflowOptions.checkLastRevState,
            convertFeedbackActions(afterMigrations, printHandler),
            convertFeedbackActions(afterAllMigrations, printHandler),
            changeIdentity,
            setRevId,
            smartPrune,
            mergeImport,
            autoPatchfileConfiguration,
            workflowOptions.migrateNoopChanges || migrateNoopChanges,
            customRevId,
            checkout);
    Module module = Module.ofInnermostEnclosingStarlarkFunction(thread);
    registerGlobalMigration(workflowName, workflow, module);
  }

  private static ImmutableList<Token> getChangeIdentity(Object changeIdentityObj)
      throws EvalException {
    String changeIdentity = convertFromNoneable(changeIdentityObj, null);

    if (changeIdentity == null) {
      return ImmutableList.of();
    }
    ImmutableList<Token> result = new Parser().parse(changeIdentity);
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
      throw Starlark.errorf("Unrecognized variable: %s", token.getValue());
    }
    check(configVarFound, "${%s} variable is required", COPYBARA_CONFIG_PATH_IDENTITY_VAR);
    return result;
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "move",
      doc = "Moves files between directories and renames files",
      parameters = {
        @Param(
            name = "before",
            named = true,
            doc =
                "The name of the file or directory before moving. If this is the empty string and"
                    + " 'after' is a directory, then all files in the workdir will be moved to the"
                    + " sub directory specified by 'after', maintaining the directory tree."),
        @Param(
            name = "after",
            named = true,
            doc =
                "The name of the file or directory after moving. If this is the empty string and"
                    + " 'before' is a directory, then all files in 'before' will be moved to the"
                    + " repo root, maintaining the directory tree inside 'before'."),
        @Param(
            name = "paths",
            named = true,
            allowedTypes = {
              @ParamType(type = Glob.class),
              @ParamType(type = NoneType.class),
            },
            doc =
                "A glob expression relative to 'before' if it represents a directory."
                    + " Only files matching the expression will be moved. For example,"
                    + " glob([\"**.java\"]), matches all java files recursively inside"
                    + " 'before' folder. Defaults to match all the files recursively.",
            defaultValue = "None"),
        @Param(
            name = "overwrite",
            named = true,
            doc =
                "Overwrite destination files if they already exist. Note that this makes the"
                    + " transformation non-reversible, since there is no way to know if the file"
                    + " was overwritten or not in the reverse workflow.",
            defaultValue = "False"),
        @Param(
            name = "regex_groups",
            named = true,
            positional = false,
            doc =
                "A set of named regexes that can be used to match part of the file name."
                    + " Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax."
                    + " For example {\"x\": \"[A-Za-z]+\"}",
            defaultValue = "{}")
      },
      useStarlarkThread = true)
  @DocDefault(field = "paths", value = "glob([\"**\"])")
  @Example(
      title = "Move a directory",
      before = "Move all the files in a directory to another directory:",
      code = "core.move(\"foo/bar_internal\", \"bar\")",
      after = "In this example, `foo/bar_internal/one` will be moved to `bar/one`.")
  @Example(
      title = "Move all the files to a subfolder",
      before = "Move all the files in the checkout dir into a directory called foo:",
      code = "core.move(\"\", \"foo\")",
      after = "In this example, `one` and `two/bar` will be moved to `foo/one` and `foo/two/bar`.")
  @Example(
      title = "Move a subfolder's content to the root",
      before = "Move the contents of a folder to the checkout root directory:",
      code = "core.move(\"foo\", \"\")",
      after = "In this example, `foo/bar` would be moved to `bar`.")
  @Example(
      title = "Move using Regex",
      before = "Change a file extension:",
      code =
          "core.move(before = 'foo/${x}.txt', after = 'foo/${x}.md', regex_groups = {"
              + " 'x': '.*'})",
      after = "In this example, `foo/bar/README.txt` will be moved to `foo/bar/README.md`.")
  public Transformation move(
      String before,
      String after,
      Object paths,
      Boolean overwrite,
      Dict<?, ?> regexes,
      StarlarkThread thread)
      throws EvalException {

    check(
        !Objects.equals(before, after),
        "Moving from the same folder to the same folder is a noop. Remove the"
            + " transformation.");

    return CopyOrMove.createMove(
        before,
        after,
        SkylarkUtil.convertStringMap(regexes, "regex_groups"),
        workflowOptions,
        convertFromNoneable(paths, Glob.ALL_FILES),
        overwrite,
        thread.getCallerLocation());
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "copy",
      doc = "Copy files between directories and renames files",
      parameters = {
        @Param(
            name = "before",
            named = true,
            doc =
                "The name of the file or directory to copy. If this is the empty string and"
                    + " 'after' is a directory, then all files in the workdir will be copied to"
                    + " the sub directory specified by 'after', maintaining the directory tree."),
        @Param(
            name = "after",
            named = true,
            doc =
                "The name of the file or directory destination. If this is the empty string and"
                    + " 'before' is a directory, then all files in 'before' will be copied to the"
                    + " repo root, maintaining the directory tree inside 'before'."),
        @Param(
            name = "paths",
            named = true,
            allowedTypes = {
              @ParamType(type = Glob.class),
              @ParamType(type = NoneType.class),
            },
            doc =
                "A glob expression relative to 'before' if it represents a directory."
                    + " Only files matching the expression will be copied. For example,"
                    + " glob([\"**.java\"]), matches all java files recursively inside"
                    + " 'before' folder. Defaults to match all the files recursively.",
            defaultValue = "None"),
        @Param(
            name = "overwrite",
            named = true,
            doc =
                "Overwrite destination files if they already exist. Note that this makes the"
                    + " transformation non-reversible, since there is no way to know if the file"
                    + " was overwritten or not in the reverse workflow.",
            defaultValue = "False"),
        @Param(
            name = "regex_groups",
            named = true,
            positional = false,
            doc =
                "A set of named regexes that can be used to match part of the file name."
                    + " Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax."
                    + " For example {\"x\": \"[A-Za-z]+\"}",
            defaultValue = "{}")
      },
      useStarlarkThread = true)
  @DocDefault(field = "paths", value = "glob([\"**\"])")
  @Example(
      title = "Copy a directory",
      before = "Move all the files in a directory to another directory:",
      code = "core.copy(\"foo/bar_internal\", \"bar\")",
      after = "In this example, `foo/bar_internal/one` will be copied to `bar/one`.")
  @Example(
      title = "Copy using Regex",
      before = "Change a file extension:",
      code =
          "core.copy(before = 'foo/${x}.txt', after = 'foo/${x}.md', regex_groups = {"
              + " 'x': '.*'})",
      after = "In this example, `foo/bar/README.txt` will be copied to `foo/bar/README.md`.")
  @Example(
      title = "Copy with reversal",
      before = "Copy all static files to a 'static' folder and use remove for reverting the change",
      code =
          "core.transform(\n"
              + "    [core.copy(\"foo\", \"foo/static\", paths = glob([\"**.css\",\"**.html\","
              + " ]))],\n"
              + "    reversal = [core.remove(glob(['foo/static/**.css',"
              + " 'foo/static/**.html']))]\n"
              + ")")
  public Transformation copy(
      String before,
      String after,
      Object paths,
      Boolean overwrite,
      Dict<?, ?> regexes,
      StarlarkThread thread)
      throws EvalException {
    check(
        !Objects.equals(before, after),
        "Copying from the same folder to the same folder is a noop. Remove the"
            + " transformation.");
    return CopyOrMove.createCopy(
        before,
        after,
        SkylarkUtil.convertStringMap(regexes, "regex_groups"),
        workflowOptions,
        convertFromNoneable(paths, Glob.ALL_FILES),
        overwrite,
        thread.getCallerLocation());
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "remove",
      doc =
          "Remove files from the workdir. **This transformation is only meant to be used inside"
              + " core.transform for reversing core.copy like transforms**. For regular file"
              + " filtering use origin_files exclude mechanism.",
      parameters = {
        @Param(name = "paths", named = true, doc = "The files to be deleted"),
      },
      useStarlarkThread = true)
  @Example(
      title = "Reverse a file copy",
      before = "Move all the files in a directory to another directory:",
      code =
          "core.transform(\n"
              + "    [core.copy(\"foo\", \"foo/public\")],\n"
              + "    reversal = [core.remove(glob([\"foo/public/**\"]))])",
      after = "In this example, `foo/one` will be moved to `foo/public/one`.")
  @Example(
      title = "Copy with reversal",
      before = "Copy all static files to a 'static' folder and use remove for reverting the change",
      code =
          "core.transform(\n"
              + "    [core.copy(\"foo\", \"foo/static\", paths = glob([\"**.css\",\"**.html\","
              + " ]))],\n"
              + "    reversal = [core.remove(glob(['foo/static/**.css',"
              + " 'foo/static/**.html']))]\n"
              + ")")
  public Remove remove(Glob paths, StarlarkThread thread) {
    return new Remove(paths, thread.getCallerLocation());
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "replace",
      doc =
          "Replace a text with another text using optional regex groups. This transformation can be"
              + " automatically reversed.",
      parameters = {
        @Param(
            name = "before",
            named = true,
            doc =
                "The text before the transformation. Can contain references to regex groups. For"
                    + " example \"foo${x}text\".<p>`before` can only contain 1 reference to each"
                    + " unique `regex_group`. If you require multiple references to the same"
                    + " `regex_group`, add `repeated_groups: True`.<p>If '$' literal character"
                    + " needs to be matched, '`$$`' should be used. For example '`$$FOO`' would"
                    + " match the literal '$FOO'."
                    + " [Note this argument is a string. If you want to match a regular expression"
                    + " it must be encoded as a regex_group.]"),
        @Param(
            name = "after",
            named = true,
            doc =
                "The text after the transformation. It can also contain references to regex "
                    + "groups, like 'before' field."),
        @Param(
            name = "regex_groups",
            named = true,
            doc =
                "A set of named regexes that can be used to match part of the replaced text."
                    + "Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax."
                    + " For example {\"x\": \"[A-Za-z]+\"}",
            defaultValue = "{}"),
        @Param(
            name = "paths",
            named = true,
            allowedTypes = {
              @ParamType(type = Glob.class),
              @ParamType(type = NoneType.class),
            },
            doc =
                "A glob expression relative to the workdir representing the files to apply the"
                    + " transformation. For example, glob([\"**.java\"]), matches all java files"
                    + " recursively. Defaults to match all the files recursively.",
            defaultValue = "None"),
        @Param(
            name = "first_only",
            named = true,
            doc =
                "If true, only replaces the first instance rather than all. In single line mode,"
                    + " replaces the first instance on each line. In multiline mode, replaces the"
                    + " first instance in each file.",
            defaultValue = "False"),
        @Param(
            name = "multiline",
            named = true,
            doc = "Whether to replace text that spans more than one line.",
            defaultValue = "False"),
        @Param(
            name = "repeated_groups",
            named = true,
            doc =
                "Allow to use a group multiple times. For example foo${repeated}/${repeated}. Note"
                    + " that this won't match \"fooX/Y\". This mechanism doesn't use"
                    + " backtracking. In other words, the group instances are treated as different"
                    + " groups in regex construction and then a validation is done after that.",
            defaultValue = "False"),
        @Param(
            name = "ignore",
            named = true,
            doc =
                "A set of regexes. Any line that matches any expression in this set, which"
                    + " might otherwise be transformed, will be ignored.",
            defaultValue = "[]"),
      },
      useStarlarkThread = true)
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
              + "   regex_groups = { 'end' : '\\\\z'},\n"
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
              + "       regex_groups = { 'end' : r'\\z'},\n"
              + "    )\n"
              + "],\n"
              + "reversal = [\n"
              + "    core.replace(\n"
              + "       before = 'some append${end}',\n"
              + "       after = '',\n"
              + "       multiline = True,\n"
              + "       regex_groups = { 'end' : r'\\z'},\n"
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
      Dict<?, ?> regexes, // <String, String>
      Object paths,
      Boolean firstOnly,
      Boolean multiline,
      Boolean repeatedGroups,
      net.starlark.java.eval.Sequence<?> ignore, // <String>
      StarlarkThread thread)
      throws EvalException {
    return Replace.create(
        thread.getCallerLocation(),
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
  @StarlarkMethod(
      name = "todo_replace",
      doc = "Replace Google style TODOs. For example `TODO(username, othername)`.",
      parameters = {
        @Param(
            name = "tags",
            named = true,
            allowedTypes = {
              @ParamType(type = net.starlark.java.eval.Sequence.class, generic1 = String.class)
            },
            doc = "Prefix tag to look for",
            defaultValue = "['TODO', 'NOTE']"),
        @Param(
            name = "mapping",
            named = true,
            doc = "Mapping of users/strings",
            defaultValue = "{}"),
        @Param(
            name = "mode",
            named = true,
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
            allowedTypes = {
              @ParamType(type = Glob.class),
              @ParamType(type = NoneType.class),
            },
            doc =
                "A glob expression relative to the workdir representing the files to apply the"
                    + " transformation. For example, glob([\"**.java\"]), matches all java files"
                    + " recursively. Defaults to match all the files recursively.",
            defaultValue = "None"),
        @Param(
            name = "default",
            named = true,
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            doc =
                "Default value if mapping not found. Only valid for 'MAP_OR_DEFAULT' or"
                    + " 'USE_DEFAULT' modes",
            defaultValue = "None"),
        @Param(
            name = "ignore",
            named = true,
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            doc =
                "If set, elements within TODO (with usernames) that match the regex will be "
                    + "ignored. For example ignore = \"foo\" would ignore \"foo\" in "
                    + "\"TODO(foo,bar)\" but not \"bar\".",
            defaultValue = "None"),
      },
      useStarlarkThread = true)
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
  @Example(
      title = "Ignoring Regex Patterns",
      before = "Ignore regEx inside TODOs when scrubbing/mapping",
      code = "core.todo_replace(\n" + "  mapping = { 'aaa' : 'foo'},\n" + "  ignore = 'b/.*'\n)",
      after = "Would replace texts like TODO(b/123, aaa) with TODO(b/123, foo)")
  public TodoReplace todoReplace(
      net.starlark.java.eval.Sequence<?> skyTags, // <String>
      Dict<?, ?> skyMapping, // <String, String>
      String modeStr,
      Object paths,
      Object skyDefault,
      Object regexToIgnore,
      StarlarkThread thread)
      throws EvalException {
    Mode mode = stringToEnum("mode", modeStr, Mode.class);
    Map<String, String> mapping = SkylarkUtil.convertStringMap(skyMapping, "mapping");
    String defaultString = convertFromNoneable(skyDefault, /*defaultValue=*/null);
    ImmutableList<String> tags =
        ImmutableList.copyOf(SkylarkUtil.convertStringList(skyTags, "tags"));
    String ignorePattern = convertFromNoneable(regexToIgnore, null);
    Pattern regexIgnorelist = ignorePattern != null ? Pattern.compile(ignorePattern) : null;

    check(!tags.isEmpty(), "'tags' cannot be empty");
    if (mode == Mode.MAP_OR_DEFAULT || mode == Mode.USE_DEFAULT) {
      check(defaultString != null, "'default' needs to be set for mode '%s'", mode);
    } else {
      check(defaultString == null, "'default' cannot be used for mode '%s'", mode);
    }
    if (mode == Mode.USE_DEFAULT || mode == Mode.SCRUB_NAMES) {
      check(mapping.isEmpty(), "'mapping' cannot be used with mode %s", mode);
    }
    return new TodoReplace(
        thread.getCallerLocation(),
        convertFromNoneable(paths, Glob.ALL_FILES),
        tags,
        mode,
        mapping,
        defaultString,
        workflowOptions.parallelizer(),
        regexIgnorelist);
  }

  public static final String TODO_FILTER_REPLACE_EXAMPLE = ""
      + "core.filter_replace(\n"
      + "    regex = 'TODO\\\\((.*?)\\\\)',\n"
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
      + ")";

  public static final String SIMPLE_FILTER_REPLACE_EXAMPLE = ""
      + "core.filter_replace(\n"
      + "    regex = 'a.*',\n"
      + "    mapping = {\n"
      + "        'afoo': 'abar',\n"
      + "        'abaz': 'abam'\n"
      + "    }\n"
      + ")";

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "filter_replace",
      doc =
          "Applies an initial filtering to find a substring to be replaced and then applies"
              + " a `mapping` of replaces for the matched text.",
      parameters = {
        @Param(name = "regex", named = true, doc = "A re2 regex to match a substring of the file",
        allowedTypes = {
            @ParamType(type = String.class)
        }),
        @Param(
            name = "mapping",
            named = true,
            doc = "A mapping function like core.replace_mapper or a dict with mapping values.",
            defaultValue = "{}"),
        @Param(
            name = "group",
            named = true,
            allowedTypes = {
              @ParamType(type = StarlarkInt.class),
              @ParamType(type = NoneType.class),
            },
            doc =
                "Extract a regex group from the matching text and pass this as parameter to"
                    + " the mapping instead of the whole matching text.",
            defaultValue = "None"),
        @Param(
            name = "paths",
            named = true,
            allowedTypes = {
              @ParamType(type = Glob.class),
              @ParamType(type = NoneType.class),
            },
            doc =
                "A glob expression relative to the workdir representing the files to apply the"
                    + " transformation. For example, glob([\"**.java\"]), matches all java files"
                    + " recursively. Defaults to match all the files recursively.",
            defaultValue = "None"),
        @Param(
            name = "reverse",
            named = true,
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            doc = "A re2 regex used as reverse transformation",
            defaultValue = "None"),
      },
      useStarlarkThread = true)
  @DocDefault(field = "paths", value = "glob([\"**\"])")
  @DocDefault(field = "reverse", value = "regex")
  @DocDefault(field = "group", value = "Whole text")
  @Example(
      title = "Simple replace with mapping",
      before = "Simplest mapping",
      code = SIMPLE_FILTER_REPLACE_EXAMPLE)
  @Example(
      title = "TODO replace",
      before = "This replace is similar to what it can be achieved with core.todo_replace:",
      code = TODO_FILTER_REPLACE_EXAMPLE)
  public FilterReplace filterReplace(
      String regex,
      Object mapping,
      Object group,
      Object paths,
      Object reverse,
      StarlarkThread thread)
      throws EvalException {
    ReversibleFunction<String, String> func = getMappingFunction(mapping);

    String afterPattern = convertFromNoneable(reverse, regex);
    int numGroup = convertFromNoneable(group, StarlarkInt.of(0)).toInt("group");
    Pattern before = Pattern.compile(regex);
    check(
        numGroup <= before.groupCount(),
        "group idx is greater than the number of groups defined in '%s'. Regex has %s groups",
        before.pattern(),
        before.groupCount());
    Pattern after = Pattern.compile(afterPattern);
    check(
        numGroup <= after.groupCount(),
        "reverse_group idx is greater than the number of groups defined in '%s'."
            + " Regex has %s groups",
        after.pattern(),
        after.groupCount());
    return new FilterReplace(
        workflowOptions,
        before,
        after,
        numGroup,
        numGroup,
        func,
        convertFromNoneable(paths, Glob.ALL_FILES),
        thread.getCallerLocation());
  }

  @SuppressWarnings("unchecked")
  public static ReversibleFunction<String, String> getMappingFunction(Object mapping)
      throws EvalException {
    if (mapping instanceof Dict) {
      ImmutableMap<String, String> map =
          ImmutableMap.copyOf(Dict.noneableCast(mapping, String.class, String.class, "mapping"));
      check(!map.isEmpty(), "Empty mapping is not allowed." + " Remove the transformation instead");
      return new MapMapper(map);
    }
    check(
        mapping instanceof ReversibleFunction,
        "mapping has to be instance of" + " map or a reversible function");
    return (ReversibleFunction<String, String>) mapping;
  }

  @StarlarkMethod(
      name = "replace_mapper",
      doc =
          "A mapping function that applies a list of replaces until one replaces the text"
              + " (Unless `all = True` is used). This should be used with core.filter_replace or"
              + " other transformations that accept text mapping as parameter.",
      parameters = {
        @Param(
            name = "mapping",
            allowedTypes = {
              @ParamType(
                  type = net.starlark.java.eval.Sequence.class,
                  generic1 = Transformation.class),
            },
            named = true,
            doc = "The list of core.replace transformations"),
        @Param(
            name = "all",
            named = true,
            positional = false,
            doc = "Run all the mappings despite a replace happens.",
            defaultValue = "False"),
      })
  public ReplaceMapper mapImports(
      net.starlark.java.eval.Sequence<?> mapping, // <Transformation>
      Boolean all)
      throws EvalException {
    check(!mapping.isEmpty(), "Empty mapping is not allowed");
    ImmutableList.Builder<Replace> replaces = ImmutableList.builder();
    for (Transformation t :
        net.starlark.java.eval.Sequence.cast(mapping, Transformation.class, "mapping")) {
      check(
          t instanceof Replace,
          "Only core.replace can be used as mapping, but got: %S", t.describe());
      Replace replace = (Replace) t;
      check(
          replace.getPaths().equals(Glob.ALL_FILES),
          "core.replace cannot use" + " 'paths' inside core.replace_mapper");
      replaces.add(replace);
    }
    return new ReplaceMapper(replaces.build(), all);
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "verify_match",
      doc =
          "Verifies that a RegEx matches (or not matches) the specified files. Does not"
              + " transform anything, but will stop the workflow if it fails.",
      parameters = {
        @Param(
            name = "regex",
            named = true,
            doc =
                "The regex pattern to verify. To satisfy the validation, there has to be at"
                    + "least one (or no matches if verify_no_match) match in each of the files "
                    + "included in paths. The re2j pattern will be applied in multiline mode, i.e."
                    + " '^' refers to the beginning of a file and '$' to its end. "
                    + "Copybara uses [re2](https://github.com/google/re2/wiki/Syntax) syntax."),
        @Param(
            name = "paths",
            named = true,
            allowedTypes = {
              @ParamType(type = Glob.class),
              @ParamType(type = NoneType.class),
            },
            doc =
                "A glob expression relative to the workdir representing the files to apply the"
                    + " transformation. For example, glob([\"**.java\"]), matches all java files"
                    + " recursively. Defaults to match all the files recursively.",
            defaultValue = "None"),
        @Param(
            name = "verify_no_match",
            named = true,
            doc = "If true, the transformation will verify that the RegEx does not match.",
            defaultValue = "False"),
        @Param(
            name = "also_on_reversal",
            named = true,
            doc =
                "If true, the check will also apply on the reversal. The default behavior is to"
                    + " not verify the pattern on reversal.",
            defaultValue = "False"),
        @Param(
            name = "failure_message",
            named = true,
            doc = "Optional string that will be included in the failure message.",
            defaultValue = "None")
      },
      useStarlarkThread = true)
  @DocDefault(field = "paths", value = "glob([\"**\"])")
  public VerifyMatch verifyMatch(
      String regex,
      Object paths,
      Boolean verifyNoMatch,
      Boolean alsoOnReversal,
      Object failureMessage,
      StarlarkThread thread)
      throws EvalException {
    return VerifyMatch.create(
        thread.getCallerLocation(),
        regex,
        convertFromNoneable(paths, Glob.ALL_FILES),
        verifyNoMatch,
        alsoOnReversal,
        SkylarkUtil.convertOptionalString(failureMessage),
        workflowOptions.parallelizer());
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
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
            allowedTypes = {
              @ParamType(
                  type = net.starlark.java.eval.Sequence.class,
                  generic1 = Transformation.class),
            },
            named = true,
            doc = "The list of transformations to run as a result of running this transformation."),
        @Param(
            name = "reversal",
            allowedTypes = {
              @ParamType(
                  type = net.starlark.java.eval.Sequence.class,
                  generic1 = Transformation.class),
              @ParamType(type = NoneType.class),
            },
            doc =
                "The list of transformations to run as a result of running this"
                    + " transformation in reverse.",
            named = true,
            positional = false,
            defaultValue = "None"),
        @Param(
            name = "ignore_noop",
            allowedTypes = {
              @ParamType(type = Boolean.class),
              @ParamType(type = NoneType.class),
            },
            doc =
                "WARNING: Deprecated. Use `noop_behavior` instead.\nIn case a noop error happens in"
                    + " the group of transformations (Both forward and reverse), it will be"
                    + " ignored, but the rest of the transformations in the group will still be"
                    + " executed. If ignore_noop is not set, we will apply the closest parent's"
                    + " ignore_noop.",
            named = true,
            positional = false,
            defaultValue = "None"),
        @Param(
            name = "noop_behavior",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            doc =
                "How to handle no-op transformations:<br><ul> <li><b>'IGNORE_NOOP'</b>: Any no-ops"
                    + " among the wrapped transformations are ignored.</li>"
                    + " <li><b>'NOOP_IF_ANY_NOOP'</b>: Throws an exception as soon as a single"
                    + " wrapped transformation is a no-op.</li> <li><b>'NOOP_IF_ALL_NOOP'</b>:"
                    + " Ignores no-ops from the wrapped transformations unless they all no-op, in"
                    + " which case an exception is thrown.</li></ul>",
            named = true,
            positional = false,
            defaultValue = "None"),
      })
  @DocDefault(field = "reversal", value = "The reverse of 'transformations'")
  @DocDefault(field = "noop_behavior", value = "NOOP_IF_ANY_NOOP")
  public Transformation transform(
      net.starlark.java.eval.Sequence<?> transformations, // <Transformation>
      Object reversal,
      Object ignoreNoop,
      Object noopBehaviorString)
      throws EvalException, ValidationException {
    checkCondition(
        Starlark.isNullOrNone(ignoreNoop) || Starlark.isNullOrNone(noopBehaviorString),
        "The deprecated param 'ignore_noop' cannot be set simultaneously with 'noop_behavior'."
            + " Prefer using 'noop_behavior'.");
    Sequence.NoopBehavior noopBehavior =
        stringToEnum(
            "noop_behavior",
            convertFromNoneable(noopBehaviorString, "NOOP_IF_ANY_NOOP"),
            Sequence.NoopBehavior.class);
    if (Boolean.TRUE.equals(ignoreNoop)) {
      noopBehavior = Sequence.NoopBehavior.IGNORE_NOOP;
    } else if (Boolean.FALSE.equals(ignoreNoop)) {
      noopBehavior = Sequence.NoopBehavior.FAIL_IF_ANY_NOOP;
    }
    Sequence forward =
        Sequence.fromConfig(
            generalOptions.profiler(),
            workflowOptions,
            transformations,
            "transformations",
            printHandler,
            debugOptions::transformWrapper,
            noopBehavior);
    net.starlark.java.eval.Sequence<Transformation> reverseList =
        convertFromNoneable(reversal, null);
    if (reverseList == null) {
      try {
        reverseList = StarlarkList.immutableCopyOf(ImmutableList.of(forward.reverse()));
      } catch (NonReversibleValidationException e) {
        throw Starlark.errorf(
            "transformations are not automatically reversible."
                + " Use 'reversal' field to explicitly configure the reversal of the transform");
      }
    }
    Sequence reverse =
        Sequence.fromConfig(
            generalOptions.profiler(),
            workflowOptions,
            reverseList,
            "reversal",
            printHandler,
            debugOptions::transformWrapper,
            noopBehavior);
    return new ExplicitReversal(forward, reverse);
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "dynamic_transform",
      doc =
          "Create a dynamic Skylark transformation. This should only be used by libraries"
              + " developers",
      parameters = {
        @Param(name = "impl", named = true, doc = "The Skylark function to call"),
        @Param(
            name = "params",
            named = true,
            doc = "The parameters to the function. Will be available under ctx.params",
            defaultValue = "{}"),
      },
      useStarlarkThread = true)
  @Example(
      title = "Create a dynamic transformation without parameters",
      before =
          "To define a simple dynamic transformation, you don't even need to use"
              + " `core.dynamic_transform`. The following transformation sets the change's message"
              + " to uppercase.",
      code = "def test(ctx):\n  ctx.set_message(ctx.message.upper())",
      testExistingVariable = "test",
      after =
          "After defining this function, you can use `test` as a transformation in"
              + " `core.workflow`.")
  @Example(
      title = "Create a dynamic transformation with parameters",
      before =
          "If you want to create a library that uses dynamic transformations, you probably want to"
              + " make them customizable. In order to do that, in your library.bara.sky, you need"
              + " to hide the dynamic transformation (prefix with '\\_') and instead expose a"
              + " function that creates the dynamic transformation with the param:",
      code =
          ""
              + "def _test_impl(ctx):\n"
              + "  ctx.set_message("
              + "ctx.message + ctx.params['name'] + str(ctx.params['number']) + '\\n')\n"
              + "\n"
              + "def test(name, number = 2):\n"
              + "  return core.dynamic_transform(impl = _test_impl,\n"
              + "                           params = { 'name': name, 'number': number})",
      testExistingVariable = "test",
      after =
          "After defining this function, you can use `test('example', 42)` as a transformation"
              + " in `core.workflow`.")
  public Transformation dynamic_transform(
      StarlarkCallable impl, Dict<?, ?> params, StarlarkThread thread) {
    return new SkylarkTransformation(
        impl, Dict.<Object, Object>copyOf(thread.mutability(), params), printHandler);
  }

  // TODO(malcon): Deprecate this method once all references moved to core.action
  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "dynamic_feedback",
      doc =
          "Create a dynamic Skylark feedback migration. This should only be used by libraries"
              + " developers",
      parameters = {
        @Param(
            name = "impl",
            named = true,
            doc = "The Skylark function to call"),
        @Param(
            name = "params",
            named = true,
            doc = "The parameters to the function. Will be available under ctx.params",
            defaultValue = "{}"),
      },
      useStarlarkThread = true)
  public Action dynamicFeedback(StarlarkCallable impl, Dict<?, ?> params, StarlarkThread thread) {
    return new StarlarkAction(findCallableName(impl, thread),
        impl, Dict.<Object, Object>copyOf(thread.mutability(), params), printHandler);
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "action",
      doc = "Create a dynamic Skylark action. This should only be used by libraries"
              + " developers. Actions are Starlark functions that receive a context, perform"
              + " some side effect and return a result (success, error or noop).",
      parameters = {
        @Param(
            name = "impl",
            named = true,
            doc = "The Skylark function to call"),
        @Param(
            name = "params",
            named = true,
            doc = "The parameters to the function. Will be available under ctx.params",
            defaultValue = "{}"),
      },
      useStarlarkThread = true)
  public Action action(StarlarkCallable impl, Dict<?, ?> params, StarlarkThread thread) {

    return new StarlarkAction(findCallableName(impl, thread),
        impl, Dict.<Object, Object>copyOf(thread.mutability(), params), printHandler);
  }

  private String findCallableName(StarlarkCallable impl, StarlarkThread thread) {
    String name = impl.getName();
    ImmutableList<CallStackEntry> stack = thread.getCallStack();
    if (name.equals("lambda") && stack.size() > 1
        && !stack.get(stack.size() - 2).name.equals("<toplevel>")) {
      name = stack.get(stack.size() - 2).name;
    }
    return name;
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "fail_with_noop",
      doc = "If invoked, it will fail the current migration as a noop",
      parameters = {
        @Param(name = "msg", named = true, doc = "The noop message"),
      })
  public Action failWithNoop(String msg) throws EmptyChangeException {
    throw new EmptyChangeException(msg);
  }

  @StarlarkMethod(name = "main_config_path",
      doc = "Location of the config file. This is subject to change",
      structField = true)
  public String getMainConfigFile() {
    return mainConfigFile.getIdentifier();
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "feedback",
      doc =
          "Defines a migration of changes' metadata, that can be invoked via the Copybara command"
              + " in the same way as a regular workflow migrates the change itself.\n"
              + "\n"
              + "It is considered change metadata any information associated with a change"
              + " (pending or submitted) that is not core to the change itself. A few examples:\n"
              + "<ul>\n"
              + "  <li> Comments: Present in any code review system. Examples: GitHub PRs or"
              + " Gerrit     code reviews.</li>\n"
              + "  <li> Labels: Used in code review systems for approvals and/or CI results.    "
              + " Examples: GitHub labels, Gerrit code review labels.</li>\n"
              + "</ul>\n"
              + "For the purpose of this workflow, it is not considered metadata the commit"
              + " message in Git, or any of the contents of the file tree.\n"
              + "\n",
      parameters = {
        @Param(
            name = "name",
            doc = "The name of the feedback workflow.",
            positional = false,
            named = true),
        @Param(
            name = "origin",
            doc = "The trigger of a feedback migration.",
            positional = false,
            named = true),
        @Param(
            name = "destination",
            doc =
                "Where to write change metadata to. This is usually a code review system like "
                    + "Gerrit or GitHub PR.",
            positional = false,
            named = true),
        @Param(
            name = "actions",
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
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            positional = false,
            doc = "A description of what this workflow achieves",
            defaultValue = "None"),
      },
      useStarlarkThread = true)
  @UsesFlags({FeedbackOptions.class})
  /*TODO(danielromero): Add default values*/
  public NoneType feedback(
      String workflowName,
      Trigger trigger,
      EndpointProvider<?> destination,
      net.starlark.java.eval.Sequence<?> feedbackActions,
      Object description,
      StarlarkThread thread)
      throws EvalException {
    ImmutableList<Action> actions = convertFeedbackActions(feedbackActions, printHandler);
    Feedback migration =
        new Feedback(
            workflowName,
            convertFromNoneable(description, null),
            mainConfigFile,
            trigger,
            destination.getEndpoint(),
            actions,
            generalOptions);
    Module module = Module.ofInnermostEnclosingStarlarkFunction(thread);
    registerGlobalMigration(workflowName, migration, module);
    return Starlark.NONE;
  }

  @StarlarkMethod(
      name = "console",
      structField = true,
      doc =  "Returns a handle to the console object.")
  public SkylarkConsole console()
      throws EvalException {
    synchronized (this) {
      if (console == null) {
        console = new SkylarkConsole(generalOptions.console());
      }
    }
    return console;
  }

  /** Registers a {@link Migration} in the global registry. */
  protected void registerGlobalMigration(String name, Migration migration, Module module)
      throws EvalException {
    getGlobalMigrations(module).addMigration(name, migration);
  }

  @StarlarkMethod(
      name = "format",
      doc =
          "Formats a String using Java's <a"
              + " href='https://docs.oracle.com/javase/8/docs/api/java/lang/String.html#format-java.lang.String-java.lang.Object...-'><code>String.format</code></a>.",
      parameters = {
        @Param(name = "format", named = true, doc = "The format string"),
        @Param(name = "args", named = true, doc = "The arguments to format"),
      })
  public String format(String format, net.starlark.java.eval.Sequence<?> args)
      throws EvalException {
    // This function presumably exists because Starlark-in-Java's 'str % tuple'
    // operator doesn't support width and precision.

    // Convert StarlarkInt to types known to Java's String.format.
    Object[] array = args.toArray(new Object[0]);
    for (int i = 0; i < array.length; i++) {
      if (array[i] instanceof StarlarkInt) {
        array[i] = ((StarlarkInt) array[i]).toNumber();
      }
    }

    try {
      return String.format(format, array);
    } catch (IllegalFormatException e) {
      throw Starlark.errorf("Invalid format: %s: %s", format, e.getMessage());
    }
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "latest_version",
      doc =
          "Selects the latest version that matches the format.  Using --force"
              + " in the CLI will force to use the reference passed as argument instead.",
      parameters = {
        @Param(
            name = "format",
            doc =
                "The format of the version. If using it for git, it has to use the complete"
                    + "refspec (e.g. 'refs/tags/${n0}.${n1}.${n2}')",
            named = true),
        @Param(
            name = "regex_groups",
            named = true,
            doc =
                "A set of named regexes that can be used to match part of the versions. Copybara"
                    + " uses [re2](https://github.com/google/re2/wiki/Syntax) syntax. Use the"
                    + " following nomenclature n0, n1, n2 for the version part (will use numeric"
                    + " sorting) or s0, s1, s2 (alphabetic sorting). Note that there can be mixed"
                    + " but the numbers cannot be repeated. In other words n0, s1, n2 is valid but"
                    + " not n0, s0, n1. n0 has more priority than n1. If there are fields where"
                    + " order is not important, use s(N+1) where N ist he latest sorted field."
                    + " Example {\"n0\": \"[0-9]+\", \"s1\": \"[a-z]+\"}",
            defaultValue = "{}"),
      },
      useStarlarkThread = true)
  @Example(
      title = "Version selector for Git tags",
      before = "Example of how to match tags that follow semantic versioning",
      code =
          "core.latest_version(\n"
              + "    format = \"refs/tags/${n0}.${n1}.${n2}\","
              + "    regex_groups = {\n"
              + "        'n0': '[0-9]+',"
              + "        'n1': '[0-9]+',"
              + "        'n2': '[0-9]+',"
              + "    }"
              + ")")
  @Example(
      title =
          "Version selector for Git tags with mixed version semantics with X.Y.Z and X.Y tagging",
      before = "Edge case example: we allow a '.' literal prefix for numeric regex groups.",
      code =
          "core.latest_version(\n"
              + "    format = \"refs/tags/${n0}.${n1}${n2}\","
              + "    regex_groups = {\n"
              + "        'n0': '[0-9]+',"
              + "        'n1': '[0-9]+',"
              + "        'n2': '(.[0-9]+)?',"
              + "    }"
              + ")")
  public VersionSelector versionSelector(
      String regex, Dict<?, ?> groups, StarlarkThread thread) // <String, String>
      throws EvalException {
    Map<String, String> groupsMap = Dict.cast(groups, String.class, String.class, "regex_groups");

    TreeMap<Integer, VersionElementType> elements = new TreeMap<>();
    Pattern regexKey = Pattern.compile("([sn])([0-9])");
    for (String s : groupsMap.keySet()) {
      Matcher matcher = regexKey.matcher(s);
      check(
          matcher.matches(),
          "Incorrect key for regex_group. Should be in the "
              + "format of n0, n1, etc. or s0, s1, etc. Value: %s",
          s);
      VersionElementType type = matcher.group(1).equals("s") ? ALPHABETIC : NUMERIC;
      int num = Integer.parseInt(matcher.group(2));
      check(
          !elements.containsKey(num) || elements.get(num) == type,
          "Cannot use same n in both s%s and n%s: %s",
          num,
          num,
          s);
      elements.put(num, type);
    }
    for (Integer num : elements.keySet()) {
      if (num > 0) {
        check(
            elements.containsKey(num - 1),
            "Cannot have s%s or n%s if s%s or n%s" + " doesn't exist",
            num,
            num,
            num - 1,
            num - 1);
      }
    }

    LatestVersionSelector versionPicker = new LatestVersionSelector(
        regex, Replace.parsePatterns(groupsMap), elements, thread.getCallerLocation());
    ImmutableList<String> extraGroups = versionPicker.getUnmatchedGroups();
    check(extraGroups.isEmpty(), "Extra regex_groups not used in pattern: %s", extraGroups);
    if (generalOptions.isForced() || generalOptions.isVersionSelectorUseCliRef()) {
      return new OrderedVersionSelector(
          ImmutableList.of(new RequestedVersionSelector(), versionPicker));
    }
    return versionPicker;
  }

  private static ImmutableList<Action> convertFeedbackActions(
      net.starlark.java.eval.Sequence<?> feedbackActions, StarlarkThread.PrintHandler printHandler)
      throws EvalException {
    ImmutableList.Builder<Action> actions = ImmutableList.builder();
    for (Object action : feedbackActions) {
      if (action instanceof StarlarkCallable) {
        actions.add(new StarlarkAction(((StarlarkCallable) action).getName(),
            (StarlarkCallable) action, Dict.empty(), printHandler));
      } else if (action instanceof Action) {
        actions.add((Action) action);
      } else {
        throw Starlark.errorf(
            "Invalid feedback action '%s 'of type: %s", action, action.getClass());
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
  public void setPrintHandler(StarlarkThread.PrintHandler printHandler) {
    this.printHandler = printHandler;
  }

  @SuppressWarnings("unused")
  @StarlarkMethod(
      name = "autopatch_config",
      doc = "Describes in the configuration for automatic patch file generation",
      parameters = {
        @Param(
            name = "header",
            doc = "A string to include at the beginning of each patch file",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            positional = false,
            defaultValue = "None"),
        @Param(
            name = "suffix",
            doc = "Suffix to use when saving patch files",
            named = true,
            positional = false,
            defaultValue = "'.patch'"),
        @Param(
            name = "directory_prefix",
            doc =
                "Directory prefix used to relativize filenames when writing patch files. E.g. if"
                    + " filename is third_party/foo/bar/bar.go and we want to write"
                    + " third_party/foo/PATCHES/bar/bar.go, the value for this field would be"
                    + " 'third_party/foo'",
            named = true,
            positional = false,
            // TODO: temporarily include a default value to not break existing usage
            defaultValue = "'None'",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            }),
        @Param(
            name = "directory",
            doc = "Directory in which to save the patch files.",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            positional = false,
            defaultValue = "'AUTOPATCHES'"),
        @Param(
            name = "strip_file_names_and_line_numbers",
            doc = "When true, strip filenames and line numbers from patch files",
            named = true,
            positional = false,
            defaultValue = "False")
      })
  public AutoPatchfileConfiguration autoPatchfileConfiguration(
      Object fileContentsPrefix,
      String suffix,
      Object directoryPrefix,
      Object directory,
      boolean stripFileNames) {
    return AutoPatchfileConfiguration.create(
        convertFromNoneable(fileContentsPrefix, null),
        suffix,
        convertFromNoneable(directoryPrefix, null),
        convertFromNoneable(directory, null),
        stripFileNames);
  }
}
