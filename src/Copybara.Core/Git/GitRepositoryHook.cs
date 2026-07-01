/*
 * Copyright (C) 2025 Google LLC
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

namespace Copybara.Git;

/// <summary>
/// Defines behavior to perform before checking out a Git repository. Port of
/// <c>com.google.copybara.git.GitRepositoryHook</c>.
/// </summary>
public interface IGitRepositoryHook
{
    /// <summary>Data class for a Git repository.</summary>
    public sealed record GitRepositoryData(string? Id, string Url);

    /// <summary>
    /// Procedures to be performed before checking out a Git repository.
    /// </summary>
    /// <exception cref="Copybara.Exceptions.ValidationException">
    /// if checkout prework fails due to user error.
    /// </exception>
    /// <exception cref="Copybara.Exceptions.RepoException">
    /// if the checkout prework fails due to a system error.
    /// </exception>
    void BeforeCheckout();

    /// <summary>Returns the Git repository data used for hook validation during a checkout.</summary>
    GitRepositoryData GetGitRepositoryData();
}
