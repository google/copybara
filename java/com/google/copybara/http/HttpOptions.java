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

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.copybara.Option;
import com.google.copybara.exception.ValidationException;
import java.nio.file.Path;
import javax.annotation.Nullable;

/** Options relating to the http endpoint. */
public class HttpOptions implements Option {
  HttpTransport transport;

  public HttpTransport getTransport() throws ValidationException {
    if (transport == null) {
      transport = new NetHttpTransport();
    }
    return transport;
  }

  /*
  TODO(b/270712326) enable this flag
  @Parameter(
      names = "--http-credential-file",
      description = "location of toml file for passing credentials to the http endpoint"
  )
   */
  public @Nullable Path credentialFile = null;
}
