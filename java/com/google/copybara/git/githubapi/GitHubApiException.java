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

package com.google.copybara.git.githubapi;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.copybara.RepoException;
import java.util.EnumSet;

/**
 * Exception that contains the error object from GitHub and maps the Http error codes
 */
public class GitHubApiException extends RepoException {

  private static final ImmutableMap<Integer, ResponseCode> CODE_MAP = ImmutableMap.copyOf(
      Maps.uniqueIndex(EnumSet.allOf(ResponseCode.class), ResponseCode::getCode));

  private final ResponseCode responseCode;
  private final int httpCode;
  private final ClientError error;
  private final String rawError;

  public GitHubApiException(int httpCode, ClientError error, String rawError) {
    super(error.getMessage() != null ? error.getMessage() : rawError);
    this.httpCode = httpCode;
    this.responseCode = parseResponseCode(httpCode);
    this.error = error;
    this.rawError = rawError;
  }

  public ResponseCode getResponseCode() {
    return responseCode;
  }

  public int getHttpCode() {
    return httpCode;
  }

  public ClientError getError() {
    return error;
  }

  public String getRawError() {
    return rawError;
  }

  /**
   * Gerrit known response codes.
   *
   * <p>Note that UNKNOWN will be used for any other not in this list.
   */
  public enum ResponseCode {
    UNKNOWN(0),
    BAD_REQUEST(400),
    FORBIDDEN(403),
    NOT_FOUND(404),
    UNPROCESSABLE_ENTITY(422);

    private final int code;

    ResponseCode(int code) {
      this.code = code;
    }

    public int getCode() {
      return code;
    }
  }

  private static ResponseCode parseResponseCode(int code) {
    ResponseCode responseCode = CODE_MAP.get(code);
    return responseCode == null ? ResponseCode.UNKNOWN : responseCode;
  }
}
