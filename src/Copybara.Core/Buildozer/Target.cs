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
using System.Text.RegularExpressions;
using Copybara.Common;
using Starlark.Eval;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara.Buildozer;

/// <summary>Specifies a target, including the package and name of target.</summary>
internal sealed class Target
{
    private static readonly Regex TargetNamePattern = new("^[^:]*:[^:]+$", RegexOptions.Compiled);

    private readonly string _pkg;
    private readonly string _name;

    private Target(string[] components)
    {
        Preconditions.CheckArgument(
            components.Length == 2, "{0}", string.Join(", ", components));

        _pkg = Preconditions.CheckNotNull(components[0]);
        _name = Preconditions.CheckNotNull(components[1]);
    }

    public string GetPackage() => _pkg;

    public string GetName() => _name;

    public override string ToString() => _pkg + ":" + _name;

    /// <summary>
    /// Parses a target specified in configuration.
    /// </summary>
    /// <param name="configString">target specified in the form <c>PKG:TARGET_NAME</c></param>
    /// <exception cref="EvalException">if <paramref name="configString"/> is not formatted correctly</exception>
    internal static Target FromConfig(string configString)
    {
        if (configString.StartsWith("/", StringComparison.Ordinal))
        {
            throw new EvalException("target must be relative and not start with '/' or '//'");
        }
        if (!TargetNamePattern.IsMatch(configString))
        {
            throw new EvalException(
                "target must be in the form of {PKG}:{TARGET_NAME}, e.g. foo/bar:baz");
        }
        return new Target(configString.Split(':', 2));
    }

    internal static ImmutableArray<string> AsStringList(IReadOnlyList<Target> targets) =>
        targets.Select(t => t.ToString()).ToImmutableArray();
}
