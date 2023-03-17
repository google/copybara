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
package com.google.copybara.http.testing;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import java.io.IOException;

/**
 * Request mocking utility removing anonymous class boilerplate.
 */
public class MockHttpTester {
  private MockHandler handler;

  private final MockHttpTransport transport =
      new MockHttpTransport() {
        @Override
        public LowLevelHttpRequest buildRequest(String method, String url) {
          MockLowLevelHttpResponse resp = new MockLowLevelHttpResponse();

          MockLowLevelHttpRequest req =
              new MockLowLevelHttpRequest() {
                @Override
                public LowLevelHttpResponse execute() throws IOException {
                  handler.handleReq(method, url, this, resp);
                  return super.execute();
                }
              };

          req.setResponse(resp);
          return req;
        }
      };

  /**
   * Get the transport that should be used in order to exercise the mock
   */
  public MockHttpTransport getTransport() {
    return transport;
  }

  /**
   * The signature of the mock implementation.
   */
  public interface MockHandler {

    /**
     * @param req - The request built by the client lib.
     * @param response - The response that will be returned, available for modification.
     */
    void handleReq(
        String method, String url, MockLowLevelHttpRequest req, MockLowLevelHttpResponse response)
        throws IOException;
  }

  /**
   * Set the mock implementation that should be used when handling requests
   */
  public void mockHttp(MockHandler handler) {
    this.handler = handler;
  }
}
