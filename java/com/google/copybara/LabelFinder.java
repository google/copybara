// Copyright 2016 Google Inc. All Rights Reserved.
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
