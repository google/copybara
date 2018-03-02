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

import com.google.api.client.util.Key;
import com.google.common.base.MoreObjects;
import javax.annotation.Nullable;

/**
 * GitHub client errors always have a message and sometimes a documentation_url
 */
public class ClientError {
  @Key
  private String message;
  @Nullable
  @Key("documentation_url")
  private String documentationUrl;

  private String rawResponse;

  public String getMessage() {
    return message;
  }

  @Nullable
  public String getDocumentationUrl() {
    return documentationUrl;
  }

  public String getRawResponse() {
    return rawResponse;
  }

  @Override
  public String toString() {
    return MoreObjects.toStringHelper(this)
        .add("message", message)
        .add("documentationUrl", documentationUrl)
        .add("rawResponse", rawResponse)
        .toString();
  }
}
