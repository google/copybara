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

import com.google.api.client.http.FileContent;
import com.google.api.client.http.HttpHeaders;
import com.google.api.client.http.MultipartContent;
import com.google.api.client.http.MultipartContent.Part;
import com.google.copybara.checks.Checker;
import com.google.copybara.checks.CheckerException;
import com.google.copybara.util.console.Console;
import java.io.IOException;
import java.nio.file.Path;
import javax.annotation.Nullable;
import net.starlark.java.eval.StarlarkValue;

/** Represents a file field in a multipart http form payload */
public class FilePart implements HttpEndpointFormPart, StarlarkValue {
  String name;
  Path filePath;
  String contentType;
  @Nullable String filename;

  public FilePart(String name, Path filePath, String contentType, @Nullable String filename) {
    this.name = name;
    this.filePath = filePath;
    this.contentType = contentType;
    this.filename = filename;
  }

  @Override
  public void addToContent(MultipartContent content) {
    HttpHeaders headers = new HttpHeaders();
    Part part =
        new Part(
            HttpEndpointFormPart.setContentDispositionHeader(headers, name, filename),
            new FileContent(contentType, filePath.toFile()));
    content.addPart(part);
  }

  @Override
  public void checkPart(Checker checker, Console console) throws CheckerException, IOException {
    checker.doCheck(filePath, console);
  }
}
