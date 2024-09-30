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

import com.google.copybara.credentials.CredentialIssuingException;
import com.google.copybara.credentials.CredentialRetrievalException;
import com.google.copybara.http.auth.AuthInterceptor;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import javax.annotation.Nullable;

/**
 * Interface for opening a URL for downloading a file
 */
public interface HttpStreamFactory {

  /**
   * Open the referenced URL and return the stream to the contents.
   *
   * @param url The URL to open.
   * @param auth The interceptor to use for authentication. If null, no authentication will be used.
   */
  InputStream open(URL url, @Nullable AuthInterceptor auth)
      throws IOException, CredentialRetrievalException, CredentialIssuingException;
}
