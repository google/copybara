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
package com.google.copybara.http.multipart;

import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.MultipartContent;
import java.io.IOException;
import java.util.StringJoiner;
import javax.annotation.Nullable;

/**
 * Represents a single part of a multipart form data request.
 */
public interface HttpEndpointFormPart {
  void addToContent(MultipartContent content) throws IOException;

  // Return a copy of the given headers with the content disposition header for form parts
  static HttpHeaders setContentDispositionHeader(
      HttpHeaders headers, String name, @Nullable String filename) {
    HttpHeaders out = headers.clone();
    StringJoiner joiner = new StringJoiner("; ");
    joiner.add("form-data");
    joiner.add(String.format("name=\"%s\"", name));
    if (filename != null) {
      joiner.add(String.format("filename=\"%s\"", filename));
    }
    out.set("Content-Disposition", joiner);
    return out;
  }
}
