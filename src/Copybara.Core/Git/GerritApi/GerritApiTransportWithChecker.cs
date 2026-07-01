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
using Copybara.Checks;
using Copybara.Common;
using Console = Copybara.Util.Console.Console;

namespace Copybara.Git.GerritApi;

/// <summary>
/// A <see cref="IGerritApiTransport"/> wrapper that runs an <see cref="IChecker"/> over each request
/// before delegating. Port of
/// <c>com.google.copybara.git.gerritapi.GerritApiTransportWithChecker</c>.
/// </summary>
/// <remarks>
/// NOTE(port): the Java original delegates to a <c>com.google.copybara.checks.ApiChecker</c>. Since
/// that helper is not (yet) ported, this class inlines its convenience logic (build a field map from
/// the arguments and call <see cref="IChecker.DoCheck(ImmutableDictionary{string,string}, Console)"/>).
/// The response type, which upstream passes as a reflected <c>Type</c>, is represented here by the
/// generic argument and surfaced to the checker as a string.
/// </remarks>
public class GerritApiTransportWithChecker : IGerritApiTransport
{
    private readonly IGerritApiTransport _delegate;
    private readonly IChecker _checker;
    private readonly Console _console;

    public GerritApiTransportWithChecker(
        IGerritApiTransport @delegate, IChecker checker, Console console)
    {
        _delegate = Preconditions.CheckNotNull(@delegate);
        _checker = Preconditions.CheckNotNull(checker);
        _console = Preconditions.CheckNotNull(console);
    }

    public Task<T?> GetAsync<T>(string path)
    {
        Check(
            ("path", path),
            ("response_type", typeof(T).ToString()));
        return _delegate.GetAsync<T>(path);
    }

    public Task<T?> PostAsync<T>(string path, object request)
    {
        Check(
            ("path", path),
            ("request", request.ToString() ?? string.Empty),
            ("response_type", typeof(T).ToString()));
        return _delegate.PostAsync<T>(path, request);
    }

    public Task<T?> PutAsync<T>(string path, object request)
    {
        Check(
            ("path", path),
            ("request", request.ToString() ?? string.Empty),
            ("response_type", typeof(T).ToString()));
        return _delegate.PutAsync<T>(path, request);
    }

    private void Check(params (string Field, string Value)[] fields)
    {
        var builder = ImmutableDictionary.CreateBuilder<string, string>();
        foreach (var (field, value) in fields)
        {
            builder[field] = value;
        }

        _checker.DoCheck(builder.ToImmutable(), _console);
    }
}
