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

package com.google.copybara.http.endpoint;

import static com.google.common.base.Strings.nullToEmpty;
import static com.google.common.collect.ImmutableSet.toImmutableSet;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpExecuteInterceptor;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpTransport;
import com.google.common.base.Ascii;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.Endpoint;
import com.google.copybara.checks.Checker;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.credentials.CredentialIssuer;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.http.auth.AuthInterceptor;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.Optional;
import javax.annotation.Nullable;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.NoneType;

/**
 * Endpoint capable of making HTTP requests to configured {@code hosts} on both the initial request
 * and every followed redirect hop.
 *
 * <p>Once a followed redirect leaves the original request's host (matched case-insensitively,
 * ignoring port) or switches from {@code https} to non-{@code https}, host credentials from {@code
 * http.host(auth=...)} are withheld and request headers containing {@code issuers} secrets ({@code
 * ${{name}}} placeholders) are removed for that hop and all subsequent hops. If such a redirect
 * would resend a request body containing an {@code issuers} secret, execution fails with {@link
 * ValidationException}.
 */
@StarlarkBuiltin(name = "http_endpoint", doc = "Calls via HTTP.")
public class HttpEndpoint implements Endpoint {

  // Immutable list to keep the issuers as key value pairs
  // The key will be used to identify the issuer
  // before executing the http call
  private final ImmutableMap<String, CredentialIssuer> issuers;

  private final ImmutableMap<String, Optional<AuthInterceptor>> hosts;
  private final ImmutableSet<String> allowedHostsLowercase;
  HttpTransport transport;
  Console console;
  @Nullable Checker checker;

  /** Whether to automatically follow redirects, true by default. */
  private boolean followRedirects = true;

  /**
   * Constructs an {@link HttpEndpoint} restricted to {@code hosts}.
   *
   * @param checker outbound request checker, or {@code null} if no checker is configured
   */
  public HttpEndpoint(
      Console console,
      HttpTransport transport,
      ImmutableMap<String, Optional<AuthInterceptor>> hosts,
      ImmutableMap<String, CredentialIssuer> issuers,
      @Nullable Checker checker) {
    this.hosts = hosts;
    this.allowedHostsLowercase =
        hosts.keySet().stream().map(Ascii::toLowerCase).collect(toImmutableSet());
    this.transport = transport;
    this.console = console;
    this.checker = checker;
    this.issuers = issuers;
  }

  @StarlarkMethod(
      name = "get",
      doc = "Execute a get request",
      parameters = {
        @Param(
            name = "url",
            named = true,
            allowedTypes = {@ParamType(type = String.class)}),
        @Param(
            name = "headers",
            named = true,
            positional = false,
            allowedTypes = {@ParamType(type = Dict.class)},
            defaultValue = "{}",
            doc = "dict of http headers for the request"),
        @Param(
            name = "auth",
            named = true,
            positional = false,
            defaultValue = "False",
            allowedTypes = {
              @ParamType(type = Boolean.class),
            }),
      })
  public HttpEndpointResponse get(String url, Object headers, Boolean auth)
      throws EvalException, ValidationException, IOException {
    return handleRequest(url, "GET", headers, null, auth);
  }

  @StarlarkMethod(
      name = "post",
      doc = "Execute a post request",
      parameters = {
        @Param(
            name = "url",
            named = true,
            allowedTypes = {@ParamType(type = String.class)}),
        @Param(
            name = "headers",
            named = true,
            positional = false,
            allowedTypes = {@ParamType(type = Dict.class)},
            defaultValue = "{}",
            doc = "dict of http headers for the request"),
        @Param(
            name = "content",
            named = true,
            positional = false,
            defaultValue = "None",
            allowedTypes = {
              @ParamType(type = HttpEndpointBody.class),
              @ParamType(type = NoneType.class),
            }),
        @Param(name = "auth", named = true, positional = false, defaultValue = "False")
      })
  public HttpEndpointResponse post(String urlIn, Object headersIn, Object content, Boolean auth)
      throws EvalException, ValidationException, IOException {
    return handleRequest(urlIn, "POST", headersIn, content, auth);
  }

  @StarlarkMethod(
      name = "delete",
      doc = "Execute a delete request",
      parameters = {
        @Param(
            name = "url",
            named = true,
            allowedTypes = {@ParamType(type = String.class)}),
        @Param(
            name = "headers",
            named = true,
            positional = false,
            allowedTypes = {@ParamType(type = Dict.class)},
            defaultValue = "{}",
            doc = "dict of http headers for the request"),
        @Param(
            name = "auth",
            named = true,
            positional = false,
            defaultValue = "False",
            allowedTypes = {
              @ParamType(type = Boolean.class),
            })
      })
  public HttpEndpointResponse delete(String urlIn, Object headersIn, Boolean auth)
      throws EvalException, ValidationException, IOException {
    return handleRequest(urlIn, "DELETE", headersIn, null, auth);
  }

  private HttpEndpointResponse handleRequest(
      String urlIn, String method, Object headersIn, Object endpointContentIn, boolean auth)
      throws EvalException, ValidationException, IOException {
    GenericUrl url = new GenericUrl(urlIn);
    validateUrl(url);

    HttpSecretInterceptor secretInterceptor = new HttpSecretInterceptor(issuers);
    Dict<String, String> headersDict = Dict.cast(headersIn, String.class, String.class, "headers");
    HttpHeaders headers = new HttpHeaders();
    ImmutableSet.Builder<String> secretHeaderNames = ImmutableSet.builder();
    for (Entry<String, String> e : headersDict.entrySet()) {
      HttpSecretInterceptor.ResolvedString resolved = secretInterceptor.resolve(e.getValue());
      if (resolved.resolvedSecret()) {
        secretHeaderNames.add(e.getKey());
      }
      headers.set(e.getKey(), ImmutableList.of(resolved.value()));
    }

    @Nullable
    HttpEndpointBody endpointContent = SkylarkUtil.convertFromNoneable(endpointContentIn, null);
    HttpContent content = null;
    boolean contentHasSecrets = false;
    if (endpointContent != null) {
      HttpContentInterceptor contentInterceptor =
          new HttpContentInterceptor(endpointContent.getContent(), secretInterceptor);
      content = contentInterceptor;
      contentHasSecrets = contentInterceptor.hasResolvedSecret();
    }

    @Nullable AuthInterceptor creds = null;
    if (auth) {
      creds =
          hosts
              .get(url.getHost())
              .orElseThrow(
                  () ->
                      new EvalException(
                          String.format(
                              "Autentication was requested, but no creds provided for %s", url)));
    }

    HttpEndpointRequest req =
        new HttpEndpointRequest(
            url, method, headers, transport, content, auth && creds != null ? creds : null);

    if (checker != null) {
      checker.doCheck(
          ImmutableMap.of(
              "url", url.toString(),
              "headers", headers.toString()),
          console);
      endpointContent.checkContent(checker, console);
    }
    HttpRequest request = req.build();
    request.setFollowRedirects(this.followRedirects);
    request.setInterceptor(
        buildHopInterceptor(
            url, request.getInterceptor(), secretHeaderNames.build(), contentHasSecrets));
    try {
      return new HttpEndpointResponse(request.execute());
    } catch (RedirectValidationException e) {
      throw e.validationException();
    }
  }

  private HttpExecuteInterceptor buildHopInterceptor(
      GenericUrl initialUrl,
      @Nullable HttpExecuteInterceptor authInterceptor,
      ImmutableSet<String> secretHeaderNames,
      boolean contentHasSecrets) {
    String initialHost = Ascii.toLowerCase(nullToEmpty(initialUrl.getHost()));
    boolean initialIsHttps = Ascii.equalsIgnoreCase("https", nullToEmpty(initialUrl.getScheme()));
    return new HttpExecuteInterceptor() {
      private boolean firstHop = true;
      private boolean withholdSecrets = false;
      private String previousHost = nullToEmpty(initialUrl.getHost());

      @Override
      public void intercept(HttpRequest request) throws IOException {
        GenericUrl currentUrl = request.getUrl();
        String currentHost = nullToEmpty(currentUrl.getHost());
        if (firstHop) {
          firstHop = false;
        } else {
          if (!allowedHostsLowercase.contains(Ascii.toLowerCase(currentHost))) {
            throw new RedirectValidationException(
                new ValidationException(
                    String.format(
                        "Illegal host: redirect from %s to %s matches none of endpoint hosts {%s}",
                        previousHost, currentHost, String.join(",", hosts.keySet()))));
          }
          boolean otherHost = !Ascii.toLowerCase(currentHost).equals(initialHost);
          boolean httpsToHttp =
              initialIsHttps
                  && !Ascii.equalsIgnoreCase("https", nullToEmpty(currentUrl.getScheme()));
          if (otherHost || httpsToHttp) {
            withholdSecrets = true;
          }
          if (withholdSecrets) {
            for (String headerName : secretHeaderNames) {
              removeHeader(request.getHeaders(), headerName);
            }
            if (contentHasSecrets && request.getContent() != null) {
              throw new RedirectValidationException(
                  new ValidationException(
                      String.format(
                          "Redirect from %s to %s would resend a request body containing secrets;"
                              + " use followRedirects(False) to handle the redirect manually",
                          previousHost, currentHost)));
            }
          }
        }
        previousHost = currentHost;
        if (authInterceptor != null) {
          authInterceptor.intercept(request);
        }
      }
    };
  }

  private static void removeHeader(HttpHeaders headers, String headerName) {
    if (headers.getClassInfo().getFieldInfo(headerName) != null) {
      headers.set(headerName, null);
    } else {
      headers.remove(headerName);
    }
  }

  public void validateUrl(GenericUrl url) throws ValidationException {
    ValidationException.checkCondition(
        hosts.containsKey(url.getHost()),
        String.format(
            "Illegal host: url host %s matches none of endpoint hosts {%s}",
            url.getHost(), String.join(",", hosts.keySet())));
  }

  @StarlarkMethod(
      name = "followRedirects",
      doc =
          "Sets whether to follow redirects automatically. When following redirects, each hop"
              + " must match an entry in `hosts`. Once a redirect leaves the original request's"
              + " host or switches from HTTPS to HTTP, credentials and headers containing"
              + " `issuers` secrets are withheld for that hop and any later hops, and redirects"
              + " that would resend a request body containing an `issuers` secret fail.",
      parameters = {
        @Param(
            name = "followRedirects",
            doc = "Whether to follow redirects automatically",
            allowedTypes = {@ParamType(type = Boolean.class)}),
      })
  public void setFollowRedirects(boolean followRedirects) {
    this.followRedirects = followRedirects;
  }

  @Override
  public ImmutableSetMultimap<String, String> describe() {
    ImmutableSetMultimap.Builder<String, String> builder = ImmutableSetMultimap.builder();
    builder.put("type", "http_endpoint");
    builder.putAll("host", hosts.keySet());
    return builder.build();
  }

  @Override
  public ImmutableList<ImmutableSetMultimap<String, String>> describeCredentials() {
    ImmutableList.Builder<ImmutableSetMultimap<String, String>> list = ImmutableList.builder();

    // Add credentials from auth interceptors for hosts.
    for (Entry<String, Optional<AuthInterceptor>> entry : hosts.entrySet()) {
      if (entry.getValue().isEmpty()) {
        continue;
      }
      for (ImmutableSetMultimap<String, String> credEntry :
          entry.getValue().get().describeCredentials()) {
        ImmutableSetMultimap.Builder<String, String> describe = ImmutableSetMultimap.builder();
        describe.putAll(credEntry);
        describe.putAll("host", entry.getKey());
        list.add(describe.build());
      }
    }

    // Add credentials from issuers used by secret interceptors.
    for (Entry<String, CredentialIssuer> entry : issuers.entrySet()) {
      ImmutableSetMultimap.Builder<String, String> describe = ImmutableSetMultimap.builder();
      describe.put("key", entry.getKey());
      describe.putAll(entry.getValue().describe());
      list.add(describe.build());
    }

    return list.build();
  }

  private static final class RedirectValidationException extends IOException {
    private final ValidationException validationException;

    private RedirectValidationException(ValidationException validationException) {
      super(validationException);
      this.validationException = validationException;
    }

    private ValidationException validationException() {
      return validationException;
    }
  }
}
