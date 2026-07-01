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

using System.Collections.Immutable;
using Copybara.Common;
using Copybara.Exceptions;

namespace Copybara.Config;

/// <summary>
/// Configuration for a Copybara project.
///
/// <para>Objects of this class represent a parsed Copybara configuration.</para>
/// </summary>
public sealed class Config
{
    private readonly ImmutableDictionary<string, IMigration> _migrations;
    private readonly string _location;
    private readonly ImmutableDictionary<string, object> _globals;

    public Config(
        IReadOnlyDictionary<string, IMigration> migrations,
        string location,
        IReadOnlyDictionary<string, object> globals)
    {
        _migrations = migrations.ToImmutableDictionary();
        _location = Preconditions.CheckNotNull(location);
        _globals = globals.ToImmutableDictionary();
    }

    /// <summary>Returns the <see cref="IMigration"/> named after <paramref name="migrationName"/>.</summary>
    /// <exception cref="ValidationException">if no migration with the given name exists</exception>
    public IMigration GetMigration(string migrationName)
    {
        ValidationException.CheckCondition(
            _migrations.ContainsKey(migrationName),
            "No migration with name '{0}' exists. Valid migrations: {1}",
            migrationName,
            string.Join(", ", _migrations.Keys));
        return _migrations[migrationName];
    }

    /// <summary>
    /// Location of the top-level config file. An arbitrary string meant to be used for
    /// logging/debugging. It shouldn't be parsed, as the format might change.
    /// </summary>
    public string GetLocation() => _location;

    /// <summary>
    /// Reads values from the global frame of the skylark environment, i.e. global variables.
    /// </summary>
    public T? GetGlobalEnvironmentVariable<T>(string name)
        where T : class =>
        _globals.TryGetValue(name, out object? value) ? value as T : null;

    /// <summary>Returns all the migrations in this configuration.</summary>
    public ImmutableDictionary<string, IMigration> GetMigrations() => _migrations;

    public override bool Equals(object? o)
    {
        if (ReferenceEquals(this, o))
        {
            return true;
        }
        if (o is not Config config)
        {
            return false;
        }
        return DictionaryEquals(_migrations, config._migrations);
    }

    public override int GetHashCode()
    {
        int hash = 17;
        foreach (var e in _migrations)
        {
            hash ^= e.Key.GetHashCode();
        }
        return hash;
    }

    public override string ToString() =>
        $"Config{{migrations=[{string.Join(", ", _migrations.Keys)}], location={_location}}}";

    private static bool DictionaryEquals(
        ImmutableDictionary<string, IMigration> a, ImmutableDictionary<string, IMigration> b)
    {
        if (a.Count != b.Count)
        {
            return false;
        }
        foreach (var e in a)
        {
            if (!b.TryGetValue(e.Key, out IMigration? other) || !Equals(e.Value, other))
            {
                return false;
            }
        }
        return true;
    }
}
