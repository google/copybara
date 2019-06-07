/*
 * Copyright (C) 2018 Google Inc.
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

package com.google.copybara.templatetoken;

import com.google.re2j.Matcher;
import com.google.re2j.Pattern;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Function;

/**
 * A template system that for texts like "This ${LABEL} is a template"
 * TODO(malcon): Consolidate this class and Parser/Token.
 */
public class LabelTemplate {
  // ([\w-]+) is coming from LabelFinder.VALID_LABEL_EXPR. Dues to a a dependency
  // issue we have it here inlined. It is not a big deal as the labels need to exist
  // and also will be refactored into Parser/Token.
  private static final Pattern VAR_PATTERN = Pattern.compile("\\$\\{([\\w-]+)}");

  private final Set<String> labels = new HashSet<>();
  private final String template;

  /**
   * Construct the template object from a String
   * @param template a String in the from of "Foo ${LABEL} ${OTHER} Bar"
   */
  public LabelTemplate(String template) {
    this.template = template;
    Matcher matcher = VAR_PATTERN.matcher(template);
    while (matcher.find()) {
      labels.add(matcher.group(1));
    }
  }

  /**
   * Resolve the template string for a particular set of labels
   */
  public String resolve(Function<String, String> labelFinder) throws LabelNotFoundException {
    Map<String, String> labelValues = new HashMap<>();
    for (String label : labels) {
      String value = labelFinder.apply(label);
      if (value == null) {
        throw new LabelNotFoundException(label);
      }
      labelValues.put(label, value);
    }

    String result = template;
    for (Entry<String, String> entry : labelValues.entrySet()) {
      result = result.replace("${" + entry.getKey() + "}", entry.getValue());
    }
    return result;
  }

  /**
   * Thrown when a label cannot be found in the message
   */
  public class LabelNotFoundException extends Exception {

    private final String label;

    LabelNotFoundException(String label) {
      super("Cannot find label " + label);
      this.label = label;
    }

    /**
     * Get the label that couldn't be found
     */
    public String getLabel() {
      return label;
    }
  }
}
