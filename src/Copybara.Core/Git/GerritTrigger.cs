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
using Console = Copybara.Util.Console.Console;
using GerritApiClient = Copybara.Git.GerritApi.GerritApi;

namespace Copybara.Git;

/// <summary>
/// A feedback trigger based on updates on a Gerrit change. Port of
/// <c>com.google.copybara.git.GerritTrigger</c>.
/// </summary>
public sealed class GerritTrigger : ITrigger
{
    private readonly LazyResourceLoader<GerritApiClient> _apiSupplier;
    private readonly string _url;
    private readonly IReadOnlySet<GerritEventTrigger> _events;
    private readonly Console _console;
    private readonly bool _allowSubmitChange;

    internal GerritTrigger(
        LazyResourceLoader<GerritApiClient> apiSupplier,
        string url,
        IReadOnlySet<GerritEventTrigger> events,
        Console console,
        bool allowSubmitChange)
    {
        _apiSupplier = Preconditions.CheckNotNull(apiSupplier);
        _url = Preconditions.CheckNotNull(url);
        _events = Preconditions.CheckNotNull(events);
        _console = console;
        _allowSubmitChange = allowSubmitChange;
    }

    public IEndpoint GetEndpoint() =>
        new GerritEndpoint(_apiSupplier, _url, _console, _allowSubmitChange);

    public ImmutableListMultimap<string, string> Describe()
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        builder.Put("type", "gerrit_trigger");
        builder.Put("url", _url);
        builder.Put("gerritSubmit", _allowSubmitChange.ToString());
        builder.PutAll("events", _events.Select(s => s.Type.ToString()));

        foreach (var trigger in _events)
        {
            if (trigger.Subtypes.Count == 0)
            {
                continue;
            }
            builder.PutAll($"SUBTYPES_{trigger.Type}", trigger.Subtypes);
        }
        return builder.Build();
    }

    public override string ToString() =>
        $"GerritTrigger{{url={_url}, event_types={string.Join(",", _events)}}}";
}
