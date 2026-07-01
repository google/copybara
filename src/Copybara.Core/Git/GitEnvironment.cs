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

namespace Copybara.Git;

/// <summary>
/// Environment for running git commands. Port of
/// <c>com.google.copybara.git.GitEnvironment</c>.
/// </summary>
public sealed class GitEnvironment
{
    private readonly ImmutableDictionary<string, string> _environment;
    private readonly bool _noGitPrompt;

    public GitEnvironment(IReadOnlyDictionary<string, string> environment)
        : this(environment, noGitPrompt: false)
    {
    }

    public GitEnvironment(IReadOnlyDictionary<string, string> environment, bool noGitPrompt)
    {
        _environment = Preconditions.CheckNotNull(environment).ToImmutableDictionary();
        _noGitPrompt = noGitPrompt;
    }

    /// <summary>
    /// Returns the environment to pass to git subprocesses. Explicitly forces the output language to
    /// english so parsing of git's output succeeds independently of the user's default locale.
    /// </summary>
    public ImmutableDictionary<string, string> GetEnvironment()
    {
        var env = new Dictionary<string, string>(_environment);

        // Explicitly set output language to english so parsing of git's output succeeds
        // independently of users default locale.
        env["LANG"] = "en_US.UTF-8";

        if (_noGitPrompt)
        {
            env["GIT_TERMINAL_PROMPT"] = "0";
        }

        return env.ToImmutableDictionary();
    }

    /// <summary>
    /// Returns a copy of this environment, with the given vars added. If a key is already present,
    /// its value is overwritten.
    /// </summary>
    public GitEnvironment WithVars(IReadOnlyDictionary<string, string> vars)
    {
        var allVars = new Dictionary<string, string>(_environment);
        foreach (var kvp in vars)
        {
            allVars[kvp.Key] = kvp.Value;
        }
        return new GitEnvironment(allVars, _noGitPrompt);
    }

    /// <summary>
    /// Returns a copy of this environment, setting explicitly to prevent Git from asking for
    /// username/password and fail if the credentials cannot be resolved.
    /// </summary>
    public GitEnvironment WithNoGitPrompt() => new(_environment, noGitPrompt: true);

    /// <summary>
    /// Returns a String representing the git binary to be executed.
    ///
    /// <para>The env var <c>GIT_EXEC_PATH</c> determines where Git looks for its sub-programs, but
    /// also the regular git binaries (git, git-upload-pack, etc) are duplicated in
    /// <c>GIT_EXEC_PATH</c>.</para>
    ///
    /// <para>If the env var is not set, then we will execute "git", that it will be resolved in the
    /// path as usual.</para>
    /// </summary>
    public string ResolveGitBinary()
    {
        if (_environment.TryGetValue("GIT_EXEC_PATH", out var execPath))
        {
            return Path.Combine(execPath, "git");
        }
        return "git";
    }
}
