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

package com.google.copybara.git.github.api;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.copybara.exception.RepoException;
import java.util.EnumSet;
import javax.annotation.Nullable;

/**
 * Exception that contains the error object from GitHub and maps the Http error codes
 */
public class GitHubApiException extends RepoException {

  private static final ImmutableMap<Integer, ResponseCode> CODE_MAP = ImmutableMap.copyOf(
      Maps.uniqueIndex(EnumSet.allOf(ResponseCode.class), ResponseCode::getCode));

  private final ResponseCode responseCode;
  private final int httpCode;
  @Nullable private final ClientError error;
  private String httpMethod;
  private String path;
  @Nullable private String request;
  private String response;

  public GitHubApiException(
      int httpCode,
      @Nullable ClientError error,
      String httpMethod,
      String path,
      @Nullable String request,
      String response) {
    super(detailedError(httpMethod, path, request, response, httpCode));
    this.httpCode = httpCode;
    this.responseCode = parseResponseCode(httpCode);
    this.error = error;
    this.httpMethod = httpMethod;
    this.path = path;
    this.request = request;
    this.response = response;
  }

  private static String detailedError(
      String httpMethod, String path, @Nullable String request, String response, int httpCode) {
    StringBuilder sb =
        new StringBuilder("GitHub API call failed with code ")
            .append(httpCode)
            .append(" The request was ")
            .append(httpMethod)
            .append(' ')
            .append(path)
            .append('\n');
    if (request != null) {
      sb.append("Request object:\n").append(request).append("\n");
    }
    sb.append("Response:\n").append(response).append("\n");
    return sb.toString();
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
    return detailedError(httpMethod, path, request, response, httpCode);
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
    CONFLICT(409),
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
