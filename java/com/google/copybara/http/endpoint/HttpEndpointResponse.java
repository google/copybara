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

import com.google.api.client.http.HttpResponse;
import com.google.copybara.CheckoutPath;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkMethod;
import net.starlark.java.eval.StarlarkValue;

/** Simple object to read an http response. */
public class HttpEndpointResponse implements StarlarkValue {

  HttpResponse response;

  public HttpEndpointResponse(HttpResponse response) {
    this.response = response;
  }

  @StarlarkMethod(name = "code", doc = "http status code")
  public int getStatusCode() {
    return response.getStatusCode();
  }

  @StarlarkMethod(name = "status", doc = "http status message")
  public String getStatusMessage() {
    return response.getStatusMessage();
  }

  @StarlarkMethod(name = "contents_string", doc = "response contents as string")
  public String responseAsString() throws IOException {
    return response.parseAsString();
  }

  @StarlarkMethod(
      name = "header",
      doc = "Returns the value of the response header specified by the field name",
      parameters = {
        @Param(
            name = "key",
            named = true,
            allowedTypes = {@ParamType(type = String.class)}),
      })
  public List<String> responseHeader(String key) {
    return response.getHeaders().getHeaderStringValues(key);
  }

  @StarlarkMethod(
      name = "download",
      doc = "Writes the content of the HTTP response into the given destination path",
      parameters = {
        @Param(name = "path", doc = "The destination Path"),
      })
  public void download(CheckoutPath path) throws IOException {
    response.download(Files.newOutputStream(path.fullPath()));
  }
}
