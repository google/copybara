/*
 * Copyright (C) 2024 Google LLC.
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

using Copybara.Exceptions;

namespace Copybara.TsJs.Npm;

/// <summary>
/// An NpmPackageIdentifier is a structured representation of an NPM package's name and scope values.
/// </summary>
internal sealed class NpmPackageIdentifier
{
    public string Scope { get; }

    public string Name { get; }

    private NpmPackageIdentifier(string scope, string name)
    {
        Scope = scope;
        Name = name;
    }

    /// <exception cref="ValidationException"/>
    public static NpmPackageIdentifier FromPackage(string packageName)
    {
        string[] parts = packageName.Split('/');
        if (parts.Length == 1)
        {
            return new NpmPackageIdentifier("", packageName);
        }

        ValidationException.CheckCondition(
            parts.Length == 2, "probably invalid package name %s", packageName);
        string scope = parts[0];
        string name = parts[1];
        ValidationException.CheckCondition(
            scope[0] == '@', "package scopes should start with \"@\"");
        return new NpmPackageIdentifier(scope.Substring(1), name);
    }

    public string ToHumanReadableName()
    {
        if (Scope.Length == 0)
        {
            return Name;
        }

        return $"@{Scope}/{Name}";
    }

    public override string ToString() =>
        $"NpmPackageIdentifier{{scope={Scope}, package={Name}}}";
}
