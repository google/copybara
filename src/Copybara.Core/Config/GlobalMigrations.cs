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

using System.Text.RegularExpressions;
using Copybara.Common;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Config;

/// <summary>Global variable that holds the registered migrations in the config files.</summary>
[StarlarkBuiltin(GlobalMigrations.GLOBAL_MIGRATIONS,
    Doc = "Global variable that holds the registered migrations in the config files",
    Documented = false)]
public sealed class GlobalMigrations : IStarlarkValue
{
    private static readonly Regex MigrationNameFormat =
        new(@"^[a-zA-Z0-9_\-\./]+$", RegexOptions.Compiled);

    private const string MigrationNamePattern = "[a-zA-Z0-9_\\-\\./]+";

    internal const string GLOBAL_MIGRATIONS = "global_migrations";

    private readonly Dictionary<string, IMigration> _migrations = new();

    public static GlobalMigrations GetGlobalMigrations(Module module) =>
        (GlobalMigrations)Preconditions.CheckNotNull(module.GetPredeclared(GLOBAL_MIGRATIONS));

    public IReadOnlyDictionary<string, IMigration> GetMigrations() => _migrations;

    /// <exception cref="EvalException">if a migration with the name already exists or the name is
    /// invalid</exception>
    public void AddMigration(string name, IMigration migration)
    {
        CheckMigrationName(name);
        SkylarkUtil.Check(
            _migrations.TryAdd(name, migration),
            "A migration with the name '{0}' is already defined",
            name);
    }

    /// <summary>Checks if a migration name conforms to the expected format.</summary>
    /// <param name="name">Migration name</param>
    /// <exception cref="EvalException">If the name does not conform to the expected format</exception>
    public static void CheckMigrationName(string name)
    {
        SkylarkUtil.Check(
            MigrationNameFormat.IsMatch(name),
            "Migration name '{0}' doesn't conform to expected pattern: {1}",
            name,
            MigrationNamePattern);
    }
}
