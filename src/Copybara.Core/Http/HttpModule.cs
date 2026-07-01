/*
 * Copyright (C) 2023 Google LLC.
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
using System.Text;
using Copybara.Checks;
using Copybara.Config;
using Copybara.Credentials;
using Copybara.Exceptions;
using Copybara.Http.Auth;
using Copybara.Http.Endpoint;
using Copybara.Http.Json;
using Copybara.Http.Multipart;
using Starlark.Annot;
using Starlark.Eval;
using Console = Copybara.Util.Console.Console;
using StarlarkList = Starlark.Eval.StarlarkList;

namespace Copybara.Http;

/// <summary>Starlark methods for working with the http endpoint.</summary>
[StarlarkBuiltin("http", Doc = "Module for working with http endpoints.")]
public class HttpModule : IStarlarkValue
{
    private readonly Console _console;
    private readonly HttpOptions _options;

    public HttpModule(Console console, HttpOptions options)
    {
        _console = console;
        _options = options;
    }

    [StarlarkMethod("url_encode", Doc = "URL-encode the input string")]
    public string UrlEncode(
        [Param(Name = "input", Doc = "The string to be encoded.",
            AllowedTypes = new[] { typeof(string) })]
        string input) => Uri.EscapeDataString(input);

    [StarlarkMethod("trigger", Doc = "Trigger for http endpoint")]
    public ITrigger Trigger(
        [Param(
            Name = "hosts",
            Doc = "A list of hosts to allow HTTP traffic to.",
            Named = true,
            AllowedTypes = new[] { typeof(StarlarkList) },
            DefaultValue = "[]",
            Positional = false)]
        StarlarkList hosts,
        [Param(
            Name = "issuers",
            Doc = "A dictionary of credential issuers.",
            Named = true,
            AllowedTypes = new[] { typeof(Dict), typeof(NoneType) },
            DefaultValue = "{}",
            Positional = false)]
        object? issuers,
        [Param(
            Name = "checker",
            AllowedTypes = new[] { typeof(IChecker), typeof(NoneType) },
            DefaultValue = "None",
            Doc = "A checker that will check calls made by the endpoint",
            Named = true,
            Positional = false)]
        object? checkerIn)
    {
        var endpoint = new HttpEndpoint(
            _console,
            _options.GetTransport(),
            BuildHostsMapWithAuthInterceptor(hosts),
            BuildIssuersMap(issuers),
            SkylarkUtil.ConvertFromNoneable<IChecker>(checkerIn, null));
        return new HttpTrigger(endpoint);
    }

    [StarlarkMethod(
        "endpoint",
        Doc = "Endpoint that executes any sort of http request. Currently restricted"
            + "to requests to specific hosts.")]
    public EndpointProvider<HttpEndpoint> Endpoint(
        [Param(
            Name = "host",
            Doc = "DEPRECATED. A single host to allow HTTP traffic to.",
            Named = true,
            AllowedTypes = new[] { typeof(string) },
            DefaultValue = "''",
            Positional = false)]
        string? host,
        [Param(
            Name = "checker",
            AllowedTypes = new[] { typeof(IChecker), typeof(NoneType) },
            DefaultValue = "None",
            Doc = "A checker that will check calls made by the endpoint",
            Named = true,
            Positional = false)]
        object? checkerIn,
        [Param(
            Name = "hosts",
            Doc = "A list of hosts to allow HTTP traffic to.",
            Named = true,
            AllowedTypes = new[] { typeof(StarlarkList) },
            DefaultValue = "[]",
            Positional = false)]
        StarlarkList hosts,
        [Param(
            Name = "issuers",
            Doc = "A dictionaty of credential issuers.",
            Named = true,
            AllowedTypes = new[] { typeof(Dict), typeof(NoneType) },
            DefaultValue = "{}",
            Positional = false)]
        object? issuers)
    {
        IChecker? checker = SkylarkUtil.ConvertFromNoneable<IChecker>(checkerIn, null);
        var h = BuildHostsMapBuilder(hosts);
        if (!string.IsNullOrEmpty(host))
        {
            h[host] = null;
        }

        return EndpointProvider.Wrap(
            new HttpEndpoint(
                _console,
                _options.GetTransport(),
                h.ToImmutableDictionary(),
                BuildIssuersMap(issuers),
                checker));
    }

    private ImmutableDictionary<string, CredentialIssuer> BuildIssuersMap(object? issuers)
    {
        var issuersMap = ImmutableDictionary.CreateBuilder<string, CredentialIssuer>();
        if (issuers is Dict dict)
        {
            foreach (var entry in dict.Entries)
            {
                issuersMap[(string)entry.Key!] = (CredentialIssuer)entry.Value!;
            }
        }

        return issuersMap.ToImmutable();
    }

    private ImmutableDictionary<string, IAuthInterceptor?> BuildHostsMapWithAuthInterceptor(
        StarlarkList hosts) => BuildHostsMapBuilder(hosts).ToImmutableDictionary();

    private Dictionary<string, IAuthInterceptor?> BuildHostsMapBuilder(StarlarkList hosts)
    {
        var h = new Dictionary<string, IAuthInterceptor?>();
        foreach (object? o in hosts)
        {
            if (o is HostCredential withCred)
            {
                h[withCred.Host] = withCred.Creds;
            }
            else
            {
                h[(string)o!] = null;
            }
        }

        return h;
    }

    [StarlarkMethod("urlencoded_form", Doc = "Creates a url-encoded form HTTP body.")]
    public HttpEndpointUrlEncodedFormContent UrlEncodedFormContent(
        [Param(
            Name = "body",
            Doc = "HTTP body object, property name will be used as key and value as value.",
            AllowedTypes = new[] { typeof(Dict) },
            DefaultValue = "{}")]
        object body) => new(body);

    [StarlarkMethod("multipart_form", Doc = "Creates a multipart form http body.")]
    public HttpEndpointMultipartFormContent MultipartFormContent(
        [Param(
            Name = "parts",
            Doc = "A list of form parts",
            AllowedTypes = new[] { typeof(StarlarkList) },
            DefaultValue = "[]")]
        StarlarkList partsIn)
    {
        var parts = Sequence.Cast<IHttpEndpointFormPart>(partsIn, "parts").ToList();
        return new HttpEndpointMultipartFormContent(parts);
    }

    [StarlarkMethod("multipart_form_text", Doc = "Create a text/plain part for a multipart form payload")]
    public IHttpEndpointFormPart MultipartFormTextField(
        [Param(Name = "name", Doc = "The name of the form field.",
            AllowedTypes = new[] { typeof(string) })]
        string name,
        [Param(Name = "text", Doc = "The form value of the field",
            AllowedTypes = new[] { typeof(string) })]
        string text) => new TextPart(name, text);

    [StarlarkMethod("multipart_form_file", Doc = "Create a file part for a multipart form payload.")]
    public IHttpEndpointFormPart MultipartFormFileField(
        [Param(Name = "name", Doc = "The name of the form field.",
            AllowedTypes = new[] { typeof(string) })]
        string name,
        [Param(Name = "path",
            Doc = "The checkout path pointing to the file to use as the field value.",
            AllowedTypes = new[] { typeof(CheckoutPath) })]
        CheckoutPath path,
        [Param(
            Name = "content_type",
            Doc = "Content type header value for the form part. "
                + "Defaults to application/octet-stream. \n"
                + "https://www.w3.org/Protocols/rfc1341/4_Content-Type.html",
            AllowedTypes = new[] { typeof(string) },
            Named = true,
            Positional = false,
            DefaultValue = "\"application/octet-stream\"")]
        string contentType,
        [Param(
            Name = "filename",
            Doc = "The filename that will be sent along with the data. "
                + "Defaults to the filename of the path parameter. "
                + "Sets the filename parameter in the content disposition "
                + "header. \n"
                + "https://www.w3.org/Protocols/HTTP/Issues/content-disposition.txt",
            AllowedTypes = new[] { typeof(string), typeof(NoneType) },
            Named = true,
            Positional = false,
            DefaultValue = "None")]
        object? filenameIn)
    {
        string? filename = SkylarkUtil.ConvertOptionalString(filenameIn);
        return new FilePart(name, path.FullPath(), contentType, filename);
    }

    [StarlarkMethod("json", Doc = "Creates a JSON HTTP body.")]
    public HttpEndpointJsonContent JsonContent(
        [Param(
            Name = "body",
            Doc = "HTTP body object, property name will be used as key and value as value.",
            AllowedTypes = new[] { typeof(object) },
            DefaultValue = "{}")]
        object? body) => new(body);

    [StarlarkMethod("host", Doc = "Wraps a host and potentially credentials for http auth.")]
    public HostCredential Host(
        [Param(
            Name = "host",
            Doc = "The host to be contacted.",
            Named = true,
            AllowedTypes = new[] { typeof(string) },
            Positional = false)]
        string host,
        [Param(
            Name = "auth",
            Doc = "Optional, an interceptor for providing credentials. Also accepts a "
                + "username_password.",
            Named = true,
            DefaultValue = "None",
            AllowedTypes = new[]
            {
                typeof(IAuthInterceptor),
                typeof(UsernamePasswordIssuer),
                typeof(NoneType),
            },
            Positional = false)]
        object? maybeCreds)
    {
        if (maybeCreds is UsernamePasswordIssuer upi)
        {
            maybeCreds = new UsernamePasswordInterceptor(upi);
        }

        IAuthInterceptor? creds = SkylarkUtil.ConvertFromNoneable<IAuthInterceptor>(maybeCreds, null);
        return new HostCredential(host, creds);
    }

    [StarlarkMethod("username_password_auth", Doc = "Authentication via username and password.")]
    public UsernamePasswordInterceptor UsernamePasswordAuth(
        [Param(
            Name = "creds",
            Doc = "The username and password credentials.",
            Named = true,
            AllowedTypes = new[] { typeof(UsernamePasswordIssuer) },
            Positional = false)]
        UsernamePasswordIssuer creds) => new(creds);

    [StarlarkMethod("bearer_auth", Doc = "Authentication via a bearer token.")]
    public BearerInterceptor BearerAuth(
        [Param(
            Name = "creds",
            Doc = "The token credentials.",
            Named = true,
            AllowedTypes = new[] { typeof(CredentialIssuer) },
            Positional = false)]
        CredentialIssuer creds) => new(creds);

    /// <summary>A host paired with optional auth credentials.</summary>
    public sealed class HostCredential : IStarlarkValue
    {
        public HostCredential(string host, IAuthInterceptor? creds)
        {
            Host = host;
            Creds = creds;
        }

        public string Host { get; }

        public IAuthInterceptor? Creds { get; }
    }
}
