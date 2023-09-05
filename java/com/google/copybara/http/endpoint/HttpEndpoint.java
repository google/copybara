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

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpTransport;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.Endpoint;
import com.google.copybara.checks.Checker;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.http.auth.AuthInterceptor;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.util.Map.Entry;
import java.util.Optional;
import javax.annotation.Nullable;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.Dict;
import net.starlark.java.eval.EvalException;
import net.starlark.java.eval.NoneType;

/**
 * Endpoint capable of making http requests.
 *
 * <p>This endpoint is currently bound to a specific host, as a security restriction.
 */
public class HttpEndpoint implements Endpoint {
  private final ImmutableMap<String, Optional<AuthInterceptor>> hosts;
  HttpTransport transport;
  Console console;
  @Nullable Checker checker;

  /** Whether to automatically follow redirects, true by default. */
  private boolean followRedirects = true;

  public HttpEndpoint(
      Console console,
      HttpTransport transport,
      ImmutableMap<String, Optional<AuthInterceptor>> hosts,
      @Nullable Checker checker) {
    this.hosts = hosts;
    this.transport = transport;
    this.console = console;
    this.checker = checker;
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

    Dict<String, String> headersDict = Dict.cast(headersIn, String.class, String.class, "headers");
    HttpHeaders headers = new HttpHeaders();
    for (Entry<String, String> e : headersDict.entrySet()) {
      headers.set(e.getKey(), ImmutableList.of(e.getValue()));
    }

    @Nullable
    HttpEndpointBody endpointContent = SkylarkUtil.convertFromNoneable(endpointContentIn, null);
    HttpContent content = null;
    if (endpointContent != null) {
      content = endpointContent.getContent();
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
    return new HttpEndpointResponse(request.execute());
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
      doc = "Sets whether to follow redirects automatically",
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
    return list.build();
  }
}
