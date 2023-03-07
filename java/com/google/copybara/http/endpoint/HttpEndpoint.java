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
import com.google.api.client.http.HttpTransport;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.copybara.Endpoint;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.util.Map.Entry;
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
  String host;
  HttpTransport transport;
  Console console;

  public HttpEndpoint(Console console, HttpTransport transport, String host) {
    this.host = host;
    this.transport = transport;
    this.console = console;
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
      })
  public HttpEndpointResponse get(String url, Object headers)
      throws EvalException, ValidationException, IOException {
    return handleRequest(url, "GET", headers, null);
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
      })
  public HttpEndpointResponse post(String urlIn, Object headersIn, Object content)
      throws EvalException, ValidationException, IOException {
    return handleRequest(urlIn, "POST", headersIn, content);
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
      })
  public HttpEndpointResponse delete(String urlIn, Object headersIn)
      throws EvalException, ValidationException, IOException {
    return handleRequest(urlIn, "DELETE", headersIn, null);
  }

  private HttpEndpointResponse handleRequest(
      String urlIn, String method, Object headersIn, Object endpointContentIn)
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

    HttpEndpointRequest req = new HttpEndpointRequest(url, method, headers, transport, content);
    return new HttpEndpointResponse(req.build().execute());
  }

  public void validateUrl(GenericUrl url) throws ValidationException {
    if (!url.getHost().equals(host)) {
      throw new ValidationException(
          String.format("url host %s does not match endpoint host %s", url.getHost(), host));
    }
  }

  @Override
  public ImmutableSetMultimap<String, String> describe() {
    return ImmutableSetMultimap.of("type", "http_endpoint", "host", host);
  }
  ;
}
