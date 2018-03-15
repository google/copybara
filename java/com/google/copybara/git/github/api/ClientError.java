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

import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.Key;
import com.google.api.client.util.Value;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import javax.annotation.Nullable;

/**
 * GitHub client errors always have a message and sometimes a documentation_url
 */
public class ClientError {
  private static final GsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();

  @Key
  private String message;
  @Nullable
  @Key("documentation_url")
  private String documentationUrl;

  public String getMessage() {
    return message;
  }

  @Key
  public List<ErrorItem> errors;

  @Nullable
  public String getDocumentationUrl() {
    return documentationUrl;
  }

  public ImmutableList<ErrorItem> getErrors() {
    return ImmutableList.copyOf(errors);
  }

  @Override
  public String toString() {
    try {
      return JSON_FACTORY.toString(this);
    } catch (IOException e) {
      throw new RuntimeException("Unexpected error: ", e);
    }
  }

  public static class ErrorItem {
    @Key private String resource;
    @Key private String field;
    @Key private ErrorType code;
    @Key @Nullable private String message;

    public String getResource() {
      return resource;
    }

    public String getField() {
      return field;
    }

    public ErrorType getCode() {
      return code;
    }

    @Nullable
    public String getMessage() {
      return message;
    }
  }

  public enum ErrorType{
    @Value("missing") MISSING,
    @Value("missing_field") MISSING_FIELD,
    @Value("invalid") INVALID,
    @Value("already_exists") ALREADY_EXISTS,
    @Value("custom") CUSTOM
  }
}
