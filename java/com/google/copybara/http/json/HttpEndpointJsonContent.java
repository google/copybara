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
package com.google.copybara.http.json;

import com.google.api.client.http.HttpContent;
import com.google.api.client.http.json.JsonHttpContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.copybara.http.endpoint.HttpEndpointBody;
import net.starlark.java.eval.StarlarkValue;

/** Constructs data for an HTTP request containing JSON data payload. */
public class HttpEndpointJsonContent implements HttpEndpointBody, StarlarkValue {

  private final Object data;
  private HttpContent body;

  public HttpEndpointJsonContent(Object data) {
    this.data = data;
  }

  @Override
  public HttpContent getContent() {
    if (body == null) {
      JsonHttpContent content = new JsonHttpContent(new GsonFactory(), data);
      this.body = content;
    }
    return body;
  }
}
