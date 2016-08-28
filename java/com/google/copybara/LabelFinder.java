/*
 * Copyright (C) 2016 Google Inc.
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

package com.google.copybara;

import com.google.common.base.Preconditions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple label finder/parser for labels like:
 * <ul>
 *   <li>foo = bar</li>
 *   <li>baz : foo</li>
 * </ul>
 */
public class LabelFinder {

  private static final Pattern labelPattern = Pattern.compile("(^[\\w-]+) *[:=] *(.*)");
  private final Matcher matcher;
  private final String label;

  public LabelFinder(String line) {
    matcher = labelPattern.matcher(line);
    this.label = line;
  }

  public boolean isLabel() {
    return matcher.matches();
  }

  public String getName() {
    Preconditions.checkState(isLabel(), "Not a label: '" + label + "'");
    return matcher.group(1);
  }

  public String getValue() {
    Preconditions.checkState(isLabel(), "Not a label: '" + label + "'");
    return matcher.group(2);
  }
}
