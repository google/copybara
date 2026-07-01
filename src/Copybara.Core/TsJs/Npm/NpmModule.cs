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

using Copybara.Common;
using Copybara.Config;
using Copybara.Http.Auth;
using Copybara.RemoteFile;
using Copybara.Version;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.TsJs.Npm;

/// <summary>Module used for NPM related Starlark operations.</summary>
[StarlarkBuiltin("npm", Doc = "Module for NPM related starlark operations", Documented = false)]
public class NpmModule : IStarlarkValue
{
    private readonly RemoteFileOptions _remoteFileOptions;

    public NpmModule(RemoteFileOptions remoteFileOptions)
    {
        _remoteFileOptions = Preconditions.CheckNotNull(remoteFileOptions);
    }

    [StarlarkMethod("npm_version_list",
        Doc = "Returns npm version list object",
        Documented = false)]
    public NpmVersionList GetNpmVersionList(
        [Param(
            Name = "package_name",
            Named = true,
            AllowedTypes = new[] { typeof(string) },
            Doc = "The Npm package name, including scope with @ if applicable.")]
        string packageName,
        [Param(
            Name = "registry_url",
            Named = true,
            AllowedTypes = new[] { typeof(string) },
            DefaultValue = "'https://registry.npmjs.com'",
            Doc = "URL of the registry to use. Defaults to the public NPM registry.")]
        string registryUrl,
        [Param(
            Name = "auth",
            Doc = "Optional, an interceptor for providing credentials.",
            Named = true,
            DefaultValue = "None",
            AllowedTypes = new[] { typeof(IAuthInterceptor), typeof(NoneType) },
            Positional = false)]
        object auth) =>
        NpmVersionList.ForPackage(
            packageName,
            registryUrl,
            _remoteFileOptions,
            SkylarkUtil.ConvertFromNoneable<IAuthInterceptor>(auth, null));

    [StarlarkMethod("npm_resolver",
        Doc = "Npm resolver that knows what to do with command line passed refs.",
        Documented = false)]
    public IVersionResolver GetResolver(
        [Param(
            Name = "package_name",
            Named = true,
            AllowedTypes = new[] { typeof(string) },
            Doc = "The Npm package name")]
        string packageName,
        [Param(
            Name = "registry_url",
            Named = true,
            AllowedTypes = new[] { typeof(string) },
            DefaultValue = "'https://registry.npmjs.com'",
            Doc = "URL of the registry to use. Defaults to the public NPM registry.")]
        string registryUrl,
        [Param(
            Name = "auth",
            Doc = "Optional, an interceptor for providing credentials.",
            Named = true,
            DefaultValue = "None",
            AllowedTypes = new[] { typeof(IAuthInterceptor), typeof(NoneType) },
            Positional = false)]
        object auth) =>
        new NpmVersionResolver(
            packageName,
            registryUrl,
            _remoteFileOptions,
            SkylarkUtil.ConvertFromNoneable<IAuthInterceptor>(auth, null));
}
