/*
 * Copyright (C) 2022 Google Inc.
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
using Copybara.Authoring;
using Copybara.Common;
using Copybara.Exceptions;
using Copybara.Http.Auth;
using Copybara.RemoteFile.Extract;
using Copybara.Revision;
using Copybara.TemplateToken;
using Copybara.Util;
using Copybara.Version;
using Console = Copybara.Util.Console.Console;

namespace Copybara.RemoteFile;

/// <summary>An <see cref="IOrigin{R}"/> class for remote files.</summary>
public class RemoteArchiveOrigin : IOrigin<RemoteArchiveRevision>
{
    private const string LabelNameConst = "RemoteArchiveOrigin";

    private readonly Author _author;
    private readonly string _message;
    private readonly GeneralOptions _generalOptions;
    private readonly RemoteFileOptions _remoteFileOptions;
    private readonly string _archiveSourceUrl;
    private readonly IVersionList? _versionList;
    private readonly IVersionSelector? _versionSelector;
    private readonly RemoteFileType _remoteFileType;
    private readonly IVersionResolver? _versionResolver;
    private readonly IAuthInterceptor? _auth;

    public RemoteArchiveOrigin(
        Author author,
        string message,
        GeneralOptions generalOptions,
        RemoteFileOptions remoteFileOptions,
        RemoteFileType remoteFileType,
        string archiveSourceUrl,
        IVersionList? versionList,
        IVersionSelector? versionSelector,
        IVersionResolver? versionResolver,
        IAuthInterceptor? auth)
    {
        _remoteFileType = remoteFileType;
        _author = author;
        _message = message;
        _generalOptions = generalOptions;
        _remoteFileOptions = remoteFileOptions;
        _archiveSourceUrl = archiveSourceUrl;
        _versionList = versionList;
        _versionSelector = versionSelector;
        _versionResolver = versionResolver;
        _auth = auth;
    }

    /// <exception cref="LabelTemplate.LabelNotFoundException"/>
    private string ResolveUrlTemplate(string url, string version) =>
        new LabelTemplate(url).Resolve(
            label =>
            {
                if (label.Equals("VERSION")
                    || label.Equals("CONTEXT_REFERENCE")
                    || label.Equals("COPYBARA_CONTEXT_REFERENCE"))
                {
                    return version;
                }
                throw new ArgumentException(
                    string.Format(
                        "Archive source templates only support '${{VERSION}}', '${{CONTEXT_REFERENCE}}'"
                        + " or '${{COPYBARA_CONTEXT_REFERENCE}}' labels, but found '{0}'",
                        label));
            });

    private string? GetUrlAssemblyStrategy(string label)
    {
        try
        {
            return ResolveUrlTemplate(_archiveSourceUrl, label);
        }
        catch (LabelTemplate.LabelNotFoundException)
        {
            return null;
        }
    }

    /// <summary>This is used to resolve new refs.</summary>
    /// <param name="reference">the version to target. If left null/empty, we will deduce intended
    /// version from what was supplied to the constructor.</param>
    /// <exception cref="RepoException"/>
    /// <exception cref="ValidationException"/>
    public RemoteArchiveRevision Resolve(string? reference)
    {
        bool canUseResolverOnCliRef =
            _generalOptions.IsVersionSelectorUseCliRef() || _generalOptions.IsForced();
        _generalOptions
            .GetConsole()
            .WarnFmtIf(
                !canUseResolverOnCliRef
                    && !string.IsNullOrEmpty(reference)
                    && _versionResolver != null,
                "Resolve version ref for '{0}' was detected, but will not apply the supplied resolver."
                    + " Consider setting --force or --use-version-selector-ref to true.",
                reference!);

        // It's a versionless import.
        if (_versionList == null || _versionSelector == null)
        {
            string url = _archiveSourceUrl;
            if (!string.IsNullOrEmpty(reference))
            {
                try
                {
                    url = ResolveUrlTemplate(_archiveSourceUrl, reference);
                }
                catch (Exception e) when (e is LabelTemplate.LabelNotFoundException or ArgumentException)
                {
                    throw new ValidationException(
                        string.Format(
                            "Could not resolve archive URL template {0} with error '{1}' and the cause"
                            + " (if any) was '{2}'",
                            _archiveSourceUrl, e.Message, e.InnerException));
                }
            }
            return new RemoteArchiveRevision(new RemoteArchiveVersion(url, reference));
        }

        try
        {
            string version =
                _versionSelector.Select(_versionList, reference, _generalOptions.GetConsole())
                ?? throw new ValidationException("Version selector returned no results.");

            if (canUseResolverOnCliRef
                && !string.IsNullOrEmpty(reference)
                && _versionResolver != null)
            {
                return (RemoteArchiveRevision)_versionResolver.Resolve(version, GetUrlAssemblyStrategy);
            }
            var remoteArchiveVersion =
                new RemoteArchiveVersion(ResolveUrlTemplate(_archiveSourceUrl, version), version);
            return new RemoteArchiveRevision(remoteArchiveVersion);
        }
        catch (Exception e)
            when (e is LabelTemplate.LabelNotFoundException or ArgumentException or ValidationException)
        {
            throw new ValidationException(
                string.Format(
                    "Could not resolve archive URL template {0} with error '{1}' and the cause (if any)"
                    + " was '{2}'",
                    _archiveSourceUrl, e.Message, e.InnerException));
        }
    }

    /// <summary>This is used to resolve the baseline.</summary>
    /// <exception cref="RepoException"/>
    /// <exception cref="ValidationException"/>
    public RemoteArchiveRevision ResolveLastRev(string reference)
    {
        Preconditions.CheckState(
            !string.IsNullOrEmpty(reference),
            "Last migrated revision reference must not be null or empty.");
        if (_versionResolver != null)
        {
            return (RemoteArchiveRevision)_versionResolver.Resolve(reference, GetUrlAssemblyStrategy);
        }

        _generalOptions
            .GetConsole()
            .WarnFmt(
                "No version resolver was supplied, will attempt to resolve baseline version by url"
                + " template.");
        string fullUrl =
            GetUrlAssemblyStrategy(reference)
            ?? throw new ValidationException(
                string.Format(
                    "Could not construct remote archive version from url='{0}' and ref='{1}'",
                    _archiveSourceUrl, reference));
        return new RemoteArchiveRevision(new RemoteArchiveVersion(fullUrl, reference));
    }

    public IOrigin<RemoteArchiveRevision>.IReader<RemoteArchiveRevision> NewReader(
        Glob originFiles, Authoring.Authoring authoring) =>
        new Reader(this, originFiles);

    public string GetLabelName() => LabelNameConst;

    public string GetTypeName() => "remotefiles.origin";

    public ImmutableListMultimap<string, string> Describe(Glob? originFiles)
    {
        var builder = ImmutableListMultimap<string, string>.CreateBuilder();
        builder.Put("type", GetTypeName());
        builder.Put("url", _archiveSourceUrl); // the unresolved url
        if (originFiles != null)
        {
            builder.PutAll("root", originFiles.Roots());
        }
        return builder.Build();
    }

    public IReadOnlyList<ImmutableListMultimap<string, string>> DescribeCredentials()
    {
        var credentials = ImmutableArray.CreateBuilder<ImmutableListMultimap<string, string>>();
        if (_versionList != null)
        {
            credentials.AddRange(_versionList.DescribeCredentials());
        }
        if (_versionResolver != null)
        {
            credentials.AddRange(_versionResolver.DescribeCredentials());
        }
        if (_auth != null)
        {
            credentials.AddRange(_auth.DescribeCredentials());
        }
        return credentials.ToImmutable();
    }

    private sealed class Reader : IOrigin<RemoteArchiveRevision>.IReader<RemoteArchiveRevision>
    {
        private readonly RemoteArchiveOrigin _origin;
        private readonly Glob _originFiles;

        public Reader(RemoteArchiveOrigin origin, Glob originFiles)
        {
            _origin = origin;
            _originFiles = originFiles;
        }

        private void WriteArchiveAsIs(RemoteArchiveRevision reference, string workdir, Stream returned)
        {
            string url = reference.GetUrl()!;
            string filename = url[(_origin._archiveSourceUrl.LastIndexOf('/') + 1)..];
            using Stream sink = File.Create(PathOps.Resolve(workdir, filename));
            returned.CopyTo(sink);
        }

        /// <exception cref="ValidationException"/>
        public void Checkout(RemoteArchiveRevision reference, string checkoutDir)
        {
            try
            {
                // TODO(joshgoldman): Add richer ref object and ability to restrict download by
                // host/url
                var url = new Uri(Preconditions.CheckNotNull(reference.GetUrl()));
                IHttpStreamFactory transport = _origin._remoteFileOptions.GetTransport();
                using (_origin._generalOptions.Profiler().Start("remote_file_" + url))
                using (Stream returned = transport.Open(url, _origin._auth))
                {
                    if (_origin._remoteFileType == RemoteFileType.AS_IS)
                    {
                        WriteArchiveAsIs(reference, checkoutDir, returned);
                    }
                    else
                    {
                        ExtractUtil.ExtractArchive(
                            returned,
                            checkoutDir,
                            RemoteFileTypeExtensions.ToExtractType(_origin._remoteFileType),
                            _originFiles);
                    }
                }
            }
            catch (IOException e)
            {
                throw new ValidationException(
                    string.Format(
                        "Could not checkout archive file at {0}: \n{1}", reference.GetUrl(), e.Message));
            }
        }

        /// <exception cref="RepoException"/>
        public Origin.ChangesResponse<RemoteArchiveRevision> Changes(
            RemoteArchiveRevision? fromRef, RemoteArchiveRevision toRef)
        {
            var console = _origin._generalOptions.GetConsole();
            if (_origin._versionSelector == null)
            {
                return Origin.ChangesResponse<RemoteArchiveRevision>.ForChanges(
                    ImmutableArray.Create(Change(toRef)));
            }
            if (fromRef == null)
            {
                console.WarnFmt(
                    "The baseline revision could not be detected, not performing downgrade"
                    + " validation.");
                return Origin.ChangesResponse<RemoteArchiveRevision>.ForChanges(
                    ImmutableArray.Create(Change(toRef)));
            }
            if (string.IsNullOrEmpty(fromRef.FixedReference())
                || string.IsNullOrEmpty(toRef.FixedReference()))
            {
                console.WarnFmt(
                    "Either the baseline ref[{0}] or the incoming ref[{1}] form as a fixed ref were"
                    + " not known, not performing downgrade validation.",
                    fromRef.FixedReference()!, toRef.FixedReference()!);
                return Origin.ChangesResponse<RemoteArchiveRevision>.ForChanges(
                    ImmutableArray.Create(Change(toRef)));
            }

            try
            {
                string? selectedVersion =
                    _origin._versionSelector.Select(
                        new SetVersionList(
                            ImmutableHashSet.Create(toRef.FixedReference()!, fromRef.FixedReference()!)),
                        requestedRef: null,
                        console);

                if (selectedVersion != null && selectedVersion.Equals(fromRef.FixedReference()))
                {
                    console.WarnFmt(
                        "The incoming ref [{0}] is not newer than the baseline ref [{1}]. "
                        + "The change response will have no changes generated.",
                        toRef.FixedReference()!, fromRef.FixedReference()!);
                    return Origin.ChangesResponse<RemoteArchiveRevision>.NoChanges(
                        Origin.EmptyReason.ToIsAncestor);
                }
            }
            catch (ValidationException e)
            {
                console.WarnFmt(
                    "An error has occurred while validating the order of changes between {0} and {1}:"
                    + " '{2}'. Defaulting to a changelist with only the incoming ref.",
                    fromRef.FixedReference()!, toRef.FixedReference()!, e.Message);
            }
            return Origin.ChangesResponse<RemoteArchiveRevision>.ForChanges(
                ImmutableArray.Create(Change(toRef)));
        }

        /// <exception cref="RepoException"/>
        public Change<RemoteArchiveRevision> Change(RemoteArchiveRevision reference) =>
            new(
                reference,
                _origin._author,
                _origin._message,
                reference.ReadTimestamp() ?? DateTimeOffset.Now,
                ImmutableListMultimap<string, string>.Empty);

        /// <exception cref="RepoException"/>
        public void VisitChanges(RemoteArchiveRevision? start, IChangesVisitor visitor)
        {
            RemoteArchiveRevision reference = start!;
            var change = new Change<IRevision>(
                reference,
                _origin._author,
                _origin._message,
                reference.ReadTimestamp() ?? DateTimeOffset.Now,
                ImmutableListMultimap<string, string>.Empty);
            visitor.Visit(change);
        }
    }
}
