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
using Copybara.Action;
using Copybara.Common;
using Copybara.Config;
using Copybara.Effect;
using Copybara.Exceptions;
using Copybara.Monitor;
using Copybara.Transform;
using Starlark.Eval;

namespace Copybara.Git;

/// <summary>
/// Mirror one or more refspec between git repositories. Port of
/// <c>com.google.copybara.git.Mirror</c>.
/// </summary>
public class Mirror : IMigration
{
    private const string ModeString = "MIRROR";

    private readonly GeneralOptions _generalOptions;
    private readonly GitOptions _gitOptions;
    private readonly string _name;
    private readonly string _origin;
    private readonly string _destination;
    private readonly IReadOnlyList<Refspec> _refspec;
    private readonly GitDestinationOptions _gitDestinationOptions;
    private readonly bool _prune;
    private readonly bool _partialFetch;
    private readonly ConfigFile _mainConfigFile;
    private readonly string? _description;
    private readonly IAction? _action;
    private readonly LazyResourceLoader<IEndpointProvider>? _originApiEndpointProvider;
    private readonly LazyResourceLoader<IEndpointProvider>? _destinationApiEndpointProvider;
    private readonly ImmutableArray<CredentialFileHandler> _credentials;
    private readonly ImmutableArray<StarlarkThread.CallStackEntry> _definitionStack;

    internal Mirror(
        GeneralOptions generalOptions,
        GitOptions gitOptions,
        string name,
        string origin,
        string destination,
        IReadOnlyList<Refspec> refspec,
        GitDestinationOptions gitDestinationOptions,
        bool prune,
        bool partialFetch,
        ConfigFile mainConfigFile,
        string? description,
        IAction? action,
        LazyResourceLoader<IEndpointProvider>? originApiEndpointProvider,
        LazyResourceLoader<IEndpointProvider>? destinationApiEndpointProvider,
        ImmutableArray<CredentialFileHandler> credentials,
        ImmutableArray<StarlarkThread.CallStackEntry> definitionStack)
    {
        _generalOptions = Preconditions.CheckNotNull(generalOptions);
        _gitOptions = Preconditions.CheckNotNull(gitOptions);
        _name = Preconditions.CheckNotNull(name);
        _origin = Preconditions.CheckNotNull(origin);
        _destination = Preconditions.CheckNotNull(destination);
        _refspec = Preconditions.CheckNotNull(refspec);
        _gitDestinationOptions = gitDestinationOptions;
        _prune = prune;
        _partialFetch = partialFetch;
        _mainConfigFile = Preconditions.CheckNotNull(mainConfigFile);
        _description = description;
        _action = action;
        _originApiEndpointProvider = originApiEndpointProvider;
        _destinationApiEndpointProvider = destinationApiEndpointProvider;
        _credentials = credentials;
        _definitionStack = definitionStack;
    }

    public void Run(string workdir, IReadOnlyList<string> sourceRefs)
    {
        using (_generalOptions.Profiler().Start("run/" + _name))
        {
            GitRepository repo = GetLocalRepo();
            MaybeConfigureGitNameAndEmail(repo);
            if (_action == null)
            {
                DefaultMirror(repo);
            }
            else
            {
                CustomMirror(repo, sourceRefs);
            }
        }

        var @event =
            new IEventMonitor.ChangeMigrationFinishedEvent(
                ImmutableArray.Create(
                    new DestinationEffect(
                        _generalOptions.DryRunMode
                            ? DestinationEffect.EffectType.NOOP
                            : DestinationEffect.EffectType.UPDATED,
                        _generalOptions.DryRunMode
                            ? "Refspecs " + RefspecString() + " can be mirrored"
                            : "Refspecs " + RefspecString() + " mirrored successfully",
                        ImmutableArray<Revision.OriginRef>.Empty,
                        new DestinationEffect.DestinationRef(
                            GetOriginDestinationRef(_destination), "mirror", url: null))),
                GetOriginDescription(),
                GetDestinationDescription());
        DispatchMigrationFinishedEvent(@event);
    }

    private void DispatchMigrationFinishedEvent(IEventMonitor.ChangeMigrationFinishedEvent @event) =>
        _generalOptions.EventMonitors().DispatchEvent(m => m.OnChangeMigrationFinished(@event));

    private void CustomMirror(GitRepository repo, IReadOnlyList<string> sourceRefs)
    {
        ActionResult? actionResult = null;

        var context =
            new GitMirrorContext(
                _action!,
                new SkylarkConsole(_generalOptions.GetConsole()),
                _generalOptions.Profiler(),
                sourceRefs,
                _refspec,
                _origin,
                _destination,
                _generalOptions.IsForced(),
                repo,
                _generalOptions.GetDirFactory(),
                Dict.Empty(),
                _gitOptions,
                _originApiEndpointProvider,
                _destinationApiEndpointProvider);
        try
        {
            _action.Run(context);
            actionResult = context.GetActionResult();

            ValidationException.CheckCondition(
                actionResult!.GetResult() != ActionResult.Result.Error,
                "An error occurred during the git.mirror migration '{0}' on action `{1}`. Detailed"
                    + " message: {2}",
                _name,
                _action.GetName(),
                actionResult.GetMsg()!);
        }
        catch (NonFastForwardRepositoryException e)
        {
            actionResult = ActionResult.ErrorResult(_action!.GetName() + ": " + e.Message);
        }
        finally
        {
            DispatchMigrationFinishedEvent(
                new IEventMonitor.ChangeMigrationFinishedEvent(
                    context.GetNewDestinationEffects().ToImmutableArray(),
                    GetOriginDescription(),
                    GetDestinationDescription()));
        }

        if (actionResult!.GetResult() == ActionResult.Result.NoOp)
        {
            throw new EmptyChangeException(
                $"git.mirror migration '{_name}' was noop. Detailed message: {actionResult.GetMsg()}");
        }
    }

    private void DefaultMirror(GitRepository repo)
    {
        var fetchRefspecs = _refspec.Select(r => r.OriginToOrigin().ToString()).ToList();

        _generalOptions.GetConsole().ProgressFmt("Fetching from {0}", _origin);

        var profiler = _generalOptions.Profiler();
        using (profiler.Start("fetch"))
        {
            repo.Fetch(
                _origin,
                prune: true,
                force: true,
                fetchRefspecs,
                _partialFetch,
                depth: null,
                tags: false);
        }

        if (_generalOptions.DryRunMode)
        {
            _generalOptions.GetConsole().ProgressFmt(
                "Skipping push to {0}. You can check the commits to push in: {1}",
                _destination,
                repo.GetGitDir());
        }
        else
        {
            _generalOptions.GetConsole().ProgressFmt("Pushing to {0}", _destination);
            var pushRefspecs = _generalOptions.IsForced()
                ? _refspec.Select(r => r.WithAllowNoFastForward()).ToList()
                : _refspec.ToList();
            using (profiler.Start("push"))
            {
                try
                {
                    repo.Push()
                        .WithPrune(_prune)
                        .WithRefspecs(_destination, pushRefspecs)
                        .WithPushOptions(_gitOptions.GitPushOptions.ToImmutableArray())
                        .Run();
                }
                catch (NonFastForwardRepositoryException e)
                {
                    throw new ValidationException(
                        "Error pushing some refs because origin is behind:" + e.Message, e);
                }
            }
        }
    }

    private void MaybeConfigureGitNameAndEmail(GitRepository repo)
    {
        if (!string.IsNullOrEmpty(_gitDestinationOptions.CommitterName))
        {
            repo.SimpleCommand("config", "user.name", _gitDestinationOptions.CommitterName);
        }
        if (!string.IsNullOrEmpty(_gitDestinationOptions.CommitterEmail))
        {
            repo.SimpleCommand("config", "user.email", _gitDestinationOptions.CommitterEmail);
        }
    }

    private static string GetOriginDestinationRef(string url)
    {
        // TODO(peer): Use GitHubHost URL normalization once the github util peer port lands. Until
        // then, return the url as-is.
        return url;
    }

    internal GitRepository GetLocalRepo()
    {
        GitRepository repo = _gitOptions.CachedBareRepoForUrl(_origin);
        foreach (var cred in _credentials)
        {
            try
            {
                cred.Install(repo, _gitOptions.GetConfigCredsFile(_generalOptions));
            }
            catch (IOException e)
            {
                throw new RepoException("Unable to store credentials", e);
            }
        }
        return repo;
    }

    private string RefspecString() =>
        "[" + string.Join(", ", _refspec.Select(r => r.ToString())) + "]";

    public ImmutableListMultimap<string, string> GetOriginDescription()
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        builder.Put("type", "git.mirror");
        builder.Put("url", _origin);
        builder.PutAll("ref", _refspec.Select(r => r.GetOrigin()));
        return builder.Build();
    }

    public ImmutableListMultimap<string, string> GetDestinationDescription()
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        builder.Put("type", "git.mirror");
        builder.Put("url", _destination);
        builder.PutAll("ref", _refspec.Select(r => r.GetDestination()));
        return builder.Build();
    }

    public IReadOnlyList<ImmutableListMultimap<string, string>> GetCredentialDescription()
    {
        var desc = ImmutableArray.CreateBuilder<ImmutableListMultimap<string, string>>();
        foreach (var cred in _credentials)
        {
            desc.AddRange(GitDescribeCredentials.Convert(cred.DescribeCredentials()));
        }
        return desc.ToImmutable();
    }

    public ConfigFile GetMainConfigFile() => _mainConfigFile;

    public string GetName() => _name;

    public string? GetDescription() => _description;

    public string GetModeString() => ModeString;

    public ImmutableArray<StarlarkThread.CallStackEntry> GetDefinitionStack() => _definitionStack;
}
