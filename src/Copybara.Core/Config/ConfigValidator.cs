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

namespace Copybara.Config;

/// <summary>
/// Validates Copybara <see cref="Config"/>s and returns a <see cref="ValidationResult"/>.
///
/// <para>Implementations of this interface should not throw exceptions for validation errors.</para>
/// </summary>
public interface ConfigValidator
{
    ValidationResult Validate(Config config, string migrationName)
    {
        var resultBuilder = new ValidationResult.Builder();
        CheckAtLeastOneMigration(resultBuilder, config);
        return resultBuilder.Build();
    }

    void CheckAtLeastOneMigration(ValidationResult.Builder resultBuilder, Config config)
    {
        if (config.GetMigrations().Count == 0)
        {
            resultBuilder.Error("At least one migration is required.");
        }
    }
}
