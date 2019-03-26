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

package com.google.copybara.util;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.List;
import javax.annotation.CheckReturnValue;

/**
 * Utility for printing tabular data to an ASCII string. Currently only supports single-line cells.
 **/
public class TablePrinter {

  final ImmutableList<String> headers;
  final ImmutableList.Builder<List<String>> rowBuilder = ImmutableList.builder();
  final int[] columnWidths;

  public TablePrinter(String... header) {
    headers = ImmutableList.copyOf(header);
    columnWidths = new int[header.length];
    for (int col = 0; col < header.length; col++) {
      columnWidths[col] = header[col].length();
    }
  }

  /** Add a row, must have the same number of elements as the header */
  public TablePrinter addRow(Object... row) {
    if (row.length != headers.size()) {
      throw new IllegalArgumentException(String.format(
          "Wrong number of values in row; expected %d. Got: %d", headers.size(), row.length));
    }
    ImmutableList<String> strings = Arrays.stream(row)
        .map(o -> CharMatcher.is('\n').removeFrom("" + o)) // null friendly, no breaks
        .collect(ImmutableList.toImmutableList());
    rowBuilder.add(strings);
    for (int col = 0; col < strings.size(); col++) {
      columnWidths[col] = Math.max(strings.get(col).length(), columnWidths[col]);
    }
    return this;
  }

  /**
   * Build the table.
   */
  @CheckReturnValue
  public String print() {
    String ret = printRow('+', '-', ImmutableList.of());
    ret += printRow('|', ' ', headers);
    ret += printRow('+', '-', ImmutableList.of());
    for (List<String> row : rowBuilder.build()) {
      ret += printRow('|', ' ', row);
    }
    ret += printRow('+', '-', ImmutableList.of());
    return ret;
  }

  private String printRow(char delim, char filler, List<String> vals) {
    ImmutableList.Builder<String> paddedVals = ImmutableList.builder();
    for (int col = 0; col < columnWidths.length; col++) {
      String val = vals.size() > col ? vals.get(col) : "";
      paddedVals.add(Strings.padEnd(val, columnWidths[col] + 1, filler));
    }
    return String.format("%1$c%2$s%1$c\n", delim, Joiner.on(delim).join(paddedVals.build()));
  }

}
