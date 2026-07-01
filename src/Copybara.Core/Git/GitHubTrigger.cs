/*
 * Copyright (C) 2018 Google Inc.
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
using Copybara.Git.GitHub.Util;
using Console = Copybara.Util.Console.Console;
using GitHubApiClient = Copybara.Git.GitHub.Api.GitHubApi;

namespace Copybara.Git;

/// <summary>
/// A feedback trigger based on updates on a GitHub PR. Port of
/// <c>com.google.copybara.git.GitHubTrigger</c>.
/// </summary>
public sealed class GitHubTrigger : ITrigger
{
    private readonly LazyResourceLoader<GitHubApiClient> _apiSupplier;
    private readonly string _url;
    private readonly GitHubHost _ghHost;
    private readonly IReadOnlySet<EventTrigger> _events;
    private readonly Console _console;
    private readonly CredentialFileHandler? _credentials;

    internal GitHubTrigger(
        LazyResourceLoader<GitHubApiClient> apiSupplier,
        string url,
        IReadOnlySet<EventTrigger> events,
        Console console,
        GitHubHost ghHost,
        CredentialFileHandler? credentials)
    {
        _apiSupplier = Preconditions.CheckNotNull(apiSupplier);
        _url = Preconditions.CheckNotNull(url);
        _ghHost = Preconditions.CheckNotNull(ghHost);
        Preconditions.CheckArgument(events.Count != 0);
        _events = events;
        _console = Preconditions.CheckNotNull(console);
        _credentials = credentials;
    }

    public IEndpoint GetEndpoint() =>
        new GitHubEndPoint(_apiSupplier, _url, _console, _ghHost, _credentials);

    public ImmutableListMultimap<string, string> Describe()
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        builder.Put("type", "github_trigger");
        builder.Put("url", _url);
        builder.PutAll("events", _events.Select(s => s.Type().ToString()));
        foreach (var trigger in _events)
        {
            if (trigger.Subtypes().Count == 0)
            {
                continue;
            }
            builder.PutAll($"SUBTYPES_{trigger.Type()}", trigger.Subtypes());
        }
        return builder.Build();
    }

    public override string ToString() =>
        $"GitHubTrigger{{url={_url}, event_types={string.Join(",", _events)}}}";

    public IReadOnlyList<ImmutableListMultimap<string, string>> DescribeCredentials() =>
        _credentials == null
            ? ImmutableArray<ImmutableListMultimap<string, string>>.Empty
            : GitDescribeCredentials.Convert(_credentials.DescribeCredentials());
}
