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

package com.google.copybara.http;

import com.google.copybara.CheckoutPath;
import com.google.copybara.EndpointProvider;
import com.google.copybara.config.SkylarkUtil;
import com.google.copybara.exception.ValidationException;
import com.google.copybara.http.endpoint.HttpEndpoint;
import com.google.copybara.http.multipart.FilePart;
import com.google.copybara.http.multipart.HttpEndpointFormPart;
import com.google.copybara.http.multipart.HttpEndpointMultipartFormContent;
import com.google.copybara.http.multipart.TextPart;
import com.google.copybara.util.console.Console;
import javax.annotation.Nullable;
import net.starlark.java.annot.Param;
import net.starlark.java.annot.ParamType;
import net.starlark.java.annot.StarlarkBuiltin;
import net.starlark.java.annot.StarlarkMethod;
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
      name = "endpoint",
      doc = "Endpoint that executes any sort of http request. Currently restricted"
          + "to requests to a specific host.",
      parameters = {@Param(name = "host", named = true)})
  public EndpointProvider<HttpEndpoint> endpoint(String host) throws ValidationException {
    return EndpointProvider.wrap(new HttpEndpoint(console, options.getTransport(), host));
  }

  @StarlarkMethod(
      name = "multipart_form",
      doc = "Creates a multipart form http body. Accepts a list of form parts.",
      parameters = {
        @Param(
            name = "parts",
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
      doc = "Create a file part for a multipart form payload. Content type "
          + "defaults to application/octet-stream.",
      parameters = {
        @Param(
            name = "name",
            doc = "The name of the form field.",
            allowedTypes = {
              @ParamType(type = String.class),
            }),
        @Param(
            name = "path",
            doc = "The checkout path pointing to the file to use "
                + "as the field value.",
            allowedTypes = {
              @ParamType(type = CheckoutPath.class),
            }),
        @Param(
            name = "content_type",
            doc = "Content type header value for the form part. "
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
            doc = "The filename that will be sent along with the data. "
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
}
