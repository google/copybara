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

namespace Copybara.Config;

/// <summary>
/// Validates Copybara <see cref="IMigration"/>s and returns a <see cref="ValidationResult"/>.
///
/// <para>Implementations of this interface should not throw exceptions for validation errors.</para>
/// </summary>
// Reference-forward: upstream dispatches on Workflow, Mirror and ActionMigration. Only
// ActionMigration is ported so far; Workflow/Mirror dispatch is added during final consolidation
// once those types land (Copybara.Workflow, Copybara.Git.Mirror).
public abstract class MigrationValidator
{
    public ValidationResult Validate(IMigration migration, Config config)
    {
        if (migration is ActionMigration actionMigration)
        {
            return ValidateActionMigration(migration.GetName(), actionMigration, config);
        }
        // TODO(consolidation): add Workflow and Mirror dispatch when those types are ported.
        throw new InvalidOperationException($"Validation missing for {migration}");
    }

    /// <summary>Performs specific validation of an <see cref="ActionMigration"/> migration.</summary>
    protected abstract ValidationResult ValidateActionMigration(
        string name, ActionMigration actionMigration, Config config);
}
