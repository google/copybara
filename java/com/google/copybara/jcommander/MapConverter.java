/*
 * Copyright (C) 2019 Google Inc.
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
package com.google.copybara.jcommander;

import com.beust.jcommander.IStringConverter;
import com.beust.jcommander.ParameterException;
import com.google.common.collect.ImmutableMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Convert a String in the form of foo:value1,bar:value2 into an ImmutableMap of Strings.
 *
 * <p>Keys and unquoted values are trimmed and empty entries are ignored. The key ends at the first
 * ':', so unquoted values can contain ':' (e.g. URLs). A value that contains ',' must be wrapped in
 * double quotes, e.g. {@code foo:"a, b",bar:c}. Inside quotes, a backslash escapes the next
 * character, so {@code \"} is a literal quote and {@code \\} is a literal backslash.
 */
public class MapConverter implements IStringConverter<ImmutableMap<String, String>> {

  private static final String USAGE =
      "Expected comma-separated key:value pairs, e.g. foo:bar,baz:\"a, b\". Wrap a value in"
          + " double quotes if it contains ','. Inside quotes, use \\\" for a literal quote and"
          + " \\\\ for a literal backslash.";

  @Override
  public ImmutableMap<String, String> convert(String s) {
    Map<String, String> result = new LinkedHashMap<>();
    int length = s.length();
    int idx = 0;
    while (idx < length) {
      int keyStart = idx;
      while (idx < length && s.charAt(idx) != ':' && s.charAt(idx) != ',') {
        idx++;
      }
      String key = s.substring(keyStart, idx).trim();
      if (idx == length || s.charAt(idx) == ',') {
        if (!key.isEmpty()) {
          throw error(s, String.format("entry '%s' is missing ':'", key));
        }
        idx++; // Empty entry.
        continue;
      }
      if (key.isEmpty()) {
        throw error(s, "found an entry with an empty key");
      }
      idx++; // Skip ':'.
      while (idx < length && Character.isWhitespace(s.charAt(idx))) {
        idx++;
      }

      String value;
      if (idx < length && s.charAt(idx) == '"') {
        idx++; // Skip opening quote.
        StringBuilder valueBuilder = new StringBuilder();
        boolean closed = false;
        while (idx < length) {
          char c = s.charAt(idx++);
          if (c == '\\' && idx < length) {
            valueBuilder.append(s.charAt(idx++));
          } else if (c == '"') {
            closed = true;
            break;
          } else {
            valueBuilder.append(c);
          }
        }
        if (!closed) {
          throw error(s, String.format("value for '%s' has an unterminated quote", key));
        }
        while (idx < length && Character.isWhitespace(s.charAt(idx))) {
          idx++;
        }
        if (idx < length && s.charAt(idx) != ',') {
          throw error(
              s,
              String.format("unexpected text after the closing quote of the value for '%s'", key));
        }
        value = valueBuilder.toString();
      } else {
        int valueStart = idx;
        while (idx < length && s.charAt(idx) != ',') {
          idx++;
        }
        value = s.substring(valueStart, idx).trim();
      }

      if (result.putIfAbsent(key, value) != null) {
        throw error(s, String.format("duplicate key '%s'", key));
      }
      idx++; // Skip ','.
    }
    return ImmutableMap.copyOf(result);
  }

  private static ParameterException error(String s, String detail) {
    return new ParameterException(String.format("Invalid value '%s': %s. %s", s, detail, USAGE));
  }
}
