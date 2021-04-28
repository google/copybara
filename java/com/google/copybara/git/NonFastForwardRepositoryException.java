/*
 * Copyright (C) 2021 Google Inc.
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

package com.google.copybara.git;

import com.google.copybara.exception.RepoException;

/**
 * This exception should be ValidationException, but sometimes it is not an user error, but
 * just a concurrency issue. For example when using git.destination that is not SoT but some
 * files are SoT in that repo. In that case, while technically we could give up, and the tool
 * doesn't really care, as it fails anyway, our internal service doesn't retry. So we prefer
 * retries in those cases.
 */
public class NonFastForwardRepositoryException extends RepoException {

  public NonFastForwardRepositoryException(String message, Throwable cause) {
    super(message, cause);
  }
}
