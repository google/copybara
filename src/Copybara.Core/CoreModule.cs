/*
 * Copyright (C) 2016 Google LLC
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
using System.Text.RegularExpressions;
using Copybara.Authoring;
using Copybara.Common;
using Copybara.Config;
using Copybara.Exceptions;
using Copybara.TemplateToken;
using Copybara.Transform;
using Copybara.Util;
using Starlark.Annot;
using Starlark.Eval;
using Starlark.Syntax;

// Domain 'Console' collides with System.Console (not used here directly) and static 'Starlark'
// collides with the root namespace segment.
using StarlarkRt = Starlark.Eval.Starlark;

// Java's net.starlark.java.eval.Sequence<?> (the interface for Starlark lists/tuples) maps to the
// concrete StarlarkList in this port; the static helper class Starlark.Eval.Sequence is unrelated.
using StarlarkSequence = Starlark.Eval.StarlarkList;
using StructImpl = Copybara.StructModule.StructImpl;

// 'Authoring' is both a namespace (Copybara.Authoring) and a type; alias the type to disambiguate.
using AuthoringType = Copybara.Authoring.Authoring;

namespace Copybara;

/// <summary>
/// Main configuration class for creating migrations.
///
/// <para>This class is exposed in Starlark configuration as an instance variable called "core". So
/// users can use it as:</para>
/// <code>
/// core.workflow(
///   name = "foo",
///   ...
/// )
/// </code>
/// </summary>
[StarlarkBuiltin(
    "core",
    Doc = "Core functionality for creating migrations, and basic transformations.")]
public class CoreModule : ILabelsAwareModule, IStarlarkValue
{
    // Restrict for label ids like 'BAZEL_REV_ID' or 'Bazel-RevId'.
    private static readonly Regex CustomRevidFormat =
        new("^([A-Z][A-Z_0-9]{1,30}_REV_ID|[A-Z][a-zA-Z0-9-]{1,30}-RevId)$");

    private const string CheckLastRevStateName = "check_last_rev_state";

    private readonly GeneralOptions _generalOptions;
    private readonly WorkflowOptions _workflowOptions;

    // TODO(port): reconcile — DebugOptions and FolderModule are being ported concurrently.
    private readonly Copybara.Transform.Debug.DebugOptions _debugOptions;
    private readonly Copybara.Folder.FolderModule _folderModule;

    private ConfigFile _mainConfigFile = null!;
    private Func<ImmutableDictionary<string, ConfigFile>>? _allConfigFiles;
    private StarlarkThread.PrintHandler? _printHandler;
    private readonly Dictionary<ConfigFile, HashSet<string>> _transformNames = new();
    private SkylarkConsole? _console;
    private readonly object _consoleLock = new();

    public CoreModule(
        GeneralOptions generalOptions,
        WorkflowOptions workflowOptions,
        Copybara.Transform.Debug.DebugOptions debugOptions,
        Copybara.Folder.FolderModule folderModule)
    {
        _generalOptions = Preconditions.CheckNotNull(generalOptions);
        _workflowOptions = Preconditions.CheckNotNull(workflowOptions);
        _debugOptions = Preconditions.CheckNotNull(debugOptions);
        _folderModule = Preconditions.CheckNotNull(folderModule);
    }

    [StarlarkMethod("reverse",
        Doc =
            "Given a list of transformations, returns the list of transformations equivalent to"
            + " undoing all the transformations")]
    public StarlarkSequence Reverse(
        [Param(Name = "transformations", Named = true,
            Doc = "The transformations to reverse",
            AllowedTypes = new[] { typeof(StarlarkSequence) })]
        StarlarkSequence transforms)
    {
        var builder = ImmutableArray.CreateBuilder<ITransformation>();
        foreach (var t in transforms)
        {
            try
            {
                builder.Add(Transformations.ToTransformation(t, "transformations", _printHandler).Reverse());
            }
            catch (NonReversibleValidationException e)
            {
                throw StarlarkRt.Errorf("{0} at {1}", e.Message, GetLocation(t));
            }
        }

        var reversed = builder.ToImmutable();
        return StarlarkList.ImmutableCopyOf(reversed.Reverse().Cast<object?>());
    }

    private static Location GetLocation(object? transformationOrCallable) =>
        transformationOrCallable switch
        {
            IStarlarkCallable callable => callable.Location,
            ITransformation transformation => transformation.Location(),
            _ => Location.BUILTIN,
        };

    [StarlarkMethod("workflow",
        Doc = "Defines a migration pipeline which can be invoked via the Copybara command.",
        UseStarlarkThread = true)]
    public void Workflow(
        [Param(Name = "name", Named = true, Positional = false, Doc = "The name of the workflow.")]
        string workflowName,
        [Param(Name = "origin", Named = true, Positional = false,
            Doc = "Where to read from the code to be migrated, before applying the transformations.")]
        object origin,
        [Param(Name = "destination", Named = true, Positional = false,
            Doc = "Where to write to the code being migrated, after applying the transformations.")]
        object destination,
        [Param(Name = "authoring", Named = true, Positional = false,
            Doc = "The author mapping configuration from origin to destination.")]
        AuthoringType authoring,
        [Param(Name = "transformations", Named = true, Positional = false, DefaultValue = "[]",
            Doc = "The transformations to be run for this workflow. They will run in sequence.")]
        StarlarkSequence transformations,
        [Param(Name = "origin_files", Named = true, Positional = false, DefaultValue = "None",
            Doc = "A glob or list of files relative to the workdir that will be read from the origin.",
            AllowedTypes = new[] { typeof(Glob), typeof(StarlarkList), typeof(NoneType) })]
        object originFiles,
        [Param(Name = "destination_files", Named = true, Positional = false, DefaultValue = "None",
            Doc = "A glob relative to the root of the destination repository.",
            AllowedTypes = new[] { typeof(Glob), typeof(StarlarkList), typeof(NoneType) })]
        object destinationFiles,
        [Param(Name = "mode", Named = true, Positional = false, DefaultValue = "\"SQUASH\"",
            Doc = "Workflow mode: SQUASH, ITERATIVE, CHANGE_REQUEST, CHANGE_REQUEST_FROM_SOT.")]
        string modeStr,
        [Param(Name = "reversible_check", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Indicates if the tool should try to reverse all the transformations at the end.",
            AllowedTypes = new[] { typeof(bool), typeof(NoneType) })]
        object reversibleCheckObj,
        [Param(Name = CheckLastRevStateName, Named = true, Positional = false, DefaultValue = "False",
            Doc = "If set to true, Copybara will validate that the destination didn't change.")]
        bool checkLastRevState,
        [Param(Name = "ask_for_confirmation", Named = true, Positional = false, DefaultValue = "False",
            Doc = "Show the diff and require the user's confirmation before making a change.")]
        bool askForConfirmation,
        [Param(Name = "dry_run", Named = true, Positional = false, DefaultValue = "False",
            Doc = "Run the migration in dry-run mode.")]
        bool dryRunMode,
        [Param(Name = "after_migration", Named = true, Positional = false, DefaultValue = "[]",
            Doc = "Run a feedback workflow after one migration happens.")]
        StarlarkSequence afterMigrations,
        [Param(Name = "after_workflow", Named = true, Positional = false, DefaultValue = "[]",
            Doc = "Run a feedback workflow after all the changes for this workflow run are migrated.")]
        StarlarkSequence afterAllMigrations,
        [Param(Name = "change_identity", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Customize the identity hash generation.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object changeIdentityObj,
        [Param(Name = "set_rev_id", Named = true, Positional = false, DefaultValue = "True",
            Doc = "Whether Copybara adds labels like 'GitOrigin-RevId' in the destination.")]
        bool setRevId,
        [Param(Name = "smart_prune", Named = true, Positional = false, DefaultValue = "False",
            Doc = "Best-effort approach at restoring the non-affected snippets.")]
        bool smartPrune,
        [Param(Name = "merge_import", Named = true, Positional = false, DefaultValue = "None",
            Doc = "A migration mode that shells out to a diffing tool to merge all files.",
            AllowedTypes = new[] { typeof(bool), typeof(MergeImportConfiguration), typeof(NoneType) })]
        object mergeImportObj,
        [Param(Name = "autopatch_config", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Configuration that describes the setting for automatic patch file generation.",
            AllowedTypes = new[] { typeof(AutoPatchfileConfiguration), typeof(NoneType) })]
        object autoPatchFileConfigurationObj,
        [Param(Name = "after_merge_transformations", Named = true, Positional = false, DefaultValue = "[]",
            Doc = "Perform these transformations after merge_import.")]
        StarlarkSequence afterMergeTransformations,
        [Param(Name = "migrate_noop_changes", Named = true, Positional = false, DefaultValue = "False",
            Doc = "Include all the changes, not only those affecting origin_files or config files.")]
        bool migrateNoopChanges,
        [Param(Name = "experimental_custom_rev_id", Named = true, Positional = false, DefaultValue = "None",
            Doc = "DEPRECATED. Use custom_rev_id.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object experimentalCustomRevIdField,
        [Param(Name = "custom_rev_id", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Use this label name instead of the one provided by the origin.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object customRevIdField,
        [Param(Name = "description", Named = true, Positional = false, DefaultValue = "None",
            Doc = "A description of what this workflow achieves",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object description,
        [Param(Name = "checkout", Named = true, Positional = false, DefaultValue = "True",
            Doc = "Allows disabling the checkout.")]
        bool checkout,
        [Param(Name = "reversible_check_ignore_files", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Ignore the files matching the glob in the reversible check",
            AllowedTypes = new[] { typeof(Glob), typeof(StarlarkList), typeof(NoneType) })]
        object reversibleCheckIgnoreFiles,
        [Param(Name = "consistency_file_path", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Under development. Must end with .bara.consistency",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object consistencyFilePathObj,
        [Param(Name = "consistency_file", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Consistency file configuration. Can be a boolean or a consistency_file_config object.",
            AllowedTypes = new[] { typeof(bool), typeof(ConsistencyFileConfiguration), typeof(NoneType) })]
        object consistencyFileObj,
        StarlarkThread thread)
    {
        var mode = SkylarkUtil.StringToEnum<WorkflowMode>("mode", modeStr);

        // Overwrite destination for testing workflow locally.
        if (_workflowOptions.ToFolder)
        {
            destination = _folderModule.Destination();
        }

        var sequenceTransform =
            Copybara.Transform.Sequence.FromConfig(
                _generalOptions.Profiler(),
                null,
                _workflowOptions,
                transformations,
                "transformations",
                _printHandler,
                _debugOptions.TransformWrapper,
                Copybara.Transform.Sequence.NoopBehavior.NOOP_IF_ANY_NOOP);

        ITransformation? reverseTransform = null;
        if (!_generalOptions.IsDisableReversibleCheck()
            && SkylarkUtil.ConvertFromNoneable(reversibleCheckObj, mode == WorkflowMode.ChangeRequest))
        {
            try
            {
                reverseTransform = sequenceTransform.Reverse();
            }
            catch (NonReversibleValidationException e)
            {
                throw StarlarkRt.Errorf("{0}", e.Message);
            }
        }

        var changeIdentity = GetChangeIdentity(changeIdentityObj);

        if (!StarlarkRt.IsNullOrNone(experimentalCustomRevIdField))
        {
            _generalOptions.GetConsole()
                .Warn("experimental_custom_rev_id is deprecated. Use custom_rev_id instead.");
        }

        string? customRevId = SkylarkUtil.ConvertFromNoneable<string?>(
            customRevIdField, SkylarkUtil.ConvertFromNoneable<string?>(experimentalCustomRevIdField, null));

        SkylarkUtil.Check(
            customRevId == null || CustomRevidFormat.IsMatch(customRevId),
            "Invalid custom_rev_id format. Format: {0}",
            CustomRevidFormat.ToString());

        if (setRevId)
        {
            SkylarkUtil.Check(
                mode != WorkflowMode.ChangeRequest || customRevId == null,
                "custom_rev_id is not allowed to be used in CHANGE_REQUEST mode if set_rev_id is set"
                    + " to true. custom_rev_id is used for looking for the baseline in the origin. No"
                    + " revId is stored in the destination.");
        }
        else
        {
            SkylarkUtil.Check(
                mode == WorkflowMode.ChangeRequest || mode == WorkflowMode.ChangeRequestFromSot,
                "'set_rev_id = False' is only supported for CHANGE_REQUEST and"
                    + " CHANGE_REQUEST_FROM_SOT mode.");
        }

        if (smartPrune)
        {
            SkylarkUtil.Check(
                mode == WorkflowMode.ChangeRequest,
                "'smart_prune = True' is only supported for CHANGE_REQUEST mode.");
        }

        if (checkLastRevState)
        {
            SkylarkUtil.Check(
                mode != WorkflowMode.ChangeRequest,
                "{0} is not compatible with {1}",
                CheckLastRevStateName,
                WorkflowMode.ChangeRequest);
        }

        var resolvedAuthoring = authoring;
        var defaultAuthorFlag = _workflowOptions.GetDefaultAuthorFlag();
        if (defaultAuthorFlag != null)
        {
            resolvedAuthoring =
                new AuthoringType(defaultAuthorFlag, authoring.GetMode(), authoring.GetAllowPredicate());
        }

        string? consistencyFilePath = SkylarkUtil.ConvertFromNoneable<string?>(consistencyFilePathObj, null);
        MergeImportConfiguration? mergeImport;
        if (mergeImportObj is bool objectValue)
        {
            mergeImport =
                objectValue
                    ? MergeImportConfiguration.Create(
                        "",
                        Glob.AllFiles,
                        !string.IsNullOrEmpty(consistencyFilePath),
                        MergeImportConfiguration.MergeStrategy.DIFF3)
                    : null;
        }
        else
        {
            mergeImport = SkylarkUtil.ConvertFromNoneable<MergeImportConfiguration?>(mergeImportObj, null);
        }

        var consistencyConfig = ResolveConsistencyFileConfig(consistencyFileObj, consistencyFilePath);

        if (mergeImport != null && mergeImport.UseConsistencyFile())
        {
            SkylarkUtil.Check(
                consistencyConfig != null,
                "error: use_consistency_file set but consistency_file.path is null");
        }

        if (consistencyConfig != null && mergeImport != null)
        {
            SkylarkUtil.Check(
                mergeImport.UseConsistencyFile(),
                "error: consistency_file.path set and merge import is enabled, but"
                    + " use_consistency_file in merge_import is false");
        }

        var autoPatchfileConfiguration =
            SkylarkUtil.ConvertFromNoneable<AutoPatchfileConfiguration?>(autoPatchFileConfigurationObj, null);

        var effectiveMode =
            _generalOptions.Squash || _workflowOptions.ImportSameVersion ? WorkflowMode.Squash : mode;

        // TODO(port): reconcile — Debug.getCallStack / captureDefinitionStackLocals depend on the
        // Starlark Debug helper. Left as empty until reconciled with the Workflow port.
        var locals = ImmutableArray<ImmutableDictionary<string, string>>.Empty;

        // Mirrors Java's `new Workflow<>(...)` where origin/destination arrive as untyped Starlark
        // objects. The non-generic factory reflects the concrete revision types out of origin and
        // destination and instantiates the correctly-typed Workflow<O, D>.
        var workflow =
            Copybara.Workflow.Create(
                workflowName,
                SkylarkUtil.ConvertFromNoneable<string?>(description, null),
                origin,
                destination,
                resolvedAuthoring,
                sequenceTransform,
                _workflowOptions.GetLastRevision(),
                _workflowOptions.IsInitHistory(),
                _generalOptions,
                Glob.WrapGlob(originFiles, Glob.AllFiles),
                Glob.WrapGlob(destinationFiles, Glob.AllFiles),
                effectiveMode,
                _workflowOptions,
                reverseTransform,
                Glob.WrapGlob(reversibleCheckIgnoreFiles, null),
                askForConfirmation,
                _mainConfigFile,
                _allConfigFiles,
                dryRunMode,
                checkLastRevState || _workflowOptions.CheckLastRevState,
                ConvertListOfActions(afterMigrations, _printHandler),
                ConvertListOfActions(afterAllMigrations, _printHandler),
                changeIdentity,
                setRevId,
                smartPrune,
                mergeImport,
                autoPatchfileConfiguration,
                AsSingleTransform(afterMergeTransformations),
                _workflowOptions.MigrateNoopChanges || migrateNoopChanges,
                customRevId,
                checkout,
                consistencyConfig,
                _workflowOptions.ExpectedFixedRef,
                _workflowOptions.PinnedFixedRef,
                thread.GetCallStack(),
                locals);

        var module = Module.OfInnermostEnclosingStarlarkFunction(thread)!;
        RegisterGlobalMigration(workflowName, workflow, module);
    }

    private Copybara.Transform.Sequence AsSingleTransform(StarlarkSequence transformations) =>
        Copybara.Transform.Sequence.FromConfig(
            _generalOptions.Profiler(),
            null,
            _workflowOptions,
            transformations,
            "transformations",
            _printHandler,
            _debugOptions.TransformWrapper,
            Copybara.Transform.Sequence.NoopBehavior.NOOP_IF_ANY_NOOP);

    private static ImmutableArray<Token> GetChangeIdentity(object changeIdentityObj)
    {
        string? changeIdentity = SkylarkUtil.ConvertFromNoneable<string?>(changeIdentityObj, null);

        if (changeIdentity == null)
        {
            return ImmutableArray<Token>.Empty;
        }

        var result = new Parser().Parse(changeIdentity);
        bool configVarFound = false;
        foreach (var token in result)
        {
            if (token.GetTokenType() != TokenType.Interpolation)
            {
                continue;
            }

            if (token.GetValue() == Copybara.Workflow.COPYBARA_CONFIG_PATH_IDENTITY_VAR)
            {
                configVarFound = true;
                continue;
            }

            if (token.GetValue() == Copybara.Workflow.COPYBARA_WORKFLOW_NAME_IDENTITY_VAR
                || token.GetValue() == Copybara.Workflow.COPYBARA_REFERENCE_IDENTITY_VAR
                || token.GetValue().StartsWith(Copybara.Workflow.COPYBARA_REFERENCE_LABEL_VAR))
            {
                continue;
            }

            throw StarlarkRt.Errorf("Unrecognized variable: {0}", token.GetValue());
        }

        SkylarkUtil.Check(
            configVarFound,
            "${{{0}}} variable is required",
            Copybara.Workflow.COPYBARA_CONFIG_PATH_IDENTITY_VAR);
        return result.ToImmutableArray();
    }

    [StarlarkMethod("move",
        Doc = "Moves files between directories and renames files",
        UseStarlarkThread = true)]
    public ITransformation Move(
        [Param(Name = "before", Named = true,
            Doc = "The name of the file or directory before moving.")]
        string before,
        [Param(Name = "after", Named = true,
            Doc = "The name of the file or directory after moving.")]
        string after,
        [Param(Name = "paths", Named = true, DefaultValue = "None",
            Doc = "A glob expression relative to 'before' if it represents a directory.",
            AllowedTypes = new[] { typeof(Glob), typeof(StarlarkList), typeof(NoneType) })]
        object paths,
        [Param(Name = "overwrite", Named = true, DefaultValue = "False",
            Doc = "Overwrite destination files if they already exist.")]
        bool overwrite,
        [Param(Name = "regex_groups", Named = true, Positional = false, DefaultValue = "{}",
            Doc = "A set of named regexes that can be used to match part of the file name.")]
        Dict regexes,
        StarlarkThread thread)
    {
        SkylarkUtil.Check(
            before != after,
            "Moving from the same folder to the same folder is a noop. Remove the transformation.");

        return CopyOrMove.CreateMove(
            before,
            after,
            SkylarkUtil.ConvertStringMap(regexes, "regex_groups"),
            Glob.WrapGlob(paths, Glob.AllFiles),
            overwrite,
            thread.GetCallerLocation());
    }

    [StarlarkMethod("rename",
        Doc =
            "A transformation for renaming several filenames in the working directory. This is a"
            + " simplified version of core.move() for just renaming filenames.",
        UseStarlarkThread = true)]
    public ITransformation Rename(
        [Param(Name = "before", Named = true, Doc = "The filepath or suffix to change")]
        string before,
        [Param(Name = "after", Named = true, Doc = "A filepath or suffix to use as replacement")]
        string after,
        [Param(Name = "paths", Named = true, DefaultValue = "None",
            Doc = "A glob expression relative to 'before' if it represents a directory.",
            AllowedTypes = new[] { typeof(Glob), typeof(StarlarkList), typeof(NoneType) })]
        object paths,
        [Param(Name = "overwrite", Named = true, DefaultValue = "False",
            Doc = "Overwrite destination files if they already exist.")]
        bool overwrite,
        [Param(Name = "suffix", Named = true, DefaultValue = "False",
            Doc = "When set to true, it will match partial parts of the path string.")]
        bool suffix,
        StarlarkThread thread)
    {
        SkylarkUtil.Check(
            before != after,
            "Renaming from the same filename to the same filename is a noop. Remove the"
                + " transformation.");

        // TODO(port): reconcile — Rename transform is being ported concurrently.
        return new Copybara.Transform.Rename(
            before,
            after,
            Glob.WrapGlob(paths, Glob.AllFiles),
            overwrite,
            suffix,
            thread.GetCallerLocation());
    }

    [StarlarkMethod("copy",
        Doc = "Copy files between directories and renames files",
        UseStarlarkThread = true)]
    public ITransformation Copy(
        [Param(Name = "before", Named = true, Doc = "The name of the file or directory to copy.")]
        string before,
        [Param(Name = "after", Named = true, Doc = "The name of the file or directory destination.")]
        string after,
        [Param(Name = "paths", Named = true, DefaultValue = "None",
            Doc = "A glob expression relative to 'before' if it represents a directory.",
            AllowedTypes = new[] { typeof(Glob), typeof(StarlarkList), typeof(NoneType) })]
        object paths,
        [Param(Name = "overwrite", Named = true, DefaultValue = "False",
            Doc = "Overwrite destination files if they already exist.")]
        bool overwrite,
        [Param(Name = "regex_groups", Named = true, Positional = false, DefaultValue = "{}",
            Doc = "A set of named regexes that can be used to match part of the file name.")]
        Dict regexes,
        StarlarkThread thread)
    {
        SkylarkUtil.Check(
            before != after,
            "Copying from the same folder to the same folder is a noop. Remove the transformation.");
        return CopyOrMove.CreateCopy(
            before,
            after,
            SkylarkUtil.ConvertStringMap(regexes, "regex_groups"),
            Glob.WrapGlob(paths, Glob.AllFiles),
            overwrite,
            thread.GetCallerLocation());
    }

    [StarlarkMethod("remove",
        Doc =
            "Remove files from the workdir. **This transformation is only meant to be used inside"
            + " core.transform for reversing core.copy like transforms**.",
        UseStarlarkThread = true)]
    public Copybara.Transform.Remove Remove(
        [Param(Name = "paths", Named = true, Doc = "The files to be deleted")]
        Glob paths,
        StarlarkThread thread) =>
        // TODO(port): reconcile — Remove transform is being ported concurrently.
        new(paths, thread.GetCallerLocation());

    [StarlarkMethod("convert_encoding",
        Doc = "Change the encoding for a set of files",
        UseStarlarkThread = true)]
    public ITransformation ConvertEncoding(
        [Param(Name = "before", Named = true,
            Doc = "The expected encoding of the files before transformation.")]
        string before,
        [Param(Name = "after", Named = true, Doc = "The encoding to convert to.")]
        string after,
        [Param(Name = "paths", Named = true, Doc = "The files to be converted")]
        Glob paths,
        StarlarkThread thread)
    {
        System.Text.Encoding cBefore;
        try
        {
            cBefore = System.Text.Encoding.GetEncoding(before);
        }
        catch (ArgumentException e)
        {
            throw new EvalException("Incorrect charset " + before + " for 'before': " + e.Message);
        }

        System.Text.Encoding cAfter;
        try
        {
            cAfter = System.Text.Encoding.GetEncoding(after);
        }
        catch (ArgumentException e)
        {
            throw new EvalException("Incorrect charset " + after + " for 'after': " + e.Message);
        }

        // TODO(port): reconcile — ConvertEncoding transform is being ported concurrently.
        return new Copybara.ConvertEncoding(cBefore, cAfter, paths);
    }

    [StarlarkMethod("replace",
        Doc =
            "Replace a text with another text using optional regex groups. This transformation can"
            + " be automatically reversed.",
        UseStarlarkThread = true)]
    public Replace Replace(
        [Param(Name = "before", Named = true,
            Doc = "The text before the transformation. Can contain references to regex groups.")]
        string before,
        [Param(Name = "after", Named = true,
            Doc = "The text after the transformation.")]
        string after,
        [Param(Name = "regex_groups", Named = true, DefaultValue = "{}",
            Doc = "A set of named regexes that can be used to match part of the replaced text.")]
        Dict regexes,
        [Param(Name = "paths", Named = true, DefaultValue = "None",
            Doc = "A glob expression relative to the workdir representing the files to apply.",
            AllowedTypes = new[] { typeof(Glob), typeof(StarlarkList), typeof(NoneType) })]
        object paths,
        [Param(Name = "first_only", Named = true, DefaultValue = "False",
            Doc = "If true, only replaces the first instance rather than all.")]
        bool firstOnly,
        [Param(Name = "multiline", Named = true, DefaultValue = "False",
            Doc = "Whether to replace text that spans more than one line.")]
        bool multiline,
        [Param(Name = "repeated_groups", Named = true, DefaultValue = "False",
            Doc = "Allow to use a group multiple times.")]
        bool repeatedGroups,
        [Param(Name = "ignore", Named = true, DefaultValue = "[]",
            Doc = "A set of regexes to ignore lines/files.")]
        StarlarkSequence ignore,
        StarlarkThread thread) =>
        Copybara.Transform.Replace.Create(
            thread.GetCallerLocation(),
            before,
            after,
            SkylarkUtil.ConvertStringMap(regexes, "regex_groups"),
            Glob.WrapGlob(paths, Glob.AllFiles),
            firstOnly,
            multiline,
            repeatedGroups,
            SkylarkUtil.ConvertStringList(ignore, "patterns_to_ignore"),
            _workflowOptions);

    [StarlarkMethod("todo_replace",
        Doc = "Replace Google style TODOs. For example `TODO(username, othername)`.",
        UseStarlarkThread = true)]
    public TodoReplace TodoReplace(
        [Param(Name = "tags", Named = true, DefaultValue = "['TODO', 'NOTE']",
            Doc = "Prefix tag to look for",
            AllowedTypes = new[] { typeof(StarlarkSequence) })]
        StarlarkSequence skyTags,
        [Param(Name = "mapping", Named = true, DefaultValue = "{}",
            Doc = "Mapping of users/strings")]
        Dict skyMapping,
        [Param(Name = "mode", Named = true, DefaultValue = "'MAP_OR_IGNORE'",
            Doc = "Mode for the replace.")]
        string modeStr,
        [Param(Name = "paths", Named = true, DefaultValue = "None",
            Doc = "A glob expression relative to the workdir representing the files to apply.",
            AllowedTypes = new[] { typeof(Glob), typeof(StarlarkList), typeof(NoneType) })]
        object paths,
        [Param(Name = "default", Named = true, DefaultValue = "None",
            Doc = "Default value if mapping not found.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object skyDefault,
        [Param(Name = "ignore", Named = true, DefaultValue = "None",
            Doc = "If set, elements within TODO that match the regex will be ignored.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object regexToIgnore,
        StarlarkThread thread)
    {
        var mode = SkylarkUtil.StringToEnum<Copybara.Transform.TodoReplace.Mode>("mode", modeStr);
        var mapping = SkylarkUtil.ConvertStringMap(skyMapping, "mapping");
        string? defaultString = SkylarkUtil.ConvertFromNoneable<string?>(skyDefault, null);
        var tags = SkylarkUtil.ConvertStringList(skyTags, "tags").ToImmutableArray();
        string? ignorePattern = SkylarkUtil.ConvertFromNoneable<string?>(regexToIgnore, null);
        Regex? regexIgnorelist = ignorePattern != null ? new Regex(ignorePattern) : null;

        SkylarkUtil.Check(tags.Length != 0, "'tags' cannot be empty");
        if (mode is Copybara.Transform.TodoReplace.Mode.MAP_OR_DEFAULT
            or Copybara.Transform.TodoReplace.Mode.USE_DEFAULT)
        {
            SkylarkUtil.Check(defaultString != null, "'default' needs to be set for mode '{0}'", mode);
        }
        else
        {
            SkylarkUtil.Check(defaultString == null, "'default' cannot be used for mode '{0}'", mode);
        }

        if (mode is Copybara.Transform.TodoReplace.Mode.USE_DEFAULT
            or Copybara.Transform.TodoReplace.Mode.SCRUB_NAMES)
        {
            SkylarkUtil.Check(mapping.Count == 0, "'mapping' cannot be used with mode {0}", mode);
        }

        return new Copybara.Transform.TodoReplace(
            thread.GetCallerLocation(),
            Glob.WrapGlob(paths, Glob.AllFiles),
            tags,
            mode,
            mapping,
            defaultString,
            _workflowOptions.Parallelizer(),
            regexIgnorelist);
    }

    public const string TodoFilterReplaceExample =
        "core.filter_replace(\n"
        + "    regex = 'TODO\\\\((.*?)\\\\)',\n"
        + "    group = 1,\n"
        + "        mapping = core.replace_mapper([\n"
        + "            core.replace(\n"
        + "                before = '${p}foo${s}',\n"
        + "                after = '${p}fooz${s}',\n"
        + "                regex_groups = { 'p': '.*', 's': '.*'}\n"
        + "            ),\n"
        + "        ],\n"
        + "        all = True\n"
        + "    )\n"
        + ")";

    public const string SimpleFilterReplaceExample =
        "core.filter_replace(\n"
        + "    regex = 'a.*',\n"
        + "    mapping = {\n"
        + "        'afoo': 'abar',\n"
        + "        'abaz': 'abam'\n"
        + "    }\n"
        + ")";

    [StarlarkMethod("filter_replace",
        Doc =
            "Applies an initial filtering to find a substring to be replaced and then applies a"
            + " `mapping` of replaces for the matched text.",
        UseStarlarkThread = true)]
    public FilterReplace FilterReplace(
        [Param(Name = "regex", Named = true, Doc = "A re2 regex to match a substring of the file",
            AllowedTypes = new[] { typeof(string) })]
        string regex,
        [Param(Name = "mapping", Named = true, DefaultValue = "{}",
            Doc = "A mapping function like core.replace_mapper or a dict with mapping values.")]
        object mapping,
        [Param(Name = "group", Named = true, DefaultValue = "None",
            Doc = "Extract a regex group from the matching text.",
            AllowedTypes = new[] { typeof(StarlarkInt), typeof(NoneType) })]
        object group,
        [Param(Name = "paths", Named = true, DefaultValue = "None",
            Doc = "A glob expression relative to the workdir representing the files to apply.",
            AllowedTypes = new[] { typeof(Glob), typeof(StarlarkList), typeof(NoneType) })]
        object paths,
        [Param(Name = "reverse", Named = true, DefaultValue = "None",
            Doc = "A re2 regex used as reverse transformation",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object reverse,
        StarlarkThread thread)
    {
        var func = GetMappingFunction(mapping);

        string afterPattern = SkylarkUtil.ConvertFromNoneable(reverse, regex);
        int numGroup = SkylarkUtil.ConvertFromNoneable(group, StarlarkInt.Of(0)).ToInt("group");
        var beforeRegex = new Regex(regex);
        int beforeGroups = beforeRegex.GetGroupNumbers().Length - 1;
        SkylarkUtil.Check(
            numGroup <= beforeGroups,
            "group idx is greater than the number of groups defined in '{0}'. Regex has {1} groups",
            beforeRegex.ToString(),
            beforeGroups);
        var afterRegex = new Regex(afterPattern);
        int afterGroups = afterRegex.GetGroupNumbers().Length - 1;
        SkylarkUtil.Check(
            numGroup <= afterGroups,
            "reverse_group idx is greater than the number of groups defined in '{0}'. Regex has {1}"
                + " groups",
            afterRegex.ToString(),
            afterGroups);
        return new FilterReplace(
            _workflowOptions,
            beforeRegex,
            afterRegex,
            numGroup,
            numGroup,
            func,
            Glob.WrapGlob(paths, Glob.AllFiles),
            thread.GetCallerLocation());
    }

    public static IReversibleFunction<string, string> GetMappingFunction(object mapping)
    {
        if (mapping is Dict dict)
        {
            var map = SkylarkUtil.ConvertStringMap(dict, "mapping");
            SkylarkUtil.Check(
                map.Count != 0, "Empty mapping is not allowed. Remove the transformation instead");
            return new MapMapper(map.ToImmutableDictionary());
        }

        SkylarkUtil.Check(
            mapping is IReversibleFunction<string, string>,
            "mapping has to be instance of map or a reversible function");
        return (IReversibleFunction<string, string>)mapping;
    }

    [StarlarkMethod("replace_mapper",
        Doc =
            "A mapping function that applies a list of replaces until one replaces the text"
            + " (Unless `all = True` is used).")]
    public ReplaceMapper MapImports(
        [Param(Name = "mapping", Named = true,
            Doc = "The list of core.replace transformations",
            AllowedTypes = new[] { typeof(StarlarkSequence) })]
        StarlarkSequence mapping,
        [Param(Name = "all", Named = true, Positional = false, DefaultValue = "False",
            Doc = "Run all the mappings despite a replace happens.")]
        bool all)
    {
        SkylarkUtil.Check(mapping.Count != 0, "Empty mapping is not allowed");
        var replaces = ImmutableArray.CreateBuilder<Replace>();
        foreach (var obj in mapping)
        {
            if (obj is not ITransformation t)
            {
                throw StarlarkRt.Errorf("Expected a transformation in 'mapping'");
            }

            SkylarkUtil.Check(
                t is Replace, "Only core.replace can be used as mapping, but got: {0}", t.Describe());
            var replace = (Replace)t;
            SkylarkUtil.Check(
                Equals(replace.GetPaths(), Glob.AllFiles),
                "core.replace cannot use 'paths' inside core.replace_mapper");
            replaces.Add(replace);
        }

        return new ReplaceMapper(replaces.ToImmutable(), all);
    }

    [StarlarkMethod("verify_match",
        Doc =
            "Verifies that a RegEx matches (or not matches) the specified files. Does not transform"
            + " anything, but will stop the workflow if it fails.",
        UseStarlarkThread = true)]
    public VerifyMatch VerifyMatch(
        [Param(Name = "regex", Named = true, Doc = "The regex pattern to verify.")]
        string regex,
        [Param(Name = "paths", Named = true, DefaultValue = "None",
            Doc = "A glob expression relative to the workdir representing the files to apply.",
            AllowedTypes = new[] { typeof(Glob), typeof(StarlarkList), typeof(NoneType) })]
        object paths,
        [Param(Name = "verify_no_match", Named = true, DefaultValue = "False",
            Doc = "If true, the transformation will verify that the RegEx does not match.")]
        bool verifyNoMatch,
        [Param(Name = "also_on_reversal", Named = true, DefaultValue = "False",
            Doc = "If true, the check will also apply on the reversal.")]
        bool alsoOnReversal,
        [Param(Name = "failure_message", Named = true, DefaultValue = "None",
            Doc = "Optional string that will be included in the failure message.")]
        object failureMessage,
        StarlarkThread thread) =>
        Copybara.Transform.VerifyMatch.Create(
            thread.GetCallerLocation(),
            regex,
            Glob.WrapGlob(paths, Glob.AllFiles),
            verifyNoMatch,
            alsoOnReversal,
            SkylarkUtil.ConvertOptionalString(failureMessage),
            _workflowOptions.Parallelizer());

    [StarlarkMethod("transform",
        Doc =
            "Groups some transformations in a transformation that can contain a particular,"
            + " manually-specified, reversal.")]
    public ITransformation Transform(
        [Param(Name = "transformations", Named = true,
            Doc = "The list of transformations to run as a result of running this transformation.",
            AllowedTypes = new[] { typeof(StarlarkSequence) })]
        StarlarkSequence transformations,
        [Param(Name = "reversal", Named = true, Positional = false, DefaultValue = "None",
            Doc = "The list of transformations to run in reverse.",
            AllowedTypes = new[] { typeof(StarlarkSequence), typeof(NoneType) })]
        object reversal,
        [Param(Name = "name", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Optional string identifier to name this transform.")]
        object name,
        [Param(Name = "ignore_noop", Named = true, Positional = false, DefaultValue = "None",
            Doc = "WARNING: Deprecated. Use `noop_behavior` instead.",
            AllowedTypes = new[] { typeof(bool), typeof(NoneType) })]
        object ignoreNoop,
        [Param(Name = "noop_behavior", Named = true, Positional = false, DefaultValue = "None",
            Doc = "How to handle no-op transformations.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object noopBehaviorString)
    {
        ValidationException.CheckCondition(
            StarlarkRt.IsNullOrNone(ignoreNoop) || StarlarkRt.IsNullOrNone(noopBehaviorString),
            "The deprecated param 'ignore_noop' cannot be set simultaneously with 'noop_behavior'."
                + " Prefer using 'noop_behavior'.");
        var noopBehavior =
            SkylarkUtil.StringToEnum<Copybara.Transform.Sequence.NoopBehavior>(
                "noop_behavior",
                SkylarkUtil.ConvertFromNoneable(noopBehaviorString, "NOOP_IF_ANY_NOOP"));
        if (ignoreNoop is true)
        {
            noopBehavior = Copybara.Transform.Sequence.NoopBehavior.IGNORE_NOOP;
        }
        else if (ignoreNoop is false)
        {
            noopBehavior = Copybara.Transform.Sequence.NoopBehavior.FAIL_IF_ANY_NOOP;
        }

        string? convertedName = SkylarkUtil.ConvertFromNoneable<string?>(name, null);
        if (!_transformNames.TryGetValue(_mainConfigFile, out var names))
        {
            names = new HashSet<string>();
            _transformNames[_mainConfigFile] = names;
        }

        if (convertedName != null && !names.Add(convertedName))
        {
            throw new ValidationException($"Name `{convertedName}` already used.");
        }

        var forward =
            Copybara.Transform.Sequence.FromConfig(
                _generalOptions.Profiler(),
                convertedName,
                _workflowOptions,
                transformations,
                "transformations",
                _printHandler,
                _debugOptions.TransformWrapper,
                noopBehavior);
        StarlarkSequence? reverseList = SkylarkUtil.ConvertFromNoneable<StarlarkSequence?>(reversal, null);
        if (reverseList == null)
        {
            try
            {
                reverseList = StarlarkList.ImmutableCopyOf(new object?[] { forward.Reverse() });
            }
            catch (NonReversibleValidationException)
            {
                throw StarlarkRt.Errorf(
                    "transformations are not automatically reversible. Use 'reversal' field to"
                        + " explicitly configure the reversal of the transform");
            }
        }

        var reverse =
            Copybara.Transform.Sequence.FromConfig(
                _generalOptions.Profiler(),
                convertedName,
                _workflowOptions,
                reverseList,
                "reversal",
                _printHandler,
                _debugOptions.TransformWrapper,
                noopBehavior);
        return new ExplicitReversal(forward, reverse);
    }

    [StarlarkMethod("dynamic_transform",
        Doc = "Create a dynamic Starlark transformation. This should only be used by library developers",
        UseStarlarkThread = true)]
    public ITransformation DynamicTransform(
        [Param(Name = "impl", Named = true, Doc = "The Starlark function to call")]
        IStarlarkCallable impl,
        [Param(Name = "params", Named = true, DefaultValue = "{}",
            Doc = "The parameters to the function. Will be available under ctx.params")]
        Dict @params,
        StarlarkThread thread) =>
        // TODO(port): reconcile — SkylarkTransformation is being ported concurrently.
        new SkylarkTransformation(impl, Dict.CopyOf(thread.Mutability, @params.Entries), _printHandler);

    [StarlarkMethod("dynamic_feedback",
        Doc = "Create a dynamic Starlark feedback migration. This should only be used by library developers",
        UseStarlarkThread = true)]
    public Copybara.Action.IAction DynamicFeedback(
        [Param(Name = "impl", Named = true, Doc = "The Starlark function to call")]
        IStarlarkCallable impl,
        [Param(Name = "params", Named = true, DefaultValue = "{}",
            Doc = "The parameters to the function. Will be available under ctx.params")]
        Dict @params,
        StarlarkThread thread) =>
        // TODO(port): reconcile — StarlarkAction is being ported concurrently.
        new Copybara.Action.StarlarkAction(
            FindCallableName(impl, thread), impl, Dict.CopyOf(thread.Mutability, @params.Entries),
            _printHandler);

    [StarlarkMethod("action",
        Doc = "Create a dynamic Starlark action. This should only be used by library developers.",
        UseStarlarkThread = true)]
    public Copybara.Action.IAction Action(
        [Param(Name = "impl", Named = true, Doc = "The Starlark function to call")]
        IStarlarkCallable impl,
        [Param(Name = "params", Named = true, DefaultValue = "{}",
            Doc = "The parameters to the function. Will be available under ctx.params")]
        Dict @params,
        StarlarkThread thread) =>
        new Copybara.Action.StarlarkAction(
            FindCallableName(impl, thread), impl, Dict.CopyOf(thread.Mutability, @params.Entries),
            _printHandler);

    private static string FindCallableName(IStarlarkCallable impl, StarlarkThread thread)
    {
        string name = impl.Name;
        var stack = thread.GetCallStack();
        if (name == "lambda" && stack.Length > 1
            && stack[stack.Length - 2].Name != "<toplevel>")
        {
            name = stack[stack.Length - 2].Name;
        }

        return name;
    }

    [StarlarkMethod("fail_with_noop",
        Doc = "If invoked, it will fail the current migration as a noop")]
    public Copybara.Action.IAction FailWithNoop(
        [Param(Name = "msg", Named = true, Doc = "The noop message")]
        string msg) =>
        throw new EmptyChangeException(msg);

    [StarlarkMethod("main_config_path",
        Doc = "Location of the config file. This is subject to change",
        StructField = true)]
    public string GetMainConfigFile() => _mainConfigFile.GetIdentifier();

    [StarlarkMethod("feedback",
        Doc =
            "Defines a migration of changes' metadata, that can be invoked via the Copybara command"
            + " in the same way as a regular workflow migrates the change itself.",
        UseStarlarkThread = true)]
    public NoneType Feedback(
        [Param(Name = "name", Named = true, Positional = false, Doc = "The name of the feedback workflow.")]
        string workflowName,
        [Param(Name = "origin", Named = true, Positional = false, Doc = "The trigger of a feedback migration.")]
        ITrigger trigger,
        [Param(Name = "destination", Named = true, Positional = false, Doc = "Where to write change metadata to.")]
        object destination,
        [Param(Name = "actions", Named = true, Positional = false, DefaultValue = "[]",
            Doc = "DEPRECATED: **DO NOT USE**")]
        StarlarkSequence actionList,
        [Param(Name = "action", Named = true, Positional = false, DefaultValue = "None",
            Doc = "An action to execute when the migration is triggered")]
        object action,
        [Param(Name = "description", Named = true, Positional = false, DefaultValue = "None",
            Doc = "A description of what this workflow achieves",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object description,
        StarlarkThread thread)
    {
        var destinationProvider = (Copybara.IEndpointProvider)destination;
        var migration =
            new ActionMigration(
                workflowName,
                SkylarkUtil.ConvertFromNoneable<string?>(description, null),
                _mainConfigFile,
                trigger,
                new StructImpl(
                    ImmutableDictionary<string, object?>.Empty.Add(
                        "destination", destinationProvider.GetEndpoint())),
                HandleActionActionsMigration(actionList, action),
                _generalOptions,
                "feedback",
                fileSystem: false,
                thread.GetCallStack());
        var module = Module.OfInnermostEnclosingStarlarkFunction(thread)!;
        RegisterGlobalMigration(workflowName, migration, module);
        return StarlarkRt.None;
    }

    private ImmutableArray<Copybara.Action.IAction> HandleActionActionsMigration(
        StarlarkSequence actionList, object action)
    {
        if (actionList.Count == 0 && ReferenceEquals(action, StarlarkRt.None))
        {
            throw new EvalException("'action' is a required field");
        }

        if (actionList.Count != 0 && !ReferenceEquals(action, StarlarkRt.None))
        {
            throw new EvalException(
                "Cannot use both 'action' and 'actions' field. 'actions' is deprecated, so use"
                    + " 'action'");
        }

        if (!ReferenceEquals(action, StarlarkRt.None))
        {
            return ImmutableArray.Create(MaybeWrapAction(_printHandler, action));
        }

        return ConvertListOfActions(actionList, _printHandler);
    }

    [StarlarkMethod("action_migration",
        Doc =
            "Defines a migration that is more flexible/less-opinionated migration than"
            + " `core.workflow`.",
        UseStarlarkThread = true)]
    public NoneType ActionMigrationMethod(
        [Param(Name = "name", Named = true, Positional = false, Doc = "The name of the migration.")]
        string workflowName,
        [Param(Name = "origin", Named = true, Positional = false,
            Doc = "The trigger endpoint of the migration. Accessible as `ctx.origin`")]
        ITrigger trigger,
        [Param(Name = "endpoints", Named = true, Positional = false,
            Doc = "One or more endpoints that the migration will have access to.")]
        IStructure endpoints,
        [Param(Name = "action", Named = true, Positional = false,
            Doc = "The action to execute when the migration is triggered.")]
        object action,
        [Param(Name = "description", Named = true, Positional = false, DefaultValue = "None",
            Doc = "A description of what this workflow achieves",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object description,
        [Param(Name = "filesystem", Named = true, Positional = false, DefaultValue = "False",
            Doc = "If true, the migration provides access to the filesystem to the endpoints",
            AllowedTypes = new[] { typeof(bool) })]
        bool filesystem,
        StarlarkThread thread)
    {
        var actions = ImmutableArray.Create(MaybeWrapAction(_printHandler, action));
        var endpointsMap = GetEndpoints(endpoints);
        if (!endpointsMap.ContainsKey(ActionMigration.DestinationEndpointName))
        {
            throw new EvalException("Action migration must have an endpoint named 'destination'.");
        }

        var migration =
            new ActionMigration(
                workflowName,
                SkylarkUtil.ConvertFromNoneable<string?>(description, null),
                _mainConfigFile,
                trigger,
                new StructImpl(endpointsMap),
                actions,
                _generalOptions,
                "action_migration",
                filesystem,
                thread.GetCallStack());
        var module = Module.OfInnermostEnclosingStarlarkFunction(thread)!;
        RegisterGlobalMigration(workflowName, migration, module);
        return StarlarkRt.None;
    }

    private static ImmutableDictionary<string, object?> GetEndpoints(IStructure endpoints)
    {
        var result = ImmutableDictionary.CreateBuilder<string, object?>();
        foreach (var fieldName in endpoints.GetFieldNames())
        {
            var epProvider = endpoints.GetValue(fieldName);
            SkylarkUtil.Check(
                epProvider is Copybara.IEndpointProvider,
                "Only endpoints can be used as values in 'endpoints' but got type '{0}' for {1}",
                StarlarkRt.Type(epProvider),
                fieldName);
            result[fieldName] = ((Copybara.IEndpointProvider)epProvider!).GetEndpoint();
        }

        return result.ToImmutable();
    }

    [StarlarkMethod("console",
        StructField = true,
        Doc = "Returns a handle to the console object.")]
    public SkylarkConsole Console()
    {
        lock (_consoleLock)
        {
            _console ??= new SkylarkConsole(_generalOptions.GetConsole());
        }

        return _console;
    }

    /// <summary>Registers a <see cref="IMigration"/> in the global registry.</summary>
    protected void RegisterGlobalMigration(string name, IMigration migration, Module module) =>
        GlobalMigrations.GetGlobalMigrations(module).AddMigration(name, migration);

    [StarlarkMethod("format",
        Doc = "Formats a String using Java's String.format style.")]
    public string Format(
        [Param(Name = "format", Named = true, Doc = "The format string")]
        string format,
        [Param(Name = "args", Named = true, Doc = "The arguments to format")]
        StarlarkSequence args)
    {
        // Convert StarlarkInt to types known to the formatter.
        var array = args.ToArray();
        for (int i = 0; i < array.Length; i++)
        {
            if (array[i] is StarlarkInt si)
            {
                array[i] = si.ToNumber();
            }
        }

        try
        {
            // NOTE(port): upstream uses Java's String.format ('%s'); this port uses .NET composite
            // formatting ('{0}'). Accepted deviation — see CLAUDE.md.
            return string.Format(format, array);
        }
        catch (FormatException e)
        {
            throw StarlarkRt.Errorf("Invalid format: {0}: {1}", format, e.Message);
        }
    }

    // TODO(port): reconcile — the Copybara.Version namespace (IVersionSelector,
    // CustomVersionSelector, LatestVersionSelector, OrderedVersionSelector,
    // RequestedVersionSelector) is being ported by another agent; these two methods forward-reference
    // it and will compile once that scope lands.
    [StarlarkMethod("custom_version_selector",
        Doc =
            "This is experimental: Custom version selector, users are able to define their own"
            + " sorting comparator and filter candidates by regex.",
        UseStarlarkThread = true)]
    public Copybara.Version.IVersionSelector CustomVersionSelector(
        [Param(Name = "comparator", Named = true,
            Doc = "A callable comparator of two strings.",
            AllowedTypes = new[] { typeof(IStarlarkCallable) })]
        IStarlarkCallable comparator,
        [Param(Name = "regex_filter", Named = true, DefaultValue = "None",
            Doc = "Only versions that match this regex will be considered.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object rawRegexFilter,
        StarlarkThread thread)
    {
        string? filterByRegex = SkylarkUtil.ConvertFromNoneable<string?>(rawRegexFilter, null);
        // TODO(port): reconcile — CustomVersionSelector is being ported concurrently.
        return new Copybara.Version.CustomVersionSelector(comparator, filterByRegex);
    }

    [StarlarkMethod("latest_version",
        Doc =
            "Selects the latest version that matches the format. Using --force in the CLI will force"
            + " to use the reference passed as argument instead.",
        UseStarlarkThread = true)]
    public Copybara.Version.IVersionSelector VersionSelector(
        [Param(Name = "format", Named = true, Doc = "The format of the version.")]
        string regex,
        [Param(Name = "regex_groups", Named = true, DefaultValue = "{}",
            Doc = "A set of named regexes that can be used to match part of the versions.")]
        Dict groups,
        StarlarkThread thread)
    {
        var groupsMap = SkylarkUtil.ConvertStringMap(groups, "regex_groups");

        var elements = new SortedDictionary<int, Copybara.Version.LatestVersionSelector.VersionElementType>();
        var regexKey = new Regex("^([sn])([0-9])$");
        foreach (var s in groupsMap.Keys)
        {
            var matcher = regexKey.Match(s);
            SkylarkUtil.Check(
                matcher.Success,
                "Incorrect key for regex_group. Should be in the format of n0, n1, etc. or s0, s1,"
                    + " etc. Value: {0}",
                s);
            var type = matcher.Groups[1].Value == "s"
                ? Copybara.Version.LatestVersionSelector.VersionElementType.ALPHABETIC
                : Copybara.Version.LatestVersionSelector.VersionElementType.NUMERIC;
            int num = int.Parse(matcher.Groups[2].Value);
            SkylarkUtil.Check(
                !elements.ContainsKey(num) || elements[num] == type,
                "Cannot use same n in both s{0} and n{1}: {2}",
                num,
                num,
                s);
            elements[num] = type;
        }

        foreach (var num in elements.Keys)
        {
            if (num > 0)
            {
                SkylarkUtil.Check(
                    elements.ContainsKey(num - 1),
                    "Cannot have s{0} or n{1} if s{2} or n{3} doesn't exist",
                    num,
                    num,
                    num - 1,
                    num - 1);
            }
        }

        // TODO(port): reconcile — version selector types are being ported concurrently.
        var versionPicker = new Copybara.Version.LatestVersionSelector(
            regex, Copybara.Transform.Replace.ParsePatterns(groupsMap), elements, thread.GetCallerLocation());
        var extraGroups = versionPicker.GetUnmatchedGroups();
        SkylarkUtil.Check(
            extraGroups.Count == 0, "Extra regex_groups not used in pattern: {0}", extraGroups);
        if (_generalOptions.IsForced() || _generalOptions.IsVersionSelectorUseCliRef())
        {
            return new Copybara.Version.OrderedVersionSelector(
                ImmutableArray.Create<Copybara.Version.IVersionSelector>(
                    new Copybara.Version.RequestedVersionSelector(), versionPicker));
        }

        return versionPicker;
    }

    private static ImmutableArray<Copybara.Action.IAction> ConvertListOfActions(
        StarlarkSequence feedbackActions, StarlarkThread.PrintHandler? printHandler)
    {
        var actions = ImmutableArray.CreateBuilder<Copybara.Action.IAction>();
        foreach (var action in feedbackActions)
        {
            actions.Add(MaybeWrapAction(printHandler, action!));
        }

        return actions.ToImmutable();
    }

    private static Copybara.Action.IAction MaybeWrapAction(
        StarlarkThread.PrintHandler? printHandler, object action)
    {
        if (action is IStarlarkCallable callable)
        {
            return new Copybara.Action.StarlarkAction(
                callable.Name, callable, Dict.Empty(), printHandler);
        }

        if (action is Copybara.Action.IAction a)
        {
            return a;
        }

        throw StarlarkRt.Errorf("Invalid action '{0}' of type: {1}", action, action.GetType());
    }

    public void SetConfigFile(ConfigFile mainConfigFile, ConfigFile currentConfigFile) =>
        _mainConfigFile = mainConfigFile;

    public void SetAllConfigResources(Func<ImmutableDictionary<string, ConfigFile>> allConfigFiles) =>
        _allConfigFiles = allConfigFiles;

    public void SetPrintHandler(StarlarkThread.PrintHandler printHandler) =>
        _printHandler = printHandler;

    [StarlarkMethod("merge_import_config",
        Doc = "Describes which paths merge_import mode should be applied")]
    public MergeImportConfiguration MergeImportConfigurationMethod(
        [Param(Name = "package_path", Named = true, Positional = false,
            Doc = "Package location (ex. 'google3/third_party/java/foo').")]
        string packagePath,
        [Param(Name = "paths", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Glob of paths to apply merge_import mode, relative to package_path",
            AllowedTypes = new[] { typeof(Glob), typeof(StarlarkList), typeof(NoneType) })]
        object pathsObj,
        [Param(Name = "use_consistency_file", Named = true, Positional = false, DefaultValue = "False",
            Doc = "Deprecated. Use consistency_file in core.workflow instead.")]
        bool useConsistencyFile,
        [Param(Name = "merge_strategy", Named = true, Positional = false, DefaultValue = "'DIFF3'",
            Doc = "The strategy to use for merging files.")]
        string mergeStrategy)
    {
        var paths = Glob.WrapGlob(pathsObj, Glob.AllFiles);
        return MergeImportConfiguration.Create(
            packagePath,
            paths!,
            useConsistencyFile,
            Enum.Parse<MergeImportConfiguration.MergeStrategy>(mergeStrategy));
    }

    [StarlarkMethod("consistency_file_config",
        Doc = "Describes the configuration for consistency file options")]
    public ConsistencyFileConfiguration ConsistencyFileConfig(
        [Param(Name = "path", Named = true, Positional = false,
            DefaultValue = "\"do-not-edit.bara.consistency\"",
            Doc = "The path to the consistency file. Must end with .bara.consistency.",
            AllowedTypes = new[] { typeof(string) })]
        string path,
        [Param(Name = "exclude_build_files", Named = true, Positional = false, DefaultValue = "False",
            Doc = "Exclude BUILD files from being hashed in consistency files.")]
        bool excludeBuildFiles)
    {
        try
        {
            ValidationException.CheckCondition(
                path.EndsWith(".bara.consistency"),
                "Consistency file path must end with .bara.consistency");
        }
        catch (ValidationException e)
        {
            throw new EvalException(e.Message);
        }

        return ConsistencyFileConfiguration.Create(path, excludeBuildFiles);
    }

    [StarlarkMethod("autopatch_config",
        Doc = "Describes in the configuration for automatic patch file generation")]
    public AutoPatchfileConfiguration AutoPatchfileConfigurationMethod(
        [Param(Name = "header", Named = true, Positional = false, DefaultValue = "None",
            Doc = "A string to include at the beginning of each patch file",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object fileContentsPrefix,
        [Param(Name = "suffix", Named = true, Positional = false, DefaultValue = "'.patch'",
            Doc = "Suffix to use when saving patch files")]
        string suffix,
        [Param(Name = "directory_prefix", Named = true, Positional = false, DefaultValue = "''",
            Doc = "Directory prefix used to relativize filenames when writing patch files.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object directoryPrefix,
        [Param(Name = "directory", Named = true, Positional = false, DefaultValue = "'AUTOPATCHES'",
            Doc = "Directory in which to save the patch files.",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) })]
        object directory,
        [Param(Name = "strip_file_names_and_line_numbers", Named = true, Positional = false,
            DefaultValue = "False",
            Doc = "When true, strip filenames and line numbers from patch files")]
        bool stripFileNamesAndLineNumbers,
        [Param(Name = "strip_file_names", Named = true, Positional = false, DefaultValue = "False",
            Doc = "When true, strip filenames from patch files")]
        bool stripFileNames,
        [Param(Name = "strip_line_numbers", Named = true, Positional = false, DefaultValue = "False",
            Doc = "When true, strip line numbers from patch files")]
        bool stripLineNumbers,
        [Param(Name = "paths", Named = true, Positional = false, DefaultValue = "None",
            Doc = "Only create patch files that match glob. Default is to match all files",
            AllowedTypes = new[] { typeof(Glob), typeof(StarlarkList), typeof(NoneType) })]
        object globObj)
    {
        var glob = Glob.WrapGlob(globObj, Glob.AllFiles);

        if (stripFileNamesAndLineNumbers && (stripFileNames || stripLineNumbers))
        {
            throw StarlarkRt.Errorf(
                "Cannot set both strip_file_names_and_line_numbers and strip_file_names /"
                    + " strip_line_numbers");
        }

        if (stripFileNamesAndLineNumbers)
        {
            stripFileNames = true;
            stripLineNumbers = true;
        }

        return AutoPatchfileConfiguration.Create(
            SkylarkUtil.ConvertFromNoneable<string?>(fileContentsPrefix, null)!,
            suffix,
            SkylarkUtil.ConvertFromNoneable<string?>(directoryPrefix, null)!,
            SkylarkUtil.ConvertFromNoneable<string?>(directory, null),
            stripFileNames,
            stripLineNumbers,
            glob!);
    }

    private ConsistencyFileConfiguration? ResolveConsistencyFileConfig(
        object consistencyFileObj, string? consistencyFilePath)
    {
        ConsistencyFileConfiguration? consistencyConfig = null;
        object? consistencyFileVal = SkylarkUtil.ConvertFromNoneable<object?>(consistencyFileObj, null);
        if (consistencyFileVal is bool b)
        {
            consistencyConfig =
                b ? ConsistencyFileConfiguration.Create("do-not-edit.bara.consistency", false) : null;
        }
        else if (consistencyFileVal is ConsistencyFileConfiguration consistencyFileConfiguration)
        {
            consistencyConfig = consistencyFileConfiguration;
        }

        // Validation for mutual exclusivity.
        if (consistencyFilePath != null && consistencyFileVal != null)
        {
            throw StarlarkRt.Errorf(
                "Cannot use both 'consistency_file_path' and 'consistency_file' parameters in"
                    + " workflow.");
        }

        if (consistencyFilePath != null)
        {
            consistencyConfig = ConsistencyFileConfiguration.Create(consistencyFilePath, false);
        }

        return consistencyConfig;
    }
}
