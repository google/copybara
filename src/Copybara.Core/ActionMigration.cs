/*
 * Copyright (C) 2023 Google Inc.
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
using Copybara.Action;
using Copybara.Common;
using Copybara.Config;
using Copybara.Effect;
using Copybara.Exceptions;
using Copybara.Monitor;
using Copybara.Transform;
using Starlark.Eval;

namespace Copybara;

/// <summary>A migration that can move code or metadata between endpoints.</summary>
public class ActionMigration : IMigration
{
    public const string DestinationEndpointName = "destination";

    private readonly string _name;
    private readonly string? _description;
    private readonly ConfigFile _configFile;
    private readonly ITrigger _trigger;
    private readonly IStructure _endpoints;
    private readonly IReadOnlyList<IAction> _actions;
    private readonly GeneralOptions _generalOptions;
    private readonly string _mode;
    private readonly bool _fileSystem;
    private readonly ImmutableArray<StarlarkThread.CallStackEntry> _definitionStack;

    public ActionMigration(
        string name,
        string? description,
        ConfigFile configFile,
        ITrigger trigger,
        IStructure endpoints,
        IReadOnlyList<IAction> actions,
        GeneralOptions generalOptions,
        string mode,
        bool fileSystem,
        ImmutableArray<StarlarkThread.CallStackEntry> definitionStack)
    {
        _name = Preconditions.CheckNotNull(name);
        _description = description;
        _configFile = Preconditions.CheckNotNull(configFile);
        _trigger = Preconditions.CheckNotNull(trigger);
        _endpoints = endpoints;
        _actions = Preconditions.CheckNotNull(actions);
        _generalOptions = Preconditions.CheckNotNull(generalOptions);
        _mode = mode;
        _fileSystem = fileSystem;
        _definitionStack = definitionStack;
    }

    public void Run(string workdir, IReadOnlyList<string> sourceRefs)
    {
        var allResultsBuilder = ImmutableArray.CreateBuilder<ActionResult>();
        string suffix = string.Join('_', sourceRefs)
            .Replace('/', '_')
            .Replace(' ', '_');
        string root = "run/" + _name + "/" + suffix.Substring(0, Math.Min(suffix.Length, 20));
        using (Profiler().Start(root))
        {
            foreach (var action in _actions)
            {
                var effects = new List<DestinationEffect>();
                using (Profiler().Start(action.GetName()))
                {
                    try
                    {
                        var console = new SkylarkConsole(_generalOptions.GetConsole());
                        EventMonitors().DispatchEvent(
                            m => m.OnChangeMigrationStarted(new IEventMonitor.ChangeMigrationStartedEvent()));
                        var context = new ActionMigrationContext(
                            this, action, _generalOptions.CliLabels(), sourceRefs, console);
                        if (_fileSystem)
                        {
                            context = context.WithFileSystem(workdir);
                        }
                        action.Run(context);
                        effects.AddRange(context.GetNewDestinationEffects());
                        var actionResult = context.GetActionResult();
                        allResultsBuilder.Add(actionResult);
                        // First error aborts the execution of the other actions.
                        ValidationException.CheckCondition(
                            actionResult.GetResult() != ActionResult.Result.Error,
                            "{0} migration '{1}' action '{2}' returned error: {3}. Aborting execution.",
                            Capitalize(_mode), _name, action.GetName(), actionResult.GetMsg());
                    }
                    finally
                    {
                        EventMonitors().DispatchEvent(m => m.OnChangeMigrationFinished(
                            new IEventMonitor.ChangeMigrationFinishedEvent(
                                effects.ToImmutableArray(),
                                GetOriginDescription(),
                                GetDestinationDescription())));
                    }
                }
            }
        }
        var allResults = allResultsBuilder.ToImmutable();
        // This check also returns true if there are no actions.
        if (allResults.All(a => a.GetResult() == ActionResult.Result.NoOp))
        {
            string detailedMessage = allResults.Length == 0
                ? "actions field is empty"
                : "[" + string.Join(", ", allResults.Select(a => a.GetMsg()).Where(m => m != null)) + "]";
            throw new EmptyChangeException(
                $"{Capitalize(_mode)} migration '{_name}' was noop. Detailed messages: {detailedMessage}");
        }
    }

    private static string Capitalize(string str) =>
        str.Substring(0, 1).ToUpperInvariant() + str.Substring(1);

    public string GetName() => _name;

    public string? GetDescription() => _description;

    public string GetModeString() => _mode;

    public ConfigFile GetMainConfigFile() => _configFile;

    public ImmutableListMultimap<string, string> GetOriginDescription() => _trigger.Describe();

    public ImmutableListMultimap<string, string> GetDestinationDescription()
    {
        // We currently require one endpoint to be the designated destination, all others should be
        // read only.
        object destination =
            Preconditions.CheckNotNull(_endpoints.GetValue(DestinationEndpointName));
        return ((IEndpoint)destination).Describe();
    }

    public IReadOnlyDictionary<string, ImmutableListMultimap<string, string>> GetEndpointDescriptions()
    {
        var result = ImmutableDictionary.CreateBuilder<string, ImmutableListMultimap<string, string>>();
        foreach (var name in _endpoints.GetFieldNames())
        {
            if (name != DestinationEndpointName && _endpoints.GetValue(name) is IEndpoint e)
            {
                result[name] = e.Describe();
            }
        }
        return result.ToImmutable();
    }

    /// <summary>
    /// Returns a multimap containing enough data to fingerprint the actions for validation purposes.
    /// </summary>
    public ImmutableListMultimap<string, ImmutableListMultimap<string, string>> GetActionsDescription()
    {
        var descriptionBuilder =
            ImmutableListMultimap<string, ImmutableListMultimap<string, string>>.CreateBuilder();
        foreach (var action in _actions)
        {
            descriptionBuilder.Put(action.GetName(), action.Describe());
        }
        return descriptionBuilder.Build();
    }

    public IReadOnlyList<ImmutableListMultimap<string, string>> GetCredentialDescription()
    {
        var allCreds = ImmutableArray.CreateBuilder<ImmutableListMultimap<string, string>>();
        allCreds.AddRange(_trigger.GetEndpoint().DescribeCredentials("trigger"));
        foreach (var name in _endpoints.GetFieldNames())
        {
            if (_endpoints.GetValue(name) is IEndpoint e)
            {
                allCreds.AddRange(e.DescribeCredentials(name));
            }
        }
        return allCreds.ToImmutable();
    }

    internal ITrigger GetTrigger() => _trigger;

    public IStructure GetEndpoints() => _endpoints;

    public override string ToString() =>
        $"ActionMigration{{name={_name}, trigger={_trigger}, endpoints={_endpoints},"
        + $" actions=[{string.Join(", ", _actions)}]}}";

    private Profiler.Profiler Profiler() => _generalOptions.Profiler();

    private IEventMonitor.EventMonitors EventMonitors() => _generalOptions.EventMonitors();

    public ImmutableArray<StarlarkThread.CallStackEntry> GetDefinitionStack() => _definitionStack;
}
