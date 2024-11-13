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

package com.google.copybara.http;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableMap;
import com.google.copybara.CheckoutPath;
import com.google.copybara.EndpointProvider;
import com.google.copybara.Trigger;
import com.google.copybara.checks.Checker;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.credentials.CredentialIssuer;
import com.google.copybara.credentials.CredentialModule.UsernamePasswordIssuer;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.http.auth.AuthInterceptor;
import com.google.copybara.http.auth.BearerInterceptor;
import com.google.copybara.http.auth.UsernamePasswordInterceptor;
import com.google.copybara.http.endpoint.HttpEndpoint;
import com.google.copybara.http.json.HttpEndpointJsonContent;
import com.google.copybara.http.multipart.FilePart;
import com.google.copybara.http.multipart.HttpEndpointFormPart;
import com.google.copybara.http.multipart.HttpEndpointMultipartFormContent;
import com.google.copybara.http.multipart.HttpEndpointUrlEncodedFormContent;
import com.google.copybara.http.multipart.TextPart;
import com.google.copybara.util.console.Console;
import java.net.URLEncoder;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.NoneType;
import net.starlark.java.eval.Sequence;
import net.starlark.java.eval.StarlarkValue;

/** Starlark methods for working with the http endpoint. */
@StarlarkBuiltin(name = "http", doc = "Module for working with http endpoints.")
public class HttpModule implements StarlarkValue {
  HttpOptions options;
  Console console;

  public HttpModule(Console console, HttpOptions options) {
    this.console = console;
    this.options = options;
  }

  @StarlarkMethod(
      name = "url_encode",
      doc = "URL-encode the input string",
      parameters = {
        @Param(
            name = "input",
            doc = "The string to be encoded.",
            allowedTypes = {@ParamType(type = String.class)})
      })
  public String urlEncode(String input) {
    return URLEncoder.encode(input, UTF_8);
  }

  @StarlarkMethod(
      name = "trigger",
      doc = "Trigger for http endpoint",
      parameters = {
        @Param(
            name = "hosts",
            doc = "A list of hosts to allow HTTP traffic to.",
            named = true,
            allowedTypes = {@ParamType(type = Sequence.class)},
            defaultValue = "[]",
            positional = false),
        @Param(
            name = "issuers",
            doc = "A dictionary of credential issuers.",
            named = true,
            allowedTypes = {@ParamType(type = Dict.class), @ParamType(type = NoneType.class)},
            defaultValue = "{}",
            positional = false),
        @Param(
            name = "checker",
            allowedTypes = {
              @ParamType(type = Checker.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            doc = "A checker that will check calls made by the endpoint",
            named = true,
            positional = false),
      })
  public Trigger trigger(Sequence<?> hosts, @Nullable Object issuers, @Nullable Object checkerIn)
      throws ValidationException, EvalException {
    HttpEndpoint endpoint =
        new HttpEndpoint(
            console,
            options.getTransport(),
            buildHostsMapWithAuthInterceptor(hosts).buildKeepingLast(),
            buildIssuersMap(issuers).buildKeepingLast(),
            SkylarkUtil.convertFromNoneable(checkerIn, null));
    return new HttpTrigger(endpoint);
  }

  @StarlarkMethod(
      name = "endpoint",
      doc =
          "Endpoint that executes any sort of http request. Currently restricted"
              + "to requests to specific hosts.",
      parameters = {
        @Param(
            name = "host",
            doc = "DEPRECATED. A single host to allow HTTP traffic to.",
            named = true,
            allowedTypes = {
              @ParamType(type = String.class),
            },
            defaultValue = "''",
            positional = false),
        @Param(
            name = "checker",
            allowedTypes = {
              @ParamType(type = Checker.class),
              @ParamType(type = NoneType.class),
            },
            defaultValue = "None",
            doc = "A checker that will check calls made by the endpoint",
            named = true,
            positional = false),
        @Param(
            name = "hosts",
            doc = "A list of hosts to allow HTTP traffic to.",
            named = true,
            allowedTypes = {@ParamType(type = Sequence.class)},
            defaultValue = "[]",
            positional = false),
        @Param(
            name = "issuers",
            doc = "A dictionaty of credential issuers.",
            named = true,
            allowedTypes = {@ParamType(type = Dict.class), @ParamType(type = NoneType.class)},
            defaultValue = "{}",
            positional = false),
      })
  public EndpointProvider<HttpEndpoint> endpoint(
      @Nullable String host,
      @Nullable Object checkerIn,
      Sequence<?> hosts,
      @Nullable Object issuers)
      throws ValidationException, EvalException {
    @Nullable Checker checker = SkylarkUtil.convertFromNoneable(checkerIn, null);
    ImmutableMap.Builder<String, Optional<AuthInterceptor>> h =
        buildHostsMapWithAuthInterceptor(hosts);
    if (host != null && !host.isEmpty()) {
      h.put(host, Optional.empty());
    }

    return EndpointProvider.wrap(
        new HttpEndpoint(
            console,
            options.getTransport(),
            h.buildKeepingLast(),
            buildIssuersMap(issuers).buildKeepingLast(),
            checker));
  }

  private ImmutableMap.Builder<String, CredentialIssuer> buildIssuersMap(Object issuers) {
    ImmutableMap.Builder<String, CredentialIssuer> issuersMap = ImmutableMap.builder();
    if (issuers == null) {
      return issuersMap;
    }
    for (Map.Entry<?, ?> entry : ((Dict<?, ?>) issuers).entrySet()) {
      issuersMap.put((String) entry.getKey(), (CredentialIssuer) entry.getValue());
    }
    return issuersMap;
  }

  private ImmutableMap.Builder<String, Optional<AuthInterceptor>> buildHostsMapWithAuthInterceptor(
      Sequence<?> hosts) {
    ImmutableMap.Builder<String, Optional<AuthInterceptor>> h = ImmutableMap.builder();
    for (Object o : hosts) {
      if (o instanceof HostCredential withCred) {
        h.put(withCred.host(), withCred.creds());
      } else {
        h.put((String) o, Optional.empty());
      }
    }
    return h;
  }

  @StarlarkMethod(
      name = "urlencoded_form",
      doc = "Creates a url-encoded form HTTP body.",
      parameters = {
        @Param(
            name = "body",
            doc = "HTTP body object, property name will be used as key and value as value.",
            allowedTypes = {@ParamType(type = Dict.class)},
            defaultValue = "{}"),
      })
  public HttpEndpointUrlEncodedFormContent urlEncodedFormContent(Object body) {
    return new HttpEndpointUrlEncodedFormContent(body);
  }

  @StarlarkMethod(
      name = "multipart_form",
      doc = "Creates a multipart form http body.",
      parameters = {
        @Param(
            name = "parts",
            doc = "A list of form parts",
            allowedTypes = {
              @ParamType(type = Sequence.class),
            },
            defaultValue = "[]")
      })
  public HttpEndpointMultipartFormContent multipartFormContent(Sequence<?> partsIn)
      throws EvalException {
    Sequence<HttpEndpointFormPart> parts =
        Sequence.cast(partsIn, HttpEndpointFormPart.class, "parts");
    return new HttpEndpointMultipartFormContent(parts);
  }

  @StarlarkMethod(
      name = "multipart_form_text",
      doc = "Create a text/plain part for a multipart form payload",
      parameters = {
        @Param(
            name = "name",
            doc = "The name of the form field.",
            allowedTypes = {@ParamType(type = String.class)}),
        @Param(
            name = "text",
            doc = "The form value of the field",
            allowedTypes = {@ParamType(type = String.class)})
      })
  public HttpEndpointFormPart multipartFormTextField(String name, String text) {
    return new TextPart(name, text);
  }

  @StarlarkMethod(
      name = "multipart_form_file",
      doc = "Create a file part for a multipart form payload.",
      parameters = {
        @Param(
            name = "name",
            doc = "The name of the form field.",
            allowedTypes = {
              @ParamType(type = String.class),
            }),
        @Param(
            name = "path",
            doc = "The checkout path pointing to the file to use " + "as the field value.",
            allowedTypes = {
              @ParamType(type = CheckoutPath.class),
            }),
        @Param(
            name = "content_type",
            doc =
                "Content type header value for the form part. "
                    + "Defaults to application/octet-stream. \n"
                    + "https://www.w3.org/Protocols/rfc1341/4_Content-Type.html",
            allowedTypes = {
              @ParamType(type = String.class),
            },
            named = true,
            positional = false,
            defaultValue = "\"application/octet-stream\""),
        @Param(
            name = "filename",
            doc =
                "The filename that will be sent along with the data. "
                    + "Defaults to the filename of the path parameter. "
                    + "Sets the filename parameter in the content disposition "
                    + "header. \n"
                    + "https://www.w3.org/Protocols/HTTP/Issues/content-disposition.txt",
            allowedTypes = {
              @ParamType(type = String.class),
              @ParamType(type = NoneType.class),
            },
            named = true,
            positional = false,
            defaultValue = "None"),
      })
  public HttpEndpointFormPart multipartFormFileField(
      String name, CheckoutPath path, String contentType, Object filenameIn) {
    @Nullable String filename = SkylarkUtil.convertOptionalString(filenameIn);
    return new FilePart(name, path.getCheckoutDir().resolve(path.getPath()), contentType, filename);
  }

  @StarlarkMethod(
      name = "json",
      doc = "Creates a JSON HTTP body.",
      parameters = {
        @Param(
            name = "body",
            doc = "HTTP body object, property name will be used as key and value as value.",
            allowedTypes = {@ParamType(type = Object.class)},
            defaultValue = "{}"),
      })
  public HttpEndpointJsonContent jsonContent(Object body) {
    return new HttpEndpointJsonContent(body);
  }

  @StarlarkMethod(
      name = "host",
      doc = "Wraps a host and potentially credentials for http auth.",
      parameters = {
        @Param(
            name = "host",
            doc = "The host to be contacted.",
            named = true,
            allowedTypes = {
              @ParamType(type = String.class),
            },
            positional = false),
        @Param(
            name = "auth",
            doc = "Optional, an interceptor for providing credentials. Also accepts a "
                + "username_password.",
            named = true,
            defaultValue = "None",
            allowedTypes = {
              @ParamType(type = AuthInterceptor.class),
              @ParamType(type = UsernamePasswordIssuer.class),
              @ParamType(type = NoneType.class)
            },
            positional = false)
      })
  public HostCredential host(String host, Object maybeCreds) {
    maybeCreds = maybeCreds instanceof UsernamePasswordIssuer
        ? new UsernamePasswordInterceptor((UsernamePasswordIssuer) maybeCreds) : maybeCreds;
    AuthInterceptor creds = SkylarkUtil.convertFromNoneable(maybeCreds, null);
    return new AutoValue_HttpModule_HostCredential(host, Optional.ofNullable(creds));
  }

  @StarlarkMethod(
      name = "username_password_auth",
      doc = "Authentication via username and password.",
      parameters = {
        @Param(
            name = "creds",
            doc = "The username and password credentials.",
            named = true,
            allowedTypes = {
              @ParamType(type = UsernamePasswordIssuer.class),
            },
            positional = false),
      })
  public UsernamePasswordInterceptor usernamePasswordAuth(UsernamePasswordIssuer creds) {
    return new UsernamePasswordInterceptor(creds);
  }

  @StarlarkMethod(
      name = "bearer_auth",
      doc = "Authentication via a bearer token.",
      parameters = {
        @Param(
            name = "creds",
            doc = "The token credentials.",
            named = true,
            allowedTypes = {
              @ParamType(type = CredentialIssuer.class),
            },
            positional = false),
      })
  public BearerInterceptor bearerAuth(CredentialIssuer creds) {
    return new BearerInterceptor(creds);
  }

  /** A username/password issuer pair tied to a host */
  @AutoValue
  public abstract static class HostCredential implements StarlarkValue {
    public abstract String host();

    public abstract Optional<AuthInterceptor> creds();
  }
}
