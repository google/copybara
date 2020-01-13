/*
 * Copyright (C) 2020 Google Inc.
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

package com.google.copybara.remotefile;

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/**
 * A HttpStreamFactory wrapping the Google GHttp Client without side effects
 */
public class GclientHttpStreamFactory implements HttpStreamFactory {

  private final HttpTransport javaNet;


  GclientHttpStreamFactory() {
    this(new NetHttpTransport());
  }

  public GclientHttpStreamFactory(HttpTransport javaNet) {
    this.javaNet = checkNotNull(javaNet);
  }

  @Override
  public InputStream open(URL url) throws IOException {
    HttpRequest req = javaNet.createRequestFactory().buildGetRequest(new GenericUrl(url));
    return req.execute().getContent();
  }
}
