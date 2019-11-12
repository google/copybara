/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara.git.gerritapi;

import com.google.common.base.Preconditions;
import com.google.copybara.checks.ApiChecker;
import com.google.copybara.exception.RepoException;
import com.google.copybara.exception.ValidationException;
import java.lang.reflect.Type;

public class GerritApiTransportWithChecker implements GerritApiTransport {

  private final GerritApiTransport delegate;
  private final ApiChecker checker;

  public GerritApiTransportWithChecker(GerritApiTransport delegate, ApiChecker checker) {
    this.delegate = Preconditions.checkNotNull(delegate);
    this.checker = Preconditions.checkNotNull(checker);
  }

  @Override
  public <T> T get(String path, Type responseType) throws RepoException, ValidationException {
    checker.check("path", path, "response_type", responseType);
    return delegate.get(path, responseType);
  }

  @Override
  public <T> T post(String path, Object request, Type responseType)
      throws RepoException, ValidationException {
    checker.check("path", path, "request", request, "response_type", responseType);
    return delegate.post(path, request, responseType);
  }

  @Override
  public <T> T put(String path, Object request, Type responseType)
      throws RepoException, ValidationException {
    checker.check("path", path, "request", request, "response_type", responseType);
    return delegate.put(path, request, responseType);
  }
}
