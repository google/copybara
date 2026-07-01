/*
 * Copyright (C) 2020 Google Inc.
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

using Copybara.Authoring;
using Copybara.Common;
using Copybara.Config;
using Copybara.Exceptions;
using Copybara.Http.Auth;
using Copybara.Version;
using Starlark.Annot;
using Starlark.Eval;
using StarlarkRt = Starlark.Eval.Starlark;

namespace Copybara.RemoteFile;

/// <summary>Module for helpers to load files from a source other than the origin. Use with caution.</summary>
[StarlarkBuiltin("remotefiles", Doc = "Functions to access remote files not in either repo.")]
public class RemoteFileModule : ILabelsAwareModule, IStarlarkValue
{
    protected readonly Options Options;

    public RemoteFileModule(Options options)
    {
        Options = Preconditions.CheckNotNull(options);
    }

    [StarlarkMethod(
        "github_archive",
        Doc = "A tarball for a specific SHA1 on GitHub. Experimental.",
        Documented = false)]
    public GithubArchive GitHubTarball(
        [Param(
            Name = "project",
            Named = true,
            DefaultValue = "[]",
            Doc = "The GitHub project from which to load the file, e.g. google/copybara")]
        string project,
        [Param(
            Name = "revision",
            Named = true,
            DefaultValue = "[]",
            Doc = "The revision to download from the project, typically a commit SHA1.")]
        string revision,
        [Param(
            Name = "type",
            Named = true,
            DefaultValue = "'TARBALL'",
            Doc = "Archive type to download, options are 'TARBALL' or 'ZIP'.")]
        string type,
        [Param(
            Name = "auth",
            Doc = "Optional, an interceptor for providing credentials.",
            Named = true,
            DefaultValue = "None",
            Positional = false,
            AllowedTypes = new[] { typeof(IAuthInterceptor), typeof(NoneType) })]
        object auth)
    {
        var generalOptions = Options.Get<GeneralOptions>();
        var remoteFileOptions = Options.Get<RemoteFileOptions>();
        try
        {
            if (!Enum.TryParse<GithubArchive.ArchiveType>(type, out var archiveType)
                || !Enum.IsDefined(archiveType))
            {
                throw StarlarkRt.Errorf(
                    "Unsupported archive type: '{0}'. Supported values: {1}",
                    type, string.Join(", ", Enum.GetNames<GithubArchive.ArchiveType>()));
            }
            return new GithubArchive(
                project,
                revision,
                archiveType,
                remoteFileOptions.GetTransport(),
                generalOptions.Profiler(),
                generalOptions.GetConsole(),
                SkylarkUtil.ConvertFromNoneable<IAuthInterceptor?>(auth, null));
        }
        catch (ValidationException e)
        {
            throw StarlarkRt.Errorf("Error setting up remote http file: {0}", e.Message);
        }
    }

    [StarlarkMethod("origin", Doc = "Defines a remote file origin.")]
    public RemoteArchiveOrigin RemoteArchiveOrigin(
        [Param(
            Name = "author",
            DefaultValue = "'Copybara <noreply@copybara.io>'",
            Doc = "Author to attribute the change to",
            Named = true,
            AllowedTypes = new[] { typeof(string) })]
        string author,
        [Param(
            Name = "message",
            DefaultValue = "'Placeholder message'",
            Doc = "Message to attach to the change",
            Named = true,
            AllowedTypes = new[] { typeof(string) })]
        string message,
        [Param(
            Name = "unpack_method",
            DefaultValue = "'AS_IS'",
            Doc =
                "The method by which to unpack the remote file. Currently 'ZIP', 'TAR', 'TAR_GZ',"
                + " 'TAR_XZ', 'TAR_BZ2', and 'AS_IS' are supported.",
            Named = true,
            AllowedTypes = new[] { typeof(string) })]
        string fileType,
        [Param(
            Name = "archive_source",
            Named = true,
            Doc =
                "Template or literal URL to download archive from. Optionally you can use"
                + " ${VERSION} in your URL string as placeholder for later resolved versions"
                + " during origin checkout. E.g."
                + " 'https://proxy.golang.org/mymodule/@v/${VERSION}.zip'",
            DefaultValue = "''",
            AllowedTypes = new[] { typeof(string) })]
        string archiveSourceUrl,
        [Param(
            Name = "version_list",
            Named = true,
            DefaultValue = "None",
            Doc = "Version list to select versions on. Omit to create a versionless origin.",
            AllowedTypes = new[] { typeof(IVersionList), typeof(NoneType) })]
        object versionList,
        [Param(
            Name = "origin_version_selector",
            Named = true,
            DefaultValue = "None",
            Doc = "Version selector used to select on version_list. Omit to create a versionless origin.",
            AllowedTypes = new[] { typeof(IVersionSelector), typeof(NoneType) })]
        object versionSelector,
        [Param(
            Name = "version_resolver",
            Named = true,
            DefaultValue = "None",
            Doc =
                "Version resolvers are used to resolve refs to specific versions. Primarily used"
                + " when command line refs are provided and accompanied by the '--force' or"
                + " '--version-selector-use-cli-ref' flag.",
            AllowedTypes = new[] { typeof(IVersionResolver), typeof(NoneType) })]
        object versionResolver,
        [Param(
            Name = "auth",
            Doc = "Optional, an interceptor for providing credentials.",
            Named = true,
            DefaultValue = "None",
            Positional = false,
            AllowedTypes = new[] { typeof(IAuthInterceptor), typeof(NoneType) })]
        object auth)
    {
        var generalOptions = Options.Get<GeneralOptions>();
        var remoteFileOptions = Options.Get<RemoteFileOptions>();
        RemoteFileType remoteFileType =
            SkylarkUtil.StringToEnum<RemoteFileType>("unpack_method", fileType);
        return new RemoteArchiveOrigin(
            Author.Parse(author),
            message,
            generalOptions,
            remoteFileOptions,
            remoteFileType,
            archiveSourceUrl,
            SkylarkUtil.ConvertFromNoneable<IVersionList?>(versionList, null),
            SkylarkUtil.ConvertFromNoneable<IVersionSelector?>(versionSelector, null),
            SkylarkUtil.ConvertFromNoneable<IVersionResolver?>(versionResolver, null),
            SkylarkUtil.ConvertFromNoneable<IAuthInterceptor?>(auth, null));
    }
}
