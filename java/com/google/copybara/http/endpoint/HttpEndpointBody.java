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

import com.google.api.client.http.HttpContent;
import com.google.copybara.checks.Checker;
import com.google.copybara.checks.CheckerException;
import com.google.copybara.util.console.Console;
import java.io.IOException;

/** An object containing the content portion of an http request */
public interface HttpEndpointBody {
  HttpContent getContent() throws IOException;

  default void checkContent(Checker checker, Console console) throws CheckerException, IOException {
    throw new CheckerException("checker not implemented for this content type");
  }
}
