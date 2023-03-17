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
import com.google.api.client.http.HttpMediaType;
import com.google.api.client.http.MultipartContent;
import com.google.copybara.http.endpoint.HttpEndpointBody;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import net.starlark.java.eval.StarlarkValue;

/**
 * Constructs data for an http request containing a multipart form data payload.
 */
public class HttpEndpointMultipartFormContent implements HttpEndpointBody, StarlarkValue {
  List<HttpEndpointFormPart> parts;

  HttpContent body;

  public HttpEndpointMultipartFormContent(List<HttpEndpointFormPart> parts) {
    this.parts = parts;
  }

  @Override
  public HttpContent getContent() throws IOException {
    if (body == null) {
      MultipartContent content = new MultipartContent();
      content.setMediaType(new HttpMediaType("multipart", "form-data"));
      content.setBoundary(UUID.randomUUID().toString());
      for (HttpEndpointFormPart part : parts) {
        part.addToContent(content);
      }
      this.body = content;
    }
    return body;
  }
}
