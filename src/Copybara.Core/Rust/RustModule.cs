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

using Copybara.Config;
using Copybara.Exceptions;
using Copybara.Http.Auth;
using Copybara.RemoteFile;
using Copybara.Version;
using Starlark.Annot;
using Starlark.Eval;

namespace Copybara.Rust;

/// <summary>A module for importing Rust crates from crates.io.</summary>
/// <remarks>
/// NOTE(port): the upstream <c>download_fuzzers</c> method depends on the git module
/// (<c>GitOptions</c>, <c>GitDestinationReader</c>, <c>GitHubHost</c>) and the toml module, which are
/// ported separately. It is intentionally omitted here until those types are available. The version
/// resolution surface (version list, resolver, requirement, selector) is fully ported.
/// </remarks>
[StarlarkBuiltin("rust", Doc = "A module for importing Rust crates", Documented = false)]
public class RustModule : IStarlarkValue
{
    private readonly RemoteFileOptions _remoteFileOptions;
    private readonly GeneralOptions _generalOptions;

    public RustModule(RemoteFileOptions remoteFileOptions, GeneralOptions generalOptions)
    {
        _remoteFileOptions = remoteFileOptions;
        _generalOptions = generalOptions;
    }

    [StarlarkMethod("crates_io_version_list",
        Doc = "Returns a crates.io version_list object",
        Documented = false)]
    public RustCratesIoVersionList GetRustCratesIoVersionList(
        [Param(Name = "crate", Named = true, Doc = "The name of the crate, e.g. \"libc\"")]
        string crateName,
        [Param(
            Name = "match_pre_release_versions",
            Named = true,
            Doc =
                "Whether we should match pre-release versions of a crate when finding the latest"
                + " version.",
            DefaultValue = "False")]
        bool matchPreReleaseVersions,
        [Param(
            Name = "ignore_yanked_versions",
            Named = true,
            Doc = "Whether this list ignores yanked versions of a crate in the upstream.",
            DefaultValue = "True")]
        bool ignoreYankedVersions,
        [Param(
            Name = "auth",
            Doc = "Optional, an interceptor for providing credentials.",
            Named = true,
            DefaultValue = "None",
            AllowedTypes = new[] { typeof(IAuthInterceptor), typeof(NoneType) },
            Positional = false)]
        object auth) =>
        RustCratesIoVersionList.ForCrate(
            crateName,
            _remoteFileOptions,
            matchPreReleaseVersions,
            ignoreYankedVersions,
            SkylarkUtil.ConvertFromNoneable<IAuthInterceptor>(auth, null));

    [StarlarkMethod("crates_io_version_resolver",
        Doc = "A version resolver for Rust crates from crates.io",
        Documented = false)]
    public IVersionResolver GetResolver(
        [Param(Name = "crate", Named = true, Doc = "The name of the rust crate.")]
        string crate,
        [Param(
            Name = "match_pre_release_versions",
            Named = true,
            Doc =
                "Whether we should match pre-release versions of a crate when finding the latest"
                + " version.",
            DefaultValue = "False")]
        bool matchPreReleaseVersions,
        [Param(
            Name = "auth",
            Doc = "Optional, an interceptor for providing credentials.",
            Named = true,
            DefaultValue = "None",
            AllowedTypes = new[] { typeof(IAuthInterceptor), typeof(NoneType) },
            Positional = false)]
        object auth) =>
        new RustCratesIoVersionResolver(
            crate,
            _remoteFileOptions,
            matchPreReleaseVersions,
            SkylarkUtil.ConvertFromNoneable<IAuthInterceptor>(auth, null));

    [StarlarkMethod("create_version_requirement",
        Doc =
            "Represents a Cargo version requirement. You can compare version strings against this"
            + " object to determine if they meet this requirement or not. ")]
    public RustVersionRequirement GetVersionRequirement(
        [Param(Name = "requirement", Named = true, Doc = "The Cargo version requirement")]
        string requirement,
        [Param(
            Name = "allow_epochs",
            Named = true,
            DefaultValue = "False",
            Doc = "Allow epoch version requirements")]
        bool allowEpochs) =>
        RustVersionRequirement.GetVersionRequirement(requirement, allowEpochs);

    [StarlarkMethod("check_version_requirement",
        Doc =
            "Checks a version against a Cargo version requirement. Currently, default, caret, and"
            + " comparison requirements are supported. Please see"
            + " https://doc.rust-lang.org/cargo/reference/specifying-dependencies.html for more"
            + " information.",
        Documented = false)]
    public bool CheckVersionRequirement(
        [Param(Name = "requirement", Named = true, Doc = "The Cargo version requirement")]
        string requirement,
        [Param(Name = "version", Named = true, Doc = "The version to check")]
        string version) =>
        // TODO(chriscampos): Remove this in favor of getVersionRequirement
        RustVersionRequirement.GetVersionRequirement(requirement, false).Fulfills(version);

    [StarlarkMethod("crates_io_version_selector",
        Doc =
            "Returns a version selector that selects the latest version of a crate based on a version"
            + " requirement. e.g. \"1.2\" selects 1.2.3, 1.2.4, but not 1.3.0.",
        Documented = false)]
    public RustCratesIoVersionSelector GetCratesIoVersionSelector(
        [Param(Name = "requirement", Named = true, Doc = "The Cargo version requirement")]
        string requirement,
        [Param(
            Name = "allow_epochs",
            Named = true,
            DefaultValue = "False",
            Doc = "Allow epoch version requirements")]
        bool allowEpochs) =>
        new RustCratesIoVersionSelector(
            RustVersionRequirement.GetVersionRequirement(requirement, allowEpochs));
}
