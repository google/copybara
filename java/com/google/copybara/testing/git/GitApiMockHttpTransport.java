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

package com.google.copybara.testing.git;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.json.Json;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.google.common.base.MoreObjects;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** A utility class to mock HTTP responses. */
public abstract class GitApiMockHttpTransport extends MockHttpTransport {
  public List<RequestRecord> requests = new ArrayList<>();

  @Override
  public LowLevelHttpRequest buildRequest(String method, String url) {
    MockLowLevelHttpRequest request =
        new MockLowLevelHttpRequest() {
          @Override
          public LowLevelHttpResponse execute() throws IOException {
            MockLowLevelHttpResponse response = (MockLowLevelHttpResponse) super.execute();
            String content = getContent(method, url, this);
            requests.add(new RequestRecord(method, url, this.getContentAsString(), content));
            response.setContent(content.getBytes(StandardCharsets.UTF_8));
            return super.execute();
          }
        };
    MockLowLevelHttpResponse response = new MockLowLevelHttpResponse();
    response.setContentType(Json.MEDIA_TYPE);
    request.setResponse(response);
    return request;
  }

  public abstract String getContent(String method, String url, MockLowLevelHttpRequest request)
      throws IOException;

  public static class RequestRecord {
    private final String method;
    private final String url;
    private final String request;
    private final String response;

    private RequestRecord(String method, String url, String request, String response) {
      this.method = method;
      this.url = url;
      this.request = request;
      this.response = response;
    }

    public String getMethod() {
      return method;
    }

    public String getUrl() {
      return url;
    }

    public String getRequest() {
      return request;
    }

    public String getResponse() {
      return response;
    }

    @Override
    public String toString() {
      return MoreObjects.toStringHelper(this)
          .add("method", method)
          .add("url", url)
          .add("request", request)
          .add("response", response)
          .toString();
    }
  }
}
