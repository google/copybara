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
using Copybara.Common;
using Starlark.Annot;

// Domain 'Console' collides with System.Console.
using Console = Copybara.Util.Console.Console;

namespace Copybara;

/// <summary>
/// A supplier of modules and <see cref="IOption"/>s for Copybara.
/// </summary>
public class ModuleSupplier
{
    // TODO(port): Upstream registers CoreGlobal.class here. CoreGlobal is not yet ported.
    // TODO(malcon): Remove once no more static modules exist.
    private static readonly ImmutableHashSet<Type> BasicModules = ImmutableHashSet<Type>.Empty;

    private readonly IReadOnlyDictionary<string, string> _environment;
    private readonly string _fileSystemRoot;
    private readonly Console _console;

    public ModuleSupplier(
        IReadOnlyDictionary<string, string> environment,
        string fileSystemRoot,
        Console console)
    {
        _environment = Preconditions.CheckNotNull(environment);
        _fileSystemRoot = Preconditions.CheckNotNull(fileSystemRoot);
        _console = Preconditions.CheckNotNull(console);
    }

    /// <summary>
    /// Returns the set of static modules available.
    /// TODO(malcon): Remove once no more static modules exist.
    /// </summary>
    protected virtual IReadOnlySet<Type> GetStaticModules() => BasicModules;

    /// <summary>
    /// Get non-static modules available.
    /// </summary>
    public virtual IReadOnlySet<object> GetModules(Options options)
    {
        GeneralOptions general = options.Get<GeneralOptions>();

        // TODO(port): The upstream ModuleSupplier wires up the full set of Starlark modules here:
        //   CoreModule (needs WorkflowOptions, DebugOptions, FolderModule), GitModule, HgModule,
        //   FolderModule, FormatModule, BuildozerModule, PatchModule, MetadataModule,
        //   Authoring.Module, RemoteFileModule, ArchiveModule, Re2Module, TomlModule, HtmlModule,
        //   XmlModule, StructModule, StarlarkDateTimeModule, StarlarkRandomModule, GoModule,
        //   RustModule, HashingModule, HttpModule, PythonModule, NpmModule, CredentialModule,
        //   and Json.INSTANCE.
        // None of these module types are ported yet; register them here as they land. Returning an
        // empty set for now so the engine can still be constructed.
        _ = general;
        return ImmutableHashSet<object>.Empty;
    }

    /// <summary>Returns a new set of <see cref="IOption"/>s.</summary>
    protected virtual Options NewOptions()
    {
        var generalOptions = new GeneralOptions(_environment, _fileSystemRoot, _console);
        var workflowOptions = new WorkflowOptions();

        // TODO(port): Upstream also constructs and registers the following options, none of which
        // are ported yet. Add them to the list below as they become available:
        //   GitOptions, GitDestinationOptions, GitOriginOptions, GitHubOptions,
        //   GitHubPrOriginOptions, GitHubDestinationOptions, GerritOptions, GitMirrorOptions,
        //   GitLabOptions, BuildifierOptions, BuildozerOptions, FolderDestinationOptions,
        //   FolderOriginOptions, HgOptions, HgOriginOptions, PatchingOptions, RemoteFileOptions,
        //   DebugOptions, GeneratorOptions, HttpOptions, RegenerateOptions, CredentialOptions.
        return new Options(
            new IOption[]
            {
                generalOptions,
                workflowOptions,
            });
    }

    /// <summary>
    /// A ModuleSet contains the collection of modules and flags for one Skylark copy.bara.sky
    /// evaluation/execution.
    /// </summary>
    public ModuleSet Create()
    {
        Options options = NewOptions();
        return CreateWithOptions(options);
    }

    public ModuleSet CreateWithOptions(Options options)
    {
        return new ModuleSet(options, GetStaticModules(), ModulesToVariableMap(options));
    }

    private IReadOnlyDictionary<string, object> ModulesToVariableMap(Options options)
    {
        return GetModules(options)
            .ToImmutableDictionary(FindClosestStarlarkBuiltinName, o => o);
    }

    private static string FindClosestStarlarkBuiltinName(object o)
    {
        Type? cls = o.GetType();
        while (cls != null && cls != typeof(object))
        {
            var annotation =
                (StarlarkBuiltinAttribute?)Attribute.GetCustomAttribute(
                    cls, typeof(StarlarkBuiltinAttribute), inherit: false);
            if (annotation != null)
            {
                return annotation.Name;
            }

            cls = cls.BaseType;
        }

        throw new InvalidOperationException(
            "Cannot find @StarlarkBuiltin for " + o.GetType());
    }
}
