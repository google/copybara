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
import com.google.api.client.http.HttpRequestFactory;
import com.google.api.client.http.HttpTransport;
import com.google.copybara.http.auth.Auth;
import java.io.IOException;
import javax.annotation.Nullable;
import net.starlark.java.eval.StarlarkValue;

/**
 * HttpRequest is initialized as a holder for all the data required to make a request. It has a
 * method that executes the request.
 */
public class HttpEndpointRequest implements StarlarkValue {

  // Request parameters
  GenericUrl url;
  String method;
  HttpHeaders headers;
  @Nullable HttpContent content;
  @Nullable Auth auth;

  // Client parameters
  HttpTransport transport;

  // Output
  HttpRequest request;

  public HttpEndpointRequest(
      GenericUrl url,
      String method,
      HttpHeaders headers,
      HttpTransport transport,
      @Nullable HttpContent content,
      @Nullable Auth auth) {
    this.url = url;
    this.method = method;
    this.headers = headers;
    this.transport = transport;
    this.content = content;
    this.auth = auth;
  }

  public HttpRequest build() throws IOException {
    if (request == null) {
      HttpRequestFactory factory = transport.createRequestFactory();
      request = factory.buildRequest(method, url, null);
      request.getHeaders().fromHttpHeaders(headers);
      if (content != null) {
        request.setContent(content);
      }
      if (auth != null) {
        request.setInterceptor(auth.basicAuthInterceptor());
      }
      request.setThrowExceptionOnExecuteError(false);
    }
    return request;
  }
}
