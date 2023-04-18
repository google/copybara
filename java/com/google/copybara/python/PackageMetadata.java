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

package com.google.copybara.python;

import com.google.common.collect.ArrayListMultimap;
import com.google.copybara.CheckoutPath;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import javax.mail.Header;
import javax.mail.MessagingException;
import javax.mail.internet.InternetHeaders;

/** Utility class for extracting metadata from a python metadata file. */
public final class PackageMetadata {

  /**
   * Parse the metadata file and return a list of key value metadata pairs.
   *
   * @throws EmptyMetadataException if no metadata are found. Some metadata fields are required, so
   *     there should always be something.
   */
  static ArrayListMultimap<String, String> getMetadata(CheckoutPath metadataPath)
      throws IOException, EmptyMetadataException {

    ArrayListMultimap<String, String> metadata = extractMetadata(metadataPath.fullPath());
    if (metadata.isEmpty()) {
      throw new EmptyMetadataException(
          String.format("no metadata fields found for file %s", metadataPath));
    }
    return metadata;
  }

  private static ArrayListMultimap<String, String> extractMetadata(Path metadataPath)
      throws IOException {
    ArrayListMultimap<String, String> metadata = ArrayListMultimap.create();
    InputStream metadataFile = Files.newInputStream(metadataPath);

    try {
      // The python package metadata format is based off email headers:
      // https://packaging.python.org/en/latest/specifications/core-metadata/
      InternetHeaders ih = new InternetHeaders(metadataFile);

      @SuppressWarnings("unchecked")
      Enumeration<Header> headers = ih.getAllHeaders();
      while (headers.hasMoreElements()) {
        Header header = headers.nextElement();
        metadata.put(header.getName(), header.getValue());
      }
    } catch (MessagingException e) {
      throw new IOException("failed to read metadata headers", e);
    }

    return metadata;
  }

  /** No metadata was found in the file. */
  public static class EmptyMetadataException extends Exception {
    EmptyMetadataException(String message) {
      super(message);
    }
  }

  private PackageMetadata() {}
}
