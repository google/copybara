/*
 * Copyright (C) 2023 Google LLC
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
using Copybara;
using Copybara.Config;
using Copybara.Exceptions;
using Copybara.Revision;
using Copybara.Util;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Regenerate;

/// <summary>
/// Regenerate contains the implementation of the logic to checkout the correct versions of code and
/// calling the helper classes to diff and upload the contents.
///
/// <para>TODO(port): wire into Copybara.Cli via RegenerateCmd (implementing
/// <c>Copybara.Cli.ICopybaraCmd</c>). Copybara.Core cannot depend on Copybara.Cli, so the command
/// class lives in the CLI project and delegates to this engine through <see cref="NewRegenerate"/>
/// and <see cref="RegenerateEngine"/>.</para>
/// </summary>
public class Regenerate<O, D>
    where O : class, IRevision
    where D : class, IRevision
{
    private readonly Console _console;
    private readonly AutoPatchfileConfiguration? _autoPatchfileConfiguration;
    private readonly Workflow<O, D> _workflow;
    private readonly string _workdir;
    private readonly GeneralOptions _generalOptions;
    private readonly WorkflowOptions _workflowOptions;
    private readonly RegenerateOptions _regenerateOptions;
    private readonly string? _sourceRef;

    public Regenerate(
        Workflow<O, D> workflow,
        string workdir,
        GeneralOptions generalOptions,
        WorkflowOptions workflowOptions,
        RegenerateOptions regenerateOptions,
        string? sourceRef)
    {
        _workflow = workflow;
        _workdir = workdir;
        _generalOptions = generalOptions;
        _workflowOptions = workflowOptions;
        _regenerateOptions = regenerateOptions;
        _console = generalOptions.GetConsole();
        _sourceRef = sourceRef;
        _autoPatchfileConfiguration = workflow.GetAutoPatchfileConfiguration();
    }

    public void DoRegenerate()
    {
        var destinationWriter =
            _workflow
                .GetDestination()
                .NewWriter(
                    new WriterContext(
                        _workflow.GetName(),
                        _workflowOptions.WorkflowIdentityUser,
                        _generalOptions.DryRunMode,
                        _workflow.GetOrigin().Resolve(null!),
                        _workflow.GetDestinationFiles().Roots()));
        var patchRegenerator =
            destinationWriter.GetPatchRegenerator(_generalOptions.GetConsole())
            ?? throw new ValidationException(
                "this destination does not support regenerating patch files");

        // use the same directory names as workflow
        // TODO(b/296111124)
        string previousPath = PathOps.Resolve(_workdir, "premerge");
        string nextPath = PathOps.Resolve(_workdir, "checkout");

        string autopatchPath = PathOps.Resolve(_workdir, "autopatches");
        Directory.CreateDirectory(previousPath);
        Directory.CreateDirectory(nextPath);
        Directory.CreateDirectory(autopatchPath);

        string? regenTargetResult = _regenerateOptions.GetRegenTarget();
        regenTargetResult ??= patchRegenerator.InferRegenTarget();
        string regenTarget =
            regenTargetResult
            ?? throw new ValidationException(
                "Regen target was neither supplied nor able to be inferred. Supply with"
                + " --regen-target parameter");
        var autopatchConfig = _workflow.GetAutoPatchfileConfiguration();

        string? regenBaseline = null;
        if (_workflow.IsConsistencyFileMergeImport())
        {
            regenBaseline = _regenerateOptions.GetRegenBaseline();
            regenBaseline ??= patchRegenerator.InferRegenBaseline();
            if (regenBaseline == null)
            {
                _console.Info("Regen baseline could not be inferred. Falling back to import baseline");
            }
        }

        if (regenBaseline != null)
        {
            ValidationException.CheckCondition(
                ConsistencyFileExists(
                    destinationWriter, regenBaseline, _workflow.GetConsistencyFilePath()!),
                "Regenerating a consistency file merge import change but no consistency file found.");
            PrepareDiffWithConsistencyFileBaseline(
                autopatchConfig,
                destinationWriter,
                previousPath,
                nextPath,
                autopatchPath,
                regenBaseline,
                regenTarget);
        }
        else
        {
            previousPath =
                PrepareDiffWithImportBaseline(
                    patchRegenerator,
                    autopatchConfig,
                    _workdir,
                    nextPath,
                    regenTarget,
                    destinationWriter);
        }

        byte[]? consistencyFile = null;
        if (_workflow.GetConsistencyFilePath() != null)
        {
            try
            {
                bool excludeBuildFiles = false;
                if (_workflow.GetConsistencyFileConfig() != null)
                {
                    excludeBuildFiles = _workflow.GetConsistencyFileConfig()!.ExcludeBuildFiles();
                }
                consistencyFile =
                    ConsistencyFile.Generate(
                            previousPath,
                            nextPath,
                            _workflow.GetDestination().GetHashFunction(),
                            _workflow.GetGeneralOptions().GetEnvironment(),
                            _workflow.IsVerbose(),
                            _workflow.GetMainConfigFile().GetIdentifier(),
                            _workflow.GetName(),
                            excludeBuildFiles)
                        .ToBytes();
            }
            catch (InsideGitDirException e)
            {
                throw new ValidationException("Error generating consistency file", e);
            }
        }

        if (autopatchConfig != null)
        {
            // generate new autopatch files in the target directory
            try
            {
                AutoPatchUtil.GeneratePatchFiles(
                    previousPath,
                    nextPath,
                    autopatchConfig.DirectoryPrefix(),
                    autopatchConfig.Directory(),
                    _workflow.IsVerbose(),
                    _workflow.GetGeneralOptions().GetEnvironment(),
                    autopatchConfig.Header(),
                    autopatchConfig.Suffix(),
                    nextPath,
                    autopatchConfig.StripFilenames(),
                    autopatchConfig.StripLineNumbers(),
                    autopatchConfig.GlobValue());
            }
            catch (InsideGitDirException e)
            {
                throw new ValidationException(
                    "Could not automatically generate patch files because temporary directory "
                    + $"{e.Path} is inside git repository {e.GitDirPath}. Error received is "
                    + $"{e.Message}",
                    e);
            }
        }

        if (_workflow.GetConsistencyFilePath() != null && consistencyFile != null)
        {
            string consistencyTarget = PathOps.Resolve(nextPath, _workflow.GetConsistencyFilePath()!);
            Directory.CreateDirectory(PathOps.GetParent(consistencyTarget)!);
            File.WriteAllBytes(consistencyTarget, consistencyFile);
        }

        // push the new set of files
        patchRegenerator.UpdateChange(
            _workflow.GetName(), nextPath, _workflow.GetDestinationFiles(), regenTarget);
    }

    private bool ConsistencyFileExists(
        IDestination<D>.IWriter<D> destinationWriter, string regenBaseline, string consistencyFilePath)
    {
        DestinationReader previousDestinationReader =
            destinationWriter.GetDestinationReader(_console, regenBaseline, _workdir);
        return previousDestinationReader.Exists(consistencyFilePath);
    }

    private void PrepareDiffWithConsistencyFileBaseline(
        AutoPatchfileConfiguration? autopatchConfig,
        IDestination<D>.IWriter<D> destinationWriter,
        string previousPath,
        string nextPath,
        string patchPath,
        string regenBaseline,
        string regenTarget)
    {
        Glob patchlessDestinationFiles = _workflow.GetDestinationFiles();

        // download all files except for patch files
        if (autopatchConfig != null)
        {
            Glob autopatchGlob =
                AutoPatchUtil.GetAutopatchGlob(
                    autopatchConfig.DirectoryPrefix(), autopatchConfig.Directory());
            patchlessDestinationFiles = Glob.Difference(patchlessDestinationFiles, autopatchGlob);
        }

        Glob consistencyFileGlob =
            Glob.CreateGlob(ImmutableArray.Create(_workflow.GetConsistencyFilePath()!));
        patchlessDestinationFiles = Glob.Difference(patchlessDestinationFiles, consistencyFileGlob);

        // copy the baseline to one directory
        DestinationReader previousDestinationReader =
            destinationWriter.GetDestinationReader(_console, regenBaseline, _workdir);
        previousDestinationReader.CopyDestinationFilesToDirectory(
            patchlessDestinationFiles, previousPath);

        // copy the target to another directory
        DestinationReader nextDestinationReader =
            destinationWriter.GetDestinationReader(_console, regenTarget, _workdir);
        nextDestinationReader.CopyDestinationFilesToDirectory(patchlessDestinationFiles, nextPath);

        // copy consistency file to a third directory
        previousDestinationReader.CopyDestinationFilesToDirectory(consistencyFileGlob, patchPath);

        // reverse patch files on the target directory here to get a pristine import
        string consistencyFilePath = PathOps.Resolve(patchPath, _workflow.GetConsistencyFilePath()!);
        if (File.Exists(consistencyFilePath))
        {
            ConsistencyFile consistencyFile =
                ConsistencyFile.FromBytes(File.ReadAllBytes(consistencyFilePath));
            consistencyFile.ReversePatches(previousPath, _workflow.GetGeneralOptions().GetEnvironment());
        }
        else
        {
            _console.Warn("ConsistencyFile enabled but no ConsistencyFile file encountered");
        }
    }

    private string PrepareDiffWithImportBaseline(
        IDestination<D>.IPatchRegenerator patchRegenerator,
        AutoPatchfileConfiguration? autopatchConfig,
        string workdir,
        string nextPath,
        string regenTarget,
        IDestination<D>.IWriter<D> destinationWriter)
    {
        WorkflowRunHelper<O, D> runHelper;
        O importRevision;

        if (_sourceRef == null)
        {
            // no source ref specified, attempt to infer
            string? inferImportBaselineResult =
                patchRegenerator.InferImportBaseline(regenTarget, workdir);
            if (inferImportBaselineResult != null)
            {
                _console.InfoFmt(
                    "Inferred import baseline {0} from destination", inferImportBaselineResult);
                importRevision = _workflow.GetOrigin().Resolve(inferImportBaselineResult);
                runHelper =
                    CreateRunHelper(workdir, importRevision, inferImportBaselineResult);
            }
            else
            {
                // no source ref, no inferred baseline
                _console.Warn(
                    "Regenerate was unable to detect the import baseline reference nor was a"
                    + " reference passed in.\n"
                    + "Ideally, the reference imported by the workflow migration is the one used"
                    + " for the import baseline.\n"
                    + "Regenerate will use the latest reference or follow `--same-version`, but this"
                    + " may not match the one used for the initial import\n"
                    + "To pass in a reference, add it to the copybara command, e.g. `copybara"
                    + " regenerate [config path] [migration name] [reference]`\n");
                // use workflow logic to determine reference
                importRevision = _workflow.GetOrigin().Resolve(_sourceRef!);
                runHelper = CreateRunHelper(workdir, importRevision, _sourceRef);

                if (WorkflowModeRunner.IsHistorySupported(runHelper))
                {
                    if (_workflowOptions.ImportSameVersion)
                    {
                        importRevision = WorkflowModeRunner.MaybeGetLastRev(runHelper)!;
                    }
                }

                _console.InfoFmt(
                    "Regenerating with import baseline from origin revision {0}",
                    importRevision.AsString());
            }
        }
        else
        {
            _console.InfoFmt("Regenerating with import baseline from source ref {0}", _sourceRef);
            importRevision = _workflow.GetOrigin().Resolve(_sourceRef);
            runHelper = CreateRunHelper(workdir, importRevision, _sourceRef);
        }

        Glob patchlessDestinationFiles = _workflow.GetDestinationFiles();
        if (autopatchConfig != null)
        {
            Glob autopatchGlob =
                AutoPatchUtil.GetAutopatchGlob(
                    autopatchConfig.DirectoryPrefix(), autopatchConfig.Directory());
            patchlessDestinationFiles = Glob.Difference(_workflow.GetDestinationFiles(), autopatchGlob);
        }
        if (_workflow.GetConsistencyFilePath() != null)
        {
            Glob consistencyFileGlob =
                Glob.CreateGlob(ImmutableArray.Create(_workflow.GetConsistencyFilePath()!));
            patchlessDestinationFiles = Glob.Difference(patchlessDestinationFiles, consistencyFileGlob);
        }

        // copy the baseline to one directory
        DestinationReader previousDestinationReader =
            destinationWriter.GetDestinationReader(
                _console, (Origin.Baseline<IRevision>?)null, _workdir);
        string importPath =
            runHelper.ImportAndTransformRevision(
                _console, null!, importRevision, new FuncSupplier(() => previousDestinationReader));

        // copy the target to another directory
        DestinationReader nextDestinationReader =
            destinationWriter.GetDestinationReader(_console, regenTarget, _workdir);
        nextDestinationReader.CopyDestinationFilesToDirectory(patchlessDestinationFiles, nextPath);

        return importPath;
    }

    private WorkflowRunHelper<O, D> CreateRunHelper(
        string workdir, O resolvedRef, string? sourceRef) =>
        _workflow.NewRunHelper(workdir, resolvedRef, sourceRef, _ => { });

    private sealed class FuncSupplier : TransformWork.IResourceSupplier<DestinationReader>
    {
        private readonly Func<DestinationReader> _func;

        public FuncSupplier(Func<DestinationReader> func) => _func = func;

        public DestinationReader Get() => _func();
    }
}

/// <summary>
/// Non-generic factory/entry helpers for <see cref="Regenerate{O,D}"/>. Allows callers (e.g. the CLI
/// command in the separate Copybara.Cli project) to construct and run a regenerate over a
/// workflow whose concrete revision types are not statically known. This mirrors upstream's
/// <c>Regenerate.newRegenerate</c>, which takes a wildcard <c>Workflow&lt;?, ?&gt;</c>.
///
/// <para>TODO(port): wire into Copybara.Cli. A <c>RegenerateCmd</c> implementing
/// <c>Copybara.Cli.ICopybaraCmd</c> should resolve the config/workflow (via
/// <c>Config.GetMigration(name)</c>), verify it is a <see cref="Workflow{O,D}"/>, and call
/// <see cref="Run"/>.</para>
/// </summary>
public static class RegenerateEngine
{
    /// <summary>
    /// Runs a regenerate for the given migration. The migration must be a
    /// <see cref="Workflow{O,D}"/>. The concrete revision type arguments are recovered from the
    /// migration's runtime type so the caller does not have to know them statically.
    /// </summary>
    /// <exception cref="ValidationException">if the migration is not a workflow.</exception>
    public static void Run(
        IMigration migration,
        string workdir,
        GeneralOptions generalOptions,
        WorkflowOptions workflowOptions,
        RegenerateOptions regenerateOptions,
        string? sourceRef)
    {
        Type? workflowType = FindWorkflowBaseType(migration.GetType());
        ValidationException.CheckCondition(
            workflowType != null,
            "regenerate patch files is only supported for workflow migrations");

        Type[] typeArgs = workflowType!.GetGenericArguments();
        Type regenerateType = typeof(Regenerate<,>).MakeGenericType(typeArgs);
        object regenerate =
            Activator.CreateInstance(
                regenerateType,
                migration,
                workdir,
                generalOptions,
                workflowOptions,
                regenerateOptions,
                sourceRef)!;
        regenerateType.GetMethod(nameof(Regenerate<IRevision, IRevision>.DoRegenerate))!
            .Invoke(regenerate, Array.Empty<object>());
    }

    private static Type? FindWorkflowBaseType(Type? type)
    {
        while (type != null)
        {
            if (type.IsGenericType && type.GetGenericTypeDefinition() == typeof(Workflow<,>))
            {
                return type;
            }
            type = type.BaseType;
        }
        return null;
    }
}
