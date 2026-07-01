/*
 * Copyright (C) 2023 Google Inc.
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

namespace Copybara.Go;

/// <summary>Module used for go related Starlark operations.</summary>
[StarlarkBuiltin("go", Doc = "Module for Go related starlark operations")]
public class GoModule : IStarlarkValue
{
    private readonly RemoteFileOptions _remoteFileOptions;

    public GoModule(RemoteFileOptions remoteFileOptions)
    {
        _remoteFileOptions = Preconditions.CheckNotNull(remoteFileOptions);
    }

    [StarlarkMethod("go_proxy_version_list",
        Doc = "Returns go proxy version list object")]
    public GoProxyVersionList GetGoProxyVersionList(
        [Param(
            Name = "module",
            Named = true,
            AllowedTypes = new[] { typeof(string) },
            Doc =
                "The go module path name. e.g. github.com/google/gopacket. This will automatically"
                + " normalize uppercase characters to '!{your_uppercase_character}' to escape"
                + " them.")]
        string module,
        [Param(
            Name = "ref",
            Named = true,
            AllowedTypes = new[] { typeof(string), typeof(NoneType) },
            DefaultValue = "None",
            Doc =
                "This parameter is primarily used to track versions at specific branches and"
                + " revisions. If a value is supplied, the returned version list will attempt"
                + " to extract version data from ${ref}.info found with go proxy at the"
                + " /@v/${ref}.info endpoint. You can leave off the .info suffix.")]
        object @ref,
        [Param(
            Name = "auth",
            Doc = "Optional, an interceptor for providing credentials.",
            Named = true,
            DefaultValue = "None",
            AllowedTypes = new[] { typeof(IAuthInterceptor), typeof(NoneType) },
            Positional = false)]
        object maybeAuth)
    {
        string? refConvert = SkylarkUtil.ConvertFromNoneable<string>(@ref, null);
        IAuthInterceptor? auth = SkylarkUtil.ConvertFromNoneable<IAuthInterceptor>(maybeAuth, null);
        if (!string.IsNullOrEmpty(refConvert))
        {
            return GoProxyVersionList.ForInfo(module, refConvert, _remoteFileOptions, auth);
        }

        return GoProxyVersionList.ForVersion(module, _remoteFileOptions, auth);
    }

    [StarlarkMethod("go_proxy_resolver",
        Doc = "Go resolver that knows what to do with command line passed refs.")]
    public IVersionResolver GetResolver(
        [Param(
            Name = "module",
            Named = true,
            AllowedTypes = new[] { typeof(string) },
            Doc =
                "The go module path name. e.g. github.com/google/gopacket. This will automatically"
                + " normalize uppercase characters to '!{your_uppercase_character}' to escape"
                + " them.")]
        string module,
        [Param(
            Name = "auth",
            Doc = "Optional, an interceptor for providing credentials.",
            Named = true,
            DefaultValue = "None",
            AllowedTypes = new[] { typeof(IAuthInterceptor), typeof(NoneType) },
            Positional = false)]
        object auth) =>
        new GoProxyVersionResolver(
            module, _remoteFileOptions, SkylarkUtil.ConvertFromNoneable<IAuthInterceptor>(auth, null));
}
