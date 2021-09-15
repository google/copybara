/*
 * Copyright (C) 2021 Google Inc.
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

package com.google.copybara.onboard;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** A class that makes usable config strings from {@link ConfigTemplate} objects */
public class ConfigBuilder {

  private final com.google.copybara.onboard.ConfigTemplate configTemplate;
  private String configInProgress;
  private final Map<String, String> keywordParams = new LinkedHashMap<>();

  public ConfigBuilder(com.google.copybara.onboard.ConfigTemplate configTemplate) {
    this.configTemplate = configTemplate;
    this.configInProgress = configTemplate.getTemplateString();
  }

  public ImmutableSet<com.google.copybara.onboard.RequiredField> getRequiredFields() {
    return ImmutableSet.copyOf(configTemplate.getRequiredFields());
  }

  public void setNamedStringParameter(String name, String value) {
    if (!configInProgress.contains(name)) {
      throw new IllegalStateException(
          String.format(
              "Named parameter %s not used in this template. Consider using"
                  + " setStringKeywordParameter instead.",
              name));
    }
    configInProgress =
        configInProgress.replace(String.format("::%s::", name), String.format("'%s'", value));
  }

  public void addStringKeywordParameter(String name, String value) {
    keywordParams.put(name, value);
  }

  public String build() {
    configInProgress =
        configInProgress.replace(
            "::keyword_params::",
            keywordParams.keySet().stream()
                .map(x -> String.format("    %s='%s',", x, keywordParams.get(x)))
                .collect(joining("\n")));
    if (!configTemplate.validate(configInProgress)) {
      throw new IllegalStateException(
          String.format(
              "Config is not valid.\n\nConfig: %s\n\nRequired Fields: %s",
              configInProgress,
              configTemplate.getRequiredFields().stream()
                  .map(com.google.copybara.onboard.RequiredField::name)
                  .collect(toList())));
    }
    return configInProgress;
  }

  public boolean isValid() {
    return configTemplate.validate(configInProgress);
  }
}
