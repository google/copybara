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

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.common.base.Suppliers;
import com.google.copybara.Option;
import com.google.copybara.exception.ValidationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;


/**
 * Options for loading files from a source other than the origin. Use with caution.
 */
public class RemoteFileOptions implements Option {


  Path storageDir;
  protected Path getStorageDir() throws IOException {
    if (storageDir == null) {
      storageDir = Files.createTempDirectory("remoteHttp");
    }
    return storageDir;
  }

  Supplier<HttpTransport> transport = Suppliers.memoize(() -> new NetHttpTransport());

  HttpTransport getTransport() throws ValidationException {
    return transport.get();
  }
}
