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
package com.google.copybara.http.multipart;

import com.google.api.client.http.HttpContent;
import com.google.api.client.http.UrlEncodedContent;
import com.google.copybara.http.endpoint.HttpEndpointBody;
import net.starlark.java.eval.StarlarkValue;

/** Constructs data for an HTTP request containing a urlencoded form data payload. */
public class HttpEndpointUrlEncodedFormContent implements HttpEndpointBody, StarlarkValue {

  private final Object data;
  private HttpContent body;

  public HttpEndpointUrlEncodedFormContent(Object data) {
    this.data = data;
  }

  @Override
  public HttpContent getContent() {
    if (body == null) {
      this.body = new UrlEncodedContent(data);
    }
    return body;
  }
}
