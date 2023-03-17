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

package com.google.copybara.http.auth;

import com.google.api.client.http.HttpExecuteInterceptor;
import net.starlark.java.eval.StarlarkValue;

/**
 * Representation for authentication information for an http request.
 * TODO(b/273968171): Generalize this interface beyond http to create a single
 * common auth interface for copybara.
 */
public class Auth implements StarlarkValue {

  private final KeySource username;
  private final KeySource password;

  public Auth(KeySource username, KeySource password) {
    this.username = username;
    this.password = password;
  }

  public HttpExecuteInterceptor basicAuthInterceptor() {
    return (req) -> req.getHeaders().setBasicAuthentication(username.get(), password.get());
  }
}
